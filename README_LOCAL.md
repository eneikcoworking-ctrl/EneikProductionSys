# Eneik Production System — Factory & Local Environment Guide

## 1. System Overview & Architecture

Eneik Production System is an autonomous multi-agent software engineering factory that plans, implements, verifies, falsifies, and delivers production-grade software applications from high-level client wishlists.

The factory runs locally as an orchestrated Docker Compose infrastructure consisting of 5 primary services and a standalone client preview harness:

```
                      +-------------------------------------------------------------+
                      |                      OPERATOR INTERACTION                   |
                      |   POST /api/wishlist, POST /api/projects, Web Dashboard     |
                      +------------------------------+------------------------------+
                                                     |
                                                     v
+----------------------------------------------------+---------------------------------------------------+
|                                       FACTORY BACKEND (Port 8080)                                      |
|                                                                                                        |
|  * Flow Core & Continuous Orchestration         * Theory of Constraints (TOC) Sentinel Engine         |
|  * Monotonic Epoch Lease Manager (CAS locks)    * 3-Layer Six Sigma Quality Model                     |
|  * JIT Reactive Runtime Observability           * Sequential Multi-Turn Philosophical Falsification   |
|  * Persistent Worker Session Coordinator        * Evidence Coherence Service (Thagard/ECHO + AGM)    |
|  * Kaizen 2-Hour Micro-Improvement Service      * Under-the-hood Defect Journal Service               |
+------------+--------------------+-------------------+--------------------+-----------------------------+
             |                    |                   |                    |
             v                    v                   v                    v
     +---------------+    +---------------+   +---------------+   +------------------------------------+
     |   ML SERVICE  |    |    RUNTIME    |   |    JUDGMENT   |   |           JUDGMENT PROXY           |
     |   (Port 8000) |    |    LAUNCHER   |   |    SIDECAR    |   |            (Port 8093)             |
     |               |    |  (Port 8091)  |   |  (Port 8092)  |   |                                    |
     | * Fast-API    |    | * Holds host  |   | * Anthropic / |   | * Gemini fallback for shadow       |
     | * Gemini 3.5  |    |   Docker sock |   |   Claude Code |   |   arbitration when Claude quota    |
     |   Flash & Pro |    | * Port re-map |   |   OAuth       |   |   exhausted; passthrough when OK   |
     | * Semantic    |    | * JIT probes  |   | * Automated   |   +------------------------------------+
     |   Embeddings  |    | * Healthcheck |   |   PR review   |
     +---------------+    +---------------+   +---------------+
                                  |
                                  v (Monitored Runtime Spawn)
             +--------------------+------------------------------------+
             |            STANDALONE CLIENT PRODUCT INSTANCE           |
             |                                                         |
             |  * Frontend Preview Proxy: http://localhost:13000       |
             |    - /registration-harness.html (RegistrationForm)      |
             |    - /dossier-harness.html (DossierSearch & Summary)    |
             |    - /privacy-harness.html (PrivacySettings)            |
             |    - /test-harness.html (CatalogSearch & Filtering)     |
             |  * Client Spring Boot Backend: http://localhost:18080   |
             |  * Client PostgreSQL DB: Port 18080 (remapped)          |
             |  * Automated Backup Cron Container                      |
             +---------------------------------------------------------+
```

---

## 2. Infrastructure Services & Ports

| Service Name | Port | Base Technology | Purpose |
|--------------|------|-----------------|---------|
| `backend` | `8080` | Spring Boot 3.2.2 (Java 17, H2 MVStore, Flyway) | Core factory lifecycle, task graph planning, TOC sentinel, continuous orchestration, multi-turn falsification. |
| `ml` | `8000` | Python 3.12, FastAPI, Google Gemini SDK | Semantic retrieval, contextual augmentation, embedding calculations, and charter-grounded prompt preparation. |
| `runtime-launcher` | `8091` | Python 3.12, FastAPI, Docker socket client | Isolated sidecar with host Docker socket. Ephemeral cloning, container port remapping (`18080+`), JVM cold-start probing, and health observation. |
| `judgment-sidecar` | `8092` | Node.js 20, Claude CLI / Anthropic OAuth | Automated invariant transition and PR merge review judgment using Claude credentials. |
| `judgment-proxy` | `8093` | Node.js 20, Gemini fallback proxy | High-availability judgment fallback routing to Gemini when Claude quotas are met, with zero backend restarts required. |
| `frontend_proxy` | `13000` | Python 3.12 HTTP Reverse Proxy | Serves client standalone Svelte component harnesses and proxies `/api/*` calls directly to client backend (`18080`). |

