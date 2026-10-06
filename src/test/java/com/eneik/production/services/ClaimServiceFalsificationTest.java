package com.eneik.production.services;

import com.eneik.production.dto.ClaimDto;
import com.eneik.production.kaizen.model.DefectJournalEntity;
import com.eneik.production.kaizen.repository.DefectJournalRepository;
import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.models.persistence.ClaimEntity;
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
import com.eneik.production.services.ClaimService.ReviewAdmissionDecision;
import com.eneik.production.services.gate.GateOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Stage 4 Popperian falsification harness for ClaimService.
 *
 * Grounded in:
 * 1. UESLI_HOHFELD_03_RIGHTS_DUTIES_MATRIX [D006, Hohfeld]: jural matrix of claim rights and duties;
 *    claims must have explicit lease expiration; disabled/decommissioned accounts are prohibited from claiming;
 *    concurrent double-claim is rejected via atomic lock preemption.
 * 2. NUEL_BELNAP_03_TRUTH_STATUS_TABLE [D012, Belnap]: three-valued review admission lattice (ADMIT, DEFER, REJECT);
 *    active session reliably defers review rather than falsely rejecting before the agent finishes.
 * 3. MARGARET_GILBERT_04_INSTITUTIONAL_FACT_REGISTER [D007, Gilbert]: institutional fact recording;
 *    dispatch budget exhaustion and capacity recovery transitions are saved to DefectJournalRepository
 *    with structured audit taxonomy.
 */
class ClaimServiceFalsificationTest {

    private ClaimRepository claimRepository;
    private TaskRepository taskRepository;
    private AccountRepository accountRepository;
    private JulesSessionRepository julesSessionRepository;
    private GateOrchestrator gateOrchestrator;
    private ClientDeliverableReadinessService readinessService;
    private PrReviewRepository prReviewRepository;
    private DefectJournalRepository defectJournalRepository;
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

        claimService = new ClaimService(
                claimRepository, taskRepository, accountRepository, julesSessionRepository,
                gateOrchestrator, readinessService, prReviewRepository, defectJournalRepository, "");

        when(claimRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private AccountEntity createEligibleAccount(UUID accountId, String capabilities) {
        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setEnabled(true);
        account.setStatus(AccountStatus.idle);
        account.setCapabilities(capabilities);
        return account;
    }

    private TaskEntity createQueuedTask(UUID taskId, String roleTag) {
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.queued);
        RoleEntity role = new RoleEntity();
        role.setTag(roleTag);
        task.setRole(role);
        return task;
    }

    // --- UESLI_HOHFELD_03_RIGHTS_DUTIES_MATRIX (D006) ---

    @Test
    @DisplayName("Hohfeld: Claim creation guarantees explicit lease expiration within one hour")
    void falsifyHohfeldRightsDuties_claimSetsLeaseExpiresAtOneHour() {
        UUID accountId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        AccountEntity account = createEligibleAccount(accountId, "BARCAN-TAG-01");
        TaskEntity task = createQueuedTask(taskId, "BARCAN-TAG-01");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(taskRepository.lockNextQueuedTask(List.of("BARCAN-TAG-01"))).thenReturn(Optional.of(task));

        Instant before = Instant.now();
        ClaimDto dto = claimService.claim(accountId, List.of("BARCAN-TAG-01"));
        Instant after = Instant.now();

        assertNotNull(dto);
        assertNotNull(dto.leaseExpiresAt(), "Hohfeld invariant violated: claim without lease expiration is impossible");
        assertTrue(dto.leaseExpiresAt().isAfter(before.plus(Duration.ofMinutes(59))),
                "Lease expiration must be at least ~1 hour from creation");
        assertTrue(dto.leaseExpiresAt().isBefore(after.plus(Duration.ofMinutes(61))),
                "Lease expiration must not exceed ~1 hour");

        ArgumentCaptor<ClaimEntity> captor = ArgumentCaptor.forClass(ClaimEntity.class);
        verify(claimRepository).save(captor.capture());
        assertNotNull(captor.getValue().getLeaseExpiresAt());
    }

    @Test
    @DisplayName("Hohfeld: Decommissioned or disabled account is strictly prohibited from claiming tasks")
    void falsifyHohfeldRightsDuties_decommissionedAccountProhibitsClaim() {
        UUID accountId = UUID.randomUUID();
        AccountEntity decommissionedAccount = createEligibleAccount(accountId, "*");
        decommissionedAccount.setStatus(AccountStatus.decommissioned);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(decommissionedAccount));

        assertThrows(IllegalStateException.class, () ->
                claimService.claim(accountId, List.of("BARCAN-TAG-01")));

        verify(taskRepository, never()).lockNextQueuedTask(any());
        verify(claimRepository, never()).save(any());

        UUID disabledAccountId = UUID.randomUUID();
        AccountEntity disabledAccount = createEligibleAccount(disabledAccountId, "*");
        disabledAccount.setEnabled(false);

        when(accountRepository.findById(disabledAccountId)).thenReturn(Optional.of(disabledAccount));

