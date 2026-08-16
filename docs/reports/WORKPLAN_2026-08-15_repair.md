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
