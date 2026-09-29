package com.eneik.production.services;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Falsification test suite for {@link LeaseWatchdogService} and periodic expired lease reaping.
 * <p>
 * Philosophical anchors from RAG corpus:
 * <ul>
 *   <li>{@code UESLI_HOHFELD_02_DEFEASIBLE_EXCEPTION_LEDGER} [D012]: Expired lease reaping vs defeasible lease extension for active sessions.</li>
 *   <li>{@code UESLI_HOHFELD_03_RIGHTS_DUTIES_MATRIX} [D006]: Worker rights boundary, duty to revoke expired claims and manage account status.</li>
 *   <li>{@code UESLI_HOHFELD_04_PRINCIPLED_INTEGRITY} [D012]: Immunity of terminal tasks and safe non-interference on concurrent CAS loss.</li>
 * </ul>
 */
class LeaseWatchdogServiceFalsificationTest {

    private ClaimRepository claimRepository;
    private TaskRepository taskRepository;
    private AccountRepository accountRepository;
    private JulesSessionRepository julesSessionRepository;
    private ClaimService claimService;
    private LeaseWatchdogService watchdogService;

    @BeforeEach
    void setUp() {
        claimRepository = mock(ClaimRepository.class);
        taskRepository = mock(TaskRepository.class);
        accountRepository = mock(AccountRepository.class);
        julesSessionRepository = mock(JulesSessionRepository.class);

        claimService = mock(ClaimService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(claimService, "claimRepository", claimRepository);
        ReflectionTestUtils.setField(claimService, "taskRepository", taskRepository);
        ReflectionTestUtils.setField(claimService, "accountRepository", accountRepository);
        ReflectionTestUtils.setField(claimService, "julesSessionRepository", julesSessionRepository);

        watchdogService = new LeaseWatchdogService(claimService);
    }

    @Test
    @DisplayName("HOHFELD_02: Expired lease without active session is reaped and requeued via CAS")
    void expiredLease_withNoActiveSession_isReapedAndRequeuedViaCAS() {
        UUID taskId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.claimed);

        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setStatus(AccountStatus.busy);

        ClaimEntity claim = new ClaimEntity();
        claim.setId(UUID.randomUUID());
        claim.setTask(task);
        claim.setAccount(account);
        claim.setLeaseExpiresAt(Instant.now().minus(5, ChronoUnit.MINUTES));

        when(claimRepository.findByReleasedAtIsNullAndLeaseExpiresAtBefore(any(Instant.class)))
                .thenReturn(List.of(claim));
        when(claimRepository.findByReleasedAtIsNull()).thenReturn(Collections.emptyList());
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(claimRepository.existsByAccountIdAndReleasedAtIsNull(accountId)).thenReturn(false);
        when(taskRepository.compareAndSetStatus(taskId, TaskStatus.claimed, TaskStatus.queued))
                .thenReturn(1);
        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(Collections.emptyList());

        watchdogService.reapExpiredLeases();

        // 1. Claim released with expired status
        assertThat(claim.getResultStatus()).isEqualTo(ClaimResultStatus.expired);
        assertThat(claim.getReleasedAt()).isNotNull();
        verify(claimRepository).save(claim);

        // 2. Task requeued via CAS
        verify(taskRepository).compareAndSetStatus(taskId, TaskStatus.claimed, TaskStatus.queued);

        // 3. Account transitioned to offline because no active claims remain
        assertThat(account.getStatus()).isEqualTo(AccountStatus.offline);
        verify(accountRepository).save(account);
    }

    @Test
    @DisplayName("HOHFELD_02: Active leases within valid lease window are never swept")
    void activeLease_withinLeaseWindow_isNotSwept() {
        when(claimRepository.findByReleasedAtIsNullAndLeaseExpiresAtBefore(any(Instant.class)))
                .thenReturn(Collections.emptyList());
        when(claimRepository.findByReleasedAtIsNull()).thenReturn(Collections.emptyList());

        watchdogService.reapExpiredLeases();

        verify(taskRepository, never()).compareAndSetStatus(any(), any(), any());
        verify(claimRepository, never()).save(any());
    }

    @Test
    @DisplayName("HOHFELD_02: Expired lease with active Jules session is defeasibly extended rather than reaped")
    void expiredLease_withActiveJulesSession_isDefeasiblyExtended() {
        UUID taskId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.claimed);

        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setStatus(AccountStatus.busy);

        Instant expiredLeaseTime = Instant.now().minus(2, ChronoUnit.MINUTES);
        ClaimEntity claim = new ClaimEntity();
        claim.setId(UUID.randomUUID());
        claim.setTask(task);
        claim.setAccount(account);
        claim.setLeaseExpiresAt(expiredLeaseTime);

