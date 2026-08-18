# Live Product Plan

Rewritten, never appended. Every number carries its source. Nothing is inferred from naming.

---

## 1. The goal, and the epistemology under it

**The factory keeps a running product under permanent falsification.**

"Finish the product" is not a goal because it is not a state. Product readiness means fitness to a
market, and fitness is never proven - only not yet refuted. What a completeness metric measures is how
much of the **currently known** scope is delivered; the next falsification enlarges that scope. A ratio
falling after a falsification round is the system working.

That is not a preference. It follows from what falsification is. A theory earns its keep by forbidding
something observable; a claim that forbids nothing is not false, it is empty. So a falsification cycle
run against a product that does not run is quantification over an empty domain - `∀x ∈ ∅` - trivially
satisfied and informative about nothing. This is the same vacuity found in the quality gate on
2026-08-18 (`allMatch` over a stream filtered to nothing), and it is why the codebase already states:

> *"a philosophical audit reasoning about a product that can't even start would be reasoning about
> nothing real. TOC: launchability is the constraint; everything else (including philosophical review)
> subordinates to it until it's cleared. Kano Must-Be by construction - not a taste judgment, a
> precondition."*
> — `WishlistSource.product_not_launchable`, 2026-08-11

**The constraint therefore has an epistemic ground, not merely a throughput one.** Subordinating
everything to launchability is not "unblock the bottleneck"; it is "make the observations possible at
all".

### The direction this forces

Two architectures are available:

- **(a)** build → complete → launch → improve
- **(b)** launch as early as it *can* run → improve while running

(a) is an infinite wait, because "complete" does not exist. (b) is forced by the epistemology: if
fitness is only ever learned by not being refuted, the product must be observable to be learned about.
Every drift back toward (a) is a category error wearing a schedule.

---

## 2. Three axes that must never be merged

| Axis | Question | Category | Answered by |
| --- | --- | --- | --- |
| **Scope delivery** | how much of the currently known intent is built | a quantity over a known set | `ClientDeliverableReadinessService` |
| **Operability** | does the thing start and stay up | a binary state about **now** | `ProductLaunchabilityService` + `runtime-launcher` |
| **Fitness** | does it match the market | a hypothesis under permanent test | falsification cycles, forever |

They are categorically different, and every failure this plan repairs is one of them being read as
another:

- Scope read as fitness → "the product is ready" → the flow gets closed. **This is the error that broke
  this system twice in earlier sessions, and it appeared in my own language repeatedly on 2026-08-18.**
- Operability read as scope → launch waits for a completeness number → infinite wait.
- Fitness read as scope → falsification is expected to terminate → the engine is switched off as
  "finished".

**Verified in code, 2026-08-19:** nothing merges them today.
`acceptProject` is reachable only from `ProjectController` - a human action; no service and no scheduler
calls it. `DELIVERED` names a state and stops nothing. `CHECK_LAUNCHABILITY` is gated on `activeProject`
alone, with no readiness condition. `DesignShopCycleEntity` is edge-triggered and explicitly anticipates
readiness *"genuinely dropping and rising again after a falsification round adds new features"*.

The architecture already embodies the philosophy. The work is to find where it does not.

---

## 3. The systems - measured 2026-08-19

71 services, 27 scheduled.

| Subsystem | Files | Owns |
| --- | --- | --- |
| `services/gate` | 8 | quality gates by stage; spec-only roles deliver documents, not code |
| `kaizen` | 8 | three levels: factory params (auto), factory source (review-only), product runtime |
| `services/operational` | 7 | flow spine state matrix, policy, operational truth |
| `services/design` | 5 | Stitch / image generation, drift, design-system falsification |
| `services/runtime` | 5 | launchability, client runtime observability, launcher client |
| `services/jules` | 5 | dispatch, sessions, review |
| `services/compiler` | 2 | wishlist → tasks |
| `services/coherence` | 1 | evidence graph: ECHO, AGM revision, Bayesian corroboration |
| `services/advice` | 2 | idle-project advice |

**Sixteen ways work enters the system** (`WishlistSource`): `client`, `role`, `role_mismatch_followup`,
`chaotic_debt`, `self_falsification`, `onboarding_finding`, `coverage_gap`, `closeout_abandoned`,
`philosophical_falsification`, `gemini_observer`, `runtime_observability_gap`,
`design_system_falsification`, `design_review_concern_pattern`, `dockerfile_missing_build_stage`,
`frontend_not_deployed`, `product_not_launchable`.

**Six of the sixteen are about operability, not features.** The system already treats "does it run" as
first-class work, which is the axis distinction of §2 made concrete.

**The launch path**: `runtime-launcher` - separate container, Docker socket, port 8091, healthcheck on
`/openapi.json` (added 2026-08-16 after it was down a whole day unnoticed). `POST /launch` clones the
product repo at a ref, requires `docker-compose.yml` **at repo root**, remaps published ports, runs
`docker compose up -d --build`. `POST /healthcheck` probes the result. **The product is launched by its
own compose file**; the launcher adds nothing and assumes nothing.

**Flags** (`GET /api/settings`):

```
client_runtime_observability_enabled  true     stitch_enabled              true (env)
design_shop_enabled                   true     stitch_api_key              SET  (****9SCw)
design_system_falsification_enabled   true     nano_banana_enabled         true (env)
philosophical_falsification_enabled   true     falsification_cycle_enabled FALSE
```

**Live state**: `ratio 1.0 · merged 25/25 · features 6/6 · pipeline idle · failed 0 ·
state ready_for_falsification`. Per §1 this means *everything currently known is delivered*, and the
enabled falsification tracks have just produced the next scope: `frontend_not_deployed` and
`design_review_concern_pattern`, both `compiling`.

