package com.eneik.production.services.jules;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.ClientDeliverableReadinessService;
import com.eneik.production.services.github.GitHubPullRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Law 4 (Закон предмета слияния) Test Suite for {@link JulesDispatchService#reconcileDoneTasksNotReachedMain}.
 *
 * <p>Formal Invariant:
 * <pre>
 *   landed(\tau) \iff \exists r: merged(r) \land base(r) = main \land hasCode(r) (for code roles)
 * </pre>
 *
 * <p>The predicate that decides whether work has reached main (used in delivery reality and value calculation)
 * must agree with the sweep reconciliation:
 * <ul>
 *   <li>If {@code hasRequiredMergeEvidence(\tau)} is true, the task has delivered code to main; it is bypassed
 *       immediately and its sessions/PR snapshots are never queried.</li>
 *   <li>If {@code hasRequiredMergeEvidence(\tau)} is false, the task claims {@code done} without verified code
 *       evidence; reconciliation proceeds to inspect sessions and closed PRs to observe non-delivery.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class JulesDispatchServiceLaw4MergeEvidenceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ClientDeliverableReadinessService readinessService;

    @Mock
    private JulesSessionRepository julesSessionRepository;

    @Mock
    private GitHubPullRequestService gitHubPullRequestService;

    private JulesDispatchService julesDispatchService;
    private ProjectEntity project;

    @BeforeEach
    void setUp() {
        julesDispatchService = mock(JulesDispatchService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(julesDispatchService, "taskRepository", taskRepository);
        ReflectionTestUtils.setField(julesDispatchService, "readinessService", readinessService);
        ReflectionTestUtils.setField(julesDispatchService, "julesSessionRepository", julesSessionRepository);
        ReflectionTestUtils.setField(julesDispatchService, "gitHubPullRequestService", gitHubPullRequestService);

        project = new ProjectEntity();
        project.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Law 4: Task with required merge evidence bypasses session inquiry in reconcileDoneTasksNotReachedMain")
    void reconcileSkipsDoneTaskWhenMergeEvidencePresent() {
        TaskEntity task = createTask(UUID.randomUUID(), project);
        when(taskRepository.findByStatus(TaskStatus.done)).thenReturn(List.of(task));
        when(readinessService.isAuxiliaryTask(task)).thenReturn(false);
        when(readinessService.hasRequiredMergeEvidence(task)).thenReturn(true);

        Map<UUID, GitHubPullRequestService.PullRequestSnapshot> snapshots = new HashMap<>();
        julesDispatchService.reconcileDoneTasksNotReachedMain(snapshots);

        // Task delivered code to main: no session lookup, no PR snapshot query
        verify(julesSessionRepository, never()).findByTaskId(any());
        verify(gitHubPullRequestService, never()).pullRequestSnapshot(any());
        verify(readinessService).hasRequiredMergeEvidence(task);
    }

    @Test
    @DisplayName("Law 4: Task lacking merge evidence proceeds to inspect sessions and snapshots")
    void reconcileInspectsDoneTaskWhenMergeEvidenceMissing() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = createTask(taskId, project);
        when(taskRepository.findByStatus(TaskStatus.done)).thenReturn(List.of(task));
        when(readinessService.isAuxiliaryTask(task)).thenReturn(false);
        when(readinessService.hasRequiredMergeEvidence(task)).thenReturn(false);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setTaskId(taskId);
        session.setExternalSessionId("session-123");
        session.setCreatedAt(Instant.now());
        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(session));

        GitHubPullRequestService.PullRequestSnapshot snapshot =
                new GitHubPullRequestService.PullRequestSnapshot(true, "owner", "repo", Collections.emptyList(), Collections.emptyList(), null);
        when(gitHubPullRequestService.pullRequestSnapshot(project)).thenReturn(snapshot);

        Map<UUID, GitHubPullRequestService.PullRequestSnapshot> snapshots = new HashMap<>();
        julesDispatchService.reconcileDoneTasksNotReachedMain(snapshots);

        // Task marked done without merge evidence must be investigated
        verify(readinessService).hasRequiredMergeEvidence(task);
        verify(julesSessionRepository).findByTaskId(taskId);
        verify(gitHubPullRequestService).pullRequestSnapshot(project);
    }

    @Test
    @DisplayName("Law 4: Auxiliary task without merge evidence is bypassed without querying sessions")
    void reconcileSkipsAuxiliaryTaskEvenIfNoMergeEvidence() {
        TaskEntity task = createTask(UUID.randomUUID(), project);
        when(taskRepository.findByStatus(TaskStatus.done)).thenReturn(List.of(task));
        when(readinessService.isAuxiliaryTask(task)).thenReturn(true);

        Map<UUID, GitHubPullRequestService.PullRequestSnapshot> snapshots = new HashMap<>();
        julesDispatchService.reconcileDoneTasksNotReachedMain(snapshots);

        verify(readinessService, never()).hasRequiredMergeEvidence(task);
        verify(julesSessionRepository, never()).findByTaskId(any());
    }

    @Test
    @DisplayName("Law 4: Task with null project is safely bypassed without NPE")
    void reconcileSkipsTaskWithNullProject() {
        TaskEntity task = createTask(UUID.randomUUID(), null);
        when(taskRepository.findByStatus(TaskStatus.done)).thenReturn(List.of(task));

        Map<UUID, GitHubPullRequestService.PullRequestSnapshot> snapshots = new HashMap<>();
        julesDispatchService.reconcileDoneTasksNotReachedMain(snapshots);

        verify(readinessService, never()).hasRequiredMergeEvidence(any());
        verify(julesSessionRepository, never()).findByTaskId(any());
    }

    private TaskEntity createTask(UUID id, ProjectEntity proj) {
        TaskEntity task = new TaskEntity();
        task.setId(id);
        task.setProject(proj);
        task.initializeStatus(TaskStatus.done);
        return task;
    }
}
