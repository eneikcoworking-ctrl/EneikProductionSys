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
