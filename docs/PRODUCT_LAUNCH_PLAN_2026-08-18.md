# Product Launch Plan

**Goal (unchanged):** the product launches when it is ready, and every later factory cycle works on a
running product - serial improvements after falsification, not improvements to an artefact nobody has
started.

This file is rewritten, never appended, so what it says is what is true now. Measurements carry their
source. Nothing here is inferred from naming.

---

## 1. How deployment actually works today - measured

`runtime-launcher` is a separate Python service with the Docker socket mounted, on port 8091, with a
healthcheck on `/openapi.json` (added 2026-08-16, because it had previously been down a whole day with
nothing saying so). `POST /launch` does exactly this:

```
git clone --depth 1 --branch <ref> <repo_url>
  -> require docker-compose.yml AT THE REPO ROOT   (else: fail, "not found after clone")
  -> _remap_ports(compose_file)                    (avoid host port collisions)
  -> docker compose -p <name> -f <file> up -d --build
POST /healthcheck                                  (probe the result)
```

**The product is launched by its own compose file.** Whatever that file describes is what runs. The
launcher adds nothing and assumes nothing.

`FalsificationCycleService` already knows how to react: if the project is not launchable/healthy it
creates a `product_not_launchable` wishlist item - one per project, not one per attempt.

## 2. Why the frontend did not deploy

**Not a launcher defect.** The launcher ran the product's own compose, and that compose builds only the
backend. The factory generated frontend *code* and did not generate its *deployment description*.

The system found this itself. Wishlist `62704107`, source `frontend_not_deployed`, created
2026-08-18T14:15 - the moment readiness reached 1.0 and the gated cycles unlocked:

> "This project has a real frontend/ directory, but Dockerfile never builds or serves it - the
> deployable image is backend-only. Build the frontend (npm …"

It is `compiling` now.

## 3. Flag state - measured 2026-08-18 via GET /api/settings

```
client_runtime_observability_enabled  = true   (database)   <- launch IS enabled
design_shop_enabled                   = true   (database)
design_system_falsification_enabled   = true   (database)
philosophical_falsification_enabled   = true   (database)
falsification_cycle_enabled           = FALSE  (database)   <- the main cycle is OFF
stitch_enabled                        = true   (env)
stitch_api_key                        = null   (database)   <- NO KEY
nano_banana_enabled                   = true   (env)
```

Two consequences follow directly and neither was visible before this measurement.

**Stitch has no key.** `DesignAssetService`'s fork is
`if (stitch_enabled && stitchClient.hasStitchKey())`. With no key that branch is skipped entirely, so
the design shop never even attempted HTML - it went straight to the image generator, which cannot emit
any. Commit `a958e60` now makes that refusal explicit instead of committing a picture to the project's
live main branch. The real blocker is named: **there is no Stitch key**.

**The main falsification cycle is off.** The two items compiling now came from the design-system and
philosophical tracks, which are on. The main line is silent.

## 4. The architectural problem, stated once

Today **"ready" means "every planned task merged"**. It does not mean "the thing runs". Those are
different predicates, and equality between them is assumed - the same substitution this factory's
repair plan has been removing all day, now at the top level: `merged` standing in for `running`.

The missing predicate already exists. `/launch` + `/healthcheck` answer "does it come up and stay up"
with real evidence. That answer is simply not part of the readiness gate: launch lives beside the flow
as a separate scheduled concern rather than as a **consequence** of being ready.

## 5. What has to become true for the serial loop to close

```
all planned work merged  ->  LAUNCH  ->  healthcheck
        ^                                    |
        |                                    v
   merge PR  <-  tasks  <-  wishlist  <-  falsification against the RUNNING product
```

- **Launch must be a consequence of readiness**, not a parallel concern. Reaching 1.0 should trigger a
  launch attempt, and the launch result should be part of what "delivered" means.
- **Falsification after launch must observe the running thing**, which is what
  `client_runtime_observability` exists for and is already enabled.
- **Every merged improvement must trigger a relaunch**, so the running version tracks main.

## 6. Status

### Done
- Measured the whole launch path end to end (§1) - no inference, source stated.
- Established why the frontend is absent (§2): the product's own compose is backend-only.
- Measured the flag state (§3), which produced two facts nothing else had surfaced.
- `a958e60`: the design generator no longer substitutes an image when implementable HTML is required -
  so a missing Stitch key now reports as itself instead of as a wasted commit to main.

### Not done - and each blocked on something specific
- **Stitch key absent.** Until it is set, the design shop can only report unavailable. Operator action;
  nothing in code can supply it.
- **`falsification_cycle_enabled` is false.** Whether that is deliberate is unknown to me. It gates the
  main improvement loop.
- **Launch is not a consequence of readiness.** No transition exists from "1.0" to "launch attempted".
  This is the central missing piece of §5 and is not yet designed, let alone built.
- **`frontend_not_deployed` is compiling.** Its task will change the product's compose. Until it merges,
  a launch would serve an incomplete product.

### Explicitly not started
Relaunch-on-merge. It only makes sense once a first launch succeeds, and would otherwise be a mechanism
with nothing to act on.
