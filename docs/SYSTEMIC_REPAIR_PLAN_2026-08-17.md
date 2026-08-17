# Systemic Repair Plan

**Current state as of 2026-08-17 19:10Z.** This file is rewritten rather than appended when work
progresses, so what it says is what is true now. The narrative record of how each finding was reached
lives in `docs/reports/WORKPLAN_2026-08-15_repair.md`; this file holds the analysis and the remaining
work.

## 0. Goal and boundary

**Goal, unchanged:** a flow free of category errors, and a production mechanism that does not fail
silently.

Inherits the Non-Negotiable Boundary of `OPERATIONAL_MATH_ARCHITECTURE.md`: nothing here changes
`TaskStatus` or `WishlistStatus`, dispatches Jules, calls GitHub write APIs, triggers AutoMerge, or
treats an LLM claim as final evidence of delivery. Every item declares its mode on the existing
Promotion Policy (`observe_only → warn_only → soft_gate → hard_gate → auto_remediate`); nothing has
entered above `warn_only`.

Grounding: `OPERATIONAL_MATH_ARCHITECTURE.md` (evidence algebra, invariant catalogue, promotion
policy), `ENGINEERING_INVARIANTS_CHARTER.md` (invariants 1–15), `docs/philosopher-patterns/` (86 role
files under the BARCAN taxonomy).

---

## 1. Corrections register

Every retraction lives here, in one place. A retracted finding is never cited again without its
retraction. This register exists because corrections were previously written inline, leaving a wrong
entry and its retraction hundreds of lines apart with equal authority.

| Claim | What was true | The error |
| --- | --- | --- |
| "The project will stall when tasks close" | `ProjectStatus` has no stalled state; `active` is the working state, ended only by a human | Predicted a state that does not exist, without reading the enum |
| "Design is blocked" | Two independent services; falsification was running and calling Stitch throughout | Merged two referents under one word |
| "F43's budget is verified — 2 nudges in 6h" | 39 nudges in the next hour | Two samples inside a backoff interval published as a termination proof |
| "Narrow pipeline, not a stall" | The session went stale; the task died 90 minutes later | A present-tense observation published as a forecast |
| "The board is fully inert" | New task claimed and 2 observer findings within 30 minutes | Three identical snapshots published as a system property |
| "Zero lock timeouts" (every pass) | 21 timeouts, 17 on `PROJECTS`, in the H2 trace | Measured in a source that does not carry the signal |
| "Both recovery exits are closed" | `OpsAuditorService` recovered the task 52 minutes later | Enumerated two paths and declared the set exhaustive |
| **F62** "a failure with surviving siblings is permanently invisible" | The second gatherer does not test siblings; the real gate is `isDependencySatisfied` | Read one gatherer, generalised to the mechanism |
| **F66** "`/recent-activity` returns 200 with 0 items" | It returns 100 lines under the key `lines` | Parsed for keys the endpoint does not use, then reported absence as a defect |
| **F67** "the substitution is on the write path (`KaizenService:120`)" | That line is in `recordUnderTheHoodDefects`; the substitution is on the **read** path | Attributed a measured effect to the first plausible line |
| "V82 drops the exactly-one-source constraint" | V82 drops and immediately re-adds it, widened | Read the DROP and not the ADD two lines below |
| "No stage of this plan moves the project" | D1 is precisely what lets the observer act on the blocking item | Answered a question about consequence with a fact about direct causation |

Seven of the first eight share one form: **a claim whose truth conditions had not been established**.
That is the same defect this plan repairs in the system, which is why it is organised by defect rather
than by finding.

---

## 2. The defects

### D1 — the evidence predicate is the limit, not the reasoner — *partially repaired*

`¬representable(p, E) → ¬decidable(p, O)`. The observer reasons only over `EvidenceNodeEntity` rows.

Repaired: infrastructure facts now reach the graph. `FactorySelfHealthService` — which already existed
and was correct — escalates database health and, since F64, lock contention; both factory-scope,
review-only, `expectedGainPercent = 0`.

**Remaining:** the readiness invariant `done_not_reached_main` still has no producer. It is detected
correctly and consumed only by `ProductReadinessDto` — a dashboard field, no reasoner. V103 made it
expressible; nothing writes it yet. This is the item that moves the live project (§5.2).

### D2 — repeated actions carry no well-founded measure — *not repaired, deliberately untouched*

`∃μ: State → ℕ` with `μ(s') < μ(s)` on every application, else no termination proof exists and the
loop ends only by an external mechanism, at a cost.

Three tasks died in nine hours to ~60 forced nudges each at a fixed 60-second interval, against a
configured bound of 2 (`jules.forced-unblock-max-attempts`). Cause **not established**. Charter
invariant 7 names the class.

