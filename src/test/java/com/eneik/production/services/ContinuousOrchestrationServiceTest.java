package com.eneik.production.services;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

    /**
     * Layer 2 (TOC)-adjacent fix (2026-08-01, live incident: several accounts failed FAILED_PRECONDITION
     * 9-13 times in a single day because the old recovery cooldown was a single fixed 30 minutes forever).
     * Verifies the exponential backoff: consecutiveApiBlockCount=1 -> base cooldown (30min), count=2 ->
     * doubled (60min), and the ceiling caps growth instead of retrying less and less forever.
     */
    @Test
    void recoverStaleBlockedAccountsUsesExponentialBackoffPerAccount() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        Instant now = Instant.now();

        AccountEntity firstBlockPastBaseCooldown = blockedAccount("a-first-block", 1, now.minus(31, ChronoUnit.MINUTES));
        AccountEntity secondBlockNotYetDoubledCooldown = blockedAccount("b-second-block-too-soon", 2, now.minus(31, ChronoUnit.MINUTES));
        AccountEntity secondBlockPastDoubledCooldown = blockedAccount("c-second-block-ready", 2, now.minus(61, ChronoUnit.MINUTES));
        AccountEntity manyBlocksPastCappedCooldown = blockedAccount("d-many-blocks-capped", 10, now.minus(500, ChronoUnit.MINUTES));

        when(accountRepository.findByStatusAndEnabledTrue(AccountStatus.api_blocked)).thenReturn(List.of(
                firstBlockPastBaseCooldown, secondBlockNotYetDoubledCooldown, secondBlockPastDoubledCooldown, manyBlocksPastCappedCooldown));
        when(accountRepository.resetSingleAccountFromApiBlocked(org.mockito.ArgumentMatchers.any())).thenReturn(1);

        ContinuousOrchestrationService service = new ContinuousOrchestrationService(
                mock(ProjectRepository.class),
                mock(ProjectFlowService.class),
                accountRepository,
                mock(JulesSessionRepository.class),
                mock(com.eneik.production.services.jules.JulesDispatchService.class),
                mock(WishlistRepository.class),
                mock(TechnicalLeadCompiler.class),
                mock(MLPredictionServiceClient.class),
                mock(TaskRepository.class),
                new SystemProgressTracker(),
                mock(SystemSettingsService.class),
                mock(PlannedWorkRecoveryService.class),
                mock(BranchGarbageCollectorService.class),
                mock(GitHubPullRequestService.class),
                mock(OperationalPolicyService.class)
        );
        ReflectionTestUtils.setField(service, "blockedAccountRecoveryCooldownMinutes", 30);
        ReflectionTestUtils.setField(service, "blockedAccountRecoveryMaxCooldownMinutes", 480);

        service.recoverStaleBlockedAccounts();

        verify(accountRepository, times(1)).resetSingleAccountFromApiBlocked(eq(firstBlockPastBaseCooldown.getId()));
        verify(accountRepository, never()).resetSingleAccountFromApiBlocked(eq(secondBlockNotYetDoubledCooldown.getId()));
        verify(accountRepository, times(1)).resetSingleAccountFromApiBlocked(eq(secondBlockPastDoubledCooldown.getId()));
        verify(accountRepository, times(1)).resetSingleAccountFromApiBlocked(eq(manyBlocksPastCappedCooldown.getId()));
    }

    private AccountEntity blockedAccount(String name, int consecutiveApiBlockCount, Instant statusChangedAt) {
        AccountEntity account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setName(name);
        account.setCapabilities("*");
        account.setStatus(AccountStatus.api_blocked);
        account.setConsecutiveApiBlockCount(consecutiveApiBlockCount);
        ReflectionTestUtils.setField(account, "statusChangedAt", statusChangedAt);
        return account;
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
