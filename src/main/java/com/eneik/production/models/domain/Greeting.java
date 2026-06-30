package com.eneik.production.models.domain;

import com.eneik.production.models.persistence.Status;

import java.time.Instant;
import java.util.UUID;

public class Greeting {
    private final UUID id;
    private final String message;
    private final Status currentStatus;
    private final Instant createdAt;
    private final Instant processingStartedAt;
    private final Instant completedAt;

    public Greeting(UUID id, String message, Status currentStatus, Instant createdAt,
                    Instant processingStartedAt, Instant completedAt) {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Greeting message must not be empty");
        }
        this.id = id;
        this.message = message;
        this.currentStatus = currentStatus;
        this.createdAt = createdAt;
        this.processingStartedAt = processingStartedAt;
        this.completedAt = completedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getMessage() {
        return message;
    }

    public Status getCurrentStatus() {
        return currentStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getProcessingStartedAt() {
        return processingStartedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public long getLeadTimeSeconds() {
        Instant end = completedAt != null ? completedAt : Instant.now();
        return Math.max(0, end.getEpochSecond() - createdAt.getEpochSecond());
    }
}
