---
philosopher_id: "BARCAN-TAG-05_NECESSARY-IDENTITY:01:derek-parfit"
name_ru: "Дерек Парфит"
barcan_tag: "BARCAN-TAG-05_NECESSARY-IDENTITY"
barcan_role: "NECESSARY-IDENTITY"
source_file: "BARCAN-TAG-05_NECESSARY-IDENTITY.md"
source_line: 38
source_principle: "Принцип психологической непрерывности идентичности"
publication_anchor: "Reasons and Persons - psychological continuity and identity"
evidence_status: "role_grounded_with_publication_anchor"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
---

# Дерек Парфит

**BARCAN tag:** `BARCAN-TAG-05_NECESSARY-IDENTITY` - NECESSARY-IDENTITY
**Role focus:** Runtime identity, reproducibility and incidents
**Project role:** SRE / DevOps / Infrastructure Engineer
**Source principle:** Принцип психологической непрерывности идентичности
**Publication anchor:** Reasons and Persons - psychological continuity and identity
**Project source:** [`BARCAN-TAG-05_NECESSARY-IDENTITY.md:38`](../../BARCAN-TAG-05_NECESSARY-IDENTITY.md#L38)

## Interpretation Boundary

- These are programming micro-patterns inspired by the role-file principle and the listed publication anchor.
- They are not claims that the philosopher wrote software-engineering advice.
- Broad patterns that fit more than five philosophers are excluded and kept in the common file.

## 10 Personal Programming Patterns

| # | Personal pattern | Publication-grounded idea | Defect prevented | Agent rule |
|---:|---|---|---|---|
| 1 | `DEREK_PARFIT_01_NAME_GATE` - Дерек Парфит: Принцип психологической непрерывности идентичности - Semantic Naming Gate | Reasons and Persons - psychological continuity and identity | unreproducible runtime identity and weak RCA; personal failure mode: losing 'Принцип психологической непрерывности идентичности' while coding. | Check names against the philosopher's central distinction before code review continues. Apply it only as a Дерек Парфит-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 2 | `DEREK_PARFIT_02_STATE_INVARIANT` - Дерек Парфит: Принцип психологической непрерывности идентичности - State Invariant Kernel | Reasons and Persons - psychological continuity and identity | unreproducible runtime identity and weak RCA; personal failure mode: losing 'Принцип психологической непрерывности идентичности' while coding. | Turn the principle into a lifecycle invariant that tests and runtime guards can enforce. Apply it only as a Дерек Парфит-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 3 | `DEREK_PARFIT_03_BOUNDARY_MAP` - Дерек Парфит: Принцип психологической непрерывности идентичности - Boundary Map | Reasons and Persons - psychological continuity and identity | unreproducible runtime identity and weak RCA; personal failure mode: losing 'Принцип психологической непрерывности идентичности' while coding. | Draw the exact edge where the principle changes how modules may communicate. Apply it only as a Дерек Парфит-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 4 | `DEREK_PARFIT_04_COUNTEREXAMPLE_TEST` - Дерек Парфит: Принцип психологической непрерывности идентичности - Counterexample Test | Reasons and Persons - psychological continuity and identity | unreproducible runtime identity and weak RCA; personal failure mode: losing 'Принцип психологической непрерывности идентичности' while coding. | Create a test designed to break the claim rather than merely demonstrate it. Apply it only as a Дерек Парфит-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 5 | `DEREK_PARFIT_05_DATA_SHAPE` - Дерек Парфит: Принцип психологической непрерывности идентичности - Data Shape Discipline | Reasons and Persons - psychological continuity and identity | unreproducible runtime identity and weak RCA; personal failure mode: losing 'Принцип психологической непрерывности идентичности' while coding. | Encode the relevant philosophical distinction in schema, type or value-object structure. Apply it only as a Дерек Парфит-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 6 | `DEREK_PARFIT_06_TRANSITION_GUARD` - Дерек Парфит: Принцип психологической непрерывности идентичности - Atomic Transition Guard | Reasons and Persons - psychological continuity and identity | unreproducible runtime identity and weak RCA; personal failure mode: losing 'Принцип психологической непрерывности идентичности' while coding. | Guard state changes with expected state, version or capability context. Apply it only as a Дерек Парфит-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 7 | `DEREK_PARFIT_07_REVIEW_BINARY` - Дерек Парфит: Принцип психологической непрерывности идентичности - Binary Review Criterion | Reasons and Persons - psychological continuity and identity | unreproducible runtime identity and weak RCA; personal failure mode: losing 'Принцип психологической непрерывности идентичности' while coding. | Make the role's approval/rejection rule inspectable and non-vague. Apply it only as a Дерек Парфит-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 8 | `DEREK_PARFIT_08_EVIDENCE_TRACE` - Дерек Парфит: Принцип психологической непрерывности идентичности - Evidence Trace | Reasons and Persons - psychological continuity and identity | unreproducible runtime identity and weak RCA; personal failure mode: losing 'Принцип психологической непрерывности идентичности' while coding. | Record the observable proof needed for later agents to trust the decision. Apply it only as a Дерек Парфит-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 9 | `DEREK_PARFIT_09_PARALLEL_WORK` - Дерек Парфит: Принцип психологической непрерывности идентичности - Parallel Conflict Shield | Reasons and Persons - psychological continuity and identity | unreproducible runtime identity and weak RCA; personal failure mode: losing 'Принцип психологической непрерывности идентичности' while coding. | Prevent two agents from applying incompatible meanings to the same surface. Apply it only as a Дерек Парфит-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 10 | `DEREK_PARFIT_10_RAG_CAPSULE` - Дерек Парфит: Принцип психологической непрерывности идентичности - RAG Doctrine Capsule | Reasons and Persons - psychological continuity and identity | unreproducible runtime identity and weak RCA; personal failure mode: losing 'Принцип психологической непрерывности идентичности' while coding. | Store the philosopher-specific rule as a retrievable decision fragment. Apply it only as a Дерек Парфит-specific micro-pattern; use the common ACP file for the broad engineering practice. |

## Common Patterns Kept Out

Use the common file for broad engineering practices such as contracts, type-driven design, property-based testing, merge queues, route ownership, generated-artifact authority and containerized toolchains:

- [Common Analytic Programming Patterns](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)
- [Parallel Development Conflict Prevention Charter](../01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md)

## RAG Instruction

When a task belongs to `BARCAN-TAG-05_NECESSARY-IDENTITY` and needs the individual voice of Дерек Парфит, retrieve this file after the common ACP file. Use the 10 patterns as a focused review lens, then attach concrete code evidence before approving work.
