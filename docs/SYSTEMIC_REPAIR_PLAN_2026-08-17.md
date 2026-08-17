# Systemic Repair Plan — 2026-08-17

## 0. Status and boundary

This plan is **diagnosis and specification only**. Nothing in it has been implemented. It inherits the
Non-Negotiable Boundary of `OPERATIONAL_MATH_ARCHITECTURE.md` without weakening it: no item here may,
in its first phase, change `TaskStatus`, change `WishlistStatus`, dispatch Jules, call GitHub write
APIs, trigger AutoMerge, or treat an LLM claim as final evidence of delivery.

Every item declares its entry mode on the existing five-step Promotion Policy
(`observe_only → warn_only → soft_gate → hard_gate → auto_remediate`). None enters above `warn_only`.

Source material: `OPERATIONAL_MATH_ARCHITECTURE.md` (evidence algebra, invariant catalogue, promotion
policy), `ENGINEERING_INVARIANTS_CHARTER.md` (invariants 1–15), `docs/philosopher-patterns/` (86 role
files, BARCAN-TAG taxonomy), and the observation log `WORKPLAN_2026-08-15_repair.md` (findings
F31–F66, 2817 lines).

---

## 1. Why this plan exists, stated honestly

Over one watch cycle (2026-08-16 → 2026-08-17) I produced a large number of findings and **a large
number of wrong claims**. The wrong claims are not incidental; they share one form, and that form is
itself diagnostic of what the system lacks.

| My claim | What was true | The error |
| --- | --- | --- |
| "The project will stall when tasks close" | `ProjectStatus` has no stalled state; `active` is the working state and only a human ends it | Predicted a state that does not exist, without reading the enum |
| "Design is blocked" | Two independent services; falsification was running and calling Stitch the whole time | Merged two referents under one word |
| "F43's budget is verified, 2 nudges in 6h" | 39 nudges in the next hour | Two samples inside a backoff interval published as a termination proof |
| "Narrow pipeline, not a stall" | The session went stale and the task died 90 minutes later | A present-tense observation published as a forecast |
| "The board is fully inert" | New task claimed and 2 observer findings within 30 minutes | Three identical snapshots published as a system property |
| "Zero lock timeouts" (every pass) | 21 lock timeouts, 17 on `PROJECTS`, in the H2 trace | Measured in a source that does not carry the signal |
| "Both recovery exits are closed — the complete circle" | `OpsAuditorService` recovered the task 52 minutes later | Enumerated two paths and declared the set exhaustive |
| "F62: a failure with surviving siblings is permanently invisible" | The second gatherer does not test siblings at all; the real gate is `isDependencySatisfied` | Read one gatherer, generalised to the mechanism |

Seven of these eight are the same mistake in different clothing: **a claim was made whose truth
conditions had not been established.** That is precisely the failure the corpus already names —
`OPERATIONAL_MATH_ARCHITECTURE.md` §"Analytic Philosophy Basis" requires that *every operational
claim has explicit conditions of truth*, and Charter invariant 12 requires *independent verification,
not self-report*.

The system exhibits the identical failure at machine scale. That correspondence is the organising
idea of this plan: **the defects below are not seven unrelated bugs, they are the same epistemic
defect instantiated in seven places.** A monitor, a reasoner, and an operator all failed the same way
because none of them was required to state what would make its claim false.

### 1.1 Corrections register (standing requirement)

The operator's specific complaint — *"you forget to record corrections"* — is upheld. Corrections
were made in prose inside the pass that discovered them and were not lifted anywhere durable, so an
earlier wrong entry and its later retraction sit 800 lines apart with equal authority.

**Rule adopted:** every retraction is recorded in one place, at the top of the observation log, in
the form `claim → what was true → the error`. The table in §1 is that register's initial content. A
finding that has been retracted is never cited again without its retraction.

---

## 2. The five structural defects

Each defect is stated as a logical form, grounded in an existing invariant, and given a repair whose
success condition is checkable.

### D1 — The evidence predicate is the limit, not the reasoner

**Logical form.** For observer `O` with evidence set `E`: `¬representable(p, E) → ¬decidable(p, O)`.
`O` cannot be at fault for `p` when `p` has no representation in `E`.

**Evidence.** The observer's whole input is an evidence snapshot plus the last 24h of
`EvidenceNodeEntity` rows, described in her own prompt as "5 independent signal sources". The
services that write those rows are `DefectJournalService`, `KaizenService`, `AutoMergeService`,
`FalsificationCycleService` — all application-layer. No producer exists for any infrastructure fact.
Therefore F63 (a scheduled job that has failed hourly for three days), F64 (21 lock timeouts), F65
(the database regrowing 91 MB → 553 MB in a day) and F66 (an endpoint returning 200 with an empty
body) were **not missed by her — they were unrepresentable.**

The same defect exists one layer down and independently: `OpsAuditorService.gatherAllEvidence` builds
exactly two evidence kinds, so its action set is bounded by what those two predicates can express.

**Why the naive fix is wrong.** Routing the trace file into her prompt is a patch: it adds volume,
not vocabulary, and it bypasses the corroboration machinery. `EvidenceNodeEntity.sourceType()`
already feeds a **distinct-sourceType corroboration count**, and `EvidenceCoherenceService` (Thagard
ECHO / Gärdenfors AGM) already scores nodes across sources. A fact that enters outside that path is
uncorroborated by construction.

**Repair.** One new producer emitting `EvidenceNodeEntity` rows for infrastructure facts as a new
`sourceType`, so every existing consumer — corroboration count, coherence scoring, the observer's
`readRecentEvidenceNodes`, the auditor — receives them unchanged. It must emit **negative** facts: a
job that has never succeeded is a decided negative with a witness, not an absence.

**Entry mode:** `observe_only`. **Success condition:** an infrastructure fact appears in
`readRecentEvidenceNodes` output and raises the corroboration count of a node it corroborates.

### D2 — Repeated actions carry no well-founded measure

**Logical form.** For a repeated action `a` there must exist `μ: State → ℕ` with `μ(s') < μ(s)` on
every application. Absent `μ`, no termination proof exists and the loop ends only by an external
mechanism, at a cost.

**Evidence.** Three tasks in nine hours took ~60 forced-unblock messages each, at a fixed 60-second
interval, and each ended when the circuit breaker killed the session and the task was retired:
`b50a4511` (19:08), `f42e448c` (23:33), `1e169d70` (overnight). The configured bound is
`jules.forced-unblock-max-attempts = 2`. On the first occurrence the first two messages were 30
minutes apart and the interval then collapsed; on the second and third it was 60 seconds from message
one. **Cause not established.** Charter invariant 7 (monotone watermarks against infinite loops)
already names this class.

**The distinction that must be preserved.** A *retry of a failed action* requires a bound. A
*periodic sweep* must never be bounded — bounding it would silently stop monitoring. A *terminal
action* happens once. Conflating these is how a previous repair attempt broke: a duplicate
`forced_unblock_attempts` column was added on the false premise that no counter existed, breaking the
migration and every integration test.

**Repair.** Before any code: instrument the existing counter and the backoff decision so the observed
interval and the persisted attempt count are both visible per session. Only once the divergence
between configured bound and observed behaviour is measured does a fix get designed.

**Entry mode:** `observe_only` (instrumentation first — this is the item most likely to cause damage
if guessed at). **Success condition:** for a stale session, the persisted attempt count and the
inter-message interval are both retrievable and are consistent with each other.

### D3 — The metric denominator is not a declared set

**Logical form.** `ratio(X, Y)` is honest only to the extent that `Y` is declared. Charter invariant
8 states the rule directly: *when computing any progress ratio, explicitly enumerate which
statuses/categories are excluded from the denominator, and why.*

**Evidence.** Three consecutive measurements with nothing merged between them:

```
18:48Z  totalPlanned 27  merged 25  ratio 0.9259  features 4/6 = 0.667
19:18Z  totalPlanned 27  merged 25  ratio 0.9259  features 4/6 = 0.667
19:48Z  totalPlanned 26  merged 25  ratio 0.9615  features 5/6 = 0.833
```

