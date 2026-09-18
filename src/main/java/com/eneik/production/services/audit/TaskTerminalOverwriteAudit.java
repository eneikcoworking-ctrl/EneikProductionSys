package com.eneik.production.services.audit;

import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.services.logging.ProjectLogFlushQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Institutional fact register for terminal task status overwrite attempts (Searle 1995 / D007).
 * Ensures that any prohibited transition of a terminal task status leaves a durable, queryable trace
 * in the institutional fact register, the defect journal, and the project event log.
 */
public final class TaskTerminalOverwriteAudit {

    private static final Logger log = LoggerFactory.getLogger(TaskTerminalOverwriteAudit.class);
    private static final int MAX_BUFFER_SIZE = 1000;
    public static final String RULE_NAME = "TERMINAL_STATUS_OVERWRITE_PROHIBITION";

    public record AttemptRecord(
            UUID taskId,
            UUID projectId,
            TaskStatus currentStatus,
            TaskStatus attemptedStatus,
            Instant timestamp,
            String rule
    ) {
        public AttemptRecord {
            Objects.requireNonNull(currentStatus, "currentStatus must not be null");
            Objects.requireNonNull(attemptedStatus, "attemptedStatus must not be null");
            if (timestamp == null) {
                timestamp = Instant.now();
            }
            if (rule == null) {
                rule = RULE_NAME;
            }
        }
    }

    private static final ConcurrentLinkedDeque<AttemptRecord> RECENT_ATTEMPTS = new ConcurrentLinkedDeque<>();
    private static final List<Consumer<AttemptRecord>> LISTENERS = new CopyOnWriteArrayList<>();

    private TaskTerminalOverwriteAudit() {
    }

    /**
     * Records a prohibited attempt to overwrite an existing terminal task status.
     *
     * @param taskId the id of the task (may be null if unassigned)
     * @param projectId the id of the project (may be null)
     * @param currentStatus the current terminal status
     * @param attemptedStatus the new status attempted to be set
     * @return the immutable audit record
     */
    public static AttemptRecord recordAttempt(UUID taskId, UUID projectId, TaskStatus currentStatus, TaskStatus attemptedStatus) {
        AttemptRecord record = new AttemptRecord(taskId, projectId, currentStatus, attemptedStatus, Instant.now(), RULE_NAME);

        RECENT_ATTEMPTS.addFirst(record);
        while (RECENT_ATTEMPTS.size() > MAX_BUFFER_SIZE) {
            RECENT_ATTEMPTS.pollLast();
        }

        log.warn("[INSTITUTIONAL-PROHIBITION] Terminal status {} of task {} (project: {}) cannot be overwritten with {}",
                currentStatus, taskId, projectId, attemptedStatus);

        // Queue in project event log if projectId is available
        if (projectId != null) {
            try {
                String message = String.format(
                        "Institutional prohibition triggered: terminal status %s of task %s cannot be overwritten with %s",
                        currentStatus, taskId, attemptedStatus
                );
                ProjectLogFlushQueue.offer(new ProjectLogFlushQueue.PendingEntry(
                        projectId,
                        record.timestamp(),
                        "WARN",
                        "com.eneik.production.models.persistence.TaskEntity",
                        message
                ));
            } catch (Exception e) {
                log.warn("Failed to queue terminal overwrite attempt into ProjectLogFlushQueue", e);
            }
        }

        // Notify external listeners (e.g. TaskTerminalOverwriteAuditService / DefectJournalService)
        for (Consumer<AttemptRecord> listener : LISTENERS) {
            try {
                listener.accept(record);
            } catch (Exception e) {
                log.error("Failed to notify audit listener of terminal status overwrite attempt", e);
            }
        }

        return record;
    }

    /**
     * Returns an unmodifiable list of recent attempts (most recent first).
     */
    public static List<AttemptRecord> getRecentAttempts() {
        return Collections.unmodifiableList(new ArrayList<>(RECENT_ATTEMPTS));
    }

    /**
     * Returns attempts recorded for a specific task.
     */
    public static List<AttemptRecord> getAttemptsForTask(UUID taskId) {
        if (taskId == null) {
            return List.of();
        }
        return RECENT_ATTEMPTS.stream()
                .filter(r -> taskId.equals(r.taskId()))
                .toList();
    }

    /**
     * Returns attempts recorded for a specific project.
     */
    public static List<AttemptRecord> getAttemptsForProject(UUID projectId) {
        if (projectId == null) {
            return List.of();
        }
        return RECENT_ATTEMPTS.stream()
                .filter(r -> projectId.equals(r.projectId()))
                .toList();
    }

    public static void registerListener(Consumer<AttemptRecord> listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    public static void unregisterListener(Consumer<AttemptRecord> listener) {
        if (listener != null) {
            LISTENERS.remove(listener);
        }
    }

    /**
     * Clears recorded attempts. Intended primarily for testing.
     */
    public static void clear() {
        RECENT_ATTEMPTS.clear();
    }
}
