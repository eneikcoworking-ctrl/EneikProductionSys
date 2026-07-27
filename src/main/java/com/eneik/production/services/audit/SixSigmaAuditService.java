package com.eneik.production.services.audit;

import com.eneik.production.models.persistence.OnboardingAuditFindingEntity;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.TaskConflictEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.OnboardingAuditFindingRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.toc.service.TocSentinelService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Six Sigma & System Quality Audit Engine.
 * Calculates DPMO (Defects Per Million Opportunities), First Time Yield (FTY),
 * and Sigma Level (Z-score) across code delivery, quality gates, and runtime execution.
 */
@Service
public class SixSigmaAuditService {

    private static final Logger log = LoggerFactory.getLogger(SixSigmaAuditService.class);

    private final PrReviewRepository prReviewRepository;
    private final TaskConflictRepository taskConflictRepository;
    private final TaskRepository taskRepository;
    private final OnboardingAuditFindingRepository onboardingAuditFindingRepository;
    private final com.eneik.production.repositories.ProjectRepository projectRepository;
    private final com.eneik.production.repositories.JulesSessionRepository julesSessionRepository;
    private final TocSentinelService tocSentinelService;

    public SixSigmaAuditService(PrReviewRepository prReviewRepository,
                                TaskConflictRepository taskConflictRepository,
                                TaskRepository taskRepository,
                                OnboardingAuditFindingRepository onboardingAuditFindingRepository,
                                com.eneik.production.repositories.ProjectRepository projectRepository,
                                com.eneik.production.repositories.JulesSessionRepository julesSessionRepository,
                                TocSentinelService tocSentinelService) {
        this.prReviewRepository = prReviewRepository;
        this.taskConflictRepository = taskConflictRepository;
        this.taskRepository = taskRepository;
        this.onboardingAuditFindingRepository = onboardingAuditFindingRepository;
        this.projectRepository = projectRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.tocSentinelService = tocSentinelService;
    }

    public record SixSigmaAuditReport(
            UUID projectId,
            String projectName,
            long totalOpportunities,
            long totalDefects,
            double dpmo,
            double yieldRatePercent,
            double sigmaLevel,
            String qualityTier,
            Map<String, Object> defectBreakdown,
            Map<String, Object> tocOperationalMetrics,
            Map<String, Object> factoryWideBenchmark,
            Instant auditedAt
    ) {}

    public SixSigmaAuditReport calculateFullSixSigmaAudit() {
        return calculateProjectSixSigmaAudit(null);
    }

