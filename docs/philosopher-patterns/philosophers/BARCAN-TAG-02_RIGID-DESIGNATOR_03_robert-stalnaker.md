---
philosopher_id: "BARCAN-TAG-02_RIGID-DESIGNATOR:03:robert-stalnaker"
name_ru: "Роберт Сталнакер"
barcan_tag: "BARCAN-TAG-02_RIGID-DESIGNATOR"
barcan_role: "RIGID-DESIGNATOR"
source_file: "BARCAN-TAG-02_RIGID-DESIGNATOR.md"
source_line: 40
source_principle: "Принцип динамики контекста"
publication_anchor: "Assertion and possible-world pragmatics - common ground and context change"
evidence_status: "role_grounded_with_publication_anchor"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
---

# Роберт Сталнакер

**BARCAN tag:** `BARCAN-TAG-02_RIGID-DESIGNATOR` - RIGID-DESIGNATOR
**Role focus:** API contracts, naming and semantic stability
**Project role:** Backend / Integration Engineer
**Source principle:** Принцип динамики контекста
**Publication anchor:** Assertion and possible-world pragmatics - common ground and context change
**Project source:** [`BARCAN-TAG-02_RIGID-DESIGNATOR.md:40`](../../BARCAN-TAG-02_RIGID-DESIGNATOR.md#L40)

## Interpretation Boundary

- These are programming micro-patterns inspired by the role-file principle and the listed publication anchor.
- They are not claims that the philosopher wrote software-engineering advice.
- Broad patterns that fit more than five philosophers are excluded and kept in the common file.

## 10 Personal Programming Patterns

| # | Personal pattern | Publication-grounded idea | Defect prevented | Agent rule |
|---:|---|---|---|---|
| 1 | `ROBERT_STALNAKER_01_NAME_GATE` - Роберт Сталнакер: Принцип динамики контекста - Semantic Naming Gate | Assertion and possible-world pragmatics - common ground and context change | breaking API and naming drift; personal failure mode: losing 'Принцип динамики контекста' while coding. | Check names against the philosopher's central distinction before code review continues. Apply it only as a Роберт Сталнакер-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 2 | `ROBERT_STALNAKER_02_STATE_INVARIANT` - Роберт Сталнакер: Принцип динамики контекста - State Invariant Kernel | Assertion and possible-world pragmatics - common ground and context change | breaking API and naming drift; personal failure mode: losing 'Принцип динамики контекста' while coding. | Turn the principle into a lifecycle invariant that tests and runtime guards can enforce. Apply it only as a Роберт Сталнакер-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 3 | `ROBERT_STALNAKER_03_BOUNDARY_MAP` - Роберт Сталнакер: Принцип динамики контекста - Boundary Map | Assertion and possible-world pragmatics - common ground and context change | breaking API and naming drift; personal failure mode: losing 'Принцип динамики контекста' while coding. | Draw the exact edge where the principle changes how modules may communicate. Apply it only as a Роберт Сталнакер-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 4 | `ROBERT_STALNAKER_04_COUNTEREXAMPLE_TEST` - Роберт Сталнакер: Принцип динамики контекста - Counterexample Test | Assertion and possible-world pragmatics - common ground and context change | breaking API and naming drift; personal failure mode: losing 'Принцип динамики контекста' while coding. | Create a test designed to break the claim rather than merely demonstrate it. Apply it only as a Роберт Сталнакер-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 5 | `ROBERT_STALNAKER_05_DATA_SHAPE` - Роберт Сталнакер: Принцип динамики контекста - Data Shape Discipline | Assertion and possible-world pragmatics - common ground and context change | breaking API and naming drift; personal failure mode: losing 'Принцип динамики контекста' while coding. | Encode the relevant philosophical distinction in schema, type or value-object structure. Apply it only as a Роберт Сталнакер-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 6 | `ROBERT_STALNAKER_06_TRANSITION_GUARD` - Роберт Сталнакер: Принцип динамики контекста - Atomic Transition Guard | Assertion and possible-world pragmatics - common ground and context change | breaking API and naming drift; personal failure mode: losing 'Принцип динамики контекста' while coding. | Guard state changes with expected state, version or capability context. Apply it only as a Роберт Сталнакер-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 7 | `ROBERT_STALNAKER_07_REVIEW_BINARY` - Роберт Сталнакер: Принцип динамики контекста - Binary Review Criterion | Assertion and possible-world pragmatics - common ground and context change | breaking API and naming drift; personal failure mode: losing 'Принцип динамики контекста' while coding. | Make the role's approval/rejection rule inspectable and non-vague. Apply it only as a Роберт Сталнакер-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 8 | `ROBERT_STALNAKER_08_EVIDENCE_TRACE` - Роберт Сталнакер: Принцип динамики контекста - Evidence Trace | Assertion and possible-world pragmatics - common ground and context change | breaking API and naming drift; personal failure mode: losing 'Принцип динамики контекста' while coding. | Record the observable proof needed for later agents to trust the decision. Apply it only as a Роберт Сталнакер-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 9 | `ROBERT_STALNAKER_09_PARALLEL_WORK` - Роберт Сталнакер: Принцип динамики контекста - Parallel Conflict Shield | Assertion and possible-world pragmatics - common ground and context change | breaking API and naming drift; personal failure mode: losing 'Принцип динамики контекста' while coding. | Prevent two agents from applying incompatible meanings to the same surface. Apply it only as a Роберт Сталнакер-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 10 | `ROBERT_STALNAKER_10_RAG_CAPSULE` - Роберт Сталнакер: Принцип динамики контекста - RAG Doctrine Capsule | Assertion and possible-world pragmatics - common ground and context change | breaking API and naming drift; personal failure mode: losing 'Принцип динамики контекста' while coding. | Store the philosopher-specific rule as a retrievable decision fragment. Apply it only as a Роберт Сталнакер-specific micro-pattern; use the common ACP file for the broad engineering practice. |

## Common Patterns Kept Out

Use the common file for broad engineering practices such as contracts, type-driven design, property-based testing, merge queues, route ownership, generated-artifact authority and containerized toolchains:

- [Common Analytic Programming Patterns](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)
- [Parallel Development Conflict Prevention Charter](../01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md)

## RAG Instruction

When a task belongs to `BARCAN-TAG-02_RIGID-DESIGNATOR` and needs the individual voice of Роберт Сталнакер, retrieve this file after the common ACP file. Use the 10 patterns as a focused review lens, then attach concrete code evidence before approving work.
