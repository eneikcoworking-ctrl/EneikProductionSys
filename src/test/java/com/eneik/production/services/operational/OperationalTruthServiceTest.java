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
}
