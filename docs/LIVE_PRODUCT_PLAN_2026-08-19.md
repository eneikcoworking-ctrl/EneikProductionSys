# Live Product Plan

Rewritten, never appended. Withdrawn items live as one line in §7, never as sections. Every number
carries its source; nothing is inferred from naming.

---

## 1. Goal, and the epistemology under it

**The factory keeps a running product under permanent falsification.**

"Finish the product" is not a goal because it is not a state. Product readiness is fitness to a market;
fitness is never proven, only not yet refuted. A completeness metric measures how much of the
**currently known** scope is delivered, and the next falsification enlarges that scope. A ratio falling
after falsification is the system working.

This is forced, not chosen. A theory earns its keep by forbidding something observable; a claim that
forbids nothing is empty. Falsification against a product that does not run is quantification over an
empty domain - trivially satisfied, informative about nothing. The codebase already says so:

> *"a philosophical audit reasoning about a product that can't even start would be reasoning about
> nothing real. TOC: launchability is the constraint; everything else (including philosophical review)
> subordinates to it until it's cleared. Kano Must-Be by construction — not a taste judgment, a
> precondition."* — `WishlistSource.product_not_launchable`, 2026-08-11

The constraint therefore has an **epistemic** ground, not a throughput one.

---

## 2. Three axes that must never be merged

| Axis | Question | Category | Owner |
| --- | --- | --- | --- |
| **Scope delivery** | how much of currently known intent is built | quantity over a known set | `ClientDeliverableReadinessService` |
| **Operability** | does it start and stay up | binary state about **now** | `ProductLaunchabilityService`, `ClientRuntimeObservabilityService` |
| **Fitness** | does it match the market | hypothesis under permanent test | philosophical falsification, forever |

Scope read as fitness closes the flow — **the error that broke this system twice in earlier sessions**.
Verified in code: `acceptProject` is reachable only from `ProjectController`, a human action;
`DELIVERED` names a state and stops nothing; `CHECK_LAUNCHABILITY` is gated on `activeProject` alone;
`DesignShopCycleEntity` explicitly anticipates readiness *"dropping and rising again after a
falsification round adds new features"*.

---

## 3. Three contexts — the levels I work at

Operator directive, encoded in `KaizenProposal.KaizenCategory` whose comment records it verbatim:
*"clearly marked as a product improvement, not mixed into the factory list."*

| Level | What it is | Who may act | What I do here |
| --- | --- | --- | --- |
| **FACTORY** | EneikProductionSys' own parameters and source | me, review-only for source | write code, measure, fix its defects |
| **DELIVERY** | wishlist → compiler → task → Jules → PR → merge → launch | me, in factory code only | fix how the process reports, decides and sequences |
| **PRODUCT** | the client repository's content and runtime | Jules, through the ordinary path | **never touch directly** — the 2026-08-07..09 contamination came from exactly that bypass |

**Every item below names all three**: where the change lives, where the defect belongs, what it is
about. They are frequently different.

---

## 3.5 SCOPE — only the active project. Everything else is noise.

**Operator directive, restated 2026-08-19 after I broke it again.** There are 22 projects. Exactly one
is `active`:

```
41af381d  test-forty-ninth  ACTIVE      <- the only project that exists for this work
2bbd00c8  test-forty-third  accepted    <- the client ended it; a human's terminal
30135572  test-forty-fourth frozen
686015fd  test-forty-sixth  frozen
… 18 more, none active
```

**Rows from a non-active project are not evidence about the factory.** A frozen project is not ticked,
so nothing recovers, advances or cleans it - and that is correct, not a defect. An accepted project
ended by human decision, and its unfinished items are supposed to stay unfinished.

I have now built findings out of dead-project rows **twice in this session**, and the second time I was
one step from repairing a mechanism that works. The cost is not only wasted work: every such finding
enters the plan as a real defect and has to be withdrawn, which is exactly the accumulation this plan
exists to prevent.

**Rule, from here on:** every query that reads project-scoped data filters to `status = active`, or
states in the same breath why a dead project is being read on purpose. No exceptions - a measurement
whose scope is not stated is not a measurement.

---

## 4. Where the live system stands — measured 2026-08-19

```
backend 200 · three containers · readiness 1.0 · merged 25/25 · features 6/6 · pipeline idle
db 855–1133 MB oscillating · git in sync with origin
```

**The constraint, traced to its root.** The launch chain is complete and working: the Phase-0 gate is
open, `ClientRuntimeObservabilityService` calls the launcher, observations are recorded. The blocker is
one line in the **product's own** compose:

```
object-storage Error failed to resolve reference
  "docker.io/minio/minio:RELEASE.2023-09-20T22-40-07Z": not found
```

Its *form* is perfect — MinIO really does name releases that way — and its referent does not exist:
`RUT_BARKAN_MARKUS_01_ACTUAL_OBJECT_REGISTER` violated inside a generated artefact.

**The factory has already delivered against this.** `frontend_not_deployed` produced a task, it
completed, and the Dockerfile on main now builds and serves the frontend
(`FROM node:20-alpine AS frontend-builder` … `COPY --from=frontend-builder … /static`). Verified at
source, not inferred.

---

## 5. Done, with contexts and verification

| # | What | Levels (change / defect / about) | Verified |
| --- | --- | --- | --- |
| 5.1 | `frontend_not_deployed` landed; Dockerfile builds and serves the frontend | — / product / product | source on main |
| 5.2 | `product_not_launchable` carries the observed cause, not just "not healthy" | factory / factory / product | 21/21, deployed |
| 5.3a | launchability gate asks **existence** (`mergedDeliverables > 0`), not completeness | factory / delivery / product | 26/26, deployed |
| 5.4a | observation cadence uses `(1 - width)`, matching its own documented contract | factory / delivery / product | 7/7, deployed; **confirmed live**: observation fired 09:12:32, six minutes after the 09:06 rollout, instead of the ~20 hours the old formula produced |
| 5.5 | a failed launch becomes an **evidence node**, not only a stored observation (`produceRuntimeObservationEvidence`) | factory / delivery / product | deployed; **confirmed live**: launch-related nodes in the ACTIVE project's coherence graph went 0 -> 1, node `d545a8a7` NEGATIVE_FINDING |
| 5.6 | `falsification_cycle_enabled` not to be touched — the engine is the philosophical track and it is on | — / — / factory | prohibition, recorded |

