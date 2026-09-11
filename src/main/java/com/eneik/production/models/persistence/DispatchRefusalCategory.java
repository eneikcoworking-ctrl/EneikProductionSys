package com.eneik.production.models.persistence;

import java.util.Locale;
import java.util.Set;

/**
 * Formal 3-valued category for dispatch refusals under Law 12, D007 (INSTITUTIONAL_FACT_REGISTER)
 * and D012 (NUEL_BELNAP_03_TRUTH_STATUS_TABLE).
 *
 * <p>Separates:
 * 1. EXTERNAL_CAPACITY: proven provider capacity/quota/rate limits.
 * 2. NON_EXTERNAL_REJECTION: proven errors in request content or internal preconditions.
 * 3. UNATTRIBUTED_REFUSAL: refusals where cause is unnamed/unspecified.
 */
public enum DispatchRefusalCategory {
    EXTERNAL_CAPACITY,
    NON_EXTERNAL_REJECTION,
    UNATTRIBUTED_REFUSAL;

    private static final Set<String> EXTERNAL_CAPACITY_CODES = Set.of(
            "jules_concurrent_capacity_exhausted",
            "jules_daily_limit",
            "concurrent session limit",
            "daily limit",
            "daily/quota/rate limit",
            "rate_limit",
            "quota_exceeded",
            "429"
    );

    private static final Set<String> NON_EXTERNAL_REJECTION_CODES = Set.of(
            "jules_request_rejected",
            "target context is undetermined",
            "no project found for task",
            "invalid_argument",
            "bad json",
            "malformed"
    );

    public static DispatchRefusalCategory fromClosureReason(String closureReason) {
        if (closureReason == null || closureReason.isBlank()) {
            return UNATTRIBUTED_REFUSAL;
        }
        String lower = closureReason.toLowerCase(Locale.ROOT);
        for (String code : NON_EXTERNAL_REJECTION_CODES) {
            if (lower.contains(code)) {
                return NON_EXTERNAL_REJECTION;
            }
        }
        for (String code : EXTERNAL_CAPACITY_CODES) {
            if (lower.contains(code)) {
                return EXTERNAL_CAPACITY;
            }
        }
        return UNATTRIBUTED_REFUSAL;
    }

    public boolean isExternal() {
        return this == EXTERNAL_CAPACITY;
    }

    public boolean isNonExternal() {
        return this == NON_EXTERNAL_REJECTION;
    }

    public boolean isUnattributed() {
        return this == UNATTRIBUTED_REFUSAL;
    }
}
