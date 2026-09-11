package com.eneik.production.services.accounts;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.ClaimService;
import com.eneik.production.services.ProjectFlowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Truth-table test for Prescription 22 (PART_WHOLE_OWNERSHIP / D004, Law 12).
 * Verifies that general-pool account admission refusal names the EXACT conjunct that failed
 * across the candidate accounts and never reports false "session slot capacity" exhaustion
 * when an account was refused due to daily limit, disablement, resting, or role exclusions.
 */
class GeneralPoolAdmissionTruthTableTest {

    private AccountRepository accountRepository;
    private TaskRepository taskRepository;
    private ClaimService claimService;
    private ProjectFlowService service;

    private static final int DEFAULT_MAX_CONCURRENT = 3;
    private static final int DEFAULT_MAX_DAILY = 15;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        taskRepository = mock(TaskRepository.class);
        claimService = mock(ClaimService.class);

        service = mock(ProjectFlowService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "accountRepository", accountRepository);
        ReflectionTestUtils.setField(service, "taskRepository", taskRepository);
        ReflectionTestUtils.setField(service, "claimService", claimService);
        ReflectionTestUtils.setField(service, "maxConcurrentJulesSessionsPerAccount", DEFAULT_MAX_CONCURRENT);
        ReflectionTestUtils.setField(service, "maxDailySessionsPerAccount", DEFAULT_MAX_DAILY);
        ReflectionTestUtils.setField(service, "self", service);
    }

    private AccountEntity createAccount(String name, boolean enabled, AccountStatus status, Integer dailyCapacity, Integer concurrentCapacity) {
        AccountEntity acc = new AccountEntity();
        acc.setId(UUID.randomUUID());
        acc.setName(name);
        acc.setEnabled(enabled);
        acc.setStatus(status);
        acc.setApiKey("valid-key");
        acc.setCapabilities("*");
        acc.setEstimatedDailyCapacity(dailyCapacity);
        acc.setEstimatedConcurrentCapacity(concurrentCapacity);
        return acc;
    }

    @Test
    @DisplayName("ADMITTED: returns ADMITTED when lock query succeeds")
    void admittedWhenLockedAccountIsPresent() {
        AccountEntity account = createAccount("acc1", true, AccountStatus.idle, 15, 3);
        ProjectFlowService.GeneralPoolAdmissionDecision decision = service.evaluateGeneralPoolAdmissionDecision(
                Optional.of(account), UUID.randomUUID(), "backend", Set.of(), null, UUID.randomUUID(), DEFAULT_MAX_CONCURRENT, DEFAULT_MAX_DAILY);

        assertEquals(AccountAdmissionOutcome.ADMITTED, decision.outcome());
        assertThat(decision.dispatchStatus()).isEqualTo("Admitted");
        assertThat(decision.logMessage()).contains("General-pool account 'acc1' admitted");
    }

    @Test
    @DisplayName("NOT_FOUND: no accounts configured in database")
    void notFoundWhenNoAccountsInPool() {
        when(accountRepository.findAll()).thenReturn(List.of());

        ProjectFlowService.GeneralPoolAdmissionDecision decision = service.evaluateGeneralPoolAdmissionDecision(
                Optional.empty(), UUID.randomUUID(), "backend", Set.of(), null, UUID.randomUUID(), DEFAULT_MAX_CONCURRENT, DEFAULT_MAX_DAILY);

        assertEquals(AccountAdmissionOutcome.NOT_FOUND, decision.outcome());
        assertThat(decision.dispatchStatus()).contains("No Jules accounts in pool");
    }

    @Test
    @DisplayName("RETIRED: all accounts in pool are decommissioned")
    void retiredWhenAllAccountsDecommissioned() {
        AccountEntity acc = createAccount("decom1", false, AccountStatus.decommissioned, 15, 3);
        when(accountRepository.findAll()).thenReturn(List.of(acc));

        ProjectFlowService.GeneralPoolAdmissionDecision decision = service.evaluateGeneralPoolAdmissionDecision(
                Optional.empty(), UUID.randomUUID(), "backend", Set.of(), null, UUID.randomUUID(), DEFAULT_MAX_CONCURRENT, DEFAULT_MAX_DAILY);

        assertEquals(AccountAdmissionOutcome.RETIRED, decision.outcome());
        assertThat(decision.dispatchStatus()).contains("All Jules accounts are decommissioned");
    }

    @Test
    @DisplayName("DISABLED: all operational accounts are disabled (enabled = false)")
    void disabledWhenAllOperationalAccountsAreDisabled() {
        AccountEntity a1 = createAccount("a1", false, AccountStatus.idle, 15, 3);
        AccountEntity a2 = createAccount("a2", false, AccountStatus.idle, 15, 3);
        when(accountRepository.findAll()).thenReturn(List.of(a1, a2));

        ProjectFlowService.GeneralPoolAdmissionDecision decision = service.evaluateGeneralPoolAdmissionDecision(
                Optional.empty(), UUID.randomUUID(), "backend", Set.of(), null, UUID.randomUUID(), DEFAULT_MAX_CONCURRENT, DEFAULT_MAX_DAILY);

        assertEquals(AccountAdmissionOutcome.DISABLED, decision.outcome());
        assertThat(decision.dispatchStatus()).isEqualTo("All operational Jules accounts are disabled (2 disabled); role context backend");
        assertThat(decision.logMessage()).contains("All operational Jules accounts are disabled (2 disabled)");
        assertThat(decision.logMessage().toLowerCase()).doesNotContain("capacity");
        assertThat(decision.logMessage().toLowerCase()).doesNotContain("ёмкость");
    }

    @Test
    @DisplayName("DAILY_LIMIT_EXCEEDED: live incident reproduction (6 disabled accounts, 1 enabled account with daily limit exceeded)")
    void dailyLimitExceededWhenEnabledAccountHitDailyQuota() {
        // Live incident: 6 disabled accounts + 1 account (eneikdru) with 501 sessions dispatched today against ceiling 15.
        // It has 0 open sessions (slots completely free), but cannot be selected because of daily limit.
        AccountEntity eneikdru = createAccount("eneikdru", true, AccountStatus.idle, 15, 3);
        eneikdru.setSessionsDispatchedToday(501);

        AccountEntity d1 = createAccount("d1", false, AccountStatus.idle, 15, 3);
        AccountEntity d2 = createAccount("d2", false, AccountStatus.idle, 15, 3);
        AccountEntity d3 = createAccount("d3", false, AccountStatus.idle, 15, 3);
        AccountEntity d4 = createAccount("d4", false, AccountStatus.idle, 15, 3);
        AccountEntity d5 = createAccount("d5", false, AccountStatus.idle, 15, 3);
        AccountEntity d6 = createAccount("d6", false, AccountStatus.idle, 15, 3);

        when(accountRepository.findAll()).thenReturn(List.of(eneikdru, d1, d2, d3, d4, d5, d6));
        when(accountRepository.countOpenSessions(eneikdru.getId())).thenReturn(0);

        ProjectFlowService.GeneralPoolAdmissionDecision decision = service.evaluateGeneralPoolAdmissionDecision(
                Optional.empty(), UUID.randomUUID(), "review", Set.of(), null, UUID.randomUUID(), DEFAULT_MAX_CONCURRENT, DEFAULT_MAX_DAILY);

        assertEquals(AccountAdmissionOutcome.DAILY_LIMIT_EXCEEDED, decision.outcome());
        assertThat(decision.dispatchStatus())
                .isEqualTo("All enabled Jules accounts exceeded daily limit (1 daily limited, 6 disabled); role context review");
        assertThat(decision.logMessage())
                .contains("All enabled Jules accounts exceeded daily limit (1 daily limited, 6 disabled)");

        // Rigorous falsification: Must NEVER report false session slot capacity exhaustion!
        assertThat(decision.dispatchStatus().toLowerCase()).doesNotContain("slot available");
        assertThat(decision.logMessage().toLowerCase()).doesNotContain("has free capacity right now");
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"daily_limited", "api_blocked"})
    @DisplayName("RESTING: enabled account has status daily_limited or api_blocked")
    void restingWhenEnabledAccountInRestingStatus(AccountStatus status) {
        AccountEntity acc = createAccount("acc1", true, status, 15, 3);
        when(accountRepository.findAll()).thenReturn(List.of(acc));
        when(accountRepository.countOpenSessions(acc.getId())).thenReturn(0);

        ProjectFlowService.GeneralPoolAdmissionDecision decision = service.evaluateGeneralPoolAdmissionDecision(
                Optional.empty(), UUID.randomUUID(), "backend", Set.of(), null, UUID.randomUUID(), DEFAULT_MAX_CONCURRENT, DEFAULT_MAX_DAILY);

        assertEquals(AccountAdmissionOutcome.RESTING, decision.outcome());
        assertThat(decision.dispatchStatus()).isEqualTo("All enabled Jules accounts are resting/blocked (1 resting); role context backend");
        assertThat(decision.logMessage()).contains("All enabled Jules accounts are resting/blocked (1 resting)");
        assertThat(decision.logMessage().toLowerCase()).doesNotContain("capacity");
    }

    @Test
    @DisplayName("SESSIONS_EXHAUSTED: enabled account has reached max concurrent sessions")
    void sessionsExhaustedWhenOpenSessionsReachCapacity() {
        AccountEntity acc = createAccount("acc1", true, AccountStatus.idle, 15, 3);
        acc.setSessionsDispatchedToday(2);
        when(accountRepository.findAll()).thenReturn(List.of(acc));
        when(accountRepository.countOpenSessions(acc.getId())).thenReturn(3);

        ProjectFlowService.GeneralPoolAdmissionDecision decision = service.evaluateGeneralPoolAdmissionDecision(
                Optional.empty(), UUID.randomUUID(), "backend", Set.of(), null, UUID.randomUUID(), DEFAULT_MAX_CONCURRENT, DEFAULT_MAX_DAILY);

        assertEquals(AccountAdmissionOutcome.SESSIONS_EXHAUSTED, decision.outcome());
        assertThat(decision.dispatchStatus()).isEqualTo("No free Jules shared session slot available for role context backend");
        assertThat(decision.logMessage()).contains("No general-pool account has free capacity right now");
    }

    @Test
    @DisplayName("EXCLUDED_BY_RULE: enabled account excluded by implementer rule (Charter Pattern #12)")
    void excludedByRuleWhenAccountIsExcludedImplementer() {
        AccountEntity acc = createAccount("implementer-acc", true, AccountStatus.idle, 15, 3);
        acc.setSessionsDispatchedToday(2);
        when(accountRepository.findAll()).thenReturn(List.of(acc));
        when(accountRepository.countOpenSessions(acc.getId())).thenReturn(0);

        ProjectFlowService.GeneralPoolAdmissionDecision decision = service.evaluateGeneralPoolAdmissionDecision(
                Optional.empty(), UUID.randomUUID(), "review", Set.of("implementer-acc"), null, UUID.randomUUID(), DEFAULT_MAX_CONCURRENT, DEFAULT_MAX_DAILY);

        assertEquals(AccountAdmissionOutcome.EXCLUDED_BY_RULE, decision.outcome());
        assertThat(decision.dispatchStatus()).isEqualTo("All eligible Jules accounts excluded by rule (1 excluded); role context review");
        assertThat(decision.logMessage()).contains("All eligible Jules accounts excluded by rule (1 excluded)");
    }

    @Test
    @DisplayName("REFUSAL_NOT_REPRODUCED_ON_RECHECK: all conjuncts pass on recheck (locked by concurrent transaction SKIP LOCKED)")
    void refusalNotReproducedOnRecheckWhenAccountSatisfiesAllConjuncts() {
        AccountEntity acc = createAccount("free-acc", true, AccountStatus.idle, 15, 3);
        acc.setSessionsDispatchedToday(2);
        when(accountRepository.findAll()).thenReturn(List.of(acc));
        when(accountRepository.countOpenSessions(acc.getId())).thenReturn(0);

        ProjectFlowService.GeneralPoolAdmissionDecision decision = service.evaluateGeneralPoolAdmissionDecision(
                Optional.empty(), UUID.randomUUID(), "backend", Set.of(), null, UUID.randomUUID(), DEFAULT_MAX_CONCURRENT, DEFAULT_MAX_DAILY);

        assertEquals(AccountAdmissionOutcome.REFUSAL_NOT_REPRODUCED_ON_RECHECK, decision.outcome());
        assertThat(decision.dispatchStatus()).isEqualTo("Jules general pool accounts busy on recheck (locked or state change); role context backend");
        assertThat(decision.logMessage()).contains("refusal not reproduced (concurrent lock or transient state change)");
    }

    @Test
    @DisplayName("MULTIPLE_CONJUNCTS_VIOLATED: distinct accounts in pool fail for different reasons")
    void multipleConjunctsViolatedWhenAccountsFailForDifferentReasons() {
        // Account 1: daily limited
        AccountEntity a1 = createAccount("a1", true, AccountStatus.idle, 15, 3);
        a1.setSessionsDispatchedToday(15);

        // Account 2: slots exhausted
        AccountEntity a2 = createAccount("a2", true, AccountStatus.idle, 15, 3);
        a2.setSessionsDispatchedToday(2);

        // Account 3: disabled
        AccountEntity a3 = createAccount("a3", false, AccountStatus.idle, 15, 3);

        when(accountRepository.findAll()).thenReturn(List.of(a1, a2, a3));
        when(accountRepository.countOpenSessions(a1.getId())).thenReturn(0);
        when(accountRepository.countOpenSessions(a2.getId())).thenReturn(3);

        ProjectFlowService.GeneralPoolAdmissionDecision decision = service.evaluateGeneralPoolAdmissionDecision(
                Optional.empty(), UUID.randomUUID(), "backend", Set.of(), null, UUID.randomUUID(), DEFAULT_MAX_CONCURRENT, DEFAULT_MAX_DAILY);

        assertEquals(AccountAdmissionOutcome.MULTIPLE_CONJUNCTS_VIOLATED, decision.outcome());
        assertThat(decision.dispatchStatus()).contains("1 daily limited");
        assertThat(decision.dispatchStatus()).contains("1 slots exhausted");
        assertThat(decision.dispatchStatus()).contains("1 disabled");
        assertThat(decision.logMessage()).contains("Jules general pool admission refused");
    }
}
