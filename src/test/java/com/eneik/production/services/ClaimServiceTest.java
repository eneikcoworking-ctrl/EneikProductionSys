package com.eneik.production.services;

import com.eneik.production.kaizen.repository.DefectJournalRepository;
import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.models.persistence.ClaimEntity;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskDispatchVerdict;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.accounts.AccountHealthService;
import com.eneik.production.services.gate.GateOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Popperian falsification tests for Mechanism #11 (D013 Runtime Drift / D005 Buchak / D008 Popper):
 * - Monotonic refusal budget counting failed sovereign sessions (local-agy-*, deepseek-*)
 * - Exponential backoff (2m -> 5m -> 15m -> 30m, cap 60m) blocking immediate re-claim
 * - Quota/429 cooldown propagation to AccountHealthService
 */
class ClaimServiceTest {

    private ClaimRepository claimRepository;
    private TaskRepository taskRepository;
    private AccountRepository accountRepository;
    private JulesSessionRepository julesSessionRepository;
    private GateOrchestrator gateOrchestrator;
    private ClientDeliverableReadinessService readinessService;
    private PrReviewRepository prReviewRepository;
    private DefectJournalRepository defectJournalRepository;
    private AccountHealthService accountHealthService;
    private ClaimService claimService;

    @BeforeEach
    void setUp() {
        claimRepository = mock(ClaimRepository.class);
        taskRepository = mock(TaskRepository.class);
        accountRepository = mock(AccountRepository.class);
        julesSessionRepository = mock(JulesSessionRepository.class);
        gateOrchestrator = mock(GateOrchestrator.class);
        readinessService = mock(ClientDeliverableReadinessService.class);
        prReviewRepository = mock(PrReviewRepository.class);
        defectJournalRepository = mock(DefectJournalRepository.class);
        accountHealthService = mock(AccountHealthService.class);

        claimService = new ClaimService(
                claimRepository, taskRepository, accountRepository, julesSessionRepository,
                gateOrchestrator, readinessService, prReviewRepository, defectJournalRepository, "");
        claimService.setAccountHealthService(accountHealthService);

        when(claimRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private TaskEntity newTask(UUID taskId, TaskStatus status) {
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(status);
        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-02");
        task.setRole(role);
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        task.setProject(project);
        return task;
    }

    private AccountEntity newAccount(UUID accountId) {
        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setEnabled(true);
        account.setStatus(AccountStatus.idle);
        account.setCapabilities("*");
        return account;
    }

    private JulesSessionEntity sovereignSession(UUID taskId, String externalId, String status, String prUrl, String reason) {
        JulesSessionEntity s = new JulesSessionEntity();
        s.setId(UUID.randomUUID());
        s.setTaskId(taskId);
        s.setExternalSessionId(externalId);
        s.setStatus(status);
        s.setPrUrl(prUrl);
        s.setClosureReason(reason);
        s.setCreatedAt(Instant.now());
        return s;
    }

    @Test
    @DisplayName("D013/D008: Failed sovereign sessions (local-agy-*, deepseek-*) increment refusedSessionCreations and retire task at budget")
    void sovereignFailuresIncrementRefusalBudgetAndRetireTaskAtLimit() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = newTask(taskId, TaskStatus.claimed);

        JulesSessionEntity agyFailed = sovereignSession(taskId, "local-agy-" + UUID.randomUUID(), "closed_no_code", null, "Individual quota reached");
        JulesSessionEntity deepseekFailed = sovereignSession(taskId, "deepseek-" + UUID.randomUUID(), "failed", null, "HTTP 429 rate limit");
        JulesSessionEntity agyWithPr = sovereignSession(taskId, "local-agy-" + UUID.randomUUID(), "pr_opened", "https://github.com/org/repo/pull/99", null);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(julesSessionRepository.countByTaskIdAndExternalSessionIdIsNullAndStatus(taskId, "failed")).thenReturn(0L);
        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(agyFailed, deepseekFailed, agyWithPr));
        when(accountRepository.countLiveAccounts()).thenReturn(1L); // budget = 2 * 1 = 2
        when(claimRepository.findFirstByTaskIdAndReleasedAtIsNullOrderByClaimedAtDesc(taskId)).thenReturn(Optional.empty());
        when(taskRepository.writeStatusUnlessTerminal(taskId, TaskStatus.blocked)).thenReturn(1);

        // Must count both failed sovereign sessions (2), never freeze at 0, and exclude the session with a real PR
        assertEquals(2L, claimService.refusedSessionCreations(taskId));

        claimService.releaseClaimToQueue(taskId, "Individual quota reached");

        verify(taskRepository).writeStatusUnlessTerminal(taskId, TaskStatus.blocked);
        assertEquals(TaskStatus.blocked, task.getStatus());
        assertEquals(TaskDispatchVerdict.UNTESTED_WITHIN_CAPACITY, task.getDispatchVerdict());
    }

