package com.eneik.production.services.accounts;

import com.eneik.production.kaizen.model.DefectJournalEntity;
import com.eneik.production.kaizen.repository.DefectJournalRepository;
import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountRoleSuccessStatsEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.AccountRoleSuccessStatsRepository;
import com.eneik.production.services.lever.LeverAgreement;
import com.eneik.production.services.lever.LeverPromotionService;
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
    private final AccountRoleSuccessStatsRepository accountRoleSuccessStatsRepository;
    private final LeverPromotionService leverPromotionService;

    public static final String F2_ACCOUNT_ROLE_SUCCESS_PROBABILITY = "F2_ACCOUNT_ROLE_SUCCESS_PROBABILITY";

    @Value("${jules.blocked-account-recovery-cooldown-minutes:30}")
    private int baseCooldownMinutes;

    @Value("${jules.blocked-account-recovery-max-cooldown-minutes:480}")
    private int maxCooldownMinutes;

    @Value("${jules.account-recovery-min-samples-for-data-driven-backoff:5}")
    private int minSamplesForDataDriven;

    @Value("${jules.account-recovery-z-factor:1.0}")
    private double zFactor;

    // 2026-08-05, fix for the live incident where one malformed request (a single oversized prompt) blocked
    // an entire 15-slot-capacity account: PRECONDITION_BLOCKED used to set the whole account to api_blocked
    // on the very first occurrence, with no way to distinguish "this one request was malformed" from "this
    // account itself is broken" (revoked auth, corrupted config - something that would fail on every future
    // request too). Below this many CONSECUTIVE precondition failures (reset to 0 by any real SUCCESS), the
    // account keeps its other concurrent capacity; only a repeat failure - real evidence the problem isn't
    // request-specific - escalates to a full account block.
    // Slow-start floor for an account with no falsified belief yet (estimatedDailyCapacity == null) -
    // same default the capacity query itself falls back to, so a never-tested account's first probe
    // starts from the same conservative point the rest of the system already assumes.
    @Value("${jules.max-daily-sessions-per-account:15}")
    private int defaultDailyCapacity;

    // Popperian probe step (BARCAN-TAG-06 philosopher #1): how far past the current, not-yet-refuted
    // ceiling to conjecture on each real success that reaches it. Deliberately additive, not multiplicative
    // - a bold jump risks a real Jules rejection on the very next dispatch for no informational gain, a
    // small step keeps testing cheap.
    @Value("${jules.daily-capacity-probe-step:5}")
    private int dailyCapacityProbeStep;

    // Falsification backoff factor: on a real DAILY_LIMIT rejection, the new belief is this fraction of the
    // actual observed failure point (today's real dispatched count when Jules said no) - a genuine
    // Bayesian-style update from real evidence, not a return to the old unverified constant.
    @Value("${jules.daily-capacity-backoff-factor:0.7}")
    private double dailyCapacityBackoffFactor;

    @Value("${jules.precondition-block-escalation-threshold:2}")
    private int preconditionBlockEscalationThreshold;

    @Value("${jules.account-recovery-full-jitter:true}")
    private boolean fullJitter = true;

    @Value("${jules.offline-account-relaxation-minutes:15}")
    private int offlineRelaxationMinutes = 15;

    private java.util.Random random = new java.util.Random();

    public AccountHealthService(AccountRepository accountRepository, DefectJournalRepository defectJournalRepository,
                                 AccountRoleSuccessStatsRepository accountRoleSuccessStatsRepository,
                                 LeverPromotionService leverPromotionService) {
        this.accountRepository = accountRepository;
        this.defectJournalRepository = defectJournalRepository;
        this.accountRoleSuccessStatsRepository = accountRoleSuccessStatsRepository;
        this.leverPromotionService = leverPromotionService;
    }

    /**
     * Called by JulesDispatchService with the outcome of ONE real dispatch attempt - the only place this
     * class learns about account health. Never called with a raw AccountEntity to mutate directly.
     */
    @Transactional
    public void reportDispatchOutcome(UUID accountId, UUID projectId, DispatchOutcome outcome, String rawReason) {
        reportDispatchOutcome(accountId, projectId, outcome, rawReason, null);
    }

    /**
     * 2026-08-08 (ML-update patch, Phase 4 / lever F2_ACCOUNT_ROLE_SUCCESS_PROBABILITY): same as the 4-arg
     * overload, plus a Beta-Bernoulli update of THIS (account, role) pair's own success-probability
     * posterior - a separate question from invariant #15's estimatedDailyCapacity (how many dispatches fit)
     * or from AccountStatus (is the account healthy at all). Constructive-empiricist discipline (van
     * Fraassen, BARCAN-TAG-04 philosopher 4): models only the observed frequency of real outcomes, never
     * posits a metaphysically "true" success rate. roleTag==null (unknown role context) skips the update
     * entirely - Jeffrey conditionalization (BARCAN-TAG-04 philosopher 2) only applies to real evidence.
     */
    @Transactional
    public void reportDispatchOutcome(UUID accountId, UUID projectId, DispatchOutcome outcome, String rawReason, String roleTag) {
        if (accountId == null) return;
        if (roleTag != null && (outcome == DispatchOutcome.SUCCESS || outcome == DispatchOutcome.PRECONDITION_BLOCKED)) {
            updateRoleSuccessStats(accountId, roleTag, outcome);
        }
        reportDispatchOutcomeCore(accountId, projectId, outcome, rawReason);
    }

    /**
     * Records this pair's PRIOR (pre-update) predicted probability against the real outcome, then updates
     * the posterior. Ordering matters: predicting AFTER updating on the very same evidence would be
     * circular (Goodman's "grue" caution, BARCAN-TAG-00 philosopher 6, applied here as "don't let the
     * newest point also validate itself") - the observation must reflect what the belief predicted BEFORE
     * seeing this outcome, not after.
     */
    private void updateRoleSuccessStats(UUID accountId, String roleTag, DispatchOutcome outcome) {
        AccountRoleSuccessStatsEntity stats = accountRoleSuccessStatsRepository
                .findByAccountIdAndRoleTag(accountId, roleTag)
                .orElseGet(() -> {
                    AccountRoleSuccessStatsEntity fresh = new AccountRoleSuccessStatsEntity();
                    fresh.setAccountId(accountId);
                    fresh.setRoleTag(roleTag);
                    return fresh;
                });

        double priorProbability = stats.getAlpha() / (stats.getAlpha() + stats.getBeta());
        boolean predictedSuccess = priorProbability >= 0.5;
        boolean actualSuccess = outcome == DispatchOutcome.SUCCESS;
        LeverAgreement agreement = predictedSuccess == actualSuccess ? LeverAgreement.TRUE : LeverAgreement.FALSE;
        leverPromotionService.recordObservation(F2_ACCOUNT_ROLE_SUCCESS_PROBABILITY,
                accountId + ":" + roleTag,
                "no_prediction",
                predictedSuccess ? "predict_success" : "predict_failure",
                agreement,
                actualSuccess ? "success" : "failure");

        if (actualSuccess) {
            stats.setAlpha(stats.getAlpha() + 1);
        } else {
            stats.setBeta(stats.getBeta() + 1);
        }
        stats.setUpdatedAt(Instant.now());
        accountRoleSuccessStatsRepository.save(stats);
    }

    private void reportDispatchOutcomeCore(UUID accountId, UUID projectId, DispatchOutcome outcome, String rawReason) {
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
                int newDailyCount = account.getSessionsDispatchedToday() + 1;
                account.setSessionsDispatchedToday(newDailyCount);
                // Leaky Bucket decay: decrement consecutiveApiBlockCount by 1 on success instead of hard reset
                account.setConsecutiveApiBlockCount(Math.max(0, account.getConsecutiveApiBlockCount() - 1));
                // Engineering invariant #15: this real success just reached (or passed) the current,
                // not-yet-refuted ceiling belief - a bold conjecture that survived a severe test (Popper),
                // so the belief revises upward. A success well below the current ceiling tests nothing new
                // and leaves the belief alone.
                int currentCeiling = account.getEstimatedDailyCapacity() != null
                        ? account.getEstimatedDailyCapacity() : defaultDailyCapacity;
                if (newDailyCount >= currentCeiling) {
                    account.setEstimatedDailyCapacity(currentCeiling + dailyCapacityProbeStep);
                    log.info("[ACCOUNT-CAPACITY] Account '{}' probed past its believed daily ceiling ({}) with a real "
                                    + "success at count={} - revised estimate upward to {}.",
                            account.getName(), currentCeiling, newDailyCount, account.getEstimatedDailyCapacity());
                }
                accountRepository.save(account);
            }
            case DAILY_LIMIT -> {
                account.setStatus(AccountStatus.daily_limited);
                // Engineering invariant #15: a real Jules rejection is the only event that falsifies the
                // daily-capacity belief - revise down from the ACTUAL observed failure point (today's real
                // dispatched count), never back to the old unverified constant. Backoff factor leaves
                // margin so the very next probe doesn't immediately re-trigger the same rejection.
                int observedFailurePoint = account.getSessionsDispatchedToday();
                int revisedCeiling = Math.max(1, (int) Math.round(observedFailurePoint * dailyCapacityBackoffFactor));
                Integer priorEstimate = account.getEstimatedDailyCapacity();
                account.setEstimatedDailyCapacity(revisedCeiling);
                log.warn("[ACCOUNT-CAPACITY] Account '{}' real daily-limit rejection from Jules at count={} - "
                                + "revised estimate from {} down to {}.",
                        account.getName(), observedFailurePoint,
                        priorEstimate != null ? priorEstimate : defaultDailyCapacity, revisedCeiling);
                accountRepository.save(account);
                defectJournalRepository.save(new DefectJournalEntity(
                        projectId, null, null, "MEDIUM", HEALTH_CATEGORY, account.getName(),
                        DAILY_LIMIT_DEFECT_TYPE, rawReason == null ? "" : rawReason, null));
            }
            case PRECONDITION_BLOCKED -> {
                int newCount = account.getConsecutiveApiBlockCount() + 1;
                account.setConsecutiveApiBlockCount(newCount);
                boolean escalate = newCount >= preconditionBlockEscalationThreshold;
                if (escalate) {
                    account.setStatus(AccountStatus.api_blocked);
                }
                accountRepository.save(account);
                defectJournalRepository.save(new DefectJournalEntity(
                        projectId, null, null, escalate ? "HIGH" : "MEDIUM", HEALTH_CATEGORY, account.getName(),
                        PRECONDITION_DEFECT_TYPE, rawReason == null ? "" : rawReason,
                        (double) newCount));
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
            Instant changedAt = account.getStatusChangedAt();
            if (changedAt == null) {
                // Anti-Zeno: if statusChangedAt is null, fall back to lastHeartbeat / createdAt
                // or a safe upper bound so the account is not permanently stuck.
                changedAt = account.getLastHeartbeat() != null ? account.getLastHeartbeat()
                        : (account.getCreatedAt() != null ? account.getCreatedAt() : now.minus(Duration.ofMinutes(maxCooldownMinutes)));
            }
            long cooldownMinutes = computeCooldownMinutes(account);
            if (changedAt.isBefore(now.minus(Duration.ofMinutes(cooldownMinutes)))) {
                if (accountRepository.resetSingleAccountFromApiBlocked(account.getId()) > 0) {
                    recovered++;
                    log.info("AccountHealthService: reset account '{}' from api_blocked to idle after a {}-minute cooldown (consecutive block count was {})",
                            account.getName(), cooldownMinutes, account.getConsecutiveApiBlockCount());
                }
            }
        }

        // Liveness Invariant: auto-relax offline accounts if they have recent heartbeat activity
        List<AccountEntity> offline = accountRepository.findByStatusAndEnabledTrue(AccountStatus.offline);
        for (AccountEntity account : offline) {
            Instant heartbeat = account.getLastHeartbeat();
            if (heartbeat != null && Duration.between(heartbeat, now).toMinutes() < offlineRelaxationMinutes) {
                account.setStatus(AccountStatus.idle);
                accountRepository.save(account);
                recovered++;
                log.info("AccountHealthService: auto-relaxed account '{}' from offline to idle (recent heartbeat {}m ago)",
                        account.getName(), Duration.between(heartbeat, now).toMinutes());
            }
        }

        return recovered;
    }

    /**
     * Data-driven cooldown with Full Jitter: median + z*stdDev of this account's own observed recovery durations,
     * falling back to the factory-wide pool when this account has too few of its own, falling back to the
     * exponential-backoff prior when even the pool is too small to trust yet.
     */
    private long computeCooldownMinutes(AccountEntity account) {
        long targetCooldown;
        List<Double> samples = observedDurations(
                defectJournalRepository.findBySourceComponentAndDefectTypeOrderByCreatedAtDesc(account.getName(), RECOVERY_DURATION_DEFECT_TYPE));

        if (samples.size() < minSamplesForDataDriven) {
            List<Double> pooled = observedDurations(
                    defectJournalRepository.findByDefectTypeOrderByCreatedAtDesc(RECOVERY_DURATION_DEFECT_TYPE))
                    .stream().limit(POOLED_SAMPLE_LIMIT).toList();
            if (pooled.size() < minSamplesForDataDriven) {
                int doublings = Math.min(Math.max(account.getConsecutiveApiBlockCount() - 1, 0), 20);
                targetCooldown = Math.min((long) baseCooldownMinutes * (1L << doublings), maxCooldownMinutes);
            } else {
                double median = median(pooled);
                double stdDev = stdDev(pooled, median);
                targetCooldown = Math.max(baseCooldownMinutes, Math.min(Math.round(median + zFactor * stdDev), maxCooldownMinutes));
            }
        } else {
            double median = median(samples);
            double stdDev = stdDev(samples, median);
            targetCooldown = Math.max(baseCooldownMinutes, Math.min(Math.round(median + zFactor * stdDev), maxCooldownMinutes));
        }

        if (!fullJitter || targetCooldown <= baseCooldownMinutes) {
            return targetCooldown;
        }
        // Full Jitter: Uniform(baseCooldownMinutes, targetCooldown)
        long range = targetCooldown - baseCooldownMinutes + 1;
        long jitter = range > 0 ? (Math.abs(random.nextLong()) % range) : 0;
        return baseCooldownMinutes + jitter;
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