        assertThrows(IllegalStateException.class, () ->
                claimService.claim(disabledAccountId, List.of("BARCAN-TAG-01")));
    }

    @Test
    @DisplayName("Hohfeld: Concurrent claim attempt on already locked/claimed task throws IllegalStateException")
    void falsifyHohfeldRightsDuties_concurrentLockFailureProhibitsDoubleClaim() {
        UUID accountId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        AccountEntity account = createEligibleAccount(accountId, "*");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        // Task exists in repository, but lockTaskByIdForUpdate returns empty (already claimed or locked concurrently)
        when(taskRepository.lockTaskByIdForUpdate(taskId)).thenReturn(Optional.empty());
        when(taskRepository.existsById(taskId)).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                claimService.claimSpecificTask(taskId, accountId));

        assertTrue(ex.getMessage().contains("Task is not in queued status or is already locked"),
                "Double claim race must be explicitly preempted");
        verify(claimRepository, never()).save(any());
    }

    // --- NUEL_BELNAP_03_TRUTH_STATUS_TABLE (D012) ---

    @Test
    @DisplayName("Belnap: Review admission decision implements strict three-valued lattice (ADMIT, DEFER, REJECT)")
    void falsifyBelnapTruthTable_reviewAdmissionTrinaryLattice() {
        // 1. Role does not require code -> ADMIT unconditionally
        assertEquals(ReviewAdmissionDecision.ADMIT,
                ClaimService.evaluateReviewAdmission(false, false, false));

        // 2. Review artifact already exists -> ADMIT
        assertEquals(ReviewAdmissionDecision.ADMIT,
                ClaimService.evaluateReviewAdmission(true, true, false));
        assertEquals(ReviewAdmissionDecision.ADMIT,
                ClaimService.evaluateReviewAdmission(true, true, true));

        // 3. Requires code, no artifact, but session is live -> DEFER (not REJECT!)
        assertEquals(ReviewAdmissionDecision.DEFER,
                ClaimService.evaluateReviewAdmission(true, false, true));

        // 4. Requires code, no artifact, and no live session -> REJECT
        assertEquals(ReviewAdmissionDecision.REJECT,
                ClaimService.evaluateReviewAdmission(true, false, false));
    }

    // --- MARGARET_GILBERT_04_INSTITUTIONAL_FACT_REGISTER (D007) ---

    @Test
    @DisplayName("Gilbert: Dispatch budget exhaustion creates institutional audit in defect journal")
    void falsifyGilbertInstitutionalFact_exhaustionPersistsInstitutionalAuditWithComposition() {
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        TaskEntity task = createQueuedTask(taskId, "BARCAN-TAG-05");
        task.setStatus(TaskStatus.claimed);
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        task.setProject(project);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.writeStatusUnlessTerminal(taskId, TaskStatus.blocked)).thenReturn(1);
        when(claimRepository.findFirstByTaskIdAndReleasedAtIsNullOrderByClaimedAtDesc(taskId))
                .thenReturn(Optional.empty());
        when(accountRepository.countLiveAccounts()).thenReturn(1L); // budget = 2
        when(julesSessionRepository.countByTaskIdAndExternalSessionIdIsNullAndStatus(taskId, "failed"))
                .thenReturn(2L); // refusals = 2 >= budget 2
        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(Collections.emptyList());

        claimService.releaseClaimToQueue(taskId, "jules_request_rejected: invalid_argument");

        assertEquals(TaskStatus.blocked, task.getStatus());
        assertEquals(TaskDispatchVerdict.DISPATCH_BUDGET_EXHAUSTED, task.getDispatchVerdict());

        ArgumentCaptor<DefectJournalEntity> captor = ArgumentCaptor.forClass(DefectJournalEntity.class);
        verify(defectJournalRepository, times(1)).save(captor.capture());

        DefectJournalEntity defect = captor.getValue();
        assertEquals(projectId, defect.getProjectId());
        assertEquals("INSTITUTIONAL_AUDIT", defect.getCategory());
        assertEquals("DISPATCH_BUDGET_EXHAUSTION_COMPOSITION", defect.getDefectType());
        assertTrue(defect.getDescription().contains("dispatch budget exhausted"));
    }

    @Test
    @DisplayName("Gilbert: Capacity recovery creates institutional audit when blocked tasks resume queue")
    void falsifyGilbertInstitutionalFact_capacityRecoveryPersistsInstitutionalAudit() {
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        TaskEntity task = createQueuedTask(taskId, "BARCAN-TAG-03");
        task.setStatus(TaskStatus.blocked);
        task.setDispatchVerdict(TaskDispatchVerdict.UNTESTED_WITHIN_CAPACITY);
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        task.setProject(project);

        when(accountRepository.countLiveAccounts()).thenReturn(2L);
        when(taskRepository.findByStatus(TaskStatus.blocked)).thenReturn(List.of(task));

        int resumed = claimService.requeueUntestedTasksOnRestoredCapacity();

        assertEquals(1, resumed);
        assertEquals(TaskStatus.queued, task.getStatus());
        assertEquals(TaskDispatchVerdict.NONE, task.getDispatchVerdict());

        ArgumentCaptor<DefectJournalEntity> captor = ArgumentCaptor.forClass(DefectJournalEntity.class);
        verify(defectJournalRepository, times(1)).save(captor.capture());

        DefectJournalEntity defect = captor.getValue();
        assertEquals("INSTITUTIONAL_AUDIT", defect.getCategory());
        assertEquals("TASK_CAPACITY_RECOVERY_RESUMED", defect.getDefectType());
        assertTrue(defect.getDescription().contains("returned to dispatch queue"));
    }
}
