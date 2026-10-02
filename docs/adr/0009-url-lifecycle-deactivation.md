# ADR 0009: URL lifecycle and deactivation

**Status:** Accepted. This is a brownfield change; it amends parts of ADR 0004 and the "mappings are immutable" rule (see *Changes to earlier decisions*).

## Problem
A short URL must be able to stop redirecting (`POST /api/v1/urls/{shortCode}/deactivate`) while its mapping, short code, destination and click history are kept, and its analytics stay readable. Deactivation must be idempotent and safe under concurrent requests. Reactivation, expiration, suspension and soft deletion are expected later but are not built now.

## Alternatives
1. **Delete the mapping row.** Breaks `fk_click_event_url_mapping` and loses history. Rejected.
2. **A boolean `active` flag.** Simple, but it can't grow into expired, suspended or deleted states without another column and migration.
3. **A `status` enum plus intent methods on the entity.** One column grows by adding values; the entity guards the transition.
4. **The State pattern or a state-machine library.** Classes or a dependency for one transition. Not justified.

## Decision
Option 3.

**Model**
- `UrlStatus { ACTIVE(true), DEACTIVATED(false) }`. Each state declares `allowsRedirect()`, the only source of redirect eligibility.
- `UrlMapping` owns the state: `status` (`@Enumerated(STRING)`, `VARCHAR(32)`) and `deactivatedAt`.
- There are no setters. `deactivate(Instant)` is idempotent (it returns `false` and keeps the original time when already deactivated), and `canRedirect()` reads `status.allowsRedirect()`.
- Invariant: `ACTIVE ⇔ deactivatedAt == null`. The entity maintains it, and the `ck_url_mapping_lifecycle` CHECK enforces it again in the database. That CHECK also restricts status to the known values, so adding a state is a deliberate migration.

**Lookup is separate from redirect eligibility**
- `UrlService.resolve` stays **lifecycle-agnostic**. Analytics uses it, so history remains readable after deactivation.
- `UrlService.resolveForRedirect` adds the eligibility check (`!canRedirect()` → `ShortCodeDeactivatedException` → **410 `SHORT_CODE_DEACTIVATED`**). `RedirectService` calls it before recording a click, so a refused redirect is never counted and has no `Location` header.
- Putting the check inside the shared `resolve` would have turned analytics into 410. Filtering by status in the repository would have made deactivated codes look unknown (404). Both were rejected for those reasons.

**410, not 404.** A deactivated mapping still exists; it is intentionally unavailable rather than unknown.

**Re-shortening a deactivated URL: 409 `URL_DEACTIVATED`**
- `UrlService.reuse()` is the single decision for "an equivalent mapping already exists". Both the duplicate fast path and the concurrent-winner path use it.
- It switches exhaustively on `UrlStatus` with no `default`: `ACTIVE` reuses the mapping, and `DEACTIVATED` throws `UrlDeactivatedException`.
- A future state fails to compile there until its shortening behaviour is chosen. This deliberately does **not** use `canRedirect()`, because "cannot redirect" won't mean "deactivated" once expired or suspended states exist.
- The service never inserts a second mapping and never reactivates. Reactivation will be an explicit operation.

**Lifecycle service**
- `UrlLifecycleService.deactivate` is a concrete class with no interface. It is separate from `UrlService`, which owns creation and resolution, because lifecycle changes are locked read-modify-write transactions with their own time source.
- It returns an immutable `DeactivateResult`, so the entity never leaves the service. `UrlController` maps that result to `DeactivateUrlResponse`.
- The endpoint lives in the existing `UrlController`: it is an operation on the same `/api/v1/urls` resource, and a second controller for one endpoint wasn't justified.

**Time.** An injected `Clock` (`ClockConfig`) supplies `deactivatedAt`, truncated to microseconds so that a repeated request returns exactly the value read back from the database.

