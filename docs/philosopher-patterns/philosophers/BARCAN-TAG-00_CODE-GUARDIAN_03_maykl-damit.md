---
philosopher_id: "BARCAN-TAG-00_CODE-GUARDIAN:03:maykl-damit"
name_ru: "Майкл Дамит"
barcan_tag: "BARCAN-TAG-00_CODE-GUARDIAN"
barcan_role: "CODE-GUARDIAN"
source_file: "BARCAN-TAG-00_CODE-GUARDIAN.md"
source_line: 35
source_principle: "Принцип верификационизма значений"
publication_anchor: "Truth and Other Enigmas / Frege: Philosophy of Language - verificationist theory of meaning"
evidence_status: "role_grounded_with_publication_anchor"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
---

# Майкл Дамит

**BARCAN tag:** `BARCAN-TAG-00_CODE-GUARDIAN` - CODE-GUARDIAN
**Role focus:** Code review, meaning and integration integrity
**Project role:** Tech Lead / Code Review Engineer
**Source principle:** Принцип верификационизма значений
**Publication anchor:** Truth and Other Enigmas / Frege: Philosophy of Language - verificationist theory of meaning
**Project source:** [`BARCAN-TAG-00_CODE-GUARDIAN.md:35`](../../BARCAN-TAG-00_CODE-GUARDIAN.md#L35)

## Interpretation Boundary

- These are programming micro-patterns inspired by the role-file principle and the listed publication anchor.
- They are not claims that the philosopher wrote software-engineering advice.
- Broad patterns that fit more than five philosophers are excluded and kept in the common file.

## 10 Personal Programming Patterns

| # | Personal pattern | Publication-grounded idea | Defect prevented | Agent rule |
|---:|---|---|---|---|
| 1 | `MAYKL_DAMIT_01_NAME_GATE` - Майкл Дамит: Принцип верификационизма значений - Semantic Naming Gate | Truth and Other Enigmas / Frege: Philosophy of Language - verificationist theory of meaning | semantic drift in code review; personal failure mode: losing 'Принцип верификационизма значений' while coding. | Check names against the philosopher's central distinction before code review continues. Apply it only as a Майкл Дамит-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 2 | `MAYKL_DAMIT_02_STATE_INVARIANT` - Майкл Дамит: Принцип верификационизма значений - State Invariant Kernel | Truth and Other Enigmas / Frege: Philosophy of Language - verificationist theory of meaning | semantic drift in code review; personal failure mode: losing 'Принцип верификационизма значений' while coding. | Turn the principle into a lifecycle invariant that tests and runtime guards can enforce. Apply it only as a Майкл Дамит-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 3 | `MAYKL_DAMIT_03_BOUNDARY_MAP` - Майкл Дамит: Принцип верификационизма значений - Boundary Map | Truth and Other Enigmas / Frege: Philosophy of Language - verificationist theory of meaning | semantic drift in code review; personal failure mode: losing 'Принцип верификационизма значений' while coding. | Draw the exact edge where the principle changes how modules may communicate. Apply it only as a Майкл Дамит-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 4 | `MAYKL_DAMIT_04_COUNTEREXAMPLE_TEST` - Майкл Дамит: Принцип верификационизма значений - Counterexample Test | Truth and Other Enigmas / Frege: Philosophy of Language - verificationist theory of meaning | semantic drift in code review; personal failure mode: losing 'Принцип верификационизма значений' while coding. | Create a test designed to break the claim rather than merely demonstrate it. Apply it only as a Майкл Дамит-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 5 | `MAYKL_DAMIT_05_DATA_SHAPE` - Майкл Дамит: Принцип верификационизма значений - Data Shape Discipline | Truth and Other Enigmas / Frege: Philosophy of Language - verificationist theory of meaning | semantic drift in code review; personal failure mode: losing 'Принцип верификационизма значений' while coding. | Encode the relevant philosophical distinction in schema, type or value-object structure. Apply it only as a Майкл Дамит-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 6 | `MAYKL_DAMIT_06_TRANSITION_GUARD` - Майкл Дамит: Принцип верификационизма значений - Atomic Transition Guard | Truth and Other Enigmas / Frege: Philosophy of Language - verificationist theory of meaning | semantic drift in code review; personal failure mode: losing 'Принцип верификационизма значений' while coding. | Guard state changes with expected state, version or capability context. Apply it only as a Майкл Дамит-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 7 | `MAYKL_DAMIT_07_REVIEW_BINARY` - Майкл Дамит: Принцип верификационизма значений - Binary Review Criterion | Truth and Other Enigmas / Frege: Philosophy of Language - verificationist theory of meaning | semantic drift in code review; personal failure mode: losing 'Принцип верификационизма значений' while coding. | Make the role's approval/rejection rule inspectable and non-vague. Apply it only as a Майкл Дамит-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 8 | `MAYKL_DAMIT_08_EVIDENCE_TRACE` - Майкл Дамит: Принцип верификационизма значений - Evidence Trace | Truth and Other Enigmas / Frege: Philosophy of Language - verificationist theory of meaning | semantic drift in code review; personal failure mode: losing 'Принцип верификационизма значений' while coding. | Record the observable proof needed for later agents to trust the decision. Apply it only as a Майкл Дамит-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 9 | `MAYKL_DAMIT_09_PARALLEL_WORK` - Майкл Дамит: Принцип верификационизма значений - Parallel Conflict Shield | Truth and Other Enigmas / Frege: Philosophy of Language - verificationist theory of meaning | semantic drift in code review; personal failure mode: losing 'Принцип верификационизма значений' while coding. | Prevent two agents from applying incompatible meanings to the same surface. Apply it only as a Майкл Дамит-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 10 | `MAYKL_DAMIT_10_RAG_CAPSULE` - Майкл Дамит: Принцип верификационизма значений - RAG Doctrine Capsule | Truth and Other Enigmas / Frege: Philosophy of Language - verificationist theory of meaning | semantic drift in code review; personal failure mode: losing 'Принцип верификационизма значений' while coding. | Store the philosopher-specific rule as a retrievable decision fragment. Apply it only as a Майкл Дамит-specific micro-pattern; use the common ACP file for the broad engineering practice. |

## Common Patterns Kept Out

Use the common file for broad engineering practices such as contracts, type-driven design, property-based testing, merge queues, route ownership, generated-artifact authority and containerized toolchains:

- [Common Analytic Programming Patterns](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)
- [Parallel Development Conflict Prevention Charter](../01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md)

## RAG Instruction

When a task belongs to `BARCAN-TAG-00_CODE-GUARDIAN` and needs the individual voice of Майкл Дамит, retrieve this file after the common ACP file. Use the 10 patterns as a focused review lens, then attach concrete code evidence before approving work.
