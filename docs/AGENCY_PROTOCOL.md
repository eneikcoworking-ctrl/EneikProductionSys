# AGENCY OPERATIONAL PROTOCOL: AUTOMATED AGENTIC WORKFLOW

## 1. ACCOUNT FLUIDITY & ROLE ASSUMPTION

To ensure maximum throughput and eliminate bottlenecks, the agency operates on a **Fluid Role Assumption** model.

### Role Mapping (Linear-to-Agent)
- **Tech Lead & Mediator (TAG-00 / TAG-09)**: Fixed roles. Responsible for task creation, architecture review, and conflict resolution.
- **Specialist Roles (TAG-01 to TAG-11)**: Fluid roles. Any of the 7 unique accounts can "assume" a role by pulling a task from Linear that carries the corresponding tag.

### How to Connect (The Agentic Handshake)
1. **Task Retrieval**: The account queries the Linear API for `In Progress` or `Todo` tasks tagged with its current operational focus.
2. **Context Loading**: Upon picking a task, the account reads the corresponding `BARCAN-TAG-XX.md` file to load the **Absolute Operational Boundaries**.
3. **Execution**: The account performs the work strictly within the directory boundaries defined in `DIRECTORY_MAP.md`.

---

## 2. LINEAR SYNC & AUTOMATION FLOW

The agency uses the existing Linear configuration to drive the development lifecycle.

| Trigger Event | Action | Responsible Agent |
|---------------|--------|-------------------|
| **New Requirement** | Create Epic / Task in Linear | TAG-09 (Mediator) |
| **Task Assignment** | Agent pulls task based on TAG | Fluid Specialist |
| **Commit / Push** | GitHub Action triggers `scripts/audit_pr.py` | Jules (Automated) |
| **Audit Success** | Move Linear task to `Review` | TAG-00 (Tech Lead) |
| **Review Approval** | Merge to `main` & Move Linear to `Done` | TAG-00 |

---

## 3. "HELLO WORLD" LANDING PAGE: TEST PROJECT EXECUTION

The first test project is a "Hello World" Landing Page. This project demonstrates the orchestration of all 12 specialists.

### Specialist Contribution Map (The 12-Layer Impact)
1.  **TAG-01 (Actualist)**: Defines the "Hello World" entity and its ontological status.
2.  **TAG-02 (Rigid Designator)**: Sets the fixed `/hello` route and API contract.
3.  **TAG-03 (Belief Intension)**: Designs the visual "Hello World" perception (Focus: Intention).
4.  **TAG-04 (Modal Quantifier)**: Adds a prediction service for user greeting preference.
5.  **TAG-05 (Necessary Identity)**: Configures the "HelloWorld" CI/CD pipeline.
6.  **TAG-06 (Deontic Consistency)**: Ensures the greeting logic meets consistency rules.
7.  **TAG-07 (Second-Order Knowledge)**: Secures the "Hello" endpoint via JWT/Auth.
8.  **TAG-08 (Substitutivity)**: Maps the greeting data to a persistence schema.
9.  **TAG-09 (Moral Dilemma)**: Resolves the tradeoff between "Hello World" speed vs features.
10. **TAG-10 (Deontic Prohibition)**: Ensures the greeting complies with international law.
11. **TAG-11 (Client Perception)**: Renders the "Hello World" UI with zero lag.
12. **TAG-00 (Code Guardian)**: Performs the final quality audit of the landing page.

---

## 4. SYSTEM EVOLUTION: ENEIKPRODUCTIONSYS

The system continuously improves via the **DMAIC (Define, Measure, Analyze, Improve, Control)** loop integrated into `scripts/audit_pr.py`.

- **Self-Correction**: If a specialist (e.g., TAG-11) repeatedly fails a performance budget audit, the system generates a Linear task for TAG-09 to investigate the process constraint.
- **Knowledge Update**: Successful patterns found by Jules are automatically proposed for the `AGENCY_PROTOCOL.md` or `BARCAN-TAG-XX.md` files.
