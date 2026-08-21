# Working plan - the product must serve

Compressed 2026-08-21 from 1184 lines. Everything removed is in
`LIVE_PRODUCT_PLAN_2026-08-19_ARCHIVE.md`, whole and unedited - the derivations, the per-session verdict
records, the Gemini removal, the TOC/Six Sigma material. Nothing was deleted. This file holds only what is
needed to act.

---

## 1. Goal

**The delivered product answers a request.** Not "tasks are done", not "reviews merged", not "quality gates
passed" - the product, launched from its own repository, returns 2xx.

Everything below exists to serve that sentence. An item that does not move it belongs in the archive.

**Measured 2026-08-21:** the factory has closed **148 tasks**, merged **144 reviews** and passed **62
quality gates** for a product that has **never once answered**. Every internal number is green and the only
external one is red. That gap is the subject of this plan.

---

## 2. The one acceptance rule

> **A task may not reach `done`, and a PR may not merge, until the product has been observed answering on
> the stack it actually ships with.**

This is not a new mechanism. `ClientRuntimeObservabilityService` already launches the product through its
own `docker-compose.yml` and probes its health port; on 2026-08-21 it captured the exact failing SQL
statement. What is missing is authority: its verdict files a wishlist and nothing else. Acceptance runs past
it.

**Why this rule and not the sixteen defects below.** They are about the factory not jamming. Every one could
be fixed and the product would still not start, because none of them touches the reason. The factory's
definition of done does not include running what it ships. That is one property, not a list.

### What it requires, in order

| # | Change | Why it is this one |
| --- | --- | --- |
| 1 | **Let the factory fix O-1 itself.** The constraint carrying the exact error is already compiling. Bound the patience, not the task: **three cycles.** | If the factory cannot close a one-line error with the diagnosis handed to it, that is the answer about the factory, and it matters more than any fix. |
| 2 | **The runtime observation gates `done`.** `healthStatus` gets the right to block the transition. | The single structural change that makes 2026-08-21 impossible. One lever instead of sixteen patches. |
| 3 | **The product's tests run against PostgreSQL** (Testcontainers, or the compose `db` service). | The same rule from the product's side: an H2-only statement then cannot pass a test. |

---

## 3. O-1 - the reason the product does not serve

Recorded since 2026-08-19 as the first open defect, and re-derived from scratch on 2026-08-21 without
anyone having acted on it in between. **The plan already had the answer at position one.**

**Precise cause, captured from the app container's own log on 2026-08-21 at 16:53:53.882Z:**

```
ERROR: syntax error at or near "ALIAS" at character 8
STATEMENT: CREATE ALIAS IF NOT EXISTS gen_random_uuid FOR "java.util.UUID.randomUUID"
Location: db/migration/V20260816054204525__create_categories_and_tags_schema.sql
```

`CREATE ALIAS` exists only in H2. The compose stack runs `postgres:15-alpine`, where `gen_random_uuid()` is
built in and needs no shim. Flyway fails on the first statement of the first migration, the Spring context
never initialises, the container dies, nothing answers the health port, the observer records
`healthStatus=null`, files `product_not_launchable`, and the cycle repeats identically.

**Correction to the older O-1 text:** it said "the build has no PostgreSQL driver". Measured 2026-08-21 -
`pom.xml` declares `org.postgresql:postgresql`. The driver is present; the killer is the H2-only statement
above.

**Why 144 merged reviews did not catch one line.** The product's `src/test/resources/application.properties`
does not override the datasource, so the whole suite inherits
`spring.datasource.url=jdbc:h2:mem:testdb` from the main config. There is no Testcontainers dependency.
**Every test, review and quality gate validated a configuration that is not the delivered one.** The reviews
were not sloppy; they were pointed at the wrong thing.

---

## 4. Open defects

Ranked by whether they block §2. Full evidence for each is in the archive.

### Blocking the acceptance rule

| id | defect |
| --- | --- |
| **O-1** | the product does not serve - §3 above |
| **O-10** | the instrument has no denominator: nothing counts launcher availability, so 46 consecutive failures were indistinguishable from 46 real negatives. Same shape observed again 2026-08-21: the assembly report stated `<no output on this container's stdout/stderr>` for a container that had written 49 lines including a fatal stack trace. The cause reached the constraint only because PostgreSQL logged the same error itself. If acceptance depends on observation, this instrument stops being advisory. |
| **O-13** | the host cannot hold the factory and a full `mvn test` at once, so verification and operation are mutually exclusive |
| **O-6** | store far larger than its contents: 2517 MiB against ~88 MB live, on the Windows filesystem via WSL. One `/api/settings` read takes 10.3 s, startup 354 s. Cleared 2026-08-21 on the operator's instruction: all four snapshots and the trace log deleted, 4.5 GB of Docker build cache pruned, host free space 8.7 GB -> 14 GB. **There is now no rollback snapshot at all**, so compacting the live store needs a fresh copy taken first, the compaction verified, and only then the copy removed. |

