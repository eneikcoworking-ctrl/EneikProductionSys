# Live Product Plan

Rewritten, never appended. Every number carries its source. Nothing inferred from naming — that rule
was broken twice while writing earlier versions of this file, and both are in §7.

---

## 1. The goal, and the epistemology under it

**The factory keeps a running product under permanent falsification.**

"Finish the product" is not a goal because it is not a state. Product readiness means fitness to a
market; fitness is never proven, only not yet refuted. A completeness metric measures how much of the
**currently known** scope is delivered, and the next falsification enlarges that scope. A ratio falling
after a falsification round is the system working.

This is forced, not chosen. A theory earns its keep by forbidding something observable; a claim that
forbids nothing is not false, it is empty. Falsification against a product that does not run is
therefore quantification over an empty domain — `∀x ∈ ∅` — trivially satisfied and informative about
nothing. The codebase already states it:

> *"a philosophical audit reasoning about a product that can't even start would be reasoning about
> nothing real. TOC: launchability is the constraint; everything else (including philosophical review)
> subordinates to it until it's cleared. Kano Must-Be by construction — not a taste judgment, a
> precondition."*
> — `WishlistSource.product_not_launchable`, 2026-08-11

So the constraint has an **epistemic** ground, not a throughput one. Subordinating to launchability is
not "unblock the bottleneck"; it is "make observation possible at all".

---

## 2. Three axes that must never be merged

| Axis | Question | Category | Owner |
| --- | --- | --- | --- |
| **Scope delivery** | how much of currently known intent is built | quantity over a known set | `ClientDeliverableReadinessService` |
| **Operability** | does it start and stay up | binary state about **now** | `ProductLaunchabilityService`, `ClientRuntimeObservabilityService` |
| **Fitness** | does it match the market | hypothesis under permanent test | philosophical falsification, forever |

Every failure this plan guards against is one axis read as another. Scope read as fitness closes the
flow — **the error that broke this system twice in earlier sessions**, and which appeared in my own
language throughout 2026-08-18.

**Verified in code:** nothing merges them. `acceptProject` is reachable only from `ProjectController`,
a human action. `DELIVERED` names a state and stops nothing. `CHECK_LAUNCHABILITY` is gated on
`activeProject` alone. `DesignShopCycleEntity` is edge-triggered and explicitly anticipates readiness
*"genuinely dropping and rising again after a falsification round adds new features"*.

---

## 3. The constraint, measured to its root

The chain is **complete and working**. Three claims in earlier versions of this file said otherwise;
all three were refuted by measurement and are in §7.

```
ProductLaunchabilityService.checkOnce
    → projects.launchability_checked_at = 2026-08-18 14:15:09      (set; the Phase-0 gate is OPEN)
ClientRuntimeObservabilityService.maybeObserve
    → launcherClient.launch(repoUrl, defaultBranch, slug)          (called; adaptive cadence)
runtime-launcher POST /launch
    → git clone → docker compose up -d --build
client_runtime_observations                              1 row
    observed_at    2026-08-18 14:17:08
    launch_success FALSE
wishlist source=product_not_launchable                   4 rows    (the system filed the work itself)
```

**The full failure text:**

```
object-storage Error failed to resolve reference
  "docker.io/minio/minio:RELEASE.2023-09-20T22-40-07Z": not found
db Error context canceled
```

**At source** — `runtime-launcher-workspace/test-forty-ninth/docker-compose.yml`:

```yaml
services:
  app:             build: …
  backup-cron:     build: …
  db:              image: postgres:15-alpine                        # resolves
  object-storage:  image: minio/minio:RELEASE.2023-09-20T22-40-07Z  # does NOT resolve
```

Two independent defects, both already recognised by the system as work:

- **The image tag has no bearer.** Its *form* is perfect — MinIO really does name releases
  `RELEASE.YYYY-MM-DDTHH-MM-SSZ` — and the referent does not exist. This is
  `RUT_BARKAN_MARKUS_01_ACTUAL_OBJECT_REGISTER` violated in a generated artefact: a name with the right
  shape and no bearer. Quantified actualism says do not quantify over what does not exist; the compose
  quantifies over an image that does not.
- **There is no frontend service at all**, which confirms `frontend_not_deployed` from a second,
  independent direction.

**The constraint is not in the factory. It is one unresolvable image reference in the product's own
compose.**

---

## 4. What is actually wrong, and at which level

**4.1 (product, one instance)** `object-storage` names a nonexistent image. Launch dies at pull, before
anything else is attempted. `db` reports `context canceled` — collateral, not a second fault.

**4.2 (product, one instance)** No frontend service. Even with 4.1 fixed, the launch would serve a
partial product.

**4.3 (factory, systemic)** **Nothing checks that a generated deployment descriptor refers to things
that exist.** `ProductLaunchabilityService` asks only *"is there a compose file"*. The next predicate in
the same family — *"do its references resolve"* — is absent, so the first check of a generated name is
the launch itself, and the failure arrives as a generic `docker compose up failed` rather than as
*"this reference has no bearer"*.

This is the level that matters. 4.1 and 4.2 are instances; 4.3 is why instances of this class keep
arriving.

---

## 5. Deterministic plan

Ordered by dependency, not by preference. Each item states its **precondition**, **what it changes**,
**why it follows**, its **falsifier**, and its **entry mode** on the existing promotion policy.
No item gates on a completeness metric; no item introduces a terminal state.

### 5.1 — Let the two product-level items land

