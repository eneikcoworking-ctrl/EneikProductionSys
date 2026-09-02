package com.eneik.production.services.operational;

import com.eneik.production.dto.operational.FlowSpineDto;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.FlowSpineEventRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.ClientDeliverableReadinessService;
import com.eneik.production.services.dashboard.SystemStatusService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Model rule 8.11 O8: a hold leaves a readable record of its reason, and a bare count is not a reason.
 *
 * <p>BLOCKED_BY_REVIEW lumps together kinds with entirely different resolvers: `conflict` has a bounded
 * resolution path, while `escalated` and `closed_unmerged` are terminal and, on a session that stays live,
 * would be counted forever. Measured 2026-09-02: this bottleneck had been breached for 6841 minutes - four
 * and three quarter days - and the number alone could not say which of those was holding it.
 */
class FailingReviewCompositionTest {

    private final ProjectEntity project = project();
    private final JulesSessionRepository sessions = mock(JulesSessionRepository.class);
    private final PrReviewRepository reviews = mock(PrReviewRepository.class);

    private static ProjectEntity project() {
        ProjectEntity p = new ProjectEntity();
        p.setId(UUID.randomUUID());
        p.setStatus(ProjectStatus.active);
        return p;
    }

    private FlowSpineService service(List<TaskEntity> tasks) {
        var projects = mock(ProjectRepository.class);
        var taskRepo = mock(TaskRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var systemStatus = mock(SystemStatusService.class);
        when(projects.findById(project.getId())).thenReturn(java.util.Optional.of(project));
        when(taskRepo.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(tasks);
        when(wishlists.findByProjectId(project.getId())).thenReturn(List.of());
        when(readiness.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(1, 0, 1, 0, 0.0, true, 0.0));
        when(systemStatus.getStatus(project.getId()))
                .thenReturn(Map.of("systemHealth", Map.of("data", Map.of("status", "ok"))));
        return new FlowSpineService(projects, taskRepo, wishlists, sessions, reviews,
                mock(FlowSpineEventRepository.class), readiness, systemStatus,
                mock(com.eneik.production.services.MLPredictionServiceClient.class),
                mock(com.eneik.production.services.lever.LeverPromotionService.class));
    }

    private TaskEntity liveTask() {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setStatus(TaskStatus.in_progress);
        task.setSourceWishlistId(UUID.randomUUID());
        task.setFeatureId(UUID.randomUUID());
        return task;
    }

    private JulesSessionEntity liveSession(TaskEntity task) {
        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTaskId(task.getId());
        session.setStatus("running");
        return session;
    }

    private PrReviewEntity review(JulesSessionEntity session, String ciStatus) {
        PrReviewEntity review = new PrReviewEntity();
        review.setId(UUID.randomUUID());
        review.setJulesSessionId(session.getId());
        review.setMerged(false);
        review.setCiStatus(ciStatus);
        return review;
    }

    @Test
    void theHoldNamesWhichKindsOfReviewAreHoldingIt() {
        TaskEntity task = liveTask();
        JulesSessionEntity session = liveSession(task);
        when(sessions.findByTaskIdIn(anyList())).thenReturn(List.of(session));
        when(reviews.findByJulesSessionIdIn(anyList()))
                .thenReturn(List.of(review(session, "conflict"), review(session, "escalated"),
                        review(session, "conflict")));

        FlowSpineDto dto = service(List.of(task)).build(project.getId());

        assertThat(dto.blockingReason()).contains("conflict=2").contains("escalated=1");
    }

    @Test
    void aStateWithNoFailingReviewCarriesNoComposition() {
        // The complement: the reason must not grow a stray empty map when nothing is failing.
        TaskEntity task = liveTask();
        JulesSessionEntity session = liveSession(task);
        when(sessions.findByTaskIdIn(anyList())).thenReturn(List.of(session));
        when(reviews.findByJulesSessionIdIn(anyList())).thenReturn(List.of(review(session, "pending")));

        FlowSpineDto dto = service(List.of(task)).build(project.getId());

        assertThat(dto.blockingReason()).doesNotContain("{}");
    }
}
