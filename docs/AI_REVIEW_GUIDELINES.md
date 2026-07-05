# AI Review Guidelines

This document defines the architectural standards and protocols for autonomous AI code review agents. All Pull Requests must be audited against these criteria.

## 1. HARD REFUSAL CRITERIA (Strictly Forbidden)

An AI Reviewer MUST reject any Pull Request that violates the following rules:

### Law of the Dumb View
- **No Business Logic in Frontend:** Any business logic computation, status interpretation, or decision-making logic inside Svelte templates or scripts is strictly forbidden.
- **Data Representation Only:** Svelte components should only handle presentation logic (formatting, layout).
- **Backend-Driven State:** Logic like "is this button enabled based on complex status" must be driven by a field calculated in the backend and provided via DTO.

### Design System Compliance
- **No Token Bypass:** Dynamic UI colors, styles, or spacing that bypass tokens defined in `docs/DESIGN_SYSTEM.md` are forbidden.
- **CSS Variables:** All styles must use the approved CSS variables (e.g., `--primary`, `--neutral-200`, etc.) or follow the 8pt grid system.
- **No Hardcoded Hex/PX:** Inline styles with arbitrary hex codes or pixel values for layout/typography are not allowed.

### Thin Triggers (Scheduled Jobs)
- **Delegation to Services:** Background jobs (`@Scheduled`) containing direct business logic or direct repository mutations are forbidden.
- **Service Layer Only:** Scheduled tasks must delegate all logic and persistence operations to Domain Services.
- **Auditability:** Jobs should focus on orchestration, not implementation details.

### Idempotency & Database Safety
- **Missing Guards:** Lack of idempotency guards in scheduling or claim-based endpoints is a refusal criterion.
- **Atomic Operations:** Claim-based logic must use proper database locks (e.g., `FOR UPDATE SKIP LOCKED`) to prevent race conditions.
- **Transactional Integrity:** Operations that transition statuses across multiple entities must be atomic and resilient to retries.

## 2. INTERACTION PROTOCOL

### Triggering Fixes
If a specific violation is found, the AI Reviewer should tag the implementation agent to trigger an automatic fix:
- Format: `@jules please fix [specific violation description] in [file path].`

### PR Decision Format
AI Reviewers must use the following exact formats for their final decision:

#### For Approval:
`CORE ARCHITECTURE VERIFIED. APPROVED.`
*(Optionally followed by a brief summary of verified points)*

#### For Rejection:
`ARCHITECTURE VIOLATION DETECTED. REJECTED.`
- **Violations:** [List of specific violations]
- **Required Changes:** [Clear description of what must be fixed]

---
*Note: This document is a living standard and should be updated as new architectural patterns are established.*
