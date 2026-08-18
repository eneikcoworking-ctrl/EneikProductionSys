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

**Done 2026-08-16.** The diagnosis turned out to be sharper than "two chains are short", and it explains why
the gap hit several profiles at once instead of one:

> Every link in these chains describes work somebody is DOING. Staleness is the one state a document
> reaches by **nobody doing anything**. A chain assembled from actions cannot represent it.

So the fix is two different kinds of link, not one:

- **Staleness** joins the existing path, because it is the trigger the path already assumed someone had:
  `update or unpublish` is an action nobody performs until something tells them the text no longer holds.
  Added to `content-management`, `document-flow`, and - by the same argument, not for symmetry - `learning`,
  where teaching a superseded procedure actively misinforms and the learner then carries a certificate
  asserting they know something that is no longer true. In `document-flow` it is worse than in a CMS:
  finding a superseded contract and believing it current is more damaging than finding nothing, because the
  user acts on it. "Found" and "still true" must be states the product can tell apart.
- **Collection state** is a SEPARATE path with its own actor, the owner of the collection, because it breaks
  independently and answers a different person: an editorial workflow can be flawless while nobody can say
  what exists. For a body of normative documents the collection's own state IS the product.

`learning` gets no owner path: `see who is stuck` already answers the collection-state question for
learners, and the gap there was about the MATERIAL only.

No code change - the chains render into the compiler prompt straight from the corpus. Pinned by two tests
that assert the property rather than the wording, so the links cannot be quietly dropped again.

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

**Done 2026-08-16.**

**F17 - the class was not being chosen, it was being defaulted.** `JulesDispatchService.parseCompilerPlan`
read `epicNode.path("kanoClass").asText("Must-Be")`, so a missing classification was spelled exactly like a
deliberate one. The vocabulary was not failing to discriminate; nothing was being asked.

**And this had already been "fixed" once.** On 2026-08-14 `ProjectFlowService.parseCompilerPlanContent`
stopped defaulting, with a comment recording that "the two sides of the flow now hold the same discipline".
That was false. There are two parsers, and the one repaired was the secondary path; the live path a
Jules-delivered plan actually travels still defaulted - which is exactly why all five epics of
test-forty-sixth came out `Must-Be` **after** that fix. A repair that asserts parity without checking it is
worth less than no repair, because it also stops anyone looking.

The correct rule was additionally written **1200 lines above it in the same file**, over
`parsePhilosophicalReport`: a critique with a missing or unrecognised `kanoClass` is dropped rather than
defaulted, because "silently defaulting here would be exactly the *system re-infers Kano and gets Must-Be*
failure mode this feature exists to avoid" (operator directive, 2026-07-25). So the rule existed in three
places and two spellings.

I nearly made it four: my first pass added an `Unclassified` marker to one parser while the other still
wrote blank - two representations of one concept, the same defect in a quieter form. The vocabulary and the
reading rule now live once, in `KanoClass`, and both parsers call it. **A fix that leaves the concept in two
places is the defect, not the repair.**

The marker is deliberately neither a valid class nor blank: a marker that is also a valid class is not a
marker, and blank is indistinguishable from a column nobody ever wrote to. `isUnclassified` still reads the
old blank spelling, so rows written before today do not start reading as classified.

It does **not** drop the epic the way the critique path drops a critique, and the asymmetry is about what is
lost: a dropped critique costs one opinion, a dropped epic costs its requirements and every slice under it.
The marker reaches the project tree, so it is visible to the operator rather than buried in a log.

A quieter consequence goes with it: `SelfFalsificationEpicMatcher` adds a bonus when two epics share a Kano
class. While everything defaulted to `Must-Be`, that bonus applied to every pair equally - a signal built to
discriminate was contributing a constant.

**F19 - the market becomes a declared fact.** `ProjectEntity.targetMarkets` (migration V101). A market is
something somebody knows about the engagement; inferring it from the brief's wording would be the same
indicator-for-property substitution repaired everywhere else this week.

Undeclared keeps rendering both DE and US **deliberately**, and now says so in the prompt: showing a duty
that does not apply costs wasted scope, omitting one that does costs a legal hole, and those are not the
same size of mistake, so the default fails towards the cheaper one. An assumption the reader cannot see is
one they cannot correct.

A declared market the corpus does not cover produces a **statement**, not an empty section - the corpus
holding nothing for Russia is a fact about the corpus, and silence would read as "this market imposes
nothing". That is the same confusion between *undecided* and *never considered* the lattice exists to
prevent.

**F18 - the omission is now explainable, from both ends.** Two defects, not one:

- `reportUncoveredStatutoryRequirements` hardcoded `List.of("DE", "US")`, making it the **third** place
  deciding a project's markets. It now uses the declared one, with the same every-market fallback the gate's
  own documentation requires.
- Its findings went to `log.warn` **and nowhere else**, so a duty the plan missed and a duty that never
  applied looked identical from outside. That is precisely why the missing GDPR epic could not be explained
  afterwards - and it is the same open loop closed in `FactorySelfHealthService` in Step 12, in a different
  service. Findings now land on the project's `factoryReport`, including an explicit "checked, found
  nothing" line: *checked and clean* and *never checked* must stay distinguishable.

Written to the report rather than raised as work, deliberately. The check is keyword-based and approximate;
generating tasks off an approximation is how a gate teaches people to ignore it. Blocking or task-creation
can follow once the false-positive rate is measured, which is the gate's own stated plan.

---

# PHASE VII · THE REST

## Step 16 · Linear provisioning
**Closes F8.** Risk: low.

`Argument Validation Error` on `projectCreate`, reproduced on both `test-forty-fifth` and
`test-forty-sixth`, so it is independent of the transient GitHub outage. Every project is created without
its Linear counterpart. Fix, or decide Linear is out of the flow and stop attempting it - a failing
integration that nobody needs is noise that trains people to ignore provisioning warnings.

**Root cause found 2026-08-16, from the live effective configuration rather than from the code:**

```
linear_team_id = "Eneik Production System"     (source: database, linear_enabled = true)
```

That is the team's **name**. `ProjectCreateInput.teamIds` takes UUIDs, so Linear answered with its generic
`Argument Validation Error` - which named nothing, so the same configuration mistake arrived on every
project looking like a transient integration fault. It reproduced on both test projects for the obvious
reason: the cause is in the configuration, not the data.

The client now refuses a malformed team id **before** calling, and says which value is wrong and where the
real id lives. The check has to be local, because a remote rejection cannot name a local cause.

Deliberately **no** name-to-id lookup. Resolving a name would make the setting mean two things, and a
setting that accepts what it should reject is how the mistake survives into whatever reads it next.

**Left for the operator:** the correct team UUID. Finding it means calling Linear with their key, which is
their decision, not something to do on their behalf. Until it is set, provisioning is now `skipped:` with a
one-line instruction instead of a recurring opaque failure - which also answers the second half of the step,
since the noise that trained people to ignore provisioning warnings is what has been removed.

## Step 17 · English-only pass
Risk: low, but touches ~187 files.

**Scope measured 2026-08-16**, because "mostly comments" turned out to be doing some work:

| Where | Files | Nature |
|---|---|---|
| `src/**` comments only | 52 | safe, purely linguistic |
| `src/**` reaching code | 15 Java | needs reading: strings, one user-facing message |
| `src/main/resources/db/migration` | 11 | **deliberately not touched** - see below |
| `docs/` | 116 | prose, mostly historical reports |
| `frontend/src`, `scripts` | 9 | mixed |

The single most common token is `эпик`/`эпики` - 128 of the occurrences are just the word "epic".

**Migrations are left alone on purpose.** `spring.flyway.validate-on-migrate=false` today, so editing an
applied migration would not break anything now - which is exactly the trap. That setting is one a careful
person would later turn ON, and eleven changed checksums would then detonate for whoever improved the
configuration. Comment cosmetics are not worth planting that.

### Found while measuring, NOT fixed here (a linguistic pass must not carry a semantic change)

**F31. The dashboard re-infers Kano from keywords and advises acceptance on it.**
`CommandDashboardService.classifyKano` matches substrings - including the Russian `интегра`, `база`,
`дизайн`, `экран` - and its answer drives `kanoRecommendation`, the text telling the operator whether to
accept the project. Three things are wrong at once:

- it is a **fourth** place deciding Kano, after the two plan parsers and the critique parser, and it does so
  by the keyword re-inference that F17 exists to remove;
- its vocabulary is a fourth one too (`One-Dimensional`, `Indifferent`), matching neither the epic classes
  nor the critique classes;
- `FeatureEntity.kanoClass` already holds the real classification the compiler made, and this reads none of
  it - so acceptance advice rests on guessed words while the actual answer sits in the database.

The Cyrillic here is **load-bearing**: deleting those four substrings silently stops classifying every piece
of historical Russian content. That is why it is recorded rather than swept up in this step - the repair is
to read `kanoClass` instead of guessing, and that is its own change with its own test.

### Done 2026-08-16 (source code)

7473 → ~2100 Cyrillic characters, 67 → ~44 Java files. What was translated is what actually leaves the
system:

- **the task text the factory writes for Jules** (`getRoleSpecificAssignment`) - Russian instructions to a
  worker, inside a system whose artifacts must be English;
- **Kaizen proposal titles and descriptions** - what the operator reads on the dashboard;
- the user-facing re-audit message in `ProjectController`;
- log messages and the quoted operator directives in comments;
- the `Ф` marker expanded to `Phase` (37 occurrences).

### NOT done, and why - three different reasons, none of them cosmetic

**1. Referents (`RoleRulesParser`, `FalsificationCycleService`, and the three tests mirroring them).**
The parser extracts sections by the Russian headings of the real charter files - `ПРИОРИТЕТЫ`,
`КРИТЕРИИ ОТКАЗА`, `ФИЛОСОФСКИЙ ФУНДАМЕНТ`. Translating the parser without the charters breaks parsing;
they are a bound pair and must move together, against the live files. **Own task.**

**2. Matchers over historical content** (`TechnicalLeadCompiler`, `ProjectFlowService`,
`WishlistContentSimilarityMatcher`, `SelfFalsificationEpicMatcher`, `CommandDashboardService`,
`JulesDispatchService`'s clarifying-question detector). These match Russian substrings in client briefs, and
the `а-яА-Я` character classes tokenise Russian text. Deleting them silently stops understanding everything
already written in Russian. **Needs a decision about historical data, not a translation.**

**3. Mojibake signatures.** `TechnicalLeadCompiler:1659` tests `value.contains("Р")` / `("С")`, and
`JulesDispatchService` tests `"рђсс"` / `"рѕс€"`. These are the classic markers of UTF-8 read as CP1251.
The Cyrillic here is not text - it is a corruption signature, and it must stay exactly as it is.

**4. `docs/` - 116 files, untouched.** Historical reports and post-mortems. Deliberately not mixed with the
source pass: this step's own rule is that a large mechanical diff must not hide a semantic change, and the
same rule argues for not putting 116 prose files in the same commit as behaviour-bearing string edits.
**Own commit, own review.**

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

**Done 2026-08-16.** The first question was not how to gate but **what the lattice is entitled to gate**,
and two candidates had to be refused before the right one was left:

- **Not acceptance.** `acceptProject` is the client's act of ending an engagement, not a claim that the
  product is ready. A lattice that abstains must never be able to stop someone ending their own engagement.
- **Not task dispatch.** A layer saying "the product does not launch" is an argument *for* dispatching
  repair work. Gating dispatch on it would make the system unable to fix the very thing being refused.

What remains is exactly what the layers judge: **the readiness the factory REPORTS**. A verdict governs
claims.

**The arithmetic was already in place, unnamed.** `CommandDashboardService` answered in three values -
`ready` when four conditions hold, `unknown` when one is unmeasurable, `not ready` when one fails. That is
Kleene conjunction written out by hand. Naming it lets the lattice join as one more conjunct rather than
being bolted on beside it:

```
report(P) = construction(P) ∧ ⋀_ℓ verdict_ℓ(P)
```

And it exposes what those four conditions were: tasks done, gates passed, PRs merged, GitHub reachable -
**all four about construction**, none about the product running or having been shown. That is F1 and F30 at
the top of the system.

**Why turning it on is safe**, in the only sense that matters: by monotonicity of `and`, adding a conjunct
can only make the claim harder to earn. No configuration of the lattice can cause the factory to report
readiness it would not have reported before.

**Three ways it declines to act**, and the third is the one worth writing down. Flag off; a different
project; **an empty lattice**. The conjunction over no propositions is `PERMIT` - correct arithmetic, and a
claim made out of silence. So an empty lattice does not permit, it stands aside. A gate that approves
because it has nothing to say is worse than no gate.

An empty `verdict_gating_project_slug` means **no** project, never every project: a scoping value that
falls back to "all" turns the first careless deploy into a factory-wide change.

`applied` is reported alongside the verdict rather than inferred from it - "the gate ran and agreed" and
"the gate never ran" are different facts, and a staged rollout cannot be judged if they look the same.

**Not done, deliberately:** the second half of the step - moving existing thresholds inside their owning
layers. Those thresholds live in services with their own histories, and relocating them is a behaviour
change dressed as a refactor. It also cannot be verified while the factory is stopped. **Own task.**

**Not done, and it is the precondition for ever switching the flag on:** the step's own last sentence -
*a gate is only as honest as its inputs, and two of five layers report numbers derived from substring
matching.* That is still true. The flag exists so the gate can be observed against a live project before
anything depends on it; turning it on before those two layers are repaired would be gating on the very
proxy measures this plan has spent eighteen steps removing.

---

## Everything left undone, 2026-08-16 — one list, for a separate conversation

All eighteen steps have been worked. What follows is what each one did **not** close, gathered here so it
is discussed on purpose rather than rediscovered by accident. Nothing in this list is blocked; each is a
deliberate deferral with a stated reason.

### New findings raised while doing the work

| # | Finding | Why it was not fixed in place |
|---|---|---|
| **F31** | `CommandDashboardService.classifyKano` re-inferred Kano from keywords - a **fourth** decider, a fourth vocabulary (`One-Dimensional`, `Indifferent`), driving the acceptance recommendation while `FeatureEntity.kanoClass` already held the real answer | **Repaired 2026-08-16.** `classifyKano` deleted; `fetchEpicKanoClasses` reads `features.kano_class`, joined by `feature_id`, which both tasks and wishlist rows carry. Unclassified counts as high-value: recommending that a client finish on an item nobody classified is advice from silence, the same reason ABSTAIN blocks. Its Russian substrings went with the mechanism |
| **F32** | The working tree carried **CRLF in 80+ files** the repo had as LF, with no `.gitattributes` and `core.autocrlf` unset | **Repaired 2026-08-16**, commit `6574307`: `* text=auto eol=lf` plus `git add --renormalize .` as a commit that does nothing else. 181 files, ~23000 lines, zero content change - checkable, not asserted: `git diff --ignore-cr-at-eol --name-only` lists only `.gitattributes`, and `git ls-files --eol \| grep -c i/crlf` is 0 |
| **F33** | `getRoleSpecificAssignment` hardcodes chess-specific task text in a general-purpose factory | A leftover from an early experiment; translated in place rather than removed, because removing it is a product decision |
| **F34** | `EmsMetricsService.containsRefusalSignal` - **the substring dependency Step 18 named, now identified and repaired 2026-08-16** | See below; it was worse than the plan recorded |

### F34 in full, because the shape of it matters

Step 18's last line said *"two of five layers report numbers derived from substring matching"* without naming
them. Reading rather than remembering found one, and it was anti-correlated with the thing it measured.

`containsRefusalSignal` matched free text for `refusal`, `reject`, `forbidden`, `violation`, `critical`,
`p0`, `security`, `auth`, `privacy`, `compliance`, `leak`, `failed`, `blocked`. One hit set
`severeSourceObjection`, and since almost every role carries `kanoBias = must_be`, that set the role's
stance straight to **`refuses`** - which `DoctrineVerdictLayer` reads.

**The regulatory floor added in Steps 13-15 requires epics about privacy, consent, security and compliance
for the German and US markets.** Every plan that obeys it contains those words by construction. So the
better a plan served the client's legal obligations, the more roles were recorded as refusing it. A metric
that punishes the behaviour it exists to encourage is worse than no metric.

`auth` was a bare substring besides, matching `author` and `authority` - the same word-boundary defect
repaired in Step 7, in a third place.

The declared property was available all along: a philosophical critique, a self-falsification finding and a
design-review concern **are** roles refusing; a client brief and a coverage gap are not. Replaced by
`ROLE_REFUSAL_SOURCES` over `WishlistSource` - the same move as Step 5.

Deliberately **not** the same predicate as `isDefectWork`, because they answer different questions.
`philosophical_falsification` is excluded from defect work (a critique proposes a genuinely new capability,
not rework) and included here for exactly the same reason: proposing it *is* the objection.

**Correction while repairing it.** My first version listed five `WishlistSource` values as "refusal-class",
and `EmsMetricsServiceTest.pendingSourceRoleRefusalBlocksDoctrineReadiness` refused it - correctly, because
`source = role` is a role objecting too. The right line is not which source but **who raised it**: `client`
is what the client asked for, every other source is something the factory itself raised. Severity is then
read off the role's own `kanoBias` at the call site, not off the text: for a must-be doctrine an open
objection is a refusal, for a performance doctrine the same objection is an objection. The doctrine decides
what its own objection weighs, which is what a doctrine is for.

### F35. The second substring input, found by auditing all five layers

Step 18 asserted two and named none. Enumerating each layer's dependencies gave a definite answer:

| Layer | Inputs | Substring-derived? |
|---|---|---|
| runtime | observation record, `latestCommitTime` | no |
| infrastructure | HTTP reachability, `DatabaseHealth` | no |
| six-sigma | none; always abstains | no |
| doctrine | `EmsMetricsService` stances | **was** - F34, repaired |
| acceptance | `MarketCorpusService.profilesInEvidence` | **yes - F35** |

The second one is code written for Step 13. `profilesInEvidence` used `haystack.contains(needle)` to decide
which value chains a product owes - the **denominator of `witnessed(P)`**. The corpus keyword `shop` is a
substring of `workshop`, and `cart` of `cartography`: a company that runs workshops would have been
classified an online shop and handed the payment and withdrawal-rights duties that follow.

**This is the fourth appearance of one defect this week** - a substring test standing in for a word test -
after `looksLikeUi` (Step 7), the compiler's keyword scan (Step 5's neighbourhood), and `auth` matching
`author` (F34).

