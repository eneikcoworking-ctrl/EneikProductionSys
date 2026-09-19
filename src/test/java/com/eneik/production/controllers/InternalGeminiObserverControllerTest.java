package com.eneik.production.controllers;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.PersistentWorkerPurpose;
import com.eneik.production.models.persistence.PersistentWorkerSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.CoherenceRunRepository;
import com.eneik.production.repositories.EvidenceNodeRepository;
import com.eneik.production.repositories.GeminiObserverActionRepository;
import com.eneik.production.repositories.GeminiObserverJournalRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.OperationalRealityFindingRepository;
import com.eneik.production.repositories.PersistentWorkerSessionRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.ContinuousOrchestrationService;
import com.eneik.production.services.GeminiObserverActionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for InternalGeminiObserverController repairing Section XXIII and Item 8 defect
 * (NUEL_BELNAP_03_TRUTH_STATUS_TABLE, DZHOZEF_RAZ_01_PROHIBITION_AS_CODE / D012, D006).
 */
class InternalGeminiObserverControllerTest {

    private ProjectRepository projectRepository;
    private PersistentWorkerSessionRepository persistentWorkerSessionRepository;
    private AccountRepository accountRepository;
    private JulesSessionRepository julesSessionRepository;
    private WishlistRepository wishlistRepository;
    private ContinuousOrchestrationService continuousOrchestrationService;
    private GeminiObserverActionService geminiObserverActionService;
    private TaskRepository taskRepository;
    private InternalGeminiObserverController controller;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        persistentWorkerSessionRepository = mock(PersistentWorkerSessionRepository.class);
        accountRepository = mock(AccountRepository.class);
        julesSessionRepository = mock(JulesSessionRepository.class);
        wishlistRepository = mock(WishlistRepository.class);
        continuousOrchestrationService = mock(ContinuousOrchestrationService.class);
        geminiObserverActionService = mock(GeminiObserverActionService.class);
        taskRepository = mock(TaskRepository.class);

