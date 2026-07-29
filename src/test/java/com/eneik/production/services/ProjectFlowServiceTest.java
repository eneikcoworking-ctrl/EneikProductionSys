package com.eneik.production.services;

import com.eneik.production.models.persistence.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectFlowServiceTest {

    @Test
    void deliverableMergeRatioUsesMergedPlannedTasksNotFeatureReadiness() {
        var readiness = new ClientDeliverableReadinessService.Readiness(
                5,
                3,
                19,
                17,
                0.6,
                true);

        assertEquals(17.0 / 19.0, ProjectFlowService.deliverableMergeRatio(readiness), 0.0001);
    }

    @Test
    void terminalSpikeCompletedIsNotAnActionableBlockedStatus() {
        assertFalse(ProjectFlowService.isActionableBlockedStatus(TaskStatus.failed));
        assertFalse(ProjectFlowService.isActionableBlockedStatus(TaskStatus.spike_completed));
        assertTrue(ProjectFlowService.isActionableBlockedStatus(TaskStatus.claimed));
        assertTrue(ProjectFlowService.isActionableBlockedStatus(TaskStatus.done));
    }
}
