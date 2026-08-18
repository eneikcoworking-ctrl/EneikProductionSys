# Live Product Plan

Rewritten, never appended. Every number carries its source. Nothing inferred from naming.

---

## 1. The goal

**The factory keeps a running product under permanent falsification.**

Not "finish the product". That state does not exist. Product readiness is fitness to a market, and
fitness is never proven - only not yet refuted. A completeness metric measures how much of the
*currently known* scope is delivered, and the next falsification cycle enlarges that scope. The number
going down after a falsification is the system working, not regressing.

Three consequences, and each is a prohibition:

- **No number may be treated as "the product is ready."** `25/25`, `6/6`, `ratio 1.0` describe a moment,
  never a finish.
- **No number may close the flow.** Verified in code: `acceptProject` is reachable only from
  `ProjectController` - a human action. No service and no scheduler calls it. `DELIVERED` names a state,
  it stops nothing.
- **Falsification runs forever.** Its output is new scope, permanently.

**The constraint is launchability**, and the codebase already says so
(`WishlistSource.product_not_launchable`, 2026-08-11):

> *"launchability is the constraint; everything else (including philosophical review) subordinates to it
> until it's cleared. Kano Must-Be by construction - not a taste judgment, a precondition."*

A philosophical audit of a product that cannot start is reasoning about nothing real. So: the product
runs as early as it *can* run - not when scope is complete - and every later cycle improves a live
thing.

---

## 2. The systems - measured 2026-08-19

**Scale:** 71 services, 27 of them scheduled.

| Subsystem | Files | What it owns |
| --- | --- | --- |
| `services/gate` | 8 | quality gates by stage; spec-only roles deliver documents, not code |
| `kaizen` | 8 | three-level improvement: factory params (auto), factory source (review-only), product runtime |
| `services/operational` | 7 | flow spine state matrix, policy, truth |
| `services/design` | 5 | Stitch/image generation, drift, design-system falsification |
| `services/runtime` | 5 | launchability, client runtime observability, launcher client |
| `services/jules` | 5 | dispatch, sessions, review |
| `services/compiler` | 2 | wishlist -> tasks |
| `services/coherence` | 1 | evidence graph: ECHO, AGM revision, Bayesian corroboration |
| `services/advice` | 2 | idle-project advice |

**Sixteen ways work can enter** (`WishlistSource`): `client`, `role`, `role_mismatch_followup`,
`chaotic_debt`, `self_falsification`, `onboarding_finding`, `coverage_gap`, `closeout_abandoned`,
`philosophical_falsification`, `gemini_observer`, `runtime_observability_gap`,
`design_system_falsification`, `design_review_concern_pattern`, `dockerfile_missing_build_stage`,
`frontend_not_deployed`, `product_not_launchable`.

Six of those sixteen are about the product being *operable* rather than *featureful*. The system
already treats operability as first-class.

**The launch path**, end to end: `runtime-launcher` (separate container, Docker socket, port 8091,
healthcheck on `/openapi.json`) exposes `POST /launch`, which clones the product repo at a ref, requires
`docker-compose.yml` **at repo root**, remaps published ports, and runs `docker compose up -d --build`.
`POST /healthcheck` probes the result. The product is launched **by its own compose file**; the launcher
assumes nothing and adds nothing.

**Flag state** (`GET /api/settings`, 2026-08-19):

```
client_runtime_observability_enabled  true    stitch_enabled         true  (env)
design_shop_enabled                   true    stitch_api_key         SET   (****9SCw)
design_system_falsification_enabled   true    nano_banana_enabled    true  (env)
philosophical_falsification_enabled   true    falsification_cycle_enabled  FALSE
```

---

## 3. Where the live product actually stands

```
readiness ratio 1.0 · merged 25/25 · features 6/6 · pipeline idle · failed 0
state ready_for_falsification
```

That is **not** "done". It is "everything currently known is delivered, and the falsification tracks
that are enabled have just produced the next scope":

- `frontend_not_deployed` (created the moment the gate opened) - the product has a real `frontend/`
  directory that its Dockerfile never builds or serves. The deployable image is backend-only.
- `design_review_concern_pattern` - design generation returned a picture where implementable HTML was
  required.

Both are `compiling`.

---

## 4. What is wrong, precisely

**4.1 Launchability is checked once per project, ever.** `ProductLaunchabilityService` sets
`launchabilityCheckedAt` unconditionally after the first check "so this never re-fetches from GitHub on
every tick forever". Sound for a file-existence probe - but it means launchability is a **verdict about
the past**, not a **state**. A product that becomes unlaunchable later is never noticed.

**4.2 Nobody launches.** The service answers "could this be launched" (is there a compose file) and
files a wishlist item if not. `/launch` and `/healthcheck` sit ready and are called by nothing in that
path. So the constraint the architecture declares as supreme is measured, and never acted on.

**4.3 The main falsification cycle is off.** `falsification_cycle_enabled = false`. The two items now
compiling came from the design-system and philosophical tracks. The main engine of permanent scope
growth is silent, and whether that is deliberate is unknown to me.

**4.4 The product's compose is incomplete.** Backend-only. Until `frontend_not_deployed` merges, any
launch would serve half a product - which is exactly why the item exists.

---

## 5. Plan

Ordered by the constraint. Nothing here proposes a terminal state, and nothing gates launch on any
completeness metric.

**5.1 Let `frontend_not_deployed` land.** It is already compiling. It changes the product's own compose
so a launch would serve the whole product. No action needed but watching - and *not* launching before
it merges.

**5.2 Make launchability a state rather than a memory.** Re-check when the thing it describes could
have changed - a merge to main - instead of once per project forever. Small, and it makes 4.1 false.

**5.3 Close the loop from constraint to action.** When launchability holds, launch: `/launch`, then
`/healthcheck`, and record the result as evidence. This is the missing edge in the whole architecture -
the constraint is declared supreme, measured, and never acted on.

**5.4 Relaunch on merge.** Once a launch has succeeded, every merge to main should produce a new
launch, so the running version tracks main and falsification always observes the current thing.

**5.5 Decide `falsification_cycle_enabled`.** Operator call. It is the engine of permanent scope growth;
with it off, the loop this plan describes has a much narrower source of new work.

### Explicitly not in this plan

- Any change that makes launch wait for a readiness number.
- Any state that ends the flow. The only terminal is `acceptProject`, and it is a human's.
