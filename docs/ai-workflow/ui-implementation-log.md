# Minimal demo UI: workflow log

This log explains how the small browser UI was added. It's a thin demonstration client, so a reviewer can exercise the backend end to end without Swagger. It shows how I specified, reviewed, adjusted and validated Claude's work (commit `e5b543c`). The UI is a supporting layer, **not** one of the three assessment scenarios. The design decision is recorded in [ADR 0010](../adr/0010-minimal-static-ui.md).

**Authorship:**
- **"I"** is me, the engineer.
- **"Claude"** is Claude Code.
- Validation is labelled **Automated tests**, **AI-executed verification** or **Engineer manual validation**.

AI was not permitted to commit changes. I reviewed and committed the result.

The narrative reads on its own. My original prompt and my full review and validation notes are in the **Supporting evidence** blocks at the end.

---

## 1. Requirements
**Goal:** a minimal, secure UI at `GET /` covering shorten, Open, Copy, analytics and deactivate, without changing the backend's role as the authority for every rule.

**What I asked Claude** (verbatim below). Plan first; no code until I approved.
- **Technology:**
  - plain HTML, CSS and vanilla JavaScript
  - no framework, npm, build system, CDN, new Java dependency, new API endpoint or CORS
  - same-origin relative calls
  - no client-side persistence
- **Behaviour:**
  - handle both shorten outcomes (201 and 200)
  - Open must go through the **short-code path**, never the destination
  - Copy writes only the short URL, as plain text
  - analytics refresh
  - deactivate with no client-side lifecycle rules
  - show only safe Problem Details fields, including `correlationId`
- **Security:**
  - safe DOM APIs only: no `innerHTML`, `eval` or inline handlers
  - a restrictive CSP sent as an HTTP header, **without breaking Swagger**
  - `nosniff`, `Referrer-Policy` and `X-Frame-Options`, with no Spring Security
  - only basic HTML validation (`type="url"`, `required`, `maxlength`)
  - deactivation must not be presented as access-controlled
- **Tests:** lightweight only. No Selenium or JS frameworks. Identify the existing test that expects `GET /` → 404.

## 2. What Claude proposed
- **The key finding:** the redirect route `GET /{shortCode}` matches *any single path segment*.
  - So `/app.js`, `/styles.css` and the welcome-page forward to `/index.html` would all be captured as short codes and return 400.
  - Narrowing the route (for example `{shortCode:[^.]+}`) would break the approved 400 contract for malformed codes such as `/plano.gov`.
- **The proposed design:**
  - assets under `/assets/`, two segments the redirect route can't match
  - an explicit, OpenAPI-hidden `GET /` page route instead of the welcome page
  - the security headers on that page response
  - a `textContent`-only client; Open built as `"/" + encodeURIComponent(shortCode)`
- **The test conflict:** `ShortCodeValidationIntegrationTest` listed `/` as an unmatched 404, so `"/"` would be removed from that list.

## 3. What I reviewed and changed, and why
- **`nosniff` must also apply to `/assets/**`** (verbatim below). It's a per-response protection, so the JavaScript and CSS responses need it, not only the HTML page. Swagger must still be unaffected.
  - I left the mechanism to Claude, asking for the smallest clean one: a narrowly scoped filter was acceptable, and Spring Security was not.
  - I asked for integration tests proving all four headers on `/`, `nosniff` on both assets, and Swagger loading without the UI CSP.
- **For the manual run:**
  - Set `APP_BASE_URL` to the port under test, so the returned and copied `shortUrl` point at the right instance.
  - Use `ftp://example.com/file` as the invalid-URL case. The browser's `type="url"` check accepts it, so the request actually reaches the backend.
- **The ADR and docs must describe the final filter design,** not the superseded "headers on the controller" wording.

Claude revised the plan:
- a single `UiSecurityHeadersFilter`: all four headers on `/`, `nosniff` on `/assets/**`, nothing elsewhere
- `UiController` only serves `index.html`

