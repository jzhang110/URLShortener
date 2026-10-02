# Manual validation: URL deactivation

The 20 manual checks from [brownfield-requirements.md](ai-workflow/brownfield-requirements.md) §44.

**Who ran what:**
- **Engineer (manual):** I personally performed these deactivation checks through Swagger, the browser and manual HTTP requests, as part of reviewing the brownfield change.
- **AI-executed (recorded below):** Claude Code ran the same 20 checks with `curl` against the live application during implementation, and recorded the observed values in the table below. This is supplementary evidence, not a substitute for the manual check.

- **Date:** 2026-10-01
- **Build:** At the time of this validation, the working tree had not yet been committed. `./mvnw verify` reported 204 tests, 0 failures, 0 errors.
- **How the AI-executed run was performed:** `./mvnw spring-boot:run` on port 8081 (8080 was in use by another local process), exercised with `curl`. The OpenAPI document was read from `/v3/api-docs`.
- **Destination used:** `https://example.com/deactivation-walkthrough`, short code `ea14cc`.

| # | Check | Expected | Observed | Result |
|---|---|---|---|---|
| 1 | Create an active URL | 201 | 201, `shortCode=ea14cc` | Pass |
| 2 | Redirect | 302 to the destination | 302, `Location: https://example.com/deactivation-walkthrough` | Pass |
| 3 | Follow several times | 302 each time | Two more 302s (3 clicks in total) | Pass |
| 4 | Analytics count | 200, 3 clicks | 200, `totalClicks=3` | Pass |
| 5 | Deactivate | — | `POST /api/v1/urls/ea14cc/deactivate` | — |
| 6 | Response status | 200 JSON | 200, `Content-Type: application/json` | Pass |
| 7 | Status | `DEACTIVATED` | `status=DEACTIVATED` | Pass |
| 8 | `deactivatedAt` populated | a timestamp | `2026-10-01T17:08:52.196987Z` | Pass |
| 9 | Deactivate again | — | sent about 1 second later | — |
| 10 | 200 and the same `deactivatedAt` | unchanged | 200, `2026-10-01T17:08:52.196987Z` (identical) | Pass |
| 11 | Follow the deactivated URL | — | `GET /ea14cc` | — |
| 12 | 410 | 410 `SHORT_CODE_DEACTIVATED`, no redirect | 410, `problem+json`, `code=SHORT_CODE_DEACTIVATED`, detail "This short URL has been deactivated.", **no `Location` header** | Pass |
| 13 | Analytics count did not increase | 3 | 200, `totalClicks=3`, `lastClickedAt` unchanged (`17:08:52.008346Z`) | Pass |
| 14 | Historical analytics available | 200 | 200 | Pass |
| 15 | Submit the same destination again | — | `POST /api/v1/urls` with the same URL | — |
| 16 | 409 | 409 `URL_DEACTIVATED` | 409, `code=URL_DEACTIVATED`, detail "This URL already has a deactivated short code." | Pass |
| 17 | Deactivate an unknown code | — | `POST /api/v1/urls/ffffff/deactivate` | — |
| 18 | 404 | 404 `SHORT_CODE_NOT_FOUND` | 404, `code=SHORT_CODE_NOT_FOUND` | Pass |
| 19 | Deactivate a malformed code | — | `POST /api/v1/urls/ABC123/deactivate` | — |
| 20 | 400 | 400 `INVALID_SHORT_CODE` | 400, `code=INVALID_SHORT_CODE`, detail "Short code must contain exactly six lowercase hexadecimal characters." | Pass |

## OpenAPI (`/v3/api-docs`)
- **Deactivate** `POST /api/v1/urls/{shortCode}/deactivate`: responses 200, 400, 404 and 500. The path parameter has the pattern `^[0-9a-f]{6}$`.
- **`DeactivateUrlResponse`** has the properties `shortCode`, `status` and `deactivatedAt`. The `status` enum is `["ACTIVE", "DEACTIVATED"]`.
- **Redirect** `GET /{shortCode}`: 302, 400, 404 and 410.
- **Shorten** `POST /api/v1/urls`: 200, 201, 400, 409, 500 and 503.

## Logs
- One INFO line, `Deactivated short code ea14cc`, carrying its correlation id. The repeated deactivation logs at DEBUG, which isn't enabled by default.
- The destination URL doesn't appear anywhere in the application log.

## Not covered manually
Concurrent deactivation and upgrading pre-existing data to V3 are covered by automated tests only (`DeactivationConcurrencyTest`, `V3MigrationTest`). As additional AI-executed verification, Claude Code temporarily removed the row lock from the concurrency path; the test then failed in 2 of 3 runs. With the lock restored, it passed 5 of 5 runs.
