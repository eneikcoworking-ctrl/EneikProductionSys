package com.eneik.production.dto;

import com.eneik.production.models.persistence.ProjectStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProjectDto(
        UUID id,
        String name,
        String slug,
        String repositoryName,
        String repositoryUrl,
        String repoUrl,
        String linearProjectKey,
        String githubRepositoryStatus,
        String githubRepositoryId,
        String linearProjectStatus,
        String linearProjectId,
        String workspacePath,
        String factoryStatus,
        String factoryReport,
        ProjectStatus status,
        String onboardingMode,
        Instant createdAt,
        Instant acceptedAt,
        long accountCount,
        String statusLabel,
        String uiColorToken,
        List<CollaboratorDto> collaborators
) {
}