        JulesSessionEntity activeSession = new JulesSessionEntity();
        activeSession.setId(UUID.randomUUID());
        activeSession.setTaskId(taskId);
        activeSession.setExternalSessionId("ext-jules-session-123");
        activeSession.setStatus("running");

        when(claimRepository.findByReleasedAtIsNullAndLeaseExpiresAtBefore(any(Instant.class)))
                .thenReturn(List.of(claim));
        when(claimRepository.findByReleasedAtIsNull()).thenReturn(Collections.emptyList());
        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(activeSession));

        watchdogService.reapExpiredLeases();

        // Lease must be defeasibly extended, NOT released
        assertThat(claim.getReleasedAt()).isNull();
        assertThat(claim.getLeaseExpiresAt()).isAfter(Instant.now());
        verify(claimRepository).save(claim);

        // Task must NOT be requeued
        verify(taskRepository, never()).compareAndSetStatus(any(), any(), any());
    }

    @Test
    @DisplayName("HOHFELD_03: Reaped claim leaves account busy if another concurrent claim remains active")
    void reapedClaim_leavesAccountBusyWhenConcurrentClaimExists() {
        UUID taskId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.claimed);

        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setStatus(AccountStatus.busy);

        ClaimEntity claim = new ClaimEntity();
        claim.setId(UUID.randomUUID());
        claim.setTask(task);
        claim.setAccount(account);
        claim.setLeaseExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));

        when(claimRepository.findByReleasedAtIsNullAndLeaseExpiresAtBefore(any(Instant.class)))
                .thenReturn(List.of(claim));
        when(claimRepository.findByReleasedAtIsNull()).thenReturn(Collections.emptyList());
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        // Another active claim exists on this account!
        when(claimRepository.existsByAccountIdAndReleasedAtIsNull(accountId)).thenReturn(true);
        when(taskRepository.compareAndSetStatus(taskId, TaskStatus.claimed, TaskStatus.queued)).thenReturn(1);
        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(Collections.emptyList());

        watchdogService.reapExpiredLeases();

        // Account status remains busy
        assertThat(account.getStatus()).isEqualTo(AccountStatus.busy);
        verify(accountRepository).save(account);
    }

    @Test
    @DisplayName("HOHFELD_04: Terminal task is closed without being resurrected into the queue")
    void terminalTask_isClosedRatherThanRequeued() {
        UUID taskId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        TaskEntity terminalTask = new TaskEntity();
        terminalTask.setId(taskId);
        terminalTask.setStatus(TaskStatus.done); // Already completed!

        AccountEntity account = new AccountEntity();
        account.setId(accountId);

        ClaimEntity claim = new ClaimEntity();
        claim.setId(UUID.randomUUID());
        claim.setTask(terminalTask);
        claim.setAccount(account);
        claim.setLeaseExpiresAt(Instant.now().minus(10, ChronoUnit.MINUTES));

        when(claimRepository.findByReleasedAtIsNullAndLeaseExpiresAtBefore(any(Instant.class)))
                .thenReturn(List.of(claim));
        when(claimRepository.findByReleasedAtIsNull()).thenReturn(Collections.emptyList());
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        watchdogService.reapExpiredLeases();

        // Terminal task claim is marked done, never requeued
        assertThat(claim.getResultStatus()).isEqualTo(ClaimResultStatus.done);
        assertThat(claim.getReleasedAt()).isNotNull();
        verify(claimRepository).save(claim);
        verify(taskRepository, never()).compareAndSetStatus(any(), any(), any());
    }

    @Test
    @DisplayName("HOHFELD_04: Concurrent task state transition (CAS returning 0) is handled gracefully")
    void concurrentTaskStateChange_losingCASHandledGracefully() {
        UUID taskId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.claimed);

        AccountEntity account = new AccountEntity();
        account.setId(accountId);

        ClaimEntity claim = new ClaimEntity();
        claim.setId(UUID.randomUUID());
        claim.setTask(task);
        claim.setAccount(account);
        claim.setLeaseExpiresAt(Instant.now().minus(5, ChronoUnit.MINUTES));

        when(claimRepository.findByReleasedAtIsNullAndLeaseExpiresAtBefore(any(Instant.class)))
                .thenReturn(List.of(claim));
        when(claimRepository.findByReleasedAtIsNull()).thenReturn(Collections.emptyList());
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(Collections.emptyList());

        // CAS returns 0 because task was concurrently marked done or failed by another thread
        when(taskRepository.compareAndSetStatus(taskId, TaskStatus.claimed, TaskStatus.queued)).thenReturn(0);

        watchdogService.reapExpiredLeases();

        // Claim still marked expired
        assertThat(claim.getResultStatus()).isEqualTo(ClaimResultStatus.expired);
        verify(claimRepository).save(claim);
        // CAS was attempted
        verify(taskRepository).compareAndSetStatus(taskId, TaskStatus.claimed, TaskStatus.queued);
    }
}
