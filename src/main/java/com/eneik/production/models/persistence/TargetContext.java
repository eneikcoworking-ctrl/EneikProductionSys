package com.eneik.production.models.persistence;

/**
 * First-class target context defining whether a wishlist/task targets the client product codebase,
 * the underlying orchestrator system engine, or is explicitly undetermined.
 *
 * <p>Truth status semantics per {@code NUEL_BELNAP_03_TRUTH_STATUS_TABLE} (D012 Policy contradiction):
 * undetermined context must not be silently coerced into client product work, as dispatching tasks to
 * an external/client repository is irreversible.
 */
public enum TargetContext {
    PRODUCT_CODEBASE,
    ORCHESTRATOR_SYSTEM,
    UNDETERMINED;

    public boolean isUndetermined() {
        return this == UNDETERMINED;
    }

    public boolean isProductCodebase() {
        return this == PRODUCT_CODEBASE;
    }

    public boolean isOrchestratorSystem() {
        return this == ORCHESTRATOR_SYSTEM;
    }
}
