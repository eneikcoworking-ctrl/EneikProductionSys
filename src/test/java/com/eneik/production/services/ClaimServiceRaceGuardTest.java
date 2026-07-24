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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ClaimService's own isTerminal() pre-check only proves a task was non-terminal at the moment it was
 * read - not at the moment the later status write executes. TaskClaimServiceTest (real H2,
 * writeStatusUnlessTerminalRefusesOnceARowReachesTerminal) already proves the atomic guard itself works;
 * these tests prove ClaimService correctly treats a 0-row-affected result from that guard as "another
 * transaction won the race - do not proceed", by decoupling what findById returns (non-terminal, so the
 * pre-check passes) from what the atomic write reports (0, simulating a concurrent terminal transition
 * that landed between the read and the write) - something a real-H2 sequential test cannot exercise,
 * since a second real write before the method call would already be visible to the method's own pre-check.
 */
class ClaimServiceRaceGuardTest {

    private final ClaimRepository claimRepository = mock(ClaimRepository.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final JulesSessionRepository julesSessionRepository = mock(JulesSessionRepository.class);
    private final GateOrchestrator gateOrchestrator = mock(GateOrchestrator.class);
    private final ClaimService claimService = new ClaimService(
            claimRepository, taskRepository, accountRepository, julesSessionRepository, gateOrchestrator);

    private TaskEntity nonTerminalTask(UUID id) {
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

    @Test
    void releaseClaimToQueueSkipsReasonWriteWhenAtomicGuardLosesTheRace() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = nonTerminalTask(taskId);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(claimRepository.findByTaskIdAndReleasedAtIsNull(taskId)).thenReturn(Optional.of(activeClaim(task)));
        // Simulates a concurrent transaction terminal-izing the row between this read and this write.
        when(taskRepository.writeStatusUnlessTerminal(eq(taskId), eq(TaskStatus.queued))).thenReturn(0);

        assertDoesNotThrow(() -> claimService.releaseClaimToQueue(taskId, "should never be written"));

        // The reason/description follow-up write only happens inside the `updated != 0` branch.
        verify(taskRepository, never()).save(any());
    }

    @Test
    void failSkipsRequeueWhenAtomicGuardLosesTheRace() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = nonTerminalTask(taskId);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(claimRepository.findByTaskIdAndReleasedAtIsNull(taskId)).thenReturn(Optional.of(activeClaim(task)));
        when(taskRepository.writeStatusUnlessTerminal(eq(taskId), eq(TaskStatus.queued))).thenReturn(0);

        assertDoesNotThrow(() -> claimService.fail(taskId));

        verify(taskRepository, never()).save(any());
    }

    @Test
    void reopenWithAmendedBriefSkipsDescriptionRewriteWhenAtomicGuardLosesTheRace() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = nonTerminalTask(taskId);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(claimRepository.findByTaskIdAndReleasedAtIsNull(taskId)).thenReturn(Optional.of(activeClaim(task)));
        when(taskRepository.writeStatusUnlessTerminal(eq(taskId), eq(TaskStatus.queued))).thenReturn(0);

        assertDoesNotThrow(() -> claimService.reopenWithAmendedBrief(taskId, "amended brief", "reason"));

        verify(taskRepository, never()).save(any());
    }

    @Test
    void closeTaskAsFailedSkipsReasonWriteWhenAtomicGuardLosesTheRace() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = nonTerminalTask(taskId);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(claimRepository.findByTaskIdAndReleasedAtIsNull(taskId)).thenReturn(Optional.of(activeClaim(task)));
        when(taskRepository.writeStatusUnlessTerminal(eq(taskId), eq(TaskStatus.failed))).thenReturn(0);

        assertDoesNotThrow(() -> claimService.closeTaskAsFailed(taskId, "should never be written"));

        verify(taskRepository, never()).save(any());
    }

    @Test
    void closeTaskAsBlockedSkipsReasonWriteWhenAtomicGuardLosesTheRace() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = nonTerminalTask(taskId);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(claimRepository.findByTaskIdAndReleasedAtIsNull(taskId)).thenReturn(Optional.of(activeClaim(task)));
        when(taskRepository.writeStatusUnlessTerminal(eq(taskId), eq(TaskStatus.blocked))).thenReturn(0);

        assertDoesNotThrow(() -> claimService.closeTaskAsBlocked(taskId, "should never be written"));

        verify(taskRepository, never()).save(any());
    }
}
