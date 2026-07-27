---
philosopher_id: "BARCAN-TAG-00_CODE-GUARDIAN:05:pol-grays"
name_ru: "Пол Грайс"
barcan_tag: "BARCAN-TAG-00_CODE-GUARDIAN"
barcan_role: "CODE-GUARDIAN"
source_file: "BARCAN-TAG-00_CODE-GUARDIAN.md"
source_line: 37
source_principle: "Принцип кооперативного дискурса"
publication_anchor: "Logic and Conversation - cooperative principle and conversational maxims"
evidence_status: "role_grounded_with_publication_anchor"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
---

# Пол Грайс

**BARCAN tag:** `BARCAN-TAG-00_CODE-GUARDIAN` - CODE-GUARDIAN
**Role focus:** Code review, meaning and integration integrity
**Project role:** Tech Lead / Code Review Engineer
**Source principle:** Принцип кооперативного дискурса
**Publication anchor:** Logic and Conversation - cooperative principle and conversational maxims
**Project source:** [`BARCAN-TAG-00_CODE-GUARDIAN.md:37`](../../BARCAN-TAG-00_CODE-GUARDIAN.md#L37)

## Interpretation Boundary

- These are programming micro-patterns inspired by the role-file principle and the listed publication anchor.
- They are not claims that the philosopher wrote software-engineering advice.
- Broad patterns that fit more than five philosophers are excluded and kept in the common file.

## 10 Personal Programming Patterns

| # | Personal pattern | Publication-grounded idea | Defect prevented | Agent rule |
|---:|---|---|---|---|
| 1 | `POL_GRAYS_01_NAME_GATE` - Пол Грайс: Принцип кооперативного дискурса - Semantic Naming Gate | Logic and Conversation - cooperative principle and conversational maxims | semantic drift in code review; personal failure mode: losing 'Принцип кооперативного дискурса' while coding. | Check names against the philosopher's central distinction before code review continues. Apply it only as a Пол Грайс-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 2 | `POL_GRAYS_02_STATE_INVARIANT` - Пол Грайс: Принцип кооперативного дискурса - State Invariant Kernel | Logic and Conversation - cooperative principle and conversational maxims | semantic drift in code review; personal failure mode: losing 'Принцип кооперативного дискурса' while coding. | Turn the principle into a lifecycle invariant that tests and runtime guards can enforce. Apply it only as a Пол Грайс-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 3 | `POL_GRAYS_03_BOUNDARY_MAP` - Пол Грайс: Принцип кооперативного дискурса - Boundary Map | Logic and Conversation - cooperative principle and conversational maxims | semantic drift in code review; personal failure mode: losing 'Принцип кооперативного дискурса' while coding. | Draw the exact edge where the principle changes how modules may communicate. Apply it only as a Пол Грайс-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 4 | `POL_GRAYS_04_COUNTEREXAMPLE_TEST` - Пол Грайс: Принцип кооперативного дискурса - Counterexample Test | Logic and Conversation - cooperative principle and conversational maxims | semantic drift in code review; personal failure mode: losing 'Принцип кооперативного дискурса' while coding. | Create a test designed to break the claim rather than merely demonstrate it. Apply it only as a Пол Грайс-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 5 | `POL_GRAYS_05_DATA_SHAPE` - Пол Грайс: Принцип кооперативного дискурса - Data Shape Discipline | Logic and Conversation - cooperative principle and conversational maxims | semantic drift in code review; personal failure mode: losing 'Принцип кооперативного дискурса' while coding. | Encode the relevant philosophical distinction in schema, type or value-object structure. Apply it only as a Пол Грайс-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 6 | `POL_GRAYS_06_TRANSITION_GUARD` - Пол Грайс: Принцип кооперативного дискурса - Atomic Transition Guard | Logic and Conversation - cooperative principle and conversational maxims | semantic drift in code review; personal failure mode: losing 'Принцип кооперативного дискурса' while coding. | Guard state changes with expected state, version or capability context. Apply it only as a Пол Грайс-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 7 | `POL_GRAYS_07_REVIEW_BINARY` - Пол Грайс: Принцип кооперативного дискурса - Binary Review Criterion | Logic and Conversation - cooperative principle and conversational maxims | semantic drift in code review; personal failure mode: losing 'Принцип кооперативного дискурса' while coding. | Make the role's approval/rejection rule inspectable and non-vague. Apply it only as a Пол Грайс-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 8 | `POL_GRAYS_08_EVIDENCE_TRACE` - Пол Грайс: Принцип кооперативного дискурса - Evidence Trace | Logic and Conversation - cooperative principle and conversational maxims | semantic drift in code review; personal failure mode: losing 'Принцип кооперативного дискурса' while coding. | Record the observable proof needed for later agents to trust the decision. Apply it only as a Пол Грайс-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 9 | `POL_GRAYS_09_PARALLEL_WORK` - Пол Грайс: Принцип кооперативного дискурса - Parallel Conflict Shield | Logic and Conversation - cooperative principle and conversational maxims | semantic drift in code review; personal failure mode: losing 'Принцип кооперативного дискурса' while coding. | Prevent two agents from applying incompatible meanings to the same surface. Apply it only as a Пол Грайс-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 10 | `POL_GRAYS_10_RAG_CAPSULE` - Пол Грайс: Принцип кооперативного дискурса - RAG Doctrine Capsule | Logic and Conversation - cooperative principle and conversational maxims | semantic drift in code review; personal failure mode: losing 'Принцип кооперативного дискурса' while coding. | Store the philosopher-specific rule as a retrievable decision fragment. Apply it only as a Пол Грайс-specific micro-pattern; use the common ACP file for the broad engineering practice. |

## Common Patterns Kept Out

Use the common file for broad engineering practices such as contracts, type-driven design, property-based testing, merge queues, route ownership, generated-artifact authority and containerized toolchains:

- [Common Analytic Programming Patterns](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)
- [Parallel Development Conflict Prevention Charter](../01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md)

## RAG Instruction

When a task belongs to `BARCAN-TAG-00_CODE-GUARDIAN` and needs the individual voice of Пол Грайс, retrieve this file after the common ACP file. Use the 10 patterns as a focused review lens, then attach concrete code evidence before approving work.
