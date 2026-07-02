package com.eneik.production.services.projectfactory;

import com.eneik.production.models.persistence.ProjectEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

@Service
public class ProjectFactoryService {
    private final ProjectWorkspaceFactoryService workspaceFactoryService;
    private final GitHubProjectFactoryClient gitHubProjectFactoryClient;
    private final LinearProjectFactoryClient linearProjectFactoryClient;
    private final ObjectMapper objectMapper;

    public ProjectFactoryService(ProjectWorkspaceFactoryService workspaceFactoryService,
                                 GitHubProjectFactoryClient gitHubProjectFactoryClient,
                                 LinearProjectFactoryClient linearProjectFactoryClient,
                                 ObjectMapper objectMapper) {
        this.workspaceFactoryService = workspaceFactoryService;
        this.gitHubProjectFactoryClient = gitHubProjectFactoryClient;
        this.linearProjectFactoryClient = linearProjectFactoryClient;
        this.objectMapper = objectMapper;
    }

    public ProjectFactoryResult provision(ProjectEntity project) {
        WorkspaceProvisioningResult workspace = workspaceFactoryService.provision(project);
        GitHubProvisioningResult github = gitHubProjectFactoryClient.provision(project, workspace.artifacts());
        String repositoryUrl = firstNonBlank(github.repositoryUrl(), project.getRepositoryUrl());
        LinearProvisioningResult linear = linearProjectFactoryClient.provision(project, repositoryUrl);

        String factoryStatus = factoryStatus(github, linear);
        String report = report(workspace, github, linear, factoryStatus);
        return new ProjectFactoryResult(
                repositoryUrl,
                github.status(),
                github.repositoryId(),
                linear.status(),
                linear.projectId(),
                workspace.workspacePath(),
                factoryStatus,
                report
        );
    }

    private String factoryStatus(GitHubProvisioningResult github, LinearProvisioningResult linear) {
        if (startsWith(github.status(), "failed") || startsWith(linear.status(), "failed")) {
            return "ready_with_warnings";
        }
        if (startsWith(github.status(), "skipped") || startsWith(linear.status(), "skipped")) {
            return "ready_local";
        }
        return "ready_external";
    }

    private boolean startsWith(String value, String prefix) {
        return value != null && value.startsWith(prefix);
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private String report(WorkspaceProvisioningResult workspace,
                          GitHubProvisioningResult github,
                          LinearProvisioningResult linear,
                          String factoryStatus) {
        ObjectNode report = objectMapper.createObjectNode();
        report.put("factoryStatus", factoryStatus);
        report.put("workspacePath", workspace.workspacePath());
        report.put("workspaceStatus", workspace.status());
        report.put("githubStatus", github.status());
        report.put("githubRepositoryUrl", github.repositoryUrl());
        report.put("githubRepositoryId", github.repositoryId());
        report.put("linearStatus", linear.status());
        report.put("linearProjectId", linear.projectId());
        report.put("linearProjectUrl", linear.projectUrl());
        return report.toString();
    }
}
