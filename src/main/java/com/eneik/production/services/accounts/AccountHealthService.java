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
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * REQUEST_REJECTED added 2026-08-29 (§10). The vocabulary had three words and the world produced a
     * fourth: a refusal caused by the content of the factory's own request. Having no name for it, the
     * regulator filed it under the nearest one it had - PRECONDITION_BLOCKED, a statement about the
     * ACCOUNT - and systematically blamed a party that had no part in the fault. Ashby: the variety of the
     * response must cover the variety of the disturbance, or classification becomes libel on whatever
     * object is closest.
     *
     * <p>It is the same distinction §4.2 drew for briefs: UNREACHED is about the factory, REFUSED is about
     * the subject. This is that distinction applied to dispatch.
     */
    public enum DispatchOutcome { SUCCESS, DAILY_LIMIT, CONCURRENT_CAPACITY_EXHAUSTED, PRECONDITION_BLOCKED, REQUEST_REJECTED,
        PRECONDITION_UNSPECIFIED, UNCLASSIFIED }

    private static final String RECOVERY_DURATION_DEFECT_TYPE = "ACCOUNT_RECOVERY_DURATION";
    public static final String BUDGET_RECOVERY_DEFECT_TYPE = "ACCOUNT_BUDGET_RECOVERY";
    private static final String PRECONDITION_DEFECT_TYPE = "API_PRECONDITION_BLOCKED";
    private static final String UNNAMED_REFUSAL_DEFECT_TYPE = "API_REFUSAL_WITHOUT_NAMED_CONDITION";
    private static final String DAILY_LIMIT_DEFECT_TYPE = "DAILY_LIMIT";
    private static final String CONCURRENT_CAPACITY_DEFECT_TYPE = "CONCURRENT_CAPACITY_EXHAUSTED";
    private static final String CONCURRENT_CAPACITY_EXPANDED_TYPE = "CONCURRENT_CAPACITY_EXPANDED";
    private static final String DAILY_CAPACITY_EXPANDED_TYPE = "DAILY_CAPACITY_EXPANDED";
    private static final String HEALTH_CATEGORY = "ACCOUNT_HEALTH";
    private static final int POOLED_SAMPLE_LIMIT = 50;

    private final AccountRepository accountRepository;
    private final DefectJournalRepository defectJournalRepository;
    private final AccountRoleSuccessStatsRepository accountRoleSuccessStatsRepository;
    private final LeverPromotionService leverPromotionService;
    private final com.eneik.production.repositories.JulesSessionRepository julesSessionRepository;
    private final Map<UUID, Double> activeMonopolies = new ConcurrentHashMap<>();

    public static final String F2_ACCOUNT_ROLE_SUCCESS_PROBABILITY = "F2_ACCOUNT_ROLE_SUCCESS_PROBABILITY";

    @Value("${jules.blocked-account-recovery-cooldown-minutes:30}")
    private int baseCooldownMinutes;

    @Value("${jules.blocked-account-recovery-max-cooldown-minutes:480}")
    private int maxCooldownMinutes;

    @Value("${jules.account-recovery-min-samples-for-data-driven-backoff:5}")
    private int minSamplesForDataDriven;

    @Value("${jules.account-recovery-z-factor:1.0}")
    private double zFactor;

    @Value("${jules.account-replenishment-default-period-hours:24}")
    private int defaultReplenishmentPeriodHours = 24;

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

    @Value("${jules.concurrent-capacity-probe-step:1}")
    private int concurrentCapacityProbeStep = 1;

    @Value("${jules.concurrent-capacity-backoff-factor:0.7}")
    private double concurrentCapacityBackoffFactor = 0.7;

    @Value("${jules.precondition-block-escalation-threshold:2}")
    private int preconditionBlockEscalationThreshold;

    @Value("${jules.account-recovery-full-jitter:true}")
    private boolean fullJitter = true;

    @Value("${jules.offline-account-relaxation-minutes:15}")
    private int offlineRelaxationMinutes = 15;

    @Value("${jules.disabled-account-review-threshold-hours:24}")
    private int disabledReviewThresholdHours = 24;

    @Value("${jules.account-monopoly-window-hours:6}")
    private int monopolyWindowHours = 6;

    @Value("${jules.account-monopoly-min-sessions:10}")
    private int monopolyMinSessions = 10;

    private java.util.Random random = new java.util.Random();

    public AccountHealthService(AccountRepository accountRepository, DefectJournalRepository defectJournalRepository,
                                 AccountRoleSuccessStatsRepository accountRoleSuccessStatsRepository,
                                 LeverPromotionService leverPromotionService,
                                 com.eneik.production.repositories.JulesSessionRepository julesSessionRepository) {
        this.accountRepository = accountRepository;
        this.defectJournalRepository = defectJournalRepository;
        this.accountRoleSuccessStatsRepository = accountRoleSuccessStatsRepository;
        this.leverPromotionService = leverPromotionService;
        this.julesSessionRepository = julesSessionRepository;
    }

    /**
     * Called by JulesDispatchService with the outcome of ONE real dispatch attempt - the only place this
     * class learns about account health. Never called with a raw AccountEntity to mutate directly.
     */
    @Transactional
    public void reportDispatchOutcome(UUID accountId, UUID projectId, DispatchOutcome outcome, String rawReason) {
        reportDispatchOutcome(accountId, projectId, null, outcome, rawReason, null);
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
        reportDispatchOutcome(accountId, projectId, null, outcome, rawReason, roleTag);
    }

    /**
     * Law 12 (Ground of denial names what it is about) & Law 8 (Variant function progress is observable):
     * Includes the task ID so journal entries and refusal warnings identify the task being dispatched.
     */
    @Transactional
    public void reportDispatchOutcome(UUID accountId, UUID projectId, UUID taskId, DispatchOutcome outcome, String rawReason, String roleTag) {
        if (accountId == null) return;
        if (roleTag != null && (outcome == DispatchOutcome.SUCCESS || outcome == DispatchOutcome.PRECONDITION_BLOCKED)) {
            updateRoleSuccessStats(accountId, roleTag, outcome);
        }
        reportDispatchOutcomeCore(accountId, projectId, taskId, outcome, rawReason);
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

    private void reportDispatchOutcomeCore(UUID accountId, UUID projectId, UUID taskId, DispatchOutcome outcome, String rawReason) {
        if (accountId == null) return;
        AccountEntity account = accountRepository.findById(accountId).orElse(null);
        if (account == null) return;

        String taskPrefix = taskId != null ? "[task=" + taskId + "] " : "";

        switch (outcome) {
            case SUCCESS -> {
                boolean wasBlocked = account.getStatus() == AccountStatus.api_blocked;
                boolean wasDailyLimited = account.getStatus() == AccountStatus.daily_limited;
                Instant blockedSince = account.getStatusChangedAt();
                if ((wasBlocked || wasDailyLimited) && blockedSince != null) {
                    long durationMinutes = Duration.between(blockedSince, Instant.now()).toMinutes();
                    defectJournalRepository.save(new DefectJournalEntity(
                            projectId, null, null, "LOW", "ACCOUNT_RECOVERY", account.getName(),
                            RECOVERY_DURATION_DEFECT_TYPE,
                            taskPrefix + "Account '" + account.getName() + "' recovered from "
                                    + (wasDailyLimited ? "daily_limited" : "api_blocked") + " after " + durationMinutes + " minute(s)",
                            (double) durationMinutes));
                    if (wasDailyLimited || isExternalBudgetExhaustion(account)) {
                        defectJournalRepository.save(new DefectJournalEntity(
                                projectId, null, null, "LOW", "ACCOUNT_RECOVERY", account.getName(),
                                BUDGET_RECOVERY_DEFECT_TYPE,
                                taskPrefix + "Account '" + account.getName() + "' recovered from external budget exhaustion after " + durationMinutes + " minute(s)",
                                (double) durationMinutes));
                    }
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
                // Invariant 15 / Law 14: concurrent capacity upward revision
                int openNow = accountRepository.countOpenSessions(account.getId());
                int concurrentCeiling = concurrentCeilingOf(account);
                if (openNow >= concurrentCeiling) {
                    int revisedConcurrent = concurrentCeiling + concurrentCapacityProbeStep;
                    account.setEstimatedConcurrentCapacity(revisedConcurrent);
                    log.info("[ACCOUNT-CAPACITY] Account '{}' held {} session(s) open at its believed "
                                    + "concurrent ceiling of {} and Jules still accepted - revised upward to {}.",
                            account.getName(), openNow, concurrentCeiling,
                            revisedConcurrent);
                    defectJournalRepository.save(new DefectJournalEntity(
                            projectId, null, null, "INFO", HEALTH_CATEGORY, account.getName(),
                            CONCURRENT_CAPACITY_EXPANDED_TYPE,
                            taskPrefix + "Account '" + account.getName() + "' revised concurrent capacity upward: prior="
                                    + concurrentCeiling + ", revised=" + revisedConcurrent
                                    + ", observed_open=" + openNow,
                            (double) revisedConcurrent));
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
                account.setConsecutiveApiBlockCount(account.getConsecutiveApiBlockCount() + 1);
                log.warn("[ACCOUNT-CAPACITY] Account '{}' real daily-limit rejection from Jules at count={} - "
                                + "revised estimate from {} down to {}.",
                        account.getName(), observedFailurePoint,
                        priorEstimate != null ? priorEstimate : defaultDailyCapacity, revisedCeiling);
                accountRepository.save(account);
                defectJournalRepository.save(new DefectJournalEntity(
                        projectId, null, null, "MEDIUM", HEALTH_CATEGORY, account.getName(),
                        DAILY_LIMIT_DEFECT_TYPE, taskPrefix + (rawReason == null ? "" : rawReason), (double) revisedCeiling));
            }
            case CONCURRENT_CAPACITY_EXHAUSTED -> {
                // Law 14 (Popper/Gerdenfors): external refusal of concurrent capacity
                // revises estimatedConcurrentCapacity down from the ACTUAL observed point (countOpenSessions),
                // never back to an unverified constant.
                int refusedAt = accountRepository.countOpenSessions(account.getId());
                int priorConcurrent = concurrentCeilingOf(account);
                int calculated = (int) Math.round(refusedAt * concurrentCapacityBackoffFactor);
                int revisedConcurrent = Math.max(1, Math.min(priorConcurrent - 1, calculated));
                if (revisedConcurrent < priorConcurrent) {
                    account.setEstimatedConcurrentCapacity(revisedConcurrent);
                    accountRepository.save(account);
                    log.warn("[ACCOUNT-CAPACITY] Account '{}' was refused a session due to concurrent capacity exhaustion while holding {} open - "
                                    + "revised concurrent estimate from {} down to {}.",
                            account.getName(), refusedAt, priorConcurrent, revisedConcurrent);
                }
                defectJournalRepository.save(new DefectJournalEntity(
                        projectId, null, null, "MEDIUM", HEALTH_CATEGORY, account.getName(),
                        CONCURRENT_CAPACITY_DEFECT_TYPE,
                        taskPrefix + "Account '" + account.getName() + "' concurrent capacity refusal: prior=" + priorConcurrent
                                + ", revised=" + revisedConcurrent + ", observed_open=" + refusedAt
                                + ", detail=" + (rawReason == null ? "" : rawReason),
                        (double) revisedConcurrent));
            }
            case PRECONDITION_UNSPECIFIED, UNCLASSIFIED -> {
                // §12, §14 & Carnap's evidence distinction: the refusal names no condition, so no cause is
                // attributed. estimatedDailyCapacity and estimatedConcurrentCapacity are NOT moved in either
                // direction - a belief is revised by evidence about its subject, and this refusal says
                // nothing about capacity.
                //
                // What IS established is not nothing, and it used to be discarded as if it were. An account
                // refused N times in a row, with no success in between, is an account that only refuses;
                // that is an observation, not an interpretation. Leaving its status `idle` publishes the
                // opposite - "available" - so "we could not tell" was rendered indistinguishable from "all
                // is well", and dispatch kept spending against it. The third outcome of a check must be
                // visible in the result, never dissolved into the second.
                //
                // So the run is counted and the account steps aside, on the same counter and the same
                // exponential-backoff recovery as a named block. Standing aside is not a verdict about why:
                // it says only that sending again, right now, is a turn of a cycle with no decreasing
                // quantity behind it (law 8).
                int unnamedRefusals = account.getConsecutiveApiBlockCount() + 1;
                account.setConsecutiveApiBlockCount(unnamedRefusals);
                boolean standAside = unnamedRefusals >= preconditionBlockEscalationThreshold;
                if (standAside) {
                    account.setStatus(AccountStatus.api_blocked);
                }
                accountRepository.save(account);
                log.warn("AccountHealthService: Jules refused a session for task {} through account '{}' without naming "
                                + "a condition ({} in a row, no success between). No cause is charged (§12, §14); the "
                                + "account {} while the run stands. Detail: {}",
                        taskId != null ? taskId : "unspecified", account.getName(), unnamedRefusals,
                        standAside ? "steps aside" : "stays in rotation", rawReason);
                defectJournalRepository.save(new DefectJournalEntity(
                        projectId, null, null, standAside ? "HIGH" : "MEDIUM", HEALTH_CATEGORY, account.getName(),
                        UNNAMED_REFUSAL_DEFECT_TYPE,
                        taskPrefix + "Account '" + account.getName() + "' refused " + unnamedRefusals
                                + " time(s) in a row without a named condition; no cause attributed. Detail: "
                                + (rawReason == null ? "" : rawReason),
                        (double) unnamedRefusals));
            }
            case REQUEST_REJECTED -> {
                // Deliberately touches nothing on the account: not its status, not its block counter, not
                // its cooldown. The account did nothing. Recording it at all is what makes the fault
                // findable - a defect with no reader is the shape this plan has caught three times.
                log.warn("AccountHealthService: Jules refused the request itself for task {} while dispatching through "
                                + "account '{}' - this is the factory's own defect and is NOT charged to the "
                                + "account (\u00a710). Detail: {}",
                        taskId != null ? taskId : "unspecified", account.getName(), rawReason);
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
                        PRECONDITION_DEFECT_TYPE, taskPrefix + (rawReason == null ? "" : rawReason),
                        (double) newCount));
            }
        }
    }

    /**
     * Periodic recovery sweep - replaces the fixed-cooldown logic that used to live in
     * ContinuousOrchestrationService. Each account's own cooldown is computed independently,
     * bounded by the replenishment period for external budget refusals (Law 9 / D010).
     */
    @Transactional
    public int recoverEligibleAccounts() {
        return recoverEligibleAccounts(Instant.now());
    }

    @Transactional
    public int recoverEligibleAccounts(Instant now) {
        Instant current = now != null ? now : Instant.now();
        // ACTUAL_OBJECT_REGISTER (D002) / INSTITUTIONAL_FACT_REGISTER (D007):
        // Resolve contradictory state via entity lifecycle with audit record per transition.
        List<AccountEntity> contradictory = accountRepository.findByStatusAndEnabledTrue(AccountStatus.decommissioned);
        if (!contradictory.isEmpty()) {
            for (AccountEntity acc : contradictory) {
                acc.setEnabled(false);
                acc.setStatusChangedAt(current);
                accountRepository.save(acc);
                String desc = String.format("Account '%s' normalized: decommissioned account forced to enabled=false to resolve contradictory state (D002/D012). Rule: ACCOUNT_LIFECYCLE_NORMALIZATION_RULE", acc.getName());
                defectJournalRepository.save(new DefectJournalEntity(
                        null, null, null, "INFO", "INSTITUTIONAL_AUDIT", acc.getName(),
                        "ACCOUNT_LIFECYCLE_NORMALIZATION_RULE", desc, 0.0));
                log.info("Institutional Fact Audit (Normalization): {}", desc);
            }
        }

        List<AccountEntity> blocked = accountRepository.findByStatusAndEnabledTrue(AccountStatus.api_blocked);
        int recovered = 0;
        for (AccountEntity account : blocked) {
            Instant changedAt = account.getStatusChangedAt();
            if (changedAt == null) {
                // Anti-Zeno: if statusChangedAt is null, fall back to lastHeartbeat / createdAt
                // or a safe upper bound so the account is not permanently stuck.
                changedAt = account.getLastHeartbeat() != null ? account.getLastHeartbeat()
                        : (account.getCreatedAt() != null ? account.getCreatedAt() : current.minus(Duration.ofMinutes(maxCooldownMinutes)));
            }
            Instant nextProbe = computeNextProbeInstant(account, current);
            if (!current.isBefore(nextProbe)) {
                if (accountRepository.resetSingleAccountFromApiBlocked(account.getId()) > 0) {
                    recovered++;
                    long effectiveCooldown = Duration.between(changedAt, current).toMinutes();
                    log.info("AccountHealthService: reset account '{}' from api_blocked to idle after a {}-minute cooldown (consecutive block count was {})",
                            account.getName(), effectiveCooldown, account.getConsecutiveApiBlockCount());
                }
            }
        }

        // Law 9 / Prescription 14 (RELIABILITY_CHAIN / D010):
        // daily_limited accounts are recovered when the next replenishment period begins or cooldown elapses,
        // rather than remaining trapped until an arbitrary midnight cron.
        List<AccountEntity> dailyLimited = accountRepository.findByStatusAndEnabledTrue(AccountStatus.daily_limited);
        for (AccountEntity account : dailyLimited) {
            Instant changedAt = account.getStatusChangedAt();
            if (changedAt == null) {
                changedAt = account.getLastHeartbeat() != null ? account.getLastHeartbeat()
                        : (account.getCreatedAt() != null ? account.getCreatedAt() : current.minus(Duration.ofMinutes(maxCooldownMinutes)));
            }
            Instant nextProbe = computeNextProbeInstant(account, current);
            if (!current.isBefore(nextProbe)) {
                if (accountRepository.resetSingleAccountFromDailyLimited(account.getId(), current) > 0) {
                    recovered++;
                    long effectiveCooldown = Duration.between(changedAt, current).toMinutes();
                    log.info("AccountHealthService: reset account '{}' from daily_limited to idle after a {}-minute cooldown (consecutive block count was {})",
                            account.getName(), effectiveCooldown, account.getConsecutiveApiBlockCount());
                }
            }
        }

        // Liveness Invariant: auto-relax offline accounts if they have recent heartbeat activity
        List<AccountEntity> offline = accountRepository.findByStatusAndEnabledTrue(AccountStatus.offline);
        for (AccountEntity account : offline) {
            Instant heartbeat = account.getLastHeartbeat();
            if (heartbeat != null && Duration.between(heartbeat, current).toMinutes() < offlineRelaxationMinutes) {
                account.setStatus(AccountStatus.idle);
                accountRepository.save(account);
                recovered++;
                log.info("AccountHealthService: auto-relaxed account '{}' from offline to idle (recent heartbeat {}m ago)",
                        account.getName(), Duration.between(heartbeat, current).toMinutes());
            }
        }

        // NUEL_BELNAP_03_TRUTH_STATUS_TABLE (D012):
        // Absorbing state visibility: disabled accounts must not be invisible to the recovery sweep.
        // When recovery candidates are zero, report disabled operational accounts.
        List<AccountEntity> disabledOperational = accountRepository.findByEnabledFalseAndStatusNot(AccountStatus.decommissioned);
        if (!disabledOperational.isEmpty()) {
            if (blocked.isEmpty() && dailyLimited.isEmpty() && offline.isEmpty()) {
                log.info("AccountHealthService: zero recovery candidates in pool; {} operational account(s) are currently disabled (enabled=false)",
                        disabledOperational.size());
            }
            for (AccountEntity account : disabledOperational) {
                Instant disabledSince = account.getStatusChangedAt() != null
                        ? account.getStatusChangedAt()
                        : account.getLastHeartbeat();
                if (disabledSince != null && Duration.between(disabledSince, current).toHours() >= disabledReviewThresholdHours) {
                    log.warn("AccountHealthService: account '{}' has been disabled for {}h (since {}); предлагается к возврату в пул оператором",
                            account.getName(), Duration.between(disabledSince, current).toHours(), disabledSince);
                }
            }
        }

        // ALONZO_CHERCH_21_DERIVED_CUTOFF (D010):
        // Inspect single-account monopoly over rolling window against derived cutoff.
        checkAccountMonopoly(current);

        return recovered;
    }

    /**
     * Alonzo Church derived cutoff (ALONZO_CHERCH_21_DERIVED_CUTOFF / D010):
     * Cutoff is derived from the actual observed distribution of dispatches:
     * Fair share under uniform rotation is p0 = 1/N.
     * Dispersion under binomial dispatch across S sessions: sigma = sqrt(p0 * (1 - p0) / S).
     * Derived cutoff is fair share plus 3-sigma statistical deviation: T = p0 + 3.0 * sigma.
     * When there is no variance to distinguish (S < N or N <= 1 or S <= 0), returns declared bound 1.0.
     */
    public static double derivedMonopolyCutoff(int livePoolSize, long totalSessions) {
        if (livePoolSize <= 1 || totalSessions <= 0 || totalSessions < livePoolSize) {
            return 1.0;
        }
        double p0 = 1.0 / livePoolSize;
        double variance = (p0 * (1.0 - p0)) / totalSessions;
        double sigma = Math.sqrt(variance);
        double cutoff = p0 + 3.0 * sigma;
        return Math.min(1.0, Math.max(p0, cutoff));
    }

    public static double derivedMonopolyCutoff(int livePoolSize) {
        return derivedMonopolyCutoff(livePoolSize, 20L);
    }

    void checkAccountMonopoly(Instant now) {
        try {
            long operationalPoolSize = accountRepository.countOperationalAccounts();
            long livePoolSize = operationalPoolSize > 0 ? operationalPoolSize : accountRepository.countLiveAccounts();
            if (livePoolSize <= 1) {
                return;
            }
            Instant windowStart = now.minus(Duration.ofHours(monopolyWindowHours));
            List<Object[]> sessionCounts = julesSessionRepository.countDispatchedSessionsByAccountSince(windowStart);
            if (sessionCounts == null || sessionCounts.isEmpty()) {
                return;
            }
            long totalSessions = 0;
            Map<UUID, Long> countsByAccount = new HashMap<>();
            for (Object[] row : sessionCounts) {
                if (row != null && row.length >= 2 && row[0] instanceof UUID accId && row[1] instanceof Number count) {
                    long c = count.longValue();
                    countsByAccount.put(accId, c);
                    totalSessions += c;
                }
            }
            if (totalSessions < monopolyMinSessions) {
                return;
            }

            double cutoff = derivedMonopolyCutoff((int) livePoolSize, totalSessions);
            Set<UUID> currentMonopolyAccounts = new HashSet<>();
            for (Map.Entry<UUID, Long> entry : countsByAccount.entrySet()) {
                double share = (double) entry.getValue() / totalSessions;
                if (share >= cutoff) {
                    currentMonopolyAccounts.add(entry.getKey());
                    String accountName = accountRepository.findById(entry.getKey())
                            .map(AccountEntity::getName)
                            .orElse(entry.getKey().toString());
                    Double previousShare = activeMonopolies.get(entry.getKey());
                    boolean stateChanged = (previousShare == null) || (Math.abs(previousShare - share) >= 0.05);
                    if (stateChanged) {
                        activeMonopolies.put(entry.getKey(), share);
                        log.warn("AccountHealthService / rotation: monopoly detected on account '{}': carrying {}% of dispatches ({}/{}) over past {}h against live pool of {} accounts (derived cutoff {}%)",
                                accountName, Math.round(share * 100), entry.getValue(), totalSessions,
                                monopolyWindowHours, livePoolSize, Math.round(cutoff * 100));
                        defectJournalRepository.save(new DefectJournalEntity(
                                null, null, null, "HIGH", HEALTH_CATEGORY, accountName,
                                "ACCOUNT_MONOPOLY_CONCENTRATION",
                                "Account '" + accountName + "' monopoly detected: carrying " + Math.round(share * 100) +
                                        "% of dispatches (" + entry.getValue() + "/" + totalSessions + ") against pool of " + livePoolSize,
                                share));
                    } else {
                        log.debug("AccountHealthService: account '{}' monopoly persists with unchanged share ({}%) - skipping duplicate defect record",
                                accountName, Math.round(share * 100));
                    }
                }
            }
            // Clean up resolved monopolies so future recurrences can be recorded fresh
            activeMonopolies.keySet().removeIf(id -> !currentMonopolyAccounts.contains(id));
        } catch (Exception e) {
            log.warn("AccountHealthService: failed to check account monopoly: {}", e.getMessage());
        }
    }

    Map<UUID, Double> getActiveMonopolies() {
        return activeMonopolies;
    }

    /**
     * Data-driven cooldown with Full Jitter: median + z*stdDev of this account's own observed recovery durations,
     * falling back to the factory-wide pool when this account has too few of its own, falling back to the
     * exponential-backoff prior when even the pool is too small to trust yet.
     */
    /** The ceiling the selector applies, read the same way it reads it. */
    private int concurrentCeilingOf(AccountEntity account) {
        if (account.getEstimatedConcurrentCapacity() != null) {
            return account.getEstimatedConcurrentCapacity();
        }
        return account.getMaxConcurrentSessions() != null ? account.getMaxConcurrentSessions() : 3;
    }

    long computeCooldownMinutes(AccountEntity account) {
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

    /**
     * Law 9 / Prescription 14 (ELVIN_GOLDMAN_01_RELIABILITY_CHAIN / D010):
     * Determines whether an account's current restriction was caused by an external budget exhaustion
     * (daily limit, quota, rate limit 429).
     */
    public boolean isExternalBudgetExhaustion(AccountEntity account) {
        if (account == null) return false;
        if (account.getStatus() == AccountStatus.daily_limited) {
            return true;
        }
        if (account.getName() != null) {
            List<DefectJournalEntity> dailyLimits = defectJournalRepository
                    .findBySourceComponentAndDefectTypeOrderByCreatedAtDesc(account.getName(), DAILY_LIMIT_DEFECT_TYPE);
            if (!dailyLimits.isEmpty()) {
                DefectJournalEntity latestDailyLimit = dailyLimits.get(0);
                Instant changedAt = account.getStatusChangedAt();
                if (changedAt != null && latestDailyLimit.getCreatedAt() != null) {
                    if (!latestDailyLimit.getCreatedAt().isBefore(changedAt.minusSeconds(120))) {
                        return true;
                    }
                }
            }
            List<DefectJournalEntity> allDefects = defectJournalRepository
                    .findBySourceComponentAndDefectTypeOrderByCreatedAtDesc(account.getName(), PRECONDITION_DEFECT_TYPE);
            if (!allDefects.isEmpty()) {
                DefectJournalEntity latestPrecondition = allDefects.get(0);
                Instant changedAt = account.getStatusChangedAt();
                if (changedAt != null && latestPrecondition.getCreatedAt() != null
                        && !latestPrecondition.getCreatedAt().isBefore(changedAt.minusSeconds(120))) {
                    String desc = latestPrecondition.getDescription();
                    if (desc != null) {
                        String lower = desc.toLowerCase();
                        if (lower.contains("quota") || lower.contains("rate limit") || lower.contains("429")
                                || lower.contains("daily_limit") || lower.contains("daily limit") || lower.contains("resource_exhausted")) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Estimates replenishment period strictly from THIS account's own observed recovery history (analogue of BetaPosterior).
     * To prevent category and lineage errors (RELIABILITY_CHAIN / D010), cross-account pooled queries are strictly avoided:
     * intervals between different accounts do not reflect supplier replenishment cycles.
     * If enough recovery duration samples exist for this account, the period is derived from the median interval between
     * its consecutive recoveries. Otherwise, falls back to the uninformative prior assumption (24 hours).
     */
    public Duration estimateReplenishmentPeriod(AccountEntity account) {
        if (account != null && account.getName() != null) {
            List<DefectJournalEntity> entries = defectJournalRepository
                    .findBySourceComponentAndDefectTypeOrderByCreatedAtDesc(account.getName(), BUDGET_RECOVERY_DEFECT_TYPE);
            if (entries.size() >= minSamplesForDataDriven) {
                List<Instant> timestamps = entries.stream()
                        .map(DefectJournalEntity::getCreatedAt)
                        .filter(Objects::nonNull)
                        .sorted()
                        .toList();
                if (timestamps.size() >= 2) {
                    List<Double> intervalMinutes = new ArrayList<>();
                    for (int i = 1; i < timestamps.size(); i++) {
                        long minutes = Duration.between(timestamps.get(i - 1), timestamps.get(i)).toMinutes();
                        if (minutes > 0) {
                            intervalMinutes.add((double) minutes);
                        }
                    }
                    if (!intervalMinutes.isEmpty()) {
                        double medMinutes = median(intervalMinutes);
                        if (medMinutes >= 30.0) {
                            return Duration.ofMinutes(Math.round(medMinutes));
                        }
                    }
                }
            }
        }
        // Documented uninformative prior assumption: provider quota replenishes on a daily cadence (defaultReplenishmentPeriodHours, 24h).
        return Duration.ofHours(defaultReplenishmentPeriodHours);
    }

    /**
     * Calculates the timestamp when the next replenishment period starts after fromInstant.
     * Anchor is taken strictly from THIS account's own last observed recovery. If this account
     * has not yet accumulated sufficient observations, the prior anchor assumption is UTC midnight.
     */
    public Instant nextReplenishmentPeriodStart(AccountEntity account, Instant fromInstant) {
        Instant current = fromInstant != null ? fromInstant : Instant.now();
        Duration period = estimateReplenishmentPeriod(account);
        Instant anchor = null;

        if (account != null && account.getName() != null) {
            List<DefectJournalEntity> entries = defectJournalRepository
                    .findBySourceComponentAndDefectTypeOrderByCreatedAtDesc(account.getName(), BUDGET_RECOVERY_DEFECT_TYPE);
            if (entries.size() >= minSamplesForDataDriven) {
                anchor = entries.get(0).getCreatedAt();
            }
        }

        if (anchor == null) {
            // Prior anchor assumption: provider quota resets at UTC midnight
            anchor = current.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant();
        }

        return calculateNextPeriodBoundary(anchor, current, period);
    }

    /**
     * Pure function: calculates the next periodic boundary strictly after fromInstant given anchor and period.
     */
    public static Instant calculateNextPeriodBoundary(Instant anchor, Instant fromInstant, Duration period) {
        if (period == null || period.isZero() || period.isNegative()) {
            period = Duration.ofHours(24);
        }
        if (anchor == null) {
            anchor = fromInstant != null
                    ? fromInstant.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant()
                    : Instant.now().atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant();
        }
        if (fromInstant == null) {
            fromInstant = Instant.now();
        }

        if (fromInstant.isBefore(anchor)) {
            long periodMillis = period.toMillis();
            if (periodMillis <= 0) periodMillis = 86_400_000L;
            long diffMillis = Duration.between(fromInstant, anchor).toMillis();
            long periodsBefore = diffMillis / periodMillis;
            Instant boundary = anchor.minusMillis(periodsBefore * periodMillis);
            if (!boundary.isAfter(fromInstant)) {
                boundary = boundary.plus(period);
            }
            return boundary;
        }

        long periodMillis = period.toMillis();
        if (periodMillis <= 0) {
            periodMillis = 86_400_000L;
        }
        long diffMillis = Duration.between(anchor, fromInstant).toMillis();
        long periodsElapsed = diffMillis / periodMillis;
        Instant currentPeriodStart = anchor.plusMillis(periodsElapsed * periodMillis);
        return currentPeriodStart.plus(period);
    }

    /**
     * Law 9 / Prescription 14:
     * Next probe instant is scheduled at the EARLIER of own backoff and next replenishment period start.
     * Guard: after an external budget exhaustion, the next probe is scheduled no later than the start of the new period.
     */
    public Instant computeNextProbeInstant(AccountEntity account) {
        return computeNextProbeInstant(account, Instant.now());
    }

    public Instant computeNextProbeInstant(AccountEntity account, Instant now) {
        if (account == null) {
            return now != null ? now : Instant.now();
        }
        Instant current = now != null ? now : Instant.now();
        Instant changedAt = account.getStatusChangedAt();
        if (changedAt == null) {
            changedAt = account.getLastHeartbeat() != null ? account.getLastHeartbeat()
                    : (account.getCreatedAt() != null ? account.getCreatedAt() : current.minus(Duration.ofMinutes(maxCooldownMinutes)));
        }
        long cooldownMinutes = computeCooldownMinutes(account);
        Instant ownBackoff = changedAt.plus(Duration.ofMinutes(cooldownMinutes));

        if (!isExternalBudgetExhaustion(account)) {
            return ownBackoff;
        }

        Instant periodStart = nextReplenishmentPeriodStart(account, changedAt);
        return ownBackoff.isBefore(periodStart) ? ownBackoff : periodStart;
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
