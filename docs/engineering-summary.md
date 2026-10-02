# Engineering summary

The assessment summary: what was built, how AI was used, the three required scenarios, how they were validated, and what remains limited. It links to detailed evidence rather than repeating it.

## 1. What was built and final status
A Java 21 / Spring Boot 4.1.1 URL shortener:
- **Shortening:** SHA-256 short codes with deterministic collision retries, and duplicate and equivalent URL handling through conservative normalization.
- **Redirects and analytics:** 302 redirects, and best-effort click analytics that can never break a redirect.
- **URL deactivation lifecycle:** the brownfield enhancement.
- **Errors:** application-handled API errors use RFC 9457 problem responses with correlation ids.
- **Persistence:** Flyway-managed H2, with PostgreSQL-oriented SQL.
- **Interfaces:** Swagger/OpenAPI, a minimal browser UI at `/`, and a Docker image.

| Item | Status |
|---|---|
| `./mvnw verify` (fresh run for this summary) | **216 tests, 0 failures, 0 errors, 0 skipped**; 36 Cucumber scenarios (193 steps); BUILD SUCCESS |
| Docker | `docker build` and `docker run` performed by the engineer ([UI log](ai-workflow/ui-implementation-log.md)); re-checked during this documentation pass (see §8) |
| Run it | [README](../README.md) → `http://localhost:8080/` (UI) and `/swagger-ui.html` |

The architecture is in [architecture.md](architecture.md) and the decisions are in [ADRs 0001–0010](adr/).

## 2. How AI was used and controlled
- **Design first.** I wrote [InitialDesignDoc.md](../InitialDesignDoc.md) before any implementation. It's kept unchanged as the original design. Where the final system differs, [architecture.md §13](architecture.md#13-how-the-final-system-differs-from-the-original-design) explains why.
- **Constraints.** Two documents bounded Claude Code's work:
  - [claude-command.md](ai-workflow/claude-command.md): the engineering standards I gave Claude.
  - [CLAUDE.md](../CLAUDE.md): repository invariants, plus the [standards](standards/) Claude produced from that prompt.
- **Plan before change.** Every substantial change started in plan mode. I reviewed each plan and required corrections before allowing implementation (§3–§6). Claude then implemented, ran `./mvnw verify`, and stopped for review.
- **Engineer ownership.** I reviewed generated code file by file, tested the running application by hand, and requested corrections where needed. **AI was not permitted to commit changes. All changes were reviewed and committed by the engineer.**
- **Evidence:**
  - [greenfield-and-review-log.md](ai-workflow/greenfield-and-review-log.md): plan iterations, my plan corrections, manual tests, review findings, test-suite review.
  - [ui-implementation-log.md](ai-workflow/ui-implementation-log.md): my review of the UI plan and code, plus manual browser, Docker and Swagger validation.
  - [brownfield-requirements.md](ai-workflow/brownfield-requirements.md): the brownfield requirements prompt.
- **Validation categories:**
  - **Automated tests:** JUnit and Cucumber.
  - **AI-executed verification:** checks Claude ran during development (curl runs, a lock mutation check).
  - **Manual validation:** checks I performed myself in Swagger, the browser and DevTools.

  They're kept separate below.

## 3. Scenario 1 — Greenfield: the initial URL shortener
- **Requirement:** implement [InitialDesignDoc.md](../InitialDesignDoc.md): shorten, redirect and analytics, with validation, duplicate detection, collision resolution, persistence and tests.
- **Why greenfield:** the repository contained only the design document and the standards prompt.
- **Decomposition:**
  1. plan and ADRs
  2. scaffold and the package structure
  3. validation, normalization and generation
  4. persistence and concurrency
  5. redirect and analytics isolation
  6. errors and OpenAPI
  7. tests (unit, integration, concurrency, schema, security, Cucumber)
  8. Docker
- **AI-assisted execution:** Claude proposed the plan, then scaffolded the implementation, tests and documentation.
- **My corrections to Claude's plan, before any code** (log, "First adjustment" through "Edits for claude on planning"):
  - Remove caching from the initial implementation (now [ADR 0007](adr/0007-no-cache-in-prototype.md)).
  - Hash the **normalized** URL, keep `destinationUrl` separate from `normalizedUrl`, and use an unambiguous retry input ([ADR 0002](adr/0002-url-identity-and-normalization.md), [ADR 0003](adr/0003-short-code-hashing-and-retry-input.md)).
  - Use Flyway as the single schema source, with Hibernate only validating. Claude's first plan used `schema.sql` ([ADR 0005](adr/0005-flyway-schema-management-and-postgres-portability.md)).
  - Define a click as a successful 302, and make sure an analytics failure can never break a redirect ([ADR 0006](adr/0006-redirect-analytics-isolation.md)).
  - Keep the database as the concurrency authority ([ADR 0004](adr/0004-database-as-concurrency-authority.md)), with interfaces only at real extension points.
  - Make normalization consistent with the verification examples, so default-port and empty-path equivalence are documented and tested rather than implied. That became the closed N1–N6 rule set.
  - Fix the smoke-test examples, keep commits green, and word the Docker pinning accurately.
  - After the scaffold, restructure into a hybrid package-by-feature layout ([ADR 0001](adr/0001-package-by-feature.md)).
