# JULES AUTOMATED PR AUDIT MANIFEST (BARCAN-MVC)

This manifest is used by **Jules (The Agent)** during automated PR audits to ensure systemic compliance across all 7 operational accounts.

## 1. STRUCTURAL BOUNDARY AUDIT
*Checks if the code is in the correct folder relative to the agent's role.*

- [ ] **Role Validation**: Does the PR author's `TAG` match the modified directory?
  - `TAG-01/02` -> Must stay within `src/models/domain/` or `src/routes/`.
  - `TAG-03/11` -> Must stay within `src/views/`.
  - `TAG-10` -> Modifications permitted ONLY in `src/controllers/policy/`.
- [ ] **Leakage Check**: Are "View" dependencies (e.g., CSS, Svelte state/stores) found in the "Model" layer? (FAIL if true).
- [ ] **Contract Integrity**: If `src/routes/` is modified, is there a corresponding update to the OpenAPI spec?

## 2. DOMAIN LOGIC AUDIT (Ontological Integrity)
- [ ] **Anemic Model Check**: If a new Model is added in `src/models/domain/`, does it contain business logic? (Reject if it's only Getters/Setters).
- [ ] **Identity Check**: Do all new Models implement the `IEntity` interface with a mandatory UUID?
- [ ] **ML Schema Sync**: If `src/models/persistence/` is modified, was `TAG-04` notified? (Check for "Notified TAG-04" in PR description).

## 3. COMPLIANCE & SECURITY AUDIT (Deontic Check)
- [ ] **Policy Injection**: Does every new Controller in `src/controllers/core/` call the `PolicyFilter` middleware?
- [ ] **PII Masking**: If a Model field is tagged `@PII`, is there a corresponding masking logic in the Controller?
- [ ] **Zero-Knowledge Check**: Are passwords or secrets being handled as raw strings? (FAIL if true; must use `SecretRef`).

## 4. LEAN / ZERO-DEFECT AUDIT
- [ ] **Test Coverage**: Does the PR include a test file in the corresponding `tests/unit/` directory?
- [ ] **Complexity Check**: Are any new functions longer than 40 lines? (Warn TAG-00).
- [ ] **Performance Budget**: If `src/views/` is changed, does the `lighthouse-ci` report show a LCP decrease > 5%?

## 5. AUDIT ACTION LOGIC (JULES EXECUTION)
1. **Grep** for forbidden imports (e.g., `import View from '../views'` inside a Model file).
2. **Validate** JSON schema for all configuration assets.
3. **Check** Linear task status: Is the task tagged with the same `BARCAN-TAG` as the PR?
4. **Outcome**:
   - **PASS**: All markers green -> Forward to `TAG-00` for final approval.
   - **WARN**: Minor violations (e.g., function length) -> Add comment, proceed.
   - **FAIL**: Structural or Policy violation -> **BLOCK PR** and request immediate fix.
