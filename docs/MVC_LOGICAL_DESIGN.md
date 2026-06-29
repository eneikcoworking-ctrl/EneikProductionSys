# LOGICAL DESIGN: MVC COMPONENTS & DOMAIN BOUNDARIES

## 1. MODELS: THE STATE & DATA INTEGRITY LAYER
*Primary Owners: ACC-02 (Architect), ACC-03 (Data)*

Models in this architecture are not mere data holders; they are the **Ontological Foundation** of the system (ref: TAG-01).

- **Domain Entities (`src/models/domain/`)**:
  - **Logic**: Encapsulates core business rules (e.g., "A user cannot have two active subscriptions").
  - **Lean/TOC Metrics**: Tracks "Throughput" of entity states (e.g., Lead -> Customer conversion velocity).
  - **Validation**: Strict schema enforcement (ref: TAG-08) and domain-specific invariants.
- **Persistence Layer (`src/models/persistence/`)**:
  - **Logic**: ORM Mappings and Repository Patterns.
  - **Constraint**: No business logic allowed here. Pure CRUD and query optimization.
- **Intelligence Layer (`src/models/ml/`)**:
  - **Logic**: Modal Quantifiers (ref: TAG-04). Bayesian predictors that feed the Model state with probability scores (e.g., "Likelihood of Churn").

---

## 2. VIEWS: THE PHENOMENAL & PERCEPTION LAYER
*Primary Owner: ACC-04 (Experience)*

Views translate complex system states into **JTBD "Customer Wishes"** (ref: TAG-03).

- **UI Structure (`src/views/pages/`)**:
  - **Logic**: Mapping JTBD progress to visible elements. If a user "wants to feel secure," the View renders explicit encryption status indicators.
- **UI-State Mapping (`src/views/states/`)**:
  - **Logic**: Implementation of **Optimistic Updates** (ref: TAG-11). The View assumes success to reduce "Cognitive Friction" while the Model validates in the background.
- **Metric Pipelines**:
  - **Core Web Vitals**: Real-time monitoring of LCP/FID to ensure the "Phenomenal Experience" isn't interrupted by technical lag.

---

## 3. CONTROLLERS: THE ORCHESTRATION & BOUNDARY LAYER
*Primary Owners: ACC-01 (TechLead), ACC-07 (Compliance)*

Controllers act as the **Deontic Consistency** filter (ref: TAG-06). They manage the flow between Perception (Views) and Reality (Models).

- **Business Logic Layer (`src/controllers/core/`)**:
  - **Logic**: Heavy request orchestration. Fetches Model data, applies cross-domain logic, and prepares the View-Model.
- **Boundary Filters (`src/controllers/policy/`)**:
  - **Logic**: **Deontic Prohibition** (ref: TAG-10). Every request passes through a regulatory filter. If a user is from a GDPR region, PII-heavy Model data is masked *before* it reaches the View.
- **Security Orchestration (`src/controllers/auth/`)**:
  - **Logic**: **Zero-Trust Validation** (ref: TAG-07). Checks Second-Order Knowledge (tokens/permissions) before allowing a Route to reach a Controller.

---

## 4. LEAN / SIX SIGMA METRIC INTEGRATION

The architecture treats every MVC interaction as a "Process Step" in a Six Sigma pipeline:

1. **Defect Tracking**: If a Controller fails to map a Model to a View, it is logged as a "Defect" in the `TAG-06` (QA) dashboard.
2. **Cycle Time**: Measured from `Route Arrival` to `View Rendered`.
3. **Throughput (TOC)**: The system capacity is limited by the DB Model write-speed. Controllers use **Asynchronous Queues** to protect the DB constraint.
