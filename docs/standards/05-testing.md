# Testing standards

## Pyramid
```
Cucumber acceptance (features/)      – consumer-visible behavior
        ↓
Integration (*IntegrationTest)       – real app, real Flyway/H2, HTTP black-box
        ↓
Unit (*Test)                         – rules, branches, edge cases; fast, no Spring
```
`./mvnw verify` runs all of them. **Every change leaves `verify` green.**

## Conventions
- **Names describe behavior:** `malformedCodeIsRejectedWithoutQueryingTheDatabase`.
- **Arrange / Act / Assert,** separated by blank lines.
- **Unit tests** construct the class directly and mock only collaborators at a boundary (repositories, generator). Don't mock value objects or the class under test.
- **Integration tests** extend `support/IntegrationTest`: random port, DB cleaned before each test, `TestApiClient` for HTTP. Prefer real collaborators. Use `@MockitoBean` / `@MockitoSpyBean` only to inject failures or force collisions.
- **Table-driven** `@ParameterizedTest` for validation and normalization rules.
- **Assert behavior, not implementation.** Assert status codes, bodies, row counts and log lines that operators rely on. Avoid brittle whole-body string equality unless the contract is exactly that.
- **Determinism:**
  - no sleeps
  - no reliance on test order
  - no wall-clock assertions beyond "present"
  - hash vectors computed independently and hard-coded
- **Regression tests first.** When fixing a defect, add a test that fails before the fix.
- **Required coverage for a feature:**
  - happy path
  - each rejection path
  - concurrency when there's shared state
  - security-sensitive behavior: leak checks, validation, injection-shaped input

## Security tests
`ErrorResponseSecurityTest` checks two things:
- the approved problem+json contract, meaning exactly these fields
- the absence of leak markers: stack frames, exception names, SQL, the internal package prefix, the thrown message

When you add an error path, add it there.
