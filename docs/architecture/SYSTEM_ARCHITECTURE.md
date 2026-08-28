# Eneik Production System — Master System Architecture & Engineering Specification

**Version:** 2026.08 | **Author:** Factory Architecture Team & CTO  
**Status:** Living Engineering Standard (Strictly Synchronized with Codebase)

---

## 1. System Philosophy & Epistemic Foundations

The Eneik Production System is built on the **anti-patch principle** (*"чистый инженерно-математический подход"*):
* **Epistemic Truth over Assertion:** A declaration without independently verifiable evidence is treated as unknown. A task marked `done` must mean the code is verified in `main`; a service marked `healthy` must mean a live HTTP probe succeeded against the running artifact.
* **Theory of Constraints (TOC):** Throughput is bounded by the primary constraint. All non-bottleneck activities are strictly subordinated to the constraint (e.g. philosophical audits subordinate to product launchability).
* **Sequential Epistemic Convergence:** Knowledge about a product evolves across multi-turn sequential reviews rather than isolated one-off monologues.

```
+----------------------------------------------------------------------------------------------------+
|                                      3-LAYER SIX SIGMA QUALITY MODEL                               |
+----------------------------------------------------------------------------------------------------+
|                                                                                                    |
|  [ LAYER 1: FACTORY META-SCOPE ]                                                                   |
|  * Infrastructure stability, physical RAM preservation, Docker socket privilege isolation          |
|  * Monotonic Epoch Lease (tau = 300s CAS locks) preventing deadlock and zombie claims               |
|  * Scheduler thread-pool starvation prevention, Hikari connection leak detection                   |
|                                                                                                    |
|  [ LAYER 2: DELIVERY SCOPE (6.0 Sigma / Zero-Defect Delivery Target) ]                              |
|  * Task graph decomposition, OpenAPI contract boundary enforcement (BARCAN-TAG-12)                |
|  * Automated PR merge reviews (BARCAN-TAG-00), CI verification, branch garbage collection          |
|  * Autonomous dual-tier arbitration (Claude OAuth primary + Gemini shadow proxy)                   |
|                                                                                                    |
|  [ LAYER 3: PRODUCT SCOPE & RUNTIME REALITY ]                                                      |
|  * JIT Reactive Runtime Observability (Commit-Scoped truth, ephemeral container launch, health)    |
|  * Multi-turn Sequential Philosophical Falsification (13 Barcan charters, 78 thinkers)             |
|  * Forced Kano classification (Must-Be, Performance, Attractive, Indifferent, Reverse)            |
|  * Evidence Coherence Engine (Thagard/ECHO + AGM belief revision + Bovens-Hartmann Bayesian update)|
|                                                                                                    |
+----------------------------------------------------------------------------------------------------+
```

---

## 2. Infrastructure Topology & Service Mesh

The production stack runs 5 core services and a standalone client preview harness:

```
                            +-------------------------------------------+
                            |            OPERATOR & CLIENT              |
                            |   POST /api/wishlist, POST /api/projects  |
                            +---------------------+---------------------+
                                                  |
                                                  v
+-------------------------------------------------+--------------------------------------------------+
|                                    FACTORY BACKEND (:8080)                                         |
|                                                                                                    |
|  * Spring Boot 3.2.2 / Java 17 runtime            * H2 Persistent MVStore (DB_CLOSE_DELAY=-1)      |
|  * HikariCP Pool (24 connections, leak detection) * Flyway migration engine                        |
|  * Flow Core & Continuous Orchestration           * TOC Sentinel Engine                            |
|  * ClientRuntimeObservabilityService              * FalsificationCycleService                      |
|  * EvidenceCoherenceService                       * KaizenService & DefectJournalService           |
+-------------+---------------------+---------------------+--------------------+---------------------+
              |                     |                     |                    |
              v (HTTP :8000)        v (HTTP :8091)        v (HTTP :8092)       v (HTTP :8093)
      +---------------+     +---------------+     +---------------+    +--------------------+
      |   ML SERVICE  |     |    RUNTIME    |     |    JUDGMENT   |    |   JUDGMENT PROXY   |
      |   (Port 8000) |     |    LAUNCHER   |     |    SIDECAR    |    |    (Port 8093)     |
      |               |     |  (Port 8091)  |     |  (Port 8092)  |    |                    |
      | * FastAPI     |     | * Holds host  |     | * Anthropic / |    | * Gemini shadow    |
      | * Gemini 3.5  |     |   Docker sock |     |   Claude Code |    |   fallback when    |
      |   Flash & Pro |     | * Port re-map |     |   OAuth       |    |   Claude limit met |
      | * Embeddings  |     | * JIT probes  |     | * PR review   |    | * Transparent      |
      +---------------+     +---------------+     +---------------+    |   passthrough      |
                                    |                                  +--------------------+
                                    v (Container Lifecycle)
              +---------------------+----------------------------------+
              |           EPHEMERAL CLIENT RUNTIME INSTANCE            |
              |                                                        |
              |  * Frontend Preview Proxy (:13000 -> :18080)           |
              |  * Spring Boot Client Backend (:18080)                 |
              |  * PostgreSQL Database (:18080 remapped)               |
              |  * Backup Cron Container                               |
              +--------------------------------------------------------+
```

---

## 3. Core Engine Components

### 3.1. Theory of Constraints (TOC) Sentinel Engine
* **Token Lifecycle:** Every operational scenario (`AUTOMERGE_CYCLE`, `TASK_CLAIM`, `DECOMPOSITION`) enters with an explicit priority token.
* **Stall Detection:** Latency is dynamically monitored; stalls exceeding $\mu + 3\sigma$ or static thresholds trigger `STALL_BOTTLENECK` alerts.
* **Subordination Rule:** If the client product's last observation is unhealthy, philosophical audits and non-critical operations are subordinated until the product reaches launchable health.

