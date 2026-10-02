# Manual validation: demo UI

**Who ran what:**
- **§1–§3, AI-executed:** Claude Code ran these during implementation, with `curl` (making the same requests `app.js` makes) and a source scan.
- **§4, engineer (manual):** I subsequently completed the browser checks myself, in Chrome against the Docker image. The full record is in [ui-implementation-log.md](ai-workflow/ui-implementation-log.md), under "Manual code review and manual functionality test".

- **Date:** 2026-10-01
- **Build:** working tree, not committed
- **How the AI-executed run was performed:** `APP_BASE_URL=http://localhost:8081 ./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081` (8080 was in use by another local process).

## 1. Page, assets and headers
| Check | Observed | Result |
|---|---|---|
| `GET /` | 200, `Content-Type: text/html` | Pass |
| `GET /` headers | `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self'; font-src 'self'; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `X-Frame-Options: DENY` | Pass |
| `GET /assets/app.js` | 200, `text/javascript`, `nosniff`, no CSP | Pass |
| `GET /assets/styles.css` | 200, `text/css`, `nosniff`, no CSP | Pass |
| `GET /swagger-ui.html` | 302 → `/swagger-ui/index.html`, then 200 with **no** UI CSP or `X-Frame-Options` | Pass |
| Route-conflict evidence (ADR 0010) | `/index.html`, `/app.js` and `/favicon.ico` → 400 `INVALID_SHORT_CODE` | As expected |

After this run, the page's charset was made explicit in `UiController` (`text/html;charset=UTF-8`). `UiIntegrationTest` asserts only the `text/html` prefix, and the page also declares `<meta charset="utf-8">`.

## 2. API flow behind the UI
| Step | Observed | Result |
|---|---|---|
| Shorten `https://example.com/ui-walkthrough?q=1` | 201, `shortCode=99f94c`, `shortUrl=http://localhost:8081/99f94c` (points at the instance under test) | Pass |
| Open `/99f94c` | 302 `Location: https://example.com/ui-walkthrough?q=1` | Pass |
| Analytics | 200, `totalClicks=1` | Pass |
| Deactivate | 200, `status=DEACTIVATED`, `deactivatedAt=2026-10-01T19:59:01.343169Z` | Pass |
| Open again | 410 `SHORT_CODE_DEACTIVATED`, with a `correlationId` | Pass |
| Shorten the same URL again | 409 `URL_DEACTIVATED`, with a `correlationId` | Pass |
| Shorten `ftp://example.com/file` (passes the browser's `type=url` check, rejected by the backend) | 400 `UNSUPPORTED_SCHEME`, detail "Only http and https URLs are supported.", with a `correlationId` | Pass |

## 3. Source check
- **`app.js`:** no `innerHTML`, `outerHTML`, `insertAdjacentHTML`, `document.write`, `eval(`, `Function(`, `localStorage`, `sessionStorage`, `indexedDB`, `document.cookie` or `console.`.
- **`index.html`:** no inline event handlers, inline `<script>` or `style=` attributes.

## 4. In-browser checks (completed manually by the engineer)
I ran these myself: `docker build -t url-shortener .`, `docker run --rm -p 8080:8080 url-shortener`, then Chrome at `http://localhost:8080/`, using real HTTPS destinations. A reviewer can repeat them.

1. The page loads without CSP violations in the DevTools console. The only expected noise is a 400 for the browser's automatic `/favicon.ico` request (ADR 0010).
2. Shorten a new URL: shortCode, shortUrl, destinationUrl and createdAt appear, and analytics shows 0.
3. **Copy**, then paste somewhere: you get the plain short URL.
4. **Open** goes through a new tab to the destination, via the backend 302.
5. **Refresh analytics**: totalClicks increments.
6. **Deactivate**: status `DEACTIVATED` and deactivatedAt appear. Clicking again shows the same deactivatedAt.
7. **Open** again: the new tab shows the backend 410 problem JSON.
8. Shorten the same URL again: the message shows `URL_DEACTIVATED: …` and a Correlation ID.
9. Shorten `ftp://example.com/file`: the message shows `UNSUPPORTED_SCHEME: …` and a Correlation ID.
10. In DevTools → Network → `/`, confirm the four response headers; on `/assets/app.js`, confirm `nosniff`.
11. Open `/swagger-ui.html`: it loads, and "Try it out" works.