A task was retired; the denominator fell by exactly one; readiness rose on both axes and a feature
flipped to complete. **Failing work improved the reported readiness of the product.** Readiness gates
self-falsification (0.9), the design shop (1.0) and the philosophical track, so a project can unlock
its own falsification stages by losing tasks.

Invariant 8 anticipates the opposite hazard — an item that structurally cannot complete must be
excluded, or the metric sticks below 100% forever. Both directions are real, and the invariant as
written does not distinguish them. **The refinement this system needs:** exclusion from the
denominator is legitimate when the work is *not required*, and illegitimate when the work is
*required and undelivered*. A retired failure is the second case; a rejected duplicate is the first.

This is Charter invariant 12's principle applied to arithmetic, and the corpus's own *limits of
substitutivity*: `task retired` may not be substituted for `value not required`.

**Repair.** The readiness calculation declares its exclusion set explicitly, and a retired-without-
replacement task is not in it. Two published numbers, not one: delivered-over-required, and
required-but-undelivered.

**Entry mode:** `warn_only` — the invariant already exists in the catalogue as
`delivered(x) → ∃ evidence(x)`; the warning is when readiness rises without a merge event.
**Success condition:** a retirement produces no upward movement in any readiness figure.

### D4 — Signals exist with no declared consumer

**Logical form.** `signal(s) ∧ ¬∃reader(s) → ¬monitoring(s)`. A signal nobody consumes is not
monitoring; it is exhaust.

**Evidence.**
- F63: a job has failed once an hour since 2026-08-14 — 60 occurrences, identical message, present in
  no dashboard, no wishlist, no evidence node.
- F60: `OpsAuditorService` correctly diagnosed the iteration-admission poka-yoke and **escalated to a
  human** — as a WARN log line and nothing else.
- My own step-6 valueless-flag reporter fires at every startup and I never read its output.

Three instances, two of them built deliberately as monitoring. The corpus already demands *public
criteria* — state must be understandable without backend knowledge — and this violates it.

**Repair.** Every signal declares its reader at the point it is emitted. A signal whose declared
reader is "a log file" is rejected at review. This is a review rule, not code, and it costs nothing
to adopt.

**Entry mode:** `warn_only`. **Success condition:** the human-review flag and the hourly job outcome
are both retrievable through the same surface an operator already reads.

### D5 — Claims are made without stated truth conditions

**Logical form.** Before ruling on `p`, `p` must be declared. This is the **Barcan condition** the
system already uses: a proposition must be in the declared set before a verdict is issued, so that
ABSTAIN ("declared, undecided") is distinguishable from silence ("never considered").

**Evidence.** Machine side: `auditProject` returns silently when `gatherAllEvidence` is empty, and
logs only when Gemini returns no decisions. Silence therefore conflates *swept and found nothing*,
*not swept*, and *service stopped* — three states with different operational meanings. I spent two
passes unable to distinguish them from outside.

Operator side: the eight errors in §1. Every one is a verdict issued on an undeclared proposition.

**Repair.** Symmetric, and both halves are cheap:
- Machine: the auditor emits a decided outcome per sweep, including "swept, zero evidence" — an
  ABSTAIN with a witness rather than an absence.
- Operator: the watch procedure states, before each pass, which propositions it will rule on and what
  source establishes each. A signal read from the wrong source (D-F64) is then a declared failure
  rather than a silent one.

**Entry mode:** `observe_only`. **Success condition:** from outside, "swept and found nothing" is
distinguishable from "did not sweep".

---

## 3. Extended evidence vocabulary for the observer

Direct response to the operator's instruction to give the observer more information. The point is
**vocabulary, not volume** — her documented failure mode is over-claiming on a narrow input (F51:
"nearly all done tasks" against 1 of 33; a nudge target `c034c2fb` that did not exist in the project),
not ignoring a wide one. More text would worsen that. More *kinds* of fact will not.

Each proposed evidence kind is given with its truth condition and its position in the existing
Evidence Algebra (strength 0–5), so it cannot be mistaken for delivery evidence.

| Evidence kind | Truth condition | Strength | Answers |
| --- | --- | --- | --- |
| `scheduled_job_outcome` | job `j` ran at `t` and returned success / failure / did-not-run | 3 (contextual verification) | F63 — a job that has never succeeded |
| `db_contention` | a lock timeout occurred on table `T` at `t` | 3 | F64 — 17 timeouts on `PROJECTS` |
| `db_volume` | measured file size at `t`, with delta since last measurement | 3 | F65 — 91 MB → 553 MB |
| `endpoint_contract` | endpoint returned 200 with empty set while the underlying data is non-empty | 2 (activity evidence only) | F66 |
| `retirement_without_replacement` | task terminally failed, no task references it via `RECOVERS_FAILED_TASK_ID_KEY` | 4 (strong implementation signal, not delivery) | D3 — required-but-undelivered work |
| `sweep_outcome` | an auditor/observer sweep completed with a decided result, including empty | 2 | D5 — ABSTAIN vs silence |

Two constraints on all six:

1. **None may exceed strength 4.** Only a merged PR into main is strength 5. An infrastructure fact
   can never constitute delivery evidence; the corpus is explicit that *operational truth must not
   treat activity evidence as delivered value*.
2. **Each must be emitted as an `EvidenceNodeEntity` with a distinct `sourceType`**, so it enters the
   distinct-sourceType corroboration count and `EvidenceCoherenceService` rather than bypassing them.

The expected effect on her reasoning is specific: today a claim like "systemic state-desynchronization"
rests on one source type and is corroborated by nothing, which is exactly how quantifier inflation
survives. With independent infrastructure facts in the graph, the same claim either gains genuine
corroboration or is visibly uncorroborated.

---

## 4. Order of work, and why this order

Ordering is by **safety of the repair**, not by severity of the symptom, because the record shows
that acting on an unmeasured hypothesis in this system does more damage than the defect being fixed
(the duplicate-column incident broke the migration and every integration test; the H2 file lock took
the production database offline).

1. **D5, operator half** — the watch procedure declares its propositions and sources. Costs nothing,
   is not code, and prevents further wrong findings from entering the record.
2. **D4** — declare readers for existing signals. A review rule; no runtime change.
3. **D1** — infrastructure evidence producer, `observe_only`. Purely additive; no existing consumer
   changes behaviour.
4. **D5, machine half** — decided sweep outcomes. Small, additive, makes D2's instrumentation
   readable.
5. **D2** — instrument the nudge counter and backoff. **Measurement only.** No change to the
   mechanism until the divergence is measured.
6. **D3** — declared denominator. Last, because it touches the readiness calculation that
   `FlowSpineService` and `OperationalTruthService` derive client-facing status from, and readiness
   gates three other subsystems.

`OpsAuditorService`'s evidence-predicate coverage is deliberately **not** on this list as a separate
item. The finding that motivated it (F62) has been retracted: the second gatherer does not test
siblings, and the real gate is `isDependencySatisfied`, which returns true when a task with the same
role, `featureId` and `ems_semantic_key` has merged. Whether the two long-standing failures are
correctly satisfied by a semantic equivalent or falsely matched is **unmeasured**, and the
measurement — comparing the `ems_semantic_key` of `ab74be69` and `36651896` against whatever is
matching them — belongs before any repair. Recorded here so the retraction travels with the finding.

---

## 5. What would falsify this plan

Stated because a plan whose outcome is fixed by construction is not a plan. Popper's criterion is
already load-bearing in this codebase's falsification track; it applies to this document too.

- **D1 is wrong** if infrastructure facts, once in the graph, produce no change in observer output or
  in corroboration counts — i.e. if the limit was never the vocabulary.
- **D2 is wrong** if the persisted attempt counter is found to be correct and bounded, and the ~60
  messages come from a path that does not consult it — in which case the defect is dispatch topology,
  not measure.
- **D3 is wrong** if the retired task was genuinely not required, in which case its removal from the
  denominator is correct and invariant 8 applies in its original direction.
- **D4 is wrong** if the signals do have consumers I did not find.
- **D5 is wrong** if silence is already distinguishable through a surface I have not read — which,
  given F64, is a live possibility and must be checked before implementing.