All three matching sites - `profilesInEvidence`, and both in `MarketComplianceGate` - now call one helper,
`MarketCorpusService.mentions`, with compiled patterns cached per keyword:

```
\bKEYWORD(<the keyword's own last letter>)?(s|es|ing|ed)?\b
```

Inflection is **enumerated rather than approximated by a prefix rule**, because a prefix rule is precisely
what let `auth` match `author`. The doubled letter is not decoration: English doubles a final consonant
before a suffix, so `shop` becomes `shopping`, and the first version of this rule stopped matching it. It is
the keyword's **own** last letter that may repeat, never an arbitrary character - so `cart` admits `cartt`,
which no English word is, and still refuses `cartography` because the boundary must close. A wildcard would
have re-opened the hole being closed.

**Why the rule is strict on both sides**, when a start-only boundary would have been simpler: one helper
serves two consumers whose safety asymmetries are opposite.

| Site | A false positive means | Direction of the error |
|---|---|---|
| `profilesInEvidence` | a chain the product does not owe joins the denominator, `witnessed(P)` falls, acceptance blocks | safe |
| `MarketComplianceGate` coverage | a duty is marked covered and silently dropped | **unsafe** |

A start-only boundary would have been kinder to the first (`workshop` fixed) and useless to the second
(`cartography` still matches `cart`). The rule is therefore chosen by the worse of its two consumers, not
the more convenient one.

The failing case was found by a test rather than by a wrong classification months later, which is the whole
argument for enumerating inflections: an approximation fails **quietly**.

**Consequence for Step 18: its stated precondition is now met.** Both substring inputs are named and
repaired. What still stands between the flag and being switched on is not the inputs but the observation -
none of this has been seen against a running factory.

### Found on the live run, test-forty-seventh, 2026-08-16

The third run of the same brief began at 01:52 UTC+4 on project `cd9a0b82-4465-4839-a651-7bd4b8cebec4`.
Recorded as observed, with the evidence, and deliberately without intervening in the flow.

**Confirmed working, on the live system rather than in a test:**

- `runtime-launcher` reports `healthy`. It had no `healthcheck:` block at all before Step 12, which is why
  its outage on 2026-08-15 was invisible.
- Linear provisioning answered with the informative refusal instead of `Argument Validation Error`:
  *"linear_team_id is 'Eneik Production System', which is not a Linear team id..."* (F8).
- Migrations V99, V100, V101 applied cleanly; schema at v101.
- `verdict_gating_enabled` resolves off with source `none`, as intended for this run.

**F36. `/api/projects/{id}/verdict` did not answer within 120 s while the flow was active.**

Measured: two attempts, `[000] total=120.0s`. In the same window `/api/settings` answered 200 but took
12.4 s, against well under a second when the factory was idle. So the request thread was not obviously
deadlocked - the whole application was slow while onboarding and wishlist compilation ran.

**The cause is not established and is not being guessed at.** Two candidates, both testable:

1. contention for database connections with the flow's own work (Hikari pool, `maximum-pool-size=24`);
2. per-layer synchronous network I/O with no overall budget - `RuntimeVerdictLayer` calls GitHub for
   `latestCommitTime`, `InfrastructureVerdictLayer` makes two HTTP probes at 5 s each.

Either way the structural point stands and is worth acting on regardless of which it turns out to be:
**`VerdictLayer` has no deadline in its contract.** A read-only observer that can hold a request thread
indefinitely is tolerable; the same code sitting in the flow's path once Step 18's gate is switched on is
not. A gate that can hang is a gate that stops the factory.

This is the first thing the live run has told us that the tests could not, which is precisely why the run
exists.

**Second pass, 02:35 UTC. The lattice produced a real, non-vacuous answer on a live project.**

```
advance = WITHHOLD   D = 11   W = 2   constraint = doctrine
```

Every layer said something worth reading, and three repairs were confirmed by the answer itself rather than
by a test:

- **F35 word boundaries, confirmed live.** The acceptance layer picked exactly `content-management` and
  `document-flow` from the client's own words for a knowledge base of epidemiological materials - sane
  kinds, and notably not `shop`. It counted **18 links**, which is the two extended chains from Step 14
  (6 editor + 3 owner, 6 originator + 3 owner) including the staleness and collection-state links added
  that step. The corpus additions are live and reaching a decision.
- **F34 holds so far.** No role sits at `refuses`. The one doctrine withhold is *"source-role objections
  are still pending"* at `stance=objects` - a pending-work signal, not a regulatory epic being read as a
  refusal. That was the exact failure mode F34 removed.
- **Step 12 self-observation, confirmed.** The infrastructure layer refused with a measured reason: *"the
  database file is 406 MB holding only 61 MB of live data (6.6x bloat) - the store is not reclaiming freed
  space, which happens when it is killed instead of closed"*. The factory found a defect in itself and said
  so in a place a human reads.

**F36 sharpened by measurement, not by argument.** The verdict endpoint answered in **68.9 s on an idle
system**, where `/api/settings` answers in 0.5 s. That discriminates the two candidates recorded earlier:
contention with the flow's work is **not** the main cause - the layers themselves are. Which layer is not
yet established and should be measured before anything is changed.

**Open prediction, checkable at the next pass.** `FactorySelfHealthService` runs on
`cron = 0 40 * * * ?`; the backend started 01:49:51 UTC, so its first firing is 02:40 UTC and had not
happened at the time of this reading - which is why zero Kaizen proposals exist, and why this is **not** a
falsification of F5 yet. The prediction is exact: after 02:40 UTC, with the database at 6.6x bloat, a
`SYSTEMIC_DEFECT` proposal titled for factory self-health must exist. If it does not, F5 is falsified.

**Not yet established, deliberately not called a falsification.** `.gitignore` on main contains only
`.eneik/`, without `target/` and `data/` - but the repository root holds no `pom.xml` and no `src/`, so the
deterministic scaffold has not run yet. Step 1's verification cannot be performed until it does. Re-check
when the repository gains a build file.

**F38. The acceptance recommendation still speaks a vocabulary the classification no longer uses.** F31
changed where the class comes from - `features.kano_class` instead of guessed words - but the message text
still reads *"pending Must-Be/One-Dimensional tasks"*, and `One-Dimensional` is not one of the four epic
classes. Cosmetic today; it becomes misleading the moment someone compares the advice against the tree.

**Third pass, 02:57 UTC. The prediction held, and it exposed two defects in Step 12 itself.**

**F5 fires - the escalation is real.** At 02:42:06, exactly on the declared `cron = 0 40 * * * ?`:

```
WARN  FactorySelfHealth: database file is 419 MB holding only 62 MB of live data (6.7x bloat) ...
INFO  [KAIZEN-SYSTEMIC] Recorded review-only systemic defect proposal
      'kz-systemic-3d650db6-...' from project null: Factory self-health: the
      orchestrator's own database is unhealthy
```

**F39. The finding is created and then invisible.** `/api/kaizen/proposals` and `/api/kaizen/opportunities`
both return zero. `KaizenController` serves `kaizenService.getProposalsForProject(projectId)`, and the
proposal carries `projectId = null` - which Step 12 chose deliberately, because a fault in the factory
belongs to no client project. That choice is what makes it unreachable through the only API a human would
look at.

This is **the F5 defect reproduced one level further out, by the repair for F5**. Detection stopped writing
to a log nobody reads and started writing to a list nobody can retrieve. The repair is not to give the
finding a fake project: it is that a factory-level proposal needs a way to be listed *as* factory-level.

**F40. The health check trips the connection-leak detector - and is the established cause of F36.**

```
java.lang.Exception: Apparent connection leak detected
  at FactorySelfHealthService.liveDataBytes(FactorySelfHealthService.java:166)
  at FactorySelfHealthService.inspect(FactorySelfHealthService.java:121)
  at FactorySelfHealthService.reportIfUnhealthy(FactorySelfHealthService.java:70)
```

`liveDataBytes()` calls H2's `DISK_SPACE_USED(table)` **once per table** in the schema. On a 419 MB store
that walks the pages of every table, and the pool's 30 s leak threshold - added in Step 4 to make exactly
this visible - fires on it.

**This closes F36 with a cause rather than a candidate.** `InfrastructureVerdictLayer.judgeDatabase()` calls
`selfHealthService.inspect()`, so every request to `/api/projects/{id}/verdict` re-runs that whole per-table
scan. That is the 68.9 s measured on an idle system, and no amount of pool tuning would have found it: the
answer was in what the layer *does*, not in what it competes with.

Three things follow, and the third is the one that matters:

1. `inspect()` needs a cached or sampled measurement, not a full scan per call.
2. A monitor that holds pooled connections for over 30 s is a contributor to the condition it reports on.
   The observer must not be a load-bearing part of the load.
3. **This is the concrete form of the deadline problem recorded as F36.** Had Step 18's flag been switched
   on before this run, the gate would have put a 69-second per-table disk scan into the flow's path. The
   decision to ship the gate off by default, and to insist the run happen before switching it on, is
   precisely what caught this - which is the strongest argument available for keeping it off until the
   layers carry a measured budget.

**F41. The compiler produced 20 epics. The database holds 1.** Operator's question, and the decisive
finding of the run.

Read from the plans the compiler itself committed to the repository:

| Plan file | Epics | Titles include |
|---|---|---|
| `task-plan-109fd79c` | 6 | Material Management, Search and Discovery, **System Outcome Measurement**, **Factory Acceptance Content**, **Business Continuity and Backups**, Identity and Access |
| `task-plan-1182b68d` | 6 | Content Lifecycle, Knowledge Discovery, **Telemetry and Measurement**, **Initial Content Seeding**, **Backup and Restore**, Account Recovery |
| `task-plan-3d221420` | 8 | Content Discovery, **Data Protection and Recovery**, Account Recovery, **Value Measurement**, **Analytics Consent Management**, **Data Subject Privacy Rights**, **Acceptance Demonstration Content**, and one malformed entry |

`/tree` reports exactly one feature: *Epidemiological Content Lifecycle and Management*.

**So the corpus work succeeded and the persistence path throws it away.** Every floor built in Steps 13-15
is visibly producing epics - the measurement floor (*System Outcome Measurement*, *Value Measurement*), the
regulatory floor (*Data Subject Privacy Rights*, *Analytics Consent Management*, *Backup and Restore*), and
the acceptance floor's seeding obligation, F21, by name: *Initial Epidemiological Content Seeding* and
*Factory Acceptance Content*. The compiler is obeying the prompt. Then `resolveEpicFeatureId` /
`SelfFalsificationEpicMatcher` collapses them into one.

**The mechanism is the one this session already named and under-corrected.** From Step 15's own commit
message: *"SelfFalsificationEpicMatcher gives a bonus when two epics share a Kano class. While everything
defaulted to Must-Be, that bonus applied to every pair equally - a signal built to discriminate was a
constant."* Removing the default was necessary and is not sufficient: **all 20 epics carry `Must-Be`
anyway**, chosen rather than defaulted, so the bonus is still a constant. Add a Cynefin bonus that is
likely as uniform, and a Jaccard over titles that all share *Epidemiological / Knowledge Base / Content*,
and the matcher sees twenty near-identical candidates.

**F17 is therefore only half repaired, and the live run says which half.** The plan asked: *"determine
whether the class is being chosen or defaulted."* The answer is **both**. The code defaulted - fixed - and
the compiler does not discriminate either: it labels *Analytics Consent Management* and *Telemetry and
Measurement* `Must-Be` exactly like the core content lifecycle. The prompt offers four classes and gets one.

One plan entry carries `kanoClass: null, title: null` - a malformed epic the compiler emitted. Under
`KanoClass.normalize` that now becomes `Unclassified` rather than a silent `Must-Be`, which is the marker
doing its job: it makes a broken epic visible instead of well-formed.

**Not yet established:** whether the twenty were merged by the matcher or dropped by something earlier.
That distinction decides the repair and must be measured, not assumed - the matcher is the strong
hypothesis because its scoring is documented and its discriminating term is provably constant here.

**F42. The same brief was compiled four times, and that is where the twenty epics came from.**

The three plan files are not three parts of one decomposition. Each is a full, independent decomposition of
**the same single client wishlist**, `504ca516`. The dashboard shows four carrier tasks, all titled
`Compile 1 wishlist(s) into task graph (504ca516)` - three `done`, one still `claimed` - and each completed
one wrote its own plan under its own per-task path (`compilerPlanPath`, made per-task by the 2026-07-24
fixed-path collision fix).

The wishlist has not left `compiling` through any of it.

The overlap is the tell. Across the three plans:

- *Backup / Business Continuity / Data Protection and Recovery* - **three times**, three wordings
- *Measurement / Telemetry / Value Measurement* - **three times**
- *Account Recovery / Identity and Access* - **three times**
- *Seeding / Factory Acceptance Content / Acceptance Demonstration Content* - **twice**
- *Search / Discovery / Knowledge Discovery* - **three times**

These are not twenty distinct epics. They are roughly six or seven ideas, each re-invented under a
different name on every pass - which is exactly the input that makes an epic matcher collapse everything,
because near-duplicates are what it is built to merge.

**So F41 and F42 are one problem seen from two ends.** Ordering the repair matters:

1. **Why does one wishlist get compiled four times and stay `compiling`?** Until that is answered, fixing
   the matcher only changes how the duplicates are stored.
2. Only then, whether the matcher's discriminating terms are constant (F41).

Attacking the matcher first would be treating the symptom, and would very likely produce twenty epics that
are six ideas repeated - which is worse than one epic, not better.

## PROPOSED REPAIR (not implemented): compilation must terminate

Run stopped 03:19 UTC, project `frozen`, PR #7 closed as WIP. Nothing below is built yet.

### The defect in one line

**The system uses a rate limit where it needs a termination condition.**
`lastCompileDispatchedAt` + `WISHLIST_COMPILE_DISPATCH_COOLDOWN_SECONDS` bound how *often* a compile may be
dispatched. They cannot bound how *many times*. There is no decreasing quantity anywhere in the loop, so
there is no termination proof - which is exactly why 16 plans on `test-forty-sixth` and 4 on
`test-forty-seventh` were possible and nobody had to be at fault.

### The doctrine already contains the answer, twice

- **Step 1:** *"an operation's effect must be verified, not logged."* Applied to file commits. Never applied
  to compilation.
- **Step 13:** *"a possibility claim is not witnessed by another possibility claim."* A dispatched carrier
  task is a possibility claim about decomposition. A plan file is another one.

### Ask after the property, not the indicator

```
compiled(w)  ⟺  ∃ e ∈ Epics  : originWishlistId(e) = w
              ∧  ∃ s ∈ Slices : originWishlistId(s) = w
```

Not "a task ran", not "a plan file exists", not "we dispatched recently" - the artefacts exist and point
back at the brief. The same referent move as Step 11.

### Admission with a well-founded measure

```
mayCompile(w)  ⟺  ¬compiled(w)  ∧  attempts(w) < B
```

`B` declared, finite, and documented as an arbitrary budget - the same honesty as
`MAX_CONFLICT_ATTEMPTS = 3`. Reasoning may be asserted; a number requires measurement, so it is labelled a
budget rather than dressed as a threshold.

**Termination proof.** Let `μ(w) = B − attempts(w) ∈ ℕ`. Every dispatch either

- **(a)** establishes `compiled(w)` — the loop exits by success, or
- **(b)** leaves it unestablished and decreases `μ` by exactly 1.

`μ` is a natural number strictly decreasing on (b), so there is no infinite descending chain: the loop
terminates in at most `B` attempts. **Today `μ` does not exist.** That absence is the whole defect, and no
tuning of the cooldown can supply it.

### Exhaustion is a stated refusal, never silence

When `μ = 0` and `¬compiled(w)`, the wishlist takes a terminal state carrying the reason. In the lattice's
own terms this is `WITHHOLD`, not `ABSTAIN`: it is *established* that decomposition failed inside its
declared budget. And per **F39**, "somewhere a human looks" must be verified rather than assumed - the
finding is only closed when it can be retrieved.

### Idempotence is the second half, and the logs already demand it

```
MVStoreException: Map entry <table.166> ... 'test-forty-seventh' ...
is locked by tx 2 and can not be updated by tx 1 within allocated time interval 2000 ms
```

Two transactions were writing the same project row. So admission must be an atomic **claim**, not a check
followed by a write - the pattern already used for `handlePrOpenedWorkflow`'s completion claim and the
design shop's per-project claim. One live claim per wishlist; a second carrier cannot be created while one
holds it.

### Order, and what must NOT be touched

1. **F42 first** - terminate compilation.
2. **F41 second** - the matcher's discriminating terms.

Fixing the matcher first would store six ideas twenty times, which is worse than one epic. And three things
stay untouched on purpose:

- **the cooldown** - it is the wrong instrument, not a mis-tuned one; changing its value hides the loop
  without ending it;
- **the per-attempt plan files** - they are the evidence that made this visible at all;
- **the matcher** - until its input stops being duplicates, any measurement of it is meaningless.

### Verification, stated before the change rather than after

On a fresh project with one brief: exactly **one** `task-plan-*.json` in `.eneik/records`, the wishlist
reaching `converted_to_task`, and the epic count in `/tree` **equal to** the epic count in that one plan.

The last clause is the point: repairing F42 converts F41 from a suspicion into a measurement. If the plan
holds 6 epics and the tree holds 6, both are answered at once. If the tree holds fewer, F41 is isolated,
quantified, and only then worth touching.

## RESULT of the fourth run - test-forty-ninth, 2026-08-16

F42 and F41 are both settled, on the running factory rather than in tests.

| Measure | 46th | 47th | **49th** |
|---|---|---|---|
| Compile carrier tasks for one brief | 16 plans | 4 | **1** |
| Epics in plan / features in tree | - | 20 / 1 | **5 / 5** |
| Epic merges logged | - | collapsed all | **none - nothing to merge** |
| Deterministic scaffold (`pom.xml`) | - | never ran | **present** |
| Errors in the log | - | - | none |

