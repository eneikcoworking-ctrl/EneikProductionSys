# ENTERPRISE MVC ARCHITECTURE: DIRECTORY MAP & ROLE MAPPING

## 1. MULTI-ACCOUNT OPERATIONAL FLOW (7 ACCOUNTS)

The 12 specialized agents are mapped into 7 operational accounts to optimize concurrent workflows and protect the system constraint (TAG-00).

| Account ID | Primary Role Account | Agents Included | MVC Domain Responsibility |
|------------|----------------------|-----------------|---------------------------|
| **ACC-01** | Tech Lead & Mediator | TAG-00, TAG-09 | **Global Controllers & Routes** (Final Audit/Review) |
| **ACC-02** | Systems Architect    | TAG-01, TAG-02 | **Models & API Contracts** (Core Domain Entities) |
| **ACC-03** | Data & Intelligence  | TAG-04, TAG-08 | **Persistence Models & ML Layer** (Data Science/DB) |
| **ACC-04** | Experience Engineer  | TAG-03, TAG-11 | **Views & Client-Side Logic** (UI/UX) |
| **ACC-05** | Reliability Engineer | TAG-05          | **Infrastructure & CI/CD** (SRE/DevOps) |
| **ACC-06** | Integrity Guardian   | TAG-06, TAG-07  | **Security Controllers & Tests** (QA/AppSec) |
| **ACC-07** | Compliance Officer   | TAG-10          | **Policy Controllers & Audit Logs** (Legal/PII) |

---

## 2. DIRECTORY STRUCTURE (LEAN / TOC ALIGNED)

```text
/
├── .github/                   # CI/CD Workflows (ACC-05: TAG-05)
├── config/                    # System Configurations
│   ├── agents/                # Agent-specific operational boundaries (ACC-01)
│   ├── env/                   # Environment variables (ACC-05)
│   └── sync/                  # Linear/Task sync scripts (ACC-01: TAG-09)
├── docs/                      # Architectural Blueprints & ADRs
├── src/                       # Source Code
│   ├── routes/                # [CONTROLLER LAYER] Entry points & Contracts (ACC-02: TAG-02)
│   ├── controllers/           # [CONTROLLER LAYER] Business Logic Orchestration
│   │   ├── core/              # Primary operations (ACC-01)
│   │   ├── policy/            # Regulatory filters (ACC-07: TAG-10)
│   │   └── auth/              # Security boundaries (ACC-06: TAG-07)
│   ├── models/                # [MODEL LAYER] Domain Entities & State
│   │   ├── domain/            # Pure Business Entities (ACC-02: TAG-01)
│   │   ├── persistence/       # DB Schemas & Mappings (ACC-03: TAG-08)
│   │   └── ml/                # Predictive Models (ACC-03: TAG-04)
│   ├── views/                 # [VIEW LAYER] UI & Interaction
│   │   ├── components/        # Atomic Design System (ACC-04: TAG-03)
│   │   ├── pages/             # Route-mapped layouts (ACC-04: TAG-11)
│   │   └── states/            # Client-side UI state (ACC-04)
│   ├── services/              # Cross-cutting concerns (External Integrations)
│   └── utils/                 # Shared Utilities (Validation, Formatting)
├── tests/                     # ZERO-DEFECT CONTROL LAYER
│   ├── unit/                  # Component-level tests (ACC-02/03/04)
│   ├── integration/           # MVC Pipeline tests (ACC-06: TAG-06)
│   ├── e2e/                   # JTBD User Journey verification (ACC-06: TAG-06)
│   └── security/              # Automated SAST/DAST audits (ACC-06: TAG-07)
├── scripts/                   # Sync & Automation
│   ├── audit_pr.py            # Jules' Automated Audit Script (TAG-00)
│   └── linear_sync.sh         # Task synchronization (TAG-09)
├── BARCAN-TAG-*.md            # Operational boundaries for agents
└── package.json               # Dependency management
```

---

## 3. LEAN/TOC PIPELINE CHECK (CONCURRENCY PROTECTION)

- **Isolation Strategy**: Code separation is strictly enforced at the directory level. `ACC-04` (Views) can work entirely within `src/views/` without blocking `ACC-02` (Models).
- **The Constraint (TAG-00)**: All PRs converge at `scripts/audit_pr.py`. This script performs automated linting/testing (Zero-Defect) *before* TAG-00 (Account 01) performs the final human-representative review.
- **Merge Bottleneck Prevention**: By using **Contract-First Development** (TAG-02), frontend and backend roles work against a mockable interface, allowing true concurrent execution.

---

## 4. ZERO-DEFECT FOLDER ISOLATION

Each account has a dedicated `test/` subdirectory for their domain.
- `tests/unit/models/` -> Managed by ACC-02/03
- `tests/unit/views/` -> Managed by ACC-04
- `tests/integration/` -> Primary workspace for ACC-06 (QA) to ensure MVC connections.

This ensures that a failure in the View layer does not block the Model pipeline during the "Lean" flow.
