package com.eneik.production.models.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Model rule 8.11 O8: a record that cannot be read correctly is not a record.
 *
 * <p>"Nobody ever asked whether this task delivered" and "it was asked and no ruling came back" leave the
 * delivery equally unverified, but they are different defects pointing at different places - the first at
 * coverage of the independent witness (rule 8.15), the second at the criteria it was given. Reported as one
 * number under the name "never asked", the line states something false about whichever of the two it is,
 * and on the live circuit it did: 385 of 665 tasks read as a coverage gap.
 */
class DeliveryQuestionRecordTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void aVerdictThatSettlesNothingIsNotTheSameAsNeverBeingAsked() {
        TaskEntity task = withVerdict("UNDECIDABLE");

        assertTrue(task.deliveryQuestionPutButUnsettled(), "the question was put and came back unsettled");
        assertTrue(task.isDeliveryVerificationAbsent(), "and the delivery is still unverified");
    }

    @Test
    void aTaskNobodyAskedIsNotCountedAsAsked() {
        // The case that keeps the two apart. Without it every unverified task reads as "asked", which is
        // the same conflation in the other direction.
        TaskEntity task = new TaskEntity();

        assertFalse(task.deliveryQuestionPutButUnsettled(), "nothing ever ruled on this task");
        assertTrue(task.isDeliveryVerificationAbsent());
    }

    @Test
    void aRuledVerdictIsNeitherUnsettledNorAbsent() {
        // Without this the split degenerates into "everything was asked and nothing was settled".
        TaskEntity satisfied = withVerdict(TaskEntity.VERDICT_SATISFIED);
        TaskEntity refuted = withVerdict(TaskEntity.VERDICT_REFUTED);

        assertFalse(satisfied.deliveryQuestionPutButUnsettled());
        assertFalse(satisfied.isDeliveryVerificationAbsent());
        assertFalse(refuted.deliveryQuestionPutButUnsettled());
        assertFalse(refuted.isDeliveryVerificationAbsent());
    }

    private TaskEntity withVerdict(String verdict) {
        TaskEntity task = new TaskEntity();
        task.setPayload(MAPPER.createObjectNode().put(TaskEntity.ACCEPTANCE_VERDICT_KEY, verdict));
        return task;
    }
}
