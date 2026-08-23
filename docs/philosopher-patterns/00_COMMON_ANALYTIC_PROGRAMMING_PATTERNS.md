# Common Analytic Programming Patterns

These patterns are intentionally shared. A concrete reusable practice belongs here when it fits more than 5 philosophers in the BARCAN corpus, so it is not duplicated inside philosopher files.

| ID | Pattern | Technique | Defect Prevention | RAG Rule |
|---|---|---|---|---|
| `ACP-001` | Design by Contract | Assertions and pre/postconditions | Catches invalid state before it leaks across module boundaries. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-002` | Type-Driven Design | Strong domain types and impossible-state encoding | Makes illegal states unrepresentable at compile time where the stack allows it. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-003` | Property-Based Testing | Generated counterexamples over invariant space | Finds edge cases human examples usually miss. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-004` | Model-Based Testing | Executable reference model for behavior | Detects implementation drift against a simpler specification. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-005` | Mutation Testing | Tests must fail when behavior is deliberately damaged | Prevents false confidence from weak assertions. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-006` | Static Analysis Gate | Linters, type checks, SAST and schema checks in CI | Moves defect discovery from runtime into coding time. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-007` | Exhaustive Case Analysis | Closed enums, sealed types and total switches | Stops unhandled states from silently reaching production. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-008` | Immutability by Default | Immutable values except at explicit boundaries | Reduces hidden mutation, race conditions and spooky action at a distance. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-009` | Pure Core, Imperative Shell | Pure decision logic wrapped by IO adapters | Keeps business behavior deterministic and easy to test. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-010` | Boundary Validation | Validate all external input at the edge | Prevents polluted data from entering trusted internals. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-011` | Schema and Contract Validation | OpenAPI, JSON Schema, AsyncAPI or equivalent | Turns interface agreements into machine-checkable facts. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-012` | Consumer-Driven Contract Tests | Provider behavior checked against consumer expectations | Stops backend/frontend and service-to-service drift. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-013` | Semantic Versioning | Public behavior changes only through explicit version rules | Prevents accidental breaking changes. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-014` | Backward Compatibility Window | Deprecated behavior remains available for a planned interval | Lets clients migrate without emergency coordination. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-015` | Idempotency Key | Repeatable commands use stable operation identity | Prevents duplicate writes after retry or network uncertainty. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-016` | Optimistic Concurrency Control | Version or status guard on state transitions | Blocks lost updates and read-then-save races. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-017` | Deterministic State Machine | Explicit states and transition table | Prevents ambiguous lifecycle behavior. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-018` | Transactional Outbox | Persist state change and event publication atomically | Avoids split-brain between database and broker. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-019` | Saga with Compensating Actions | Long workflow split into reversible steps | Contains partial failure in distributed processes. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-020` | Circuit Breaker | Stop calling unhealthy dependencies temporarily | Prevents cascading outages. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-021` | Bulkhead Isolation | Separate resource pools for separate failure domains | Stops one overloaded path from sinking the whole system. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-022` | Retry with Exponential Backoff and Jitter | Bounded retry for transient faults | Reduces thundering herd and retry storms. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-023` | Rate Limiting and Backpressure | Control inflow before queues become unbounded | Protects latency, memory and external APIs. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-024` | Timeout Budgeting | Every remote call has a bounded time contract | Avoids stuck threads and zombie workflows. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-025` | Observability and Traceability | Logs, metrics, spans and correlation IDs | Turns suspicion into inspectable evidence. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-026` | Structured Error Taxonomy | Errors include class, cause, retryability and safe action | Prevents ambiguous recovery behavior. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-027` | Audit Trail | Security and business-critical decisions are recorded | Makes accountability and rollback analysis possible. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-028` | Least Privilege | Grant only the minimum required authority | Limits blast radius after bugs or compromise. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-029` | Zero Trust Verification | No caller, token or payload is trusted by default | Stops confused-deputy and spoofing defects. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-030` | Secrets Isolation | Secrets never enter source, logs or client bundles | Prevents credential leaks. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-031` | Input Canonicalization | Normalize before validation, comparison or authorization | Prevents bypass through alternate representations. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-032` | Safe Output Encoding | Encode per target context | Prevents injection through UI, SQL, shell, logs or URLs. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-033` | Accessibility by Default | WCAG, keyboard flow and touch target checks | Prevents unusable interfaces from passing as complete. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-034` | Golden Master Regression | Preserve known observable behavior during risky change | Catches accidental regressions in legacy surfaces. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-035` | Snapshot with Semantic Assertions | Snapshots are paired with behavior assertions | Prevents brittle visual/text snapshots from hiding real defects. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-036` | Test Data Builder | Named fixtures built through domain factories | Reduces fragile test setup and accidental invalid data. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-037` | Reproducible Seed Injection | Randomness and time are injectable in tests | Removes flakes from nondeterministic logic. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-038` | Containerized Toolchain Contract | Build/test commands run in pinned containers | Prevents PATH, JDK, Node and native binding drift. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-039` | In-Memory Test Datasource | Tests isolate database state from local files | Avoids stale file DB corruption and cross-run contamination. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-040` | Migration Serialization | Schema changes pass through one ordered lane | Prevents competing migrations and Flyway collisions. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-041` | Migration Rollback Plan | Every migration has a tested recovery story | Reduces irreversible production failures. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-042` | Feature Flag Isolation | Incomplete work is hidden behind explicit capability switches | Lets parallel branches land without exposing half-built behavior. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-043` | Canary Release | Expose change to a small monitored population first | Detects production-only failures before full rollout. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-044` | Fast Rollback | Rollback is practiced and bounded by time | Keeps recovery from becoming improvisation. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-045` | Small Vertical PR | One coherent behavior slice per PR | Shrinks review surface and conflict probability. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-046` | Merge Queue Gate | Rebuild and retest on current main before merge | Stops green-but-stale PRs from breaking main. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-047` | Single Writer Ownership | Each shared surface has a declared owner | Prevents parallel agents from editing the same contract blindly. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-048` | CODEOWNERS and Review Routing | Ownership is enforced by repository rules | Ensures the right role sees the risky change. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-049` | Generated Artifact Authority | Generated files are changed only through the generator | Prevents manual drift and regeneration conflicts. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-050` | Append-Only Extension Point | Prefer new files/registrations over editing shared cores | Reduces merge conflicts in parallel work. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-051` | No Shared Constants Drift | Enums and constants have one owner and compatibility tests | Stops semantic divergence behind identical names. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-052` | Route Ownership Registry | Each endpoint has one owning controller or handler | Prevents duplicate-route runtime failures. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-053` | Endpoint Collision Scan | CI checks that no two handlers claim the same method/path | Catches Spring/FastAPI/Express route conflicts before boot. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-054` | Semantic Conflict Test | After text merge, run affected contract and behavior tests | Catches meaning conflicts that Git cannot see. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-055` | Conflict Forecast in PR | PR declares touched paths, owners and integration points | Lets reviewers spot collisions before code lands. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-056` | Conflict Resolution Evidence | Manual conflict resolution must add or run targeted evidence | Prevents guessed merges from entering main. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-057` | No-Op Supersession | Superseded PRs are converted to tested no-op merges or closed | Clears stale work without reintroducing old code. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-058` | Stale Claim Self-Healing | Stuck automation claims are released with evidence | Keeps autonomous queues moving. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-059` | CI Status Reconciliation | System reconciles GitHub state with internal task state | Prevents already-merged work from staying blocked. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-060` | RAG Source-Grounded Retrieval | Agents cite exact source chunks before applying doctrine | Prevents philosophical slogans from becoming hallucinated rules. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-061` | Hoare Triple Review | State precondition, command and postcondition before touching critical code | Prevents code that is locally plausible but globally unproved. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-062` | Temporal Specification | Describe safety and liveness properties for workflows | Catches impossible lifecycle promises and stuck-state defects early. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-063` | Model Checking | Explore finite state spaces with tools such as TLA+, Alloy or equivalent | Finds interleavings and corner states missed by example tests. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-064` | State-Space Reduction | Collapse irrelevant states before verification | Keeps formal checks tractable without losing the defect class under review. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-065` | Algebraic Data Types | Represent alternatives as tagged sums and products | Prevents invalid combinations from being representable. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-066` | Refinement Types | Attach predicates to values where the language or tooling supports it | Moves range, format and permission defects into type or check time. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-067` | Dependent Type Boundary | Use proof-carrying values at the highest-risk interfaces | Prevents consumers from assuming facts that were never established. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-068` | SMT Constraint Check | Encode conflicting rules as satisfiability constraints | Finds inconsistent requirements before implementation spreads them. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-069` | Abstract Interpretation | Approximate program behavior over safe abstract domains | Detects classes of runtime errors without executing every path. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-070` | Separation Logic Ownership | Prove mutable resources have non-overlapping owners | Prevents aliasing bugs, leaks and hidden shared mutation. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-071` | Linear Resource Discipline | Use single-use or affine ownership for scarce effects | Stops duplicate sends, double frees and repeated irreversible actions. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-072` | Lock Ordering Protocol | Acquire shared locks in one global order | Prevents deadlocks during parallel execution. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-073` | Lease Fencing Token | Every worker mutation carries a monotonic lease token | Prevents stale workers from overwriting newer authority. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-074` | Monotonic State Design | Prefer state transitions that only move forward in a lattice | Prevents rollback races and non-convergent replicas. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-075` | CRDT Convergence | Use commutative, associative and idempotent merge operations | Lets distributed edits converge without central locking. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-076` | Lattice-Based Merge | Represent partial knowledge with join/meet operations | Prevents ad hoc conflict resolution from losing information. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-077` | Deterministic Replay | Persist inputs, time and decisions enough to replay incidents | Turns intermittent production failures into reproducible traces. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-078` | Event Sourcing with Projection Tests | Derive current state from an append-only event log | Prevents silent history loss and makes state reconstruction auditable. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-079` | Metamorphic Testing | Assert relations between transformed inputs and outputs | Finds bugs when no single oracle is available. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-080` | Differential Testing | Compare independent implementations or versions on the same cases | Finds semantic drift across adapters, clients and runtimes. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-081` | Coverage-Guided Fuzzing | Generate hostile inputs guided by executed paths | Finds parser, validation and memory defects missed by examples. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-082` | Combinatorial Interaction Testing | Cover pairwise or t-wise parameter interactions | Reduces configuration defects without exhaustive test explosion. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-083` | Symbolic Execution | Explore path conditions instead of concrete examples only | Finds branch-specific defects before production traffic finds them. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-084` | Taint Tracking | Mark untrusted data and verify every sink is protected | Prevents injection and authorization bypass through hidden data flow. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-085` | Information Flow Control | Enforce allowed movement between confidentiality and integrity levels | Prevents sensitive or untrusted data from crossing forbidden boundaries. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-086` | Capability-Based Authority | Pass explicit unforgeable permissions instead of ambient authority | Prevents confused-deputy and over-permission defects. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-087` | Formal Grammar Boundary | Parse external languages with an explicit grammar | Prevents partial parser acceptance and ambiguous syntax handling. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-088` | Parser Serializer Round Trip | Parse, serialize and parse again with semantic equality checks | Prevents lossy transformations and migration corruption. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-089` | Canonical Intermediate Representation | Normalize equivalent forms before comparison and transformation | Prevents duplicate identities and representation-dependent behavior. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-090` | Total Function Interface | Every public function defines behavior for every input class | Prevents implicit undefined behavior at module boundaries. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-091` | Option and Result Types | Represent absence and failure explicitly | Prevents null dereferences and swallowed errors. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-092` | Resource Scope Guard | Bind acquisition and release to lexical or transaction scope | Prevents leaks after exceptions and early returns. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-093` | Compatibility Matrix | Track supported producer/consumer and schema version pairs | Prevents accidental deployment of incompatible components. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-094` | Ontology Registry | Centralize domain vocabulary, ownership and canonical meanings | Prevents duplicate concepts with different operational semantics. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-095` | Trace Context Propagation | Carry correlation context through every async and remote boundary | Prevents orphaned logs and untraceable workflow failures. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-096` | SLO Error Budget Gate | Use service objectives to decide release and rollback policy | Prevents local feature progress from consuming reliability silently. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-097` | Chaos Experiment | Inject controlled dependency, latency and infrastructure faults | Finds resilience gaps before real incidents compound them. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-098` | Kill Switch | Every risky external effect has a fast disable path | Prevents prolonged damage from a bad deployment or dependency change. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-099` | Shadow Traffic Verification | Run new behavior beside old behavior before user-visible cutover | Finds semantic differences without exposing clients. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-100` | Canary Invariant Monitor | Bind rollout progression to live invariant checks | Prevents a canary from advancing after hidden correctness drift. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-101` | Verdict Carries Its Subject | A recorded judgement stores what it examined, not only what it concluded | Stops two different acts of judgement from collapsing into one indistinguishable record, so a narrow verdict cannot be read as a broad one. | Retrieve as common background; do not copy into a philosopher's personal patterns. |
| `ACP-102` | Criterion Is Not The Concept | An operational test stands for a concept only over the class of bearers it was calibrated on | Stops a criterion that is co-extensional with a concept for one kind of subject from being applied to every kind, where it silently changes the truth value. | Retrieve as common background; do not copy into a philosopher's personal patterns. |

