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
    private final GreetingStatus currentStatus;
    private final Instant createdAt;
    private final Integer leadTimeSeconds;

    public Greeting(UUID id, String message, GreetingStatus currentStatus, Instant createdAt, Integer leadTimeSeconds) {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Greeting must have a real, existing message (Actualist Principle)");
        }
        this.id = id;
        this.message = message;
        this.currentStatus = currentStatus;
        this.createdAt = createdAt;
        this.leadTimeSeconds = leadTimeSeconds;
    }

    public UUID getId() {
        return id;
    }

    public String getMessage() {
        return message;
    }

    public GreetingStatus getCurrentStatus() {
        return currentStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Integer getLeadTimeSeconds() {
        return leadTimeSeconds;
    }

    public String getFormattedGreeting() {
        return String.format("%s [%s] (Created at %s)", message, currentStatus, createdAt.toString());
    }
}
