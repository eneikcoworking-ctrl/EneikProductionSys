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
    private final com.eneik.production.repositories.FeatureRepository featureRepository;

    public SixSigmaAuditService(PrReviewRepository prReviewRepository,
                                TaskConflictRepository taskConflictRepository,
                                TaskRepository taskRepository,
                                OnboardingAuditFindingRepository onboardingAuditFindingRepository,
                                com.eneik.production.repositories.ProjectRepository projectRepository,
                                com.eneik.production.repositories.JulesSessionRepository julesSessionRepository,
                                TocSentinelService tocSentinelService,
                                com.eneik.production.repositories.FeatureRepository featureRepository) {
        this.prReviewRepository = prReviewRepository;
        this.taskConflictRepository = taskConflictRepository;
        this.taskRepository = taskRepository;
        this.onboardingAuditFindingRepository = onboardingAuditFindingRepository;
        this.projectRepository = projectRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.tocSentinelService = tocSentinelService;
        this.featureRepository = featureRepository;
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
            Instant auditedAt
    ) {}

    /**
     * 2026-08-04 (3-layer Factory/Delivery/Product model, real bug fixed as part of building it): this
     * used to call calculateProjectSixSigmaAudit(getActiveProjectId()) - which, despite the method's own
     * name, is NOT factory-wide at all. calculateProjectSixSigmaAudit immediately coerces any null
     * projectId to a specific project via getActiveProjectId() before its own targetProjectId==null
     * branches (cross-project TOC anomalies, factory-wide opportunity floors) ever get a chance to run -
     * they were dead code, unreachable through any public call path. This is the genuine Layer 1
     * "Factory" number: cross-project, the only caller of calculateSixSigmaAuditInternal with a real null.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public SixSigmaAuditReport calculateFullSixSigmaAudit() {
        return calculateSixSigmaAuditInternal(null);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public UUID getActiveProjectId() {
        return projectRepository.findAll().stream()
                .filter(p -> "active".equalsIgnoreCase(String.valueOf(p.getStatus())) || "orchestrated".equalsIgnoreCase(String.valueOf(p.getStatus())))
                .map(com.eneik.production.models.persistence.ProjectEntity::getId)
                .findFirst()
                .orElseGet(() -> projectRepository.findAll().stream()
                        .map(com.eneik.production.models.persistence.ProjectEntity::getId)
                        .findFirst()
                        .orElse(null));
    }

    /**
     * Layer 2 "Delivery" number: full history for ONE project (including dismissed/duplicate/failed work
     * that ever went through PR review or a quality gate for it), including its default-active-project
     * fallback for backward compatibility with every existing caller that passes null expecting "whichever
     * project is active" rather than genuinely cross-project. For the real Layer 1 factory-wide number,
     * use {@link #calculateFullSixSigmaAudit()} instead, which bypasses this fallback entirely.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public SixSigmaAuditReport calculateProjectSixSigmaAudit(UUID projectId) {
        if (projectId == null) {
            projectId = getActiveProjectId();
        }
        return calculateSixSigmaAuditInternal(projectId);
    }

    /**
     * Layer 3 "Product" number: one эпик's own shipped-work quality only (quality gates + PR conflicts,
     * no onboarding findings, no runtime anomalies - those are process/platform signals, not product
     * defects). Reuses the exact per-эпик category counts ProjectTreeService already computes for the
     * dashboard tree, so the tree's per-epic sigma and this endpoint's product-layer sigma can never
     * silently diverge into two different formulas for "the same" number.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public SixSigmaAuditReport calculateFeatureSixSigmaAudit(UUID projectId, UUID featureId) {
        DefectOpportunityCount qgCounts = computeQualityGateCounts(null, featureId);
        DefectOpportunityCount prCounts = computePrConflictCounts(null, featureId);
        long totalOpportunities = qgCounts.opportunities() + prCounts.opportunities();
        long totalDefects = qgCounts.defects() + prCounts.defects();
        if (totalOpportunities == 0) {
            totalOpportunities = 10; // same "fresh subgroup" baseline ProcessControlService's u-chart uses
        }
        double dpmo = calculateDpmo(totalDefects, totalOpportunities);
        double sigmaLevel = calculateSigmaLevel(dpmo);
        double yieldRatePercent = Math.max(0.0, ((double) (totalOpportunities - totalDefects) / totalOpportunities) * 100.0);

        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("prConflicts", Map.of("opportunities", prCounts.opportunities(), "defects", prCounts.defects(),
                "dpmo", calculateDpmo(prCounts.defects(), prCounts.opportunities())));
        breakdown.put("qualityGateChecks", Map.of("opportunities", qgCounts.opportunities(), "defects", qgCounts.defects(),
                "dpmo", calculateDpmo(qgCounts.defects(), qgCounts.opportunities())));

        String projectName = projectId != null
                ? projectRepository.findById(projectId).map(com.eneik.production.models.persistence.ProjectEntity::getName)
                        .orElse("PROJECT_" + projectId.toString().substring(0, 8))
                : "UNKNOWN_PROJECT";

        return new SixSigmaAuditReport(
                projectId, projectName, totalOpportunities, totalDefects,
                Math.round(dpmo * 100.0) / 100.0, Math.round(yieldRatePercent * 100.0) / 100.0,
                Math.round(sigmaLevel * 100.0) / 100.0, getQualityTier(sigmaLevel),
                breakdown, Map.of(), Instant.now());
    }

    /**
     * Layer 3 "Product" number for a whole project: sums {@link #calculateFeatureSixSigmaAudit} across
     * every one of the project's non-dismissed features - real shipped-epic quality only, no process
     * waste (dismissed/duplicate epics never counted, per {@code findByProjectIdAndDismissedAtIsNull})
     * and no platform noise (onboarding findings, runtime anomalies) mixed in, unlike the Layer 2 number.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public SixSigmaAuditReport calculateProductLayerSixSigmaAudit(UUID projectId) {
        var features = projectId != null ? featureRepository.findByProjectIdAndDismissedAtIsNull(projectId) : List.<com.eneik.production.models.persistence.FeatureEntity>of();
        long totalOpportunities = 0;
        long totalDefects = 0;
        long prOpp = 0, prDef = 0, qgOpp = 0, qgDef = 0;
        for (var feature : features) {
            DefectOpportunityCount qg = computeQualityGateCounts(null, feature.getId());
            DefectOpportunityCount pr = computePrConflictCounts(null, feature.getId());
            qgOpp += qg.opportunities(); qgDef += qg.defects();
            prOpp += pr.opportunities(); prDef += pr.defects();
        }
        totalOpportunities = qgOpp + prOpp;
        totalDefects = qgDef + prDef;
        if (totalOpportunities == 0) {
            totalOpportunities = 10;
        }
        double dpmo = calculateDpmo(totalDefects, totalOpportunities);
        double sigmaLevel = calculateSigmaLevel(dpmo);
        double yieldRatePercent = Math.max(0.0, ((double) (totalOpportunities - totalDefects) / totalOpportunities) * 100.0);

        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("prConflicts", Map.of("opportunities", prOpp, "defects", prDef, "dpmo", calculateDpmo(prDef, prOpp)));
        breakdown.put("qualityGateChecks", Map.of("opportunities", qgOpp, "defects", qgDef, "dpmo", calculateDpmo(qgDef, qgOpp)));

        String projectName = projectId != null
                ? projectRepository.findById(projectId).map(com.eneik.production.models.persistence.ProjectEntity::getName)
                        .orElse("PROJECT_" + projectId.toString().substring(0, 8))
                : "UNKNOWN_PROJECT";

        return new SixSigmaAuditReport(
                projectId, projectName, totalOpportunities, totalDefects,
                Math.round(dpmo * 100.0) / 100.0, Math.round(yieldRatePercent * 100.0) / 100.0,
                Math.round(sigmaLevel * 100.0) / 100.0, getQualityTier(sigmaLevel),
                breakdown, Map.of("shippedEpicCount", features.size()), Instant.now());
    }

    private SixSigmaAuditReport calculateSixSigmaAuditInternal(UUID projectId) {
        final UUID targetProjectId = projectId;

        // 1. Category A: PR Merge & Conflict Opportunities
        DefectOpportunityCount prCounts = computePrConflictCounts(targetProjectId, null);
        long conflictDefects = prCounts.defects();
        long prOpportunities = prCounts.opportunities();

        // 2. Category B: Quality Gate Checks
        DefectOpportunityCount qgCounts = computeQualityGateCounts(targetProjectId, null);
        long qgDefects = qgCounts.defects();
        long qgOpportunities = qgCounts.opportunities();

        // 3. Category C: Onboarding Audit Findings
        List<OnboardingAuditFindingEntity> onboardingFindings = onboardingAuditFindingRepository.findAll();
        if (targetProjectId != null) {
            onboardingFindings = onboardingFindings.stream()
                    .filter(f -> f.getProject() != null && targetProjectId.equals(f.getProject().getId()))
                    .toList();
        }
        long onboardingOpportunities = Math.max(onboardingFindings.size() * 5L, targetProjectId == null ? 20L : 5L);
        long onboardingDefects = onboardingFindings.size();

        // 4. Category D: TOC Sentinel Runtime Execution Anomalies
        var tocAnomalies = tocSentinelService.getRecentAnomalies();
        long tocOpportunities = Math.max(tocSentinelService.getGraph().getCompletedCountAllNodes() * 2L, targetProjectId == null ? 50L : 10L);
        long tocDefects = tocAnomalies.size();

        // Totals
        long totalOpportunities = prOpportunities + qgOpportunities + onboardingOpportunities + (targetProjectId == null ? tocOpportunities : 0L);
        long totalDefects = conflictDefects + qgDefects + onboardingDefects + (targetProjectId == null ? tocDefects : 0L);

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
        if (targetProjectId == null) {
            breakdown.put("runtimeAnomalies", Map.of("opportunities", tocOpportunities, "defects", tocDefects, "dpmo", calculateDpmo(tocDefects, tocOpportunities)));
        }

        Map<String, Object> tocMetrics = new LinkedHashMap<>();
        var status = tocSentinelService.getDbrStatus();
        tocMetrics.put("primaryConstraint", status.primaryConstraintNode());
        tocMetrics.put("constraintQueueLength", status.constraintQueueLength());
        tocMetrics.put("constraintUtilizationPercent", Math.round(status.constraintUtilization() * 1000.0) / 10.0);
        tocMetrics.put("ropeThrottlingActive", status.ropeThrottlingActive());
        tocMetrics.put("anomaliesCount", tocAnomalies.size());

        String projectName = "FACTORY_WIDE_ALL_PROJECTS";
        if (targetProjectId != null) {
            projectName = projectRepository.findById(targetProjectId)
                    .map(com.eneik.production.models.persistence.ProjectEntity::getName)
                    .orElse("PROJECT_" + targetProjectId.toString().substring(0, 8));
        }

        SixSigmaAuditReport report = new SixSigmaAuditReport(
                targetProjectId,
                projectName,
                totalOpportunities,
                totalDefects,
                Math.round(dpmo * 100.0) / 100.0,
                Math.round(yieldRatePercent * 100.0) / 100.0,
                Math.round(sigmaLevel * 100.0) / 100.0,
                qualityTier,
                breakdown,
                tocMetrics,
                Instant.now()
        );

        log.info("[SIX-SIGMA-AUDIT] Audit Completed | Scope: {} | DPMO: {} | Yield: {}% | Sigma Level: {} (Tier: {}) | Defects: {}/{}",
                projectName, report.dpmo(), report.yieldRatePercent(), report.sigmaLevel(), report.qualityTier(), report.totalDefects(), report.totalOpportunities());

        return report;
    }

    /**
     * u₁/u₂ raw counts (Layer 1 of the unified Lean/TOC/Six Sigma system, 2026-08-01). Shared by both
     * the project-wide audit above and ProcessControlService's per-эпик u-chart - same computation,
     * narrower scope, never duplicated. Exactly one of projectId/featureId should be non-null: featureId
     * takes priority (эпик is the u-chart subgroup - within one project, never mixed across projects).
     */
    public record DefectOpportunityCount(long defects, long opportunities) {}

    public DefectOpportunityCount computeQualityGateCounts(UUID projectId, UUID featureId) {
        List<TaskEntity> tasks;
        if (featureId != null) {
            tasks = taskRepository.findByFeatureId(featureId);
        } else {
            tasks = taskRepository.findAll();
            if (projectId != null) {
                tasks = tasks.stream()
                        .filter(t -> t.getProject() != null && projectId.equals(t.getProject().getId()))
                        .toList();
            }
        }

        long opportunities = 0;
        long defects = 0;
        for (TaskEntity task : tasks) {
            JsonNode report = task.getQualityGateReport();
            if (report != null && report.has("checks")) {
                JsonNode checks = report.get("checks");
                opportunities += checks.size();
                for (JsonNode check : checks) {
                    if (!check.path("passed").asBoolean(true)) {
                        defects++;
                    }
                }
            }
        }
        return new DefectOpportunityCount(defects, opportunities);
    }

    public DefectOpportunityCount computePrConflictCounts(UUID projectId, UUID featureId) {
        List<PrReviewEntity> reviews = prReviewRepository.findAll();
        List<TaskConflictEntity> conflicts = taskConflictRepository.findAll();

        if (featureId != null) {
            reviews = reviews.stream()
                    .filter(r -> {
                        if (r.getJulesSessionId() == null) return false;
                        var sessionOpt = julesSessionRepository.findById(r.getJulesSessionId());
                        if (sessionOpt.isEmpty()) return false;
                        var taskOpt = taskRepository.findById(sessionOpt.get().getTaskId());
                        return taskOpt.isPresent() && featureId.equals(taskOpt.get().getFeatureId());
                    })
                    .toList();
            conflicts = conflicts.stream()
                    .filter(c -> c.getTask() != null && featureId.equals(c.getTask().getFeatureId()))
                    .toList();
        } else if (projectId != null) {
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
        return new DefectOpportunityCount(conflictDefects, mergedPrs + conflictDefects);
    }

    // Public static (2026-08-02): reused as-is by ProjectTreeService for per-feature DPMO - same
    // formula, not duplicated.
    public static double calculateDpmo(long defects, long opportunities) {
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
