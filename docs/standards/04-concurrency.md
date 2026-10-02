# Thread safety and concurrency conventions

Assume every bean method runs concurrently on many request threads. Durable invariants must not rely on JVM-local locking, and multi-instance behaviour must be analysed separately. This prototype exercised in-process concurrency, not a multi-instance deployment.

## Rules
1. **The database is the concurrency authority for durable state.** Enforce invariants with constraints (UNIQUE, FK). Handle `DataIntegrityViolationException` and re-read (ADR 0004). Never use JVM locks to protect database state: they don't span instances.
2. **No mutable fields in singletons.** If one seems necessary, write the analysis (who reads, who writes, what the atomicity requirement is) in a comment and pick the primitive for that requirement:
   - atomics for single-variable transitions
   - a lock only when several variables must change together
   - `ConcurrentHashMap` only for a genuinely shared map
3. **No check-then-act** against shared state without a guard that makes it atomic. In this codebase a pre-check read is only an optimization; the constraint decides.
4. **Insert-only for high-contention data** (click events). Never use read-modify-write counters.
5. **Non-thread-safe JDK types** (`MessageDigest`, `SimpleDateFormat`) are created per call or confined to one thread.
6. **Thread-locals** (MDC) are always cleared in `finally`.
7. **Transactions:**
   - Keep them short.
   - Never hold a transaction or lock across remote I/O.
   - When a failure must be tolerated, give that work its own transaction and put the catch outside it (ADR 0006).
   - Never retry inside a transaction that has already failed: PostgreSQL aborts it.

## Analysis checklist for new workflows
Document each of these in `docs/architecture.md` §8:
- shared state
- races
- atomicity
- visibility
- ordering
- duplicate processing
- idempotency
- deadlock risk
- partial failure
- multi-instance behavior

## Tests
Concurrency tests assert **observable outcomes**, such as row counts, distinct codes and click totals. They don't assert which primitives are used. Start threads together with `CountDownLatch` or `CyclicBarrier`, collect results through `Future`s, and always `shutdownNow()` the pool in `finally`.