---

## 6. The work now: TOC as a procedure, not a catalogue

### 6.1 The diagnosis

Theory of Constraints is five steps. The factory performs one and a fragment of another.

| Step | Required | Present |
| --- | --- | --- |
| 1 Identify | — | `product_not_launchable` is filed ✓ |
| 2 **Exploit** | get maximum from the constraint | **nothing** |
| 3 Subordinate | everything else defers | one consumer defers: philosophical review |
| 4 **Elevate** | invest to break it | **nothing** |
| 5 **Repeat** | do not let inertia become the constraint | **nothing** |

**The category error.** A found constraint is filed as *an item in the ordinary queue* — the same pool
as coverage gaps, design concerns and client requests. It carries `essential` and Kano Must-Be, and it
**waits its turn**. Measured: `WishlistEntity` has no priority field at all; `leanValue` is the only
value-bearing column and nothing orders selection by it.

A constraint is not a high-priority item. A constraint is what the throughput of the whole is limited
by; everything else is slack. Putting it in one queue with slack hands the decision to whatever
ordering the queue happens to have. Goldratt's *subordinate* means the literal thing: **non-constraints
idle if that is what it takes for the constraint never to wait.**

### 6.2 The proposal

**Subordination is a condition in the policy, not an `if` inside one service.**

`OperationalPolicyService.authorize(action, project)` is already the single place that decides what may
run, and it already gates on states — `FROZEN`, `ACCEPTED`, `GITHUB_RATE_LIMITED`. A constraint belongs
there, in the same shape:

> While a constraint is open for a project, only actions that serve it are authorised.

This supplies every missing step at once:

- **Exploit (2)** — the constraint's work dispatches immediately, because it is the only thing allowed.
- **Subordinate (3)** — not one branch in one service but a property of the policy, because the policy
  is the single source of truth about permissibility.
- **Elevate (4)** — the factory's whole capacity points at one place by construction.
- **Repeat (5)** — the constraint clears, the gate opens, the next failed observation names the next
  one. No accumulated exceptions, so no inertia.

### 6.3 What it needs, measured

`FlowSpineDto` already carries `pendingWishlist` as a **count**; it does not carry the **sources**. So
the policy cannot currently ask "is one of these a constraint".

The gap is exactly one fact, computed where every other count is computed
(`FlowSpineService.inputs()`, which already holds the wishlist list) and consumed where every other
gate is consumed. **No new service, no new query, no second source of truth.**

### 6.4 Why it is mathematically sound

- **One source of truth.** The policy already decides authorisation; the constraint becomes one more
  condition there, not a parallel mechanism.
- **Minimum branching.** Not sixteen sources with bespoke rules — one predicate: *is a constraint open,
  and does this action serve it*.
- **Provable effect.** While a constraint is open, dispatches of non-constraint work are **zero**.
  Countable in the log, not estimated.
- **No catalogue.** The constraint is recognised by being *open in the data*, not by matching a list of
  known symptoms — which is what made every previous blocker cost a code change.

### 6.5 The rule that must not be lost

A constraint lives at one of the three levels of §3, and **a product constraint must not stop factory
kaizen**, nor the reverse. The predicate must carry the level or it will silence the wrong work. This
is not decoration: it is the operator directive that keeps the two streams visibly separate.

### 6.6 The three forks, decided by data

Measured 2026-08-19 across every project's constraint-class wishlist items:

```
2bbd00c8  product_not_launchable         pending           13 Aug 02:59   six days queued
30135572  product_not_launchable         compiling         13 Aug 06:06   six days in a transient state
686015fd  dockerfile_missing_build_stage converted_to_task 15 Aug 10:19
686015fd  dockerfile_missing_build_stage converted_to_task 15 Aug 10:26   DUPLICATE, 7 min later
686015fd  frontend_not_deployed          x2                10:19 / 10:26  DUPLICATE
686015fd  product_not_launchable         x2                11:20 / 12:03  DUPLICATE
```

**What clears a constraint — a fresh healthy observation, not a status.** `2bbd00c8`'s item has been
`pending` for six days, and `dismissed` items of this class exist. Since
`existsByProjectIdAndSource` blocks a new filing regardless of status, a dismissed constraint would
permanently prevent re-filing while the product stays broken. Only an observation speaks about now; a
status speaks about a record.

**Subordination cannot gate dispatch alone.** `30135572`'s constraint has been `compiling` for six
days - it never reaches dispatch, so a dispatch-only gate would subordinate nothing while the
constraint sits still. Whatever the mechanism gates, it must act where the constraint actually stalls.

**What serves the constraint — the task compiled from its wishlist item.** The link exists and is
populated: every one of the 46 tasks on test-forty-ninth carries `source_wishlist_id`.

### 6.7 The three "defects" — WITHDRAWN, all of them

The six-day strands and the duplicates are on **dead projects**, measured after the fact:

```
2bbd00c8  accepted   the client ended the engagement
30135572  frozen
686015fd  frozen
41af381d  active      the only live project
```

- The constraint `pending` for six days belongs to an **accepted** project. Work there ended by human
  decision - the only legitimate terminal in this system (§2). Leaving its constraint unresolved is
  correct.
