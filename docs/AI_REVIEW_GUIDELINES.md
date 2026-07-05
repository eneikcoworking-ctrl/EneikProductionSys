# AI Reviewer Guidelines (Eneik V2)

## Hard Refusal Criteria
PRs MUST be rejected if any of the following architectural violations are detected:

1. **Dumb View Violation**: Svelte components containing business logic, JSON parsing, or conditional styling based on raw data instead of pre-computed DTO fields (tokens).
2. **Design System Violation**: Use of inline styles or HEX codes not defined in `docs/DESIGN_SYSTEM.md`.
3. **Thread Lock Violation**: Use of non-atomic selections or manual synchronization in high-concurrency blocks (e.g., account selection) that bypasses the `FOR UPDATE SKIP LOCKED` pattern.

## Approval Token
If and only if the code meets all architectural standards, the reviewer must comment:
"CORE ARCHITECTURE VERIFIED. APPROVED."
