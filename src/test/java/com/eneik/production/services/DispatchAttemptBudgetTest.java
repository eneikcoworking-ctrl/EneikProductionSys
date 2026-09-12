package com.eneik.production.services;

import com.eneik.production.kaizen.model.DefectJournalEntity;
import com.eneik.production.kaizen.repository.DefectJournalRepository;
import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.ClaimEntity;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.gate.GateOrchestrator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Action plan 4.1. The dispatch loop - queued, claim an account, Jules refuses to create the session,
 * requeue - had no variant function, and the live database showed what that costs: one carrier task with
 * 67 refused sessions across 7 accounts over four and a half days, one compiler task with 123, and 375 of
 * 775 recorded sessions being refusals that produced nothing.
 *
 * <p>Two tests, deliberately. The first fixes the budget itself; the second fixes that
 * releaseClaimToQueue - the one place a failed dispatch returns to the queue - actually consults it. A
 * correct predicate nobody calls is what {@code reviewFallbackTargetsAreTerminal} already was: reachable
 * only from paths that need a live session, on a task whose every attempt failed before one existed.
 */
class DispatchAttemptBudgetTest {

    private final ClaimRepository claimRepository = mock(ClaimRepository.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final JulesSessionRepository julesSessionRepository = mock(JulesSessionRepository.class);
    private final GateOrchestrator gateOrchestrator = mock(GateOrchestrator.class);
    private final ClientDeliverableReadinessService readinessService = mock(ClientDeliverableReadinessService.class);
    private final DefectJournalRepository defectJournalRepository = mock(DefectJournalRepository.class);

    private final ClaimService claimService = new ClaimService(
            claimRepository, taskRepository, accountRepository, julesSessionRepository, gateOrchestrator,
            readinessService, null, defectJournalRepository);

    private TaskEntity claimedTask(UUID id) {
        TaskEntity task = new TaskEntity();
        task.setId(id);
        task.setStatus(TaskStatus.claimed);
        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-02");
        task.setRole(role);
        return task;
    }

    private ClaimEntity activeClaim(TaskEntity task) {
        ClaimEntity claim = new ClaimEntity();
        claim.setTask(task);
        AccountEntity account = new AccountEntity();
        account.setId(UUID.randomUUID());
        claim.setAccount(account);
        return claim;
    }

    private void wire(UUID taskId, TaskEntity task, long liveAccounts, long refusals) {
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(claimRepository.findFirstByTaskIdAndReleasedAtIsNullOrderByClaimedAtDesc(taskId))
                .thenReturn(Optional.of(activeClaim(task)));
        when(accountRepository.countLiveAccounts()).thenReturn(liveAccounts);
        when(julesSessionRepository.countByTaskIdAndExternalSessionIdIsNullAndStatus(taskId, "failed"))
                .thenReturn(refusals);
    }

    /** A_max = 2 * live accounts: seven living accounts buy fourteen attempts, not an unbounded number. */
    @Test
    void budgetIsTwoAttemptsPerLivingAccount() {
        when(accountRepository.countLiveAccounts()).thenReturn(7L);
        assertEquals(14L, claimService.dispatchAttemptBudget());
    }

    /** An empty pool must still admit a first attempt rather than retiring every task on sight. */
    @Test
    void budgetNeverCollapsesToZero() {
        when(accountRepository.countLiveAccounts()).thenReturn(0L);
        assertTrue(claimService.dispatchAttemptBudget() > 0);
    }

    /** Under budget the loop is untouched: the task goes back to the queue exactly as before. */
    @Test
    void requeuesWhileTheBudgetHolds() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = claimedTask(taskId);
        wire(taskId, task, 7L, 13L);
        when(taskRepository.writeStatusUnlessTerminal(eq(taskId), eq(TaskStatus.queued))).thenReturn(1);

        claimService.releaseClaimToQueue(taskId, "jules_create_session_failed: HTTP 400");

        verify(taskRepository).writeStatusUnlessTerminal(taskId, TaskStatus.queued);
        verify(taskRepository, never()).writeStatusUnlessTerminal(taskId, TaskStatus.blocked);
    }

