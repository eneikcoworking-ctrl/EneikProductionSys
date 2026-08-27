# Working plan - the factory keeps a running product under permanent falsification

Per-session verdict records, the Gemini removal and the TOC/Six Sigma derivations are in
`LIVE_PRODUCT_PLAN_2026-08-19_ARCHIVE.md`, whole and unedited.

> **Numbering.** 1-16 are the archive's own numbers, so every cross-reference inside these sections still
> resolves; present here are 1, 2, 3, 7, 8, 9, 10, 11. Archive material is always cited as "archive §N";
> a bare §N means a section of this file. Sections recording work done after 2026-08-19 start at 20.

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
live (archive §13.4), and the constraint exists as a row carrying the product's own failure (archive §13.5). What blocks
it is no longer epistemic, only unbuilt.

One flow defect this work exposed, recorded here rather than fixed in passing: the constraint is filed
**only** inside `executePhilosophicalCycleForProject`, behind five gates that all belong to philosophical
review, on a cron that fires every two days - and its one accelerator, the Gemini observer, is switched
off (archive 5.11). So the identification of the constraint is a side effect of the very process that is supposed
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
- an unanswered launch call recorded as a failed product (archive 5.5)
- a depleted API quota recorded as an unreadable answer (archive 5.10)
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


### 9.4 ACP-105 - A Universal Claim Must Declare Its Domain

`BARCAN-TAG-01_ACTUALIST-OBJECT`. §1 rejects falsifying a product that does not run because it is
quantification over an empty domain - "trivially satisfied, informative about nothing". The same structure
was sitting one level down, in the machinery that decides whether work was verified.

`GateOrchestrator` computed its verdict as

    boolean allPassed = results.stream().allMatch(GateResult::passed);

`allMatch` on an empty list returns `true`. A task whose role no gate supports - **eight of the thirteen
roles have no gate at all** - produced an empty `results` and was recorded as having passed every
applicable check when none was applied.

**`allMatch` is a fraction in disguise.** It says "N of N passed", and at N = 0 it reports 0/0 as success.
Invariant 8 - state the denominator - had been applied to every ratio in this system and to no boolean,
because a boolean does not look like it has one.

> **Where a domain can be empty, "all passed" and "nothing was asked" are different values, and the
> cardinality travels with the verdict.**

**What was built, and what was deliberately not.** The boolean is unchanged: making it false when nothing
applied would fail every task of eight roles and stop the factory, which is a repair that damages. What
was added is the denominator - `applicableChecksByStage` - and readers that need real evidence ask it.

**Per stage, and this is not a detail.** The first version counted all applicable checks together, and the
`stages` array records the stages *requested* - `runQualityGate` always requests all of them. So a task
with a TASK_SPEC check and no delivery check showed a positive count beside a requested delivery stage and
read as verified for delivery: the same substitution the change exists to remove, rebuilt an hour later by
the person removing it. Found only by checking what the field *means*, after checking who writes it and
who reads it had already returned "safe".

**Three gates were returning `GateResult(true, "not applicable")`.** Unreachable through the orchestrator,
which filters by `supports()` first - but reachable from three tests named `shouldPassNonXTask`, which
asserted it. The defect was pinned by a test, so removing it looked like breakage. The tests now assert
what production actually relies on: `supports()` rejects the task, so the check is never reached.

**The mechanical form, for any future code.** Find `allMatch`, `anyMatch`, `noneMatch`, a ratio or a
percentage, and ask what it yields on the empty set. If the answer is success, that is `∀x ∈ ∅`, and the
cardinality belongs beside it.


### 9.5 ACP-106 - A Name Is A Claim

`BARCAN-TAG-02_RIGID-DESIGNATOR`. The name of a mechanism asserts what it does. When the name asserts a
guarantee the mechanism does not provide, it does not merely mislead - it **suppresses the question**,
because the guarantee reads as already answered.