Each falsifier is a measurement, not an argument.

---

## 6. Stages

Staged so that each stage is independently valuable, independently revertible, and strictly less
risky than the one after it. The project stays active throughout; no stage requires stopping the
flow.

### Stage 1 — Declare what is measured and who reads it *(no code)*

Covers D5 operator half and D4. Deliverable: `WATCH_PROTOCOL.md` — a register of every proposition
the watch rules on, its authoritative source, and its falsifier; plus a signal→reader register.

Risk: none. Nothing executes. Revert: delete a document.

**Status: DONE (2026-08-17).** Every source in the register was verified against the running system
before entry. The register already carries seven falsifiers derived from real counter-examples,
including the one that would have prevented the "zero lock timeouts" error.

Byproduct, recorded in the protocol: the evidence graph was measured and D1 became quantitative —
73 nodes across **three** sourceTypes where the observer's prompt declares five, 70% of them from a
single source, `acceptedNodes` equal to `totalNodes`.

### Stage 2 — Make silence decidable *(additive, `observe_only`)* — **DONE 2026-08-17**

Covers D5 machine half. The auditor emits a decided outcome for every sweep, including "swept, zero
evidence". Today `auditProject` returns silently when `gatherAllEvidence` is empty, so from outside
"swept and found nothing", "did not sweep" and "service stopped" are one indistinguishable state.

Smallest possible change, purely additive, no behaviour altered. Chosen before Stage 3 because
Stage 3's instrumentation is unreadable without it.

Success condition: from outside, an empty sweep is distinguishable from an absent sweep.

**Delivered.** `OpsAuditorService.auditProject` now declares the empty case as an ABSTAIN carrying
its own witness (which gatherers were consulted) instead of returning silently. Commit `5555366`.

Verification actually run, in the order the lesson requires:
- `mvn test-compile` (not `compile`) - exit 0. The image build compiles test sources, so a check that
  skips them is not a check of what the target runs.
- `mvn -Dtest=OpsAuditorServiceTest test` - exit 0, and the new line appears twice in the run,
  proving it fires on the empty-evidence path rather than merely compiling.
- Image rebuilt, backend redeployed while the board held zero queued and zero claimed tasks, so the
  restart interrupted no work.

Why this is not the F56 defect it superficially resembles: F56 is an *action* denied 35 times in 35
minutes against an empty work set - zero information content, once per poll. This is a *periodic
sweep*, once per 30 minutes per project, and its information content is non-zero precisely because
liveness is otherwise unobservable. The distinction is the one recorded under D2: a retry needs a
bound, a sweep must never be bounded into silence. A future reader tempted to "clean up" this line
would be removing the only external evidence that the auditor runs at all.

### Stage 3 — Infrastructure as a signal source *(additive, `observe_only`)*

Covers D1. One producer emitting `EvidenceNodeEntity` rows with a new `sourceType` for infrastructure
facts, entering the existing corroboration and coherence path rather than bypassing it. Vocabulary
from §3, all at strength ≤ 4, negatives included.

Success condition: an infrastructure fact appears in `readRecentEvidenceNodes` and raises the
distinct-sourceType corroboration count of a node it corroborates.

### Stage 4 — Measure the nudge divergence *(measurement only)*

Covers D2. Instrument the persisted attempt counter and the backoff decision so that the observed
interval and the stored count are both visible per session. **No change to the mechanism.**

This stage deliberately produces no fix. The configured bound is 2 and ~60 messages were sent three
times; the divergence is unexplained, and the one previous attempt to repair this mechanism on an
unmeasured hypothesis added a duplicate `forced_unblock_attempts` column and broke the migration and
every integration test. The measurement is the deliverable.

Success condition: for a stale session, stored count and observed interval are both retrievable and
mutually consistent — or their inconsistency is exhibited.

### Stage 5 — Declared denominator *(`warn_only`)*

Covers D3. Readiness declares its exclusion set explicitly; a retired-without-replacement task is not
in it. Two published figures: delivered-over-required, and required-but-undelivered.

Last because it touches the calculation `FlowSpineService` and `OperationalTruthService` derive
client-facing status from, and because readiness gates self-falsification (0.9), the design shop
(1.0) and the philosophical track. Entered at `warn_only`: warn when readiness rises with no merge
event, before changing any number.

Success condition: a retirement produces no upward movement in any readiness figure.

### Not staged — pending measurement

`OpsAuditorService` evidence-predicate coverage. The motivating finding (F62) is **retracted**: the
second gatherer does not test siblings; the real gate is `isDependencySatisfied`, true when a task
with the same role, `featureId` and `ems_semantic_key` has merged. Whether `ab74be69` and `36651896`
are correctly satisfied by a semantic equivalent or falsely matched is unmeasured. The measurement
precedes any repair and does not belong in a stage until it has been done.

---

## 7. Type discipline: three kinds of problem, never mixed

Operator directive, 2026-08-17. The system separates **factory problems**, **value-delivery
problems**, and **product problems**. These are distinct types and must not be substituted for one
another.

This is not a filing convention. It is the corpus's own rule — `OPERATIONAL_MATH_ARCHITECTURE.md`
requires *type distinctions: activity, merge, delivery, trust, and user value are not
interchangeable*, and *limits of substitutivity: `task done` cannot be substituted for `value
delivered`*. A finding filed under the wrong type routes to the wrong repair and, worse, licenses a
wrong inference: a factory defect read as a product defect makes the client's software look broken
when it is not, and a product defect read as a factory defect gets "fixed" by changing the
orchestrator.

The observer already carries this distinction and uses it correctly. Her 14:22Z entry reads: *"Since
this state tracking is handled by the orchestrator/factory and is independent of the project's own
code, this is a platform-scope issue."* That is a correct type assignment, made unprompted. It is
therefore a hook to build on, not a capability to add.

### Findings retyped

| Type | Definition | Findings |
| --- | --- | --- |
| **Factory** | Defects in the orchestrator itself. Invisible to the client; they degrade the machine that builds products. | D1 evidence-predicate poverty · D2 nudge loop with no measure (F43) · D5 undecidable silence · F63 hourly job that has never succeeded · F64 lock timeouts · F65 database regrowth · F66 empty endpoint · F56 denial noise · F60 human-review flag with no reader · F58 dashboard vs Flow Core disagreement |
| **Value delivery** | Defects in how delivered work is counted, gated, or reported. The product may be fine and the factory may be running; what breaks is the claim about what has been delivered. | D3 undeclared denominator (F59) · retirement without replacement leaving `failed` uncorrelated with recovery · readiness gating self-falsification, the design shop and the philosophical track on a figure that failure can raise |
| **Product** | Defects in the client's software. | The Flyway `IF NOT EXISTS` finding (observer, 22:48Z — verified against the client repo: real, severity arguable) · `apply_design_system` failing every call with `Request contains an invalid argument`, which is a product-design defect surfacing through a factory service |

### Consequences for this plan

- **Stage 2 is a factory repair.** The sweep outcome says nothing about delivery or product, and the
  committed code says so in its own comment so a later reader cannot mistake it.
- **Stage 3's evidence vocabulary is entirely factory-scope** — job outcomes, contention, volume,
  endpoint contracts. Every kind in §3 must be emitted with its type, or it will be corroborated
  against facts of a different type and produce false coherence.
- **Stage 5 is the only value-delivery stage**, and it is last precisely because miscounting delivery
  is the failure mode that most easily masquerades as the other two.
- `apply_design_system` failing is filed under product, not factory, even though a factory service
  reports it. **The reporter's identity does not determine the type; the referent does.**

### Requirement added to Stage 3

Every infrastructure evidence node carries its type explicitly. Corroboration must be computed
**within** a type: a factory fact corroborating a product claim is not corroboration, it is a
category error of exactly the kind Charter invariant 6 names (Ryle). Without this constraint,
enriching the evidence graph would make the observer's quantifier inflation worse rather than
better — she would gain new facts to over-read across type boundaries.

---

## 8. Correction: the three-way separation is architecture, and it is finer than I stated

