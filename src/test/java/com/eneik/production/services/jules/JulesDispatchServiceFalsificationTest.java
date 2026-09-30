package com.eneik.production.services.jules;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TargetContext;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.DesignShopCycleRepository;
import com.eneik.production.repositories.FeatureThreadRepository;
import com.eneik.production.repositories.JulesActivityResponseRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.ReviewConcernRepository;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.ClaimService;
import com.eneik.production.services.ClientDeliverableReadinessService;
import com.eneik.production.services.FalsificationCycleService;
import com.eneik.production.services.GeminiContextService;
import com.eneik.production.services.MLPredictionServiceClient;
import com.eneik.production.services.PersistentWorkerSessionService;
import com.eneik.production.services.ProjectFlowService;
import com.eneik.production.services.RoleCapabilityLoader;
import com.eneik.production.services.WishlistContentSimilarityMatcher;
import com.eneik.production.services.accounts.AccountHealthService;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.monitor.PrReviewPipelineService;
import com.eneik.production.services.monitor.SystemProgressTracker;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.stitch.StitchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Falsification test suite for {@link JulesDispatchService}.
 * <p>
 * Philosophical anchors from RAG corpus:
 * <ul>
 *   <li>{@code AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP} [D004, Varzi]: Aggregate ownership of sessions and deduplication of active work.</li>
 *   <li>{@code NUEL_BELNAP_03_TRUTH_STATUS_TABLE} [D012, Belnap]: Three-valued target context resolution (PRODUCT_CODEBASE, ORCHESTRATOR_SYSTEM, UNDETERMINED).</li>
 *   <li>{@code DZHOZEF_RAZ_01_PROHIBITION_AS_CODE} [D006, Raz]: Normative refusal to dispatch tasks with undetermined target context or missing project.</li>
 * </ul>
 */
class JulesDispatchServiceFalsificationTest {

    private JulesApiClient julesApiClient;
    private JulesSessionRepository julesSessionRepository;
    private TaskRepository taskRepository;
    private ClaimService claimService;
    private JulesDispatchService dispatchService;

    @BeforeEach
    void setUp() {
        julesApiClient = mock(JulesApiClient.class);
        julesSessionRepository = mock(JulesSessionRepository.class);
        taskRepository = mock(TaskRepository.class);
        claimService = mock(ClaimService.class);
        TaskConflictRepository taskConflictRepository = mock(TaskConflictRepository.class);
        AccountRepository accountRepository = mock(AccountRepository.class);

        SessionLifecycleService sessionLifecycleService = new SessionLifecycleService(
                julesSessionRepository, accountRepository, taskRepository, julesApiClient, null);
        ReflectionTestUtils.setField(sessionLifecycleService, "self", sessionLifecycleService);

        dispatchService = new JulesDispatchService(
                julesApiClient,
                julesSessionRepository,
                mock(JulesActivityResponseRepository.class),
                mock(WishlistRepository.class),
                accountRepository,
                taskRepository,
                taskConflictRepository,
                claimService,
                mock(RoleCapabilityLoader.class),
                mock(PrReviewPipelineService.class),
                mock(MLPredictionServiceClient.class),
                mock(RoleRepository.class),
                mock(GitHubPullRequestService.class),
                mock(PrReviewRepository.class),
                mock(SystemProgressTracker.class),
                mock(ProjectFlowService.class),
                mock(FalsificationCycleService.class),
                mock(FeatureThreadRepository.class),
                mock(ClientDeliverableReadinessService.class),
                mock(PersistentWorkerSessionService.class),
                mock(ProjectRepository.class),
                mock(WishlistContentSimilarityMatcher.class),
                mock(SystemSettingsService.class),
                mock(GeminiContextService.class),
                mock(ReviewConcernRepository.class),
                mock(AccountHealthService.class),
                sessionLifecycleService,
                mock(DesignShopCycleRepository.class),
                mock(StitchClient.class),
                "prefix/",
                null
        );
        ReflectionTestUtils.setField(dispatchService, "self", dispatchService);
        when(julesSessionRepository.save(any(JulesSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("DZHOZEF_RAZ_01: Undetermined target context strictly rejects dispatch and persists failed session")
    void raz_undeterminedTargetContextRefusesDispatch() {
        UUID taskId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.claimed);
        task.setTargetContext(TargetContext.UNDETERMINED);

        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(Collections.emptyList());

        JulesDispatchResult result = dispatchService.dispatch(task, accountId);

        // 1. Result reflects explicit refusal
        assertFalse(result.dispatched(), "Task with UNDETERMINED target context must not be dispatched");
        assertEquals("Dispatch rejected: target context is undetermined", result.reason());

        // 2. A failed session record is persisted with explainable reason
        ArgumentCaptor<JulesSessionEntity> captor = ArgumentCaptor.forClass(JulesSessionEntity.class);
        verify(julesSessionRepository).save(captor.capture());
        JulesSessionEntity saved = captor.getValue();
        assertEquals("failed", saved.getStatus());
        assertEquals("Dispatch rejected: target context is undetermined", saved.getClosureReason());
        assertEquals(taskId, saved.getTaskId());

        // 3. No external Jules call was made
        verifyNoInteractions(julesApiClient);
    }

    @Test
    @DisplayName("DZHOZEF_RAZ_01: Null target context is treated identically to UNDETERMINED and refused")
    void raz_nullTargetContextRefusesDispatch() {
        UUID taskId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.claimed);
        task.setTargetContext(null);

        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(Collections.emptyList());

        JulesDispatchResult result = dispatchService.dispatch(task, accountId);

        assertFalse(result.dispatched(), "Task with null target context must not be dispatched");
        assertEquals("Dispatch rejected: target context is undetermined", result.reason());
        verifyNoInteractions(julesApiClient);
    }

    @Test
    @DisplayName("AHILLE_VARTSI_02: Task with existing active session deduplicates and skips redundant dispatch")
    void varzi_existingActiveSessionSkipsDuplicateDispatch() {
        UUID taskId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.claimed);
        task.setTargetContext(TargetContext.PRODUCT_CODEBASE);

        JulesSessionEntity activeSession = new JulesSessionEntity();
        activeSession.setId(UUID.randomUUID());
        activeSession.setTaskId(taskId);
        activeSession.setExternalSessionId("ext-active-999");
        activeSession.setStatus("running"); // Belongs to ACTIVE_SESSION_STATUSES

        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(activeSession));

        JulesDispatchResult result = dispatchService.dispatch(task, accountId);

        assertTrue(result.dispatched(), "Returns true to indicate task is already dispatched");
        assertEquals("ext-active-999", result.sessionName());
        assertEquals("already dispatched, skipping duplicate", result.reason());

        // No new session was saved and no HTTP call made
        verify(julesSessionRepository, never()).save(any());
        verifyNoInteractions(julesApiClient);
    }