## RAG Retrieval Rule

1. Retrieve this file first for universally reusable engineering practices.
2. Retrieve exactly one philosopher file for individual style and judgment.
3. If a new concrete personal pattern starts fitting more than 5 philosophers, move it here and replace it in those philosopher files.
4. For mathematical assignment and QA rules, retrieve `02_RAG_MATHEMATICAL_ASSIGNMENT_MODEL.md`.
5. For parallel development tasks, also retrieve `01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md`.

---

## ACP-101 — Verdict Carries Its Subject

**Publication anchor:** J. L. Austin, *How to Do Things with Words* — felicity conditions of a
performative. **Added 2026-08-18** from a live incident in this system, recorded in
`docs/reports/WORKPLAN_2026-08-15_repair.md`.

### The rule

When code records a judgement — passed, approved, verified, accepted, reviewed — the record must state
**what was examined**, not only what was concluded. A verdict without its subject is not a weaker
verdict; it is a different kind of thing, because two acts of judgement over different subjects become
one indistinguishable row.

**Proof obligation:** point to the field, column or report key that names the scope of the judgement,
and show that two judgements made in different circumstances produce distinguishable records.

### Why it is not `ACP-015`, `ACP-016` or `PERFORMATIVE_COMMIT`

`DZHON_OSTIN_10_PERFORMATIVE_COMMIT` already requires that *a declaration have its operational
consequence*: declare a status, and the transition, event or gate it performs must exist. That runs
**forwards**, from the declaration to the world.

