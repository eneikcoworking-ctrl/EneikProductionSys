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

### Stage 2 — Make silence decidable *(additive, `observe_only`)*

Covers D5 machine half. The auditor emits a decided outcome for every sweep, including "swept, zero
evidence". Today `auditProject` returns silently when `gatherAllEvidence` is empty, so from outside
"swept and found nothing", "did not sweep" and "service stopped" are one indistinguishable state.

Smallest possible change, purely additive, no behaviour altered. Chosen before Stage 3 because
Stage 3's instrumentation is unreadable without it.

Success condition: from outside, an empty sweep is distinguishable from an absent sweep.

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
