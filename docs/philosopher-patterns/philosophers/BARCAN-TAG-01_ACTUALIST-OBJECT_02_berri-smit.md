---
philosopher_id: "BARCAN-TAG-01_ACTUALIST-OBJECT:02:berri-smit"
name_ru: "Бэрри Смит"
barcan_tag: "BARCAN-TAG-01_ACTUALIST-OBJECT"
barcan_role: "ACTUALIST-OBJECT"
source_file: "BARCAN-TAG-01_ACTUALIST-OBJECT.md"
source_line: 24
source_principle: "Принцип прикладной формальной онтологии"
publication_anchor: "Applied ontology and Basic Formal Ontology - formal taxonomies for real domains"
evidence_status: "role_grounded_with_publication_anchor"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
---

# Бэрри Смит

**BARCAN tag:** `BARCAN-TAG-01_ACTUALIST-OBJECT` - ACTUALIST-OBJECT
**Role focus:** Domain objects, identity and bounded contexts
**Project role:** System / Solution Architect
**Source principle:** Принцип прикладной формальной онтологии
**Publication anchor:** Applied ontology and Basic Formal Ontology - formal taxonomies for real domains
**Project source:** [`BARCAN-TAG-01_ACTUALIST-OBJECT.md:24`](../../BARCAN-TAG-01_ACTUALIST-OBJECT.md#L24)

## Interpretation Boundary

- These are programming micro-patterns inspired by the role-file principle and the listed publication anchor.
- They are not claims that the philosopher wrote software-engineering advice.
- Broad patterns that fit more than five philosophers are excluded and kept in the common file.

## 10 Personal Programming Patterns

| # | Personal pattern | Publication-grounded idea | Defect prevented | Agent rule |
|---:|---|---|---|---|
| 1 | `BERRI_SMIT_01_NAME_GATE` - Бэрри Смит: Принцип прикладной формальной онтологии - Semantic Naming Gate | Applied ontology and Basic Formal Ontology - formal taxonomies for real domains | anemic or duplicated domain entities; personal failure mode: losing 'Принцип прикладной формальной онтологии' while coding. | Check names against the philosopher's central distinction before code review continues. Apply it only as a Бэрри Смит-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 2 | `BERRI_SMIT_02_STATE_INVARIANT` - Бэрри Смит: Принцип прикладной формальной онтологии - State Invariant Kernel | Applied ontology and Basic Formal Ontology - formal taxonomies for real domains | anemic or duplicated domain entities; personal failure mode: losing 'Принцип прикладной формальной онтологии' while coding. | Turn the principle into a lifecycle invariant that tests and runtime guards can enforce. Apply it only as a Бэрри Смит-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 3 | `BERRI_SMIT_03_BOUNDARY_MAP` - Бэрри Смит: Принцип прикладной формальной онтологии - Boundary Map | Applied ontology and Basic Formal Ontology - formal taxonomies for real domains | anemic or duplicated domain entities; personal failure mode: losing 'Принцип прикладной формальной онтологии' while coding. | Draw the exact edge where the principle changes how modules may communicate. Apply it only as a Бэрри Смит-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 4 | `BERRI_SMIT_04_COUNTEREXAMPLE_TEST` - Бэрри Смит: Принцип прикладной формальной онтологии - Counterexample Test | Applied ontology and Basic Formal Ontology - formal taxonomies for real domains | anemic or duplicated domain entities; personal failure mode: losing 'Принцип прикладной формальной онтологии' while coding. | Create a test designed to break the claim rather than merely demonstrate it. Apply it only as a Бэрри Смит-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 5 | `BERRI_SMIT_05_DATA_SHAPE` - Бэрри Смит: Принцип прикладной формальной онтологии - Data Shape Discipline | Applied ontology and Basic Formal Ontology - formal taxonomies for real domains | anemic or duplicated domain entities; personal failure mode: losing 'Принцип прикладной формальной онтологии' while coding. | Encode the relevant philosophical distinction in schema, type or value-object structure. Apply it only as a Бэрри Смит-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 6 | `BERRI_SMIT_06_TRANSITION_GUARD` - Бэрри Смит: Принцип прикладной формальной онтологии - Atomic Transition Guard | Applied ontology and Basic Formal Ontology - formal taxonomies for real domains | anemic or duplicated domain entities; personal failure mode: losing 'Принцип прикладной формальной онтологии' while coding. | Guard state changes with expected state, version or capability context. Apply it only as a Бэрри Смит-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 7 | `BERRI_SMIT_07_REVIEW_BINARY` - Бэрри Смит: Принцип прикладной формальной онтологии - Binary Review Criterion | Applied ontology and Basic Formal Ontology - formal taxonomies for real domains | anemic or duplicated domain entities; personal failure mode: losing 'Принцип прикладной формальной онтологии' while coding. | Make the role's approval/rejection rule inspectable and non-vague. Apply it only as a Бэрри Смит-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 8 | `BERRI_SMIT_08_EVIDENCE_TRACE` - Бэрри Смит: Принцип прикладной формальной онтологии - Evidence Trace | Applied ontology and Basic Formal Ontology - formal taxonomies for real domains | anemic or duplicated domain entities; personal failure mode: losing 'Принцип прикладной формальной онтологии' while coding. | Record the observable proof needed for later agents to trust the decision. Apply it only as a Бэрри Смит-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 9 | `BERRI_SMIT_09_PARALLEL_WORK` - Бэрри Смит: Принцип прикладной формальной онтологии - Parallel Conflict Shield | Applied ontology and Basic Formal Ontology - formal taxonomies for real domains | anemic or duplicated domain entities; personal failure mode: losing 'Принцип прикладной формальной онтологии' while coding. | Prevent two agents from applying incompatible meanings to the same surface. Apply it only as a Бэрри Смит-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 10 | `BERRI_SMIT_10_RAG_CAPSULE` - Бэрри Смит: Принцип прикладной формальной онтологии - RAG Doctrine Capsule | Applied ontology and Basic Formal Ontology - formal taxonomies for real domains | anemic or duplicated domain entities; personal failure mode: losing 'Принцип прикладной формальной онтологии' while coding. | Store the philosopher-specific rule as a retrievable decision fragment. Apply it only as a Бэрри Смит-specific micro-pattern; use the common ACP file for the broad engineering practice. |

## Common Patterns Kept Out

Use the common file for broad engineering practices such as contracts, type-driven design, property-based testing, merge queues, route ownership, generated-artifact authority and containerized toolchains:

- [Common Analytic Programming Patterns](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)
- [Parallel Development Conflict Prevention Charter](../01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md)

## RAG Instruction

When a task belongs to `BARCAN-TAG-01_ACTUALIST-OBJECT` and needs the individual voice of Бэрри Смит, retrieve this file after the common ACP file. Use the 10 patterns as a focused review lens, then attach concrete code evidence before approving work.