**F42 closed.** One carrier task, `done`. The referent exit plus mu = B - compileAttempts terminated it.

**F41 closed, and the matcher is EXONERATED.** Five epics in the plan, five features in the tree, and not
one `SelfFalsificationEpicMatcher: matched` line. It never over-merged; it was fed twenty near-duplicates
that were six ideas repeated by four decompositions, and merging near-duplicates is its job. Not touching
its scoring blind was right - the repair would have broken something that works.

Five PRs merged: the task plan, an ADR on document storage, the search API contract, a search-analytics
schema, and review verdicts. The corpus floors keep landing: *Baseline Epidemiological Content
Provisioning* is F21's seeding obligation again.

**A reporting error of mine, recorded because the lesson is the plan's own.** The first pass this run
counted PLAN FILES and reported "2 compilations, the fix half-works". Two files existed, but one was named
by Jules on a timestamp - Java only ever emits UUID names - so the file count was an INDICATOR of
compilation, not compilation itself. Counting carrier tasks, the thing itself, gives one. I substituted an
indicator for the property inside a report about substituting indicators for properties, and had begun
proposing a hunt for a second write path that does not exist.

**RETRACTED: "the compiler does not discriminate Kano".** Operator's correction, and it is the doctrine's
own rule turned on my own reporting.

All five epics COULD NOT have come out otherwise. The prompt itself mandates `Must-Be` for the measurement
epic (*"a product nobody can tell is working is indistinguishable from one that is not"*) and for every
regulatory-floor epic, which is where backup and recovery come from. The remaining three - cataloguing,
search, seeding - are the value chain of a knowledge base: without them there is no product. No delighter
was requested, and inventing one is explicitly forbidden.

**A test whose outcome is fixed by construction is not a test.** I applied a measurement to a proposition
whose value the prompt had already determined, and read the result as evidence. That is the same shape as
the matcher's Kano bonus - a term built to discriminate contributing a constant - which I found in the code
this morning and then committed in my own analysis.

**Three times this run I issued a WITHHOLD where the honest verdict was ABSTAIN:** "the compiler does not
discriminate" (input contained nothing to discriminate), "the fix half-works" (I counted plan files, an
indicator), "the matcher over-merges" (its input was duplicates). Each converted absence of evidence into a
refusal - the defect this entire doctrine exists to remove, committed by the author of the layer that
forbids it.

**And I broke the Barcan condition:** propositions must be DECLARED before they are ruled on. I found
numbers and invented propositions to fit them afterwards, so I could not tell "undecided" from "never
considered".

Declared now, before further observation:

```
P1  the compiler discriminates Kano classes
    testable ONLY on input that admits a non-Must-Be answer: a client request beyond
    necessity, a falsification finding, a request that would harm the product.
    Until such input exists: ABSTAIN. That is not a defect.
P2  the compile loop terminates              CLOSED - PERMIT (one carrier task)
P3  the epic matcher does not over-merge     CLOSED - PERMIT (5 plan epics = 5 tree features, no merges)
P4  the deterministic scaffold runs          PERMIT (pom.xml present)
```

### Second pass on test-forty-ninth, 07:47 UTC+4 - repairs holding

**The client brief was compiled ONCE and is `converted_to_task` with `compile_attempts = 0`.** Two compiler
tasks exist, but they carry two different wishlists: `56484b6d` is the brief (done), `ba6ac99c` is a finding
raised by the Gemini observer - new work, not a repeat. Counting compiler tasks rather than plan files is
the only reading that distinguishes them; the file count is still 2 because Jules names one of them itself.

**F40 confirmed by measurement:** `GET /api/projects/{id}/verdict` now answers in **8.6 s**, against 68.9 s
before the cache. Eight-fold, and it isolates the cause exactly where it was diagnosed - the per-table
`DISK_SPACE_USED` walk inside `inspect()`.

**Zero epic merges logged.** Nothing to merge, because duplicates are no longer produced. P3 stays closed.

Flow state: 24 tasks (11 done, 6 claimed, 6 queued, 1 in review), 21 wishlists of which 19 are slices of the
brief. Readiness reports `unknown` - correct: the work is unfinished and the system says "not established"
rather than "not ready", which is the Kleene mapping doing its job.

**The observer loop closed on this run**: a `gemini_observer` wishlist appeared, i.e. she found something
and raised work for it.

### Passes 3-4 on test-forty-ninth - the repair holds, and its limit is now known

Compiler tasks per wishlist, counted over three passes:

```
56484b6d  the client brief      1     held across the whole run
ba6ac99c  a Gemini finding      2     rose to 2, then STOPPED - never reached the budget of 3
506ea961  a later wishlist      1
```

**F42 holds, and its boundary is measured rather than assumed.** The brief compiled once. One wishlist
reached two attempts and stopped on its own, so both exits work: the referent test ends a wishlist that has
produced slices, and nothing has yet needed the budget - `exhausted its decomposition budget` has not fired
once.

**Known limit of the repair, stated honestly.** The referent test cannot stop a wishlist that has produced
nothing yet - by construction, since that is the case that legitimately retries. Only the budget bounds
that one. So a brief that keeps failing costs up to 3 attempts, not 1. Terminating, but not free. The
atomic dispatch claim proposed earlier and not implemented would close it at 1, and would also remove the
`Timeout trying to lock table "PROJECTS"` errors that have cost 3 coverage-audit checks and 1 task dispatch
in an hour.

**F40 confirmed stable:** verdict endpoint 8.6 s, then 9.0 s, against 68.9 s before the cache.

**Zero epic merges across the whole run.** P3 stays closed.

**A Gemini finding that did not survive checking.** She reported *"tasks marked done disagree with their
actual GitHub PR state"* and offered task `dc09037e` as evidence. That task is `Compile 1 wishlist(s)
(56484b6d)` and it is legitimately `done` - the very compile that decomposed the brief once. The claim may
hold elsewhere; the evidence offered for it does not. By this plan's own rule that is an `ABSTAIN`, not a
confirmation - and it is worth noting that the observer's finding is the same CLASS as F1, which suggests
she is looking in the right place even when the instance is wrong.

Flow: 28 tasks, 19 done, no errors beyond the known lock timeouts.

### Later passes on test-forty-ninth - what held, and what I got wrong

**Held.** Four distinct wishlists, five compile tasks: the client brief exactly ONE, one Gemini finding two
(stopped on its own), two later wishlists one each. `exhausted its decomposition budget` never fired.
Zero epic merges across the entire run - P3 confirmed four passes running. Flow reached 34 tasks, 23 done.

**Database compacted with the operator's approval.** 549 MB -> 378 MB on clean stop -> **91 MB** after
`SHUTDOWN COMPACT`. Row counts identical before and after: tasks 1279, wishlist 1194, projects 22, features
190. Backup kept at `data/eneik_db.mv.db.pre-compact-20260816`.

**F40 IS NOT FIXED, AND MY DIAGNOSIS WAS WRONG - not merely incomplete.**

```
before the cache   68.9 s
one hour later      8.6 s   <- I reported this as the repair working
after compaction  >120 s   <- worse than before any of it
```

`/api/settings` answers in 0.5 s in the same minute, so the machine is not loaded. And the database is now
six times smaller, which should have made a per-table scan six times faster - it got slower. **Therefore
the per-table scan is not the cause.** I found a method that holds a connection past 30 s and concluded it
was also the source of the 69 s, which was inference from coincidence, not measurement. The cache masked it
for its ten-minute lifetime and I sampled inside that window and called it fixed.

That is the fourth time in this run I turned a single observation into a law. The others: counting plan
files instead of compile tasks; reading all-Must-Be as a classifier defect when the prompt mandates it;
proposing an atomic dispatch claim that already exists at `ProjectFlowService:1903`.

**The honest next step is measurement, not repair:** time each `VerdictLayer` separately. Until then the
cause is unknown, and the infrastructure and acceptance layers are unobservable because the endpoint that
exposes them hangs.

**Also open:** 22 `Timeout trying to lock` / connection-leak lines per 35 minutes, unchanged by compaction -
consistent with the size not being the cause. Two tasks (`Data Schema`, `UI Slice`) sit `failed` with
`retry_count = 0`; the count is static, not a cascade, and the reason is not yet established.

### F43. The unblock message loop - F42 in a second place, and this one burns Jules quota

Traced from the two `failed` tasks on test-forty-ninth, backwards through the log.

```
05:49  task 36651896 early-unblocked on an open-but-unmerged spec dependency, started in parallel
06:00  told its dependency had finalised
06:50  declared stale -> "Sent Forced stale-revising unblock message"
06:51  Sent Forced stale-revising unblock message
06:52  Sent Forced stale-revising unblock message
       ... once every 60 seconds, same session, same task ...
07:54  Flow Core: BLOCKED_BY_TASK - ORCHESTRATE and RECOVER_FAILED_FRONTIER denied for the WHOLE project
07:54  iteration-admission poka-yoke retires the task to clear the block
08:11  identical sequence for task ab74be69
08:31  Gemini dismisses both orphaned wishlists, freeing the compiler-admission gate
```

**Over sixty forced messages into one Jules session in one hour, for one task.** And it is still running:
6 messages in the last 40 minutes on another task.

**This is exactly F42's shape in a different place.** A rate limit (one per minute) with no attempt bound:
nothing decreases, so nothing terminates. The loop is not ended by its own logic but by a *different*
mechanism an hour later, and the price is paid twice - Jules quota, and the whole project frozen in
`BLOCKED_BY_TASK` while one task sits blocked.

The same repair applies and is already written and proven: a declared finite budget with a counter, so
`mu = B - attempts` decreases on every message and the task is declared unblockable after B rather than
after an hour. Site: `JulesDispatchService`, the `Forced stale-revising unblock message` path.

**Note the cost asymmetry that makes this worse than the compile loop.** A repeated decomposition wastes a
Jules session. A repeated unblock message wastes a session AND holds the entire project in a state where
Flow Core denies orchestration - so one stuck task stops all work until the poka-yoke fires.

### F44. The design shop waits behind a threshold the run may never reach

Measured on test-forty-ninth at 37 tasks, 32 done, queue empty:

```
readiness: unknown
unmet:     "Some tasks are not done or in review"
           "Some quality gates failed"
```

`DesignShopOrchestrationService.tick` runs every 5 minutes over active projects, but only STARTS a cycle
when readiness RISES to 1.0. Readiness is a conjunction that includes every task being done and every
quality gate passing. This project has two `failed` tasks - retired by the iteration-admission poka-yoke
after the forced-unblock sequence - and at least one failed quality gate.

**So the design shop is not broken and is also not reachable.** A single task that fails anywhere in the
run closes the door permanently for that project, because readiness can then never rise to 1.0 again.
Zero `DesignShop` log lines across the whole run is the correct behaviour of an unsatisfiable condition.

That is the answer to the operator's question "how do you do a full run without design": design sits behind
a gate the run does not reach, and nothing reports that fact - the shop simply stays silent, which looks
identical to it being switched off. It was in fact enabled the whole time.

**Not proposing a fix.** Whether the trigger should be a rising edge on 1.0, a threshold below 1.0, or an
explicit operator action is a product decision about when a client should be shown designs, not a defect to
be patched. Recording it as measured.

Also confirmed this pass: 135 `SQL Error 90020` (database already in use) all predate the backend restart,
zero since; no repeated meaningful log line in 35 minutes, so no new F42/F43-shaped loop; the client brief
still shows exactly ONE compiler task across eight passes.

### F45. The design-system call sends Eneik's own project id to Stitch

First activity from `design_system_falsification_enabled`, switched on 2026-08-16. It fails, and the error
is exact rather than generic:

```
StitchClient: tool call create_design_system returned an error result: Requested entity was not found.
```

The HTTP call succeeds; Stitch answers, and its answer is that the entity does not exist. Of the two
arguments only one is a REFERENCE to something that must already exist:

```java
stitchClient.createDesignSystem(
        project.getId().toString(),   // <- Eneik's internal project UUID
        epic.title(), "LIBRE_CASLON_TEXT", "IBM_PLEX_SANS", "#7d8570", "#c99a2e");
```

`41af381d-...` is a row id in this system's own database. Stitch has never heard of it; Stitch projects
carry Stitch ids.

**The system already knows these are different identifiers - in one place.** `DesignAssetResult.stitchProjectId`
exists precisely because a Stitch project id is not an Eneik project id. One concept in two places, only one
of which knows - the same shape as F17's Kano default, F34's two spellings of an objection, and the two
plan parsers.

**Not fixed.** The repair is to pass the Stitch project id, but whether this project HAS one, where it is
persisted, and what should happen when it does not are three things I have not read yet. A fifth guess in
one day is worth less than one reading.

Frequency is low - 2 occurrences in 35 minutes, against 3 in three seconds when first observed - so this is
not an F42/F43-shaped loop and does not burn quota.

**F37. `ProjectEntity.targetMarkets` is declared but unreachable.** The column and the reading side landed
in Step 15, and `test-forty-seventh` was created with `targetMarkets: null` - because nothing can set it.
There is no field on `ProjectCreateRequestDto`, no settings key, no UI. Every project therefore gets F19's
safe default: both DE and US rendered, with the "market undeclared" preamble stating the assumption. That
is the correct fallback and it is also the only reachable behaviour, so the declaration currently buys the
statement in the prompt and nothing else. Completing it means a create-time field and a way to change it
later.

### Carried forward from earlier steps

- **F2** - settings audit trail. Needs its own entity, migration and write-path interception (deferred at
  Step 6 so one commit would not do two unrelated things).
- **`looksLikeUi` vocabulary** - `public` still counts as a UI term, so "public API contract" matches. Step
  7 fixed *how* terms are matched; *which* terms count needs evidence.
- **The remaining polls→events conversion** - ten call sites, deliberately deferred pending measurement.

### Left open by steps 12-18

- **Step 13** - the acceptance **walker**. `client_acceptance_traversals` has a reader and no writer; what
  performs a client-facing walk of the live instance is Stage 6 of the findings' own order of work.
- **Step 16** - the correct Linear team **UUID**. Finding it means calling Linear with the operator's key,
  which is their decision.
- **Step 17** - three categories of Cyrillic that must not be translated as text (charter **referents**,
  **matchers** over historical Russian content, **mojibake signatures**), plus **116 `docs/` files** left
  for their own commit.
- **Step 18** - moving existing thresholds inside their owning layers, and, before the flag may ever be
  switched on, repairing **the two layers that still report numbers derived from substring matching**.
  Gating on a proxy measure would undo what eighteen steps were spent removing.

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
| 8 · design shop: modal kinds of failure | **done** | |
| 9 · the verdict lattice, read-only | **done** | |
| 10 · duplicate metric counted recovery as waste | **done** | |
| 11 · ask whether the observed product still exists | **done** | |
| 12 · the factory watches itself | **done** | `85353b3` |
| 13 · the acceptance chain | **done** | `effdc35` |
| 14 · missing links in the value chains | **done** | `7b97f41` |
| 15 · Kano gradation, scoping, market coverage | **done** | `7b97f41` |
| 16 · Linear provisioning | **done** | `7b97f41` |
| 17 · English-only pass | **source done, docs open** | |
| 18 · gating | **built, flag off** | |

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

## Watch pass 2026-08-16 16:04Z — test-forty-ninth (41af381d)

Measured over the HTTP API only: `docker logs` is unavailable this pass (WSL→Windows interop dead,
`accept4 failed 110` on every `.exe`), so the log-frequency loop hunt could not be run. The loop below
was found instead through the wishlist trail, which carries the same evidence.

### F46 (LOOP, live, costs real money) — design-system falsification re-creates a Stitch design system every 30 min and never applies it

Three `design_system_falsification` wishlists, all `dismissed`, each naming a **different** Stitch
design system id:

```
2026-08-16T15:30:02Z  Stitch design system 117098067252365250   epic 'Epidemiological Material Search and Retrieval'  apply failed
2026-08-16T15:30:03Z  Stitch design system 15040433662171631907 epic 'Knowledge Base Utilization Measurement'         apply failed
2026-08-16T16:00:06Z  Stitch design system 16711088566797296707 epic 'Epidemiological Material Cataloging and Mgmt'    apply failed
```

The 30-minute cron creates a fresh design system per eligible epic, `apply_design_system` fails, the
finding is dismissed, and the next tick starts over from scratch. **No attempt counter, no budget,
nothing decreasing** — the exact F42/F43 shape. Unlike those, each iteration allocates a real
billable Stitch resource, so the cost grows without bound.

Fix shape (do NOT patch by widening the cooldown): an attempt budget per (project, epic) with a
well-founded measure, and reuse of an already-created design system id instead of creating a new one
on every retry.

### F47 — the apply failure carries no underlying reason

Full recorded text is `"apply failed - Stitch apply_design_system call failed."` and nothing more.
The real Stitch error is dropped, so F46 cannot be diagnosed from the wishlist trail alone. The
underlying cause of the apply failure is therefore **not yet established** — F45 fixed creation
(ids are now real), application is a separate, still-open failure.

### F48 (STALL) — every non-terminal task is flagged stale; board shape unchanged since last pass

`pipeline`: `done 32, claimed 3, queued 1, failed 2`. The non-done shape (2 failed / 3 claimed /
1 queued) is **identical to the previous pass** — an unchanged board, i.e. a stall, not stability.
The system's own `blockedItems` agrees, flagging all four non-terminal items:

```
claimed | UI Slice Ed79db3e     | stale_in_progress
claimed | Test Coverage 1f67e34a | stale_in_progress
claimed | API Slice 77380b22     | stale_in_progress
queued  | Test Coverage F55efeef | stale_in_progress   (oldestWaitingMinutes = 218, BARCAN-TAG-06)
```

### F49 — a task reports `done` while its code never reached main

```
done | Runtime Contract 8becdc01 | done_not_reached_main
```

Status and merge evidence disagree. This is the readiness invariant catching a task that closed
without landing.

### F50 — every epic has a null Kano class

All 6 epics return `kanoClass: null` from `/epics`. After F31 the dashboard reads `kanoClass`
instead of guessing, so the field being empty is now visible rather than masked — nothing is
writing it.

### F36 extension — `/tree` hangs like `/verdict`

`GET /api/projects/{id}/tree` → 45 s timeout, 0 bytes, while `/dashboard` (401 KB) returns in 8.1 s
and `/epics`, `/pull-requests`, `/observer-journal`, `/runtime-health` all return normally. Same
hang shape as `/verdict`; whatever F36 really is, it is not unique to the verdict endpoint.

### Not defects this pass

- **Design shop silent** — correct. `featureReadinessRatio = 0.5` (3 of 6 features complete); the
  shop only starts on the rise to 1.0. Expected, per the watch brief.
- **Philosophical falsification not running** — `falsificationEligible: false`
  (`0.5 < 0.9` threshold), `decompositionComplete: false`, status `decomposing`. Gated, not broken.
- **Client brief still exactly 1 compiler task** — holds.
- `pull-requests` returns 0 while `mergedPlannedTasks = 22`; the client repo does carry real
  branches (`git ls-remote` lists api-contract-…, barcan-tag-02-…, barcan-tag-06-…, barcan-tag-12-…).
  Endpoint scope vs. reality not yet reconciled — measure before calling it a defect.

## Watch pass 2026-08-16 16:18Z — test-forty-ninth (14 min after previous)

`docker logs` still unavailable (WSL interop dead). Measured over the HTTP API.

### F48 CORRECTED — the board is NOT stalled; `stale_in_progress` is not proof of death

`Test Coverage 1f67e34a` moved `claimed -> done` and merged between the two passes:

```
pipeline prev: queued 1, claimed 3, done 32, failed 2   mergedPlannedTasks 22
pipeline now : queued 1, claimed 2, done 33, failed 2   mergedPlannedTasks 23
```

That task was flagged `stale_in_progress` in the previous pass and finished 14 minutes later. My
previous entry reported all four non-terminal items as stalled on the strength of that flag; that was
an overstatement. **`stale_in_progress` marks a staleness window elapsing, not a dead task** — it is
the same class of false positive the watch brief warns about for the observer.

What IS genuinely stuck: `Test Coverage F55efeef`, queued, `oldestWaitingMinutes` 218 -> 234 — grown
by exactly the 16 minutes elapsed, i.e. no progress at all. One item, not four.

Unchanged: `failed 2` (the two awaiting replacement), `featureReadinessRatio 0.5`,
`completeFeatures 3/6`, `decompositionComplete false`, `falsificationEligible false`, status
`decomposing`. Wishlist counts identical across all six sources.

### F51 (NEW, loop + unsound evidence) — the observer repeats one overstated claim hourly and acts on it

Five journal entries, roughly hourly (08:26, 11:22, 12:25, 13:24, 14:22Z), all asserting the same
"massive, systemic state-desynchronization", each ending in an intent to nudge. Quoted:

```
14:22Z  "Verified that nearly all 'done' tasks are plagued by operational reality findings
         indicating a massive, systemic mismatch between the orchestrator's internal task status
         and actual GitHub PR states. ... Will try nudging the remaining stuck task candidates
         (fc6459d5 and c034c2fb) which have not yet been nudged."
