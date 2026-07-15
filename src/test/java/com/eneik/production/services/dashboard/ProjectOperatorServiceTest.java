package com.eneik.production.services.dashboard;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.services.ClaimService;
import com.eneik.production.services.MLPredictionServiceClient;
import com.eneik.production.services.ProjectFlowService;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectOperatorServiceTest {

    @Test
    void explanationQuestionDoesNotRunMutatingToolEvenIfPlannerRequestsIt() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ProjectOperationalContextService contextService = mock(ProjectOperationalContextService.class);
        MLPredictionServiceClient mlPredictionServiceClient = mock(MLPredictionServiceClient.class);
        ProjectFlowService projectFlowService = mock(ProjectFlowService.class);
        ClaimService claimService = mock(ClaimService.class);
        GitHubPullRequestService gitHubPullRequestService = mock(GitHubPullRequestService.class);
        SystemSettingsService settingsService = mock(SystemSettingsService.class);

        ProjectOperatorService service = new ProjectOperatorService(
                projectRepository,
                contextService,
                mlPredictionServiceClient,
                projectFlowService,
                claimService,
                gitHubPullRequestService,
                settingsService,
                "project-workspaces",
                ".",
                "target/operator-memory-test",
                true,
                "eneikproductionsys",
                "eneikcoworking-ctrl",
                30,
                3,
                true,
                new ObjectMapper()
        );

        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setName("test-project");
        project.setSlug("test-project");
        project.setStatus(ProjectStatus.active);
        project.setRepositoryName("org/test-project");

        ProjectOperationalContextService.ProjectOperationalContext context =
                new ProjectOperationalContextService.ProjectOperationalContext(
                        projectId,
                        "test-project",
                        Map.of("project", Map.of("id", projectId.toString(), "name", "test-project")),
                        "{\"project\":{\"name\":\"test-project\"}}",
                        new ProjectOperationalContextService.PrStats(
                                false,
                                0,
                                0,
                                "",
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of()
                        )
                );

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(contextService.build(projectId, "test-project")).thenReturn(context);
        when(mlPredictionServiceClient.chat(anyString(), contains("tool planner")))
                .thenReturn("{\"toolCalls\":[{\"tool\":\"orchestrate_project\",\"reason\":\"bad planner choice\",\"args\":{}}]}");
        when(mlPredictionServiceClient.chat(anyString(), contains("PROJECT_FACT_PACK")))
                .thenReturn("Mutating tool was not run because this was an explanation question.");

        String answer = service.answer(projectId, "test-project", "How does decomposition and task assignment work?");

        assertEquals("Mutating tool was not run because this was an explanation question.", answer);
        verify(projectFlowService, never()).orchestrate(any());
        verify(projectFlowService, never()).dispatchQueuedTasks(any());
        verify(projectFlowService, never()).dispatchReviewTasks(any());
    }

    @Test
    void criticCanReplaceUnsupportedTemplateAnswer() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ProjectOperationalContextService contextService = mock(ProjectOperationalContextService.class);
        MLPredictionServiceClient mlPredictionServiceClient = mock(MLPredictionServiceClient.class);
        ProjectFlowService projectFlowService = mock(ProjectFlowService.class);
        ClaimService claimService = mock(ClaimService.class);
        GitHubPullRequestService gitHubPullRequestService = mock(GitHubPullRequestService.class);
        SystemSettingsService settingsService = mock(SystemSettingsService.class);

        ProjectOperatorService service = new ProjectOperatorService(
                projectRepository,
                contextService,
                mlPredictionServiceClient,
                projectFlowService,
                claimService,
                gitHubPullRequestService,
                settingsService,
                "project-workspaces",
                ".",
                "target/operator-memory-test",
                true,
                "eneikproductionsys",
                "eneikcoworking-ctrl",
                30,
                3,
                true,
                new ObjectMapper()
        );

        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setName("test-project");
        project.setSlug("test-project");
        project.setStatus(ProjectStatus.active);
        project.setRepositoryName("org/test-project");

        ProjectOperationalContextService.ProjectOperationalContext context =
                new ProjectOperationalContextService.ProjectOperationalContext(
                        projectId,
                        "test-project",
                        Map.of("accountsAvailableForProject", Map.of("enabled", 0)),
                        "{\"accountsAvailableForProject\":{\"enabled\":0}}",
                        new ProjectOperationalContextService.PrStats(
                                false,
                                0,
                                0,
                                "",
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of()
                        )
                );

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(contextService.build(projectId, "test-project")).thenReturn(context);
        when(mlPredictionServiceClient.chat(anyString(), contains("tool planner")))
                .thenReturn("{\"toolCalls\":[]}");
        when(mlPredictionServiceClient.chat(anyString(), contains("PROJECT_FACT_PACK")))
                .thenReturn("Hello! I see 20 Jules accounts and everything is great.");
        when(mlPredictionServiceClient.chat(anyString(), contains("answer critic")))
                .thenReturn("{\"verdict\":\"revise\",\"issues\":[\"unsupported account count\"],\"revisedAnswer\":\"VERIFIED: The selected project evidence does not show 20 Jules accounts.\"}");

        String answer = service.answer(projectId, "test-project", "How many Jules accounts are available?");

        assertEquals("VERIFIED: The selected project evidence does not show 20 Jules accounts.", answer);
    }

    @Test
    void finalAnswerSanitizesInternalEvidenceNames() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ProjectOperationalContextService contextService = mock(ProjectOperationalContextService.class);
        MLPredictionServiceClient mlPredictionServiceClient = mock(MLPredictionServiceClient.class);
        ProjectFlowService projectFlowService = mock(ProjectFlowService.class);
        ClaimService claimService = mock(ClaimService.class);
        GitHubPullRequestService gitHubPullRequestService = mock(GitHubPullRequestService.class);
        SystemSettingsService settingsService = mock(SystemSettingsService.class);

        ProjectOperatorService service = new ProjectOperatorService(
                projectRepository,
                contextService,
                mlPredictionServiceClient,
                projectFlowService,
                claimService,
                gitHubPullRequestService,
                settingsService,
                "project-workspaces",
                ".",
                "target/operator-memory-test",
                true,
                "eneikproductionsys",
                "eneikcoworking-ctrl",
                30,
                3,
                true,
                new ObjectMapper()
        );

        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setName("test-project");
        project.setSlug("test-project");
        project.setStatus(ProjectStatus.active);
        project.setRepositoryName("org/test-project");

        ProjectOperationalContextService.ProjectOperationalContext context =
                new ProjectOperationalContextService.ProjectOperationalContext(
                        projectId,
                        "test-project",
                        Map.of("project", Map.of("name", "test-project")),
                        "{\"project\":{\"name\":\"test-project\"}}",
                        new ProjectOperationalContextService.PrStats(
                                false,
                                0,
                                0,
                                "",
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of()
                        )
                );

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(contextService.build(projectId, "test-project")).thenReturn(context);
        when(mlPredictionServiceClient.chat(anyString(), contains("tool planner")))
                .thenReturn("{\"toolCalls\":[]}");
        when(mlPredictionServiceClient.chat(anyString(), contains("PROJECT_FACT_PACK")))
                .thenReturn("Hello! PROJECT_FACT_PACK and OPERATOR_EVIDENCE show no runnable tests.");
        when(mlPredictionServiceClient.chat(anyString(), contains("answer critic")))
                .thenReturn("{\"verdict\":\"pass\",\"issues\":[]}");

        String answer = service.answer(projectId, "test-project", "What can you test?");

        assertEquals("the selected project facts and the collected operator evidence show no runnable tests.", answer);
    }
}