Operator correction, 2026-08-17: *"this is how the system is built — it is not just a rule."* Upheld,
and checking Kaizen shows my §7 framing was too coarse.

`KaizenProposal.KaizenCategory` encodes the separation in the type system, and the load-bearing
distinction is not *what the problem is about* but **what action is safe**:

| Categories | Scope | Safety boundary |
| --- | --- | --- |
| `WASTE_REDUCTION`, `SPEED_OPTIMIZATION`, `DEFECT_ELIMINATION`, `BUFFER_TUNING` | factory **runtime parameters** | auto-applicable by `periodicKaizenCycle` |
| `SYSTEMIC_DEFECT`, `KNOWN_PATTERN_VIOLATION`, `ROLE_QUALITY_DRIFT` | factory **source code** | review-only, `expectedGainPercent = 0`, **never auto-applied** |
| `PRODUCT_RUNTIME_DEFECT` | the client **product's** own runtime | review-only, deliberately never folded into `SYSTEMIC_DEFECT` |

The enum states the invariant in its own comment: *fixing the factory's own source code is never a
safe automatic action, unlike this engine's other three categories which only ever tune runtime
parameters.* And `PRODUCT_RUNTIME_DEFECT` carries the operator's own requirement verbatim: *"clearly
marked as a product improvement, not mixed into the factory list."*

Two consequences for this plan, both correcting §7:

1. **`KNOWN_PATTERN_VIOLATION` already does what I proposed to build.** It carries a defect whose
   `rootCausePatternId` matches one of the documented charter patterns, **cited by number**. Stage 3
   should route infrastructure facts into this existing category where they match a charter invariant
   rather than inventing a parallel taxonomy — F59's denominator defect is invariant 8, the nudge
   loop is invariant 7. Findings that match no pattern stay `SYSTEMIC_DEFECT`.
2. **Nothing in Stage 3 may be auto-applicable.** Every infrastructure fact concerns the factory's
   own source or configuration, so it inherits the review-only boundary with
   `expectedGainPercent = 0`. This is now a hard constraint on the stage, not a preference.

### F67 (NEW, factory) — factory-scope findings are filed under whichever client project was active

`KaizenService` line 120 and line 211:

```java
final UUID targetProjectId = (projectId != null) ? projectId : sixSigmaAuditService.getActiveProjectId();
```

A finding with no project — which is what a defect in the orchestrator's own code is — has a client
project substituted for it. The live proposal shows the result:

```
category:        SYSTEMIC_DEFECT
title:           "Gemini observer (platform): Project is in a persistent state of stagnation …"
targetComponent: EneikProductionSys          <- correctly the factory
projectName:     test-forty-ninth            <- but filed under a client project
projectId:       41af381d-5789-404d-b76d-f2c85e3728b0
expectedGain:    0.0                         <- correctly review-only
```

Measured: `GET /api/kaizen/opportunities` returns 4 open proposals, **0 of them with a null
projectId**. The factory has no defect backlog of its own; its defects are scattered across client
projects. Two concrete consequences: the question *"what is wrong with the factory"* cannot be asked
without sweeping every project, and when a project reaches `accepted` or `archived` its findings about
`EneikProductionSys` go with it.

**The observer already types this correctly.** She wrote `(platform)` in the title and set
`targetComponent = EneikProductionSys`. She also wrote, unprompted, in her 14:22Z journal entry:
*"Since this state tracking is handled by the orchestrator/factory and is independent of the
project's own code, this is a platform-scope issue."* The persistence layer discards a distinction
she is already drawing correctly.

**This changes the answer to "how do we make Gemini see and fix it".** She sees it. The repair is not
to teach her, enrich her prompt, or give her more evidence — it is to stop overwriting the type she
declares. `targetComponent` already carries it; `projectId` contradicts it in the same row.

Type: **factory**. Entry mode: `observe_only` — surface the contradiction between `targetComponent`
and `projectId` before changing either. Falsifier: if `getActiveProjectId()` is null in production so
the substitution never fires, then the observed attribution came from the caller and the defect is
elsewhere — this must be measured before any repair.

---

## 9. Stage 2 — live confirmation, 2026-08-17

The first sweep on the new build declared its outcome:

```
2026-08-17T09:30:02.146Z  INFO [PROJECT:41af381d-…]  OpsAuditorService: project test-forty-ninth -
    swept, 0 evidence item(s) from gatherers
    [orphaned_wishlist_behind_failed_task, orphaned_dependency_chain]; ABSTAIN - no decision requested
```

Before this change the same sweep produced nothing at all. Two consecutive sweeps on 2026-08-16
(20:30, 21:00) were silent while three terminally failed tasks sat in the project, and the service's
liveness could not be established from outside by any means.

**Success condition met:** an empty sweep is now distinguishable from an absent sweep.

Volume, measured rather than estimated: `runAuditCycle` iterates only `ProjectStatus.active`
projects, and of 22 projects exactly one is active (12 `frozen`, 7 `accepted`, 2 `waiting`). So the
declaration costs **two lines per hour**, not the ~44 I assumed when weighing it against the F56
noise defect. The margin is wider than the argument needed.

An incidental fact worth recording, because it reframes several earlier observations: the entire
factory currently has **one** active project. Every "the board is unchanged" reading in the
observation log describes the whole factory's work in flight, not one project's share of it.

---

## 10. Stage 3 — the producer already exists, works, and is unreadable

Stage 3 was specified as "build an infrastructure evidence producer". Reading the code before writing
any showed that would have duplicated existing machinery. **`FactorySelfHealthService` already is
that producer**, and it is correct in every respect this plan asked for:

- cron `0 40 * * * ?`, hourly, watching the factory itself rather than the products it builds
- measures the orchestrator's own database: file size, live data, bloat ratio
- escalates through `recordSystemicDefectProposal(null, "Global", …)` — factory scope, done properly
- `SYSTEMIC_DEFECT`, `expectedGainPercent = 0`, review-only, with the boundary stated in its own
  javadoc: *"Detecting a problem in oneself does not license repairing oneself."*
- deduplicates on `lastReportedAssessment` so a persistent condition yields one finding, not one per hour

Its javadoc already names the defect this plan calls D4, and was written to close it: *"This service
detected the factory's own ill health and wrote log.warn — which is the shape of a closed loop with
the closure missing, because nothing reads logs autonomously."*

### It ran, and it was right

```
2026-08-17T09:40:34.021Z  WARN  FactorySelfHealth: database file is 573 MB holding only 59 MB of
    live data (9.6x bloat) - the store is not reclaiming freed space, which happens when it is
    killed instead of closed; a clean shutdown compacts it
```

Measured independently: 585 MB inside the container, `sizeWarnMb = 512` (no override), so
`tooBig = true` and `healthy = false` — the service is firing exactly as designed. Its diagnosis is
also correct in substance: the store is repeatedly killed rather than closed (Docker Desktop stopped;
containers exited 255; `compose up` after each), which is precisely the condition it describes.

### F68 (NEW, factory) — factory-scope findings are recorded successfully and cannot be read by anyone

The escalation succeeded. The log proves it:

```
09:40:34.108Z  [KAIZEN-SYSTEMIC] Recorded review-only systemic defect proposal
               'kz-systemic-a819306c-…' from project null: Factory self-health
```

That proposal appears in **neither** `/api/kaizen/opportunities` **nor** `/api/kaizen/history`. Both
route through:

```java
public Collection<KaizenProposal> getProposalsForProject(UUID projectId) {
    if (projectId == null) {
        projectId = sixSigmaAuditService.getActiveProjectId();   // substitution on READ
    }
    final UUID targetPid = projectId;
    Collection<KaizenProposal> projectProposals = (targetPid == null)
            ? allProposals()
            : allProposals().stream().filter(p -> Objects.equals(p.getProjectId(), targetPid)).toList();
```

Asking for *all* proposals substitutes the *active* project and then filters to exactly it.
`Objects.equals(null, <active-project-uuid>)` is false, so every factory-scope proposal is filtered
out. They become visible only when there is no active project at all — that is, only when the factory
is idle.

Live confirmation of both halves, same hour:

