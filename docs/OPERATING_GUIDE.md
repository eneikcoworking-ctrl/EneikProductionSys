# AGENCY OPERATING GUIDE: HOW TO USE THE SYSTEM

This guide explains how to operate the EneikProductionSys agency, from task creation to automated deployment.

## 1. FOR HUMAN STEERAGE (TECH LEAD / MEDIATOR)

### Step 1: Task Initialization (Linear)
- Log into Linear.
- Create a new task.
- **Critical**: Attach the correct `BARCAN-TAG-XX` label. If the task is complex, TAG-09 (Mediator) must break it down into atomic tasks for different specialists.

### Step 2: Architecture Oversight
- Monitor the `docs/DIRECTORY_MAP.md` for any changes.
- TAG-00 (Tech Lead) performs the final human-representative review on all PRs that have passed Jules' automated audit.

---

## 2. FOR AGENTIC OPERATORS (SPECIALISTS)

### Step 1: Assumption of Role
- Query Linear for tasks tagged with your current capability.
- Read the corresponding `BARCAN-TAG-XX.md` in the root directory. This is your **Strict Operational Boundary**.

### Step 2: Implementation (The MVC Flow)
- Work strictly within the folders mapped to your role in `docs/DIRECTORY_MAP.md`.
- **TAG-01/02/08**: Start with the Model and Contract.
- **TAG-03/11**: Follow with the View based on the established Contract.

### Step 3: Submission & Audit
- Push your changes to a new branch.
- Jules (Automated Agent) will run the audit based on `docs/JULES_AUDIT_MANIFEST.md`.
- If the audit fails, fix the structural or policy violations immediately.

---

## 3. HOW THE SYSTEM EVOLVES (EneikProductionSys)

- **The DMAIC Loop**: Every PR audit feeds metrics into the system.
- If you find a pattern that should be standardized, update the `src/utils/patterns/` directory.
- The system will autonomously suggest updates to the `BARCAN-TAG-XX.md` files based on successful completions.

---

## 4. TROUBLESHOOTING

- **Merge Bottleneck**: If TAG-00 is overloaded, TAG-09 must re-evaluate the task decomposition to allow more concurrent work in the View/Model layers.
- **Policy Block**: If a PR is blocked by `docs/JULES_AUDIT_MANIFEST.md` for a policy violation, consult `BARCAN-TAG-10.md` for compliance rules.