- **Manual validation (mine):** I containerized and ran the app, then tested 25 API scenarios and edge cases (log, "Manually test application functionality").
  - That testing showed malformed short codes returning 404. I changed the contract to **400 `INVALID_SHORT_CODE` before any lookup**.
  - I also corrected Claude's proposed `UrlService` edit, which still mapped malformed codes to not-found (commit `fafcaaf`).
- **Manual review finding:** reviewing the code file by file, I identified **five issues or gaps not covered by the initial generated test suite**. Four are edge-case correctness gaps and one is a maintainability finding. The four correctness gaps were fixed with regression coverage, and the maintainability finding was refactored and validated by the existing/updated test suite (commit `b79cae8`):

  | Issue or gap | How it was found | Resolution |
  |---|---|---|
  | Explicit ports `0`, `65536`, `99999` were accepted | Manual Swagger boundary tests | `INVALID_PORT` (1–65535) |
  | `[::1]` bypassed self-reference prevention | Manual test | One local host-canonicalization rule, with no DNS lookup |
  | An exact 2048-character input normalized to 2049 characters (N5 adds `/`) and failed to persist | Boundary testing | V2 widens `normalized_url` to 2049; the public 2048 limit is kept |
  | A broad `DataIntegrityViolationException` catch treated unrelated failures as retryable collisions | Code review, reproduced through the length issue | Classify the violation as a duplicate, a code race, or unexpected (rethrown → 500) |
  | The URL was parsed twice | Code review (maintainability) | The normalizer takes the validated `URI` |

- **Test-suite review (mine):** this led to commit `12cafde`.
  - The unexpected-integrity test was strengthened to stub and verify both race checks.
  - `retryInputCannotCollideWithAFragmentUrl` was removed, because one example can't prove "cannot collide".
  - BDD wording was corrected: no ownership language, no "never a redirect loop" overclaim, and the "persisted" step was renamed.
- **Outcome:** commits `d2bec6d`, `e2d116c`, `6a3e0d4`, `fafcaaf`, `b79cae8`, `12cafde`.

## 4. Scenario 2 — Brownfield: URL deactivation
- **Requirement:** [brownfield-requirements.md](ai-workflow/brownfield-requirements.md). Deactivate a short URL so it stops redirecting, while keeping the mapping, its history and its analytics. Deactivation must be idempotent and concurrency-safe.
- **Why brownfield:** it changes an existing, tested system and must preserve its contracts (shortening, redirects, analytics, error handling, migrations).
- **Analysis before change:** Claude's impact analysis traced every caller first. It found that `UrlService.resolve` serves **both** redirect and analytics, so adding a lifecycle check there would have turned analytics into 410.

  The design splits the two:
  - `resolve` is lifecycle-agnostic, for analytics.
  - `resolveForRedirect` enforces eligibility.
- **Design** ([ADR 0009](adr/0009-url-lifecycle-deactivation.md)):
  - an enum `UrlStatus`, stored as a string
  - `UrlMapping.deactivate()` and `canRedirect()`, with identity columns still immutable
  - a `V3` migration with `DEFAULT 'ACTIVE'` and a CHECK invariant
  - soft deactivation, never deletion
  - 410 `SHORT_CODE_DEACTIVATED`, with no click recorded
  - 409 `URL_DEACTIVATED` on re-shortening, with no silent reactivation
  - idempotent 200 that keeps the original `deactivatedAt`
  - a one-row `PESSIMISTIC_WRITE` lock for concurrent deactivations
  - an injected `Clock`, truncated to microseconds
- **My plan corrections before implementation:**
  - Decide the 409 with an exhaustive `switch` on `UrlStatus`, not `!canRedirect()`.
  - Return an immutable `DeactivateResult` instead of the JPA entity.
  - Use `VARCHAR(32)`.
  - Validate short codes following the existing controller convention.
  - Remove an absolute "reads never wait" claim from the concurrency design.
  - Document the shortening/deactivation race alongside the redirect race.