```
09:20:32Z  proposal recorded from project 41af381d…  -> visible in the API
09:40:34Z  proposal recorded from project null       -> stored, invisible in both endpoints
```

**The factory diagnoses itself correctly, records the diagnosis correctly, and no reader — human or
Gemini — can retrieve it.** The closure the service's javadoc set out to add is still missing, one
layer further along than where it was fixed.

### Correction to F67

F67 claimed the substitution was on the **write** path at `KaizenService` lines 120/211. That was
wrong: those lines are inside `recordUnderTheHoodDefects`, a different method (silent telemetry), and
`recordSystemicDefectProposal` does not substitute at all — it passes `projectId` through and handles
null explicitly as `"Global"`. The falsifier declared with F67 is what caught this.

F67 splits:

- **F67 (stands, factory):** `GeminiProjectObserverService:551` calls
  `recordSystemicDefectProposal(project.getId(), project.getName(), …)` unconditionally, so a finding
  the observer herself typed as platform-scope is filed under a client project. `FactorySelfHealthService:108`
  proves the correct call shape exists — `(null, "Global")`.
- **F68 (new, above):** correctly-stored factory-scope findings are unreadable.

The two compound: findings that *should* be factory-scope are given a project (F67), and those that
*are* factory-scope cannot be read (F68). Between them the factory has no legible backlog of its own
in either direction.

### Stage 3 respecified

Not "build a producer". The producer exists and works. Stage 3 is now:

1. **Make factory-scope proposals retrievable.** The read substitution treats "no project specified"
   as "the active project", which silently converts a request for everything into a request for one
   thing. Factory scope needs to be expressible in the query rather than colliding with the null case.
2. **Stop discarding the observer's own type judgement** (F67) — she already writes `(platform)` and
   `targetComponent = EneikProductionSys`.
3. **Only then** consider new evidence kinds, and only for facts `FactorySelfHealthService` does not
   already cover. Its current scope is the database; the lock timeouts (F64) and the endpoint contract
   (F66) are not covered and remain candidates.

Entry mode unchanged: `observe_only`. Nothing here changes what the factory does — only what can be
seen of what it already does.

### F68 closed — live verification, 2026-08-17 10:40Z

```
GET /api/kaizen/factory        -> 200, 1 finding
    SYSTEMIC_DEFECT | Global | "Factory self-health: the orchestrator's own database is unhealthy"

GET /api/kaizen/opportunities  -> 4 findings, unchanged
```

The finding recorded at 09:40:34 — the one that existed in the system and could be retrieved by
nobody — is now readable. The existing project-scope endpoint returns exactly what it returned
before, so nothing that consumed it changed behaviour.

Incidentally established: proposals survive a restart. The 09:40 finding was still present after the
10:40 redeploy, so this is durable state, not an in-memory list.

**The factory now has a backlog of its own.** Its first entry is a real, correct, previously
unreachable diagnosis: the orchestrator's database is 573 MB holding 59 MB of live data, 9.6x bloat,
because the store is repeatedly killed rather than closed. That is a factory problem, not a
value-delivery problem and not a product problem, and it is now filed as one.

### What remains in Stage 3

1. **F67** — `GeminiProjectObserverService:551` still passes `project.getId()` unconditionally, so a
   finding the observer types as platform-scope is filed under a client project. The correct call
   shape exists two files away (`FactorySelfHealthService:108` → `(null, "Global")`). Now that a
   factory surface exists to receive them, this is worth doing; before, it would have moved findings
   from one unreadable place to another.
2. **The observer still cannot see factory-scope evidence.** `readRecentEvidenceNodes` calls
   `findByProjectIdAndCreatedAtAfter(project.getId(), …)`, and `writeEvidenceNode` copies the
   proposal's null projectId onto the node, so factory evidence nodes match no project query. Fixing
   this is **not** a matter of feeding them into her project sweep — that would mix types and, per §7,
   make corroboration across type boundaries possible, which is a category error. It needs her to be
   able to reason about the factory *as the factory*. Design question, not yet specified.
3. **Uncovered facts** — `FactorySelfHealthService` watches the database only. Lock timeouts (F64) and
   the endpoint contract violation (F66) have no producer. These are the only genuinely new evidence
   kinds Stage 3 still needs, and they are far fewer than the six proposed in §3 before the code was
   read.

### F67 closed — 2026-08-17 11:02Z

`GeminiProjectObserverService` now files an **undisputed** platform finding with a null projectId and
`"Global"`, the same call shape `FactorySelfHealthService:108` already used, so it lands on
`GET /api/kaizen/factory` rather than under whichever client project surfaced it.

The **disputed** branch is deliberately unchanged. There the observer self-reported `product` and only
`PlatformSelfReferenceDetector` disagrees; filing it as factory scope would resolve a dispute this
code is explicitly written not to resolve — Charter Pattern #12, *do not trust either side blind* —
and the project is the context that makes the disagreement investigable at all. Scope correctness is
not worth destroying an open question.

The originating project moves into the description rather than being dropped, so the finding stays
traceable to where its evidence was observed without being mis-typed.

**A test asserted the old behaviour and failed.** `platformScopeFindingGoesToKaizenNeverBecomesAWishlist`
verified `recordSystemicDefectProposal(eq(project.getId()), eq(project.getName()), …)`. That assertion
encoded F67 itself. It was changed to `isNull(), eq("Global")` — and strengthened, so the fourth
argument now asserts the originating project survives in the description instead of matching
`anyString()`. The test's actual subject, stated in its own comment — that a platform finding never
becomes a wishlist dispatched against the client's repo — is the
`verify(wishlistRepository, never()).save(any())` line, which is untouched and still passing.

Recording this explicitly because "the test failed so I changed the test" is the move that hides real
regressions. The justification here is that the specification changed deliberately and is written
down as F67; the test's own stated purpose did not.

Verification: 23/23 in `GeminiProjectObserverServiceTest`; `mvn test-compile` exit 0; deployed at
11:01:59Z; `GET /api/kaizen/factory` still returns the self-health finding.

### Stage 3 status

Done: F68 (factory findings retrievable), F67 (observer's platform findings filed at factory scope).

Remaining, unchanged from the previous entry:
- the observer still cannot **read** factory-scope evidence (`findByProjectIdAndCreatedAtAfter` never
  matches a null projectId). Not a routing fix — it needs her to reason about the factory as the
  factory, which is a design question, not yet specified.
- lock timeouts (F64) and the endpoint contract violation (F66) still have no producer.

---

## 11. D6 — testimony recorded as evidence becomes evidence of itself

Found while specifying the remaining Stage 3 item. It is more serious than the item it was found
under, and it explains a behaviour this log has been describing for two days without understanding.

### The measurement

`GET /api/projects/{id}/coherence-graph`, 2026-08-17:

```
total nodes                    53
KAIZEN_PROPOSAL nodes          26
  originating from the observer  10   ("Gemini observer (platform): …")
polarity            NEGATIVE_FINDING 40 · POSITIVE_CONFIRMATION 13
accepted                       53 of 53   (nothing has ever been rejected)
```

Two of the ten, verbatim:

```
"Gemini observer (platform): Systemic platform-level desynchronization where internal task states
 are marked 'd…"
"Gemini observer (platform): Systemic state-desynchronization between internal 'done' task status
 and GitHub PR…"
```

That is the same claim, twice, as two separate pieces of evidence.

### The circle

1. The observer asserts X.
2. X is recorded as a `SYSTEMIC_DEFECT` Kaizen proposal.
3. `KaizenService.writeEvidenceNode` writes an `EvidenceNodeEntity` for it, `sourceType`
   `KAIZEN_PROPOSAL`, carrying the proposal's projectId.
4. On the next cycle `readRecentEvidenceNodes` calls
   `findByProjectIdAndCreatedAtAfter(project.getId(), now-24h)` and returns it.
5. She reads her own prior assertion as evidence, from a *different* sourceType than the one she is
   arguing from — so it raises the distinct-sourceType corroboration count.
6. She restates X, now apparently corroborated. Return to 2.

**This is the mechanism of the quantifier inflation recorded as F51.** On 2026-08-16 her claim
"nearly all 'done' tasks are plagued by operational reality findings" was measured against the board:
**1 of 33**. I recorded that as a reasoning failure. It is not. A claim that manufactures its own
corroboration will strengthen monotonically regardless of the world, and "one" becomes "nearly all"
without anything false being inferred at any single step. The inference is locally valid; the
evidence set is circular.

### Why the existing guard does not reach it

The code already rejects exactly this failure — **within a cycle**:

> *"Termination is deliberately NOT her own self-report ('isolation problem' of pure coherentism — an
> LLM can always generate a plausible 'yes, one more look' even when nothing real changed; this
> system has lived incidents of exactly that failure mode)."*