---

## 3. Prerequisites & Quick Start

### Prerequisites
* Docker Desktop running on host (Linux containers mode).
* Python 3.10+ (for local helper utilities and reverse proxy).
* Minimum recommended host RAM: 8 GB.

### Starting the Factory Stack

To start all core services cleanly in background:
```bash
docker compose up -d backend ml runtime-launcher judgment-proxy
```

> **Memory Preservation Directive:** Keep `judgment-sidecar` stopped (`docker stop eneikproductionsys-judgment-sidecar-1`) to conserve ~400 MB RAM when running on resource-constrained hosts (~8 GB total), allowing `judgment-proxy` to arbitrate shadow verdicts via Gemini.

### Verifying Service Health

```bash
# Check Docker container status
docker ps

# Check factory backend health
curl http://localhost:8080/health

# Check ML service docs
curl http://localhost:8000/docs

# Check Runtime Launcher OpenAPI spec
curl http://localhost:8091/openapi.json

# Check Judgment Proxy health
curl http://localhost:8093/health
```

---

## 4. Operational API & Workflows

### 4.1. Client Wishlist Management

Submit a new client requirement or feature request:
```bash
curl -X POST http://localhost:8080/api/wishlist \
  -H "Content-Type: application/json" \
  -d '{
    "projectId": "a716e82e-f4e2-4486-93bd-33f1e498386e",
    "content": "Справки по сотрудникам, за период, по месяцам, по научным направлениям с фильтрацией типов документов и экспортом в PDF",
    "source": "client"
  }'
```

List all wishlists:
```bash
curl http://localhost:8080/api/wishlist
```

### 4.2. JIT Reactive Runtime Observability

Request an immediate, on-demand real observation of the client product's running containers:
```bash
curl http://localhost:8080/api/projects/a716e82e-f4e2-4486-93bd-33f1e498386e/runtime-health
```

### 4.3. Multi-Turn Philosophical Falsification

Trigger a sequential Popperian/Kano philosophical audit round for the active project:
```bash
curl -X POST http://localhost:8080/api/projects/a716e82e-f4e2-4486-93bd-33f1e498386e/philosophical-falsification/run
```

Check active persistent workers executing philosophical audits or wishlist compilation:
```bash
curl http://localhost:8080/internal/gemini-observer/persistent-workers?projectId=a716e82e-f4e2-4486-93bd-33f1e498386e
```

### 4.4. Internal Task Graph & Flow Spine

Inspect real task statuses across all factory features:
```bash
curl http://localhost:8080/internal/tasks
```

---

## 5. Mathematical & Engineering Foundations

1. **3-Layer Six Sigma Model**:
   * **Layer 1 (Factory Meta-Scope)**: Infrastructure reliability, scheduler saturation, database lease monotonic bounds ($\tau = 300\text{s}$), RAM conservation.
   * **Layer 2 (Delivery Scope)**: Quality gates, PR merge reviews, contract synchronization, CI verification ($6.0\sigma$ zero-defect delivery target).
   * **Layer 3 (Product Scope)**: Real user functionality, UI usability, end-to-end user workflows, legal/regulatory compliance duties (GDPR/BGB).

2. **Theory of Constraints (TOC) Sentinel**:
   * Continuous token tracking (`TocSentinelService`) across pipeline stages (`AUTOMERGE_PROCESSING`, `TASK_CLAIM`, `DECOMPOSITION`).
   * Dynamic stall detection ($\mu + 3\sigma$) identifying bottleneck nodes.
   * Strict subordination (e.g. philosophical audits strictly subordinate to launchability constraints).

3. **Epistemic Coherence & Anti-Patch Principle**:
   * Real observations are commit-scoped ($C_{\text{sha}}$).
   * Stale multi-day records never block fresh commits; JIT reactive verification probes live truth dynamically.
   * Forced Kano classification (`Must-Be`, `Performance`, `Attractive`, `Indifferent`, `Reverse`) where `Reverse` identifies features to prune.
