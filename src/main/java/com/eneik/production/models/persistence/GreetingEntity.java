package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * @file GreetingEntity.java
 * @agent TAG-08 (Substitutivity Salva Veritate)
 */
@Entity
@Table(name = "greetings")
public class GreetingEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String message;

    @Column(name = "current_status")
    private String currentStatus;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "lead_time_seconds")
    private long leadTimeSeconds;

    public GreetingEntity() {}

    public GreetingEntity(UUID id, String message, String currentStatus, Instant createdAt, long leadTimeSeconds) {
        this.id = id;
        this.message = message;
        this.currentStatus = currentStatus;
        this.createdAt = createdAt;
        this.leadTimeSeconds = leadTimeSeconds;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getCurrentStatus() { return currentStatus; }
    public void setCurrentStatus(String currentStatus) { this.currentStatus = currentStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public long getLeadTimeSeconds() { return leadTimeSeconds; }
    public void setLeadTimeSeconds(long leadTimeSeconds) { this.leadTimeSeconds = leadTimeSeconds; }
}
