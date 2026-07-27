---
philosopher_id: "BARCAN-TAG-05_NECESSARY-IDENTITY:06:ketrin-houli"
name_ru: "Кэтрин Хоули"
barcan_tag: "BARCAN-TAG-05_NECESSARY-IDENTITY"
barcan_role: "NECESSARY-IDENTITY"
source_file: "BARCAN-TAG-05_NECESSARY-IDENTITY.md"
source_line: 43
source_principle: "Принцип персистенции через изменение свойств"
publication_anchor: "How Things Persist - persistence through change"
evidence_status: "role_grounded_with_publication_anchor"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
---

# Кэтрин Хоули

**BARCAN tag:** `BARCAN-TAG-05_NECESSARY-IDENTITY` - NECESSARY-IDENTITY
**Role focus:** Runtime identity, reproducibility and incidents
**Project role:** SRE / DevOps / Infrastructure Engineer
**Source principle:** Принцип персистенции через изменение свойств
**Publication anchor:** How Things Persist - persistence through change
**Project source:** [`BARCAN-TAG-05_NECESSARY-IDENTITY.md:43`](../../BARCAN-TAG-05_NECESSARY-IDENTITY.md#L43)

## Interpretation Boundary

- These are programming micro-patterns inspired by the role-file principle and the listed publication anchor.
- They are not claims that the philosopher wrote software-engineering advice.
- Broad patterns that fit more than five philosophers are excluded and kept in the common file.

## 10 Personal Programming Patterns

| # | Personal pattern | Publication-grounded idea | Defect prevented | Agent rule |
|---:|---|---|---|---|
| 1 | `KETRIN_HOULI_01_NAME_GATE` - Кэтрин Хоули: Принцип персистенции через изменение свойств - Semantic Naming Gate | How Things Persist - persistence through change | unreproducible runtime identity and weak RCA; personal failure mode: losing 'Принцип персистенции через изменение свойств' while coding. | Check names against the philosopher's central distinction before code review continues. Apply it only as a Кэтрин Хоули-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 2 | `KETRIN_HOULI_02_STATE_INVARIANT` - Кэтрин Хоули: Принцип персистенции через изменение свойств - State Invariant Kernel | How Things Persist - persistence through change | unreproducible runtime identity and weak RCA; personal failure mode: losing 'Принцип персистенции через изменение свойств' while coding. | Turn the principle into a lifecycle invariant that tests and runtime guards can enforce. Apply it only as a Кэтрин Хоули-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 3 | `KETRIN_HOULI_03_BOUNDARY_MAP` - Кэтрин Хоули: Принцип персистенции через изменение свойств - Boundary Map | How Things Persist - persistence through change | unreproducible runtime identity and weak RCA; personal failure mode: losing 'Принцип персистенции через изменение свойств' while coding. | Draw the exact edge where the principle changes how modules may communicate. Apply it only as a Кэтрин Хоули-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 4 | `KETRIN_HOULI_04_COUNTEREXAMPLE_TEST` - Кэтрин Хоули: Принцип персистенции через изменение свойств - Counterexample Test | How Things Persist - persistence through change | unreproducible runtime identity and weak RCA; personal failure mode: losing 'Принцип персистенции через изменение свойств' while coding. | Create a test designed to break the claim rather than merely demonstrate it. Apply it only as a Кэтрин Хоули-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 5 | `KETRIN_HOULI_05_DATA_SHAPE` - Кэтрин Хоули: Принцип персистенции через изменение свойств - Data Shape Discipline | How Things Persist - persistence through change | unreproducible runtime identity and weak RCA; personal failure mode: losing 'Принцип персистенции через изменение свойств' while coding. | Encode the relevant philosophical distinction in schema, type or value-object structure. Apply it only as a Кэтрин Хоули-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 6 | `KETRIN_HOULI_06_TRANSITION_GUARD` - Кэтрин Хоули: Принцип персистенции через изменение свойств - Atomic Transition Guard | How Things Persist - persistence through change | unreproducible runtime identity and weak RCA; personal failure mode: losing 'Принцип персистенции через изменение свойств' while coding. | Guard state changes with expected state, version or capability context. Apply it only as a Кэтрин Хоули-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 7 | `KETRIN_HOULI_07_REVIEW_BINARY` - Кэтрин Хоули: Принцип персистенции через изменение свойств - Binary Review Criterion | How Things Persist - persistence through change | unreproducible runtime identity and weak RCA; personal failure mode: losing 'Принцип персистенции через изменение свойств' while coding. | Make the role's approval/rejection rule inspectable and non-vague. Apply it only as a Кэтрин Хоули-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 8 | `KETRIN_HOULI_08_EVIDENCE_TRACE` - Кэтрин Хоули: Принцип персистенции через изменение свойств - Evidence Trace | How Things Persist - persistence through change | unreproducible runtime identity and weak RCA; personal failure mode: losing 'Принцип персистенции через изменение свойств' while coding. | Record the observable proof needed for later agents to trust the decision. Apply it only as a Кэтрин Хоули-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 9 | `KETRIN_HOULI_09_PARALLEL_WORK` - Кэтрин Хоули: Принцип персистенции через изменение свойств - Parallel Conflict Shield | How Things Persist - persistence through change | unreproducible runtime identity and weak RCA; personal failure mode: losing 'Принцип персистенции через изменение свойств' while coding. | Prevent two agents from applying incompatible meanings to the same surface. Apply it only as a Кэтрин Хоули-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 10 | `KETRIN_HOULI_10_RAG_CAPSULE` - Кэтрин Хоули: Принцип персистенции через изменение свойств - RAG Doctrine Capsule | How Things Persist - persistence through change | unreproducible runtime identity and weak RCA; personal failure mode: losing 'Принцип персистенции через изменение свойств' while coding. | Store the philosopher-specific rule as a retrievable decision fragment. Apply it only as a Кэтрин Хоули-specific micro-pattern; use the common ACP file for the broad engineering practice. |

## Common Patterns Kept Out

Use the common file for broad engineering practices such as contracts, type-driven design, property-based testing, merge queues, route ownership, generated-artifact authority and containerized toolchains:

- [Common Analytic Programming Patterns](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)
- [Parallel Development Conflict Prevention Charter](../01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md)

## RAG Instruction

When a task belongs to `BARCAN-TAG-05_NECESSARY-IDENTITY` and needs the individual voice of Кэтрин Хоули, retrieve this file after the common ACP file. Use the 10 patterns as a focused review lens, then attach concrete code evidence before approving work.
