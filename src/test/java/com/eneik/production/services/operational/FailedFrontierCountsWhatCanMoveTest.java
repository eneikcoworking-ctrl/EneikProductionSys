package com.eneik.production.services.operational;

import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Model rule 8.6 with Charter invariant 8: a globally blocking state must count only what its named
 * resolver can act on, and an element that can structurally never reach done leaves the denominator.
 *
 * <p>Measured 04.09 on test-fiftieth: one failed task, resumable on its own terms, waiting on a dependency
 * whose single resume was already spent. It held BLOCKED_BY_FAILED_FRONTIER for 17103 minutes with no path
 * out, and writing its status instead of correcting the count produced 128 blocks against 129 retirements
 * of the same row - a cycle with no decreasing measure.
 */
class FailedFrontierCountsWhatCanMoveTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void aTaskWaitingOnADependencyNothingWillReviveIsNotCounted() {
        WishlistEntity brief = clientBrief();
        TaskEntity dependency = resumable(brief);
        dependency.setPayload(MAPPER.createObjectNode().put("ems_bounded_plan_resume_count", 1));
        TaskEntity waiting = resumable(brief);
        waiting.setDependsOn(dependency);

        assertEquals(0, FlowSpineService.countFailedTheResolverCanAct(
                List.of(waiting), Map.of(brief.getId(), brief)));
    }

    @Test
    void aTaskWaitingOnADependencyThatCanStillBeRevivedIsCounted() {
        // The mandatory reverse case: without it the gate would stop reporting real work the resolver is
        // about to revive, and a genuine hold would go unnamed.
        WishlistEntity brief = clientBrief();
        TaskEntity dependency = resumable(brief);
        TaskEntity waiting = resumable(brief);
        waiting.setDependsOn(dependency);

        assertEquals(1, FlowSpineService.countFailedTheResolverCanAct(
                List.of(waiting), Map.of(brief.getId(), brief)));
    }

    @Test
    void aTaskWithNoDependencyAtAllIsStillCounted() {
        WishlistEntity brief = clientBrief();

        assertEquals(1, FlowSpineService.countFailedTheResolverCanAct(
                List.of(resumable(brief)), Map.of(brief.getId(), brief)));
    }

    private WishlistEntity clientBrief() {
        WishlistEntity brief = new WishlistEntity();
        brief.setId(UUID.randomUUID());
        brief.setSource(WishlistSource.client);
        return brief;
    }

    private TaskEntity resumable(WishlistEntity brief) {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setStatus(TaskStatus.failed);
        task.setFeatureId(UUID.randomUUID());
        task.setSourceWishlistId(brief.getId());
        task.setJulesDispatchStatus("auto-recovery is disabled; dependent task retired");
        return task;
    }
}
