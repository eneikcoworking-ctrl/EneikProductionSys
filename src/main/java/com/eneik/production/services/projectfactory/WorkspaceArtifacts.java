package com.eneik.production.services.projectfactory;

public record WorkspaceArtifacts(
        String readme,
        String envExample,
        String ciWorkflow,
        String projectBrief
) {
}
