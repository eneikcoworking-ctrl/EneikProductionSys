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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Model rule 8.20: a task in a review status needs ITS OWN review artifact.
 *
 * <p>The invariant compared two project-wide aggregates - "are there review tasks" against "are there open
 * reviews" - so it warned on the ordinary case of a review task whose pull request had just merged, and
 * stayed silent on a task with no artifact at all. Measured on the live circuit 2026-08-30 21:45: four
 * review tasks, zero open review artifacts, and nothing in that number able to say which of the two it was.
 * A warning that fires on the normal case teaches the reader that warnings are not about problems.
 */
class ReviewArtifactInvariantTest {

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

    private TaskEntity reviewTask() {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setStatus(TaskStatus.review);
        task.setSourceWishlistId(UUID.randomUUID());
        task.setFeatureId(UUID.randomUUID());
        return task;
    }

    private String invariantStatus(FlowSpineDto dto) {
        return dto.invariants().stream()
                .filter(i -> "review_requires_artifact".equals(i.key()))
                .map(FlowSpineDto.FlowInvariant::status)
                .findFirst().orElse("(absent)");
    }

    @Test
    void aReviewTaskWithNoArtifactAtAllBreaksTheInvariant() {
        TaskEntity task = reviewTask();
        when(sessions.findByTaskIdIn(anyList())).thenReturn(List.of());
        when(reviews.findByJulesSessionIdIn(anyList())).thenReturn(List.of());

        assertEquals("warn", invariantStatus(service(List.of(task)).build(project.getId())));
    }

    @Test
    void aReviewTaskWhoseArtifactAlreadyMergedDoesNot() {
        // This is the case the old aggregate warned on: merged reviews are not open, so openReviews was 0
        // while the artifact plainly existed and had done its job.
        TaskEntity task = reviewTask();
        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTaskId(task.getId());
        session.setStatus("running");
        PrReviewEntity review = new PrReviewEntity();
        review.setId(UUID.randomUUID());
        review.setJulesSessionId(session.getId());
        review.setMerged(true);
        when(sessions.findByTaskIdIn(anyList())).thenReturn(List.of(session));
        when(reviews.findByJulesSessionIdIn(anyList())).thenReturn(List.of(review));

        assertEquals("pass", invariantStatus(service(List.of(task)).build(project.getId())));
    }
}
