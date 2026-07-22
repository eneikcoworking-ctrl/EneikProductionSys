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

        assertFalse(AutoMergeService.isReviewPollCandidate(reviewWithStatus("failure")));
        assertFalse(AutoMergeService.isReviewPollCandidate(reviewWithStatus("conflict")));
        assertFalse(AutoMergeService.isReviewPollCandidate(reviewWithStatus("owner_mismatch")));
        assertFalse(AutoMergeService.isReviewPollCandidate(reviewWithStatus("unowned")));
        assertFalse(AutoMergeService.isReviewPollCandidate(reviewWithStatus("invalid_pr")));
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