    /**
     * At the budget the task leaves the dispatchable set once, in data - it is not filtered at each
     * reader. Invariant 8: an element that cannot reach done must leave the set the decision is made over.
     */
    @Test
    void leavesTheQueueWhenTheBudgetIsSpent() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = claimedTask(taskId);
        wire(taskId, task, 7L, 14L);
        when(taskRepository.writeStatusUnlessTerminal(eq(taskId), eq(TaskStatus.blocked))).thenReturn(1);

        claimService.releaseClaimToQueue(taskId, "jules_create_session_failed: HTTP 400");

        verify(taskRepository, never()).writeStatusUnlessTerminal(taskId, TaskStatus.queued);
        verify(taskRepository).writeStatusUnlessTerminal(taskId, TaskStatus.blocked);
    }

    /**
     * `failed` would state a verdict nobody reached: 52 of the 67 refusals measured on the runaway carrier
     * named no side's precondition, so the budget records spent capacity, never fault.
     */
    @Test
    void retirementDoesNotClaimTheTaskFailed() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = claimedTask(taskId);
        wire(taskId, task, 3L, 99L);
        when(taskRepository.writeStatusUnlessTerminal(eq(taskId), eq(TaskStatus.blocked))).thenReturn(1);

        claimService.releaseClaimToQueue(taskId, "jules_precondition_unspecified");

        verify(taskRepository, never()).writeStatusUnlessTerminal(taskId, TaskStatus.failed);
    }



    /** Losing the atomic guard means another transaction decided the row; nothing further is written. */
    @Test
    void retirementRespectsTheAtomicGuard() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = claimedTask(taskId);
        wire(taskId, task, 7L, 30L);
        when(taskRepository.writeStatusUnlessTerminal(eq(taskId), eq(TaskStatus.blocked))).thenReturn(0);

        claimService.releaseClaimToQueue(taskId, "jules_create_session_failed");

        verify(taskRepository, never()).save(any(TaskEntity.class));
    }

    /**
     * Prescription 15 (Law 12 / D007 INSTITUTIONAL_FACT_REGISTER):
     * Task whose refusals are all external transitions to UNTESTED_WITHIN_CAPACITY in status `blocked`,
     * and never receives an absorbing terminal verdict (failed).
     */
    @Test
    void exhaustionWithOnlyExternalRefusals_markedUntestedWithinCapacityAndDoesNotReceiveTerminalFailed() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = claimedTask(taskId);
        wire(taskId, task, 7L, 14L);
        when(taskRepository.writeStatusUnlessTerminal(eq(taskId), eq(TaskStatus.blocked))).thenReturn(1);

        JulesSessionEntity s1 = new JulesSessionEntity();
        s1.setTaskId(taskId);
        s1.setStatus("failed");
        s1.setClosureReason("jules_concurrent_capacity_exhausted: account reached Jules concurrent session limit.");

        JulesSessionEntity s2 = new JulesSessionEntity();
        s2.setTaskId(taskId);
        s2.setStatus("failed");
        s2.setClosureReason("jules_daily_limit: account reached an explicit Jules daily/quota/rate limit.");

        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(s1, s2));

        claimService.releaseClaimToQueue(taskId, "jules_daily_limit: provider quota exhausted");

        // Status is set to blocked (non-terminal, renewable)
        verify(taskRepository).writeStatusUnlessTerminal(taskId, TaskStatus.blocked);
        verify(taskRepository, never()).writeStatusUnlessTerminal(taskId, TaskStatus.failed);

        // Jules dispatch status reflects composition and UNTESTED_WITHIN_CAPACITY
        assertTrue(task.getJulesDispatchStatus().startsWith("UNTESTED_WITHIN_CAPACITY"));
        assertTrue(task.getJulesDispatchStatus().contains("14/14 attempts"));
        assertTrue(task.getJulesDispatchStatus().contains("14 external, 0 non-external, 0 unattributed"));
        assertTrue(ClaimService.isUntestedWithinCapacity(task));
        assertEquals(com.eneik.production.models.persistence.TaskDispatchVerdict.UNTESTED_WITHIN_CAPACITY, task.getDispatchVerdict());

        // Audit registered
        verify(defectJournalRepository).save(any(DefectJournalEntity.class));
    }

    /**
     * Prescription 15: If any refusal was non-external (e.g. jules_request_rejected where the executor refused
     * the request itself), the task is marked DISPATCH_BUDGET_EXHAUSTED naming the composition, and is not
     * marked UNTESTED_WITHIN_CAPACITY.
     */
    @Test
    void exhaustionWithNonExternalRefusal_markedDispatchBudgetExhaustedWithoutUntestedTag() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = claimedTask(taskId);
        wire(taskId, task, 7L, 14L);
        when(taskRepository.writeStatusUnlessTerminal(eq(taskId), eq(TaskStatus.blocked))).thenReturn(1);

        JulesSessionEntity s1 = new JulesSessionEntity();
        s1.setTaskId(taskId);
        s1.setStatus("failed");
        s1.setClosureReason("jules_request_rejected: Jules refused the request itself, not the account. HTTP 400 INVALID_ARGUMENT");

        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(s1));

        claimService.releaseClaimToQueue(taskId, "jules_daily_limit: rate limit");

        // Status is blocked
        verify(taskRepository).writeStatusUnlessTerminal(taskId, TaskStatus.blocked);
        verify(taskRepository, never()).writeStatusUnlessTerminal(taskId, TaskStatus.failed);

        // Jules dispatch status reflects non-external refusal present
        assertTrue(task.getJulesDispatchStatus().startsWith("DISPATCH_BUDGET_EXHAUSTED"));
        assertTrue(task.getJulesDispatchStatus().contains("13 external, 1 non-external, 0 unattributed"));
        assertFalse(ClaimService.isUntestedWithinCapacity(task));
        assertEquals(com.eneik.production.models.persistence.TaskDispatchVerdict.DISPATCH_BUDGET_EXHAUSTED, task.getDispatchVerdict());
    }

    /**
     * Prescription 15 / NUEL_BELNAP_03_TRUTH_STATUS_TABLE (D012):
     * Refusals where cause is unnamed/unattributed (e.g. jules_precondition_unspecified, null/blank)
     * are NOT treated as proven external capacity. An exhaustion of purely unattributed refusals produces
     * DISPATCH_BUDGET_EXHAUSTED naming 0 external, 0 non-external, 14 unattributed, and is NOT marked
     * UNTESTED_WITHIN_CAPACITY.
     */
    /**
     * Prescription 15 & 30 / NUEL_BELNAP_03_TRUTH_STATUS_TABLE (D012) & INSTITUTIONAL_FACT_REGISTER (D007):
     * Refusals where cause is unnamed/unattributed (e.g. jules_precondition_unspecified, null/blank)
     * are NOT treated as proven external capacity. An exhaustion of purely unattributed refusals produces
     * UNATTRIBUTED_DISPATCH_REFUSAL naming 0 external, 0 non-external, 14 unattributed ("external system refuses without cause").
     * It is not marked UNTESTED_WITHIN_CAPACITY, but is marked resumable.
     */
    @Test
    void exhaustionWithUnattributedRefusals_isMarkedUnattributedDispatchRefusalAndResumable() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = claimedTask(taskId);
        wire(taskId, task, 7L, 14L);
        when(taskRepository.writeStatusUnlessTerminal(eq(taskId), eq(TaskStatus.blocked))).thenReturn(1);

        JulesSessionEntity s1 = new JulesSessionEntity();
        s1.setTaskId(taskId);
        s1.setStatus("failed");
        s1.setClosureReason("jules_precondition_unspecified: Jules cited an unspecified precondition");

        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(s1));

        claimService.releaseClaimToQueue(taskId, "jules_precondition_unspecified: unspecified failure");

        // Status is blocked
        verify(taskRepository).writeStatusUnlessTerminal(taskId, TaskStatus.blocked);
        verify(taskRepository, never()).writeStatusUnlessTerminal(taskId, TaskStatus.failed);

        // Jules dispatch status reflects pure unattributed refusals
        assertTrue(task.getJulesDispatchStatus().startsWith("UNATTRIBUTED_DISPATCH_REFUSAL"));
        assertTrue(task.getJulesDispatchStatus().contains("0 external, 0 non-external, 14 unattributed"));
        assertFalse(ClaimService.isUntestedWithinCapacity(task));
        assertTrue(ClaimService.isUnattributedDispatchRefusal(task));
        assertTrue(ClaimService.isResumableDispatchRefusal(task));
        assertEquals(com.eneik.production.models.persistence.TaskDispatchVerdict.UNATTRIBUTED_DISPATCH_REFUSAL, task.getDispatchVerdict());
    }

    /**
     * Prescription 30 (INSTITUTIONAL_FACT_REGISTER / D007):
     * Resolution path: tasks with UNATTRIBUTED_DISPATCH_REFUSAL are unblocked when capacity recovers,
     * returning to `queued` with a fresh budget and logging an institutional audit fact.
     */
    @Test
    void requeueUntestedTasksOnRestoredCapacity_resumesUnattributedDispatchRefusalTasks() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.blocked);
        task.setDispatchVerdict(com.eneik.production.models.persistence.TaskDispatchVerdict.UNATTRIBUTED_DISPATCH_REFUSAL);
        task.setJulesDispatchStatus("UNATTRIBUTED_DISPATCH_REFUSAL: dispatch budget exhausted (14/14 attempts)");

        when(accountRepository.countLiveAccounts()).thenReturn(7L);
        when(taskRepository.findByStatus(TaskStatus.blocked)).thenReturn(List.of(task));

        java.time.Instant now = java.time.Instant.now();
        int requeued = claimService.requeueUntestedTasksOnRestoredCapacity(now);

        assertEquals(1, requeued);
        assertEquals(TaskStatus.queued, task.getStatus());
        assertEquals(com.eneik.production.models.persistence.TaskDispatchVerdict.NONE, task.getDispatchVerdict());
        assertEquals(now, task.getLastBudgetResetAt());
        assertTrue(task.getJulesDispatchStatus().startsWith("REQUEUED_ON_CAPACITY_RECOVERY"));

        verify(taskRepository).save(task);

        org.mockito.ArgumentCaptor<DefectJournalEntity> captor = org.mockito.ArgumentCaptor.forClass(DefectJournalEntity.class);
        verify(defectJournalRepository).save(captor.capture());
        assertEquals("TASK_CAPACITY_RECOVERY_RESUMED", captor.getValue().getDefectType());
    }

    /**
     * Prescription 30: Tasks with DISPATCH_BUDGET_EXHAUSTED (where non-external rejections occurred)
     * are strictly terminal in blocked and must NOT be resumed by capacity recovery.
     */
    @Test
    void requeueUntestedTasksOnRestoredCapacity_doesNotResumeNonExternalRejections() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.blocked);
        task.setDispatchVerdict(com.eneik.production.models.persistence.TaskDispatchVerdict.DISPATCH_BUDGET_EXHAUSTED);
        task.setJulesDispatchStatus("DISPATCH_BUDGET_EXHAUSTED: dispatch budget exhausted (14/14 attempts); 13 external, 1 non-external");

        when(accountRepository.countLiveAccounts()).thenReturn(7L);
        when(taskRepository.findByStatus(TaskStatus.blocked)).thenReturn(List.of(task));

        int requeued = claimService.requeueUntestedTasksOnRestoredCapacity(java.time.Instant.now());

        assertEquals(0, requeued);
        assertEquals(TaskStatus.blocked, task.getStatus());
        assertEquals(com.eneik.production.models.persistence.TaskDispatchVerdict.DISPATCH_BUDGET_EXHAUSTED, task.getDispatchVerdict());
        verify(taskRepository, never()).save(task);
    }

    /**
     * Prescription 30 / INUS_FACTOR_CHECK (D007):
     * Consecutive identical unattributed refusals enforce strictly one attempt per backoff window.
     */
    @Test
    void consecutiveIdenticalUnattributedRefusals_throttlesToSingleAttemptPerBackoffWindow() {
        UUID taskId = UUID.randomUUID();
        java.time.Instant now = java.time.Instant.now();

        JulesSessionEntity s1 = new JulesSessionEntity();
        s1.setTaskId(taskId);
        s1.setStatus("failed");
        s1.setClosureReason("jules_precondition_unspecified: Jules cited an unspecified precondition");
        s1.setCreatedAt(now.minus(java.time.Duration.ofMinutes(5)));

        JulesSessionEntity s2 = new JulesSessionEntity();
        s2.setTaskId(taskId);
        s2.setStatus("failed");
        s2.setClosureReason("jules_precondition_unspecified: Jules cited an unspecified precondition");
        s2.setCreatedAt(now.minus(java.time.Duration.ofMinutes(2)));

        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(s1, s2));

        // 2 consecutive identical unattributed refusals -> streak is 2
        assertEquals(2, claimService.consecutiveIdenticalUnattributedRefusals(taskId));

        // Active throttling within 15-minute window from s2 (2 minutes ago)
        assertTrue(claimService.isIdenticalUnattributedRefusalThrottled(taskId, now));

        // After the 15-minute backoff window has passed, throttling lifts allowing one probe
        java.time.Instant future = now.plus(java.time.Duration.ofMinutes(14)); // 16 minutes after s2
        assertFalse(claimService.isIdenticalUnattributedRefusalThrottled(taskId, future));
    }

    /**
     * Prescription 30 / INUS_FACTOR_CHECK (D007):
     * Non-identical or external refusals do NOT trigger identical-refusal throttling;
     * 14 attempts remain available for diverse causes.
     */
    @Test
    void distinctOrExternalRefusals_doNotTriggerIdenticalRefusalThrottling() {
        UUID taskId = UUID.randomUUID();
        java.time.Instant now = java.time.Instant.now();

        JulesSessionEntity s1 = new JulesSessionEntity();
        s1.setTaskId(taskId);
        s1.setStatus("failed");
        s1.setClosureReason("jules_daily_limit: rate limit");
        s1.setCreatedAt(now.minus(java.time.Duration.ofMinutes(5)));

        JulesSessionEntity s2 = new JulesSessionEntity();
        s2.setTaskId(taskId);
        s2.setStatus("failed");
        s2.setClosureReason("jules_precondition_unspecified: condition");
        s2.setCreatedAt(now.minus(java.time.Duration.ofMinutes(2)));

        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(s1, s2));

        // Only 1 trailing unattributed refusal -> streak is 1 (< threshold of 2)
        assertEquals(1, claimService.consecutiveIdenticalUnattributedRefusals(taskId));
        assertFalse(claimService.isIdenticalUnattributedRefusalThrottled(taskId, now));
    }

    /**
     * Prescription 30 / INSTITUTIONAL_FACT_REGISTER (D007):
     * Carrier deaths (dispatch budget exhaustions) are counted and reported for telemetry.
     */
    @Test
    void carrierDeaths_areCountedAndAuditedInDefectJournal() {
        UUID projectId = UUID.randomUUID();
        UUID carrierTaskId = UUID.randomUUID();
        java.time.Instant now = java.time.Instant.now();

        DefectJournalEntity e1 = new DefectJournalEntity(
                projectId, carrierTaskId, null, "INFO", "INSTITUTIONAL_AUDIT", "carrier",
                "DISPATCH_BUDGET_EXHAUSTION_COMPOSITION", "Task " + carrierTaskId + " (carrier=true) dispatch budget exhausted", 0.0);

        DefectJournalEntity e2 = new DefectJournalEntity(
                projectId, UUID.randomUUID(), null, "INFO", "INSTITUTIONAL_AUDIT", "task",
                "DISPATCH_BUDGET_EXHAUSTION_COMPOSITION", "Task non-carrier (carrier=false) dispatch budget exhausted", 0.0);

        when(defectJournalRepository.findByProjectIdAndCreatedAtAfter(eq(projectId), any(java.time.Instant.class)))
                .thenReturn(List.of(e1, e2));

        // Only the carrier entry is counted as a carrier death
        assertEquals(1L, claimService.countCarrierDeathsPast24Hours(projectId));
    }

    /**
     * Prescription 15 Rule 1: "счёт не трогать — ёмкость действительно потрачена, запрос был сделан".
     * refusedSessionCreations remains faithful and is not dampened or reset.
     */
    @Test
    void attemptCountIsFaithfulAndNotDampened() {
        UUID taskId = UUID.randomUUID();
        when(julesSessionRepository.countByTaskIdAndExternalSessionIdIsNullAndStatus(taskId, "failed")).thenReturn(14L);
        assertEquals(14L, claimService.refusedSessionCreations(taskId));
    }

    /**
     * Prescription 15 (NUEL_BELNAP_03 / D012):
     * Resolution path: when capacity recovers, tasks in UNTESTED_WITHIN_CAPACITY are unblocked,
     * returned to `queued`, receive a fresh budget from reset timestamp, and log an audit fact.
     */
    @Test
    void requeueUntestedTasksOnRestoredCapacity_returnsTasksToQueuedWithFreshBudgetAndAuditFact() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.blocked);
        task.setDispatchVerdict(com.eneik.production.models.persistence.TaskDispatchVerdict.UNTESTED_WITHIN_CAPACITY);
        task.setJulesDispatchStatus("UNTESTED_WITHIN_CAPACITY: dispatch budget exhausted (14/14 attempts)");

        when(accountRepository.countLiveAccounts()).thenReturn(7L);
        when(taskRepository.findByStatus(TaskStatus.blocked)).thenReturn(List.of(task));

        java.time.Instant now = java.time.Instant.now();
        int requeued = claimService.requeueUntestedTasksOnRestoredCapacity(now);

        assertEquals(1, requeued);
        assertEquals(TaskStatus.queued, task.getStatus());
        assertEquals(com.eneik.production.models.persistence.TaskDispatchVerdict.NONE, task.getDispatchVerdict());
        assertEquals(now, task.getLastBudgetResetAt());
        assertTrue(task.getJulesDispatchStatus().startsWith("REQUEUED_ON_CAPACITY_RECOVERY"));

        verify(taskRepository).save(task);

        // Institutional audit fact recorded in DefectJournal
        org.mockito.ArgumentCaptor<DefectJournalEntity> captor = org.mockito.ArgumentCaptor.forClass(DefectJournalEntity.class);
        verify(defectJournalRepository).save(captor.capture());
        assertEquals("TASK_CAPACITY_RECOVERY_RESUMED", captor.getValue().getDefectType());

        // Subsequent budget query consults sessions created AFTER reset timestamp
        when(julesSessionRepository.countByTaskIdAndExternalSessionIdIsNullAndStatusAndCreatedAtAfter(taskId, "failed", now))
                .thenReturn(0L);
        assertEquals(0L, claimService.refusedSessionCreations(taskId));
    }

    /**
     * Prescription 15: Typed refusal category separates external capacity limits from request rejections.
     */
    @Test
    void typedDispatchRefusalCategory_separatesExternalCapacityFromRequestRejections() {
        assertTrue(com.eneik.production.models.persistence.DispatchRefusalCategory
                .fromClosureReason("jules_concurrent_capacity_exhausted: account limit").isExternal());
        assertTrue(com.eneik.production.models.persistence.DispatchRefusalCategory
                .fromClosureReason("jules_daily_limit: rate limit").isExternal());
        assertFalse(com.eneik.production.models.persistence.DispatchRefusalCategory
                .fromClosureReason("jules_request_rejected: malformed").isExternal());
        assertTrue(com.eneik.production.models.persistence.DispatchRefusalCategory
                .fromClosureReason("jules_request_rejected: malformed").isNonExternal());
        assertTrue(com.eneik.production.models.persistence.DispatchRefusalCategory
                .fromClosureReason("jules_precondition_unspecified").isUnattributed());
        assertTrue(com.eneik.production.models.persistence.DispatchRefusalCategory
                .fromClosureReason(null).isUnattributed());
        assertTrue(com.eneik.production.models.persistence.DispatchRefusalCategory
                .fromClosureReason("").isUnattributed());
    }
}
