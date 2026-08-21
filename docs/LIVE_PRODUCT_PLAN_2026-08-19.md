# Live Product Plan

Rewritten, never appended. This file was rewritten in full on 2026-08-20 after growing to nineteen
appended sections with three competing work orders and four stale measurement blocks - exactly the
accumulation its own header forbids. Withdrawn claims live as one line in §12, never as sections. Every
number carries its source; nothing is inferred from naming.

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
> subordinates to it until it's cleared. Kano Must-Be by construction - not a taste judgment, a
> precondition."* - `WishlistSource.product_not_launchable`, 2026-08-11

The constraint therefore has an **epistemic** ground, not a throughput one.

### 1.1 The goal stated so it can only be met or not met

**One row exists in `client_runtime_observations` for the ACTIVE project with `launch_success = true`
and a non-null `health_status_code` returned by the product's own health endpoint, written by the
factory's own launcher, with no human step in the launch.**

Not "the product is ready". Not "the scope is complete". Not "the blocker is fixed". One observation,
made by the factory, of the product answering for itself. It is either in the table or it is not.

It belongs to all three value levels at once (§8): the first unit of product value, proof that delivery
reached reality rather than `main`, and factory value because the observation was produced without me.

**It is not terminal.** The observation is a not-yet-refuted claim, refutable by the next one.
Falsification cycles continue after it, forever. Reaching it opens the loop; it closes nothing.

---

## 2. Three axes that must never be merged

| Axis | Question | Category | Owner |
| --- | --- | --- | --- |
| **Scope delivery** | how much of currently known intent is built | quantity over a known set | `ClientDeliverableReadinessService` |
| **Operability** | does it start and stay up | binary state about **now** | `ProductLaunchabilityService`, `ClientRuntimeObservabilityService` |
| **Fitness** | does it match the market | hypothesis under permanent test | philosophical falsification, forever |

Scope read as fitness closes the flow - **the error that broke this system twice in earlier sessions**.
Verified in code: `acceptProject` is reachable only from `ProjectController`, a human action;
`DELIVERED` names a state and stops nothing; `CHECK_LAUNCHABILITY` is gated on `activeProject` alone;
`DesignShopCycleEntity` explicitly anticipates readiness *"dropping and rising again after a
falsification round adds new features"*.

---

## 3. Three contexts - the levels I work at

Operator directive, encoded in `KaizenProposal.KaizenCategory` whose comment records it verbatim:
*"clearly marked as a product improvement, not mixed into the factory list."*

| Level | What it is | Who may act | What I do here |
| --- | --- | --- | --- |
| **FACTORY** | EneikProductionSys' own parameters and source | me, review-only for source | write code, measure, fix its defects |
| **DELIVERY** | wishlist -> compiler -> task -> Jules -> PR -> merge -> launch | me, in factory code only | fix how the process reports, decides and sequences |
| **PRODUCT** | the client repository's content and runtime | Jules, through the ordinary path | **never touch directly** - the 2026-08-07..09 contamination came from exactly that bypass |

Every item below names all three: where the change lives, where the defect belongs, what it is about.
They are frequently different.

### 3.1 Scope rule - only the active project

There are 22 projects. Exactly one is `active`: `41af381d test-forty-ninth`. The rest are `accepted`
(a human ended the engagement) or `frozen` (not ticked, so nothing recovers or advances them - correct,
not a defect).

**Rows from a non-active project are not evidence about the factory.** Every query that reads
project-scoped data filters to `status = active`, or states in the same breath why a dead project is
being read on purpose. A measurement whose scope is not stated is not a measurement.

---

## 4. State, measured 2026-08-21 from the stopped database

Read directly out of `data/eneik_db.mv.db` with the factory down, scoped to the active project
`41af381d test-forty-ninth`. The previous block in this section was a dashboard snapshot taken at
2026-08-20 11:30Z and it was wrong in three ways; the corrections are in §14.

```
client_runtime_observations, project 41af381d
  total 56 · launch_success=TRUE 8 · health_status_code NOT NULL 0 · instrument_failure 46
  last row that actually observed the product   2026-08-20 11:39:59

observations per hour
  05..11h   1 · 1 · 1 · 1 · 1 · 1 · 1      (0 instrument failures)
  12h      17                              (17 instrument failures)
  13h      28                              (28 instrument failures)
  13:34:16  last row of any kind - the factory was stopped

posterior over the 10 rows that observed the product   Beta(1,11)
  q(0.025) = 0.002299 · 24h x q = 3.3 min -> clamped to the 1h floor
host   data/eneik_db.mv.db 2 339 028 992 bytes (2231 MiB), from 1920 MiB the day before
```

**`health_status_code` is null in all 56 rows.** Eight of them launched the stack; not one produced a
product that answered for itself. §1.1's goal has therefore never been half-met in the sense of health -
only the launch half has ever been true, and only ten times has the product been looked at at all.

**Between 11:40 and 13:34 the factory made 45 launcher calls that reached nothing.** Every one of them
carries `runtime-launcher unreachable: I/O error on POST http://runtime-launcher:8091/launch`. The
observation rate went from 1/hour to 28/hour at exactly the moment the instrument stopped answering.
That is not a coincidence and it is not the launcher's fault - §6 O-9 is the mechanism, and it is a
defect this plan's own item 5.5 introduced.

---

## 5. Shipped, with verification

Every row was verified by measurement, not by a build's exit code.

| # | Change | Levels (change / defect / about) | Verified |
| --- | --- | --- | --- |
| 5.1 | `frontend_not_deployed` landed; the Dockerfile builds and serves the frontend | - / product / product | source on main |
| 5.2 | `product_not_launchable` carries the observed cause, not just "not healthy" | factory / factory / product | 21/21, deployed |
| 5.3 | launchability gate asks **existence** (`mergedDeliverables > 0`), not completeness | factory / delivery / product | 26/26, deployed |
| 5.4 | a failed launch becomes an **evidence node**, not only a stored observation | factory / delivery / product | live: launch-related nodes in the coherence graph 0 -> 1, node `d545a8a7` |
| 5.5 | `V104` - an unanswered launch call is a **missing** observation, not a negative one | factory / factory / product | live: the health summary switched from the 16:42 instrument failure to the 09:12 real one, width 0.596 -> 0.699 |
| 5.6 | cadence keyed on the credible interval's **lower bound**, not its width | factory / delivery / product | 27/27; live: next check 09:11Z -> 05:34Z, fired 90 s after deploy |
| 5.7 | a declined delivery is **routed**, not left standing (`d6e6c17`, `a86254e`) | factory / delivery / delivery | live: `pending_review` 1 -> 0, `SYSTEM_STALLED` cleared, the 4-per-minute loop gone (§13.1) |
| 5.8 | the launcher names the failing service and quotes its own container log | factory / factory / product | live: observations now carry `assembly: ...` |
| 5.9 | `product_not_launchable` addressed to `BARCAN-TAG-00` explicitly; TAG-00 rescoped to the assembly | factory / delivery / product | 25/25, deployed |
| 5.10 | a depleted Gemini quota is named as a billing state, not an unreadable answer | factory / factory / factory | 23/23, deployed |
| 5.11 | Gemini project observer switched **off** | factory / factory / factory | flag `gemini_project_observer_enabled=false`, verified in the database |
| 5.12 | backend heap 1536m -> 1024m | factory / factory / factory | `Xmx1024m` read from the running process; free memory 543 -> 1741 MB |

---

## 6. Open defects, ranked by what they cost

