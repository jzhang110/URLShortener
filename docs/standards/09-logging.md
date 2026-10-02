# Logging and observability standards

## Levels
| Level | Use | Examples here |
|---|---|---|
| ERROR | Needs attention; a request failed unexpectedly or capacity is exhausted | Unhandled exception, short-code exhaustion |
| WARN | Abnormal but recovered | Click recording failed, redirect still served |
| INFO | Meaningful business or lifecycle event | Mapping created (short code only) |
| DEBUG | Developer diagnostics | Collision retries, rejection reasons |
| TRACE | Avoid |  |

## Rules
- SLF4J with parameterized messages (`log.info("Created short code {}", code)`). Never build messages by string concatenation.
- **Correlation.** `CorrelationIdFilter` puts `correlationId` in MDC for request-scoped processing, and `logging.pattern.correlation` includes the field in the log pattern. Request-scoped log entries carry the id; startup and other non-request entries may have none. The same request id goes back to clients in `X-Correlation-Id` and in problem responses where applicable.
- **Never log:**
  - full destination URLs (query strings may carry tokens)
  - request or response bodies
  - headers such as `Authorization` or cookies
  - credentials, secrets or configuration values
  - personal data
- **Sanitize untrusted values** before logging them with `LogSanitizer.sanitize`, which strips CR/LF/TAB and truncates. Short codes are only logged after they've been validated against the hex pattern, or sanitized.
- Log an exception once, where it's handled, with the throwable as the last argument so the stack trace stays server-side.
- Logs never reach API consumers.

## Future observability
Actuator health, metrics (for example redirects, clicks-recorded vs failed, collision retries), tracing, and centralized log shipping. These are listed as future non-functional requirements.
