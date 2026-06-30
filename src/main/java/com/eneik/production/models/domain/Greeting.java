package com.eneik.production.models.domain;

import com.eneik.production.models.persistence.Status;
import lombok.Value;
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
    private final String currentStatus;
    private final Instant createdAt;
    private final int leadTimeSeconds;
    private boolean highRiskPredicted;

    public Greeting(UUID id, String message, String currentStatus, int leadTimeSeconds) {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Greeting must have a real, existing message (Actualist Principle)");
        }
        this.id = id;
        this.message = message;
        this.currentStatus = currentStatus;
        this.createdAt = createdAt;
        this.createdAt = Instant.now();
        this.leadTimeSeconds = leadTimeSeconds;
    }

    public UUID getId() {
        return id;
    }

    public String getMessage() {
        return message;
    }

    public GreetingStatus getCurrentStatus() {
    public String getCurrentStatus() {
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
    public int getLeadTimeSeconds() {
        return leadTimeSeconds;
    }

    public boolean isHighRiskPredicted() {
        return highRiskPredicted;
    }

    public void setHighRiskPredicted(boolean highRiskPredicted) {
        this.highRiskPredicted = highRiskPredicted;
 * Pure Domain Model for Greeting.
 * Independent of persistence annotations, representing the business state.
 */
@Value
public class Greeting {
    UUID id;
    String message;
    Status currentStatus;
    Instant createdAt;
    Instant processingStartedAt;
    Instant completedAt;

    /**
     * Calculates the Lead Time in seconds.
     * Lead Time is the total time from creation to completion (or now if not completed).
     *
     * @return Lead time in seconds.
     */
    public long getLeadTimeSeconds() {
        Instant end = (completedAt != null) ? completedAt : Instant.now();
        return end.getEpochSecond() - createdAt.getEpochSecond();
    }
}
