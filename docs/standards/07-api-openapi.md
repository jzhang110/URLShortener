# API design and OpenAPI / Swagger conventions

## REST
- **URLs.** Resource-oriented and plural: `/api/v1/urls`, `/api/v1/urls/{shortCode}/analytics`, `/api/v1/urls/{shortCode}/deactivate`. Management endpoints are versioned under `/api/v1`. The public redirect `/{shortCode}` remains unversioned so published short links are not broken by API versioning. The demo UI at `/` and `/assets/**` is static content, not an API, and is hidden from OpenAPI (ADR 0010).
- **Status codes.**
  - 201 + `Location` when something is created
  - 200 when an idempotent repeat returns the existing resource (shorten, deactivate)
  - 302 for redirects (ADR 0008)
  - 400 for invalid input, 404 for unknown resources
  - 409 when a request conflicts with the resource's lifecycle (shortening a URL whose mapping is deactivated, ADR 0009)
  - 410 when a known resource is intentionally unavailable (a deactivated short code)
  - 503 when temporarily unable to allocate
  - 500 only for unexpected errors
- **DTOs.** Requests and responses are records in `<feature>/api/dto`, and controllers map service results to them. Services never depend on DTOs. Never return JPA entities. Field names are camelCase and timestamps are ISO-8601 UTC.
- **Validation.** Bean Validation on the DTO covers shape (`@NotBlank`). Domain validation belongs in the service layer (`UrlValidator`), so every caller gets it. Path-variable format checks (`ShortCode.requireValid`) also run in the controller, so malformed input is rejected before any lookup; the service repeats the same shared check.
- **Errors.** Errors handled by the API error-handling layer use `application/problem+json`; see [error handling](08-error-handling.md). Framework-level 404s for unmatched routes aren't guaranteed to.
- **Idempotency.** Design POSTs to be naturally idempotent where the domain allows it, as in shorten.
- **Pagination.** Add `page`/`size` parameters with a stable sort to any future collection endpoint. Never return unbounded lists.

## OpenAPI
- springdoc generates the spec from the code:
  - Swagger UI: `/swagger-ui.html`
  - Spec: `/v3/api-docs` (JSON) and `/v3/api-docs.yaml`
  - Turn off with `SPRINGDOC_ENABLED=false`
- Every endpoint has an `@Operation` (summary plus the behavior a client must know, such as idempotency or click counting) and an `@ApiResponse` for **each** status it can return. Error responses reference `ProblemDetail` with media type `application/problem+json`.
- DTO fields carry `@Schema` descriptions and realistic examples.
- Keep annotations concise. Put long explanations in `docs/`, not in controllers.
- Keep the spec in sync with behavior: when you change a status code or field, update its annotation in the same change.
