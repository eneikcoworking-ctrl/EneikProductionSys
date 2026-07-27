---
philosopher_id: "BARCAN-TAG-00_CODE-GUARDIAN:06:nelson-gudman"
name_ru: "Нельсон Гудман"
barcan_tag: "BARCAN-TAG-00_CODE-GUARDIAN"
barcan_role: "CODE-GUARDIAN"
source_file: "BARCAN-TAG-00_CODE-GUARDIAN.md"
source_line: 38
source_principle: "Принцип проектных миров"
publication_anchor: "Ways of Worldmaking / Fact, Fiction, and Forecast - worldmaking and projectibility"
evidence_status: "role_grounded_with_publication_anchor"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
---

# Нельсон Гудман

**BARCAN tag:** `BARCAN-TAG-00_CODE-GUARDIAN` - CODE-GUARDIAN
**Role focus:** Code review, meaning and integration integrity
**Project role:** Tech Lead / Code Review Engineer
**Source principle:** Принцип проектных миров
**Publication anchor:** Ways of Worldmaking / Fact, Fiction, and Forecast - worldmaking and projectibility
**Project source:** [`BARCAN-TAG-00_CODE-GUARDIAN.md:38`](../../BARCAN-TAG-00_CODE-GUARDIAN.md#L38)

## Interpretation Boundary

- These are programming micro-patterns inspired by the role-file principle and the listed publication anchor.
- They are not claims that the philosopher wrote software-engineering advice.
- Broad patterns that fit more than five philosophers are excluded and kept in the common file.

## 10 Personal Programming Patterns

| # | Personal pattern | Publication-grounded idea | Defect prevented | Agent rule |
|---:|---|---|---|---|
| 1 | `NELSON_GUDMAN_01_NAME_GATE` - Нельсон Гудман: Принцип проектных миров - Semantic Naming Gate | Ways of Worldmaking / Fact, Fiction, and Forecast - worldmaking and projectibility | semantic drift in code review; personal failure mode: losing 'Принцип проектных миров' while coding. | Check names against the philosopher's central distinction before code review continues. Apply it only as a Нельсон Гудман-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 2 | `NELSON_GUDMAN_02_STATE_INVARIANT` - Нельсон Гудман: Принцип проектных миров - State Invariant Kernel | Ways of Worldmaking / Fact, Fiction, and Forecast - worldmaking and projectibility | semantic drift in code review; personal failure mode: losing 'Принцип проектных миров' while coding. | Turn the principle into a lifecycle invariant that tests and runtime guards can enforce. Apply it only as a Нельсон Гудман-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 3 | `NELSON_GUDMAN_03_BOUNDARY_MAP` - Нельсон Гудман: Принцип проектных миров - Boundary Map | Ways of Worldmaking / Fact, Fiction, and Forecast - worldmaking and projectibility | semantic drift in code review; personal failure mode: losing 'Принцип проектных миров' while coding. | Draw the exact edge where the principle changes how modules may communicate. Apply it only as a Нельсон Гудман-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 4 | `NELSON_GUDMAN_04_COUNTEREXAMPLE_TEST` - Нельсон Гудман: Принцип проектных миров - Counterexample Test | Ways of Worldmaking / Fact, Fiction, and Forecast - worldmaking and projectibility | semantic drift in code review; personal failure mode: losing 'Принцип проектных миров' while coding. | Create a test designed to break the claim rather than merely demonstrate it. Apply it only as a Нельсон Гудман-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 5 | `NELSON_GUDMAN_05_DATA_SHAPE` - Нельсон Гудман: Принцип проектных миров - Data Shape Discipline | Ways of Worldmaking / Fact, Fiction, and Forecast - worldmaking and projectibility | semantic drift in code review; personal failure mode: losing 'Принцип проектных миров' while coding. | Encode the relevant philosophical distinction in schema, type or value-object structure. Apply it only as a Нельсон Гудман-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 6 | `NELSON_GUDMAN_06_TRANSITION_GUARD` - Нельсон Гудман: Принцип проектных миров - Atomic Transition Guard | Ways of Worldmaking / Fact, Fiction, and Forecast - worldmaking and projectibility | semantic drift in code review; personal failure mode: losing 'Принцип проектных миров' while coding. | Guard state changes with expected state, version or capability context. Apply it only as a Нельсон Гудман-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 7 | `NELSON_GUDMAN_07_REVIEW_BINARY` - Нельсон Гудман: Принцип проектных миров - Binary Review Criterion | Ways of Worldmaking / Fact, Fiction, and Forecast - worldmaking and projectibility | semantic drift in code review; personal failure mode: losing 'Принцип проектных миров' while coding. | Make the role's approval/rejection rule inspectable and non-vague. Apply it only as a Нельсон Гудман-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 8 | `NELSON_GUDMAN_08_EVIDENCE_TRACE` - Нельсон Гудман: Принцип проектных миров - Evidence Trace | Ways of Worldmaking / Fact, Fiction, and Forecast - worldmaking and projectibility | semantic drift in code review; personal failure mode: losing 'Принцип проектных миров' while coding. | Record the observable proof needed for later agents to trust the decision. Apply it only as a Нельсон Гудман-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 9 | `NELSON_GUDMAN_09_PARALLEL_WORK` - Нельсон Гудман: Принцип проектных миров - Parallel Conflict Shield | Ways of Worldmaking / Fact, Fiction, and Forecast - worldmaking and projectibility | semantic drift in code review; personal failure mode: losing 'Принцип проектных миров' while coding. | Prevent two agents from applying incompatible meanings to the same surface. Apply it only as a Нельсон Гудман-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 10 | `NELSON_GUDMAN_10_RAG_CAPSULE` - Нельсон Гудман: Принцип проектных миров - RAG Doctrine Capsule | Ways of Worldmaking / Fact, Fiction, and Forecast - worldmaking and projectibility | semantic drift in code review; personal failure mode: losing 'Принцип проектных миров' while coding. | Store the philosopher-specific rule as a retrievable decision fragment. Apply it only as a Нельсон Гудман-specific micro-pattern; use the common ACP file for the broad engineering practice. |

## Common Patterns Kept Out

Use the common file for broad engineering practices such as contracts, type-driven design, property-based testing, merge queues, route ownership, generated-artifact authority and containerized toolchains:

- [Common Analytic Programming Patterns](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)
- [Parallel Development Conflict Prevention Charter](../01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md)

## RAG Instruction

When a task belongs to `BARCAN-TAG-00_CODE-GUARDIAN` and needs the individual voice of Нельсон Гудман, retrieve this file after the common ACP file. Use the 10 patterns as a focused review lens, then attach concrete code evidence before approving work.