    @Test
    @DisplayName("D013/D005: Exponential backoff (2m->5m->15m->30m, cap 60m) blocks re-claim inside cooldown window")
    void releaseClaimToQueueAppliesExponentialBackoffAndBlocksReclaimInsideWindow() {
        assertEquals(Duration.ofMinutes(2), ClaimService.computeExponentialBackoff(1));
        assertEquals(Duration.ofMinutes(5), ClaimService.computeExponentialBackoff(2));
        assertEquals(Duration.ofMinutes(15), ClaimService.computeExponentialBackoff(3));
        assertEquals(Duration.ofMinutes(30), ClaimService.computeExponentialBackoff(4));
        assertEquals(Duration.ofMinutes(60), ClaimService.computeExponentialBackoff(5));
        assertEquals(Duration.ofMinutes(60), ClaimService.computeExponentialBackoff(20));

        UUID taskId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        TaskEntity task = newTask(taskId, TaskStatus.claimed);
        AccountEntity account = newAccount(accountId);

        JulesSessionEntity agyFailed1 = sovereignSession(taskId, "local-agy-" + UUID.randomUUID(), "failed", null, "empty stdout");
        JulesSessionEntity agyFailed2 = sovereignSession(taskId, "local-agy-" + UUID.randomUUID(), "closed_no_code", null, "empty stdout");

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.countLiveAccounts()).thenReturn(5L); // budget = 10
        when(julesSessionRepository.countByTaskIdAndExternalSessionIdIsNullAndStatus(taskId, "failed")).thenReturn(0L);
        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(agyFailed1, agyFailed2));
        when(claimRepository.findFirstByTaskIdAndReleasedAtIsNullOrderByClaimedAtDesc(taskId)).thenReturn(Optional.empty());
        when(taskRepository.writeStatusUnlessTerminal(taskId, TaskStatus.queued)).thenReturn(1);

        claimService.releaseClaimToQueue(taskId, "empty stdout");

        // Attempt 2 => 5-minute backoff window persisted in memory and task payload
        assertTrue(claimService.isTaskInDispatchBackoff(task, Instant.now().plusSeconds(30)));
        assertFalse(claimService.isTaskInDispatchBackoff(task, Instant.now().plus(Duration.ofMinutes(6))));
        assertTrue(task.getPayload() != null && task.getPayload().has(ClaimService.PAYLOAD_DISPATCH_BACKOFF_UNTIL));

        // Re-claim inside backoff window must be blocked on both claimSpecificTask and claim()
        task.setStatus(TaskStatus.queued);
        when(taskRepository.lockTaskByIdForUpdate(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.lockNextQueuedTask(List.of("BARCAN-TAG-02"))).thenReturn(Optional.of(task));

        assertThrows(IllegalStateException.class, () -> claimService.claimSpecificTask(taskId, accountId));
        assertNull(claimService.claim(accountId, List.of("BARCAN-TAG-02")));
    }

    @Test
    @DisplayName("D005/D013: Quota failure (Individual quota reached / HTTP 429) triggers AccountHealthService DAILY_LIMIT cooldown")
    void quotaFailureReportsDailyLimitToAccountHealthService() {
        UUID taskId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        TaskEntity task = newTask(taskId, TaskStatus.claimed);
        AccountEntity account = newAccount(accountId);

        ClaimEntity activeClaim = new ClaimEntity();
        activeClaim.setTask(task);
        activeClaim.setAccount(account);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(claimRepository.findFirstByTaskIdAndReleasedAtIsNullOrderByClaimedAtDesc(taskId))
                .thenReturn(Optional.of(activeClaim));
        when(accountRepository.countLiveAccounts()).thenReturn(5L);
        when(julesSessionRepository.countByTaskIdAndExternalSessionIdIsNullAndStatus(taskId, "failed")).thenReturn(0L);
        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(
                sovereignSession(taskId, "local-agy-" + UUID.randomUUID(), "failed", null, "Individual quota reached (exit 3)")
        ));
        when(taskRepository.writeStatusUnlessTerminal(taskId, TaskStatus.queued)).thenReturn(1);

        claimService.releaseClaimToQueue(taskId, "Individual quota reached (exit 3)");

        verify(accountHealthService).reportDispatchOutcome(
                eq(accountId),
                eq(task.getProject().getId()),
                eq(taskId),
                eq(AccountHealthService.DispatchOutcome.DAILY_LIMIT),
                eq("Individual quota reached (exit 3)"),
                eq("BARCAN-TAG-02"));
    }
}