13:24Z  "A large number of tasks (e.g., 23e34965, fc6459d5, c034c2fb) remain marked internally as
         'done' while operational reality findings confirm persistent conflicts with their GitHub
         PR status."
```

Checked against the board, every specific claim fails:

| her claim | measured |
|---|---|
| "nearly all 'done' tasks" desynchronized | **1 of 33** done tasks carries `done_not_reached_main` |
| `fc6459d5` a stuck candidate to nudge | exists, status `done` (`Data Schema 88bd5721`), **not flagged** |
| `23e34965` desynchronized | exists, status `done` (`API Contract 33533b16`), **not flagged** |
| `c034c2fb` a stuck candidate to nudge | **does not exist among this project's tasks at all** |

The one real instance is `Runtime Contract 8becdc01 | done_not_reached_main` — which she never names.

So this is both shapes at once: a **loop** (the same conclusion re-derived hourly, each round emitting
nudges that change nothing and never terminate) and **unsound evidence** (a quantifier stretched from
1/33 to "nearly all", plus a nudge target that does not exist). Her conclusion that the project is
"effectively stalled" is contradicted by the movement measured above.

Fix shape: her nudge action needs a well-founded measure like any other retry, and her findings need
the referent test — assert a task is stuck only against its live status, not against a remembered one.

### F52 (NEW) — the product has never been observed running

`/runtime-health`: `observationCount 0`, `lastObservedAt null`, `liveUrl null`,
`recentObservations []`. `posteriorMean 0.5` is the untouched prior, not evidence. Nothing has ever
launched this project's product, so every launchability signal downstream is uninformed.

### F46 — not re-confirmed this window

Still exactly 3 `design_system_falsification` wishlists; the 30-minute cron last fired at 16:00:06Z
and the next tick is due at 16:30Z, after this pass. Neither confirmed nor contradicted here.

## Watch pass 2026-08-16 16:48Z — test-forty-ninth

`docker logs` available again (WSL interop recovered). **The container is
`eneikproductionsys-backend-1`, not `eneik-backend`** — earlier passes in this session queried the
wrong name and read the resulting empty output as "no activity". Any such earlier conclusion drawn
from a silent `docker logs` is void.

### F53 (NEW, THE ANSWER to "why was no replacement created") — recovery of the failed frontier is denied by state, 52 times in 35 minutes

```
52   Continuous Orchestration: policy denied RECOVER_FAILED_FRONTIER for project test-forty-ninth
     in state DECOMPOSING: Operational action RECOVER_FAILED_FRONTIER denied by Flow Core state
     DECOMPOSING with authorization ENFORCED_ACTIONS_AVAILABLE.
```

Roughly once every 40 seconds. `RECOVER_FAILED_FRONTIER` is precisely the action that would produce
replacement work for the two permanently-failed tasks. Flow Core refuses it because the project is in
`DECOMPOSING` and enforced actions are still available.

And decomposition does not complete: `decompositionComplete: false` across every pass today, status
`decomposing`, `completeFeatures 3/6` unchanged.

So the shape is: **recovery waits for decomposition; decomposition does not finish.** Whether the two
failed tasks are themselves what holds decomposition open is the next thing to establish — if they
are, this is a genuine circular wait, not merely a slow one. Do not touch the policy before that is
measured.

The retry itself is also the familiar defect: 52 identical denials with no attempt counter and
nothing decreasing. The denial is an expected outcome of a rate-limited poll, so it should be logged
once per state, not once per tick.

### F54 (NEW) — 34 concurrent-claim collisions in 35 minutes on one workflow

```
34   handlePrOpenedWorkflow: session UUID pr_opened completion is already claimed by a concurrent
     invocation; skipping this one instead of risking duplicate work
```

The guard added earlier works — no duplicate work is done. But firing 34 times in 35 minutes means
several scheduler threads (`scheduling-task-2/6/8/10`) are racing on the same session continuously.
The guard is treating contention as normal steady state rather than preventing it.

### F46 UPDATED — the real Stitch error is `Request contains an invalid argument`

The underlying error F47 said was missing is now visible:

```
15:30:01.209Z WARN StitchClient: tool call create_design_system returned an error result:
              Request contains an invalid argument.
15:30:02.545Z WARN StitchClient: tool call apply_design_system returned an error result:
              Request contains an invalid argument.
16:00:06.681Z WARN StitchClient: tool call apply_design_system returned an error result:
              Request contains an invalid argument.
```

So F45 did **not** fix the argument problem. `create_design_system` is intermittent (failed for epic
`6e2959ed` at 15:30:01, succeeded for the same epic at 16:00:06); `apply_design_system` fails every
single time. The fix must be to the arguments themselves, against the Stitch tool schema — not to
the retry rhythm.

Worse, the failure is recorded as a success:

```
16:00:06.729Z INFO DesignSystemFalsificationService: recorded design-system pass for epic
              6e2959ed (...), designSystemId=16711088566797296707, applied=false
```

A "pass" with `applied=false` is a **false-success record** — the design system exists and was never
applied, but the pass is booked.

Correction to my own F46 framing: this is **not** a fixed-period unbounded loop. Passes occurred at
15:30:01, 15:30:02, 15:30:03 and 16:00:06, and **no tick appears at 16:30** (last design line in a
90-minute window is 16:00:06). The cost claim stands — three orphaned Stitch design systems exist —
but "every 30 minutes forever" was not measured and is withdrawn.

### F55 (NEW) — connection leak still occurring

One `java.lang.Exception: Apparent connection leak detected` in the 35-minute window, after all the
transaction-span work. Not eliminated.

### STALL — the board is unchanged over 30 minutes

```
16:18Z  queued 1 · claimed 2 · done 33 · failed 2   mergedPlannedTasks 23
16:48Z  queued 1 · claimed 2 · done 33 · failed 2   mergedPlannedTasks 23
```

Identical. `oldestWaitingMinutes` 234 -> 264, grown by exactly the 30 minutes elapsed. All six
wishlist source counts identical. This is a stall, not stability.

Two open PRs are being re-inspected every orchestration tick (21 times in 35 min): PR #61
`Fix Epidemiological Protocol Search Test Expectations` and PR #60
`feat(ui): implement interactive protocol management dashboard`. Neither is merging.

## Watch pass 2026-08-16 17:18Z — test-forty-ninth

### The stall from the previous pass broke — substantial movement in 30 minutes

```
16:48Z  queued 1 · claimed 2 · done 33 · failed 2   merged 23/27  features 3/6  ratio 0.500
17:18Z  queued 0 · claimed 1 · done 35 · failed 2   merged 25/27  features 4/6  ratio 0.667
```

`blockedItems` collapsed from 4 to 1. Both open PRs closed during the window — BRANCH-GC observed
`Found 2 open PR` once, `Found 1 open PR` twice, then `Found 0 open PR` 32 times.

The queued `Test Coverage F55efeef` that had waited 264 minutes **did dispatch and complete**. So the
previous pass's "stall" was a 30-minute quiet window inside a working flow, not a stopped flow. Two
passes of identical counts is evidently not sufficient evidence of a stall in this system; the
dispatch cadence is slower than the watch interval.

### F53 REFINED — the denials are loud but only one of them actually blocks

All four denial types, counted over 35 minutes, every one citing state `DECOMPOSING` with
authorization `ENFORCED_ACTIONS_AVAILABLE`:

```
36  policy denied RECOVER_FAILED_FRONTIER
32  policy denied DISPATCH_REVIEW_TASKS      (+32 "Flow policy denies review dispatch; skipping")
18  policy denied DISPATCH_QUEUED_TASKS      (+18 "Flow policy denies queued-task dispatch; skipping")
 1  policy denied ORCHESTRATE
```

Critically, `DISPATCH_QUEUED_TASKS` was denied 18 times **and the queued task still dispatched and
finished within the same window**. So these denials are a periodic poll hitting a
temporarily-unauthorized action, not a hard block — work proceeds by other paths. My previous
framing, that the queued task's 264-minute wait was caused by this denial, is withdrawn: it was not
measured, and the outcome contradicts it.

What does survive: **`failed` stayed at 2 across every pass today** while everything else moved. With
`RECOVER_FAILED_FRONTIER` denied 36 times in 35 minutes and no replacement work appearing, the
missing-replacement problem remains real and remains attached to this action. The circular-wait
hypothesis is now weaker, though — decomposition is clearly still advancing
(`completeFeatures 3 -> 4`), so recovery is waiting on a moving target rather than a stuck one.

Still unmeasured, and the next thing to establish: whether `RECOVER_FAILED_FRONTIER` becomes
authorized once `decompositionComplete` flips true, or whether the two failed tasks are themselves
among the 2 unmerged of 27 that keep it false.

### F46 CLOSED as not-a-loop

Zero design/Stitch log lines in the last 40 minutes; last activity remains 16:00:06Z. The 16:30 and
17:00 ticks produced nothing. This is consistent with the sweep recording one pass per epic and
finding nothing new afterwards — i.e. it terminated on its own after covering each eligible epic
once. The "unbounded 30-minute loop" framing is withdrawn entirely.

What remains real from F46, unchanged: `apply_design_system` fails every time with
`Request contains an invalid argument`, three Stitch design systems were created and never applied,
and the failure is booked as a pass with `applied=false`.

### F50 unchanged — all 6 epics still report `kanoClass: null`

### Readiness now 0.667

Design shop starts only on the rise to 1.0; philosophical falsification needs 0.9. Both correctly
silent at 0.667. Two of six features remain incomplete, 2 of 27 planned tasks unmerged.

## Watch pass 2026-08-16 17:48Z — test-forty-ninth

### Board unchanged over 30 minutes — and this time the reason is measured

```
17:18Z  queued 0 · claimed 1 · done 35 · failed 2   merged 25/27  features 4/6  ratio 0.667
17:48Z  queued 0 · claimed 1 · done 35 · failed 2   merged 25/27  features 4/6  ratio 0.667
```

Identical, including all six wishlist source counts and `blockedItems` (still the single
`done_not_reached_main` on `Runtime Contract 8becdc01`).

Unlike the previous unchanged-board pass, this one is explained rather than asserted. Exactly one
unit of work is in flight — `b50a4511 API Slice 77380b22` (BARCAN-TAG-02) — and it is alive:

```
17:45:45Z  ClaimService: Maintenance: Extended lease for task b50a4511-13bc-4033-b0b2-652ced78835d
           because Jules session is still active
```

Lease extensions over a 6-hour window: **1 for `b50a4511`, 1 for `bc113800`, nothing else.** So the
extension is a one-off for live work, not a renewal loop — this is not the F42/F43 shape.

An unchanged board with one live session and an empty queue is a narrow pipeline, not a stall.

### Real code landed in the previous window

```
17:16:55Z  [BRANCH-GC] Inspecting open PR #65 ('test(qa): add Epidemiological Protocols management
           QA automated test suite')
           merged PR #65
```

That merge is already accounted for in the `done 33 -> 35` / `merged 23 -> 25` jump reported at
17:18Z. `Found 0 open PR` 44 times since — the repo currently has no open PRs.

### F56 (NEW) — an action is denied 35 times while there is nothing for it to do

```
35  policy denied RECOVER_FAILED_FRONTIER
35  policy denied DISPATCH_QUEUED_TASKS
34  policy denied DISPATCH_REVIEW_TASKS
 1  policy denied ORCHESTRATE
```

`DISPATCH_QUEUED_TASKS` was denied 35 times in 35 minutes while `totalQueued` is **0** and
`queue.byTag` is empty. The policy is being consulted, and the denial logged at INFO, for an action
that has no work to act on either way. Same for `DISPATCH_REVIEW_TASKS` with `review 0`.

This is not a functional defect — nothing is blocked that would otherwise proceed — but it is the
reason the log looks like a system in distress when it is merely idle. The denial should be logged
once per state transition, not once per poll, and ideally not evaluated at all when the action's
work set is empty.

### `failed` still 2, still no replacement

`RECOVER_FAILED_FRONTIER` denied 35 more times this window. `ab74be69 UI Slice 1559c9b0`
(BARCAN-TAG-11) and `36651896 Data Schema 7dd76d5f` (BARCAN-TAG-08) have been `failed` all day with
no replacement work created. This remains the one open substantive finding.

### Clean this window

Zero `Apparent connection leak`, zero `Timeout trying to lock`, zero `BLOCKED_BY_TASK`.
Zero design/Stitch lines (last activity still 16:00:06Z). Readiness 0.667 — design shop (1.0) and
philosophical falsification (0.9) both correctly silent. All 6 epics still `kanoClass: null`.

## Watch pass 2026-08-16 18:18Z — test-forty-ninth

### REAL STALL — the system raised it itself, at ERROR, and it is correct

First occurrence `2026-08-16T18:02:51Z`, escalating once per minute:

```
ERROR  SYSTEM STALLED: no forward progress (dispatch/merge) for 60 minutes with actionable work
       present: queuedTasks=0, pendingOrCompilingWishlists=0, activeNonTerminalTasks=1,
       reviewTasksWithPr=0.
```

The board confirms it — unchanged across three consecutive passes, a full hour:

```
17:18Z  queued 0 · claimed 1 · done 35 · failed 2   merged 25/27  features 4/6  ratio 0.667
17:48Z  queued 0 · claimed 1 · done 35 · failed 2   merged 25/27  features 4/6  ratio 0.667
18:18Z  queued 0 · claimed 1 · done 35 · failed 2   merged 25/27  features 4/6  ratio 0.667
```

**This reverses my previous pass's call.** At 17:48 I reported "narrow pipeline, not a stall" because
`b50a4511 API Slice 77380b22` held a live Jules session. That was true then. Since then the session
went stale-revising and the task has not moved for over 90 minutes. The reading was right for its
moment and wrong as a forecast — a live session is evidence of work now, not evidence that work will
continue.

Also 16 occurrences in 35 minutes of the milder variant, same root cause:

```
16  SYSTEM STALLED: Branch Garbage Collector not triggered because no review task with a PR URL
    is actionable.
