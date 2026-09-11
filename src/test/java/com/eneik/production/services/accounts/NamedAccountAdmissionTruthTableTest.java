package com.eneik.production.services.accounts;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.ClaimService;
import com.eneik.production.services.ProjectFlowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Truth-table test for Prescription 20 (PRINCIPLED_INTEGRITY / D012, Law 12).
 * Verifies that named account selection refusal names the EXACT conjunct that failed
 * and never reports false "capacity" exhaustion when an account is disabled, resting,
 * retired, or locked by a concurrent claim.
 */
class NamedAccountAdmissionTruthTableTest {

    private AccountRepository accountRepository;
    private TaskRepository taskRepository;
    private ClaimService claimService;
    private ProjectFlowService service;

    private static final String COMPILER_ACCOUNT = "eneikdru";
    private static final int DEFAULT_MAX_SESSIONS = 3;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        taskRepository = mock(TaskRepository.class);
        claimService = mock(ClaimService.class);

        service = mock(ProjectFlowService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "accountRepository", accountRepository);
        ReflectionTestUtils.setField(service, "taskRepository", taskRepository);
        ReflectionTestUtils.setField(service, "claimService", claimService);
        ReflectionTestUtils.setField(service, "maxConcurrentJulesSessionsPerAccount", DEFAULT_MAX_SESSIONS);
        ReflectionTestUtils.setField(service, "self", service);