        controller = new InternalGeminiObserverController(
                mock(GeminiObserverJournalRepository.class),
                mock(GeminiObserverActionRepository.class),
                mock(EvidenceNodeRepository.class),
                mock(CoherenceRunRepository.class),
                mock(OperationalRealityFindingRepository.class),
                julesSessionRepository,
                mock(PrReviewRepository.class),
                accountRepository,
                taskRepository,
                continuousOrchestrationService,
                geminiObserverActionService,
                projectRepository,
                persistentWorkerSessionRepository,
                mock(JdbcTemplate.class),
                wishlistRepository
        );
    }

    @Test
    @DisplayName("persistentWorkers with explicit projectId returns workers for that project")
    void persistentWorkersWithExplicitProjectId() {
        UUID projectId = UUID.randomUUID();
        PersistentWorkerSessionEntity worker = new PersistentWorkerSessionEntity();
        worker.setId(UUID.randomUUID());
        worker.setProjectId(projectId);
        worker.setPurpose(PersistentWorkerPurpose.WISHLIST_COMPILER);
        worker.setCreatedAt(Instant.now());

        when(persistentWorkerSessionRepository.findByProjectId(projectId)).thenReturn(List.of(worker));

        List<Map<String, Object>> result = controller.persistentWorkers(projectId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("id")).isEqualTo(worker.getId());
        assertThat(result.get(0).get("projectId")).isEqualTo(projectId);
        assertThat(result.get(0).get("purpose")).isEqualTo(PersistentWorkerPurpose.WISHLIST_COMPILER);
        verify(persistentWorkerSessionRepository).findByProjectId(projectId);
    }

    @Test
    @DisplayName("persistentWorkers without projectId falls back to single active project")
    void persistentWorkersWithoutProjectIdResolvesActiveProject() {
        UUID activeProjectId = UUID.randomUUID();
        ProjectEntity activeProject = new ProjectEntity();
        activeProject.setId(activeProjectId);
        activeProject.setStatus(ProjectStatus.active);

        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active))
                .thenReturn(List.of(activeProject));

        PersistentWorkerSessionEntity worker = new PersistentWorkerSessionEntity();
        worker.setId(UUID.randomUUID());
        worker.setProjectId(activeProjectId);
        when(persistentWorkerSessionRepository.findByProjectId(activeProjectId)).thenReturn(List.of(worker));

        List<Map<String, Object>> result = controller.persistentWorkers(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("id")).isEqualTo(worker.getId());
        verify(persistentWorkerSessionRepository).findByProjectId(activeProjectId);
    }

    @Test
    @DisplayName("persistentWorkers without projectId returns UNDETERMINED_PROJECT when no single active project exists")
    void persistentWorkersWithoutProjectIdReturnsUndeterminedWhenNoActiveProject() {
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active))
                .thenReturn(List.of());

        List<Map<String, Object>> result = controller.persistentWorkers(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("status")).isEqualTo("UNDETERMINED_PROJECT");
        verify(persistentWorkerSessionRepository, never()).findAll();
    }

    @Test
    @DisplayName("dispatchCapacityProbe with explicit parameters queries capacity accurately")
    void dispatchCapacityProbeWithExplicitParams() {
        UUID projectId = UUID.randomUUID();
        String tag = "BARCAN-TAG-05";

        AccountEntity account = new AccountEntity();
        account.setName("EneikGroup");
        when(accountRepository.lockNextJulesAccountWithCapacity(eq(projectId), eq(tag), eq(3), any(), eq(15), any()))
                .thenReturn(Optional.of(account));

        Map<String, Object> result = controller.dispatchCapacityProbe(projectId, tag);

        assertThat(result.get("found")).isEqualTo(true);
        assertThat(result.get("tag")).isEqualTo(tag);
        assertThat(result.get("projectId")).isEqualTo(projectId);
        assertThat(result.get("accountName")).isEqualTo("EneikGroup");
    }

    @Test
    @DisplayName("dispatchCapacityProbe without params resolves active project and defaults tag to BARCAN-TAG-11")
    void dispatchCapacityProbeWithoutParamsResolvesActiveProjectAndDefaultTag() {
        UUID activeProjectId = UUID.randomUUID();
        ProjectEntity activeProject = new ProjectEntity();
        activeProject.setId(activeProjectId);
        activeProject.setStatus(ProjectStatus.active);

        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active))
                .thenReturn(List.of(activeProject));

        AccountEntity account = new AccountEntity();
        account.setName("JulesWorker");
        when(accountRepository.lockNextJulesAccountWithCapacity(eq(activeProjectId), eq("BARCAN-TAG-11"), eq(3), any(), eq(15), any()))
                .thenReturn(Optional.of(account));

        Map<String, Object> result = controller.dispatchCapacityProbe(null, "BARCAN-TAG-11");

        assertThat(result.get("found")).isEqualTo(true);
        assertThat(result.get("tag")).isEqualTo("BARCAN-TAG-11");
        assertThat(result.get("projectId")).isEqualTo(activeProjectId);
        assertThat(result.get("accountName")).isEqualTo("JulesWorker");
    }

    @Test
    @DisplayName("dispatchCapacityProbe without active project returns UNDETERMINED_PROJECT without 500 error")
    void dispatchCapacityProbeWithoutActiveProjectReturnsUndeterminedStatus() {
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active))
                .thenReturn(List.of());

        Map<String, Object> result = controller.dispatchCapacityProbe(null, "BARCAN-TAG-11");

        assertThat(result.get("found")).isEqualTo(false);
        assertThat(result.get("status")).isEqualTo("UNDETERMINED_PROJECT");
        assertThat(result.get("tag")).isEqualTo("BARCAN-TAG-11");
        assertThat(result).containsKey("message");
    }

    @Test
    @DisplayName("Falsification harness (Popper 1934 / D008): dispatchCapacityProbe catches database exceptions and avoids 500")
    void dispatchCapacityProbeCatchesDatabaseException() {
        UUID projectId = UUID.randomUUID();
        when(accountRepository.lockNextJulesAccountWithCapacity(any(), any(), anyInt(), any(), anyInt(), any()))
                .thenThrow(new RuntimeException("Database connection timeout during capacity lock"));

        Map<String, Object> result = controller.dispatchCapacityProbe(projectId, "BARCAN-TAG-11");

        assertThat(result.get("found")).isEqualTo(false);
        assertThat(result.get("status")).isEqualTo("PROBE_FAILED");
        assertThat(result.get("tag")).isEqualTo("BARCAN-TAG-11");
        assertThat(result.get("error")).isEqualTo("Database connection timeout during capacity lock");
    }

    @Test
    @DisplayName("Falsification harness (Popper 1934 / D008): persistentWorkers catches database exceptions and avoids 500")
    void persistentWorkersCatchesDatabaseException() {
        UUID projectId = UUID.randomUUID();
        when(persistentWorkerSessionRepository.findByProjectId(projectId))
                .thenThrow(new RuntimeException("Deadlock or table access error"));

        List<Map<String, Object>> result = controller.persistentWorkers(projectId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("status")).isEqualTo("QUERY_FAILED");
        assertThat(result.get(0).get("error")).isEqualTo("Deadlock or table access error");
    }

    @Test
    @DisplayName("dispatchCapacityProbe with blank or null tag defaults to BARCAN-TAG-11")
    void dispatchCapacityProbeDefaultsBlankTag() {
        UUID projectId = UUID.randomUUID();
        when(accountRepository.lockNextJulesAccountWithCapacity(eq(projectId), eq("BARCAN-TAG-11"), anyInt(), any(), anyInt(), any()))
                .thenReturn(Optional.empty());

        Map<String, Object> result = controller.dispatchCapacityProbe(projectId, "   ");

        assertThat(result.get("tag")).isEqualTo("BARCAN-TAG-11");
        assertThat(result.get("found")).isEqualTo(false);
    }

    @Test
    @DisplayName("Multiple active projects return UNDETERMINED_PROJECT with candidate project list")
    void multipleActiveProjectsListCandidateProjects() {
        ProjectEntity p1 = new ProjectEntity();
        p1.setId(UUID.randomUUID());
        p1.setName("Project One");
        p1.setStatus(ProjectStatus.active);

        ProjectEntity p2 = new ProjectEntity();
        p2.setId(UUID.randomUUID());
        p2.setName("Project Two");
        p2.setStatus(ProjectStatus.active);

        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active))
                .thenReturn(List.of(p1, p2));

        List<Map<String, Object>> workerResult = controller.persistentWorkers(null);
        assertThat(workerResult).hasSize(1);
        assertThat(workerResult.get(0).get("status")).isEqualTo("UNDETERMINED_PROJECT");
        assertThat(workerResult.get(0)).containsKey("activeProjects");

        Map<String, Object> probeResult = controller.dispatchCapacityProbe(null, null);
        assertThat(probeResult.get("status")).isEqualTo("UNDETERMINED_PROJECT");
        assertThat(probeResult).containsKey("activeProjects");
    }

    @Test
    @DisplayName("accountCapacity queries open sessions using bounded findByStatusIn and computes blocked metrics accurately")
    void accountCapacityUsesBoundedFindByStatusInQuery() {
        UUID accountId = UUID.randomUUID();
        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setName("test-account");
        account.setMaxConcurrentSessions(3);

        when(accountRepository.findAllByOrderByNameAsc()).thenReturn(List.of(account));
        when(accountRepository.findAll()).thenReturn(List.of(account));

        UUID taskId1 = UUID.randomUUID();
        com.eneik.production.models.persistence.JulesSessionEntity openSession =
                new com.eneik.production.models.persistence.JulesSessionEntity();
        openSession.setId(UUID.randomUUID());
        openSession.setAccountId(accountId);
        openSession.setTaskId(taskId1);
        openSession.setStatus("running");

        com.eneik.production.models.persistence.JulesSessionEntity sessionWithNullTaskId =
                new com.eneik.production.models.persistence.JulesSessionEntity();
        sessionWithNullTaskId.setId(UUID.randomUUID());
        sessionWithNullTaskId.setAccountId(accountId);
        sessionWithNullTaskId.setTaskId(null);
        sessionWithNullTaskId.setStatus("queued");

        when(julesSessionRepository.findByStatusIn(List.of("queued", "running", "revising", "stuck")))
                .thenReturn(List.of(openSession, sessionWithNullTaskId));

        com.eneik.production.models.persistence.TaskEntity task1 =
                new com.eneik.production.models.persistence.TaskEntity();
        task1.setId(taskId1);
        task1.setStatus(com.eneik.production.models.persistence.TaskStatus.in_progress);

        when(taskRepository.findById(taskId1)).thenReturn(Optional.of(task1));

        List<Map<String, Object>> result = controller.accountCapacity();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("accountName")).isEqualTo("test-account");
        assertThat(result.get(0).get("countIncludingBlocked_currentLiveBehavior")).isEqualTo(1);
        assertThat(result.get(0).get("countExcludingBlocked_afterMyFix")).isEqualTo(1);
        verify(julesSessionRepository).findByStatusIn(List.of("queued", "running", "revising", "stuck"));
        verify(julesSessionRepository, never()).findAll();
    }

    @Test
    @DisplayName("dispatchEligibilityDetail uses ordered account query and filters decommissioned accounts")
    void dispatchEligibilityDetailUsesOrderedAccountQuery() {
        UUID accountId = UUID.randomUUID();
        AccountEntity activeAccount = new AccountEntity();
        activeAccount.setId(accountId);
        activeAccount.setName("active-account");
        activeAccount.setEnabled(true);
        activeAccount.setStatus(com.eneik.production.models.persistence.AccountStatus.idle);
        activeAccount.setCapabilities("BARCAN-TAG-08");

        AccountEntity decommissioned = new AccountEntity();
        decommissioned.setId(UUID.randomUUID());
        decommissioned.setName("decommissioned-account");
        decommissioned.setEnabled(false);
        decommissioned.setStatus(com.eneik.production.models.persistence.AccountStatus.decommissioned);

        when(accountRepository.findAllByOrderByNameAsc()).thenReturn(List.of(activeAccount, decommissioned));

        List<Map<String, Object>> result = controller.dispatchEligibilityDetail("BARCAN-TAG-08");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("name")).isEqualTo("active-account");
        assertThat(result.get(0).get("hasTagCapability")).isEqualTo(true);
        verify(accountRepository).findAllByOrderByNameAsc();
    }

    @Test
    @DisplayName("releaseFinalizingWishlist invokes compareAndSetStatus and includes caller context")
    void releaseFinalizingWishlistIncludesCallerContext() {
        UUID wishlistId = UUID.randomUUID();
        when(wishlistRepository.compareAndSetStatus(wishlistId,
                com.eneik.production.models.persistence.WishlistStatus.finalizing,
                com.eneik.production.models.persistence.WishlistStatus.pending)).thenReturn(1);

        String result = controller.releaseFinalizingWishlist(wishlistId);

        assertThat(result).contains("released " + wishlistId + " back to pending");
        assertThat(result).contains("[caller:");
        verify(wishlistRepository).compareAndSetStatus(wishlistId,
                com.eneik.production.models.persistence.WishlistStatus.finalizing,
                com.eneik.production.models.persistence.WishlistStatus.pending);
    }

    @Test
    @DisplayName("resetDailySessionCountsNow triggers daily reset and includes caller context")
    void resetDailySessionCountsNowIncludesCallerContext() {
        String result = controller.resetDailySessionCountsNow();

        assertThat(result).contains("Reset sessionsDispatchedToday and daily_limited accounts");
        assertThat(result).contains("run manually by");
        verify(continuousOrchestrationService).resetDailyLimitedAccounts();
    }

    @Test
    @DisplayName("clearCorruptedSessionPrUrl clears URL, persists entity and returns caller in result")
    void clearCorruptedSessionPrUrlClearsAndReturnsCaller() {
        UUID sessionId = UUID.randomUUID();
        com.eneik.production.models.persistence.JulesSessionEntity session =
                new com.eneik.production.models.persistence.JulesSessionEntity();
        session.setId(sessionId);
        session.setPrUrl("https://github.com/org/repo/pull/42");

        when(julesSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        Map<String, Object> result = controller.clearCorruptedSessionPrUrl(sessionId);

        assertThat(result.get("sessionId")).isEqualTo(sessionId);
        assertThat(result.get("prUrlBefore")).isEqualTo("https://github.com/org/repo/pull/42");
        assertThat(result.get("prUrlAfter")).isEqualTo("null");
        assertThat(result).containsKey("caller");
        assertThat(session.getPrUrl()).isNull();
        verify(julesSessionRepository).save(session);
    }

    @Test
    @DisplayName("retireStuckWorkerNow passes caller attribution into action service reason")
    void retireStuckWorkerNowIncludesCallerInReason() {
        UUID projectId = UUID.randomUUID();
        UUID carrierTaskId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(geminiObserverActionService.retireStuckWorker(eq(project), eq(carrierTaskId.toString()), any()))
                .thenReturn("retired worker ok");

        String result = controller.retireStuckWorkerNow(projectId, carrierTaskId, "manual fix");

        assertThat(result).isEqualTo("retired worker ok");
        verify(geminiObserverActionService).retireStuckWorker(eq(project), eq(carrierTaskId.toString()),
                org.mockito.ArgumentMatchers.contains("[caller:"));
    }
}
