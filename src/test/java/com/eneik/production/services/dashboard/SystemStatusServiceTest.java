package com.eneik.production.services.dashboard;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskConflictEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.LinearIssueMetadataRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.audit.SixSigmaAuditService;
import com.eneik.production.services.github.GitHubApiBudgetService;
import com.eneik.production.services.googleai.GoogleAiResourceService;
import com.eneik.production.services.monitor.AiHealthTracker;
import com.eneik.production.services.monitor.SystemProgressTracker;
import com.eneik.production.services.settings.SystemSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemStatusServiceTest {

    @Test
    void projectAccountsFetchesOnlyAvailableAccountsForProject() throws Exception {
        UUID projectId = UUID.randomUUID();
        AccountEntity idle = account(AccountStatus.idle, null, "abcd1234");
        AccountEntity busy = account(AccountStatus.busy, projectId, " ");
        AccountRepository accounts = mock(AccountRepository.class);
        when(accounts.findAvailableForProjectOrderByNameAsc(projectId)).thenReturn(List.of(idle, busy));

        SystemStatusService service = newService(accounts);
        Method method = SystemStatusService.class.getDeclaredMethod("accounts", UUID.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> section = (Map<String, Object>) method.invoke(service, projectId);

        assertThat(section).containsEntry("total", 2)
                .containsEntry("operational", 2L)
                .containsEntry("effectiveOperational", 2L)
                .containsEntry("apiKeyConfigured", 1L)
                .containsEntry("idle", 1L)
                .containsEntry("busy", 1L)
                .containsEntry("decommissioned", 0L);
        assertThat((List<Map<String, Object>>) section.get("items")).hasSize(2);
        verify(accounts, never()).findAll();
        verify(accounts).findAvailableForProjectOrderByNameAsc(projectId);
    }


    @Test
    void globalJulesSessionSummaryUsesRepositoryCountsInsteadOfLoadingAllRows() throws Exception {
        JulesSessionRepository sessions = mock(JulesSessionRepository.class);
        when(sessions.count()).thenReturn(7L);
        when(sessions.countByStatus("queued")).thenReturn(1L);
        when(sessions.countByStatus("running")).thenReturn(2L);
        when(sessions.countByStatus("pr_opened")).thenReturn(3L);
        when(sessions.countByStatus("failed")).thenReturn(4L);
        when(sessions.countByStatus("stuck")).thenReturn(5L);

        SystemStatusService service = newService(sessions);
        Method method = SystemStatusService.class.getDeclaredMethod("julesSessions", UUID.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> section = (Map<String, Object>) method.invoke(service, (UUID) null);

        assertThat(section).containsEntry("total", 7L)
                .containsEntry("queued", 1L)
                .containsEntry("running", 2L)
                .containsEntry("pr_opened", 3L)
                .containsEntry("failed", 4L)
                .containsEntry("stuck", 5L);
        verify(sessions, never()).findAll();
        verify(sessions).count();
        verify(sessions).countByStatus("queued");
        verify(sessions).countByStatus("running");
        verify(sessions).countByStatus("pr_opened");
        verify(sessions).countByStatus("failed");
        verify(sessions).countByStatus("stuck");
    }


    @Test
    void projectJulesSessionSummaryFetchesOnlySessionsForProjectTasks() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID firstTaskId = UUID.randomUUID();
        UUID secondTaskId = UUID.randomUUID();
        TaskEntity firstTask = new TaskEntity();
        firstTask.setId(firstTaskId);
        TaskEntity secondTask = new TaskEntity();
        secondTask.setId(secondTaskId);

        TaskRepository tasks = mock(TaskRepository.class);
        JulesSessionRepository sessions = mock(JulesSessionRepository.class);
        when(tasks.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(firstTask, secondTask));

        JulesSessionEntity queued = new JulesSessionEntity();
        queued.setTaskId(firstTaskId);
        queued.setStatus("queued");
        JulesSessionEntity running = new JulesSessionEntity();
        running.setTaskId(secondTaskId);
        running.setStatus("running");
        when(sessions.findByTaskIdIn(argThat(ids -> ids.size() == 2
                && ids.contains(firstTaskId)
                && ids.contains(secondTaskId))))
                .thenReturn(List.of(queued, running));

        SystemStatusService service = newService(tasks, sessions);
        Method method = SystemStatusService.class.getDeclaredMethod("julesSessions", UUID.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> section = (Map<String, Object>) method.invoke(service, projectId);

        assertThat(section).containsEntry("total", 2L)
                .containsEntry("queued", 1L)
                .containsEntry("running", 1L)
                .containsEntry("pr_opened", 0L)
                .containsEntry("failed", 0L)
                .containsEntry("stuck", 0L);
        verify(sessions, never()).findAll();
        verify(sessions).findByTaskIdIn(argThat(ids -> ids.size() == 2
                && ids.contains(firstTaskId)
                && ids.contains(secondTaskId)));
    }


    @Test
    void projectConflictDpmoFetchesOnlyProjectReviewsAndConflicts() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setDescription("project task");

        TaskRepository tasks = mock(TaskRepository.class);
        JulesSessionRepository sessions = mock(JulesSessionRepository.class);
        PrReviewRepository reviews = mock(PrReviewRepository.class);
        TaskConflictRepository conflicts = mock(TaskConflictRepository.class);
        when(tasks.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(task));

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        when(sessions.findByTaskIdIn(argThat(ids -> ids.size() == 1 && ids.contains(taskId))))
                .thenReturn(List.of(session));

        PrReviewEntity review = new PrReviewEntity();
        review.setJulesSessionId(sessionId);
        review.setMerged(true);
        review.setCreatedAt(Instant.now());
        when(reviews.findByJulesSessionIdIn(argThat(ids -> ids.size() == 1 && ids.contains(sessionId))))
                .thenReturn(List.of(review));

        TaskConflictEntity conflict = new TaskConflictEntity();
        conflict.setId(UUID.randomUUID());
        conflict.setTask(task);
        conflict.setDetectedAt(Instant.now());
        conflict.setResolutionStatus("open");
        when(conflicts.findByTaskIdIn(argThat(ids -> ids.size() == 1 && ids.contains(taskId))))
                .thenReturn(List.of(conflict));

        SystemStatusService service = newService(tasks, sessions, reviews, conflicts);
        Method method = SystemStatusService.class.getDeclaredMethod("conflictDpmo", UUID.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> section = (Map<String, Object>) method.invoke(service, projectId);

        assertThat(section).containsEntry("totalMergeAttempts", 2L)
                .containsEntry("conflicts", 1L);
        verify(reviews, never()).findAll();
        verify(conflicts, never()).findAll();
        verify(sessions, never()).findAll();
        verify(reviews).findByJulesSessionIdIn(argThat(ids -> ids.size() == 1 && ids.contains(sessionId)));
        verify(conflicts).findByTaskIdIn(argThat(ids -> ids.size() == 1 && ids.contains(taskId)));
    }


    @Test
    void globalConflictDpmoCountsMergedReviewsAndConflictsWithoutLoadingAllRows() throws Exception {
        PrReviewRepository reviews = mock(PrReviewRepository.class);
        when(reviews.countByMergedTrue()).thenReturn(3L);
        when(reviews.countByMergedTrueAndCreatedAtAfter(any(Instant.class))).thenReturn(1L);

        TaskConflictRepository.ParetoRow conflictType = paretoRow("merge", 1L);
        TaskConflictRepository.ParetoRow resolutionStatus = paretoRow("auto_resolved", 1L);
        TaskConflictRepository conflicts = mock(TaskConflictRepository.class);
        when(conflicts.count()).thenReturn(1L);
        when(conflicts.countByDetectedAtAfter(any(Instant.class))).thenReturn(1L);
        when(conflicts.findActiveByResolutionStatusNot("auto_resolved")).thenReturn(List.of());
        when(conflicts.countByConflictType()).thenReturn(List.of(conflictType));
        when(conflicts.countByResolutionStatus()).thenReturn(List.of(resolutionStatus));

        SystemStatusService service = newService(mock(TaskRepository.class), mock(JulesSessionRepository.class), reviews, conflicts);
        Method method = SystemStatusService.class.getDeclaredMethod("conflictDpmo", UUID.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> section = (Map<String, Object>) method.invoke(service, (UUID) null);

        assertThat(section).containsEntry("totalMergeAttempts", 4L)
                .containsEntry("conflicts", 1L);
        assertThat((Map<String, Object>) section.get("last7Days")).containsEntry("totalMergeAttempts", 2L)
                .containsEntry("conflicts", 1L);
        assertThat((List<Map<String, Object>>) section.get("activeConflicts")).isEmpty();
        verify(reviews, never()).findAll();
        verify(conflicts, never()).findAll();
        verify(reviews).countByMergedTrue();
        verify(reviews).countByMergedTrueAndCreatedAtAfter(any(Instant.class));
        verify(conflicts).count();
        verify(conflicts).countByDetectedAtAfter(any(Instant.class));
        verify(conflicts).findActiveByResolutionStatusNot("auto_resolved");
        verify(conflicts).countByConflictType();
        verify(conflicts).countByResolutionStatus();
    }


    @Test
    void globalLinearCompletenessFetchesOnlyTasksWithLinearIssueIds() throws Exception {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setLinearIssueId("LIN-42");
        TaskEntity blank = new TaskEntity();
        blank.setId(UUID.randomUUID());
        blank.setLinearIssueId(" ");

        TaskRepository tasks = mock(TaskRepository.class);
        LinearIssueMetadataRepository linear = mock(LinearIssueMetadataRepository.class);
        when(tasks.findByLinearIssueIdIsNotNull()).thenReturn(List.of(task, blank));
        when(linear.findById(taskId)).thenReturn(Optional.empty());

        SystemStatusService service = newService(tasks, mock(JulesSessionRepository.class), linear);
        Method method = SystemStatusService.class.getDeclaredMethod("linearCompleteness", UUID.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> section = (Map<String, Object>) method.invoke(service, (UUID) null);

        assertThat(section).containsEntry("totalIssues", 1);
        verify(tasks, never()).findAll();
        verify(tasks).findByLinearIssueIdIsNotNull();
        verify(linear).findById(taskId);
    }


    @Test
    void operationalBlockersFetchesOnlySessionsForEachTerminalProject() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setName("finished-project");
        project.setStatus(ProjectStatus.accepted);
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.done);
        JulesSessionEntity session = new JulesSessionEntity();
        session.setTaskId(taskId);
        session.setStatus("running");

        SystemSettingsService settings = mock(SystemSettingsService.class);
        when(settings.effectiveValue("system_stall_status")).thenReturn("ok");
        GitHubApiBudgetService github = mock(GitHubApiBudgetService.class);
        when(github.snapshot()).thenReturn(new GitHubApiBudgetService.Snapshot(
                "ok", true, null, null, null, null, null, null, "", "", Instant.now(), Map.of()));
        ProjectRepository projects = mock(ProjectRepository.class);
        when(projects.findAll()).thenReturn(List.of(project));
        TaskRepository tasks = mock(TaskRepository.class);
        when(tasks.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(task));
        WishlistRepository wishlists = mock(WishlistRepository.class);
        when(wishlists.findByProjectId(projectId)).thenReturn(List.of());
        JulesSessionRepository sessions = mock(JulesSessionRepository.class);
        when(sessions.findByTaskIdIn(argThat(ids -> ids.size() == 1 && ids.contains(taskId))))
                .thenReturn(List.of(session));

        SystemStatusService service = new SystemStatusService(
                settings,
                mock(AccountRepository.class),
                tasks,
                sessions,
                mock(LinearIssueMetadataRepository.class),
                mock(JdbcTemplate.class),
                mock(PrReviewRepository.class),
                mock(TaskConflictRepository.class),
                wishlists,
                projects,
                mock(EmsMetricsService.class),
                mock(GoogleAiResourceService.class),
                github,
                mock(SystemProgressTracker.class),
                mock(AiHealthTracker.class),
                mock(Environment.class),
                mock(SixSigmaAuditService.class));
        Method method = SystemStatusService.class.getDeclaredMethod("operationalBlockers", UUID.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> section = (Map<String, Object>) method.invoke(service, (UUID) null);

        assertThat(section).containsEntry("status", "blocked")
                .containsEntry("count", 1);
        verify(sessions, never()).findAll();
        verify(sessions).findByTaskIdIn(argThat(ids -> ids.size() == 1 && ids.contains(taskId)));
    }

    private AccountEntity account(AccountStatus status, UUID currentProjectId, String apiKey) {
        AccountEntity account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setName(status.name());
        account.setStatus(status);
        account.setCapabilities("*");
        account.setCurrentProjectId(currentProjectId);
        account.setApiKey(apiKey);
        return account;
    }

    private SystemStatusService newService(AccountRepository accounts) {
        return new SystemStatusService(
                mock(SystemSettingsService.class),
                accounts,
                mock(TaskRepository.class),
                mock(JulesSessionRepository.class),
                mock(LinearIssueMetadataRepository.class),
                mock(JdbcTemplate.class),
                mock(PrReviewRepository.class),
                mock(TaskConflictRepository.class),
                mock(WishlistRepository.class),
                mock(ProjectRepository.class),
                mock(EmsMetricsService.class),
                mock(GoogleAiResourceService.class),
                mock(GitHubApiBudgetService.class),
                mock(SystemProgressTracker.class),
                mock(AiHealthTracker.class),
                mock(Environment.class),
                mock(SixSigmaAuditService.class));
    }

    private TaskConflictRepository.ParetoRow paretoRow(String name, long defects) {
        TaskConflictRepository.ParetoRow row = mock(TaskConflictRepository.ParetoRow.class);
        when(row.getName()).thenReturn(name);
        when(row.getDefects()).thenReturn(defects);
        return row;
    }

    private SystemStatusService newService(JulesSessionRepository sessions) {
        return newService(mock(TaskRepository.class), sessions);
    }

    private SystemStatusService newService(TaskRepository tasks, JulesSessionRepository sessions) {
        return newService(tasks, sessions, mock(LinearIssueMetadataRepository.class));
    }

    private SystemStatusService newService(TaskRepository tasks, JulesSessionRepository sessions,
                                           LinearIssueMetadataRepository linear) {
        return newService(tasks, sessions, linear, mock(PrReviewRepository.class), mock(TaskConflictRepository.class));
    }

    private SystemStatusService newService(TaskRepository tasks, JulesSessionRepository sessions,
                                           PrReviewRepository reviews, TaskConflictRepository conflicts) {
        return newService(tasks, sessions, mock(LinearIssueMetadataRepository.class), reviews, conflicts);
    }

    private SystemStatusService newService(TaskRepository tasks, JulesSessionRepository sessions,
                                           LinearIssueMetadataRepository linear,
                                           PrReviewRepository reviews, TaskConflictRepository conflicts) {
        return new SystemStatusService(
                mock(SystemSettingsService.class),
                mock(AccountRepository.class),
                tasks,
                sessions,
                linear,
                mock(JdbcTemplate.class),
                reviews,
                conflicts,
                mock(WishlistRepository.class),
                mock(ProjectRepository.class),
                mock(EmsMetricsService.class),
                mock(GoogleAiResourceService.class),
                mock(GitHubApiBudgetService.class),
                mock(SystemProgressTracker.class),
                mock(AiHealthTracker.class),
                mock(Environment.class),
                mock(SixSigmaAuditService.class));
    }
}
