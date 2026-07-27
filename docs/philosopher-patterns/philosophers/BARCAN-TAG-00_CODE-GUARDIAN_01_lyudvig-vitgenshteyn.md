---
philosopher_id: "BARCAN-TAG-00_CODE-GUARDIAN:01:lyudvig-vitgenshteyn"
name_ru: "Людвиг Витгенштейн"
barcan_tag: "BARCAN-TAG-00_CODE-GUARDIAN"
barcan_role: "CODE-GUARDIAN"
source_file: "BARCAN-TAG-00_CODE-GUARDIAN.md"
source_line: 33
source_principle: "Принцип языковых игр"
publication_anchor: "Philosophical Investigations - language-games, meaning as use, private-language argument"
evidence_status: "role_grounded_with_publication_anchor"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
---

# Людвиг Витгенштейн

**BARCAN tag:** `BARCAN-TAG-00_CODE-GUARDIAN` - CODE-GUARDIAN
**Role focus:** Code review, meaning and integration integrity
**Project role:** Tech Lead / Code Review Engineer
**Source principle:** Принцип языковых игр
**Publication anchor:** Philosophical Investigations - language-games, meaning as use, private-language argument
**Project source:** [`BARCAN-TAG-00_CODE-GUARDIAN.md:33`](../../BARCAN-TAG-00_CODE-GUARDIAN.md#L33)

## Interpretation Boundary

- These are programming micro-patterns inspired by the role-file principle and the listed publication anchor.
- They are not claims that the philosopher wrote software-engineering advice.
- Broad patterns that fit more than five philosophers are excluded and kept in the common file.

## 10 Personal Programming Patterns

| # | Personal pattern | Publication-grounded idea | Defect prevented | Agent rule |
|---:|---|---|---|---|
| 1 | `LYUDVIG_VITGENSHTEYN_01_NAME_GATE` - Людвиг Витгенштейн: Принцип языковых игр - Semantic Naming Gate | Philosophical Investigations - language-games, meaning as use, private-language argument | semantic drift in code review; personal failure mode: losing 'Принцип языковых игр' while coding. | Check names against the philosopher's central distinction before code review continues. Apply it only as a Людвиг Витгенштейн-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 2 | `LYUDVIG_VITGENSHTEYN_02_STATE_INVARIANT` - Людвиг Витгенштейн: Принцип языковых игр - State Invariant Kernel | Philosophical Investigations - language-games, meaning as use, private-language argument | semantic drift in code review; personal failure mode: losing 'Принцип языковых игр' while coding. | Turn the principle into a lifecycle invariant that tests and runtime guards can enforce. Apply it only as a Людвиг Витгенштейн-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 3 | `LYUDVIG_VITGENSHTEYN_03_BOUNDARY_MAP` - Людвиг Витгенштейн: Принцип языковых игр - Boundary Map | Philosophical Investigations - language-games, meaning as use, private-language argument | semantic drift in code review; personal failure mode: losing 'Принцип языковых игр' while coding. | Draw the exact edge where the principle changes how modules may communicate. Apply it only as a Людвиг Витгенштейн-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 4 | `LYUDVIG_VITGENSHTEYN_04_COUNTEREXAMPLE_TEST` - Людвиг Витгенштейн: Принцип языковых игр - Counterexample Test | Philosophical Investigations - language-games, meaning as use, private-language argument | semantic drift in code review; personal failure mode: losing 'Принцип языковых игр' while coding. | Create a test designed to break the claim rather than merely demonstrate it. Apply it only as a Людвиг Витгенштейн-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 5 | `LYUDVIG_VITGENSHTEYN_05_DATA_SHAPE` - Людвиг Витгенштейн: Принцип языковых игр - Data Shape Discipline | Philosophical Investigations - language-games, meaning as use, private-language argument | semantic drift in code review; personal failure mode: losing 'Принцип языковых игр' while coding. | Encode the relevant philosophical distinction in schema, type or value-object structure. Apply it only as a Людвиг Витгенштейн-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 6 | `LYUDVIG_VITGENSHTEYN_06_TRANSITION_GUARD` - Людвиг Витгенштейн: Принцип языковых игр - Atomic Transition Guard | Philosophical Investigations - language-games, meaning as use, private-language argument | semantic drift in code review; personal failure mode: losing 'Принцип языковых игр' while coding. | Guard state changes with expected state, version or capability context. Apply it only as a Людвиг Витгенштейн-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 7 | `LYUDVIG_VITGENSHTEYN_07_REVIEW_BINARY` - Людвиг Витгенштейн: Принцип языковых игр - Binary Review Criterion | Philosophical Investigations - language-games, meaning as use, private-language argument | semantic drift in code review; personal failure mode: losing 'Принцип языковых игр' while coding. | Make the role's approval/rejection rule inspectable and non-vague. Apply it only as a Людвиг Витгенштейн-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 8 | `LYUDVIG_VITGENSHTEYN_08_EVIDENCE_TRACE` - Людвиг Витгенштейн: Принцип языковых игр - Evidence Trace | Philosophical Investigations - language-games, meaning as use, private-language argument | semantic drift in code review; personal failure mode: losing 'Принцип языковых игр' while coding. | Record the observable proof needed for later agents to trust the decision. Apply it only as a Людвиг Витгенштейн-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 9 | `LYUDVIG_VITGENSHTEYN_09_PARALLEL_WORK` - Людвиг Витгенштейн: Принцип языковых игр - Parallel Conflict Shield | Philosophical Investigations - language-games, meaning as use, private-language argument | semantic drift in code review; personal failure mode: losing 'Принцип языковых игр' while coding. | Prevent two agents from applying incompatible meanings to the same surface. Apply it only as a Людвиг Витгенштейн-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 10 | `LYUDVIG_VITGENSHTEYN_10_RAG_CAPSULE` - Людвиг Витгенштейн: Принцип языковых игр - RAG Doctrine Capsule | Philosophical Investigations - language-games, meaning as use, private-language argument | semantic drift in code review; personal failure mode: losing 'Принцип языковых игр' while coding. | Store the philosopher-specific rule as a retrievable decision fragment. Apply it only as a Людвиг Витгенштейн-specific micro-pattern; use the common ACP file for the broad engineering practice. |

## Common Patterns Kept Out

Use the common file for broad engineering practices such as contracts, type-driven design, property-based testing, merge queues, route ownership, generated-artifact authority and containerized toolchains:

- [Common Analytic Programming Patterns](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)
- [Parallel Development Conflict Prevention Charter](../01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md)

## RAG Instruction

When a task belongs to `BARCAN-TAG-00_CODE-GUARDIAN` and needs the individual voice of Людвиг Витгенштейн, retrieve this file after the common ACP file. Use the 10 patterns as a focused review lens, then attach concrete code evidence before approving work.
