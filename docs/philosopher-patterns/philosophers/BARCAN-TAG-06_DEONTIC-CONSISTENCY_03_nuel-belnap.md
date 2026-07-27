---
philosopher_id: "BARCAN-TAG-06_DEONTIC-CONSISTENCY:03:nuel-belnap"
name_ru: "Нуэль Белнап"
barcan_tag: "BARCAN-TAG-06_DEONTIC-CONSISTENCY"
barcan_role: "DEONTIC-CONSISTENCY"
source_file: "BARCAN-TAG-06_DEONTIC-CONSISTENCY.md"
source_line: 40
source_principle: "Принцип четырёхзначной логики (True/False/Both/Neither) — отвергается для итогового статуса, применяется для диагностики"
publication_anchor: "A Useful Four-Valued Logic / how a computer should think - many-valued diagnostics"
evidence_status: "role_grounded_with_publication_anchor"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
---

# Нуэль Белнап

**BARCAN tag:** `BARCAN-TAG-06_DEONTIC-CONSISTENCY` - DEONTIC-CONSISTENCY
**Role focus:** Testing, truth status and quality gates
**Project role:** QA Automation / Performance Engineer
**Source principle:** Принцип четырёхзначной логики (True/False/Both/Neither) — отвергается для итогового статуса, применяется для диагностики
**Publication anchor:** A Useful Four-Valued Logic / how a computer should think - many-valued diagnostics
**Project source:** [`BARCAN-TAG-06_DEONTIC-CONSISTENCY.md:40`](../../BARCAN-TAG-06_DEONTIC-CONSISTENCY.md#L40)

## Interpretation Boundary

- These are programming micro-patterns inspired by the role-file principle and the listed publication anchor.
- They are not claims that the philosopher wrote software-engineering advice.
- Broad patterns that fit more than five philosophers are excluded and kept in the common file.

## 10 Personal Programming Patterns

| # | Personal pattern | Publication-grounded idea | Defect prevented | Agent rule |
|---:|---|---|---|---|
| 1 | `NUEL_BELNAP_01_NAME_GATE` - Нуэль Белнап: Принцип четырёхзначной логики (True/False/Both/Neither) — отвергается для итогового статуса, применяется для диагностики - Semantic Naming Gate | A Useful Four-Valued Logic / how a computer should think - many-valued diagnostics | flaky or untruthful quality gates; personal failure mode: losing 'Принцип четырёхзначной логики (True/False/Both/Neither) — отвергается для итогового статуса, применяется для диагностики' while coding. | Check names against the philosopher's central distinction before code review continues. Apply it only as a Нуэль Белнап-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 2 | `NUEL_BELNAP_02_STATE_INVARIANT` - Нуэль Белнап: Принцип четырёхзначной логики (True/False/Both/Neither) — отвергается для итогового статуса, применяется для диагностики - State Invariant Kernel | A Useful Four-Valued Logic / how a computer should think - many-valued diagnostics | flaky or untruthful quality gates; personal failure mode: losing 'Принцип четырёхзначной логики (True/False/Both/Neither) — отвергается для итогового статуса, применяется для диагностики' while coding. | Turn the principle into a lifecycle invariant that tests and runtime guards can enforce. Apply it only as a Нуэль Белнап-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 3 | `NUEL_BELNAP_03_BOUNDARY_MAP` - Нуэль Белнап: Принцип четырёхзначной логики (True/False/Both/Neither) — отвергается для итогового статуса, применяется для диагностики - Boundary Map | A Useful Four-Valued Logic / how a computer should think - many-valued diagnostics | flaky or untruthful quality gates; personal failure mode: losing 'Принцип четырёхзначной логики (True/False/Both/Neither) — отвергается для итогового статуса, применяется для диагностики' while coding. | Draw the exact edge where the principle changes how modules may communicate. Apply it only as a Нуэль Белнап-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 4 | `NUEL_BELNAP_04_COUNTEREXAMPLE_TEST` - Нуэль Белнап: Принцип четырёхзначной логики (True/False/Both/Neither) — отвергается для итогового статуса, применяется для диагностики - Counterexample Test | A Useful Four-Valued Logic / how a computer should think - many-valued diagnostics | flaky or untruthful quality gates; personal failure mode: losing 'Принцип четырёхзначной логики (True/False/Both/Neither) — отвергается для итогового статуса, применяется для диагностики' while coding. | Create a test designed to break the claim rather than merely demonstrate it. Apply it only as a Нуэль Белнап-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 5 | `NUEL_BELNAP_05_DATA_SHAPE` - Нуэль Белнап: Принцип четырёхзначной логики (True/False/Both/Neither) — отвергается для итогового статуса, применяется для диагностики - Data Shape Discipline | A Useful Four-Valued Logic / how a computer should think - many-valued diagnostics | flaky or untruthful quality gates; personal failure mode: losing 'Принцип четырёхзначной логики (True/False/Both/Neither) — отвергается для итогового статуса, применяется для диагностики' while coding. | Encode the relevant philosophical distinction in schema, type or value-object structure. Apply it only as a Нуэль Белнап-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 6 | `NUEL_BELNAP_06_TRANSITION_GUARD` - Нуэль Белнап: Принцип четырёхзначной логики (True/False/Both/Neither) — отвергается для итогового статуса, применяется для диагностики - Atomic Transition Guard | A Useful Four-Valued Logic / how a computer should think - many-valued diagnostics | flaky or untruthful quality gates; personal failure mode: losing 'Принцип четырёхзначной логики (True/False/Both/Neither) — отвергается для итогового статуса, применяется для диагностики' while coding. | Guard state changes with expected state, version or capability context. Apply it only as a Нуэль Белнап-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 7 | `NUEL_BELNAP_07_REVIEW_BINARY` - Нуэль Белнап: Принцип четырёхзначной логики (True/False/Both/Neither) — отвергается для итогового статуса, применяется для диагностики - Binary Review Criterion | A Useful Four-Valued Logic / how a computer should think - many-valued diagnostics | flaky or untruthful quality gates; personal failure mode: losing 'Принцип четырёхзначной логики (True/False/Both/Neither) — отвергается для итогового статуса, применяется для диагностики' while coding. | Make the role's approval/rejection rule inspectable and non-vague. Apply it only as a Нуэль Белнап-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 8 | `NUEL_BELNAP_08_EVIDENCE_TRACE` - Нуэль Белнап: Принцип четырёхзначной логики (True/False/Both/Neither) — отвергается для итогового статуса, применяется для диагностики - Evidence Trace | A Useful Four-Valued Logic / how a computer should think - many-valued diagnostics | flaky or untruthful quality gates; personal failure mode: losing 'Принцип четырёхзначной логики (True/False/Both/Neither) — отвергается для итогового статуса, применяется для диагностики' while coding. | Record the observable proof needed for later agents to trust the decision. Apply it only as a Нуэль Белнап-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 9 | `NUEL_BELNAP_09_PARALLEL_WORK` - Нуэль Белнап: Принцип четырёхзначной логики (True/False/Both/Neither) — отвергается для итогового статуса, применяется для диагностики - Parallel Conflict Shield | A Useful Four-Valued Logic / how a computer should think - many-valued diagnostics | flaky or untruthful quality gates; personal failure mode: losing 'Принцип четырёхзначной логики (True/False/Both/Neither) — отвергается для итогового статуса, применяется для диагностики' while coding. | Prevent two agents from applying incompatible meanings to the same surface. Apply it only as a Нуэль Белнап-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 10 | `NUEL_BELNAP_10_RAG_CAPSULE` - Нуэль Белнап: Принцип четырёхзначной логики (True/False/Both/Neither) — отвергается для итогового статуса, применяется для диагностики - RAG Doctrine Capsule | A Useful Four-Valued Logic / how a computer should think - many-valued diagnostics | flaky or untruthful quality gates; personal failure mode: losing 'Принцип четырёхзначной логики (True/False/Both/Neither) — отвергается для итогового статуса, применяется для диагностики' while coding. | Store the philosopher-specific rule as a retrievable decision fragment. Apply it only as a Нуэль Белнап-specific micro-pattern; use the common ACP file for the broad engineering practice. |

## Common Patterns Kept Out

Use the common file for broad engineering practices such as contracts, type-driven design, property-based testing, merge queues, route ownership, generated-artifact authority and containerized toolchains:

- [Common Analytic Programming Patterns](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)
- [Parallel Development Conflict Prevention Charter](../01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md)

## RAG Instruction

When a task belongs to `BARCAN-TAG-06_DEONTIC-CONSISTENCY` and needs the individual voice of Нуэль Белнап, retrieve this file after the common ACP file. Use the 10 patterns as a focused review lens, then attach concrete code evidence before approving work.