```

### F43's fix VERIFIED LIVE — the forced-unblock budget holds

Exactly **2** forced-unblock messages in a 6-hour window, both to the same session, 30 minutes apart:

```
17:38:53.605Z  Sent Forced stale-revising unblock message to Jules session sessions/8476359396578350915
18:08:54.176Z  Sent Forced stale-revising unblock message to Jules session sessions/8476359396578350915
```

Configured maximum is 2 (`jules.forced-unblock-max-attempts`). The measure decreased, the backoff
spaced the attempts, and the loop terminated. Against F43's original 60+ nudges in an hour, this is
the fix working exactly as designed — and it is the mechanism I nearly destroyed on 2026-08-16 by
adding a duplicate `forced_unblock_attempts` column on the false premise that no counter existed.
Recorded here as live confirmation that the existing machinery was correct and my "fix" was the bug.

### What to watch next pass

The nudge budget is now **spent** on session `8476359396578350915`. The question the next pass must
answer by measurement, not inference: does the task get retired/failed now that the budget is
exhausted, or does it sit `claimed` indefinitely? If nothing retires it, the termination proof stops
at "stop nudging" instead of reaching a terminal state, and `b50a4511` becomes a third permanently
stuck task alongside the two `failed` ones.

### Unchanged

`failed` still 2 — `ab74be69 UI Slice 1559c9b0`, `36651896 Data Schema 7dd76d5f` — no replacement,
`RECOVER_FAILED_FRONTIER` still denied. All six wishlist source counts identical. Zero open PRs.
Zero design/Stitch lines (last activity 16:00:06Z). Readiness 0.667: design shop (1.0) and
philosophical falsification (0.9) correctly silent. All 6 epics still `kanoClass: null`.

## Watch pass 2026-08-16 18:48Z — test-forty-ninth

### F43 HAS RECURRED LIVE — 27 forced nudges in 60 minutes, one per minute, to a single session

```
27  Sent Forced stale-revising unblock message to Jules session sessions/8476359396578350915
```

All 27 to the same session. The cadence:

```
17:38:53   <- nudge 1
18:08:54   <- nudge 2   (30 min apart, backoff working)
18:38:26   <- nudge 3
18:39:08
18:39:53
18:40:53  18:41:53  18:42:53  18:43:53  18:44:53
18:45:53  18:46:53  18:47:53  18:48:53   <- every 60 seconds, still going
```

**This directly contradicts what I reported one pass ago.** At 18:18Z I wrote "F43's fix VERIFIED
LIVE — the forced-unblock budget holds, exactly 2 in a 6-hour window" and recorded it as confirmation
that the existing machinery was correct. Thirty minutes later the same session had taken 27. I
measured two data points during a backoff interval and published them as a proof of termination.
That is the second time today I converted a snapshot into a verdict (the first being "narrow
pipeline, not a stall" at 17:48Z). The 18:18Z entry's verification claim is **withdrawn**.

What is measured, and nothing beyond it:
- the budget is configured at 2 (`jules.forced-unblock-max-attempts`)
- 27 nudges went out inside one hour to one session
- the first two respected a 30-minute backoff; from nudge 3 the interval collapsed to 60 seconds
- the collapse begins at 18:38, after the project entered `SYSTEM_STALLED`

Whether the stalled state routes through a different, unbudgeted nudge path — or the counter is
reset, or not persisted — is **not established**. I am not touching it. The last time I acted on a
guess about this exact mechanism I added a duplicate `forced_unblock_attempts` column and broke the
migration and every integration test.

### F57 (NEW) — the stall state forbids the action that would clear the stall

Flow Core moved the project from `DECOMPOSING` to `SYSTEM_STALLED`, and recovery is refused there too:

```
policy denied RECOVER_FAILED_FRONTIER for project test-forty-ninth in state SYSTEM_STALLED:
Operational action RECOVER_FAILED_FRONTIER denied by Flow Core state SYSTEM_STALLED:
System status is stalled.
```

`RECOVER_FAILED_FRONTIER` is the action that would produce replacement work for the two failed tasks.
It is denied in `DECOMPOSING` because decomposition is unfinished, and denied in `SYSTEM_STALLED`
because the system is stalled. The state that exists to signal "work has stopped" is the state in
which the remedy for stopped work is unavailable.

This subsumes F53: the problem was never specific to `DECOMPOSING`.

### F58 (NEW) — the dashboard and Flow Core disagree about the project's state

`/dashboard` reports `productReadiness.status: "decomposing"` at 18:48Z while Flow Core is enforcing
policy against state `SYSTEM_STALLED` in the same minute. Two readers of project state, two answers.
The dashboard is the surface an operator looks at, and it is the one showing the wrong value.

### Board — unchanged for 90 minutes

```
17:18Z  queued 0 · claimed 1 · done 35 · failed 2   merged 25/27  features 4/6  ratio 0.667
17:48Z  identical
18:18Z  identical
18:48Z  identical
```

`b50a4511 API Slice 77380b22` still `claimed`, still not retired despite the nudge budget being
nominally spent. Its lease was extended again at 18:46:45 "because Jules session is still active" —
so the system simultaneously believes the session is alive (extends the lease) and stuck (nudges it
every 60 seconds).

Answering the question the previous pass left open: **the task was not retired.** The termination
path stops at nudging and never reaches a terminal state.

### Unchanged

`failed` still 2, no replacement. All six wishlist source counts identical. Zero open PRs. Zero
design/Stitch lines since 16:00:06Z. Readiness 0.667 — design shop (1.0) and philosophical
falsification (0.9) correctly silent. All 6 epics still `kanoClass: null`.

## Watch pass 2026-08-16 19:18Z — test-forty-ninth — THE FULL CHAIN, MEASURED

### The nudge loop ended exactly as the watch brief predicts: by another mechanism, at a cost

45 forced nudges in 60 minutes, one per minute, all to session `8476359396578350915`, last one at
19:07:53. Then:

```
19:08:52.581  WARN  Poka-yoke: circuit breaker closed session 7b042351 for task b50a4511
19:08:52.582  WARN  Closed Jules session sessions/8476359396578350915 for task b50a4511
                    due to stuck_session_timeout
19:08:54.547  WARN  ProjectFlowService: retiring blocked task b50a4511 without creating a
                    recovery wishlist/task; product recovery reuses an existing planned task ID
                    or enters through self_falsification
```

Board: `claimed 1 -> 0`, **`failed 2 -> 3`**. `b50a4511 API Slice 77380b22` is now the third
permanently-failed task. The cost of the loop is one dead task.

### Why no replacement is ever created — the complete circle, every link measured

The retirement message states the design intent outright: retire **without creating recovery work**,
and delegate recovery to `self_falsification`. Both exits are then closed:

1. **`RECOVER_FAILED_FRONTIER`** — denied in `DECOMPOSING` ("decomposition unfinished") and denied in
   `SYSTEM_STALLED` ("system status is stalled"). Measured in both states today.
2. **`self_falsification`** — `falsificationEligible: false`, `falsificationThreshold: 0.9`,
   `featureReadinessRatio: 0.667`. Not eligible.

So a task fails → it is retired with no replacement by design → recovery is delegated to
self-falsification → self-falsification is gated behind a readiness threshold → and every failed task
without a replacement holds readiness down. **Failures suppress the only mechanism authorised to
repair failures.**

This is the answer to the operator's question of 2026-08-16 ("why was no replacement created"),
now established by measurement rather than inference. It is also the same failure mode
`ClientDeliverableReadinessService` documented from the 2026-08-06 incident (task `5ac0b91b`,
"retired by iteration-admission poka-yoke with no child work created") — recurring live, unfixed.

Note: `selfFalsificationReadyRatio` — the metric that grants dead-end credit specifically to break
this circle — is **not exposed by `/dashboard`**, so whether the credit is being applied and still
falls short, or is not reaching this gate at all, could not be measured from outside. That is the
one remaining unknown, and it is the right place to look first.

### Do not fix this by lowering the threshold or by excluding failed tasks from the denominator

Both would be the patch-instead-of-mathematics move. The defect is the circular dependency itself:
the repair mechanism must not be gated on a quantity that failures reduce.

### F58 persists — dashboard still disagrees with Flow Core

`/dashboard` reports `status: "decomposing"` at 19:18Z; Flow Core denied `RECOVER_FAILED_FRONTIER`
against state `SYSTEM_STALLED` at 19:17:56.

### Unchanged

`done 35`, `merged 25/27`, `features 4/6`, `ratio 0.667`, `mergedRatio 0.926`. All six wishlist
source counts identical. Zero open PRs. Zero design/Stitch lines since 16:00:06Z. Design shop (1.0)
and philosophical falsification (0.9) correctly silent. All 6 epics still `kanoClass: null`.
`blockedItems` back to one entry: `Runtime Contract 8becdc01 | done_not_reached_main`.

## Watch pass 2026-08-16 19:48Z — test-forty-ninth

### F59 (NEW, SERIOUS) — retiring a failed task RAISES readiness, because it leaves the denominator

Three consecutive measurements of the same project, nothing merged between them:

```
18:48Z  totalPlanned 27  merged 25  mergedRatio 0.9259  features 4/6 = 0.667
19:18Z  totalPlanned 27  merged 25  mergedRatio 0.9259  features 4/6 = 0.667
19:48Z  totalPlanned 26  merged 25  mergedRatio 0.9615  features 5/6 = 0.833
```

`mergedPlannedTasks` never moved — 25 throughout. `done` never moved — 35 throughout. **The
denominator shrank from 27 to 26**, exactly the retired task `b50a4511`, and readiness rose on both
axes: `mergedRatio 0.926 -> 0.962`, and a whole feature flipped to complete, `4/6 -> 5/6`.

A feature became "complete" because its outstanding task was killed, not because it was built.

This is precisely the third state the operator ruled out on 2026-08-16: *"if tasks are needed they
must be done; if they are not needed they must not be counted."* The system takes a third path — the
task is neither done nor replaced, it is dropped from the count — and the product's reported
readiness **improves as a direct result of work failing**.

The consequence compounds with the circle recorded at 19:18Z. Readiness gates self-falsification
(0.9), the design shop (1.0) and the philosophical track. Each retired failure pushes readiness
toward those thresholds. A project can therefore unlock its own falsification and design stages by
losing tasks — arriving at "ready" with less product, not more. `mergedRatio` is already 0.962 and
one retirement away from 0.9-class thresholds on that axis.

Note this cuts against the fix I was tempted toward earlier: excluding failed tasks from the
denominator is not a candidate repair, it is **the defect itself**, already implemented.

### The stall cleared — by the same retirement

Zero `SYSTEM STALLED` lines and zero forced nudges in the last 35 minutes, against 45 nudges an hour
earlier. Flow Core state returned from `SYSTEM_STALLED` to `DECOMPOSING`. Killing the task removed
the "actionable work present" condition, so the stall detector fell silent. The stall was resolved by
destroying the work it was complaining about.

### `RECOVER_FAILED_FRONTIER` still denied, now with 3 failed tasks

```
7  policy denied RECOVER_FAILED_FRONTIER ... in state DECOMPOSING ... ENFORCED_ACTIONS_AVAILABLE
7  policy denied DISPATCH_REVIEW_TASKS   ... same
7  policy denied DISPATCH_QUEUED_TASKS   ... same (queue is empty)
```

`falsificationEligible` still `false`. `failed` is 3: `ab74be69 UI Slice 1559c9b0`,
`36651896 Data Schema 7dd76d5f`, `b50a4511 API Slice 77380b22`. No replacement for any of them.

### Board otherwise unchanged

`queued 0 · claimed 0 · done 35 · failed 3` — nothing in flight at all. All six wishlist source
counts identical for the sixth consecutive pass. Zero open PRs. Zero design/Stitch lines since
16:00:06Z. Design shop (1.0) and philosophical falsification (0.9) still correctly silent — but see
F59 for why that silence may end for the wrong reason. All 6 epics still `kanoClass: null`.

## Watch pass 2026-08-16 20:18Z — test-forty-ninth

### CORRECTION to the 19:18Z entry — recovery DOES happen, via a third mechanism I never looked for

At 19:18Z I wrote that both recovery exits were closed and called it "the complete circle, every link
measured". That conclusion was wrong. There is a third path, and it fired:

```
20:00:15Z  OpsAuditorService: created recovery task 4bb0510f-9bc3-49bb-844d-e2f436980e73
20:08:50Z  [BRANCH-GC] Inspecting open PR #66 ('feat(api): recovery slice for epidemiological pr…')
20:08:58Z  AutoMergeService [DIRECT-SWEEP]: Found clean open GitHub PR #66
```

Board: a new task `Recovery API Slice` appeared and is already `done`; `done 35 -> 36`.

Full sequence: `b50a4511` retired 19:08:54 → `OpsAuditorService` created a recovery task 52 minutes
later → Jules implemented it → PR #66 opened, was swept clean and merged → done by 20:18. The factory
repaired its own failure end to end within 70 minutes, unaided.

The error in my 19:18Z analysis was one of exhaustiveness, not of measurement: I enumerated
`RECOVER_FAILED_FRONTIER` and `self_falsification` — the two paths the retirement message itself
names — verified both were closed, and declared the set complete. I never asked whether a third
mechanism existed. `OpsAuditorService` is not mentioned in that message and I had not encountered it
in any code I read. The "failures suppress the only mechanism authorised to repair failures"
conclusion is **withdrawn**.

### The real remaining gap — recovery covered the newest failure only

Exactly **one** `created recovery task` line in a 12-hour window. The two tasks that have been
`failed` since before this watch began got nothing:

```
ab74be69  UI Slice 1559c9b0    (BARCAN-TAG-11)   failed all day, no recovery task
36651896  Data Schema 7dd76d5f (BARCAN-TAG-08)   failed all day, no recovery task
b50a4511  API Slice 77380b22   (BARCAN-TAG-02)   failed 19:08, recovered 20:00  ✓
```

So the mechanism works and is not reaching the older failures. Whether that is an age cutoff, a
one-per-sweep rate, or a condition those two do not satisfy is **not established** — `OpsAuditorService`
has not been read yet. That is the next thing to measure, and it is a narrower and more tractable
question than the circle I described yesterday.

### The recovery task does not repair the readiness deficit

```
19:48Z  totalPlanned 26  merged 25  ratio 0.9615  features 5/6 = 0.833  done 35
20:18Z  totalPlanned 26  merged 25  ratio 0.9615  features 5/6 = 0.833  done 36
```

`done` rose by one and every readiness figure stayed put. The recovery task sits outside the planned
set, so merging it neither restores the lost denominator entry nor moves `mergedPlannedTasks`. The
product got the code back; the metric did not get the task back.

F59 therefore stands unchanged: the retirement's effect on readiness (27 -> 26 denominator) was never
undone by the recovery.

### Unchanged

`queued 0 · claimed 0 · failed 3`. `falsificationEligible false`, `decompositionComplete false`,
status `decomposing`. All six wishlist source counts identical for the seventh consecutive pass.
Zero design/Stitch lines since 16:00:06Z. Design shop (1.0) and philosophical falsification (0.9)
correctly silent at 0.833. All 6 epics still `kanoClass: null`. No `SYSTEM STALLED` lines, no forced
nudges, no connection leaks this window.

## Watch pass 2026-08-16 20:48Z — test-forty-ninth

### Answered: why only one failure was recovered — one auditor action per sweep

`OpsAuditorService` read directly. The mechanism:

- `@Scheduled(cron = "${ops-auditor.cron:0 */30 * * * ?}")` — every 30 minutes
- the auditor returns **`private record AuditorDecision(String tool, String subjectId, String reasoning)`**
  — a single tool call against a single subject per sweep
- `createTargetedRecoveryTask` is one of its tools; it re-validates its own precondition in code and
  refuses if `hasOpenRecoveryTaskFor(projectTasks, failedTaskId)`
- the recovery task carries `RECOVERS_FAILED_TASK_ID_KEY` in its payload, pointing back at the
  original, and inherits the failed task's role and featureId

So the reason the two older failures were not recovered is **not** an age cutoff or an eligibility
filter — it is that the auditor gets one action per 30-minute sweep and spent it elsewhere. The
hypothesis I offered last pass (age cutoff / rate / unsatisfied condition) is resolved: it is the
rate, by design.

Complete auditor record over three hours — only three actions exist:

```
19:30:35  dismissed orphaned wishlist 77380b22 for project test-forty-ninth
19:30:35  FLAGGED FOR HUMAN REVIEW - subject b50a4511
20:00:15  created recovery task 4bb0510f (role=BARCAN-TAG-02) for failed task b50a4511
```

The 20:30 sweep produced no log line at all — no decision was recorded, and the two older failed
tasks were not addressed. At one action per half hour, with dismissals and flags competing for the
same slot, a backlog of three failures cannot be worked down promptly even in principle.

### F60 (NEW) — an escalation to a human exists only as a WARN log line

```
19:30:35  WARN  OpsAuditorService: FLAGGED FOR HUMAN REVIEW - project test-forty-ninth
                subject b50a4511 - Task b50a4511 failed due to an 'iteration-admission poka-yoke'.
                Since this indicates a systemic rejection at the iteration gate, …
```

The auditor correctly diagnosed the failure — the iteration-admission poka-yoke, the same mechanism
named in the 2026-08-06 incident — and escalated it to a human. That escalation is a log line and
nothing else: it does not appear in `/dashboard`, in the observer journal, or in any wishlist. It is
detection without a reader, the same F5 shape already recorded against my own step-6 valueless-flag
reporter.

Note the auditor then recovered the task anyway at 20:00, so nothing was lost this time. The defect
is that the flag's visibility does not depend on that: had recovery not followed, the operator would
have had no way to learn the system had asked for them.

### Board fully unchanged — and now genuinely inert

```
20:18Z  queued 0 · claimed 0 · done 36 · failed 3   totalPlanned 26  merged 25  ratio 0.9615  features 5/6
20:48Z  identical in every field
```

Nothing in flight at all: zero queued, zero claimed, zero in review. Zero new recovery tasks this
window. All six wishlist source counts identical for the eighth consecutive pass. The only project
activity in the log is the denial poll — `RECOVER_FAILED_FRONTIER`, `DISPATCH_REVIEW_TASKS`,
`DISPATCH_QUEUED_TASKS`, 8 each, all citing `DECOMPOSING`.

With no work in flight, the sole remaining engine for this project is the auditor's one action per
30 minutes. `failed` stands at 3 with one recovered.

### Unchanged

`falsificationEligible false`, `decompositionComplete false`, status `decomposing`, readiness 0.833.
Design shop (1.0) and philosophical falsification (0.9) correctly silent. Zero design/Stitch lines
since 16:00:06Z. All 6 epics still `kanoClass: null`. No stalls, nudges, leaks or lock timeouts.

## Watch pass 2026-08-16 21:18Z — test-forty-ninth

### F61 (NEW) — the auditor is unobservable when idle, and it is the project's only remaining engine

Exactly **three** `OpsAuditorService` log lines exist in a 6-hour window, all inside a 30-minute
span:

```
19:30:35  dismissed orphaned wishlist 77380b22
19:30:35  FLAGGED FOR HUMAN REVIEW - subject b50a4511
20:00:15  created recovery task 4bb0510f for failed task b50a4511
```

Nothing before 19:30, nothing after 20:00. The service logs only when it *acts*; there is no
"sweep ran, no action taken" line. Its silence is therefore ambiguous — a sweep that ran and decided
nothing is indistinguishable from a sweep that did not run, or from a service that has stopped.

That ambiguity matters here specifically because, with zero tasks in flight, the auditor's one
action per 30 minutes is the **only** thing that can still move this project. Two consecutive sweeps
(20:30, 21:00) produced nothing, and from outside there is no way to tell whether it is deciding
"nothing to do" or is dead.

This is the inverse of F56: there, an action with an empty work set is logged 35 times in 35 minutes;
here, the one mechanism whose liveness actually matters logs nothing at all. Both are the same
underlying error — log volume is not tied to what an observer needs to know.

### The board is fully inert — third consecutive identical pass

```
20:18Z  queued 0 · claimed 0 · review 0 · done 36 · failed 3   merged 25/26  features 5/6 = 0.833
20:48Z  identical
21:18Z  identical
```

Zero tasks in any active state. No new tasks. All six wishlist source counts identical for the ninth
consecutive pass. The only log activity for this project is the denial poll (5 each of
`RECOVER_FAILED_FRONTIER`, `DISPATCH_REVIEW_TASKS`, `DISPATCH_QUEUED_TASKS`, all citing
`DECOMPOSING`).

### The recovered task's original remains `failed` forever

```
failed: b50a4511 API Slice 77380b22     <- recovery task built, PR #66 merged, recovery is `done`
        ab74be69 UI Slice 1559c9b0      <- no recovery
        36651896 Data Schema 7dd76d5f   <- no recovery
