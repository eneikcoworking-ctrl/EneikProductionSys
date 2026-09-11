package com.eneik.production.controllers;

import com.eneik.production.services.audit.SixSigmaAuditService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QualityGateControllerTest {

    @Test
    void getDefectRateDelegatesToSixSigmaAuditServiceGlobal() {
        SixSigmaAuditService auditService = mock(SixSigmaAuditService.class);
        Map<String, Object> expected = Map.of(
                "totalAttempts", 10L,
                "totalOpportunities", 30L,
                "defects", 2L,
                "passedChecks", 27L,
                "undetermined", 1L,
                "dpmo", 66666.67
        );
        when(auditService.computeQualityGateDefectRate(null)).thenReturn(expected);

        QualityGateController controller = new QualityGateController(auditService);
        Map<String, Object> result = controller.getDefectRate(null);

        assertThat(result).isEqualTo(expected);
        verify(auditService).computeQualityGateDefectRate(null);
    }

    @Test
    void getDefectRateDelegatesToSixSigmaAuditServiceProjectScoped() {
        UUID projectId = UUID.randomUUID();
        SixSigmaAuditService auditService = mock(SixSigmaAuditService.class);
        Map<String, Object> expected = Map.of(
                "totalAttempts", 5L,
                "totalOpportunities", 15L,
                "defects", 0L,
                "passedChecks", 15L,
                "undetermined", 0L,
                "dpmo", 0.0
        );
        when(auditService.computeQualityGateDefectRate(projectId)).thenReturn(expected);

        QualityGateController controller = new QualityGateController(auditService);
        Map<String, Object> result = controller.getDefectRate(projectId);

        assertThat(result).isEqualTo(expected);
        verify(auditService).computeQualityGateDefectRate(projectId);
    }
}
