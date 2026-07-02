package com.eneik.production.dto;

import com.eneik.production.models.persistence.ProjectStatus;
import java.time.Instant;
import java.util.UUID;

public record ProjectDto(
        UUID id,
        String name,
        String slug,
        String repositoryName,
        String repositoryUrl,
        String linearProjectKey,
        ProjectStatus status,
        Instant createdAt,
        Instant acceptedAt
) {
}