    public SixSigmaAuditReport calculateProjectSixSigmaAudit(UUID projectId) {
        // 1. Category A: PR Merge & Conflict Opportunities
        List<PrReviewEntity> reviews = prReviewRepository.findAll();
        List<TaskConflictEntity> conflicts = taskConflictRepository.findAll();

        if (projectId != null) {
            reviews = reviews.stream()
                    .filter(r -> {
                        if (r.getJulesSessionId() == null) return false;
                        var sessionOpt = julesSessionRepository.findById(r.getJulesSessionId());
                        if (sessionOpt.isEmpty()) return false;
                        var taskOpt = taskRepository.findById(sessionOpt.get().getTaskId());
                        return taskOpt.isPresent() && taskOpt.get().getProject() != null && projectId.equals(taskOpt.get().getProject().getId());
                    })
                    .toList();
            conflicts = conflicts.stream()
                    .filter(c -> c.getTask() != null && c.getTask().getProject() != null && projectId.equals(c.getTask().getProject().getId()))
                    .toList();
        }

        long mergedPrs = reviews.stream().filter(r -> Boolean.TRUE.equals(r.getMerged())).count();
        long conflictDefects = conflicts.size();
        long prOpportunities = mergedPrs + conflictDefects;

        // 2. Category B: Quality Gate Checks
        List<TaskEntity> tasks = taskRepository.findAll();
        if (projectId != null) {
            tasks = tasks.stream()
                    .filter(t -> t.getProject() != null && projectId.equals(t.getProject().getId()))
                    .toList();
        }

        long qgOpportunities = 0;
        long qgDefects = 0;

        for (TaskEntity task : tasks) {
            JsonNode report = task.getQualityGateReport();
            if (report != null && report.has("checks")) {
                JsonNode checks = report.get("checks");
                qgOpportunities += checks.size();
                for (JsonNode check : checks) {
                    if (!check.path("passed").asBoolean(true)) {
                        qgDefects++;
                    }
                }
            }
        }

        // 3. Category C: Onboarding Audit Findings
        List<OnboardingAuditFindingEntity> onboardingFindings = onboardingAuditFindingRepository.findAll();
        if (projectId != null) {
            onboardingFindings = onboardingFindings.stream()
                    .filter(f -> f.getProject() != null && projectId.equals(f.getProject().getId()))
                    .toList();
        }
        long onboardingOpportunities = Math.max(onboardingFindings.size() * 5L, projectId == null ? 20L : 5L);
        long onboardingDefects = onboardingFindings.size();

        // 4. Category D: TOC Sentinel Runtime Execution Anomalies
        var tocAnomalies = tocSentinelService.getRecentAnomalies();
        long tocOpportunities = Math.max(tocSentinelService.getGraph().getCompletedCountAllNodes() * 2L, projectId == null ? 50L : 10L);
        long tocDefects = tocAnomalies.size();

        // Totals
        long totalOpportunities = prOpportunities + qgOpportunities + onboardingOpportunities + (projectId == null ? tocOpportunities : 0L);
        long totalDefects = conflictDefects + qgDefects + onboardingDefects + (projectId == null ? tocDefects : 0L);

        if (totalOpportunities == 0) {
            totalOpportunities = 100; // Baseline default if fresh DB or fresh project
        }

        double dpmo = ((double) totalDefects / totalOpportunities) * 1_000_000.0;
        double yieldRatePercent = Math.max(0.0, ((double) (totalOpportunities - totalDefects) / totalOpportunities) * 100.0);
        double sigmaLevel = calculateSigmaLevel(dpmo);
        String qualityTier = getQualityTier(sigmaLevel);

        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("prConflicts", Map.of("opportunities", prOpportunities, "defects", conflictDefects, "dpmo", calculateDpmo(conflictDefects, prOpportunities)));
        breakdown.put("qualityGateChecks", Map.of("opportunities", qgOpportunities, "defects", qgDefects, "dpmo", calculateDpmo(qgDefects, qgOpportunities)));
        breakdown.put("onboardingFindings", Map.of("opportunities", onboardingOpportunities, "defects", onboardingDefects, "dpmo", calculateDpmo(onboardingDefects, onboardingOpportunities)));
        if (projectId == null) {
            breakdown.put("runtimeAnomalies", Map.of("opportunities", tocOpportunities, "defects", tocDefects, "dpmo", calculateDpmo(tocDefects, tocOpportunities)));
        }

        Map<String, Object> tocMetrics = new LinkedHashMap<>();
        var status = tocSentinelService.getDbrStatus();
        tocMetrics.put("primaryConstraint", status.primaryConstraintNode());
        tocMetrics.put("constraintQueueLength", status.constraintQueueLength());
        tocMetrics.put("constraintUtilizationPercent", Math.round(status.constraintUtilization() * 1000.0) / 10.0);
        tocMetrics.put("ropeThrottlingActive", status.ropeThrottlingActive());
        tocMetrics.put("anomaliesCount", tocAnomalies.size());

        Map<String, Object> factoryBenchmark = new LinkedHashMap<>();
        if (projectId != null) {
            SixSigmaAuditReport factoryReport = calculateFullSixSigmaAudit();
            factoryBenchmark.put("factoryDpmo", factoryReport.dpmo());
            factoryBenchmark.put("factoryYieldPercent", factoryReport.yieldRatePercent());
            factoryBenchmark.put("factorySigmaLevel", factoryReport.sigmaLevel());
            factoryBenchmark.put("factoryQualityTier", factoryReport.qualityTier());
        }

        String projectName = "FACTORY_WIDE_ALL_PROJECTS";
        if (projectId != null) {
            projectName = projectRepository.findById(projectId)
                    .map(com.eneik.production.models.persistence.ProjectEntity::getName)
                    .orElse("PROJECT_" + projectId.toString().substring(0, 8));
        }

        SixSigmaAuditReport report = new SixSigmaAuditReport(
                projectId,
                projectName,
                totalOpportunities,
                totalDefects,
                Math.round(dpmo * 100.0) / 100.0,
                Math.round(yieldRatePercent * 100.0) / 100.0,
                Math.round(sigmaLevel * 100.0) / 100.0,
                qualityTier,
                breakdown,
                tocMetrics,
                factoryBenchmark,
                Instant.now()
        );

        log.info("[SIX-SIGMA-AUDIT] Audit Completed | Scope: {} | DPMO: {} | Yield: {}% | Sigma Level: {} (Tier: {}) | Defects: {}/{}",
                projectName, report.dpmo(), report.yieldRatePercent(), report.sigmaLevel(), report.qualityTier(), report.totalDefects(), report.totalOpportunities());

        return report;
    }

    private double calculateDpmo(long defects, long opportunities) {
        if (opportunities <= 0) return 0.0;
        return Math.round((((double) defects / opportunities) * 1_000_000.0) * 100.0) / 100.0;
    }

    /**
     * Converts DPMO to 6 Sigma scale (with standard 1.5 sigma process shift).
     */
    public static double calculateSigmaLevel(double dpmo) {
        if (dpmo <= 3.4) return 6.0;
        if (dpmo <= 233) return 5.0 + (1.0 - (dpmo - 3.4) / (233 - 3.4));
        if (dpmo <= 6210) return 4.0 + (1.0 - (dpmo - 233) / (6210 - 233));
        if (dpmo <= 66807) return 3.0 + (1.0 - (dpmo - 6210) / (66807 - 6210));
        if (dpmo <= 308537) return 2.0 + (1.0 - (dpmo - 66807) / (308537 - 66807));
        if (dpmo <= 691462) return 1.0 + (1.0 - (dpmo - 308537) / (691462 - 308537));
        return Math.max(0.0, 1.0 - (dpmo - 691462) / 308538.0);
    }

    private String getQualityTier(double sigmaLevel) {
        if (sigmaLevel >= 6.0) return "WORLD_CLASS_SIX_SIGMA";
        if (sigmaLevel >= 5.0) return "EXCELLENT_FIVE_SIGMA";
        if (sigmaLevel >= 4.0) return "GOOD_FOUR_SIGMA";
        if (sigmaLevel >= 3.0) return "AVERAGE_THREE_SIGMA";
        if (sigmaLevel >= 2.0) return "NEEDS_IMPROVEMENT_TWO_SIGMA";
        return "CRITICAL_DEFECT_RATE";
    }
}
