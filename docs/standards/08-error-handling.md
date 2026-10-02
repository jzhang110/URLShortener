# Error-handling conventions

- **One place.** `common/error/GlobalExceptionHandler` (`@RestControllerAdvice`, extending `ResponseEntityExceptionHandler` so Spring MVC's built-in errors get the same format).
- **Contract (RFC 9457).** `Content-Type: application/problem+json`, with exactly these fields:

  | Field | Content |
  |---|---|
  | `type` | `urn:problem-type:url-shortener:<code>` |
  | `title` | HTTP reason phrase |
  | `status` | HTTP status |
  | `detail` | Fixed, client-safe sentence |
  | `instance` | Request path |
  | `code` | Stable machine code (`UNSUPPORTED_SCHEME`, `INVALID_SHORT_CODE`, `SHORT_CODE_NOT_FOUND`, `SHORT_CODE_DEACTIVATED` (410), `URL_DEACTIVATED` (409), `INTERNAL_ERROR`, …) |
  | `correlationId` | Same as the `X-Correlation-Id` header |
  | `timestamp` | ISO-8601 UTC |
  | `errors` | Only for bean-validation failures: `[{field, message}]` |

- **Domain exceptions** are thrown by services, not controllers. There's one exception to this: path-variable format checks (`ShortCode.requireValid` → `InvalidShortCodeException`) run in the controller, so malformed input never reaches a lookup, and `UrlService.resolve` repeats the check as a second line of defense. Each exception maps to one status and code. Messages come from a fixed catalog (`InvalidUrlException.Reason`), never from `e.getMessage()` of an arbitrary exception.
- **Unexpected exceptions** return 500 `INTERNAL_ERROR` with a generic detail. The full exception is logged at ERROR with the correlation id. The client quotes the id and the operator finds the log.
- **Never return:**
  - stack traces
  - exception class names or messages from infrastructure
  - SQL
  - table or constraint names
  - file paths
  - hostnames
  - secrets

  `server.error.include-*` settings are all off as a second line of defense.
- **Don't use exceptions for expected control flow inside services.** A unique-constraint violation is the one deliberate exception (ADR 0004), and it's caught right at the insert.
- **Never corrupt data on failure.** A mapping's identity is immutable, its lifecycle changes only through `deactivate()` inside a transaction (ADR 0009), and failed inserts leave nothing behind.
