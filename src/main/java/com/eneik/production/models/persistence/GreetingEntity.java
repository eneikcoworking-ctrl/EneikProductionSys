package com.eneik.production.models.persistence;

import com.eneik.production.models.domain.GreetingStatus;
import jakarta.persistence.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
 * Persistence entity for greetings, enriched with Lean and Theory of Constraints (TOC) metrics.
 *
 * Includes timestamps for Lead Time and Cycle Time calculation:
 * - createdAt: When the record entered the system (start of Lead Time).
 * - processingStartedAt: When work actually began (start of Cycle Time).
 * - completedAt: When work was finished (end of Lead and Cycle Time).
 */
@Entity
@Table(name = "greetings")
@Getter
@Setter
@NoArgsConstructor
public class GreetingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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
    /**
     * Start of Lead Time. The moment the greeting was received by the system.
     */
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /**
     * Start of Cycle Time. The moment processing moved from RECEIVED to IN_PROGRESS.
     */
    @Column
    private Instant processingStartedAt;

    /**
     * End of Lead Time and Cycle Time.
     */
    @Column
    private Instant completedAt;

    /**
     * Current status in the production pipeline. Used for WIP (Work In Progress) limits.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status currentStatus = Status.RECEIVED;

    public GreetingEntity(String message) {
        this.message = message;
        this.createdAt = Instant.now();
        this.currentStatus = Status.RECEIVED;
    }
}
