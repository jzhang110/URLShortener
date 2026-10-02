# Brownfield scenario: URL deactivation

This document has two parts:
1. **Workflow** (below): what was done, why, and how it was validated.
2. **[Original requirements specification](#original-requirements-specification-as-given-to-claude):** the specification I wrote and gave to Claude, kept unchanged.

**Authorship:**
- **"I"** is me, the engineer.
- **"Claude"** is Claude Code.
- Validation is labelled **Automated tests**, **AI-executed verification** or **Engineer manual validation**.

AI was not permitted to commit changes. I reviewed and committed the result as `75beb1c`. The design decision is recorded in [ADR 0009](../adr/0009-url-lifecycle-deactivation.md).

---

## 1. The system was designed to evolve before it was built
My original design, [InitialDesignDoc.md](../../InitialDesignDoc.md), was written before any implementation. It required that "application architecture should be able to accommodate these [future enhancements] without substantial refactoring", and it named the enhancements I expected:

- **User management:** registration, authentication and URL ownership
- **URL management:** deactivation, deletion and expiration dates
- **Custom aliases**
- **Malicious URL detection** (reputation checks)
- **Abuse reporting**
- **Advanced analytics:** geographic, referrer and time-based
- **URL suspension** by administrators or automated controls
- **Future non-functional growth:** durability across restarts, high availability, horizontal scaling with distributed caching, advanced security (rate limiting, DDoS protection), observability, and consistency across instances

The original domain design already prepared for them:
- **`UrlMapping`** listed potential future fields **`ownerId`, `Status` and `expiresAt`**.
- **`User`** was identified as a **future entity**. Ownership on `UrlMapping` was meant to be **optional**, so existing anonymous URLs would keep working.
- **`ClickEvent`** belongs to exactly one existing `UrlMapping`. I noted that this "limits the future delete feature to be soft deletes only": a mapping with click history can't simply be removed.

All of these capabilities were **deliberately left out of the initial scope**. The design's "Out of Scope — Future Enhancements" list includes authentication and ownership, expiration, deletion, custom aliases, advanced malicious-link detection, PostgreSQL, Redis caching and multi-instance deployment.

The greenfield implementation then built extension points into the code ([greenfield log](greenfield-and-review-log.md)):
- feature packages with inward-pointing layers (ADR 0001)
- Flyway as the only, versioned schema source (ADR 0005)
- interfaces only where variation was expected (`ShortCodeGenerator`, `DestinationPolicy`)
- an analytics feature that references mappings by id

## 2. Deactivation was selected as the brownfield enhancement
For this assessment, **URL deactivation is the one brownfield enhancement selected and implemented**.

The source material doesn't record a detailed comparison of every anticipated enhancement. What it does record (specification §1, §2 and §49) is why deactivation is a good demonstration:
- it introduces **lifecycle behaviour** into an existing, tested system
- it must **preserve the existing shortening, redirect and analytics responsibilities**
- it exercises the `Status` extension point the original design anticipated

The rest of the anticipated list **remains future work**: reactivation, expiration, deletion, suspension, ownership and authentication, custom aliases, reputation checks, abuse reporting, advanced analytics and the infrastructure items. See [architecture.md §1](../architecture.md#1-requirements-summary) and §46 of the specification.

**The sequence:**
1. the original design anticipated evolution
2. those capabilities were left out of the initial scope
3. deactivation was selected as the brownfield change
4. an impact analysis was run against the existing system
5. the feature was added without restructuring the unrelated core workflows

## 3. The requirement
I wrote the [requirements specification](#original-requirements-specification-as-given-to-claude) below. In short:
- **The endpoint:** `POST /api/v1/urls/{shortCode}/deactivate`, which returns `shortCode`, `status` and `deactivatedAt`.
- **The model:** an enum-backed `UrlStatus` (`ACTIVE`, `DEACTIVATED`) owned by `UrlMapping`, with an intent method instead of setters.
- **Lifecycle behaviour:**
  - idempotent repeats that keep the original timestamp
  - an injected `Clock`
  - 410 for deactivated redirects, with no click recorded
  - analytics preserved
  - 409 when an equivalent URL is shortened again, never a silent reactivation
- **Concurrency and data:** safe concurrent deactivation, and a forward-only V3 migration that keeps existing data valid.
- **The trap:** an explicit warning (§20) against adding a lifecycle check to a shared lookup used by analytics.
- **Design quality:** SOLID applied pragmatically, with no needless abstractions.
- **Validation:** unit, integration, concurrency, BDD and migration tests, plus a 20-step manual checklist (§44).
- **Security:** the authentication limitation must be documented, not patched with fake auth.

## 4. Planning: Claude's impact analysis and initial proposal
**What I asked Claude** (verbatim below):
- read the requirements, the standards and the whole codebase
- produce an **impact analysis and implementation plan, without writing code**
- explicitly evaluate SOLID, encapsulation, extensibility, concurrency and security
- identify places where a simple implementation would be unsafe

**What Claude found in the existing system:**
- **The key risk:** `UrlService.resolve()` is shared by both `RedirectService` (redirects) and `AnalyticsService` (analytics). A deactivation check added there would turn analytics into 410 for deactivated URLs, which is exactly the trap the specification warns about.
- **The proposed separation:**
  - `resolve()` stays **lifecycle-agnostic**, for analytics.
  - A new `resolveForRedirect()` enforces eligibility through `UrlMapping.canRedirect()`, backed by `UrlStatus.allowsRedirect()`.
- **Other unsafe shortcuts it listed:**
  - filtering by status in the repository, which would make deactivated codes look unknown (404, not 410)
  - skipping deactivated rows when shortening, which hits the unique hash and returns the inactive mapping as 200
  - copying `updatable = false` onto the lifecycle columns, which silently skips the UPDATE
  - an unlocked read-modify-write, a lost update under concurrency
  - an untruncated timestamp, so a repeat appears to change the time

**What Claude proposed:**
- a focused `UrlLifecycleService` (concrete, no interface)
- the endpoint in the existing `UrlController`, because it's the same resource, rather than a new controller
- a V3 migration with a CHECK constraint on status and `deactivated_at`
- a one-row `PESSIMISTIC_WRITE` lock for concurrent deactivations, with the reasoning for departing from ADR 0004 recorded
- an injected `Clock`
- 410 and 409 handlers in the existing Problem Details handler
- explicit notes on where the plan changes earlier decisions (mapping immutability, the no-locks rule, the single `resolve`)

Several of its first choices were changed in my review:
- the 409 decision used `!existing.canRedirect()`
- the service returned the JPA entity
- the status column was `VARCHAR(16)`
- short-code validation appeared in both the controller and the service

## 5. My corrections A1–A4, and why
I approved the overall design but required four changes before implementation (verbatim below):

| # | Claude's proposal | My correction | Why |
|---|---|---|---|
| **A1** | Decide the 409 with `!existing.canRedirect()` | Decide it explicitly on the lifecycle **status**, centrally | "Cannot redirect" won't mean "deactivated" once states such as EXPIRED or SUSPENDED exist; `canRedirect()` must stay purely about redirect eligibility |
| **A2** | `UrlLifecycleService` returns the `UrlMapping` entity | Return an immutable `DeactivateResult(shortCode, status, deactivatedAt)`, which the controller maps to the DTO | Keeps the JPA entity inside the service boundary and avoids coupling the controller to persistence; consistent with `ShortenResult` |
| **A3** | `VARCHAR(16)` status column | `VARCHAR(32)` (migration and JPA) | Room for future status names; adding a state still needs a deliberate migration of the CHECK |
| **A4** | Short-code validation in both the controller and the service | Avoid duplication unless it matches an established convention; the service must stay authoritative | Avoid needless duplication without weakening the guard |

**How Claude applied them:**
- **A1:** a single `reuse()` method with an **exhaustive `switch` on `UrlStatus` and no `default`**. `ACTIVE` reuses the mapping, `DEACTIVATED` throws `UrlDeactivatedException`, and a future state fails to compile until someone decides its behaviour.
- **A2, A3:** applied as specified.
- **A4:** Claude showed that the existing `RedirectController` and `AnalyticsController` already validate at the boundary, with `UrlService` re-checking. Following that convention, through the one shared `ShortCode.requireValid`, keeps all three short-code endpoints consistent, so I accepted it.

## 6. Reconciliation before implementation
Claude first added A1–A4 as a note at the top of the plan, leaving the older text below. I required the plan to be **reconciled so no contradictory version remained**, plus two more accuracy fixes:
- **Concurrency claims must be precise.** The plan said ordinary reads "never wait" on the lock. I asked for the actual guarantee instead: plain non-locking reads under READ COMMITTED on the multi-version databases we target (H2 MVStore, PostgreSQL), and correctness that doesn't depend on readers never waiting.
- **Document the shortening/deactivation race,** alongside the redirect race. A shorten whose lookup sees ACTIVE before deactivation commits may return the existing mapping (200). Any lookup after the commit gets 409. In neither case is a second mapping created.

## 7. Final design and trade-offs
Details are in [ADR 0009](../adr/0009-url-lifecycle-deactivation.md).
- **Soft deactivation, not deletion.** The mapping, short code, destination and click history are all kept, which preserves the `ClickEvent` foreign key the original design anticipated.
- **The entity owns the lifecycle.**
  - `deactivate(Instant)` is idempotent and keeps the original time.
  - `canRedirect()` answers redirect eligibility.
  - The identity columns stay non-updatable.
  - The invariant (ACTIVE ⇔ no `deactivatedAt`) is enforced by the entity and by the database CHECK.
- **Lookup is separate from eligibility.** Analytics uses `resolve()`; redirects use `resolveForRedirect()`. A deactivated redirect gets 410 before any click is recorded.
- **Concurrency.** A one-row lock serializes deactivations, so every caller sees the same `deactivatedAt`. The trade-off is that competing deactivators wait briefly; ADR 0004's no-lock rule still holds for inserts.
- **Race guarantees** (documented, not hidden):
  - Requests that start after the commit see DEACTIVATED, because there's no cache (ADR 0007).
  - A request already in flight may still complete on ACTIVE.
- **Time.** An injected `Clock`, truncated to microseconds so repeated responses match what the database stores.
- **Security limitation.** The endpoint is unauthenticated because the prototype has no users. This is documented as a production blocker and not patched with partial auth.

## 8. What was implemented (by Claude)
The change stayed inside the existing `url` feature and its layers:
- **Domain:** `UrlStatus`, `UrlMapping` (lifecycle fields and methods), `ShortCodeDeactivatedException`, `UrlDeactivatedException`.
- **Service:**
  - `UrlLifecycleService` and `DeactivateResult`
  - `UrlService`: `resolveForRedirect()`, the exhaustive `reuse()`, and `resolve()` documented as lifecycle-agnostic
  - `RedirectService` switched to `resolveForRedirect()`
- **Repository:** `findForUpdateByShortCode` (`PESSIMISTIC_WRITE`).
- **API and config:**
  - `UrlController` gains `POST /{shortCode}/deactivate`
  - `DeactivateUrlResponse`
  - `GlobalExceptionHandler`: 410 and 409
  - `ClockConfig`
  - OpenAPI annotations on the redirect, shorten and analytics endpoints
- **Persistence:** `V3__add_url_deactivation.sql`, which adds `status VARCHAR(32) DEFAULT 'ACTIVE' NOT NULL`, a nullable `deactivated_at`, and `ck_url_mapping_lifecycle`. V1 and V2 are untouched.
- **Not restructured:** shortening, collision handling, normalization, click recording and the analytics queries.

## 9. Issues I found in code and test review, and their fixes
After implementation I reviewed the production code and tests and asked for two targeted changes (verbatim below):
1. **`UrlMapping.deactivate()` must also use an exhaustive `switch` on `UrlStatus`, with no `default`.** It had treated every non-DEACTIVATED state as deactivatable, so a future state would silently become deactivatable. With the switch, adding a state forces a compile-time decision. Behaviour for ACTIVE and DEACTIVATED is unchanged.
2. **The truncation test didn't prove truncation.** `UrlLifecycleServiceTest` used a clock value already at microsecond precision. It now uses `…07:30:00.123456789Z` and asserts `…07:30:00.123456Z`. Production code is unchanged.

## 10. Validation
- **Automated tests:**
  - `UrlMappingTest` (lifecycle invariants)
  - `UrlLifecycleServiceTest` (clock time, idempotency, not-found, malformed → no repository access)
  - `UrlServiceTest`: eligibility, **a guard that `resolve()` still finds deactivated mappings**, and 409 on both reuse paths
  - `UrlDeactivationIntegrationTest`
  - `DeactivationConcurrencyTest` (16 racing requests → one `deactivatedAt`)
  - `SchemaMigrationTest`, and `V3MigrationTest` (upgrades a database holding V2 data and keeps the mappings and clicks)
  - `ErrorResponseSecurityTest` (410 and 409 contracts)
  - `ShortCodeValidationIntegrationTest` (the deactivate path)
  - `url_deactivation.feature`

  At this stage, `./mvnw verify` reported **204 tests, 0 failures, 0 errors**.
- **AI-executed verification:**
  - **The 20-step checklist with curl**, recorded in [manual-validation-url-deactivation.md](../manual-validation-url-deactivation.md).
  - **A lock mutation check.** With the row lock temporarily replaced by a plain read, `DeactivationConcurrencyTest` failed in 2 of 3 runs, showing the test detects lost updates. With the lock restored, it passed 5 of 5 runs.
- **Engineer manual validation:**
  - I personally performed the deactivation checks through Swagger, the browser and manual requests: create, redirect, analytics, deactivate, repeat deactivate, 410, unchanged analytics, 409 on re-shortening, unknown → 404, malformed → 400.
  - I reviewed the production code, migration, tests and documentation before committing.

## 11. Outcome
Commit `75beb1c`. Deactivation was added as a lifecycle concern inside the existing architecture:
- active URLs behave exactly as before
- deactivated URLs return 410 and keep their history
- repeat and concurrent requests converge on one timestamp
- existing data migrates safely
- nothing in the shortening, redirect or analytics workflows was restructured

The remaining anticipated enhancements (§2) stay future work. The exhaustive status switches and the CHECK-constrained status column make adding the next lifecycle state a deliberate, compiler-checked change.

---

## Supporting evidence (original prompts)

<details>
<summary>My planning prompt: impact analysis before any implementation</summary>

> You are implementing a brownfield enhancement to the existing URL shortener. Before proposing any implementation, read and analyze these files from the repository: brownfield-requirements.md; claude-command.md; the existing project design and architecture documentation; the current production code relevant to URL creation, redirect resolution, analytics, persistence, validation, exception handling, Swagger/OpenAPI, Flyway, and testing.
>
> Use the following precedence if anything conflicts: 1. brownfield-requirements.md is the primary source of truth for this feature's required behavior. 2. Existing intentional architectural decisions and current application contracts should be preserved unless the brownfield requirements explicitly require a change. 3. claude-command.md defines the engineering standards for how the feature should be analyzed, designed, implemented, tested, secured, documented, and reviewed. 4. Use Principal Engineer-level judgment for implementation details not explicitly defined.
>
> Do not write or modify code yet. First inspect the existing repository and produce a brownfield impact analysis and implementation plan. The plan must include: current components, classes, workflows, and database structures affected by URL deactivation; how the existing UrlMapping domain model should evolve; how enum-backed lifecycle status should be modeled and persisted; how lifecycle invariants such as ACTIVE vs DEACTIVATED and deactivatedAt should be encapsulated; whether a dedicated lifecycle service and/or management controller is justified based on the current architecture; how the implementation will preserve SRP, OCP, LSP where applicable, ISP, DIP, encapsulation, high cohesion, and low coupling; how the design remains open to future lifecycle states such as expiration, suspension, reactivation, and soft deletion without implementing those features now; how redirect eligibility will be separated from generic mapping lookup so historical analytics remain available for deactivated URLs; how duplicate shortening of a deactivated destination will return the required conflict behavior without silently reactivating or creating another mapping; Flyway migration changes and how existing data will remain valid; transaction boundaries, idempotency, and concurrency behavior for repeated or concurrent deactivation requests; redirect/deactivation race behavior and what guarantees the implementation can realistically provide; error-handling changes; API contract changes and Swagger/OpenAPI updates; security implications, especially the fact that authentication, ownership, and authorization are currently outside scope; unit, integration, concurrency, Cucumber/BDD, and schema/migration tests required; documentation and ADR updates required; regression risks; any assumptions, ambiguities, or conflicts.
>
> Explicitly review the proposed design against: SRP, OCP, LSP where applicable, ISP, DIP, encapsulation, separation of concerns, high cohesion, low coupling, testability, maintainability, future lifecycle extensibility, concurrency safety, security, simplicity.
>
> Apply SOLID and OOD pragmatically. Do not introduce interfaces, factories, abstract classes, strategy patterns, state-machine frameworks, or additional architectural layers solely to demonstrate design patterns. Every abstraction must have either a concrete current responsibility or a credible extension point already identified by the requirements. Prefer the simplest design that satisfies the brownfield requirements while fitting naturally into the existing codebase.
>
> Do not silently replace existing architectural decisions. If you believe an existing design should change, explicitly state: the current design, the concern, the proposed change, why the change is necessary, the tradeoffs. Also identify any places where a seemingly simple implementation would be unsafe, such as adding a deactivation check to a shared resolution method and accidentally breaking analytics.

</details>

<details>
<summary>My corrections A1–A4 to Claude's plan</summary>

> The plan is approved with the following required adjustments before implementation:
>
> 1. Do not use `!existing.canRedirect()` as the condition for duplicate-shortening conflict behavior. `canRedirect()` should remain the domain abstraction for redirect eligibility, but inability to redirect is not semantically equivalent to DEACTIVATED once future states such as EXPIRED or SUSPENDED exist. In the centralized shortening reuse path, handle the currently defined DEACTIVATED lifecycle state explicitly and return URL_DEACTIVATED as required. Keep this decision centralized rather than scattering enum comparisons.
> 2. Do not return the JPA UrlMapping entity from UrlLifecycleService. Introduce a small immutable application/service result such as `DeactivateResult(String shortCode, UrlStatus status, Instant deactivatedAt)`. UrlLifecycleService should return this result. UrlController should map that result to DeactivateUrlResponse. This keeps the persistence/domain entity from escaping the service boundary, avoids coupling the controller to the JPA entity, keeps API DTOs out of the service layer, and is consistent with the existing ShortenResult-style application boundary.
> 3. Use VARCHAR(32) rather than VARCHAR(16) for persisted UrlStatus, including the JPA column length. Continue using EnumType.STRING and the database CHECK constraint. Adding a future lifecycle state should still require an intentional migration updating the CHECK constraint.
> 4. Avoid duplicate short-code validation between the controller and service unless that matches an established existing controller convention. The service must remain authoritative enough that malformed input cannot reach the repository.
>
> Everything else in the plan is approved, including: UrlStatus owning redirect permission; UrlMapping owning the deactivate state transition; ACTIVE/deactivatedAt and DEACTIVATED/deactivatedAt invariants; resolve remaining lifecycle-agnostic; resolveForRedirect enforcing redirect eligibility; preserving analytics access after deactivation; dedicated UrlLifecycleService; injected Clock; V3 forward-only migration; database CHECK constraint; idempotent 200 behavior; pessimistic row locking for deterministic concurrent deactivation; 410 SHORT_CODE_DEACTIVATED; 409 URL_DEACTIVATED on duplicate shortening; preservation of historical analytics; migration-from-V2 compatibility testing; unit, integration, concurrency, schema, security-contract, and Cucumber coverage; OpenAPI changes; ADR and documentation updates; documented authentication/authorization limitation.
>
> Update the plan to reflect these adjustments, then proceed with implementation incrementally. After implementation, run the complete verification suite but do not consider the feature complete until I manually review the production-code changes, test changes, migration, and documentation.

</details>

<details>
<summary>My reconciliation instruction (no contradictory plan text; precise concurrency claims; shortening race)</summary>

> The revised architectural decisions are approved. Before implementation, reconcile the full plan so the superseded text in the lower sections matches A1–A4. Do not leave contradictory versions of the design in the plan. Specifically: change all lifecycle status column references from VARCHAR(16)/length=16 to VARCHAR(32)/length=32; change UrlLifecycleService.deactivate to return DeactivateResult rather than UrlMapping; update controller/response mapping to use DeactivateResult rather than UrlMapping; replace the old `!existing.canRedirect()` duplicate-shortening logic with the approved exhaustive UrlStatus decision; update the statement that no code outside the entity compares lifecycle state, since the centralized shortening reuse decision now intentionally switches on UrlStatus; preserve canRedirect() exclusively as the redirect-eligibility abstraction.
>
> Also adjust the concurrency documentation so it does not make an absolute cross-database claim that ordinary reads "never wait." State the actual locking/isolation guarantee supported by the implementation.
>
> Finally, document the duplicate-shortening/deactivation race similarly to the redirect/deactivation race: a shortening request that observes ACTIVE before deactivation commits may return the existing mapping, while requests after committed deactivation must return 409 URL_DEACTIVATED.
>
> After reconciling the plan, proceed with implementation according to the approved design. Do not commit anything yet. Run the full verification suite when implementation is complete, then stop so I can manually review production code, migration, tests, and documentation before commit.

</details>

<details>
<summary>My post-review production-code correction: exhaustive switch in <code>UrlMapping.deactivate()</code></summary>

> Production-code review found one lifecycle extensibility improvement. Update `UrlMapping.deactivate(Instant)` to use an exhaustive switch on UrlStatus rather than treating every non-DEACTIVATED state as eligible for deactivation. Current behavior for ACTIVE and DEACTIVATED must remain identical: ACTIVE → set DEACTIVATED and deactivatedAt, return true; DEACTIVATED → preserve original timestamp and return false. Do not add a default branch. The intent is that adding a future lifecycle state causes a compile-time decision about whether that state may transition to DEACTIVATED. Make no other production-code changes.

</details>

<details>
<summary>My post-review test correction: prove microsecond truncation</summary>

> The test review found one small coverage gap. UrlLifecycleService deliberately truncates Clock time to microseconds because the first in-memory response must match a later value read back from the database. The current UrlLifecycleServiceTest uses an Instant that is already at microsecond precision, so it does not actually prove truncation occurs. Update the test clock to use a nanosecond-precision Instant, for example `2026-10-01T07:30:00.123456789Z`, and assert that the resulting deactivatedAt is `2026-10-01T07:30:00.123456Z`. Keep the production implementation unchanged. Make no other test changes for this request, then run ./mvnw verify. Do not commit.

</details>

---

## Original requirements specification (as given to Claude)

The specification below is reproduced unchanged.

# **Brownfield Feature Requirements — URL Deactivation**

## **1\. Feature Overview**

Extend the existing URL shortener with the ability to **deactivate an existing shortened URL**.

Deactivation must prevent future redirects while preserving:

* the existing `UrlMapping`;  
* the original destination URL;  
* the short code;  
* historical click events;  
* historical analytics.

This is a **brownfield enhancement** to an existing working application. The implementation must integrate with the current architecture without breaking existing URL creation, redirect, analytics, collision handling, validation, concurrency, or error-handling behavior.

The existing design anticipated URL lifecycle management as a future extension rather than part of the initial prototype. InitialDesignDoc

---

# **2\. Goals**

The feature shall:

* allow an existing active short URL to be deactivated;  
* represent lifecycle state with an enum-backed domain model;  
* prevent deactivated short URLs from redirecting;  
* preserve historical analytics;  
* make deactivation idempotent;  
* preserve existing mappings rather than physically deleting them;  
* integrate cleanly with the current codebase;  
* follow object-oriented design and SOLID principles;  
* remain open to future URL lifecycle capabilities such as:  
  * reactivation;  
  * expiration;  
  * suspension;  
  * soft deletion;  
* avoid unnecessary architectural complexity.

---

# **3\. API Requirement**

Add a URL deactivation endpoint:

POST /api/v1/urls/{shortCode}/deactivate

## **Successful response**

For an active short URL:

200 OK  
Content-Type: application/json

Example:

{  
  "shortCode": "abc123",  
  "status": "DEACTIVATED",  
  "deactivatedAt": "2026-10-01T07:30:00Z"  
}

The endpoint shall return a representation of the resulting lifecycle state.

---

# **4\. Response Model**

Introduce a dedicated API response model such as:

public record DeactivateUrlResponse(  
        String shortCode,  
        UrlStatus status,  
        Instant deactivatedAt) {  
}

The API shall use the enum-backed `UrlStatus` value rather than a free-form status string.

The response model should remain separate from the JPA entity.

---

# **5\. URL Lifecycle Status**

Introduce a domain enum:

public enum UrlStatus {  
    ACTIVE,  
    DEACTIVATED  
}

The enum shall be designed so that additional lifecycle states may be added later without requiring widespread redesign.

Potential future states may include:

EXPIRED  
SUSPENDED  
DELETED

These future states are **not part of the current implementation**.

The original design already identified `Status` and `expiresAt` as potential future `UrlMapping` fields. InitialDesignDoc

---

# **6\. Persistence Requirements**

Add lifecycle state to `url_mapping`.

Create a new Flyway migration rather than modifying an existing migration.

Expected migration:

V3\_\_add\_url\_deactivation.sql

Add:

status  
deactivated\_at

Conceptually:

status VARCHAR(...) NOT NULL DEFAULT 'ACTIVE',  
deactivated\_at TIMESTAMP WITH TIME ZONE NULL

Existing rows must migrate to:

status \= ACTIVE  
deactivated\_at \= NULL

No existing `UrlMapping` or `ClickEvent` records may be lost.

Existing click-event relationships must remain valid. The original model defines every `ClickEvent` as belonging to an existing `UrlMapping`, which is one reason lifecycle changes should preserve the mapping rather than physically remove it. InitialDesignDoc

---

# **7\. Enum Persistence**

Persist `UrlStatus` using its string representation.

Use:

@Enumerated(EnumType.STRING)

Do not persist enum ordinals.

Required persisted values initially:

ACTIVE  
DEACTIVATED

This prevents enum ordering changes from altering database semantics and makes future status additions safer.

---

# **8\. Domain Model Requirements**

`UrlMapping` shall own its lifecycle state.

Add lifecycle fields conceptually equivalent to:

private UrlStatus status;  
private Instant deactivatedAt;

The entity must not expose arbitrary public state mutation such as:

setStatus(...)  
setDeactivatedAt(...)

Instead, expose intent-based domain behavior such as:

deactivate(...)

For example:

mapping.deactivate(timestamp);

The entity should enforce the relationship between lifecycle state and deactivation timestamp.

Required invariant:

ACTIVE  
→ deactivatedAt \== null

DEACTIVATED  
→ deactivatedAt \!= null  
---

# **9\. Domain State Transition**

The initial supported lifecycle transition is:

ACTIVE  
  |  
  | deactivate  
  v  
DEACTIVATED

The following transition is **not supported**:

DEACTIVATED → ACTIVE

Reactivation remains a future feature.

The entity should make repeated deactivation safe and idempotent.

---

# **10\. Deactivation Behavior**

For an active mapping:

status \= ACTIVE  
deactivatedAt \= null

After successful deactivation:

status \= DEACTIVATED  
deactivatedAt \= current timestamp

Return:

200 OK

with:

{  
  "shortCode": "abc123",  
  "status": "DEACTIVATED",  
  "deactivatedAt": "..."  
}  
---

# **11\. Idempotency**

Deactivation must be idempotent.

Given:

abc123  
status \= DEACTIVATED  
deactivatedAt \= T1

a subsequent request:

POST /api/v1/urls/abc123/deactivate

shall return:

200 OK

and:

status \= DEACTIVATED  
deactivatedAt \= T1

The original `deactivatedAt` value must not be replaced by a new timestamp during ordinary sequential retries.

---

# **12\. Timestamp Handling**

Lifecycle logic should use an injectable time source.

Prefer:

Clock

and:

Instant.now(clock)

instead of directly calling:

Instant.now()

inside lifecycle business logic.

This allows deterministic testing of `deactivatedAt`.

---

# **13\. Unknown Short Code**

For a syntactically valid short code that does not exist:

POST /api/v1/urls/abcdef/deactivate

return:

404 Not Found

using the existing application error contract.

Expected error code:

SHORT\_CODE\_NOT\_FOUND

Do not create a mapping.

---

# **14\. Invalid Short Code**

A malformed short code must fail validation before persistence access.

Examples include codes that violate the existing short-code format.

Return:

400 Bad Request

Expected code:

INVALID\_SHORT\_CODE

The repository should not be queried for a malformed short code.

Existing short-code validation behavior must be reused rather than duplicated.

---

# **15\. Redirect Behavior**

Active URL behavior must remain unchanged:

ACTIVE  
→ GET /{shortCode}  
→ 302 Found  
→ Location: original destination  
→ successful redirect may record ClickEvent

A deactivated mapping must not redirect.

Instead:

DEACTIVATED  
→ GET /{shortCode}  
→ 410 Gone

Expected application error code:

SHORT\_CODE\_DEACTIVATED

Suggested client-facing detail:

This short URL has been deactivated.

No `Location` redirect header should be returned.

---

# **16\. Why 410 Instead of 404**

A deactivated mapping still exists.

The resource is intentionally unavailable rather than unknown.

Therefore:

unknown code  
→ 404

known but deactivated code  
→ 410

The implementation shall preserve that distinction.

---

# **17\. Analytics Behavior**

Historical analytics must remain available after deactivation.

Example:

create URL  
→ redirect 3 times  
→ totalClicks \= 3

deactivate URL

request analytics  
→ 200  
→ totalClicks \= 3

Deactivation must not:

* delete click events;  
* reset click counts;  
* hide existing analytics;  
* cause analytics lookup to return `410`.

The existing design defines analytics as derived from access events associated with a mapping. InitialDesignDoc

---

# **18\. Redirects After Deactivation Must Not Affect Analytics**

Given:

totalClicks \= 3

after deactivation:

GET /abc123  
→ 410

the analytics count must remain:

3

No new `ClickEvent` may be created for a deactivated URL.

Only successful redirects should contribute to analytics, consistent with the existing application's behavior.

---

# **19\. Mapping Lookup Semantics**

The implementation must explicitly distinguish between:

finding an existing mapping

and:

resolving a mapping for redirect

Different use cases have different lifecycle requirements.

### **Redirect**

mapping must exist  
AND  
mapping must permit redirect

### **Analytics**

mapping must exist  
lifecycle state does not prevent historical analytics access

### **Lifecycle management**

mapping must exist  
mapping may already be deactivated

Do not globally treat every deactivated mapping as nonexistent.

---

# **20\. Existing `resolve()` Impact**

The current application uses URL resolution in multiple workflows.

The implementation must inspect existing usages before modifying shared resolution behavior.

Do not simply add:

if (status \== DEACTIVATED) {  
    throw ...  
}

to a shared lookup path if doing so would inadvertently break analytics.

The brownfield implementation should introduce the minimum clean separation necessary between:

mapping lookup

and:

redirect eligibility  
---

# **21\. Duplicate Shortening After Deactivation**

Given:

POST /api/v1/urls  
destination \= https://example.com/a

→ short code abc123

then:

POST /api/v1/urls/abc123/deactivate

then the same normalized destination is submitted again:

POST /api/v1/urls

the service must **not**:

* create another mapping;  
* silently reactivate the mapping;  
* return the inactive short URL as though it were usable.

Instead return:

409 Conflict

Expected application code:

URL\_DEACTIVATED

Suggested detail:

This URL already has a deactivated short code.

This preserves the existing normalized-URL uniqueness model.

---

# **22\. No Automatic Reactivation**

Submitting the same destination again must not reactivate a deactivated URL.

Reactivation must be an explicit future lifecycle operation.

Therefore:

shortening request  
\!=  
reactivation request  
---

# **23\. Concurrency Requirements**

Deactivation must behave safely under concurrent requests.

For two concurrent deactivation requests:

Request A ─┐  
           ├──► one final DEACTIVATED mapping  
Request B ─┘

Both requests should converge on the same final state.

No duplicate mapping or inconsistent lifecycle state may be created.

---

# **24\. Redirect/Deactivation Race**

Do not introduce unnecessary distributed locking.

Required consistency guarantee:

> After the deactivation transaction has committed, subsequent redirect requests must not redirect and must return `410 Gone`.

A redirect already in progress while deactivation commits may complete.

This limitation should be documented rather than attempting to guarantee unrealistic total ordering across simultaneous requests.

---

# **25\. Architecture Requirements**

The feature must integrate cleanly with the existing architecture.

The design shall follow:

* object-oriented design;  
* object-oriented programming;  
* SOLID principles;  
* encapsulation;  
* separation of concerns;  
* high cohesion;  
* low coupling;  
* maintainability;  
* testability;  
* pragmatic extensibility.

The original architecture explicitly required components with clearly separated responsibilities and future capabilities to be introduced without substantial refactoring. InitialDesignDoc

---

# **26\. Single Responsibility Principle**

Each component must retain a focused responsibility.

Expected separation:

Controller  
→ HTTP concerns

Lifecycle service  
→ lifecycle use-case orchestration

UrlMapping  
→ mapping state and lifecycle invariants

Repository  
→ persistence

Exception handler  
→ mapping domain/application exceptions to HTTP responses

DTO  
→ API representation

Do not place lifecycle business rules inside controllers.

Do not place HTTP semantics inside entities.

Do not place lifecycle state-transition logic inside repositories.

---

# **27\. URL Lifecycle Service**

Lifecycle management should be separated from unrelated creation/collision responsibilities where doing so improves cohesion.

A focused service such as:

UrlLifecycleService

is preferred if consistent with the current architecture.

Its current responsibility would be:

deactivate URL

and it may later accommodate:

reactivate  
expire  
suspend  
soft-delete

without requiring these future features to be implemented now.

The current design describes `UrlService` as responsible for creation, duplicate detection, collision resolution, and resolution. InitialDesignDoc

Avoid turning it into a catch-all service containing every future URL-management concern.

---

# **28\. Controller Design**

A focused URL management controller may be introduced if it provides a cleaner responsibility boundary.

Conceptually:

UrlController  
→ shortening

UrlManagementController  
→ lifecycle operations

Possible endpoint ownership:

UrlManagementController  
POST /api/v1/urls/{shortCode}/deactivate

However, the implementation should inspect the current controller structure before deciding.

Do not create another controller solely for architectural appearance if the existing controller already represents URL management cleanly and remains cohesive.

---

# **29\. Open/Closed Principle**

The implementation should allow additional lifecycle states without requiring lifecycle logic to be copied throughout the application.

Avoid repeated checks such as:

if (mapping.getStatus() \== UrlStatus.DEACTIVATED)

across many unrelated classes.

Prefer centralized intent-based behavior such as:

mapping.canRedirect()

or lifecycle-related behavior associated with the domain model/status.

For example, `UrlStatus` may expose behavior conceptually equivalent to:

ACTIVE(true),  
DEACTIVATED(false);

with:

allowsRedirect()

if that produces a simpler design.

Do not implement future states prematurely.

---

# **30\. Encapsulation**

Prefer:

mapping.deactivate(timestamp);  
mapping.canRedirect();

over:

mapping.setStatus(...);  
mapping.setDeactivatedAt(...);

or widespread direct enum comparisons.

The object owning lifecycle state should be responsible for maintaining its invariants.

---

# **31\. Interface Segregation**

Do not introduce broad interfaces such as:

UrlOperations {  
    shorten();  
    redirect();  
    analytics();  
    deactivate();  
    delete();  
    expire();  
}

Do not add hypothetical methods for future requirements.

Interfaces should remain focused on actual current responsibilities.

---

# **32\. Dependency Inversion**

Services should depend on existing meaningful abstractions such as repository interfaces.

Use dependency injection for infrastructure concerns such as:

Clock

where it improves deterministic behavior and testability.

Do not create an interface solely because a class is a Spring service.

For example, do not automatically create:

UrlLifecycleService  
UrlLifecycleServiceImpl

unless multiple implementations or a meaningful abstraction boundary actually exists.

---

# **33\. Liskov / Inheritance Requirements**

This feature does not require an inheritance hierarchy.

Do not create unnecessary structures such as:

AbstractUrlLifecycleHandler  
ActiveUrlHandler  
DeactivatedUrlHandler  
LifecycleStrategy  
LifecycleStrategyFactory

solely to demonstrate design patterns.

Prefer composition, enums, domain behavior, and focused services.

---

# **34\. Pragmatic SOLID Requirement**

Apply SOLID pragmatically.

Do not introduce:

* unnecessary factories;  
* unnecessary abstract classes;  
* unnecessary interfaces;  
* unnecessary strategy patterns;  
* unnecessary state-machine frameworks;  
* additional layers with no meaningful responsibility.

Add abstractions only where they represent:

1. a real current responsibility; or  
2. a credible extension point already identified by the requirements.

---

# **35\. Error Handling**

New errors must use the application's existing Problem Details/error response approach.

Required new conditions include:

SHORT\_CODE\_DEACTIVATED  
→ 410 Gone

URL\_DEACTIVATED  
→ 409 Conflict

Existing:

SHORT\_CODE\_NOT\_FOUND  
INVALID\_SHORT\_CODE

should be reused rather than duplicated.

Client errors must not expose:

* SQL;  
* stack traces;  
* Java class names;  
* internal repository details;  
* database schema implementation details.

---

# **36\. Logging**

Successful first-time deactivation may log an informational event such as:

Deactivated short code abc123

Repeated idempotent deactivation may use debug-level logging if needed.

Do not log sensitive or unnecessary destination data such as:

* full destination URL;  
* URL query parameters;  
* request body.

Continue using the application's existing correlation-ID behavior.

---

# **37\. Existing Behavior Must Remain Unchanged**

The following existing functionality must continue to work:

URL creation  
URL validation  
URL normalization  
duplicate detection for active URLs  
SHA-256 short-code generation  
collision resolution  
concurrent creation handling  
redirect behavior for ACTIVE mappings  
click tracking for successful redirects  
analytics retrieval  
analytics failure isolation  
short-code validation  
Problem Details error handling  
correlation IDs  
database uniqueness constraints  
Swagger/OpenAPI documentation  
Flyway migration validation

This is a brownfield change, so regression protection is required.

---

# **38\. Migration Requirements**

Do not edit:

V1  
V2

Create a new forward migration.

The migration must:

* preserve all existing mappings;  
* preserve all click events;  
* mark all pre-existing mappings `ACTIVE`;  
* leave `deactivated_at` null for pre-existing mappings;  
* work with the application's existing H2/PostgreSQL-compatible migration strategy;  
* continue to pass Hibernate schema validation.

---

# **39\. Unit Test Requirements**

Add focused unit tests for lifecycle behavior.

At minimum verify:

active mapping can be deactivated

deactivation sets:  
status \= DEACTIVATED  
deactivatedAt \= clock time

repeated deactivation:  
keeps DEACTIVATED  
preserves original timestamp

deactivating unknown code:  
throws existing not-found exception

malformed code:  
fails before repository lookup

redirect eligibility:  
ACTIVE allowed  
DEACTIVATED denied

duplicate active destination:  
existing behavior preserved

duplicate deactivated destination:  
409-oriented application exception

Tests should validate business behavior rather than implementation details where practical.

---

# **40\. Integration Test Requirements**

Add integration tests that verify the feature through the actual application/database boundary.

Required scenarios:

create → deactivate → 200

deactivate → returned status is DEACTIVATED

deactivate → deactivatedAt populated

deactivate twice → 200 both times

deactivate twice → timestamp unchanged

deactivate unknown code → 404

deactivate malformed code → 400

create → redirect → 302

create → deactivate → redirect → 410

deactivated redirect → no click event

historical analytics remain after deactivation

same destination submitted after deactivation → 409

migration defaults existing mappings to ACTIVE  
---

# **41\. Concurrency Test Requirements**

Add focused concurrency coverage where meaningful.

At minimum validate:

concurrent deactivation requests  
→ final state DEACTIVATED  
→ no inconsistent persisted state

Do not create excessive concurrency infrastructure solely for this feature.

Existing concurrency tests must remain green.

---

# **42\. Cucumber / BDD Requirements**

Add a dedicated brownfield acceptance feature, for example:

url\_deactivation.feature

BDD scenarios should describe externally observable behavior rather than database implementation.

Recommended scenarios:

Feature: URL deactivation

  Scenario: Deactivate an active short URL  
    Given a URL has been shortened  
    When the client deactivates the short URL  
    Then the response status is 200  
    And the short URL status is "DEACTIVATED"

  Scenario: A deactivated URL no longer redirects  
    Given a URL has been shortened  
    And the short URL has been deactivated  
    When the client follows the short URL  
    Then the response status is 410

  Scenario: Deactivation is idempotent  
    Given a URL has been shortened  
    And the short URL has been deactivated  
    When the client deactivates the short URL again  
    Then the response status is 200  
    And the original deactivation time is preserved

  Scenario: Historical analytics remain available  
    Given a URL has been shortened  
    And the short URL has been followed 3 times  
    And the short URL has been deactivated  
    When the client requests analytics for the short URL  
    Then the click count is 3

  Scenario: A deactivated URL cannot be shortened again  
    Given a URL has been shortened  
    And the short URL has been deactivated  
    When the client requests a shortened URL for the same destination  
    Then the response status is 409

Exact wording should remain consistent with the project's existing BDD standards.

---

# **43\. Swagger / OpenAPI Requirements**

The deactivation endpoint shall appear in Swagger/OpenAPI.

Document:

200  
successful or idempotent deactivation

400  
malformed short code

404  
unknown short code

409  
where applicable for lifecycle conflict behavior

500  
unexpected application error

Document the successful response schema:

shortCode  
status  
deactivatedAt

Document the enum values exposed by the API.

---

# **44\. Manual Validation Requirements**

After automated testing passes, manually validate through Swagger or equivalent HTTP requests.

Minimum manual checks:

1\. Create active URL.  
2\. Confirm redirect returns 302\.  
3\. Follow URL several times.  
4\. Confirm analytics count.  
5\. Deactivate URL.  
6\. Confirm 200 response.  
7\. Confirm status \= DEACTIVATED.  
8\. Confirm deactivatedAt populated.  
9\. Deactivate again.  
10\. Confirm 200 and same deactivatedAt.  
11\. Follow deactivated URL.  
12\. Confirm 410\.  
13\. Confirm analytics count did not increase.  
14\. Confirm historical analytics remain available.  
15\. Submit same destination again.  
16\. Confirm 409\.  
17\. Deactivate unknown code.  
18\. Confirm 404\.  
19\. Deactivate malformed code.  
20\. Confirm 400\.

Record these manual checks for the assessment's human-validation documentation.

---

# **45\. Security Limitation**

The current prototype does not implement:

authentication  
authorization  
users  
URL ownership

Therefore, this endpoint does not represent a production-ready public URL-management authorization model.

The limitation shall be documented clearly:

> The prototype demonstrates URL lifecycle behavior, but production exposure of the deactivation endpoint would require authenticated ownership or administrative authorization.

Do not introduce an artificial hard-coded token or partial authentication system solely for this feature.

User management and ownership were already identified as future functionality in the original design. InitialDesignDoc

---

# **46\. Out of Scope**

The brownfield enhancement shall **not** implement:

reactivation  
physical deletion  
soft-delete API  
expiration  
automatic expiration jobs  
administrative suspension  
malicious-link suspension  
user registration  
authentication  
authorization  
URL ownership  
bulk lifecycle operations  
lifecycle history/audit table  
admin UI  
new short code for a deactivated destination  
changing historical analytics  
distributed locking  
event-driven lifecycle processing  
---

# **47\. Acceptance Criteria**

### **AC1 — Successful deactivation**

Given an active short URL, when it is deactivated, then:

HTTP 200  
status \= DEACTIVATED  
deactivatedAt \!= null  
---

### **AC2 — Idempotent deactivation**

Given an already deactivated short URL, when it is deactivated again, then:

HTTP 200  
status remains DEACTIVATED  
original deactivatedAt remains unchanged  
---

### **AC3 — Redirect blocked**

Given a deactivated short URL, when it is followed, then:

HTTP 410  
SHORT\_CODE\_DEACTIVATED  
no redirect occurs  
---

### **AC4 — No analytics increment**

Given a deactivated short URL, when a redirect is attempted, then:

no new ClickEvent is created  
---

### **AC5 — Historical analytics preserved**

Given a short URL with historical clicks, after it is deactivated:

analytics endpoint remains available  
historical count remains unchanged  
---

### **AC6 — Duplicate deactivated destination**

Given a destination already associated with a deactivated mapping, when the same normalized destination is shortened again:

HTTP 409  
URL\_DEACTIVATED

The existing mapping remains deactivated.

No additional mapping is created.

---

### **AC7 — Unknown code**

Given a syntactically valid unknown code, when deactivation is requested:

HTTP 404  
SHORT\_CODE\_NOT\_FOUND  
---

### **AC8 — Invalid code**

Given a malformed code, when deactivation is requested:

HTTP 400  
INVALID\_SHORT\_CODE

Persistence lookup must not occur.

---

### **AC9 — Existing active behavior**

Given an active mapping, existing creation, redirect, and analytics behavior remains unchanged.

---

### **AC10 — Migration safety**

After applying the new migration:

all existing mappings remain present  
all existing mappings are ACTIVE  
all existing ClickEvents remain present  
---

### **AC11 — Concurrent deactivation**

Concurrent deactivation requests shall converge on:

one mapping  
status \= DEACTIVATED  
valid deactivatedAt

without duplicate state or corruption.

---

### **AC12 — Full regression suite**

After implementation:

./mvnw verify

must succeed with:

0 failures  
0 errors

All existing tests must continue to pass in addition to the new lifecycle tests.

---

# **48\. Engineering Constraints for AI-Assisted Implementation**

Before modifying code, the AI shall:

1. inspect the existing production architecture;  
2. inspect current entity/repository/service/controller relationships;  
3. inspect how analytics currently resolves mappings;  
4. inspect existing short-code validation;  
5. inspect current exception handling and Problem Details;  
6. inspect Flyway migration conventions;  
7. inspect existing testing and Cucumber conventions;  
8. identify all impacted files;  
9. identify regression risks;  
10. produce an implementation plan for human review.

The AI must **not begin implementation during the planning step**.

The plan should explain specifically how it will prevent deactivation from accidentally breaking historical analytics.

---

# **49\. Design Philosophy**

The implementation should demonstrate:

existing working system  
        ↓  
understand current responsibilities  
        ↓  
identify lifecycle extension point  
        ↓  
make minimal schema evolution  
        ↓  
encapsulate lifecycle behavior  
        ↓  
separate redirect eligibility from existence  
        ↓  
preserve analytics/history  
        ↓  
validate regressions

The objective is **not** to demonstrate the largest number of design patterns.

The objective is to demonstrate that a new lifecycle requirement can be safely introduced into an existing application using sound engineering judgment.

---

