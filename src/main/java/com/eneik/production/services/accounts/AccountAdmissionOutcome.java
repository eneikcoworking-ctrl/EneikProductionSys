package com.eneik.production.services.accounts;

/**
 * Outcome of named account admission check (Prescription 20, PRINCIPLED_INTEGRITY / D012, Law 12).
 * Each failure state maps directly to the specific conjunct of the admission predicate that failed,
 * eliminating false capacity reporting when an account is disabled, resting, retired, or locked.
 */
public enum AccountAdmissionOutcome {
    /** Account was successfully locked and admitted for work. */
    ADMITTED,

    /** Account is explicitly disabled (enabled = false). */
    DISABLED,

    /** Account is decommissioned or offline. */
    RETIRED,

    /** Account is in temporary resting/backoff status (daily_limited or api_blocked). */
    RESTING,

    /** Account has reached or exceeded its max concurrent session capacity. */
    SESSIONS_EXHAUSTED,

    /** Account satisfies all conjuncts, but could not be locked due to a concurrent transaction claim (SKIP LOCKED). */
    LOCKED_BY_CONCURRENT_CLAIM,

    /** Account with the specified name does not exist in the repository. */
    NOT_FOUND
}
