package com.eneik.production.services.toc;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.github.GitHubApiBudgetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Layer 2 (TOC) of the unified Lean-TOC-Six-Sigma system - see docs/ENGINEERING_INVARIANTS_CHARTER.md
 * and the plan this implements. Identifies the FACTORY's own current drum (bottleneck resource) among
 * its macro production resources - distinct from {@link com.eneik.production.toc.service.TocSentinelService},
 * which tracks instrumented internal cycles (e.g. AUTOMERGE_CYCLE) at a finer grain. The drum is
 * identified, not assumed fixed (operator's own observation: it can move across a project's segments):
 * at any point, compare queue length against capacity for each candidate resource - the one with the
 * highest queue-to-capacity ratio is currently constraining throughput (classical TOC: WIP piles up in
 * front of the constraint).
 */
@Service
public class ConstraintIdentificationService {

    private static final Logger log = LoggerFactory.getLogger(ConstraintIdentificationService.class);

    public enum ConstraintResource { DISPATCH_CAPACITY, REVIEW_CAPACITY, GITHUB_API_BUDGET, JULES_SESSION_SLOTS }

    public record DrumAssessment(ConstraintResource resource, double pressure, long queueLength, double capacity) {}

    public record BufferRecommendation(long bufferCapacity, double meanCycleTimeSeconds,
                                        double stdDevCycleTimeSeconds, double throughputPerSecond, int sampleSize) {}

    private final TaskRepository taskRepository;
    private final AccountRepository accountRepository;
    private final JulesSessionRepository julesSessionRepository;
    private final GitHubApiBudgetService gitHubApiBudgetService;

    // No dedicated concurrency limiter exists yet for review-verdict LLM calls (they run inline, not
    // through a Jules session slot) - this is a conservative, documented nominal capacity until real
    // instrumentation exists, not a fabricated precise number.
    @Value("${process-control.nominal-review-capacity:3}")
    private int nominalReviewCapacity;

    @Value("${process-control.default-account-session-slots:3}")
    private int defaultAccountSessionSlots;

    public ConstraintIdentificationService(TaskRepository taskRepository,
                                            AccountRepository accountRepository,
                                            JulesSessionRepository julesSessionRepository,
                                            GitHubApiBudgetService gitHubApiBudgetService) {
        this.taskRepository = taskRepository;
        this.accountRepository = accountRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.gitHubApiBudgetService = gitHubApiBudgetService;
    }

    private static final List<String> ACTIVE_SESSION_STATUSES = List.of("queued", "running", "revising", "stuck");

    public DrumAssessment identifyDrum(UUID projectId) {
        List<TaskEntity> tasks = projectId != null
                ? taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                : taskRepository.findAll();

        long queuedTasks = tasks.stream().filter(t -> t.getStatus() == TaskStatus.queued).count();
        long reviewTasks = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.pending_review || t.getStatus() == TaskStatus.review)
                .count();

        // Operational capacity denominator aligned with dispatch eligibility
        // (Section VII / 10-такт 2/10, ELVIN_GOLDMAN_01_RELIABILITY_CHAIN / D010, Goldman;
        // NUEL_BELNAP_03_TRUTH_STATUS_TABLE / D012).
        // Uses bounded factory-wide reference data (findAllByOrderByNameAsc) and excludes
        // non-operational accounts (disabled, daily_limited, api_blocked, offline, decommissioned)
        // so that the drum does not fabricate phantom capacity for accounts the dispatcher would reject.
        List<AccountEntity> operationalAccounts = accountRepository.findAllByOrderByNameAsc().stream()
                .filter(AccountEntity::isEnabled)
                .filter(a -> a.getStatus() == AccountStatus.idle || a.getStatus() == AccountStatus.busy)
                .toList();
        double totalSlots = operationalAccounts.stream()
                .mapToInt(a -> a.getMaxConcurrentSessions() != null ? a.getMaxConcurrentSessions() : defaultAccountSessionSlots)
                .sum();

        Set<UUID> projectTaskIds = tasks.stream().map(TaskEntity::getId).collect(Collectors.toSet());

        long activeSessions = julesSessionRepository.findByStatusIn(ACTIVE_SESSION_STATUSES).stream()
                .filter(s -> s.getTaskId() != null && projectTaskIds.contains(s.getTaskId()))
                .count();

