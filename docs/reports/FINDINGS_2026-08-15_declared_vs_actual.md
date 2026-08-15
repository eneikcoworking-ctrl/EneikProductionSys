# Findings, 2026-08-15: declared state versus actual state

Everything below was found in one night — partly while building the market corpus, partly by watching
`test-forty-sixth` run live. It is recorded for deliberate repair, not for immediate patching.

## The single diagnosis

Nearly every defect here is one mistake wearing different clothes: **the system trusts a declaration
instead of checking what the declaration refers to.** A task says `done`; a setting says `registered`; a
flag says `enabled`; a backup says `configured`; a comment says `runs daily`. In each case something in the
system reads the sign and never looks at the thing signified.

That is why they cluster: they are not eighteen unrelated bugs but one epistemic habit expressed eighteen
times. Fixing them one at a time will produce eighteen patches and the nineteenth instance next week.
Fixing the habit means: **wherever a state is declared, the declaration must carry the evidence that
establishes it, and a declaration with no evidence must be treated as unknown rather than as true.**

The Kaizen engine already encodes the right instinct - `SYSTEMIC_DEFECT` and its three siblings carry
`expectedGainPercent=0` and are never auto-applied, because a claim about the factory's own code is not
self-certifying. The same suspicion is missing everywhere else.

## THE CONSTRAINT (fix first - everything else is downstream)

**F1. A task can report `done` without its work reaching `main`, and the flow continues.**
Evidence: `test-forty-sixth`, blocked item `Runtime Contract 5d534314` (BARCAN-TAG-01), status `done`,
reason `done_not_reached_main`, 2.7h. Gemini independently raised the same thing as a `SYSTEMIC_DEFECT`
proposal (findings `d4239fa3`, `f47c7fd1`), which sits in the review-only queue, correctly untouched.

This is the constraint in the TOC sense: it is upstream of every conflict recorded below. The task that was
supposed to establish the shared execution boundary declared success without delivering, and 21 dependent
tasks were dispatched against a contract that did not exist.

The repair is not "detect the discrepancy". It is that `done` must **mean** "the change is on main", so the
status cannot be set from a session's self-report alone.

**F20. A conflict makes itself unresolvable: the system demands green CI before it will look at a conflict,
and the conflict is what makes green CI impossible.** (Third recurrence of one deadlock shape. **FIXED
2026-08-15**, see below.)

Verified chain, every step measured on `test-forty-sixth` PR#12:

1. PR#12 is conflicted - `mergeable: false`, `mergeable_state: dirty`.
2. GitHub runs `pull_request` workflows against `refs/pull/N/merge`. A conflicted PR has no such ref, so no
   workflow can start. **Measured: 0 workflow runs for that branch**, while the same repository's CI ran
   fine on every other PR (44 runs, event `pull_request`).
3. With no check-runs, `pullRequestChecks` returns `(available=true, successful=false, status="pending")`,
   detail *"No GitHub check-runs exist for the PR head"*.
4. The gate `if (!checks.available() || !checks.successful()) return;` therefore refuses the merge.
5. `handleMergeConflict` is reachable ONLY from GitHub answering 405/409 to an actual merge attempt further
   down. **Measured: 0 calls in 3h**, while the PR sat reviewed and approved since 02:17.

**A wrong diagnosis was recorded here first and is retracted:** this was initially attributed to Flow Core
denying `MERGE_PR` in `BLOCKED_BY_REVIEW`. That was inference, not evidence. `MERGE_PR` was never denied -
**0 "Refusing to merge" lines in 3h** - and `BLOCKED_BY_REVIEW` is deliberately absent from `MERGE_PR`'s
exclusion set, narrowed on 2026-07-31 for this very class of incident. The gate at fault is CI, not policy.
Recording the retraction rather than silently editing it: acting on the first version would have changed
the authorization model to fix a problem it does not cause.

The two earlier fixes of this shape (2026-07-23 "conflict", 2026-07-31 "policy_denied") both widened
`pollCandidateStatuses`. That keeps the review alive but routes it back into the same closed loop. The real
error is **ordering**: waiting for green CI before looking at a conflict presumes CI can go green, which a
conflict forbids.