### Not blocking it

| id | defect |
| --- | --- |
| O-3 | Kaizen has no write-side identity |
| O-4 | dead Jules sessions polled forever |
| O-5 | `GET /api/projects/{id}/tree` never answers |
| O-7 | a design asset fetched and missed forever |
| O-8 | `requiresCodeForDelivery` answers a code question about a delivery concept |
| O-9 | the cadence clock counts measurements but limits attempts |
| O-11 | the posterior counts observations, but its object only changes on merge |
| O-12 | three tests red on `main`, unrelated to this session |
| O-14 | the embedding path still routes to Gemini, whose quota is gone |

### Fixed 2026-08-21

| id | defect | commit |
| --- | --- | --- |
| O-15 | the decomposition budget sat on one of two admission paths, so a brief was re-dispatched every ~17 min, merging a PR into the client repository each time | `e6cb928` |
| O-16 | a compiler task could go terminal before its own completion handler ran | `e6cb928` |
| O-17 | the forced-unblock budget - the only thing that closes a stale session - was restored by the verdict our own unblock message provoked; one session held the WIP slot 373 min against a 90 min SLA | `fc4ce0c` |

All three are the same shape: **a rule introduced with one consumer enumerated instead of all of them.**
Live proof that they changed outcomes is not yet in hand; the predictions are written so observation can
refute them.

---

## 5. Shipped this session, with verification

| item | verification |
| --- | --- |
| L-1 route `chatCritical` to the judgment sidecar | confirmed live |
| L-2 bound the deferral when the classifier is unavailable | 899 tests; in `e6cb928`; bytes verified in the jar |
| L-3 retire the stuck task | confirmed live |
| L-5 verify V109's collapse | confirmed live |
| L-6 the subordination lever decides from `soft_gate`, restrictive-only | in `e6cb928`; running at `shadow` |
| L-7 commit the session's work | `e6cb928`, `fc4ce0c`, both pushed |
| L-8 a constraint is cleared by a fresh healthy observation | confirmed live |
| L-9 the observer files `product_not_launchable` itself | fired live; the constraint carried the exact SQL error |
| **L-4 the constraint reaches a running product** | **OPEN - this is §1** |

Verification means bytes in the built jar, not a build's exit code. That rule exists because "deployed" was
claimed twice for a jar that did not contain the change (§6).

---

## 6. Corrections - claims that measurement refuted

Kept in full because the pattern is the point: every one is a claim made from reading a name, a flag or a
memory instead of the thing itself.

| I claimed | Measurement showed | The error |
| --- | --- | --- |
| "Nobody launches; the constraint is never acted on" | `ClientRuntimeObservabilityService` calls the launcher | read a service's role from its name |
| "The main falsification engine is off" | it is the philosophical track, and it is on | inferred a component's role from a flag's name |
| "`stitch_api_key` is null" | `****9SCw` - I had read `enabled` | absence in the wrong field read as absence in data |
| "Deployed" ×2 | the jar did not contain the change | read a build's exit code as proof the image changed |
| "Zero observations" | `docker logs` had returned one line - its own bridge error | read a broken instrument's silence as a fact |
| Announced a restart I never performed | backend was down 47 minutes | reported my own action without verifying it |
| "O-16: `completeWishlistCompilation` is unreachable; `pr_opened` appears zero times" | `reconcileStrandedPrOpenedWorkflows` runs every 60 s and did the conversion | absence of an event in one log window read as proof of impossibility |
| "The O-17 fix is verified" (twice) | first the test never reached the branch; then `@Value` fields are not injected in a unit test, so the ceiling was silently 0 and both cases took the same path | a green test read as evidence without checking it could ever have been red |
| "The app container logged nothing, the report was too early" | the app wrote 49 lines starting at second one; the report ran after them | a plausible mechanism asserted before measuring it |

---

## 7. Forbidden by construction

- No number may be treated as "the product is ready."
- No number may close the flow. The only terminal is `acceptProject`, and it is a human's.
- No launch may wait on a completeness metric.
- No falsification track may be switched off as "finished."
- No product content may be written directly into the client repo, bypassing wishlist -> task -> Jules.
- No claim about the system from memory: read it, or do not say it.
- No measurement without its scope stated.
- No waiting presented as work: if a window must pass, everything independent of it proceeds meanwhile.
- `AutoMergeService` is not to be modified without a separate decision - see the archive for its deadlock
  history.

---

## 8. Held, on command

Move the factory to a server. Details in the archive.