```

`b50a4511` is still counted among the three failures even though its replacement work shipped and
merged. The recovery task is a separate row; the original is never reconciled. So `failed` can only
ever grow, and the count carries no information about whether the work was actually recovered — two
of these three are genuinely outstanding, one is not, and nothing in the data distinguishes them.

This compounds F59: the retirement removed the task from the readiness denominator, and the recovery
did not put it back, and the original still shows as failed. The same task is simultaneously
uncounted for readiness and counted for failure.

### Unchanged

`falsificationEligible false`, `decompositionComplete false`, status `decomposing`, readiness 0.833.
Design shop (1.0) and philosophical falsification (0.9) correctly silent. Zero design/Stitch lines
since 16:00:06Z. All 6 epics still `kanoClass: null`. No stalls, nudges, leaks or lock timeouts this
window.

## Watch pass 2026-08-16 21:48Z — test-forty-ninth

### F61 CORRECTED — the auditor is enabled and sweeping; the silence has one specific cause

I claimed last pass that no "sweep ran, no action" line exists. That was wrong — it does:

```java
private void auditProject(ProjectEntity project) {
    List<Evidence> evidence = self.gatherAllEvidence(project);
    if (evidence.isEmpty()) {
        return;                                            // <- silent
    }
    ...
    if (decisions.isEmpty()) {
        log.info("OpsAuditorService: project {} - {} evidence item(s) gathered, "
               + "Gemini returned no actionable decisions", project.getName(), evidence.size());
        return;                                            // <- logged
    }
```

And the flag is on, read from the running system rather than from source defaults:

```
ops_auditor_enabled -> {enabled: true, source: "database"}
```

So the sweep runs, and the two remaining explanations are distinguishable: `decisions.isEmpty()`
logs at INFO and no such line appears anywhere, therefore **`gatherAllEvidence` is returning empty
for this project**. The auditor is not stuck and not dead — it is finding nothing to look at on a
project holding three failed tasks.

The evidence gatherers visible in the method are orphan-shaped —
`gatherOrphanedWishlistEvidence`, `gatherOrphanedDependencyChainEvidence`, plus lookups by
`findByProjectId` / `findBySourceWishlistIdIn`. That is consistent with the 19:30 action being an
orphaned-wishlist dismissal. Whether a plain `failed` task with no orphan attached ever produces
evidence at all is **not yet established** — I read the method's call sites, not its body. That is
the next measurement, and it is the sharp version of the question F61 asked vaguely.

### The board woke up — "inert" was wrong

```
21:18Z  queued 0 · claimed 0 · done 36 · failed 3   gemini_observer wishlists 11
21:48Z  queued 0 · claimed 1 · done 36 · failed 3   gemini_observer wishlists 13
```

New task `Build Pipeline 115f4b3f` is `claimed`, and the observer produced **two new findings**
(11 -> 13), the first movement in the wishlist counts across ten passes.

Last pass I described the board as "fully inert" after three identical readings. That is the third
time today I have turned a run of identical snapshots into a claim about the system's condition —
after "narrow pipeline, not a stall" (17:48) and "F43's budget verified" (18:18). The pattern is
consistent enough to state as a rule for this project: **at this flow's cadence, three identical
half-hourly readings are not evidence of a stopped system.** Only the system's own
`SYSTEM STALLED` detector, with its 60-minute no-forward-progress window, has been reliable on that
question.

### Unchanged

`failed` still 3 (`b50a4511` still listed despite its recovery having merged — F59/F61 note stands),
readiness 0.833, merged 25/26, features 5/6, `falsificationEligible false`, status `decomposing`.
Denial poll continues at 5 each per thread for `RECOVER_FAILED_FRONTIER`, `DISPATCH_REVIEW_TASKS`,
`DISPATCH_QUEUED_TASKS`. Zero design/Stitch lines since 16:00:06Z. Design shop (1.0) and
philosophical falsification (0.9) correctly silent at 0.833. All 6 epics still `kanoClass: null`.
No stalls, nudges, leaks or lock timeouts this window.

## Watch pass 2026-08-16 22:18Z — test-forty-ninth

### F62 — DEFINITIVE ANSWER: the recovery mechanism can only see failures shaped as orphans

`gatherAllEvidence` read in full. It has exactly two sources:

```java
public List<Evidence> gatherAllEvidence(ProjectEntity project) {
    List<Evidence> evidence = new ArrayList<>(gatherOrphanedWishlistEvidence(project));
    evidence.addAll(gatherOrphanedDependencyChainEvidence(project));
    return evidence;
}
```

and the orphan test inside the first one is:

```java
boolean allTerminalFailed = derivedTasks.stream().allMatch(t -> t.getStatus() == TaskStatus.failed);
```

**A failed task produces evidence only if EVERY task derived from its wishlist failed.** A failure
whose siblings succeeded orphans nothing, generates no evidence, and is therefore invisible to the
auditor — permanently, not temporarily.

This explains the whole observed pattern exactly:

```
b50a4511  API Slice 77380b22    -> sole derived task of wishlist 77380b22 -> allTerminalFailed
                                   -> orphan -> evidence -> auditor dismissed that very wishlist at
                                   19:30:35 and created the recovery task at 20:00:15  ✓
ab74be69  UI Slice 1559c9b0     -> siblings succeeded -> no orphan -> no evidence -> never recovered
36651896  Data Schema 7dd76d5f  -> siblings succeeded -> no orphan -> no evidence -> never recovered
```

The auditor dismissing "orphaned wishlist **77380b22**" is the same id as the recovered task's title.
That is the mechanism confirming itself in the log.

So the answer to the operator's question of 2026-08-16 is not a rate limit, not an age cutoff, and
not the circular readiness dependency I described at 19:18Z. It is a **coverage gap in the evidence
predicate**: `OpsAuditorService` audits for orphans, and a partially-failed wishlist is not an
orphan. Every earlier explanation I gave for this is superseded by this one.

The repair belongs here — a failed task with no replacement should generate evidence in its own
right, independently of what its siblings did. That is additive to `gatherAllEvidence` and does not
touch the readiness mathematics, the retirement path, or Flow Core policy. **Not implemented; no
intervention taken.**

### Board — one task in flight, unchanged 30 minutes

```
21:48Z  queued 0 · claimed 1 · done 36 · failed 3   merged 25/26  features 5/6 = 0.833
22:18Z  identical
```

`f42e448c Build Pipeline 115f4b3f` claimed for 30 minutes; no lease extension, no nudge, no stall
line — nothing yet indicates it is in trouble. Per the rule recorded last pass, a single unchanged
reading is not evidence of a stall at this cadence.

Wishlist counts identical to last pass (`gemini_observer` 13, `coverage_gap` 12, `client` 19,
`design_system_falsification` 3, `self_falsification` 2, `role` 1) — the two new observer findings
recorded at 21:48Z are the most recent movement.

### Unchanged

`failed` 3, readiness 0.833, `falsificationEligible false`, status `decomposing`. Denial poll at 7
each per thread. Zero design/Stitch lines since 16:00:06Z. Design shop (1.0) and philosophical
falsification (0.9) correctly silent. All 6 epics still `kanoClass: null`. No stalls, nudges, leaks
or lock timeouts this window.

## Watch pass 2026-08-16 22:48Z — test-forty-ninth

### The observer's evidence survives checking this time — a real change from 16:18Z

Her newest finding:

```
Gemini project observer finding (severity: medium): Code integrity violation: PR #19 uses a
superficial 'IF NOT EXISTS' fix for a database migration that masks an underlying root cause in
Flyway state synchronization. Evidence: CODE_INTEGRITY_FINDING (node b2852b4b)
```

Checked against the client repo directly (cloned `eneikdru/test-forty-ninth`, depth 60):

```
V20260816054204525__create_categories_and_tags_schema.sql:1
    CREATE ALIAS IF NOT EXISTS gen_random_uuid FOR "java.util.UUID.randomUUID";

git log for that file:
    f978233  Fix Flyway schemas and test assertions, untrack target artifacts   <- guard added here
    48a90f8  feat: add relational schema migration for categories and tags      <- original
```

The factual scaffolding is real: exactly one migration carries `IF NOT EXISTS`, and it was **not** in
the original migration — it was added by a later commit whose own message says it is fixing Flyway
schemas. Her description of the shape is accurate.

Her interpretation is arguable — the guarded statement is `CREATE ALIAS gen_random_uuid`, an H2 shim
for a Postgres function, where an idempotency guard is ordinary practice rather than evidence of
masked corruption. "Code integrity violation" likely overstates it. But this is a disagreement about
severity, not a fabrication.

That is a material improvement on the 16:18Z assessment, where all three of her specific claims
failed: "nearly all done tasks" against 1 of 33, and a nudge target `c034c2fb` that did not exist in
the project at all. Recorded here so the earlier F51 entry is not read as a standing verdict on her
reliability — the failure mode there was quantifier inflation and a phantom id, and neither recurs
here.

### Board — one merge-less completion, observer output accelerating

```
22:18Z  queued 0 · claimed 1 · done 36 · failed 3   merged 25/26  gemini_observer 13
22:48Z  queued 0 · claimed 1 · done 37 · failed 3   merged 25/26  gemini_observer 15
```

`done` rose by one with `mergedPlannedTasks` unchanged at 25 — the same signature as the recovery
task at 20:18Z: work completing outside the planned set, so it moves `done` without moving readiness.

The observer has produced findings at 11 -> 13 -> 15 across the last two passes, the only source
generating new material on this project. `f42e448c Build Pipeline 115f4b3f` has now been `claimed`
for 60 minutes with no lease extension, no nudge and no stall line — worth watching next pass, since
60 minutes is the threshold the system's own detector uses.

### Unchanged

`failed` 3 (F62's two invisible failures still uncovered), readiness 0.833, `falsificationEligible
false`, status `decomposing`. Denial poll at 7 each per thread. Zero design/Stitch lines since
16:00:06Z. Design shop (1.0) and philosophical falsification (0.9) correctly silent. All 6 epics
still `kanoClass: null`. No stalls, nudges, leaks or lock timeouts this window.

## Watch pass 2026-08-16 23:18Z — test-forty-ninth

### F43 RECURRED ON A SECOND TASK — and this time there was no backoff at all

```
39  Sent Forced stale-revising unblock message to Jules session sessions/8833681214974395634
```

All 39 to one session, inside 60 minutes. The cadence, from the very first message:

```
22:33:52   <- nudge 1
22:34:52   <- nudge 2      60 seconds
22:35:52   <- nudge 3      60 seconds
   …
23:18:53   <- still going, 60-second interval throughout
```

This differs materially from the 18:48Z occurrence and sharpens the diagnosis. There, nudges 1 and 2
were 30 minutes apart — the configured backoff visibly worked — and only collapsed to 60 seconds from
nudge 3, after the project entered `SYSTEM_STALLED`. **Here the interval is 60 seconds from message
one.** No backoff was ever applied to this session.

So the earlier hypothesis — that entering `SYSTEM_STALLED` routes through an unbudgeted path — does
not survive: this session was never in that state when the burst began, and the project is in
`DECOMPOSING` throughout this window (`policy denied … in state DECOMPOSING`, 5 per thread). Whatever
governs the backoff is not applying consistently between sessions. Cause still **not established**;
`jules.forced-unblock-max-attempts` is configured at 2 and 39 messages went out.

This is now twice in one evening, on two different tasks (`b50a4511`, then `f42e448c`), so it is the
default behaviour for a stale session on this project rather than an isolated incident. The first
occurrence ended with the circuit breaker killing the task at a cost of one permanent failure; there
is no reason yet to expect a different ending here.

I am not touching it. The one time I acted on a guess about this mechanism I added a duplicate
`forced_unblock_attempts` column and broke the migration and every integration test.

### Board — unchanged, with the nudged task still claimed

```
22:48Z  queued 0 · claimed 1 · done 37 · failed 3   merged 25/26  features 5/6 = 0.833
23:18Z  identical in every field
```

`f42e448c Build Pipeline 115f4b3f` has been `claimed` for 90 minutes and is the nudge target. No
`Extended lease` line this window — unlike `b50a4511`, which was getting lease extensions "because
Jules session is still active" while being nudged. Zero `SYSTEM STALLED` lines so far; the system's
own 60-minute no-forward-progress detector has not fired for this one yet.

All six wishlist source counts identical (`gemini_observer` 15, `coverage_gap` 12, `client` 19,
`design_system_falsification` 3, `self_falsification` 2, `role` 1) — the observer's run of new
findings stopped after 15.

### Unchanged

`failed` 3, F62's two invisible failures still uncovered. Readiness 0.833, `falsificationEligible
false`, status `decomposing`. Zero connection leaks, zero lock timeouts. Zero design/Stitch lines
since 16:00:06Z. Design shop (1.0) and philosophical falsification (0.9) correctly silent. All 6
epics still `kanoClass: null`.

## Watch pass 2026-08-16 23:48Z — test-forty-ninth

### The nudge-to-death sequence completed a second time, identically

```
22:33:52   nudge 1 to sessions/8833681214974395634
   …       one nudge per minute, ~60 minutes, no backoff at any point
23:32:52   last nudge
23:33:52   WARN  Poka-yoke: circuit breaker closed session 743c0842 for task f42e448c
23:33:52   WARN  Closed Jules session sessions/8833681214974395634 for task f42e448c
                 due to stuck_session_timeout
23:33:56   WARN  ProjectFlowService: retiring blocked task f42e448c …
```

Board: `failed 3 -> 4`. `f42e448c Build Pipeline 115f4b3f` is dead.

Two tasks, two identical endings within six hours — 60-odd nudges over an hour, then the circuit
breaker, then retirement. This is now demonstrably **the standard fate of a stale session on this
project**, not an incident. It also matches F43's original signature exactly ("60+ forced Jules
nudges in an hour"), so F43 is not fixed in any sense that survives contact with a real stale session.

### F59 does NOT apply to this retirement — and that is informative

```
23:18Z  totalPlanned 26  merged 25  ratio 0.9615  features 5/6 = 0.833
23:48Z  totalPlanned 26  merged 25  ratio 0.9615  features 5/6 = 0.833
```

Every readiness figure is unchanged despite a task being retired. When `b50a4511` was retired at
19:08 the denominator fell 27 -> 26 and a feature flipped complete. Here nothing moved.

The difference is that `Build Pipeline 115f4b3f` was never in the planned set — the same category as
the `Recovery API Slice` task, whose completion also moved `done` without moving `mergedPlannedTasks`.
So F59's "failure raises readiness" effect is specific to **planned** tasks being retired; retiring
non-planned work is metric-neutral. That narrows F59 usefully rather than contradicting it.

### The flow immediately picked up new work

`claimed` is 1 again: `1e169d70 Build Pipeline Ebfba197`, a fresh task, claimed within minutes of the
kill. No nudges against its session yet. `blockedItems` is back to the single long-standing
`Runtime Contract 8becdc01 | done_not_reached_main`.

So the project is not winding down — it replaced the killed work and continued. The observer also
produced two more findings (15 -> 17), continuing to be the most productive source here.

### Unchanged

`failed` now 4: `f42e448c`, `b50a4511`, `ab74be69`, `36651896`. Of these, only `b50a4511` ever
received a recovery task — F62's orphan-only evidence predicate still leaves the rest uncovered, and
`f42e448c` joins that set. Readiness 0.833, `falsificationEligible false`, status `decomposing`.
Zero connection leaks, zero lock timeouts, zero `SYSTEM STALLED` lines this window. Zero design/Stitch
lines since 16:00:06Z. Design shop (1.0) and philosophical falsification (0.9) correctly silent. All
6 epics still `kanoClass: null`.

## Watch pass 2026-08-17 00:18Z — test-forty-ninth

### F62 CONFIRMED PREDICTIVELY — the orphan predicate behaved exactly as derived from the code

At 22:18Z I read `gatherAllEvidence` and stated: a failed task is recovered **only** when every task
derived from its wishlist failed, making that wishlist an orphan. `f42e448c Build Pipeline 115f4b3f`
died at 23:33:56. The next auditor sweep:

```
00:00:17.638Z  OpsAuditorService: dismissed orphaned wishlist 115f4b3f
00:00:17.663Z  OpsAuditorService: created recovery task db0430a3-1f68-…
```

`115f4b3f` is precisely the wishlist id carried in the dead task's title. Orphan detected → evidence
produced → recovery created, 26 minutes after retirement, i.e. on the next 30-minute sweep. The
recovery task `Recovery Build Pipeline` is already `done` (`done 37 -> 38`).

This is the same shape as `b50a4511`/`77380b22` at 19:30–20:00, and it is now a prediction that held
rather than a pattern read backwards off two samples. F62 stands as the established mechanism.

The two failures that do **not** orphan their wishlists remain untouched for a second consecutive
cycle:

```
ab74be69  UI Slice 1559c9b0     — failed all day, siblings survived, no evidence, no recovery
36651896  Data Schema 7dd76d5f  — failed all day, siblings survived, no evidence, no recovery
```

Two auditor sweeps have now run while these sat there, and both spent their action on the
freshly-orphaned wishlist instead. The coverage gap is not a queueing delay — those two are not
candidates at all.

### The retire-recover cycle is metric-neutral and self-sustaining

```
23:48Z  done 37 · failed 4   totalPlanned 26  merged 25  ratio 0.9615  features 5/6 = 0.833
00:18Z  done 38 · failed 4   totalPlanned 26  merged 25  ratio 0.9615  features 5/6 = 0.833
```

A task died, a replacement was built and merged, `done` rose by one, and **not one readiness figure
moved**. The original `Build Pipeline 115f4b3f` is still counted among the four failures even though
its replacement shipped — the same non-reconciliation recorded at 21:18Z for `b50a4511`.

So the factory can run this loop indefinitely: task stales → ~60 nudges → circuit breaker → retire →
orphan → recovery task → merged → `done` +1 → readiness unchanged → `failed` +1 permanently. It is
productive in code terms and inert in metric terms.

### Board

`claimed` is 1 — `1e169d70 Build Pipeline Ebfba197`, claimed since ~23:45, **zero nudges against its
session so far**, so it is not yet on the stale path. `queued` 0, `review` 0. `failed` 4. All six
wishlist source counts identical (`gemini_observer` 17, `coverage_gap` 12, `client` 19,
`design_system_falsification` 3, `self_falsification` 2, `role` 1).

### Unchanged

Readiness 0.833, `falsificationEligible false`, status `decomposing`. Zero connection leaks, lock
timeouts or `SYSTEM STALLED` lines this window. Zero design/Stitch lines since 16:00:06Z. Design shop
(1.0) and philosophical falsification (0.9) correctly silent. All 6 epics still `kanoClass: null`.

## Watch pass 2026-08-17 00:48Z — test-forty-ninth — quiet, nothing new

```
00:18Z  queued 0 · claimed 1 · review 0 · done 38 · failed 4   totalPlanned 26  merged 25  features 5/6 = 0.833
00:48Z  identical in every field
```

All six wishlist source counts identical. No new tasks. Zero forced nudges, zero `SYSTEM STALLED`
lines, zero connection leaks, zero lock timeouts.

`1e169d70 Build Pipeline Ebfba197` has been `claimed` for about an hour and is alive:

```
00:35:43Z  ClaimService: Maintenance: Extended lease for task 1e169d70-b05f-45bb-a875-972810cd285d
           because Jules session is still active
```

This is the same signature `b50a4511` showed before it went stale, and I am recording it as a present
fact only. A live session is evidence of work now, not a forecast of how it ends — the lesson from
17:48Z.

### The auditor's silence is now explained rather than open

Zero `OpsAuditorService` lines this window, and under F62 that is the expected result, not an
anomaly: the only two outstanding failures (`ab74be69 UI Slice 1559c9b0`, `36651896 Data Schema
7dd76d5f`) have surviving siblings, so their wishlists are not orphans, so `gatherAllEvidence`
returns empty and `auditProject` exits at its silent branch. Nothing else on this project currently
produces evidence.

That closes the F61 line of enquiry entirely: the auditor is enabled (`ops_auditor_enabled`,
source `database`), it sweeps, and it correctly finds nothing — because its evidence predicate does
not cover the two failures that remain. The defect is F62's coverage gap, not the auditor's liveness.

### Unchanged

`failed` 4 (`Build Pipeline 115f4b3f` still listed despite `Recovery Build Pipeline` having merged —
the non-reconciliation from 21:18Z). Readiness 0.833, `falsificationEligible false`, status
`decomposing`. Denial poll at 6 each per thread, all citing `DECOMPOSING`. Zero design/Stitch lines
since 16:00:06Z. Design shop (1.0) and philosophical falsification (0.9) correctly silent. All 6
epics still `kanoClass: null`.

## Watch pass 2026-08-17 01:18Z — test-forty-ninth

### The nudge-to-death cycle has begun a THIRD time, on a third task

```
01:12:10Z  first Forced stale-revising unblock to sessions/17454916506787164653
01:15:52Z  01:16:52Z  01:17:51Z  01:18:52Z   — 60-second interval, no backoff
7 nudges in the 35-minute window so far
```

Target is `1e169d70 Build Pipeline Ebfba197`, `claimed` since ~23:45 and alive as recently as
00:35:43 ("Extended lease … because Jules session is still active"). Same 60-second cadence from the
first message as the second instance, i.e. no backoff applied here either.

Alongside it the system's own detector is escalating at ERROR:

```
01:18:51Z ERROR  SYSTEM STALLED: no forward progress (dispatch/merge) for 67 minutes with actionable
                 work present: queuedTasks=0, pendingOrCompilingWishlists=0,
                 activeNonTerminalTasks=1, reviewTasksWithPr=0.
```

23 of those in 35 minutes, plus 23 of the milder Branch-GC variant.

Three tasks in roughly nine hours — `b50a4511` (19:08), `f42e448c` (23:33), now `1e169d70` — have
entered the identical sequence. The first two both ended the same way: circuit breaker closes the
session on `stuck_session_timeout`, `ProjectFlowService` retires the task without recovery work, and
`failed` increments permanently. I am **not** asserting this one ends the same way; that is exactly
the forecast-from-snapshot error I made three times yesterday. What is established is that the
sequence has started for a third time and that the configured budget
(`jules.forced-unblock-max-attempts` = 2) is again not constraining it.

At three occurrences this is no longer an incident but the project's normal failure path, and it is
worth stating plainly: **on this project, a Jules session that goes stale is nudged roughly sixty
times and then killed.** F43 is not fixed.

### Board unchanged

```
00:48Z  queued 0 · claimed 1 · done 38 · failed 4   merged 25/26  features 5/6 = 0.833
01:18Z  identical in every field
```

All six wishlist source counts identical. Zero connection leaks, zero lock timeouts, zero auditor
lines (expected under F62 — the two outstanding failures have surviving siblings and so produce no
orphan evidence).

### Unchanged

`failed` 4, readiness 0.833, `falsificationEligible false`, status `decomposing`. Zero design/Stitch
lines since 16:00:06Z. Design shop (1.0) and philosophical falsification (0.9) correctly silent. All
6 epics still `kanoClass: null`.

## Watch pass 2026-08-17 ~07:10Z — test-forty-ninth — after a Docker outage and restart

### Infrastructure: the engine was down, and my first reading of it was false

At 07:03Z `docker logs --since 35m` returned `nudges: 0 | stalls: 0`. Those zeros were **not**
evidence of a quiet system — the Docker engine was stopped and the command had no output at all:

```
failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine;
check if the path is correct and if the daemon is running
```

This is exactly the trap already recorded for this environment: a failed container query produces
empty output that reads as "nothing is happening". Distinguishable this time from the WSL-interop
failure, because `whoami.exe` answered normally — interop was fine, the engine itself was stopped.
My own attempt to start Docker Desktop from WSL was refused by Windows ("Отказано в доступе"); the
operator restarted it. Stack brought back up with `docker compose up -d` — all four containers
healthy, backend answering.

**Consequence: the overnight logs are gone.** The containers were recreated, so `docker logs` starts
from the restart. The sequence between 01:18Z and the outage cannot be reconstructed; only its
outcome is visible on the board.

### The third nudge-to-death cycle completed — the answer is no, it did not fix itself

```
01:18Z  queued 0 · claimed 1 · done 38 · failed 4    (1e169d70 Build Pipeline Ebfba197 in flight,
                                                      7 nudges in, SYSTEM STALLED at 67 min)
07:10Z  queued 0 · claimed 0 · done 39 · failed 5
```

`Build Pipeline Ebfba197` is now `failed`. Three for three: every task that entered the stale-session
path yesterday ended retired. `done` rose by one, consistent with a recovery task completing as in
the two previous cycles.

### Readiness has not moved in over eleven hours

```
19:48Z (2026-08-16)  totalPlanned 26  merged 25  ratio 0.9615  features 5/6 = 0.833
07:10Z (2026-08-17)  totalPlanned 26  merged 25  ratio 0.9615  features 5/6 = 0.833
```

Identical. `falsificationEligible false`, `decompositionComplete false`, status `decomposing`
throughout. Across that span the factory retired two tasks, built and merged two recovery tasks, and
moved `done` from 37 to 39 — and not one readiness figure changed. The retire-recover loop recorded
at 00:18Z is confirmed over a long window: **productive in code, inert in metric.**

### Current state: nothing in flight

`queued 0 · claimed 0 · review 0`. Five failed tasks:

```
Build Pipeline Ebfba197 · Build Pipeline 115f4b3f · API Slice 77380b22
UI Slice 1559c9b0 · Data Schema 7dd76d5f
```

All six wishlist source counts identical to last night (`gemini_observer` 17, `coverage_gap` 12,
`client` 19, `design_system_falsification` 3, `self_falsification` 2, `role` 1) — no new material
from any source, including the observer, which had been the only productive one.

Under F62, `UI Slice 1559c9b0` and `Data Schema 7dd76d5f` remain permanently uncovered: their
siblings survived, so their wishlists are not orphans and the auditor's evidence predicate never
sees them.

## 2026-08-17 — problems found in the H2 trace log, a source I had never checked

The operator pointed out that the system keeps its own logs, not just Docker's. `data/eneik_db.trace.db`
lives on the host mount and therefore survives container restarts — the overnight window I reported
as unrecoverable was recoverable. Four problems, none of them previously reported.

### F63 — a job has failed once an hour for three days, silently, on a case-sensitivity bug

```
2026-08-17 01:40:07Z jdbc[209]: exception
org.h2.jdbc.JdbcSQLSyntaxErrorException: Table "FLYWAY_SCHEMA_HISTORY" not found
    (candidates are: "flyway_schema_history"); SQL statement:
CALL DISK_SPACE_USED(?) [42103-224]
```

**60 occurrences**, one per hour, unbroken: 1 on 2026-08-14, 24 on 08-15, 29 on 08-16, 6 so far on
08-17. Identical every time — an uppercase table name passed to `DISK_SPACE_USED` against a database
whose table is lowercase, and H2 says so in the error itself.

Whatever this job measures has never once succeeded. It appears in no Docker log, no dashboard, no
wishlist — three days of hourly failure with no reader, and it is still failing now. Same F5 shape as
the human-review flag (F60): a signal exists and nothing consumes it.

### F64 — I reported "zero lock timeouts" in every pass and was reading the wrong source

The watch brief asks for `Timeout trying to lock`. I grepped Docker logs, found none, and wrote "zero
lock timeouts" in pass after pass. They were in the trace log the whole time — **21 of them**:

```
17  Timeout trying to lock table "PROJECTS"
 2  Timeout trying to lock table "ACCOUNTS"
 1  Timeout trying to lock table "WISHLIST"
 1  Timeout trying to lock table "CLAIMS"
```

Spread across 08-15 (9) and 08-16 (12), the last at 2026-08-16 15:xx, none since. Every "zero lock
timeouts" line in the passes above is void — the measurement was taken from a source that does not
carry the signal. The contention is real and concentrated on `PROJECTS`.

### F65 — the database has regrown 6× since the compaction, in under a day

```
2026-08-16  compacted to        91 MB   (row counts verified identical)
2026-08-17  eneik_db.mv.db     553 MB
```

Six-fold growth in roughly 24 hours, back past the 549 MB that prompted the compaction in the first
place. The compaction treated the symptom; whatever produces the volume was never identified, so the
same H2 OOM risk that caused a real crash is rebuilding. The trace file is a further 11.2 MB.

### F66 — `/recent-activity` returns nothing

`GET /api/projects/{id}/recent-activity` returns HTTP 200 with **0 items** for a project that has 39
done tasks, 5 failures and 54 wishlists. An endpoint that answers successfully with an empty set is
worse than one that errors: it reads as "no activity" rather than "not wired up". It is also why I
could not reconstruct the overnight sequence from the API and wrongly concluded it was lost.

### Correction to the 07:10Z entry

I wrote that the overnight sequence "cannot be reconstructed". That was wrong — it was a conclusion
about the system drawn from the limits of the one source I happened to be using. The trace log covers
01:40 through 05:40 continuously.

## 2026-08-17 — why the observer could not have found F63–F66, and what to change

### The cause is structural, not a failure of her reasoning

Her entire input is an evidence snapshot plus `readRecentEvidenceNodes` — "the last 24h of evidence
nodes from all 5 independent signal sources". Everything she can reason about must first exist as an
`EvidenceNodeEntity`. The services that write those nodes are:

```
DefectJournalService · KaizenService · AutoMergeService · FalsificationCycleService
```

All four are **application-layer**. Nothing anywhere writes an evidence node for an infrastructure
fact. There is no producer for:

- an exception in the H2 trace log (F63 — 60 hourly failures over three days)
- a lock timeout (F64 — 21, seventeen of them on `PROJECTS`)
- database file growth (F65 — 91 MB → 553 MB in a day)
- an endpoint answering 200 with an empty body (F66)

So the observer is not failing to notice these. **They are not expressible in the only vocabulary she
is given.** Asking her to find them is like asking someone to describe a colour absent from their
palette. Her documented failure mode is the opposite one — over-claiming from the evidence she does
have (F51: "nearly all done tasks" against 1 of 33, a nudge target that did not exist). She
over-reads a narrow input rather than ignoring a wide one.

This also explains why her only verified-good finding (22:48Z, the Flyway `IF NOT EXISTS`) came from
`CODE_INTEGRITY_FINDING` — a source type that does exist.

### The improvement: make infrastructure a first-class signal source, not a patch

The system already has the machinery. `EvidenceNodeEntity.sourceType()` feeds a **distinct-sourceType
corroboration count**, and `EvidenceCoherenceService` (Thagard ECHO / Gärdenfors AGM) already scores
and reconciles nodes across sources. Adding a sixth source type therefore costs no new reasoning
layer — it makes infrastructure facts citizens of the graph that already exists, and every downstream
consumer (corroboration, coherence, her tools, the auditor) picks them up unchanged.

Concretely, one new producer that emits evidence nodes for infrastructure facts:

- scheduled-job outcomes, so a job that has never succeeded is a fact in the graph rather than a line
  in a file nobody opens (F63)
- database contention and growth as measured quantities (F64, F65)
- endpoint contract violations — 200 with an empty set where the data plainly exists (F66)

Two properties matter more than the list:

**It must record the negative.** F63 is invisible precisely because a job failing produces nothing
anywhere. A source that only emits on success cannot represent "never worked". This is the same F5
shape as F60's human-review flag and the step-6 valueless-flag reporter I built myself — detection
with no reader. The general rule the system keeps violating: *a signal with no consumer is not
monitoring.*

**It must not become another unread log.** The point is not to route the trace file into Gemini's
prompt — that is a patch and it would drown her. The point is that an infrastructure fact should
enter through the same predicate-and-corroboration path as every other fact, so it is subject to the
same three-valued discipline: a job that has never succeeded is not ABSTAIN, it is a decided negative
with a witness.

### Related: the same predicate-coverage defect, twice

F62 is the identical error one layer down — `OpsAuditorService.gatherAllEvidence` only builds
orphan-shaped evidence, so a failed task with surviving siblings is permanently invisible to it. Two
different components, same root cause: **the evidence predicate, not the reasoner, is the limit.**
Any fix aimed at the reasoner (better prompts, more frequent sweeps, a stronger model) cannot reach
either problem.

### Plan status

The plan is 2817 lines, findings F31–F66. Everything from F43 onward is **recorded and not
implemented** — this watch has produced diagnosis only, per the standing instruction not to intervene
in the flow. The substantive open items, in the order I would fix them:

1. **F62** — auditor evidence predicate misses non-orphan failures. Two tasks
   (`UI Slice 1559c9b0`, `Data Schema 7dd76d5f`) are permanently unrecoverable today because of it.
2. **F43** — nudge-to-death: three tasks killed in nine hours, ~60 nudges each, configured budget of
   2 not applying. Cause still not established; do not touch without measuring first.
3. **F65** — database back to 553 MB, past the level that caused a real OOM crash. Cause never
   identified; the compaction was symptomatic.
4. **F59** — retiring a planned task raises readiness by shrinking the denominator.
5. **F63** — hourly job that has never succeeded.
6. The evidence-source gap above, which is what would have surfaced 3 and 5 without me.

## F65 investigated 2026-08-17 22:15Z — the growth is not data, and compaction is not absent

### What is measured

Live data against file size, from the factory's own self-health measurements plus direct sampling:

```
09:40Z   573 MB file   59 MB live    9.6x
16:40Z   784 MB file   60 MB live   12.9x
19:00Z   892 MB file   (backup taken)
22:11Z  1278 MB file
```

`dL/dt ≈ 0.14 MB/h` against `dF/dt ≈ 46 MB/h`. **The file grows roughly 330x faster than the data it
holds.** Whatever is consuming disk, it is not rows.

### The file does not grow monotonically — it saws

Sampled every 25 s:

```
22:11:06  1277.8
22:11:31  1322.5    +44.7
22:11:56  1322.5
22:12:21  1322.5
22:12:46  1322.5    flat for ~2 min
22:13:12  1322.5
22:13:37  1307.4    -15.1
22:14:02  1000.5    -306.9   <- a single large reclaim
22:14:27  1121.0   +120.5
22:14:52  1121.0
```

**Background compaction is working.** It reclaimed 307 MB in one step. So the earlier framing —
"the store is not reclaiming freed space" — is incomplete: it reclaims, and then the file grows
straight back.

That shape matters. If application writes were the cause, the file would climb monotonically between
reclaims. Instead growth arrives in bursts immediately around the reclaims, which is the signature of
**compaction writing its own new chunks** — the reclaim process paying a write cost that produces
fresh garbage.

### The candidate that is ruled out

`ProjectAuditPipelineService` runs every ~55 s (22:14:34, 22:15:29 …), a full COVERAGE + STITCH_DESIGN
pipeline on a project with zero tasks in flight — a strong suspect for heavy writes. It is **not** the
writer: the class contains **zero** `save(` or `saveAll(` calls. Ruled out by inspection, not by
argument.

### The shape of the defect, stated mathematically

Let `L` be live bytes, `F` file bytes, `G = F − L` garbage bytes. Compaction is a process that
decreases `G` but is itself a writer, so each pass adds `w > 0` to `F`. The system is stable when the
reclaim per unit time exceeds `w`; here it settles into an equilibrium around 1.1–1.3 GB instead of
converging toward `L ≈ 60 MB`.

The fill rate is the reason: `L/F ≈ 60/1300 ≈ 4.6%`. MVStore compacts toward a target fill rate and
moves a bounded amount per pass, so from 4.6% the target is unreachable in bounded steps — the process
runs continuously, pays its write cost continuously, and never terminates. **A reclaim loop whose
measure decreases but whose own by-product replenishes it at a comparable rate has no termination
proof**, which is defect D2's shape applied to storage rather than to retries.

### What this predicts, and what has not been established

If the analysis holds, a single full compaction restores a high fill rate, after which background
compaction becomes cheap and terminating — which is exactly what the 2026-08-16 compaction did
(549 MB → 91 MB, row counts identical). That compaction was therefore **not** symptomatic treatment,
as recorded earlier in this log; it restored the precondition under which the reclaim process
terminates. That earlier characterisation is withdrawn.

**Not established:** why the file was 892 MB before the 21:55Z restart and 1121 MB after, when
`stop_grace_period: 90s` exists precisely so H2 can close and compact. A clean close should have
reduced it. Two candidates, neither measured: the close-time compaction is time-bounded and
accomplishes little on a 4.6%-full 1.3 GB file, or the close is not actually clean despite the grace
period. **Distinguishing these is the next measurement, and it must come before any change** — the
correct repair differs completely between them.

## 2026-08-18 — the state gate and its named resolver disagree about what a failed frontier is

After the stranded-claim release the project reached `BLOCKED_BY_FAILED_FRONTIER`. Measured why it
stays there.

The gate, `FlowSpineService:240`:

```java
if (input.failedTasks() > 0) {
    return "BLOCKED_BY_FAILED_FRONTIER";
}
```

**Any** failed task, regardless of kind. The same state machine names its resolver in the transition
row itself (`matrix(120, "ACTIVE", "failedTasks > 0 and no live work", "BLOCKED_BY_FAILED_FRONTIER",
"PlannedWorkRecoveryService", …)`), and that resolver's own predicate is far narrower —
`isEligibleRetiredPlanTask` requires `sourceWishlistId != null`, `featureId != null`, a source wishlist
whose `source` is in `PRODUCT_SOURCES`, and a `julesDispatchStatus` matching one of three literal
strings.

All five failed tasks, measured:

```
1e169d70 Build Pipeline Ebfba197   featureId null   sourceWishlistId null
f42e448c Build Pipeline 115f4b3f   featureId null   sourceWishlistId null
b50a4511 API Slice 77380b22        featureId null   sourceWishlistId null
ab74be69 UI Slice 1559c9b0         featureId null   sourceWishlistId null
36651896 Data Schema 7dd76d5f      featureId null   sourceWishlistId null

all five: julesDispatchStatus = "Blocked task retired by iteration-admission poka-yoke;
                                 no child work created"
```

They fail three of the five conditions independently, and the retirement path
(`ProjectFlowService:1521`) does not null those fields — they were never set. These are not planned
deliverables, which is consistent with `totalPlannedTasks 26 / mergedPlannedTasks 25`: the five sit
outside that set entirely.

So: the gate counts them, the resolver cannot touch them, and neither is wrong on its own terms.
**`failed task` is being substituted for `failed planned deliverable`** — the same limits-of-
substitutivity error Charter invariant 8 names for metric denominators, here in a state predicate.
The set the gate quantifies over is undeclared.

Consequence, measured: `ORCHESTRATE`, `DISPATCH_QUEUED_TASKS` and `DISPATCH_REVIEW_TASKS` are denied
in this state, `resumeNextFrontier` runs every tick and resumes 0, and the condition cannot clear by
itself.

The whitelist is also worth recording as a shape: condition five is three literal substrings, and the
2026-08-01 comment above them says it was widened "for the GENERAL case" — but what was added was a
third specific string. The dominant failure mode on this project, the iteration-admission poka-yoke
retirement, is a fourth phrasing and is not in the list. Widening it again would repeat the shape
rather than fix it.

**Not fixed.** The repair is a declared set, not another string, and which side should change - the
gate's predicate or the tasks' missing identity - is unmeasured.

## 2026-08-18 05:22Z — the flow moved, the deliverable count did not

Source for every number below: `GET /api/projects/41af381d.../dashboard`, fields `pipeline` and
`productReadiness`. Log counts from `docker logs eneikproductionsys-backend-1`.

After the retry-eligible set was declared (`3a8e5bb`), all five failed tasks resumed and dispatched
across two passes, and every one reached `done`:

```
04:06Z   queued 0  claimed 0  done 39  failed 5     (72 hours unchanged)
04:09Z   queued 0  claimed 3  done 39  failed 2
04:52Z   queued 0  claimed 1  done 43  failed 0
05:22Z   queued 0  claimed 0  done 44  failed 0
```

Five resumed, five dispatched, four PRs opened. The flow is healthy: nothing failed, nothing queued,
nothing stuck.

**And the deliverable count did not move.**

```
mergedPlannedTasks   25 / 26   before and after
completeFeatures      5 / 6    before and after
featureReadinessRatio 0.833    before and after
blockedItems          Runtime Contract 8becdc01 | done_not_reached_main   (unchanged, still the only one)
```

### The fact, separated from the hypothesis

**Fact:** five tasks went `failed → done` and `mergedPlannedTasks` stayed 25. None of the five appears
in `blockedItems`, so for each of them `reachedMain(task) || isAuxiliaryTask(task)` holds.

**Narrowed hypothesis:** the five are not members of the deliverable set. `totalPlannedTasks` stayed
26 throughout - before the resume, during, and after - so the denominator never contained them. Their
completion therefore cannot raise the numerator, whichever of `reachedMain` or `isAuxiliaryTask` is
what keeps them off the blocked list.

**Not established:** which of the two it is. Distinguishing them needs the task rows, and the dashboard
DTO does not expose role, cynefin reliably, `featureId` or `sourceWishlistId` - reading absence in that
projection as absence in the data is exactly the error made on 2026-08-18 with these same five tasks.
The store is not being read for this; it is not worth touching the database for a distinction that
changes nothing about what to do next.

### What this means for the goal, which is unchanged

The flow defect is fixed and demonstrated: work that could not move now moves, and the mechanism that
released it carries a well-founded measure (`resumeCount >= 1`, at most one resume per task).

The delivery gap is untouched and is exactly what it was three days ago: **one task, `8becdc01`,
asserting `done` with no merge evidence.** That is the substitutivity error the goal names - `task
done` standing in for `value delivered` - and it now stands alone, with nothing else in the system
obscuring it.

## 2026-08-18 05:5xZ — 8becdc01 is a real `task done != value delivered` gap

Established from non-invasive sources only: `GET /api/projects/{id}/dashboard` and the backend log.
The store was not read and nothing was built.

```
id                    f163e834-dbc7-46cf-8f1e-163f97bf17c6
title                 Runtime Contract 8becdc01
status                done
qualityGatePassed     true
cynefinDomain         clear
tag                   BARCAN-TAG-01  (Architecture)
julesSessionName      null
julesDispatchStatus   null
payload.source_wishlist_id  8becdc01-d4ef-4d8a-82b2-e9d9dbebc7c9
mentions in the entire backend log   0
```

### Why the nulls are a measurement and not a projection artefact

The dashboard task DTO carries exactly these keys: `cynefinDomain, dependsOn, description, id,
julesDispatchStatus, julesSessionName, payload, priority, qualityGatePassed, status, tag, title`.

`julesSessionName` and `julesDispatchStatus` **are in that set**, and on other `done` tasks from the
same payload they are populated:

```
392deb2d  julesSessionName 'sessions/4542055490084663732'   julesDispatchStatus 'Dispatched to Jules'
db0430a3  julesSessionName 'sessions/10690298081185582019'  julesDispatchStatus 'Dispatched to Jules'
```

This is the distinction that was missed on 2026-08-18 with the five failed tasks: there,
`featureId` and `sourceWishlistId` were **absent from the DTO's key set entirely**, and their apparent
nullity was an artefact. Here the keys are present and the values are null, which is a fact about the
data. The comparison against two sibling tasks is what establishes it.

### The finding

**No work was ever dispatched for this task.** No Jules session, no dispatch status, no PR, and the
backend log has never mentioned its id - not once, across the whole retained log. Yet it is `done`
and `qualityGatePassed` is `true`.

It is therefore a genuine instance of the substitutivity error the goal names: `task done` standing in
for `value delivered`, with nothing behind the claim. `done_not_reached_main` is the readiness
invariant correctly refusing to accept the claim, and it has been the single blocked item since
2026-08-16.

Its `cynefinDomain` is `clear`, so it is not excluded as auxiliary on that ground.

### Narrow question, not yet answered

How did a task reach `done` with `qualityGatePassed = true` having never been dispatched? That is one
question about one task, and answering it is the next step. It is deliberately not widened into a
review of the quality gate or of task-completion paths in general.

## 2026-08-18 — how 8becdc01 passed the quality gate: vacuous truth

Answered from source only. No store read, no build, no endpoint beyond the dashboard already used.

`GateOrchestrator:47-72` is the **only** place that sets the flag on a task:

```java
List<GateResult> results = gateChecks.stream()
        .filter(check -> stages.contains(check.stage()))
        .filter(check -> check.supports(task))
        .filter(check -> !(buildPhase && check.isBuildPhaseExempt()))
        .map(check -> check.check(task))
        .toList();

boolean allPassed = results.stream().allMatch(GateResult::passed);
…
task.setQualityGatePassed(allPassed);
```

`Stream.allMatch` returns `true` on an empty stream. So when every check is filtered out, the task is
recorded as having passed.

There are exactly three checks, and each `supports(task)` tests the task's role tag against a fixed
set:

```
BackendContractGate      BACKEND_TAGS = { BARCAN-TAG-02, BARCAN-TAG-07 }
DesignExcellenceGate     UI_TAGS      = { BARCAN-TAG-03, BARCAN-TAG-11 }
VerificationEvidenceGate QA_TAGS      = { BARCAN-TAG-06 }
```

`f163e834` carries `tag: BARCAN-TAG-01` (Architecture) — a member of none of them. Every check is
filtered out by `supports`, `results` is empty, `allMatch` is vacuously true, and
`qualityGatePassed` is written as `true`.

**That is the answer to the narrow question.** The task did not pass a gate; no gate applied to it,
and "no gate applied" was recorded in the same field, with the same value, as "every gate passed".

### The form

`∀x ∈ ∅ · P(x)` is true, and reporting it as a positive result is the error. This is the actualist
rule the corpus states for objects, applied to a quantifier: a claim about a domain that turns out to
be empty asserts nothing, and must not be stored in a field that also carries assertions about
non-empty domains. Two distinguishable states — *checked and passed* and *nothing to check* — are
being written into one boolean, which is the same limits-of-substitutivity error the goal names.

It is also why `done_not_reached_main` was right and the gate was not: the readiness invariant asked
for merge evidence and found none, while the gate asked nothing and reported success.

### Scope, held deliberately narrow

The union of the three tag sets is `{02, 03, 06, 07, 11}`. Any task whose role tag falls outside it
takes the same empty-stream path. **That observation is recorded, not acted on** - the beacon's
instruction is one task, not an audit of the quality gate, and this is one line of shared code whose
behaviour change would affect every task with an uncovered role.

### The fix, specified and NOT applied

The minimal correct change separates the two states rather than tightening the boolean: an empty
`results` is `not_applicable`, not `passed`. Flipping `allPassed` to `false` for the empty case would
be wrong in the opposite direction - it would fail tasks that were never in scope.

**Not implemented.** It changes a recorded outcome for every task with an uncovered role, and the
correct destination for the third state (a separate field, an enum, or a report-only distinction) is
an operator decision, not an inference from one task.

## 2026-08-18 — the invariant, the change point, and the test (specified, not implemented)

### Blast radius, measured before proposing anything

`ClaimService:206-214` is the reader that matters:

```java
gateOrchestrator.runQualityGate(task);
if (!task.isQualityGatePassed()) {
    task.setRetryCount(task.getRetryCount() + 1);
    if (task.getRetryCount() >= 3) task.setStatus(TaskStatus.blocked);
    else                           task.setStatus(TaskStatus.queued);
}
```

`false` means **failed**, not *not applicable*: three retries, then `blocked`. Flipping the empty case
to `false` would push every task whose role tag is outside `{02,03,06,07,11}` through three retries and
into `blocked`. The caution recorded earlier against that is now measured rather than argued.

Other readers: `EmsMetricsService` (quality multiplier 1.0 vs 0.7, and a `gated` count),
`ProjectOperationalContextService` (writes it as a fact for reasoning), `JulesDispatchService:3520`
(defence in depth — a reviewer verdict may not override a failed mechanical gate).

### Correction to the framing

**The vacuous success does not mask the deliverable gap.** `done_not_reached_main` is an independent
readiness invariant: it asked for merge evidence, found none, and has reported the gap since
2026-08-16. Readiness never consults `qualityGatePassed`. The gap was visible throughout.

The actual harm is different and narrower: `qualityGatePassed = true` is a **false positive claim about
verification**. It tells every reader above that a task was mechanically verified when no check ran.
That inflates a metric, feeds a reasoning fact, and weakens a defence-in-depth check.

### The invariant

**Positive verification requires a non-empty evidence set:**

```
qualityGatePassed = true   ⟹   |results| > 0
```

### The smallest form that records the distinction

The distinction is *already persisted* — the report carries `checks: []` for the vacuous case. What is
missing is that it is not stated, so no reader can ask. One line in `GateOrchestrator`, beside the
existing `report.put("passed", allPassed)`:

```java
report.put("applicable", !results.isEmpty());
```

**No reader changes behaviour**, because the boolean is untouched. What changes is that the gate stops
being silent about its own scope. It is the honest minimum: do not alter what the gate decides, stop
omitting that there was nothing to decide.

Flipping `allPassed`, adding an enum, or adding a column are all larger and none is needed to make the
vacuous case machine-checkable for the first time.

### The test that proves it

1. A task whose role tag is in none of `BACKEND_TAGS`, `UI_TAGS`, `QA_TAGS` (e.g. `BARCAN-TAG-01`, the
   tag `f163e834` carries): `runQualityGate` produces a report with `applicable = false` and an empty
   `checks` array, **and** `qualityGatePassed` and the task's status are unchanged from before the
   call. This proves the vacuous case is now distinguishable and that nothing was broken to achieve it.
2. A task tagged `BARCAN-TAG-06`: `applicable = true`. This proves the ordinary path is untouched.

### Status

Specified. Not implemented — the beacon's instruction is to fix the change point and the test first.

## 2026-08-18 — RETRACTION and the real diagnosis of 8becdc01

### Retracted: the vacuous-truth explanation

I recorded that `f163e834` passed the quality gate because no check applies to `BARCAN-TAG-01`, so
`results` was empty and `allMatch` was vacuously true. **That is wrong.**

`GateCheck.supports(TaskEntity)` has a default body of `return true`, and `BaseQualityGate` does not
override it. It therefore applies to **every** task. `results` is never empty.

The error: I grepped for `public boolean supports` implementations, found three, and treated them as
the full set. A class that does not override an interface default is not a class without the
behaviour. **I read the absence of a method as the absence of a behaviour** - the fourth time in one
day of reading silence as a fact.

`GateOrchestratorIntegrationTest` names five `BASE_CHECKS` and was in the repository the whole time.
I did not open it because I was searching only for `supports`. The change was reverted before
deployment; nothing shipped on the false premise.

### What the gate actually verifies

`BaseQualityGate` contributes five checks to every task:

```
Business Value Check       payload has lean_value, and it is not "waste"
DoD Check                  a definition-of-done text is present
Acceptance Criteria Check  the text contains a Given/When/Then pattern
Repo URL Check             a repository URL is present
Active Role Check          the task has a role and the role is active
```

Every one is a property of the task's **description**. Not one touches a Jules session, a PR, a diff,
or main. `f163e834` passes all five because it is **exceptionally well written** - JTBD, Kano
classification, Cynefin domain, TOC constraint ref, DoD, acceptance criteria, explicit boundaries.

### The real defect, and it is worse than vacuity

`qualityGatePassed` means **"this task is properly specified"**. Six readers treat it as **"this task's
work was mechanically verified"**:

```
ClaimService              false -> three retries, then blocked
EmsMetricsService         quality multiplier 1.0 vs 0.7, and a `gated` count
ProjectOperationalContext written as a fact for reasoning
JulesDispatchService      defence in depth: a reviewer verdict may not override a failed gate
```

A vacuous claim asserts nothing. This asserts something **true about the wrong subject**:
`well-specified` substituted for `delivered`. That is the limits-of-substitutivity error the goal
names, and it is harder to see precisely because the verification is real - it simply verifies the
description.

`done_not_reached_main` is the only mechanism in the system that asks about delivery. That is why it
alone caught this, and why it has been right since 2026-08-16 while the gate said the task was fine.

### What is established about the task itself

```
BARCAN-TAG-01 (Architecture)   appears on exactly ONE task in the project - this one
                               all 43 others use 02, 05, 06, 08, 11, 12
toc_constraint_ref             BOOTSTRAP-ENVIRONMENT-BOUNDARY
kano                           Must-Be          lean_value: essential
DoD                            "One branch and one PR are opened for this role only"
actual                         no session, no PR, zero mentions in the backend log
```

`runQualityGate`'s only caller is `ClaimService.complete`, which requires an active claim, and reaching
`done` needs that call **twice**. So the task was claimed and completed twice with no Jules session
ever created. **How that happened is not established** and is the next thing to measure - not guessed
at, and not patched around.

Note also: comma-joined `source_role_tag` values (`"BARCAN-TAG-01, BARCAN-TAG-09"`) are normal - 43 of
44 tasks carry them. A hypothesis that this was the anomaly was refuted by measurement before any
action was taken.