**Fix applied:** the conflict check now runs *before* the CI gate. When GitHub reports `mergeable=false`,
the review is marked `conflict` and `handleMergeConflict` is called directly, without waiting for checks
that cannot appear.

**Still open here:** `executeMerge` appears not to have been called for this review at all (no "Refusing to
merge" line was produced even though the CI gate should have logged one). `AUTOMERGE_PROCESSING` runs every
60s and completes, so the service is alive; the review is evidently not entering the candidate set. Ten
distinct `ciStatus` values are written across the codebase - `superseded`, `invalid_pr`, `closed_unmerged`,
`unowned`, `owner_mismatch`, `escalated`, and whatever `checks.status()` yields - while only five are
polled (`success`, `pending`, `unavailable`, `conflict`, `policy_denied`). Any review that lands on one of
the other five silently leaves the pipeline forever. That set difference is the general form of the two
historical fixes and should be closed once, not one status at a time.

## Factory level (Layer 1)

**F2. No audit trail for settings changes.** `falsification_cycle_enabled=false` (source: `database`) - who
set it, when, and from what previous value is unrecoverable. A switch that changes what the whole factory
does is flipped without a record.

**F3. Settings can exist with source `none`.** `design_system_falsification_enabled` is registered and has
no value anywhere. The key resolves, the feature looks configured, and it never runs. This exact class
already caused one silent outage (`system_orchestrator_repository_name`, fixed 2026-08-14 with a
source-scanning test). **Action: audit all 38 settings for source `none`.**

**F4. Eleven of 37 `@Scheduled` methods are state polls wearing a cron's clothes.** They fire blind and
their first act is to check a state and return. The clearest case:

```java
@Scheduled(cron = "0 0 3 */2 * ?")            // every 2 days at 03:00
if (!readiness.decompositionComplete()
        || readiness.selfFalsificationReadyRatio() < threshold) return;   // ← first statement
```

The clock knows nothing about readiness. Operator's stated intent: falsification should start **as soon as
the project is ready for initial deployment, after coverage and design analysis** - an event, not an hour.
Cost: readiness can be reached at any moment and is sampled every 48h, so mean waiting waste is ~24h and
worst case 48h, during which the project keeps producing work the falsification would have redirected.

Repair shape: the transition that establishes readiness publishes an event; falsification subscribes. Cron
survives only as a low-frequency safety net for a lost event, never as the primary trigger.

Affected (the eleven): `OpsAuditorService.runAuditCycle`, `DesignShopOrchestrationService.tick`,
`AutoMergeService.processAutoMerge`, `FalsificationCycleService.runDailyFalsificationCycle` and
`.advanceInProgressPhilosophicalDiscussions`, `GeminiContextService.reindexStandingKnowledge`,
`GeminiProjectObserverService.runObserverCycle`, `ProjectEventLogService.flush`,
`DesignSystemFalsificationService.applyDesignSystemsToShippedEpics`,
`JulesDispatchService.processPendingReviewBatch` and `.reconcileTaskStatusAgainstGitHubTruth`.
**One of these - `FactorySelfHealthService` (hourly) - I added on 2026-08-14; it repeats the pattern and
should be converted with the rest.**

**F5. `FactorySelfHealthService` reports to a log nobody reads autonomously.** It detects the factory's own
ill health and writes `log.warn`. That is the shape of a closed loop without the closure. Its findings
belong in the same stream as product findings - a `SYSTEMIC_DEFECT` proposal, visible on the dashboard -
while keeping the rule that factory code is never auto-modified.

**F6. `runtime-launcher` was not running and nothing said so.** Without it a product cannot be launched or
verified, and the TOC subordination in `FalsificationCycleService` gates on launchability - so philosophy
would have been silently skipped or run blind. The operator noticed, not the factory.

**F7. `JpaSystemException: Unable to rollback against JDBC Connection`**, repeating every ~15 min on
`Failed to check coverage-audit eligibility for project 686015fd`. Coverage-audit eligibility is precisely
the gate the operator named as preceding falsification, so this failure blocks the intended chain.

**F8. Linear provisioning fails** with `Argument Validation Error` on `projectCreate` - reproduced on both
`test-forty-fifth` and `test-forty-sixth`, so it is independent of the transient GitHub outage.

**F9. A literal `%` in a `.formatted()` template silently breaks a prompt.** `"80% done"` parses as the
format specifier `% d` (space flag, `d` conversion). It consumed an argument and threw
`IllegalFormatConversion` on every call to `wishlistCompilerPromptBatch`, which would have disabled all four
decomposition floors on first deploy. Fixed as `%%`; the class needs a guard, because the failure is
invisible until the method is actually called.

**F10. One keyword list serving two roles - found three times in one file.** Condition-of-applicability
confused with product kind; condition words placed among coverage words (a plan containing "in-app
purchase" scored as having disclosed loot-box odds); one capability's coverage words marking a sibling
duty covered. Each time the check looked healthy and stayed silent exactly where it should have spoken.
Separation now exists (`appliesWhenKeywords` vs `detectionKeywords`, per-expectation override) but the
naming does not make the roles obvious, and a fourth instance is likely.

## Delivery level (Layer 2)

**F11. `.gitignore` excludes only `.eneik/`.** Neither `target/` nor `data/`. PR#6 therefore committed 13
build artifacts (`target/classes/*.class`, surefire reports, maven-status) plus a binary H2 file
`data/appdb.mv.db`. Two tasks that both compile produce different bytes at identical paths, so **conflict
is guaranteed by construction**, independent of what the tasks were asked to do. PR#12 (`DIRTY`) is the
first casualty: its opening act is adding `data/.gitignore`.

**F12. No shared skeleton, so each task invents one.** Consequences already merged into `main`:
two Spring Boot entrypoints (`generated/Application.java`, `generated/TelemetryApplication.java`); two
models of the same entity (`entity/EpidemiologicalMaterial.java` and `material/Material.java`, with
separate repositories); two migrations creating overlapping tables (`create_materials_table`,
`search_index_schema`). All downstream of F1 - the runtime contract never landed.

**F13. 21 generated work items are stored with `source=client`.** The client wrote one brief; the compiler
produced the rest. Any later analysis of "what the client asked for" is wrong on 21 of 22 rows - and that
analysis is exactly what the market corpus exists to reason about.

**F14. Eleven items are titled `Internal UI work item`** while being a data schema, an API contract or a
backend service. The `BARCAN-TAG` is right; the noun is wrong.

**F15. `GET /api/projects/{id}/epics` returns `tasks: 0`** while `productReadiness.totalPlannedTasks=21`.
A reader of that endpoint would conclude no work exists.

## Product level (Layer 3)

**F16. The content-management and document-flow chains have no "it went stale" and no "state of the
collection" link.** Found by the operator's first question - *why are there no reports?* Both chains end at
an action on a single document and never ask how anyone learns that a document needs revising, or what the
collection contains and what is actually used. For a knowledge base of normative documents this is the
governing need, and its absence is mine, not the compiler's: it was told to compare against known chains
and did so faithfully.

Proposed chain: `draft -> edit -> approve -> publish -> **learn it has gone stale** -> update or unpublish
-> **see what exists and what is used**`. Note GQM telemetry does not substitute: telemetry measures whether
the system works, a report answers what the collection holds.

**F17. All five epics were classed `Must-Be`.** No gradation. The Kano vocabulary is present and not
discriminating; worth checking whether the class is being chosen or defaulted.

**F18. GDPR data-subject rights did not appear**, though the corpus scopes that duty to every product and
the system stores staff data. Possibly a correct judgement for a Russian institute; unexplained either way,
and an unexplained omission is indistinguishable from a miss.

**F19. The corpus covers DE and US; the test brief is a Russian institution.** Value chains are
market-independent so the experiment stands, but the regulatory floor renders duties that cannot apply.

**F21. Nothing creates or seeds the product's primary content.** Operator's own observation, 2026-08-15:
there should be a tool for this. A knowledge base with zero materials is formally complete and practically
useless - as is a shop with no products, a course catalogue with no courses, a CRM with no pipeline stages.
The factory builds the vessel and never fills it, and every readiness measure it has will report success.

Same class as F16: a link nobody looked at, at the end of the chain. It is arguably the last link of every
`content-management`, `learning` and `shop` chain in the corpus - *the thing exists and there is something
in it* - and no chain currently names it. Marked for future work, not for this repair pass.

---

# APPROVED REPAIR: conflict resolution (operator directive 2026-08-15)

To be carried out **methodically, as one continuous piece of work** - not split into separately-approved
fragments. Recorded here so the reasoning survives the session.

## F22. The conflict mechanism is not mathematically sound, in three independent senses

**(a) The number does not participate in any decision.** At its only call site the entropy result is
computed, logged, and discarded; the actual gate is a hardcoded path filter. The documented threshold
`H(C) < 0.2` exists nowhere in code.

**(b) Shannon entropy cannot express the intended distinction.** `H(p, 1−p) = H(1−p, p)` - it measures
mixedness, not which side dominates. Computed from the shipped code:

| orchestrator | product | H(C) | documented rule | code's `isTrivial` |
|---|---|---|---|---|
| 3 | 0 | 0.0 | trivial | trivial |
| **0** | **10** | **0.0** | **trivial** | not trivial |
| **0** | **1** | **0.0** | **trivial** | not trivial |
| 4 | 1 | 0.72 | not trivial | **trivial** |

A **pure product-code conflict scores H = 0** and would be auto-resolved under the documented rule. The
doc and the code disagree in both directions. Only the fact that the rule is unwired prevents harm - which
makes it an invitation to "finish the job" and enable exactly that.

There is a second, deeper defect: **entropy averages, safety requires conjunction.** A mean hides one
dangerous file among nine harmless ones. "All of them must hold" is not expressible as a mean at any
threshold.

**(c) Three inconsistent definitions of "orchestrator-owned" in one mechanism:**
calculator classification (`.eneik/` ∨ `.gitignore` ∨ `*.md`); calculator's `isTrivial` (count rule
`prod == 0 ∨ (p_orch ≥ 0.8 ∧ prod ≤ 1)`); AutoMergeService's filter (`.eneik/` ∨ `.gitignore`, no `.md`).

**Most dangerous consequence:** root `.gitignore` is classified as disposable orchestrator noise, and the
fast path syncs such files to main's content - discarding the branch's edit. Per F11, `.gitignore` is the
file whose content decides whether `target/` gets committed, i.e. whether every later task conflicts. The
mechanism built to resolve conflicts systematically discards the one edit that ends them. PR#12's first
act was adding `data/.gitignore`.

The comment justifying this ("branches only ever add redundant root .gitignore edits") was true for
test-thirty-seventh and became false once bootstrap stopped writing a correct `.gitignore`. **The path
pattern outlived the condition that made it true.**

**Mechanical defects found alongside:**
- comment says the fast path is bounded to `resolutionAttempts == 0`; the code reads `< 10`
- `conflict.setResolutionAttempts(1)` **assigns** rather than increments, while escalation tests
  `get() + 1 >= 3` - a path that keeps "succeeding" pins the counter at 1 and can never escalate
- Tier-1 sync always merges `"main"` regardless of the PR's actual `baseRef`; `UP_TO_DATE` then prints
  "Branch is now clean!" having verified nothing
- the escalation comment asserts the branch is "unrecoverable" - an unfounded universal claim; three
  failures exhaust a budget, they do not prove impossibility

## The principle: substitutivity salva veritate

The mechanism asks *"how complex is this conflict?"* - hence a scalar and a threshold. The right question
is *"does discarding one side change any truth the system has asserted?"* That is Leibniz's law, already in
this system's vocabulary as BARCAN-TAG-08.

The system **already declares ownership** and the mechanism ignores it: `TaskEntity.fileScope` is populated
by `TechnicalLeadCompiler.determineFileScope` for every task at compile time (it even emits
`collisionNotes`), and `ProjectFileClaimEntity` records `filePath → taskId/featureId`.

```
substitutable(f, t)  ⟺  f ∉ fileScope(t)  ∧  ¬∃ live claim on f
n = |{ f ∈ conflictingFiles : ¬substitutable(f, t) }|
auto-resolve         ⟺  n = 0
```

**There is no threshold, and that is the point.** A threshold is the signature of a proxy measure; when the
measure captures the property itself, the gate is `n = 0`.

Why declarations rather than path patterns: the referent "what this task owns" is fixed **at dispatch
time** and cannot decay. A path pattern is a guess about authorship that outlives its own truth conditions
- demonstrated above.

Popperian layer: one cannot prove a conflict trivial, only fail to find evidence that it is not. So the
default is escalation, auto-resolution requires positive evidence of substitutability for **every**
conflicting file, and the loss asymmetry demands it - wrongly auto-resolving is silent unrecoverable loss
of work, wrongly escalating costs one visible bounded session.

## Order of work (dependency-driven, not preference-driven)

| Stage | Work | Why here |
|---|---|---|
| **0** | One ownership definition replacing three. Implement `substitutable` on `fileScope` + claims. Delete path-pattern classification | Any formula over three diverging definitions computes different things. The only stage that **removes a false premise** instead of adding logic |
| **1** | Remove the decorative math: delete `ConflictEntropyCalculator` or reduce it to an honest observation with no documented threshold | While `H(C)` is computed beside a non-existent threshold it invites being wired in - which would enable auto-resolution of pure product conflicts |
| **2** | The gate becomes `n = 0`; everything else escalates to a session | Requires stage 0 |
| **3** | Mechanical defects: increment instead of assign; PR's real `baseRef` instead of hardcoded `"main"`; reconcile comment and code on the bound; correct the "unrecoverable" claim | Independent bugs, fixed after the semantics are right so the behaviour being fixed is the final one |
| **4** | Irreversibility: escalation triggers Branch GC which retires the branch and destroys work. **An automated path must not perform an irreversible action to resolve uncertainty.** Preserve and mark instead of deleting | This is the stage that stops loss; the others improve decisions about what is already gone. PR#12 consumed here: plan 21 → 19 |
| **5** | Make the constants measurable: record per conflict the files, `n`, attempts, outcome, so `P(resolve \| attempt k)` becomes measurable and the cap of 3 stops being magic | Measuring is only worth doing on a process whose semantics are correct |

Until stage 5 produces data, the 3-attempt cap must be **labelled an arbitrary budget**, not presented as a
judgement about branch recoverability - the same rule the market corpus applies to `derived`: reasoning may
be asserted, a number requires measurement.

**TOC note:** the constraint here is not conflicts - the system survives those. It is that **work can
vanish silently**: a PR is closed, a branch retired, the plan shrinks, and no counter distinguishes
"conflict resolved" from "conflict removed along with the work".

---

# FLOW STUDY, 2026-08-15 (beyond the task pipeline)

Measured live on `test-forty-sixth`. The flow has several layers and **they return three different
verdicts about the same project, with nowhere that reconciles them.**

| Layer | Verdict |
|---|---|
| Task pipeline | 82% complete, 19/19 planned merged, 4 failed - healthy |
| Doctrine (13 BARCAN roles) | **`blocked`** - 0 satisfied, 7 object, 2 refuse, 4 unknown |
| Six Sigma | **DPMO 954,545** - 63 of 66 tasks classified as defect work |
| Dependency graph | fragmented - 14 disconnected graphs over 42 tasks, 7 duplicate semantic keys |
| Runtime | one observation, 6h stale, taken before two repairs |

**F23. The Six Sigma layer measures keyword matches, not defects - and Kaizen acts on the result.**

`isDefectWork` classifies a task as a defect by substring-matching free text:

```java
// EmsMetricsService:558           // TechnicalLeadCompiler:486
"defect" "bug" "blocker"           "defect" "bug" "blocker"
"recovery" "circuit breaker"       "failure" "failed" "regression"
"generated artifact"               "circuit breaker" "generated artifact"
```

Two different word lists in two places for one concept - the same three-definitions defect as F22(c).
`recovery` appears only in the metrics list, `failure`/`failed`/`regression` only in the compiler's.

Consequence measured: 63 of 66 tasks are "defect work", DPMO 954,545, i.e. a 95% defect rate on a project
that merged 19 of 19 planned tasks with 4 failures. The number is not describing the process.

Two ways the classifier misfires by construction: a **feature** named "Self-Service Account **Recovery**"
is defect work by name, and any acceptance criterion that specifies error handling contains "failure".
(Checked whether this session's own QUALITY FLOOR caused it - it did not: only 3 of 21 original slices
match. The bulk comes from elsewhere, most plausibly `task.getRetryCount() > 0`, which would mean nearly
every task is retried at least once - itself worth measuring.)

Why this is worse than the entropy defect: entropy was computed and discarded. **DPMO is consumed** -
`KaizenService:585` reads `sixSigmaAuditService.calculateFullSixSigmaAudit().dpmo()` as `postMetric`, the
number that decides whether an applied micro-improvement worked. So the improvement engine judges its own
effect by a quantity derived from substring matching.

**F24. The doctrine layer refuses and nothing acts on it.** `statusLabel: blocked`, interpretation *"One or
more BARCAN doctrines refuse the current project state; resolve Must-Be objections before acceptance"*.
BARCAN-TAG-11 and TAG-12 refuse outright; seven more object; not one role is satisfied. Meanwhile the task
pipeline dispatches normally and reports 82% progress. A layer designed to withhold acceptance is
withholding it, and no other layer reads that.

**F25. UX/UI is the real bottleneck, and the task queue does not show it.** Per-stage:

| stage | total | done | blocked |
|---|---|---|---|
| decision | 21 | 21 | 0 |
| architecture | 1 | 1 | 0 |
| data-model | 3 | 3 | 0 |
| api-contract | 5 | 4 | 1 |
| implementation | 9 | 7 | 0 |
| **UX/UI** | **13** | 9 | **3** |
| build/deploy | 7 | 4 | 0 |
| verification | 7 | 5 | 0 |

66 tasks across the flow versus the 22 "planned" the readiness view reports - and a third of everything
blocked sits in UX/UI. By TOC that is where subordination should point; nothing points there.

**F26. The dependency graph is mostly not a graph.** `graphTasks: 42`, `uniqueGraphs: 14`,
`dependencyCoverage: 0.57` - 43% of tasks carry no dependency edge, and the work splits into 14
disconnected fragments with a critical path of 4. `duplicateSemanticKeys: 7`, whose own interpretation
says *"orchestration should collapse or skip repeated work"* - and nothing collapses it.

**F27. Philosophy is blocked twice over.** `falsificationEligible: false` because `mergedRatio 0.86 <
threshold 0.9`, **and** separately by TOC subordination on a launch observation from before two repairs.
Either gate alone would hold it. Neither re-evaluates on an event.

---

# PROPOSED ARCHITECTURE: the verdict lattice

Answers the structural problem behind F22-F27, not any one of them. Recorded for deliberation.

## The problem, stated exactly

Five layers report on one project and return `82%`, `blocked`, `954545`, `0.57`, `launchSuccess=false`.
These cannot be combined, because **they are not measurements of one quantity.** They are different
*modalities* applied to the same proposition:

| Layer | Modality | Asks |
|---|---|---|
| Task pipeline | actuality | how much declared work is actual |
| Doctrine | **deontic** | is this state permitted |
| Six Sigma | frequency | how dense are defects |
| Graph | structure | is the dependency relation coherent |
| Runtime | actuality | does it run |

Averaging a deontic claim with a frequency is a category error. It is the same category error, one level
up, that produced both defects found today: entropy **averaged** where safety required conjunction, and
DPMO **counted** where the question was classification. The architecture reproduces at the system level the
mistake its components make individually.

## The move: one type, not one number

Do not ask "how good is this project" - that question has no single answer and its pursuit is what
manufactures incommensurable scalars. Ask the question every layer can answer in its own terms:

> **May this project advance to its next state?**

Three admissible answers - `permit`, `withhold`, `abstain` - and every layer maps its native measure to one
of them by **its own declared rule**. Thresholds do not disappear; they become **local, singular and
auditable**, stated once inside the layer that owns the measure, instead of being smuggled into a global
score nobody can inspect.

## Combination: Kleene conjunction, not a weighted sum

```
advance(P)  =  ⋀ verdict_ℓ(P)        over all applicable layers ℓ
```

with strong three-valued conjunction: `withhold ∧ anything = withhold`; `permit ∧ abstain = abstain`;
`permit` only when every layer permits.

Four properties, each of which fixes a defect found today at the architectural level rather than one site
at a time:

1. **No approval can outvote a refusal.** Conjunction, never a mean. This is the entropy defect (F22b)
   fixed structurally - a mean can never express "all of them must hold" at any threshold.
2. **Abstention is not permission.** Today four doctrine roles sit at `unknown` and the flow proceeds. Under
   conjunction they block. Popper made structural: absence of a refutation is not a verification.
3. **Monotone.** Adding a layer can only make advance harder, never easier - so the factory may grow its own
   verification without any risk that a new check accidentally unblocks something. This is what makes
   autonomous self-extension safe.
4. **A layer that cannot justify its verdict must abstain.** Six Sigma, whose defect classification is
   substring matching (F23), owes `abstain`, not a number. That converts a false measurement into visible
   epistemic debt - and, because abstention blocks, forces the repair instead of hiding it.

## Declaration: the Barcan condition

This system is named after quantified modal logic, and its central formula bears on exactly this point.
The Barcan formula holds when the domain does not grow across possible worlds - nothing new comes into
existence merely by moving to another world.

Applied here: **each layer declares, at the moment it becomes applicable, the finite set of propositions it
will rule on.** Verdicts are then over a fixed domain, not over whatever the layer happened to notice this
tick.

Without it, `abstain` is ambiguous between *"declared and not yet decided"* and *"never considered"* - and
the second is invisible, which is how every silent gap in this document arose. With it, the two are
distinct and the second cannot occur.

This is the same rigid-designation argument that grounds the conflict repair (F22): the referent is fixed
by declaration at the moment of commitment and cannot decay, whereas a pattern re-derived later outlives
its own truth conditions.

## The two numbers that replace all the others

```
D(P) = |{ declared propositions whose verdict is abstain }|     epistemic debt
W(P) = |{ declared propositions whose verdict is withhold }|    refusals

advance(P)  ⟺  D(P) = 0  ∧  W(P) = 0
```

**No threshold, and again that is the point** - a threshold is the signature of a proxy measure. `D` and `W`
are commensurable because they count objects of one type: verdicts on declared propositions. Unification is
achieved not by normalising `82%` and `954545` onto a common scale - which is impossible - but by mapping
every layer onto a common *type*.

DPMO, coverage ratios and completion rates do not vanish. They are demoted to what they always were:
**evidence a layer cites for its verdict**, never verdicts themselves.

## What follows without further design

**The TOC constraint becomes derivable.** The constraint is the layer maximising `W + D`. Measured now, that
is the doctrine layer (2 refuse, 7 object, 4 unknown) - and within the task layer, UX/UI (F25). Today
subordination is asserted by hand and points at the queue; here it is computed and points where the block
actually is.

**Kano attaches naturally.** Each declared proposition carries its Kano class, so `W` splits:
`advance ⟺ W_must = 0`, while `W_performance > 0` is a **documented compromise** rather than a block -
which is precisely the distinction the flow cannot currently express, and why the corpus insists on the
class in the first place.

**Staleness becomes expressible.** A verdict carries the observation it rests on. When the referent changes
- two Dockerfile repairs land - the verdict reverts to `abstain` rather than remaining `withhold` on
six-hour-old evidence (F27). Evidence expiring is the same rule the market corpus already applies to
`observed` entries; here it applies to verdicts.

## Order of construction

| Stage | Work |
|---|---|
| **A** | The `Verdict` type and Kleene conjunction. Pure, no dependencies, fully testable in isolation |
| **B** | Each layer declares its proposition set and maps its own measure to a verdict by its own stated rule. Six Sigma declares `abstain` until F23 is repaired - honestly, and blocking |
| **C** | `D` and `W` computed and surfaced. One place where the flow's verdicts are reconciled - the thing that does not exist today |
| **D** | Advance gates read `advance(P)` instead of their private conditions. Existing thresholds move inside their owning layer, unchanged in value, now singular and inspectable |
| **E** | Verdicts carry their evidence and revert to `abstain` when the evidence's referent changes |

Stages A-C add a reading of the system without changing its behaviour, so they can be built and observed
against the live flow before anything depends on them. Only D changes what the factory does.

---

# LATER FINDINGS, same day

**F28. The design shop discards a successful generation because of the model's name, and retries forever.**

```java
if (!result.available() || !"stitch".equals(result.model()) || result.repoDraftPath() == null ...) {
    self.releaseStartCycleClaim(project.getId());
    log.warn("no usable Stitch draft ... (model={}, message={}); will retry next tick");
    return;
}
```

Live on `test-forty-sixth`: `model=gemini-3.1-flash-image`, `message=Generated design asset and metadata.`
**The generation succeeded.** It is rejected because the model's name is not the literal string `"stitch"`.

Rejecting a fallback image model here is correct and deliberate (operator directive 2026-08-10: a raw
image carries no HTML a session could implement against). The defect is what follows: the tick releases the
claim and retries, on the stated assumption that this "resolves itself on retry". Six attempts recorded -
10:24, 11:15, 11:24, 13:40, 13:45, 13:54 - and the condition is not transient: Stitch is degraded and keeps
answering with an image model. Retrying an unchanging condition consumes generation quota indefinitely and
never escalates.

Same shape as everything else in this document: **a declaration ("the model is called stitch") stands in
for the property ("there is a usable draft with implementable HTML")**, and an unbounded retry hides the
gap instead of surfacing it. Under the verdict lattice this layer owes `withhold` with its reason, not a
silent loop.

**F29. Attribution correction - the corpus did not produce the epics credited to it.**

Recorded because it was asserted to the operator and was wrong.

Timeline: the backend image running when `test-forty-sixth` was decomposed (23:46-00:05) was built from
`d737705` at **23:02**. The commit promoting the corpus from inert to influential - `64d34ee`, which
introduced `derived` and `valueChainsFromCorpus` - landed at **02:35**, three and a half hours later.

So at decomposition time:

| Epic | Actual source |
|---|---|
| Automated Backups and Verified Restore | **corpus** - `backup-restore` was already `statutory` (GDPR Art. 32(1)(c)), and the wording "a restore that has actually been performed" is the corpus entry's |
| Self-Service Account Recovery | **not the corpus** - `account-recovery` was `hypothesis` and therefore inert |
| Goal-Question-Metric Telemetry | **not the corpus** - `product-measurement` was `literature` and therefore inert |

The latter two came from the prompt floors added in `3db0eb6` (17:54) or from the session's own judgement.
The claim that all three traced to corpus entries "promoted from inert to influential the same day" was
false: the promotion happened after the decomposition it was credited with.

**The value chains have never influenced a decomposition.** They did not exist at 23:46. Whether the three
wishlists compiled after the 03:12 deploy used them is unverified - the prompt is not logged, so there is
currently no way to confirm from the outside that `valueChainsFromCorpus()` rendered anything. That is
itself worth fixing: **a floor whose presence in the prompt cannot be observed cannot be known to work.**

**F30. Nothing shows the client the working, deployed product** (operator observation, 2026-08-15).

Every `valuePath` in the corpus traces the END USER's journey. None traces the buyer's: *how does the
client see that what they paid for exists and runs?* The factory reaches `DELIVERED` on merge counts, and
acceptance is a human act performed against nothing in particular.

This is the same missing link as F21 (nothing seeds primary content) seen from the other side, and the two
compound: a knowledge base with no materials, shown to nobody, is reported as complete. Together they are
the last link of every chain in the corpus - *the thing exists, there is something in it, and the person
who ordered it can see it working.*

Proposed as a second chain on every profile rather than a step in the first, because it fails
independently: a product can be perfect for its users and undemonstrable to its buyer.

```
acceptance chain: deployed and reachable -> seeded with enough content to mean something
                  -> the client can walk one real path themselves -> they can tell it is theirs
```

The mechanism partly exists - `runtime-launcher` produces a live URL, surfaced in `runtime-health` - so the
gap is not capability but that nothing composes those parts into something a client is handed. Under the
verdict lattice this is a declared proposition like any other, and while it is undecided the acceptance
gate owes `abstain`.

## What went right (so the repair does not break it)

Worth stating precisely, because these are now load-bearing:

- Three epics appeared that the client never asked for - **account recovery**, **backup with a restore that
  has actually been performed**, **GQM telemetry** - each traceable to a corpus entry promoted from inert
  to influential the same day.
- Two links appeared inside the cataloguing epic that the brief never mentions: *update or unpublish*, and
  *destructive actions must be confirmed*.
- **No false legal firings.** No Impressum, no sales tax, no age rating, no loot-box odds on a knowledge
  base. The conditional mechanism held.
- Accessibility landed as criteria inside every epic's requirements rather than as a separate "make it
  accessible" epic - exactly as the corpus demands.
- No padding: five epics, 21 tasks, zero failures at the time of writing.
