package com.eneik.production.models.domain;

import com.eneik.production.models.persistence.Status;
import lombok.Value;
import java.time.Instant;
import java.util.UUID;

/**
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
