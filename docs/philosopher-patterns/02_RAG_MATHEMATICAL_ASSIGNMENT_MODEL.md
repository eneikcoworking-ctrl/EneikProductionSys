# RAG Mathematical Assignment Model

This file defines the reproducible assignment rule for the philosopher-pattern corpus. It is the authority for generators, QA and retrieval policy.

## Sets

- `F`: philosopher rows extracted from `BARCAN-TAG-*.md`; expected cardinality is `86`.
- `C`: common analytic programming patterns, stored in `00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md`.
- `S`: parameterized philosopher-specific micro-pattern slots in the generator.
- `D`: defect taxonomy used as the target prevention space.

## Common Pattern Rule

A concrete reusable engineering practice is common iff `fit_count(pattern) > 5`. Common practices stay in `C` and must not be copied into philosopher files as personal patterns.

## Personal Assignment Score

For philosopher `f` and slot `s`:

```text
score(f, s) =
  3 * |axes(tag(f)) intersect axes(s)|
  + 2 * |axes(publication_anchor(f)) intersect axes(s)|
  + 1 * |axes(role/focus/principle(f)) intersect axes(s)|
  + stable_hash_jitter(f, s)
```

The hash term is deterministic and smaller than `0.001`; it only breaks ties without changing substantive ranking.

## Selection Rule

For each philosopher, sort `S` by descending `score(f, s)` and then by slot key. Select the first `20` slots. This is a lower-bound invariant: future generators may select more, but QA fails if a philosopher receives fewer.

## RAG Retrieval Policy

1. Retrieve `00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md` for reusable defect-prevention techniques.
2. Retrieve this model file when the agent needs to explain why a philosopher received a pattern.
3. Retrieve exactly one philosopher file for individual style, principle and defect lens.
4. For parallel development, also retrieve `01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md`.
5. A generated answer must cite the source row, publication anchor, selected pattern ID and defect class before applying a doctrine.

## QA Invariants

- Exactly `13` source BARCAN files.
- Exactly `86` philosopher source rows and generated philosopher files.
- At least `20` personal patterns per philosopher.
- Personal pattern IDs are globally unique.
- Personal pattern names are globally unique.
- Common patterns and personal patterns are separated by the common-threshold rule.

## Tag Axes

| Tag | Axes |
|---|---|
| `BARCAN-TAG-00_CODE-GUARDIAN` | conflict, evidence, integration, language, meaning, review |
| `BARCAN-TAG-01_ACTUALIST-OBJECT` | boundary, composition, domain, identity, ontology, types |
| `BARCAN-TAG-02_RIGID-DESIGNATOR` | api, compatibility, context, identity, naming, reference |
| `BARCAN-TAG-03_BELIEF-INTENSION` | agency, causal, cognitive, context, feedback, perception, ux |
| `BARCAN-TAG-04_MODAL-QUANTIFIER` | causal, evidence, model, prediction, probability, uncertainty |
| `BARCAN-TAG-05_NECESSARY-IDENTITY` | causal, composition, history, identity, replay, runtime |
| `BARCAN-TAG-06_DEONTIC-CONSISTENCY` | counterexample, logic, status, testing, truth, verification |
| `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE` | authority, epistemic, evidence, information, security, validation |
| `BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE` | computation, data, lineage, logic, substitution, types |
| `BARCAN-TAG-09_MORAL-DILEMMA` | coherence, cost, interpretation, pragmatic, tradeoff, value |
| `BARCAN-TAG-10_DEONTIC-PROHIBITION` | authority, exception, law, normative, permission, policy |
| `BARCAN-TAG-11_CLIENT-PERCEPTION` | accessibility, aesthetic, cognitive, identity, information, perception, ux |
| `BARCAN-TAG-12_SOCIAL-CONTRACT` | commitment, contract, coordination, institution, parallel, planning |

## Defect Taxonomy

| ID | Defect class | Description |
|---|---|---|
| `D001` | Semantic drift | A name, rule or interface keeps the old spelling but changes meaning. |
| `D002` | Invalid state | The code permits a domain state the role principle forbids. |
| `D003` | Contract drift | Producer and consumer assumptions diverge. |
| `D004` | Concurrency conflict | Two agents or workers claim the same mutable surface. |
| `D005` | Partial distributed failure | One side effect lands while the matching state/event does not. |
| `D006` | Authorization ambiguity | Authority, permission or prohibition is inferred instead of proven. |
| `D007` | Evidence gap | The agent cannot cite code, test, trace or source evidence for a claim. |
| `D008` | False green | A check reports success without covering the relevant behavior. |
| `D009` | Substitution failure | Replacement preserves shape but not semantics. |
| `D010` | Data lineage loss | A value loses origin, identity or transformation history. |
| `D011` | Perception failure | The UI is technically present but not usable, visible or accessible. |
| `D012` | Policy contradiction | Two rules can require incompatible actions. |
| `D013` | Runtime drift | The deployed runtime no longer corresponds to the trusted repository state. |
| `D014` | RAG hallucination | The agent applies doctrine without exact retrieved grounding. |
| `D015` | Aesthetic drift | Visual language (color, type, symmetry, spacing) fails to trace to the declared design system, or diverges across screens generated under the same design-system id. |
