package com.eneik.production.services;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.services.github.GitHubPullRequestService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
                mock(com.eneik.production.services.ProjectFlowService.class),
                mock(com.eneik.production.repositories.EvidenceNodeRepository.class),
                mock(com.eneik.production.repositories.OperationalRealityFindingRepository.class));

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
                mock(com.eneik.production.services.ProjectFlowService.class),
                mock(com.eneik.production.repositories.EvidenceNodeRepository.class),
                mock(com.eneik.production.repositories.OperationalRealityFindingRepository.class));

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
                mock(com.eneik.production.services.ProjectFlowService.class),
                mock(com.eneik.production.repositories.EvidenceNodeRepository.class),
                mock(com.eneik.production.repositories.OperationalRealityFindingRepository.class));

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

    @Test
    void terminalGithubStateNeverReTerminalizesAnAlreadySupersededReview() {
        // Live incident, 2026-08-08 (test-forty-third): a review repairTaskForConfirmedMerge already marked
        // ciStatus="superseded" (the LOSING review in a real supersession - genuine evidence already
        // correctly attributed to the winning review) kept getting picked back up here every single cycle
        // (163 times for one PR alone since the last deploy) and had its correct "superseded" marker
        // overwritten back to "closed_unmerged" - two methods fighting over the same row forever, with real
        // GitHub calls wasted on every pass. This review must never even reach the GitHub-call branch.
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
                mock(com.eneik.production.services.ProjectFlowService.class),
                mock(com.eneik.production.repositories.EvidenceNodeRepository.class),
                mock(com.eneik.production.repositories.OperationalRealityFindingRepository.class));

        PrReviewEntity supersededReview = reviewWithStatus("superseded");
        supersededReview.setPrUrl("https://github.com/org/repo/pull/174");
        supersededReview.setMerged(false);

        // Second, distinct symptom of the SAME root cause, also found live (PR #152): a review already
        // settled as "closed_unmerged" kept being re-fetched from GitHub and re-saved every cycle with no
        // new information - wasteful, not corrupting, but the same missing-filter class of bug.
        PrReviewEntity alreadyClosedReview = reviewWithStatus("closed_unmerged");
        alreadyClosedReview.setPrUrl("https://github.com/org/repo/pull/152");
        alreadyClosedReview.setMerged(false);

        when(settings.effectiveBoolean("github_enabled")).thenReturn(true);
        when(prReviews.findByMergedFalseOrMergedIsNull()).thenReturn(List.of(supersededReview, alreadyClosedReview));

        assertEquals(0, service.reconcileTerminalGithubStateForReviews());
        assertEquals("superseded", supersededReview.getCiStatus());
        assertEquals("closed_unmerged", alreadyClosedReview.getCiStatus());
        verifyNoInteractions(gitHub);
        verify(prReviews, never()).save(supersededReview);
        verify(prReviews, never()).save(alreadyClosedReview);
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
                mock(com.eneik.production.services.ProjectFlowService.class),
                mock(com.eneik.production.repositories.EvidenceNodeRepository.class),
                mock(com.eneik.production.repositories.OperationalRealityFindingRepository.class));

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
                mock(com.eneik.production.services.ProjectFlowService.class),
                mock(com.eneik.production.repositories.EvidenceNodeRepository.class),
                mock(com.eneik.production.repositories.OperationalRealityFindingRepository.class));

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
        when(prReviews.findByMergedTrueAndJulesSessionIdIsNotNull()).thenReturn(List.of(review));
        when(prReviews.findByJulesSessionIdIsNotNull()).thenReturn(List.of(review));
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
                mock(com.eneik.production.services.ProjectFlowService.class),
                mock(com.eneik.production.repositories.EvidenceNodeRepository.class),
                mock(com.eneik.production.repositories.OperationalRealityFindingRepository.class));

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
                mock(com.eneik.production.services.ProjectFlowService.class),
                mock(com.eneik.production.repositories.EvidenceNodeRepository.class),
                mock(com.eneik.production.repositories.OperationalRealityFindingRepository.class));

        UUID taskId = UUID.randomUUID();
        JulesSessionEntity winner = new JulesSessionEntity();
        winner.setId(UUID.randomUUID());
        winner.setTaskId(taskId);
        winner.setStatus("cancelled");
        JulesSessionEntity duplicate = new JulesSessionEntity();
        duplicate.setId(UUID.randomUUID());
        duplicate.setTaskId(taskId);
        duplicate.setStatus("running");

        com.eneik.production.models.persistence.ProjectEntity project =
                new com.eneik.production.models.persistence.ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setStatus(com.eneik.production.models.persistence.ProjectStatus.active);

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.pending_review);
        task.setProject(project);

        PrReviewEntity merged = reviewWithStatus("success");
        merged.setJulesSessionId(winner.getId());
        merged.setPrUrl("https://github.com/org/repo/pull/merged");
        merged.setMerged(true);
        PrReviewEntity oldConflict = reviewWithStatus("conflict");
        oldConflict.setJulesSessionId(duplicate.getId());
        oldConflict.setPrUrl("https://github.com/org/repo/pull/old");
        oldConflict.setMerged(false);

        when(prReviews.findByMergedTrueAndJulesSessionIdIsNotNull()).thenReturn(List.of(merged, oldConflict));
        when(prReviews.findByJulesSessionIdIsNotNull()).thenReturn(List.of(merged, oldConflict));
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
                mock(com.eneik.production.services.ProjectFlowService.class),
                mock(com.eneik.production.repositories.EvidenceNodeRepository.class),
                mock(com.eneik.production.repositories.OperationalRealityFindingRepository.class));

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
        when(sessions.findByPrUrlIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        when(tasks.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(task));
        when(sessions.findByTaskId(taskId)).thenReturn(List.of(strandedSession));
        when(conflicts.findFirstByTaskIdAndResolutionStatus(taskId, "pending")).thenReturn(Optional.empty());

        service.reconcileMergedGitHubPullRequests();

        assertEquals(TaskStatus.done, task.getStatus());
        verify(tasks).save(task);
        verify(claims).releaseTerminalClaim(taskId);

        // This branch creates a brand-new PrReviewEntity (winningReview was null) - the other prNumber
        // regression test above covers the update-existing-review branch of the same ternary.
        ArgumentCaptor<PrReviewEntity> reviewCaptor = ArgumentCaptor.forClass(PrReviewEntity.class);
        verify(prReviews).save(reviewCaptor.capture());
        assertEquals(71, reviewCaptor.getValue().getPrNumber());
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
                mock(com.eneik.production.services.ProjectFlowService.class),
                mock(com.eneik.production.repositories.EvidenceNodeRepository.class),
                mock(com.eneik.production.repositories.OperationalRealityFindingRepository.class));

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
        when(sessions.findByPrUrlIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        when(tasks.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(task));
        when(sessions.findByTaskId(taskId)).thenReturn(List.of(winningSession));
        when(prReviews.findByMergedTrueAndJulesSessionIdIsNotNull()).thenReturn(List.of(staleUnmergedReview));
        when(prReviews.findByJulesSessionIdIsNotNull()).thenReturn(List.of(staleUnmergedReview));
        when(conflicts.findFirstByTaskIdAndResolutionStatus(taskId, "pending")).thenReturn(Optional.empty());

        service.reconcileMergedGitHubPullRequests();

        assertEquals(TaskStatus.done, task.getStatus());
        assertEquals(true, staleUnmergedReview.getMerged());
        assertEquals("https://github.com/eneikdru/test-fortieth/pull/17", staleUnmergedReview.getPrUrl());
        assertEquals(17, staleUnmergedReview.getPrNumber(),
                "repairTaskForConfirmedMerge must also capture the real PR number, not just the URL - "
                        + "needed for code-integrity finding attribution (SixSigmaAuditService.computePrConflictCounts-style join)");
        verify(prReviews).save(staleUnmergedReview);
    }

    /**
     * Direct regression test for the 2026-08-08 incident (engineering invariant #14, task 9d572d25 on
     * test-forty-third): AutoMergeService.progressCloseout opens its own PR from the SAME continuation
     * branch a task's real implementer session used, so a plain branch-token match cannot tell it apart
     * from the task's own PR. A task whose real work already correctly shows hasCode=true must not have
     * that evidence silently overwritten by an unrelated, empty Closeout PR that happens to share the
     * branch token.
     */
    @Test
    void closeoutPrSharingABranchTokenNeverOverwritesAlreadyCorrectMergeEvidence() {
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
                mock(com.eneik.production.services.ProjectFlowService.class),
                mock(com.eneik.production.repositories.EvidenceNodeRepository.class),
                mock(com.eneik.production.repositories.OperationalRealityFindingRepository.class));

        com.eneik.production.models.persistence.ProjectEntity project =
                new com.eneik.production.models.persistence.ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setStatus(com.eneik.production.models.persistence.ProjectStatus.active);

        UUID taskId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.done);
        task.setProject(project);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTaskId(taskId);
        session.setStatus("closed_terminal_task");
        session.setExternalSessionId("sessions/9775454418732355504");
        session.setPrUrl("https://github.com/eneikdru/test-forty-third/pull/34");

        // Already correct - the task's real implementation PR, hasCode=true, matching the session's own prUrl.
        PrReviewEntity realReview = reviewWithStatus("success");
        realReview.setJulesSessionId(session.getId());
        realReview.setPrUrl("https://github.com/eneikdru/test-forty-third/pull/34");
        realReview.setPrNumber(34);
        realReview.setMerged(true);
        realReview.setHasCode(true);
        realReview.setBaseRef("main");

        // The unrelated Closeout PR - empty, real work already merged directly - sharing the SAME branch
        // token because progressCloseout built it from the same continuation branch.
        GitHubPullRequestService.GitHubPullRequest closeoutPr = new GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/eneikdru/test-forty-third/pull/36",
                36,
                "Closeout: integrate feature " + UUID.randomUUID() + " into main",
                "feat/notification-triggers-9775454418732355504",
                "jules",
                true,
                "main",
                true,
                java.time.Instant.now());

        when(settings.effectiveBoolean("github_enabled")).thenReturn(true);
        when(projects.findByStatusOrderByCreatedAtDesc(com.eneik.production.models.persistence.ProjectStatus.active))
                .thenReturn(List.of(project));
        when(gitHub.pullRequestSnapshot(project)).thenReturn(new GitHubPullRequestService.PullRequestSnapshot(
                true, "org", "repo", List.of(), List.of(closeoutPr), null));
        // Session's own prUrl already points at the REAL PR (#34), not the closeout PR - the exact-match
        // pass finds nothing new to do since #34 never shows up in mergedPrUrls (only the closeout PR is
        // in this GitHub snapshot). The branch-token fallback pass is what must refuse to touch it.
        when(sessions.findByPrUrlIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(session));
        when(tasks.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(task));
        when(sessions.findByTaskId(taskId)).thenReturn(List.of(session));
        when(prReviews.findByMergedTrueAndJulesSessionIdIsNotNull()).thenReturn(List.of(realReview));
        when(prReviews.findByJulesSessionIdIsNotNull()).thenReturn(List.of(realReview));

        service.reconcileMergedGitHubPullRequests();

        assertEquals(true, realReview.getHasCode(),
                "a Closeout PR sharing this task's branch token must never downgrade already-correct hasCode=true evidence");
        assertEquals("https://github.com/eneikdru/test-forty-third/pull/34", realReview.getPrUrl(),
                "the review must keep pointing at the task's real implementation PR, not the unrelated Closeout PR");
        verify(prReviews, never()).save(any(PrReviewEntity.class));
    }

    // --- reconcileOpenGitHubPullRequests (2026-08-06 incident) -----------------------------------------

    @Test
    void openUnmergedPrWithNoReviewGetsARealPrReviewEntityAndCorrectedStatus() {
        // Direct regression test for the 2026-08-06 incident: a session's prUrl was set (something detected
        // the real PR) but its status never advanced past "running" and no PrReviewEntity was ever created -
        // invisible to the whole review/merge pipeline even though the real GitHub PR sat open, mergeable,
        // CI green for 90+ minutes.
        var prReviews = mock(com.eneik.production.repositories.PrReviewRepository.class);
        var sessions = mock(com.eneik.production.repositories.JulesSessionRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        var settings = mock(com.eneik.production.services.settings.SystemSettingsService.class);
        var gitHub = mock(com.eneik.production.services.github.GitHubPullRequestService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var evidenceNodes = mock(com.eneik.production.repositories.EvidenceNodeRepository.class);
        var realityFindings = mock(com.eneik.production.repositories.OperationalRealityFindingRepository.class);
        var julesDispatchService = mock(com.eneik.production.services.jules.JulesDispatchService.class);
        AutoMergeService service = new AutoMergeService(
                prReviews, sessions, tasks, settings,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(com.eneik.production.services.advice.RoleAdviceLoopService.class),
                mock(com.eneik.production.repositories.TaskConflictRepository.class),
                julesDispatchService,
                mock(RoleCapabilityLoader.class), mock(com.eneik.production.repositories.WishlistRepository.class),
                mock(MLPredictionServiceClient.class),
                gitHub,
                new com.eneik.production.services.github.GitHubApiBudgetService(),
                mock(com.eneik.production.services.video.VideoAssetService.class),
                mock(com.eneik.production.services.dashboard.ProjectOperationalContextService.class),
                mock(com.eneik.production.services.monitor.SystemProgressTracker.class),
                mock(CodeChangeClassifier.class), mock(com.eneik.production.repositories.FeatureThreadRepository.class),
                mock(ClaimService.class), projects,
                mock(ClientDeliverableReadinessService.class),
                mock(com.eneik.production.services.GeminiContextService.class),
                mock(com.eneik.production.services.ProjectFlowService.class),
                evidenceNodes, realityFindings);

        com.eneik.production.models.persistence.ProjectEntity project =
                new com.eneik.production.models.persistence.ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setStatus(com.eneik.production.models.persistence.ProjectStatus.active);

        UUID taskId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.claimed);
        task.setProject(project);

        UUID sessionId = UUID.randomUUID();
        JulesSessionEntity stuckSession = new JulesSessionEntity();
        stuckSession.setId(sessionId);
        stuckSession.setTaskId(taskId);
        stuckSession.setStatus("running");
        stuckSession.setPrUrl("https://github.com/eneikdru/test-forty-second/pull/10");

        GitHubPullRequestService.GitHubPullRequest openPr = new GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/eneikdru/test-forty-second/pull/10", 10,
                "Add TAG-09 Philosophical Product Falsification Audit",
                "jules/sessions-x", "jules", false, "main", false, java.time.Instant.now());

        when(settings.effectiveBoolean("github_enabled")).thenReturn(true);
        when(projects.findByStatusOrderByCreatedAtDesc(com.eneik.production.models.persistence.ProjectStatus.active))
                .thenReturn(List.of(project));
        when(gitHub.pullRequestSnapshot(project)).thenReturn(new GitHubPullRequestService.PullRequestSnapshot(
                true, "eneikdru", "test-forty-second", List.of(openPr), List.of(), null));
        when(gitHub.mergeableState(project, 10)).thenReturn(Optional.of(
                new GitHubPullRequestService.MergeableState(true, "CLEAN")));
        when(gitHub.pullRequestChecks(project, 10)).thenReturn(
                new GitHubPullRequestService.PullRequestChecks(true, true, "success", "All checks passed"));
        when(sessions.findByPrUrlIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(stuckSession));
        when(tasks.findById(taskId)).thenReturn(Optional.of(task));
        when(prReviews.existsByJulesSessionId(sessionId)).thenReturn(false);
        when(realityFindings.save(any())).thenAnswer(inv -> {
            var f = inv.getArgument(0, com.eneik.production.models.persistence.OperationalRealityFindingEntity.class);
            f.setId(UUID.randomUUID());
            return f;
        });

        service.reconcileOpenGitHubPullRequests();

        // Real completion workflow must be replayed - a bare status write is invisible to
        // JulesDispatchService.pollStatus's own edge-detection (see syncOpenPullRequestsFromGitHub's
        // identical, already-fixed lesson for this exact bug class).
        verify(julesDispatchService).handlePrOpenedWorkflow(stuckSession);

        ArgumentCaptor<PrReviewEntity> reviewCaptor = ArgumentCaptor.forClass(PrReviewEntity.class);
        verify(prReviews).save(reviewCaptor.capture());
        assertEquals(10, reviewCaptor.getValue().getPrNumber());
        assertFalse(reviewCaptor.getValue().getMerged());
        // Real CI-checks vocabulary ("success"/"pending"/...), not GitHub's mergeStateStatus axis
        // ("clean"/"dirty"/...) - the latter silently excluded every reconciled review from
        // isReviewPollCandidate forever (confirmed live, 2026-08-06).
        assertEquals("success", reviewCaptor.getValue().getCiStatus());

        assertEquals("pr_opened", stuckSession.getStatus());
        verify(sessions).save(stuckSession);

        ArgumentCaptor<com.eneik.production.models.persistence.EvidenceNodeEntity> nodeCaptor =
                ArgumentCaptor.forClass(com.eneik.production.models.persistence.EvidenceNodeEntity.class);
        verify(evidenceNodes).save(nodeCaptor.capture());
        assertEquals(com.eneik.production.models.persistence.EvidenceNodeEntity.Polarity.NEGATIVE_FINDING,
                nodeCaptor.getValue().getPolarity());
        ArgumentCaptor<com.eneik.production.models.persistence.OperationalRealityFindingEntity> findingCaptor =
                ArgumentCaptor.forClass(com.eneik.production.models.persistence.OperationalRealityFindingEntity.class);
        verify(realityFindings).save(findingCaptor.capture());
        assertEquals("running", findingCaptor.getValue().getExpectedStatus(),
                "must record the status BEFORE this reconciler corrected it, not a vacuous post-mutation comparison");
        assertEquals("open, mergeable", findingCaptor.getValue().getActualGithubState(),
                "must not duplicate the PR URL (already its own column) - that overflowed VARCHAR(64) live, 2026-08-06");
    }

    @Test
    void openPrWithExistingReviewAndCorrectStatusIsLeftAlone() {
        // The reconciler's trigger is objective (missing review row / stale status), not "every open PR" -
        // an already-correctly-reconciled session must not be touched every single cycle.
        var prReviews = mock(com.eneik.production.repositories.PrReviewRepository.class);
        var sessions = mock(com.eneik.production.repositories.JulesSessionRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        var settings = mock(com.eneik.production.services.settings.SystemSettingsService.class);
        var gitHub = mock(com.eneik.production.services.github.GitHubPullRequestService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var evidenceNodes = mock(com.eneik.production.repositories.EvidenceNodeRepository.class);
        var realityFindings = mock(com.eneik.production.repositories.OperationalRealityFindingRepository.class);
        AutoMergeService service = new AutoMergeService(
                prReviews, sessions, tasks, settings,
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
                mock(ClaimService.class), projects,
                mock(ClientDeliverableReadinessService.class),
                mock(com.eneik.production.services.GeminiContextService.class),
                mock(com.eneik.production.services.ProjectFlowService.class),
                evidenceNodes, realityFindings);

        com.eneik.production.models.persistence.ProjectEntity project =
                new com.eneik.production.models.persistence.ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setStatus(com.eneik.production.models.persistence.ProjectStatus.active);

        UUID taskId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);

        UUID sessionId = UUID.randomUUID();
        JulesSessionEntity healthySession = new JulesSessionEntity();
        healthySession.setId(sessionId);
        healthySession.setTaskId(taskId);
        healthySession.setStatus("pr_opened");
        healthySession.setPrUrl("https://github.com/org/repo/pull/5");

        GitHubPullRequestService.GitHubPullRequest openPr = new GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/org/repo/pull/5", 5, "title", "jules/x", "jules", false, "main", false, java.time.Instant.now());

        when(settings.effectiveBoolean("github_enabled")).thenReturn(true);
        when(projects.findByStatusOrderByCreatedAtDesc(com.eneik.production.models.persistence.ProjectStatus.active))
                .thenReturn(List.of(project));
        when(gitHub.pullRequestSnapshot(project)).thenReturn(new GitHubPullRequestService.PullRequestSnapshot(
                true, "org", "repo", List.of(openPr), List.of(), null));
        when(sessions.findByPrUrlIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(healthySession));
        when(tasks.findById(taskId)).thenReturn(Optional.of(task));
        when(prReviews.existsByJulesSessionId(sessionId)).thenReturn(true);

        service.reconcileOpenGitHubPullRequests();

        verify(prReviews, never()).save(any());
        verify(sessions, never()).save(any());
        verify(evidenceNodes, never()).save(any());
        verify(realityFindings, never()).save(any());
    }

    @Test
    void openPrReconciliationNeverRepairsASessionWhoseTaskIsAlreadyTerminal() {
        // Live incident, 2026-08-09 (test-forty-third, task c48e1f7e): this reconciler kept setting a
        // session back to "pr_opened" every cycle because a stale open-PR snapshot still listed it, while
        // JulesDispatchService.closeSessionsForTerminalTasks kept closing that SAME session right back down
        // because its task was already "failed" - two independently-maintained beliefs fighting over one
        // row, every ~1 minute for 13+ minutes straight, real GitHub calls wasted each time. A task whose
        // fate is already decided (failed/done/blocked/spike_completed) must never be "repaired" toward
        // pr_opened - JulesDispatchService.isTerminalTask is the one canonical definition (invariant #14).
        var prReviews = mock(com.eneik.production.repositories.PrReviewRepository.class);
        var sessions = mock(com.eneik.production.repositories.JulesSessionRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        var settings = mock(com.eneik.production.services.settings.SystemSettingsService.class);
        var gitHub = mock(com.eneik.production.services.github.GitHubPullRequestService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var evidenceNodes = mock(com.eneik.production.repositories.EvidenceNodeRepository.class);
        var realityFindings = mock(com.eneik.production.repositories.OperationalRealityFindingRepository.class);
        var julesDispatchService = mock(com.eneik.production.services.jules.JulesDispatchService.class);
        AutoMergeService service = new AutoMergeService(
                prReviews, sessions, tasks, settings,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(com.eneik.production.services.advice.RoleAdviceLoopService.class),
                mock(com.eneik.production.repositories.TaskConflictRepository.class),
                julesDispatchService,
                mock(RoleCapabilityLoader.class), mock(com.eneik.production.repositories.WishlistRepository.class),
                mock(MLPredictionServiceClient.class),
                gitHub,
                new com.eneik.production.services.github.GitHubApiBudgetService(),
                mock(com.eneik.production.services.video.VideoAssetService.class),
                mock(com.eneik.production.services.dashboard.ProjectOperationalContextService.class),
                mock(com.eneik.production.services.monitor.SystemProgressTracker.class),
                mock(CodeChangeClassifier.class), mock(com.eneik.production.repositories.FeatureThreadRepository.class),
                mock(ClaimService.class), projects,
                mock(ClientDeliverableReadinessService.class),
                mock(com.eneik.production.services.GeminiContextService.class),
                mock(com.eneik.production.services.ProjectFlowService.class),
                evidenceNodes, realityFindings);

        com.eneik.production.models.persistence.ProjectEntity project =
                new com.eneik.production.models.persistence.ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setStatus(com.eneik.production.models.persistence.ProjectStatus.active);

        UUID taskId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.failed);
        task.setProject(project);

        UUID sessionId = UUID.randomUUID();
        JulesSessionEntity closedSession = new JulesSessionEntity();
        closedSession.setId(sessionId);
        closedSession.setTaskId(taskId);
        closedSession.setStatus("closed_terminal_task");
        closedSession.setPrUrl("https://github.com/eneikdru/test-forty-third/pull/219");

        GitHubPullRequestService.GitHubPullRequest openPr = new GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/eneikdru/test-forty-third/pull/219", 219,
                "some title", "jules/sessions-y", "jules", false, "main", false, java.time.Instant.now());

        when(settings.effectiveBoolean("github_enabled")).thenReturn(true);
        when(projects.findByStatusOrderByCreatedAtDesc(com.eneik.production.models.persistence.ProjectStatus.active))
                .thenReturn(List.of(project));
        when(gitHub.pullRequestSnapshot(project)).thenReturn(new GitHubPullRequestService.PullRequestSnapshot(
                true, "eneikdru", "test-forty-third", List.of(openPr), List.of(), null));
        when(sessions.findByPrUrlIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(closedSession));
        when(tasks.findById(taskId)).thenReturn(Optional.of(task));

        service.reconcileOpenGitHubPullRequests();

        verify(gitHub, never()).mergeableState(any(), anyInt());
        verify(gitHub, never()).pullRequestChecks(any(), anyInt());
        verify(julesDispatchService, never()).handlePrOpenedWorkflow(any());
        verify(prReviews, never()).save(any());
        verify(sessions, never()).save(any());
        assertEquals("closed_terminal_task", closedSession.getStatus());
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
                mock(com.eneik.production.services.ProjectFlowService.class),
                mock(com.eneik.production.repositories.EvidenceNodeRepository.class),
                mock(com.eneik.production.repositories.OperationalRealityFindingRepository.class));

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
                mock(com.eneik.production.services.ProjectFlowService.class),
                mock(com.eneik.production.repositories.EvidenceNodeRepository.class),
                mock(com.eneik.production.repositories.OperationalRealityFindingRepository.class));

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

    private static AutoMergeService serviceWith(
            com.eneik.production.repositories.TaskRepository tasks,
            com.eneik.production.repositories.WishlistRepository wishlists) {
        return serviceWith(tasks, wishlists, mock(ClaimService.class));
    }

    private static AutoMergeService serviceWith(
            com.eneik.production.repositories.TaskRepository tasks,
            com.eneik.production.repositories.WishlistRepository wishlists,
            ClaimService claims) {
        return new AutoMergeService(
                mock(com.eneik.production.repositories.PrReviewRepository.class),
                mock(com.eneik.production.repositories.JulesSessionRepository.class), tasks,
                mock(com.eneik.production.services.settings.SystemSettingsService.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(com.eneik.production.services.advice.RoleAdviceLoopService.class),
                mock(com.eneik.production.repositories.TaskConflictRepository.class),
                mock(com.eneik.production.services.jules.JulesDispatchService.class),
                mock(RoleCapabilityLoader.class), wishlists,
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
                mock(com.eneik.production.services.ProjectFlowService.class),
                mock(com.eneik.production.repositories.EvidenceNodeRepository.class),
                mock(com.eneik.production.repositories.OperationalRealityFindingRepository.class));
    }

    // 2026-08-19: the poka-yoke used to decline to close the task and stop there, which left task 779705b2
    // at pending_review permanently while the reconciler re-entered the same refusal 262 times in 65
    // minutes. Declining to certify a delivery and leaving the object where it stands are two different
    // obligations - this covers the second one.
    @Test
    void anUncertifiedMergeRetiresTheAttemptAndReopensTheRequirement() {
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        var wishlists = mock(com.eneik.production.repositories.WishlistRepository.class);
        AutoMergeService service = serviceWith(tasks, wishlists);

        UUID wishlistId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setStatus(TaskStatus.pending_review);
        task.setSourceWishlistId(wishlistId);

        com.eneik.production.models.persistence.WishlistEntity wishlist =
                new com.eneik.production.models.persistence.WishlistEntity();
        wishlist.setId(wishlistId);
        wishlist.setStatus(com.eneik.production.models.persistence.WishlistStatus.converted_to_task);

        when(tasks.findBySourceWishlistIdIn(List.of(wishlistId))).thenReturn(List.of(task));
        when(tasks.findById(task.getId())).thenAnswer(inv -> {
            task.setStatus(TaskStatus.failed); // ClaimService.closeTaskAsFailed is what really writes this
            return Optional.of(task);
        });
        when(wishlists.findById(wishlistId)).thenReturn(Optional.of(wishlist));

        service.routeUncertifiedMerge(task, "https://github.com/org/repo/pull/107");

        assertEquals(TaskStatus.failed, task.getStatus());
        assertEquals(com.eneik.production.models.persistence.WishlistStatus.pending, wishlist.getStatus());
        verify(wishlists).save(wishlist);
    }

    /**
     * Plan §4.40, measured live 2026-08-29 18:08. Client brief 8aff0d75 (the Moodle integration) produced
     * slice 2c3442ef "Self-Service Account Recovery", which produced task 048e4e50, whose PR merged with no
     * code. The task was retired, the brief sat `dismissed`, no sibling task existed, and that slice of the
     * client's requirement simply ended. `dismissed` means two different things - a brief that produced
     * nothing, and a brief whose work has now failed without delivering - and refusing on it buried the
     * requirement this routing exists to save.
     */
    @Test
    void aDismissedRequirementWhoseWorkMergedEmptyIsReopened() {
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        var wishlists = mock(com.eneik.production.repositories.WishlistRepository.class);
        AutoMergeService service = serviceWith(tasks, wishlists);

        UUID wishlistId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setStatus(TaskStatus.pending_review);
        task.setSourceWishlistId(wishlistId);

        com.eneik.production.models.persistence.WishlistEntity dismissed =
                new com.eneik.production.models.persistence.WishlistEntity();
        dismissed.setId(wishlistId);
        dismissed.setStatus(com.eneik.production.models.persistence.WishlistStatus.dismissed);

        when(tasks.findBySourceWishlistIdIn(List.of(wishlistId))).thenReturn(List.of(task));
        when(tasks.findById(task.getId())).thenAnswer(inv -> {
            task.setStatus(TaskStatus.failed);
            return Optional.of(task);
        });
        when(wishlists.findById(wishlistId)).thenReturn(Optional.of(dismissed));

        service.routeUncertifiedMerge(task, "https://github.com/org/repo/pull/442");

        assertEquals(com.eneik.production.models.persistence.WishlistStatus.pending, dismissed.getStatus());
        verify(wishlists).save(dismissed);
    }

    /**
     * The other half, and it is not optional: a brief already open is left exactly as it is. Without this
     * the routing would write to an open requirement on every cycle and stop being idempotent.
     */
    @Test
    void aRequirementAlreadyOpenIsLeftAlone() {
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        var wishlists = mock(com.eneik.production.repositories.WishlistRepository.class);
        AutoMergeService service = serviceWith(tasks, wishlists);

        UUID wishlistId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setStatus(TaskStatus.pending_review);
        task.setSourceWishlistId(wishlistId);

        com.eneik.production.models.persistence.WishlistEntity open =
                new com.eneik.production.models.persistence.WishlistEntity();
        open.setId(wishlistId);
        open.setStatus(com.eneik.production.models.persistence.WishlistStatus.pending);

        when(tasks.findBySourceWishlistIdIn(List.of(wishlistId))).thenReturn(List.of(task));
        when(tasks.findById(task.getId())).thenAnswer(inv -> {
            task.setStatus(TaskStatus.failed);
            return Optional.of(task);
        });
        when(wishlists.findById(wishlistId)).thenReturn(Optional.of(open));

        service.routeUncertifiedMerge(task, "https://github.com/org/repo/pull/443");

        verify(wishlists, never()).save(any());
    }

    // The bound is a well-founded measure: failed siblings from the same wishlist only ever increase, so
    // the requirement cannot be re-minted forever by a role that keeps merging empty PRs.
    @Test
    void aRequirementThatAlreadySpentItsAttemptsIsNotReopenedAgain() {
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        var wishlists = mock(com.eneik.production.repositories.WishlistRepository.class);
        AutoMergeService service = serviceWith(tasks, wishlists);

        UUID wishlistId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setStatus(TaskStatus.pending_review);
        task.setSourceWishlistId(wishlistId);

        TaskEntity firstFailure = new TaskEntity();
        firstFailure.setId(UUID.randomUUID());
        firstFailure.setStatus(TaskStatus.failed);
        TaskEntity secondFailure = new TaskEntity();
        secondFailure.setId(UUID.randomUUID());
        secondFailure.setStatus(TaskStatus.failed);

        when(tasks.findBySourceWishlistIdIn(List.of(wishlistId)))
                .thenReturn(List.of(task, firstFailure, secondFailure));
        when(tasks.findById(task.getId())).thenAnswer(inv -> {
            task.setStatus(TaskStatus.failed);
            return Optional.of(task);
        });

        service.routeUncertifiedMerge(task, "https://github.com/org/repo/pull/107");

        assertEquals(TaskStatus.failed, task.getStatus());
        verify(wishlists, never()).save(any());
    }

    // Idempotency (Charter invariant 4): the reconciler re-enters this path on every cycle over the same
    // merged PR. A task it already retired must produce no second write and no second log line.
    @Test
    void anAlreadyRetiredAttemptIsNotRoutedTwice() {
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        var wishlists = mock(com.eneik.production.repositories.WishlistRepository.class);
        AutoMergeService service = serviceWith(tasks, wishlists);

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setStatus(TaskStatus.failed);
        task.setSourceWishlistId(UUID.randomUUID());

        service.routeUncertifiedMerge(task, "https://github.com/org/repo/pull/107");

        verify(wishlists, never()).save(any());
    }

    // A concurrent terminal write wins: the guarded status write reports zero rows and nothing else happens.
    @Test
    void aConcurrentlyTerminalTaskDoesNotReopenItsRequirement() {
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        var wishlists = mock(com.eneik.production.repositories.WishlistRepository.class);
        AutoMergeService service = serviceWith(tasks, wishlists);

        UUID wishlistId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setStatus(TaskStatus.pending_review);
        task.setSourceWishlistId(wishlistId);

        when(tasks.findBySourceWishlistIdIn(List.of(wishlistId))).thenReturn(List.of(task));
        when(tasks.findById(task.getId())).thenReturn(Optional.of(task)); // stays pending_review

        service.routeUncertifiedMerge(task, "https://github.com/org/repo/pull/107");

        assertEquals(TaskStatus.pending_review, task.getStatus());
        verify(wishlists, never()).save(any());
    }

}
