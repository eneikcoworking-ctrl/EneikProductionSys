package com.eneik.production.models.persistence;

import java.util.ArrayList;
import java.util.List;

/**
 * Account lifecycle status and availability truth table
 * (LUDWIG_WITTGENSTEIN_01_FACT_STATE_TABLE / D002 Invalid state,
 *  NUEL_BELNAP_03_TRUTH_STATUS_TABLE / D012 Policy contradiction).
 *
 * Availability is a strict conjunction: status == idle AND enabled == true AND apiKey != null.
 * When any conjunct fails, the failure reason must explicitly name every violated conjunct.
 */
public enum AccountStatus {
    idle, busy, offline, daily_limited, api_blocked, decommissioned;

    /**
     * An account is operational if it is not decommissioned.
     */
    public boolean isOperational() {
        return this != decommissioned;
    }

    /**
     * An account is ready to receive tasks if its status is idle.
     */
    public boolean isReady() {
        return this == idle;
    }

    /**
     * Evaluates whether the status and enablement conjunction holds.
     */
    public boolean isAvailable(boolean enabled) {
        return enabled && this == idle;
    }

    /**
     * Evaluates whether the full availability conjunction (status, enablement, API key) holds.
     */
    public boolean isAvailable(boolean enabled, String apiKey) {
        return enabled && this == idle && apiKey != null && !apiKey.isBlank();
    }

    /**
     * Formulates an explicit explanation naming all failed conjuncts of the availability predicate.
     * Returns null when all conjuncts hold (account is available).
     */
    public static String evaluateAvailabilityReason(AccountStatus status, boolean enabled, String apiKey) {
        if (status == decommissioned) {
            return "status == decommissioned";
        }
        List<String> reasons = new ArrayList<>();
        if (!enabled) {
            reasons.add("enabled == false");
        }
        if (status == null || status != idle) {
            reasons.add("status == " + (status != null ? status.name() : "null"));
        }
        if (apiKey == null || apiKey.isBlank()) {
            reasons.add("apiKey == missing");
        }
        return reasons.isEmpty() ? null : String.join("; ", reasons);
    }
}

