package com.eneik.production.models.persistence;

/**
 * Formal typed representation of task dispatch exhaustion under Law 12 and NUEL_BELNAP_03 (D012).
 * Separates renewable capacity exhaustion (UNTESTED_WITHIN_CAPACITY) from regular exhaustion
 * with non-external rejections (DISPATCH_BUDGET_EXHAUSTED).
 */
public enum TaskDispatchVerdict {
    NONE,
    UNTESTED_WITHIN_CAPACITY,
    DISPATCH_BUDGET_EXHAUSTED;

    public boolean isUntestedWithinCapacity() {
        return this == UNTESTED_WITHIN_CAPACITY;
    }
}