- The constraint stranded in `compiling` is on a **frozen** project. `PlannedWorkRecoveryService`
  **already recovers stuck `compiling` wishlists** - it checks for an active compiler task, then routes
  the item to `converted_to_task` or back to `pending` - and it runs from the per-project orchestration
  tick, which frozen projects do not receive. Also correct.
- The duplicates are on a frozen project and predate its freezing.

I built three delivery defects out of the *shape* of data without checking the *status* of the projects
it came from, and was about to repair a mechanism that works. Recorded in §7.

**What survives from 6.6:** the fork decisions stand, because they rest on the semantics of the states
and the dedup, not on those rows. A constraint is still cleared by a fresh healthy observation rather
than a status, and subordination still cannot gate dispatch alone.

---

## 7. Corrections — my claims that measurement refuted

| I claimed | Measurement showed | The error |
| --- | --- | --- |
| "Nobody launches; the constraint is measured and never acted on" | `ClientRuntimeObservabilityService` calls the launcher; observations exist | read a service's name as its scope |
| "Launchability is a verdict about the past with no state object" | `ClientRuntimeObservationEntity` — append-only, owner, identity, timestamp | concluded a model absent without looking |
| "The main falsification engine is off" | The engine is the philosophical track, and it is on | inferred a component's role from a flag's name |
| "`stitch_api_key` is null" | `****9SCw` — set; I had read `enabled`, null for all secrets | absence in the wrong field read as absence in data |
| "Launch should be a consequence of readiness" | Separate axes, deliberately (§2) | proposed merging distinctions an incident had separated |
| "Gemini does not participate in the blocker chain" | `GeminiObserverActionService.triggerFalsificationRun` pulls the cycle forward | described her actions from memory instead of reading them |
| 5.3 registry pre-check | `docker compose up` already resolves references authoritatively | would have been a second source of truth for one fact |
| 5.4 `launchabilityCheckedAt` mis-used as state | Its only two readers use it as a bootstrap marker | judged a field mis-used without reading its call sites |
| "Deployed" ×2 | The jar did not contain the change | read a build's exit code as proof the image changed |
| "Zero ticks / zero observations" | `docker logs` had returned one line — its own bridge error | read a broken instrument's silence as a fact |
| Left the backend down 47 minutes | Announced a restart I never performed | reported my own action without verifying it happened |
| Three delivery defects from constraint items stranded for six days | All on accepted or frozen projects; `compiling` recovery already exists and correctly does not run on frozen ones | read the shape of data without checking the status of what produced it - and nearly repaired a working mechanism |
| "Jules accepted the task and has not started" | Jules's own API answered `state: IN_PROGRESS`, prompt `Update MinIO image tag in Docker Compose` | inferred from the absence of a warning line that the poll had happened and succeeded - absence of a complaint is not evidence of a question asked |
| Read the observer journal and constraint history without filtering to the active project — twice | 21 of 22 projects are accepted or frozen; their rows say nothing about the live factory | ignored the scope rule this session opened with (§3.5) |

---

## 8. Forbidden by construction

- No number may be treated as "the product is ready."
- No number may close the flow. The only terminal is `acceptProject`, and it is a human's.
- No launch may wait on a completeness metric.
- No falsification track may be switched off as "finished."
- No product content may be written directly into the client repo, bypassing wishlist → task → Jules.
- No claim about the system from memory: read it, or do not say it.

---

## 9. Diagnostics 2026-08-19 15:45Z - measured waste, ACTIVE project only

Method: one 65-minute log window (2119 lines), every WARN/ERROR normalised and counted, then each
frequent one traced to its cause. Frequency is the measure - a defect that fires four times a minute
forever costs more than one that fires once.

| # | Waste | Measured | Cause, where established |
| --- | --- | --- | --- |
| D-1 | `AutoMergeService` re-enters the same repair forever | **262 log lines / 65 min** (4/min) for one task | task `779705b2`, PR #107 merged with `hasCode=false`. The 2026-08-18 poka-yoke correctly declines to close it, but leaves it at `pending_review` with no path onward, so `taskNeedsRepair` stays true and the early return never fires |
| D-2 | Dead Jules sessions polled forever | 52 `activities fetch failed: 404` / 65 min | three sessions in `pr_opened` whose Jules-side session no longer exists. Verified directly: queried all nine live sessions with their own owning account keys - the five `running` ones answer 200, three of the four `pr_opened` ones answer 404 on both session and activities |
| D-3 | H2 store far larger than its contents | file 1211-1271 MB, live data 88 MB (13.7x) | **not monotonic**: the file fell 139 MB in five minutes, so the store does reclaim. Row-level churn is low - `TASKS` 5 updates/h, `JULES_SESSIONS` 9/h, `PROJECT_EVENT_LOG` 298 inserts/h. Cause not isolated; a 30-minute size sampler is running |
| D-4 | `GET /api/projects/{id}/tree` never answers | 90 s, `http=000` | not investigated. Correlates in time with the Hikari leak warnings; the dashboard is the likely caller, which would make this the lag felt in the UI |
| D-5 | A design asset fetched and missed forever | 14 / 65 min | `design/approved/20260818165327-mockup/mockup.html` is requested on `main` and is not there |
| D-6 | Connection-leak detections | 15 / 65 min | Hikari reports the connection later returned "unleaked", so these are slow queries crossing the leak threshold, not lost connections |

### What D-1 actually means

This is the one that costs delivery, not just log lines. A Jules session opened an **empty** PR, the PR
merged, and the poka-yoke I added on 2026-08-18 refuses to call that delivery - correctly. But refusing
is all it does. Nothing requeues the task, nothing fails it, nothing tells anyone. The work is silently
not done, and the reconciler spends four cycles a minute rediscovering that it will not act.

Declining to certify delivery and leaving the object where it stands are two different obligations. The
fix owns the first and not the second.

### Order