`AutoMergeService` calls its task-closing reconciler a **poka-yoke**. Poka-yoke is the guarantee that a
defect cannot be accepted by the receiving side: the corner of a SIM card is cut at manufacture, so the
wrong orientation is not detected - it is unperformable. What the reconciler does is the reverse. It writes

    task.setStatus(TaskStatus.done);

because a sibling pull request merged, with no test of what the task promised. Error acceptance under the
name of error proofing.

Measured 2026-08-23: four tasks of `test-fiftieth` closed through that path. Two hours before the code was
read, this assistant reported the mechanism to the operator **by its name**, as though the name were the
explanation, and the operator refused it - poka-yoke is not a patch or a stub, it is the highest grade of
the guarantee. The word had stood in for the check for a month, including in the report of the one auditing
it.

The lesson is not to rename things. It is that a name asserting a guarantee is a claim like any other in
this plan and must be held to it - and where it cannot be held, the guarantee gets built rather than the
word kept. What was built is §27.

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
| **BOOTSTRAP (before 20), the factory itself** | **leave the datastore undecided, so ARCHITECTURE can decide it** | **it decides it instead - see 10.5** |
| ARCHITECTURE (20), TAG-01 | decide and declare the datastores in the runtime contract | the contract covers code only |
| OPERATIONS (50), TAG-05 | build compose **from** the contract | built it from nothing |
| INTEGRATION (70), TAG-00 | check the artifacts agree with the contract | never dispatched; now addressed and rescoped (archive 5.9) |

The runtime observation is the last line, and it is the only one that fired - at the most expensive point.

### 10.5 The scaffold pre-empts the declaration - measured 2026-08-21

10.1 attributes the H2 half to a Jules task on 2026-08-16 05:58. That is no longer the whole account.
Read in the factory's own source today:

- `ProjectFlowService.commitDeterministicJavaScaffoldIfAbsent` commits `pom.xml`, `.gitignore` and
  `src/main/resources/application.properties` into **every new project**, deterministically, before any
  Jules task runs.
- `javaScaffoldPomXml()` declares `com.h2database:h2` and `flyway-core`. **No PostgreSQL driver. No
  Testcontainers.**
- `javaScaffoldApplicationProperties()` writes `spring.datasource.url=jdbc:h2:file:./data/appdb`,
  `spring.datasource.driver-class-name=org.h2.Driver`, and alongside them
  `spring.jpa.hibernate.ddl-auto=validate` and `spring.flyway.enabled=true` - a strict configuration
  aimed at a database that will not be shipped.
- The factory writes no `docker-compose.yml` at all. It is authored later by `BARCAN-TAG-00`, together
  with the runtime contract itself.
- `ProjectFactoryService.registerStandardHotspots` registers `application.properties` and
  `docker-compose.yml` as standard hotspots for every project: **the factory already knows these two are
  the pair that matters.** Nothing requires them to agree.

**Why this changes the diagnosis rather than adding to it.** 10.2 says the factory must not choose a
stack. It chooses one, at bootstrap, in committed files. ARCHITECTURE then cannot *decide* the datastore -
it can only contradict files already on `main` - so the contract stays silent about a question that looks
already answered. The silence at stage 20 is a **consequence** of the decision at bootstrap, not an
independent omission.

This makes the repair cheaper and more certain than 10.4 implies: the first row is one deterministic
method in the factory, not agent behaviour. It also makes the defect **structural for every new project** -
a greenfield repository acquires it at creation, before the first client wish exists.

**What it does not license.** "Scaffold PostgreSQL instead" is the patch 10.2 forbids, and it fails the
greenfield test in the other direction: a brief that wants an embedded store would then carry a server it
never uses. The scaffold's job is to leave the question open while still producing something that builds -
not to answer it earlier and differently.

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
| `GeminiContextService.embed` | vectors for retrieval | **REFUTED 2026-08-23 - see 28.7.** This row was false when written | retrieval has returned an empty list on every call since the quota ran out |

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
## 20. Where the current state is read from

