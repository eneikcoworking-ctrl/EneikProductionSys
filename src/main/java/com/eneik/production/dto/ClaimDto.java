package com.eneik.production.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record ClaimDto(
    UUID claimId,
    UUID taskId,
    String roleTag,
    String description,
    JsonNode payload,
    Instant leaseExpiresAt
) {}
