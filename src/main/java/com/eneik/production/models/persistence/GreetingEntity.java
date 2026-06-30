package com.eneik.production.models.persistence;

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

    @Column(name = "created_at")
    private Instant createdAt;

    // Default constructor for JPA
    public GreetingEntity() {}

    public GreetingEntity(UUID id, String message, Instant createdAt) {
        this.id = id;
        this.message = message;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
