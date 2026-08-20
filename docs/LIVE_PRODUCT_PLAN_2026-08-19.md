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

## 4. State, measured 2026-08-20 11:30Z

```
tasks       claimed 1 · done 130 · failed 10
runtime     10 observations · last 10:38:43Z · launch=TRUE · health=null
posterior   mean 0.091 · interval width 0.306
flow        DECOMPOSING / IMPLEMENTING / DELIVERED (no SYSTEM_STALLED)
host        1272 MB free of 3917 · store 1920 MB
```

**The product launches and does not serve.** Four containers come up - app, postgres, minio,
backup-cron - and the application dies during startup:

```
Driver org.h2.Driver claims to not accept jdbcUrl, jdbc:postgresql://db:5432/epidemiology_db
```

`docker-compose.yml` declares PostgreSQL, `application.properties` declares H2, and `pom.xml` carries no
PostgreSQL driver at all. This is a PRODUCT defect and goes through wishlist -> task -> Jules. Its
diagnosis is §10.

The previous blocker - an unresolvable MinIO image tag, a name with perfect form and no bearer - is
**closed**: PRs #113, #118, #123 and #128 fixed and then verified it, and both
`minio/minio:RELEASE.2023-11-01T18-37-25Z` and `postgres:15-alpine` are pulled on the host.

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
| O-1 | product does not serve: compose says PostgreSQL, config says H2, build has no PostgreSQL driver | every observation since 05:34Z | product |
| O-2 | invariant statuses are computed every cycle and **never persisted**, so `pass -> warn` is undetectable in principle | confirmed across every reference to `InvariantStatus` | factory |
| O-3 | Kaizen has no write-side identity | 347 rows carrying **10** distinct `(category, target_component)` pairs | factory |
| O-4 | dead Jules sessions polled forever | 52 `404`/hour; 3 of 4 `pr_opened` sessions answer 404 to their own account key | factory |
| O-5 | `GET /api/projects/{id}/tree` never answers | 90 s, `http=000` | factory |
| O-6 | store far larger than its contents | 1920 MB file, ~88 MB live; growth bursty, not monotonic; row churn low | factory |
| O-7 | a design asset fetched and missed forever | 14/hour, `design/approved/20260818165327-mockup/mockup.html` absent on main | delivery |
| O-8 | `requiresCodeForDelivery` answers a code question about a delivery concept | 5 phantom deliveries measured; no bearer for the content case yet | delivery |

Closed since the last rewrite: the 4-per-minute reconciler loop (5.7), the instrument-failure pollution
(5.5), the symmetric cadence (5.6), the unnamed launch failure (5.8).

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

**Not built. Deliberately after the items in §11** - subordinating everything to a constraint the system
cannot yet observe reliably would subordinate it to a guess.

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

Today: |C| = 1, Beta(1,10), LCB ≈ 0.003. **V_p = 0.**

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
| `OpsAuditorService.chatCritical` | evidence-only auditor: 2 evidence kinds in, 3 decisions out, may ABSTAIN | **move to the subscription agent** - it already is factory-level judgment | same function, flat cost |
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

### 11.4 The subscription agent - what it replaces and how it reaches the backend

It replaces **one** thing: `OpsAuditorService`'s judgment, plus the factory-level refutations nobody acts
on. It needs no backend change - everything is exposed:

    GET  /api/dashboard/operational-truth      invariants with pass/warn status
    GET  /api/projects/{id}/runtime-health     product state
    GET  /api/projects/{id}/coherence-graph    evidence
    GET  /internal/tasks/status-counts         flow, project-scoped
    POST /api/wishlist                         create work

**The cost gate is the order of operations.** It runs on a timer, but its first act is one cheap HTTP
read: are there unhandled factory-level refutations? If not, it exits **without invoking a model at all**.
The observer called the model *to find out whether there was news*; this asks the factory, and wakes
judgment only when the answer is yes.

**Its output is bounded to two kinds**: a factory-level wishlist, or a plan correction. Never prose, never
a journal entry, never product work. Prose without one of those two outcomes is the overproduction the
observer was switched off for.

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

## 12. The order

One list. It replaces the three that accumulated in the appended versions.

| # | Work | Level | Why here | Depends on |
| --- | --- | --- | --- | --- |
| 1 | **Persist the invariant status vector** and write a row only on transition | factory | `pass -> warn` is undetectable in principle without a stored previous value; it is the wake signal for everything in §11.4, and its write-on-change shape is the §11.3 lesson applied at birth | - |
| 2 | **Kaizen write-side identity** + recurrence count | factory | independent of everything; removes 337 of 347 rows going forward and gives kaizen refutability | - |
| 3 | **Role-relative delivery predicate** (O-8) | delivery | unblocks nothing today (no bearer) but the trap is in the code, and it is the predicate §8.3 depends on | enters at `OBSERVE_ONLY`: compute old and new side by side |
| 4 | **Observe on merge**, not only on a timer | delivery | a merge changes the object the posterior is about; without it a correct fix is invisible for up to a full delay | - |
| 5 | **Bury dead sessions** (O-4) | factory | a 404 on the session itself is proof of absence; cheap, and it is why the log cannot be read | - |
| 6 | **Move the auditor to the subscription agent**, gated on a refutation being present | factory | §11.4; deliberately after 1, because without stored transitions there is nothing to gate on | 1 |
| 7 | **TOC subordination in the policy** (§7) | factory | subordinating everything to a constraint the system cannot yet observe reliably would subordinate it to a guess | 1, 4 |
| 8 | **Declared capability register** -> real `V_p` (§8.2) | delivery + product | the denominator must come from the brief; the largest item, and worth nothing until the observation loop is trustworthy | 4 |

**Not on this list, deliberately:** O-5 (`/tree` hangs - costs the operator, not the product), O-6 (store
growth - measured, cause not isolated, no fix without a cause), O-7 (missing design asset - noise), and
anything inside the client repository.

**O-1, the product's stack defect, is not on this list at all** - it is product work, already addressed to
`BARCAN-TAG-00` with the runtime-contract DoD. The factory fixes it or the factory is not autonomous.

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

---

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