---

## 4. What is measured to be wrong

**4.1 Launchability is a verdict about the past, not a state.** `ProductLaunchabilityService` sets
`launchabilityCheckedAt` unconditionally after the first check, "so this never re-fetches from GitHub on
every tick forever". Correct as written for a file-existence probe. But operability is a claim **about
now** (§2), and a value written once cannot be one. A product that becomes unlaunchable later is never
noticed.

**4.2 The constraint is measured and never acted on.** The service answers "could this be launched" and
files a wishlist item when the answer is no. `/launch` and `/healthcheck` exist, are healthy, and are
called by nothing in that path. The architecture declares launchability supreme (§1) and then leaves the
edge from constraint to action missing.

**4.3 The main falsification engine is the philosophical track, and it is running.**
`philosophical_falsification_enabled = true`, and it is what produced both items now compiling. An
earlier version of this plan read `falsification_cycle_enabled = false` as "the engine is off" - that
was wrong. That flag governs a different, internally complex mechanism which is **deliberately left
alone** (operator directive, 2026-08-19). Nothing here proposes touching it.

Recorded as a correction rather than edited away: I inferred which component was "the main engine" from
a flag name, which is the same reading-from-naming this plan forbids in its own header.

**4.4 The product's compose is backend-only.** The reason `frontend_not_deployed` exists. Until it
merges, a launch would serve half a product.

---

## 5. Plan

Ordered by the constraint, because §1 says everything subordinates to it. Each item states **why it
follows**, **what would falsify it**, and its **entry mode** on the existing promotion policy
(`observe_only → warn_only → soft_gate → hard_gate → auto_remediate`).

### 5.1 — Let `frontend_not_deployed` land. Do not launch before it does.

*Follows because*: launching a backend-only image would produce a running thing that is not the product,
and falsification against it would be observing the wrong object - the referent error, not a lesser
version of the right one.

*Falsified if*: the item merges and the compose still does not serve the frontend. Then the task's
own scope was wrong, not the plan's ordering.

*Mode*: none. Watch only.

### 5.2 — Make launchability a state instead of a memory.

*Follows because*: §2 classes operability as a claim about **now**, and §4.1 shows it is currently
written once. Re-check when the thing it describes could have changed - a merge to main - rather than
once per project forever.

*Falsified if*: re-checking finds the value never changes in practice. Then the once-only check was
adequate and 4.1 is a defect on paper only.

*Mode*: `observe_only` - record the re-checked state, change no decision.

### 5.3 — Close the edge from constraint to action.

*Follows because*: a constraint that is measured and never acted on is not a constraint, it is a report.
When launchability holds: `POST /launch`, then `POST /healthcheck`, and record the outcome as evidence
in the graph so it can corroborate and be corroborated like any other fact.

*Falsified if*: a launch succeeds and nothing downstream can use it - i.e. no falsification track
actually observes the running product. Then the missing edge was further along than this.

*Mode*: `observe_only` first - launch into the launcher's own workspace and record, before anything
gates on the result.

### 5.4 — Relaunch on merge, once a first launch has succeeded.

*Follows because*: falsification must observe the **current** thing (§1). A running instance that lags
main is a stale referent, and reasoning about it is reasoning about something that no longer exists.

*Falsified if*: relaunching produces no new observations - i.e. the observability layer reports the same
things regardless of version. Then the value is in observation, not in freshness.

*Mode*: deferred. It is meaningless until 5.3 has succeeded once, and building it earlier would be a
mechanism with nothing to act on.

### 5.5 — Do not touch `falsification_cycle_enabled`.

*Follows because*: the engine of the goal is the philosophical track, which is already on (§4.3). That
other flag governs a separate mechanism that is complex inside, and the operator has directed it be left
alone. A flag whose blast radius I cannot measure is not a lever I may pull.

*Mode*: none. This is a prohibition, not a task.

---

## 6. Guards against my own demonstrated failure modes

These are not general caution. Each is a thing I did on 2026-08-18, recorded so the plan does not
depend on my remembering.

| Failure | Instances | Guard |
| --- | --- | --- |
| Reading absence in a projection as absence in the data | 5 (dashboard DTO fields ×2, `unzip` in container, `docker logs` on a dead bridge, `stitch_api_key` masked field) | Before concluding "X is absent", show the same query returning a value for a case known to have one |
| Treating a metric as a terminal state | throughout the day, in language | §2 is the check: name which axis the number belongs to before drawing any conclusion from it |
| Proposing to merge deliberately separated distinctions | the first version of this plan proposed gating launch on readiness | Any proposal that reduces the number of distinct predicates must state what incident originally separated them |
| Declaring deployed what was never built | 2 (build exit code read as proof; `backend UP` from a stale image) | A build's exit code is authority on nothing; verify the artifact or the behaviour |
| Reading a tool's silence as a fact | the same 5 as above | Verify the instrument before believing the measurement |
| Inferring a component's role from a flag's name | 1 (read `falsification_cycle_enabled=false` as "the main engine is off"; the main engine is the philosophical track and it is on) | Name the component that implements the thing, and show it running, before describing what is or is not working |

---

## 7. Forbidden by construction

- **No number may be treated as "the product is ready."**
- **No number may close the flow.** The only terminal is `acceptProject`, and it is a human's.
- **No launch may wait on a completeness metric.** Waiting for "complete" is waiting for a state that
  does not exist (§1).
- **No falsification track may be switched off as "finished."** Finishing is not a thing it does.
