package com.eneik.production.services.accounts;

import com.eneik.production.kaizen.model.DefectJournalEntity;
import com.eneik.production.kaizen.repository.DefectJournalRepository;
import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.repositories.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Sole-owner account-health transitions (reportDispatchOutcome) and the data-driven recovery backoff
 * (recoverEligibleAccounts) - 2026-08-01, closing the charter #10 violation where AccountEntity.status
 * used to be written from 5 different places with no single owner.
 */
class AccountHealthServiceTest {

    private AccountRepository accountRepository;
    private DefectJournalRepository defectJournalRepository;
    private com.eneik.production.repositories.AccountRoleSuccessStatsRepository accountRoleSuccessStatsRepository;
    private com.eneik.production.services.lever.LeverPromotionService leverPromotionService;
    private AccountHealthService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        defectJournalRepository = mock(DefectJournalRepository.class);
        accountRoleSuccessStatsRepository = mock(com.eneik.production.repositories.AccountRoleSuccessStatsRepository.class);
        leverPromotionService = mock(com.eneik.production.services.lever.LeverPromotionService.class);
        service = new AccountHealthService(accountRepository, defectJournalRepository,
                accountRoleSuccessStatsRepository, leverPromotionService);
        ReflectionTestUtils.setField(service, "baseCooldownMinutes", 30);
        ReflectionTestUtils.setField(service, "maxCooldownMinutes", 480);
        ReflectionTestUtils.setField(service, "minSamplesForDataDriven", 5);
        ReflectionTestUtils.setField(service, "zFactor", 1.0);
        ReflectionTestUtils.setField(service, "preconditionBlockEscalationThreshold", 2);
        ReflectionTestUtils.setField(service, "defaultDailyCapacity", 15);
        ReflectionTestUtils.setField(service, "dailyCapacityProbeStep", 5);
        ReflectionTestUtils.setField(service, "dailyCapacityBackoffFactor", 0.7);
        when(defectJournalRepository.findBySourceComponentAndDefectTypeOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Collections.emptyList());
        when(defectJournalRepository.findByDefectTypeOrderByCreatedAtDesc(any()))
                .thenReturn(Collections.emptyList());
    }

    private AccountEntity account(String name, AccountStatus status, int consecutiveApiBlockCount, Instant statusChangedAt) {
        AccountEntity account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setName(name);
        account.setCapabilities("*");
        account.setStatus(status);
        account.setConsecutiveApiBlockCount(consecutiveApiBlockCount);
        ReflectionTestUtils.setField(account, "statusChangedAt", statusChangedAt);
        return account;
    }

    @Test
    void successAfterBeingBlockedRecordsRecoveryDurationAndResetsCounter() {
        AccountEntity blocked = account("acc-1", AccountStatus.api_blocked, 3, Instant.now().minus(45, ChronoUnit.MINUTES));
        when(accountRepository.findById(blocked.getId())).thenReturn(Optional.of(blocked));
        UUID projectId = UUID.randomUUID();

        service.reportDispatchOutcome(blocked.getId(), projectId, AccountHealthService.DispatchOutcome.SUCCESS, null);

        assertEquals(0, blocked.getConsecutiveApiBlockCount());
        assertEquals(1, blocked.getSessionsDispatchedToday());
        verify(defectJournalRepository).save(argThat(defect ->
                "ACCOUNT_RECOVERY_DURATION".equals(defect.getDefectType())
                        && defect.getMetricValue() != null
                        && defect.getMetricValue() >= 44 && defect.getMetricValue() <= 46));
        verify(accountRepository).save(blocked);
    }

    @Test
    void successWhileAlreadyIdleDoesNotFabricateARecoveryRecord() {
        AccountEntity idle = account("acc-2", AccountStatus.idle, 0, Instant.now());
        when(accountRepository.findById(idle.getId())).thenReturn(Optional.of(idle));

        service.reportDispatchOutcome(idle.getId(), UUID.randomUUID(), AccountHealthService.DispatchOutcome.SUCCESS, null);

        verify(defectJournalRepository, never()).save(any());
    }

    @Test
    void firstPreconditionFailureDoesNotBlockTheWholeAccount() {
        // Regression test for the live incident: a single malformed request (one oversized prompt) used to
        // set the whole account to api_blocked immediately, zeroing out every other concurrent slot over one
        // bad payload. Below the escalation threshold, the account keeps its other capacity - only the
        // counter and a MEDIUM-severity defect record it.
        AccountEntity idle = account("acc-3", AccountStatus.idle, 0, Instant.now());
        when(accountRepository.findById(idle.getId())).thenReturn(Optional.of(idle));

        service.reportDispatchOutcome(idle.getId(), UUID.randomUUID(),
                AccountHealthService.DispatchOutcome.PRECONDITION_BLOCKED, "Precondition check failed.");

        assertEquals(AccountStatus.idle, idle.getStatus());
        assertEquals(1, idle.getConsecutiveApiBlockCount());
        verify(defectJournalRepository).save(argThat(defect ->
                "API_PRECONDITION_BLOCKED".equals(defect.getDefectType()) && "MEDIUM".equals(defect.getSeverity())));
    }

    @Test
    void secondConsecutivePreconditionFailureEscalatesToAccountBlock() {
        // Real evidence the problem repeats (not request-specific) is what earns the full account block.
        AccountEntity oneStrike = account("acc-3b", AccountStatus.idle, 1, Instant.now());
        when(accountRepository.findById(oneStrike.getId())).thenReturn(Optional.of(oneStrike));

        service.reportDispatchOutcome(oneStrike.getId(), UUID.randomUUID(),
                AccountHealthService.DispatchOutcome.PRECONDITION_BLOCKED, "Precondition check failed.");

        assertEquals(AccountStatus.api_blocked, oneStrike.getStatus());
        assertEquals(2, oneStrike.getConsecutiveApiBlockCount());
        verify(defectJournalRepository).save(argThat(defect ->
                "API_PRECONDITION_BLOCKED".equals(defect.getDefectType()) && "HIGH".equals(defect.getSeverity())));
    }

    @Test
    void dailyLimitSetsStatusAndRecordsDefect() {
        AccountEntity idle = account("acc-4", AccountStatus.idle, 0, Instant.now());
        when(accountRepository.findById(idle.getId())).thenReturn(Optional.of(idle));

        service.reportDispatchOutcome(idle.getId(), UUID.randomUUID(),
                AccountHealthService.DispatchOutcome.DAILY_LIMIT, "quota exceeded");

        assertEquals(AccountStatus.daily_limited, idle.getStatus());
        verify(defectJournalRepository).save(argThat(defect -> "DAILY_LIMIT".equals(defect.getDefectType())));
    }

    // Engineering invariant #15 (2026-08-08): a real success that reaches the current, not-yet-refuted
    // daily-capacity belief is a bold conjecture that survived a severe test (Popper) - the belief revises
    // upward. Below, defaultDailyCapacity=15 and dailyCapacityProbeStep=5 (from setUp), so an account
    // starting with no prior estimate (null -> falls back to 15) that reaches count=15 must probe to 20.
    @Test
    void successReachingTheCurrentCeilingProbesTheEstimateUpward() {
        AccountEntity account = account("acc-probe", AccountStatus.idle, 0, Instant.now());
        account.setSessionsDispatchedToday(14);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        service.reportDispatchOutcome(account.getId(), UUID.randomUUID(), AccountHealthService.DispatchOutcome.SUCCESS, null);

        assertEquals(15, account.getSessionsDispatchedToday());
        assertEquals(20, account.getEstimatedDailyCapacity());
    }

    // A success well below the current ceiling tests nothing new - the belief must not move on
    // uninformative evidence (this is what distinguishes a real probe from noise).
    @Test
    void successWellBelowTheCurrentCeilingLeavesTheEstimateUnchanged() {
        AccountEntity account = account("acc-quiet", AccountStatus.idle, 0, Instant.now());
        account.setSessionsDispatchedToday(2);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        service.reportDispatchOutcome(account.getId(), UUID.randomUUID(), AccountHealthService.DispatchOutcome.SUCCESS, null);

        assertEquals(3, account.getSessionsDispatchedToday());
        assertEquals(null, account.getEstimatedDailyCapacity());
    }

    // The only event allowed to lower the belief: a real DAILY_LIMIT rejection from Jules. Revises to
    // backoffFactor (0.7) * the actual observed failure point, never back to the old unverified constant.
    @Test
    void realDailyLimitRejectionRevisesTheEstimateDownFromTheObservedFailurePoint() {
        AccountEntity account = account("acc-falsified", AccountStatus.idle, 0, Instant.now());
        account.setSessionsDispatchedToday(23);
        account.setEstimatedDailyCapacity(25);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        service.reportDispatchOutcome(account.getId(), UUID.randomUUID(),
                AccountHealthService.DispatchOutcome.DAILY_LIMIT, "quota exceeded");

        assertEquals(AccountStatus.daily_limited, account.getStatus());
        assertEquals(16, account.getEstimatedDailyCapacity());
    }

    @Test
    void recoverEligibleAccountsFallsBackToExponentialPriorWithTooFewSamples() {
        Instant now = Instant.now();
        AccountEntity firstBlockReady = account("a-first", AccountStatus.api_blocked, 1, now.minus(31, ChronoUnit.MINUTES));
        AccountEntity secondBlockTooSoon = account("b-second-too-soon", AccountStatus.api_blocked, 2, now.minus(31, ChronoUnit.MINUTES));
        AccountEntity secondBlockReady = account("c-second-ready", AccountStatus.api_blocked, 2, now.minus(61, ChronoUnit.MINUTES));
        AccountEntity manyBlocksCapped = account("d-many-capped", AccountStatus.api_blocked, 10, now.minus(500, ChronoUnit.MINUTES));

        when(accountRepository.findByStatusAndEnabledTrue(AccountStatus.api_blocked)).thenReturn(List.of(
                firstBlockReady, secondBlockTooSoon, secondBlockReady, manyBlocksCapped));
        when(accountRepository.resetSingleAccountFromApiBlocked(any())).thenReturn(1);

        service.recoverEligibleAccounts();

        verify(accountRepository, times(1)).resetSingleAccountFromApiBlocked(eq(firstBlockReady.getId()));
        verify(accountRepository, never()).resetSingleAccountFromApiBlocked(eq(secondBlockTooSoon.getId()));
        verify(accountRepository, times(1)).resetSingleAccountFromApiBlocked(eq(secondBlockReady.getId()));
        verify(accountRepository, times(1)).resetSingleAccountFromApiBlocked(eq(manyBlocksCapped.getId()));
    }

    @Test
    void recoverEligibleAccountsUsesMedianPlusZSigmaOnceEnoughHistoryExists() {
        // 5 past recovery durations (minutes): median=30, sample stdDev≈15.81 -> cooldown ≈ round(30+15.81)=46
        List<Double> durations = List.of(10.0, 20.0, 30.0, 40.0, 50.0);
        List<DefectJournalEntity> journalEntries = new ArrayList<>();
        for (double d : durations) {
            journalEntries.add(new DefectJournalEntity(null, null, null, "LOW", "ACCOUNT_HEALTH",
                    "e-data-driven", "ACCOUNT_RECOVERY_DURATION", "recovered", d));
        }
        when(defectJournalRepository.findBySourceComponentAndDefectTypeOrderByCreatedAtDesc("e-data-driven", "ACCOUNT_RECOVERY_DURATION"))
                .thenReturn(journalEntries);

        Instant now = Instant.now();
        AccountEntity notYetReady = account("e-data-driven", AccountStatus.api_blocked, 6, now.minus(45, ChronoUnit.MINUTES));
        AccountEntity ready = account("e-data-driven", AccountStatus.api_blocked, 6, now.minus(47, ChronoUnit.MINUTES));

        when(accountRepository.findByStatusAndEnabledTrue(AccountStatus.api_blocked)).thenReturn(List.of(notYetReady));
        service.recoverEligibleAccounts();
        verify(accountRepository, never()).resetSingleAccountFromApiBlocked(eq(notYetReady.getId()));

        when(accountRepository.findByStatusAndEnabledTrue(AccountStatus.api_blocked)).thenReturn(List.of(ready));
        when(accountRepository.resetSingleAccountFromApiBlocked(eq(ready.getId()))).thenReturn(1);
        service.recoverEligibleAccounts();
        verify(accountRepository, times(1)).resetSingleAccountFromApiBlocked(eq(ready.getId()));
    }

    @Test
    void medianAndStdDevMatchHandComputedValues() {
        List<Double> values = List.of(10.0, 20.0, 30.0, 40.0, 50.0);
        double median = AccountHealthService.median(values);
        double stdDev = AccountHealthService.stdDev(values, median);
        assertEquals(30.0, median, 1e-9);
        assertEquals(Math.sqrt(250.0), stdDev, 1e-9);
    }

    // 2026-08-08 (ML-update patch, Phase 4): F2_ACCOUNT_ROLE_SUCCESS_PROBABILITY Beta-Bernoulli tracking.

    @Test
    void realSuccessIncrementsAlphaForThatAccountRolePair() {
        AccountEntity acc = account("acc-role", AccountStatus.idle, 0, null);
        when(accountRepository.findById(acc.getId())).thenReturn(Optional.of(acc));
        when(accountRoleSuccessStatsRepository.findByAccountIdAndRoleTag(acc.getId(), "BARCAN-TAG-11"))
                .thenReturn(Optional.empty());

        service.reportDispatchOutcome(acc.getId(), UUID.randomUUID(),
                AccountHealthService.DispatchOutcome.SUCCESS, null, "BARCAN-TAG-11");

        var captor = org.mockito.ArgumentCaptor.forClass(com.eneik.production.models.persistence.AccountRoleSuccessStatsEntity.class);
        verify(accountRoleSuccessStatsRepository).save(captor.capture());
        assertEquals(2.0, captor.getValue().getAlpha(), 1e-9);
        assertEquals(1.0, captor.getValue().getBeta(), 1e-9);
    }

    @Test
    void preconditionBlockedIncrementsBetaForThatAccountRolePair() {
        AccountEntity acc = account("acc-role-2", AccountStatus.idle, 0, null);
        when(accountRepository.findById(acc.getId())).thenReturn(Optional.of(acc));
        when(accountRoleSuccessStatsRepository.findByAccountIdAndRoleTag(acc.getId(), "BARCAN-TAG-02"))
                .thenReturn(Optional.empty());

        service.reportDispatchOutcome(acc.getId(), UUID.randomUUID(),
                AccountHealthService.DispatchOutcome.PRECONDITION_BLOCKED, "bad request", "BARCAN-TAG-02");

        var captor = org.mockito.ArgumentCaptor.forClass(com.eneik.production.models.persistence.AccountRoleSuccessStatsEntity.class);
        verify(accountRoleSuccessStatsRepository).save(captor.capture());
        assertEquals(1.0, captor.getValue().getAlpha(), 1e-9);
        assertEquals(2.0, captor.getValue().getBeta(), 1e-9);
    }

    @Test
    void dailyLimitDoesNotTouchRoleSuccessStatsAtAll() {
        AccountEntity acc = account("acc-role-3", AccountStatus.idle, 0, null);
        when(accountRepository.findById(acc.getId())).thenReturn(Optional.of(acc));

        service.reportDispatchOutcome(acc.getId(), UUID.randomUUID(),
                AccountHealthService.DispatchOutcome.DAILY_LIMIT, "limit hit", "BARCAN-TAG-11");

        verifyNoInteractions(accountRoleSuccessStatsRepository, leverPromotionService);
    }

    @Test
    void nullRoleTagSkipsTheRoleSuccessUpdateEntirely() {
        AccountEntity acc = account("acc-role-4", AccountStatus.idle, 0, null);
        when(accountRepository.findById(acc.getId())).thenReturn(Optional.of(acc));

        service.reportDispatchOutcome(acc.getId(), UUID.randomUUID(),
                AccountHealthService.DispatchOutcome.SUCCESS, null);

        verifyNoInteractions(accountRoleSuccessStatsRepository, leverPromotionService);
    }

    @Test
    void observationRecordsDisagreementWhenAWeakPriorPredictsFailureButTheRealOutcomeSucceeds() {
        AccountEntity acc = account("acc-role-5", AccountStatus.idle, 0, null);
        when(accountRepository.findById(acc.getId())).thenReturn(Optional.of(acc));
        com.eneik.production.models.persistence.AccountRoleSuccessStatsEntity weakPrior =
                new com.eneik.production.models.persistence.AccountRoleSuccessStatsEntity();
        weakPrior.setAccountId(acc.getId());
        weakPrior.setRoleTag("BARCAN-TAG-07");
        weakPrior.setAlpha(1.0);
        weakPrior.setBeta(9.0); // prior probability 0.1 -> predicts failure
        when(accountRoleSuccessStatsRepository.findByAccountIdAndRoleTag(acc.getId(), "BARCAN-TAG-07"))
                .thenReturn(Optional.of(weakPrior));

        service.reportDispatchOutcome(acc.getId(), UUID.randomUUID(),
                AccountHealthService.DispatchOutcome.SUCCESS, null, "BARCAN-TAG-07");

        verify(leverPromotionService).recordObservation(
                org.mockito.ArgumentMatchers.eq(AccountHealthService.F2_ACCOUNT_ROLE_SUCCESS_PROBABILITY),
                org.mockito.ArgumentMatchers.eq(acc.getId() + ":BARCAN-TAG-07"),
                org.mockito.ArgumentMatchers.eq("no_prediction"),
                org.mockito.ArgumentMatchers.eq("predict_failure"),
                org.mockito.ArgumentMatchers.eq(com.eneik.production.services.lever.LeverAgreement.FALSE),
                org.mockito.ArgumentMatchers.eq("success"));
    }
}
