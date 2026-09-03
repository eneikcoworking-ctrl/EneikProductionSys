package com.eneik.production.services.operational;

import com.eneik.production.models.persistence.TaskEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Model rule 8.11 O8, read through verificationism: a count whose meaning has no method of settling it
 * says nothing.
 *
 * <p>272 of 665 tasks on the live circuit carry a verdict that settled nothing. The judgment can record
 * three different grounds for that, and they are three different defects with three different repairs -
 * criteria no delivery could falsify, an input the channel could not carry, and an answer that did not come
 * in the declared form. Summed into one number they name no place to act.
 */
class UnsettledJudgmentGroundTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void eachRecordedGroundIsNamedApartFromTheOthers() {
        assertEquals("criteria nothing can falsify", ground(
                "this task carries only the compiler's process criteria and no claim about the product, "
                        + "because the criterion the client stated was substituted before dispatch; delivery "
                        + "cannot be tested against statements that no delivery can falsify"));
        assertEquals("input too large for the channel", ground(
                "the judgment channel could not carry this input after 3 attempts; the merged diff of "
                        + "https://example/pr/1 does not fit the sidecar's argument limit"));
        assertEquals("answer not in the declared form", ground(
                "the judgment did not answer in the declared form: MAYBE"));
    }

    @Test
    void aGroundMatchingNoneOfThemIsNotFoldedIntoOne() {
        // Putting an unreadable row into a named bucket is how a count stops answering. Without this the
        // three named grounds would silently absorb everything the judgment learns to record later.
        assertEquals("other", ground("something the judgment recorded that this reader does not know"));
        assertEquals("no ground recorded", ground(""));
    }

    private String ground(String reason) {
        TaskEntity task = new TaskEntity();
        task.setPayload(MAPPER.createObjectNode()
                .put(TaskEntity.ACCEPTANCE_VERDICT_KEY, "UNDECIDABLE")
                .put(TaskEntity.ACCEPTANCE_VERDICT_REASON_KEY, reason));
        return FlowSpineService.groundOfUnsettledJudgment(task);
    }
}
