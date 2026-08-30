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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Model rule 8.20 with 8.15. Refusing to record `done` for a code-owing task with nothing on main is right
 * and stays - a row must not be the only evidence that work exists. Where such a task was then LEFT was the
 * defect: `review` means an artifact is under review, and this one has none.
 *
 * <p>Measured on the live circuit 2026-08-30 22:27: "3 of 3 review task(s) carry no review artifact of their
 * own". Those tasks could not progress, the review dispatcher kept sending them to a reviewer with nothing
 * to read, and their claims were never released - three of the seven live accounts held for work that had
 * already ended.
 */
class UndeliveredReviewTaskExitTest {

    private final ClaimRepository claimRepository = mock(ClaimRepository.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final JulesSessionRepository julesSessionRepository = mock(JulesSessionRepository.class);
    private final GateOrchestrator gateOrchestrator = mock(GateOrchestrator.class);
    private final ClientDeliverableReadinessService readinessService = mock(ClientDeliverableReadinessService.class);
    private final ClaimService claimService = new ClaimService(
            claimRepository, taskRepository, accountRepository, julesSessionRepository, gateOrchestrator,
            readinessService);

    private TaskEntity taskInReview() {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setStatus(TaskStatus.review);
        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-02");
        task.setRole(role);
        return task;
    }

    private ClaimEntity claimFor(TaskEntity task) {
        ClaimEntity claim = new ClaimEntity();
        claim.setTask(task);
        AccountEntity account = new AccountEntity();
        account.setId(UUID.randomUUID());
        claim.setAccount(account);
        when(claimRepository.findFirstByTaskIdAndReleasedAtIsNullOrderByClaimedAtDesc(task.getId()))
                .thenReturn(Optional.of(claim));
        return claim;
    }

    @Test
    void aCodeOwingTaskWithNothingOnMainLeavesReviewForAStateThatHasExits() {
        TaskEntity task = taskInReview();
        ClaimEntity claim = claimFor(task);
        when(readinessService.requiresCodeForDelivery(task)).thenReturn(true);
        when(readinessService.hasRequiredMergeEvidence(task)).thenReturn(false);

        claimService.complete(task.getId());

        assertEquals(TaskStatus.failed, task.getStatus(),
                "review has no exit for a task with no artifact; failed has a bounded resume and a repair path");
        assertNotNull(claim.getReleasedAt(), "the account must not stay held for work that already ended");
    }

    @Test
    void aTaskWhoseCodeIsOnMainIsStillMarkedDone() {
        // The other half: this must not turn delivery into failure.
        TaskEntity task = taskInReview();
        ClaimEntity claim = claimFor(task);
        when(readinessService.requiresCodeForDelivery(task)).thenReturn(true);
        when(readinessService.hasRequiredMergeEvidence(task)).thenReturn(true);

        claimService.complete(task.getId());

        assertEquals(TaskStatus.done, task.getStatus());
        assertNotNull(claim.getReleasedAt());
    }

    @Test
    void aSpecRoleIsUntouchedBecauseItOwesNoCode() {
        // Roles that deliver documents must never be failed for having no code on main.
        TaskEntity task = taskInReview();
        ClaimEntity claim = claimFor(task);
        when(readinessService.requiresCodeForDelivery(task)).thenReturn(false);

        claimService.complete(task.getId());

        assertEquals(TaskStatus.done, task.getStatus());
        assertNotNull(claim.getReleasedAt());
    }
}
