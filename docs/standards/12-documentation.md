# Documentation expectations

- **Explain *why*, not *what*.** The reader is an engineer who has the code and needs the reasoning.
- **Where things live:**

  | Document | Contents |
  |---|---|
  | `README.md` | What it is, how to build, run, test and use it (one screen to first request) |
  | `docs/engineering-summary.md` | Assessment summary: scenarios, AI usage and ownership, validation, risks |
  | `docs/manual-validation-*.md`, `../ai-workflow/ambiguous-requirement-url-identity.md` | Validation evidence and the ambiguity write-up. Manual (engineer) and AI-executed checks are labelled separately |
  | `docs/ai-workflow/` | Supporting evidence: prompts, plan iterations, review logs |
  | `docs/architecture.md` | Requirements, business rules, normalization rules, domain model, packages, workflows (mermaid), API, errors, concurrency analysis, threat model, logging, testing, assumptions |
  | `docs/persistence.md` | Schema strategy, entity ↔ table mapping, constraints and why, transactions, H2 vs PostgreSQL |
  | `docs/adr/NNNN-title.md` | One decision each: Status, Problem, Alternatives, Decision, Tradeoffs |
  | `docs/standards/` | Engineering rules for this repo |
  | `CLAUDE.md` | Entry point for AI-assisted work; links to the above |
  | OpenAPI annotations | The API contract, generated into Swagger |

- **Write an ADR** when a decision affects architecture, persistence, concurrency, security or the API, or when it deviates from `InitialDesignDoc.md`. Never silently replace intended architecture: document the concern, the impact, the alternative and the tradeoffs.
- **When a change affects documented behaviour, update the relevant documentation before considering the change complete.** A stale doc is a defect.
- **Record assumptions** in `docs/architecture.md` §12 when requirements are ambiguous.
- Don't document trivial implementation details or repeat the code.
- Code comments follow the same rule: invariants, reasons, and ADR references.
