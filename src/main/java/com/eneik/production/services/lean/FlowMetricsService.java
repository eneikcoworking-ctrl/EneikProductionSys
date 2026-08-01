package com.eneik.production.services.lean;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Layer 3 (Lean) of the unified Lean-TOC-Six-Sigma system - see docs/ENGINEERING_INVARIANTS_CHARTER.md
 * and the plan this implements. Cycle Time, Lead Time, and WIP from existing TaskEntity/JulesSessionEntity
 * timestamps; Little's Law (WIP ≈ Throughput × Cycle Time) as a live, purely arithmetic consistency check
 * that needs no domain judgment - a persistent violation signals the system's own status tracking is
 * lying, not process noise (the exact shape of the SYSTEM_STALLED/pending_review incidents this factory
 * has already hit live). Waste is computed from real status-transition evidence (a dismissed wishlist
 * never led to any work), never an LLM's narrative label written before the work even started.
 */
@Service
public class FlowMetricsService {

    private static final Logger log = LoggerFactory.getLogger(FlowMetricsService.class);

    private static final Set<TaskStatus> TERMINAL_STATUSES =
            EnumSet.of(TaskStatus.done, TaskStatus.failed, TaskStatus.spike_completed);

    public record FlowMetricsReport(
            UUID projectId,
            int wip,
            double throughputPerDay,
            double avgCycleTimeDays,
            double avgLeadTimeDays,
            int cycleTimeSampleSize,
            double littlesLawExpectedWip,
            double littlesLawDeviationRatio,
            boolean littlesLawInconsistent,
            long wasteWishlistCount,
            long totalWishlistCount,
            double wasteRatio,
            Instant computedAt
    ) {}

    private final TaskRepository taskRepository;
    private final JulesSessionRepository julesSessionRepository;
    private final WishlistRepository wishlistRepository;

    @Value("${process-control.littles-law-deviation-threshold:0.5}")
    private double littlesLawDeviationThreshold;

    public FlowMetricsService(TaskRepository taskRepository,
                               JulesSessionRepository julesSessionRepository,
                               WishlistRepository wishlistRepository) {
        this.taskRepository = taskRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.wishlistRepository = wishlistRepository;
    }

    public FlowMetricsReport computeForProject(UUID projectId) {
        List<TaskEntity> tasks = taskRepository.findAll().stream()
                .filter(t -> t.getProject() != null && projectId.equals(t.getProject().getId()))
                .toList();

        int wip = (int) tasks.stream().filter(t -> !TERMINAL_STATUSES.contains(t.getStatus())).count();

        List<TaskEntity> doneTasks = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.done)
                .filter(t -> t.getCreatedAt() != null && t.getUpdatedAt() != null)
                .toList();

        double avgLeadTimeDays = doneTasks.stream()
                .mapToDouble(t -> Duration.between(t.getCreatedAt(), t.getUpdatedAt()).toSeconds() / 86400.0)
                .average().orElse(0.0);

        java.util.List<Double> cycleTimeDaysList = new java.util.ArrayList<>();
        for (TaskEntity t : doneTasks) {
            Instant dispatchAt = earliestSessionDispatchAt(t.getId());
            if (dispatchAt != null) {
                cycleTimeDaysList.add(Duration.between(dispatchAt, t.getUpdatedAt()).toSeconds() / 86400.0);
            }
        }
        double avgCycleTimeDays = cycleTimeDaysList.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        double windowDays = 1.0;
        if (!doneTasks.isEmpty()) {
            Instant earliest = doneTasks.stream().map(TaskEntity::getCreatedAt).min(Comparator.naturalOrder()).orElse(Instant.now());
            Instant latest = doneTasks.stream().map(TaskEntity::getUpdatedAt).max(Comparator.naturalOrder()).orElse(Instant.now());
            windowDays = Math.max(1.0, Duration.between(earliest, latest).toSeconds() / 86400.0);
        }
        double throughputPerDay = doneTasks.size() / windowDays;

        double littlesLawExpectedWip = throughputPerDay * avgCycleTimeDays;
        double deviationRatio;
        boolean inconsistent;
        if (doneTasks.size() < 3 || cycleTimeDaysList.isEmpty()) {
            // Not enough completed work yet for Little's Law's steady-state assumption to mean anything.
            deviationRatio = 0.0;
            inconsistent = false;
        } else if (littlesLawExpectedWip > 0) {
            deviationRatio = Math.abs(wip - littlesLawExpectedWip) / littlesLawExpectedWip;
            inconsistent = deviationRatio > littlesLawDeviationThreshold;
        } else {
            deviationRatio = wip > 0 ? 1.0 : 0.0;
            inconsistent = wip > 0;
        }

        long wasteCount = wishlistRepository.countByProjectIdAndStatus(projectId, WishlistStatus.dismissed);
        long totalCount = wishlistRepository.findByProjectId(projectId).size();
        double wasteRatio = totalCount > 0 ? (double) wasteCount / totalCount : 0.0;

        if (inconsistent) {
            log.warn("[FLOW-METRICS] project={} Little's Law inconsistency: WIP={} vs expected={} (deviation={})",
                    projectId, wip, String.format("%.2f", littlesLawExpectedWip), String.format("%.2f", deviationRatio));
        }

        return new FlowMetricsReport(
                projectId, wip, throughputPerDay, avgCycleTimeDays, avgLeadTimeDays, cycleTimeDaysList.size(),
                littlesLawExpectedWip, deviationRatio, inconsistent,
                wasteCount, totalCount, wasteRatio, Instant.now()
        );
    }

    private Instant earliestSessionDispatchAt(UUID taskId) {
        List<JulesSessionEntity> sessions = julesSessionRepository.findByTaskId(taskId);
        return sessions.stream()
                .map(JulesSessionEntity::getCreatedAt)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }
}
