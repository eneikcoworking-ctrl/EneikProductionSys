package com.eneik.production.services.operational;

import com.eneik.production.models.persistence.ProjectStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowSpineServiceTest {

    @Test
    void frozenProjectDominatesUnderlyingWork() {
        FlowSpineService.StateInputs input = input(ProjectStatus.frozen, 4, 2, 1, 0, 3, 1,
                1, 0, 2, 1, 1, 1, 5, 2, 1, 0, 5, 1, false, "stalled", true);

        assertEquals("FROZEN", FlowSpineService.decideState(input));
        assertTrue(FlowSpineService.isBlockingState("FROZEN"));
    }

    @Test
    void localDuplicateContentBlocksBeforeDispatch() {
        FlowSpineService.StateInputs input = input(ProjectStatus.active, 3, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 3, 0, true, "ok", true);

        assertEquals("BLOCKED_BY_DUPLICATE_CONTENT", FlowSpineService.decideState(input));
    }

    @Test
    void githubRateLimitBlocksBeforeReviewWhenNoLocalHardBlocker() {
        FlowSpineService.StateInputs input = input(ProjectStatus.active, 0, 0, 2, 0, 0, 0,
                0, 0, 0, 0, 2, 1, 0, 1, 2, 0, 8, 2, true, "github_rate_limited", false);

        assertEquals("GITHUB_RATE_LIMITED", FlowSpineService.decideState(input));
        assertTrue(FlowSpineService.isBlockingState("GITHUB_RATE_LIMITED"));
    }

    @Test
    void duplicateContentStillDominatesGithubRateLimit() {
        FlowSpineService.StateInputs input = input(ProjectStatus.active, 3, 0, 2, 0, 0, 0,
                0, 0, 0, 0, 2, 1, 0, 1, 2, 0, 8, 2, true, "github_rate_limited", true);

        assertEquals("BLOCKED_BY_DUPLICATE_CONTENT", FlowSpineService.decideState(input));
    }

    @Test
    void failedFrontierWithoutLiveWorkIsNotIdle() {
        FlowSpineService.StateInputs input = input(ProjectStatus.active, 0, 0, 0, 0, 7, 0,
                0, 0, 0, 1, 0, 0, 4, 2, 1, 0, 5, 1, true, "ok", false);

        assertEquals("BLOCKED_BY_FAILED_FRONTIER", FlowSpineService.decideState(input));
    }

    @Test
    void failingReviewBlocksBeforeUnderReview() {
        FlowSpineService.StateInputs input = input(ProjectStatus.active, 0, 0, 2, 0, 0, 0,
                0, 0, 0, 0, 2, 1, 0, 1, 2, 0, 8, 2, true, "ok", false);

        assertEquals("BLOCKED_BY_REVIEW", FlowSpineService.decideState(input));
    }

    @Test
    void queuedAndImplementingAndReviewStatesAreStable() {
        assertEquals("QUEUED", FlowSpineService.decideState(input(ProjectStatus.active, 1, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 3, 0, true, "ok", false)));
        assertEquals("IMPLEMENTING", FlowSpineService.decideState(input(ProjectStatus.active, 0, 1, 0, 0, 0, 0,
                0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 3, 0, true, "ok", false)));
        assertEquals("UNDER_REVIEW", FlowSpineService.decideState(input(ProjectStatus.active, 0, 0, 1, 0, 0, 0,
                0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 3, 0, true, "ok", false)));
    }

    @Test
    void deliveredRequiresAllFeaturesComplete() {
        FlowSpineService.StateInputs input = input(ProjectStatus.active, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 3, 0, 0, 5, 0, 2, 2, 6, 6, true, "ok", false);

        assertEquals("DELIVERED", FlowSpineService.decideState(input));
        assertEquals("client_value_delivered", FlowSpineService.valueStatus("DELIVERED", input));
        assertFalse(FlowSpineService.isBlockingState("DELIVERED"));
    }

    @Test
    void transitionMatrixContainsDeterministicPrecedenceRows() {
        assertEquals(16, FlowSpineService.transitionMatrix().size());
        assertEquals("FROZEN", FlowSpineService.transitionMatrix().get(0).to());
        assertEquals("GITHUB_RATE_LIMITED", FlowSpineService.transitionMatrix().get(3).to());
        assertEquals("IDLE_NO_ACTIONABLE_WORK", FlowSpineService.transitionMatrix().get(15).to());
    }

    @Test
    void bottleneckTaxonomySeparatesReviewAndRuntimeDefects() {
        assertEquals("review_bottleneck", FlowSpineService.bottleneckType("BLOCKED_BY_REVIEW", "ok"));
        assertEquals("github_rate_limit_bottleneck", FlowSpineService.bottleneckType("GITHUB_RATE_LIMITED", "github_rate_limited"));
        assertEquals("runtime_status_bottleneck", FlowSpineService.bottleneckType("UNKNOWN", "content_defect"));
        assertEquals("", FlowSpineService.bottleneckType("DELIVERED", "ok"));
    }

    @Test
    void slaSpecsMakeBlockingReviewHighUrgency() {
        FlowSpineService.SlaSpec review = FlowSpineService.slaForState("BLOCKED_BY_REVIEW");
        FlowSpineService.SlaSpec github = FlowSpineService.slaForState("GITHUB_RATE_LIMITED");
        FlowSpineService.SlaSpec queued = FlowSpineService.slaForState("QUEUED");
        FlowSpineService.SlaSpec delivered = FlowSpineService.slaForState("DELIVERED");

        assertEquals(30, review.minutes());
        assertEquals("high", review.severity());
        assertEquals(0, github.minutes());
        assertEquals("high", github.severity());
        assertEquals(15, queued.minutes());
        assertEquals(-1, delivered.minutes());
    }

    private FlowSpineService.StateInputs input(ProjectStatus projectStatus,
                                               long queuedTasks,
                                               long activeTasks,
                                               long reviewTasks,
                                               long doneTasks,
                                               long failedTasks,
                                               long blockedTasks,
                                               long pendingWishlist,
                                               long compilingWishlist,
                                               long openSessions,
                                               int mergedReviews,
                                               int openReviews,
                                               int failingReviews,
                                               int qualityGatePassed,
                                               int qualityGateFailed,
                                               int totalFeatures,
                                               int completeFeatures,
                                               int totalDeliverables,
                                               int mergedDeliverables,
                                               boolean decompositionComplete,
                                               String systemStatus,
                                               boolean duplicateContentDetected) {
        return new FlowSpineService.StateInputs(projectStatus, queuedTasks, activeTasks, reviewTasks, doneTasks,
                failedTasks, blockedTasks, pendingWishlist, compilingWishlist, openSessions, mergedReviews,
                openReviews, failingReviews, qualityGatePassed, qualityGateFailed, totalFeatures, completeFeatures,
                totalDeliverables, mergedDeliverables, decompositionComplete, systemStatus, duplicateContentDetected);
    }
}
