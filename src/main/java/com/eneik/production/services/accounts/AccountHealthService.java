package com.eneik.production.services.accounts;

import com.eneik.production.kaizen.model.DefectJournalEntity;
import com.eneik.production.kaizen.repository.DefectJournalRepository;
import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.repositories.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Sole owner of a Jules account's HEALTH state (idle / api_blocked / daily_limited) - 2026-08-01, closing
 * the charter pattern #10 violation (single choke point for a shared invariant) found live: before this,
 * AccountEntity.status was written from JulesDispatchService (inline, mid-dispatch), ContinuousOrchestrationService
 * (the recovery scheduler), and two raw bulk/native AccountRepository queries that silently skipped
 * statusChangedAt entirely - corrupting any time-based backoff computed from it. Deliberately does NOT own
 * `busy` (ClaimService's legitimate concern - occupancy, not health) or `offline`/`decommissioned`
 * (administrative, human-driven via AccountController).
 *
 * The recovery cooldown is data-driven, not an arbitrary formula: every real recovery (api_blocked -> a
 * genuinely successful dispatch) is one observed sample of "how long did this block actually last" -
 * recorded via the existing DefectJournalEntity ledger (reused, not a new parallel store). Once enough
 * samples exist (per-account, falling back to the factory-wide pool when an account is new), the next
 * cooldown is `median(pastDurations) + z * stdDev(pastDurations)` - the same z*sigma safety-margin idiom
 * already used by ConstraintIdentificationService's buffer sizing this same day, not a new ad hoc number.
 * Before enough samples accumulate, an exponential-backoff prior (30/60/120/240min, capped) is used instead -
 * this is honest Bayesian-style behavior: trust the data once there is enough of it, not before.
 */
@Service
public class AccountHealthService {

    private static final Logger log = LoggerFactory.getLogger(AccountHealthService.class);

    public enum DispatchOutcome { SUCCESS, DAILY_LIMIT, PRECONDITION_BLOCKED }

    private static final String RECOVERY_DURATION_DEFECT_TYPE = "ACCOUNT_RECOVERY_DURATION";
    private static final String PRECONDITION_DEFECT_TYPE = "API_PRECONDITION_BLOCKED";
    private static final String DAILY_LIMIT_DEFECT_TYPE = "DAILY_LIMIT";
    private static final String HEALTH_CATEGORY = "ACCOUNT_HEALTH";
    private static final int POOLED_SAMPLE_LIMIT = 50;

    private final AccountRepository accountRepository;
    private final DefectJournalRepository defectJournalRepository;

    @Value("${jules.blocked-account-recovery-cooldown-minutes:30}")
    private int baseCooldownMinutes;

    @Value("${jules.blocked-account-recovery-max-cooldown-minutes:480}")
    private int maxCooldownMinutes;

    @Value("${jules.account-recovery-min-samples-for-data-driven-backoff:5}")
    private int minSamplesForDataDriven;

    @Value("${jules.account-recovery-z-factor:1.0}")
    private double zFactor;

    public AccountHealthService(AccountRepository accountRepository, DefectJournalRepository defectJournalRepository) {
        this.accountRepository = accountRepository;
        this.defectJournalRepository = defectJournalRepository;
    }

    /**
     * Called by JulesDispatchService with the outcome of ONE real dispatch attempt - the only place this
     * class learns about account health. Never called with a raw AccountEntity to mutate directly.
     */
    @Transactional
    public void reportDispatchOutcome(UUID accountId, UUID projectId, DispatchOutcome outcome, String rawReason) {
        if (accountId == null) return;
        AccountEntity account = accountRepository.findById(accountId).orElse(null);
        if (account == null) return;

        switch (outcome) {
            case SUCCESS -> {
                boolean wasBlocked = account.getStatus() == AccountStatus.api_blocked;
                Instant blockedSince = account.getStatusChangedAt();
                if (wasBlocked && blockedSince != null) {
                    long durationMinutes = Duration.between(blockedSince, Instant.now()).toMinutes();
                    defectJournalRepository.save(new DefectJournalEntity(
                            projectId, null, null, "LOW", HEALTH_CATEGORY, account.getName(),
                            RECOVERY_DURATION_DEFECT_TYPE,
                            "Account '" + account.getName() + "' recovered from api_blocked after " + durationMinutes + " minute(s)",
                            (double) durationMinutes));
                }
                account.setSessionsDispatchedToday(account.getSessionsDispatchedToday() + 1);
                account.setConsecutiveApiBlockCount(0);
                accountRepository.save(account);
            }
            case DAILY_LIMIT -> {
                account.setStatus(AccountStatus.daily_limited);
                accountRepository.save(account);
                defectJournalRepository.save(new DefectJournalEntity(
                        projectId, null, null, "MEDIUM", HEALTH_CATEGORY, account.getName(),
                        DAILY_LIMIT_DEFECT_TYPE, rawReason == null ? "" : rawReason, null));
            }
            case PRECONDITION_BLOCKED -> {
                account.setStatus(AccountStatus.api_blocked);
                account.setConsecutiveApiBlockCount(account.getConsecutiveApiBlockCount() + 1);
                accountRepository.save(account);
                defectJournalRepository.save(new DefectJournalEntity(
                        projectId, null, null, "HIGH", HEALTH_CATEGORY, account.getName(),
                        PRECONDITION_DEFECT_TYPE, rawReason == null ? "" : rawReason,
                        (double) account.getConsecutiveApiBlockCount()));
            }
        }
    }

    /**
     * Periodic recovery sweep - replaces the fixed-cooldown logic that used to live in
     * ContinuousOrchestrationService. Each account's own cooldown is computed independently.
     */
    @Transactional
    public int recoverEligibleAccounts() {
        List<AccountEntity> blocked = accountRepository.findByStatusAndEnabledTrue(AccountStatus.api_blocked);
        Instant now = Instant.now();
        int recovered = 0;
        for (AccountEntity account : blocked) {
            Instant changedAt = account.getStatusChangedAt() != null ? account.getStatusChangedAt() : now;
            long cooldownMinutes = computeCooldownMinutes(account);
            if (changedAt.isBefore(now.minus(Duration.ofMinutes(cooldownMinutes)))) {
                if (accountRepository.resetSingleAccountFromApiBlocked(account.getId()) > 0) {
                    recovered++;
                    log.info("AccountHealthService: reset account '{}' from api_blocked to idle after a {}-minute cooldown (consecutive block count was {})",
                            account.getName(), cooldownMinutes, account.getConsecutiveApiBlockCount());
                }
            }
        }
        return recovered;
    }

    /**
     * Data-driven cooldown: median + z*stdDev of this account's own observed recovery durations, falling
     * back to the factory-wide pool when this account has too few of its own, falling back to the
     * exponential-backoff prior when even the pool is too small to trust yet.
     */
    private long computeCooldownMinutes(AccountEntity account) {
        List<Double> samples = observedDurations(
                defectJournalRepository.findBySourceComponentAndDefectTypeOrderByCreatedAtDesc(account.getName(), RECOVERY_DURATION_DEFECT_TYPE));

        if (samples.size() < minSamplesForDataDriven) {
            List<Double> pooled = observedDurations(
                    defectJournalRepository.findByDefectTypeOrderByCreatedAtDesc(RECOVERY_DURATION_DEFECT_TYPE))
                    .stream().limit(POOLED_SAMPLE_LIMIT).toList();
            if (pooled.size() < minSamplesForDataDriven) {
                int doublings = Math.min(Math.max(account.getConsecutiveApiBlockCount() - 1, 0), 20);
                return Math.min((long) baseCooldownMinutes * (1L << doublings), maxCooldownMinutes);
            }
            samples = pooled;
        }

        double median = median(samples);
        double stdDev = stdDev(samples, median);
        long cooldown = Math.round(median + zFactor * stdDev);
        return Math.max(baseCooldownMinutes, Math.min(cooldown, maxCooldownMinutes));
    }

    /** Thin wrapper so every scheduled account-status mutation goes through this one service, not the repository directly. */
    @Transactional
    public int resetDailyLimitedAccounts() {
        return accountRepository.resetDailyLimitedAccounts(Instant.now());
    }

    /** Thin wrapper - see resetDailyLimitedAccounts(). */
    @Transactional
    public int reclassifyPreconditionDailyLimitedAccounts() {
        return accountRepository.reclassifyPreconditionDailyLimitedAccounts();
    }

    private List<Double> observedDurations(List<DefectJournalEntity> entries) {
        return entries.stream().map(DefectJournalEntity::getMetricValue).filter(Objects::nonNull).toList();
    }

    static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        if (n == 0) return 0.0;
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    static double stdDev(List<Double> values, double mean) {
        if (values.size() < 2) return 0.0;
        double sumSq = values.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum();
        return Math.sqrt(sumSq / (values.size() - 1));
    }
}