This pattern is its converse. The declaration exists, the consequence exists, each write is correct —
and the record still misleads, because it does not say **which declaration it was**. Austin's own
distinction covers it: an utterance's force depends on the circumstances of its utterance, so "I
verify this" spoken at two different moments about two different things is **two acts**, and a record
that keeps only the words has lost the act.

### The incident that produced it

`GateOrchestrator` has two public entry points writing one boolean into one field:

```
runTaskSpecGate(task)   called at task CREATION     -> "this task is well specified"
runQualityGate(task)    called at implementer FINISH -> "the work passed every applicable check"
```

Six readers consume that field as the second meaning. A task can therefore be recorded as verified
having delivered nothing — measured live: `f163e834`, status `done`, `qualityGatePassed = true`, no
claim, no session, no PR, zero mentions in the log. Its flag was written two seconds after creation and
never revisited. Across the whole project the gap between a task's creation and its gate log is 2–5
seconds, which is that same creation-time gating.

**Both writes were true.** That is the difficulty this pattern exists for: the defect cannot be found
by asking whether a value is correct, only by asking what it is about. No test of truth would have
caught it.

### How it was discharged

The report gained the stages the verdict covers. One line; the boolean untouched; no reader's behaviour
changed. What changed is that *verified* can now be asked of a specific question, and a task whose only
gate log carries `[TASK_SPEC]` has never been verified for delivery — a readable fact instead of an
inference from timestamps.

