package com.eneik.production.services.projectfactory;

public record GitHubProvisioningResult(
        String status,
        String repositoryUrl,
        String repositoryId
) {
}
