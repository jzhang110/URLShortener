# Cucumber and BDD conventions

- **Location.** `src/test/resources/features/*.feature` holds the scenarios. Glue is in `com.schwab.urlshortener.bdd`. `CucumberSuiteTest` runs them in `mvn verify`.
- **Context.** `CucumberSpringConfiguration` starts the full application on a random port with the real Flyway schema. The database is cleaned and spies are reset in a `@Before` hook, so every scenario runs independently, in any order.
- **Write scenarios before the implementation.** New scenarios can be merged ahead of their code with the tag `@pending`, which the suite skips (`not @pending`). Remove the tag in the change that implements the behavior. `main` must never contain a failing scenario.
- **Language.**
  - Scenarios use the consumer's vocabulary: short URL, destination, click. Don't use classes, tables or SQL.
  - `Given` is state, `When` is one action, `Then`/`And` are observable outcomes.
- **Observable outcomes only.** Assert HTTP status, headers, bodies and analytics numbers. Persistence is shown through behavior ("the returned short URL redirects to …"), not by querying tables.
- **Thin steps.** A step is one call to `TestApiClient` or one assertion. Put reusable logic in `support/` (`TestApiClient`, `DatabaseCleaner`, `ErrorAssertions`).
- **Reuse steps.** Search `UrlShortenerSteps` before adding a step. Use Cucumber expressions (`{string}`, `{int}`) rather than regex.
- **Scenario Outlines** for rule tables, such as normalization rules and rejection reasons. Name the rule in the title.
- **Failure injection** (for example "click analytics cannot be recorded") goes through the `@MockitoSpyBean` on `CucumberSpringConfiguration`, and it's reset before each scenario.
- **Cucumber doesn't replace unit tests.** Edge cases and branch coverage belong in JUnit.
