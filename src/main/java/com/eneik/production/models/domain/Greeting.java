package com.eneik.production.models.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * @file Greeting.java
 * @agent TAG-01 (Actualist Object)
 * @description Domain Model aligned with Integration Contract.
 */
public class Greeting {
    private final UUID id;
    private final String message;
    private final String currentStatus;
    private final Instant createdAt;
    private final long leadTimeSeconds;

    public Greeting(UUID id, String message, String currentStatus, Instant createdAt, long leadTimeSeconds) {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Message cannot be empty");
        }
        this.id = id;
        this.message = message;
        this.currentStatus = currentStatus;
        this.createdAt = createdAt;
        this.leadTimeSeconds = leadTimeSeconds;
    }

    public UUID getId() { return id; }
    public String getMessage() { return message; }
    public String getCurrentStatus() { return currentStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public long getLeadTimeSeconds() { return leadTimeSeconds; }
}
