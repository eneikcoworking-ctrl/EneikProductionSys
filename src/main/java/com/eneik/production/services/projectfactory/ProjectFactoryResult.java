package com.eneik.production.services.projectfactory;

public record ProjectFactoryResult(
        String repositoryUrl,
        String githubRepositoryStatus,
        String githubRepositoryId,
        String linearProjectStatus,
        String linearProjectId,
        String workspacePath,
        String factoryStatus,
        String factoryReport
) {
}
