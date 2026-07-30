package com.eneik.production.services.projectfactory;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.repositories.ProjectHotspotFileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectFactoryServiceTest {

    private final ProjectWorkspaceFactoryService workspaceFactoryService = mock(ProjectWorkspaceFactoryService.class);
    private final GitHubProjectFactoryClient gitHubProjectFactoryClient = mock(GitHubProjectFactoryClient.class);
    private final LinearProjectFactoryClient linearProjectFactoryClient = mock(LinearProjectFactoryClient.class);
    private final ProjectHotspotFileRepository hotspotFileRepository = mock(ProjectHotspotFileRepository.class);
    private final WorkspaceArtifacts artifacts = new WorkspaceArtifacts("readme", "env", "ci", "brief");

    @Test
    void collaboratorInviteFailureKeepsProjectWaiting() {
        ProjectFactoryService service = service();
        ProjectEntity project = project();

        when(workspaceFactoryService.provision(project))
                .thenReturn(new WorkspaceProvisioningResult("/tmp/project", artifacts, "workspace ready"));
        when(gitHubProjectFactoryClient.provision(project, artifacts))
                .thenReturn(new GitHubProvisioningResult(
                        "created repository with configuration warnings",
                        "https://github.com/eneikdru/test-project",
                        "123",
                        List.of("collaborator dmitrefrem-eneik not_found"),
                        List.of(new CollaboratorProvisioningResult(
                                "dmitrefrem-eneik", "not_found", 404, "GitHub user/repository not visible"))));
        when(linearProjectFactoryClient.provision(project, "https://github.com/eneikdru/test-project"))
                .thenReturn(new LinearProvisioningResult("skipped: Linear disabled", null, null));
        when(hotspotFileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectFactoryResult result = service.provision(project);

        assertEquals("waiting", result.factoryStatus());
    }

    @Test
    void sentOrPendingCollaboratorInviteDoesNotBlockFactoryReadiness() {
        ProjectFactoryService service = service();
        ProjectEntity project = project();

        when(workspaceFactoryService.provision(project))
                .thenReturn(new WorkspaceProvisioningResult("/tmp/project", artifacts, "workspace ready"));
        when(gitHubProjectFactoryClient.provision(project, artifacts))
                .thenReturn(new GitHubProvisioningResult(
                        "created repository and configured protections",
                        "https://github.com/eneikdru/test-project",
                        "123",
                        List.of(),
                        List.of(
                                new CollaboratorProvisioningResult("agent-a", "invitation_sent", 201, "sent"),
                                new CollaboratorProvisioningResult("agent-b", "validation_failed_or_pending", 422, "already pending"),
                                new CollaboratorProvisioningResult("owner", "already_has_access", 204, "owner"))));
        when(linearProjectFactoryClient.provision(project, "https://github.com/eneikdru/test-project"))
                .thenReturn(new LinearProvisioningResult("skipped: Linear disabled", null, null));
        when(hotspotFileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectFactoryResult result = service.provision(project);

        assertEquals("ready_local", result.factoryStatus());
    }

    private ProjectFactoryService service() {
        return new ProjectFactoryService(
                workspaceFactoryService,
                gitHubProjectFactoryClient,
                linearProjectFactoryClient,
                new ObjectMapper(),
                hotspotFileRepository);
    }

    private ProjectEntity project() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("Test Project");
        project.setRepositoryName("test-project");
        return project;
    }
}
