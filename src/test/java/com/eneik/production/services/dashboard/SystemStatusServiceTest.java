package com.eneik.production.services.dashboard;

import com.eneik.production.models.persistence.JulesSessionEntity;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemStatusServiceTest {

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

    private SystemStatusService newService(JulesSessionRepository sessions) {
        return newService(mock(TaskRepository.class), sessions);
    }

    private SystemStatusService newService(TaskRepository tasks, JulesSessionRepository sessions) {
        return new SystemStatusService(
                mock(SystemSettingsService.class),
                mock(AccountRepository.class),
                tasks,
                sessions,
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
}
