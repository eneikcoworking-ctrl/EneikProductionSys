package com.eneik.production.services;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.PersistentWorkerPurpose;
import com.eneik.production.models.persistence.PersistentWorkerSessionEntity;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.PersistentWorkerSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure bookkeeping for the persistent-worker mechanism (see the design plan discussed with the operator,
 * 2026-07-20): the wishlist compiler and PR review fallback reuse ONE long-lived Jules session per
 * (project, purpose) across many cycles instead of spawning a fresh session/branch/PR every cycle.
 * Deliberately talks only to this table and JulesSessionEntity's status field - never to JulesApiClient
 * directly (that stays owned by JulesDispatchService, which calls back into this service for the DB
 * bookkeeping around each Jules API call it makes on a persistent worker's behalf).
 *
 * Scope boundary: this service and everything it's called from only ever handles the compiler/review-
 * fallback "system" task types. It has no knowledge of and is never called from the implementer (code-
 * writing) task dispatch/claim/quality-gate path.
 */
@Service
public class PersistentWorkerSessionService {
    private static final Logger log = LoggerFactory.getLogger(PersistentWorkerSessionService.class);

    private final PersistentWorkerSessionRepository repository;
    private final JulesSessionRepository julesSessionRepository;
    private final ObjectMapper objectMapper;

    @Value("${orchestration.persistent-worker-sessions-enabled:false}")
    private boolean enabled;

    // Bounds unbounded context growth on one Jules conversation - after this many cycles or this much
    // wall-clock age, the worker is retired (its PR/branch left as-is, never deleted automatically) and a
    // fresh one is created on the next cycle that needs one.
    @Value("${orchestration.persistent-worker-max-cycles:30}")
    private int maxCycles;

    @Value("${orchestration.persistent-worker-max-age-hours:24}")
    private int maxAgeHours;

    public PersistentWorkerSessionService(PersistentWorkerSessionRepository repository,
                                          JulesSessionRepository julesSessionRepository,
                                          ObjectMapper objectMapper) {
        this.repository = repository;
        this.julesSessionRepository = julesSessionRepository;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Transactional(readOnly = true)
    public Optional<PersistentWorkerSessionEntity> findActiveWorker(UUID projectId, PersistentWorkerPurpose purpose) {
        return repository.findByProjectIdAndPurposeAndRetiredAtIsNull(projectId, purpose);
    }

    @Transactional
    public PersistentWorkerSessionEntity registerFreshWorker(UUID projectId, PersistentWorkerPurpose purpose,
            UUID carrierTaskId, UUID julesSessionId, List<UUID> batchIds) {
        PersistentWorkerSessionEntity worker = new PersistentWorkerSessionEntity();
        worker.setProjectId(projectId);
        worker.setPurpose(purpose);
        worker.setCarrierTaskId(carrierTaskId);
        worker.setCurrentJulesSessionId(julesSessionId);
        worker.setCycleCount(1);
        worker.setCurrentBatchIds(toArrayNode(batchIds));
        worker.setLastMessageSentAt(Instant.now());
        return repository.save(worker);
    }

    /**
     * True only when the worker's session is settled at `pr_opened` (idle, no batch in flight) and it
     * hasn't hit its rotation cap - i.e. safe to send it a new follow-up message this cycle.
     */
    @Transactional(readOnly = true)
    public boolean isIdleAndFresh(PersistentWorkerSessionEntity worker) {
        if (worker.isBatchInFlight()) {
            return false;
        }
        if (needsRotation(worker)) {
            return false;
        }
        if (worker.getCurrentJulesSessionId() == null) {
            return false;
        }
        return julesSessionRepository.findById(worker.getCurrentJulesSessionId())
                .map(JulesSessionEntity::getStatus)
                .map("pr_opened"::equals)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean needsRotation(PersistentWorkerSessionEntity worker) {
        if (worker.getCycleCount() >= maxCycles) {
            return true;
        }
        return worker.getCreatedAt() != null
                && worker.getCreatedAt().isBefore(Instant.now().minus(Duration.ofHours(maxAgeHours)));
    }

    @Transactional
    public void retire(PersistentWorkerSessionEntity worker, String reason) {
        log.info("Persistent worker {} (project {}, purpose {}) retired after {} cycle(s): {}",
                worker.getId(), worker.getProjectId(), worker.getPurpose(), worker.getCycleCount(), reason);
        worker.setRetiredAt(Instant.now());
        repository.save(worker);
    }

    /**
     * Records that a new message was just sent to the worker's existing session with a new batch. Does
     * NOT touch the JulesSessionEntity's status - the caller (JulesDispatchService, which owns the actual
     * Jules API call) is responsible for flipping it to "revising" right after the message send succeeds,
     * so a failed send never leaves the worker looking busy when nothing was actually sent.
     */
    @Transactional
    public void recordBatchSent(PersistentWorkerSessionEntity worker, List<UUID> batchIds) {
        worker.setCurrentBatchIds(toArrayNode(batchIds));
        worker.setLastMessageSentAt(Instant.now());
        worker.setCycleCount(worker.getCycleCount() + 1);
        repository.save(worker);
    }

    /**
     * Called from the completion handler once a persistent worker's session reached `pr_opened` again
     * (the edge-triggered signal that Jules responded to the latest message). Returns the batch ids that
     * were in flight (empty if this edge doesn't correspond to a real in-flight batch - a stray/duplicate
     * event, the idempotency guard for this pipeline) and clears them, marking the worker idle again.
     */
    @Transactional
    public List<UUID> consumeCurrentBatch(PersistentWorkerSessionEntity worker) {
        List<UUID> ids = parseBatchIds(worker);
        worker.setCurrentBatchIds(null);
        repository.save(worker);
        return ids;
    }

    @Transactional(readOnly = true)
    public Optional<PersistentWorkerSessionEntity> findByCarrierTaskId(UUID carrierTaskId) {
        return repository.findByCarrierTaskId(carrierTaskId);
    }

    private List<UUID> parseBatchIds(PersistentWorkerSessionEntity worker) {
        if (!worker.isBatchInFlight()) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>();
        worker.getCurrentBatchIds().forEach(node -> {
            try {
                ids.add(UUID.fromString(node.asText()));
            } catch (IllegalArgumentException ignored) {
                // skip malformed entries rather than failing the whole batch
            }
        });
        return ids;
    }

    private ArrayNode toArrayNode(List<UUID> ids) {
        ArrayNode array = objectMapper.createArrayNode();
        for (UUID id : ids) {
            array.add(id.toString());
        }
        return array;
    }
}
