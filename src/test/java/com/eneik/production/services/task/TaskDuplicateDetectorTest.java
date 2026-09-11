package com.eneik.production.services.task;

import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TaskDuplicateDetectorTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("taskContentKey prioritizes contentKey over description with generated UUID")
    void taskContentKeyPrioritizesContentKey() {
        String sharedContentKey = "compile:proj-123:sha256abc";
        TaskEntity task1 = new TaskEntity();
        task1.setContentKey(sharedContentKey);
        task1.setDescription("Read instructions at .eneik/records/task-plan-" + UUID.randomUUID() + ".json");

        TaskEntity task2 = new TaskEntity();
        task2.setContentKey(sharedContentKey);
        task2.setDescription("Read instructions at .eneik/records/task-plan-" + UUID.randomUUID() + ".json");

        // Despite different random plan paths in descriptions, contentKey unifies their work identity
        assertEquals(sharedContentKey, TaskDuplicateDetector.taskContentKey(task1));
        assertEquals(sharedContentKey, TaskDuplicateDetector.taskContentKey(task2));
        assertEquals(TaskDuplicateDetector.taskContentKey(task1), TaskDuplicateDetector.taskContentKey(task2));
    }

    @Test
    @DisplayName("taskContentKey falls back to payload slice_title then description")
    void taskContentKeyFallbacks() throws Exception {
        TaskEntity sliceTask = new TaskEntity();
        sliceTask.setPayload(objectMapper.readTree("{\"slice_title\":\"Slice 1: Auth\"}"));
        sliceTask.setDescription("Different description");

        TaskEntity descTask = new TaskEntity();
        descTask.setDescription("Just plain description");

        assertEquals("Slice 1: Auth", TaskDuplicateDetector.taskContentKey(sliceTask));
        assertEquals("Just plain description", TaskDuplicateDetector.taskContentKey(descTask));
    }

    @Test
    @DisplayName("Stuck duplicates ignore terminal tasks and deliberate recovery replacements")
    void stuckDuplicatesExcludeTerminalAndDeliberateRecovery() throws Exception {
        String key = "work:item:1";

        // 4 terminal tasks sharing same key
        TaskEntity t1 = new TaskEntity();
        t1.setContentKey(key);
        t1.setStatus(TaskStatus.done);

        TaskEntity t2 = new TaskEntity();
        t2.setContentKey(key);
        t2.setStatus(TaskStatus.failed);

        TaskEntity t3 = new TaskEntity();
        t3.setContentKey(key);
        t3.setStatus(TaskStatus.blocked);

        TaskEntity t4 = new TaskEntity();
        t4.setContentKey(key);
        t4.setStatus(TaskStatus.spike_completed);

        // Deliberate recovery task
        TaskEntity rec = new TaskEntity();
        rec.setContentKey(key);
        rec.setStatus(TaskStatus.queued);
        rec.setPayload(objectMapper.readTree("{\"recoversFailedTaskId\":\"" + UUID.randomUUID() + "\"}"));

        List<TaskEntity> tasks = List.of(t1, t2, t3, t4, rec);
        assertFalse(TaskDuplicateDetector.hasStuckDuplicateContent(tasks));
        assertTrue(TaskDuplicateDetector.findStuckDuplicateContent(tasks).isEmpty());

        // Now add 3 real active queued tasks
        TaskEntity active1 = new TaskEntity();
        active1.setContentKey(key);
        active1.setStatus(TaskStatus.queued);

        TaskEntity active2 = new TaskEntity();
        active2.setContentKey(key);
        active2.setStatus(TaskStatus.queued);

        TaskEntity active3 = new TaskEntity();
        active3.setContentKey(key);
        active3.setStatus(TaskStatus.claimed);

        List<TaskEntity> activeTasks = List.of(t1, rec, active1, active2, active3);
        assertTrue(TaskDuplicateDetector.hasStuckDuplicateContent(activeTasks));
        List<TaskDuplicateDetector.StuckDuplicateContent> stuck = TaskDuplicateDetector.findStuckDuplicateContent(activeTasks);
        assertEquals(1, stuck.size());
        assertEquals(key, stuck.get(0).contentKey());
        assertEquals(3, stuck.get(0).count());
    }

    @Test
    @DisplayName("Velocity detection strictly requires > COMPILE_ATTEMPT_BUDGET (lawful 3 attempts do not trip)")
    void velocityDetectionRequiresStrictlyGreaterThanBudget() throws Exception {
        String sliceTitle = "Implement auth middleware";
        Instant now = Instant.now();
        Instant windowStart = now.minus(Duration.ofHours(2));

        // 1 base task + 2 legitimate recovery tasks (MAX_REPAIR_DEPTH = 2) = 3 tasks total
        TaskEntity base = new TaskEntity();
        base.setPayload(objectMapper.readTree("{\"slice_title\":\"" + sliceTitle + "\"}"));
        base.setStatus(TaskStatus.failed);
        base.setCreatedAt(windowStart.plusSeconds(300));

        TaskEntity repair1 = new TaskEntity();
        repair1.setPayload(objectMapper.readTree("{\"slice_title\":\"" + sliceTitle + "\",\"recoversFailedTaskId\":\"" + UUID.randomUUID() + "\"}"));
        repair1.setStatus(TaskStatus.failed);
        repair1.setCreatedAt(windowStart.plusSeconds(600));

        TaskEntity repair2 = new TaskEntity();
        repair2.setPayload(objectMapper.readTree("{\"slice_title\":\"" + sliceTitle + "\",\"recoversFailedTaskId\":\"" + UUID.randomUUID() + "\"}"));
        repair2.setStatus(TaskStatus.done);
        repair2.setCreatedAt(windowStart.plusSeconds(900));

        List<TaskEntity> legalBatch = List.of(base, repair1, repair2);

        // Lawful budget (3 attempts) does NOT trip velocity
        List<TaskDuplicateDetector.DuplicateGenerationVelocity> legalVelocities =
                TaskDuplicateDetector.detectDuplicateGenerationVelocity(legalBatch, windowStart, now, WishlistEntity.COMPILE_ATTEMPT_BUDGET);
        assertTrue(legalVelocities.isEmpty(), "Lawful budget of 3 attempts (1 base + 2 repairs) must not trip velocity");

        // 4th task generated (exceeding budget of 3) DOES trip velocity
        TaskEntity excessTask = new TaskEntity();
        excessTask.setPayload(objectMapper.readTree("{\"slice_title\":\"" + sliceTitle + "\",\"recoversFailedTaskId\":\"" + UUID.randomUUID() + "\"}"));
        excessTask.setStatus(TaskStatus.queued);
        excessTask.setCreatedAt(windowStart.plusSeconds(1200));

        List<TaskEntity> excessBatch = List.of(base, repair1, repair2, excessTask);
        List<TaskDuplicateDetector.DuplicateGenerationVelocity> excessVelocities =
                TaskDuplicateDetector.detectDuplicateGenerationVelocity(excessBatch, windowStart, now, WishlistEntity.COMPILE_ATTEMPT_BUDGET);
        assertEquals(1, excessVelocities.size());
        assertEquals(sliceTitle, excessVelocities.get(0).contentKey());
        assertEquals(4, excessVelocities.get(0).count());
    }

    @Test
    @DisplayName("evaluateVelocity handles process attempts exceeding budget")
    void evaluateVelocityForRevivedProcessUnits() {
        Duration window = Duration.ofHours(2);
        Instant now = Instant.now();
        Instant windowStart = now.minus(window);
        int threshold = WishlistEntity.COMPILE_ATTEMPT_BUDGET; // 3

        // 3 compile attempts (lawful budget) -> no defect
        Optional<TaskDuplicateDetector.DuplicateGenerationVelocity> atBudget =
                TaskDuplicateDetector.evaluateVelocity("compile:p1:hash", 3, window, windowStart, now, threshold);
        assertTrue(atBudget.isEmpty());

        // 4 compile attempts (exceeds budget) -> velocity defect
        Optional<TaskDuplicateDetector.DuplicateGenerationVelocity> overBudget =
                TaskDuplicateDetector.evaluateVelocity("compile:p1:hash", 4, window, windowStart, now, threshold);
        assertTrue(overBudget.isPresent());
        assertEquals(4, overBudget.get().count());
        assertEquals("compile:p1:hash", overBudget.get().contentKey());
    }
}
