package com.eneik.production.models.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The delivery question is asked by two instruments, and a task counts as asked when EITHER has ruled.
 *
 * <p>Measured on test-fiftieth, 2026-08-28, over 365 tasks: the gate instrument
 * ({@code applicableChecksByStage.IMPLEMENTATION_RESULT}) applied to ZERO of them, while the criterion
 * instrument had already ruled on 127 - 82 satisfied, 45 refuted. FlowSpine reported
 * "NEVER ASKED=365 of 365" the whole time, because the predicate read only the instrument whose coverage
 * was nil. These tests pin the union, and pin the boundary that makes it honest: recorded ignorance is
 * not an answer.
 */
class DeliveryVerificationPredicateTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TaskEntity taskWithVerdict(String verdict) {
        TaskEntity task = new TaskEntity();
        ObjectNode payload = MAPPER.createObjectNode();
        if (verdict != null) {
            payload.put(TaskEntity.ACCEPTANCE_VERDICT_KEY, verdict);
        }
        task.setPayload(payload);
        return task;
    }

    private TaskEntity taskWithGateChecks(int implementationResultChecks, boolean passed) {
        TaskEntity task = new TaskEntity();
        ObjectNode report = MAPPER.createObjectNode();
        report.putObject("applicableChecksByStage").put("IMPLEMENTATION_RESULT", implementationResultChecks);
        task.setQualityGateReport(report);
        task.setQualityGatePassed(passed);
        return task;
    }

    @Test
    void aSatisfiedCriterionVerifiesDeliveryEvenWithNoGateAtAll() {
        // The measured majority case: no gate ever applied, yet the client's own criterion was met.
        TaskEntity task = taskWithVerdict(TaskEntity.VERDICT_SATISFIED);
        assertTrue(task.isVerifiedForDelivery());
        assertFalse(task.isDeliveryVerificationAbsent());
        assertFalse(task.deliveryRefuted());
    }

    @Test
    void aRefutedCriterionCountsAsAskedButNotAsVerified() {
        TaskEntity task = taskWithVerdict(TaskEntity.VERDICT_REFUTED);
        assertFalse(task.isVerifiedForDelivery());
        assertFalse(task.isDeliveryVerificationAbsent(), "a refutation is an answer, not silence");
        assertTrue(task.deliveryRefuted());
    }

    @Test
    void recordedIgnoranceIsNotAnAnswer() {
        // The boundary that keeps this honest. UNDECIDABLE and NOT_JUDGED_NO_DIFF say the question was put
        // and could not be settled. Counting them as answers is the ACP-105 error the codebase already
        // documents: a field with no place for "not measured" starts reporting silence as a result.
        for (String nonRuling : new String[]{"UNDECIDABLE", "NOT_JUDGED_NO_DIFF"}) {
            TaskEntity task = taskWithVerdict(nonRuling);
            assertTrue(task.isDeliveryVerificationAbsent(), nonRuling + " must still count as never asked");
            assertFalse(task.isVerifiedForDelivery(), nonRuling + " must never count as verified");
            assertFalse(task.deliveryRefuted(), nonRuling + " is not a refutation");
        }
    }

    @Test
    void noVerdictAndNoGateMeansNeverAsked() {
        assertTrue(taskWithVerdict(null).isDeliveryVerificationAbsent());
        assertTrue(new TaskEntity().isDeliveryVerificationAbsent());
    }

    @Test
    void theGateInstrumentStillWorksOnItsOwn() {
        // The union adds an instrument, it does not replace one. A task the gate did rule on keeps its
        // answer even with no verdict recorded.
        assertTrue(taskWithGateChecks(2, true).isVerifiedForDelivery());
        assertFalse(taskWithGateChecks(2, true).isDeliveryVerificationAbsent());
        assertFalse(taskWithGateChecks(2, false).isVerifiedForDelivery());
        // Zero applicable checks is the ACP-105 case the gate predicate was already written to catch:
        // allMatch over an empty list is true, so "passed" with nothing applied is not verified.
        assertFalse(taskWithGateChecks(0, true).isVerifiedForDelivery());
        assertTrue(taskWithGateChecks(0, true).isDeliveryVerificationAbsent());
    }

    @Test
    void aSatisfiedVerdictOutranksAFailedGate() {
        // Deliberate and stated: the criterion is what the client asked for; the gate is what a role
        // generally owes. When both ruled and they disagree, the client's question decides delivery.
        TaskEntity task = taskWithGateChecks(3, false);
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put(TaskEntity.ACCEPTANCE_VERDICT_KEY, TaskEntity.VERDICT_SATISFIED);
        task.setPayload(payload);
        assertTrue(task.isVerifiedForDelivery());
    }
}
