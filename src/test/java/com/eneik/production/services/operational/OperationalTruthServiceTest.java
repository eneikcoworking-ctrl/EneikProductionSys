package com.eneik.production.services.operational;

import com.eneik.production.services.ClientDeliverableReadinessService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalTruthServiceTest {

    @Test
    void deliveryStatusSeparatesNoScopeDecomposingBuildingAndDelivered() {
        assertEquals("no_scope", OperationalTruthService.deliveryStatus(
                new ClientDeliverableReadinessService.Readiness(0, 0, 0, 0, 0.0, false)));
        assertEquals("decomposing", OperationalTruthService.deliveryStatus(
                new ClientDeliverableReadinessService.Readiness(3, 0, 9, 0, 0.0, false)));
        assertEquals("building", OperationalTruthService.deliveryStatus(
                new ClientDeliverableReadinessService.Readiness(3, 2, 9, 7, 2.0 / 3.0, true)));
        assertEquals("delivered", OperationalTruthService.deliveryStatus(
                new ClientDeliverableReadinessService.Readiness(3, 3, 9, 9, 1.0, true)));
    }

    @Test
    void trustLevelUsesStableBands() {
        assertEquals("trusted", OperationalTruthService.trustLevel(0.95));
        assertEquals("watch", OperationalTruthService.trustLevel(0.70));
        assertEquals("degraded", OperationalTruthService.trustLevel(0.50));
        assertEquals("blocked", OperationalTruthService.trustLevel(0.20));
    }

    @Test
    void onlyExplicitHealthySystemStatusesAvoidTrustBlock() {
        assertFalse(OperationalTruthService.isTrustBlockingSystemStatus("ok"));
        assertFalse(OperationalTruthService.isTrustBlockingSystemStatus("idle_no_actionable_work"));
        assertFalse(OperationalTruthService.isTrustBlockingSystemStatus("busy_with_actionable_work"));
        assertTrue(OperationalTruthService.isTrustBlockingSystemStatus("content_defect"));
        assertTrue(OperationalTruthService.isTrustBlockingSystemStatus("stalled"));
    }

    @Test
    void clampRoundsAndBoundsTrustScores() {
        assertEquals(1.0, OperationalTruthService.clamp(1.5));
        assertEquals(0.0, OperationalTruthService.clamp(-0.1));
        assertEquals(0.67, OperationalTruthService.clamp(0.666));
    }
}
