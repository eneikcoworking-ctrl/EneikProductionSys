# Work plan: repairing the factory, 2026-08-15

Every finding in `FINDINGS_2026-08-15_declared_vs_actual.md` is assigned to a step here. Nothing is
deferred. Steps are ordered so each is verifiable alone and none depends on a later one; risk rises
monotonically, and the factory can be stopped after any step with a consistent system.

**Already fixed, listed for completeness:** F9 (a literal `%` broke every compiler prompt - fixed in
`64d34ee`), F10 (one keyword list serving two roles - fixed in `64d34ee`), F20 (conflict/CI deadlock -
fixed in `821f12b`, deployed, **not yet witnessed live** because no conflict has occurred since; its
verification belongs to Step 3).

**What a fresh reading changed.** F1, F11 and half of F28 are one mechanical defect - an operation's return
value is written to a log instead of acted on. And the verdict lattice is not a separate plan but the
container for the rest; since its first stages are read-only it is built as an observer early, where it
costs nothing and immediately supplies the missing place where layers reconcile.

---

# PHASE I · STOP THE LOSSES

Work is being destroyed and quota burned right now. Nothing else matters until this stops.

## Step 1 · An operation's effect must be verified, not logged
**Closes F1, F11, F12.** Risk: low. Factory may run.

`commitFile` is documented create-only ("no `sha`, so this is a create, not an update"), written for
timestamped design assets. Bootstrap reused it for fixed paths, one of which (`.gitignore`) already exists,
so the write fails and the failure is only logged - while `task.setStatus(done)` has already run on the
strength of a different file.

1. Add **`upsertFile`** as a *new* method: read current `sha`, include it, fall back to create when absent.
   Do **not** change `commitFile` - fourteen call sites depend on create-only semantics, and design-draft
   promotion needs a second write to *fail* rather than silently overwrite an approved mockup.
2. Both deterministic scaffold methods switch to `upsertFile`.
3. `completeBootstrapDeterministically`: `task.setStatus(done)` moves **after** all scaffold commits and
   fires only if every one returned true. A partial bootstrap stays queued - the existing working fallback.

**Verify:** new throwaway project; `.gitignore` on `main` contains `target/` and `data/`; `bootstrap.md`,
`pom.xml`, `application.properties` all present. Today `.gitignore` has exactly one commit on every project.

**Why first:** upstream of the conflicts. No skeleton → each task invents one → build artifacts committed →
every pair of compiling tasks conflicts (F12).

## Step 2 · The design shop stops burning quota
**Closes F28.** Risk: low, one isolated service. Factory may run.

Replace the identity test with a property test and split failures by modality:

```
usable(r)      ⟺ r.available ∧ repoDraftPath ≠ ∅ ∧ implementableHtmlExists(repoDraftPath)
unavailable(r) ⟺ ¬r.available                    → retry within a declared budget
wrongKind(r)   ⟺ r.available ∧ ¬usable(r)        → record, escalate once, never retry
```

The model's name leaves the gate and becomes evidence in the reason. The loop closes because **no quantity
of retries changes the kind of a thing**.

**Verify:** the same cause must not produce a second identical retry. Today: six identical failures in four
hours, each a real generation call.

## Step 3 · Conflicts: stop destroying work, then fix the mathematics
**Closes F22 entirely; verifies F20.** Risk: medium - `AutoMergeService` has documented incident history.
Factory idle.

Stage 4 of the original order comes **first**, because it is the only part that stops loss rather than
improving decisions about work already gone.