| # | Defect | Measured | Level |
| --- | --- | --- | --- |
| O-1 | product does not serve: compose says PostgreSQL, config says H2, build has no PostgreSQL driver | `health_status_code` null in all 56 observations, 8 successful launches included | product |
| O-9 | the cadence clock counts measurements but limits attempts, so it stops limiting exactly when the instrument fails | 1/hour -> 28/hour at 11:40 when the launcher went unreachable; 45 calls into nothing | factory |
| O-10 | the instrument has no denominator - nothing counts launcher availability, so 46 consecutive failures produced zero findings | 46 rows written, posterior unchanged, dashboard clean | factory |
| O-11 | the posterior counts observations, but the object it is a belief about only changes on merge | 7 identical readings of one unchanged artifact between 05h and 11h, each updating Beta | factory |
| O-13 | the host cannot hold the factory and a full `mvn test` at once, so verification and operation are serialised | 4 containers ~2.3 GB + a 2 GB test JVM against 3.9 GB total; measured 583 MB free before the run was killed | factory |
| O-12 | three tests are red on `main`, unrelated to this session | `ProjectFlowServiceTest` x2, `DesignSystemFalsificationServiceTest` x1; reproduced with today's changes reverted | factory |
| O-16 | **the cause under O-15**: a wishlist-compiler task can be driven terminal by `AutoMergeService`'s poka-yoke merge reconciliation before its session is ever seen in `pr_opened`, and `completeWishlistCompilation` - the ONLY place a compiled wishlist becomes `converted_to_task` or honestly `dismissed` - is reached exclusively from `handlePrOpenedWorkflowClaimed`. The compile genuinely happened and its plan is merged on `main`; nothing ever reads it | `completeWishlistCompilation` logged nothing at all across the whole backend log, and the string `pr_opened` appears zero times, while both compiler tasks reached `done` via `Poka-yoke: reconciled merged outcome ... repaired status=true` and their sessions were then closed *because the task is already terminal*. `closeSessionForTerminalTask` already repairs exactly this shape one layer down - it releases a dead persistent-worker carrier's stranded `finalizing` wishlists - and the one-shot compiler task is the same shape with no such handling | factory |
| O-15 | a compiler task can reach `done` by a route that never runs its own completion handler, so its wishlist is left in `compiling`, recovered to `pending`, and dispatched again - forever, opening and merging a fresh PR into the CLIENT repository on every turn | Measured twice in a row on the same row `875cfb77` (2026-08-21): dispatch 12:17:38 -> `done` 12:24:24 via `AutoMergeService` poka-yoke (`repaired status=true`, PR #152 merged) -> session closed 12:24:46 *because the task is already terminal* -> `[RECOVERY] ... -> pending` 12:25:53 -> re-dispatch 12:34:34 -> `done` 12:41:32 (PR #153) -> `pending` 12:42:56. Period ~17 min. **Why the existing bound does not bind:** F42 already put a decreasing measure on this loop - `mu = WISHLIST_COMPILE_ATTEMPT_BUDGET - compileAttempts` - after one brief was decomposed 16 times. It was added to ONE admission path (`orchestrate`, line 649, together with the `alreadyDecomposed` skip). The iteration-admission path that actually re-dispatched here checks only the WIP limits and the 900 s cooldown, and a cooldown is not a decreasing quantity; the one-shot compiler dispatch writes `lastCompileDispatchedAt` but never increments `compileAttempts`, so on that route mu is constant and the budget can never be reached. ACP-103 again: the rule was introduced with one consumer enumerated instead of all of them | factory |
| O-14 | the embedding path still routes to Gemini, whose quota is gone, and the D3 duplicate-content lever therefore fails **silent and open** - it reports nothing found, which is indistinguishable from having found nothing | `ML service embed call failed: 502 Bad Gateway: "Gemini embedding call failed: HTTP Error 429: Too Many Requests"`, 3 per orchestration tick, 2026-08-21 12:17Z. The LIVE duplicate detector is unaffected - it is `duplicateContent()`, exact-key based, no embeddings - so this is not a flow stoppage; what is dead is the lever's evidence supply, so D3 can never accumulate the samples its own promotion ladder requires and stays at `observe_only` forever | factory |
| O-3 | Kaizen has no write-side identity | 347 rows carrying **10** distinct `(category, target_component)` pairs | factory |
| O-4 | dead Jules sessions polled forever | 52 `404`/hour; 3 of 4 `pr_opened` sessions answer 404 to their own account key | factory |
| O-5 | `GET /api/projects/{id}/tree` never answers | 90 s, `http=000` | factory |
| O-6 | store far larger than its contents, and it now costs the factory its responsiveness | 2231 MiB file, ~88 MB live; grew 311 MiB in one day, and O-9 is a named contributor. **Re-measured 2026-08-21 14:20Z: 2517 MiB**, on the Windows filesystem via WSL. Consequences measured the same minute, not inferred: startup 354 s (was 53 s that morning), `/actuator/health` 6 s, one `/api/settings` read 10.3 s, host load average 15.9 while the containers together used ~50% CPU - the rest is I/O wait. `data/` holds 6.6 GB in total, ~4 GB of it stale snapshots kept deliberately by the operator | factory |
| O-7 | a design asset fetched and missed forever | 14/hour, `design/approved/20260818165327-mockup/mockup.html` absent on main | delivery |
| O-8 | `requiresCodeForDelivery` answers a code question about a delivery concept | 5 phantom deliveries measured; no bearer for the content case yet | delivery |

O-2 is closed: the invariant status vector is persisted and its transitions were verified live (§13.3).

**A second consequence of O-13, learned by causing it.** Two `mvn` runs against the same bind-mounted
`target/` are a race, not parallelism: one rewrites `target/test-classes` while the other's surefire reads
it, and the second reports `No tests matching pattern` - a message that reads like a tooling quirk and is
not one. Measured 2026-08-21: a run left going for 39 minutes, holding 654 MB, was still recompiling when
the next one started. Serialise runs, and check `docker ps` before starting one - the host has room for
exactly one.

**O-13 is why §16 is not a convenience.** Every verification cycle in this session required stopping the
factory first, and each stop/start pair costs about four minutes of startup plus whatever the factory did
not do meanwhile. It is not a blocker for §1.1 - it is a tax on every measurement, paid repeatedly.
Measured the same evening: a graceful stop also shrank the store 2 339 028 992 -> 2 248 667 136 bytes,
because MVStore only reclaims pages on a clean close - so the tax is partly refunded, and a hard kill
would forfeit it (O-6).

**O-9, O-10 and O-11 are one defect seen three times**, and §9.3 states it once. They are listed
separately because they are fixed in three different places, not because they are three ideas.

**O-12 is stated here rather than fixed silently.** Three red tests on `main` mean the suite has not been
a gate for at least some time, and a suite that is not a gate cannot refute anything - which makes every
"68/68" and "27/27" in §12 a claim about a subset rather than about the build. It was found by reverting
this session's changes and re-running, so its independence from today's work is measured, not assumed.

---

## 7. TOC as a procedure, not a catalogue

Theory of Constraints is five steps. The factory performs one and a fragment of another: it *identifies*
(`product_not_launchable` is filed) and one consumer *subordinates* (philosophical review defers).
Exploit, elevate and repeat are absent.

**The category error.** A found constraint is filed as an item in the ordinary queue - the same pool as
coverage gaps and client requests - and waits its turn. `WishlistEntity` has no priority field;
`leanValue` is the only value-bearing column and nothing orders selection by it. But a constraint is not
a high-priority item: it is what the throughput of the whole is limited by, and everything else is slack.

**The proposal.** Subordination is a condition in the policy, not an `if` inside one service.
`OperationalPolicyService.authorize(action, project)` is already the single place that decides what may
run and already gates on states (`FROZEN`, `ACCEPTED`, `GITHUB_RATE_LIMITED`). A constraint belongs
there, in the same shape: *while a constraint is open for a project, only actions that serve it are
authorised.* That supplies exploit, subordinate, elevate and repeat at once, with one predicate rather
than sixteen bespoke rules.

Two constraints on the design, both established from data:

- **A constraint is cleared by a fresh healthy observation, not by a status.** `existsByProjectIdAndSource`
  blocks re-filing regardless of status, so a dismissed constraint would permanently prevent re-filing
  while the product stays broken. Only an observation speaks about now.
- **Subordination cannot gate dispatch alone.** A constraint can stall in `compiling` and never reach
  dispatch, so a dispatch-only gate would subordinate nothing.

And the rule that must not be lost: a constraint lives at one of the three levels of §3, and a product
constraint must not stop factory kaizen, nor the reverse. The predicate carries the level or it silences
the wrong work.

**Not built.** The stated precondition - "subordinating everything to a constraint the system cannot yet
observe reliably would subordinate it to a guess" - **is now met**: O-9 and O-10 are fixed and verified
live (§13.4), and the constraint exists as a row carrying the product's own failure (§13.5). What blocks
it is no longer epistemic, only unbuilt.

One flow defect this work exposed, recorded here rather than fixed in passing: the constraint is filed
**only** inside `executePhilosophicalCycleForProject`, behind five gates that all belong to philosophical
review, on a cron that fires every two days - and its one accelerator, the Gemini observer, is switched
off (5.11). So the identification of the constraint is a side effect of the very process that is supposed
to subordinate to it. Measured: 95 wishlist rows for the active project and, until 2026-08-21, zero
`product_not_launchable` - while the product had never once answered a health check. That inversion is
what §7's policy predicate would remove, and it is the reason to build it.

---

## 8. The three values, as mathematics on existing machinery

Value is not one quantity and it is not code. There are three, one per context (§3), and mixing any two
is a category error. Each has a different bearer, a different declared denominator, and a different way
of being refuted.

| | Product value | Delivery value | Factory value |
| --- | --- | --- | --- |
| **Bearer** | the running instance | the engagement with the client | the factory itself |
| **Counts** | capabilities a user can exercise | brief items answered by a real artifact | requirements carried to product value with no operator |
| **Denominator** | capabilities the product claims | items in the client's brief | requirements attempted |
| **Refuted by** | an observation where the capability fails | a brief item with no artifact answering it | any human intervention |
| **Code vs content** | **the distinction vanishes** - a page with real copy works, one with placeholder text does not | **the distinction matters** - a copywriter's markdown is delivery even if it never becomes behaviour | irrelevant - what is counted is who moved it |

### 8.1 What is already load-bearing

| Built | What it gives the value question |
| --- | --- |
| `OperationalTruthService` (651 lines) | the read-only value layer the math document specifies, with the invariant catalogue already computed in observe/warn status |
| Evidence Algebra 0-5 | a declared strength order: merged PR = 5, runtime check = 3, agent prose = 1, generated filler = 0 (negative) |
| `ClientDeliverableReadinessService.Readiness` | `ratio`, `completeFeatures/totalFeatures`, with denominator exclusions enumerated per invariant 8 |
| `BetaPosterior` | exact conjugate Beta-Bernoulli with real Beta quantiles |
| `RuntimeHealthShiftDetector` | exact two-sided binomial test against a baseline |
| `LeverStage` + `LeverPromotionService` + 1185 observations | the promotion policy, implemented: `OBSERVE_ONLY -> WARN_ONLY -> SOFT_GATE -> HARD_GATE -> AUTO_REMEDIATE` |
| `WishlistSource.coverage_gap` | the audit asking whether the decomposition covers the brief |
| `GeminiContextService` | exact cosine similarity with an Otsu-style dynamic floor - retrieval is local linear algebra |

The math document already states the principle written up as `ACP-102`: *"limits of substitutivity:
`task done` cannot be substituted for `value delivered`"*.

### 8.2 Product value

Today's measure is the degenerate case: one Beta(α, β) over a single Bernoulli - did the stack boot and
answer `/health`. That is |C| = 1 where C is the set of capabilities the product claims. The general form
keeps one posterior per capability:

    V_p = |{ c ∈ C : LCB_0.95(c) ≥ θ }|,   θ declared

- **Popper.** LCB < 1 for every finite sample, so no capability is ever proven - only not yet refuted. A
  single failing observation lowers LCB_c and the capability leaves the count. The measure can fall.
- **Invariant 8.** |C| is declared from the client's brief with exclusions enumerated, not from the
  factory's own decomposition.
- **Invariant 12.** The witness is the launcher - external to the agent that wrote the code, which is
  what invariant 12 demands and what its own incident was about.
- **Lower bound, not mean.** The mean rewards ignorance; the lower bound makes confidence something
  evidence has to earn. Same reason 5.6 keys the cadence there.

Today, read from the database 2026-08-21: |C| = 1, 57 attempts of which 10 observed the product, all unhealthy - Beta(1,11), mean 0.0833, LCB ≈ 0.0023. **V_p = 0.**

### 8.3 Delivery value

Both halves exist; neither is a new measure:

    V_d      = ratio × coverage
    ratio    = mergedDeliverables / totalDeliverables            (built, invariant-8 clean)
    coverage = brief items with ≥ 1 deliverable / brief items    (audited, never expressed as a number)

`ratio` answers *did we merge what we planned*. The `coverage_gap` audit answers *did we plan what was
asked* - it runs and produces findings, but not a fraction. Until it does, `ratio = 1.0` reads as
delivery when it only ever meant "we finished what we set ourselves", which is self-attestation
(invariant 12) at the level of scope. The one defect inside `ratio` is O-8.

### 8.4 Factory value

    V_f = |requirements that reached V_p with zero operator steps| / |requirements attempted|

The machinery exists - `LeverPromotionService` already tracks Beta-Bernoulli evidence per lever. What
does not exist is the numerator's precondition: **nothing records that a human acted.** Restarts,
reopened wishlists, edited settings leave no row attributable to the requirement they touched. Until that
exists V_f cannot be computed, and every autonomy claim about this factory is unfalsifiable.

---

## 9. Patterns established this session

### 9.1 ACP-102 - Criterion Is Not The Concept

`BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE`, Frege, sense and reference. Written into
`docs/philosopher-patterns/00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md`.

A concept the system reasons with - *delivered*, *done*, *healthy* - is operationalised by a concrete
test. The test and the concept agree **only over the class of bearers the test was calibrated on**.
Outside it they come apart, and because the test keeps returning a clean boolean, nothing announces that
it is now answering a different question. A criterion may be substituted for its concept only where the
class of bearers is declared and the bearer belongs to it.

Measured instances, all the same shape:
- `requiresCodeForDelivery` answers a code question about a delivery concept (O-8)
- an unanswered launch call recorded as a failed product (5.5)
- a depleted API quota recorded as an unreadable answer (5.10)
- "the launch call returned success" standing for "the product launched"

The criterion was right for 94 of 99 cases, which is exactly why it survived: a criterion that is nearly
always co-extensional is the hardest kind to catch.

### 9.2 Content is a set of refutable claims, not an artifact that exists

"Is there text in this field" is the content analogue of "does the PR contain code" - satisfied by lorem
ipsum, by generated filler, and by a title naming a feature the product does not have. The Evidence
Algebra already grades that at **0, negative evidence**. No check enforces it.

Content is a set of **claims about the product**, each checkable against the product:

- **Reference** (Frege, TAG-08): every substantive noun phrase in a heading, `<title>` or meta
  description must have a bearer - a declared capability, a real route, a real entity. A `<title>` naming
  a capability the product does not expose is a name with perfect form and no bearer, structurally the
  same defect as the MinIO tag.
- **Felicity** (Austin, the anchor of ACP-101): a call to action is a performative. "Start free trial"
  commits the product to having a trial; its felicity condition is that the act it names can be performed.
- **Actuality** (Barcan Marcus, TAG-01): content must quantify over actual objects. Placeholder copy
  quantifies over an empty domain - the same `∀x ∈ ∅` structure as falsifying a product that does not
  start.

**Relevance is not a property, it is a posterior.** A claim true when written can be false later. Content
gets exactly the treatment §8.2 gives capabilities: one Beta per claim, refuted when the page and the
product disagree. A content claim **is** a capability in the observable sense - no second measure, no
second vocabulary.

No fourteenth role. Content is a delivery **artifact kind**, which is what ACP-102 says a role declares:
TAG-11 authors it, TAG-12's contract supplies the referents, TAG-06 verifies via the existing
`VerificationEvidenceGate`, the launcher refutes.

**What must not be built:** keyword density, readability indices, SEO checklists. None is a claim about
the product and none can be refuted by observing it. They measure the text against itself - invariant 12
with extra arithmetic.

### 9.3 ACP-103 - Reclassification Without Census

`BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE`, the same anchor as ACP-102 and its exact dual.

ACP-102 is about a criterion silently answering a different question than the concept it stands for.
ACP-103 is what happens **after you fix one**. When the system learns that a record means something
other than it thought, the record's meaning changes for **every** reader at once. Fixing the reader that
prompted the discovery leaves the others reading the old meaning - and they now read it wrongly, silently,
with the same clean booleans as before. A reclassification is not complete until its consumers are
enumerated and each one is asked which accounting it belongs to.

The measured instance is item 5.5 of this plan, my own change.

`V104` established that a row whose launcher never answered is a fact about the **instrument**, not about
the product. That is right, and it fixed the posterior. `lastRealObservation` was written to carry the
new classification, and its javadoc states the reason: *"The cadence clock must run from when the product
was last really looked at, not from when the instrument last failed to look."* That sentence is the
defect. The clock does not answer *when was the product last looked at*; it answers *when did we last
spend an attempt*, because what it governs is the rate of attempts.

The census, run 2026-08-21 across every reader of the classification:

| Site | The question it actually answers | Belongs to |
| --- | --- | --- |
| `ClientRuntimeObservabilityService:111` cadence clock | when did we last **attempt** | the instrument - **was reading product rows** |
| `:156` the write | what is this row a fact about | the classification itself |
| `:228` shift-detector input | how has the **product's** health moved | the product |
| `:290` frontend summary | how is the **product** | the product |
| `:330` `posteriorFrom` | belief about the **product** | the product |
| `TocSubordinationLever:123` | is the **product** constraint open | the product |

Five of six ask about the product and were correct. Exactly one asks about the instrument, and it was
the one left reading the product's rows. The consequence is not a rounding error: a rate limiter whose
clock advances only on successful measurements stops limiting precisely when measurement stops working.

    attempts per unit time  =  1 / max(floor, base x LCB)   while the instrument answers
                            =  the tick rate                while it does not

Measured on this project: **1/hour to 28/hour**, a 28x amplification arriving exactly when the thing
being called was least able to serve it. Positive feedback, and invisible - the rows were written,
correctly marked, correctly excluded from the posterior, and therefore absent from every number a human
or an agent would have looked at.

**The general rule this adds to the corpus.** A record carries a declared subject (invariant 8: state the
denominator). ACP-103 says the declaration is only half of it - each **consumer** of the record must
declare which accounting it is doing, and a consumer whose question is about a different bearer than the
records it reads is broken no matter how correct the records are. When a subject is reclassified, the
census is part of the change, not follow-up work.

**The census must cover readers of the RECORD, not readers of the FIELD.** This was learned by getting
it wrong the same evening. The census above enumerated every reader of `isInstrumentFailure` and
`lastRealObservation` - and `FalsificationCycleService.latestErrorText` is neither: it reads
`recentObservations().get(0)` and never touches the flag, so the grep that produced the table did not see
it. It took the newest row of any kind while its caller's other input, `lastObservationHealthy`, is
computed from the newest real one, and its own javadoc says it exists "precisely to stop a claim and its
witness from drifting apart". Measured live at 23:25Z: the filed constraint cited
`runtime-launcher unreachable: I/O error on POST http://runtime-launcher:8091/launch` - this factory's own
sidecar - as the evidence for what to fix in the CLIENT's repository, where no such component exists.
A census keyed on the classification's field is a census of the wrong population.

**What must not be concluded:** that instrument rows should go back into the posterior. They must not -
V104 is right about the product's accounting. The instrument needs its own denominator (O-10), which is
the second thing the census makes visible: nothing at all counts launcher availability, so a component
that failed 46 consecutive times produced no finding, no lever observation and no invariant transition.
An unmeasured bearer cannot be refuted, and by §1 that makes it unteachable.
---

## 10. The assembly has no owner

### 10.1 What happened, to the minute

`2026-08-16 05:58:50` - task *"feat(db): Add schema for search analytics events"* creates `pom.xml` and
`application.properties` with the H2 driver. Correct for its brief: a schema needs a database, in-memory
H2 is the standard scaffold.

`2026-08-16 06:10:38` - twelve minutes later, task *"Configure automated backup jobs and alerting
mechanism"* creates `docker-compose.yml` declaring `postgres:15-alpine` and passing
`SPRING_DATASOURCE_URL`. Correct for its brief: backups cannot be demonstrated against an in-memory
database.

Neither wrote a wrong line. The second overrode the *URL* and not the *driver*, and never added the
PostgreSQL dependency - because its subject was backups. **The defect lives between two correctly
executed slices.**

### 10.2 The factory can raise any stack, and must not choose one

The launcher runs `docker compose up` on the product's own file: already stack-agnostic, and that is the
right design. Which datastore is right is a **product** question belonging to the specification - for one
brief H2 is the better answer, for another PostgreSQL is. "Always PostgreSQL" would be a patch. What
holds universally:

> The runtime contract names every service the product runs against. `docker-compose.yml`, the build
> manifest and the application configuration are **consequences** of that declaration, not independent
> decisions.

The artifact exists: `docs/architecture/adr-002-runtime-contract.md`, produced at ARCHITECTURE (order 20,
`BARCAN-TAG-01`). It fixes both code boundaries and the install/run/test commands. It names **no
datastore at all**. The place is right, the stage is right, the role is right; half the content is
missing. `TAG-01` is `ACTUALIST-OBJECT` - a datastore no artifact declares is not an actual object.

### 10.3 Why the integration role never runs

`BARCAN-TAG-00` (CODE-GUARDIAN, INTEGRATION, order 70) has **0 tasks on this project and 4 across the
factory's whole history of 1375**. Every other role worked.

`TechnicalLeadCompiler.targetRoleForWishlist` takes the role from the wishlist's tag, its DoD, or keyword
inference. Wishlists come from client intent, Gemini, coverage gaps and falsification. **Integration is
nobody's requirement** - it is a property of the assembly, and a requirement-pulled decomposition cannot
produce it. Not a bug: a construction.

Two confirmations that the role was also mis-defined: `product_not_launchable` named no role and fell to
keyword inference, sending the MinIO blocker to OPERATIONS (a symbol fixed, not an assembly); and TAG-00's
file scope was `<Feature>IntegrationService.java` - the compiler believed integration means writing a
class. Both corrected in 5.9.

### 10.4 Three absences at three stages

| Stage | Whose work | What is absent |
| --- | --- | --- |
| ARCHITECTURE (20), TAG-01 | decide and declare the datastores in the runtime contract | the contract covers code only |
| OPERATIONS (50), TAG-05 | build compose **from** the contract | built it from nothing |
| INTEGRATION (70), TAG-00 | check the artifacts agree with the contract | never dispatched; now addressed and rescoped (5.9) |

The runtime observation is the last line, and it is the only one that fired - at the most expensive point.

---

## 11. Leaving Gemini entirely

### 11.1 There is no machine learning in this factory

The `ml` service is a 547-line HTTP proxy to the Gemini API. Its entire dependency list is `fastapi`,
`uvicorn`, `pydantic` - no sklearn, no torch, no numpy. Nothing is trained. Even `predict/bottleneck`
asks Gemini to score WIP and cycle time and falls back on exact arithmetic for any failure:

    risk = (min(wip/MAX_WIP, 1) + min(cycle/SLA, 1)) / 2

with a logistic variant beside it. The mathematics is written and always runs; the model on top of it
cannot be more correct than the formula it falls back to.

### 11.2 The five call sites

| Site | What it does | Replacement | Net |
| --- | --- | --- | --- |
| `GeminiProjectObserverService` | hourly narrative observation | **dropped (5.11).** The path it served is now mechanism | 24 paid calls/day removed; it produced 3 actions in 3 days |
| `JulesDispatchService.reviewPr` | PR review | **already removed** 2026-07-25 after a cost incident | nothing to do |
| `JulesDispatchService.chatCritical` | is a silent session looping or waiting? | rules + escalate a *repeated* misclassification | small loss of precision, no loss of mechanism |
| `OpsAuditorService.chatCritical` | evidence-only auditor: 2 evidence kinds in, 3 decisions out, may ABSTAIN | **move to the subscription agent** - it already is factory-level judgment | same function, **on the subscription already paid for** - see the correction below |
| `GeminiContextService.embed` | vectors for retrieval | **already paid**: 1525 chunks, 111 sources indexed; retrieval is local cosine similarity | degrades only for corpus files added later |

### 11.3 Kaizen is not being reduced - its bookkeeping is being fixed

347 rows, **10 distinct `(category, target_component)` pairs**: 34:1 storage to signal.
`getDeduplicatedProposals` already keys on exactly that pair, but **at read time only**. The operator's
view is correct; the table is not, and those rows are part of O-6.

Moving that identity to the write gives kaizen three things it does not have:

- **a recurrence count** - "this problem occurred 79 times" is a measure of severity, today invisible
  because every recurrence is indistinguishable from a new problem;
- **refutability** - after applying a micro-step, a counter that keeps rising says the improvement did not
  hold. Today that cannot be known. A non-refutable improvement is not an improvement;
- **standardisation that means something** - SDCA requires proving the gain was held, and "the counter
  stopped rising since X" is that proof.

Independent of Gemini: `FactorySelfHealthService` authored the 79-row database-health repetition itself.

### 11.4 The subscription agent - what it replaces, and what it turned out to be

**Correction, 2026-08-21.** The row above said "flat cost", and the first build did not deliver it: it
called `/v1/messages` with an `sk-ant-` key, which is metered per token and needs a balance on an API
account. That is not a subscription, and replacing a metered API whose credit had run out (Gemini) with
another metered API is not an improvement. The operator was right to call it what it was.

What was missed is that the subscription is **already on this machine**: Claude Code is installed at
`/usr/local/bin/claude` and authenticated against the operator's own account. Verified by a real call,
not by reading documentation - a Linux container with `@anthropic-ai/claude-code` installed via npm and
the existing OAuth credential mounted returned a schema-bounded verdict:

```
structured_output: {"verdict":"ABSTAIN","reason":"linux container probe"}
provider: firstParty   canonicalModel: claude-opus-5
```

No key, no balance, and no WSL interop - which was emitting `accept4 failed 110` on `.exe` calls that
same hour, and has taken Docker down with it before.

Two costs stated plainly, so "flat" is not claimed twice. A cold invocation loads Claude Code's own
context first: 6-14k tokens before the prompt is read. `--bare` would cut that and cannot be used - its
own help says "OAuth and keychain are never read", so it works only with an API key, which is the thing
being avoided. On a subscription this is limit consumption rather than money, and at the measured 2.9
wakeups a day it is accepted.

It replaces **one** thing: `OpsAuditorService`'s judgment, plus the factory-level refutations nobody
acts on.

**The design in this section was wrong about where it lives, and the code corrected it.** It was written
as an external process polling five HTTP endpoints. Built, it is `FactoryJudgmentService` inside the
factory, reading `invariant_status_changes` directly. The reason is the contract with the operator, not
convenience: an external process needs its own host, its own scheduler, its own credential store and its
own deploy - four operator steps, and by §8.4 a path that needs the operator scores zero factory value by
construction, permanently. In-process, the operator supplies a key and nothing else, exactly as
`jules_api_key` has worked since that pattern began.

**The cost gate is the order of operations, and it survived unchanged.** The cycle's first act is one
indexed query: are there unjudged factory-level refutations? If not it returns having invoked no model at
all. The observer called the model *to find out whether there was news*; this asks the factory, and wakes
judgment only when the answer is yes.

**Its output is bounded by a schema on the request, not by a request inside the prompt.**
a JSON schema travelling with the request (`--json-schema`), `additionalProperties: false` and a
two-valued verdict enum: ABSTAIN, or one factory-level finding filed into the sink the factory already
has. Never prose, never a journal entry,
never product work. ACP-102 applies to the agent as much as to anything it reads - an answer that merely
looks like a verdict is not one.

**Two non-answers, kept apart.** An endpoint that cannot be reached is a fact about the instrument and
leaves the transition unjudged for retry; an answer that is declined or off-schema is a fact about that
input and will be declined identically forever, so the row is marked read. Collapsing them made one row
an absorbing state at the head of a FIFO queue - the same shape as O-9, caught in review before it ran.
And because a quiet drain is worse than a visible block, a cycle that rules on nothing files a finding
against the judgment layer itself.

### 11.5 Wake on refutation, not on change - measured

Waking on every merge is 40 wakeups/day, which reproduces polling with a different clock. Confirmations
are free and infinite; a theory earns attention when it is **refuted**.

Reconstructed from `TRUST_SIGNAL_SNAPSHOTS` - 74 snapshots for the active project, 2026-08-16 to
2026-08-20, every two hours, no waiting required:

```
closed unmerged PRs (failing_reviews > 0)   0 transitions
duplicate content                           2
uncaptured defects (recent > 0)             0
quality gate failures                       0
trust score below 0.5                      10
                                     total 12 over 4.13 days  ->  2.9 per day
```

**2.9 wakeups per day against 40.** And each one, by construction, carries information: it means the
system was wrong about itself.

**The limit that must be stated.** Refutation is visible only where an expectation is written. The stack
defect O-1 violated none of the seven invariants, because nothing asserted that the artifacts agree with
the contract. A silent system is unrefutable and therefore unteachable - so **every change must leave
behind a new checkable assertion.** The set of expectations grows, and the agent wakes more precisely not
because it got smarter but because the factory says more about itself.

---

## 12. The work - one defect, its consequences, and the deployment

### 12.1 What was already built, and is still not running

Nine pieces of work are in `main` and unit-verified. The running image contains items 1 and 2 only.
Nothing else below has ever been observed doing anything.

| # | Work | Level | Commit | State |
| --- | --- | --- | --- | --- |
| 1 | persist the invariant status vector, write only on transition | factory | `ef487fa` | **deployed and verified live** (§13.3) |
| 2 | Kaizen write-side identity + recurrence count | factory | `ef487fa` | deployed; live verification waits for the factory to run |
| 3 | role-relative delivery predicate - one source of truth | delivery | `8b3cba5` | built, 68/68 |
| 4 | a merge may pull the observation forward, floor still binds | delivery | `5fb563c` | built, 21/21 |
| 5 | bury sessions whose remote record is provably gone | factory | `cd2e68b` | built, 74/74 |
| 6 | factory-level judgment, woken by refutation, **operator supplies nothing** | factory | working tree | rebuilt onto the subscription 2026-08-21 - **off by default** |
| 7 | TOC subordination, in shadow | factory | `0e6b525` | built, 10/10, decides nothing |
| 8 | declared capability register -> real `V_p` and the product-layer Six Sigma opportunity | delivery + product | `b9f201c` | built, 30/30 |

Item 6 is no longer the deliberate omission it was. It is `judgment-sidecar` (the Claude Code CLI plus
~70 lines of Node, holding the operator's OAuth credential and nothing else), `JudgmentAgentClient`,
`FactoryJudgmentService` and `V108`. `SystemSettingsService` registers **one** key for it,
`judgment_agent_enabled` - there is no credential to supply, because the subscription is already paid.
Cadence, cursor, retry and the disposal of every ruling belong to the factory. Its wake signal is item 1's
transitions; with no unjudged transition a cycle costs one indexed query and invokes no model at all.

The credential is mounted into that sidecar and nowhere else - the same decision the operator made for the
docker socket, recorded in `docker-compose.yml`: a privilege belongs in the smallest separately-reviewable
surface, never in the 346-file backend.

### 12.2 The correction this session's measurement forces

The measurement in §4 refuted three of my own claims (§14) and exposed one defect wearing three faces.
It is stated once as **ACP-103** in §9.3: a reclassification is not complete until its consumers are
enumerated. The work is not three patches; it is one census carried through to the three places the
census points at.

**a. The cadence clock must run on attempts.** `ClientRuntimeObservabilityService:111` is the single
consumer of the instrument/product classification that asks an instrument question. It reads
`lastRealObservation`; it must read the newest row of any kind. The posterior, the shift detector, the
frontend summary and `TocSubordinationLever` keep reading real observations only - V104 is right about
the product's accounting and is not touched. One clock moves; five stay.

**b. The instrument needs a denominator.** Invariant 8 applies to the launcher as much as to delivery:
nothing counts its availability, so 46 consecutive failures were correctly recorded, correctly excluded,
and therefore invisible. A run of instrument failures is a factory defect and must surface as one -
through the sink the factory already has, keyed to the launcher so it stands as one standing finding
rather than one per row.

**c. The posterior counts observations; it should count artifacts.** De Finetti licenses the Beta
posterior only over an exchangeable sequence. Between merges the artifact on `main` is constant and the
outcome is deterministic, so seven readings of one unchanged object are not seven draws. The sufficient
statistic is over distinct artifacts observed, and `productChangedSince` already detects the boundary -
it just only ever pulls the check forward, never holds it back. Identity at the write, invariant 4, the
same lesson as V105 and Kaizen. The floor stays: `BetaPosterior`'s own contract forbids ever functionally
stopping, and that is not being weakened.

**e. The designator, not the readers.** Three consumers read `RuntimeHealthSummary.recentObservations()`
and took `get(0)` as a fact about the product: this file's own cadence clock (a), the constraint's cited
cause, and `DeliveryRealityProducerService`, which wrote it into the coherence graph as "expected
launchable, actually failed". Patching each reader is the wrong shape - the fourth reader would repeat it.
The component carries every ATTEMPT, so it is named `recentAttempts`, and the summary exposes
`productObservations()` / `lastProductObservation()`, whose names state the bearer and which physically
cannot return an instrument row. ACP-102: a designator must pick out what it denotes; `recentObservations`
was a name with a bearer it did not have. §15 is a list of things forbidden by discipline - this is one
moved into the construction, where forgetting is not possible. The frontend reads the same field and is
renamed with it.

**d. Three red tests on `main` (O-12).** Established by reverting this session's changes and reproducing
them. Until they are green the suite is not a gate, and every count in §12.1 is a claim about a subset.

### 12.3 Deployment, once 12.2 is green

Deploying before (a) is deploying a measured 28x amplifier: the next time the launcher is slow or late to
start, the hammering resumes on the first tick, and it will present as "everything is lagging" rather
than as a line in a log.

1. Build once. Verify by **bytes** that the jar carries `V104`-`V109`, `ProductCapabilityService`,
   `TocSubordinationLever`, `FactoryJudgmentService`, and the named members of the day's fixes
   (`oneDrawPerArtifact`, `reportInstrumentOutage`, `lastProductObservation`) - never a build's exit code.
2. Start the backend alone; confirm every migration applies and startup is clean. `judgment_agent_enabled`
   is `false`, so the judgment cycle must log nothing at all.
3. Start `runtime-launcher` **before** anything can observe. Confirm it answers, then start `ml`.
4. Watch the first observation: it must carry `assembly:` with the app container's own log, and the
   attempt clock must now hold the cadence at the floor even if the launcher is down - the direct
   refutation of O-9, and the thing to watch for first.
5. Read `GET /api/projects/{id}/product-value`. `V_p` is expected to be **0**, with
   `declaredCapabilities` possibly 0 too. Both zeros are honest and mean different things.
6. Only then O-1, the constraint, through the ordinary path: wishlist -> task -> Jules. It is product
   work addressed to `BARCAN-TAG-00` and is never touched directly.

**Still not on this list:** O-5 (`/tree` hangs), O-6 (store growth - O-9 is now a named contributor but
not proven to be the whole of it), O-7 (missing design asset).

### 12.4 What is left, 2026-08-21 07:45Z

Written after the factory was found stalled, so the list is ordered by what is blocking the goal rather
than by what is interesting.

| # | Work | Why it blocks | State |
| --- | --- | --- | --- |
| L-1 | **Route `chatCritical` to the judgment sidecar** | Gemini was switched off (5.11) and its account is out of credit, but `MLPredictionServiceClient.chatCritical` still routes there - measured **39 × `502 Bad Gateway: Gemini`** in 600 log lines. Its two callers are exactly the two rows §11.2 listed as moving off Gemini. With no adjudicator, `JulesDispatchService` cannot decide whether a silent session is looping or waiting, so it defers forever | **done, verified live 2026-08-21 09:19Z** - 502s went 39 -> 0 and the stuck session closed |
| L-2 | **Bound the deferral** | `closeLoopAndCreateFollowUps` returns without acting on `UNAVAILABLE`, by design, because an unreachable reviewer says nothing about the session. Correct while the reviewer is *transiently* down; an absorbing state once it is *permanently* gone. Same shape as the judgment layer's UNJUDGEABLE/UNAVAILABLE split | **built 2026-08-21, not yet observed live** - see *L-2, as built* below |
| L-3 | **Retire the stuck task** | `13977462 UI Slice (00ce800b)`, `claimed` since 2026-08-20T11:08Z - ten hours before this session began. Forced unblock attempted twice without progress. It is the only non-terminal task, so `SYSTEM STALLED` has been firing for 252 minutes with nothing else to do | **resolved, verified live 2026-08-21 12:5xZ** - the stuck session closed once L-1 gave the adjudicator a working reviewer, and no manual retirement was needed. Measured on `/flow-spine`: `currentState=DECOMPOSING`, `activeTasks=2`, `openSessions=2`, `compilingWishlist=1`, `bottlenecks=[]`, `blockingReason` empty. The factory is neither stalled nor idle |
| L-8 | **A constraint is cleared by a fresh healthy observation, not by a task status** | Measured live: the constraint was filed 00:44Z, compiled 01:15Z, its derived work item finished 01:27Z - and at 02:09Z the product still answered nothing. The row is `converted_to_task`, and `existsByProjectIdAndSource` blocks re-filing **regardless of status**, so it can never be filed again while the product stays broken. §7 predicted this in writing and it went unconnected to what the dashboard was showing. The factory therefore sits idle while its own highest-priority work is open | **done, verified live** - the row re-filed itself with `STILL open after 4 finished attempt(s)` |
| L-9 | **An idle factory with an unrefuted product is a refutation of §1** - *fixed, see below* | `queuedTasks=0, pendingOrCompilingWishlists=0` is not "everything is done" - it means the work generators have stopped. `falsification_cycle_enabled=false`, the philosophical cycle fires every two days, the Gemini observer is off, and the judgment agent waits on transitions that have not occurred. The factory is idle **by construction**. Operator, 2026-08-21: *"фабрике всегда должно что быть делать"* - and by §1 that is not a preference, it is what permanent falsification means | **done, verified live 12:15Z** (§13.7) - filed by the observer 400 ms after the evidence, zero lines from the old door, `compiling` on the next tick |
| L-4 | **O-1 through Jules** | The constraint is filed, compiled and its derived work item completed at 01:27Z, yet the product still answers nothing. Two observations on two different commits: `launch=true, health=null` | waiting on flow |
| L-5 | Verify V109's collapse | Needs two observations of the *same* commit; `main` has moved between every pair so far | **done, verified live** - 18 real observations, 6 of them on `74af88ee`, collapsed to 13 draws; posterior mean 0.066667 = Beta(1,14) exactly |
| L-6 | §7 policy predicate | Its precondition is met (§13.4, §13.5). L-9 removed the *identification* half of §7's inversion; this is the *subordination* half - the rule that decides what may run while the constraint is open | **built 2026-08-21, not yet observed live** - see *L-6, as built* below |
| L-7 | Commit the session's work | Every image so far is built `dirty`; nothing is in `main` | **done** - `eda4efd` on `main` |

**L-9, as built.** Identification of the constraint moved out of `FalsificationCycleService` into
`LaunchabilityConstraintService`, and `ClientRuntimeObservabilityService` now calls it the moment it
writes an unhealthy observation of the product. The philosophical cycle still calls it too, so its
subordination behaviour is unchanged - it is simply no longer the only door.

The absurdity that was there is worth stating plainly, because it is the general shape of §7's inversion:
the philosophical review is the process that must **stand aside** while the product is broken, and it was
also the only process that **noticed** it was broken. If it did not run - and it runs every two days,
behind five of its own gates, with its one accelerator switched off - nobody noticed, nothing was filed,
and the factory had no work. Meanwhile the observer starts the product every hour and sees the answer
with its own eyes; it knew first, it knew best, and it did nothing with what it knew.

Cadence of identification: every two days and only if five unrelated conditions hold -> every hour and
only that the product did not answer. Re-filing stays bounded inside the service (an attempt in flight
blocks a second; a finished one waits out a cooldown), so an hourly cadence cannot become a compile loop.

**L-2, as built.** The `UNAVAILABLE` branch of `closeLoopAndCreateFollowUps` now counts, and the count is
`blindCycleCount` - not a new field. That counter already meant exactly this: consecutive cycles in which
this session could not be SEEN. Its only previous cause was an activity log too large to read; a classifier
that cannot answer is the same epistemic state reached by another route, and one source of truth is better
than two. Below `jules.forced-unblock-blind-cycle-threshold` (5) the charitable reading is unchanged and
nothing closes. At the threshold the deferral ends and control falls through to the circuit breaker that
already exists - no second closure mechanism - and the closure reason says in words that the session was
closed on the reviewer's absence and NOT on evidence about the session, so a later reader is not misled into
thinking its writing was read and found wanting. **Rollback:** delete the increment and the threshold check;
the branch returns to deferring unconditionally.

**L-6, as built.** Nothing new was constructed. `TocSubordinationLever` has existed since `0e6b525`,
`ContinuousOrchestrationService` has called it on every policy check since 2026-08-20, and the five-stage
ladder (`LeverStage`, `LeverPromotionService.evaluatePromotions`) already promotes a rule on its own
accumulated agreement rate. The one thing missing was that this lever could never arrive anywhere: it
returned the incumbent's answer unconditionally, so no amount of evidence could ever change a decision. A
promotion nothing acts on is not a promotion. From `soft_gate` upward the rule now decides, and in one
direction only - `incumbentAllowed && candidateAllowed` can turn an allow into a deny and can never turn a
deny into an allow, because subordinate means non-constraints idle, never that slack may do something the
flow state forbids. `OBSERVE` and `CHECK_LAUNCHABILITY` stay outside the restriction for the reason §7 gives:
only a fresh healthy observation clears the constraint, so suppressing the act that ends it would make it
permanent.

Two things were corrected while doing it rather than left as debt. The read of the observation history was
unbounded, which is acceptable at one shadow sample per tick and is not acceptable on a per-action policy
check - the bounded repository method already existed and is now used. And the method was still called
`observe` while it had started to decide; it is `subordinate`. A method named "observe" that decides is a
designator with a bearer it does not have, which is ACP-102 exactly, and the name has to move when the
behaviour does.

The application point did **not** move into `OperationalPolicyService.authorize`, though that is the more
general predicate. `authorize` is also consulted by read-only status endpoints inside a readOnly
transaction, and the lever writes an observation for every pair it sees. Invariant #10's "one point of
application" means the place a decision is acted on, not every place the question is asked.

**Rollback for both:** `git revert` the commit; neither touches a schema, and the ladder's stored stage is
read-only to them.

**O-15 and O-16, as built.** Two defects, one shape, found by watching the constraint L-9 had just made
the factory file for itself.

*O-15 - the bound that was not on every path.* F42 already put a decreasing measure on the compile loop
after one brief was decomposed 16 times: `mu = WISHLIST_COMPILE_ATTEMPT_BUDGET - compileAttempts`. It sat on
one admission path. The other checks the WIP limits and a 900 s cooldown, and a cooldown bounds how OFTEN,
never how MANY TIMES; the one-shot compiler dispatch recorded its timestamp but never incremented
`compileAttempts`, so on that route mu was constant and the budget unreachable. The guard is now one method
that both admissions call, and mu decreases on every dispatch route. Nothing new was invented - F42's own
WITHHOLD, with its `factoryReport` line for a human, is what fires.

*O-16 - the cause.* `completeWishlistCompilation` is the only place a compiled brief becomes
`converted_to_task` or honestly `dismissed`, and it is reachable only from the `pr_opened` handler. When
`AutoMergeService`'s poka-yoke drives the compiler task terminal from the merged PR first, the session is
closed for being terminal and that handler never runs. `closeSessionForTerminalTask` already repairs this
exact shape one layer down - it releases a dead persistent-worker carrier's stranded briefs - so the
one-shot compiler task now gets the same treatment: read the plan the compiler already merged to `main` and
build the graph from it. `ingestPlanFromContent` was deliberately not reused despite doing almost this: it
collects every pending/compiling brief in the project, and `epicPlan.sourceIndex()` is positional against
the batch the prompt was built from, so it would attach one brief's epics to another.

`AutoMergeService` was not touched. §12.1 records why, and nothing here needs it to change.

**Falsifiable, both:** the constraint must receive at most three compiler dispatches, then
`exhausted its decomposition budget (3 attempts)` with the same line in the project's `factoryReport`; and
a compiler task that goes terminal early must log `O-16 recovery: ... rebuilt its N brief(s)` instead of the
brief returning to `pending`. A fourth dispatch refutes O-15. A brief back in `pending` with no recovery
line refutes O-16.

**Not on this list and still open:** O-3..O-8 as recorded in §6, plus three signals read off
`/api/system-status` at 02:11Z that nobody has looked into - `CI_STATUS=failing`,
`roleDoctrineReadiness=blocked`, and `dpmoCodeTasksOnly=655172`, which is 65% defect density on code tasks
and deserves its own analysis rather than a line here.

**The honest accounting of the stall.** The task has been stuck since 11:08Z on 2026-08-20, ten hours
before this session. What this session added was not the stall but the reason it cannot clear: Gemini was
switched off here and the replacement §11.2 named was never connected. Switching a provider off without
wiring its replacement is not a replacement, and pointing at the absent provider is pointing at my own
unfinished work.
---

## 13. Verdict records

Recorded as baseline -> intervention -> observed delta -> verdict, with an explicit rollback and
postcondition. One changed factor per step.

### 13.1 The `pending_review` blocker, 2026-08-20

**Baseline 05:26-06:13Z.** 1 task in `pending_review` (`aaa599ac`, TAG-05, PR #113 merged with
`hasCode=false`); 9 failed; Flow Core state `SYSTEM_STALLED` sustained ~151 min, denying dispatch and
recovery **project-wide**; the reconciler re-entering the same refusal every ~60 s.

Causal chain, from code and log: the poka-yoke declines to close the task and leaves it standing ->
`checkForSystemStall` counts a `pending_review` task as actionable work -> with idle capacity and no
progress it sets `stalled` -> `FlowSpineService` maps that to `SYSTEM_STALLED` -> the policy denies
dispatch. **One task with nowhere to go froze the entire flow.**

**Intervention.** `d6e6c17` routes the requirement rather than the task identity (retire as `failed`,
reopen the source wishlist, bounded by already-failed siblings - invariant 7). `a86254e` corrects a defect
the first revealed live: writing the status directly threw `TransactionRequiredException` every minute,
because that entry point is deliberately non-transactional and annotating it would not help (self-
invocation never crosses the proxy). Routed through `ClaimService.closeTaskAsFailed`, the existing
transactional owner - invariant 10.

**Observed delta 06:14:10Z, holding at 06:18Z.** `pending_review` 1 -> 0; `failed` 9 -> 10; state
`SYSTEM_STALLED` -> `DECOMPOSING`/`IMPLEMENTING`/`DELIVERED`; `STALL` lines 5/5 min -> 0; poka-yoke
re-entries ~5/5 min -> 0. The source wishlist was **not** reopened - it was already `dismissed`, having
collapsed into a semantic duplicate at compile time, and the guard skipped it as written.

**Verdict: confirmed.** Not a proof the mechanism is correct in general - only that it is not yet refuted,
on one real case, at the point where it mattered.

**Rollback.** Revert `a86254e` then `d6e6c17`. The only persistent state is task `aaa599ac` at `failed`
with its claim released - inert, idempotent, and `failed` is absorbing (invariant 3). No migration, no
setting, no schema.

**Postcondition.** No task may remain in `pending_review` against a merged code-free PR for longer than
one reconciler cycle. If one does, either the routing did not fire (look for an exception inside
`routeUncertifiedMerge` - that is how `a86254e` was found) or its role's delivery predicate is wrong for
that role's artifact kind (O-8).

### 13.2 Cadence keyed on the lower bound, 2026-08-20

**Baseline 05:26Z.** Interval width 0.596, delay 9.69 h, next check due 09:11Z; last error text naming no
service.

**Intervention.** `4315bfb` - one multiplier: the credible interval's lower bound instead of `(1 - width)`.
A test that fails under the old rule was added: six consecutive successes and six consecutive failures
produce the **identical width**, which is exactly why width alone cannot decide cadence.

**Observed delta.** Next check 05:34:03Z - 90 seconds after deploy, not 8 hours. Observations 4 -> 10 in
five hours.

**Verdict: confirmed. Rollback:** restore `(1.0 - credibleIntervalWidth())`; one expression, no other
state. **Postcondition:** a product observed broken is re-checked at the floor; a product observed working
earns a delay proportional to the confidence it has earned.


### 13.3 Invariant transitions recorded, 2026-08-20 12:38Z - order item 1

**Baseline.** `OperationalTruthService.invariants()` evaluated seven Charter invariants on every call and
returned them in a DTO read only by the dashboard controller. Nothing persisted them, confirmed across
every reference to `InvariantStatus`, so `pass -> warn` was undetectable in principle.

**Intervention.** `ef487fa` - `V105` plus recording inside the existing two-hourly `TrustSnapshotService`
pass. Rows are written on **transition only**.

**Observed delta.** Seven rows on the first pass, one per invariant, each with `<first>` as its previous
status. Immediately informative:

```
done_is_not_delivery      <first> -> warn      2 done task(s) lack local merged PR evidence
defect_requires_...       <first> -> observed  15 recent defect(s) should be checked for capture
five others               <first> -> pass
```

The `warn` is a live refutation of a standing Charter claim. Before this table it existed only inside a
DTO nobody read.

**Idempotency, verified live rather than only in the unit test.** The backend was restarted to force a
second pass with nothing changed. Over the following seven minutes the table stayed at **7 rows** - the
second pass wrote nothing. A confirmation leaves no trace, as designed.

**Verdict: confirmed.** **Rollback:** revert `ef487fa`; `V105` leaves an unused table, which is inert.
**Postcondition:** the row count rises only when an invariant's status actually changes. If it grows on a
quiet cycle, the write-side identity has been lost - the exact defect this item was built to avoid
repeating from `KAIZEN_PROPOSALS`.

**Order item 2 (Kaizen write-side identity) is deployed and unit-verified (16/16); its live verification
waits for the factory to run, since proposals are only generated on its own cycle.**


### 13.4 The cadence clock and the instrument's denominator, 2026-08-20 23:10Z - O-9 and O-10

**Baseline, measured off the stopped database.** The launcher stopped answering at 11:40 and the
observation rate went from 1/hour (05h..11h, one row each) to 17/hour and then 28/hour, 45 calls reaching
nothing between 11:40 and 13:34. Every row correctly marked `instrument_failure=TRUE`, correctly excluded
from the posterior, and therefore absent from every number a reader would consult. 46 consecutive
instrument failures produced zero findings.

**Intervention.** One clock moved (`ClientRuntimeObservabilityService:111`): the cadence gate now runs on
the newest row of any kind, because what it limits is attempts and an unanswered call is an attempt. Five
other consumers of the instrument/product classification were audited and left alone - they ask about the
product and were already right (ACP-103 census, section 9.3). Separately the instrument gained a
denominator: N consecutive unanswered calls file one standing factory finding keyed to the launcher.

**Observed, backend up and launcher deliberately still down - the exact condition that produced the 28/hour.**

```
23:10:18  "2 task(s) reached done since the last observation at 11:39:59Z; observing now
           rather than waiting out the timer"
23:10:21  project 41af381d observed - launchSuccess=false healthStatus=null instrumentFailure=true
23:10:21  "the runtime launcher has failed to answer 3 consecutive times; reporting it as a
           factory defect"
attempts in the following 6.5 minutes: 1        (the old clock would have attempted on every tick)
```

**Verdict: both hold.** The single attempt was pulled forward by a real product change, not by a frozen
clock - the correct reason - and the limiter then held. `GET /api/kaizen/factory` returns the finding
`[SYSTEMIC_DEFECT] target='runtime-launcher'` standing beside three others with distinct
`targetComponent` values, which is also the first live confirmation that the 2026-08-17 dedup fix keeps
distinct factory findings from displacing one another.

**Rollback:** revert the clock to `lastRealObservation` and set
`client-runtime-observability.instrument-outage-threshold` beyond reach. **Postcondition to watch:** with
the launcher up, observations must remain rate-limited rather than resuming per-tick - the floor is one
hour and nothing in this change weakens it.

**Not yet verified:** V109's artifact collapse. It needs two observations that reach the launcher and
carry a real `commit_sha`, and no row in the table has one yet.
### 13.5 The constraint cites the right bearer, 2026-08-21 00:44Z - ACP-103's fourth face

**Baseline, measured on the artifact rather than the log line.** At 23:25Z the factory filed the
constraint through its own mechanism and the row read, verbatim:

```
Observed failure, exactly as the launcher recorded it - this is evidence, not a hypothesis:
runtime-launcher unreachable: I/O error on POST request for "http://runtime-launcher:8091/launch"
```

That is a fact about this factory's own sidecar, offered as the evidence for what to fix in the CLIENT's
repository, where no such component exists. `TechnicalLeadCompiler` classes `product_not_launchable` as
defect work at weight 1.0, so the next compile would have carried it to a task and to Jules.

**Why the census missed it.** The audit in §9.3 enumerated readers of `isInstrumentFailure` and
`lastRealObservation`. `latestErrorText` is neither: it read `recentObservations().get(0)` and never
touched the flag. Two further readers of the same rows were found the same way once the census was
re-run over the RECORD - `DeliveryRealityProducerService`, which wrote the cause into the coherence graph
as "expected launchable, actually failed", and the dashboard's own pulse line, whose DTO did not carry
`instrumentFailure` at all, so every instrument outage has been drawn to the operator as the product
going down since V104 shipped.

**Intervention - the designator, not the readers.** `RuntimeHealthSummary.recentObservations` is renamed
`recentAttempts`, because that is what it holds, and the summary gained `productObservations()` /
`lastProductObservation()`, which cannot return an instrument row. All three Java readers and the
frontend now go through them. Patching four call sites would have left the fifth.

**Rollback:** revert the record component name and the three call sites. **Row handling:** the 23:25Z row
was **deleted, not dismissed** - `existsByProjectIdAndSource` is status-agnostic, so a dismissed
constraint could never be re-filed while the product stays broken (§7). Deleting undid an action this
session caused rather than editing what the factory had written.

**Observed, same input, same mechanism, corrected code:**

```
00:44:49  created product_not_launchable wishlist for project 41af381d
00:44:49  "not currently launchable/healthy - subordinating philosophical review to that
           constraint (TOC) instead of auditing a broken product this cycle"

cited evidence  HTTPConnectionPool(host='localhost', port=18080) ... [Errno 111] Connection refused
                | assembly: every service reports running, so ...
mentions launcher outage : False        (was True)
mentions product failure : True
sourceRoleTag            : BARCAN-TAG-00
```

**Verdict: holds.** The constraint is filed, addressed to the assembly role, and carries the product's own
failure - which is O-1's signature, and the first time the constraint has existed as a row at all.
### 13.6 The judgment layer's first cycle refuted its own input, 2026-08-21 02:20Z

**Setup.** `judgment-sidecar` up and healthy, running the operator's Claude subscription with no
credential supplied by anyone; `judgment_agent_enabled` flipped to true at 02:07Z; seven rows sat
unjudged in `invariant_status_changes`, left that way by V108 on purpose because they were real records
the factory had never acted on.

**Observed.**

```
02:20:40  7 unjudged invariant transition(s); ruling on up to 5 this cycle
02:22:04  ABSTAIN delivered_requires_evidence          (null -> pass)
02:22:43  ABSTAIN done_is_not_delivery                 (null -> warn)
02:22:57  ABSTAIN closed_unmerged_is_not_delivery      (null -> pass)
02:23:14  ABSTAIN runtime_status_affects_trust         (null -> pass)
02:23:28  ABSTAIN duplicate_content_blocks_throughput_trust (null -> pass)
all five rows carry judged_at; two remain, also null -> X
```

**Verdict: the loop works and the queue was wrong.** Every ruling landed, the cursor was written, and
nothing was judged twice - that half holds. What the agent then said, five times in its own words, is
that it had been handed nothing to rule on: *"a baseline entry into pass is not a transition away from an
asserted property and carries no signal of a factory defect."* All seven rows are `previous_status = null`
- first-ever registrations, not transitions. V105 exists because of Popper's asymmetry, and a baseline is
a confirmation with no predecessor: the least informative row the table can hold. Roughly $0.30 per
ruling, five times, on rows that were knowably uninformative before they were sent.

**Whose defect.** Mine, and the same shape as O-9 and ACP-103 for the third time: one table holds two
kinds of fact - baseline registration and real transition - and the reader took them as one.
`findByJudgedAtIsNullOrderByObservedAtAsc` did not distinguish them.

**Intervention.** The query is replaced by
`findByJudgedAtIsNullAndPreviousStatusIsNotNullOrderByObservedAtAsc`, and the old one is **deleted** from
the repository rather than left beside it. Same principle as `recentAttempts` in §12.2(e): the exclusion
belongs in the type, not in a filter each caller must remember. The two remaining baselines are left
unjudged and out of scope, which is what they always were.

**Rollback:** restore the previous derived query. **Postcondition, verified 2026-08-21 03:01Z:** the cycle
ran for sixteen minutes across at least one tick and wrote no line at all. Silence alone would not prove
it - an unfired scheduler looks identical - so the input was measured directly rather than inferred from
the absence of output:

```
judgment_agent_enabled = true (database)
invariant_status_changes: 7 rows, 7 with previous_status IS NULL, 0 in the queue, 5 judged
```

The queue is empty because every row is a baseline, and the cycle correctly invoked nothing. It will stay
silent until an invariant actually stops holding - at the measured rate, 2.9 times a day.
---

### 13.7 The constraint identified itself, 2026-08-21 12:15Z - L-9

**The prediction, written before the deploy and falsifiable both ways.** After deploying, the
`product_not_launchable` row must appear **by itself**, within an hour of an observation whose product
did not answer, logged by `LaunchabilityConstraintService`. If a second line appeared from
`FalsificationCycleService`, the old door was still open. If none appeared at all, the observer was not
calling the service and L-9 was not built.

**Observed, on the first observation after startup, with no human in the loop:**

```
12:15:39.503  ClientRuntimeObservabilityService : project 41af381d observed -
              launchSuccess=true healthStatus=null instrumentFailure=false
12:15:39.903  LaunchabilityConstraintService    : the launchability constraint is STILL open after
              6 finished attempt(s); re-filing it rather than leaving the factory idle
12:15:39.913  LaunchabilityConstraintService    : created product_not_launchable wishlist
FalsificationCycleService constraint lines in the whole log: 0
row status 84 seconds later: compiling
```

**Verdict: holds, both halves.** 400 ms from evidence to constraint, by the process that produced the
evidence; the philosophical cycle - which fires every two days behind five of its own gates - was not
involved and is no longer the only thing that can notice. The row was already `compiling` on the next
compiler tick, so the identification is connected to the work generator and not merely written down.

**Rollback:** delete the `ensureOpen` call in `ClientRuntimeObservabilityService`; the philosophical
cycle's own call is untouched and restores exactly the previous behaviour.

**What the constraint now carries as evidence** - the same run, quoted from the row's own `errorText`:

```
service 'app' (container 'runtime-observe-app-1') state=running:
  <no output on this container's stdout/stderr; published=[18080->8080/tcp, 18080->8080/tcp];
   entrypoint=["java","-jar","app.jar"]; cmd=null; restarts=0; status=running exit=0>
health check -> localhost:18080/health : Connection refused
```

A JVM that is running, was given 397 s, published its port, never restarted, and wrote **not one line**
to stdout while nothing listened on 8080. That refutes port-mismatch, crash-restart and never-started
alike; the defect is inside the product's own image. It is O-1, it is product work, and it goes to Jules
through the ordinary path - never edited here.


## 14. Corrections - my claims that measurement refuted

| I claimed | Measurement showed | The error |
| --- | --- | --- |
| "Nobody launches; the constraint is measured and never acted on" | `ClientRuntimeObservabilityService` calls the launcher; observations exist | read a service's name as its scope |
| "Launchability is a verdict about the past with no state object" | `ClientRuntimeObservationEntity` - append-only, owner, identity, timestamp | concluded a model absent without looking |
| "The main falsification engine is off" | The engine is the philosophical track, and it is on | inferred a component's role from a flag's name |
| "`stitch_api_key` is null" | `****9SCw` - set; I had read `enabled`, null for all secrets | absence in the wrong field read as absence in data |
| "Launch should be a consequence of readiness" | Separate axes, deliberately (§2) | proposed merging distinctions an incident had separated |
| "Gemini does not participate in the blocker chain" | `triggerFalsificationRun` pulls the cycle forward | described her actions from memory instead of reading them |
| Registry pre-check before launch | `docker compose up` already resolves references authoritatively | would have been a second source of truth for one fact |
| `launchabilityCheckedAt` mis-used as state | Its only two readers use it as a bootstrap marker | judged a field mis-used without reading its call sites |
| "Deployed" ×2 | The jar did not contain the change | read a build's exit code as proof the image changed |
| "Zero ticks / zero observations" | `docker logs` had returned one line - its own bridge error | read a broken instrument's silence as a fact |
| Left the backend down 47 minutes | Announced a restart I never performed | reported my own action without verifying it happened |
| Three delivery defects from six-day strands | All on accepted or frozen projects; `compiling` recovery already exists and correctly does not run on frozen ones | read the shape of data without checking the status of what produced it - and nearly repaired a working mechanism |
| "Jules accepted the task and has not started" | Jules's own API answered `state: IN_PROGRESS` | inferred from the absence of a warning line that the poll had happened - absence of a complaint is not evidence of a question asked |
| Read the observer journal without filtering to the active project - twice | 21 of 22 projects are dead | ignored the scope rule this session opened with |
| Held the routing fix over the ACP-102 content trap | Among the thirteen BARCAN roles the trap has **no bearer** - TAG-03 and every spec stage are already exempt | let a correct principle block a fix for a case it does not cover, while the flow stayed frozen for hours |
| "Gemini stopped running" | She ran hourly and every response failed to parse | read silence in a projection as absence of activity, again |
| "The parse failure is a format problem" | HTTP 429 `RESOURCE_EXHAUSTED` - the account is out of credit | began diagnosing a mechanism before checking the instrument's preconditions |
| "12 transitions over 12 days = 1.0/day" | The snapshots span 4.13 days: **2.9/day** | divided by a period I had not read off the data I was quoting |
| "Kaizen has no deduplication" | It deduplicates by `(category, target_component)` - at **read** time | checked one side of a mechanism and described it as the whole |
| "State: 10 observations, launch=TRUE, health=null" | 56 observations; 8 launched; `health_status_code` null in **all 56**; 46 are instrument failures | quoted §4's own dashboard snapshot as if it were the table, for a whole day after it went stale |
| "The observer costs 24 launches/day producing 0 bits" | True only while the launcher answers. It had been at **28/hour** since 11:40 the previous day | computed the designed behaviour and presented it as the observed one - without opening the table |
| "The dominant defect is the posterior's exchangeability" | The dominant defect is the cadence clock (O-9), an order of magnitude larger and introduced by my own item 5.5 | analysed the model before measuring the mechanism, and found the more elegant defect rather than the bigger one |
| "V104 fixed the instrument/product confusion" | It fixed one of six consumers and never enumerated the rest - **ACP-103**, §9.3 | treated a reclassification as a value change rather than as a change to every reader of that value |
| "The census found all six consumers" | A seventh, `FalsificationCycleService.latestErrorText`, reads the rows without touching the flag - found only by reading the artifact the factory actually produced | enumerated readers of the FIELD and called it a census of the RECORD - ACP-103 applied to itself, and failed |
| Filed the constraint by triggering the cycle by hand | It filed real evidence about the wrong bearer, and would have sent Jules to fix the factory's sidecar inside the client's repo | acted on a mechanism before reading what that mechanism produces |
| "The subscription agent replaces the auditor at **flat cost**" (§11.2) | The first build called `/v1/messages` with an `sk-ant-` key - metered per token, needs an API balance. Swapping a metered API whose credit had run out for another metered API | promised a billing model I had not checked, and built to the promise instead of to the requirement |
| "Denying the agent its tools will cut the cost" | Same prompt: $0.33 with tools, **$0.81 without**. Denying tools moves spend into reasoning | predicted a number from a mechanism I had not measured. The change is still right - the agent was reading the sidecar's own filesystem and judging nearly blind - but for the correctness reason only |

---

## 15. Forbidden by construction

- No number may be treated as "the product is ready."
- No number may close the flow. The only terminal is `acceptProject`, and it is a human's.
- No launch may wait on a completeness metric.
- No falsification track may be switched off as "finished."
- No product content may be written directly into the client repo, bypassing wishlist -> task -> Jules.
- No claim about the system from memory: read it, or do not say it.
- No measurement without its scope stated.
- No waiting presented as work: if a window must pass, everything independent of it proceeds meanwhile.

---

## 16. Held - move the factory to a server, on command

The host limit costs time; it does not block §1.1. Written down so it can be executed without re-deriving.

Four compose services - `backend` (8080), `frontend` (3000), `ml` (8000), `runtime-launcher` (8091) - and
three volumes: `./data`, `./project-workspaces`, and the repository itself mounted read-only at
`/app/eneik-system`.

**The state is one file.** `data/eneik_db.mv.db` is 1920 MB holding ~88 MB of live data. It must **not**
be copied while the backend runs - doing exactly that produced `File corrupted while reading record`. Stop
the backend, let H2 close cleanly (it compacts on close), then copy. Every credential the factory holds
lives in that same file, so moving it moves the access with it.

**The launcher holds the Docker socket** because it starts client products on the same host. On a public
server that is root on the box: 8091 and 8000 must never be exposed.

**Environment actually required:** `GEMINI_API_KEY`, `GITHUB_TOKEN`, `GITHUB_ORG`, `STITCH_API_KEY`, plus
Linear's three if used. The other ~30 compose variables have defaults.

**Order:** Docker on the server -> stop the factory and let the store close -> copy `data/` and
`project-workspaces/` -> clone, write `.env` -> `docker compose up -d --build` (Flyway migrates on boot)
-> expose only 3000 and 8080 behind a reverse proxy with auth.

**What it buys:** the host limit disappears - it cost a failed launch at 16:32Z and forces the backend
down for every build. **What it does not buy:** every defect in this plan travels unchanged. The server
removes a physical constraint, not a logical one.
