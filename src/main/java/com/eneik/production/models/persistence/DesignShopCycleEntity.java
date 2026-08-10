package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * One row per project: tracks the design shop's edge-triggered readiness state (see
 * DesignShopOrchestrationService) so a project that has been "ready to assemble" for many ticks in a
 * row only starts one design cycle, not one per tick - while still allowing a brand new cycle once
 * readiness genuinely drops and rises again (e.g. after a falsification round adds new features).
 */
@Entity
@Table(name = "design_shop_cycles")
public class DesignShopCycleEntity {
    public static final String STAGE_IDLE = "IDLE";
    public static final String STAGE_AWAITING_REVIEW = "AWAITING_REVIEW";
    public static final String STAGE_DONE = "DONE";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_id", nullable = false, unique = true)
    private UUID projectId;

    @Column(name = "last_was_ready", nullable = false)
    private boolean lastWasReady;

    @Column(name = "stage", nullable = false, length = 32)
    private String stage = STAGE_IDLE;

    @Column(name = "draft_path", length = 512)
    private String draftPath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public boolean isLastWasReady() { return lastWasReady; }
    public void setLastWasReady(boolean lastWasReady) { this.lastWasReady = lastWasReady; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    public String getDraftPath() { return draftPath; }
    public void setDraftPath(String draftPath) { this.draftPath = draftPath; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
