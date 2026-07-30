package com.eneik.production.services;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.compiler.TechnicalLeadCompiler;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.monitor.SystemProgressTracker;
import com.eneik.production.services.operational.OperationalPolicyService;
import com.eneik.production.services.orchestration.BranchGarbageCollectorService;
import com.eneik.production.services.settings.SystemSettingsService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContinuousOrchestrationServiceTest {

    @Test
    void systemWorkSnapshotCountsOnlyActiveProjects() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        JulesSessionRepository julesSessionRepository = mock(JulesSessionRepository.class);

        ProjectEntity activeProject = project(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "active", ProjectStatus.active);
        ProjectEntity frozenProject = project(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "frozen", ProjectStatus.frozen);
        TaskEntity queued = task(activeProject, TaskStatus.queued);
        TaskEntity review = task(activeProject, TaskStatus.review);
        TaskEntity frozenQueued = task(frozenProject, TaskStatus.queued);
        WishlistEntity pending = wishlist(activeProject.getId(), WishlistStatus.pending);
        JulesSessionEntity reviewSession = new JulesSessionEntity();
        reviewSession.setTaskId(review.getId());
        reviewSession.setPrUrl("https://github.com/org/repo/pull/1");

        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(activeProject));
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(activeProject.getId())).thenReturn(List.of(queued, review));
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(frozenProject.getId())).thenReturn(List.of(frozenQueued));
        when(wishlistRepository.findByProjectId(activeProject.getId())).thenReturn(List.of(pending));
        when(julesSessionRepository.findByTaskId(review.getId())).thenReturn(List.of(reviewSession));

        ContinuousOrchestrationService service = new ContinuousOrchestrationService(
                projectRepository,
                mock(ProjectFlowService.class),
                mock(AccountRepository.class),
                julesSessionRepository,
                mock(com.eneik.production.services.jules.JulesDispatchService.class),
                wishlistRepository,
                mock(TechnicalLeadCompiler.class),
                mock(MLPredictionServiceClient.class),
                taskRepository,
                new SystemProgressTracker(),
                mock(SystemSettingsService.class),
                mock(PlannedWorkRecoveryService.class),
                mock(BranchGarbageCollectorService.class),
                mock(GitHubPullRequestService.class),
                mock(OperationalPolicyService.class)
        );

        ContinuousOrchestrationService.SystemWorkSnapshot snapshot = service.systemWorkSnapshot();

        assertEquals(1, snapshot.queuedTasks());
        assertEquals(1, snapshot.pendingWishlists());
        assertEquals(1, snapshot.activeNonTerminalTasks());
        assertEquals(1, snapshot.reviewTasksWithPr());
        assertTrue(snapshot.hasActionableWork());
    }

    private ProjectEntity project(UUID id, String name, ProjectStatus status) {
        ProjectEntity project = new ProjectEntity();
        project.setId(id);
        project.setName(name);
        project.setStatus(status);
        return project;
    }

    private TaskEntity task(ProjectEntity project, TaskStatus status) {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setStatus(status);
        return task;
    }

    private WishlistEntity wishlist(UUID projectId, WishlistStatus status) {
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(UUID.randomUUID());
        wishlist.setProjectId(projectId);
        wishlist.setStatus(status);
        return wishlist;
    }
}
