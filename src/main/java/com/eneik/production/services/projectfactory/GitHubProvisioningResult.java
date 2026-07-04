package com.eneik.production.services.projectfactory;

import java.util.List;

public record GitHubProvisioningResult(
        String status,
        String repositoryUrl,
        String repositoryId,
        List<String> warnings,
        List<CollaboratorProvisioningResult> collaborators
) {
    public GitHubProvisioningResult(String status, String repositoryUrl, String repositoryId) {
        this(status, repositoryUrl, repositoryId, List.of(), List.of());
    }
}