1. **Never perform an irreversible action to resolve uncertainty.** Escalation triggers Branch GC, which
   retires the branch and destroys the work. Preserve and mark. (PR#12 was consumed this way: plan 21 → 19.)
2. One ownership definition replacing three, grounded in declarations the system already makes:
   `substitutable(f,t) ⟺ f ∉ fileScope(t) ∧ ¬∃ live claim on f`.
3. Delete `ConflictEntropyCalculator` or reduce it to an observation with no documented threshold.
4. Gate becomes `n = 0`.
5. Mechanical: `setResolutionAttempts` increments rather than assigns; Tier-1 sync uses the PR's real
   `baseRef` instead of hardcoded `"main"`; the comment claiming a bound of `== 0` reconciled with the
   code's `< 10`; "unrecoverable" becomes "budget exhausted".
6. **Witness F20**: construct a conflicting PR deliberately and confirm `handleMergeConflict` now fires.

**Verify:** all 17 `AutoMergeServiceTest` cases stay green - they encode the two prior deadlock fixes. New
cases for `substitutable` over a synthetic `fileScope`.

## Step 4 · The database stops growing, and the rollback errors stop
**Closes F7.** Risk: low-medium. Factory stopped for the compaction.

Two live problems in one place. `JpaSystemException: Unable to rollback against JDBC Connection` repeats
roughly every 15 minutes on coverage-audit eligibility - the very gate that precedes falsification. And the
H2 file is **400 MB today against 101 MB after yesterday's compaction** - fourfold in a day.

1. Diagnose the rollback failure: connection-pool exhaustion, a closed connection, or a transaction held
   across something slow. The 2026-08-14 incident had the same signature at a larger scale.
2. Measure what is actually growing, table by table, before touching anything.
3. Extend retention if the growth is in an append-only table the current policy deliberately spares -
   `ProjectEventLogRetentionService` retains by meaning and never touches unaccepted projects, which is
   correct and also why it may not be catching this.
4. Clean shutdown + `SHUTDOWN COMPACT`, verifying row counts before and after.

**Verify:** zero rollback exceptions over an hour; file size proportional to live data.

---

# PHASE II · MAKE THE INPUTS HONEST

Four subsystems report numbers that do not describe reality. Anything built on them inherits the error.

## Step 5 · Metrics measure the process, not the vocabulary
**Closes F23.** Risk: low-medium - DPMO will change, because it is currently wrong.

`isDefectWork` substring-matches free text against **two different word lists in two places**, so a
*feature* named "Self-Service Account **Recovery**" is defect work by name, and any acceptance criterion
describing error handling contains "failure". Result: 63 of 66 tasks classified as defects, DPMO 954,545,
on a project that merged 22 of 22.

The system **already declares** what kind of work each item is - `WishlistSource`. `self_falsification`,
`product_not_launchable`, `dockerfile_missing_build_stage`, `coverage_gap`, `design_review_concern_pattern`
and `gemini_observer` are defect-class by construction; `client` and `role` are not.

```
isDefectWork(t) ⟺ defectClass(originWishlistSource(t)) ∨ t.retryCount > 0
```

One definition, one place, both call sites. Same substitutivity move as Step 3, applied to metrics.

**Consumer to check:** `KaizenService:585` reads `dpmo()` as the `postMetric` deciding whether an applied
micro-improvement worked. Until this lands, the improvement engine is judging itself by substring matching.

**Verify:** recompute over `test-forty-sixth`'s frozen data; the defect count must match the number of
defect-sourced wishlists (currently 30 of 53), not 63 of 66.

## Step 6 · Settings cannot be silently absent
**Closes F3, F2.** Risk: low.

Four of 38 settings resolve with source `none` - registered, valueless. A boolean flag with no value reads
`false` and its feature is silently off; this already cost an entire falsification pass
(`design_system_falsification_enabled`), and the class has now recurred three times.

1. Startup logs every `source=none` key at WARN; the settings endpoint marks them. A registered-but-
   valueless flag is a defect, not a default.
2. The existing source-scanning test extended to fail when a *boolean* key resolves to `none`.
3. **Audit trail (F2):** who changed a setting, when, from what. `falsification_cycle_enabled=false` came
   from the database and its author is unrecoverable. A switch that changes what the whole factory does
   must not flip without a record.

**Verify:** flip a flag, read its history back.

## Step 7 · Items say where they came from
**Closes F13, F14, F15.** Risk: low.

1. **F13:** 21 of 22 generated work items are stored with `source=client`. The client wrote one brief. Any
   analysis of "what the client asked for" - exactly what the market corpus reasons about - is wrong on 21
   rows out of 22. Compiler-generated items take a compiler source.
2. **F14:** eleven items titled `Internal UI work item` are a data schema, an API contract, a backend
   service. The `BARCAN-TAG` is right, the noun is wrong; derive it from the role.
3. **F15:** `GET /api/projects/{id}/epics` returns `tasks: 0` while `totalPlannedTasks = 22`. A reader of
   that endpoint concludes no work exists.

**Verify:** the endpoint's task counts equal `productReadiness`; source counts match wishlist provenance.

## Step 8 · What is sent to the compiler must be observable
**Closes F29.** Risk: low.

The corpus floors and value chains are injected into the compiler prompt, and **there is no way to confirm
from outside that they rendered.** The prompt is not logged, so it cannot be known whether
`valueChainsFromCorpus()` produced anything on any run. This is how the misattribution in F29 happened -
epics were credited to a corpus that had not yet become influential.

**A floor whose presence in the prompt cannot be observed cannot be known to work.**

1. Persist the rendered prompt, or at minimum a digest per section, alongside the compiler task.
2. Surface which corpus entries and which value chains were injected, per decomposition.
3. Re-check the F29 attribution against real evidence once a decomposition runs post-fix.

**Verify:** decompose a brief and read back exactly which chains and duties were sent.

---

# PHASE III · THE PLACE WHERE LAYERS RECONCILE

## Step 9 · The verdict lattice, as a read-only observer
**Closes the structure behind F22-F27; makes F24 and F25 visible.** Risk: near zero - changes no behaviour.

Five layers report `82%`, `blocked`, `954545`, `0.57`, `launchSuccess=false` about one project. They cannot
be combined because they are different *modalities*, not measurements of one quantity. Averaging a deontic
claim with a frequency is the same category error, one level up, that produced both the entropy calculator
and DPMO.

1. `Verdict ∈ {permit, withhold, abstain}` with Kleene strong conjunction. Pure, testable in isolation.
2. Each layer declares up front the finite set of propositions it rules on - the Barcan condition. Without
   it `abstain` cannot distinguish "declared, undecided" from "never considered", and the second is
   invisible, which is how every silent gap in the findings arose.
3. Each layer maps its own measure to a verdict by its own stated rule; thresholds become local and
   auditable instead of smuggled into a global score.
4. Compute and surface `D` (abstentions) and `W` (refusals). `advance(P) ⟺ D = 0 ∧ W = 0`.

**Free consequences:**
- **F24** - the doctrine layer already refuses (`blocked`, 0 of 13 satisfied, 2 refusing, 7 objecting) and
  nothing reads it. Here it is simply another verdict source, read like any other.
- **F25** - the TOC constraint becomes derivable as `argmax(W + D)` instead of asserted. Measured now it
  points at the doctrine layer and, within tasks, at UX/UI - 13 tasks holding 3 of the 4 blocked - neither
  of which the queue view shows.

## Step 10 · The dependency graph becomes a graph
**Closes F26.** Risk: low - diagnostic first.

`graphTasks 42`, `uniqueGraphs 14`, `dependencyCoverage 0.57`: 43% of tasks carry no edge and the work
splits into 14 disconnected fragments with a critical path of 4. `duplicateSemanticKeys: 7`, whose own
interpretation says *"orchestration should collapse or skip repeated work"* - and nothing collapses it.

1. Measure first: are the 7 duplicates genuine repeats, and does the fragmentation cost anything, or is it
   an artifact of decomposition emitting independent slices by design?
2. Only then decide whether to collapse duplicates automatically or to surface them as a verdict.

**Why after Step 9:** if fragmentation matters it should be a layer's verdict, not another private rule.

---

# PHASE IV · CLOSE THE LOOPS

## Step 11 · Events instead of polls
**Closes F4, F27.** Risk: low.

Eleven of 37 `@Scheduled` methods fire blind and immediately check a state and return - a poll wearing a
cron's clothes. The clearest case: falsification runs `0 0 3 */2 * ?` and its first statement checks
readiness. Readiness can be reached at any moment and is sampled every 48 hours.

Live consequence measured today: the product was repaired **twice** (10:38, 12:00) and never re-observed;
the launch verdict is still the one taken at 10:21 before either repair, and philosophy is subordinated to
it. It is blocked twice over - also by `mergedRatio` which has since reached 1.0.

1. The transition establishing readiness publishes an event; falsification subscribes. Cron survives only
   as a rare safety net for a lost event.
2. A verdict carries the observation it rests on and **reverts to `abstain` when that observation's
   referent changes** - the same expiry rule the corpus applies to `observed` entries.
3. Convert the remaining ten polls, including `FactorySelfHealthService` (hourly), which this session added
   and which repeats the pattern.

**Verify:** land a Dockerfile fix and see a new observation without waiting for a tick.

## Step 12 · The factory watches itself
**Closes F5, F6.** Risk: low.

`FactorySelfHealthService` detects the factory's own ill health and writes `log.warn`. Nothing reads logs
autonomously - a closed loop without the closure. And `runtime-launcher` was down with nothing saying so;
without it no product can be launched or verified, and the TOC subordination gates on launchability, so
philosophy would have been skipped or run blind. The operator noticed, not the factory.

1. Self-health findings become `SYSTEMIC_DEFECT` proposals - the same stream as product findings, visible
   on the dashboard - while keeping the rule that factory code is never auto-modified.
2. A missing dependency of the flow (launcher, ML, database) is a declared proposition in the lattice, so
   its absence is a `withhold`, not silence.

**Done 2026-08-16.** `FactorySelfHealthService` escalates through `KaizenService.recordSystemicDefectProposal`
(`expectedGainPercent = 0`, never auto-applied, so the autonomy boundary holds: finding a fault in itself is
not a licence to repair itself), deduplicated on the assessment text so a persisting condition yields one
finding rather than one per hour. New `InfrastructureVerdictLayer` declares three propositions - launcher,
ML, database.

Two things found while doing it, both kept as reasoning rather than as separate findings:

- `runtime-launcher` is the only one of the three with **no `healthcheck:` block in docker-compose.yml**.
  That is why its being down was invisible; the layer now covers it, but the compose gap is real and
  narrower to fix.
- The ML shape is the same defect already live in the code: `MLPredictionServiceClient` returns `false`
  from bottleneck prediction and `null` from embed when the call fails, so an unreachable ML service reads
  downstream as a positive finding of *no bottleneck* and *no semantic neighbours*. The withheld
  proposition names that consequence explicitly.

Each proposition is about REACHABILITY, not about the dependency's opinion of its own health - any answer
settles it, including a 503. Conflating the two would let one sick product block every other project's
verdict. The probe is `GET /openapi.json` (FastAPI's own, side-effect free): the launcher's four real
routes are all POST and two of them act on live containers, so a check that caused the state it reports on
would be worse than no check.

Safe by construction for now: `VerdictReconciliation` is read by the controller only, so a layer able to
`withhold` cannot block anything until Step 18 wires the gates.

---

# PHASE V · THE PRODUCT REACHES THE CLIENT

## Step 13 · The acceptance chain
**Closes F30, F21.** Risk: low for the corpus, medium for the compiler prompt. Depends on Step 11.

Every `valuePath` traces the END USER's journey. None traces the buyer's: how does the client see that what
they paid for exists and runs? `DELIVERED` is computed from merge counts - a possibility claim with no
witness.

1. One `acceptanceRule` in `profiles.json`, status `derived`, instantiated against whatever `valuePaths`
   each profile declares. Not sixteen chains: the acceptance chain is the existing path under a change of
   quantifier - *there **exists** one complete traversal, performed **by the client**, on the **deployed**
   instance, against **real content**.*
2. **Seeding (F21)** follows from requirement two: a knowledge base with no materials cannot exercise "find
   the material", so it is unacceptable by rule rather than by opinion. The compiler must plan for content
   that exercises every link.
3. Traversal evidence recorded; `witnessed(P) = Σ|v|` gates acceptability. No threshold - value multiplies
   along a chain, so a partial traversal witnesses nothing.

**Depends on Step 11:** without a fresh observation there is no deployed instance to traverse.

**Done 2026-08-16.** `acceptanceRule` is one entry at the top of `profiles.json` (schema v3), not a
seventeenth chain, because acceptance is the sixteen existing chains under a change of quantifier: a
valuePath says every link must be POSSIBLE, the rule says one complete traversal HAS BEEN performed, by the
client, on the deployed instance, against real content. Sixteen copies of one idea would drift.

The seeding obligation (F21) is rendered into the compiler prompt FROM the corpus, never restated in the
prompt string - a second copy of a rule is a claim that can drift from the first, which is the shape of
every defect found this week. It asks for real initial content in the client's own domain and language,
because "Test Item 1" demonstrates nothing and has to be deleted before anyone can be shown the product.

`AcceptanceVerdictLayer` computes

```
witnessed(P) = product over declared paths of (client-walked links / declared links)
```

A product, not a sum, and with no threshold: value multiplies along a chain, so a client who got stuck
halfway was not shown a working product. One unwalked link makes the whole thing zero.

Three things this forced into the open:

- **The vacuous case is the dangerous one.** An empty product is 1, so a project with no client brief, or
  one whose kind the corpus does not recognise, would have been the EASIEST to accept. Every empty
  denominator resolves to `abstain` instead, and each says which gap it is - an unrecognised kind is the
  corpus's debt, not the product's clearance.
- **The denominator comes from the client's own words only** (`WishlistSource.client`). The factory's
  generated wishlists are full of product vocabulary; letting them in would let the factory choose which
  chains it owes.
- **`walkedBy` separates client from factory walks.** A factory walk witnesses that the path CAN be
  walked - the proposition the valuePath already made. Counting it as acceptance would let the factory
  accept its own work. Factory walks are reported in the reason rather than discarded, so a refusal never
  looks like nothing was tried.

`ABSTAIN` rather than `WITHHOLD` throughout, per F30's own wording: an absent record cannot refute the
client having walked the product - they may simply not have told us. Abstention blocks just as firmly.

**What remains for the walker.** `client_acceptance_traversals` is the evidence store and it now has a
reader; what writes to it - a client-facing acceptance walk on the live instance - is Stage 6 in the
findings' own order of work and depends on the lattice gate (Step 18). Until then the layer abstains, which
is the honest state and is visible rather than silent.

---

# PHASE VI · THE CORPUS BECOMES COMPLETE

## Step 14 · Missing links in the value chains
**Closes F16.** Risk: low.

Found by the operator's first question - *why are there no reports?* The content-management and
document-flow chains end at an action on a single document and never ask how anyone learns a document has
gone stale, or what the collection holds and what is used. For a knowledge base of normative documents that
is the governing need.

```
draft → edit → approve → publish → learn it has gone stale → update or unpublish
      → see what exists and what is used
```

GQM telemetry does not substitute: telemetry measures whether the system works, a report answers what the
collection holds.

## Step 15 · Kano gradation, scoping, and market coverage
**Closes F17, F18, F19.** Risk: low. Needs more than one project's evidence.

1. **F17** - all five epics came out `Must-Be`. The vocabulary exists and is not discriminating. Determine
   whether the class is being chosen or defaulted, then either fix the prompt or the parser.
2. **F18** - GDPR data-subject rights did not appear, though the corpus scopes that duty to every product
   and the system stores staff data. Possibly correct for a Russian institute; unexplained either way, and
   an unexplained omission is indistinguishable from a miss.
3. **F19** - the corpus covers DE and US; this brief was a Russian institution. Value chains are
   market-independent so the experiment stands, but the regulatory floor renders duties that cannot apply.
   Either scope the floor by the project's declared market, or state that markets outside DE/US get chains
   only.

---

# PHASE VII · THE REST

## Step 16 · Linear provisioning
**Closes F8.** Risk: low.

`Argument Validation Error` on `projectCreate`, reproduced on both `test-forty-fifth` and
`test-forty-sixth`, so it is independent of the transient GitHub outage. Every project is created without
its Linear counterpart. Fix, or decide Linear is out of the flow and stop attempting it - a failing
integration that nobody needs is noise that trains people to ignore provisioning warnings.

## Step 17 · English-only pass
Risk: low, but touches ~187 files.

Code, comments, commits, docs, corpora and test names are English; Russian is the language of conversation
only. A 2026-08-15 scan found roughly 187 files containing Cyrillic, mostly comments. A dedicated pass,
never mixed into repair work, so that a large mechanical diff never hides a semantic change.

---

# PHASE VIII · THE LATTICE DECIDES

## Step 18 · Gating
Risk: **high** - this changes what the factory permits. Behind a flag, one project first.

Advance gates read `advance(P) = ⋀ verdict_ℓ(P)` instead of their private conditions. Existing thresholds
move inside their owning layer, unchanged in value, now singular and inspectable.

Last, because a gate is only as honest as its inputs, and today two of five layers report numbers derived
from substring matching.

---

## Progress, 2026-08-15

| Step | State | Commit |
|---|---|---|
| 1 · effects verified not logged | **done** | `2165248` |
| 2 · design shop stops burning quota | **done** | `2165248` |
| 3 · conflicts: stop destroying work, then the mathematics | **done** | `9879c07` |
| 4 · pool named, held connection observable | **done** | `2e1c81c` |
| 5 · metrics read declarations, not prose | **done** | `eedf0b7` |
| 6 · valueless flags named at startup | **done** | `926bacc` |
| 7 · UI detection by whole words | **done** | `820e9e2` |
| 8-18 | pending | |

**Three findings did not survive verification and are retracted in place** rather than quietly dropped,
because acting on any of them would have damaged working behaviour:

- **F13** (generated items marked `source=client`) - not a defect. `WishlistSource` records the KIND of
  work and is inherited by slices deliberately; `originWishlistId` records lineage. Every consumer already
  distinguishes them, and Step 5's defect classification *depends* on that inheritance.
- **F15** (`epics` returns `tasks: 0`) - my own error. `EpicDiagnostic` has no `tasks` field; it has
  `codeProducingItemCount` and `mergedItemCount`. My inspection script asked for a field that does not
  exist and I recorded the `None` as a system defect.
- **F20's first diagnosis** (Flow Core denying `MERGE_PR`) - retracted earlier the same day; the real cause
  was the CI gate. `MERGE_PR` was never denied.

**Two open items carried forward from steps already closed:**

- **F2**, the settings audit trail, was deliberately left out of Step 6: it needs its own entity, migration
  and write-path interception, and mixing it in would make one commit do two unrelated things.
- The `looksLikeUi` **vocabulary** still counts `public` as a UI term, so "public API contract" matches.
  Step 7 fixed HOW terms are matched; WHICH terms count is a separate decision needing evidence.

## Every finding, and where it is handled

| # | Finding | Step |
|---|---|---|
| F1 | `done` without delivery | 1 |
| F2 | no settings audit trail | 6 |
| F3 | settings with source `none` | 6 |
| F4 | polls disguised as schedules | 11 |
| F5 | self-health writes to a log nobody reads | 12 |
| F6 | `runtime-launcher` down, nothing said | 12 |
| F7 | JPA rollback failures, DB growth | 4 |
| F8 | Linear provisioning fails | 16 |
| F9 | `%` broke the compiler prompt | **done** `64d34ee` |
| F10 | one keyword list, two roles | **done** `64d34ee` |
| F11 | `.gitignore` never updated | 1 |
| F12 | no shared skeleton | 1 |
| F13 | generated items marked `source=client` | 7 |
| F14 | wrong item titles | 7 |
| F15 | `epics` returns `tasks: 0` | 7 |
| F16 | chains lack staleness and collection state | 14 |
| F17 | every epic `Must-Be` | 15 |
| F18 | GDPR rights absent | 15 |
| F19 | corpus market scope | 15 |
| F20 | conflict/CI deadlock | fixed `821f12b`, witnessed in 3 |
| F21 | nothing seeds primary content | 13 |
| F22 | conflict mathematics unsound | 3 |
| F23 | Six Sigma measures keywords | 5 |
| F24 | doctrine refuses, nobody reads | 9 |
| F25 | UX/UI bottleneck invisible | 9 |
| F26 | dependency graph fragmented | 10 |
| F27 | philosophy blocked on stale evidence | 11 |
| F28 | design shop retries forever | 2 |
| F29 | prompt contents unobservable | 8 |
| F30 | nothing shows the client the product | 13 |
| — | ~187 files containing Cyrillic | 17 |

## Order in one line

**Stop the losses (1-4) → make the inputs honest (5-8) → build the place where they reconcile (9-10) →
close the loops (11-12) → let the client see it (13) → complete the corpus (14-15) → clear the rest
(16-17) → and only then let it decide (18).**
