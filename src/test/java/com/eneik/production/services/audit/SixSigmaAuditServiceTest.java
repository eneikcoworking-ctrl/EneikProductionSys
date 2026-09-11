package com.eneik.production.services.audit;

import com.eneik.production.models.persistence.CodeIntegrityFindingEntity;
import com.eneik.production.models.persistence.FalsificationRunEntity;
import com.eneik.production.models.persistence.FeatureEntity;
import com.eneik.production.repositories.CodeIntegrityFindingRepository;
import com.eneik.production.repositories.FalsificationRunRepository;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.OnboardingAuditFindingRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.toc.engine.TocAnomalyDetector;
import com.eneik.production.toc.engine.TocExecutionGraph;
import com.eneik.production.toc.engine.TocOptimizer;
import com.eneik.production.toc.service.TocSentinelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SixSigmaAuditServiceTest {

    private PrReviewRepository prReviewRepository;
    private TaskConflictRepository taskConflictRepository;
    private TaskRepository taskRepository;
    private OnboardingAuditFindingRepository onboardingAuditFindingRepository;
    private TocSentinelService tocSentinelService;
    private FeatureRepository featureRepository;
    private CodeIntegrityFindingRepository codeIntegrityFindingRepository;
    private FalsificationRunRepository falsificationRunRepository;
    private com.eneik.production.services.lever.LeverPromotionService leverPromotionService;
    private ProjectRepository projectRepository;
    private JulesSessionRepository julesSessionRepository;

    private SixSigmaAuditService auditService;

    @BeforeEach
    void setUp() {
        prReviewRepository = mock(PrReviewRepository.class);
        taskConflictRepository = mock(TaskConflictRepository.class);
        taskRepository = mock(TaskRepository.class);
        onboardingAuditFindingRepository = mock(OnboardingAuditFindingRepository.class);
        featureRepository = mock(FeatureRepository.class);
        codeIntegrityFindingRepository = mock(CodeIntegrityFindingRepository.class);
        falsificationRunRepository = mock(FalsificationRunRepository.class);
        projectRepository = mock(ProjectRepository.class);
        julesSessionRepository = mock(JulesSessionRepository.class);

        TocExecutionGraph graph = new TocExecutionGraph();
        TocAnomalyDetector anomalyDetector = new TocAnomalyDetector(graph);
        TocOptimizer optimizer = new TocOptimizer(graph);
        tocSentinelService = new TocSentinelService(graph, anomalyDetector, optimizer);

        when(prReviewRepository.findAll()).thenReturn(Collections.emptyList());
        when(prReviewRepository.countByMergedTrue()).thenReturn(0L);
        when(prReviewRepository.findByJulesSessionIdInAndMergedTrue(any())).thenReturn(Collections.emptyList());

        when(taskConflictRepository.findAll()).thenReturn(Collections.emptyList());
        when(taskConflictRepository.count()).thenReturn(0L);
        when(taskConflictRepository.findByTaskIdIn(any())).thenReturn(Collections.emptyList());

        when(taskRepository.findAll()).thenReturn(Collections.emptyList());

        when(onboardingAuditFindingRepository.findAll()).thenReturn(Collections.emptyList());
        when(onboardingAuditFindingRepository.count()).thenReturn(0L);
        when(onboardingAuditFindingRepository.countByProjectId(any())).thenReturn(0L);

        when(codeIntegrityFindingRepository.findByProjectId(any())).thenReturn(Collections.emptyList());
        when(codeIntegrityFindingRepository.findByFeatureId(any())).thenReturn(Collections.emptyList());
        when(falsificationRunRepository.findAllById(any())).thenReturn(Collections.emptyList());
        when(julesSessionRepository.findByTaskIdIn(any())).thenReturn(Collections.emptyList());
        when(projectRepository.findByStatusOrderByCreatedAtDesc(any())).thenReturn(Collections.emptyList());

        leverPromotionService = mock(com.eneik.production.services.lever.LeverPromotionService.class);
        when(leverPromotionService.currentStage(any())).thenReturn(com.eneik.production.services.lever.LeverStage.OBSERVE_ONLY);

        auditService = new SixSigmaAuditService(
                prReviewRepository,
                taskConflictRepository,
                taskRepository,
                onboardingAuditFindingRepository,
                mock(com.eneik.production.repositories.CapabilityObservationRepository.class),
                projectRepository,
                julesSessionRepository,
                tocSentinelService,
                featureRepository,
                codeIntegrityFindingRepository,
                falsificationRunRepository,
                leverPromotionService
        );
    }

    /**
     * Guard for "a reference is not its referent" (2026-08-29, plan §4.26).
     *
     * <p>The task behind a TaskConflictEntity is a LAZY proxy: it is never null, and reading any field
     * other than getId() loads the row. This scoping filter used to read getProject()/getFeatureId(), so
     * it asked whether a REFERENCE was present rather than whether its REFERENT exists - and on the live
     * circuit the 2-hour Kaizen cycle died with EntityNotFoundException on every run, against 92 conflict
     * rows whose tasks are gone.
     *
     * <p>The proxy here refuses every field but getId(), exactly as Hibernate's does for a missing row.
     * The count must still be produced, and it must be produced from identity alone.
     */
    @Test
    void conflictScopingUsesTaskIdentityAndNeverLoadsTheTaskBehindTheProxy() {
        UUID projectId = UUID.randomUUID();
        UUID liveTaskId = UUID.randomUUID();

        com.eneik.production.models.persistence.TaskEntity liveTask =
                new com.eneik.production.models.persistence.TaskEntity();
        liveTask.setId(liveTaskId);
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(liveTask));

        com.eneik.production.models.persistence.TaskEntity proxyOfLiveTask =
                mock(com.eneik.production.models.persistence.TaskEntity.class);
        when(proxyOfLiveTask.getId()).thenReturn(liveTaskId);
        when(proxyOfLiveTask.getProject()).thenThrow(new jakarta.persistence.EntityNotFoundException("proxy must not be loaded"));
        when(proxyOfLiveTask.getFeatureId()).thenThrow(new jakarta.persistence.EntityNotFoundException("proxy must not be loaded"));

        com.eneik.production.models.persistence.TaskEntity proxyOfDeletedTask =
                mock(com.eneik.production.models.persistence.TaskEntity.class);
        when(proxyOfDeletedTask.getId()).thenReturn(UUID.randomUUID());
        when(proxyOfDeletedTask.getProject()).thenThrow(new jakarta.persistence.EntityNotFoundException("task no longer exists"));

        com.eneik.production.models.persistence.TaskConflictEntity inScope =
                new com.eneik.production.models.persistence.TaskConflictEntity();
        inScope.setTask(proxyOfLiveTask);
        com.eneik.production.models.persistence.TaskConflictEntity orphan =
                new com.eneik.production.models.persistence.TaskConflictEntity();
        orphan.setTask(proxyOfDeletedTask);
        when(taskConflictRepository.findByTaskIdIn(List.of(liveTaskId))).thenReturn(List.of(inScope));

        SixSigmaAuditService.DefectOpportunityCount counts = auditService.computePrConflictCounts(projectId, null);

        // The conflict on the project's own task counts; the one whose task cannot be found belongs to no
        // project and so counts nowhere.
        assertThat(counts.defects()).isEqualTo(1);
        verify(taskConflictRepository, never()).findAll();
        verify(prReviewRepository, never()).findAll();

        // 4-arg overload compatibility check: verifies proxy resistance when caller passes in-memory lists
        SixSigmaAuditService.DefectOpportunityCount fourArgCounts =
                auditService.computePrConflictCounts(projectId, null, List.of(), List.of(inScope, orphan));
        assertThat(fourArgCounts.defects()).isEqualTo(1);
    }

    @Test
    void testSigmaLevelCalculationFormula() {
        // 3.4 DPMO = 6.0 Sigma
        assertThat(SixSigmaAuditService.calculateSigmaLevel(3.4)).isEqualTo(6.0);

        // 233 DPMO = 5.0 Sigma
        assertThat(SixSigmaAuditService.calculateSigmaLevel(233)).isEqualTo(5.0);

        // 6210 DPMO = 4.0 Sigma
        assertThat(SixSigmaAuditService.calculateSigmaLevel(6210)).isEqualTo(4.0);

        // 66807 DPMO = 3.0 Sigma
        assertThat(SixSigmaAuditService.calculateSigmaLevel(66807)).isEqualTo(3.0);
    }

    @Test
    void testFullAuditExecutionWithZeroDefects() {
        var report = auditService.calculateFullSixSigmaAudit();

        assertThat(report).isNotNull();
        assertThat(report.totalDefects()).isEqualTo(0);
        assertThat(report.dpmo()).isEqualTo(0.0);
        assertThat(report.yieldRatePercent()).isEqualTo(100.0);
        assertThat(report.sigmaLevel()).isEqualTo(6.0);
        assertThat(report.qualityTier()).isEqualTo("WORLD_CLASS_SIX_SIGMA");
    }

    // --- 3-layer Factory/Delivery/Product model (2026-08-04) ------------------------------------------

    @Test
    void factoryLayerIsGenuinelyCrossProjectNotAnAliasForOneActiveProject() {
        // Regression test for the real bug this refactor fixed: calculateFullSixSigmaAudit() used to
        // call calculateProjectSixSigmaAudit(getActiveProjectId()), which coerced a null projectId to a
        // specific project BEFORE the factory-wide (targetProjectId==null) branches ever ran - those
        // branches were unreachable dead code. Now it must call the internal calc with a real null.
        var report = auditService.calculateFullSixSigmaAudit();

        assertThat(report.projectId()).isNull();
        assertThat(report.projectName()).isEqualTo("FACTORY_WIDE_ALL_PROJECTS");
        // Runtime anomalies (Category D) are only ever counted when targetProjectId is genuinely null -
        // their presence here is proof the factory-wide branch actually ran.
        assertThat(report.defectBreakdown()).containsKey("runtimeAnomalies");
    }

    @Test
    void deliveryLayerStaysScopedToOneProjectAndExcludesRuntimeAnomalies() {
        UUID projectId = UUID.randomUUID();

        var report = auditService.calculateProjectSixSigmaAudit(projectId);

        assertThat(report.projectId()).isEqualTo(projectId);
        // Layer 2 "Delivery" deliberately never mixes in factory-wide runtime anomalies - that's Layer 1
        // only, per calculateSixSigmaAuditInternal's targetProjectId==null guard.
        assertThat(report.defectBreakdown()).doesNotContainKey("runtimeAnomalies");
    }

    @Test
    void productLayerSumsOnlyNonDismissedFeaturesAndReportsShippedEpicCount() {
        UUID projectId = UUID.randomUUID();
        FeatureEntity feature1 = new FeatureEntity();
        feature1.setId(UUID.randomUUID());
        FeatureEntity feature2 = new FeatureEntity();
        feature2.setId(UUID.randomUUID());
        when(featureRepository.findByProjectIdAndDismissedAtIsNull(projectId)).thenReturn(List.of(feature1, feature2));
        when(taskRepository.findByFeatureId(feature1.getId())).thenReturn(Collections.emptyList());
        when(taskRepository.findByFeatureId(feature2.getId())).thenReturn(Collections.emptyList());

        var report = auditService.calculateProductLayerSixSigmaAudit(projectId);

        assertThat(report.projectId()).isEqualTo(projectId);
        assertThat(report.tocOperationalMetrics()).containsEntry("shippedEpicCount", 2);
        // A dismissed feature was never in the featureRepository stub above, so it can't have
        // contributed - the query itself (findByProjectIdAndDismissedAtIsNull) is what enforces this.
    }

    @Test
    void productLayerWithNoFeaturesIsNotApplicableNotZeroDefects() {
        UUID projectId = UUID.randomUUID();
        when(featureRepository.findByProjectIdAndDismissedAtIsNull(projectId)).thenReturn(Collections.emptyList());
        // Explicit, not just relying on the @BeforeEach default: a project with no falsification run
        // history yet must resolve to the same honest "not yet applicable" 6.0 baseline as every other
        // fresh category, never a fabricated defect-free reading.
        when(codeIntegrityFindingRepository.findByProjectId(projectId)).thenReturn(Collections.emptyList());

        var report = auditService.calculateProductLayerSixSigmaAudit(projectId);

        assertThat(report.tocOperationalMetrics()).containsEntry("shippedEpicCount", 0);
        assertThat(report.sigmaLevel()).isEqualTo(6.0);
    }

    // --- Code-integrity findings (stub/layer_violation detection, 2026-08-05) -------------------------

    @Test
    void featureLayerGainsCodeIntegrityFindingsWhenPresentForThatFeature() {
        UUID projectId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        FalsificationRunEntity run = new FalsificationRunEntity();
        run.setId(runId);
        run.setRolesCheckedCount(13);

        CodeIntegrityFindingEntity finding = new CodeIntegrityFindingEntity();
        finding.setId(UUID.randomUUID());
        finding.setProjectId(projectId);
        finding.setFeatureId(featureId);
        finding.setFalsificationRunId(runId);
        finding.setFindingType("stub");
        finding.setReason("fakes success, no real work");

        when(codeIntegrityFindingRepository.findByFeatureId(featureId)).thenReturn(List.of(finding));
        when(falsificationRunRepository.findAllById(java.util.Set.of(runId))).thenReturn(List.of(run));

        var report = auditService.calculateFeatureSixSigmaAudit(projectId, featureId);

        assertThat(report.defectBreakdown()).containsKey("codeIntegrityFindings");
        @SuppressWarnings("unchecked")
        var ciBreakdown = (java.util.Map<String, Object>) report.defectBreakdown().get("codeIntegrityFindings");
        assertThat(ciBreakdown).containsEntry("defects", 1L).containsEntry("opportunities", 13L);
    }

    @Test
    void productLayerCountsARunOnceEvenWhenItsFindingsSpanTwoFeatures() {
        // Regression guard for the Option-2-rejected/Option-3-adopted design: opportunities for this
        // category are PER-RUN (one audit checks every active role charter at once), not per-finding or
        // per-feature - a single run whose findings landed in two different features must contribute its
        // rolesCheckedCount to the Product-wide total exactly once, never once per feature it touched.
        UUID projectId = UUID.randomUUID();
        UUID featureA = UUID.randomUUID();
        UUID featureB = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        FalsificationRunEntity run = new FalsificationRunEntity();
        run.setId(runId);
        run.setRolesCheckedCount(13);

        CodeIntegrityFindingEntity findingA = new CodeIntegrityFindingEntity();
        findingA.setId(UUID.randomUUID());
        findingA.setProjectId(projectId);
        findingA.setFeatureId(featureA);
        findingA.setFalsificationRunId(runId);
        findingA.setFindingType("stub");
        findingA.setReason("fake handler in feature A");

        CodeIntegrityFindingEntity findingB = new CodeIntegrityFindingEntity();
        findingB.setId(UUID.randomUUID());
        findingB.setProjectId(projectId);
        findingB.setFeatureId(featureB);
        findingB.setFalsificationRunId(runId);
        findingB.setFindingType("layer_violation");
        findingB.setReason("unwired integration in feature B");

        when(featureRepository.findByProjectIdAndDismissedAtIsNull(projectId)).thenReturn(Collections.emptyList());
        when(codeIntegrityFindingRepository.findByProjectId(projectId)).thenReturn(List.of(findingA, findingB));
        when(falsificationRunRepository.findAllById(java.util.Set.of(runId))).thenReturn(List.of(run));

        var report = auditService.calculateProductLayerSixSigmaAudit(projectId);

        assertThat(report.defectBreakdown()).containsKey("codeIntegrityFindings");
        @SuppressWarnings("unchecked")
        var ciBreakdown = (java.util.Map<String, Object>) report.defectBreakdown().get("codeIntegrityFindings");
        assertThat(ciBreakdown).containsEntry("defects", 2L).containsEntry("opportunities", 13L);
    }

    // --- Role defect-weight drift (2026-08-07, DMAIC Control-phase wiring) -----------------------------

    private com.eneik.production.models.persistence.TaskEntity terminalTaskWithDefectWeight(
            String roleTag, double defectWeight, com.eneik.production.models.persistence.TaskStatus status) {
        com.eneik.production.models.persistence.RoleEntity role = new com.eneik.production.models.persistence.RoleEntity();
        role.setTag(roleTag);
        com.eneik.production.models.persistence.TaskEntity task = new com.eneik.production.models.persistence.TaskEntity();
        task.setId(UUID.randomUUID());
        task.setRole(role);
        task.setStatus(status);
        task.setPayload(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("ems_defect_weight", defectWeight));
        return task;
    }

    @Test
    void detectsGenuineUpwardDriftInARolesOwnHistory() {
        UUID projectId = UUID.randomUUID();
        // taskRepository already returns newest-first (findByProjectIdOrderByCreatedAtDesc) - first 3
        // entries are the "recent" half, last 3 are the "historical" half.
        List<com.eneik.production.models.persistence.TaskEntity> tasks = List.of(
                terminalTaskWithDefectWeight("BARCAN-TAG-02", 9.0, com.eneik.production.models.persistence.TaskStatus.done),
                terminalTaskWithDefectWeight("BARCAN-TAG-02", 8.0, com.eneik.production.models.persistence.TaskStatus.failed),
                terminalTaskWithDefectWeight("BARCAN-TAG-02", 10.0, com.eneik.production.models.persistence.TaskStatus.done),
                terminalTaskWithDefectWeight("BARCAN-TAG-02", 2.0, com.eneik.production.models.persistence.TaskStatus.done),
                terminalTaskWithDefectWeight("BARCAN-TAG-02", 3.0, com.eneik.production.models.persistence.TaskStatus.done),
                terminalTaskWithDefectWeight("BARCAN-TAG-02", 1.0, com.eneik.production.models.persistence.TaskStatus.done)
        );
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(tasks);

        List<SixSigmaAuditService.RoleQualityDrift> drifts = auditService.detectRoleDefectWeightDrift(projectId);

        assertThat(drifts).hasSize(1);
        SixSigmaAuditService.RoleQualityDrift drift = drifts.get(0);
        assertThat(drift.roleTag()).isEqualTo("BARCAN-TAG-02");
        assertThat(drift.recentAverage()).isEqualTo(9.0);
        assertThat(drift.historicalAverage()).isEqualTo(2.0);

        // 2026-08-08 (ML-update patch, Phase 6): every eligible role records a P1_ROLE_DRIFT_EWMA
        // observation each cycle, regardless of whether the incumbent flags it - both mechanisms agree
        // here (real variance, real upward jump), so agreement is TRUE.
        org.mockito.Mockito.verify(leverPromotionService).recordObservation(
                org.mockito.ArgumentMatchers.eq(SixSigmaAuditService.P1_ROLE_DRIFT_EWMA),
                org.mockito.ArgumentMatchers.eq(projectId + ":BARCAN-TAG-02"),
                org.mockito.ArgumentMatchers.eq("drift"), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(com.eneik.production.services.lever.LeverAgreement.TRUE),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void promotedEwmaLeverCanOverrideTheRatioHeuristicWhenTheyDisagree() {
        UUID projectId = UUID.randomUUID();
        // Historical has real variance (mean 4, sigma ~4.24) so its EWMA control limit (~8.24) sits ABOVE
        // the recent run's steady value of 7 - the ratio heuristic (7 >= 4*1.5=6) flags drift, but the
        // more statistically rigorous EWMA does not confirm it crossed a real control limit.
        List<com.eneik.production.models.persistence.TaskEntity> tasks = List.of(
                terminalTaskWithDefectWeight("BARCAN-TAG-05", 7.0, com.eneik.production.models.persistence.TaskStatus.done),
                terminalTaskWithDefectWeight("BARCAN-TAG-05", 7.0, com.eneik.production.models.persistence.TaskStatus.done),
                terminalTaskWithDefectWeight("BARCAN-TAG-05", 7.0, com.eneik.production.models.persistence.TaskStatus.done),
                terminalTaskWithDefectWeight("BARCAN-TAG-05", 1.0, com.eneik.production.models.persistence.TaskStatus.done),
                terminalTaskWithDefectWeight("BARCAN-TAG-05", 1.0, com.eneik.production.models.persistence.TaskStatus.done),
                terminalTaskWithDefectWeight("BARCAN-TAG-05", 10.0, com.eneik.production.models.persistence.TaskStatus.done)
        );
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(tasks);

        // observe_only (default stub): incumbent ratio heuristic decides - role IS flagged.
        assertThat(auditService.detectRoleDefectWeightDrift(projectId)).hasSize(1);

        // promoted: candidate EWMA decides instead - does NOT confirm drift, role is NOT flagged.
        when(leverPromotionService.currentStage(SixSigmaAuditService.P1_ROLE_DRIFT_EWMA))
                .thenReturn(com.eneik.production.services.lever.LeverStage.SOFT_GATE);
        assertThat(auditService.detectRoleDefectWeightDrift(projectId)).isEmpty();
    }

    @Test
    void stableHistoryForARoleIsNotFlaggedAsDrift() {
        UUID projectId = UUID.randomUUID();
        List<com.eneik.production.models.persistence.TaskEntity> tasks = List.of(
                terminalTaskWithDefectWeight("BARCAN-TAG-08", 5.0, com.eneik.production.models.persistence.TaskStatus.done),
                terminalTaskWithDefectWeight("BARCAN-TAG-08", 5.0, com.eneik.production.models.persistence.TaskStatus.done),
                terminalTaskWithDefectWeight("BARCAN-TAG-08", 5.0, com.eneik.production.models.persistence.TaskStatus.done),
                terminalTaskWithDefectWeight("BARCAN-TAG-08", 5.0, com.eneik.production.models.persistence.TaskStatus.done),
                terminalTaskWithDefectWeight("BARCAN-TAG-08", 5.0, com.eneik.production.models.persistence.TaskStatus.done),
                terminalTaskWithDefectWeight("BARCAN-TAG-08", 5.0, com.eneik.production.models.persistence.TaskStatus.done)
        );
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(tasks);

        assertThat(auditService.detectRoleDefectWeightDrift(projectId)).isEmpty();
    }

    @Test
    void tooFewSamplesForARoleNeverFlaggedRegardlessOfSpread() {
        UUID projectId = UUID.randomUUID();
        List<com.eneik.production.models.persistence.TaskEntity> tasks = List.of(
                terminalTaskWithDefectWeight("BARCAN-TAG-11", 20.0, com.eneik.production.models.persistence.TaskStatus.done),
                terminalTaskWithDefectWeight("BARCAN-TAG-11", 1.0, com.eneik.production.models.persistence.TaskStatus.done)
        );
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(tasks);

        assertThat(auditService.detectRoleDefectWeightDrift(projectId)).isEmpty();
    }

    // 2026-08-08 (ML-update patch, Phase 1 / lever F1_KAIZEN_CTQ_TARGETING): computeCtqBreakdown feeds both
    // SystemStatusService's dashboard and KaizenService's targeting decision - one shared computation.

    @Test
    void computeCtqBreakdownSortsByDefectCountDescending() throws Exception {
        com.eneik.production.models.persistence.TaskEntity taskA = new com.eneik.production.models.persistence.TaskEntity();
        taskA.setQualityGateReport(new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                "{\"checks\":[{\"name\":\"unit_tests\",\"passed\":false},{\"name\":\"lint\",\"passed\":true}]}"));
        com.eneik.production.models.persistence.TaskEntity taskB = new com.eneik.production.models.persistence.TaskEntity();
        taskB.setQualityGateReport(new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                "{\"checks\":[{\"name\":\"unit_tests\",\"passed\":false},{\"name\":\"unit_tests\",\"passed\":false}]}"));
        when(taskRepository.findByQualityGateReportIsNotNull()).thenReturn(List.of(taskA, taskB));

        List<SixSigmaAuditService.CtqEntry> breakdown = auditService.computeCtqBreakdown(null);

        assertThat(breakdown).hasSize(2);
        assertThat(breakdown.get(0).checkName()).isEqualTo("unit_tests");
        assertThat(breakdown.get(0).defects()).isEqualTo(3);
        assertThat(breakdown.get(0).opportunities()).isEqualTo(3);
        assertThat(breakdown.get(1).checkName()).isEqualTo("lint");
        assertThat(breakdown.get(1).defects()).isEqualTo(0);
        verify(taskRepository, never()).findAll();
    }

    @Test
    void computeCtqBreakdownIsEmptyWhenNoTaskHasAQualityGateReport() {
        when(taskRepository.findByQualityGateReportIsNotNull()).thenReturn(List.of());

        assertThat(auditService.computeCtqBreakdown(null)).isEmpty();
        verify(taskRepository, never()).findAll();
    }

    @Test
    void computeQualityGateDefectRateCategorizesMissingPassedFieldAsUndetermined() throws Exception {
        com.eneik.production.models.persistence.TaskEntity task = new com.eneik.production.models.persistence.TaskEntity();
        task.setQualityGateReport(new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                "{\"checks\":[" +
                "{\"name\":\"unit_tests\",\"passed\":false}," +
                "{\"name\":\"lint\",\"passed\":true}," +
                "{\"name\":\"security_probe\"}" +
                "]}"));
        when(taskRepository.findByQualityGateReportIsNotNull()).thenReturn(List.of(task));

        Map<String, Object> result = auditService.computeQualityGateDefectRate(null);

        assertThat(result).isNotNull();
        assertThat(result.get("totalAttempts")).isEqualTo(1L);
        assertThat(result.get("totalOpportunities")).isEqualTo(3L);
        assertThat(result.get("defects")).isEqualTo(1L);
        assertThat(result.get("passedChecks")).isEqualTo(1L);
        assertThat(result.get("undetermined")).isEqualTo(1L);
        double expectedDpmo = (1.0 / 3.0) * 1_000_000.0;
        assertThat((double) result.get("dpmo")).isCloseTo(expectedDpmo, org.assertj.core.data.Offset.offset(0.01));
        verify(taskRepository, never()).findAll();
    }

    // --- Invariants: getActiveProjectId & Storage Query Discipline (2026-09-11) ----------------------

    @Test
    void getActiveProjectIdReturnsSingleActiveProjectAndNeverCallsFindAll() {
        UUID activeId = UUID.randomUUID();
        com.eneik.production.models.persistence.ProjectEntity activeProject =
                new com.eneik.production.models.persistence.ProjectEntity();
        activeProject.setId(activeId);
        activeProject.setStatus(com.eneik.production.models.persistence.ProjectStatus.active);

        when(projectRepository.findByStatusOrderByCreatedAtDesc(com.eneik.production.models.persistence.ProjectStatus.active))
                .thenReturn(List.of(activeProject));

        UUID resolved = auditService.getActiveProjectId();

        assertThat(resolved).isEqualTo(activeId);
        verify(projectRepository, never()).findAll();
    }

    @Test
    void getActiveProjectIdReturnsNullWhenNoActiveProjectExistsWithoutArbitraryFallback() {
        when(projectRepository.findByStatusOrderByCreatedAtDesc(com.eneik.production.models.persistence.ProjectStatus.active))
                .thenReturn(Collections.emptyList());

        UUID resolved = auditService.getActiveProjectId();

        assertThat(resolved).isNull();
        verify(projectRepository, never()).findAll();
    }

    @Test
    void getActiveProjectIdReturnsNullWhenMultipleActiveProjectsExistEnforcingSingleProjectLaw() {
        com.eneik.production.models.persistence.ProjectEntity project1 =
                new com.eneik.production.models.persistence.ProjectEntity();
        project1.setId(UUID.randomUUID());
        com.eneik.production.models.persistence.ProjectEntity project2 =
                new com.eneik.production.models.persistence.ProjectEntity();
        project2.setId(UUID.randomUUID());

        when(projectRepository.findByStatusOrderByCreatedAtDesc(com.eneik.production.models.persistence.ProjectStatus.active))
                .thenReturn(List.of(project1, project2));

        UUID resolved = auditService.getActiveProjectId();

        // Factory invariant: "Завод за 1 раз делает 1 проект". Ambiguous state returns null (undetermined), not arbitrary choice.
        assertThat(resolved).isNull();
        verify(projectRepository, never()).findAll();
    }

    @Test
    void calculateDeliverySixSigmaAuditResolvesActiveProjectWhenNull() {
        UUID activeId = UUID.randomUUID();
        com.eneik.production.models.persistence.ProjectEntity activeProject =
                new com.eneik.production.models.persistence.ProjectEntity();
        activeProject.setId(activeId);
        activeProject.setName("test-fiftieth");
        activeProject.setStatus(com.eneik.production.models.persistence.ProjectStatus.active);

        when(projectRepository.findByStatusOrderByCreatedAtDesc(com.eneik.production.models.persistence.ProjectStatus.active))
                .thenReturn(List.of(activeProject));
        when(projectRepository.findById(activeId)).thenReturn(java.util.Optional.of(activeProject));

        var report = auditService.calculateDeliverySixSigmaAudit(null);

        assertThat(report.projectId()).isEqualTo(activeId);
        assertThat(report.projectName()).isEqualTo("test-fiftieth");
        verify(projectRepository, never()).findAll();
        verify(onboardingAuditFindingRepository, never()).findAll();
        verify(prReviewRepository, never()).findAll();
        verify(taskConflictRepository, never()).findAll();
    }

    @Test
    void calculateFullSixSigmaAuditNeverCallsFindAll() {
        var report = auditService.calculateFullSixSigmaAudit();

        assertThat(report).isNotNull();
        verify(projectRepository, never()).findAll();
        verify(onboardingAuditFindingRepository, never()).findAll();
        verify(prReviewRepository, never()).findAll();
        verify(taskConflictRepository, never()).findAll();
        verify(taskRepository, never()).findAll();
    }

    @Test
    void calculateProductLayerSixSigmaAuditReturnsUndeterminedWhenNoActiveProject() {
        when(projectRepository.findByStatusOrderByCreatedAtDesc(com.eneik.production.models.persistence.ProjectStatus.active))
                .thenReturn(Collections.emptyList());

        var report = auditService.calculateProductLayerSixSigmaAudit(null);

        assertThat(report.projectId()).isNull();
        assertThat(report.projectName()).isEqualTo("NO_ACTIVE_PROJECT");
        assertThat(report.qualityTier()).isEqualTo("UNDETERMINED");
        assertThat(report.sigmaLevel()).isEqualTo(0.0);
    }

    @Test
    void computePrConflictCountsGlobalUsesCountsAndNeverFindAll() {
        when(prReviewRepository.countByMergedTrue()).thenReturn(15L);
        when(taskConflictRepository.count()).thenReturn(3L);

        var counts = auditService.computePrConflictCounts(null, null);

        assertThat(counts.defects()).isEqualTo(3L);
        assertThat(counts.opportunities()).isEqualTo(18L);
        verify(prReviewRepository, never()).findAll();
        verify(taskConflictRepository, never()).findAll();
    }

    @Test
    void computePrConflictCountsProjectUsesScopedQueriesAndNeverFindAll() {
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        com.eneik.production.models.persistence.TaskEntity task =
                new com.eneik.production.models.persistence.TaskEntity();
        task.setId(taskId);
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(task));

        com.eneik.production.models.persistence.JulesSessionEntity session =
                new com.eneik.production.models.persistence.JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        when(julesSessionRepository.findByTaskIdIn(List.of(taskId))).thenReturn(List.of(session));

        com.eneik.production.models.persistence.PrReviewEntity review =
                new com.eneik.production.models.persistence.PrReviewEntity();
        review.setJulesSessionId(sessionId);
        review.setMerged(true);
        when(prReviewRepository.findByJulesSessionIdInAndMergedTrue(List.of(sessionId))).thenReturn(List.of(review));

        com.eneik.production.models.persistence.TaskConflictEntity conflict =
                new com.eneik.production.models.persistence.TaskConflictEntity();
        conflict.setTask(task);
        when(taskConflictRepository.findByTaskIdIn(List.of(taskId))).thenReturn(List.of(conflict));

        var counts = auditService.computePrConflictCounts(projectId, null);

        assertThat(counts.defects()).isEqualTo(1L);
        assertThat(counts.opportunities()).isEqualTo(2L); // 1 merged PR + 1 conflict defect
        verify(prReviewRepository, never()).findAll();
        verify(taskConflictRepository, never()).findAll();
    }

    @Test
    void calculateDeliverySixSigmaAuditReturnsUndeterminedAndNotFactoryWhenNoActiveProject() {
        when(projectRepository.findByStatusOrderByCreatedAtDesc(com.eneik.production.models.persistence.ProjectStatus.active))
                .thenReturn(Collections.emptyList());

        var deliveryReport = auditService.calculateDeliverySixSigmaAudit(null);
        var factoryReport = auditService.calculateFullSixSigmaAudit();

        assertThat(deliveryReport.projectId()).isNull();
        assertThat(deliveryReport.projectName()).isEqualTo("NO_ACTIVE_PROJECT");
        assertThat(deliveryReport.qualityTier()).isEqualTo("UNDETERMINED");
        assertThat(deliveryReport.totalOpportunities()).isEqualTo(0L);
        assertThat(factoryReport.projectName()).isEqualTo("FACTORY_WIDE_ALL_PROJECTS");
        assertThat(deliveryReport.projectName()).isNotEqualTo(factoryReport.projectName());
    }
}
