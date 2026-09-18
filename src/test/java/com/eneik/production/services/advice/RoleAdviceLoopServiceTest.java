package com.eneik.production.services.advice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RoleAdviceLoopServiceTest {

    private RoleAdviceLoopService roleAdviceLoopService;

    @BeforeEach
    void setUp() {
        roleAdviceLoopService = new RoleAdviceLoopService();
    }

    @Test
    void afterTaskComplete_withValidTaskId_suppressesSpeculativeWork() {
        UUID taskId = UUID.randomUUID();
        RoleAdviceLoopService.RoleAdviceGuardVerdict verdict = roleAdviceLoopService.afterTaskComplete(taskId);

        assertEquals(RoleAdviceLoopService.RoleAdviceGuardVerdict.SPECULATIVE_WORK_SUPPRESSED, verdict);
    }

    @Test
    void afterTaskComplete_withNullTaskId_returnsTaskNullOrSkipped() {
        RoleAdviceLoopService.RoleAdviceGuardVerdict verdict = roleAdviceLoopService.afterTaskComplete(null);

        assertEquals(RoleAdviceLoopService.RoleAdviceGuardVerdict.TASK_NULL_OR_SKIPPED, verdict);
    }
}
