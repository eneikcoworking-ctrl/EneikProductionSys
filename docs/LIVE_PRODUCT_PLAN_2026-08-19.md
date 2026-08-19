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

