package com.eneik.production.services.audit;

import com.eneik.production.models.persistence.OnboardingAuditFindingEntity;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.TaskConflictEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.OnboardingAuditFindingRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.lever.LeverAgreement;
import com.eneik.production.services.lever.LeverPromotionService;
import com.eneik.production.services.lever.LeverStage;
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
    private final com.eneik.production.repositories.CodeIntegrityFindingRepository codeIntegrityFindingRepository;
    private final com.eneik.production.repositories.CapabilityObservationRepository capabilityObservationRepository;
    private final com.eneik.production.repositories.FalsificationRunRepository falsificationRunRepository;
    private final LeverPromotionService leverPromotionService;

    public static final String P1_ROLE_DRIFT_EWMA = "P1_ROLE_DRIFT_EWMA";

    public SixSigmaAuditService(PrReviewRepository prReviewRepository,
                                TaskConflictRepository taskConflictRepository,
                                TaskRepository taskRepository,
                                OnboardingAuditFindingRepository onboardingAuditFindingRepository,
                                com.eneik.production.repositories.CapabilityObservationRepository capabilityObservationRepository,
                                com.eneik.production.repositories.ProjectRepository projectRepository,
                                com.eneik.production.repositories.JulesSessionRepository julesSessionRepository,
                                TocSentinelService tocSentinelService,
                                com.eneik.production.repositories.FeatureRepository featureRepository,
                                com.eneik.production.repositories.CodeIntegrityFindingRepository codeIntegrityFindingRepository,
                                com.eneik.production.repositories.FalsificationRunRepository falsificationRunRepository,
                                LeverPromotionService leverPromotionService) {
        this.prReviewRepository = prReviewRepository;
        this.taskConflictRepository = taskConflictRepository;
        this.taskRepository = taskRepository;
        this.onboardingAuditFindingRepository = onboardingAuditFindingRepository;
        this.capabilityObservationRepository = capabilityObservationRepository;
        this.projectRepository = projectRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.tocSentinelService = tocSentinelService;
        this.featureRepository = featureRepository;
        this.codeIntegrityFindingRepository = codeIntegrityFindingRepository;
        this.falsificationRunRepository = falsificationRunRepository;
        this.leverPromotionService = leverPromotionService;
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
        DefectOpportunityCount ciCounts = computeCodeIntegrityFindingCounts(projectId, featureId);
        long totalOpportunities = qgCounts.opportunities() + prCounts.opportunities() + ciCounts.opportunities();
        long totalDefects = qgCounts.defects() + prCounts.defects() + ciCounts.defects();
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
        breakdown.put("codeIntegrityFindings", Map.of("opportunities", ciCounts.opportunities(), "defects", ciCounts.defects(),
                "dpmo", calculateDpmo(ciCounts.defects(), ciCounts.opportunities())));

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
        // Code-integrity findings are NOT summed per-feature here like the two categories above - see
        // computeCodeIntegrityFindingCounts's own javadoc: "opportunities" for this category is per-RUN,
        // and a single audit run's diff can span multiple features, so summing the per-feature call across
        // this loop would double-count any run whose findings landed in more than one feature. Called once,
        // directly, with featureId=null - this naturally includes every attributed finding (regardless of
        // which feature) plus every unattributed one, each counted against its own run exactly once.
        DefectOpportunityCount ciCounts = computeCodeIntegrityFindingCounts(projectId, null);
        // 2026-08-21: the fourth category, and the only one observed on the running product rather than
        // read out of the factory's own records. See computeCapabilityObservationCounts.
        DefectOpportunityCount capCounts = computeCapabilityObservationCounts(projectId);
        totalOpportunities = qgOpp + prOpp + ciCounts.opportunities() + capCounts.opportunities();
        totalDefects = qgDef + prDef + ciCounts.defects() + capCounts.defects();
        if (totalOpportunities == 0) {
            totalOpportunities = 10;
        }
        double dpmo = calculateDpmo(totalDefects, totalOpportunities);
        double sigmaLevel = calculateSigmaLevel(dpmo);
        double yieldRatePercent = Math.max(0.0, ((double) (totalOpportunities - totalDefects) / totalOpportunities) * 100.0);

        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("prConflicts", Map.of("opportunities", prOpp, "defects", prDef, "dpmo", calculateDpmo(prDef, prOpp)));
        breakdown.put("qualityGateChecks", Map.of("opportunities", qgOpp, "defects", qgDef, "dpmo", calculateDpmo(qgDef, qgOpp)));
        breakdown.put("codeIntegrityFindings", Map.of("opportunities", ciCounts.opportunities(), "defects", ciCounts.defects(),
                "dpmo", calculateDpmo(ciCounts.defects(), ciCounts.opportunities())));
        breakdown.put("capabilityObservations", Map.of("opportunities", capCounts.opportunities(), "defects", capCounts.defects(),
                "dpmo", calculateDpmo(capCounts.defects(), capCounts.opportunities())));

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

    /**
     * The one defect category whose witness is outside the factory.
     *
     * 2026-08-21: the Layer 3 "Product" number already separates itself correctly from Layer 2 - it counts
     * only non-dismissed features and deliberately excludes onboarding findings and runtime anomalies as
     * platform noise. What it could not do was draw a defect from the product actually running: all three
     * of its categories - quality gates, PR conflicts, code-integrity findings - are records THIS FACTORY
     * created about its own work, inspected at build time. That is Charter invariant 12 at the level of the
     * measure itself: the entity producing the result was the only source confirming it.
     *
     * A capability observation is one probe of one route the product's own OpenAPI contract DECLARES,
     * performed by the launcher against the running instance. Opportunity = one such probe; defect = the
     * declared capability did not answer. Added as a fourth category here rather than as a second DPMO
     * elsewhere, so "the product's sigma" stays one number computed in one place.
     */
    public DefectOpportunityCount computeCapabilityObservationCounts(UUID projectId) {
        if (projectId == null) {
            return new DefectOpportunityCount(0, 0);
        }
        long opportunities = 0;
        long defects = 0;
        for (var row : capabilityObservationRepository.findByProjectIdOrderByObservedAtDesc(projectId)) {
            opportunities++;
            if (!row.isSatisfied()) {
                defects++;
            }
        }
        return new DefectOpportunityCount(defects, opportunities);
    }

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

    /** One quality-gate check type's own Pareto contribution - checkName is GateResult.checkName() (see GateOrchestrator). */
    public record CtqEntry(String checkName, long defects, long opportunities) {}

    /**
     * 2026-08-08 (ML-update patch, Phase 1 / lever F1_KAIZEN_CTQ_TARGETING): per-check-name breakdown of
     * computeQualityGateCounts' same underlying data - previously only computed inline inside
     * SystemStatusService.qualityGate's dashboard JSON assembly (ctqBreakdown), never exposed as a reusable
     * query. KaizenService's DEFECT_ELIMINATION proposal used to name its target generically ("QualityGate")
     * even though ~91% of factory-wide defects concentrate in a small number of specific checks (Sober's
     * parsimony/AIC-BIC, BARCAN-TAG-04 philosopher 6: the simplest explanation of a DPMO spike sufficient to
     * act on is the single dominant check, not "everything"). SystemStatusService.qualityGate is expected to
     * be migrated onto this method too so the two never silently diverge (engineering invariant #14).
     */
    public List<CtqEntry> computeCtqBreakdown(UUID projectId) {
        List<TaskEntity> tasks = taskRepository.findAll();
        if (projectId != null) {
            tasks = tasks.stream()
                    .filter(t -> t.getProject() != null && projectId.equals(t.getProject().getId()))
                    .toList();
        }

        Map<String, long[]> counts = new LinkedHashMap<>(); // checkName -> [defects, opportunities]
        for (TaskEntity task : tasks) {
            JsonNode report = task.getQualityGateReport();
            if (report == null || !report.has("checks")) {
                continue;
            }
            for (JsonNode check : report.get("checks")) {
                String checkName = check.path("name").asText("unknown_check");
                long[] entry = counts.computeIfAbsent(checkName, k -> new long[2]);
                entry[1]++;
                if (!check.path("passed").asBoolean(true)) {
                    entry[0]++;
                }
            }
        }

        return counts.entrySet().stream()
                .map(e -> new CtqEntry(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted((a, b) -> Long.compare(b.defects(), a.defects()))
                .toList();
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

    /**
     * Code-integrity defect category (2026-08-05): stub code / architectural layer violations found by
     * the methodological falsification audit ({@link com.eneik.production.services.FalsificationCycleService
     * #applyAuditViolations}), attributed to the real feature a finding's cited PR belongs to. Unlike
     * {@link #computeQualityGateCounts} / {@link #computePrConflictCounts} above - both inherently
     * per-task/per-feature quantities with no cross-feature overlap - "opportunities" here is a PER-RUN
     * quantity (one audit invocation checks every active role charter at once, not per feature). Summed
     * once per DISTINCT falsification run referenced by these findings, never once per finding, so a run
     * whose findings landed in two different roles/features never has its own roles-checked count counted
     * twice. Callers must NOT sum this method's per-feature result across a feature loop to get a
     * project-wide total (that would double-count any run spanning multiple features) - call with
     * featureId=null directly instead, as {@link #calculateProductLayerSixSigmaAudit} does.
     */
    public DefectOpportunityCount computeCodeIntegrityFindingCounts(UUID projectId, UUID featureId) {
        List<com.eneik.production.models.persistence.CodeIntegrityFindingEntity> findings = featureId != null
                ? codeIntegrityFindingRepository.findByFeatureId(featureId)
                : (projectId != null ? codeIntegrityFindingRepository.findByProjectId(projectId) : List.of());
        long defects = findings.size();
        Set<UUID> runIds = findings.stream()
                .map(com.eneik.production.models.persistence.CodeIntegrityFindingEntity::getFalsificationRunId)
                .collect(java.util.stream.Collectors.toSet());
        long opportunities = falsificationRunRepository.findAllById(runIds).stream()
                .mapToLong(com.eneik.production.models.persistence.FalsificationRunEntity::getRolesCheckedCount)
                .sum();
        return new DefectOpportunityCount(defects, opportunities);
    }

    /** One role's ems_defect_weight trend within a single project's own history - see detectRoleDefectWeightDrift. */
    public record RoleQualityDrift(String roleTag, double historicalAverage, double recentAverage,
                                    int historicalSampleSize, int recentSampleSize) {
    }

    private static final int DRIFT_MIN_SAMPLES_PER_SIDE = 3;
    // Recent-half average must be at least 50% higher than the earlier-half average to count as a real
    // trend, not sampling noise - deliberately a ratio, not an absolute delta, since defectWeight's own
    // scale (TechnicalLeadCompiler.defectWeight) already varies by role/cynefin.
    private static final double DRIFT_RATIO_THRESHOLD = 1.5;

    // 2026-08-08 (ML-update patch, Phase 6 / lever P1_ROLE_DRIFT_EWMA): standard Six Sigma EWMA control-
    // chart parameters - lambda=0.2 (smoothing weight on the newest sample) and L=3 (3-sigma control limit),
    // both textbook defaults, not tuned for this project specifically. Sellars' "myth of the given"
    // (BARCAN-TAG-09 philosopher 5): the raw ems_defect_weight numbers don't self-interpret - a control
    // limit is a constructed inferential frame, documented explicitly here rather than treated as objectively
    // self-evident, same as the ratio threshold above.
    private static final double EWMA_LAMBDA = 0.2;
    private static final double EWMA_L_FACTOR = 3.0;

    /**
     * 2026-08-07 (Kaizen/DMAIC Control-phase wiring): ems_defect_weight has been computed per-task at
     * compile time (TechnicalLeadCompiler.criticalityScore) and persisted into every task's own payload
     * since the EMS metadata framework was built - but the persisted field itself was never read back
     * anywhere (confirmed live audit, 2026-08-07): real effort spent computing and storing a genuine
     * quality signal, then never actually looked at again. This is the "Measure" half of DMAIC without a
     * "Control" half - a control chart nobody reads. Splits each role's own DONE/failed task history for
     * one project into an older and a newer half (both already ordered by createdAt) and flags a role whose
     * recent average is meaningfully higher than its own earlier average - a leading indicator that
     * something about how that role is being executed has gotten worse, not a one-off failure. Deliberately
     * scoped to ONE project's own history, not cross-project: a role's inherent difficulty varies by domain,
     * comparing against ITS OWN past is the only fair baseline.
     */
    public List<RoleQualityDrift> detectRoleDefectWeightDrift(UUID projectId) {
        List<TaskEntity> terminalTasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .filter(t -> t.getStatus() == com.eneik.production.models.persistence.TaskStatus.done
                        || t.getStatus() == com.eneik.production.models.persistence.TaskStatus.failed)
                .filter(t -> t.getRole() != null && t.getPayload() != null)
                .toList();

        Map<String, List<Double>> weightsByRoleNewestFirst = new LinkedHashMap<>();
        for (TaskEntity task : terminalTasks) {
            JsonNode weightNode = task.getPayload().path("ems_defect_weight");
            if (!weightNode.isNumber()) {
                continue;
            }
            weightsByRoleNewestFirst.computeIfAbsent(task.getRole().getTag(), k -> new ArrayList<>()).add(weightNode.asDouble());
        }

        boolean useEwma = leverPromotionService.currentStage(P1_ROLE_DRIFT_EWMA).atLeast(LeverStage.SOFT_GATE);

        List<RoleQualityDrift> drifts = new ArrayList<>();
        for (Map.Entry<String, List<Double>> entry : weightsByRoleNewestFirst.entrySet()) {
            List<Double> weightsNewestFirst = entry.getValue();
            if (weightsNewestFirst.size() < DRIFT_MIN_SAMPLES_PER_SIDE * 2) {
                continue;
            }
            int half = weightsNewestFirst.size() / 2;
            List<Double> recent = weightsNewestFirst.subList(0, half);
            List<Double> historical = weightsNewestFirst.subList(half, weightsNewestFirst.size());
            double recentAvg = recent.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double historicalAvg = historical.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            boolean incumbentFlag = historicalAvg > 0.0 && recentAvg >= historicalAvg * DRIFT_RATIO_THRESHOLD;

            boolean candidateFlag = ewmaExceedsUpperControlLimit(historical, recent);
            String subjectId = projectId + ":" + entry.getKey();
            LeverAgreement agreement = incumbentFlag == candidateFlag ? LeverAgreement.TRUE : LeverAgreement.FALSE;
            leverPromotionService.recordObservation(P1_ROLE_DRIFT_EWMA, subjectId,
                    incumbentFlag ? "drift" : "no_drift", candidateFlag ? "drift" : "no_drift",
                    agreement, incumbentFlag ? "ratio_flagged_drift" : "ratio_flagged_no_drift");

            boolean effectiveFlag = useEwma ? candidateFlag : incumbentFlag;
            if (effectiveFlag) {
                drifts.add(new RoleQualityDrift(entry.getKey(), historicalAvg, recentAvg, historical.size(), recent.size()));
            }
        }
        return drifts;
    }

    /**
     * EWMA control-chart candidate (see EWMA_LAMBDA/EWMA_L_FACTOR above). Uses the historical half as the
     * reference distribution (center = its mean, sigma = its own std dev) and walks the recent half in
     * chronological order (oldest first - both input lists are newest-first, so iterated in reverse) to
     * build the smoothed statistic; flags only an UPPER breach, since a defect-weight DECREASE is not the
     * failure mode this control chart exists to catch. Uses the asymptotic (steady-state) control limit -
     * a standard, well-established simplification, not a per-sample time-varying limit - since this
     * project's sample sizes are small enough that the two are practically indistinguishable.
     */
    private boolean ewmaExceedsUpperControlLimit(List<Double> historicalNewestFirst, List<Double> recentNewestFirst) {
        double mean = historicalNewestFirst.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = historicalNewestFirst.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average().orElse(0.0);
        double sigma = Math.sqrt(variance);
        if (sigma <= 0.0) {
            return false; // no dispersion in the reference distribution - a control limit is not meaningful
        }
        double upperControlLimit = mean + EWMA_L_FACTOR * sigma * Math.sqrt(EWMA_LAMBDA / (2 - EWMA_LAMBDA));

        double z = mean;
        List<Double> recentOldestFirst = new ArrayList<>(recentNewestFirst);
        java.util.Collections.reverse(recentOldestFirst);
        for (double x : recentOldestFirst) {
            z = EWMA_LAMBDA * x + (1 - EWMA_LAMBDA) * z;
        }
        return z > upperControlLimit;
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
