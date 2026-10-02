# Code review checklist

Review every change as if you were the principal engineer approving a production pull request.

**Correctness**
- [ ] Meets the requirements and business rules (BR-1…BR-10). Any deviation has an ADR.
- [ ] Error cases are handled, and failures never corrupt or overwrite mappings.

**Tests**
- [ ] `./mvnw verify` is green: unit, integration, concurrency, schema, Cucumber.
- [ ] New behavior has a Cucumber scenario, or a stated reason why not.
- [ ] Unit and integration tests cover the happy path and meaningful failure paths.
- [ ] There's a regression test for every bug fix.

**Concurrency**
- [ ] Shared mutable state has been reviewed, and no singleton has unanalysed mutable fields.
- [ ] Races and duplicate processing are handled by DB constraints, not JVM locks.
- [ ] Transaction boundaries are correct: none poisoned by expected violations, and failure-tolerant work runs in its own transaction.
- [ ] There's a concurrency test when shared state is involved.

**Security**
- [ ] Untrusted input is validated with allow-lists and size limits.
- [ ] Queries are parameterized.
- [ ] Error responses keep the safe contract; `ErrorResponseSecurityTest` covers any new error path.
- [ ] Logs contain no secrets, full URLs or unsanitized input.
- [ ] There are no secrets in source or the image. Any new dependency is justified.

**Design**
- [ ] Responsibilities are separated: no business logic in controllers; repositories do persistence only.
- [ ] SOLID holds, with interfaces only at real extension points.
- [ ] No unnecessary abstractions, patterns or speculative code. No dead code.
- [ ] Naming communicates intent. Methods are small. Comments explain *why*.

**Persistence**
- [ ] Schema changes are a new Flyway migration using portable SQL; entities match it (`validate` passes).
- [ ] Constraints and indexes are justified by invariants or access patterns.

**API and docs**
- [ ] OpenAPI annotations match actual statuses and fields.
- [ ] `README`, `architecture.md`, `persistence.md` and ADRs are updated where behavior or decisions changed.

**Container**
- [ ] The Dockerfile still runs as non-root, with a minimal runtime image and specific base tags.
