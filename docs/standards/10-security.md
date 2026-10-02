# Security standards

Security is part of design, implementation, testing and review. Every feature that crosses the trust boundary updates the threat table in `docs/architecture.md` §9.

## Input
- All HTTP input is untrusted. Validate at the service boundary with allow-lists:
  - schemes: http and https only
  - short codes: `^[0-9a-f]{6}$`
  - correlation ids: `[A-Za-z0-9._-]{1,64}`
- Enforce size limits on every string. A submitted destination URL is at most 2048 characters. Its internal normalized form may need 2049 (N5 can add `/` to an empty path), and persistence is sized for both (`destination_url` 2048, `normalized_url` 2049).
- Reject rather than "fix" dangerous input: userinfo, own-domain destinations, unsupported schemes.
- This service **never fetches destination URLs**. Anything that ever does (previews, reputation checks) needs a server-side request forgery (SSRF) review first: block private, loopback and link-local ranges, set timeouts, don't follow redirects blindly.

## Persistence
- Spring Data derived queries and JPQL with bound parameters only. No string-concatenated SQL or JPQL, and no native queries without review.
- The DB user has only the privileges it needs (future PostgreSQL: DML on app tables; Flyway gets DDL through a separate role).

## Output and errors
- Error bodies follow the safe contract ([08-error-handling](08-error-handling.md)). Redirect targets are returned only in the `Location` header, never reflected into HTML.

## Secrets and configuration
- No secrets in source, images or logs. Configuration comes from environment variables (`APP_BASE_URL`, `APP_OWN_HOSTS`, `SPRINGDOC_ENABLED`).
- Future credentials go through the environment or a secret manager.

## Dependencies
- Add one only with a clear need; prefer the Spring Boot BOM.
- Dependabot watches Maven and Docker. Review its PRs promptly.

## Containers
- Non-root user and a JRE-only runtime image.
- The app jar is read-only to the runtime user.
- One exposed port, and nothing is baked into the image.

## Browser UI
- The demo UI gets its security headers only from `UiSecurityHeadersFilter` (ADR 0010):
  - a strict `'self'`-only CSP on `/`, with no `unsafe-inline`
  - `nosniff` on `/` and `/assets/**`
  - `Referrer-Policy: no-referrer` and `X-Frame-Options: DENY` on `/`
- API values are rendered only with `textContent`.

## Future work (not in the prototype)
- Authentication and authorization. This is required before exposing URL management: deactivation is currently unauthenticated (ADR 0009). Add a CSRF review once cookies or sessions exist.
- Rate limiting and abuse controls.
- Malicious-URL detection through `DestinationPolicy`.
- Swagger disabled in production.
