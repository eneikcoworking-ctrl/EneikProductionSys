package com.eneik.production.models.persistence;

import com.eneik.production.models.domain.GreetingStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * @file GreetingEntity.java
 * @agent TAG-08 (Substitutivity Salva Veritate)
 * @description JPA Entity for database persistence.
 */
@Entity
@Table(name = "greetings")
public class GreetingEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status")
    private GreetingStatus currentStatus;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "lead_time_seconds")
    private Integer leadTimeSeconds;

    // Default constructor for JPA
    public GreetingEntity() {}

    public GreetingEntity(UUID id, String message, GreetingStatus currentStatus, Instant createdAt, Integer leadTimeSeconds) {
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

    public GreetingStatus getCurrentStatus() { return currentStatus; }
    public void setCurrentStatus(GreetingStatus currentStatus) { this.currentStatus = currentStatus; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Integer getLeadTimeSeconds() { return leadTimeSeconds; }
    public void setLeadTimeSeconds(Integer leadTimeSeconds) { this.leadTimeSeconds = leadTimeSeconds; }
}
