package com.eneik.production.models.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests enforcing Law 20 / Invariant S2 from ENGINEERING_PHILOSOPHY_ACTION_PLAN.md:
 * terminal(τ) ⟹ status(τ) cannot be overwritten.
 *
 * Terminal states: {done, failed, spike_completed}.
 */
public class TaskEntityLaw20Test {

    @Test
    @DisplayName("TaskStatus.isTerminal() correctly identifies terminal vs non-terminal statuses")
    void testTaskStatusIsTerminal() {
        // Terminal statuses under Law 20
        assertTrue(TaskStatus.done.isTerminal(), "done must be terminal");
        assertTrue(TaskStatus.failed.isTerminal(), "failed must be terminal");
        assertTrue(TaskStatus.spike_completed.isTerminal(), "spike_completed must be terminal");

        // Non-terminal statuses
        assertFalse(TaskStatus.queued.isTerminal(), "queued is not terminal");
        assertFalse(TaskStatus.claimed.isTerminal(), "claimed is not terminal");
        assertFalse(TaskStatus.in_progress.isTerminal(), "in_progress is not terminal");
        assertFalse(TaskStatus.pending_review.isTerminal(), "pending_review is not terminal");
        assertFalse(TaskStatus.review.isTerminal(), "review is not terminal");
        assertFalse(TaskStatus.blocked.isTerminal(), "blocked is recoverable, not terminal");
    }

    @Test
    @DisplayName("TaskEntity lifecycle: non-terminal transitions succeed")
    void testNonTerminalTransitionsSucceed() {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        assertEquals(TaskStatus.queued, task.getStatus());
        assertFalse(task.isTerminal());

        task.setStatus(TaskStatus.claimed);
        assertEquals(TaskStatus.claimed, task.getStatus());
        assertFalse(task.isTerminal());

        task.setStatus(TaskStatus.in_progress);
        assertEquals(TaskStatus.in_progress, task.getStatus());
        assertFalse(task.isTerminal());

        task.setStatus(TaskStatus.pending_review);
        assertEquals(TaskStatus.pending_review, task.getStatus());

        task.setStatus(TaskStatus.review);
        assertEquals(TaskStatus.review, task.getStatus());

        task.setStatus(TaskStatus.blocked);
        assertEquals(TaskStatus.blocked, task.getStatus());

        task.setStatus(TaskStatus.queued);
        assertEquals(TaskStatus.queued, task.getStatus());
    }

    @Test
    @DisplayName("TaskEntity reaches done: becomes terminal and rejects mutation to any different status")
    void testDoneIsIrreversible() {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setStatus(TaskStatus.done);

        assertTrue(task.isTerminal());
        assertEquals(TaskStatus.done, task.getStatus());

        // Idempotent assignment succeeds
        assertDoesNotThrow(() -> task.setStatus(TaskStatus.done));

        // Any different status throws IllegalStateException under Law 20 / Invariant S2
        assertThrows(IllegalStateException.class, () -> task.setStatus(TaskStatus.queued));
        assertThrows(IllegalStateException.class, () -> task.setStatus(TaskStatus.claimed));
        assertThrows(IllegalStateException.class, () -> task.setStatus(TaskStatus.in_progress));
        assertThrows(IllegalStateException.class, () -> task.setStatus(TaskStatus.pending_review));
        assertThrows(IllegalStateException.class, () -> task.setStatus(TaskStatus.review));
        assertThrows(IllegalStateException.class, () -> task.setStatus(TaskStatus.failed));
        assertThrows(IllegalStateException.class, () -> task.setStatus(TaskStatus.spike_completed));
        assertThrows(IllegalStateException.class, () -> task.setStatus(TaskStatus.blocked));
    }

    @Test
    @DisplayName("TaskEntity reaches failed: becomes terminal and rejects mutation to any different status")
    void testFailedIsIrreversible() {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setStatus(TaskStatus.failed);

        assertTrue(task.isTerminal());
        assertEquals(TaskStatus.failed, task.getStatus());

        // Idempotent assignment succeeds
        assertDoesNotThrow(() -> task.setStatus(TaskStatus.failed));

        // Any different status throws IllegalStateException
        assertThrows(IllegalStateException.class, () -> task.setStatus(TaskStatus.queued));
        assertThrows(IllegalStateException.class, () -> task.setStatus(TaskStatus.claimed));
        assertThrows(IllegalStateException.class, () -> task.setStatus(TaskStatus.in_progress));
        assertThrows(IllegalStateException.class, () -> task.setStatus(TaskStatus.done));
        assertThrows(IllegalStateException.class, () -> task.setStatus(TaskStatus.spike_completed));
        assertThrows(IllegalStateException.class, () -> task.setStatus(TaskStatus.blocked));
    }

    @Test
    @DisplayName("TaskEntity reaches spike_completed: becomes terminal and rejects mutation to any different status")
    void testSpikeCompletedIsIrreversible() {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setStatus(TaskStatus.spike_completed);

        assertTrue(task.isTerminal());
        assertEquals(TaskStatus.spike_completed, task.getStatus());

        // Idempotent assignment succeeds
        assertDoesNotThrow(() -> task.setStatus(TaskStatus.spike_completed));

        // Any different status throws IllegalStateException
        assertThrows(IllegalStateException.class, () -> task.setStatus(TaskStatus.queued));
        assertThrows(IllegalStateException.class, () -> task.setStatus(TaskStatus.claimed));
        assertThrows(IllegalStateException.class, () -> task.setStatus(TaskStatus.done));
        assertThrows(IllegalStateException.class, () -> task.setStatus(TaskStatus.failed));
        assertThrows(IllegalStateException.class, () -> task.setStatus(TaskStatus.blocked));
    }

    @Test
    @DisplayName("initializeStatus allows explicit status setting during instantiation")
    void testInitializeStatus() {
        TaskEntity task = new TaskEntity();
        task.initializeStatus(TaskStatus.queued);
        assertEquals(TaskStatus.queued, task.getStatus());
        assertFalse(task.isTerminal());
    }
}