The within-cycle loop terminates on two external signals: no unseen evidence-node id, and coherence
scores within epsilon. Both are correct and both are scoped to one cycle. **Across cycles the same
isolation problem is unguarded**, and the feedback runs through persistence instead of through the
tool loop, so the guard cannot see it.

### The philosophical form

This is Charter invariant 12 — *an entity producing a result cannot be the sole source confirming its
correctness* — violated by a route the invariant's authors did not anticipate: the entity does not
confirm itself directly, it confirms itself **through the record of its own claim**.

It is also a violation of the Evidence Algebra as written. `OPERATIONAL_MATH_ARCHITECTURE.md` puts
*agent prose, generated title, planned item text* at **strength 1 — intent or claim, not delivery**,
and states *agent claims are never final evidence*. A Kaizen proposal derived from an agent finding
is agent prose that has acquired a database row. The row changes its persistence, not its epistemic
strength; the graph treats the row as a peer of a merged-PR fact.

The deepest form is Tarski's: a language cannot contain its own truth predicate without
contradiction. The observer's findings are statements *about* the evidence stream. When they are
written back into that same stream they stop being a metalanguage and become object-language
sentences, and self-reference is no longer stratified. Charter invariant 7 records the same shape one
level down as a lived incident — *"the audit recognised its own PR with the report as new merged work
and restarted itself endlessly"* — and fixed it with a monotone watermark. The watermark idiom is the
stratification; it was applied to merges and not to evidence.

### What F67 already did to this, unintentionally

Filing undisputed platform findings with a null projectId (F67, deployed 11:02Z) means their evidence
nodes carry a null projectId, and `findByProjectIdAndCreatedAtAfter(project.getId(), …)` cannot match
them. **The loop is therefore already broken for undisputed platform findings.** That was not the
intent of F67 and is worth stating plainly rather than claiming as foresight.

It is not broken for the disputed branch, which still carries the project id by deliberate design
(§F67), and the ten existing nodes retain their old projectId and continue to feed back until they
age out of the 24-hour window.

### Not implemented — and why not today

The repair is to stratify: an evidence node derived from an agent's own finding must not be readable
by that agent as evidence, or must be readable only at its true strength.

I am not making that change now. It alters what the observer sees on every cycle, in a reasoning loop
whose failure modes this log has been wrong about repeatedly, and the operator's instruction is *do no
harm*. Two specific things must be measured first:

1. **How much of her current evidence is her own.** Ten of fifty-three nodes are hers, but the
   question that matters is what fraction of the nodes she actually reads per cycle — the 24-hour
   window, not the whole graph.
2. **Whether `EvidenceCoherenceService` already discounts them.** `acceptedNodes` equals `totalNodes`
   and nothing has ever been rejected, which suggests it does not — but "suggests" is not
   established, and this is precisely the kind of inference this plan exists to stop me making.

Type: **factory**. Entry mode when specified: `observe_only` — measure the self-derived fraction and
surface it before changing what she reads.

## 12. D6 measured — the category error located exactly

Goal restated, unchanged: **a flow free of category errors and a production mechanism that does not
fail.** What follows is one category error, found where the plan said to look, measured rather than
argued.

### Three measurements

**1 — how much of her evidence is her own.**

```
nodes in her 24h read window        46 of 46
  self-derived (her prior findings) 10   = 22%
sourceTypes present in the window   KAIZEN_PROPOSAL 26 · OPERATIONAL_REALITY_FINDING 20
  all 10 self-derived are           KAIZEN_PROPOSAL
```

With two source types in the window, "corroborated by a different source type" is a two-sided coin,
and 10 of the 26 faces on one side are her own earlier words.

**2 — how the mathematics uses that.** `EvidenceCoherenceService` resolves a polarity contradiction
by revising *"the side with LOWER historical entrenchment (fewer **distinct source types** that have
ever corroborated it)"* — `distinctHistoricallyCorroboratingSourceTypes`. Her restatement, typed
`KAIZEN_PROPOSAL`, therefore increases the entrenchment of the position she is arguing for.

**3 — what reliability it is granted.** `sourceReliability(sourceType)` is honestly built — three
tiers, outcome-calibrated where data exists. Measured now:

```
STANDARDIZED + REVERTED = 0        (threshold: coherence.min-reliability-samples = 10)
-> tier-1 outcome calibration does NOT apply
-> falls through to accepted/total over coherence history
accepted nodes = 53 of 53          nothing has ever been rejected
```

So `KAIZEN_PROPOSAL` reliability rests at the top of its range, and every node of that type inherits
it.

### The category error, precisely

`sourceReliability` and `distinctHistoricallyCorroboratingSourceTypes` both key on **sourceType**, and
`EvidenceNodeEntity.sourceType()` is derived from *which foreign key is set*:

```java
if (defectJournalId != null)             return "DEFECT_JOURNAL";
if (codeIntegrityFindingId != null)      return "CODE_INTEGRITY_FINDING";
if (kaizenProposalId != null)            return "KAIZEN_PROPOSAL";
if (geminiFindingId != null)             return "GEMINI_FINDING";
if (operationalRealityFindingId != null) return "OPERATIONAL_REALITY_FINDING";
```

A foreign key records **where a finding is stored**. Reliability and corroboration are properties of
**where a finding came from**. Those are different categories, and the system reads the first as if it
were the second — Ryle's mistake, which Charter invariant 6 already names for numeric types crossing a
serialization boundary. Here it crosses a persistence boundary instead.

The consequence is exact: `FactorySelfHealthService`'s measured 9.6x database bloat and the observer's
prose about "systemic desynchronization" are both stored as Kaizen proposals, therefore both typed
`KAIZEN_PROPOSAL`, therefore granted the same reliability. The Evidence Algebra says they are not the
same: *agent prose, generated title, planned item text* is **strength 1 — intent or claim, not
delivery**, and *agent claims are never final evidence*. The algebra's distinction is correct and the
graph cannot see it, because provenance was never written down.

### The system already declares the right category and never populates it

`sourceType()` includes `GEMINI_FINDING`, and `EvidenceNodeEntity` has a `geminiFindingId` column for
it. **Nothing anywhere sets it** — zero call sites. Her testimony has a dedicated, correct source type
that has never been used; the Kaizen path files it under the storage channel instead.

That also resolves a discrepancy noted earlier in this plan. Her prompt describes *"5 independent
signal sources"*; three appear in the data. The two missing ones are `GEMINI_FINDING` and
`DEFECT_JOURNAL` — declared in the enum, never written. The Barcan condition is honoured in the
declaration and violated in the data, and one of the empty ones is precisely the type that would make
her self-corroboration visible as self-corroboration.

### Why this is not being changed today

The fix is to type by provenance: a node derived from an agent's finding is `GEMINI_FINDING`, not
`KAIZEN_PROPOSAL`. Note `V82__operational_reality_findings.sql` **drops**
`chk_evidence_nodes_exactly_one_source`, so a node may now carry both keys — but `sourceType()`
returns the first match in its if-chain, so making provenance win requires reordering that chain, and
that reorders the type of **every existing node**, changing entrenchment and reliability for the whole
graph at once.

That is a live reasoning system whose failure modes this log has misjudged repeatedly, and the
standing instruction is *do no harm*. Changing corroboration counts and source reliabilities in one
step, on my own judgement, is the opposite of the discipline this plan exists to impose.

