package com.eneik.production.services;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.ClaimEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.gate.GateOrchestrator;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private final ClaimService claimService = new ClaimService(
            claimRepository, taskRepository, accountRepository, julesSessionRepository, gateOrchestrator,
            readinessService);

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
}
