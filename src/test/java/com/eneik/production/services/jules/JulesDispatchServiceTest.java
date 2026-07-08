package com.eneik.production.services.jules;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class JulesDispatchServiceTest {

    private JulesApiClient julesApiClient;
    private JulesSessionRepository julesSessionRepository;
    private com.eneik.production.repositories.AccountRepository accountRepository;
    private TaskRepository taskRepository;
    private com.eneik.production.services.ClaimService claimService;
    private com.eneik.production.services.RoleCapabilityLoader roleCapabilityLoader;
    private JulesDispatchService julesDispatchService;

    @BeforeEach
    void setUp() {
        julesApiClient = mock(JulesApiClient.class);
        julesSessionRepository = mock(JulesSessionRepository.class);
        accountRepository = mock(com.eneik.production.repositories.AccountRepository.class);
        taskRepository = mock(TaskRepository.class);
        claimService = mock(com.eneik.production.services.ClaimService.class);
        roleCapabilityLoader = mock(com.eneik.production.services.RoleCapabilityLoader.class);
        com.eneik.production.services.monitor.PrReviewPipelineService prReviewPipelineService = mock(com.eneik.production.services.monitor.PrReviewPipelineService.class);
        com.eneik.production.services.MLPredictionServiceClient mlPredictionServiceClient = mock(com.eneik.production.services.MLPredictionServiceClient.class);
        com.eneik.production.repositories.RoleRepository roleRepository = mock(com.eneik.production.repositories.RoleRepository.class);
        com.eneik.production.repositories.TaskConflictRepository taskConflictRepository = mock(com.eneik.production.repositories.TaskConflictRepository.class);
        julesDispatchService = new JulesDispatchService(
            julesApiClient, julesSessionRepository, accountRepository, taskRepository, taskConflictRepository, claimService, roleCapabilityLoader,
            prReviewPipelineService, mlPredictionServiceClient, roleRepository, "prefix/"
        );
        ReflectionTestUtils.setField(julesDispatchService, "stuckThresholdMinutes", 30);
    }

    @Test
    void testMapExternalStatus() {
        assertEquals("queued", julesDispatchService.mapExternalStatus("QUEUED"));
        assertEquals("running", julesDispatchService.mapExternalStatus("RUNNING"));
        assertEquals("pr_opened", julesDispatchService.mapExternalStatus("SUCCEEDED"));
        assertEquals("failed", julesDispatchService.mapExternalStatus("FAILED"));
        assertEquals("failed", julesDispatchService.mapExternalStatus("CANCELLED"));
        assertEquals("running", julesDispatchService.mapExternalStatus("UNKNOWN"));
        assertEquals("running", julesDispatchService.mapExternalStatus(null));
    }

    @Test
    void testDetectStuck() {
        // When
        julesDispatchService.detectStuck();

        // Then
        verify(claimService, times(1)).detectStuckSessions(30);
    }
}