D-1 first: it loses work. D-2 second: it is cheap and it is the reason the log cannot be read. D-4 next,
because a hanging endpoint is what the operator feels. D-3 stays open and measured, not guessed at.
None of these touch the product repository.

---

## 10. Where value stops reaching the user - measured 2026-08-19 16:10Z

The question is not "is the factory busy". It is "what of the user's product actually changed". Every
number below is scoped to the ACTIVE project and was read from the database, not from a projection.

### F-1 Phantom deliveries: work declared done that put nothing in the product

Of 99 `done` tasks, 54 have **no merged PR containing code**. 47 of those are legitimate - `BARCAN-TAG-09`
is the DECISION stage and `EmsFlowStage` marks it `specOnly`, so a code-free merge is its correct shape.
I checked that before reporting, because the raw 54 invites exactly the wrong alarm.

Five are not legitimate - their role requires code and none arrived:

| Task | Role | Title |
| --- | --- | --- |
| `b50a4511` | TAG-02 implementation | API Slice (77380b22) |
| `32f8f498` | TAG-05 operations | Build Pipeline (e4f6126b) |
| `1e169d70` | TAG-05 operations | Build Pipeline (ebfba197) |
| `392deb2d` | TAG-05 operations | Recovery: Build Pipeline (ebfba197) |
| `03777375` | TAG-07 implementation | Recovery: Access Guard (a38949b8) |

**The live consequence.** Three of the five are `BARCAN-TAG-05` "Build Pipeline". Across the whole project
TAG-05 delivers code in 7 of 10 completed tasks. The MinIO fix currently running is a TAG-05 task titled
"Build Pipeline D4d84ab3". It is the same role, the same shape, and on the record so far it has roughly a
one-in-three chance of merging empty - after which the blocker persists and nothing says so.

This is the flow defect that matters most: the product's single blocker is being fixed by the role with
the worst delivery record, and the failure mode is silent.

### F-2 A declined delivery has nowhere to go

