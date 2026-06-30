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
    private final Instant timestamp;

    public Greeting(UUID id, String message) {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Greeting must have a real, existing message (Actualist Principle)");
        }
        this.id = id;
        this.message = message;
        this.timestamp = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getMessage() {
        return message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getFormattedGreeting() {
        return String.format("%s (Verified at %s)", message, timestamp.toString());
    }
}