        // bypass self.claimAccountForTask by directly executing the action supplier
        doAnswer(inv -> {
            java.util.function.Supplier<Optional<AccountEntity>> action = inv.getArgument(1);
            return action.get();
        }).when(service).claimAccountForTask(any(), any());
    }

    private AccountEntity createAccount(boolean enabled, AccountStatus status, Integer maxSessions) {
        AccountEntity acc = new AccountEntity();
        acc.setId(UUID.randomUUID());
        acc.setName(COMPILER_ACCOUNT);
        acc.setEnabled(enabled);
        acc.setStatus(status);
        acc.setMaxConcurrentSessions(maxSessions);
        return acc;
    }

    @Test
    @DisplayName("ADMITTED: returns ADMITTED when lock query succeeds")
    void admittedWhenLockedAccountIsPresent() {
        AccountEntity account = createAccount(true, AccountStatus.idle, 3);
        AccountAdmissionOutcome outcome = service.evaluateNamedAccountAdmissionDecision(
                Optional.of(account), COMPILER_ACCOUNT, UUID.randomUUID(), DEFAULT_MAX_SESSIONS).outcome();
        assertEquals(AccountAdmissionOutcome.ADMITTED, outcome);
    }

    @Test
    @DisplayName("NOT_FOUND: account does not exist in repository")
    void notFoundWhenAccountMissing() {
        when(accountRepository.findByName(COMPILER_ACCOUNT)).thenReturn(Optional.empty());

        ProjectFlowService.NamedAccountAdmissionDecision decision = service.evaluateNamedAccountAdmissionDecision(
                Optional.empty(), COMPILER_ACCOUNT, UUID.randomUUID(), DEFAULT_MAX_SESSIONS);

        assertEquals(AccountAdmissionOutcome.NOT_FOUND, decision.outcome());
        assertThat(decision.dispatchStatus()).isEqualTo("Compiler account 'eneikdru' not found");
        assertThat(decision.logMessage().toLowerCase()).doesNotContain("capacity");
        assertThat(decision.logMessage().toLowerCase()).doesNotContain("ёмкость");
    }

    @Test
    @DisplayName("DISABLED: account exists but enabled=false")
    void disabledWhenAccountNotEnabled() {
        AccountEntity account = createAccount(false, AccountStatus.idle, 3);
        when(accountRepository.findByName(COMPILER_ACCOUNT)).thenReturn(Optional.of(account));

        ProjectFlowService.NamedAccountAdmissionDecision decision = service.evaluateNamedAccountAdmissionDecision(
                Optional.empty(), COMPILER_ACCOUNT, UUID.randomUUID(), DEFAULT_MAX_SESSIONS);

        assertEquals(AccountAdmissionOutcome.DISABLED, decision.outcome());
        assertThat(decision.dispatchStatus()).isEqualTo("Compiler account 'eneikdru' is disabled");
        assertThat(decision.logMessage()).contains("is disabled (enabled=false)");
        assertThat(decision.logMessage().toLowerCase()).doesNotContain("capacity");
        assertThat(decision.logMessage().toLowerCase()).doesNotContain("ёмкость");
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"decommissioned", "offline"})
    @DisplayName("RETIRED: account has decommissioned or offline status")
    void retiredWhenDecommissionedOrOffline(AccountStatus status) {
        AccountEntity account = createAccount(true, status, 3);
        when(accountRepository.findByName(COMPILER_ACCOUNT)).thenReturn(Optional.of(account));

        ProjectFlowService.NamedAccountAdmissionDecision decision = service.evaluateNamedAccountAdmissionDecision(
                Optional.empty(), COMPILER_ACCOUNT, UUID.randomUUID(), DEFAULT_MAX_SESSIONS);

        assertEquals(AccountAdmissionOutcome.RETIRED, decision.outcome());
        assertThat(decision.dispatchStatus()).isEqualTo("Compiler account 'eneikdru' is retired/offline");
        assertThat(decision.logMessage()).contains("retired/offline");
        assertThat(decision.logMessage().toLowerCase()).doesNotContain("capacity");
        assertThat(decision.logMessage().toLowerCase()).doesNotContain("ёмкость");
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"daily_limited", "api_blocked"})
    @DisplayName("RESTING: account has daily_limited or api_blocked status (falsification: 0 sessions does NOT report capacity)")
    void restingWhenDailyLimitedOrApiBlocked(AccountStatus status) {
        AccountEntity account = createAccount(true, status, 3);
        when(accountRepository.findByName(COMPILER_ACCOUNT)).thenReturn(Optional.of(account));
        when(accountRepository.countOpenSessions(account.getId())).thenReturn(0);

        ProjectFlowService.NamedAccountAdmissionDecision decision = service.evaluateNamedAccountAdmissionDecision(
                Optional.empty(), COMPILER_ACCOUNT, UUID.randomUUID(), DEFAULT_MAX_SESSIONS);

        assertEquals(AccountAdmissionOutcome.RESTING, decision.outcome());
        assertThat(decision.dispatchStatus()).isEqualTo("Compiler account 'eneikdru' is resting/blocked");
        assertThat(decision.logMessage()).contains("resting/blocked");
        // Rigorous falsification: must never claim capacity exhaustion
        assertThat(decision.logMessage().toLowerCase()).doesNotContain("capacity");
        assertThat(decision.logMessage().toLowerCase()).doesNotContain("ёмкость");
    }

    @Test
    @DisplayName("SESSIONS_EXHAUSTED: account is enabled and idle, but open sessions >= limit (genuine capacity exhaustion)")
    void sessionsExhaustedWhenOpenSessionsAtOrAboveLimit() {
        AccountEntity account = createAccount(true, AccountStatus.idle, 3);
        when(accountRepository.findByName(COMPILER_ACCOUNT)).thenReturn(Optional.of(account));
        when(accountRepository.countOpenSessions(account.getId())).thenReturn(3);

        ProjectFlowService.NamedAccountAdmissionDecision decision = service.evaluateNamedAccountAdmissionDecision(
                Optional.empty(), COMPILER_ACCOUNT, UUID.randomUUID(), DEFAULT_MAX_SESSIONS);

        assertEquals(AccountAdmissionOutcome.SESSIONS_EXHAUSTED, decision.outcome());
        assertThat(decision.dispatchStatus()).isEqualTo("Compiler account 'eneikdru' has no free capacity");
        assertThat(decision.logMessage()).contains("has no free capacity");
    }

    @Test
    @DisplayName("LOCKED_BY_CONCURRENT_CLAIM: account satisfies all conjuncts, but lock was held by another transaction")
    void lockedByConcurrentClaimWhenAllConjunctsPassButLockQueryWasEmpty() {
        AccountEntity account = createAccount(true, AccountStatus.idle, 3);
        when(accountRepository.findByName(COMPILER_ACCOUNT)).thenReturn(Optional.of(account));
        // Only 1 open session out of 3 -> plenty of capacity
        when(accountRepository.countOpenSessions(account.getId())).thenReturn(1);

        ProjectFlowService.NamedAccountAdmissionDecision decision = service.evaluateNamedAccountAdmissionDecision(
                Optional.empty(), COMPILER_ACCOUNT, UUID.randomUUID(), DEFAULT_MAX_SESSIONS);

        assertEquals(AccountAdmissionOutcome.LOCKED_BY_CONCURRENT_CLAIM, decision.outcome());
        assertThat(decision.dispatchStatus()).isEqualTo("Compiler account 'eneikdru' is locked by concurrent claim");
        assertThat(decision.logMessage()).contains("locked by concurrent claim");
        assertThat(decision.logMessage().toLowerCase()).doesNotContain("capacity");
        assertThat(decision.logMessage().toLowerCase()).doesNotContain("ёмкость");
    }

    @Test
    @DisplayName("End-to-End dispatchToGeneralPool: daily_limited account sets task dispatch status to resting and not capacity")
    void dispatchToGeneralPoolRecordsRestingStatusOnTaskForDailyLimited() {
        TaskEntity compilerTask = new TaskEntity();
        compilerTask.setId(UUID.randomUUID());
        RoleEntity role = new RoleEntity(); role.setTag("BARCAN-TAG-09");
        compilerTask.setRole(role);
        compilerTask.setStatus(TaskStatus.queued);

        AccountEntity account = createAccount(true, AccountStatus.daily_limited, 3);
        when(accountRepository.lockAccountByNameWithCapacity(eq(COMPILER_ACCOUNT), anyInt()))
                .thenReturn(Optional.empty());
        when(accountRepository.findByName(COMPILER_ACCOUNT)).thenReturn(Optional.of(account));
        when(accountRepository.countOpenSessions(account.getId())).thenReturn(0);

        boolean dispatched = ReflectionTestUtils.invokeMethod(service, "dispatchToGeneralPool",
                compilerTask, java.util.Set.of(), null, COMPILER_ACCOUNT);

        assertFalse(dispatched);
        assertThat(compilerTask.getJulesDispatchStatus()).isEqualTo("Compiler account 'eneikdru' is resting/blocked");
        assertThat(compilerTask.getJulesDispatchStatus().toLowerCase()).doesNotContain("capacity");
        assertThat(compilerTask.getJulesDispatchStatus().toLowerCase()).doesNotContain("ёмкость");
        verify(taskRepository).save(compilerTask);
    }
}
