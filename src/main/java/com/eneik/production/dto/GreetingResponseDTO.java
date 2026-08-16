package com.eneik.production.dto;

import com.eneik.production.models.persistence.Status;

import java.time.Instant;
import java.util.UUID;

public class GreetingResponseDTO {
    private final UUID id;
    private final String message;
    private final Status currentStatus;
    private final Instant createdAt;
    private final long leadTimeSeconds;

    public GreetingResponseDTO(UUID id, String message, Status currentStatus, Instant createdAt, long leadTimeSeconds) {
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

    public Status getCurrentStatus() {
        return currentStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getLeadTimeSeconds() {
        return leadTimeSeconds;
    }
}
