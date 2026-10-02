# CLAUDE.md

Guidance for Claude Code (and humans) working in this repository.

## What this is
A production-oriented prototype URL shortener: Java 21, Spring Boot 4.1.1, Maven, H2 + Flyway, springdoc, JUnit 5/Mockito, Cucumber, Docker, and a minimal static demo UI at `/`.
- Requirements: `InitialDesignDoc.md` (the original design, kept unchanged) and `docs/ai-workflow/brownfield-requirements.md` (deactivation)
- Design: `docs/architecture.md`, `docs/persistence.md`, `docs/adr/`; the assessment overview is in `docs/engineering-summary.md`
- AI-assisted changes are planned and reviewed by the engineer. AI does not commit.

## Commands
```bash
./mvnw verify                      # all tests: unit, integration, concurrency, schema, Cucumber
./mvnw test -Dtest=UrlServiceTest  # a single test class
./mvnw spring-boot:run             # app on :8080; demo UI at /, Swagger UI at /swagger-ui.html
docker build -t url-shortener . && docker run --rm -p 8080:8080 url-shortener
```

## Engineering standards (read the relevant one before changing code)
1. [Java](docs/standards/01-java.md)
2. [Spring Boot](docs/standards/02-spring-boot.md)
3. [OOP & SOLID](docs/standards/03-oop-solid.md)
4. [Concurrency](docs/standards/04-concurrency.md)
5. [Testing](docs/standards/05-testing.md)
6. [Cucumber/BDD](docs/standards/06-bdd-cucumber.md)
7. [API & OpenAPI](docs/standards/07-api-openapi.md)
8. [Error handling](docs/standards/08-error-handling.md)
9. [Logging](docs/standards/09-logging.md)
10. [Security](docs/standards/10-security.md)
11. [Persistence](docs/standards/11-persistence.md)
12. [Documentation](docs/standards/12-documentation.md)
13. [Git & commits](docs/standards/13-git-commits.md)
14. [Code review checklist](docs/standards/14-code-review-checklist.md)

## Invariants you must not break
- **Schema:** Flyway is the only schema source. Hibernate is `ddl-auto: validate`. A schema change means a new `V<n>__*.sql` in portable SQL.
- **Concurrency:** uniqueness comes from DB constraints (`uk_url_mapping_short_code`, `uk_url_mapping_normalized_url_hash`), not JVM locks. `UrlService.shorten` must stay non-transactional, with one transaction per insert attempt (ADR 0004).
- **Identity:** only the **normalized** URL is hashed. The normalization rules are exactly N1–N6 (ADR 0002). The retry input is `normalizedUrl + " #" + n` (ADR 0003).
- **Redirects:** 302 only. A click is recorded only after a successful resolution. Click recording is best-effort in its own `REQUIRES_NEW` transaction, with the catch outside it, and must never turn a valid redirect into an error (ADR 0006).
- **Lifecycle:** (ADR 0009)
  - `UrlMapping` owns its status: change it only through intent methods (`deactivate`), never setters.
  - `UrlService.resolve` is lifecycle-agnostic because analytics depends on it. Redirect eligibility belongs only in `resolveForRedirect` (`canRedirect()`).
  - What an existing mapping means for shortening is decided only in the exhaustive `UrlService.reuse()` switch.
  - A new status needs a migration that replaces `ck_url_mapping_lifecycle`.
- **Errors handled by `GlobalExceptionHandler`:** `application/problem+json` with the fixed contract. Never leak exception text, SQL or internals.
- **Logging:** never log full destination URLs. Sanitize untrusted values.
- **Design:** interfaces only at real extension points (`ShortCodeGenerator`, `DestinationPolicy`). No cache (ADR 0007).
- **UI:** a thin static client (`/` and `/assets/**`). It never duplicates backend rules. UI assets must stay at two or more path segments, because `/{shortCode}` captures single segments. UI security headers come only from `UiSecurityHeadersFilter` (ADR 0010).
- **Packages:** features at the top (`url`, `analytics`, `redirect`, `ui`, `common`) with responsibility subpackages inside (`api`, `api/dto`, `service`, `domain`, `repository`, `generation`). Dependencies point inward (`api → service → repository/domain`). Services never import `api.dto`. Entities live in `domain` (ADR 0001).

## Workflow for a feature
1. Define the behavior.
2. Write Cucumber scenarios (tag `@pending` until implemented).
3. Assess security and concurrency impact.
4. Implement the simplest maintainable solution.
5. Write unit, integration, concurrency and security tests.
6. Update the OpenAPI annotations.
7. Update the docs and ADRs.
8. Run `./mvnw verify` until it's green.
9. Self-review against the checklist.
