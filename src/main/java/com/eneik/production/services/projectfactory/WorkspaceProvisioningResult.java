package com.eneik.production.services.projectfactory;

public record WorkspaceProvisioningResult(
        String workspacePath,
        WorkspaceArtifacts artifacts,
        String status
) {
}