Not stored here. Every snapshot written into this plan was stale within a day and then argued from as if it
were not - twice. The live numbers come from `/api/projects/{id}/dashboard`, the backend log, and
`docker ps`, and they are read at the moment of the question.

What is recorded here instead is what measurement established and what remains owed.

---

## 21. Why the product did not serve - corrected 2026-08-23

The first diagnosis, carried at position one from 2026-08-19, was a datastore disagreement: compose said
PostgreSQL, application config said H2. That was real, and it was not the reason.

**The reason, measured:** `docker-compose.yml` of `test-fiftieth` declares two services, `db` and `backup`,
both `postgres:15-alpine`. There is no application service in it at all. The `backend` appears once, in
`docker-compose.override.yml`, as a bare `eclipse-temurin:21-jre-alpine` with no build, no jar and no
command. And the launcher ran `docker compose -f docker-compose.yml`, which suppresses Docker's automatic
loading of the override - so the only service that could serve HTTP was never started, on any run, for the
life of the project. `_remap_ports` then took the first published port it found, which was PostgreSQL's, and
the factory sent an HTTP health check to the database.

Fixed in the launcher 2026-08-23: the topology is resolved by Compose itself before ports are renumbered,
and the probed port is chosen by what it serves.

---

## 22. Open defects

| # | Defect | Level | State |
| --- | --- | --- | --- |
| O-13 | the host cannot hold the factory and a full `mvn test` at once | factory | mitigated, not fixed: WSL capped at 3500 MB with six containers inside it; the tests still need the containers stopped |
| D-4 | `runQualityGate` is called from one of the five writers of `TaskStatus.done`, and covers five of thirteen roles | delivery | open, untouched by design - changing it moves every role at once |
| F-16 | `KaizenService.evaluateAndStandardize` passes the caller's id to `deleteMatching` as the row to KEEP, and after deduplication that id is not always the persisted one | factory | latent, not live; the edit deletes rows, so which row survives must be measured before it is changed |
| F-17 | `runtime-launcher-workspace` keeps clones of finished projects; nothing removes them | factory | open, harmless |
| F-18 | factory-internal tasks carry criteria about report files, so a diff is the wrong instrument for judging them | delivery | open; closes with D-1 or not at all |
| P-1 | the frontend of `test-fiftieth` has no bundler, so 55 generated design artefacts and the Svelte components written against them cannot reach the runtime | product | open, owed as scope - found by the parallel session 2026-08-26 |

---

## 23. Shipped

In git history with its verification in each commit message, from `19abf10` to `92f4c2f`. Not restated here.

---

## 24. Corrections - claims that measurement refuted

Kept because each one is a claim this plan asserted and measurement then killed. The list is the record of
how this system is wrong, which is the only thing that makes it teachable.

| claim | what refuted it |
| --- | --- |
| a completion handler was unreachable, because `pr_opened` appeared zero times in one log window | a 60-second replay job did the work; absence in a bounded window is not impossibility |
| gating `done` on the product answering | merges two of §2's axes and deadlocks any project from zero - the rule forbids its own precondition |
| the scaffold pre-empted the runtime declaration on `test-forty-ninth` | the scaffold returns at its first line for non-greenfield projects and had never run |
| retrieval was "already paid for and local" (§11.2) | it embeds the QUERY on every call; with the quota exhausted every retrieval returned an empty list, and the corpus reached no prompt for three days |
| the three falsification flags were blank, so the method was off | `value` is null for every boolean flag by design - it is masked, and the answer is in `enabled`. Two were true, one was a stored `false` |
| observation must wait for `DELIVERED`, because assembly-phase samples bias `V_p` downward | §25 forbids it in as many words: **no launch may wait on a completeness metric**. The measurement was right and the conclusion was wrong - see §29 |

---

## 25. Forbidden by construction

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

## 26. Held, on command

Move the factory to a server. Details in the archive.

---

## 27. Delivery is judged against the task's own criterion

