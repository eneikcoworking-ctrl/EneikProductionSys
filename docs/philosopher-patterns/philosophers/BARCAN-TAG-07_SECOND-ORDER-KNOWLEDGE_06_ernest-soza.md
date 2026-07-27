---
philosopher_id: "BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE:06:ernest-soza"
name_ru: "Эрнест Соза"
barcan_tag: "BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE"
barcan_role: "SECOND-ORDER-KNOWLEDGE"
source_file: "BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE.md"
source_line: 35
source_principle: "Принцип эпистемической добродетели"
publication_anchor: "Knowledge in Perspective / A Virtue Epistemology - virtue epistemology"
evidence_status: "role_grounded_with_publication_anchor"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
---

# Эрнест Соза

**BARCAN tag:** `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE` - SECOND-ORDER-KNOWLEDGE
**Role focus:** Security, validation and proof of authority
**Project role:** AppSec / DevSecOps Engineer
**Source principle:** Принцип эпистемической добродетели
**Publication anchor:** Knowledge in Perspective / A Virtue Epistemology - virtue epistemology
**Project source:** [`BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE.md:35`](../../BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE.md#L35)

## Interpretation Boundary

- These are programming micro-patterns inspired by the role-file principle and the listed publication anchor.
- They are not claims that the philosopher wrote software-engineering advice.
- Broad patterns that fit more than five philosophers are excluded and kept in the common file.

## 10 Personal Programming Patterns

| # | Personal pattern | Publication-grounded idea | Defect prevented | Agent rule |
|---:|---|---|---|---|
| 1 | `ERNEST_SOZA_01_NAME_GATE` - Эрнест Соза: Принцип эпистемической добродетели - Semantic Naming Gate | Knowledge in Perspective / A Virtue Epistemology - virtue epistemology | authorization and validation blind spots; personal failure mode: losing 'Принцип эпистемической добродетели' while coding. | Check names against the philosopher's central distinction before code review continues. Apply it only as a Эрнест Соза-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 2 | `ERNEST_SOZA_02_STATE_INVARIANT` - Эрнест Соза: Принцип эпистемической добродетели - State Invariant Kernel | Knowledge in Perspective / A Virtue Epistemology - virtue epistemology | authorization and validation blind spots; personal failure mode: losing 'Принцип эпистемической добродетели' while coding. | Turn the principle into a lifecycle invariant that tests and runtime guards can enforce. Apply it only as a Эрнест Соза-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 3 | `ERNEST_SOZA_03_BOUNDARY_MAP` - Эрнест Соза: Принцип эпистемической добродетели - Boundary Map | Knowledge in Perspective / A Virtue Epistemology - virtue epistemology | authorization and validation blind spots; personal failure mode: losing 'Принцип эпистемической добродетели' while coding. | Draw the exact edge where the principle changes how modules may communicate. Apply it only as a Эрнест Соза-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 4 | `ERNEST_SOZA_04_COUNTEREXAMPLE_TEST` - Эрнест Соза: Принцип эпистемической добродетели - Counterexample Test | Knowledge in Perspective / A Virtue Epistemology - virtue epistemology | authorization and validation blind spots; personal failure mode: losing 'Принцип эпистемической добродетели' while coding. | Create a test designed to break the claim rather than merely demonstrate it. Apply it only as a Эрнест Соза-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 5 | `ERNEST_SOZA_05_DATA_SHAPE` - Эрнест Соза: Принцип эпистемической добродетели - Data Shape Discipline | Knowledge in Perspective / A Virtue Epistemology - virtue epistemology | authorization and validation blind spots; personal failure mode: losing 'Принцип эпистемической добродетели' while coding. | Encode the relevant philosophical distinction in schema, type or value-object structure. Apply it only as a Эрнест Соза-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 6 | `ERNEST_SOZA_06_TRANSITION_GUARD` - Эрнест Соза: Принцип эпистемической добродетели - Atomic Transition Guard | Knowledge in Perspective / A Virtue Epistemology - virtue epistemology | authorization and validation blind spots; personal failure mode: losing 'Принцип эпистемической добродетели' while coding. | Guard state changes with expected state, version or capability context. Apply it only as a Эрнест Соза-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 7 | `ERNEST_SOZA_07_REVIEW_BINARY` - Эрнест Соза: Принцип эпистемической добродетели - Binary Review Criterion | Knowledge in Perspective / A Virtue Epistemology - virtue epistemology | authorization and validation blind spots; personal failure mode: losing 'Принцип эпистемической добродетели' while coding. | Make the role's approval/rejection rule inspectable and non-vague. Apply it only as a Эрнест Соза-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 8 | `ERNEST_SOZA_08_EVIDENCE_TRACE` - Эрнест Соза: Принцип эпистемической добродетели - Evidence Trace | Knowledge in Perspective / A Virtue Epistemology - virtue epistemology | authorization and validation blind spots; personal failure mode: losing 'Принцип эпистемической добродетели' while coding. | Record the observable proof needed for later agents to trust the decision. Apply it only as a Эрнест Соза-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 9 | `ERNEST_SOZA_09_PARALLEL_WORK` - Эрнест Соза: Принцип эпистемической добродетели - Parallel Conflict Shield | Knowledge in Perspective / A Virtue Epistemology - virtue epistemology | authorization and validation blind spots; personal failure mode: losing 'Принцип эпистемической добродетели' while coding. | Prevent two agents from applying incompatible meanings to the same surface. Apply it only as a Эрнест Соза-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 10 | `ERNEST_SOZA_10_RAG_CAPSULE` - Эрнест Соза: Принцип эпистемической добродетели - RAG Doctrine Capsule | Knowledge in Perspective / A Virtue Epistemology - virtue epistemology | authorization and validation blind spots; personal failure mode: losing 'Принцип эпистемической добродетели' while coding. | Store the philosopher-specific rule as a retrievable decision fragment. Apply it only as a Эрнест Соза-specific micro-pattern; use the common ACP file for the broad engineering practice. |

## Common Patterns Kept Out

Use the common file for broad engineering practices such as contracts, type-driven design, property-based testing, merge queues, route ownership, generated-artifact authority and containerized toolchains:

- [Common Analytic Programming Patterns](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)
- [Parallel Development Conflict Prevention Charter](../01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md)

## RAG Instruction

When a task belongs to `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE` and needs the individual voice of Эрнест Соза, retrieve this file after the common ACP file. Use the 10 patterns as a focused review lens, then attach concrete code evidence before approving work.
