package com.eneik.production.dto;

import java.time.Instant;
import java.util.UUID;

public record ClaimDto(
    UUID id,
    UUID taskId,
    UUID accountId,
    String roleTag,
    Instant claimedAt,
    Instant leaseExpiresAt
) {}
