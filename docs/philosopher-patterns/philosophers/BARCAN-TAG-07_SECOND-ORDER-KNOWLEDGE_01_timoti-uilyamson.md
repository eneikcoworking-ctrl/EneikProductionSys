---
philosopher_id: "BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE:01:timoti-uilyamson"
name_ru: "Тимоти Уильямсон"
barcan_tag: "BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE"
barcan_role: "SECOND-ORDER-KNOWLEDGE"
source_file: "BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE.md"
source_line: 30
source_principle: "Принцип примата знания (Knowledge First)"
publication_anchor: "Knowledge and Its Limits - knowledge-first epistemology"
evidence_status: "role_grounded_with_publication_anchor"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
---

# Тимоти Уильямсон

**BARCAN tag:** `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE` - SECOND-ORDER-KNOWLEDGE
**Role focus:** Security, validation and proof of authority
**Project role:** AppSec / DevSecOps Engineer
**Source principle:** Принцип примата знания (Knowledge First)
**Publication anchor:** Knowledge and Its Limits - knowledge-first epistemology
**Project source:** [`BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE.md:30`](../../BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE.md#L30)

## Interpretation Boundary

- These are programming micro-patterns inspired by the role-file principle and the listed publication anchor.
- They are not claims that the philosopher wrote software-engineering advice.
- Broad patterns that fit more than five philosophers are excluded and kept in the common file.

## 10 Personal Programming Patterns

| # | Personal pattern | Publication-grounded idea | Defect prevented | Agent rule |
|---:|---|---|---|---|
| 1 | `TIMOTI_UILYAMSON_01_NAME_GATE` - Тимоти Уильямсон: Принцип примата знания (Knowledge First) - Semantic Naming Gate | Knowledge and Its Limits - knowledge-first epistemology | authorization and validation blind spots; personal failure mode: losing 'Принцип примата знания (Knowledge First)' while coding. | Check names against the philosopher's central distinction before code review continues. Apply it only as a Тимоти Уильямсон-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 2 | `TIMOTI_UILYAMSON_02_STATE_INVARIANT` - Тимоти Уильямсон: Принцип примата знания (Knowledge First) - State Invariant Kernel | Knowledge and Its Limits - knowledge-first epistemology | authorization and validation blind spots; personal failure mode: losing 'Принцип примата знания (Knowledge First)' while coding. | Turn the principle into a lifecycle invariant that tests and runtime guards can enforce. Apply it only as a Тимоти Уильямсон-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 3 | `TIMOTI_UILYAMSON_03_BOUNDARY_MAP` - Тимоти Уильямсон: Принцип примата знания (Knowledge First) - Boundary Map | Knowledge and Its Limits - knowledge-first epistemology | authorization and validation blind spots; personal failure mode: losing 'Принцип примата знания (Knowledge First)' while coding. | Draw the exact edge where the principle changes how modules may communicate. Apply it only as a Тимоти Уильямсон-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 4 | `TIMOTI_UILYAMSON_04_COUNTEREXAMPLE_TEST` - Тимоти Уильямсон: Принцип примата знания (Knowledge First) - Counterexample Test | Knowledge and Its Limits - knowledge-first epistemology | authorization and validation blind spots; personal failure mode: losing 'Принцип примата знания (Knowledge First)' while coding. | Create a test designed to break the claim rather than merely demonstrate it. Apply it only as a Тимоти Уильямсон-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 5 | `TIMOTI_UILYAMSON_05_DATA_SHAPE` - Тимоти Уильямсон: Принцип примата знания (Knowledge First) - Data Shape Discipline | Knowledge and Its Limits - knowledge-first epistemology | authorization and validation blind spots; personal failure mode: losing 'Принцип примата знания (Knowledge First)' while coding. | Encode the relevant philosophical distinction in schema, type or value-object structure. Apply it only as a Тимоти Уильямсон-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 6 | `TIMOTI_UILYAMSON_06_TRANSITION_GUARD` - Тимоти Уильямсон: Принцип примата знания (Knowledge First) - Atomic Transition Guard | Knowledge and Its Limits - knowledge-first epistemology | authorization and validation blind spots; personal failure mode: losing 'Принцип примата знания (Knowledge First)' while coding. | Guard state changes with expected state, version or capability context. Apply it only as a Тимоти Уильямсон-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 7 | `TIMOTI_UILYAMSON_07_REVIEW_BINARY` - Тимоти Уильямсон: Принцип примата знания (Knowledge First) - Binary Review Criterion | Knowledge and Its Limits - knowledge-first epistemology | authorization and validation blind spots; personal failure mode: losing 'Принцип примата знания (Knowledge First)' while coding. | Make the role's approval/rejection rule inspectable and non-vague. Apply it only as a Тимоти Уильямсон-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 8 | `TIMOTI_UILYAMSON_08_EVIDENCE_TRACE` - Тимоти Уильямсон: Принцип примата знания (Knowledge First) - Evidence Trace | Knowledge and Its Limits - knowledge-first epistemology | authorization and validation blind spots; personal failure mode: losing 'Принцип примата знания (Knowledge First)' while coding. | Record the observable proof needed for later agents to trust the decision. Apply it only as a Тимоти Уильямсон-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 9 | `TIMOTI_UILYAMSON_09_PARALLEL_WORK` - Тимоти Уильямсон: Принцип примата знания (Knowledge First) - Parallel Conflict Shield | Knowledge and Its Limits - knowledge-first epistemology | authorization and validation blind spots; personal failure mode: losing 'Принцип примата знания (Knowledge First)' while coding. | Prevent two agents from applying incompatible meanings to the same surface. Apply it only as a Тимоти Уильямсон-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 10 | `TIMOTI_UILYAMSON_10_RAG_CAPSULE` - Тимоти Уильямсон: Принцип примата знания (Knowledge First) - RAG Doctrine Capsule | Knowledge and Its Limits - knowledge-first epistemology | authorization and validation blind spots; personal failure mode: losing 'Принцип примата знания (Knowledge First)' while coding. | Store the philosopher-specific rule as a retrievable decision fragment. Apply it only as a Тимоти Уильямсон-specific micro-pattern; use the common ACP file for the broad engineering practice. |

## Common Patterns Kept Out

Use the common file for broad engineering practices such as contracts, type-driven design, property-based testing, merge queues, route ownership, generated-artifact authority and containerized toolchains:

- [Common Analytic Programming Patterns](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)
- [Parallel Development Conflict Prevention Charter](../01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md)

## RAG Instruction

When a task belongs to `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE` and needs the individual voice of Тимоти Уильямсон, retrieve this file after the common ACP file. Use the 10 patterns as a focused review lens, then attach concrete code evidence before approving work.
