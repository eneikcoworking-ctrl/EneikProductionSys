package com.eneik.production.dto;

import com.eneik.production.models.persistence.AccountStatus;
import java.time.Instant;
import java.util.UUID;

public record AccountDto(
    UUID id,
    String name,
    AccountStatus status,
    String capabilities,
    Instant lastHeartbeat,
    UUID currentProjectId,
    String apiKeyMasked,
    String githubUsername,
    boolean enabled,
    Integer maxConcurrentSessions,
    String provider
) {
    public AccountDto(
            UUID id,
            String name,
            AccountStatus status,
            String capabilities,
            Instant lastHeartbeat,
            UUID currentProjectId,
            String apiKeyMasked,
            String githubUsername,
            boolean enabled,
            Integer maxConcurrentSessions) {
        this(id, name, status, capabilities, lastHeartbeat, currentProjectId, apiKeyMasked, githubUsername, enabled, maxConcurrentSessions, "GOOGLE_JULES");
    }
}