### Where to apply it

Any field whose name is a past participle of judgement — `passed`, `approved`, `validated`, `checked`,
`reviewed`, `accepted` — and which is written from more than one call site, or at more than one moment
in an object's life. If the writes cannot be told apart afterwards, the record is incomplete even when
every write was correct.

---

## ACP-102 — Criterion Is Not The Concept

**Publication anchor:** Gottlob Frege, *Über Sinn und Bedeutung* — sense, reference and the failure of
substitutivity outside a shared context. **Role grounding:** `BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE`,
Готлоб Фреге, «Принцип разграничения смысла и значения»; anchored on
`GOTLOB_FREGE_01_SUBSTITUTION_ORACLE` (D009 substitution failure) and
`GOTLOB_FREGE_09_SENSE_REFERENCE_SPLIT`. **Added 2026-08-19** from a live measurement in this system.

### The rule

A concept the system reasons with — *delivered*, *done*, *healthy*, *complete*, *reviewed* — is normally
operationalised by a concrete test: the PR contains code, the endpoint returned 200, the file exists. The
test and the concept agree **only over the class of bearers the test was calibrated on**. Outside that
class they come apart, and because the test keeps returning a clean boolean, nothing announces that it is
now answering a different question.

So: a criterion may be substituted for its concept only where the class of bearers is declared and the
bearer belongs to it. Where bearers differ in kind, the criterion must be **relative to the bearer's
declared kind**, not global.