Built 2026-08-23, running. A closed task is read against the acceptance criteria it carries and the diff
merged for it, plus the repository's file listing on main - because a diff cannot show what is absent. The
verdict is `SATISFIED`, `REFUTED`, `UNDECIDABLE` or `NOT_JUDGED_NO_DIFF`, the last recorded as a fact and
never as a pass. A refutation is filed as scope carrying the criterion, the pull request and the judgment's
own words.

It rules at the DELIVERY level and does not reuse `JudgmentAgentClient.judge()`, whose system prompt rules
on the FACTORY and says delivery is not its subject. It blocks no transition: refusing on ignorance turns a
safety net into a new way to strand tasks.

**Why it exists.** Measured on `test-fiftieth`: 26 tasks, 13 merged pull requests, and
`applicableChecksByStage.IMPLEMENTATION_RESULT = 0` on all 26. The gates were not weak, they were
unreachable - D-4 above.

---

## 28. Findings that outlived their repairs

The repairs themselves are in git. These are the three that are checks to run, not fixes to remember.

**A detector with a reader and no actor is the same defect as one with no reader.** `done_not_reached_main`
was detected from 2026-07-25, turned into evidence 2026-08-17, and became work only on 2026-08-23. The check
before any detector is called finished: name the `wishlist` row it produces. An evidence node, a log line, a
dashboard field or a metric is not that row.

**A guard must be reachable from its own defect** (ACP-108). Three instances in one day, each found only by
watching the repair fail. State the input the guard refuses, then trace it forward from the caller.

**Scope filed without an epic is work that moves nothing.** A feature closes when ITS tasks close. Inherited
from the task the finding is about, never invented; a finding with no natural parent keeps none, and says so.

---

## 29. Two sessions, one system - 2026-08-27

A parallel session worked this factory from 25 to 27 August and its record is
`docs/E3_EPISTEMIC_ENGINE_PLAN.md`, `docs/architecture/SYSTEM_ARCHITECTURE.md` and
`FLOW_FAILURES_JOURNAL.md`. What it established, and where the two lines of work meet:

**Stratification.** `ℋ = ℋ_Product ∪̇ ℋ_Factory ∪̇ ℋ_Doctrine`, Russell types under a Tarski hierarchy. A
product worker retrieves from `ℋ_Product ∪ ℋ_Doctrine` only, so factory meta-language has measure zero in
its prompts. Diagnosed from PR blockers #298, #300 and #302, where Jules refused to merge on
`Φ_task ∧ ℐ_onto ⊢ ⊥` - a task naming `AutoMergeService` inside the client repository is unsatisfiable.

This composes with the source-type restriction made here on 2026-08-23 rather than colliding with it. The
three lists nest strictly: `ROLE_INDEPENDENT (2) ⊂ PRODUCT_WORKER (5) ⊂ METHOD (9)`, and the difference
between the outer two is exactly the four meta-language types. The factory's own auditor reads the
meta-language; a product worker does not.

**The observation gate, reversed.** `OBSERVE_CLIENT_RUNTIME` no longer requires `DELIVERED`. §25 forbids
gating a launch on a completeness metric, and that rule predates the gate that broke it.

The measurement behind the gate still stands, and it is not an argument for the gate: `V_p` fell
0.2 → 0.167 → 0.143 across three assembly-phase samples, and that posterior drives
`LaunchabilityConstraintService`, so a phase error manufactures scope. The defect is not the observing, it
is pooling two populations into one estimator. Assembly-phase and delivered-phase observations are not
samples of the same quantity, and Invariant 8 applies: state the denominator. **Owed: `V_p` stratified by
phase, observation left continuous.**

**Environment, as the parallel session left it.** `judgment-proxy:8093` replaces the sidecar after the
weekly Claude limit was exhausted. `judgment-sidecar` and `frontend` deliberately down. Four core containers
plus two client containers inside a 3500 MB WSL cap. Host disk recovered to 41 GB. `nano_banana_enabled` and
`gemini_project_observer_enabled` set false.

**Not to be repeated, by the operator's instruction:** do not switch off working mechanisms of the factory.
