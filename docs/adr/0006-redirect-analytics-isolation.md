# ADR 0006: Redirect / analytics isolation

**Status:** Accepted

## Problem
The design doc requires every successful redirect to record a click. If recording is part of the redirect's transaction, an analytics failure turns a valid redirect into a 500. Worse, catching the exception *inside* a Spring transaction still leaves the transaction marked rollback-only. The commit then throws `UnexpectedRollbackException`, and the error reaches the client anyway.

## Definitions
- **Successful click (BR-7):** the short code resolved to an existing mapping and the application went ahead with returning 302.
- Network delivery of the 302, and whether the destination loads, is outside what the application can observe. It isn't measured, and there's no destination pinging, health checking or proxying.
- Unknown or malformed codes, deactivated codes (ADR 0009), and invalid requests are never counted (BR-6).

## Alternatives
1. Resolve and record in one transaction. An analytics failure then breaks the redirect.
2. Catch the exception inside the same transaction. The transaction is still rollback-only, so the redirect still fails.
3. **Separate transactions, with the catch outside the analytics transaction.**
4. Asynchronous recording (`@Async`, queue, outbox). Clicks can be lost on crash unless there's an outbox, and it adds infrastructure.

## Decision
Option 3, synchronous and best-effort:
1. `UrlService.resolveForRedirect` runs in its own `@Transactional(readOnly = true)` transaction, which **commits and closes** before analytics runs. If the mapping is not found, the result is 404. If it exists but is deactivated, the result is 410 (ADR 0009). In both cases no click is recorded.
2. `AnalyticsService.recordClick` inserts in a separate `TransactionTemplate` with `PROPAGATION_REQUIRES_NEW`.
3. The `try/catch (RuntimeException)` wraps that template, **outside** the transaction boundary. It therefore catches insert errors and commit failures, including `UnexpectedRollbackException`. On failure it logs WARN internally with the short code, correlation id and exception, then returns normally.
4. `RedirectService`, which is not transactional, returns 302 either way. The client never sees an analytics failure.

`REQUIRES_NEW` keeps this true even if a future caller wraps the redirect in a transaction.

## Consequences and tradeoffs
- (+) The core redirect keeps working even when analytics fails (BR-8).
- (−) A click is lost if its insert fails. It's logged at WARN, so the count is a lower bound during failures.
- (−) A click is counted when the 302 is returned, even if the client disconnects before receiving it. That's inherent to BR-7.
- **Future:** guaranteed delivery would mean writing click events to a transactional outbox, or publishing them to an event stream, and aggregating them asynchronously. The redirect API contract would not change.

## Verification
- `RedirectIntegrationTest`: valid code → click + 302; unknown code → 404 with no click; repository failure → still 302, no click row; WARN logged with the correlation id and no internal details in the response.
- `AnalyticsServiceTest`: the insert and a failed commit both leave `recordClick` not throwing, and it uses `REQUIRES_NEW`.
- `redirect.feature`: the analytics-outage scenario.
