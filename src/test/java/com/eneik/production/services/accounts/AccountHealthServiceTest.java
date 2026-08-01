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
import static org.mockito.Mockito.when;

/**
 * Sole-owner account-health transitions (reportDispatchOutcome) and the data-driven recovery backoff
 * (recoverEligibleAccounts) - 2026-08-01, closing the charter #10 violation where AccountEntity.status
 * used to be written from 5 different places with no single owner.
 */
class AccountHealthServiceTest {

    private AccountRepository accountRepository;
    private DefectJournalRepository defectJournalRepository;
    private AccountHealthService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        defectJournalRepository = mock(DefectJournalRepository.class);
        service = new AccountHealthService(accountRepository, defectJournalRepository);
        ReflectionTestUtils.setField(service, "baseCooldownMinutes", 30);
        ReflectionTestUtils.setField(service, "maxCooldownMinutes", 480);
        ReflectionTestUtils.setField(service, "minSamplesForDataDriven", 5);
        ReflectionTestUtils.setField(service, "zFactor", 1.0);
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
    void preconditionBlockedSetsStatusIncrementsCounterAndRecordsDefect() {
        AccountEntity idle = account("acc-3", AccountStatus.idle, 0, Instant.now());
        when(accountRepository.findById(idle.getId())).thenReturn(Optional.of(idle));

        service.reportDispatchOutcome(idle.getId(), UUID.randomUUID(),
                AccountHealthService.DispatchOutcome.PRECONDITION_BLOCKED, "Precondition check failed.");

        assertEquals(AccountStatus.api_blocked, idle.getStatus());
        assertEquals(1, idle.getConsecutiveApiBlockCount());
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
}
