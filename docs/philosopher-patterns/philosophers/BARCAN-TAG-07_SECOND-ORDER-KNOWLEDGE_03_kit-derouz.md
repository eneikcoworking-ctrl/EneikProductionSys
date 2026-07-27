---
philosopher_id: "BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE:03:kit-derouz"
name_ru: "Кит ДеРоуз"
barcan_tag: "BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE"
barcan_role: "SECOND-ORDER-KNOWLEDGE"
source_file: "BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE.md"
source_line: 32
source_principle: "Принцип эпистемического контекстуализма"
publication_anchor: "Solving the Skeptical Problem - epistemic contextualism"
evidence_status: "role_grounded_with_publication_anchor"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
---

# Кит ДеРоуз

**BARCAN tag:** `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE` - SECOND-ORDER-KNOWLEDGE
**Role focus:** Security, validation and proof of authority
**Project role:** AppSec / DevSecOps Engineer
**Source principle:** Принцип эпистемического контекстуализма
**Publication anchor:** Solving the Skeptical Problem - epistemic contextualism
**Project source:** [`BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE.md:32`](../../BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE.md#L32)

## Interpretation Boundary

- These are programming micro-patterns inspired by the role-file principle and the listed publication anchor.
- They are not claims that the philosopher wrote software-engineering advice.
- Broad patterns that fit more than five philosophers are excluded and kept in the common file.

## 10 Personal Programming Patterns

| # | Personal pattern | Publication-grounded idea | Defect prevented | Agent rule |
|---:|---|---|---|---|
| 1 | `KIT_DEROUZ_01_NAME_GATE` - Кит ДеРоуз: Принцип эпистемического контекстуализма - Semantic Naming Gate | Solving the Skeptical Problem - epistemic contextualism | authorization and validation blind spots; personal failure mode: losing 'Принцип эпистемического контекстуализма' while coding. | Check names against the philosopher's central distinction before code review continues. Apply it only as a Кит ДеРоуз-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 2 | `KIT_DEROUZ_02_STATE_INVARIANT` - Кит ДеРоуз: Принцип эпистемического контекстуализма - State Invariant Kernel | Solving the Skeptical Problem - epistemic contextualism | authorization and validation blind spots; personal failure mode: losing 'Принцип эпистемического контекстуализма' while coding. | Turn the principle into a lifecycle invariant that tests and runtime guards can enforce. Apply it only as a Кит ДеРоуз-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 3 | `KIT_DEROUZ_03_BOUNDARY_MAP` - Кит ДеРоуз: Принцип эпистемического контекстуализма - Boundary Map | Solving the Skeptical Problem - epistemic contextualism | authorization and validation blind spots; personal failure mode: losing 'Принцип эпистемического контекстуализма' while coding. | Draw the exact edge where the principle changes how modules may communicate. Apply it only as a Кит ДеРоуз-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 4 | `KIT_DEROUZ_04_COUNTEREXAMPLE_TEST` - Кит ДеРоуз: Принцип эпистемического контекстуализма - Counterexample Test | Solving the Skeptical Problem - epistemic contextualism | authorization and validation blind spots; personal failure mode: losing 'Принцип эпистемического контекстуализма' while coding. | Create a test designed to break the claim rather than merely demonstrate it. Apply it only as a Кит ДеРоуз-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 5 | `KIT_DEROUZ_05_DATA_SHAPE` - Кит ДеРоуз: Принцип эпистемического контекстуализма - Data Shape Discipline | Solving the Skeptical Problem - epistemic contextualism | authorization and validation blind spots; personal failure mode: losing 'Принцип эпистемического контекстуализма' while coding. | Encode the relevant philosophical distinction in schema, type or value-object structure. Apply it only as a Кит ДеРоуз-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 6 | `KIT_DEROUZ_06_TRANSITION_GUARD` - Кит ДеРоуз: Принцип эпистемического контекстуализма - Atomic Transition Guard | Solving the Skeptical Problem - epistemic contextualism | authorization and validation blind spots; personal failure mode: losing 'Принцип эпистемического контекстуализма' while coding. | Guard state changes with expected state, version or capability context. Apply it only as a Кит ДеРоуз-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 7 | `KIT_DEROUZ_07_REVIEW_BINARY` - Кит ДеРоуз: Принцип эпистемического контекстуализма - Binary Review Criterion | Solving the Skeptical Problem - epistemic contextualism | authorization and validation blind spots; personal failure mode: losing 'Принцип эпистемического контекстуализма' while coding. | Make the role's approval/rejection rule inspectable and non-vague. Apply it only as a Кит ДеРоуз-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 8 | `KIT_DEROUZ_08_EVIDENCE_TRACE` - Кит ДеРоуз: Принцип эпистемического контекстуализма - Evidence Trace | Solving the Skeptical Problem - epistemic contextualism | authorization and validation blind spots; personal failure mode: losing 'Принцип эпистемического контекстуализма' while coding. | Record the observable proof needed for later agents to trust the decision. Apply it only as a Кит ДеРоуз-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 9 | `KIT_DEROUZ_09_PARALLEL_WORK` - Кит ДеРоуз: Принцип эпистемического контекстуализма - Parallel Conflict Shield | Solving the Skeptical Problem - epistemic contextualism | authorization and validation blind spots; personal failure mode: losing 'Принцип эпистемического контекстуализма' while coding. | Prevent two agents from applying incompatible meanings to the same surface. Apply it only as a Кит ДеРоуз-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 10 | `KIT_DEROUZ_10_RAG_CAPSULE` - Кит ДеРоуз: Принцип эпистемического контекстуализма - RAG Doctrine Capsule | Solving the Skeptical Problem - epistemic contextualism | authorization and validation blind spots; personal failure mode: losing 'Принцип эпистемического контекстуализма' while coding. | Store the philosopher-specific rule as a retrievable decision fragment. Apply it only as a Кит ДеРоуз-specific micro-pattern; use the common ACP file for the broad engineering practice. |

## Common Patterns Kept Out

Use the common file for broad engineering practices such as contracts, type-driven design, property-based testing, merge queues, route ownership, generated-artifact authority and containerized toolchains:

- [Common Analytic Programming Patterns](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)
- [Parallel Development Conflict Prevention Charter](../01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md)

## RAG Instruction

When a task belongs to `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE` and needs the individual voice of Кит ДеРоуз, retrieve this file after the common ACP file. Use the 10 patterns as a focused review lens, then attach concrete code evidence before approving work.