Untouched on purpose: the one prior attempt to repair this mechanism on an unmeasured hypothesis added
a duplicate `forced_unblock_attempts` column and broke the migration and every integration test. The
distinction any repair must preserve: a **retry** needs a bound, a **sweep** must never be bounded, a
**terminal action** happens once.

### D3 — the metric denominator is not a declared set — *not repaired*

Charter invariant 8: *enumerate explicitly which statuses are excluded from the denominator, and why.*

Measured: retiring a planned task dropped `totalPlanned` 27 → 26 with nothing merged, raising
`mergedRatio` 0.926 → 0.962 and flipping a feature to complete. **Failing work improved the reported
readiness.** Readiness gates self-falsification (0.9), the design shop (1.0) and the philosophical
track.

Invariant 8 as written covers only the opposite hazard. The refinement this system needs: exclusion is
legitimate when the work is *not required*, illegitimate when it is *required and undelivered*. This is
*limits of substitutivity* — `task retired` may not stand in for `value not required`.

### D4 — signals exist with no declared consumer — *partially repaired*

`signal(s) ∧ ¬∃reader(s) → ¬monitoring(s)`.

| Signal | Reader | State |
| --- | --- | --- |
| Factory self-health (database, locks) | `/api/kaizen/factory` | fixed (F68, F69) |
| Auditor sweep outcome | log, decided | fixed (Stage 2) |
| `SYSTEM STALLED: no forward progress` | log only | open |
| `FLAGGED FOR HUMAN REVIEW` | log only | open |
| `done_not_reached_main` | dashboard DTO only | open — **and it is the blocking one** |

### D5 — claims made without stated truth conditions — *repaired, both halves*

Machine: `auditProject` returned silently on empty evidence, making "swept and found nothing", "did not
sweep" and "service stopped" one indistinguishable state. It now declares an ABSTAIN carrying its own
witness. Confirmed live 09:30:02Z.

Operator: `WATCH_PROTOCOL.md` declares every proposition the watch rules on, its authoritative source
and its falsifier. A proposition absent from that register has not been ruled on, and silence about it
is ABSTAIN.

### D6 — testimony recorded as evidence becomes evidence of itself — *schema repaired, write path pending*

Measured before V103:

```
her 24h read window          46 nodes
  her own prior findings     10  = 22%
sourceTypes present          KAIZEN_PROPOSAL 26 · OPERATIONAL_REALITY_FINDING 20
STANDARDIZED + REVERTED       0  (threshold 10)  -> outcome calibration inactive
accepted                     53 of 53           -> reliability rests at the top of its range
```

`sourceReliability` and `distinctHistoricallyCorroboratingSourceTypes` key on `sourceType`, and
`sourceType()` is derived from which FK is set — from **where a finding is stored**, while reliability
is a property of **where it came from**. Her prose inherited the reliability of measurement-derived
proposals, and her restatement counted as a second independent source corroborating her own position.

**That is the mechanism behind F51's quantifier inflation**: the claim measured 1 of 33 while asserting
"nearly all". A claim that manufactures its own corroboration strengthens regardless of the world, and
no single inference step is invalid.

Charter invariant 12 (*independent verification, not self-attestation*) violated by a route its authors
did not anticipate — the entity confirms itself **through the record of its own claim** — and a
violation of the evidence algebra, which puts agent prose at strength 1, *intent or claim, not
delivery*. In the corpus's own words (`DZHON_OSTIN_02_CATEGORY_ERROR_SCAN`): *reject code that treats an
observation as authority without an adapter; point to the type, schema or adapter that preserves the
category boundary.*

**V103 supplies that adapter.** `gemini_findings` now exists as the referent
`evidence_nodes.gemini_finding_id` was created for in V79 and never given.

---

## 3. Closed findings

| # | Defect | Repair | Verified |
| --- | --- | --- | --- |
| F64 | 21 lock timeouts existed only in `<db>.trace.db` | producer in `FactorySelfHealthService`, scheduled path only — kept off `inspect()`, which is on `/verdict`; monotone high-water mark (Charter invariant 7) | 16:40Z live |
| F67 | undisputed platform findings filed under a client project | filed with null projectId and `"Global"`; disputed branch deliberately unchanged (invariant 12 — do not resolve a dispute the code exists not to resolve) | 11:02Z live |
| F68 | factory-scope findings recorded successfully, retrievable by nobody | `getFactoryProposals()` + `GET /api/kaizen/factory`, a separate route because factory and project are different types | 10:40Z live |
| F69 | the factory backlog held exactly one finding | the dedupe key was correct; the **designator** was not — `targetComponent` was the whole system for every factory finding | 17:40Z live, 3 coexisting |
| — | auditor silence undecidable | ABSTAIN with witness | 09:30Z live |

