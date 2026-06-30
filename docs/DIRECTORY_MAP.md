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

## 2. DIRECTORY STRUCTURE (SPRING BOOT / SVELTE / LEAN / TOC ALIGNED)

```text
/
├── .github/                   # CI/CD Workflows (ACC-05: TAG-05)
├── config/                    # System Configurations
│   ├── agents/                # Agent-specific operational boundaries (ACC-01)
│   ├── env/                   # Environment variables (ACC-05)
│   └── sync/                  # Linear/Task sync scripts (ACC-01: TAG-09)
├── docs/                      # Architectural Blueprints & ADRs
├── src/main/java/com/eneik/production/
│   ├── controllers/           # [CONTROLLER LAYER] Business Logic Orchestration
│   │   ├── policy/            # Regulatory filters (ACC-07: TAG-10)
│   │   └── GreetingController.java # Primary operations (ACC-01/TAG-02)
│   ├── models/                # [MODEL LAYER] Domain Entities & State
│   │   ├── domain/            # Pure Business Entities (ACC-02: TAG-01)
│   │   ├── persistence/       # DB Schemas & Mappings (ACC-03: TAG-08)
│   │   └── ml/                # Predictive Models (ACC-03: TAG-04)
│   ├── services/              # Cross-cutting concerns (External Integrations)
│   └── utils/                 # Shared Utilities (Validation, Formatting)
├── src/main/resources/        # App configurations & SQL
├── frontend/                  # [VIEW LAYER] Svelte Application (ACC-04)
│   ├── src/                   # Svelte Components & Logic
│   └── package.json           # Frontend dependencies
├── src/test/java/             # Java Integration/Unit tests
├── tests/                     # ZERO-DEFECT CONTROL LAYER (Global/E2E)
│   ├── integration/           # Cross-service tests
│   └── security/              # Automated SAST/DAST audits
├── scripts/                   # Sync & Automation
├── pom.xml                    # Maven Dependency Management
├── 00_INTEGRATION_CONTRACT.md # Final Source of Truth
├── BARCAN-TAG-*.md            # Operational boundaries for agents
└── README_BRAND-OS_AGENTS.md  # Global Agent Map
```

---

## 3. LEAN/TOC PIPELINE CHECK (CONCURRENCY PROTECTION)

- **Isolation Strategy**: Code separation is strictly enforced at the package level in Java and the `frontend/` directory for UI.
- **The Constraint (TAG-00)**: All PRs converge at the automated audit script. This performs automated linting/testing (Zero-Defect) *before* TAG-00 (Account 01) performs the final human-representative review.
- **Merge Bottleneck Prevention**: By using **Contract-First Development** (TAG-02), frontend and backend roles work against a mockable interface, allowing true concurrent execution.
