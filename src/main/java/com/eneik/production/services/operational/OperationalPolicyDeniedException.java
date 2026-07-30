package com.eneik.production.services.operational;

import java.util.UUID;

public class OperationalPolicyDeniedException extends RuntimeException {
    private final UUID projectId;
    private final OperationalAction action;
    private final String state;
    private final String authorizationStatus;

    public OperationalPolicyDeniedException(UUID projectId,
                                            OperationalAction action,
                                            String state,
                                            String authorizationStatus,
                                            String reason) {
        super(reason);
        this.projectId = projectId;
        this.action = action;
        this.state = state;
        this.authorizationStatus = authorizationStatus;
    }

    public UUID projectId() {
        return projectId;
    }

    public OperationalAction action() {
        return action;
    }

    public String state() {
        return state;
    }

    public String authorizationStatus() {
        return authorizationStatus;
    }
}
