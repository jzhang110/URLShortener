# Java coding standards

- **Java 21.** Use records for immutable data (DTOs, value results, configuration), `var` only when the type is obvious from the right-hand side, switch expressions and text blocks where they make code clearer.
- **Naming.** Classes are nouns (`UrlNormalizer`), methods are verbs (`normalize`), booleans read as predicates (`created()`). Test names describe behavior (`unknownShortCodeReturns404AndRecordsNoClick`).
- **Immutability.** Fields are `final` unless JPA requires otherwise. Copy incoming collections defensively (`List.copyOf`). Entities expose getters only.
- **Visibility.** Use the smallest that works. Package-private is the default for implementation classes (`Sha256ShortCodeGenerator`, `SelfReferencePolicy`, controllers). Make something `public` only when another package needs it, and remember that feature subpackages (`api`, `service`, `domain`, ...) are separate packages.
- **Methods.** Keep them small, with one level of abstraction each, and prefer early returns over nesting.
- **Nulls.** Don't return `null` from public methods; use `Optional` for "maybe absent" lookups. Document the cases where a DTO field can legitimately be null (`lastClickedAt`).
- **Exceptions.** Use unchecked domain exceptions (`InvalidUrlException`, `ShortCodeNotFoundException`). Never swallow an exception without logging. The one deliberate swallow is best-effort analytics (ADR 0006), and it logs at WARN.
- **Comments.** Explain *why*: invariants, tradeoffs, links to ADRs. Delete comments that restate the code.
- **Static utilities.** Only for pure, stateless functions with no domain meaning (`Sha256`, `LogSanitizer`).
- **Warnings.** The build compiles with `-Xlint:all`. Treat new warnings as defects.
- **Locale.** Lowercase and uppercase with `Locale.ROOT`.
