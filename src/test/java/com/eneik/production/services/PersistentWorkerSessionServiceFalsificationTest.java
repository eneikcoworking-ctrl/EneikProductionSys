package com.eneik.production.services;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.PersistentWorkerSessionEntity;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.PersistentWorkerSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.time.Duration;
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
import static org.mockito.Mockito.when;

/**
 * Stage 4 Popperian falsification harness for PersistentWorkerSessionService.
 *
 * Grounded in:
 * 1. AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP [D004, Varzi]: aggregate ownership of persistent workers;
 *    service is pure bookkeeping and has strictly ZERO dependencies or calls to JulesApiClient transport.
 * 2. DZHOZEF_RAZ_01_PROHIBITION_AS_CODE [D006, Raz]: normative prohibition as executable denial;
 *    isIdleAndFresh strictly prohibits work when batch is in flight, rotation cap is reached, or session status != pr_opened.
 * 3. ELVIN_GOLDMAN_01_RELIABILITY_CHAIN [D010, Goldman]: reliabilism of epistemic processes;
 *    peekCurrentBatch never mutates in-flight batch data on worker crash; malformed JSON UUIDs are safely filtered.
 */
class PersistentWorkerSessionServiceFalsificationTest {

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

    // --- AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP (D004) ---

    @Test
    @DisplayName("Varzi: PersistentWorkerSessionService has strictly zero transport dependencies (no JulesApiClient)")
    void falsifyVarziPartWholeOwnership_strictlyZeroTransportDependencies() {
        // Form boundary: the service must talk only to DB repositories and never hold a reference to JulesApiClient
        for (Field field : PersistentWorkerSessionService.class.getDeclaredFields()) {
            String typeName = field.getType().getName();
            assertFalse(typeName.contains("JulesApiClient"),
                    "Varzi aggregate ownership violated: PersistentWorkerSessionService must not depend on JulesApiClient");
        }
    }

    // --- DZHOZEF_RAZ_01_PROHIBITION_AS_CODE (D006) ---

    @Test
    @DisplayName("Raz: isIdleAndFresh strictly prohibits sending work when a batch is already in flight")
    void falsifyRazProhibition_isIdleAndFreshProhibitsWhenBatchIsInFlight() {
        PersistentWorkerSessionEntity worker = new PersistentWorkerSessionEntity();
        worker.setCycleCount(1);
        worker.setCreatedAt(Instant.now());
        UUID julesSessionId = UUID.randomUUID();
        worker.setCurrentJulesSessionId(julesSessionId);

        JulesSessionEntity julesSession = new JulesSessionEntity();
        julesSession.setId(julesSessionId);
        julesSession.setStatus("pr_opened");
        when(julesSessionRepository.findById(julesSessionId)).thenReturn(Optional.of(julesSession));

        // When batch is in flight (currentBatchIds has elements)
        service.recordBatchSent(worker, List.of(UUID.randomUUID()));
        assertTrue(worker.isBatchInFlight());

        // Raz prohibition: sending another message must be forbidden
        assertFalse(service.isIdleAndFresh(worker),
                "Raz prohibition violated: isIdleAndFresh must return false when batch is in flight");
    }

    @Test
    @DisplayName("Raz: isIdleAndFresh strictly prohibits work when cycle count reaches rotation cap")
    void falsifyRazProhibition_isIdleAndFreshProhibitsWhenCycleCountReachesCap() {
        PersistentWorkerSessionEntity worker = new PersistentWorkerSessionEntity();
        worker.setCycleCount(30); // maxCycles is 30
        worker.setCreatedAt(Instant.now());
        UUID julesSessionId = UUID.randomUUID();
        worker.setCurrentJulesSessionId(julesSessionId);

        JulesSessionEntity julesSession = new JulesSessionEntity();
        julesSession.setId(julesSessionId);
        julesSession.setStatus("pr_opened");
        when(julesSessionRepository.findById(julesSessionId)).thenReturn(Optional.of(julesSession));

        assertTrue(service.needsRotation(worker), "Cycle cap must trigger needsRotation");
        assertFalse(service.isIdleAndFresh(worker),
                "Raz prohibition violated: worker at cycle cap must not be fresh");
    }

