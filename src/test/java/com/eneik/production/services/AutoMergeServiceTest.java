package com.eneik.production.services;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.services.github.GitHubPullRequestService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class AutoMergeServiceTest {

    @Test
    void onlyPollsReviewStatesThatCanStillBecomeMergeable() {
        assertTrue(AutoMergeService.isReviewPollCandidate(reviewWithStatus(null)));
        assertTrue(AutoMergeService.isReviewPollCandidate(reviewWithStatus("success")));
        assertTrue(AutoMergeService.isReviewPollCandidate(reviewWithStatus("pending")));
        assertTrue(AutoMergeService.isReviewPollCandidate(reviewWithStatus("unavailable")));
        // Systemic fix (2026-07-23, confirmed live on test-thirty-fifth, task 17e3f9ae / PR#28): "conflict"
        // genuinely CAN still become mergeable - that's the entire premise of handleMergeConflict's 3-attempt
        // in-place resolution. Excluding it here used to permanently deadlock the retry loop: it was the
        // ONLY code path that could ever re-check GitHub's real mergeable state or drive a second attempt,
        // and this filter prevented it from ever running again after the first conflict. Confirmed live:
        // Jules pushed a fix commit, CI went green, but GitHub still reported a real conflict - and nothing
        // ever re-checked because the review had already dropped out of pendingReviews for good.
        assertTrue(AutoMergeService.isReviewPollCandidate(reviewWithStatus("conflict")));
        // Systemic fix (2026-07-31, confirmed live: PR#13/task 529e5252, already approved with nothing
        // wrong of its own): executeMerge sets this status the instant Flow Core denies MERGE_PR for a
        // reason unrelated to this specific review (e.g. a DIFFERENT review elsewhere is failing). Same
        // deadlock shape as "conflict" above - excluding it here meant the review permanently dropped out
        // of pendingReviews the moment it was first denied, even after the real blocker fully resolved.
        assertTrue(AutoMergeService.isReviewPollCandidate(reviewWithStatus("policy_denied")));

        assertFalse(AutoMergeService.isReviewPollCandidate(reviewWithStatus("failure")));
        // Genuinely terminal - handleMergeConflict's own 3-attempt cap is exhausted, so unlike plain
        // "conflict" this must NOT be retried (it would re-escalate a brand new TaskConflictEntity forever).
        assertFalse(AutoMergeService.isReviewPollCandidate(reviewWithStatus("escalated")));
        assertFalse(AutoMergeService.isReviewPollCandidate(reviewWithStatus("owner_mismatch")));
        assertFalse(AutoMergeService.isReviewPollCandidate(reviewWithStatus("unowned")));
        assertFalse(AutoMergeService.isReviewPollCandidate(reviewWithStatus("invalid_pr")));
        // Genuinely terminal (2026-07-24) - a PR closed without merging is dead, never retry it.
        assertFalse(AutoMergeService.isReviewPollCandidate(reviewWithStatus("closed_unmerged")));
    }

    @Test
    void escalatedConflictGetsTerminalCiStatusSoItStopsBeingRetried() {
        var prReviews = mock(com.eneik.production.repositories.PrReviewRepository.class);
        var sessions = mock(com.eneik.production.repositories.JulesSessionRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        var conflicts = mock(com.eneik.production.repositories.TaskConflictRepository.class);
        var claims = mock(ClaimService.class);
        AutoMergeService service = new AutoMergeService(
                prReviews, sessions, tasks,
                mock(com.eneik.production.services.settings.SystemSettingsService.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(com.eneik.production.services.advice.RoleAdviceLoopService.class),
                conflicts, mock(com.eneik.production.services.jules.JulesDispatchService.class),
                mock(RoleCapabilityLoader.class), mock(com.eneik.production.repositories.WishlistRepository.class),
                mock(MLPredictionServiceClient.class),
                mock(com.eneik.production.services.github.GitHubPullRequestService.class),
                new com.eneik.production.services.github.GitHubApiBudgetService(),
                mock(com.eneik.production.services.video.VideoAssetService.class),
                mock(com.eneik.production.services.dashboard.ProjectOperationalContextService.class),
                mock(com.eneik.production.services.monitor.SystemProgressTracker.class),
                mock(CodeChangeClassifier.class), mock(com.eneik.production.repositories.FeatureThreadRepository.class),
                claims, mock(com.eneik.production.repositories.ProjectRepository.class),
                mock(ClientDeliverableReadinessService.class),
                mock(com.eneik.production.services.GeminiContextService.class),
                mock(com.eneik.production.services.ProjectFlowService.class));

        UUID taskId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setStatus("pr_opened");
        session.setExternalSessionId("sessions/conflict-escalation");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.review);

        PrReviewEntity review = reviewWithStatus("conflict");
        review.setJulesSessionId(sessionId);
        review.setPrUrl("https://github.com/org/repo/pull/28");
        review.setMerged(false);

        com.eneik.production.models.persistence.TaskConflictEntity existingConflict =
                new com.eneik.production.models.persistence.TaskConflictEntity();
        existingConflict.setTask(task);
        existingConflict.setResolutionAttempts(2);
        existingConflict.setResolutionStatus("pending");

        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessions.findByTaskId(taskId)).thenReturn(List.of(session));
        when(tasks.findById(taskId)).thenReturn(Optional.of(task));
        when(prReviews.existsByJulesSessionIdInAndMergedTrue(List.of(sessionId))).thenReturn(false);
        when(conflicts.findFirstByTaskIdAndResolutionStatus(taskId, "pending")).thenReturn(Optional.of(existingConflict));

        // Third attempt (existing conflict already at 2) - crosses the 3-attempt cap this same call.
        service.handleMergeConflict(review, null, null, null, null);

        assertEquals("escalated", existingConflict.getResolutionStatus());
        assertEquals("escalated", review.getCiStatus());
        // The whole point: once genuinely exhausted, this must fall OUT of the poll-candidate set, or the
        // next tick would create a brand new TaskConflictEntity and re-escalate forever.
        assertFalse(AutoMergeService.isReviewPollCandidate(review));
    }

    @Test
    void terminalGithubStateMarksClosedPrAsClosedUnmergedBeforePolling() {
        var prReviews = mock(com.eneik.production.repositories.PrReviewRepository.class);
        var settings = mock(com.eneik.production.services.settings.SystemSettingsService.class);
        var gitHub = mock(com.eneik.production.services.github.GitHubPullRequestService.class);
        AutoMergeService service = new AutoMergeService(
                prReviews,
                mock(com.eneik.production.repositories.JulesSessionRepository.class),
                mock(com.eneik.production.repositories.TaskRepository.class),
                settings,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(com.eneik.production.services.advice.RoleAdviceLoopService.class),
                mock(com.eneik.production.repositories.TaskConflictRepository.class),
                mock(com.eneik.production.services.jules.JulesDispatchService.class),
                mock(RoleCapabilityLoader.class), mock(com.eneik.production.repositories.WishlistRepository.class),
                mock(MLPredictionServiceClient.class),
                gitHub,
                new com.eneik.production.services.github.GitHubApiBudgetService(),
                mock(com.eneik.production.services.video.VideoAssetService.class),
                mock(com.eneik.production.services.dashboard.ProjectOperationalContextService.class),
                mock(com.eneik.production.services.monitor.SystemProgressTracker.class),
                mock(CodeChangeClassifier.class), mock(com.eneik.production.repositories.FeatureThreadRepository.class),
                mock(ClaimService.class), mock(com.eneik.production.repositories.ProjectRepository.class),
                mock(ClientDeliverableReadinessService.class),
                mock(com.eneik.production.services.GeminiContextService.class),
                mock(com.eneik.production.services.ProjectFlowService.class));

        PrReviewEntity review = reviewWithStatus("success");
        review.setPrUrl("https://github.com/org/repo/pull/44");
        review.setMerged(false);

        when(settings.effectiveBoolean("github_enabled")).thenReturn(true);
        when(prReviews.findByMergedFalseOrMergedIsNull()).thenReturn(List.of(review));
        when(gitHub.fetchPullRequestByNumber("org", "repo", 44)).thenReturn(Optional.of(
                new com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest(
                        "https://github.com/org/repo/pull/44",
                        44,
                        "closed stale work",
                        "jules-closed",
                        "jules",
                        false,
                        "main",
                        true,
                        java.time.Instant.now())));

        assertEquals(1, service.reconcileTerminalGithubStateForReviews());
        assertEquals("closed_unmerged", review.getCiStatus());
        assertFalse(AutoMergeService.isReviewPollCandidate(review));
        verify(prReviews).save(review);
    }

    @Test
    void terminalGithubStateSkipsAReviewBelongingToANonActiveProjectWithoutCallingGitHub() {
        // GitHub budget guard (2026-07-31): live-measured, this loop's GitHub calls were overwhelmingly
        // spent reconciling reviews for frozen/accepted projects with nothing left to reconcile toward.
        var prReviews = mock(com.eneik.production.repositories.PrReviewRepository.class);
        var settings = mock(com.eneik.production.services.settings.SystemSettingsService.class);
        var gitHub = mock(com.eneik.production.services.github.GitHubPullRequestService.class);
        var julesSessions = mock(com.eneik.production.repositories.JulesSessionRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        AutoMergeService service = new AutoMergeService(
                prReviews,
                julesSessions,
                tasks,
                settings,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(com.eneik.production.services.advice.RoleAdviceLoopService.class),
                mock(com.eneik.production.repositories.TaskConflictRepository.class),
                mock(com.eneik.production.services.jules.JulesDispatchService.class),
                mock(RoleCapabilityLoader.class), mock(com.eneik.production.repositories.WishlistRepository.class),
                mock(MLPredictionServiceClient.class),
                gitHub,
                new com.eneik.production.services.github.GitHubApiBudgetService(),
                mock(com.eneik.production.services.video.VideoAssetService.class),
                mock(com.eneik.production.services.dashboard.ProjectOperationalContextService.class),
                mock(com.eneik.production.services.monitor.SystemProgressTracker.class),
                mock(CodeChangeClassifier.class), mock(com.eneik.production.repositories.FeatureThreadRepository.class),
                mock(ClaimService.class), mock(com.eneik.production.repositories.ProjectRepository.class),
                mock(ClientDeliverableReadinessService.class),
                mock(com.eneik.production.services.GeminiContextService.class),
                mock(com.eneik.production.services.ProjectFlowService.class));

        java.util.UUID sessionId = java.util.UUID.randomUUID();
        java.util.UUID taskId = java.util.UUID.randomUUID();
        PrReviewEntity review = reviewWithStatus("success");
        review.setPrUrl("https://github.com/org/repo/pull/78");
        review.setMerged(false);
        review.setJulesSessionId(sessionId);

        com.eneik.production.models.persistence.ProjectEntity frozenProject =
                new com.eneik.production.models.persistence.ProjectEntity();
        frozenProject.setId(java.util.UUID.randomUUID());
        frozenProject.setStatus(com.eneik.production.models.persistence.ProjectStatus.frozen);
        com.eneik.production.models.persistence.TaskEntity task =
                new com.eneik.production.models.persistence.TaskEntity();
        task.setId(taskId);
        task.setProject(frozenProject);
        com.eneik.production.models.persistence.JulesSessionEntity session =
                new com.eneik.production.models.persistence.JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);

        when(settings.effectiveBoolean("github_enabled")).thenReturn(true);
        when(prReviews.findByMergedFalseOrMergedIsNull()).thenReturn(List.of(review));
        when(julesSessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(tasks.findById(taskId)).thenReturn(Optional.of(task));

        assertEquals(0, service.reconcileTerminalGithubStateForReviews());
        verifyNoInteractions(gitHub);
        assertEquals("success", review.getCiStatus());
    }

    // 2026-08-01: the belongsToActiveProject guard above was originally wired into ONLY
    // reconcileTerminalGithubStateForReviews - confirmed live that resurrectTriviallyEscalatedConflicts and
    // resurrectAlreadyMergedReviews shared the exact same unscoped prReviewRepository query and kept
    // burning the shared GitHub API budget on an already-accepted project (test-thirty-third) every single
    // processAutoMerge tick, starving the genuinely active project's coverage/review dispatch. Both direct
    // regression tests for that incident.

    @Test
    void resurrectTriviallyEscalatedConflictsSkipsAReviewBelongingToANonActiveProjectWithoutCallingGitHub() {
        var prReviews = mock(com.eneik.production.repositories.PrReviewRepository.class);
        var settings = mock(com.eneik.production.services.settings.SystemSettingsService.class);
        var gitHub = mock(com.eneik.production.services.github.GitHubPullRequestService.class);
        var julesSessions = mock(com.eneik.production.repositories.JulesSessionRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        AutoMergeService service = new AutoMergeService(
                prReviews,
                julesSessions,
                tasks,
                settings,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(com.eneik.production.services.advice.RoleAdviceLoopService.class),
                mock(com.eneik.production.repositories.TaskConflictRepository.class),
                mock(com.eneik.production.services.jules.JulesDispatchService.class),
                mock(RoleCapabilityLoader.class), mock(com.eneik.production.repositories.WishlistRepository.class),
                mock(MLPredictionServiceClient.class),
                gitHub,
                new com.eneik.production.services.github.GitHubApiBudgetService(),
                mock(com.eneik.production.services.video.VideoAssetService.class),
                mock(com.eneik.production.services.dashboard.ProjectOperationalContextService.class),
                mock(com.eneik.production.services.monitor.SystemProgressTracker.class),
                mock(CodeChangeClassifier.class), mock(com.eneik.production.repositories.FeatureThreadRepository.class),
                mock(ClaimService.class), mock(com.eneik.production.repositories.ProjectRepository.class),
                mock(ClientDeliverableReadinessService.class),
                mock(com.eneik.production.services.GeminiContextService.class),
                mock(com.eneik.production.services.ProjectFlowService.class));

        java.util.UUID sessionId = java.util.UUID.randomUUID();
        java.util.UUID taskId = java.util.UUID.randomUUID();
        PrReviewEntity review = reviewWithStatus("escalated");
        review.setPrUrl("https://github.com/org/repo/pull/5");
        review.setMerged(false);
        review.setJulesSessionId(sessionId);

        com.eneik.production.models.persistence.ProjectEntity acceptedProject =
                new com.eneik.production.models.persistence.ProjectEntity();
        acceptedProject.setId(java.util.UUID.randomUUID());
        acceptedProject.setStatus(com.eneik.production.models.persistence.ProjectStatus.accepted);
        com.eneik.production.models.persistence.TaskEntity task =
                new com.eneik.production.models.persistence.TaskEntity();
        task.setId(taskId);
        task.setProject(acceptedProject);
        com.eneik.production.models.persistence.JulesSessionEntity session =
                new com.eneik.production.models.persistence.JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);

        when(settings.effectiveBoolean("github_enabled")).thenReturn(true);
        when(settings.effectiveValue("github_token")).thenReturn("dummy-token");
        when(prReviews.findByMergedFalseOrMergedIsNull()).thenReturn(List.of(review));
        when(julesSessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(tasks.findById(taskId)).thenReturn(Optional.of(task));

        service.resurrectTriviallyEscalatedConflicts();

        verifyNoInteractions(gitHub);
    }

    @Test
    void resurrectAlreadyMergedReviewsSkipsAReviewBelongingToANonActiveProjectWithoutCallingGitHub() {
        var prReviews = mock(com.eneik.production.repositories.PrReviewRepository.class);
        var settings = mock(com.eneik.production.services.settings.SystemSettingsService.class);
        var gitHub = mock(com.eneik.production.services.github.GitHubPullRequestService.class);
        var julesSessions = mock(com.eneik.production.repositories.JulesSessionRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        AutoMergeService service = new AutoMergeService(
                prReviews,
                julesSessions,
                tasks,
                settings,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(com.eneik.production.services.advice.RoleAdviceLoopService.class),
                mock(com.eneik.production.repositories.TaskConflictRepository.class),
                mock(com.eneik.production.services.jules.JulesDispatchService.class),
                mock(RoleCapabilityLoader.class), mock(com.eneik.production.repositories.WishlistRepository.class),
                mock(MLPredictionServiceClient.class),
                gitHub,
                new com.eneik.production.services.github.GitHubApiBudgetService(),
                mock(com.eneik.production.services.video.VideoAssetService.class),
                mock(com.eneik.production.services.dashboard.ProjectOperationalContextService.class),
                mock(com.eneik.production.services.monitor.SystemProgressTracker.class),
                mock(CodeChangeClassifier.class), mock(com.eneik.production.repositories.FeatureThreadRepository.class),
                mock(ClaimService.class), mock(com.eneik.production.repositories.ProjectRepository.class),
                mock(ClientDeliverableReadinessService.class),
                mock(com.eneik.production.services.GeminiContextService.class),
                mock(com.eneik.production.services.ProjectFlowService.class));

        java.util.UUID sessionId = java.util.UUID.randomUUID();
        java.util.UUID taskId = java.util.UUID.randomUUID();
        PrReviewEntity review = reviewWithStatus("success");
        review.setPrUrl("https://github.com/org/repo/pull/5");
        review.setMerged(false);
        review.setJulesSessionId(sessionId);

        com.eneik.production.models.persistence.ProjectEntity acceptedProject =
                new com.eneik.production.models.persistence.ProjectEntity();
        acceptedProject.setId(java.util.UUID.randomUUID());
        acceptedProject.setStatus(com.eneik.production.models.persistence.ProjectStatus.accepted);
        com.eneik.production.models.persistence.TaskEntity task =
                new com.eneik.production.models.persistence.TaskEntity();
        task.setId(taskId);
        task.setProject(acceptedProject);
        com.eneik.production.models.persistence.JulesSessionEntity session =
                new com.eneik.production.models.persistence.JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);

        when(settings.effectiveBoolean("github_enabled")).thenReturn(true);
        when(prReviews.findAll()).thenReturn(List.of(review));
        when(julesSessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(tasks.findById(taskId)).thenReturn(Optional.of(task));

        service.resurrectAlreadyMergedReviews();

        verifyNoInteractions(gitHub);
    }

    @Test
    void mergedSiblingRetiresLateConflictWithoutReopeningTask() {
        var prReviews = mock(com.eneik.production.repositories.PrReviewRepository.class);
        var sessions = mock(com.eneik.production.repositories.JulesSessionRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        var conflicts = mock(com.eneik.production.repositories.TaskConflictRepository.class);
        var claims = mock(ClaimService.class);
        AutoMergeService service = new AutoMergeService(
                prReviews, sessions, tasks,
                mock(com.eneik.production.services.settings.SystemSettingsService.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(com.eneik.production.services.advice.RoleAdviceLoopService.class),
                conflicts, mock(com.eneik.production.services.jules.JulesDispatchService.class),
                mock(RoleCapabilityLoader.class), mock(com.eneik.production.repositories.WishlistRepository.class),
                mock(MLPredictionServiceClient.class),
                mock(com.eneik.production.services.github.GitHubPullRequestService.class),
                new com.eneik.production.services.github.GitHubApiBudgetService(),
                mock(com.eneik.production.services.video.VideoAssetService.class),
                mock(com.eneik.production.services.dashboard.ProjectOperationalContextService.class),
                mock(com.eneik.production.services.monitor.SystemProgressTracker.class),
                mock(CodeChangeClassifier.class), mock(com.eneik.production.repositories.FeatureThreadRepository.class),
                claims, mock(com.eneik.production.repositories.ProjectRepository.class),
                mock(ClientDeliverableReadinessService.class),
                mock(com.eneik.production.services.GeminiContextService.class),
                mock(com.eneik.production.services.ProjectFlowService.class));

        UUID taskId = UUID.randomUUID();
        JulesSessionEntity staleSession = new JulesSessionEntity();
        staleSession.setId(UUID.randomUUID());
        staleSession.setTaskId(taskId);
        staleSession.setStatus("running");
        staleSession.setExternalSessionId("sessions/late-rebase");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.claimed);

        PrReviewEntity staleReview = reviewWithStatus("success");
        staleReview.setPrUrl("https://github.com/org/repo/pull/old");
        staleReview.setMerged(false);

        when(sessions.findByTaskId(taskId)).thenReturn(List.of(staleSession));
        when(prReviews.existsByJulesSessionIdInAndMergedTrue(List.of(staleSession.getId()))).thenReturn(true);
        when(conflicts.findFirstByTaskIdAndResolutionStatus(taskId, "pending")).thenReturn(Optional.empty());

        assertTrue(service.reconcileReviewAgainstTaskOutcome(staleReview, task));
        assertEquals(TaskStatus.done, task.getStatus());
        assertEquals("superseded", staleReview.getCiStatus());
        assertEquals("cancelled", staleSession.getStatus());
        verify(claims).releaseTerminalClaim(taskId);
        verify(tasks).save(task);
        verify(prReviews).save(staleReview);
    }

    @Test
    void periodicReconcileRepairsHistoricalMergedTaskEvenWhenOldReviewIsNoLongerPolled() {
        var prReviews = mock(com.eneik.production.repositories.PrReviewRepository.class);
        var sessions = mock(com.eneik.production.repositories.JulesSessionRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        var conflicts = mock(com.eneik.production.repositories.TaskConflictRepository.class);
        var claims = mock(ClaimService.class);
        AutoMergeService service = new AutoMergeService(
                prReviews, sessions, tasks,
                mock(com.eneik.production.services.settings.SystemSettingsService.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(com.eneik.production.services.advice.RoleAdviceLoopService.class),
                conflicts, mock(com.eneik.production.services.jules.JulesDispatchService.class),
                mock(RoleCapabilityLoader.class), mock(com.eneik.production.repositories.WishlistRepository.class),
                mock(MLPredictionServiceClient.class),
                mock(com.eneik.production.services.github.GitHubPullRequestService.class),
                new com.eneik.production.services.github.GitHubApiBudgetService(),
                mock(com.eneik.production.services.video.VideoAssetService.class),
                mock(com.eneik.production.services.dashboard.ProjectOperationalContextService.class),
                mock(com.eneik.production.services.monitor.SystemProgressTracker.class),
                mock(CodeChangeClassifier.class), mock(com.eneik.production.repositories.FeatureThreadRepository.class),
                claims, mock(com.eneik.production.repositories.ProjectRepository.class),
                mock(ClientDeliverableReadinessService.class),
                mock(com.eneik.production.services.GeminiContextService.class),
                mock(com.eneik.production.services.ProjectFlowService.class));

        UUID taskId = UUID.randomUUID();
        JulesSessionEntity winner = new JulesSessionEntity();
        winner.setId(UUID.randomUUID());
        winner.setTaskId(taskId);
        winner.setStatus("cancelled");
        JulesSessionEntity duplicate = new JulesSessionEntity();
        duplicate.setId(UUID.randomUUID());
        duplicate.setTaskId(taskId);
        duplicate.setStatus("running");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.pending_review);

        PrReviewEntity merged = reviewWithStatus("success");
        merged.setJulesSessionId(winner.getId());
        merged.setPrUrl("https://github.com/org/repo/pull/merged");
        merged.setMerged(true);
        PrReviewEntity oldConflict = reviewWithStatus("conflict");
        oldConflict.setJulesSessionId(duplicate.getId());
        oldConflict.setPrUrl("https://github.com/org/repo/pull/old");
        oldConflict.setMerged(false);

        when(prReviews.findAll()).thenReturn(List.of(merged, oldConflict));
        when(sessions.findById(winner.getId())).thenReturn(Optional.of(winner));
        when(sessions.findByTaskId(taskId)).thenReturn(List.of(winner, duplicate));
        when(tasks.findById(taskId)).thenReturn(Optional.of(task));
        when(conflicts.findFirstByTaskIdAndResolutionStatus(taskId, "pending")).thenReturn(Optional.empty());

        service.reconcileMergedTaskOutcomes();

        assertEquals(TaskStatus.done, task.getStatus());
        assertEquals("cancelled", duplicate.getStatus());
        assertEquals("superseded", oldConflict.getCiStatus());
        verify(claims).releaseTerminalClaim(taskId);
    }

    @Test
    void mergedGithubPrRepairsTaskEvenWhenSessionNeverRecordedItsPrUrlLocally() {
        // Regression test for the 2026-07-31 incident: task 597cbced sat in pending_review for 9+ hours
        // because its session's local prUrl was never set (the session never crossed the local edge that
        // records it), so reconcileMergedGitHubPullRequests' exact-prUrl match never found the underlying
        // PR even though it was already merged on GitHub and this sweep ran every 60 seconds the whole time.
        var prReviews = mock(com.eneik.production.repositories.PrReviewRepository.class);
        var sessions = mock(com.eneik.production.repositories.JulesSessionRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        var conflicts = mock(com.eneik.production.repositories.TaskConflictRepository.class);
        var claims = mock(ClaimService.class);
        var settings = mock(com.eneik.production.services.settings.SystemSettingsService.class);
        var gitHub = mock(com.eneik.production.services.github.GitHubPullRequestService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        AutoMergeService service = new AutoMergeService(
                prReviews, sessions, tasks, settings,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(com.eneik.production.services.advice.RoleAdviceLoopService.class),
                conflicts, mock(com.eneik.production.services.jules.JulesDispatchService.class),
                mock(RoleCapabilityLoader.class), mock(com.eneik.production.repositories.WishlistRepository.class),
                mock(MLPredictionServiceClient.class),
                gitHub,
                new com.eneik.production.services.github.GitHubApiBudgetService(),
                mock(com.eneik.production.services.video.VideoAssetService.class),
                mock(com.eneik.production.services.dashboard.ProjectOperationalContextService.class),
                mock(com.eneik.production.services.monitor.SystemProgressTracker.class),
                mock(CodeChangeClassifier.class), mock(com.eneik.production.repositories.FeatureThreadRepository.class),
                claims, projects,
                mock(ClientDeliverableReadinessService.class),
                mock(com.eneik.production.services.GeminiContextService.class),
                mock(com.eneik.production.services.ProjectFlowService.class));

        com.eneik.production.models.persistence.ProjectEntity project =
                new com.eneik.production.models.persistence.ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setStatus(com.eneik.production.models.persistence.ProjectStatus.active);

        UUID taskId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.pending_review);
        task.setProject(project);

        JulesSessionEntity strandedSession = new JulesSessionEntity();
        strandedSession.setId(UUID.randomUUID());
        strandedSession.setTaskId(taskId);
        strandedSession.setStatus("stuck");
        strandedSession.setExternalSessionId("sessions/12568286363758467645");
        strandedSession.setPrUrl(null);

        GitHubPullRequestService.GitHubPullRequest mergedPr = new GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/org/repo/pull/71",
                71,
                "Runtime Contract 71278502",
                "jules/sessions-12568286363758467645-schema",
                "jules",
                true,
                "main",
                true,
                java.time.Instant.now());

        when(settings.effectiveBoolean("github_enabled")).thenReturn(true);
        when(projects.findByStatusOrderByCreatedAtDesc(com.eneik.production.models.persistence.ProjectStatus.active))
                .thenReturn(List.of(project));
        when(gitHub.pullRequestSnapshot(project)).thenReturn(new GitHubPullRequestService.PullRequestSnapshot(
                true, "org", "repo", List.of(), List.of(mergedPr), null));
        when(sessions.findAll()).thenReturn(List.of());
        when(tasks.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(task));
        when(sessions.findByTaskId(taskId)).thenReturn(List.of(strandedSession));
        when(conflicts.findFirstByTaskIdAndResolutionStatus(taskId, "pending")).thenReturn(Optional.empty());

        service.reconcileMergedGitHubPullRequests();

        assertEquals(TaskStatus.done, task.getStatus());
        verify(tasks).save(task);
        verify(claims).releaseTerminalClaim(taskId);
    }

    @Test
    void mergedGithubPrUpdatesAnExistingButStaleUnmergedReviewInstantly() {
        // Regression test for the second half of the same 2026-07-31 incident (task ff03b176/PR#3->#17):
        // the winning session had ALREADY reached pr_opened once before (against the original, since-closed
        // PR), so it already owned a PrReviewEntity - just one with merged=false and a prUrl pointing at the
        // dead PR. The old code only ever synthesized a review when NONE existed for the session at all, so
        // this existing-but-stale row was silently never updated, and isDependencySatisfied kept returning
        // false for every downstream dependent forever, even after the task itself was marked done.
        var prReviews = mock(com.eneik.production.repositories.PrReviewRepository.class);
        var sessions = mock(com.eneik.production.repositories.JulesSessionRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        var conflicts = mock(com.eneik.production.repositories.TaskConflictRepository.class);
        var claims = mock(ClaimService.class);
        var settings = mock(com.eneik.production.services.settings.SystemSettingsService.class);
        var gitHub = mock(com.eneik.production.services.github.GitHubPullRequestService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        AutoMergeService service = new AutoMergeService(
                prReviews, sessions, tasks, settings,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(com.eneik.production.services.advice.RoleAdviceLoopService.class),
                conflicts, mock(com.eneik.production.services.jules.JulesDispatchService.class),
                mock(RoleCapabilityLoader.class), mock(com.eneik.production.repositories.WishlistRepository.class),
                mock(MLPredictionServiceClient.class),
                gitHub,
                new com.eneik.production.services.github.GitHubApiBudgetService(),
                mock(com.eneik.production.services.video.VideoAssetService.class),
                mock(com.eneik.production.services.dashboard.ProjectOperationalContextService.class),
                mock(com.eneik.production.services.monitor.SystemProgressTracker.class),
                mock(CodeChangeClassifier.class), mock(com.eneik.production.repositories.FeatureThreadRepository.class),
                claims, projects,
                mock(ClientDeliverableReadinessService.class),
                mock(com.eneik.production.services.GeminiContextService.class),
                mock(com.eneik.production.services.ProjectFlowService.class));

        com.eneik.production.models.persistence.ProjectEntity project =
                new com.eneik.production.models.persistence.ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setStatus(com.eneik.production.models.persistence.ProjectStatus.active);

        UUID taskId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.queued);
        task.setProject(project);

        JulesSessionEntity winningSession = new JulesSessionEntity();
        winningSession.setId(UUID.randomUUID());
        winningSession.setTaskId(taskId);
        winningSession.setStatus("pr_opened");
        winningSession.setExternalSessionId("sessions/3279867026003486022");
        winningSession.setPrUrl("https://github.com/eneikdru/test-fortieth/pull/3");

        PrReviewEntity staleUnmergedReview = reviewWithStatus("pending");
        staleUnmergedReview.setJulesSessionId(winningSession.getId());
        staleUnmergedReview.setPrUrl("https://github.com/eneikdru/test-fortieth/pull/3");
        staleUnmergedReview.setMerged(false);

        GitHubPullRequestService.GitHubPullRequest mergedPr = new GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/eneikdru/test-fortieth/pull/17",
                17,
                "Runtime Contract 20666c21",
                "feat/data-schema-strategy-20666c21-3279867026003486022",
                "jules",
                true,
                "main",
                true,
                java.time.Instant.now());

        when(settings.effectiveBoolean("github_enabled")).thenReturn(true);
        when(projects.findByStatusOrderByCreatedAtDesc(com.eneik.production.models.persistence.ProjectStatus.active))
                .thenReturn(List.of(project));
        when(gitHub.pullRequestSnapshot(project)).thenReturn(new GitHubPullRequestService.PullRequestSnapshot(
                true, "org", "repo", List.of(), List.of(mergedPr), null));
        when(sessions.findAll()).thenReturn(List.of());
        when(tasks.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(task));
        when(sessions.findByTaskId(taskId)).thenReturn(List.of(winningSession));
        when(prReviews.findAll()).thenReturn(List.of(staleUnmergedReview));
        when(conflicts.findFirstByTaskIdAndResolutionStatus(taskId, "pending")).thenReturn(Optional.empty());

        service.reconcileMergedGitHubPullRequests();

        assertEquals(TaskStatus.done, task.getStatus());
        assertEquals(true, staleUnmergedReview.getMerged());
        assertEquals("https://github.com/eneikdru/test-fortieth/pull/17", staleUnmergedReview.getPrUrl());
        verify(prReviews).save(staleUnmergedReview);
    }

    @Test
    void closeoutSelfHealsWhenAccumulationBranchIsConfirmedGone() {
        var featureThreads = mock(com.eneik.production.repositories.FeatureThreadRepository.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var gitHub = mock(com.eneik.production.services.github.GitHubPullRequestService.class);
        AutoMergeService service = new AutoMergeService(
                mock(com.eneik.production.repositories.PrReviewRepository.class),
                mock(com.eneik.production.repositories.JulesSessionRepository.class),
                mock(com.eneik.production.repositories.TaskRepository.class),
                mock(com.eneik.production.services.settings.SystemSettingsService.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(com.eneik.production.services.advice.RoleAdviceLoopService.class),
                mock(com.eneik.production.repositories.TaskConflictRepository.class),
                mock(com.eneik.production.services.jules.JulesDispatchService.class),
                mock(RoleCapabilityLoader.class), mock(com.eneik.production.repositories.WishlistRepository.class),
                mock(MLPredictionServiceClient.class),
                gitHub,
                new com.eneik.production.services.github.GitHubApiBudgetService(),
                mock(com.eneik.production.services.video.VideoAssetService.class),
                mock(com.eneik.production.services.dashboard.ProjectOperationalContextService.class),
                mock(com.eneik.production.services.monitor.SystemProgressTracker.class),
                mock(CodeChangeClassifier.class), featureThreads,
                mock(ClaimService.class), mock(com.eneik.production.repositories.ProjectRepository.class),
                readiness,
                mock(com.eneik.production.services.GeminiContextService.class),
                mock(com.eneik.production.services.ProjectFlowService.class));

        com.eneik.production.models.persistence.ProjectEntity project = new com.eneik.production.models.persistence.ProjectEntity();
        project.setId(UUID.randomUUID());

        com.eneik.production.models.persistence.FeatureThreadEntity thread =
                new com.eneik.production.models.persistence.FeatureThreadEntity();
        thread.setFeatureId(UUID.randomUUID());
        thread.setBranchName("feature/ddd91e1e-thread");

        when(readiness.isFeatureReadyForCloseout(project.getId(), thread.getFeatureId())).thenReturn(true);
        when(gitHub.createPullRequest(eq(project), eq("feature/ddd91e1e-thread"), eq("main"), anyString(), anyString()))
                .thenReturn(Optional.empty());
        // The last task's own PR already merged straight to main and deleted this branch - nothing left to
        // close out via a separate closeout PR.
        when(gitHub.branchExists(project, "feature/ddd91e1e-thread")).thenReturn(false);

        service.progressCloseout(project, thread);

        assertTrue(thread.getMergedToMainAt() != null, "feature should be treated as closed out once its branch is confirmed gone");
        verify(featureThreads).save(thread);
        // The bug this fixes: closeOutReadyFeatureThreads only ever queries threads with mergedToMainAt IS
        // NULL (findByProjectIdAndMergedToMainAtIsNullAndAbandonedAtIsNull) - once this is set, the thread
        // drops out of that query entirely, so progressCloseout (and therefore createPullRequest) never
        // gets invoked for it again on any future cycle. This one createPullRequest call is the single,
        // final attempt, not evidence of an ongoing retry loop.
        verify(gitHub, times(1)).createPullRequest(any(), any(), any(), anyString(), anyString());
    }

    @Test
    void closeoutKeepsRetryingWhenBranchStatusIsInconclusive() {
        var featureThreads = mock(com.eneik.production.repositories.FeatureThreadRepository.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var gitHub = mock(com.eneik.production.services.github.GitHubPullRequestService.class);
        AutoMergeService service = new AutoMergeService(
                mock(com.eneik.production.repositories.PrReviewRepository.class),
                mock(com.eneik.production.repositories.JulesSessionRepository.class),
                mock(com.eneik.production.repositories.TaskRepository.class),
                mock(com.eneik.production.services.settings.SystemSettingsService.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(com.eneik.production.services.advice.RoleAdviceLoopService.class),
                mock(com.eneik.production.repositories.TaskConflictRepository.class),
                mock(com.eneik.production.services.jules.JulesDispatchService.class),
                mock(RoleCapabilityLoader.class), mock(com.eneik.production.repositories.WishlistRepository.class),
                mock(MLPredictionServiceClient.class),
                gitHub,
                new com.eneik.production.services.github.GitHubApiBudgetService(),
                mock(com.eneik.production.services.video.VideoAssetService.class),
                mock(com.eneik.production.services.dashboard.ProjectOperationalContextService.class),
                mock(com.eneik.production.services.monitor.SystemProgressTracker.class),
                mock(CodeChangeClassifier.class), featureThreads,
                mock(ClaimService.class), mock(com.eneik.production.repositories.ProjectRepository.class),
                readiness,
                mock(com.eneik.production.services.GeminiContextService.class),
                mock(com.eneik.production.services.ProjectFlowService.class));

        com.eneik.production.models.persistence.ProjectEntity project = new com.eneik.production.models.persistence.ProjectEntity();
        project.setId(UUID.randomUUID());

        com.eneik.production.models.persistence.FeatureThreadEntity thread =
                new com.eneik.production.models.persistence.FeatureThreadEntity();
        thread.setFeatureId(UUID.randomUUID());
        thread.setBranchName("feature/still-there-thread");

        when(readiness.isFeatureReadyForCloseout(project.getId(), thread.getFeatureId())).thenReturn(true);
        when(gitHub.createPullRequest(eq(project), eq("feature/still-there-thread"), eq("main"), anyString(), anyString()))
                .thenReturn(Optional.empty());
        // branchExists defaults to true (mock, unstubbed here) for the inconclusive/still-there case -
        // matches GitHubPullRequestService.branchExists' own "assume it exists" default.
        when(gitHub.branchExists(project, "feature/still-there-thread")).thenReturn(true);

        service.progressCloseout(project, thread);

        assertTrue(thread.getMergedToMainAt() == null, "must not be marked closed out while the branch might still be there");
        verify(featureThreads, never()).save(any());
    }

    private PrReviewEntity reviewWithStatus(String status) {
        PrReviewEntity review = new PrReviewEntity();
        review.setCiStatus(status);
        return review;
    }
}