- **After my code review:**
  - `UrlMapping.deactivate()` also uses an exhaustive switch, so a new state must be classified on purpose.
  - The lifecycle test now uses a nanosecond clock, to prove the microsecond truncation.
- **Validation:**
  - **Automated:**
    - `UrlMappingTest`, `UrlLifecycleServiceTest`, `UrlServiceTest` (including the `resolve` analytics guard)
    - `UrlDeactivationIntegrationTest`, `DeactivationConcurrencyTest`
    - `SchemaMigrationTest`, and `V3MigrationTest` (upgrades V2 data)
    - `ErrorResponseSecurityTest` (410 and 409 contracts)
    - `url_deactivation.feature`
  - **AI-executed:** Claude ran the 20-step checklist with curl ([manual-validation-url-deactivation.md](manual-validation-url-deactivation.md)). It also confirmed the concurrency test is meaningful: with the row lock removed, the test failed in 2 of 3 runs.
  - **Manual (mine):** I ran the deactivation checks myself through Swagger, the browser and manual requests.
- **Outcome:** commit `75beb1c`.

## 5. Scenario 3 — Ambiguous requirement: what is an "identical" URL?
- **Requirement:** "Return the existing shortened URL when an identical original URL is submitted again." The word "identical" is undefined. Detail is in [ambiguous-requirement-url-identity.md](ai-workflow/ambiguous-requirement-url-identity.md).
- **Alternatives:**
  - raw bytes
  - **conservative normalization** (chosen)
  - full RFC 3986 normalization
  - aggressive "semantic" rules: sort the query, strip slashes, drop fragments
  - fetching the URLs and comparing destinations, rejected for SSRF risk, latency and instability
- **Decision:** two URLs are equivalent *under this application's URL identity rules* (N1–N6):
  - Merged: trim, scheme and host case, default ports, an empty path becoming `/`.
  - Kept distinct: path case, non-root trailing slash, query order, fragments, non-default ports. These are conservative identity decisions.
  - **Why:** every equivalent form redirects to the first-submitted destination. A wrong merge would send one user to another user's resource; a wrong split costs only storage.
- **Execution:** the analysis showed the code already implemented this interpretation, so **no production code changed.**
  - Tests were added for the gaps against nine comparison pairs.
  - ADR 0002 was corrected: the rules are a *subset* of RFC 3986, and the empty-port and 409 notes were added.
  - Per my instruction, the docs avoid claiming universal semantic equivalence.
- **Validation:**
  - **Automated:** `UrlNormalizerTest`, `ShortenIntegrationTest`, `shorten.feature`.
  - **AI-executed:** a curl run of the nine pairs.
  - **Manual (mine):** I ran the comparison cases myself.
- **Outcome:** commit `314b60d`.

## 6. Minimal UI (supporting demo layer, not a scenario)
- **What it is:** plain HTML, CSS and vanilla JS at `/`, so a reviewer can exercise shorten → open → copy → analytics → deactivate without Swagger. It adds no framework, dependency, build step or API endpoint, and the backend stays authoritative ([ADR 0010](adr/0010-minimal-static-ui.md)).
- **Route conflict:** Claude found, and verified on the running app, that `GET /{shortCode}` captures single-segment names such as `/index.html` and `/app.js`. The fix keeps the 400 contract for malformed codes:
  - an explicit `GET /` route
  - assets under `/assets/`
- **Security:**
  - `UiSecurityHeadersFilter` gives `/` a strict CSP plus `nosniff`, `no-referrer` and `DENY`, and gives `/assets/**` `nosniff`.
  - Swagger is untouched.
  - Rendering is `textContent` only; Open links to `/` plus the encoded short code, never to the destination.
- **My plan changes:**
  - extend `nosniff` to the assets
  - align `APP_BASE_URL` with the test port
  - add `ftp://…` as a browser-valid, backend-invalid case
- **Manual (mine):** I built and ran the Docker image and tested the full flow in Chrome. In DevTools I verified the CSP, `nosniff` on `/`, `/assets/app.js` and `/assets/styles.css`, `Referrer-Policy` and `X-Frame-Options`, plus the 410 after deactivation and safe error rendering. Swagger still loaded ([ui-implementation-log.md](ai-workflow/ui-implementation-log.md), [manual-validation-ui.md](manual-validation-ui.md)).
- **Outcome:** commit `e5b543c`.

