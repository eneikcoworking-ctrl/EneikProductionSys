package com.eneik.production.models.persistence;

/**
 * First-class lifecycle state of a project in the factory.
 *
 * Implements NUEL_BELNAP_03_TRUTH_STATUS_TABLE (D012 Policy contradiction / Nuel Belnap 1977):
 * Orchestration states must represent operational truth explicitly rather than conflating distinct
 * operational realities. Prior to Prescription 57, a stalled project (no progress for >45m with
 * actionable work present) had no first-class state in this enum and was falsely read as {@link #active}
 * by callers. Introducing {@link #stalled} ensures that a stopped/wedged project is distinguished
 * from an actively progressing project with a single read of state.
 */
public enum ProjectStatus {
    active,
    analyzing,
    waiting,
    frozen,
    accepted,
    archived,
    stalled;

    public boolean isActive() {
        return this == active;
    }

    public boolean isStalled() {
        return this == stalled;
    }

    public boolean isTerminal() {
        return this == accepted || this == archived;
    }

    /**
     * Operational project domain (GILBERT_RAYL_03_CATEGORY_ERROR_SCAN / DZHON_OSTIN_02_CATEGORY_ERROR_SCAN [D002]
     * and NUEL_BELNAP_03_TRUTH_STATUS_TABLE [D012]):
     * {@link #stalled} distinguishes lack of recent forward progress from {@link #active} in telemetry,
     * while keeping the project inside autonomous recovery, PR reconciliation, merge, and dispatch loops.
     */
    public boolean isOperational() {
        return this == active || this == stalled;
    }
}
