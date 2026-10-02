# ADR 0004: The database is the concurrency authority

**Status:** Accepted

## Problem
Concurrent requests can race in two ways:
- Two requests for the same URL could both miss the duplicate check and both insert.
- Two different URLs could land on the same candidate code at the same moment.

Either race could break BR-2 (one code ↔ one URL) or BR-3 (one URL ↔ one mapping). The design also has to stay correct when there are several application instances.

## Alternatives
1. JVM locking (`synchronized`, a `ConcurrentHashMap` of in-flight URLs, `ReentrantLock`). This only protects one process.
2. Pessimistic DB locking (`SELECT ... FOR UPDATE`, table locks). It's heavy, and it can't lock a row that doesn't exist yet.
3. Serializable isolation. It's database-specific and needs retry loops anyway.
4. **Unique constraints plus catching the violation.**

## Decision
Option 4. `UNIQUE(short_code)` and `UNIQUE(normalized_url_hash)` are the guarantees. Application reads (the hash lookup and the code-exists check) are only fast paths. The algorithm in `UrlService.shorten`:
1. Look up by hash. If a row is found, return it.
2. For each attempt, generate a code and skip it if it's taken. Otherwise INSERT in its **own** transaction.
3. On `DataIntegrityViolationException`, classify it before acting:
   - Re-read by hash. If a row is found, a concurrent duplicate won, so return it.
   - Otherwise check whether the candidate code is now taken. If it is, it was a code race with a different URL, so try the next attempt.
   - Otherwise neither race explains the violation, so rethrow it. The client gets a generic 500 `INTERNAL_ERROR` and the cause is logged. It must never be retried into a misleading 503 `SHORT_CODE_UNAVAILABLE`.

`shorten()` is not `@Transactional`, so a violation never poisons an outer transaction. That matters on PostgreSQL ([persistence.md](../persistence.md)).

## Tradeoffs
- (+) Concurrency authority comes from the database's unique constraints, not from application-level coordination. This was verified under concurrent execution on H2. Application instances sharing the same database are designed to rely on those same persisted constraints; multi-instance deployment wasn't exercised in this prototype. The application adds no JVM locks and no pessimistic row locks to coordinate inserts. Any locking involved in constraint enforcement belongs to the database engine. PostgreSQL is designed for but not verified in this prototype ([persistence.md](../persistence.md)). Simple code.
- (−) A losing racer pays for one failed insert and one re-read, which is cheap and rare.
- **Scope:** this decision covers *inserts*. Changing an existing row's lifecycle is a read-modify-write, and it takes a one-row database lock instead ([ADR 0009](0009-url-lifecycle-deactivation.md)).
- Verified by `ShortenConcurrencyTest` (32 racing identical or equivalent submissions → 1 row) and `CollisionIntegrationTest` (8 racing URLs forced onto the same code sequence → 8 distinct codes, each redirecting to its own destination).
