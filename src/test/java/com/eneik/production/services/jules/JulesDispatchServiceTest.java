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
    private com.eneik.production.services.RoleCapabilityLoader roleCapabilityLoader;
    private JulesDispatchService julesDispatchService;

    @BeforeEach
    void setUp() {
        julesApiClient = mock(JulesApiClient.class);
        julesSessionRepository = mock(JulesSessionRepository.class);
        accountRepository = mock(com.eneik.production.repositories.AccountRepository.class);
        taskRepository = mock(TaskRepository.class);
        roleCapabilityLoader = mock(com.eneik.production.services.RoleCapabilityLoader.class);
        julesDispatchService = new JulesDispatchService(julesApiClient, julesSessionRepository, accountRepository, taskRepository, roleCapabilityLoader, "prefix/");
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
        // Given
        Instant now = Instant.now();
        Instant thirtyOneMinutesAgo = now.minus(31, ChronoUnit.MINUTES);
        Instant twentyNineMinutesAgo = now.minus(29, ChronoUnit.MINUTES);

        JulesSessionEntity stuckSession = new JulesSessionEntity();
        stuckSession.setId(UUID.randomUUID());
        stuckSession.setStatus("running");
        stuckSession.setUpdatedAt(thirtyOneMinutesAgo);

        JulesSessionEntity activeSession = new JulesSessionEntity();
        activeSession.setId(UUID.randomUUID());
        activeSession.setStatus("running");
        activeSession.setUpdatedAt(twentyNineMinutesAgo);

        when(julesSessionRepository.findByStatus("running")).thenReturn(List.of(stuckSession, activeSession));

        // When
        julesDispatchService.detectStuck();

        // Then
        assertEquals("stuck", stuckSession.getStatus());
        assertEquals("running", activeSession.getStatus());
        verify(julesSessionRepository, times(1)).save(stuckSession);
        verify(julesSessionRepository, never()).save(activeSession);
    }
}
