package com.eneik.production.services.operational;

import com.eneik.production.dto.operational.OperationalTruthDto;
import com.eneik.production.kaizen.repository.DefectJournalRepository;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.ClientDeliverableReadinessService;
import com.eneik.production.services.dashboard.SystemStatusService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationalTruthServiceTest {

    @Test
    void deliveryStatusSeparatesNoScopeDecomposingBuildingAndDelivered() {
        assertEquals("no_scope", OperationalTruthService.deliveryStatus(
                new ClientDeliverableReadinessService.Readiness(0, 0, 0, 0, 0.0, false)));
        assertEquals("decomposing", OperationalTruthService.deliveryStatus(
                new ClientDeliverableReadinessService.Readiness(3, 0, 9, 0, 0.0, false)));
        assertEquals("building", OperationalTruthService.deliveryStatus(
                new ClientDeliverableReadinessService.Readiness(3, 2, 9, 7, 2.0 / 3.0, true)));
        assertEquals("delivered", OperationalTruthService.deliveryStatus(
                new ClientDeliverableReadinessService.Readiness(3, 3, 9, 9, 1.0, true)));
    }

    @Test
    void trustLevelUsesStableBands() {
        assertEquals("trusted", OperationalTruthService.trustLevel(0.95));
        assertEquals("watch", OperationalTruthService.trustLevel(0.70));
        assertEquals("degraded", OperationalTruthService.trustLevel(0.50));
        assertEquals("blocked", OperationalTruthService.trustLevel(0.20));
    }

    @Test
    void onlyExplicitHealthySystemStatusesAvoidTrustBlock() {
        assertFalse(OperationalTruthService.isTrustBlockingSystemStatus("ok"));
        assertFalse(OperationalTruthService.isTrustBlockingSystemStatus("idle_no_actionable_work"));
        assertFalse(OperationalTruthService.isTrustBlockingSystemStatus("busy_with_actionable_work"));
        assertFalse(OperationalTruthService.isTrustBlockingSystemStatus("undetermined"));
        assertTrue(OperationalTruthService.isTrustBlockingSystemStatus("content_defect"));
        assertTrue(OperationalTruthService.isTrustBlockingSystemStatus("stalled"));
    }

    @Test
    void clampRoundsAndBoundsTrustScores() {
        assertEquals(1.0, OperationalTruthService.clamp(1.5));
        assertEquals(0.0, OperationalTruthService.clamp(-0.1));
        assertEquals(0.67, OperationalTruthService.clamp(0.666));
    }

    @Test
    void aTerminallyFailedTasksLongDeadReviewNoLongerAppearsAsALiveBlocker() {
        // Same architectural class as the FlowSpineService fix (2026-07-31, task 529e5252/test-fortieth):
        // this dashboard computed failingReviews/openReviews over the project's entire history too, so a
        // review belonging to an already-terminal task would misreport as a live "review_not_mergeable"
        // blocker forever. This service never gates autonomous actions (only OperationalTruthController
        // reads it), so the practical impact was a misleading dashboard, not a stall - but it's the same
        // bug and deserves the same fix for consistency and honest reporting.
        var projects = mock(ProjectRepository.class);
        var tasks = mock(TaskRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var sessions = mock(JulesSessionRepository.class);
        var reviews = mock(PrReviewRepository.class);
        var defects = mock(DefectJournalRepository.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var systemStatus = mock(SystemStatusService.class);
        OperationalTruthService service = new OperationalTruthService(
                projects, tasks, wishlists, sessions, reviews, defects, readiness, systemStatus,
                mock(com.eneik.production.services.ProjectFlowService.class));

        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID deadTaskId = UUID.randomUUID();
        TaskEntity deadTask = new TaskEntity();
        deadTask.setId(deadTaskId);
        deadTask.setStatus(TaskStatus.failed);
        deadTask.setDescription("Runtime Contract 20666c21");

        JulesSessionEntity deadSession = new JulesSessionEntity();
        deadSession.setId(UUID.randomUUID());
        deadSession.setTaskId(deadTaskId);
        deadSession.setStatus("stuck");

        PrReviewEntity deadReview = new PrReviewEntity();
        deadReview.setJulesSessionId(deadSession.getId());
        deadReview.setCiStatus("closed_unmerged");
        deadReview.setMerged(false);

        when(projects.findById(projectId)).thenReturn(java.util.Optional.of(project));
        when(tasks.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(deadTask));
        when(wishlists.findByProjectId(projectId)).thenReturn(List.of());
        when(sessions.findByTaskIdIn(List.of(deadTaskId))).thenReturn(List.of(deadSession));
        when(reviews.findByJulesSessionIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(deadReview));
        when(defects.findByProjectIdAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(projectId), any(Instant.class)))
                .thenReturn(List.of());
        when(readiness.computeForProject(projectId)).thenReturn(ClientDeliverableReadinessService.Readiness.none());
        when(systemStatus.getStatus(projectId)).thenReturn(
                Map.of("systemHealth", Map.of("data", Map.of("status", "ok"))));

        OperationalTruthDto dto = service.build(projectId);

        assertEquals(0, dto.evidence().failingReviews());
        assertEquals(0, dto.evidence().openReviews());
        assertTrue(dto.blockedValue().blockers().stream()
                .noneMatch(b -> "review_not_mergeable".equals(b.type())));
    }

    @Test
    void aSupersededSessionsDeadReviewNoLongerAppearsAsALiveBlockerAfterBranchGcRequeuedTheTask() {
        // Same architectural gap as FlowSpineServiceTest's identical regression test (2026-08-01,
        // test-fortieth/PR#119, task 72ec0f54): Branch GC cancelled the stale session and re-queued the
        // TASK itself for a fresh attempt - the task never went terminal, so it wasn't excluded, and the
        // old session's real "closed_unmerged" review kept misreporting as a live blocker.
        var projects = mock(ProjectRepository.class);
        var tasks = mock(TaskRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var sessions = mock(JulesSessionRepository.class);
        var reviews = mock(PrReviewRepository.class);
        var defects = mock(DefectJournalRepository.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var systemStatus = mock(SystemStatusService.class);
        OperationalTruthService service = new OperationalTruthService(
                projects, tasks, wishlists, sessions, reviews, defects, readiness, systemStatus,
                mock(com.eneik.production.services.ProjectFlowService.class));

        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID requeuedTaskId = UUID.randomUUID();
        TaskEntity requeuedTask = new TaskEntity();
        requeuedTask.setId(requeuedTaskId);
        requeuedTask.setStatus(TaskStatus.queued);
        requeuedTask.setDescription("API Slice 9a624cbf");

        JulesSessionEntity supersededSession = new JulesSessionEntity();
        supersededSession.setId(UUID.randomUUID());
        supersededSession.setTaskId(requeuedTaskId);
        supersededSession.setStatus("cancelled");

        PrReviewEntity deadReview = new PrReviewEntity();
        deadReview.setJulesSessionId(supersededSession.getId());
        deadReview.setCiStatus("closed_unmerged");
        deadReview.setMerged(false);

        when(projects.findById(projectId)).thenReturn(java.util.Optional.of(project));
        when(tasks.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(requeuedTask));
        when(wishlists.findByProjectId(projectId)).thenReturn(List.of());
        when(sessions.findByTaskIdIn(List.of(requeuedTaskId))).thenReturn(List.of(supersededSession));
        when(reviews.findByJulesSessionIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(deadReview));
        when(defects.findByProjectIdAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(projectId), any(Instant.class)))
                .thenReturn(List.of());
        when(readiness.computeForProject(projectId)).thenReturn(ClientDeliverableReadinessService.Readiness.none());
        when(systemStatus.getStatus(projectId)).thenReturn(
                Map.of("systemHealth", Map.of("data", Map.of("status", "ok"))));

        OperationalTruthDto dto = service.build(projectId);

        assertEquals(0, dto.evidence().failingReviews());
        assertTrue(dto.blockedValue().blockers().stream()
                .noneMatch(b -> "review_not_mergeable".equals(b.type())));
    }

    // 2026-08-09 (operator-flagged, test-forty-third: 5 "done" tasks permanently flagged
    // done_without_delivery_evidence despite real fixes landing - "точность подсчётов должна быть 100%
    // истинной"). 3 of 5 were the wishlist-compiler's own decomposition-planning carrier tasks, which
    // structurally never get a PrReviewEntity (they merge via AutoMergeService's no-code record path -
    // there is nothing to review for a task-plan JSON file). These two tests pin the fix down: a carrier
    // task must never count against this blocker, but a genuine done task with no review evidence still
    // must - the exemption is scoped to task TYPE, not a blanket relaxation.

    private String clientScopeInvariant(java.util.List<com.eneik.production.models.persistence.WishlistEntity> wishlist) {
        var projects = mock(ProjectRepository.class);
        var tasks = mock(TaskRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var sessions = mock(JulesSessionRepository.class);
        var reviews = mock(PrReviewRepository.class);
        var defects = mock(DefectJournalRepository.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var systemStatus = mock(SystemStatusService.class);
        var flow = mock(com.eneik.production.services.ProjectFlowService.class);
        OperationalTruthService service = new OperationalTruthService(
                projects, tasks, wishlists, sessions, reviews, defects, readiness, systemStatus, flow);

        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        when(projects.findById(projectId)).thenReturn(java.util.Optional.of(project));
        when(tasks.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of());
        when(wishlists.findByProjectId(projectId)).thenReturn(wishlist);
        when(reviews.findByJulesSessionIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        when(defects.findByProjectIdAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(projectId), any(Instant.class)))
                .thenReturn(List.of());
        when(readiness.computeForProject(projectId)).thenReturn(ClientDeliverableReadinessService.Readiness.none());
        when(systemStatus.getStatus(projectId)).thenReturn(
                Map.of("systemHealth", Map.of("data", Map.of("status", "ok"))));

        return service.build(projectId).invariants().stream()
                .filter(i -> "factory_serves_a_client_brief".equals(i.key()))
                .map(OperationalTruthDto.InvariantStatus::status)
                .findFirst()
                .orElse("absent");
    }

    private com.eneik.production.models.persistence.WishlistEntity brief(
            com.eneik.production.models.persistence.WishlistSource source, UUID originWishlistId) {
        var item = new com.eneik.production.models.persistence.WishlistEntity();
        item.setId(UUID.randomUUID());
        item.setSource(source);
        item.setOriginWishlistId(originWishlistId);
        return item;
    }

    /**
     * Plan §4.31. The factory could not tell "nothing to do" from "working only on myself". Measured on
     * test-fiftieth: zero client root briefs, 197 of ~222 wishlists carrying the factory's own complaint
     * that its work never reached main, and the client's actual brief present nowhere - confirmed against
     * the client repository's own tree, which had no file named for any part of it.
     */
    @Test
    void aProjectWithOnlySelfGeneratedBriefsDoesNotPassTheClientScopeInvariant() {
        String status = clientScopeInvariant(List.of(
                brief(com.eneik.production.models.persistence.WishlistSource.delivery_never_reached_main, null),
                brief(com.eneik.production.models.persistence.WishlistSource.coverage_gap, null)));

        assertNotEquals("pass", status);
    }

    /** The other half: a real client root brief passes, so the invariant is not simply always failing. */
    @Test
    void aProjectWithAClientRootBriefPassesTheClientScopeInvariant() {
        String status = clientScopeInvariant(List.of(
                brief(com.eneik.production.models.persistence.WishlistSource.client, null),
                brief(com.eneik.production.models.persistence.WishlistSource.client, UUID.randomUUID())));

        assertEquals("pass", status);
    }

    @Test
    void aDoneWishlistCompilerCarrierTaskWithNoReviewNeverCountsAsMissingDeliveryEvidence() {
        var projects = mock(ProjectRepository.class);
        var tasks = mock(TaskRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var sessions = mock(JulesSessionRepository.class);
        var reviews = mock(PrReviewRepository.class);
        var defects = mock(DefectJournalRepository.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var systemStatus = mock(SystemStatusService.class);
        var flow = mock(com.eneik.production.services.ProjectFlowService.class);
        OperationalTruthService service = new OperationalTruthService(
                projects, tasks, wishlists, sessions, reviews, defects, readiness, systemStatus, flow);

        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID carrierTaskId = UUID.randomUUID();
        TaskEntity carrierTask = new TaskEntity();
        carrierTask.setId(carrierTaskId);
        carrierTask.setStatus(TaskStatus.done);
        carrierTask.setDescription("Decompose wishlist into task plan");
        when(flow.isWishlistCompilerTask(carrierTask)).thenReturn(true);

        when(projects.findById(projectId)).thenReturn(java.util.Optional.of(project));
        when(tasks.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(carrierTask));
        when(wishlists.findByProjectId(projectId)).thenReturn(List.of());
        when(sessions.findByTaskIdIn(List.of(carrierTaskId))).thenReturn(List.of());
        when(reviews.findByJulesSessionIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        when(defects.findByProjectIdAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(projectId), any(Instant.class)))
                .thenReturn(List.of());
        when(readiness.computeForProject(projectId)).thenReturn(ClientDeliverableReadinessService.Readiness.none());
        when(systemStatus.getStatus(projectId)).thenReturn(
                Map.of("systemHealth", Map.of("data", Map.of("status", "ok"))));

        OperationalTruthDto dto = service.build(projectId);

        assertTrue(dto.blockedValue().blockers().stream()
                .noneMatch(b -> "done_without_delivery_evidence".equals(b.type())));
    }

    @Test
    void aDoneRealTaskWithNoReviewStillCountsAsMissingDeliveryEvidence() {
        var projects = mock(ProjectRepository.class);
        var tasks = mock(TaskRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var sessions = mock(JulesSessionRepository.class);
        var reviews = mock(PrReviewRepository.class);
        var defects = mock(DefectJournalRepository.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var systemStatus = mock(SystemStatusService.class);
        var flow = mock(com.eneik.production.services.ProjectFlowService.class);
        OperationalTruthService service = new OperationalTruthService(
                projects, tasks, wishlists, sessions, reviews, defects, readiness, systemStatus, flow);

        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID realTaskId = UUID.randomUUID();
        TaskEntity realTask = new TaskEntity();
        realTask.setId(realTaskId);
        realTask.setStatus(TaskStatus.done);
        realTask.setDescription("Implement document search filters");
        // flow's predicates all default to false (unstubbed mock) - a genuine implementation task.

        when(projects.findById(projectId)).thenReturn(java.util.Optional.of(project));
        when(tasks.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(realTask));
        when(wishlists.findByProjectId(projectId)).thenReturn(List.of());
        when(sessions.findByTaskIdIn(List.of(realTaskId))).thenReturn(List.of());
        when(reviews.findByJulesSessionIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        when(defects.findByProjectIdAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(projectId), any(Instant.class)))
                .thenReturn(List.of());
        when(readiness.computeForProject(projectId)).thenReturn(ClientDeliverableReadinessService.Readiness.none());
        when(systemStatus.getStatus(projectId)).thenReturn(
                Map.of("systemHealth", Map.of("data", Map.of("status", "ok"))));

        OperationalTruthDto dto = service.build(projectId);

        assertTrue(dto.blockedValue().blockers().stream()
                .anyMatch(b -> "done_without_delivery_evidence".equals(b.type())));
    }

    @Test
    void projectWithoutEvidenceHasZeroScoreAndUndeterminedTrustLevel() {
        // ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE (D006): do not authorize trust from belief or
        // intention alone; require knowledge-grade evidence. A project with zero merged reviews and zero
        // passed quality gates must never receive score 1.0 or level "trusted".
        var projects = mock(ProjectRepository.class);
        var tasks = mock(TaskRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var sessions = mock(JulesSessionRepository.class);
        var reviews = mock(PrReviewRepository.class);
        var defects = mock(DefectJournalRepository.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var systemStatus = mock(SystemStatusService.class);
        var flow = mock(com.eneik.production.services.ProjectFlowService.class);
        OperationalTruthService service = new OperationalTruthService(
                projects, tasks, wishlists, sessions, reviews, defects, readiness, systemStatus, flow);

        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        when(projects.findById(projectId)).thenReturn(java.util.Optional.of(project));
        when(tasks.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of());
        when(wishlists.findByProjectId(projectId)).thenReturn(List.of());
        when(reviews.findByJulesSessionIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        when(defects.findByProjectIdAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(projectId), any(Instant.class)))
                .thenReturn(List.of());
        when(readiness.computeForProject(projectId)).thenReturn(ClientDeliverableReadinessService.Readiness.none());
        when(systemStatus.getStatus(projectId)).thenReturn(
                Map.of("systemHealth", Map.of("data", Map.of("status", "ok"))));

        OperationalTruthDto dto = service.build(projectId);

        assertEquals(0.0, dto.trust().score());
        assertEquals("undetermined", dto.trust().level());
        assertNotEquals("trusted", dto.trust().level());
        assertTrue(dto.trust().positiveSignals().stream()
                .anyMatch(s -> s.contains("No delivery or quality-gate verification evidence")));

        String invariantStatus = dto.invariants().stream()
                .filter(i -> "trust_requires_positive_evidence".equals(i.key()))
                .map(OperationalTruthDto.InvariantStatus::status)
                .findFirst()
                .orElse("absent");
        assertEquals("observed", invariantStatus);
    }

    @Test
    void computeBaseTrustGrowsSlowlyInPackets() {
        // ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS (D010): trust grows slowly and in packets with named
        // threshold and packet size.
        assertEquals(0.0, OperationalTruthService.computeBaseTrust(0));
        assertEquals(0.0, OperationalTruthService.computeBaseTrust(-5));

        // Packet 0: 1..4 items -> 0.50 (degraded baseline)
        assertEquals(0.50, OperationalTruthService.computeBaseTrust(1));
        assertEquals(0.50, OperationalTruthService.computeBaseTrust(4));

        // Packet 1: 5..9 items -> 0.65 (watch baseline)
        assertEquals(0.65, OperationalTruthService.computeBaseTrust(5));
        assertEquals(0.65, OperationalTruthService.computeBaseTrust(9));

        // Packet 2: 10..14 items -> 0.75 (watch upper)
        assertEquals(0.75, OperationalTruthService.computeBaseTrust(10));
        assertEquals(0.75, OperationalTruthService.computeBaseTrust(14));

        // Packet 3: 15..19 items -> 0.85 (trusted baseline)
        assertEquals(0.85, OperationalTruthService.computeBaseTrust(15));
        assertEquals(0.85, OperationalTruthService.computeBaseTrust(19));

        // Packet 4 / Threshold reached: >= 20 items -> 1.00 (maximum base trust)
        assertEquals(1.00, OperationalTruthService.computeBaseTrust(20));
        assertEquals(1.00, OperationalTruthService.computeBaseTrust(100));
    }

    @Test
    void positiveEvidenceAccumulationPromotesTrustLevel() {
        var projects = mock(ProjectRepository.class);
        var tasks = mock(TaskRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var sessions = mock(JulesSessionRepository.class);
        var reviews = mock(PrReviewRepository.class);
        var defects = mock(DefectJournalRepository.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var systemStatus = mock(SystemStatusService.class);
        var flow = mock(com.eneik.production.services.ProjectFlowService.class);
        OperationalTruthService service = new OperationalTruthService(
                projects, tasks, wishlists, sessions, reviews, defects, readiness, systemStatus, flow);

        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        // 5 tasks with verified delivery -> packet 1 (base score 0.65, watch)
        List<TaskEntity> passedTasks = java.util.stream.IntStream.range(0, 5).mapToObj(i -> {
            TaskEntity t = new TaskEntity();
            t.setId(UUID.randomUUID());
            t.setProject(project);
            t.setStatus(TaskStatus.done);
            t.setQualityGatePassed(true);
            t.setPayload(mapper.createObjectNode().put("acceptance_verdict", "SATISFIED"));
            return t;
        }).toList();

        when(projects.findById(projectId)).thenReturn(java.util.Optional.of(project));
        when(tasks.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(passedTasks);
        when(wishlists.findByProjectId(projectId)).thenReturn(List.of());
        when(reviews.findByJulesSessionIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        when(defects.findByProjectIdAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(projectId), any(Instant.class)))
                .thenReturn(List.of());
        when(readiness.computeForProject(projectId)).thenReturn(ClientDeliverableReadinessService.Readiness.none());
        when(systemStatus.getStatus(projectId)).thenReturn(
                Map.of("systemHealth", Map.of("data", Map.of("status", "ok"))));

        OperationalTruthDto dto = service.build(projectId);
        assertEquals(0.65, dto.trust().score());
        assertEquals("watch", dto.trust().level());

        String invariantStatus = dto.invariants().stream()
                .filter(i -> "trust_requires_positive_evidence".equals(i.key()))
                .map(OperationalTruthDto.InvariantStatus::status)
                .findFirst()
                .orElse("absent");
        assertEquals("pass", invariantStatus);
    }

    @Test
    void asymmetricDemotionDropsTrustImmediatelyOnConfirmedFailure() {
        // ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS (D010): 20 items needed to reach 1.0, but a single
        // confirmed failure demotes immediately.
        OperationalTruthService service = new OperationalTruthService(
                mock(ProjectRepository.class), mock(TaskRepository.class), mock(WishlistRepository.class),
                mock(JulesSessionRepository.class), mock(PrReviewRepository.class),
                mock(DefectJournalRepository.class), mock(ClientDeliverableReadinessService.class),
                mock(SystemStatusService.class), mock(com.eneik.production.services.ProjectFlowService.class));

        // Case 1: 20 passed gates, 0 failures -> 1.0, trusted
        var evidenceFull = new OperationalTruthDto.EvidenceSummary(0, 0, 0, 0, 20, 0, 0, List.of());
        var emptyBlockers = List.<OperationalTruthDto.Blocker>of();
        var cleanDuplicate = new OperationalTruthService.DuplicateContent(false, 0);
        var trustFull = service.trust(evidenceFull, emptyBlockers, "ok", cleanDuplicate, List.of());
        assertEquals(1.00, trustFull.score());
        assertEquals("trusted", trustFull.level());

        // Case 2: 1 failing review (-0.20) -> immediately drops to 0.80, demoted to "watch"
        var evidenceWithFailingReview = new OperationalTruthDto.EvidenceSummary(0, 0, 0, 1, 20, 0, 0, List.of());
        var trustDemotedReview = service.trust(evidenceWithFailingReview, emptyBlockers, "ok", cleanDuplicate, List.of());
        assertEquals(0.80, trustDemotedReview.score());
        assertEquals("watch", trustDemotedReview.level());

        // Case 3: Failing review (-0.20) + duplicate content (-0.30) -> drops to 0.50, demoted to "degraded"
        var duplicateActive = new OperationalTruthService.DuplicateContent(true, 4);
        var trustDemotedDegraded = service.trust(evidenceWithFailingReview, emptyBlockers, "ok", duplicateActive, List.of());
        assertEquals(0.50, trustDemotedDegraded.score());
        assertEquals("degraded", trustDemotedDegraded.level());

        // Case 4: Failing review (-0.20) + content defect (-0.35) + duplicate (-0.30) + failed gate (-0.15)
        // 1.0 - 0.20 - 0.35 - 0.30 - 0.15 = 0.0 -> "blocked"
        var evidenceMultipleFailures = new OperationalTruthDto.EvidenceSummary(0, 0, 0, 1, 20, 1, 0, List.of());
        var trustBlocked = service.trust(evidenceMultipleFailures, emptyBlockers, "content_defect", duplicateActive, List.of());
        assertEquals(0.0, trustBlocked.score());
        assertEquals("blocked", trustBlocked.level());
    }

    @Test
    void trustLevelOverloadWithEvidenceFlag() {
        assertEquals("trusted", OperationalTruthService.trustLevel(0.95));
        assertEquals("watch", OperationalTruthService.trustLevel(0.70));
        assertEquals("degraded", OperationalTruthService.trustLevel(0.50));
        assertEquals("blocked", OperationalTruthService.trustLevel(0.20));

        // Overload with explicit positive evidence flag:
        assertEquals("undetermined", OperationalTruthService.trustLevel(0.0, false));
        assertEquals("undetermined", OperationalTruthService.trustLevel(0.95, false));
        assertEquals("blocked", OperationalTruthService.trustLevel(0.0, true));
        assertEquals("trusted", OperationalTruthService.trustLevel(0.85, true));
    }

    @Test
    void triStateTruthPartitionVerifiedFailedAbsentAndRecencyWindow() {
        // NUEL_BELNAP_03_TRUTH_STATUS_TABLE (D012) & DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT (D009):
        // Partition of truth: verified + failed + unapplied == tasks with quality gate report.
        // ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS (D010): evidence older than TRUST_RECENCY_WINDOW is excluded.
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        // 1. Verified task (recent): delivery check applied > 0 and passed
        TaskEntity verifiedTask = new TaskEntity();
        verifiedTask.setId(UUID.randomUUID());
        verifiedTask.setCreatedAt(Instant.now().minus(java.time.Duration.ofDays(2)));
        verifiedTask.setQualityGatePassed(true);
        var reportVerified = mapper.createObjectNode();
        reportVerified.putObject("applicableChecksByStage").put("IMPLEMENTATION_RESULT", 3);
        verifiedTask.setQualityGateReport(reportVerified);

        assertTrue(verifiedTask.isVerifiedForDelivery());
        assertFalse(verifiedTask.isDeliveryVerificationFailed());
        assertFalse(verifiedTask.isDeliveryVerificationAbsent());

        // 2. Failed task (recent): delivery check applied > 0 and failed
        TaskEntity failedTask = new TaskEntity();
        failedTask.setId(UUID.randomUUID());
        failedTask.setCreatedAt(Instant.now().minus(java.time.Duration.ofDays(5)));
        failedTask.setQualityGatePassed(false);
        var reportFailed = mapper.createObjectNode();
        reportFailed.putObject("applicableChecksByStage").put("IMPLEMENTATION_RESULT", 2);
        failedTask.setQualityGateReport(reportFailed);

        assertFalse(failedTask.isVerifiedForDelivery());
        assertTrue(failedTask.isDeliveryVerificationFailed());
        assertFalse(failedTask.isDeliveryVerificationAbsent());

        // 3. Unapplied task (388 case): qualityGateReport present, but 0 delivery checks applied
        TaskEntity unappliedTask = new TaskEntity();
        unappliedTask.setId(UUID.randomUUID());
        unappliedTask.setCreatedAt(Instant.now().minus(java.time.Duration.ofDays(10)));
        unappliedTask.setQualityGatePassed(false);
        var reportUnapplied = mapper.createObjectNode();
        reportUnapplied.putObject("applicableChecksByStage").put("IMPLEMENTATION_RESULT", 0);
        unappliedTask.setQualityGateReport(reportUnapplied);

        assertFalse(unappliedTask.isVerifiedForDelivery());
        assertFalse(unappliedTask.isDeliveryVerificationFailed()); // NOT failed! 0 applied checks must not count as failed!
        assertTrue(unappliedTask.isDeliveryVerificationAbsent());

        // 4. Stale verified task: older than TRUST_RECENCY_WINDOW (e.g. 40 days ago)
        TaskEntity staleVerifiedTask = new TaskEntity();
        staleVerifiedTask.setId(UUID.randomUUID());
        staleVerifiedTask.setCreatedAt(Instant.now().minus(java.time.Duration.ofDays(40)));
        staleVerifiedTask.setQualityGatePassed(true);
        var reportStale = mapper.createObjectNode();
        reportStale.putObject("applicableChecksByStage").put("IMPLEMENTATION_RESULT", 1);
        staleVerifiedTask.setQualityGateReport(reportStale);

        assertTrue(staleVerifiedTask.isVerifiedForDelivery());

        // Verify with OperationalTruthService.build()
        var projects = mock(ProjectRepository.class);
        var tasks = mock(TaskRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var sessions = mock(JulesSessionRepository.class);
        var reviews = mock(PrReviewRepository.class);
        var defects = mock(DefectJournalRepository.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var systemStatus = mock(SystemStatusService.class);
        var flow = mock(com.eneik.production.services.ProjectFlowService.class);
        OperationalTruthService service = new OperationalTruthService(
                projects, tasks, wishlists, sessions, reviews, defects, readiness, systemStatus, flow);

        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        when(projects.findById(projectId)).thenReturn(java.util.Optional.of(project));
        when(tasks.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(
                List.of(verifiedTask, failedTask, unappliedTask, staleVerifiedTask));
        when(wishlists.findByProjectId(projectId)).thenReturn(List.of());
        when(reviews.findByJulesSessionIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        when(defects.findByProjectIdAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(projectId), any(Instant.class)))
                .thenReturn(List.of());
        when(readiness.computeForProject(projectId)).thenReturn(ClientDeliverableReadinessService.Readiness.none());
        when(systemStatus.getStatus(projectId)).thenReturn(
                Map.of("systemHealth", Map.of("data", Map.of("status", "ok"))));

        OperationalTruthDto dto = service.build(projectId);
        OperationalTruthDto.EvidenceSummary ev = dto.evidence();

        // Stale task excluded by recency window: only recent verified counted
        assertEquals(1, ev.qualityGatePassed());
        assertEquals(1, ev.qualityGateFailed());
        assertEquals(1, ev.qualityGateUnapplied());

        // For recent tasks with report: verified (1) + failed (1) + unapplied (1) == 3
        assertEquals(3, ev.qualityGatePassed() + ev.qualityGateFailed() + ev.qualityGateUnapplied());

        // Warning only includes actual failed checks, not unapplied checks
        assertTrue(dto.trust().warnings().stream().anyMatch(w -> w.contains("1 task(s) have failed quality-gate evidence.")));
        assertFalse(dto.trust().warnings().stream().anyMatch(w -> w.contains("unapplied")));
    }

    @Test
    void institutionalAuditRecordsDoNotPenalizeTrustOrCountAsDefects() {
        var projects = mock(ProjectRepository.class);
        var tasks = mock(TaskRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var sessions = mock(JulesSessionRepository.class);
        var reviews = mock(PrReviewRepository.class);
        var defects = mock(DefectJournalRepository.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var systemStatus = mock(SystemStatusService.class);
        var flow = mock(com.eneik.production.services.ProjectFlowService.class);

        var service = new OperationalTruthService(
                projects, tasks, wishlists, sessions, reviews, defects, readiness, systemStatus, flow);

        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        when(projects.findById(projectId)).thenReturn(java.util.Optional.of(project));
        when(tasks.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of());
        when(wishlists.findByProjectId(projectId)).thenReturn(List.of());
        when(reviews.findByJulesSessionIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());

        // Return an institutional audit record from defectJournalRepository
        var auditRecord = new com.eneik.production.kaizen.model.DefectJournalEntity(
                projectId, null, null, "INFO", "INSTITUTIONAL_AUDIT", "eneikdru",
                "ACCOUNT_LIFECYCLE_ENABLEMENT_RULE", "Account enabled toggled", 1.0);
        when(defects.findByProjectIdAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(projectId), any(Instant.class)))
                .thenReturn(List.of(auditRecord));
        when(readiness.computeForProject(projectId)).thenReturn(ClientDeliverableReadinessService.Readiness.none());
        when(systemStatus.getStatus(projectId)).thenReturn(
                Map.of("systemHealth", Map.of("data", Map.of("status", "ok"))));

        OperationalTruthDto dto = service.build(projectId);

        // Audit record must be excluded from defects and trust warnings
        assertEquals(0, dto.defects().recentDefects());
        assertTrue(dto.defects().items().isEmpty());
        assertFalse(dto.trust().warnings().stream().anyMatch(w -> w.contains("defect-journal item(s)")));
    }
}