## 4. What was implemented (by Claude)
- **Static files:** `static/index.html`, `static/assets/app.js`, `static/assets/styles.css`.
- **`ui/api/UiController`:** `@Hidden`, `GET /` → `index.html` as `text/html;charset=UTF-8`.
- **`ui/web/UiSecurityHeadersFilter`.**
- **`ui/UiIntegrationTest`.**
- **A one-line change in `ShortCodeValidationIntegrationTest`:** `/` removed from the unmatched-route list; `/foo/bar` and `/://test` stay.
- **Docs:** ADR 0010, `architecture.md`, README.

## 5. Validation
**Automated tests:** `UiIntegrationTest` (5 tests):
- `/` → 200 HTML with the marker
- all four headers, including the exact CSP
- both assets → 200 with `nosniff` and no CSP
- Swagger UI → 200 without the UI CSP
- `/v3/api-docs` doesn't list `/`

The full suite passed: 216 tests.

**AI-executed verification:** Claude ran these during implementation:
- It confirmed the route conflict on the running app: `/index.html`, `/app.js` and `/favicon.ico` each returned 400.
- It checked all the headers and the Swagger behaviour with `curl`.
- It ran the API flow behind the UI, including 410 after deactivation, 409 on re-shortening and 400 for `ftp://`.
- It grepped the source for banned DOM and storage APIs.

The page charset was set explicitly after that run. Details are in [manual-validation-ui.md](../manual-validation-ui.md).

**Engineer manual validation (mine).** Full notes are below.
- **Code review:** I reviewed 16 points.
  - the route conflict, and that the redirect route wasn't weakened
  - unchanged redirect behaviour
  - the minimal regression-test change
  - the minimal architecture, with the backend authoritative
  - same-origin calls
  - Open going through `/{shortCode}`
  - XSS-safe rendering, with no banned APIs
  - navigation and new-tab safety
  - the CSP without `unsafe-*`
  - the header scope
  - the asset placement
  - the controller hidden from OpenAPI
  - safe error rendering
  - the honest message about unauthenticated deactivation
- **End to end, in Chrome against the Docker image:**

  | Check | Result I observed |
  |---|---|
  | `docker build` / `docker run -p 8080:8080` | Image built; app started |
  | Load `/` | UI rendered; no external assets |
  | Shorten real HTTPS URLs (e.g. Apple, Tesla) | 201; shortCode, shortUrl, destinationUrl and createdAt shown; analytics 0 |
  | Open | DevTools showed the backend **302** before the browser reached the destination |
  | Refresh analytics after repeated opens | `totalClicks` matched the successful redirects; `lastClickedAt` set |
  | Copy | Success message; the short URL was copied |
  | Deactivate | 200; `DEACTIVATED` and `deactivatedAt` shown; mapping and analytics still visible |
  | Open after deactivation | **410 `SHORT_CODE_DEACTIVATED`**; no redirect; click count unchanged |
  | `ftp://example.com/file` | 400 `UNSUPPORTED_SCHEME`, shown safely with the correlation ID |
  | Error safety | No stack traces, SQL, class names or internal messages |
  | Swagger UI | Loaded; "Try it out" worked; the operations listed with no UI route |
- **Security headers, checked in DevTools:**
  - `/` → 200 `text/html;charset=UTF-8` with the full CSP, `nosniff`, `Referrer-Policy: no-referrer` and `X-Frame-Options: DENY`.
  - `/assets/app.js` → 200 `text/javascript` with `nosniff`.
  - `/assets/styles.css` → 200 `text/css` with `nosniff`.

## 6. Result
Commit `e5b543c`: a minimal browser interface that exercises the existing backend end to end. It doesn't duplicate backend rules or weaken the API, the redirect route or Swagger.

**Known limitations** (ADR 0010):
- the browser's automatic `/favicon.ico` request gets a harmless 400
- Copy needs a secure context (`localhost` qualifies)
- deactivation remains unauthenticated in the prototype

---

## Supporting evidence

<details>
<summary>My original UI requirements prompt</summary>

