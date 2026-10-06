package com.eneik.production.services.accounts;

import com.eneik.production.kaizen.model.DefectJournalEntity;
import com.eneik.production.kaizen.repository.DefectJournalRepository;
import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.AccountRoleSuccessStatsRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.services.lever.LeverPromotionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Popperian Falsification Test Harness for AccountHealthService (Stage 4).
 *
 * <p>Philosopher Anchors:
 * 1. AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP [D004, Varzi]:
 *    Sole aggregate ownership of account HEALTH state (idle, api_blocked, daily_limited).
 *    Deliberate non-interference in occupancy (busy - owned by ClaimService) and administrative state (offline/decommissioned).
 *    Normalization of contradictory decommissioned state (enabled=false) with explicit institutional audit log.
 *
 * 2. NUEL_BELNAP_03_TRUTH_STATUS_TABLE [D012, Belnap]:
 *    Ashby's requisite variety in DispatchOutcome lattice. REQUEST_REJECTED (factory defect) is never charged
 *    to the account (zero status/counter/defect mutation). PRECONDITION_UNSPECIFIED / UNCLASSIFIED steps aside
 *    without falsely revising capacity estimates or libeling the account with a named cause.
 *
 * 3. ELVIN_GOLDMAN_01_RELIABILITY_CHAIN [D010, Goldman]:
 *    Causal Bayesian-style reliability: recovery cooldown is calculated from empirical samples (median + z*sigma)
 *    when >= 5 samples exist, falling back to exponential prior when data is scarce.
 *    Replenishment period strictly avoids cross-account pollution (category error) and computes honest boundaries.
 */
class AccountHealthServiceFalsificationTest {

    private AccountRepository accountRepository;
    private DefectJournalRepository defectJournalRepository;
    private AccountRoleSuccessStatsRepository accountRoleSuccessStatsRepository;
    private LeverPromotionService leverPromotionService;
    private JulesSessionRepository julesSessionRepository;
    private AccountHealthService service;

    private final UUID accountId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        defectJournalRepository = mock(DefectJournalRepository.class);
        accountRoleSuccessStatsRepository = mock(AccountRoleSuccessStatsRepository.class);
        leverPromotionService = mock(LeverPromotionService.class);
        julesSessionRepository = mock(JulesSessionRepository.class);

        service = new AccountHealthService(
                accountRepository,
                defectJournalRepository,
                accountRoleSuccessStatsRepository,
                leverPromotionService,
                julesSessionRepository
        );

        ReflectionTestUtils.setField(service, "baseCooldownMinutes", 30);
        ReflectionTestUtils.setField(service, "maxCooldownMinutes", 480);
        ReflectionTestUtils.setField(service, "minSamplesForDataDriven", 5);
        ReflectionTestUtils.setField(service, "zFactor", 1.0);
        ReflectionTestUtils.setField(service, "preconditionBlockEscalationThreshold", 2);
        ReflectionTestUtils.setField(service, "defaultDailyCapacity", 15);
        ReflectionTestUtils.setField(service, "dailyCapacityProbeStep", 5);
        ReflectionTestUtils.setField(service, "dailyCapacityBackoffFactor", 0.7);
        ReflectionTestUtils.setField(service, "fullJitter", false);
        ReflectionTestUtils.setField(service, "offlineRelaxationMinutes", 15);
        ReflectionTestUtils.setField(service, "defaultReplenishmentPeriodHours", 24);

