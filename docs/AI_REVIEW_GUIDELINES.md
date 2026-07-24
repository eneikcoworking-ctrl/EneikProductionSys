# AI Review Guidelines

This document defines the architectural standards and protocols for autonomous AI code review agents. All Pull Requests must be audited against these criteria.

## 1. HARD REFUSAL CRITERIA (Strictly Forbidden)

An AI Reviewer MUST reject any Pull Request that violates the following rules:

### Law of the Dumb View
- **No Business Logic in Frontend:** Any business logic computation, status interpretation, or decision-making logic inside Svelte templates or scripts is strictly forbidden.
- **Data Representation Only:** Svelte components should only handle presentation logic (formatting, layout).
- **Backend-Driven State:** Logic like "is this button enabled based on complex status" must be driven by a field calculated in the backend and provided via DTO.
- **Pre-computed Tokens:** Components must consume pre-computed fields and tokens passed by backend DTOs (e.g., standard classes like `text-success`, `border-error`).

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
- **Named anti-pattern - read-then-write status revival:** the specific, recurring shape of this bug: code
  reads a status/lifecycle field to decide whether a transition is allowed (`if (!isTerminal(status)) { ... }`),
  and only later, in a separate statement, writes a new value (`entity.setStatus(x); repository.save(entity)`).
  Between the read and the write there is a window where a concurrent request can change the same row -
  silently resurrecting or duplicating work that already finished. **Reject** any such pattern on a row a
  scheduled job or more than one endpoint can reach. **Required fix:** replace the plain save with a guarded
  conditional update - `UPDATE ... SET status = ? WHERE id = ? AND status = <expected/not-terminal>`
  (JPQL `@Modifying` query, or the ORM's equivalent) - so the write is a no-op instead of an overwrite the
  instant the row has already changed. Two matched examples for calibration:
  - **Reject:** `TaskEntity t = repo.findById(id); if (t.getStatus() != DONE) { t.setStatus(QUEUED); repo.save(t); }`
  - **Approve:** `int updated = repo.updateStatusIfNotTerminal(id, QUEUED); if (updated == 0) { /* another writer already finished it - do nothing */ }`
- **A different, related shape - check-then-create races:** admission logic like "dispatch a new record only
  if no active one exists for this project" is a check-then-**INSERT** race, not fixable with the CAS pattern
  above (there is no row to guard yet). **Required fix:** the check-and-create must happen inside one
  transaction holding a lock that serializes concurrent callers (e.g. `SELECT ... FOR UPDATE` on the owning
  parent row), not a bare existence check followed by an unconditional insert.

### Determinism & Canonical Representation
- **Seeded randomness:** any `Random`/`math.random`-equivalent source, current-time value, or generated ID
  that feeds a code path a test asserts against must be injectable/seedable, with the test supplying a fixed
  seed. An unseeded source makes the test's outcome non-reproducible - it can pass on one run and fail on the
  next for the identical code, which is a **reject**, not a flake to wave through.
- **No category errors across serialization boundaries:** JSON has no canonical numeric type in most
  languages' JSON libraries - the same floating-point value can be parsed back as a `Double` or a
  `BigDecimal` (or equivalent) depending on notation (e.g. scientific notation) or which JSON provider a
  test's JSON-path helper happens to select. A raw ordering/equality matcher typed for one numeric type,
  compared against a JSON-path-extracted value of a different one, is a real and reproducible failure, not
  a rare edge case. **Required fix:** parse the extracted value to an explicit, known type before comparing,
  or use an approximate/tolerance comparison - never assert directly against a bare literal via a
  type-inferring matcher.

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