**Precondition:** none. Both are already `compiling`.
**Changes:** the product's compose gains a frontend service and, via `dockerfile_missing_build_stage`,
a build stage that serves it.
**Follows because:** they are ordinary product work already routed through wishlist → compiler → task →
Jules. Touching them by hand would bypass the path whose bypass caused the 2026-08-07..09
self-referential contamination incident, recorded in `ProductLaunchabilityService`'s own javadoc.
**Falsifier:** they merge and the compose still lacks a frontend service → the task's scope was wrong,
not the ordering.
**Mode:** none. Watch only. **Do not launch before they merge** — launching a partial product means
falsifying the wrong object.

### 5.2 — The unresolvable image reference must become work, not a launch failure

**Precondition:** none.
**Changes:** the `minio/minio:RELEASE.2023-09-20T22-40-07Z` reference is replaced by one that resolves.
**Follows because:** this is the single thing killing the launch (§3), and it is product content — so it
belongs on the same wishlist → task path as 5.1, for the same contamination reason. There are already
four `product_not_launchable` items; whether any of them names *this* reference is **unmeasured**, and
that measurement comes first: filing a fifth would repeat the unbounded-repetition failure the
one-per-project dedup guard exists to prevent.
**Falsifier:** the tag resolves on retry → it was a transient registry fault, not a nonexistent bearer,
and 4.3 does not follow from this instance.
**Mode:** none — ordinary product work.

### 5.3 — Give launchability the predicate it is missing: references must have bearers

**Precondition:** 5.2 measured, so the check is written against a known-real instance.
**Changes:** `ProductLaunchabilityService` gains one further precondition — every `image:` in the
product's compose resolves in the registry — recorded like its existing one and routed the same way when
it fails.
**Follows because:** this is the actualist rule (`RUT_BARKAN_MARKUS_01`, score 12.0, defect class *D002
invalid state*) applied to a generated artefact. The service already owns operability preconditions; its
current predicate is simply too shallow, asking whether the descriptor **exists** and not whether what it
**names** exists. It is not a new mechanism — it is completing one.
**Falsifier:** the check never fires again on any project → generated composes do not in fact invent
image names, and 4.3 was a single accident rather than a class.
**Mode:** `observe_only` — record the unresolvable reference and file work, gate nothing.

### 5.4 — Launchability must be read from observations, not from a timestamp

**Precondition:** 5.3 exists, so there is more than one precondition to represent.
**Changes:** "is this launchable **now**" is answered from the latest `ClientRuntimeObservationEntity`,
not from `launchability_checked_at` being non-null.
**Follows because:** §2 classes operability as a state about now.
`ClientRuntimeObservationEntity` is already exactly the right object — `id`, `projectId`, `observedAt`,
`launchSuccess`, append-only — satisfying both
`RUT_BARKAN_MARKUS_01_ACTUAL_OBJECT_REGISTER` (owner, identity, lifecycle) and
`DEREK_PARFIT_01_PERSISTENCE_SNAPSHOT` (*"show stable identifiers and replay evidence across time
points"*). The defect is that a Phase-0 boolean is consulted where a history exists.
**Falsifier:** the derived state never differs from the timestamp in practice → the timestamp was
adequate and this is a defect on paper.
**Mode:** `observe_only` — publish the derived state beside the existing one before anything reads it.

### 5.5 — Relaunch when main changes

**Precondition:** one launch has succeeded. Meaningless before that.
**Changes:** a merge to main triggers a new launch, so the running instance tracks main.
**Follows because:** falsification must observe the **current** product (§1); an instance lagging main
is a stale referent, and reasoning about it is reasoning about something that no longer exists.
**Falsifier:** relaunching yields no new observations → the value is in observing, not in freshness.
**Mode:** deferred until its precondition holds. Building it now would be a mechanism with nothing to
act on.

### 5.6 — `falsification_cycle_enabled` is not to be touched

The engine of the goal is the **philosophical** track and it is enabled — it produced both items now
compiling. That other flag governs a different, internally complex mechanism, and the operator has
directed it be left alone. A flag whose blast radius I cannot measure is not a lever I may pull.
**Mode:** prohibition, not a task.

---

## 6. Dependency order, explicitly

```
5.2 (measure existing product_not_launchable items, then fix the reference)
 └─ 5.1 (frontend + build stage land)
     └─ first successful launch
         ├─ 5.4 (launchability read from observations)
         └─ 5.5 (relaunch on merge)
5.3 (reference-resolution precondition) — independent of the above, but written after 5.2
     so it is built against a measured instance rather than an imagined one
```

Nothing in this order waits on a readiness number. The only sequencing is causal.

---

## 7. Corrections — claims of mine that measurement refuted

| I claimed | Measurement | The error |
| --- | --- | --- |
| "Nobody launches; the constraint is measured and never acted on" | `ClientRuntimeObservabilityService` calls `launcherClient.launch`; one observation row exists | Read a service's *name* as its scope; never opened the caller |
| "Launchability is a verdict about the past, with no state object" | `ClientRuntimeObservationEntity` — append-only, owner, identity, `observedAt` | Concluded a model was absent without looking for it |
| "The main falsification engine is off" | The main engine is the philosophical track, and it is on | Inferred a component's role from a flag's name |
| "`stitch_api_key` is null" | `****9SCw` — set; I read `enabled`, which is null for all secrets | Read absence in the wrong field as absence in the data — the fifth instance that day |
| "Launch should be a consequence of readiness" | The two are separate axes, deliberately (§2) | Proposed merging distinctions an incident had separated |

---

## 8. Forbidden by construction

- No number may be treated as "the product is ready."
- No number may close the flow. The only terminal is `acceptProject`, and it is a human's.
- No launch may wait on a completeness metric — that is waiting for a state that does not exist.
- No falsification track may be switched off as "finished."
- No product content may be written directly into the client repo, bypassing wishlist → task → Jules.
  That bypass is what caused the 2026-08-07..09 self-referential contamination.
