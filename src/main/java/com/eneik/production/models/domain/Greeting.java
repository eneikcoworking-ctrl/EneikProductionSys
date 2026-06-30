package com.eneik.production.models.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * @file Greeting.java
 * @agent TAG-01 (Actualist Object)
 * @description Domain Model for Greeting. Pure business logic and ontological validation.
 */
public class Greeting {
    private final UUID id;
    private final String message;
    private final String currentStatus;
    private final Instant createdAt;
    private final int leadTimeSeconds;
    private boolean highRiskPredicted;

    public Greeting(UUID id, String message, String currentStatus, int leadTimeSeconds) {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Greeting must have a real, existing message (Actualist Principle)");
        }
        this.id = id;
        this.message = message;
        this.currentStatus = currentStatus;
        this.createdAt = Instant.now();
        this.leadTimeSeconds = leadTimeSeconds;
    }

    public UUID getId() {
        return id;
    }

    public String getMessage() {
        return message;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getLeadTimeSeconds() {
        return leadTimeSeconds;
    }

    public boolean isHighRiskPredicted() {
        return highRiskPredicted;
    }

    public void setHighRiskPredicted(boolean highRiskPredicted) {
        this.highRiskPredicted = highRiskPredicted;
    }
}
