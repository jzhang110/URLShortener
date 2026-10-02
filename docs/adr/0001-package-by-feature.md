# ADR 0001: Hybrid package-by-feature

**Status:** Accepted. Amended: features now contain responsibility subpackages.

## Problem
The command template suggests global layer packages (`controller`, `service`, `repository`, ...). With three small features, that would scatter each feature across the whole tree. The opposite, one flat package per feature, got hard to scan as the `url` feature grew to about 18 classes.

## Alternatives
1. Package by layer (global `controller/service/repository/model`).
2. Flat package by feature (`url`, `redirect`, `analytics`, `common`). This was the original choice.
3. **Hybrid:** features at the top level, with responsibility subpackages (`api`, `api/dto`, `service`, `domain`, `repository`, `generation`) inside each feature.
4. Hexagonal modules (ports/adapters per feature).

## Decision
Option 3.
- Top-level feature boundaries stay the same: `url`, `analytics`, `redirect`, `common`.
- `redirect` is its own feature because it orchestrates `url` and `analytics`. Putting it inside either would create a package cycle.
- Inside each feature, dependencies point inward: `api → service → repository/generation/domain`.
- Services never depend on API DTOs.
- Persisted entities live in `domain`.
- `common` is split into `error`, `config`, `filter` and `logging`.

The layout is shown in `docs/architecture.md` §4.

> **Note (2026-10-01):** a fifth top-level feature, `ui` (`api/UiController`, `web/UiSecurityHeadersFilter`), was added for the demo UI ([ADR 0010](0010-minimal-static-ui.md)). It follows the same layout and depends on no other feature.

## Tradeoffs
- (+) Feature cohesion is preserved: a new feature such as `users` is a new top-level package. Classes are quick to find by role, and the inward dependency rule is visible in imports.
- (−) Classes used across subpackages must be `public`: the `UrlMapping` and `ClickEvent` constructors, `Sha256`, and the DTO factory methods. Package-private is still the default where a class stays inside its subpackage, for example the controllers, `Sha256ShortCodeGenerator` and `SelfReferencePolicy`.
- Option 4 remains a possible next step. The feature packages are already the module seams.
