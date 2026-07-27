---
philosopher_id: "BARCAN-TAG-01_ACTUALIST-OBJECT:06:kit-fayn"
name_ru: "Кит Файн"
barcan_tag: "BARCAN-TAG-01_ACTUALIST-OBJECT"
barcan_role: "ACTUALIST-OBJECT"
source_file: "BARCAN-TAG-01_ACTUALIST-OBJECT.md"
source_line: 28
source_principle: "Принцип метафизики сущностей"
publication_anchor: "Essence and Modality - essence before modal description"
evidence_status: "role_grounded_with_publication_anchor"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
---

# Кит Файн

**BARCAN tag:** `BARCAN-TAG-01_ACTUALIST-OBJECT` - ACTUALIST-OBJECT
**Role focus:** Domain objects, identity and bounded contexts
**Project role:** System / Solution Architect
**Source principle:** Принцип метафизики сущностей
**Publication anchor:** Essence and Modality - essence before modal description
**Project source:** [`BARCAN-TAG-01_ACTUALIST-OBJECT.md:28`](../../BARCAN-TAG-01_ACTUALIST-OBJECT.md#L28)

## Interpretation Boundary

- These are programming micro-patterns inspired by the role-file principle and the listed publication anchor.
- They are not claims that the philosopher wrote software-engineering advice.
- Broad patterns that fit more than five philosophers are excluded and kept in the common file.

## 10 Personal Programming Patterns

| # | Personal pattern | Publication-grounded idea | Defect prevented | Agent rule |
|---:|---|---|---|---|
| 1 | `KIT_FAYN_01_NAME_GATE` - Кит Файн: Принцип метафизики сущностей - Semantic Naming Gate | Essence and Modality - essence before modal description | anemic or duplicated domain entities; personal failure mode: losing 'Принцип метафизики сущностей' while coding. | Check names against the philosopher's central distinction before code review continues. Apply it only as a Кит Файн-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 2 | `KIT_FAYN_02_STATE_INVARIANT` - Кит Файн: Принцип метафизики сущностей - State Invariant Kernel | Essence and Modality - essence before modal description | anemic or duplicated domain entities; personal failure mode: losing 'Принцип метафизики сущностей' while coding. | Turn the principle into a lifecycle invariant that tests and runtime guards can enforce. Apply it only as a Кит Файн-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 3 | `KIT_FAYN_03_BOUNDARY_MAP` - Кит Файн: Принцип метафизики сущностей - Boundary Map | Essence and Modality - essence before modal description | anemic or duplicated domain entities; personal failure mode: losing 'Принцип метафизики сущностей' while coding. | Draw the exact edge where the principle changes how modules may communicate. Apply it only as a Кит Файн-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 4 | `KIT_FAYN_04_COUNTEREXAMPLE_TEST` - Кит Файн: Принцип метафизики сущностей - Counterexample Test | Essence and Modality - essence before modal description | anemic or duplicated domain entities; personal failure mode: losing 'Принцип метафизики сущностей' while coding. | Create a test designed to break the claim rather than merely demonstrate it. Apply it only as a Кит Файн-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 5 | `KIT_FAYN_05_DATA_SHAPE` - Кит Файн: Принцип метафизики сущностей - Data Shape Discipline | Essence and Modality - essence before modal description | anemic or duplicated domain entities; personal failure mode: losing 'Принцип метафизики сущностей' while coding. | Encode the relevant philosophical distinction in schema, type or value-object structure. Apply it only as a Кит Файн-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 6 | `KIT_FAYN_06_TRANSITION_GUARD` - Кит Файн: Принцип метафизики сущностей - Atomic Transition Guard | Essence and Modality - essence before modal description | anemic or duplicated domain entities; personal failure mode: losing 'Принцип метафизики сущностей' while coding. | Guard state changes with expected state, version or capability context. Apply it only as a Кит Файн-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 7 | `KIT_FAYN_07_REVIEW_BINARY` - Кит Файн: Принцип метафизики сущностей - Binary Review Criterion | Essence and Modality - essence before modal description | anemic or duplicated domain entities; personal failure mode: losing 'Принцип метафизики сущностей' while coding. | Make the role's approval/rejection rule inspectable and non-vague. Apply it only as a Кит Файн-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 8 | `KIT_FAYN_08_EVIDENCE_TRACE` - Кит Файн: Принцип метафизики сущностей - Evidence Trace | Essence and Modality - essence before modal description | anemic or duplicated domain entities; personal failure mode: losing 'Принцип метафизики сущностей' while coding. | Record the observable proof needed for later agents to trust the decision. Apply it only as a Кит Файн-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 9 | `KIT_FAYN_09_PARALLEL_WORK` - Кит Файн: Принцип метафизики сущностей - Parallel Conflict Shield | Essence and Modality - essence before modal description | anemic or duplicated domain entities; personal failure mode: losing 'Принцип метафизики сущностей' while coding. | Prevent two agents from applying incompatible meanings to the same surface. Apply it only as a Кит Файн-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 10 | `KIT_FAYN_10_RAG_CAPSULE` - Кит Файн: Принцип метафизики сущностей - RAG Doctrine Capsule | Essence and Modality - essence before modal description | anemic or duplicated domain entities; personal failure mode: losing 'Принцип метафизики сущностей' while coding. | Store the philosopher-specific rule as a retrievable decision fragment. Apply it only as a Кит Файн-specific micro-pattern; use the common ACP file for the broad engineering practice. |

## Common Patterns Kept Out

Use the common file for broad engineering practices such as contracts, type-driven design, property-based testing, merge queues, route ownership, generated-artifact authority and containerized toolchains:

- [Common Analytic Programming Patterns](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)
- [Parallel Development Conflict Prevention Charter](../01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md)

## RAG Instruction

When a task belongs to `BARCAN-TAG-01_ACTUALIST-OBJECT` and needs the individual voice of Кит Файн, retrieve this file after the common ACP file. Use the 10 patterns as a focused review lens, then attach concrete code evidence before approving work.