    @Test
    @DisplayName("AHILLE_VARTSI_02: hasActiveSession matches exactly ACTIVE_SESSION_STATUSES contract")
    void varzi_hasActiveSessionRespectsActiveStatusContract() {
        UUID taskId = UUID.randomUUID();

        JulesSessionEntity running = new JulesSessionEntity();
        running.setStatus("running");
        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(running));
        assertTrue(dispatchService.hasActiveSession(taskId), "'running' must be recognized as active");

        JulesSessionEntity queued = new JulesSessionEntity();
        queued.setStatus("queued");
        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(queued));
        assertTrue(dispatchService.hasActiveSession(taskId), "'queued' must be recognized as active");

        JulesSessionEntity completed = new JulesSessionEntity();
        completed.setStatus("completed");
        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(completed));
        assertFalse(dispatchService.hasActiveSession(taskId), "'completed' must not be recognized as active");

        JulesSessionEntity failed = new JulesSessionEntity();
        failed.setStatus("failed");
        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(failed));
        assertFalse(dispatchService.hasActiveSession(taskId), "'failed' must not be recognized as active");
    }

    @Test
    @DisplayName("NUEL_BELNAP_03: Three-valued target context resolution distinguishes product, orchestrator, and undetermined")
    void belnap_threeValuedTargetContextResolution() {
        // TargetContext has explicit trichotomy: PRODUCT_CODEBASE, ORCHESTRATOR_SYSTEM, UNDETERMINED
        TargetContext[] values = TargetContext.values();
        assertEquals(3, values.length);
        assertTrue(List.of(values).contains(TargetContext.PRODUCT_CODEBASE));
        assertTrue(List.of(values).contains(TargetContext.ORCHESTRATOR_SYSTEM));
        assertTrue(List.of(values).contains(TargetContext.UNDETERMINED));
    }

    @Test
    @DisplayName("DZHOZEF_RAZ_01: Task missing project reference is rejected with explainable closure reason")
    void raz_taskWithoutProjectRefusesDispatch() {
        UUID taskId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.claimed);
        task.setTargetContext(TargetContext.PRODUCT_CODEBASE);
        task.setProject(null); // Missing project

        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(Collections.emptyList());

        JulesDispatchResult result = dispatchService.dispatch(task, accountId);

        assertFalse(result.dispatched());
        assertEquals("No project found for task", result.reason());
        verifyNoInteractions(julesApiClient);
    }
}
