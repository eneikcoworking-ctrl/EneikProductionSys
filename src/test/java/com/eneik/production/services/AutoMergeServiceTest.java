package com.eneik.production.services;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
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

        assertFalse(AutoMergeService.isReviewPollCandidate(reviewWithStatus("failure")));
        // Genuinely terminal - handleMergeConflict's own 3-attempt cap is exhausted, so unlike plain
        // "conflict" this must NOT be retried (it would re-escalate a brand new TaskConflictEntity forever).
        assertFalse(AutoMergeService.isReviewPollCandidate(reviewWithStatus("escalated")));
        assertFalse(AutoMergeService.isReviewPollCandidate(reviewWithStatus("owner_mismatch")));
        assertFalse(AutoMergeService.isReviewPollCandidate(reviewWithStatus("unowned")));
        assertFalse(AutoMergeService.isReviewPollCandidate(reviewWithStatus("invalid_pr")));
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
                mock(com.eneik.production.services.video.VideoAssetService.class),
                mock(com.eneik.production.services.dashboard.ProjectOperationalContextService.class),
                mock(com.eneik.production.services.monitor.SystemProgressTracker.class),
                mock(CodeChangeClassifier.class), mock(com.eneik.production.repositories.FeatureThreadRepository.class),
                claims, mock(com.eneik.production.repositories.ProjectRepository.class));

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
                mock(com.eneik.production.services.video.VideoAssetService.class),
                mock(com.eneik.production.services.dashboard.ProjectOperationalContextService.class),
                mock(com.eneik.production.services.monitor.SystemProgressTracker.class),
                mock(CodeChangeClassifier.class), mock(com.eneik.production.repositories.FeatureThreadRepository.class),
                claims, mock(com.eneik.production.repositories.ProjectRepository.class));

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
                mock(com.eneik.production.services.video.VideoAssetService.class),
                mock(com.eneik.production.services.dashboard.ProjectOperationalContextService.class),
                mock(com.eneik.production.services.monitor.SystemProgressTracker.class),
                mock(CodeChangeClassifier.class), mock(com.eneik.production.repositories.FeatureThreadRepository.class),
                claims, mock(com.eneik.production.repositories.ProjectRepository.class));

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

    private PrReviewEntity reviewWithStatus(String status) {
        PrReviewEntity review = new PrReviewEntity();
        review.setCiStatus(status);
        return review;
    }
}
