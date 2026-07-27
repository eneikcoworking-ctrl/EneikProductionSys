---
philosopher_id: "BARCAN-TAG-09_MORAL-DILEMMA:05:uilfrid-sellars"
name_ru: "Уилфрид Селларс"
barcan_tag: "BARCAN-TAG-09_MORAL-DILEMMA"
barcan_role: "MORAL-DILEMMA"
source_file: "BARCAN-TAG-09_MORAL-DILEMMA.md"
source_line: 28
source_principle: "Пространство причин"
publication_anchor: "Empiricism and the Philosophy of Mind - critique of the Myth of the Given"
evidence_status: "role_grounded_with_publication_anchor"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
---

# Уилфрид Селларс

**BARCAN tag:** `BARCAN-TAG-09_MORAL-DILEMMA` - MORAL-DILEMMA
**Role focus:** Value, tradeoffs and waste prevention
**Project role:** Systems Analyst / Technical Product Manager / Technical Lead
**Source principle:** Пространство причин
**Publication anchor:** Empiricism and the Philosophy of Mind - critique of the Myth of the Given
**Project source:** [`BARCAN-TAG-09_MORAL-DILEMMA.md:28`](../../BARCAN-TAG-09_MORAL-DILEMMA.md#L28)

## Interpretation Boundary

- These are programming micro-patterns inspired by the role-file principle and the listed publication anchor.
- They are not claims that the philosopher wrote software-engineering advice.
- Broad patterns that fit more than five philosophers are excluded and kept in the common file.

## 10 Personal Programming Patterns

| # | Personal pattern | Publication-grounded idea | Defect prevented | Agent rule |
|---:|---|---|---|---|
| 1 | `UILFRID_SELLARS_01_NAME_GATE` - Уилфрид Селларс: Пространство причин - Semantic Naming Gate | Empiricism and the Philosophy of Mind - critique of the Myth of the Given | local optimization and wasteful scope; personal failure mode: losing 'Пространство причин' while coding. | Check names against the philosopher's central distinction before code review continues. Apply it only as a Уилфрид Селларс-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 2 | `UILFRID_SELLARS_02_STATE_INVARIANT` - Уилфрид Селларс: Пространство причин - State Invariant Kernel | Empiricism and the Philosophy of Mind - critique of the Myth of the Given | local optimization and wasteful scope; personal failure mode: losing 'Пространство причин' while coding. | Turn the principle into a lifecycle invariant that tests and runtime guards can enforce. Apply it only as a Уилфрид Селларс-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 3 | `UILFRID_SELLARS_03_BOUNDARY_MAP` - Уилфрид Селларс: Пространство причин - Boundary Map | Empiricism and the Philosophy of Mind - critique of the Myth of the Given | local optimization and wasteful scope; personal failure mode: losing 'Пространство причин' while coding. | Draw the exact edge where the principle changes how modules may communicate. Apply it only as a Уилфрид Селларс-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 4 | `UILFRID_SELLARS_04_COUNTEREXAMPLE_TEST` - Уилфрид Селларс: Пространство причин - Counterexample Test | Empiricism and the Philosophy of Mind - critique of the Myth of the Given | local optimization and wasteful scope; personal failure mode: losing 'Пространство причин' while coding. | Create a test designed to break the claim rather than merely demonstrate it. Apply it only as a Уилфрид Селларс-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 5 | `UILFRID_SELLARS_05_DATA_SHAPE` - Уилфрид Селларс: Пространство причин - Data Shape Discipline | Empiricism and the Philosophy of Mind - critique of the Myth of the Given | local optimization and wasteful scope; personal failure mode: losing 'Пространство причин' while coding. | Encode the relevant philosophical distinction in schema, type or value-object structure. Apply it only as a Уилфрид Селларс-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 6 | `UILFRID_SELLARS_06_TRANSITION_GUARD` - Уилфрид Селларс: Пространство причин - Atomic Transition Guard | Empiricism and the Philosophy of Mind - critique of the Myth of the Given | local optimization and wasteful scope; personal failure mode: losing 'Пространство причин' while coding. | Guard state changes with expected state, version or capability context. Apply it only as a Уилфрид Селларс-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 7 | `UILFRID_SELLARS_07_REVIEW_BINARY` - Уилфрид Селларс: Пространство причин - Binary Review Criterion | Empiricism and the Philosophy of Mind - critique of the Myth of the Given | local optimization and wasteful scope; personal failure mode: losing 'Пространство причин' while coding. | Make the role's approval/rejection rule inspectable and non-vague. Apply it only as a Уилфрид Селларс-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 8 | `UILFRID_SELLARS_08_EVIDENCE_TRACE` - Уилфрид Селларс: Пространство причин - Evidence Trace | Empiricism and the Philosophy of Mind - critique of the Myth of the Given | local optimization and wasteful scope; personal failure mode: losing 'Пространство причин' while coding. | Record the observable proof needed for later agents to trust the decision. Apply it only as a Уилфрид Селларс-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 9 | `UILFRID_SELLARS_09_PARALLEL_WORK` - Уилфрид Селларс: Пространство причин - Parallel Conflict Shield | Empiricism and the Philosophy of Mind - critique of the Myth of the Given | local optimization and wasteful scope; personal failure mode: losing 'Пространство причин' while coding. | Prevent two agents from applying incompatible meanings to the same surface. Apply it only as a Уилфрид Селларс-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 10 | `UILFRID_SELLARS_10_RAG_CAPSULE` - Уилфрид Селларс: Пространство причин - RAG Doctrine Capsule | Empiricism and the Philosophy of Mind - critique of the Myth of the Given | local optimization and wasteful scope; personal failure mode: losing 'Пространство причин' while coding. | Store the philosopher-specific rule as a retrievable decision fragment. Apply it only as a Уилфрид Селларс-specific micro-pattern; use the common ACP file for the broad engineering practice. |

## Common Patterns Kept Out

Use the common file for broad engineering practices such as contracts, type-driven design, property-based testing, merge queues, route ownership, generated-artifact authority and containerized toolchains:

- [Common Analytic Programming Patterns](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)
- [Parallel Development Conflict Prevention Charter](../01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md)

## RAG Instruction

When a task belongs to `BARCAN-TAG-09_MORAL-DILEMMA` and needs the individual voice of Уилфрид Селларс, retrieve this file after the common ACP file. Use the 10 patterns as a focused review lens, then attach concrete code evidence before approving work.