        when(defectJournalRepository.findBySourceComponentAndDefectTypeOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Collections.emptyList());
        when(defectJournalRepository.findByDefectTypeOrderByCreatedAtDesc(any()))
                .thenReturn(Collections.emptyList());
    }

    private AccountEntity createAccount(String name, AccountStatus status, int blockCount, Instant statusChangedAt) {
        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setName(name);
        account.setStatus(status);
        account.setEnabled(true);
        account.setConsecutiveApiBlockCount(blockCount);
        account.setSessionsDispatchedToday(0);
        account.setMaxConcurrentSessions(3);
        ReflectionTestUtils.setField(account, "statusChangedAt", statusChangedAt);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        return account;
    }

    // =========================================================================
    // 1. AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP [D004, Varzi]
    // =========================================================================

    @Test
    @DisplayName("Varzi D004: AccountHealthService does not overwrite busy occupancy on SUCCESS and preserves non-health status")
    void falsifyVarziPartWholeOwnership_soleOwnerOfHealthDoesNotMutateBusyOccupancy() {
        // Given an account that is currently marked 'busy' (occupancy managed by ClaimService)
        AccountEntity busyAccount = createAccount("busy-worker", AccountStatus.busy, 0, Instant.now().minus(10, ChronoUnit.MINUTES));

        // When SUCCESS is reported (which updates session count and decays block count)
        service.reportDispatchOutcome(busyAccount.getId(), projectId, AccountHealthService.DispatchOutcome.SUCCESS, null);

        // Then AccountHealthService MUST NOT reset status to idle - 'busy' is ClaimService's invariant!
        assertEquals(AccountStatus.busy, busyAccount.getStatus(),
                "Falsification: AccountHealthService violated part-whole boundary by altering ClaimService's 'busy' occupancy!");
        assertEquals(1, busyAccount.getSessionsDispatchedToday());
        verify(accountRepository).save(busyAccount);
    }

    @Test
    @DisplayName("Varzi D004 / Gilbert D007: recoverEligibleAccounts normalizes contradictory decommissioned state with audit record")
    void falsifyVarziPartWholeOwnership_normalizesContradictoryDecommissionedStateWithAudit() {
        // Given a contradictory decommissioned account that still has enabled=true
        AccountEntity contradictory = new AccountEntity();
        contradictory.setId(UUID.randomUUID());
        contradictory.setName("decommissioned-active");
        contradictory.setStatus(AccountStatus.decommissioned);
        ReflectionTestUtils.setField(contradictory, "enabled", true);

        when(accountRepository.findByStatusAndEnabledTrue(AccountStatus.decommissioned))
                .thenReturn(List.of(contradictory));
        when(accountRepository.findByStatusAndEnabledTrue(AccountStatus.api_blocked))
                .thenReturn(Collections.emptyList());
        when(accountRepository.findByStatusAndEnabledTrue(AccountStatus.daily_limited))
                .thenReturn(Collections.emptyList());
        when(accountRepository.findByStatusAndEnabledTrue(AccountStatus.offline))
                .thenReturn(Collections.emptyList());
        when(accountRepository.findByEnabledFalseAndStatusNot(AccountStatus.decommissioned))
                .thenReturn(Collections.emptyList());

        Instant now = Instant.now();
        int recovered = service.recoverEligibleAccounts(now);

        assertEquals(0, recovered, "Decommissioned normalization is audit cleanup, not a recovery into pool");
        assertFalse(contradictory.isEnabled(), "Falsification: contradictory decommissioned account was not disabled!");
        assertEquals(now, contradictory.getStatusChangedAt());
        verify(accountRepository).save(contradictory);

        // Verify institutional audit record written to DefectJournal
        verify(defectJournalRepository).save(argThat(dj ->
                "ACCOUNT_LIFECYCLE_NORMALIZATION_RULE".equals(dj.getDefectType())
                        && "INSTITUTIONAL_AUDIT".equals(dj.getCategory())
                        && "decommissioned-active".equals(dj.getSourceComponent())
        ));
    }

    // =========================================================================
    // 2. NUEL_BELNAP_03_TRUTH_STATUS_TABLE [D012, Belnap]
    // =========================================================================

    @Test
    @DisplayName("Belnap D012 / Ashby: REQUEST_REJECTED is factory fault and is strictly NOT charged to account")
    void falsifyBelnapTruthStatusTable_requestRejectedDoesNotLibelOrPenalizeAccount() {
        // Given a healthy idle account
        AccountEntity account = createAccount("innocent-account", AccountStatus.idle, 0, Instant.now());
        UUID taskId = UUID.randomUUID();

        // When dispatch results in REQUEST_REJECTED (e.g. invalid prompt / model argument error from factory)
        service.reportDispatchOutcome(account.getId(), projectId, taskId,
                AccountHealthService.DispatchOutcome.REQUEST_REJECTED,
                "{\"error\":{\"status\":\"INVALID_ARGUMENT\",\"message\":\"prompt too long\"}}", null);

        // Then the account MUST remain completely untouched:
        assertEquals(AccountStatus.idle, account.getStatus(), "Falsification: account status was changed on REQUEST_REJECTED!");
        assertEquals(0, account.getConsecutiveApiBlockCount(), "Falsification: block count incremented for factory request error!");
        assertEquals(0, account.getSessionsDispatchedToday(), "Falsification: session count altered!");

        // And no mutations saved to repository, no defects charged to the account in DefectJournal
        verify(accountRepository, never()).save(any());
        verify(defectJournalRepository, never()).save(any());
    }

    @Test
    @DisplayName("Belnap D012: PRECONDITION_UNSPECIFIED steps aside at threshold without false capacity reduction")
    void falsifyBelnapTruthStatusTable_unspecifiedRefusalStepsAsideWithoutCapacityDrift() {
        // Given an account with known daily capacity 20 and concurrent capacity 3
        AccountEntity account = createAccount("unspecified-victim", AccountStatus.idle, 0, Instant.now());
        account.setEstimatedDailyCapacity(20);
        account.setEstimatedConcurrentCapacity(3);

        // First unspecified refusal: increment counter to 1, stays idle, capacity untouched
        service.reportDispatchOutcome(account.getId(), projectId, UUID.randomUUID(),
                AccountHealthService.DispatchOutcome.PRECONDITION_UNSPECIFIED, "mystery 500 error", null);

        assertEquals(AccountStatus.idle, account.getStatus());
        assertEquals(1, account.getConsecutiveApiBlockCount());
        assertEquals(20, account.getEstimatedDailyCapacity(), "Falsification: daily capacity moved on uninformative refusal!");
        assertEquals(3, account.getEstimatedConcurrentCapacity(), "Falsification: concurrent capacity moved on uninformative refusal!");

        // Second unspecified refusal: reaches threshold 2 -> steps aside (api_blocked), still no capacity change
        service.reportDispatchOutcome(account.getId(), projectId, UUID.randomUUID(),
                AccountHealthService.DispatchOutcome.PRECONDITION_UNSPECIFIED, "mystery 500 error again", null);

        assertEquals(AccountStatus.api_blocked, account.getStatus());
        assertEquals(2, account.getConsecutiveApiBlockCount());
        assertEquals(20, account.getEstimatedDailyCapacity(), "Falsification: daily capacity corrupted on step aside!");
        assertEquals(3, account.getEstimatedConcurrentCapacity(), "Falsification: concurrent capacity corrupted on step aside!");

        // Defect journal captures UNNAMED_REFUSAL_DEFECT_TYPE with HIGH severity
        verify(defectJournalRepository).save(argThat(dj ->
                "API_REFUSAL_WITHOUT_NAMED_CONDITION".equals(dj.getDefectType())
                        && "HIGH".equals(dj.getSeverity())
        ));
    }

    // =========================================================================
    // 3. ELVIN_GOLDMAN_01_RELIABILITY_CHAIN [D010, Goldman]
    // =========================================================================

    @Test
    @DisplayName("Goldman D010: computeCooldownMinutes calculates median + z*sigma when sufficient samples exist")
    void falsifyGoldmanReliabilityChain_dataDrivenCooldownComputesMedianPlusZSigmaWhenSufficientSamples() {
        AccountEntity account = createAccount("measured-account", AccountStatus.api_blocked, 1, Instant.now());

        // Given 5 observed recovery duration samples: [20.0, 30.0, 40.0, 50.0, 60.0]
        // Median = 40.0, Mean = 40.0, Variance = ((20^2 + 10^2 + 0 + 10^2 + 20^2) / 4) = 1000/4 = 250.0, stdDev = sqrt(250) ~= 15.811
        // targetCooldown = round(40.0 + 1.0 * 15.811) = 56 minutes
        List<DefectJournalEntity> samples = List.of(
                new DefectJournalEntity(projectId, null, null, "LOW", "ACCOUNT_HEALTH", account.getName(), "ACCOUNT_RECOVERY_DURATION", "sample 1", 20.0),
                new DefectJournalEntity(projectId, null, null, "LOW", "ACCOUNT_HEALTH", account.getName(), "ACCOUNT_RECOVERY_DURATION", "sample 2", 30.0),
                new DefectJournalEntity(projectId, null, null, "LOW", "ACCOUNT_HEALTH", account.getName(), "ACCOUNT_RECOVERY_DURATION", "sample 3", 40.0),
                new DefectJournalEntity(projectId, null, null, "LOW", "ACCOUNT_HEALTH", account.getName(), "ACCOUNT_RECOVERY_DURATION", "sample 4", 50.0),
                new DefectJournalEntity(projectId, null, null, "LOW", "ACCOUNT_HEALTH", account.getName(), "ACCOUNT_RECOVERY_DURATION", "sample 5", 60.0)
        );

        when(defectJournalRepository.findBySourceComponentAndDefectTypeOrderByCreatedAtDesc(
                account.getName(), "ACCOUNT_RECOVERY_DURATION")).thenReturn(samples);

        long cooldown = service.computeCooldownMinutes(account);

        assertEquals(56L, cooldown,
                "Falsification: data-driven cooldown did not match empirical median + z*sigma formula!");
    }

    @Test
    @DisplayName("Goldman D010: estimateReplenishmentPeriod falls back to 24h prior without cross-account pollution")
    void falsifyGoldmanReliabilityChain_estimateReplenishmentPeriodAvoidsCrossAccountPollution() {
        AccountEntity account = createAccount("new-account", AccountStatus.daily_limited, 1, Instant.now());

        // Zero observed samples for this specific account
        when(defectJournalRepository.findBySourceComponentAndDefectTypeOrderByCreatedAtDesc(
                account.getName(), AccountHealthService.BUDGET_RECOVERY_DEFECT_TYPE))
                .thenReturn(Collections.emptyList());

        // Replenishment period estimate MUST be default prior 24h
        Duration period = service.estimateReplenishmentPeriod(account);
        assertEquals(Duration.ofHours(24), period,
                "Falsification: uninformative prior did not return default 24h replenishment period!");

        // And pure function calculateNextPeriodBoundary works reliably across boundary
        Instant anchor = Instant.parse("2026-10-06T00:00:00Z");
        Instant current = Instant.parse("2026-10-06T15:30:00Z");
        Instant nextBoundary = AccountHealthService.calculateNextPeriodBoundary(anchor, current, period);

        assertEquals(Instant.parse("2026-10-07T00:00:00Z"), nextBoundary,
                "Falsification: calculateNextPeriodBoundary produced wrong next replenishment boundary!");
    }
}
