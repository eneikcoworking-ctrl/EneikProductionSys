package com.eneik.production.models.persistence;

import com.eneik.production.kaizen.repository.DefectJournalRepository;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.services.accounts.AccountHealthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Screen for account availability conjunction and explicit refusal naming
 * (LUDWIG_WITTGENSTEIN_01_FACT_STATE_TABLE / D002 Invalid state,
 *  NUEL_BELNAP_03_TRUTH_STATUS_TABLE / D012 Policy contradiction).
 *
 * Proof obligations:
 * 1. Availability is a conjunction of status == idle AND enabled == true AND apiKey != null.
 * 2. An idle account with enabled == false is unavailable, and its refusal explicitly names "enabled == false".
 * 3. An enabled account with non-idle status is unavailable, and its refusal explicitly names the status.
 * 4. An account violating multiple conjuncts explicitly names every failed condition in a single reading.
 * 5. A decommissioned account is non-operational and reports "status == decommissioned".
 * 6. Disabled accounts in resting statuses (api_blocked) have cooldowns evaluated and reset to idle by AccountHealthService.
 */
class AccountStatusConjunctionTest {

    @Test
    @DisplayName("Account is available if and only if enabled == true, status == idle, and apiKey is present")
    void accountIsAvailableOnlyWhenAllConjunctsHold() {
        AccountEntity account = new AccountEntity();
        account.setStatus(AccountStatus.idle);
        account.setEnabled(true);
        account.setApiKey("test-api-key");

        assertThat(account.isAvailable()).isTrue();
        assertThat(account.isOperational()).isTrue();
        assertThat(account.getUnavailabilityReason()).isNull();
        assertThat(account.getAvailabilitySummary()).isEqualTo("available");
    }

    @Test
    @DisplayName("Idle account with enabled == false is unavailable and explicitly names enabled == false")
    void idleAccountWithDisabledFlagExplicitlyNamesCondition() {
        AccountEntity account = new AccountEntity();
        account.setStatus(AccountStatus.idle);
        account.setEnabled(false);
        account.setApiKey("valid-key");

        assertThat(account.isAvailable()).isFalse();
        assertThat(account.isOperational()).isTrue();
        assertThat(account.getUnavailabilityReason()).isEqualTo("enabled == false");
        assertThat(account.getAvailabilitySummary()).isEqualTo("enabled == false");
    }

    @Test
    @DisplayName("Enabled account with non-idle status explicitly names status in unavailability reason")
    void nonIdleAccountExplicitlyNamesStatus() {
        AccountEntity account = new AccountEntity();
        account.setEnabled(true);
        account.setApiKey("valid-key");

        account.setStatus(AccountStatus.busy);
        assertThat(account.isAvailable()).isFalse();
        assertThat(account.getUnavailabilityReason()).isEqualTo("status == busy");

        account.setStatus(AccountStatus.daily_limited);
        assertThat(account.isAvailable()).isFalse();
        assertThat(account.getUnavailabilityReason()).isEqualTo("status == daily_limited");

        account.setStatus(AccountStatus.api_blocked);
        assertThat(account.isAvailable()).isFalse();
        assertThat(account.getUnavailabilityReason()).isEqualTo("status == api_blocked");

        account.setStatus(AccountStatus.offline);
        assertThat(account.isAvailable()).isFalse();
        assertThat(account.getUnavailabilityReason()).isEqualTo("status == offline");
    }

    @Test
    @DisplayName("Account violating multiple conjuncts names each failed conjunct in one reading")
    void multipleViolatedConjunctsAreExplicitlyNamed() {
        AccountEntity account = new AccountEntity();
        account.setStatus(AccountStatus.api_blocked);
        account.setEnabled(false);
        account.setApiKey(null);

        assertThat(account.isAvailable()).isFalse();
        assertThat(account.getUnavailabilityReason())
                .contains("enabled == false")
                .contains("status == api_blocked")
                .contains("apiKey == missing");
    }

    @Test
    @DisplayName("Decommissioned account reports non-operational and status == decommissioned")
    void decommissionedAccountReportsNonOperational() {
        AccountEntity account = new AccountEntity();
        account.setStatus(AccountStatus.decommissioned);

        assertThat(account.isOperational()).isFalse();
        assertThat(account.isAvailable()).isFalse();
        assertThat(account.getUnavailabilityReason()).isEqualTo("status == decommissioned");
    }

    @Test
    @DisplayName("Missing or blank API key fails availability conjunction even when idle and enabled")
    void missingApiKeyFailsAvailabilityConjunction() {
        AccountEntity account = new AccountEntity();
        account.setStatus(AccountStatus.idle);
        account.setEnabled(true);
        account.setApiKey("   ");

        assertThat(account.isAvailable()).isFalse();
        assertThat(account.getUnavailabilityReason()).isEqualTo("apiKey == missing");
    }

    @Test
    @DisplayName("AccountHealthService normalizes resting disabled account status to idle after cooldown")
    void recoverEligibleAccountsNormalizesDisabledRestingAccount() {
        AccountRepository accountRepo = mock(AccountRepository.class);
        DefectJournalRepository defectRepo = mock(DefectJournalRepository.class);

        when(accountRepo.findByStatusAndEnabledTrue(any())).thenReturn(Collections.emptyList());

        AccountEntity disabledBlocked = new AccountEntity();
        disabledBlocked.setId(UUID.randomUUID());
        disabledBlocked.setName("disabled-blocked-account");
        disabledBlocked.setStatus(AccountStatus.api_blocked);
        disabledBlocked.setEnabled(false);
        Instant past = Instant.now().minus(Duration.ofHours(5));
        disabledBlocked.setStatusChangedAt(past);

        when(accountRepo.findByEnabledFalseAndStatusNot(AccountStatus.decommissioned))
                .thenReturn(List.of(disabledBlocked));
        when(accountRepo.resetSingleAccountFromApiBlocked(disabledBlocked.getId())).thenReturn(1);

        AccountHealthService healthService = new AccountHealthService(
                accountRepo,
                defectRepo,
                mock(com.eneik.production.repositories.AccountRoleSuccessStatsRepository.class),
                mock(com.eneik.production.services.lever.LeverPromotionService.class),
                mock(com.eneik.production.repositories.JulesSessionRepository.class)
        );
        ReflectionTestUtils.setField(healthService, "disabledReviewThresholdHours", 24);

        int recovered = healthService.recoverEligibleAccounts();

        verify(accountRepo).resetSingleAccountFromApiBlocked(disabledBlocked.getId());
    }
}