`V103` applied cleanly to the live database at 19:02:36Z (`now at version v103`), after validation on a
throwaway H2 2.2.224 — the production version — and a full backup
(`data/eneik_db.mv.db.pre-V103`, 892 MB).

---

## 4. Type discipline

The separation is architectural, encoded in `KaizenProposal.KaizenCategory`, and its load-bearing axis
is **which action is safe**, not what the problem is about:

| Categories | Scope | Boundary |
| --- | --- | --- |
| `WASTE_REDUCTION`, `SPEED_OPTIMIZATION`, `DEFECT_ELIMINATION`, `BUFFER_TUNING` | factory **runtime parameters** | auto-applicable |
| `SYSTEMIC_DEFECT`, `KNOWN_PATTERN_VIOLATION`, `ROLE_QUALITY_DRIFT` | factory **source code** | review-only, `expectedGainPercent = 0`, never auto-applied |
| `PRODUCT_RUNTIME_DEFECT` | the client **product's** runtime | review-only, never folded into `SYSTEMIC_DEFECT` |

The enum states the invariant itself: *fixing the factory's own source code is never a safe automatic
action*. `FactorySelfHealthService` restates it: *detecting a problem in oneself does not license
repairing oneself.*

Two consequences constraining the remaining work:

- `KNOWN_PATTERN_VIOLATION` already routes a defect to a charter pattern **by number**. Findings that
  match a charter invariant belong there, not in a parallel taxonomy.
- Corroboration must be computed **within** a type. A factory fact corroborating a product claim is a
  category error (invariant 6, Ryle). Without that constraint, enriching the evidence graph would
  worsen the observer's quantifier inflation rather than fix it.

The observer already types correctly and unprompted — *"this state tracking is handled by the
orchestrator/factory and is independent of the project's own code, this is a platform-scope issue"*.
The repair was never to teach her; it was to stop discarding what she declares.

---

## 5. Remaining work, in order

Ordered by safety of the repair, because acting on an unmeasured hypothesis in this system has done
more damage than the defects being fixed.

### 5.1 — Write path for `gemini_findings` *(next, additive)*

The table exists; nothing writes it. Until something does, her testimony still enters the graph typed
by its storage channel. Closes D6.

Success condition: a node derived from her assertion types as `GEMINI_FINDING`, and `sourceReliability`
calibrates it separately from `KAIZEN_PROPOSAL`.

### 5.2 — Producer for `done_not_reached_main` *(the item that moves the project)*

V103 made a session-less reality finding expressible. A task asserting `done` with no merge evidence can
now become an `OperationalRealityFindingEntity`, and therefore an evidence node.

**Live instance:** `f163e834` "Runtime Contract 8becdc01" — status `done`, `julesSessionName` null,
`julesDispatchStatus` null, no PR, no featureId. Detected since 2026-08-16; zero evidence nodes name it;
no producer could express it. The project stands at 5/6 features and 25/26 merged tasks with this as its
single blocked item. At 6/6, readiness reaches 1.0 — the design shop's threshold, and above the
philosophical track's 0.9.

Success condition: the blocking item appears in the evidence graph and the observer can name it.

### 5.3 — Instrument the nudge divergence (D2) *(measurement only, no fix)*

### 5.4 — Declared denominator (D3) *(`warn_only`, last — it touches the calculation three subsystems gate on)*

### Not scheduled

`OpsAuditorService` predicate coverage. F62 is retracted; the real gate is `isDependencySatisfied`, true
when a task with the same role, `featureId` and `ems_semantic_key` has merged. Whether the two
long-standing failures are correctly satisfied by a semantic equivalent or falsely matched is
**unmeasured**, and that measurement precedes any repair.

---

## 6. Falsifiers

A plan whose outcome is fixed by construction is not a plan.

- **5.2 is wrong** if a producer for `done_not_reached_main` changes nothing observable — i.e. if the
  observer had the fact available by a route not yet found.
- **D2 is wrong** if the persisted counter is correct and bounded and the ~60 messages come from a path
  that never consults it; the defect would then be dispatch topology, not measure.
- **D3 is wrong** if the retired task was genuinely not required, in which case invariant 8 applies in
  its original direction.
- **D6 is wrong** if typing testimony separately leaves corroboration counts and reliabilities
  unchanged — i.e. if the graph never actually used the mis-typing.
- **The whole plan is wrong** if the factory's problems are productivity problems rather than
  self-knowledge problems. Evidence against: the single item blocking the live project was detected
  correctly by an existing invariant and could reach no reasoner.

Each falsifier is a measurement, not an argument.
