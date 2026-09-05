package com.eneik.production.services;

import com.eneik.production.dto.dashboard.BlockedItemDto;
import com.eneik.production.dto.dashboard.BottleneckDto;
import com.eneik.production.dto.dashboard.QueueDashboardDto;
import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.dashboard.BottleneckDetectionService;
import com.eneik.production.services.jules.JulesDispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Law 8 (Закон вариантной функции / Variant Function & Empirical Windows) Test Suite:
 *
 *   ∀ цикл L ∃ v: Состояние → ℕ, v ограничена снизу, v строго убывает на каждом обороте L
 *
 * Grounding: Ludwig Wittgenstein (Tractatus 1.1 / facts over arbitrary spatial intervals)
 * & Henri Bergson (lived duration vs spatialized seconds).
 *
 * 1. Variant function strictly decreases on every attempt:
 *    - Compilation: v = A(w) - compileAttempts(w)
 *    - Carrier retries: v = MAX_RETRIES - retryCount(t)
 *    - Resume: v = 1 - resumeCount(t)
 *    - Repair: v = A(w) - repairDepth(w)
 * 2. Single Well-Founded Limit on Blind Cycles:
 *    - v = forcedUnblockBlindCycleThreshold - blindCycleCount terminates the loop.
 *    - The 60-minute trust window does not block blind recovery.
 * 3. Empirical Initial Ceiling Derivation:
 *    - A(w) is derived from project observation history f(observations), not an unexamined constant.
 *    - Upward: raised when conversion succeeds at the boundary attempt.
 *    - Downward: clamped on empty compiler answer (evidence about the brief), establishing decompositionRefused.
 * 4. Observable Channel Facts over Arbitrary Spatial Windows:
 *    - Epic cleanup: withholds when compilation is in flight, judges when finished.
 *    - Blocked items: recognizes orphaned in-progress work immediately without 2-hour wait.
 *    - Bottleneck detection: recognizes structural pool depletion immediately without 10-minute wait.
 */
class VariantFunctionLaw8Test {

    private WishlistRepository wishlistRepository;
    private TaskRepository taskRepository;
    private AccountRepository accountRepository;
    private ClaimRepository claimRepository;
    private ClaimService claimService;
    private ClientDeliverableReadinessService readinessService;
    private ProjectFlowService projectFlowService;