**Specified, not implemented.** The safe sequence, in order:
1. Populate `geminiFindingId` on nodes derived from her findings **in addition to** `kaizenProposalId`,
   changing no reads. Provenance becomes recorded fact; nothing consumes it yet. `observe_only`.
2. Measure the divergence: how many nodes would change type, and what that does to entrenchment on
   the contradictions currently in the graph.
3. Only then decide whether `sourceType()` should prefer provenance — with the measurement in hand
   rather than the argument above.

Step 1 is additive and reversible. Steps 2 and 3 are not, and neither should be taken on inference.

### Plan corrections carried out here

- **§3's proposed evidence vocabulary is reduced again.** Before writing any new source type, the two
  already declared and empty must be populated. Adding a sixth while two sit unused would repeat the
  defect rather than fix it.
- **D1 is amended.** It said the observer's limit is that infrastructure facts are unrepresentable.
  True, and incomplete: representable facts are *mis*-typed as well. Enriching the vocabulary without
  fixing provenance would add sources whose evidential weight is again decided by their storage
  channel.
- **F51 is re-explained, not retracted.** The measurement stands — "nearly all" against 1 of 33. The
  cause was recorded as a reasoning failure on her part; it is structural. A claim that manufactures
  its own corroboration strengthens regardless of the world.

## 13. Correction, and the deeper result: assertions are not objects in this ontology

### Correction to §12

§12 states that `V82__operational_reality_findings.sql` **drops**
`chk_evidence_nodes_exactly_one_source`, so a node could carry both keys. **That is wrong.** V82 drops
it and re-adds it in the same migration, widened to include the new column:

```sql
ALTER TABLE evidence_nodes DROP CONSTRAINT chk_evidence_nodes_exactly_one_source;
ALTER TABLE evidence_nodes ADD CONSTRAINT chk_evidence_nodes_exactly_one_source CHECK (
    (CASE WHEN defect_journal_id IS NOT NULL THEN 1 ELSE 0 END
   + … + CASE WHEN operational_reality_finding_id IS NOT NULL THEN 1 ELSE 0 END) = 1
);
```

Exactly one source per node is still enforced at the database level. **The "safe first step" specified
in §12 — populate `geminiFindingId` in addition to `kaizenProposalId`, changing no reads — is
impossible.** The database would reject every such row. Recorded here rather than silently amended,
per the corrections rule adopted in §1.1.

### What blocks it is not the constraint

Even with the constraint gone, the step would have been wrong. `V79__kaizen_proposals_and_evidence_nodes.sql:26`
says so in its own comment:

```
-- is set per row (gemini_finding_id has no FK target yet - Gemini findings become WishlistEntity today).
```

`gemini_finding_id` is a bare UUID column with **no referent to point at**. Populating it would have
meant minting a name for a thing that does not exist — a designator with no bearer. That is precisely
what `BARCAN-TAG-01_ACTUALIST-OBJECT` forbids, and what `BARCAN-TAG-02_RIGID-DESIGNATOR` requires the
opposite of: a designator must pick out the same thing in every context, and an invented UUID picks
out nothing in any of them.

### The result this exposes

**This system has no representation for "an agent asserted P".**

Her findings exist only as what was *done about* them — a `WishlistEntity` (work created) or a
`KaizenProposal` (proposal created). Both are the **uptake** of the assertion, not the assertion. So
when the evidence graph asks "what kind of thing is this", the only answer available is the kind of
action that followed, and reliability is then computed from that.

This is Austin exactly, and the corpus already carries the rule.
`DZHON_OSTIN_02_CATEGORY_ERROR_SCAN`, in the factory's own words:

> *"Reject code that treats a process as an object, **an observation as authority** or a policy as
> data without an adapter. Proof obligation: **Point to the type, schema or adapter that preserves the
> category boundary.**"*

Discharging that proof obligation is what fails. There is no type, schema or adapter that preserves
the boundary between *she asserted it* and *a measurement established it*, because one of the two
categories has no representation. The evidence algebra distinguishes them — agent prose is strength 1,
measurement is 3 — and the schema cannot.

So D6 is not a mis-set field. **It is a missing entity.** That is why the reliability of her prose
equals the reliability of `FactorySelfHealthService`'s 9.6x bloat measurement: not because someone
chose to weigh them equally, but because the system has no way to say they are different kinds of
thing.

### Specified: the minimal schema change

Persist agent findings as findings — a table `gemini_findings` (id, projectId, scope as she declared
it, summary, evidence text, severity, createdAt), the FK target `gemini_finding_id` was written for
and never given. Then:

- an evidence node derived from her assertion carries `geminiFindingId` and types as `GEMINI_FINDING`;
- `sourceReliability("GEMINI_FINDING")` calibrates on her own outcome record, separately from
  `KAIZEN_PROPOSAL`;
- `distinctHistoricallyCorroboratingSourceTypes` stops counting her restatement as a second
  independent source, because it is now visibly the same source;
- the two declared-but-empty source types drop to one, and the Barcan condition is honoured in the
  data as well as the declaration.

None of this changes what she is shown or how the flow runs; it changes what the graph knows about
where a claim came from. It is nonetheless a migration plus a new entity plus a write path, and it
touches the reliability mathematics the moment `GEMINI_FINDING` starts appearing. **Not implemented —
this is the operator's call, and it is the first item in this plan that cannot be done additively.**

### What is safe to do next instead

Two items remain that are additive and blocked by nothing:

1. **F64 — lock timeouts have no producer.** 21 occurred, 17 on `PROJECTS`, all invisible outside the
   H2 trace file. `FactorySelfHealthService` is the natural home: it already watches the factory's own
   database, already escalates factory-scope, already carries the review-only boundary.
2. **F65 is already producing correctly** and is now readable via `/api/kaizen/factory` — the 9.6x
   bloat finding is live. Nothing more is needed there.

Item 1 is the next implementation step under the existing, unchanged goal: a flow without category
errors, and a production mechanism that does not fail silently.

## 14. F64 closed, and F69 found by closing it

### F64 — lock contention now has a producer, verified live 16:40Z

```
16:40:27Z  WARN  FactorySelfHealth: database file is 784 MB holding only 60 MB of live data (12.9x bloat)
16:40:31Z  WARN  FactorySelfHealth: the orchestrator's own store recorded 21 lock timeout(s)
                 (standing total at first observation)
```

Twenty-one is exactly the count measured in `eneik_db.trace.db`. The signal that existed only in a
file nobody opens now reaches `GET /api/kaizen/factory` as a factory-scope, review-only finding.

Note the database is deteriorating quickly: **573 MB / 9.6x at 09:40 → 784 MB / 12.9x at 16:40**, on a
host with 3917 MB total. This is F65 and it is getting worse, not stable.

### F69 (NEW, factory) — the factory backlog can hold exactly one finding

Two proposals were recorded seconds apart:

```
16:40:27.660Z  [KAIZEN-SYSTEMIC] Recorded … 'kz-systemic-6a6181a1-…'   (database bloat)
16:40:31.631Z  [KAIZEN-SYSTEMIC] Recorded … 'kz-systemic-7d3ca51e-…'   (lock contention)
```

`GET /api/kaizen/factory` returns **one** — `7d3ca51e`. The bloat finding, recorded four seconds
earlier, is not shown. The cause:

```java
String key = p.getCategory() + ":" + p.getTargetComponent();
```

`recordSystemicDefectProposal` hardcodes `targetComponent = "EneikProductionSys"`, so **every**
factory finding collapses to the single key `SYSTEMIC_DEFECT:EneikProductionSys`. The factory backlog
is structurally capped at one item regardless of how many distinct defects exist, and each new one
silently displaces the last. That is why the observer's platform finding vanished when the
lock-contention finding arrived.

This is the same category error as D6, one level up: **the key identifies the component a finding is
about, not the finding.** Two different defects in the same component are treated as the same
finding — the key is a designator of the subject, not of the claim, so it cannot rigidly pick out
what it is used to identify (`BARCAN-TAG-02_RIGID-DESIGNATOR`).

It also negates most of F68's value. Making the factory backlog readable achieves little while the
backlog can only ever show its most recent entry.

