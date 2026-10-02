# Spring Boot best practices

- **Versions** come from the Spring Boot BOM (`spring-boot-starter-parent` 4.1.1). Pin a version explicitly only for libraries outside the BOM (springdoc, Cucumber), and give the reason in a pom comment.
- **Constructor injection only.** No field injection in production code. A single constructor needs no `@Autowired`.
- **Beans are stateless singletons.** A bean field must be final and hold either a collaborator or immutable configuration. Mutable per-request state belongs in local variables.
- **Configuration.** Bind to validated `@ConfigurationProperties` records (`ShortenerProperties`). Never read `@Value` strings in business code. Anything that varies by environment is an `${ENV_VAR:default}` placeholder in `application.yml`.
- **Layers.**
  - Controllers handle HTTP concerns, `@Valid`, DTO mapping and delegation.
  - Services hold the business rules and orchestration.
  - Repositories handle persistence only.
  - `@RestControllerAdvice` maps errors.
- **Transactions.** Declare them on service methods, never on controllers. Keep them short. Don't wrap loops that tolerate constraint violations (ADR 0004). Use `readOnly = true` for reads.
- **`spring.jpa.open-in-view=false`.** Services return detached domain/value results such as `ResolvedUrl`, not JPA entities or API DTOs.
- **Starters.** Boot 4 splits test support by module: `spring-boot-starter-webmvc-test` and `spring-boot-starter-data-jpa-test`.
- **No new starters** unless there's a concrete requirement, such as actuator once observability is in scope.
