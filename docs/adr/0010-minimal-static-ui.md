# ADR 0010: Minimal static demo UI

**Status:** Accepted

## Problem
Reviewers need to exercise the backend end to end (shorten, open, copy, analytics, deactivate) from a browser, without Swagger. The UI must be a thin client: plain HTML, CSS and JavaScript, with no framework, build step, new dependency, new API endpoint or CORS. The backend stays authoritative for every rule.

## Routing constraint
`RedirectController` maps `GET /{shortCode}`, which matches **any single path segment**, including names with dots (`/plano.gov` returns 400 `INVALID_SHORT_CODE` by design). Controller mappings outrank Spring's static-resource handler. Verified against the running application:

| Path | Result |
|---|---|
| `/index.html` | 400 `INVALID_SHORT_CODE` |
| `/app.js` | 400 `INVALID_SHORT_CODE` |
| `/favicon.ico` | 400 `INVALID_SHORT_CODE` |

That rules out two simple options:
- **Spring Boot's welcome page.** It forwards `/` to `/index.html`, which the redirect route would capture.
- **Top-level `/app.js` and `/styles.css`.** Both would be captured.

Narrowing the route (for example `/{shortCode:[^.]+}`) was rejected because it would turn malformed codes such as `/plano.gov` into 404s, breaking the approved 400 contract.

## Decision
- **Assets live under `/assets/`** (`static/assets/app.js`, `static/assets/styles.css`). These are two-segment paths, so `/{shortCode}` can't match them. Spring's default static handler serves them, with no catch-all route.
- **`GET /` is an explicit page route,** `ui/api/UiController`, which returns `static/index.html` as `text/html;charset=UTF-8`.
  - `/{shortCode}` can't match `/`, so there's no ambiguity.
  - It's a page, not an API operation, so it's marked `@Hidden` and stays out of OpenAPI.
- **Browser security headers come from one narrowly scoped filter,** `ui/web/UiSecurityHeadersFilter`. Its `shouldNotFilter` skips every path except exactly `/` and `/assets/**`:

  | Response | Headers |
  |---|---|
  | `GET /` | `Content-Security-Policy` (below), `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `X-Frame-Options: DENY` |
  | `/assets/**` | `X-Content-Type-Options: nosniff` (a per-response protection, so the script and stylesheet get it too) |
  | Everything else: API, redirects, Swagger UI, `/v3/api-docs` | Unchanged |

  ```
  default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self';
  font-src 'self'; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'
  ```

  The CSP has no `unsafe-inline` or `unsafe-eval`. The page has no inline script, style or event handler, and visibility uses the `hidden` attribute.
- **Swagger is unaffected.** Its responses never pass the filter's path check, so its own scripts aren't blocked; a test asserts that Swagger UI has no UI CSP. springdoc's explicit `/swagger-ui.html` mapping outranks `/{shortCode}`.
- **No Spring Security and no dependency.** A filter is the smallest mechanism that can also reach static-resource responses. `CorrelationIdFilter` is unchanged.

## Client design (`app.js`)
- **Calls:** same-origin relative `fetch` calls to the existing endpoints only. There's no CORS, no third-party request, and the page never fetches destination URLs.
- **XSS:** every API value is untrusted text, rendered through `textContent`. There is no `innerHTML`, `outerHTML`, `insertAdjacentHTML`, `document.write`, `eval` or `Function`.
- **Open** links to `"/" + encodeURIComponent(shortCode)`, so it goes through the real redirect endpoint (`target="_blank"`, `rel="noopener noreferrer"`). It's never built from the destination, and the destination is displayed as text only, never as a link.
- **Copy** writes only `shortUrl`, as plain text, through `navigator.clipboard.writeText`.
- **Errors** show only the problem `code`, `detail` and `correlationId`.
- **No client-side rules:** validation, normalization, short-code format and lifecycle behaviour are left to the backend. The only browser constraints are `type="url" required maxlength="2048"`. The lifecycle status is shown only from a deactivate response; the client never assumes ACTIVE.
- **No persistent client state:** the current result exists only in memory. No `localStorage`, `sessionStorage`, cookies or other persistence is used, and nothing is logged to the console.

## Consequences and limitations
- (+) Small: three static files and two small classes, with no dependency or API change.
- (−) **`GET /` changed from 404 to 200.** `ShortCodeValidationIntegrationTest` no longer lists `/` as an unmatched route; `/foo/bar` and `/://test` still are.
- (−) **The browser's automatic `/favicon.ico` request** gets a harmless 400 problem response from the redirect route. No favicon asset is added.
- **`shortUrl` follows config, Open follows the page.** `shortUrl` comes from `APP_BASE_URL` and is shown and copied as-is, while Open always targets the serving origin.
- **Clipboard needs a secure context.** `localhost` qualifies; other plain-http hosts show "Copy failed".
- **Deactivation is not access-controlled** in this prototype ([ADR 0009](0009-url-lifecycle-deactivation.md)). The UI says so, and adds no partial auth.

## Verification
- **`UiIntegrationTest`:**
  - `/` returns 200 HTML with the "URL Shortener" marker
  - `/` has all four headers, including the exact CSP
  - both assets return 200 with `nosniff` and no CSP
  - Swagger UI returns 200 without the UI CSP
  - `/v3/api-docs` doesn't list `/`
- **Manual:** see [manual-validation-ui.md](../manual-validation-ui.md).
