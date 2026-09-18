package com.eneik.production.services.toc;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.github.GitHubApiBudgetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Layer 2 (TOC) math verification - dynamic drum identification among the factory's own macro resources
 * (queue-length / capacity ratio, classical TOC: WIP piles up in front of the constraint) and the
 * variance-based buffer formula (bufferCapacity ≈ ceil(z × throughput × σ(cycleTime))) that replaces a
 * hardcoded "+2" with a number tied to this project's own measured cycle-time variance.
 */
public class ConstraintIdentificationServiceTest {

    private TaskRepository taskRepository;
    private AccountRepository accountRepository;
    private JulesSessionRepository julesSessionRepository;
    private GitHubApiBudgetService gitHubApiBudgetService;
    private ConstraintIdentificationService service;
    private UUID projectId;
    private ProjectEntity project;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        accountRepository = mock(AccountRepository.class);
        julesSessionRepository = mock(JulesSessionRepository.class);
        gitHubApiBudgetService = mock(GitHubApiBudgetService.class);

        projectId = UUID.randomUUID();
        project = new ProjectEntity();
        project.setId(projectId);

        when(accountRepository.findAllByOrderByNameAsc()).thenReturn(Collections.emptyList());
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(any())).thenReturn(Collections.emptyList());
        when(taskRepository.findByProjectIdAndStatus(any(), any())).thenReturn(Collections.emptyList());
        when(julesSessionRepository.findByStatusIn(any())).thenReturn(Collections.emptyList());
        when(gitHubApiBudgetService.snapshot()).thenReturn(new GitHubApiBudgetService.Snapshot(
                "available", true, 5000, 5000, 0, null, null, null, null, "fresh", Instant.now(), java.util.Map.of()));

        service = new ConstraintIdentificationService(taskRepository, accountRepository, julesSessionRepository, gitHubApiBudgetService);
        ReflectionTestUtils.setField(service, "nominalReviewCapacity", 3);
        ReflectionTestUtils.setField(service, "defaultAccountSessionSlots", 3);
    }

    private TaskEntity task(TaskStatus status) {
        TaskEntity t = new TaskEntity();
        t.setId(UUID.randomUUID());
        t.setProject(project);
        t.setStatus(status);
        return t;
    }

    @Test
    void dispatchBacklogFarExceedingSlotsIsIdentifiedAsDrum() {
        List<TaskEntity> tasks = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) tasks.add(task(TaskStatus.queued));
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(tasks);

        AccountEntity account = new AccountEntity();
        account.setEnabled(true);
        account.setStatus(com.eneik.production.models.persistence.AccountStatus.idle);
        account.setMaxConcurrentSessions(2);
        when(accountRepository.findAllByOrderByNameAsc()).thenReturn(List.of(account));

        var drum = service.identifyDrum(projectId);

        assertThat(drum.resource()).isEqualTo(ConstraintIdentificationService.ConstraintResource.DISPATCH_CAPACITY);
        assertThat(drum.pressure()).isEqualTo(10.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(drum.queueLength()).isEqualTo(20L);
        verify(taskRepository, org.mockito.Mockito.never()).findAll();
        verify(accountRepository, org.mockito.Mockito.never()).findAll();
    }

    @Test
    void nearExhaustedGithubBudgetIsIdentifiedAsDrumWhenOtherQueuesAreCalm() {
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(Collections.emptyList());
        when(gitHubApiBudgetService.snapshot()).thenReturn(new GitHubApiBudgetService.Snapshot(
                "exhausted", false, 5000, 10, 4990, null, null, null, null, "near limit", Instant.now(), java.util.Map.of()));

        var drum = service.identifyDrum(projectId);

        assertThat(drum.resource()).isEqualTo(ConstraintIdentificationService.ConstraintResource.GITHUB_API_BUDGET);
        assertThat(drum.pressure()).isEqualTo(1.0 - (10.0 / 5000.0), org.assertj.core.data.Offset.offset(1e-9));
        verify(taskRepository, org.mockito.Mockito.never()).findAll();
    }

    @Test
    void bufferRecommendationFloorsAtOneWithFewerThanTwoSamples() {
        when(taskRepository.findByProjectIdAndStatus(projectId, TaskStatus.done)).thenReturn(Collections.emptyList());

        var rec = service.recommendedBufferCapacity(projectId, 3.0);

        assertThat(rec.bufferCapacity()).isEqualTo(1);
        assertThat(rec.stdDevCycleTimeSeconds()).isEqualTo(0.0);
        verify(taskRepository, org.mockito.Mockito.never()).findAll();
    }

    @Test
    void bufferRecommendationScalesWithMeasuredVariance() {
        // 4 done tasks over an 8-day window, cycle times (days): 1, 1, 1, 5 -> mean=2, sample stdDev computable by hand.
        Instant base = Instant.now().minus(10, ChronoUnit.DAYS);
        List<TaskEntity> done = new java.util.ArrayList<>();
        int[] cycleDays = {1, 1, 1, 5};
        Instant cursor = base;
        for (int days : cycleDays) {
            TaskEntity t = task(TaskStatus.done);
            t.setCreatedAt(cursor);
            t.setUpdatedAt(cursor.plus(days, ChronoUnit.DAYS));
            done.add(t);
            cursor = cursor.plus(2, ChronoUnit.DAYS);
        }
        when(taskRepository.findByProjectIdAndStatus(projectId, TaskStatus.done)).thenReturn(done);

        var rec = service.recommendedBufferCapacity(projectId, 3.0);

        double meanSeconds = (1 + 1 + 1 + 5) / 4.0 * 86400.0;
        assertThat(rec.meanCycleTimeSeconds()).isEqualTo(meanSeconds, org.assertj.core.data.Offset.offset(1.0));
        assertThat(rec.stdDevCycleTimeSeconds()).isGreaterThan(0.0);
        assertThat(rec.bufferCapacity()).isGreaterThanOrEqualTo(1);
        assertThat(rec.sampleSize()).isEqualTo(4);
        verify(taskRepository, org.mockito.Mockito.never()).findAll();
    }

    @Test
    void identifyDrumIsScopedToProjectAndNeverUsesFindAll() {
        TaskEntity taskProjectA = task(TaskStatus.queued);
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(taskProjectA));

        com.eneik.production.models.persistence.JulesSessionEntity sessionA = new com.eneik.production.models.persistence.JulesSessionEntity();
        sessionA.setId(UUID.randomUUID());
        sessionA.setTaskId(taskProjectA.getId());
        sessionA.setStatus("running");

        com.eneik.production.models.persistence.JulesSessionEntity sessionOtherProject = new com.eneik.production.models.persistence.JulesSessionEntity();
        sessionOtherProject.setId(UUID.randomUUID());
        sessionOtherProject.setTaskId(UUID.randomUUID()); // unrelated task
        sessionOtherProject.setStatus("running");

        when(julesSessionRepository.findByStatusIn(any())).thenReturn(List.of(sessionA, sessionOtherProject));

        AccountEntity account = new AccountEntity();
        account.setEnabled(true);
        account.setStatus(com.eneik.production.models.persistence.AccountStatus.idle);
        account.setMaxConcurrentSessions(5);
        when(accountRepository.findAllByOrderByNameAsc()).thenReturn(List.of(account));

        var drum = service.identifyDrum(projectId);

        // Only sessionA belongs to Project A -> activeSessions = 1, sessionSlotPressure = 1 / 5 = 0.2
        assertThat(drum.capacity()).isGreaterThan(0.0);
        verify(taskRepository, org.mockito.Mockito.never()).findAll();
        verify(taskRepository, org.mockito.Mockito.never()).findById(any());
        verify(accountRepository, org.mockito.Mockito.never()).findAll();
    }

    @Test
    void drumCapacityExcludesNonOperationalAccounts() {
        TaskEntity taskA = task(TaskStatus.queued);
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(taskA));

        AccountEntity dailyLimited = new AccountEntity();
        dailyLimited.setEnabled(true);
        dailyLimited.setStatus(com.eneik.production.models.persistence.AccountStatus.daily_limited);
        dailyLimited.setMaxConcurrentSessions(10);

        AccountEntity disabled = new AccountEntity();
        disabled.setEnabled(false);
        disabled.setStatus(com.eneik.production.models.persistence.AccountStatus.idle);
        disabled.setMaxConcurrentSessions(10);

        AccountEntity apiBlocked = new AccountEntity();
        apiBlocked.setEnabled(true);
        apiBlocked.setStatus(com.eneik.production.models.persistence.AccountStatus.api_blocked);
        apiBlocked.setMaxConcurrentSessions(10);

        // All accounts are non-operational -> totalSlots must be 0
        when(accountRepository.findAllByOrderByNameAsc()).thenReturn(List.of(dailyLimited, disabled, apiBlocked));

        var drum = service.identifyDrum(projectId);

        // With queued tasks and 0 operational capacity, dispatch pressure is infinite
        assertThat(drum.resource()).isEqualTo(ConstraintIdentificationService.ConstraintResource.DISPATCH_CAPACITY);
        assertThat(drum.capacity()).isEqualTo(0.0);
        assertThat(drum.pressure()).isEqualTo(Double.MAX_VALUE);
    }
}
