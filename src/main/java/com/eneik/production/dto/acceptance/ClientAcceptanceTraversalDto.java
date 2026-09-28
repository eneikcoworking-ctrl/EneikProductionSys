package com.eneik.production.dto.acceptance;

import java.time.Instant;
import java.util.UUID;

/**
 * Проекция зарегистрированного институционального факта обхода (AGY_ASKS #1, V100).
 */
public record ClientAcceptanceTraversalDto(
        UUID id,
        UUID projectId,
        String profileId,
        String actor,
        String link,
        Instant traversedAt,
        String walkedBy,
        String evidence,
        String instanceUrl
) {}
