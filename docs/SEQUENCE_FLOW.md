# DATA INTERACTION & SEQUENCE FLOW: THE BARCAN PIPELINE

## 1. HIGH-LEVEL REQUEST TRAVERSAL

In this multi-agent environment, a request isn't just data; it's a series of **Semantic Validations**.

```mermaid
sequenceDiagram
    participant U as User (Client Perception)
    participant R as Routes (Rigid Designator)
    participant C as Controllers (Deontic Consistency)
    participant P as Policy Controller (Compliance)
    participant M as Models (Actualist Object)
    participant V as Views (Belief Intension)

    U->>R: 1. Request initiated (JTBD Trigger)
    R->>R: 2. Semantic Mapping (Rigid Designator check)
    R->>C: 3. Route hit (Orchestration begins)

    C->>P: 4. Policy Filter (GDPR/152-FZ Check)
    P-->>C: 5. Approval / Masking Rules

    C->>M: 6. Model State Query (Actualist Logic)
    M->>M: 7. Invariant Validation (Entity integrity)
    M-->>C: 8. Verified Domain Object

    C->>V: 9. View-Model Preparation
    V->>V: 10. Phenomenal Mapping (Accessibility/WCAG)
    V-->>U: 11. Screen Rendered (Optimistic Update confirmed)
```

---

## 2. STEP-BY-STEP LOGIC

### Step 1-3: Routing & Semantic Fixation (TAG-02)
- **Role**: `ACC-02 (Rigid Designator)`
- **Logic**: The request is matched against a **Fixed Designator** (OpenAPI Contract). If the input structure deviates from the contract, the request is rejected *before* it hits business logic (Lean Principle: Early Defect Detection).

### Step 4-5: Deontic Filtering (TAG-10)
- **Role**: `ACC-07 (Compliance)`
- **Logic**: The **Policy Controller** injects "Secondary Rules" (Hart's Principle). It checks if the user's "Modal Context" (Location, Role) permits access to specific Model properties.

### Step 6-8: Model Interaction (TAG-01 / TAG-08)
- **Role**: `ACC-02 / ACC-03`
- **Logic**: The Model is queried. The **Actualist Object** ensures only "existing objects" are handled. If the Model belongs to the "ML Layer" (TAG-04), it calculates the predictive state (e.g., "Likelihood of churn") during the fetch.

### Step 9-11: View Perception (TAG-03 / TAG-11)
- **Role**: `ACC-04`
- **Logic**: The **Belief Intension** layer maps the raw Model data into a "Phenomenal Experience." This includes formatting according to the **Atomic Design System** and ensuring **Web Vitals** aren't compromised during hydration.

---

## 3. CONCURRENCY & PROTECTING THE CONSTRAINT

- **The Constraint**: The write-heavy DB Model (TAG-08).
- **Sequence Protection**:
  1. Requests that modify state (POST/PUT) are queued in the **Controller Layer**.
  2. The Controller returns an "Optimistic View" to the User immediately (TAG-11).
  3. The Model update happens asynchronously, protecting the user from system lag (TOC: Buffer Management).