## 7. AI output traceability (representative)
| AI output | Result | Engineer rationale | Evidence |
|---|---|---|---|
| Initial plan with caching and `schema.sql` | Rejected / changed | No measured need for a cache; one versioned schema source | Log; ADR 0005, 0007 |
| Identity = trim + lowercase scheme/host; one stored URL, hashed as given | Changed | Hash the normalized URL explicitly; store the destination separately; make the rules consistent with the examples (default port, empty path) | Log; ADR 0002 |
| Flat package-by-feature scaffold | Changed | Hybrid feature/responsibility layout | Log; ADR 0001 |
| Malformed codes → 404 (original design) | Changed | 400 at the boundary, before lookup | Log; commit `fafcaaf` |
| `UrlService.resolve` edit throwing not-found for malformed codes | Corrected before approval | Consistent 400 vs 404 semantics | Log |
| Validation, persistence and integrity handling | Five issues or gaps found in manual review | Correctness and maintainability | Log; commit `b79cae8` |
| `retryInputCannotCollideWithAFragmentUrl` test | Rejected (removed) | One example can't prove impossibility | Log; commit `12cafde` |
| Shared-`resolve` trap, identified by Claude | Accepted | Keeps analytics working after deactivation | ADR 0009 |
| 409 decided by `!canRedirect()`; entity returned from the service | Changed | Future states must not be misclassified; keep the entity inside the service | ADR 0009 |
| `deactivate()` using an `if` rather than an exhaustive switch | Changed after review | Compile-time decision for new states | `UrlMapping` |
| "No code change needed" for the ambiguous requirement | Accepted | The code already matched; tests and docs codify it | Commit `314b60d` |
| UI headers only on `/` | Changed | `nosniff` is per response, so assets need it too | UI log; ADR 0010 |
| UI route-conflict analysis | Accepted | Avoids weakening the short-code route | ADR 0010 |

## 8. Quality gates
| Gate | Evidence |
|---|---|
| Unit (97 tests) | Validator, normalizer, self-reference, generator vectors, `UrlService` branches, lifecycle, analytics isolation |
| Integration (82 Spring/HTTP tests + 1 Flyway migration test) | Real app, real Flyway/H2, HTTP black-box; includes the error-contract/leak tests, the UI and header tests, and the schema tests |
| Concurrency | `ShortenConcurrencyTest`, `CollisionIntegrationTest`, `DeactivationConcurrencyTest`, and concurrent redirects in `AnalyticsIntegrationTest` |
| Acceptance | 36 Cucumber scenarios (`src/test/resources/features`) |
| Manual (engineer) | Swagger API and edge-case testing, review findings, deactivation, ambiguity cases, browser/DevTools/Docker (§3–§6) |
| AI-executed | Curl walkthroughs and the lock mutation check, recorded separately in the manual-validation docs |
| Docker (AI-executed, during this documentation pass) | `docker build` succeeded. A container run with `-p 8082:8080 -e APP_BASE_URL=http://localhost:8082` gave: `/` → 200 with all four UI headers; `/assets/app.js` with `nosniff`; shorten → returned `shortUrl` followed → 302; analytics 1; deactivate → `DEACTIVATED`; follow again → 410; Swagger 200 without the UI CSP |

## 9. Risks, trade-offs and limitations
- **Durability:** H2 is in memory, so all data is lost on restart.
  - PostgreSQL portability is designed and documented ([persistence.md](persistence.md)), but **not** integration-tested against PostgreSQL.
  - Concurrency tests run on H2 only.
- **Security:** there is no authentication, authorization or URL ownership, so **anyone who knows a short code can deactivate it**. Other gaps:
  - no rate limiting, so 6-hex codes (about 16.7M) can be enumerated
  - Swagger is enabled by default
- **Scale:** this is a single-instance prototype with no cache ([ADR 0007](adr/0007-no-cache-in-prototype.md)). The design is stateless and backed by database constraints, but multi-instance deployment hasn't been exercised.
- **Behaviour:**
  - **Normalization is deliberately conservative,** so some practically equivalent URLs get separate codes.
  - **Analytics are best-effort:** a failed click insert is logged and lost.
  - **Deactivation race:** a redirect already in progress when deactivation commits may still record one click.
  - **The first-submitted form is the redirect target** for every equivalent URL.
- **UI:** a thin demo, not a production frontend. A browser `/favicon.ico` request gets a harmless 400, and Copy needs a secure context (`localhost` qualifies).

## 10. Assumptions and final status
- Assumptions are listed in [architecture.md §12](architecture.md#12-assumptions). Material architectural deviations from the original design are documented in architecture.md §13 and the relevant ADRs.
- **Status:**
  - All three scenarios are implemented and validated.
  - The full suite passes (216 / 0 / 0).
  - The application runs locally and in Docker, with the UI and Swagger.
  - Remaining limitations are listed in §9.
