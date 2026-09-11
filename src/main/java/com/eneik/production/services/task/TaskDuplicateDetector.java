package com.eneik.production.services.task;

import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Task Duplicate Detector - unified work-identity and duplicate evaluation engine.
 *
 * <p>Preserves the category boundary between state ("how many duplicates are stuck right now")
 * and process over time ("rate of duplicate generation") according to Law 2 and
 * {@code GILBERT_RAYL_03_CATEGORY_ERROR_SCAN} (D002):
 * <ul>
 *   <li>{@link StuckDuplicateContent}: In-flight state of non-terminal tasks currently active in the pipeline.
 *       Excludes terminal tasks and deliberate recovery replacements. Evaluated over recent tasks window.</li>
 *   <li>{@link DuplicateGenerationVelocity}: Process over time measuring rate of task creation sharing work identity.
 *       Includes terminal tasks (done, failed, etc.) across a rolling time window. Threshold derived from
 *       {@link WishlistEntity#COMPILE_ATTEMPT_BUDGET} (3 = 1 base attempt + 2 repairs). Recorded to DefectJournal.
 *       Condition is strictly greater than budget (&gt; B) so legal attempts (up to 3) are never flagged.</li>
 * </ul>
 *
 * <p>Work identity resolution order:
 * <ol>
 *   <li>{@code task.getContentKey()}: Explicit identity (e.g. compiler carrier key {@code compile:<projectId>:<hash>}).</li>
 *   <li>{@code payload.slice_title}: Canonical slice title for decomposition work items.</li>
 *   <li>{@code task.getDescription()}: Fallback task description.</li>
 * </ol>
 */
public final class TaskDuplicateDetector {

    public static final Set<TaskStatus> TERMINAL_STATUSES = Set.of(
            TaskStatus.done, TaskStatus.failed, TaskStatus.blocked, TaskStatus.spike_completed);

    public static final int DEFAULT_STUCK_WINDOW_LIMIT = 30;
    public static final int DEFAULT_STUCK_THRESHOLD = 3;

    /**
     * Threshold derived from {@link WishlistEntity#COMPILE_ATTEMPT_BUDGET} = 3.
     * Condition for velocity defect is count &gt; threshold (&gt; B).
     * Exactly B attempts (1 baseline attempt + 2 repairs before attempt exhaustion)
     * is the lawful budget. Only strictly exceeding the budget (&gt; 3) constitutes
     * an uncontrolled generation loop.
     *
     * <p>Deliberate recovery tasks ({@link #isDeliberateRecoveryTask}) are INCLUDED in velocity
     * detection because the legal recovery depth allows at most 1 base task + 2 repairs = 3 tasks.
     * Any generation producing &gt; 3 tasks within the window exceeds the legal recovery budget.
     */
    public static final int DEFAULT_VELOCITY_THRESHOLD = WishlistEntity.COMPILE_ATTEMPT_BUDGET;

    /**
     * Default rolling time window for duplicate generation velocity (2 hours, aligning with Kaizen cadence).
     */
    public static final Duration DEFAULT_VELOCITY_WINDOW = Duration.ofHours(2);

    /**
     * In-flight state: duplicates currently stuck/active in the system.
     */
    public record StuckDuplicateContent(String contentKey, long count) {}

    /**
     * Process over time: velocity of task creation sharing the same work identity across a time window.
     */
    public record DuplicateGenerationVelocity(
            String contentKey,
            long count,
            Duration window,
            Instant windowStart,
            Instant windowEnd
    ) {}

    private TaskDuplicateDetector() {
        // Utility / domain logic class
    }

    /**
     * Resolves the canonical work identity for a task.
     */
    public static String taskContentKey(TaskEntity task) {
        if (task == null) {
            return "";
        }
        if (task.getContentKey() != null && !task.getContentKey().isBlank()) {
            return task.getContentKey().trim();
        }
        if (task.getPayload() != null) {
            String sliceTitle = task.getPayload().path("slice_title").asText("");
            if (!sliceTitle.isBlank()) {
                return sliceTitle.trim();
            }
        }
        String desc = task.getDescription();
        return desc != null ? desc.trim() : "";
    }

    public static boolean isTerminal(TaskEntity task) {
        return task == null || TERMINAL_STATUSES.contains(task.getStatus());
    }

    public static boolean isDeliberateRecoveryTask(TaskEntity task) {
        return task != null && task.getPayload() != null && task.getPayload().has("recoversFailedTaskId");
    }

    /**
     * Evaluates in-flight stuck duplicate content using default window limit (30) and threshold (3).
     */
    public static List<StuckDuplicateContent> findStuckDuplicateContent(List<TaskEntity> tasks) {
        return findStuckDuplicateContent(tasks, DEFAULT_STUCK_WINDOW_LIMIT, DEFAULT_STUCK_THRESHOLD);
    }

    /**
     * Evaluates in-flight stuck duplicate content.
     * Excludes terminal tasks and deliberate recovery replacements.
     */
    public static List<StuckDuplicateContent> findStuckDuplicateContent(List<TaskEntity> tasks, int windowLimit, int threshold) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        Map<String, Long> counts = tasks.stream()
                .limit(windowLimit)
                .filter(task -> !isTerminal(task))
                .filter(task -> !isDeliberateRecoveryTask(task))
                .map(TaskDuplicateDetector::taskContentKey)
                .filter(key -> key != null && !key.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), HashMap::new, Collectors.counting()));

        return counts.entrySet().stream()
                .filter(e -> e.getValue() >= threshold)
                .map(e -> new StuckDuplicateContent(e.getKey(), e.getValue()))
                .toList();
    }

    public static boolean hasStuckDuplicateContent(List<TaskEntity> tasks) {
        return hasStuckDuplicateContent(tasks, DEFAULT_STUCK_WINDOW_LIMIT, DEFAULT_STUCK_THRESHOLD);
    }

    public static boolean hasStuckDuplicateContent(List<TaskEntity> tasks, int windowLimit, int threshold) {
        return !findStuckDuplicateContent(tasks, windowLimit, threshold).isEmpty();
    }

    /**
     * Evaluates velocity for an individual work identity.
     * Triggers when count &gt; threshold (&gt; B).
     */
    public static Optional<DuplicateGenerationVelocity> evaluateVelocity(
            String contentKey, long count, Duration window, Instant windowStart, Instant windowEnd, int threshold) {
        if (contentKey == null || contentKey.isBlank() || count <= threshold) {
            return Optional.empty();
        }
        return Optional.of(new DuplicateGenerationVelocity(contentKey.trim(), count, window, windowStart, windowEnd));
    }

    /**
     * Detects duplicate generation velocity across a time window.
     * Terminal tasks ARE included to measure generation rate over time.
     * Condition is strictly greater than threshold (&gt; B).
     */
    public static List<DuplicateGenerationVelocity> detectDuplicateGenerationVelocity(
            List<TaskEntity> tasksInWindow, Instant windowStart, Instant windowEnd) {
        return detectDuplicateGenerationVelocity(tasksInWindow, windowStart, windowEnd, DEFAULT_VELOCITY_THRESHOLD);
    }

    /**
     * Detects duplicate generation velocity across a time window.
     * Terminal tasks ARE included to measure generation rate over time.
     * Condition is strictly greater than threshold (&gt; B).
     */
    public static List<DuplicateGenerationVelocity> detectDuplicateGenerationVelocity(
            List<TaskEntity> tasksInWindow, Instant windowStart, Instant windowEnd, int threshold) {
        if (tasksInWindow == null || tasksInWindow.isEmpty()) {
            return List.of();
        }
        Duration window = Duration.between(windowStart, windowEnd);
        Map<String, Long> counts = tasksInWindow.stream()
                .map(TaskDuplicateDetector::taskContentKey)
                .filter(key -> key != null && !key.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), HashMap::new, Collectors.counting()));

        return counts.entrySet().stream()
                .filter(e -> e.getValue() > threshold)
                .map(e -> new DuplicateGenerationVelocity(e.getKey(), e.getValue(), window, windowStart, windowEnd))
                .toList();
    }
}