> You are adding a final, intentionally minimal web UI to the existing URL shortener so a reviewer can run the application and exercise the backend end-to-end without needing Swagger.
>
> Do not implement or modify anything yet.
>
> Before proposing changes, inspect:
>
> * the current production code
> * existing API contracts and DTOs
> * current static-resource behavior
> * current controller mappings
> * current error handling / Problem Details
> * current Swagger/OpenAPI setup
> * current tests
> * current security-related code and headers
> * `claude-command.md`
>
> This UI is a thin client over the existing backend. The backend remains the source of truth for all validation, normalization, lifecycle behavior, analytics, and security rules.
>
> Constraints:
>
> * Keep the UI extremely small.
> * No React.
> * No npm or Node.
> * No frontend framework.
> * No new Java dependencies.
> * No new backend API endpoints.
> * No frontend build system.
> * No external CDN assets.
> * No external fonts.
> * No analytics/tracking scripts.
> * No client-side persistence.
> * Use plain HTML, CSS, and vanilla JavaScript only.
> * Serve the UI from Spring Boot static resources at `GET /`.
> * Use same-origin relative API calls.
> * Do not add CORS configuration.
> * Do not duplicate backend business logic in JavaScript.
>
> Preferred files:
>
> src/main/resources/static/index.html
> src/main/resources/static/app.js
> src/main/resources/static/styles.css
>
> Required UI functionality:
>
> 1. Shorten URL
> * destination URL input
> * "Shorten" button
> * call `POST /api/v1/urls`
> * support both `201 Created` and `200 OK` existing-mapping behavior
> * display:
>      * shortCode
>      * shortUrl
>      * destinationUrl
>      * createdAt
> * display safe Problem Details errors, including `URL_DEACTIVATED`
> 2. Open short URL
> * provide an "Open" action that navigates to the generated short URL
> * the link must use the application's short-code path, not the raw destination URL
> * this should exercise the real backend redirect endpoint
> 3. Copy short URL
> * provide a "Copy" action
> * copy only the generated short URL as plain text
> 4. Analytics
> * call `GET /api/v1/urls/{shortCode}/analytics`
> * display:
>      * totalClicks
>      * lastClickedAt
> * provide a "Refresh analytics" button
> 5. Deactivate
> * call `POST /api/v1/urls/{shortCode}/deactivate`
> * display:
>      * status
>      * deactivatedAt
> * preserve the existing idempotent API behavior
> * do not invent client-side lifecycle rules
> 6. Status/error display
> * use a small inline message area
> * show useful safe fields only
> * if a Problem Details response includes `correlationId`, display it
> * do not expose or invent internal exception details
>
> Visual design:
>
> * simple engineering demo UI
> * centered layout
> * approximately 600–750px maximum width
> * system font
> * restrained styling
> * simple bordered sections/cards
> * responsive enough for a normal browser window
> * no animation
> * no dashboard
> * no charts
> * no branding exercise
> * no decorative complexity
>
> Security requirements:
>
> Treat all user input and API responses as untrusted.
>
> 1. DOM / XSS safety
>
> Do not use:
>
> * `innerHTML`
> * `outerHTML`
> * `insertAdjacentHTML`
> * `document.write`
> * `eval`
> * `Function()`
> * inline event handlers
>
> Use:
>
> * `textContent`
> * `createElement`
> * `replaceChildren`
> * safe property assignment
> * `addEventListener`
>
> All user input, destination URLs, API values, error details, correlation IDs, and short codes must be rendered through safe DOM APIs.
>
> 2. Link safety
>
> The Open link must be built from the validated short code and the current application origin or a same-origin relative path.
>
> Do not create the Open link from the user-supplied destination URL.
>
> Encode dynamic path components with `encodeURIComponent`.
>
> If opening in a new tab, use:
>
> target="\_blank"
> rel="noopener noreferrer"
>
> 3. Content Security Policy
>
> Do not use inline JavaScript or inline event handlers.
>
> Keep:
>
> * JavaScript in `app.js`
> * CSS in `styles.css`
>
> Do not load resources from CDNs or third-party origins.
>
> Propose and apply an appropriately restrictive CSP for the UI, approximately:
>
> default-src 'self';
> script-src 'self';
> style-src 'self';
> connect-src 'self';
> img-src 'self';
> font-src 'self';
> object-src 'none';
> base-uri 'none';
> form-action 'self';
> frame-ancestors 'none';
>
> Prefer an HTTP response header rather than only a meta tag.
>
> Important:
> Do not apply a restrictive UI CSP globally if doing so would break Swagger UI.
>
> Inspect how Swagger UI is currently served and scope UI-specific security headers so Swagger/OpenAPI remains functional.
>
> Do not weaken the CSP with `unsafe-inline` merely for convenience.
>
> 4. Additional browser security headers
>
> For the application UI, add appropriate headers without introducing Spring Security or another dependency solely for this purpose.
>
> At minimum evaluate:
>
> * `X-Content-Type-Options: nosniff`
> * `Referrer-Policy: no-referrer`
> * `X-Frame-Options: DENY`
>
> A very small servlet/Spring filter or equivalent existing mechanism is acceptable if necessary.
>
> Keep it narrowly scoped to the static UI where appropriate.
>
> 5. Same-origin behavior
> * use relative same-origin API paths
> * do not add CORS
> * do not call third-party APIs
> * do not perform DNS/network requests from the UI
> * do not fetch destination URLs directly
> 6. Client-side validation
>
> Use only basic usability constraints such as:
>
> * `type="url"`
> * `required`
> * `maxlength="2048"`
>
> The backend remains authoritative.
>
> Do not reproduce the backend's complex URL validation, normalization, short-code validation, or lifecycle rules in JavaScript.
>
> 7. Sensitive data / browser persistence
>
> Do not use:
>
> * `localStorage`
> * `sessionStorage`
> * IndexedDB
> * cookies
> * browser-side persistence
>
> for destinations, analytics, lifecycle state, or API responses.
>
> Keep UI state only in memory/current DOM.
>
> Do not log destination URLs, API payloads, or error payloads to the browser console in production code.
>
> 8. Clipboard
>
> Use `navigator.clipboard.writeText()` for the generated short URL only.
>
> Do not write HTML clipboard formats.
>
> 9. Error handling
>
> Render only safe Problem Details fields needed by the user, such as:
>
> * code
> * detail
> * correlationId
>
> Use `textContent` for all rendered error values.
>
> Do not expose stack traces, class names, SQL, repository names, file paths, or other internal details.
>
> 10. Authentication limitation
>
> Do not add a partial authentication or authorization system solely for this UI.
>
> The existing deactivation API is intentionally unauthenticated in this prototype.
>
> Keep the documented limitation that production URL management would require authenticated owner/admin authorization.
>
> Do not imply in the UI that deactivation is access-controlled.
>
> Regression consideration:
>
> The application currently has a test that treats `GET /` as an unmatched route returning `404`.
>
> Once `index.html` exists, `GET /` intentionally becomes `200`.
>
> Identify the exact affected test and propose the minimum required change.
>
> Testing requirements:
>
> Keep testing intentionally small.
>
> Do not introduce:
>
> * Selenium
> * Playwright
> * Jest
> * Cypress
> * frontend test frameworks
>
> Add only lightweight integration coverage appropriate to a static UI:
>
> 1. `GET /` returns `200`.
> 2. The response contains an identifiable UI marker such as `URL Shortener`.
> 3. `GET /` includes the intended browser security headers.
> 4. Swagger UI remains accessible after any security-header implementation.
> 5. Existing backend tests remain unchanged except where `GET /` intentionally changes from `404` to `200`.
>
> Do not attempt to unit-test JavaScript unless you identify a concrete need that cannot be covered reasonably otherwise.
>
> Manual validation after implementation should include:
>
> * load `/`
> * shorten a new URL
> * verify the returned short URL is displayed
> * copy short URL
> * open short URL and confirm backend redirect works
> * refresh analytics and observe click count
> * deactivate URL
> * confirm lifecycle state is displayed
> * verify subsequent redirect returns the expected backend `410`
> * verify Problem Details errors render safely
> * inspect browser DevTools and confirm:
> * CSP is present
> * `X-Content-Type-Options` is present
> * `Referrer-Policy` is present
> * `X-Frame-Options` is present
> * inspect source / JavaScript and confirm no unsafe DOM insertion APIs are used
> * confirm Swagger UI still loads and works
>
> Plan requirements:
>
> Before implementing, provide:
>
> * impact analysis
> * exact files to add/change
> * current route/test conflict involving `/`
> * proposed UI structure
> * API-to-UI interaction flow
> * DOM/XSS strategy
> * link construction strategy
> * CSP/security-header strategy
> * explanation of how Swagger remains unaffected
> * error-handling strategy
> * regression risks
> * testing plan
> * manual validation plan
> * any assumptions or limitations
>
> Apply OOD/SOLID pragmatically.
>
> Do not create extra Java services, interfaces, abstractions, or frontend architecture layers unless there is a concrete need.
>
> Prefer the simplest secure design that integrates naturally into the current application.
>
> Do not implement yet.
>
> Stop after the impact analysis and implementation plan and wait for my approval.