### 3.2. Monotonic Epoch Lease Manager
* **Problem Solved:** Prevents zombie worker claims and unreleased database locks from stalling development.
* **Mathematical Invariant:** Each lease has a finite duration $\tau_{\text{lease}} = 300\text{ s}$.
* **Atomic CAS Update:**
  $$\text{claim}(T) = \begin{cases} \text{SUCCESS}, & \text{if } L_{\text{claimedAt}} = \text{NULL} \lor L_{\text{claimedAt}} < (T - \tau_{\text{lease}}) \\ \text{DENIED}, & \text{otherwise} \end{cases}$$

### 3.3. JIT Reactive Runtime Observability
* **Problem Solved:** Decouples decisions from stale historical database records.
* **Mechanism:** When gating actions (e.g. Turn 1 of falsification), `ClientRuntimeObservabilityService.ensureFreshObservation(project)` evaluates:
  1. Does any observation exist?
  2. Is the latest observation older than 1 hour?
  3. Is the latest observation failed or from an older Git commit ($C_{\text{obs}} \neq C_{\text{head}}$)?
  If yes, it immediately executes an on-demand JIT probe via `RuntimeLauncherClient`.

### 3.4. Multi-Turn Sequential Philosophical Falsification
* **Popperian Refutation:** Audits look for refuting evidence rather than confirming rhetoric.
* **13 Barcan Role Charters (78 Thinkers):**
  * `BARCAN-TAG-00` to `BARCAN-TAG-12` each define 6 historical philosophers (Descartes, Spinoza, Leibniz, Popper, Kant, Wittgenstein, Locke, etc.) reasoning from their complete published worldviews.
* **Forced Kano Taxonomy:**
  * `Must-Be` (Legal duties, dead ends, unconfirmed actions, broken paths).
  * `Performance` (Linear quality scale).
  * `Attractive` (Delighters).
  * `Indifferent` (Zero impact).
  * `Reverse` (**Most valuable:** features whose presence actively harms the product, flagging what to delete).

### 3.5. Epistemic Coherence Engine (Thagard/ECHO + AGM + Bovens-Hartmann)
* **Coherence Networks:** Findings are modeled as nodes in an explanatory coherence graph.
* **AGM Belief Revision:** New empirical refutations cause minimal belief contractions without introducing contradictions.
* **Bayesian Reliability Updates:** Worker posterior reliability $\text{Beta}(\alpha, \beta)$ updates monotonically with verifiable defect discovery.

---

## 4. Operational API Reference

| Endpoint | Method | Payload / Params | Purpose |
|----------|--------|------------------|---------|
| `/api/wishlist` | `POST` | `{"projectId": "...", "content": "...", "source": "client"}` | Enqueue a new user requirement for automated task decomposition. |
| `/api/wishlist` | `GET` | — | List all active and compiled wishlists. |
| `/api/projects/{id}/philosophical-falsification/run` | `POST` | — | Trigger on-demand multi-turn Popperian philosophical falsification. |
| `/api/projects/{id}/runtime-health` | `GET` | — | Retrieve recent container launch and health probe history. |
| `/internal/tasks` | `GET` | — | Query complete internal task graph with execution statuses. |
| `/internal/gemini-observer/persistent-workers` | `GET` | `?projectId=...` | Inspect active long-running persistent worker sessions. |

---

## 5. Summary of Roles & Charters (13 Active Agents)

| Role Tag | Code Name | Primary Responsibility | Group |
|----------|-----------|------------------------|-------|
| `BARCAN-TAG-00` | CODE-GUARDIAN | Tech Lead, Automated PR Review, Six Sigma Gate | A |
| `BARCAN-TAG-01` | ACTUALIST-OBJECT | Solution Architect, Bounded Contexts, Domain Model | A |
| `BARCAN-TAG-02` | RIGID-DESIGNATOR | Backend & Integration Engineer, Spring Boot Services | A |
| `BARCAN-TAG-03` | BELIEF-INTENSION | UI/UX Designer, Design System, Usability & WCAG | C |
| `BARCAN-TAG-04` | MODAL-QUANTIFIER | Data Scientist, ML Models, Semantic Embeddings | B |
| `BARCAN-TAG-05` | NECESSARY-IDENTITY | SRE / DevOps, Docker Compose, Resilience | D |
| `BARCAN-TAG-06` | DEONTIC-CONSISTENCY | QA Automation, Integration Tests, Invariant Verification | D |
| `BARCAN-TAG-07` | SECOND-ORDER-KNOWLEDGE | AppSec / DevSecOps, SAST, Secrets & Auth | D |
| `BARCAN-TAG-08` | SUBSTITUTIVITY-SALVA-VERITATE | Data Engineer / DBA, PostgreSQL Schemas & Migrations | B |
| `BARCAN-TAG-09` | MORAL-DILEMMA | Technical Product Manager, Priority Mediation | A |
| `BARCAN-TAG-10` | DEONTIC-PROHIBITION | Data Governance, GDPR, Legal Compliance & Privacy | E |
| `BARCAN-TAG-11` | CLIENT-PERCEPTION | Frontend Engineer, Svelte/Tailwind Implementation | C |
| `BARCAN-TAG-12` | SOCIAL-CONTRACT | API Contract Designer, Machine-Readable OpenAPI Specs | A |
