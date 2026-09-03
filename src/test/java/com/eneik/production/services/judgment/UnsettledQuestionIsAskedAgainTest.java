package com.eneik.production.services.judgment;

import com.eneik.production.models.persistence.TaskEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Model rule 8.24: a verdict that settled nothing is not a decision.
 *
 * <p>The sweep selected only tasks carrying no verdict at all, so a question that was put and came back
 * unsettled was never put again. Measured 04.09: 272 of 665 tasks frozen as unverified, and they would have
 * stayed frozen after the cause of the unsettlement was removed - a belief held on a limit that no longer
 * exists, against Charter invariant 15.
 *
 * <p>The turn of that cycle carries an observed quantity rather than an assigned one: the ground itself.
 * While each ground differs from the last, asking is still learning; once it repeats, asking has stopped
 * producing knowledge and the question closes as unsettleable.
 */
class UnsettledQuestionIsAskedAgainTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DeliveredWorkJudgmentService service =
            mock(DeliveredWorkJudgmentService.class, org.mockito.Mockito.CALLS_REAL_METHODS);

    @Test
    void aQuestionNothingHasRuledOnIsAsked() {
        assertTrue(service.stillWorthAsking(new TaskEntity()));
    }

    @Test
    void anUnsettledVerdictWhoseGroundHasNotRepeatedIsAskedAgain() {
        TaskEntity task = withVerdict("UNDECIDABLE", "the diff was shown in part", false);

        assertTrue(service.stillWorthAsking(task), "nothing would ever revisit this task");
    }

    @Test
    void anUnsettledVerdictWhoseGroundRepeatedIsNotAskedAgain() {
        // The mandatory reverse case: without it the factory re-asks forever the questions that cannot be
        // settled, spending the judgment channel on work that produces no knowledge.
        TaskEntity task = withVerdict("UNDECIDABLE", "the criteria describe no observable outcome", true);

        assertFalse(service.stillWorthAsking(task));
    }

    @Test
    void aRuledVerdictIsNeverAskedAgain() {
        assertFalse(service.stillWorthAsking(withVerdict("SATISFIED", "the diff carries the change", false)));
        assertFalse(service.stillWorthAsking(withVerdict("REFUTED", "no code in the merged diff", false)));
    }

    private TaskEntity withVerdict(String verdict, String ground, boolean repeated) {
        TaskEntity task = new TaskEntity();
        ObjectNode payload = MAPPER.createObjectNode()
                .put(TaskEntity.ACCEPTANCE_VERDICT_KEY, verdict)
                .put(TaskEntity.ACCEPTANCE_VERDICT_REASON_KEY, ground);
        if (repeated) {
            payload.put(DeliveredWorkJudgmentService.GROUND_REPEATED_KEY, true);
        }
        task.setPayload(payload);
        return task;
    }
}
