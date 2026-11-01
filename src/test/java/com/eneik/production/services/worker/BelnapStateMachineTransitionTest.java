package com.eneik.production.services.worker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BelnapStateMachineTransitionTest {

    @Test
    @DisplayName("Belnap 4-Valued Logic: verifies terminal and success predicate semantics")
    void testBelnapTruthTable() {
        // N - In progress
        BelnapStatus n = BelnapStatus.NEITHER;
        assertFalse(n.isTerminal());
        assertFalse(n.isSuccess());
        assertFalse(n.isConflict());

        // T - Truth / Success
        BelnapStatus t = BelnapStatus.TRUE;
        assertTrue(t.isTerminal());
        assertTrue(t.isSuccess());

        // F - Falsified
        BelnapStatus f = BelnapStatus.FALSE;
        assertTrue(f.isTerminal());
        assertTrue(f.isFalsified());

        // B - Both / Contradiction
        BelnapStatus b = BelnapStatus.BOTH;
        assertTrue(b.isTerminal());
        assertTrue(b.isConflict());
    }

    @Test
    @DisplayName("WorkerPollResult correctly mirrors Belnap states")
    void testWorkerPollResultBelnapMapping() {
        UUID id = UUID.randomUUID();

        WorkerPollResult resN = WorkerPollResult.inProgress(id, "generating");
        assertEquals(BelnapStatus.NEITHER, resN.belnapStatus());
        assertFalse(resN.isCompleted());
        assertFalse(resN.isFailed());

        WorkerPollResult resT = WorkerPollResult.success(id, "https://github.com/pull/1");
        assertEquals(BelnapStatus.TRUE, resT.belnapStatus());
        assertTrue(resT.isCompleted());

        WorkerPollResult resF = WorkerPollResult.failed(id, "compilation error");
        assertEquals(BelnapStatus.FALSE, resF.belnapStatus());
        assertTrue(resF.isFailed());

        WorkerPollResult resB = WorkerPollResult.conflict(id, "git merge conflict");
        assertEquals(BelnapStatus.BOTH, resB.belnapStatus());
        assertTrue(resB.belnapStatus().isConflict());
    }
}