**Proof obligation:** name the class of bearers over which the criterion and the concept are
co-extensional, and show what the criterion returns for a bearer outside that class. If that answer is
wrong, the criterion is not the concept and must be indexed by kind.

### Why it is not `ACP-101`, and not `GOTLOB_FREGE_06`

`ACP-101` (Verdict Carries Its Subject) is about a **record** losing which act produced it. Here every
record is complete and honest; the defect is upstream, in the **predicate** — it was never true of the
whole domain it is applied to.

`GOTLOB_FREGE_06_LEVEL_OF_ABSTRACTION_LOCK` forbids mixing claims from different abstraction levels. That
is the special case where bearers differ **by level**. This pattern is the general case: bearers can
differ by kind at the same level — a content role and an implementation role sit at the same level of the
same flow and still have different delivery artifacts.

### The incident that produced it

`ClientDeliverableReadinessService.requiresCodeForDelivery` operationalises *delivered* as *the merged PR
contains code*, exempting one role tag and the spec stages. `CodeChangeClassifier` decides "contains code"
by a deny-list: `.md`, `README`, `design/draft|approved/`, `.eneik/` and generated artifacts are not code;
everything else is.

Both are correct for an implementation role. For a role whose delivery artifact is prose — copy, content,
a written specification shipped as markdown — the same test reports *nothing delivered* about work that
was fully delivered. Measured on the active project: of 99 tasks recorded `done`, 54 had no merged PR
containing code; 47 of those are the DECISION stage, which is `specOnly` and therefore correct, and 5 are
genuine phantom deliveries. The criterion happened to be right for 94 of 99 — which is exactly why it
survived: a criterion that is nearly always co-extensional is the hardest kind to catch.