Task `779705b2` (PR #107, merged, `hasCode=false`) sits at `pending_review` permanently. The 2026-08-18
poka-yoke correctly refuses to certify it, and `AutoMergeService` re-enters the same refusal **4 times a
minute, forever** (262 log lines in 65 minutes). Declining to certify and leaving the object where it
stands are two obligations; the fix took the first and not the second. Same class as F-1: work quietly
not done.

### F-3 Kaizen produces proposals and never closes them

274 proposals in `PROPOSED` since 2026-08-05. 31 `APPLIED` in the system's whole history. Two
`STANDARDIZED`. And they are overwhelmingly the *same* proposal re-inserted:

| Count | Title |
| --- | --- |
| 79 | Factory self-health: the orchestrator's own database is unhealthy |
| 27 | u-chart out of control: qualityGate (epic 60677bf0) |
| 25 | Factory self-health: lock contention on the orchestrator's own database |
| 14 | u-chart out of control: taskRevival (epic c1be406c) |

A recurring observation must revise one record, not insert another - Charter invariant 4, idempotency.
As built, the improvement loop is a generator of duplicates, and the one `PRODUCT_RUNTIME_DEFECT`
proposal that concerns the user's product is buried among 273 about the factory's own database.

### F-4 The observer observes and cannot act

Gemini wrote 66 journal entries in four days; the model was actually called for 54 of them. Total actions
taken: **14** - and 11 of those were `nudgeStuckSession` on 2026-08-16. Two actions on 08-18, three on
08-19. Her latest entries repeat the same sentence hourly, and the most recent one says it outright:

> "I have exhausted my direct action capacity for these platform issues, as they require infra..."

Fifty-four paid calls to restate a standing finding is overproduction in the exact lean sense. Two things
are wrong and they are separable: she is called **on a timer rather than when the evidence changes**, and
when she is called she has no instrument for what she keeps finding.

### F-5 Generated artifacts still carry Cyrillic

`u-chart out of control: qualityGate (эпик ...)` - 27 rows. Project artifacts are English-only; this
string is produced by factory code, not typed in chat.

### F-6 Store growth, still open and still measured, not guessed

Sampled every two minutes: 1218 MB flat for four minutes, then 1218 -> 1336 MB in eight minutes, then
flat again. Live data 88 MB. Growth is bursty, not steady, and the store does reclaim (an earlier sample
fell 139 MB in five minutes). Row-level churn stays low. Cause still unnamed - deliberately.

---

## 11. What to build, in order, and why that order

**1. Route the declined delivery (fixes F-2, closes the hole F-1 leaves open).**
When a merged PR carries no code and the role requires code, one predicate already knows
(`hasRequiredMergeEvidence`). The reconciler must not only decline to close - it must hand the task back
to the flow: requeue it under the existing bounded-retry rule, or fail it loudly when retries are spent.
Silence is the defect, not the refusal. This also ends the 4-per-minute loop, because the task stops
being permanently un-repairable.

**2. Observe on merge, not only on a timer.**
A merge to `main` changes the object the posterior is about; the accumulated observations describe the
previous product. The merge must mark the observation due, leaving the adaptive formula in charge of
everything else. Without this, even a correct MinIO fix is invisible for up to seven hours.

**3. Make the cadence asymmetric.**
Present rule keys on interval width alone, so "confidently working" and "confidently broken" produce an
identical delay - 14.3 h at six consistent observations either way. Rarity must be earned by evidence of
health: key on the credible interval's lower bound. Six failures then give 1 h, six successes 14 h,
ignorance 1 h.

**4. Give Kaizen identity (fixes F-3).**
Proposal identity = (category, target component, normalised title). A recurring finding updates its row
and increments a recurrence count. 274 rows collapse to a few dozen, and the product-level proposal stops
being buried.

**5. Bury dead sessions (D-2).**
Three `pr_opened` sessions whose Jules-side session returns 404 are polled forever. A 404 on the session
itself is proof of absence - close the record.

**6. Then, and only then, Gemini's cadence (F-4).**
Call her when the evidence graph changes, not hourly. This is deliberately last: changing when she is
called before items 1-5 exist would only make her restate the same standing finding faster.

Not on this list, deliberately: the store growth (F-6, measured and unexplained - no fix without a
cause), the hanging `/tree` endpoint (D-4, real but it costs the operator, not the product), and anything
inside the client repository.

---

## 12. Three values, kept apart - the measure the factory does not yet have

Operator correction, 2026-08-19: value is not code, and there is not one value. There are three, one per
context (§3), and mixing any two is a category error. Each has a different bearer, a different declared
denominator, and a different way of being refuted.

| | Product value | Delivery value | Factory value |
| --- | --- | --- | --- |
| **Bearer** | the running instance | the engagement with the client | the factory itself |
| **Counts** | capabilities a user can actually exercise | brief items answered by a real artifact | requirements carried to product value with no operator |
| **Denominator** | capabilities the product claims | items in the client's brief | requirements attempted |
| **Refuted by** | an observation of the live product where the capability fails | a brief item with no artifact answering it | any human intervention |
| **Code vs content** | **the distinction vanishes** - a page with real copy works, a page with placeholder text does not; the observer cannot tell what produced it | **the distinction matters** - a copywriter's markdown is delivery even if it never becomes running behaviour | irrelevant - what is counted is who moved it |

Three consequences that are not cosmetic:

1. **`requiresCodeForDelivery` asks a code question about a delivery concept.** It has two answers, "code
   required" and "exempt". It needs one answer per role: the artifact that constitutes *that* role's
   delivery. See `ACP-102 - Criterion Is Not The Concept`
   (`BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE`, Frege), written up from this measurement.

2. **The routing fix in §11.1 must be rebuilt on that predicate before it deploys.** As written it retires
   a task whose merged PR has no *code*. A content role shipping markdown would be retired as having
   delivered nothing, and its requirement re-minted until the retry bound was spent. Measured today the
   active project has no such role in that state, so nothing has been damaged - but the trap is in the
   code, not in the data.

3. **Product value has no measure at all today.** Every counter the factory keeps - `done` 99, readiness
   1.0, coherence 15.2, 274 kaizen proposals - is factory-internal. The honest reading of this project
   right now is: product value **zero** (the product does not launch, so no capability is exercisable),
   delivery value high, factory value near zero for every requirement I touched by hand this session.
   Those three numbers disagreeing is the correct behaviour of three separate measures, not a defect.

Build order for this section: the role-relative delivery predicate first (it unblocks §11.1), then the
product-value measure as an observation over the running instance, then factory value as the fraction of
requirements that reached product value untouched. Delivery value already exists in readiness once its
predicate is corrected.

---

## 13. The goal, stated so it can only be met or not met

**Goal.** One row exists in `client_runtime_observations` for the ACTIVE project with
`launch_success = true` **and** a non-null `health_status_code` returned by the product's own health
endpoint, written by the factory's own launcher, with no human step in the launch.

Not "the product is ready". Not "the scope is complete". Not "the blocker is fixed". One observation,
made by the factory, of the product answering for itself. It is either in the table or it is not.

**Why this and nothing else.** It is the only claim that belongs to all three value levels at once
(§12): it is the first unit of product value (a running instance answering), it proves delivery reached
reality rather than `main`, and it is factory value because the observation was produced without me. Any
other formulation lets one level stand in for another, which §12 forbids.

**It is not terminal.** The observation is a not-yet-refuted claim, refutable by the next observation.
Falsification cycles continue after it, forever. Reaching it opens the loop; it does not close anything.

### Measured state against the goal, 2026-08-19 18:36Z

| Blocker | State |
| --- | --- |
| MinIO tag unresolvable | **gone.** Jules merged PR #118 `fix(docker): update MinIO image tag`; the task's own PR #120 carries code. `minio/minio:RELEASE.2023-11-01T18-37-25Z` and `postgres:15-alpine` are both pulled on the host, so the reference now resolves |
| Host cannot fit factory + product | **fixed.** Backend heap 1536m -> 1024m; free memory 543 MB -> 1741 MB. Verified in the running process (`Xmx1024m` read from `/proc`), not in the compose file |
| Launch attempt at 16:32 never completed | **open.** The launcher logged no `POST /launch` at all - uvicorn writes its access line on completion, so the request was still in flight when the backend gave up at 600 s. No product containers remain. Most likely the 576 MB of free memory at the time; now retestable |
| The failure was recorded as the **product's** | **open, and it is a category error.** `runtime-launcher unreachable: Read timed out` is a fact about the instrument, not the object. It went into the posterior as `launch_success = false`, so an instrument fault now makes the factory believe the product is broken - and stretches the next check to ~9.7 h. This is `ACP-102` again: the criterion "the launch call returned success" is not the concept "the product launched" |
| Next observation is ~9.7 h away | **open.** Beta(1,4) after three recorded failures, two of which the product did not cause |

### The path, deterministic

1. **Separate instrument failure from product failure.** An unreachable or timed-out launcher must not
   update the product posterior at all - it is a missing observation, not a negative one. Without this the
   goal is unreachable by construction: every instrument fault pushes the next attempt further away.
2. **Observe on merge** (§11.2), so the fix that already landed gets tested now rather than after 09:00.
3. **Retest.** With 1741 MB free the launch has room it did not have at 16:32.
4. The role-relative delivery predicate (§12.1) and the routing fix (§11.1) stay queued behind these -
   they protect future work, they do not stand between here and the goal.

---

## 14. Move the factory to the Hetzner server - on command, not now

Held deliberately: the host limit costs time, it does not block the goal in §13. Written down so it can be
executed without re-deriving it.

**What moves.** Four compose services - `backend` (8080), `frontend` (3000), `ml` (8000),
`runtime-launcher` (8091) - plus three volumes: `./data`, `./project-workspaces`, and the repository
itself mounted read-only at `/app/eneik-system` (the philosopher corpus and market corpus live there so a
correction takes effect on restart rather than on rebuild).

**First hard part - the state is one file.** `data/eneik_db.mv.db` is 1356 MB holding ~90 MB of live data;
the whole `data/` directory is 5.5 GB. It must **not** be copied while the backend runs - doing exactly
that on 2026-08-19 produced `File corrupted while reading record`. Stop the backend, let H2 close cleanly
(it compacts on close), then copy: roughly 90 MB instead of 5.5 GB. Every credential the factory holds -
per-account Jules keys, settings, flags - lives in that same file, so moving it moves the access with it.

**Second hard part - the launcher holds the Docker socket.** `/var/run/docker.sock` is bind-mounted into
`runtime-launcher` because it starts client products on the same host. On a public server that is root on
the box: port 8091 must never be exposed, and neither must 8000.

**Environment actually required:** `GEMINI_API_KEY`, `GITHUB_TOKEN`, `GITHUB_ORG`, `STITCH_API_KEY`, and
Linear's three if used. The other ~30 compose variables have defaults.

**Order:** Docker on the server -> stop the factory here and let the store close -> copy `data/` and
`project-workspaces/` -> clone the repository, write `.env` -> `docker compose up -d --build` (Flyway
migrates on boot) -> expose only 3000 and 8080, behind a reverse proxy with auth.

**What it buys:** the host limit disappears. Today it cost a failed launch at 16:32Z and forces the
backend down for every build. **What it does not buy:** every defect in this plan travels with the system
unchanged. The server removes a physical constraint, not a logical one.

---

## 15. The three values as mathematics, built on what already exists

Written after re-reading `OPERATIONAL_MATH_ARCHITECTURE.md` and `ENGINEERING_INVARIANTS_CHARTER.md`
rather than from memory. An earlier draft of this section was a taxonomy: it proposed measures that the
system already has, ignored the promotion discipline that keeps new rules from breaking flow, and would
have been dangerous to execute.

### 15.0 What is already load-bearing

| Already built | What it gives the value question |
| --- | --- |
| `OperationalTruthService` (651 lines) + `OperationalTruthController` | the read-only value layer the math document specifies, with the invariant catalogue already computed - `delivered_requires_evidence`, `done_is_not_delivery`, `closed_unmerged_is_not_delivery`, `agent_claims_are_weak_evidence` - in observe/warn status |
| Evidence Algebra 0-5 (math doc) | a declared strength order. Merged PR = 5, runtime check = 3, agent prose = 1. "Activity evidence must not be treated as delivered value" is already law here |
| `ClientDeliverableReadinessService.Readiness` | `totalFeatures / completeFeatures / totalDeliverables / mergedDeliverables / ratio`, with denominator exclusions already enumerated per invariant 8 |
| `BetaPosterior` | exact conjugate Beta-Bernoulli with real Beta quantiles - the measure machinery for any repeated yes/no observation |
| `RuntimeHealthShiftDetector` | exact two-sided binomial test against a baseline |
| `LeverStage` + `LeverPromotionService` + 1185 `LEVER_OBSERVATIONS` | the promotion policy from the math doc, implemented: `OBSERVE_ONLY -> WARN_ONLY -> SOFT_GATE -> HARD_GATE -> AUTO_REMEDIATE` |
| `WishlistSource.coverage_gap` (24 on this project) | the audit that asks whether the decomposition covers the client's brief |
| `EvidenceCoherenceService` | Thagard ECHO activation + AGM revision over the evidence graph |

The math document also already states the principle I wrote up as `ACP-102`: *"limits of substitutivity:
`task done` cannot be substituted for `value delivered`"*. ACP-102 is its specialisation to the case
where the substitution is legitimate over one class of bearers and silently wrong outside it.

### 15.1 Product value

**Today's measure is the degenerate case.** `BetaPosterior` maintains one Beta(α, β) over a single
Bernoulli: did the stack boot and answer `/health`. That is |C| = 1, where C is the set of capabilities
the product claims.

**General form.** For each capability c ∈ C, one Beta(α_c, β_c), updated by one observation of c:

    V_p = |{ c ∈ C : LCB_0.95(c) ≥ θ }|,   θ declared

- **Popper.** LCB < 1 for every finite sample, so no capability is ever proven - only not yet refuted.
  A single failing observation lowers LCB_c and the capability leaves the count. The measure can fall.
- **Invariant 8, denominator.** |C| is declared from the client's brief, with exclusions enumerated -
  not from the factory's own decomposition.
- **Invariant 12, independent verification.** The witness is the launcher: external to the agent that
  wrote the code, which is exactly what invariant 12 demands and what its own incident (a frontend
  delivered with zero real backend calls) was about.
- **Why the lower bound, not the mean.** The mean rewards ignorance; LCB makes confidence something
  evidence has to earn. Same reason §11.3 keys the cadence on the lower bound.

**Today:** |C| = 1, α = 1, β = 3, LCB ≈ 0.008, θ would be ≥ 0.5 on any honest setting. **V_p = 0.**

### 15.2 Delivery value

Both halves already exist and neither is a new measure:

    V_d  =  ratio  ×  coverage
    ratio    = mergedDeliverables / totalDeliverables      (built, invariant-8 clean)
    coverage = brief items with >= 1 deliverable / brief items   (audited, never expressed as a number)

`ratio` answers *did we merge what we planned*. The `coverage_gap` audit answers *did we plan what was
asked* - it already runs and already produces wishlists, but it produces findings, not a fraction. Until
it does, `ratio = 1.0` is read as delivery when it only ever meant "we finished what we set ourselves",
which is self-attestation (invariant 12) at the level of scope.

**The one real defect** is inside `ratio`'s merge predicate: `requiresCodeForDelivery` answers a code
question about a delivery concept, correct for 10 of 13 roles and silently wrong for the rest (ACP-102,
§12.1). Measured: 5 phantom deliveries, and a trap waiting for any content role.

### 15.3 Factory value

    V_f = |requirements that reached V_p with zero operator steps| / |requirements attempted|

The machinery exists - `LeverPromotionService` already tracks Beta-Bernoulli evidence per lever and
promotes through the five stages. What does not exist is the numerator's precondition: **nothing records
that a human acted.** Restarts, reopened wishlists, edited settings, hand-triggered merges leave no row
attributable to the requirement they touched. Until that exists V_f cannot be computed at all, and every
autonomy claim about this factory is unfalsifiable.

### 15.4 What could break, and the discipline that prevents it

The math document's Non-Negotiable Boundary says the operational math layer must not mutate flow state.
Two of the items below are write-side and therefore not "math layer" work at all - they change the write
owner and must be treated as such.

| Change | What it can break | Entry stage |
| --- | --- | --- |
| Role-relative delivery predicate (§12.1) | `ratio` feeds the `self_falsification` readiness gate and FlowSpine's DELIVERED status. Moving it silently can unblock work that should be blocked, or freeze the falsification mechanism that is this codebase's only authorised producer of replacement work for a dead task | `OBSERVE_ONLY`: compute old and new side by side, log the disagreements, promote only once the difference is understood and bounded |
| Per-capability observation (§15.1) | |C| health checks per cycle instead of 1, on a 3.9 GB host that already failed one launch on memory | `OBSERVE_ONLY`, one capability per cycle round-robin - which is also what keeps the Beta updates independent |
| Routing an uncertified merge (§11.1) | write-side: it moves `TaskStatus`. Absorbing-state rule (invariant 3) and CAS (invariant 1) both apply; the retry bound is the well-founded measure (invariant 7) | not a lever - a guarded write, already written, held until the predicate above is right |
| Excluding instrument failures (§13) | changes cadence only; bounded, tested 25/25 | done |

Nothing here is promoted past `OBSERVE_ONLY` without the math document's own four criteria: matches real
incidents across cycles, false positives bounded, regression test exists, rollback is one setting.

---

## 16. The assembly has no owner - diagnosis and the single change that gives it one

### 16.1 What actually happened, to the minute

`2026-08-16 05:58:50` - task *"feat(db): Add schema for search analytics events"* creates `pom.xml` and
`application.properties` with `spring.datasource.driverClassName=org.h2.Driver`. Correct for its brief: a
schema needs a database, in-memory H2 is the standard scaffold.

`2026-08-16 06:10:38` - twelve minutes later, task *"Configure automated backup jobs and alerting
mechanism"* creates `Dockerfile` and `docker-compose.yml`, declaring `postgres:15-alpine` and passing
`SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/...`. Correct for its brief: backups cannot be
demonstrated against an in-memory database.

Neither wrote a wrong line. The second overrode the *URL* and not the *driver*, and never added the
PostgreSQL dependency to `pom.xml` - because its subject was backups, not the application's datasource.
`pom.xml` still contains only H2 today.

The product therefore does not describe one stack. It describes two, and no stack can be raised from it.
The application dies before opening its port:
`Driver org.h2.Driver claims to not accept jdbcUrl, jdbc:postgresql://db:5432/epidemiology_db`.

### 16.2 The factory can raise any stack, and should not choose one

`RuntimeLauncherClient` runs `docker compose up` on the product's own file. It is already
stack-agnostic and that is the right design - it raises whatever the repository declares. Java with
PostgreSQL, Python with Mongo, anything.

Which datastore is right is a **product** question and belongs to the specification: for one brief H2 is
the better answer, for another PostgreSQL is. A factory rule of the form "always PostgreSQL" would be a
patch. What holds universally, without prescribing any stack:

> The runtime contract names every service the product runs against. `docker-compose.yml`, the build
> manifest and the application configuration are **consequences** of that declaration, not independent
> decisions.

The artifact for this already exists in the product: `docs/architecture/adr-002-runtime-contract.md`,
"Repository Execution Boundary and Runtime Contract", produced at the ARCHITECTURE stage (order 20,
`BARCAN-TAG-01`). It fixes the backend boundary, the frontend boundary, and the install/run/test commands
for both. It names **no datastore at all**. The place is right, the stage is right, the role is right;
half the content is missing.

`BARCAN-TAG-01` is `ACTUALIST-OBJECT` - Ruth Barcan Marcus: register only actual objects. A datastore no
artifact declares is not an actual object, and three artifacts each presupposing a different one are a
register of non-actual objects. The role whose principle is exactly this is the role that must own it.

### 16.3 Why the integration role never runs - the structural cause

`BARCAN-TAG-00` (CODE-GUARDIAN, INTEGRATION, order 70) has **0 tasks on this project** and **4 out of
1375 across the whole factory's history**. Every other role worked.

The cause is in `TechnicalLeadCompiler.targetRoleForWishlist`: a task exists only where a **wishlist**
exists, and the role is taken from the wishlist's own tag, its DoD, or keyword inference over its text.
Wishlists come from client intent, Gemini, coverage gaps and falsification.

**Integration is not anyone's requirement.** It is a property of the assembly. A requirement-pulled
decomposition cannot produce it - not because of a bug, but by construction. So the role exists, is
routable, and is never reached.

Two further findings confirm the role is not merely idle but mis-defined:

- `product_not_launchable` sets no `sourceRoleTag` and names no role in its DoD, so it falls through to
  keyword inference. Its text contains no "merge"/"integration"/"artifact", so it is **not** routed to
  TAG-00. Measured: the MinIO blocker became a TAG-05 *Build Pipeline* task - operations fixing a symbol,
  not integration fixing an assembly.
- TAG-00's own file scope in the compiler is
  `src/main/java/.../<Feature>IntegrationService.java`. The compiler believes integration means *writing
  a class*. Even when dispatched, it would not check the assembly.

### 16.4 Three absences, at three stages - not one defect

| Stage | Whose work | What is absent |
| --- | --- | --- |
| ARCHITECTURE (20), TAG-01 | decide and declare the datastores in the runtime contract | the contract covers code only |
| OPERATIONS (50), TAG-05 | build compose **from** the contract | built it from nothing and invented PostgreSQL |
| INTEGRATION (70), TAG-00 | check the artifacts agree with the contract | never dispatched, and scoped to write a class if it were |

The runtime observation is the last line, and today it is the only one that fired - at the most expensive
point in the flow.

### 16.5 The change

One principle, applied in one place each. The factory already has the shape: factory-generated wishlist
sources (`coverage_gap`, `self_falsification`, `product_not_launchable`) exist precisely to produce work
no client asked for. What is missing is that assembly failure is addressed to nobody and carries no cause.

**a. The launcher returns the failing service's own log.** A health failure today yields
`connection refused` - the symptom. The cause sat in the container log two steps away and nothing read
it. Without a cause there is nothing for an implementer to fix, and the factory can only guess.

**b. `product_not_launchable` is addressed to `BARCAN-TAG-00` explicitly** and carries that cause, instead
of being routed by keyword inference to whoever the text happens to resemble.

**c. TAG-00's scope and definition of done become the assembly:** every artifact is derivable from the
declared runtime contract, and where the contract does not name the services the product runs against,
extending it is part of the work. Not a Java class.

The factory still chooses no stack. It requires that a stack be chosen once, declared, and that everything
else follow from the declaration - which is checkable without knowing which choice is better.

---

## 17. Content as claims, not as an artifact that exists

Operator directive, 2026-08-20: a content role needs a real mechanism of philosophical verification -
actuality, relevance, SEO titles and metadata - not a presence check. Written here because a presence
check is exactly what a stub is, and a stub would fail for the same reason the whole delivery predicate
failed: it would substitute a criterion for the concept.

### 17.1 The wrong question

"Is there text in this field" is the content analogue of "does the PR contain code". It is satisfied by
lorem ipsum, by a generated generic paragraph, and by a title naming a feature the product does not have.
The Evidence Algebra already grades that at **0 - negative evidence**: *"Duplicate/fallback/generated
generic content"*. The factory's own scale says filler is worse than absent, and no check currently
enforces it.

### 17.2 The right question, in three conditions

Content is not an artifact that occupies a file. It is a set of **claims about the product**, addressed
to a reader, and each claim is checkable against the product itself.

**Reference** - `BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE`, Frege, sense and reference. Every
substantive noun phrase in a heading, title or meta description must have a bearer in the product: a
declared capability, a real route, a real entity. A `<title>` naming a capability the product does not
expose is a name with perfect form and no bearer - structurally the same defect as
`minio/minio:RELEASE.2023-09-20T22-40-07Z`, which was also a well-formed name for nothing. Checkable
against the served HTML the launcher already fetches (`RuntimeLauncherClient.fetchHtml`, built for the
design-drift check) plus the OpenAPI contract TAG-12 already produces.

**Felicity** - Austin, already the anchor of `ACP-101`. A call to action is a performative: "Start free
trial" commits the product to having a trial. Its felicity condition is that the act it names can be
performed. Checkable: every CTA resolves to a reachable route on the running instance.

**Actuality** - `BARCAN-TAG-01_ACTUALIST-OBJECT`, Ruth Barcan Marcus. Content must quantify over actual
objects. Placeholder copy, generic filler and duplicated boilerplate quantify over an empty domain: the
page is vacuously "filled" and delivers nothing, the same `forall x in empty set` structure as running a
falsification cycle over a product that does not start.

### 17.3 Relevance is not a property, it is a posterior

A claim true when written can be false later: a price, a feature list, a capability that regressed.
Content therefore gets exactly the treatment §15.1 gives capabilities - **one Beta posterior per claim**,
updated by observing the live page, refuted the moment the page and the product disagree. Relevance is
never proven; it is not yet refuted, and it decays without observation.

This is the point where content stops being a separate concern. A content claim **is** a capability in
the observable sense: something the product asserts it can do, checkable from outside by the launcher,
counted in `V_p` under the same lower-bound rule. No second measure, no second vocabulary.

### 17.4 Where it belongs in the flow

No fourteenth role. The BARCAN taxonomy is fixed at thirteen and content is not a new stage - it is a
delivery **artifact kind**, which is precisely what `ACP-102` says a role declares:

| Stage | Role | Content obligation |
| --- | --- | --- |
| ARCHITECTURE (20) | TAG-01 | the runtime contract names where content lives and what serves it, as it must for any dependency |
| EXPERIENCE (30) | TAG-11 `CLIENT-PERCEPTION` | authors the copy, headings and metadata - its delivery artifact is content, not code, and the delivery predicate must say so |
| API_CONTRACT (27) | TAG-12 | the contract that gives the reference check its referents |
| VERIFICATION (60) | TAG-06 | the three conditions above are its acceptance evidence, and `VerificationEvidenceGate` already exists as the place a QA role proves something without producing code |
| observation | launcher | fetches the served page and refutes claims that no longer hold |

`requiresCodeForDelivery` must therefore answer CONTENT for TAG-11, not CODE - and today it answers CODE,
which is the trap §12.1 names. It has no bearer yet only because nothing has been filed as content work.

### 17.5 What must not be built

A keyword-density score, a readability index, a "SEO checklist" - none of them are claims about the
product and none of them can be refuted by observing it. They measure the text against itself, which is
self-attestation (invariant 12) with extra arithmetic. The only admissible checks are the ones that can be
falsified by the running product.

