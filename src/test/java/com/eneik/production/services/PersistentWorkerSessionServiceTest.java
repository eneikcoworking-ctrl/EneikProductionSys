package com.eneik.production.services;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.PersistentWorkerPurpose;
import com.eneik.production.models.persistence.PersistentWorkerSessionEntity;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.PersistentWorkerSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersistentWorkerSessionServiceTest {

    private PersistentWorkerSessionRepository repository;
    private JulesSessionRepository julesSessionRepository;
    private ObjectMapper objectMapper;
    private PersistentWorkerSessionService service;

    @BeforeEach
    void setUp() {
        repository = mock(PersistentWorkerSessionRepository.class);
        julesSessionRepository = mock(JulesSessionRepository.class);
        objectMapper = new ObjectMapper();
        service = new PersistentWorkerSessionService(repository, julesSessionRepository, objectMapper);

        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "maxCycles", 30);
        ReflectionTestUtils.setField(service, "maxAgeHours", 24);

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void isEnabledReturnsConfiguredValue() {
        assertTrue(service.isEnabled());
    }

    @Test
    void findActiveWorkerDelegatesToRepository() {
        UUID projectId = UUID.randomUUID();
        PersistentWorkerPurpose purpose = PersistentWorkerPurpose.WISHLIST_COMPILER;
        PersistentWorkerSessionEntity entity = new PersistentWorkerSessionEntity();
        when(repository.findByProjectIdAndPurposeAndRetiredAtIsNull(projectId, purpose))
                .thenReturn(Optional.of(entity));

        Optional<PersistentWorkerSessionEntity> result = service.findActiveWorker(projectId, purpose);

        assertTrue(result.isPresent());
        assertEquals(entity, result.get());
    }

    @Test
    void registerFreshWorkerInitializesCycleCountAndBatch() {
        UUID projectId = UUID.randomUUID();
        PersistentWorkerPurpose purpose = PersistentWorkerPurpose.REVIEW_FALLBACK;
        UUID carrierTaskId = UUID.randomUUID();
        UUID julesSessionId = UUID.randomUUID();
        UUID batchId1 = UUID.randomUUID();
        UUID batchId2 = UUID.randomUUID();

        PersistentWorkerSessionEntity worker = service.registerFreshWorker(
                projectId, purpose, carrierTaskId, julesSessionId, List.of(batchId1, batchId2));

        assertNotNull(worker);
        assertEquals(projectId, worker.getProjectId());
        assertEquals(purpose, worker.getPurpose());
        assertEquals(carrierTaskId, worker.getCarrierTaskId());
        assertEquals(julesSessionId, worker.getCurrentJulesSessionId());
        assertEquals(1, worker.getCycleCount());
        assertNotNull(worker.getCurrentBatchIds());
        assertTrue(worker.isBatchInFlight());
        assertNotNull(worker.getLastMessageSentAt());
        verify(repository, times(1)).save(worker);
    }

    @Test
    void retireSetsRetiredAtTimestamp() {
        PersistentWorkerSessionEntity worker = new PersistentWorkerSessionEntity();
        assertNull(worker.getRetiredAt());

        service.retire(worker, "test reason");

        assertNotNull(worker.getRetiredAt());
        verify(repository, times(1)).save(worker);
    }

    @Test
    void recordBatchSentIncrementsCycleCountAndUpdatesBatch() {
        PersistentWorkerSessionEntity worker = new PersistentWorkerSessionEntity();
        worker.setCycleCount(5);
        UUID b1 = UUID.randomUUID();

        service.recordBatchSent(worker, List.of(b1));

        assertEquals(6, worker.getCycleCount());
        assertTrue(worker.isBatchInFlight());
        assertNotNull(worker.getLastMessageSentAt());
        verify(repository, times(1)).save(worker);
    }

    @Test
    void consumeCurrentBatchClearsBatchAndReturnsIds() {
        PersistentWorkerSessionEntity worker = new PersistentWorkerSessionEntity();
        UUID b1 = UUID.randomUUID();
        UUID b2 = UUID.randomUUID();
        service.recordBatchSent(worker, List.of(b1, b2));

        List<UUID> consumed = service.consumeCurrentBatch(worker);

        assertEquals(List.of(b1, b2), consumed);
        assertFalse(worker.isBatchInFlight());
        assertNull(worker.getCurrentBatchIds());
        verify(repository, times(2)).save(worker);
    }

    @Test
    void peekCurrentBatchDoesNotClearBatchIds() {
        PersistentWorkerSessionEntity worker = new PersistentWorkerSessionEntity();
        UUID b1 = UUID.randomUUID();
        service.recordBatchSent(worker, List.of(b1));

        List<UUID> peeked = service.peekCurrentBatch(worker);

        assertEquals(List.of(b1), peeked);
        assertTrue(worker.isBatchInFlight(), "peekCurrentBatch must leave batch intact");
        assertNotNull(worker.getCurrentBatchIds());
    }

    @Test
    void findByCarrierTaskIdDelegatesToRepository() {
        UUID carrierTaskId = UUID.randomUUID();
        PersistentWorkerSessionEntity worker = new PersistentWorkerSessionEntity();
        when(repository.findByCarrierTaskId(carrierTaskId)).thenReturn(Optional.of(worker));

        Optional<PersistentWorkerSessionEntity> result = service.findByCarrierTaskId(carrierTaskId);

        assertTrue(result.isPresent());
        assertEquals(worker, result.get());
    }
}
