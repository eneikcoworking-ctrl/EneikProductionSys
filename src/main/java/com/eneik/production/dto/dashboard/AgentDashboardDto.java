package com.eneik.production.dto.dashboard;

import com.eneik.production.models.persistence.AccountStatus;
import java.time.Instant;
import java.util.UUID;

public record AgentDashboardDto(
    UUID accountId,
    String name,
    AccountStatus status,
    String currentRoleTag,
    String currentTaskDescription,
    Instant claimedAt,
    Instant leaseExpiresAt,
    Instant lastHeartbeat
) {}
