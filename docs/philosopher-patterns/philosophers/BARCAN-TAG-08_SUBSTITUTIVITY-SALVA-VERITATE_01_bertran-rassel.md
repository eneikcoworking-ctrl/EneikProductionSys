---
philosopher_id: "BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE:01:bertran-rassel"
name_ru: "Бертран Рассел"
barcan_tag: "BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE"
barcan_role: "SUBSTITUTIVITY-SALVA-VERITATE"
source_file: "BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE.md"
source_line: 29
source_principle: "Принцип теории типов"
publication_anchor: "On Denoting / Principia Mathematica - descriptions and logical analysis"
evidence_status: "role_grounded_with_publication_anchor"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
---

# Бертран Рассел

**BARCAN tag:** `BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE` - SUBSTITUTIVITY-SALVA-VERITATE
**Role focus:** Data types, substitution and lineage
**Project role:** Data Engineer / DBA
**Source principle:** Принцип теории типов
**Publication anchor:** On Denoting / Principia Mathematica - descriptions and logical analysis
**Project source:** [`BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE.md:29`](../../BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE.md#L29)

## Interpretation Boundary

- These are programming micro-patterns inspired by the role-file principle and the listed publication anchor.
- They are not claims that the philosopher wrote software-engineering advice.
- Broad patterns that fit more than five philosophers are excluded and kept in the common file.

## 10 Personal Programming Patterns

| # | Personal pattern | Publication-grounded idea | Defect prevented | Agent rule |
|---:|---|---|---|---|
| 1 | `BERTRAN_RASSEL_01_NAME_GATE` - Бертран Рассел: Принцип теории типов - Semantic Naming Gate | On Denoting / Principia Mathematica - descriptions and logical analysis | unsafe substitution and data lineage loss; personal failure mode: losing 'Принцип теории типов' while coding. | Check names against the philosopher's central distinction before code review continues. Apply it only as a Бертран Рассел-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 2 | `BERTRAN_RASSEL_02_STATE_INVARIANT` - Бертран Рассел: Принцип теории типов - State Invariant Kernel | On Denoting / Principia Mathematica - descriptions and logical analysis | unsafe substitution and data lineage loss; personal failure mode: losing 'Принцип теории типов' while coding. | Turn the principle into a lifecycle invariant that tests and runtime guards can enforce. Apply it only as a Бертран Рассел-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 3 | `BERTRAN_RASSEL_03_BOUNDARY_MAP` - Бертран Рассел: Принцип теории типов - Boundary Map | On Denoting / Principia Mathematica - descriptions and logical analysis | unsafe substitution and data lineage loss; personal failure mode: losing 'Принцип теории типов' while coding. | Draw the exact edge where the principle changes how modules may communicate. Apply it only as a Бертран Рассел-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 4 | `BERTRAN_RASSEL_04_COUNTEREXAMPLE_TEST` - Бертран Рассел: Принцип теории типов - Counterexample Test | On Denoting / Principia Mathematica - descriptions and logical analysis | unsafe substitution and data lineage loss; personal failure mode: losing 'Принцип теории типов' while coding. | Create a test designed to break the claim rather than merely demonstrate it. Apply it only as a Бертран Рассел-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 5 | `BERTRAN_RASSEL_05_DATA_SHAPE` - Бертран Рассел: Принцип теории типов - Data Shape Discipline | On Denoting / Principia Mathematica - descriptions and logical analysis | unsafe substitution and data lineage loss; personal failure mode: losing 'Принцип теории типов' while coding. | Encode the relevant philosophical distinction in schema, type or value-object structure. Apply it only as a Бертран Рассел-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 6 | `BERTRAN_RASSEL_06_TRANSITION_GUARD` - Бертран Рассел: Принцип теории типов - Atomic Transition Guard | On Denoting / Principia Mathematica - descriptions and logical analysis | unsafe substitution and data lineage loss; personal failure mode: losing 'Принцип теории типов' while coding. | Guard state changes with expected state, version or capability context. Apply it only as a Бертран Рассел-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 7 | `BERTRAN_RASSEL_07_REVIEW_BINARY` - Бертран Рассел: Принцип теории типов - Binary Review Criterion | On Denoting / Principia Mathematica - descriptions and logical analysis | unsafe substitution and data lineage loss; personal failure mode: losing 'Принцип теории типов' while coding. | Make the role's approval/rejection rule inspectable and non-vague. Apply it only as a Бертран Рассел-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 8 | `BERTRAN_RASSEL_08_EVIDENCE_TRACE` - Бертран Рассел: Принцип теории типов - Evidence Trace | On Denoting / Principia Mathematica - descriptions and logical analysis | unsafe substitution and data lineage loss; personal failure mode: losing 'Принцип теории типов' while coding. | Record the observable proof needed for later agents to trust the decision. Apply it only as a Бертран Рассел-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 9 | `BERTRAN_RASSEL_09_PARALLEL_WORK` - Бертран Рассел: Принцип теории типов - Parallel Conflict Shield | On Denoting / Principia Mathematica - descriptions and logical analysis | unsafe substitution and data lineage loss; personal failure mode: losing 'Принцип теории типов' while coding. | Prevent two agents from applying incompatible meanings to the same surface. Apply it only as a Бертран Рассел-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 10 | `BERTRAN_RASSEL_10_RAG_CAPSULE` - Бертран Рассел: Принцип теории типов - RAG Doctrine Capsule | On Denoting / Principia Mathematica - descriptions and logical analysis | unsafe substitution and data lineage loss; personal failure mode: losing 'Принцип теории типов' while coding. | Store the philosopher-specific rule as a retrievable decision fragment. Apply it only as a Бертран Рассел-specific micro-pattern; use the common ACP file for the broad engineering practice. |

## Common Patterns Kept Out

Use the common file for broad engineering practices such as contracts, type-driven design, property-based testing, merge queues, route ownership, generated-artifact authority and containerized toolchains:

- [Common Analytic Programming Patterns](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)
- [Parallel Development Conflict Prevention Charter](../01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md)

## RAG Instruction

When a task belongs to `BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE` and needs the individual voice of Бертран Рассел, retrieve this file after the common ACP file. Use the 10 patterns as a focused review lens, then attach concrete code evidence before approving work.
