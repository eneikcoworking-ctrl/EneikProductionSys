package com.eneik.production.models.persistence;

/**
 * Formal typed representation of task dispatch exhaustion under Law 12, NUEL_BELNAP_03 (D012)
 * and INSTITUTIONAL_FACT_REGISTER (D007).
 * Separates renewable capacity exhaustion (UNTESTED_WITHIN_CAPACITY), renewable provider unattributed
 * refusals (UNATTRIBUTED_DISPATCH_REFUSAL), and terminal non-external request exhaustion (DISPATCH_BUDGET_EXHAUSTED).
 */
public enum TaskDispatchVerdict {
    NONE,
    UNTESTED_WITHIN_CAPACITY,
    UNATTRIBUTED_DISPATCH_REFUSAL,
    DISPATCH_BUDGET_EXHAUSTED;

    public boolean isUntestedWithinCapacity() {
        return this == UNTESTED_WITHIN_CAPACITY;
    }

    public boolean isUnattributedDispatchRefusal() {
        return this == UNATTRIBUTED_DISPATCH_REFUSAL;
    }

    public boolean isResumable() {
        return this == UNTESTED_WITHIN_CAPACITY || this == UNATTRIBUTED_DISPATCH_REFUSAL;
    }
}
