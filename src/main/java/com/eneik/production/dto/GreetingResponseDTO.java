package com.eneik.production.dto;

import com.eneik.production.models.persistence.Status;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object for Greeting responses.
 * Strictly adheres to the REST contract defined in 00_INTEGRATION_CONTRACT.md.
 */
@Data
@Builder
public class GreetingResponseDTO {
    private UUID id;
    private String message;
    private Status currentStatus;
    private Instant createdAt;
    private long leadTimeSeconds;
}
