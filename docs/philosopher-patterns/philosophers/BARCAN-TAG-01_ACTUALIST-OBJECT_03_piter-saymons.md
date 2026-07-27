---
philosopher_id: "BARCAN-TAG-01_ACTUALIST-OBJECT:03:piter-saymons"
name_ru: "Питер Саймонс"
barcan_tag: "BARCAN-TAG-01_ACTUALIST-OBJECT"
barcan_role: "ACTUALIST-OBJECT"
source_file: "BARCAN-TAG-01_ACTUALIST-OBJECT.md"
source_line: 25
source_principle: "Принцип современной мереологии"
publication_anchor: "Parts: A Study in Ontology - mereology and part-whole structure"
evidence_status: "role_grounded_with_publication_anchor"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
---

# Питер Саймонс

**BARCAN tag:** `BARCAN-TAG-01_ACTUALIST-OBJECT` - ACTUALIST-OBJECT
**Role focus:** Domain objects, identity and bounded contexts
**Project role:** System / Solution Architect
**Source principle:** Принцип современной мереологии
**Publication anchor:** Parts: A Study in Ontology - mereology and part-whole structure
**Project source:** [`BARCAN-TAG-01_ACTUALIST-OBJECT.md:25`](../../BARCAN-TAG-01_ACTUALIST-OBJECT.md#L25)

## Interpretation Boundary

- These are programming micro-patterns inspired by the role-file principle and the listed publication anchor.
- They are not claims that the philosopher wrote software-engineering advice.
- Broad patterns that fit more than five philosophers are excluded and kept in the common file.

## 10 Personal Programming Patterns

| # | Personal pattern | Publication-grounded idea | Defect prevented | Agent rule |
|---:|---|---|---|---|
| 1 | `PITER_SAYMONS_01_NAME_GATE` - Питер Саймонс: Принцип современной мереологии - Semantic Naming Gate | Parts: A Study in Ontology - mereology and part-whole structure | anemic or duplicated domain entities; personal failure mode: losing 'Принцип современной мереологии' while coding. | Check names against the philosopher's central distinction before code review continues. Apply it only as a Питер Саймонс-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 2 | `PITER_SAYMONS_02_STATE_INVARIANT` - Питер Саймонс: Принцип современной мереологии - State Invariant Kernel | Parts: A Study in Ontology - mereology and part-whole structure | anemic or duplicated domain entities; personal failure mode: losing 'Принцип современной мереологии' while coding. | Turn the principle into a lifecycle invariant that tests and runtime guards can enforce. Apply it only as a Питер Саймонс-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 3 | `PITER_SAYMONS_03_BOUNDARY_MAP` - Питер Саймонс: Принцип современной мереологии - Boundary Map | Parts: A Study in Ontology - mereology and part-whole structure | anemic or duplicated domain entities; personal failure mode: losing 'Принцип современной мереологии' while coding. | Draw the exact edge where the principle changes how modules may communicate. Apply it only as a Питер Саймонс-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 4 | `PITER_SAYMONS_04_COUNTEREXAMPLE_TEST` - Питер Саймонс: Принцип современной мереологии - Counterexample Test | Parts: A Study in Ontology - mereology and part-whole structure | anemic or duplicated domain entities; personal failure mode: losing 'Принцип современной мереологии' while coding. | Create a test designed to break the claim rather than merely demonstrate it. Apply it only as a Питер Саймонс-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 5 | `PITER_SAYMONS_05_DATA_SHAPE` - Питер Саймонс: Принцип современной мереологии - Data Shape Discipline | Parts: A Study in Ontology - mereology and part-whole structure | anemic or duplicated domain entities; personal failure mode: losing 'Принцип современной мереологии' while coding. | Encode the relevant philosophical distinction in schema, type or value-object structure. Apply it only as a Питер Саймонс-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 6 | `PITER_SAYMONS_06_TRANSITION_GUARD` - Питер Саймонс: Принцип современной мереологии - Atomic Transition Guard | Parts: A Study in Ontology - mereology and part-whole structure | anemic or duplicated domain entities; personal failure mode: losing 'Принцип современной мереологии' while coding. | Guard state changes with expected state, version or capability context. Apply it only as a Питер Саймонс-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 7 | `PITER_SAYMONS_07_REVIEW_BINARY` - Питер Саймонс: Принцип современной мереологии - Binary Review Criterion | Parts: A Study in Ontology - mereology and part-whole structure | anemic or duplicated domain entities; personal failure mode: losing 'Принцип современной мереологии' while coding. | Make the role's approval/rejection rule inspectable and non-vague. Apply it only as a Питер Саймонс-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 8 | `PITER_SAYMONS_08_EVIDENCE_TRACE` - Питер Саймонс: Принцип современной мереологии - Evidence Trace | Parts: A Study in Ontology - mereology and part-whole structure | anemic or duplicated domain entities; personal failure mode: losing 'Принцип современной мереологии' while coding. | Record the observable proof needed for later agents to trust the decision. Apply it only as a Питер Саймонс-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 9 | `PITER_SAYMONS_09_PARALLEL_WORK` - Питер Саймонс: Принцип современной мереологии - Parallel Conflict Shield | Parts: A Study in Ontology - mereology and part-whole structure | anemic or duplicated domain entities; personal failure mode: losing 'Принцип современной мереологии' while coding. | Prevent two agents from applying incompatible meanings to the same surface. Apply it only as a Питер Саймонс-specific micro-pattern; use the common ACP file for the broad engineering practice. |
| 10 | `PITER_SAYMONS_10_RAG_CAPSULE` - Питер Саймонс: Принцип современной мереологии - RAG Doctrine Capsule | Parts: A Study in Ontology - mereology and part-whole structure | anemic or duplicated domain entities; personal failure mode: losing 'Принцип современной мереологии' while coding. | Store the philosopher-specific rule as a retrievable decision fragment. Apply it only as a Питер Саймонс-specific micro-pattern; use the common ACP file for the broad engineering practice. |

## Common Patterns Kept Out

Use the common file for broad engineering practices such as contracts, type-driven design, property-based testing, merge queues, route ownership, generated-artifact authority and containerized toolchains:

- [Common Analytic Programming Patterns](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)
- [Parallel Development Conflict Prevention Charter](../01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md)

## RAG Instruction

When a task belongs to `BARCAN-TAG-01_ACTUALIST-OBJECT` and needs the individual voice of Питер Саймонс, retrieve this file after the common ACP file. Use the 10 patterns as a focused review lens, then attach concrete code evidence before approving work.
