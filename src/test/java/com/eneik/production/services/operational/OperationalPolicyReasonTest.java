package com.eneik.production.services.operational;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Action plan 4.6. Measured 2026-08-29: DISPATCH_REVIEW_TASKS was refused once a tick, fifteen ticks
 * running, saying "denied by Flow Core state DECOMPOSING" - while CHECK_COVERAGE_AUDITS, whose set of
 * blocking states is byte-for-byte the same, was allowed in those same ticks. The state was blocking
 * neither. The message named a cause that was not one, and this session spent an hour investigating the
 * state it named.
 */
class OperationalPolicyReasonTest {

    @Test
    void decomposingOnAnActiveProjectBlocksNothing() {
        assertFalse(OperationalPolicyService.stateBlocks("DECOMPOSING", "active"));
    }

    @Test
    void theGloballyBlockingStatesStillBlock() {
        for (String state : OperationalPolicyService.GLOBALLY_BLOCKING_STATES) {
            assertTrue(OperationalPolicyService.stateBlocks(state, "active"), state + " must block");
        }
        assertTrue(OperationalPolicyService.stateBlocks("SYSTEM_STALLED", "active"), "hard blocks still block");
        assertTrue(OperationalPolicyService.stateBlocks("DECOMPOSING", "archived"),
                "an inactive project blocks whatever the state says");
    }

    @Test
    void aDenialUnderANonBlockingStateDoesNotBlameTheState() {
        String reason = OperationalPolicyService.denialReason(
                OperationalAction.DISPATCH_REVIEW_TASKS, "DECOMPOSING", "active", null,
                "ENFORCED_ACTIONS_AVAILABLE");

        assertTrue(reason.contains("is not blocked by Flow Core state DECOMPOSING"),
                "the sentence must say the state is not the cause: " + reason);
        assertTrue(reason.contains("nothing for it to act on"),
                "and must say what the cause is: " + reason);
        assertFalse(reason.contains("denied by Flow Core state"),
                "it must not claim the state denied it: " + reason);
    }

    @Test
    void aDenialUnderABlockingStateStillNamesIt() {
        String reason = OperationalPolicyService.denialReason(
                OperationalAction.DISPATCH_QUEUED_TASKS, "FROZEN", "active", "the line is stopped",
                "ENFORCED_STOP_THE_LINE");

        assertTrue(reason.contains("denied by Flow Core state FROZEN"), reason);
        assertTrue(reason.contains("the line is stopped"), reason);
    }

    @Test
    void recoverFailedFrontierUnderBlockedByReviewDoesNotBlameTheState() {
        assertFalse(OperationalPolicyService.stateBlocks(OperationalAction.RECOVER_FAILED_FRONTIER, "BLOCKED_BY_REVIEW", "active"),
                "BLOCKED_BY_REVIEW is not in GLOBALLY_BLOCKING_STATES and must not block RECOVER_FAILED_FRONTIER");

        String reason = OperationalPolicyService.denialReason(
                OperationalAction.RECOVER_FAILED_FRONTIER, "BLOCKED_BY_REVIEW", "active",
                "1 failing review(s) exist", "ENFORCED_ACTIONS_AVAILABLE");

        assertTrue(reason.contains("is not blocked by Flow Core state BLOCKED_BY_REVIEW"),
                "the sentence must say BLOCKED_BY_REVIEW is not the cause for RECOVER_FAILED_FRONTIER: " + reason);
        assertTrue(reason.contains("nothing for it to act on"),
                "and must say what the cause is: " + reason);
        assertFalse(reason.contains("denied by Flow Core state BLOCKED_BY_REVIEW"),
                "it must not claim BLOCKED_BY_REVIEW denied it: " + reason);
    }
}