    @BeforeEach
    void setUp() {
        wishlistRepository = mock(WishlistRepository.class);
        taskRepository = mock(TaskRepository.class);
        accountRepository = mock(AccountRepository.class);
        claimRepository = mock(ClaimRepository.class);
        claimService = mock(ClaimService.class);
        readinessService = mock(ClientDeliverableReadinessService.class);

        projectFlowService = mock(ProjectFlowService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(projectFlowService, "wishlistRepository", wishlistRepository);
        ReflectionTestUtils.setField(projectFlowService, "taskRepository", taskRepository);
        ReflectionTestUtils.setField(projectFlowService, "claimService", claimService);
        ReflectionTestUtils.setField(projectFlowService, "readinessService", readinessService);
    }

    @Test
    @DisplayName("Law 8.1: Compilation variant function v = A(w) - compileAttempts(w) strictly decreases and exhausts")
    void compilationVariantFunctionStrictlyDecreases() {
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(UUID.randomUUID());
        wishlist.setCompileAttemptCeiling(3);
        wishlist.setCompileAttempts(0);

        int v0 = wishlist.effectiveCompileCeiling() - wishlist.getCompileAttempts();
        assertEquals(3, v0);
        assertFalse(wishlist.decompositionExhausted());

        // Attempt 1 reaching compiler
        wishlist.setCompileAttempts(wishlist.getCompileAttempts() + 1);
        int v1 = wishlist.effectiveCompileCeiling() - wishlist.getCompileAttempts();
        assertEquals(2, v1);
        assertTrue(v1 < v0);
        assertFalse(wishlist.decompositionExhausted());

        // Attempt 2 reaching compiler
        wishlist.setCompileAttempts(wishlist.getCompileAttempts() + 1);
        int v2 = wishlist.effectiveCompileCeiling() - wishlist.getCompileAttempts();
        assertEquals(1, v2);
        assertTrue(v2 < v1);
        assertFalse(wishlist.decompositionExhausted());

        // Attempt 3 reaching compiler -> exhausting
        wishlist.setCompileAttempts(wishlist.getCompileAttempts() + 1);
        int v3 = wishlist.effectiveCompileCeiling() - wishlist.getCompileAttempts();
        assertEquals(0, v3);
        assertTrue(v3 < v2);
        assertTrue(wishlist.decompositionExhausted());
    }

    @Test
    @DisplayName("Law 8.2: Initial compile ceiling is derived from empirical project history, not a blind constant")
    void initialCompileCeilingDerivedFromProjectObservationHistory() {
        UUID projectId = UUID.randomUUID();

        // Case 1: Fresh project with no history -> defaults to baseline budget (3)
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(List.of());
        int ceilingFresh = projectFlowService.deriveInitialCompileCeiling(projectId);
        assertEquals(WishlistEntity.COMPILE_ATTEMPT_BUDGET, ceilingFresh);

        // Case 2: Project with converted briefs requiring up to 3 attempts -> derived ceiling is max(3+1, 3) = 4
        WishlistEntity convertedBrief = new WishlistEntity();
        convertedBrief.setStatus(WishlistStatus.converted_to_task);
        convertedBrief.setCompileAttempts(3);

        when(wishlistRepository.findByProjectId(projectId)).thenReturn(List.of(convertedBrief));
        int ceilingObserved = projectFlowService.deriveInitialCompileCeiling(projectId);
        assertEquals(4, ceilingObserved);

        // Case 3: Project with established higher ceiling (5) on open briefs -> preserves 5
        WishlistEntity highCeilingBrief = new WishlistEntity();
        highCeilingBrief.setStatus(WishlistStatus.pending);
        highCeilingBrief.setCompileAttemptCeiling(5);

        when(wishlistRepository.findByProjectId(projectId)).thenReturn(List.of(convertedBrief, highCeilingBrief));
        int ceilingEstablished = projectFlowService.deriveInitialCompileCeiling(projectId);
        assertEquals(5, ceilingEstablished);
    }

    @Test
    @DisplayName("Law 8.3: Boundary success raises ceiling upward, while empty answer clamps ceiling downward")
    void boundarySuccessRaisesCeilingAndEmptyAnswerClampsCeiling() {
        UUID projectId = UUID.randomUUID();

        // Upward: success at boundary
        WishlistEntity convertedAtBoundary = new WishlistEntity();
        convertedAtBoundary.setId(UUID.randomUUID());
        convertedAtBoundary.setProjectId(projectId);
        convertedAtBoundary.setCompileAttemptCeiling(3);
        convertedAtBoundary.setCompileAttempts(3); // succeeded on attempt 3 of 3

        WishlistEntity stillOpen = new WishlistEntity();
        stillOpen.setId(UUID.randomUUID());
        stillOpen.setProjectId(projectId);
        stillOpen.setStatus(WishlistStatus.pending);
        stillOpen.setCompileAttemptCeiling(3);

        when(wishlistRepository.findByProjectId(projectId)).thenReturn(List.of(stillOpen));

        projectFlowService.raiseCompileCeilingIfTheProbeSurvivedAtTheBoundary(convertedAtBoundary);

        assertEquals(4, stillOpen.getCompileAttemptCeiling());
        verify(wishlistRepository).saveAll(List.of(stillOpen));

        // Downward: compiler answered with emptiness -> evidence about the brief
        WishlistEntity emptyResponseBrief = new WishlistEntity();
        emptyResponseBrief.setId(UUID.randomUUID());
        emptyResponseBrief.setCompileAttempts(1);
        emptyResponseBrief.setCompileAttemptCeiling(4);
        emptyResponseBrief.setLastCompileReachedAt(Instant.now());

        when(wishlistRepository.findAllById(List.of(emptyResponseBrief.getId()))).thenReturn(List.of(emptyResponseBrief));

        projectFlowService.clampCompileCeilingOnEmptyCompilerAnswer(List.of(emptyResponseBrief.getId()));

        assertEquals(1, emptyResponseBrief.getCompileAttemptCeiling());
        assertTrue(emptyResponseBrief.decompositionExhausted());
        assertTrue(emptyResponseBrief.decompositionRefused());
    }

    @Test
    @DisplayName("Law 8.4: Blind cycles loop terminates on its declared limit without being held by 60-minute trust window")
    void blindCyclesLoopTerminatesOnDeclaredThresholdWithoutTrustWindowBlock() {
        JulesSessionRepository julesSessionRepository = mock(JulesSessionRepository.class);
        JulesDispatchService dispatchService = mock(JulesDispatchService.class, CALLS_REAL_METHODS);

        ReflectionTestUtils.setField(dispatchService, "julesSessionRepository", julesSessionRepository);
        ReflectionTestUtils.setField(dispatchService, "taskRepository", taskRepository);
        ReflectionTestUtils.setField(dispatchService, "projectFlowService", projectFlowService);
        ReflectionTestUtils.setField(dispatchService, "gitHubPullRequestService", mock(com.eneik.production.services.github.GitHubPullRequestService.class));
        ReflectionTestUtils.setField(dispatchService, "julesApiClient", mock(com.eneik.production.services.jules.JulesApiClient.class));
        ReflectionTestUtils.setField(dispatchService, "forcedUnblockBlindCycleThreshold", 5);
        ReflectionTestUtils.setField(dispatchService, "forcedUnblockMaxAttempts", 2);

        JulesSessionEntity blindSession = new JulesSessionEntity();
        blindSession.setId(UUID.randomUUID());
        blindSession.setExternalSessionId("sessions/test-blind-law8");
        blindSession.setStatus("running");
        blindSession.setBlindCycleCount(5); // Reached declared threshold
        blindSession.setForcedUnblockAttempts(0);
        // lastProgress was 5 minutes ago (< 60 minutes, so trust window would previously block it)
        blindSession.setLastProgressAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        blindSession.setTaskId(UUID.randomUUID());

        TaskEntity task = new TaskEntity();
        task.setId(blindSession.getTaskId());
        ProjectEntity project = new ProjectEntity();
        project.setStatus(ProjectStatus.active);
        task.setProject(project);

        when(julesSessionRepository.findByStatusIn(anyList())).thenReturn(List.of(blindSession));
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        dispatchService.forceUnblockOverflowedSessions();

        // The session must have attempted unblock and reset blindCycleCount to 0,
        // proving it was NOT blocked by the 60-minute trust window
        assertEquals(1, blindSession.getForcedUnblockAttempts());
        assertEquals(0, blindSession.getBlindCycleCount());
        verify(julesSessionRepository).save(blindSession);
    }

    @Test
    @DisplayName("Law 8.5: Orphaned in-progress task without claim is recognized immediately without 2-hour delay")
    void blockedItemsRecognizesUnclaimedInProgressImmediately() {
        TaskEntity activeTask = new TaskEntity();
        activeTask.setId(UUID.randomUUID());
        activeTask.setStatus(TaskStatus.in_progress);
        activeTask.setUpdatedAt(Instant.now().minus(10, ChronoUnit.MINUTES)); // Only 10 mins ago

        TaskEntity orphanedTask = new TaskEntity();
        orphanedTask.setId(UUID.randomUUID());
        orphanedTask.setStatus(TaskStatus.in_progress);
        orphanedTask.setUpdatedAt(Instant.now().minus(5, ChronoUnit.MINUTES)); // Only 5 mins ago (< 2 hours)

        // activeTask has an active claim
        when(claimService.hasActiveClaim(activeTask.getId())).thenReturn(true);
        // orphanedTask has NO active claim
        when(claimService.hasActiveClaim(orphanedTask.getId())).thenReturn(false);

        List<BlockedItemDto> blocked = projectFlowService.computeBlockedItems(List.of(activeTask, orphanedTask));

        // activeTask is NOT blocked
        assertTrue(blocked.stream().noneMatch(b -> b.id().equals(activeTask.getId())));

        // orphanedTask IS recognized as blocked immediately
        Optional<BlockedItemDto> orphanedBlocked = blocked.stream()
                .filter(b -> b.id().equals(orphanedTask.getId()))
                .findFirst();
        assertTrue(orphanedBlocked.isPresent());
        assertEquals("unclaimed_in_progress", orphanedBlocked.get().reason());
    }

    @Test
    @DisplayName("Law 8.6: Bottleneck detection recognizes structural pool depletion immediately without 10-minute wait")
    void bottleneckDetectionReportsStructuralDepletionImmediately() {
        BottleneckDetectionService service = new BottleneckDetectionService(taskRepository, accountRepository, claimRepository);

        String tag = "BARCAN-TAG-02";
        // Task queued only 1 minute ago (< 10 minutes)
        QueueDashboardDto.TagCountDto row = new QueueDashboardDto.TagCountDto(tag, 2L, 1L);
        when(taskRepository.queuedGroupedByTag()).thenReturn(List.of(row));
        when(accountRepository.existsJulesAccountWithCapacity(eq(tag), anyInt())).thenReturn(false);

        // All accounts are daily_limited or api_blocked (activeAccounts = 0)
        AccountEntity acc1 = new AccountEntity();
        acc1.setStatus(AccountStatus.daily_limited);
        AccountEntity acc2 = new AccountEntity();
        acc2.setStatus(AccountStatus.api_blocked);
        when(accountRepository.findAll()).thenReturn(List.of(acc1, acc2));

        List<BottleneckDto> bottlenecks = service.detect();

        assertEquals(1, bottlenecks.size());
        assertEquals("no_free_jules_slot", bottlenecks.get(0).type());
        assertEquals(tag, bottlenecks.get(0).tag());
        assertTrue(bottlenecks.get(0).reason().contains("Capacity reduction"));
    }

    @Test
    @DisplayName("Law 8.7: Epic cleanup withholds judgment while compilation is actively in flight regardless of age")
    void epicCleanupWithholdsWhenCompilationInFlight() {
        ClientDeliverableReadinessService readiness = mock(ClientDeliverableReadinessService.class, CALLS_REAL_METHODS);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        FeatureRepository featureRepository = mock(FeatureRepository.class);

        ReflectionTestUtils.setField(readiness, "projectRepository", projectRepository);
        ReflectionTestUtils.setField(readiness, "wishlistRepository", wishlistRepository);
        ReflectionTestUtils.setField(readiness, "featureRepository", featureRepository);

        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));

        // Wishlist is currently compiling
        WishlistEntity compilingWishlist = new WishlistEntity();
        compilingWishlist.setId(UUID.randomUUID());
        compilingWishlist.setProjectId(projectId);
        compilingWishlist.setStatus(WishlistStatus.compiling);

        when(wishlistRepository.findByProjectId(projectId)).thenReturn(List.of(compilingWishlist));

        // Epic is older than 10 minutes (e.g. 25 minutes old) with 0 items
        ClientDeliverableReadinessService.EpicDiagnostic diagnostic = new ClientDeliverableReadinessService.EpicDiagnostic(
                UUID.randomUUID(),
                "Valueless Candidate Epic",
                UUID.randomUUID(),
                Instant.now().minus(25, ChronoUnit.MINUTES),
                true,
                false,
                0, // 0 code producing items
                0  // 0 merged items
        );
        doReturn(List.of(diagnostic)).when(readiness).listEpicDiagnostics(projectId);

        readiness.deleteValuelessEpics();

        // Verified: epic was NOT deleted because compilation is in flight!
        verify(featureRepository, never()).deleteById(any());
        verify(featureRepository, never()).save(any());
    }
}
