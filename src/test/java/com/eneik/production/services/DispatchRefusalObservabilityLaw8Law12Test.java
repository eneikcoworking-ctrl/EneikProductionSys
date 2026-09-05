package com.eneik.production.services;

import com.eneik.production.kaizen.model.DefectJournalEntity;
import com.eneik.production.kaizen.repository.DefectJournalRepository;
import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.AccountRoleSuccessStatsRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.accounts.AccountHealthService;
import com.eneik.production.services.gate.GateOrchestrator;
import com.eneik.production.services.lever.LeverPromotionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Laws 8 & 12: Dispatch Refusal Observability and Variant Function Screen:
 *
 * Law 8: Variant function progress A(tau) / A_max is observable on every iteration and does not fail silently.
 * Law 12: Grounds of denial name what it is about (the task and account) rather than an unassociated refusal.
 */
class DispatchRefusalObservabilityLaw8Law12Test {

    private AccountRepository accountRepository;
    private DefectJournalRepository defectJournalRepository;
    private AccountRoleSuccessStatsRepository statsRepository;
    private LeverPromotionService leverPromotionService;
    private JulesSessionRepository julesSessionRepository;
    private TaskRepository taskRepository;
    private ClaimRepository claimRepository;
    private GateOrchestrator gateOrchestrator;
    private ClientDeliverableReadinessService readinessService;

    private AccountHealthService accountHealthService;
    private ClaimService claimService;

    private final UUID accountId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID taskId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        defectJournalRepository = mock(DefectJournalRepository.class);
        statsRepository = mock(AccountRoleSuccessStatsRepository.class);
        leverPromotionService = mock(LeverPromotionService.class);
        julesSessionRepository = mock(JulesSessionRepository.class);
        taskRepository = mock(TaskRepository.class);
        claimRepository = mock(ClaimRepository.class);
        gateOrchestrator = mock(GateOrchestrator.class);
        readinessService = mock(ClientDeliverableReadinessService.class);

        accountHealthService = new AccountHealthService(
                accountRepository,
                defectJournalRepository,
                statsRepository,
                leverPromotionService,
                julesSessionRepository
        );

        claimService = new ClaimService(
                claimRepository,
                taskRepository,
                accountRepository,
                julesSessionRepository,
                gateOrchestrator,
                readinessService
        );
    }

    private AccountEntity createAccount(String name, int concurrentCeiling, int openSessions) {
        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setName(name);
        account.setStatus(AccountStatus.idle);
        account.setEnabled(true);
        account.setEstimatedConcurrentCapacity(concurrentCeiling);
        account.setMaxConcurrentSessions(3);
        account.setSessionsDispatchedToday(5);
        account.setConsecutiveApiBlockCount(0);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.countOpenSessions(accountId)).thenReturn(openSessions);
        return account;
    }

    @Test
    @DisplayName("Law 12: reportDispatchOutcome preserves taskId on DefectJournalEntity for precondition block")
    void reportDispatchOutcomePreservesTaskIdOnDefectJournal() {
        createAccount("test-account", 3, 0);

        accountHealthService.reportDispatchOutcome(
                accountId,
                projectId,
                taskId,
                AccountHealthService.DispatchOutcome.PRECONDITION_BLOCKED,
                "API precondition failed",
                "BARCAN-TAG-02"
        );

        ArgumentCaptor<DefectJournalEntity> captor = ArgumentCaptor.forClass(DefectJournalEntity.class);
        verify(defectJournalRepository).save(captor.capture());
        DefectJournalEntity defect = captor.getValue();

        assertEquals(projectId, defect.getProjectId());
        assertTrue(defect.getDescription().contains("[task=" + taskId + "]"), "Defect description must record the task being dispatched (Law 12)");
        assertEquals("test-account", defect.getSourceComponent());
        assertEquals("API_PRECONDITION_BLOCKED", defect.getDefectType());
    }

    @Test
    @DisplayName("Law 12: Backward compatibility overload preserves null taskId without throwing")
    void backwardCompatibilityOverloadPreservesNullTaskId() {
        createAccount("legacy-account", 3, 0);

        accountHealthService.reportDispatchOutcome(
                accountId,
                projectId,
                AccountHealthService.DispatchOutcome.PRECONDITION_BLOCKED,
                "API precondition failed"
        );

        ArgumentCaptor<DefectJournalEntity> captor = ArgumentCaptor.forClass(DefectJournalEntity.class);
        verify(defectJournalRepository).save(captor.capture());
        DefectJournalEntity defect = captor.getValue();

        assertEquals(projectId, defect.getProjectId());
        assertFalse(defect.getDescription().contains("[task="), "Legacy caller leaves taskId unmentioned in description");
        assertEquals("legacy-account", defect.getSourceComponent());
    }

    @Test
    @DisplayName("Law 8: ClaimService retires task to blocked when refusal count reaches dispatchAttemptBudget")
    void claimServiceRetiresTaskWhenRefusalsReachBudget() {
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.claimed);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        // 7 live accounts -> budget is 14
        when(accountRepository.countLiveAccounts()).thenReturn(7L);
        when(julesSessionRepository.countByTaskIdAndExternalSessionIdIsNullAndStatus(taskId, "failed")).thenReturn(14L);
        when(taskRepository.writeStatusUnlessTerminal(taskId, TaskStatus.blocked)).thenReturn(1);

        claimService.releaseClaimToQueue(taskId, "Precondition check failed");

        verify(taskRepository).writeStatusUnlessTerminal(taskId, TaskStatus.blocked);
        verify(taskRepository, never()).writeStatusUnlessTerminal(taskId, TaskStatus.queued);
    }

    @Test
    @DisplayName("Law 8: ClaimService requeues task when refusal count is below dispatchAttemptBudget")
    void claimServiceRequeuesTaskWhenRefusalsBelowBudget() {
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.claimed);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        // 7 live accounts -> budget is 14. Refusals at 5 < 14
        when(accountRepository.countLiveAccounts()).thenReturn(7L);
        when(julesSessionRepository.countByTaskIdAndExternalSessionIdIsNullAndStatus(taskId, "failed")).thenReturn(5L);
        when(taskRepository.writeStatusUnlessTerminal(taskId, TaskStatus.queued)).thenReturn(1);

        claimService.releaseClaimToQueue(taskId, "Precondition check failed");

        verify(taskRepository).writeStatusUnlessTerminal(taskId, TaskStatus.queued);
        verify(taskRepository, never()).writeStatusUnlessTerminal(taskId, TaskStatus.blocked);
    }
}
