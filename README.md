# URL Shortener

A prototype URL shortening service built for the Charles Schwab AI-Proficient Software Engineer assessment, with Java 21 and Spring Boot 4.1.1. It creates 6-character short codes derived from SHA-256, redirects them, counts clicks, and supports deactivating a short URL. The code, tests, error handling and container follow production practice. The H2 in-memory database, the single-instance setup and the lack of authentication are prototype limitations (see [Limitations](#prototype-limitations)).

**Start here:** run it with Docker below, then read **[docs/engineering-summary.md](docs/engineering-summary.md)**. It covers the three assessment scenarios, how AI was used, validation, and risks.

## Capabilities
- **Shorten** an http(s) URL: `201` for a new mapping, `200` with the existing mapping for an equivalent URL.
- **Redirect** `GET /{shortCode}` with `302`. A deactivated code returns `410`.
- **Analytics:** total clicks and the last click time. Only successful redirects count, and history stays available after deactivation.
- **Deactivate** a short URL. Repeat requests are idempotent. Re-shortening its URL returns `409`, and nothing is silently reactivated.
- **Validation and safe errors:** API errors handled by the application use RFC 9457 `application/problem+json` with a stable `code` and a `correlationId`.
- **Demo UI** at `/`, **Swagger UI** at `/swagger-ui.html`.

## Prerequisites
- **Docker**, for the quickest start, or
- **JDK 21.** No Maven install is needed: use `./mvnw` (or `mvnw.cmd` on Windows).

## Quick start (Docker)
```bash
docker build -t url-shortener .
docker run --rm -p 8080:8080 url-shortener
```
Then open:
- the demo UI at **http://localhost:8080/**
- Swagger UI at **http://localhost:8080/swagger-ui.html**
- the OpenAPI spec at http://localhost:8080/v3/api-docs

The image builds the jar without running tests; run `./mvnw verify` for those. It runs as a non-root user on a JRE-only Alpine base.

## Run locally (Maven)
```bash
./mvnw spring-boot:run
```

| Environment variable | Default | Purpose |
|---|---|---|
| `APP_BASE_URL` | `http://localhost:8080` | Public base for generated short URLs. Its host is also blocked as a destination. **If you use a different port, set this to match**, so the displayed and copied short URLs point at that instance |
| `APP_OWN_HOSTS` | `localhost,127.0.0.1,[::1]` | Extra hosts treated as "this shortener" (redirect-loop prevention) |
| `SPRINGDOC_ENABLED` | `true` | Set to `false` to disable Swagger UI and the spec |

## Reviewer walkthrough (about 3 minutes)
1. Open http://localhost:8080/ and shorten `https://example.com/some/page?ref=demo`. The result shows `shortCode`, `shortUrl`, `destinationUrl` and `createdAt`.
2. Click **Open**. A new tab follows the backend `302` to the destination.
3. Click **Refresh analytics**. `totalClicks` goes up.
4. Click **Deactivate**. The result shows `status = DEACTIVATED` and `deactivatedAt`. Clicking again returns the same time.
5. Click **Open** again. The backend returns `410 SHORT_CODE_DEACTIVATED`. Refresh analytics: the count is unchanged.
6. Shorten the same URL again. You get `409 URL_DEACTIVATED`, shown with its correlation ID.
7. Shorten `ftp://example.com/file`. You get `400 UNSUPPORTED_SCHEME`.
8. Optionally, try the same requests in Swagger UI.

The same flow with curl:
```bash
API=http://localhost:8080
curl -i -X POST "$API/api/v1/urls" -H 'Content-Type: application/json' -d '{"url":"https://example.com/some/page"}'
curl -i "$API/<shortCode>"                                   # 302 Location: https://example.com/some/page
curl -i "$API/api/v1/urls/<shortCode>/analytics"             # 200 {"totalClicks":1,...}
curl -i -X POST "$API/api/v1/urls/<shortCode>/deactivate"    # 200 {"status":"DEACTIVATED",...}
curl -i "$API/<shortCode>"                                   # 410 problem+json
```

## API
| Method | Path | Result |
|---|---|---|
| POST | `/api/v1/urls`, body `{"url": "..."}` | **201** + `Location` (new) · **200** (an equivalent active URL exists) · 400 · 409 (the equivalent URL is deactivated) · 503 |
| GET | `/{shortCode}` | **302** `Location: <destination>` · 400 (malformed code) · 404 (unknown) · 410 (deactivated) |
| GET | `/api/v1/urls/{shortCode}/analytics` | **200** `{shortCode, totalClicks, lastClickedAt}` · 400 · 404 |
| POST | `/api/v1/urls/{shortCode}/deactivate` | **200** `{shortCode, status, deactivatedAt}`, idempotent · 400 · 404 |
| GET | `/`, `/assets/**` | Demo UI (static; not part of the API) |

Short codes must match `^[0-9a-f]{6}$`. Unexpected failures return a generic `500 INTERNAL_ERROR` without internal details.

## Tests
```bash
./mvnw verify
```
Runs unit, integration (real Flyway/H2 schema over HTTP), concurrency, migration, error-contract/security and Cucumber acceptance tests (`src/test/resources/features`). Current result: **216 tests, 0 failures, 0 errors** (36 Cucumber scenarios).

## Architecture in brief
- **Packages:** feature packages (`url`, `analytics`, `redirect`, `ui`, `common`), each with `api → service → repository/domain` inside.
- **Identity:** URLs are normalized by a closed, conservative rule set, then hashed. Database unique constraints, not JVM locks, enforce one mapping per normalized URL identity and one mapping per short code.
- **Redirects:** 302, never 301, so every visit is counted. Click recording runs in its own transaction and can never break a redirect.
- **Lifecycle:** a `status` enum on the mapping. `resolveForRedirect` enforces eligibility, while analytics uses a lifecycle-agnostic lookup.
- **Schema:** Flyway (`V1`–`V3`) is the only schema source; Hibernate only validates.

Details: [docs/architecture.md](docs/architecture.md), [docs/persistence.md](docs/persistence.md), [ADRs](docs/adr/).

## Prototype limitations
- **Data is in-memory (H2)** and lost on restart. PostgreSQL portability is designed for but not tested against PostgreSQL.
- **No authentication, authorization or URL ownership.** Anyone who knows a short code can deactivate it. Production exposure would require authenticated ownership or administrative authorization.
- **No rate limiting,** so short codes are enumerable.
- **Single instance, no cache.** Swagger is enabled by default.
- **URL equivalence is deliberately conservative.** For example, `?a=1&b=2` and `?b=2&a=1` get different codes.
- **The UI is a minimal demonstration client,** not a production frontend.

The full list is in [engineering summary §9](docs/engineering-summary.md#9-risks-trade-offs-and-limitations).

## Documentation
| Document | Purpose |
|---|---|
| [docs/engineering-summary.md](docs/engineering-summary.md) | **Assessment summary:** the three scenarios, AI usage and ownership, validation, risks |
| [InitialDesignDoc.md](InitialDesignDoc.md) | My original pre-implementation design (kept unchanged) |
| [docs/architecture.md](docs/architecture.md) | Current architecture, flows, API, errors, concurrency, security, and differences from the original design |
| [docs/adr/](docs/adr/) | Architecture decision records 0001–0010 |
| [docs/ai-workflow/ambiguous-requirement-url-identity.md](docs/ai-workflow/ambiguous-requirement-url-identity.md) | Ambiguous-requirement scenario: what counts as an "identical" URL |
| [docs/manual-validation-url-deactivation.md](docs/manual-validation-url-deactivation.md) · [docs/manual-validation-ui.md](docs/manual-validation-ui.md) | Validation evidence for deactivation and the UI |
| [docs/standards/](docs/standards/) · [CLAUDE.md](CLAUDE.md) | Engineering standards and the guardrails used for AI-assisted work |
| [docs/ai-workflow/](docs/ai-workflow/) | Optional detail: prompts, plan iterations, review logs |

## Project layout
```
src/main/java/com/schwab/urlshortener/
  url/         shorten, resolve, lifecycle   api/ (dto/), service/, domain/, repository/, generation/
  analytics/   clicks + analytics            api/ (dto/), service/, domain/, repository/
  redirect/    GET /{code} workflow          api/, service/
  ui/          demo UI                       api/ (GET / page), web/ (UI security headers)
  common/      cross-cutting                 error/, config/, filter/, logging/
src/main/resources/static/         demo UI: index.html, assets/app.js, assets/styles.css
src/main/resources/db/migration/   Flyway migrations (the only schema source)
src/test/                          unit, integration, concurrency, security, Cucumber
docs/                              summary, architecture, ADRs, standards, validation, AI workflow
```
