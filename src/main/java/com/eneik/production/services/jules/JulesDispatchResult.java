package com.eneik.production.services.jules;

public record JulesDispatchResult(
        boolean dispatched,
        String sessionName,
        String reason
) {
}
