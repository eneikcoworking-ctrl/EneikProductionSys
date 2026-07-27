---
philosopher_id: "BARCAN-TAG-09_MORAL-DILEMMA:06:hillari-patnem"
name_ru: "Хиллари Патнэм"
barcan_tag: "BARCAN-TAG-09_MORAL-DILEMMA"
barcan_role: "MORAL-DILEMMA"
source_file: "BARCAN-TAG-09_MORAL-DILEMMA.md"
source_line: 29
source_principle: "Прагматический реализм"
publication_anchor: "Reason, Truth and History / The Meaning of 'Meaning' - internal realism and semantic externalism"
evidence_status: "role_grounded_with_publication_anchor"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
---

# Хиллари Патнэм

**BARCAN tag:** `BARCAN-TAG-09_MORAL-DILEMMA` - MORAL-DILEMMA
**Role focus:** Value, tradeoffs and waste prevention
**Project role:** Systems Analyst / Technical Product Manager / Technical Lead
**Source principle:** Прагматический реализм
**Publication anchor:** Reason, Truth and History / The Meaning of 'Meaning' - internal realism and semantic externalism
**Project source:** [`BARCAN-TAG-09_MORAL-DILEMMA.md:29`](../../BARCAN-TAG-09_MORAL-DILEMMA.md#L29)

## Interpretation Boundary

- These are programming micro-patterns inspired by the role-file principle and the listed publication anchor.
- They are not claims that the philosopher wrote software-engineering advice.
- Broad patterns that fit more than five philosophers are excluded and kept in the common file.

## 10 Personal Programming Patterns

| # | Personal pattern | Publication-grounded idea | Defect prevented | Agent rule |
|---:|---|---|---|---|
| 1 | `HILLARI_PATNEM_01_NAME_GATE` - Хиллари Патнэм: Прагматический реализм - Semantic Naming Gate | Reason, Truth and History / The Meaning of 'Meaning' - internal realism and semantic externalism | local optimization and wasteful scope; personal failure mode: losing 'Прагматический реализм' while coding. | Check names against the philosopher's central distinction before code review continues. Apply it only as a Хиллари Патнэм-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 2 | `HILLARI_PATNEM_02_STATE_INVARIANT` - Хиллари Патнэм: Прагматический реализм - State Invariant Kernel | Reason, Truth and History / The Meaning of 'Meaning' - internal realism and semantic externalism | local optimization and wasteful scope; personal failure mode: losing 'Прагматический реализм' while coding. | Turn the principle into a lifecycle invariant that tests and runtime guards can enforce. Apply it only as a Хиллари Патнэм-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 3 | `HILLARI_PATNEM_03_BOUNDARY_MAP` - Хиллари Патнэм: Прагматический реализм - Boundary Map | Reason, Truth and History / The Meaning of 'Meaning' - internal realism and semantic externalism | local optimization and wasteful scope; personal failure mode: losing 'Прагматический реализм' while coding. | Draw the exact edge where the principle changes how modules may communicate. Apply it only as a Хиллари Патнэм-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 4 | `HILLARI_PATNEM_04_COUNTEREXAMPLE_TEST` - Хиллари Патнэм: Прагматический реализм - Counterexample Test | Reason, Truth and History / The Meaning of 'Meaning' - internal realism and semantic externalism | local optimization and wasteful scope; personal failure mode: losing 'Прагматический реализм' while coding. | Create a test designed to break the claim rather than merely demonstrate it. Apply it only as a Хиллари Патнэм-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 5 | `HILLARI_PATNEM_05_DATA_SHAPE` - Хиллари Патнэм: Прагматический реализм - Data Shape Discipline | Reason, Truth and History / The Meaning of 'Meaning' - internal realism and semantic externalism | local optimization and wasteful scope; personal failure mode: losing 'Прагматический реализм' while coding. | Encode the relevant philosophical distinction in schema, type or value-object structure. Apply it only as a Хиллари Патнэм-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 6 | `HILLARI_PATNEM_06_TRANSITION_GUARD` - Хиллари Патнэм: Прагматический реализм - Atomic Transition Guard | Reason, Truth and History / The Meaning of 'Meaning' - internal realism and semantic externalism | local optimization and wasteful scope; personal failure mode: losing 'Прагматический реализм' while coding. | Guard state changes with expected state, version or capability context. Apply it only as a Хиллари Патнэм-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 7 | `HILLARI_PATNEM_07_REVIEW_BINARY` - Хиллари Патнэм: Прагматический реализм - Binary Review Criterion | Reason, Truth and History / The Meaning of 'Meaning' - internal realism and semantic externalism | local optimization and wasteful scope; personal failure mode: losing 'Прагматический реализм' while coding. | Make the role's approval/rejection rule inspectable and non-vague. Apply it only as a Хиллари Патнэм-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 8 | `HILLARI_PATNEM_08_EVIDENCE_TRACE` - Хиллари Патнэм: Прагматический реализм - Evidence Trace | Reason, Truth and History / The Meaning of 'Meaning' - internal realism and semantic externalism | local optimization and wasteful scope; personal failure mode: losing 'Прагматический реализм' while coding. | Record the observable proof needed for later agents to trust the decision. Apply it only as a Хиллари Патнэм-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 9 | `HILLARI_PATNEM_09_PARALLEL_WORK` - Хиллари Патнэм: Прагматический реализм - Parallel Conflict Shield | Reason, Truth and History / The Meaning of 'Meaning' - internal realism and semantic externalism | local optimization and wasteful scope; personal failure mode: losing 'Прагматический реализм' while coding. | Prevent two agents from applying incompatible meanings to the same surface. Apply it only as a Хиллари Патнэм-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 10 | `HILLARI_PATNEM_10_RAG_CAPSULE` - Хиллари Патнэм: Прагматический реализм - RAG Doctrine Capsule | Reason, Truth and History / The Meaning of 'Meaning' - internal realism and semantic externalism | local optimization and wasteful scope; personal failure mode: losing 'Прагматический реализм' while coding. | Store the philosopher-specific rule as a retrievable decision fragment. Apply it only as a Хиллари Патнэм-specific micro-pattern; use the common ACP file for the broad engineering practice. |

## Common Patterns Kept Out

Use the common file for broad engineering practices such as contracts, type-driven design, property-based testing, merge queues, route ownership, generated-artifact authority and containerized toolchains:

- [Common Analytic Programming Patterns](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)
- [Parallel Development Conflict Prevention Charter](../01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md)

## RAG Instruction

When a task belongs to `BARCAN-TAG-09_MORAL-DILEMMA` and needs the individual voice of Хиллари Патнэм, retrieve this file after the common ACP file. Use the 10 patterns as a focused review lens, then attach concrete code evidence before approving work.