        double dispatchPressure = queuedTasks == 0 ? 0.0
                : (totalSlots <= 0 ? Double.MAX_VALUE : queuedTasks / totalSlots);
        double reviewPressure = reviewTasks == 0 ? 0.0
                : reviewTasks / (double) Math.max(1, nominalReviewCapacity);

        var budget = gitHubApiBudgetService.snapshot();
        double budgetPressure = (budget.limit() != null && budget.limit() > 0 && budget.remaining() != null)
                ? 1.0 - ((double) budget.remaining() / budget.limit())
                : 0.0;

        double sessionSlotPressure = totalSlots > 0 ? activeSessions / totalSlots
                : (activeSessions > 0 ? 1.0 : 0.0);

        DrumAssessment[] candidates = new DrumAssessment[]{
                new DrumAssessment(ConstraintResource.DISPATCH_CAPACITY, dispatchPressure, queuedTasks, totalSlots),
                new DrumAssessment(ConstraintResource.REVIEW_CAPACITY, reviewPressure, reviewTasks, nominalReviewCapacity),
                new DrumAssessment(ConstraintResource.GITHUB_API_BUDGET, budgetPressure,
                        budget.used() != null ? budget.used() : 0, budget.limit() != null ? budget.limit() : 0),
                new DrumAssessment(ConstraintResource.JULES_SESSION_SLOTS, sessionSlotPressure, activeSessions, totalSlots),
        };

        DrumAssessment drum = candidates[0];
        for (DrumAssessment c : candidates) {
            if (c.pressure() > drum.pressure()) {
                drum = c;
            }
        }

        log.info("[TOC-CONSTRAINT] project={} drum={} pressure={} (dispatch={}, review={}, githubBudget={}, sessionSlots={})",
                projectId, drum.resource(), String.format("%.3f", drum.pressure()),
                String.format("%.3f", dispatchPressure), String.format("%.3f", reviewPressure),
                String.format("%.3f", budgetPressure), String.format("%.3f", sessionSlotPressure));
        return drum;
    }

    /**
     * Buffer sized from the statistical variance of this project's own task cycle time (createdAt →
     * updatedAt for status=done tasks), converted from a time buffer to a WIP-count buffer via Little's
     * Law-style safety-stock sizing: {@code bufferCapacity ≈ ceil(z × throughput × σ(cycleTime))}.
     * Replaces a hardcoded increment with a number tied to this project's own measured variance.
     */
    public BufferRecommendation recommendedBufferCapacity(UUID projectId, double zFactor) {
        List<TaskEntity> doneTasks = (projectId != null
                ? taskRepository.findByProjectIdAndStatus(projectId, TaskStatus.done)
                : taskRepository.findAll().stream().filter(t -> t.getStatus() == TaskStatus.done).toList())
                .stream()
                .filter(t -> t.getCreatedAt() != null && t.getUpdatedAt() != null)
                .toList();

        if (doneTasks.size() < 2) {
            return new BufferRecommendation(1, 0.0, 0.0, 0.0, doneTasks.size());
        }

        List<Double> cycleSeconds = doneTasks.stream()
                .map(t -> (double) Math.max(0, Duration.between(t.getCreatedAt(), t.getUpdatedAt()).toSeconds()))
                .toList();

        double mean = cycleSeconds.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = cycleSeconds.stream()
                .mapToDouble(v -> (v - mean) * (v - mean))
                .sum() / (cycleSeconds.size() - 1);
        double stdDev = Math.sqrt(Math.max(0.0, variance));

        java.time.Instant earliest = doneTasks.stream().map(TaskEntity::getCreatedAt).min(java.time.Instant::compareTo).orElse(java.time.Instant.now());
        java.time.Instant latest = doneTasks.stream().map(TaskEntity::getUpdatedAt).max(java.time.Instant::compareTo).orElse(java.time.Instant.now());
        double windowSeconds = Math.max(1.0, Duration.between(earliest, latest).toSeconds());
        double throughput = doneTasks.size() / windowSeconds;

        long bufferCapacity = Math.max(1, (long) Math.ceil(zFactor * throughput * stdDev));
        return new BufferRecommendation(bufferCapacity, mean, stdDev, throughput, doneTasks.size());
    }
}