**Not fixed yet.** The dedupe key is shared with project-scope proposals, where collapsing repeated
`ROLE_QUALITY_DRIFT` reports for one role is plausibly intentional. Changing the key globally would
change what the existing dashboard shows. The impact must be measured before the key is touched —
specifically, how many currently-hidden proposals would become visible, and whether any category
relies on the collapse.

### F69 closed — live verification 17:40Z

```
GET /api/kaizen/factory  -> 3 findings

  EneikProductionSys                   Gemini observer (platform): Systemic platform-level desync…
  EneikProductionSys/database-storage  Factory self-health: the orchestrator's own database is unhealthy
  EneikProductionSys/database-locks    Factory self-health: lock contention on the orchestrator's own …
```

Three distinct factory findings coexist where the backlog previously held exactly one. The observer's
platform finding, which had been displaced twice, is present again alongside both self-health
findings.

The dedupe key was not touched. Only the designator changed, and only for callers that know what
their finding is about.

### A deployment trap worth recording

The first build of this fix returned `BUILD_EXIT=1` (the WSL bridge died mid-build), and the same
command then printed `backend UP` — because `docker compose up -d` started the **previous** image and
the health check passed against it. Reading the last line would have meant reporting a successful
deployment of code that was never built.

Same class as the earlier `docker ps` trap: a failed step upstream produces output downstream that
looks like success. Added to `WATCH_PROTOCOL.md`'s falsifier set in spirit — **a build's exit code is
the authority on whether an image changed; a health check is not.**

## 15. F66 RETRACTED — it was my measurement error, not a defect

`GET /api/projects/{id}/recent-activity` returns:

```
keys: ['projectId', 'lines']
lines: 100
```

One hundred lines, correctly. It is an in-memory `LogScopeBuffer`, not a database query. My original
check parsed the response for `activities` / `content` / `events`, found none of them, and I recorded
"an endpoint that answers 200 with an empty set" as a finding.

The endpoint was never broken. **I measured it wrongly**, and — the part worth recording — I did so in
the same batch of findings where I diagnosed F64 as *"measured in a source that does not carry the
signal"*. I committed the error I was in the act of naming.

Added to the corrections register (§1.1):

| My claim | What was true | The error |
| --- | --- | --- |
| "F66: `/recent-activity` returns 200 with 0 items" | It returns 100 lines under the key `lines` | Parsed for keys the endpoint does not use, then reported absence as a defect |

Consequence for Stage 3: the remaining producer list is now **empty**. F64 is done and F66 does not
exist. Stage 3 is complete apart from D6, which needs a migration and is the operator's decision.

## 16. Where the project actually moves — measured, not inferred

Asked directly: which part of this plan moves the project. The honest answer required a measurement,
and it is not flattering to the plan.

```
completeFeatures       5 / 6
mergedPlannedTasks     25 / 26
openWishlistCount      0
blockedItems           1  ->  Runtime Contract 8becdc01 | done_not_reached_main
falsificationEligible  false   (threshold 0.9, readiness 0.833)
```

**The project is one item short on both axes.** Not five - the five `failed` tasks are not what holds
it. One task, and it is not failed:

```
id                  f163e834-dbc7-46cf-8f1e-163f97bf17c6
title               Runtime Contract 8becdc01
status              done
role                BARCAN-TAG-01
featureId           null
julesSessionName    null
julesDispatchStatus null
```

A task marked `done` that **was never dispatched** - no Jules session, no dispatch status, no PR, no
feature. It reports completion and has no evidence of any work at all. `done_not_reached_main` is the
readiness invariant catching precisely that, and it has been the single blocked item since
2026-08-16.

### The answer to the question

**No stage of this plan moves the project.** Stages 1-3 repair the factory's knowledge of itself:
what it can measure, what it can read, what it can distinguish. That work is real - four defects
closed today, all verified live - and none of it dispatches a task.

What moves this project is resolving `f163e834`. At 5/6 features, completing the sixth takes readiness
to 1.0, which is the threshold the design shop waits on and above the 0.9 the philosophical track
waits on. **One item stands between this project and both gates opening.**

That reframes the plan honestly rather than changing its goal. The goal is unchanged: a flow without
category errors and a production mechanism that does not fail silently. `f163e834` is an instance of
exactly that goal - a task asserting `done` with no evidence is the substitutivity error the corpus
forbids, `task done` standing in for `value delivered`. It is the goal's own test case, sitting in the
live project.

**Not yet established:** whether `f163e834` is the task holding the sixth feature incomplete. It has
`featureId: null`, so it cannot be attached to any feature, and the relation between it and the
missing feature is unmeasured. That measurement comes before any action on it.

## 17. Correcting §16 — the plan does move the project, through D1

§16 concluded "no stage of this plan moves the project". That was too literal an answer, and tracing
the chain properly shows it is wrong.

### Why Gemini cannot currently act on the blocking item

```
nodes in the evidence graph naming f163e834 / 8becdc01   0
OPERATIONAL_REALITY_FINDING nodes                        7
   all of the form "Session <id> status disagreed with real GitHub PR state for task <id>"
```

The blocking task has `julesSessionName: null` — it never had a session. The operational-reality
detector compares a session's status against GitHub's PR state, so for this task there is nothing to
compare and it can never emit. And `OpsAuditorService` only gathers evidence about `failed` tasks and
orphaned wishlists, while this task is `done`.

So **no producer in the factory can generate evidence about the one item blocking the project**, and
the measurement confirms it: zero nodes name it.

### The detection exists and reaches only a screen

`ClientDeliverableReadinessService` **does** detect it — `done_not_reached_main` has been the single
entry in `blockedItems` since 2026-08-16. Who consumes `blockedItems`:

```
src/main/java/com/eneik/production/dto/dashboard/ProductReadinessDto.java:16   List<BlockedItemDto> blockedItems
```

A DTO. Nothing else. The invariant that names the exact thing blocking the project reaches a dashboard
field and no reasoner. That is defect D4 — *a signal with no reader is not monitoring* — sitting on
the most consequential signal in the system.

### So the answer is yes, and it is D1

D1's repair — making facts representable as evidence — is exactly what would put `done_not_reached_main`
in front of her. She has been reporting this class for two days ("internal task states marked 'done'
while operational reality findings confirm conflicts with GitHub PR status"). On 2026-08-16 I measured
her claim as 1 of 33 and filed it as quantifier inflation.

**The one was real, and it is this task.** She was right about the instance and wrong about the
quantifier — and D6 explains the quantifier: with her own restatements corroborating her, "one"
inflates. Fix D6 and the same finding deflates to a precise, actionable claim about one task. Fix D1
and she has the evidence to make it at all.

F67/F68/F69 already did their part of this: her platform findings now persist instead of displacing
each other, are filed at factory scope, and are readable. Before today each new finding erased the
last, which is why this one never accumulated into anything.

### Both paths terminate at the same wall

Building the D1 producer additively is blocked, and by the same thing D6 is blocked by:

```
OperationalRealityFindingEntity:32   @Column(name = "jules_session_id", nullable = false)
V82__operational_reality_findings.sql:9   jules_session_id UUID NOT NULL
```

The evidence type for *"the record disagrees with reality"* structurally **requires a session**. It can
express "session says X, GitHub says Y" and cannot express "the task claims done and nothing ever
happened". The predicate cannot represent the case — the same shape as F62, D1 and D6, now on the
decisive instance.

D6 needs `gemini_findings` to exist so testimony can be typed as testimony. D1 needs an evidence type
that does not presuppose a session. **Two independent lines of this plan converge on one migration to
the evidence schema**, and that convergence is the argument for doing it: it is not one feature's
plumbing, it is the thing standing between the factory and its own ability to see two different
classes of fact.

That is the operator's decision, and it is now supported by measurement on a live blocking instance
rather than by argument.

### Goal unchanged

A flow without category errors and a production mechanism that does not fail silently. `f163e834` is
that goal's own test case: a task asserting `done` with no evidence of any work, invisible to every
mechanism built to catch exactly that, while the invariant that detected it talks only to a screen.
