package com.eneik.production.services;

import com.eneik.production.models.persistence.GeminiObserverActionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.GeminiObserverActionRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.jules.JulesDispatchService;
import com.eneik.production.services.operational.OperationalAction;
import com.eneik.production.services.operational.OperationalPolicyService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Every one of her five actions must go through the same operational policy gate as any other mutating
 * path in the system (2026-07-30) - no side door around the enforced Flow Core, exactly the gap found while
 * diagnosing why she was silently doing nothing on a hard-stopped project.
 */
class GeminiObserverActionServiceTest {

    private WishlistRepository wishlistRepository;
    private TaskRepository taskRepository;
    private TaskConflictRepository taskConflictRepository;
    private JulesDispatchService julesDispatchService;
    private FalsificationCycleService falsificationCycleService;
    private GeminiObserverActionRepository actionRepository;
    private OperationalPolicyService operationalPolicyService;
    private PlannedWorkRecoveryService plannedWorkRecoveryService;
    private GeminiObserverActionService service;

    private void setUp() {
        wishlistRepository = mock(WishlistRepository.class);
        taskRepository = mock(TaskRepository.class);
        taskConflictRepository = mock(TaskConflictRepository.class);
        julesDispatchService = mock(JulesDispatchService.class);
        falsificationCycleService = mock(FalsificationCycleService.class);
        actionRepository = mock(GeminiObserverActionRepository.class);
        operationalPolicyService = mock(OperationalPolicyService.class);
        plannedWorkRecoveryService = mock(PlannedWorkRecoveryService.class);
        service = new GeminiObserverActionService(wishlistRepository, taskRepository, taskConflictRepository,
                julesDispatchService, falsificationCycleService, actionRepository, operationalPolicyService,
                plannedWorkRecoveryService);
    }

    private ProjectEntity project() {
        ProjectEntity p = new ProjectEntity();
        p.setId(UUID.randomUUID());
        return p;
    }

    private OperationalPolicyService.OperationalDecision decision(ProjectEntity project, OperationalAction action,
                                                                    boolean allowed, String reason) {
        return new OperationalPolicyService.OperationalDecision(
                project.getId(), action, allowed, "SOME_STATE", "STATUS", reason, List.of(), null);
    }

    @Test
    void policyDenialBlocksTheActionAndNeverTouchesTheWishlist() {
        setUp();
        ProjectEntity project = project();
        UUID wishlistId = UUID.randomUUID();
        when(operationalPolicyService.authorize(project.getId(), OperationalAction.DISMISS_WISHLIST))
                .thenReturn(decision(project, OperationalAction.DISMISS_WISHLIST, false,
                        "Operational action DISMISS_WISHLIST denied by Flow Core state GITHUB_RATE_LIMITED."));

        String outcome = service.dismissWishlist(project, wishlistId.toString(), "looks dead");

        assertTrue(outcome.startsWith("denied:"));
        verifyNoInteractions(wishlistRepository);
        ArgumentCaptor<GeminiObserverActionEntity> captor = ArgumentCaptor.forClass(GeminiObserverActionEntity.class);
        verify(actionRepository).save(captor.capture());
        assertEquals("denied", captor.getValue().getOutcome());
        assertTrue(captor.getValue().isVerified());
    }

    @Test
    void policyAllowLetsTheActionProceedAsBefore() {
        setUp();
        ProjectEntity project = project();
        UUID wishlistId = UUID.randomUUID();
        when(operationalPolicyService.authorize(project.getId(), OperationalAction.DISMISS_WISHLIST))
                .thenReturn(decision(project, OperationalAction.DISMISS_WISHLIST, true, "allowed"));
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(wishlistId);
        wishlist.setProjectId(project.getId());
        wishlist.setStatus(WishlistStatus.pending);
        when(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist));

        String outcome = service.dismissWishlist(project, wishlistId.toString(), "looks dead");

        assertEquals("success", outcome);
        assertEquals(WishlistStatus.dismissed, wishlist.getStatus());
        verify(wishlistRepository).save(wishlist);
    }

    @Test
    void triggerFalsificationRunIsGatedByTheAuditPipelineAction() {
        // Reuses RUN_PROJECT_AUDIT_PIPELINE rather than a dedicated action type - it IS that same pipeline,
        // just pulled forward on her request.
        setUp();
        ProjectEntity project = project();
        when(operationalPolicyService.authorize(project.getId(), OperationalAction.RUN_PROJECT_AUDIT_PIPELINE))
                .thenReturn(decision(project, OperationalAction.RUN_PROJECT_AUDIT_PIPELINE, false, "not ready"));

        String outcome = service.triggerFalsificationRun(project, project.getId().toString(), "readiness gate met");

        assertTrue(outcome.startsWith("denied:"));
        verifyNoInteractions(falsificationCycleService);
    }

    @Test
    void abandonConflictDeniedByPolicyNeverTouchesTheConflict() {
        setUp();
        ProjectEntity project = project();
        UUID conflictId = UUID.randomUUID();
        when(operationalPolicyService.authorize(project.getId(), OperationalAction.ABANDON_CONFLICT))
                .thenReturn(decision(project, OperationalAction.ABANDON_CONFLICT, false, "project terminal"));

        String outcome = service.abandonConflict(project, conflictId.toString(), "beyond resolving");

        assertTrue(outcome.startsWith("denied:"));
        verifyNoInteractions(taskConflictRepository);
    }

    @Test
    void reviveFailedTaskDeniedByPolicyNeverTouchesPlannedWorkRecovery() {
        setUp();
        ProjectEntity project = project();
        UUID taskId = UUID.randomUUID();
        when(operationalPolicyService.authorize(project.getId(), OperationalAction.REVIVE_FAILED_TASK))
                .thenReturn(decision(project, OperationalAction.REVIVE_FAILED_TASK, false, "GitHub rate limited"));

        String outcome = service.reviveFailedTask(project, taskId.toString(), "PR closed without merge");

        assertTrue(outcome.startsWith("denied:"));
        verifyNoInteractions(plannedWorkRecoveryService);
    }

    @Test
    void reviveFailedTaskCallsThePlannedWorkRecoveryAtomicResumePath() {
        setUp();
        ProjectEntity project = project();
        UUID taskId = UUID.randomUUID();
        when(operationalPolicyService.authorize(project.getId(), OperationalAction.REVIVE_FAILED_TASK))
                .thenReturn(decision(project, OperationalAction.REVIVE_FAILED_TASK, true, "allowed"));
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setStatus(TaskStatus.failed);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(plannedWorkRecoveryService.resumeTask(taskId)).thenReturn(true);

        String outcome = service.reviveFailedTask(project, taskId.toString(), "PR closed without merge");

        assertEquals("success", outcome);
        verify(plannedWorkRecoveryService).resumeTask(taskId);
    }
}
