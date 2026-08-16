package com.eneik.production.models.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per (project, purpose) - see ProjectFlowService/JulesDispatchService persistent-worker dispatch
 * logic. Tracks the single long-lived Jules session reused across many wishlist-compiler or PR-review-
 * fallback cycles instead of spawning a fresh session/branch/PR every cycle. carrierTaskId is the one
 * TaskEntity the session stays bound to (jules_sessions.task_id is NOT NULL, so a durable task row is
 * required); currentBatchIds is empty when the worker is idle and holds the in-flight batch's wishlist/
 * task ids while a message is being processed - see JulesDispatchService.completePersistentWorkerCycle.
 */
@Entity
@Table(name = "persistent_worker_sessions")
public class PersistentWorkerSessionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PersistentWorkerPurpose purpose;

    @Column(name = "carrier_task_id")
    private UUID carrierTaskId;

    @Column(name = "current_jules_session_id")
    private UUID currentJulesSessionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "current_batch_ids")
    private JsonNode currentBatchIds;

    @Column(name = "cycle_count", nullable = false)
    private int cycleCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_message_sent_at")
    private Instant lastMessageSentAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public PersistentWorkerPurpose getPurpose() { return purpose; }
    public void setPurpose(PersistentWorkerPurpose purpose) { this.purpose = purpose; }
    public UUID getCarrierTaskId() { return carrierTaskId; }
    public void setCarrierTaskId(UUID carrierTaskId) { this.carrierTaskId = carrierTaskId; }
    public UUID getCurrentJulesSessionId() { return currentJulesSessionId; }
    public void setCurrentJulesSessionId(UUID currentJulesSessionId) { this.currentJulesSessionId = currentJulesSessionId; }
    public JsonNode getCurrentBatchIds() { return currentBatchIds; }
    public void setCurrentBatchIds(JsonNode currentBatchIds) { this.currentBatchIds = currentBatchIds; }
    public int getCycleCount() { return cycleCount; }
    public void setCycleCount(int cycleCount) { this.cycleCount = cycleCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastMessageSentAt() { return lastMessageSentAt; }
    public void setLastMessageSentAt(Instant lastMessageSentAt) { this.lastMessageSentAt = lastMessageSentAt; }
    public Instant getRetiredAt() { return retiredAt; }
    public void setRetiredAt(Instant retiredAt) { this.retiredAt = retiredAt; }

    public boolean isBatchInFlight() {
        return currentBatchIds != null && currentBatchIds.isArray() && !currentBatchIds.isEmpty();
    }
}
