# Common Analytic Programming Patterns

These patterns are intentionally shared. They match more than five philosophers in the BARCAN corpus, so they live here instead of being duplicated inside philosopher files.

| ID | Pattern | Technique | Defect Prevention | RAG Rule |
|---|---|---|---|---|
| `ACP-001` | Design by Contract | Assertions and pre/postconditions | Catches invalid state before it leaks across module boundaries. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-002` | Type-Driven Design | Strong domain types and impossible-state encoding | Makes illegal states unrepresentable at compile time where the stack allows it. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-003` | Property-Based Testing | Generated counterexamples over invariant space | Finds edge cases human examples usually miss. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-004` | Model-Based Testing | Executable reference model for behavior | Detects implementation drift against a simpler specification. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-005` | Mutation Testing | Tests must fail when behavior is deliberately damaged | Prevents false confidence from weak assertions. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-006` | Static Analysis Gate | Linters, type checks, SAST and schema checks in CI | Moves defect discovery from runtime into coding time. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-007` | Exhaustive Case Analysis | Closed enums, sealed types and total switches | Stops unhandled states from silently reaching production. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-008` | Immutability by Default | Immutable values except at explicit boundaries | Reduces hidden mutation, race conditions and spooky action at a distance. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-009` | Pure Core, Imperative Shell | Pure decision logic wrapped by IO adapters | Keeps business behavior deterministic and easy to test. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-010` | Boundary Validation | Validate all external input at the edge | Prevents polluted data from entering trusted internals. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-011` | Schema and Contract Validation | OpenAPI, JSON Schema, AsyncAPI or equivalent | Turns interface agreements into machine-checkable facts. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-012` | Consumer-Driven Contract Tests | Provider behavior checked against consumer expectations | Stops backend/frontend and service-to-service drift. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-013` | Semantic Versioning | Public behavior changes only through explicit version rules | Prevents accidental breaking changes. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-014` | Backward Compatibility Window | Deprecated behavior remains available for a planned interval | Lets clients migrate without emergency coordination. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-015` | Idempotency Key | Repeatable commands use stable operation identity | Prevents duplicate writes after retry or network uncertainty. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-016` | Optimistic Concurrency Control | Version or status guard on state transitions | Blocks lost updates and read-then-save races. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-017` | Deterministic State Machine | Explicit states and transition table | Prevents ambiguous lifecycle behavior. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-018` | Transactional Outbox | Persist state change and event publication atomically | Avoids split-brain between database and broker. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-019` | Saga with Compensating Actions | Long workflow split into reversible steps | Contains partial failure in distributed processes. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-020` | Circuit Breaker | Stop calling unhealthy dependencies temporarily | Prevents cascading outages. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-021` | Bulkhead Isolation | Separate resource pools for separate failure domains | Stops one overloaded path from sinking the whole system. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-022` | Retry with Exponential Backoff and Jitter | Bounded retry for transient faults | Reduces thundering herd and retry storms. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-023` | Rate Limiting and Backpressure | Control inflow before queues become unbounded | Protects latency, memory and external APIs. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-024` | Timeout Budgeting | Every remote call has a bounded time contract | Avoids stuck threads and zombie workflows. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-025` | Observability and Traceability | Logs, metrics, spans and correlation IDs | Turns suspicion into inspectable evidence. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-026` | Structured Error Taxonomy | Errors include class, cause, retryability and safe action | Prevents ambiguous recovery behavior. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-027` | Audit Trail | Security and business-critical decisions are recorded | Makes accountability and rollback analysis possible. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-028` | Least Privilege | Grant only the minimum required authority | Limits blast radius after bugs or compromise. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-029` | Zero Trust Verification | No caller, token or payload is trusted by default | Stops confused-deputy and spoofing defects. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-030` | Secrets Isolation | Secrets never enter source, logs or client bundles | Prevents credential leaks. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-031` | Input Canonicalization | Normalize before validation, comparison or authorization | Prevents bypass through alternate representations. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-032` | Safe Output Encoding | Encode per target context | Prevents injection through UI, SQL, shell, logs or URLs. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-033` | Accessibility by Default | WCAG, keyboard flow and touch target checks | Prevents unusable interfaces from passing as complete. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-034` | Golden Master Regression | Preserve known observable behavior during risky change | Catches accidental regressions in legacy surfaces. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-035` | Snapshot with Semantic Assertions | Snapshots are paired with behavior assertions | Prevents brittle visual/text snapshots from hiding real defects. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-036` | Test Data Builder | Named fixtures built through domain factories | Reduces fragile test setup and accidental invalid data. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-037` | Reproducible Seed Injection | Randomness and time are injectable in tests | Removes flakes from nondeterministic logic. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-038` | Containerized Toolchain Contract | Build/test commands run in pinned containers | Prevents PATH, JDK, Node and native binding drift. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-039` | In-Memory Test Datasource | Tests isolate database state from local files | Avoids stale file DB corruption and cross-run contamination. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-040` | Migration Serialization | Schema changes pass through one ordered lane | Prevents competing migrations and Flyway collisions. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-041` | Migration Rollback Plan | Every migration has a tested recovery story | Reduces irreversible production failures. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-042` | Feature Flag Isolation | Incomplete work is hidden behind explicit capability switches | Lets parallel branches land without exposing half-built behavior. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-043` | Canary Release | Expose change to a small monitored population first | Detects production-only failures before full rollout. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-044` | Fast Rollback | Rollback is practiced and bounded by time | Keeps recovery from becoming improvisation. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-045` | Small Vertical PR | One coherent behavior slice per PR | Shrinks review surface and conflict probability. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-046` | Merge Queue Gate | Rebuild and retest on current main before merge | Stops green-but-stale PRs from breaking main. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-047` | Single Writer Ownership | Each shared surface has a declared owner | Prevents parallel agents from editing the same contract blindly. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-048` | CODEOWNERS and Review Routing | Ownership is enforced by repository rules | Ensures the right role sees the risky change. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-049` | Generated Artifact Authority | Generated files are changed only through the generator | Prevents manual drift and regeneration conflicts. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-050` | Append-Only Extension Point | Prefer new files/registrations over editing shared cores | Reduces merge conflicts in parallel work. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-051` | No Shared Constants Drift | Enums and constants have one owner and compatibility tests | Stops semantic divergence behind identical names. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-052` | Route Ownership Registry | Each endpoint has one owning controller or handler | Prevents duplicate-route runtime failures. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-053` | Endpoint Collision Scan | CI checks that no two handlers claim the same method/path | Catches Spring/FastAPI/Express route conflicts before boot. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-054` | Semantic Conflict Test | After text merge, run affected contract and behavior tests | Catches meaning conflicts that Git cannot see. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-055` | Conflict Forecast in PR | PR declares touched paths, owners and integration points | Lets reviewers spot collisions before code lands. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-056` | Conflict Resolution Evidence | Manual conflict resolution must add or run targeted evidence | Prevents guessed merges from entering main. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-057` | No-Op Supersession | Superseded PRs are converted to tested no-op merges or closed | Clears stale work without reintroducing old code. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-058` | Stale Claim Self-Healing | Stuck automation claims are released with evidence | Keeps autonomous queues moving. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-059` | CI Status Reconciliation | System reconciles GitHub state with internal task state | Prevents already-merged work from staying blocked. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |
| `ACP-060` | RAG Source-Grounded Retrieval | Agents cite exact source chunks before applying doctrine | Prevents philosophical slogans from becoming hallucinated rules. | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |

## RAG Retrieval Rule

1. Retrieve this file first for universally reusable engineering practices.
2. Retrieve exactly one philosopher file for individual style and judgment.
3. If a new personal pattern starts fitting more than five philosophers, move it here and replace it in those philosopher files.
4. For parallel development tasks, also retrieve `01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md`.