    @Test
    @DisplayName("Raz: isIdleAndFresh strictly prohibits work when worker age exceeds maxAgeHours")
    void falsifyRazProhibition_isIdleAndFreshProhibitsWhenAgeExceedsLimit() {
        PersistentWorkerSessionEntity worker = new PersistentWorkerSessionEntity();
        worker.setCycleCount(5);
        worker.setCreatedAt(Instant.now().minus(Duration.ofHours(25))); // maxAgeHours is 24
        UUID julesSessionId = UUID.randomUUID();
        worker.setCurrentJulesSessionId(julesSessionId);

        JulesSessionEntity julesSession = new JulesSessionEntity();
        julesSession.setId(julesSessionId);
        julesSession.setStatus("pr_opened");
        when(julesSessionRepository.findById(julesSessionId)).thenReturn(Optional.of(julesSession));

        assertTrue(service.needsRotation(worker), "Age limit must trigger needsRotation");
        assertFalse(service.isIdleAndFresh(worker),
                "Raz prohibition violated: aged worker must not be fresh");
    }

    @Test
    @DisplayName("Raz: isIdleAndFresh strictly prohibits work when session status is not pr_opened")
    void falsifyRazProhibition_isIdleAndFreshProhibitsWhenSessionNotPrOpened() {
        PersistentWorkerSessionEntity worker = new PersistentWorkerSessionEntity();
        worker.setCycleCount(5);
        worker.setCreatedAt(Instant.now());
        UUID julesSessionId = UUID.randomUUID();
        worker.setCurrentJulesSessionId(julesSessionId);

        JulesSessionEntity julesSession = new JulesSessionEntity();
        julesSession.setId(julesSessionId);
        julesSession.setStatus("running"); // Busy with Jules
        when(julesSessionRepository.findById(julesSessionId)).thenReturn(Optional.of(julesSession));

        assertFalse(service.isIdleAndFresh(worker),
                "Raz prohibition violated: running session is not settled at pr_opened");
    }

    // --- ELVIN_GOLDMAN_01_RELIABILITY_CHAIN (D010) ---

    @Test
    @DisplayName("Goldman: peekCurrentBatch reliably preserves in-flight batch data on crash/retirement")
    void falsifyGoldmanReliabilityChain_peekDoesNotMutateBatchIds() {
        PersistentWorkerSessionEntity worker = new PersistentWorkerSessionEntity();
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        service.recordBatchSent(worker, List.of(id1, id2));

        // Audit before peek
        assertTrue(worker.isBatchInFlight());
        assertEquals(2, worker.getCurrentBatchIds().size());

        // Peek operation (e.g. during carrier failure recovery)
        List<UUID> peeked = service.peekCurrentBatch(worker);
        assertEquals(List.of(id1, id2), peeked);

        // Audit after peek: batch MUST NOT be cleared
        assertTrue(worker.isBatchInFlight(), "Reliable process violated: peekCurrentBatch must not mutate batch state");
        assertNotNull(worker.getCurrentBatchIds());
    }

    @Test
    @DisplayName("Goldman: parseBatchIds safely skips malformed UUIDs without throwing exception")
    void falsifyGoldmanReliabilityChain_malformedUuidSafelySkippedWithoutException() {
        PersistentWorkerSessionEntity worker = new PersistentWorkerSessionEntity();
        UUID validId = UUID.randomUUID();

        ArrayNode arrayNode = objectMapper.createArrayNode();
        arrayNode.add("not-a-valid-uuid");
        arrayNode.add(validId.toString());
        arrayNode.add("another-malformed-string");
        worker.setCurrentBatchIds(arrayNode);

        List<UUID> parsed = service.peekCurrentBatch(worker);

        assertEquals(1, parsed.size(), "Only the valid UUID must be extracted");
        assertEquals(validId, parsed.get(0));
    }
}
