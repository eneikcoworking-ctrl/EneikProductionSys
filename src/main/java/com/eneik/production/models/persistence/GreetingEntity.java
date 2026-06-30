package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

/**
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