The danger is not the miscount. It is that a repair built on that criterion — retire the attempt and put
the requirement back in the flow — would have destroyed real content work as though nothing had arrived,
repeatedly, until its retry bound was spent.

### The general form for products

Each role declares the artifact that constitutes **its** delivery: code, content, specification, build
configuration, verification evidence. One predicate still owns the question (one point of application),
but it asks the bearer what counts before it answers. There are then no roles "exempt from delivery" —
only roles delivering different kinds of thing, which is the honest description of what was always true.

## ACP-107 — Retrieval Is Local, Judgment Is Subscribed

### The rule

A product that reasons over its own corpus - market data, competitors, demand, customer records, its own
documents - splits that work in two and never lets the halves share a dependency.

**Retrieval runs locally, always, for free.** Embeddings come from a small ONNX model shipped inside the
product. No account, no key, no quota, no network. Search, ranking, clustering and "find me things like
this" work on day one, offline, for every user, forever.

**Judgment is subscribed, and the subscription is the customer's.** Anything that forms an opinion - reads
the retrieved material and rules on it - runs against a model the user connects in the admin panel with
their own subscription. The product ships with no vendor key and meters nothing.

### Why the two must not share a dependency

They are different kinds of act and only one of them can be false.

Retrieval answers *what is relevant*, and its output is a ranked list that the next step can check.
Judgment answers *what follows*, and its output is a claim. Routing retrieval through a judgment model
looks like an upgrade and is a category error: it makes every search a paid, slow, rate-limited opinion,
and it makes the product stop working entirely when the account behind it lapses.

Measured, 2026-08-20 to 2026-08-23, in the factory that produced this pattern. Retrieval over a 140-file
corpus embedded the QUERY through a metered API on every call and returned an empty list when that call
failed:

    float[] queryVector = mlPredictionServiceClient.embed(query);
    if (queryVector == null) {
        return List.of();
    }

The account ran out of credit. For three days every prompt in the system went out with no corpus behind it
at all - no pattern, no role definition, none of the method the system exists to apply. Nothing raised an
alarm, because an empty list is also what "nothing relevant was found" looks like. **A failure state that
is indistinguishable from a healthy one cannot be refuted, and therefore cannot be noticed.**

### What it buys the product

The product is fully useful with no AI account attached: it searches, it groups, it measures, it compares.
Then the customer connects their own subscription in the admin panel and the same product starts forming
opinions on top of what it already retrieves. Nothing about the base product degrades if they never do, and
nothing about it stops if they cancel.

For the vendor this removes per-user metering entirely - the expensive half is paid for by whoever uses it.
For the customer it removes the question of what the vendor does with their key.

### Choosing the local model

By measured size against measured need, never by name. In the reference implementation fastembed offered
three multilingual models: 384 dimensions at 0.22 GB, 768 at 1.0 GB, and 1024 at 2.24 GB. The smallest was
chosen because the host had already lost a container to memory pressure - the ranking quality difference
did not outweigh a fivefold difference in footprint. ONNX rather than torch for the same reason: about
200 MB against roughly 1.5 GB.

Multilingual is not optional where the corpus or the customer is not English. An English-only model does
not refuse a Russian query - it ranks it badly and silently, which is this same pattern's own failure one
level down.

### The invariant that comes with it

Vectors from two different models cannot be compared, and the comparison **does not fail**: cosine
similarity over mismatched dimensions returns 0.0, which reads as "not similar". Any product using this
pattern must store the embedding model's identity and dimension alongside every vector, refuse comparison
across a mismatch loudly rather than scoring it zero, and reindex when the model changes rather than
waiting to be told.

### Where to apply it

Every Eneik product that measures a market, ranks competitors, groups customer feedback, or searches its
own documents. The admin panel exposes one screen: connect your model subscription. Everything below that
screen already works.