## Concurrency
- **Concurrent deactivations.** `deactivate` reads the row with `PESSIMISTIC_WRITE` (`SELECT … FOR UPDATE`) inside one short transaction. Competing requests are serialized: the second gets the lock only after the first commits, sees DEACTIVATED, and returns the same `deactivatedAt`. Without the lock, both would read ACTIVE and the last writer would overwrite the timestamp, a lost update that `DeactivationConcurrencyTest` detects.
- **Why a lock, when ADR 0004 rejected locks.** ADR 0004 rejected locking for *inserts*, where there is no row to lock and unique constraints do the job. Here the row exists, the lock covers exactly one row, no other application lock is taken (which avoids lock-ordering deadlocks between application locks), and it is held only for the duration of the short transaction. An `@Version` column would have needed schema and a retry loop for the same guarantee.
- **Other reads.** Redirects, analytics and shortening use plain, non-locking `SELECT`s, so they don't request the row lock or queue behind it. Under the default READ COMMITTED isolation they see the last committed lifecycle state. On H2 (MVStore) and PostgreSQL, both multi-version databases, such reads aren't blocked by the writer; a differently configured database could make them wait briefly. Correctness doesn't depend on readers never waiting.
- **Redirect/deactivation race.** After the deactivation transaction commits, a redirect that starts afterwards reads the committed DEACTIVATED state and returns 410; there's no application cache to invalidate (ADR 0007). Where instances share the same database, they're designed to observe the persisted state the same way. Multi-instance deployment wasn't exercised in this prototype. A redirect that read ACTIVE before the commit may still return 302 and record one click timestamped just after `deactivatedAt`. Total ordering is not guaranteed and no distributed lock is used.
- **Shortening/deactivation race.** A shortening request whose lookup read the mapping as ACTIVE before the commit may return the existing mapping (200). Any lookup after the commit returns 409. In neither case is a second mapping created, because the unique hash constraint still holds.

## Security
The prototype has no authentication, users, ownership or authorization (out of scope in the design document), so anyone who knows a short code can deactivate it. **The prototype demonstrates URL lifecycle behaviour, but production exposure of the deactivation endpoint would require authenticated ownership or administrative authorization.** No placeholder token or partial auth is added.

The short code is validated (`ShortCode.requireValid`) before any lookup, both in the controller (as on the other short-code endpoints) and in the service, which stays authoritative. Only the validated code is logged, never the destination. The 410/404 distinction reveals that a code once existed; the requirements ask for it.

## Changes to earlier decisions
| Earlier decision | Change | Why |
|---|---|---|
| `UrlMapping` immutable once persisted (standards 03 and 11) | Identity columns stay `updatable = false`; only `status` and `deactivated_at` change, and only through `deactivate()` | Lifecycle state has to change; BR-4 (a mapping is never overwritten) still holds |
| ADR 0004: no locks | One-row `PESSIMISTIC_WRITE` lock in `deactivate` only | Prevents a lost update on an existing row (see above) |
| One `resolve` for every use | `resolve` (any state) and `resolveForRedirect` (eligible only) | Analytics must keep working for deactivated mappings |
| Timestamps via `Instant.now()` | Lifecycle uses the injected `Clock`; `UrlService` and `AnalyticsService` are unchanged | Deterministic `deactivatedAt` tests; aligning the other services is a separate cleanup |

## Extending later
- **Reactivation:** `UrlMapping.reactivate()` and a service method, plus a decision on whether `deactivatedAt` is cleared.
- **Expiration or suspension:** a new `UrlStatus` value with its `allowsRedirect`, a migration replacing `ck_url_mapping_lifecycle`, branches in the compile-forced switches in `UrlService.reuse()` and `UrlMapping.deactivate()`, and probably its own 410 code.
- **A resolution cache** (ADR 0007) would need eviction on every status change.

## Verification
- **Unit:** `UrlMappingTest`, `UrlLifecycleServiceTest`, and `UrlServiceTest` (eligibility, the lifecycle-agnostic `resolve` guard, 409 on both reuse paths).
- **Integration:** `UrlDeactivationIntegrationTest`, `ShortCodeValidationIntegrationTest` (deactivate path), `ErrorResponseSecurityTest` (410 and 409 contracts).
- **Concurrency:** `DeactivationConcurrencyTest`.
- **Schema:** `SchemaMigrationTest` (columns, CHECK, defaults) and `V3MigrationTest` (upgrading V2 data).
- **Acceptance:** `url_deactivation.feature`.
