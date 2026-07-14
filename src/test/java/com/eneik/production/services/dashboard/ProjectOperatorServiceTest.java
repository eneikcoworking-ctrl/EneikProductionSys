package com.eneik.production.services.dashboard;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.services.ClaimService;
import com.eneik.production.services.MLPredictionServiceClient;
import com.eneik.production.services.ProjectFlowService;
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

        ProjectOperatorService service = new ProjectOperatorService(
                projectRepository,
                contextService,
                mlPredictionServiceClient,
                projectFlowService,
                claimService,
                "project-workspaces",
                ".",
                true,
                "eneikproductionsys",
                30,
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
}
