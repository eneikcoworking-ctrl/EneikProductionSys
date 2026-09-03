package com.eneik.production.services.operational;

import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Rule 8.11 O8, on the same number a second time.
 *
 * <p>"Never asked" counted work that has not finished beside work that finished and was never asked. Only
 * the second is a gap in the independent witness (8.15); the first has nothing to be asked about yet.
 * Measured 04.09: of 118 never asked, 112 had simply not closed - so the number named a coverage gap
 * eighteen times larger than the one that exists.
 */
class NeverAskedIsNotOneNumberTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void workThatFinishedAndWasNeverAskedIsCounted() {
        assertEquals(1, FlowSpineService.countNeverAskedAndClosed(List.of(task(TaskStatus.done))));
    }

    @Test
    void workThatHasNotFinishedIsNotCountedAsAGap() {
        // The reverse case that makes the split mean something: without it the count stays the old number
        // under a new name.
        assertEquals(0, FlowSpineService.countNeverAskedAndClosed(
                List.of(task(TaskStatus.queued), task(TaskStatus.in_progress), task(TaskStatus.failed))));
    }

    @Test
    void workThatWasAskedIsNotCountedAtAll() {
        // Neither a ruled verdict nor an unsettled one belongs here - both were asked.
        TaskEntity unsettled = task(TaskStatus.done);
        unsettled.setPayload(MAPPER.createObjectNode()
                .put(TaskEntity.ACCEPTANCE_VERDICT_KEY, "UNDECIDABLE")
                .put(TaskEntity.ACCEPTANCE_VERDICT_REASON_KEY, "could not be settled"));
        TaskEntity ruled = task(TaskStatus.done);
        ruled.setPayload(MAPPER.createObjectNode()
                .put(TaskEntity.ACCEPTANCE_VERDICT_KEY, TaskEntity.VERDICT_SATISFIED));

        assertEquals(0, FlowSpineService.countNeverAskedAndClosed(List.of(unsettled, ruled)));
    }

    private TaskEntity task(TaskStatus status) {
        TaskEntity task = new TaskEntity();
        task.setStatus(status);
        return task;
    }
}
