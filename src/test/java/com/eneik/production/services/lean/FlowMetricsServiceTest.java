package com.eneik.production.services.lean;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Layer 3 (Lean) math verification - Cycle Time/Lead Time/WIP from real timestamps, Little's Law
 * (WIP ≈ Throughput × Cycle Time) as a purely arithmetic consistency check, and waste computed from real
 * status-transition evidence (a dismissed wishlist), never an LLM narrative label.
 */
public class FlowMetricsServiceTest {

    private TaskRepository taskRepository;
    private JulesSessionRepository julesSessionRepository;
    private WishlistRepository wishlistRepository;
    private FlowMetricsService service;
    private UUID projectId;
    private ProjectEntity project;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        julesSessionRepository = mock(JulesSessionRepository.class);
        wishlistRepository = mock(WishlistRepository.class);
        service = new FlowMetricsService(taskRepository, julesSessionRepository, wishlistRepository);
        ReflectionTestUtils.setField(service, "littlesLawDeviationThreshold", 0.5);

        projectId = UUID.randomUUID();
        project = new ProjectEntity();
        project.setId(projectId);

        when(wishlistRepository.countByProjectIdAndStatus(any(), any())).thenReturn(0L);
        when(wishlistRepository.findByProjectId(any())).thenReturn(Collections.emptyList());
        when(julesSessionRepository.findByTaskId(any())).thenReturn(Collections.emptyList());
    }

    private TaskEntity doneTask(Instant createdAt, Instant updatedAt) {
        TaskEntity t = new TaskEntity();
        t.setId(UUID.randomUUID());
        t.setProject(project);
        t.setStatus(TaskStatus.done);
        t.setCreatedAt(createdAt);
        t.setUpdatedAt(updatedAt);
        return t;
    }

    @Test
    void littlesLawConsistentWhenWipMatchesThroughputTimesCycleTime() {
        // 10 done tasks, each with a 1-day cycle time, spread evenly over a 10-day window ->
        // throughput = 10/10 = 1/day, avg cycle time = 1 day -> expected WIP = 1.
        Instant base = Instant.now().minus(10, ChronoUnit.DAYS);
        List<TaskEntity> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Instant dispatchAt = base.plus(i, ChronoUnit.DAYS);
            TaskEntity t = doneTask(dispatchAt, dispatchAt.plus(1, ChronoUnit.DAYS));
            tasks.add(t);
            JulesSessionEntity session = new JulesSessionEntity();
            session.setCreatedAt(dispatchAt);
            when(julesSessionRepository.findByTaskId(t.getId())).thenReturn(List.of(session));
        }
        // Exactly 1 task genuinely in flight right now, consistent with the Little's Law expectation.
        TaskEntity inFlight = new TaskEntity();
        inFlight.setProject(project);
        inFlight.setStatus(TaskStatus.in_progress);
        tasks.add(inFlight);

        when(taskRepository.findAll()).thenReturn(tasks);

        var report = service.computeForProject(projectId);

        assertThat(report.wip()).isEqualTo(1);
        assertThat(report.throughputPerDay()).isEqualTo(1.0, org.assertj.core.data.Offset.offset(0.05));
        assertThat(report.avgCycleTimeDays()).isEqualTo(1.0, org.assertj.core.data.Offset.offset(0.05));
        assertThat(report.littlesLawInconsistent()).isFalse();
    }

    @Test
    void littlesLawFlaggedInconsistentWhenWipWildlyExceedsExpectation() {
        Instant base = Instant.now().minus(10, ChronoUnit.DAYS);
        List<TaskEntity> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Instant dispatchAt = base.plus(i, ChronoUnit.DAYS);
            TaskEntity t = doneTask(dispatchAt, dispatchAt.plus(1, ChronoUnit.DAYS));
            tasks.add(t);
            JulesSessionEntity session = new JulesSessionEntity();
            session.setCreatedAt(dispatchAt);
            when(julesSessionRepository.findByTaskId(t.getId())).thenReturn(List.of(session));
        }
        // 50 tasks "stuck" in flight - wildly more than throughput(1/day) x cycleTime(1 day) = 1 predicts.
        // Real shape of the SYSTEM_STALLED/pending_review incidents: status tracking says work is in
        // flight but the flow math says it shouldn't be.
        for (int i = 0; i < 50; i++) {
            TaskEntity stuck = new TaskEntity();
            stuck.setProject(project);
            stuck.setStatus(TaskStatus.pending_review);
            tasks.add(stuck);
        }

        when(taskRepository.findAll()).thenReturn(tasks);

        var report = service.computeForProject(projectId);

        assertThat(report.wip()).isEqualTo(50);
        assertThat(report.littlesLawInconsistent()).isTrue();
        assertThat(report.littlesLawDeviationRatio()).isGreaterThan(0.5);
    }

    @Test
    void wasteRatioComputedFromDismissedWishlists() {
        when(taskRepository.findAll()).thenReturn(Collections.emptyList());
        when(wishlistRepository.countByProjectIdAndStatus(eq(projectId), eq(WishlistStatus.dismissed))).thenReturn(3L);
        List<com.eneik.production.models.persistence.WishlistEntity> tenWishlists = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tenWishlists.add(new com.eneik.production.models.persistence.WishlistEntity());
        }
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(tenWishlists);

        var report = service.computeForProject(projectId);

        assertThat(report.wasteWishlistCount()).isEqualTo(3L);
        assertThat(report.totalWishlistCount()).isEqualTo(10L);
        assertThat(report.wasteRatio()).isEqualTo(0.3, org.assertj.core.data.Offset.offset(1e-9));
    }
}
