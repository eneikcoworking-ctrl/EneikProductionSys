package com.eneik.production.services.projectfactory;

public record CollaboratorProvisioningResult(
        String username,
        String status,
        int githubStatus,
        String detail
) {
}