</details>

<details>
<summary>My review of the UI plan (security-header adjustment)</summary>

> The plan is approved with one security adjustment before implementation.
>
> Keep the route-conflict solution, /assets layout, GET / controller, CSP strategy, UI behavior, tests, and documentation plan as proposed.
>
> Adjustment:
>
> Do not limit X-Content-Type-Options: nosniff to only GET /.
>
> \`nosniff\` is a per-response protection, so ensure the UI's static JavaScript and CSS responses also receive:
>
> X-Content-Type-Options: nosniff
>
> Specifically, the security-header behavior should be:
>
> GET /
> \- Content-Security-Policy
> \- X-Content-Type-Options: nosniff
> \- Referrer-Policy: no-referrer
> \- X-Frame-Options: DENY
>
> /assets/\*\*
> \- X-Content-Type-Options: nosniff
>
> Swagger/OpenAPI responses must remain unaffected and must not receive the restrictive UI CSP.
>
> Choose the smallest clean implementation for this. A narrowly scoped filter/interceptor is acceptable if that is cleaner than trying to attach headers through the static resource handler, but do not introduce Spring Security or new dependencies.
>
> Add integration coverage confirming:
> \- GET / has all four intended headers
> \- GET /assets/app.js has X-Content-Type-Options: nosniff
> \- GET /assets/styles.css has X-Content-Type-Options: nosniff
> \- Swagger UI still loads and does not receive the UI Content-Security-Policy
>
> Everything else in the plan is approved.
>
> Implement it, run ./mvnw verify, and stop afterward without committing so I can manually review the diff and browser behavior.

</details>

<details>
<summary>My full manual code review and verification notes (original text)</summary>

Manual Code Review and Verification – Minimal UI Feature

Before committing the UI implementation, I manually reviewed the generated changes and their architectural impact rather than relying only on automated tests.

MANUAL CODE REVIEW

1. Route conflict with GET /{shortCode}
I reviewed the fact that the existing redirect controller matches single path segments and could intercept normal static-resource names.

I confirmed that:

* The UI uses an explicit GET / route.
* JavaScript and CSS are served under /assets/\*\*.
* The existing redirect route does not need to be weakened.
* Existing malformed-short-code behavior remains unchanged.
2. Existing redirect behavior
I confirmed that adding the UI did not change the behavior of GET /{shortCode}.

I specifically checked that:

* Valid short codes still redirect normally.
* Invalid short-code paths still follow the existing validation behavior.
* No broad catch-all route was added.
* The UI route does not interfere with the redirect endpoint.
3. Regression test change
The existing ShortCodeValidationIntegrationTest previously treated / as an ordinary 404\.

Since / now intentionally serves the UI, I reviewed and approved the minimal regression-test change:

* Remove / from the ordinary-404 parameterized test.
* Keep /foo/bar and other unrelated paths as 404s.
* Preserve the assertion that unrelated routes do not trigger repository short-code lookups.
4. UI architecture
I confirmed the UI remains intentionally minimal.

The implementation uses:

* Plain HTML
* Plain CSS
* Vanilla JavaScript
* No JavaScript framework
* No frontend build tool
* No additional runtime dependency
* No new business API endpoints
* No CORS configuration

The UI is only a thin client over the existing backend API.

5. Backend remains authoritative
I reviewed the frontend logic to ensure backend rules were not duplicated in JavaScript.

The backend remains responsible for:

* URL validation
* URL normalization
* Duplicate URL detection
* Short-code validation
* Redirect behavior
* Analytics
* URL lifecycle/deactivation
* Error responses

The browser UI only sends requests and displays responses.

6. Same-origin API requests
I confirmed that the frontend uses relative same-origin API paths, including:
* /api/v1/urls
* /api/v1/urls/{shortCode}/analytics
* /api/v1/urls/{shortCode}/deactivate
* /{shortCode}

No external API origin is required and no CORS configuration was added.

7. Open short URL behavior
I reviewed the Open button behavior.

The UI does not navigate directly to destinationUrl.

Instead, Open uses the application's own /{shortCode} endpoint.

This ensures that opening a shortened URL actually exercises:

* The backend redirect flow
* Short-code validation
* Lifecycle eligibility
* Click analytics
8. XSS and untrusted-data handling
I reviewed how API-returned values are inserted into the page.

The UI treats backend values as untrusted text.

The implementation uses safe DOM operations such as:

* textContent
* createElement
* replaceChildren
* addEventListener

The UI does not use unsafe dynamic HTML rendering for API values.

I also reviewed the source to ensure it does not use:

* innerHTML
* outerHTML
* insertAdjacentHTML
* document.write
* eval
* Function
* Inline event handlers
* javascript: URLs
9. Navigation safety
I confirmed that the only URL dynamically assigned for navigation is the relative short-code path generated from the short code.

The destination URL returned by the API is displayed as text rather than directly converted into an executable link.

10. New-tab safety
    I reviewed the Open behavior and confirmed that opening the short URL in another tab uses protections equivalent to:
    * noopener
    * noreferrer
11. Content Security Policy
    I reviewed the CSP design and confirmed that the UI does not require unsafe-inline or unsafe-eval.

The configured policy restricts content to same-origin resources and includes controls such as:

* default-src 'self'
* script-src 'self'
* style-src 'self'
* connect-src 'self'
* img-src 'self'
* font-src 'self'
* object-src 'none'
* base-uri 'none'
* form-action 'self'
* frame-ancestors 'none'
12. Security-header scope
    I reviewed the security-header implementation to ensure it is scoped to the browser UI rather than unexpectedly changing the API contract.

The UI-specific security behavior applies to:

* /
* /assets/\*\*

Swagger and the backend APIs continue to behave independently.

13. Static asset placement
    I reviewed the decision to place browser assets under:
    * /assets/app.js
    * /assets/styles.css

This avoids conflict with the existing single-segment GET /{shortCode} route.

14. Swagger/OpenAPI isolation
    I confirmed that the page-serving UI controller is hidden from the OpenAPI definition.

The UI route is not exposed as a business API operation.

Swagger continues to document the existing application endpoints only.

15. Error rendering
    I reviewed how errors are displayed in the UI.

The browser displays safe backend Problem Details fields such as:

* Stable error code
* Human-readable detail
* Correlation ID

The UI does not expose:

* Stack traces
* SQL
* Java class names
* Internal exception messages
* Internal implementation details
16. Deactivation authorization limitation
    I confirmed that the UI clearly identifies the prototype limitation that URL deactivation is currently unauthenticated.

The page states that a production implementation would require authenticated owner or administrator authorization.

The UI does not pretend that authentication or authorization exists when it does not.

MANUAL END-TO-END FUNCTIONAL VERIFICATION

After reviewing the code and confirming the automated test suite passed, I manually tested the containerized application in Chrome.

1. Docker build
I built the application container with:

docker build \-t url-shortener .

The image built successfully.

2. Docker runtime
I ran the application with:

docker run \--rm \-p 8080:8080 url-shortener

The application started successfully.

3. UI startup
I opened:

[http\://localhost:8080/](http://localhost:8080/)

I confirmed that:

* The page loaded successfully.
* The URL Shortener UI rendered.
* The destination URL input was available.
* The Shorten button was available.
* No frontend framework or external assets were required.
4. Create a short URL
I manually entered real HTTPS destinations, including sites such as Apple and Tesla.

I clicked Shorten and confirmed the UI displayed:

* shortCode
* shortUrl
* destinationUrl
* createdAt

The backend returned 201 for a new mapping.

5. Initial analytics
After creating a new URL, I confirmed the UI loaded analytics.

The initial values showed:

* totalClicks \= 0
* No lastClickedAt value
6. Open shortened URL
I clicked Open.

The request went through the application's own short URL.

I confirmed the browser successfully arrived at the real destination.

7. Verify HTTP redirect
I opened Chrome DevTools and inspected the Network tab.

I confirmed that the short-code request to the backend returned:

302 Found

before the browser navigated to the destination website.

This verified that the UI was exercising the actual backend redirect flow rather than bypassing it.

8. Analytics increment
After following the short URL, I returned to the UI and clicked Refresh analytics.

I confirmed that:

* totalClicks increased.
* lastClickedAt was populated.

I repeated the redirect multiple times and confirmed that the analytics count reflected the number of successful redirect requests.

9. Copy short URL
I clicked Copy.

I confirmed the UI displayed a successful copy message and copied the shortened URL.

10. Deactivate a URL
    I clicked Deactivate.

I confirmed that:

* The API request returned 200\.
* The UI displayed status \= DEACTIVATED.
* The UI displayed deactivatedAt.
* The existing mapping information remained visible.
* Historical analytics remained visible.
11. Open after deactivation
    After deactivation, I manually opened the same short URL again.

I confirmed the backend returned:

410 Gone

with:

SHORT\_CODE\_DEACTIVATED

The URL did not redirect to the destination.

12. Historical analytics after deactivation
    I confirmed that analytics recorded before deactivation remained available.

The existing click count was preserved.

The rejected 410 redirect attempt did not add another successful click.

13. Invalid destination URL
    I submitted:

ftp\://example.com/file

The backend returned:

400 Bad Request

with:

UNSUPPORTED\_SCHEME

The UI safely displayed the error information and correlation ID.

14. Error-response safety
    I confirmed that the UI displayed only the backend's intended problem information.

It did not expose:

* Stack traces
* SQL
* Internal class names
* Internal exception details
15. Swagger regression check
    I opened:

[http\://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

I confirmed that:

* Swagger UI still loaded normally.
* "Try it out" still worked.
* The UI addition did not interfere with Swagger routing.
16. OpenAPI regression check
    I confirmed that Swagger still showed the intended backend operations:
    * POST /api/v1/urls
    * POST /api/v1/urls/{shortCode}/deactivate
    * GET /api/v1/urls/{shortCode}/analytics
    * GET /{shortCode}

The new browser UI route did not appear as a public API operation.

MANUAL SECURITY HEADER VERIFICATION

I also inspected the actual HTTP responses using Chrome DevTools → Network → Headers.

1. GET /

I selected the localhost document request and confirmed:

Status:
200 OK

Content-Type:
text/html;charset=UTF-8

Content-Security-Policy:
default-src 'self';
script-src 'self';
style-src 'self';
connect-src 'self';
img-src 'self';
font-src 'self';
object-src 'none';
base-uri 'none';
form-action 'self';
frame-ancestors 'none'

X-Content-Type-Options:
nosniff

Referrer-Policy:
no-referrer

X-Frame-Options:
DENY

This confirmed that the intended browser security controls are present on the HTML document.

2. GET /assets/app.js

I selected the app.js request in DevTools and confirmed:

Status:
200 OK

Content-Type:
text/javascript

X-Content-Type-Options:
nosniff

This confirmed that JavaScript is served using the expected MIME type and MIME sniffing is disabled.

3. GET /assets/styles.css

I selected the styles.css request and confirmed:

Status:
200 OK

Content-Type:
text/css

X-Content-Type-Options:
nosniff

This confirmed that the stylesheet is served using the expected MIME type and MIME sniffing is disabled.

FINAL MANUAL VERIFICATION RESULT

The UI was manually validated at multiple levels:

* Source-code and architecture review
* Route-conflict review
* Security/XSS review
* Automated regression suite
* Docker build
* Docker runtime
* Browser end-to-end workflow
* Real 302 redirect verification
* Analytics verification
* URL deactivation verification
* 410 behavior after deactivation
* Invalid-input/error handling
* Swagger regression testing
* OpenAPI regression testing
* CSP verification
* X-Content-Type-Options verification
* Referrer-Policy verification
* X-Frame-Options verification
* Static JavaScript and CSS MIME-type verification

The result was a minimal browser interface that exercises the existing backend end to end without duplicating backend business rules or weakening the existing API and redirect contracts.

</details>
