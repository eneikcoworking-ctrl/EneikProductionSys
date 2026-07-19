package com.eneik.production.services;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.*;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.compiler.TechnicalLeadCompiler;
import com.eneik.production.services.jules.JulesApiClient;
import com.eneik.production.services.jules.JulesDispatchResult;
import com.eneik.production.services.jules.JulesDispatchService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdempotencyTest {

    @Test
    void testTaskCompilationIdempotency() {
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);

        TechnicalLeadCompiler compiler = new TechnicalLeadCompiler(
            wishlistRepository, taskRepository, projectRepository,
            mock(RoleRepository.class), mock(ProjectGenerationStateRepository.class),
            mock(com.eneik.production.services.gate.GateOrchestrator.class),
            mock(BottleneckAwarePriorityService.class),
            new com.fasterxml.jackson.databind.ObjectMapper(),
            mock(ProjectHotspotFileRepository.class)
        );

        UUID wishlistId = UUID.randomUUID();
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(wishlistId);
        wishlist.setStatus(WishlistStatus.converted_to_task);
        wishlist.setContent("Test content");
        wishlist.setProjectId(UUID.randomUUID());
        wishlist.setCompiledByRole("BARCAN-TAG-09");
        wishlist.setTocConstraintRef("ref");
        wishlist.setLeanValue(LeanValue.essential);
        wishlist.setJtbd("jtbd");
        wishlist.setSixSigmaMetric("metric");
        wishlist.setDod("dod BARCAN-TAG-01");
        wishlist.setAcceptanceCriteria("criteria");

        ProjectEntity project = new ProjectEntity();
        project.setId(wishlist.getProjectId());
        project.setStatus(ProjectStatus.active);

        when(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist));
        when(projectRepository.findById(wishlist.getProjectId())).thenReturn(Optional.of(project));

        TaskEntity existingTask = new TaskEntity();
        when(taskRepository.findByProjectIdAndDescription(any(), any())).thenReturn(Optional.of(existingTask));

        TaskEntity result = compiler.createTaskFromWishlist(wishlistId);

        assertSame(existingTask, result);
        verify(taskRepository, never()).save(any());
    }

    @Test
    void testJulesDispatchIdempotency() {
        JulesSessionRepository sessionRepository = mock(JulesSessionRepository.class);
        JulesApiClient apiClient = mock(JulesApiClient.class);

        JulesDispatchService dispatchService = new JulesDispatchService(
            apiClient, sessionRepository,
            mock(com.eneik.production.repositories.JulesActivityResponseRepository.class),
            mock(WishlistRepository.class),
            mock(com.eneik.production.repositories.AccountRepository.class),
            mock(TaskRepository.class),
            mock(com.eneik.production.repositories.TaskConflictRepository.class),
            mock(ClaimService.class),
            mock(RoleCapabilityLoader.class),
            mock(com.eneik.production.services.monitor.PrReviewPipelineService.class),
            mock(com.eneik.production.services.MLPredictionServiceClient.class),
            mock(com.eneik.production.repositories.RoleRepository.class),
            mock(com.eneik.production.services.github.GitHubPullRequestService.class),
            mock(com.eneik.production.repositories.PrReviewRepository.class),
            mock(com.eneik.production.services.monitor.SystemProgressTracker.class),
            mock(com.eneik.production.services.ProjectFlowService.class),
            mock(com.eneik.production.repositories.NeedsHumanReviewRepository.class),
            mock(com.eneik.production.services.FalsificationCycleService.class),
            mock(com.eneik.production.repositories.RoleThreadRepository.class),
            "prefix/"
        );

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());

        JulesSessionEntity activeSession = new JulesSessionEntity();
        activeSession.setStatus("running");
        activeSession.setExternalSessionId("ext-123");

        when(sessionRepository.findByTaskId(task.getId())).thenReturn(Collections.singletonList(activeSession));

        JulesDispatchResult result = dispatchService.dispatch(task);

        assertTrue(result.dispatched());
        assertEquals("already dispatched, skipping duplicate", result.reason());
        verify(apiClient, never()).createSession(any(), any(), any());
    }
}
