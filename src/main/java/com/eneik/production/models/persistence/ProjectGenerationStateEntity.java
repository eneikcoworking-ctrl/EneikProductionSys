package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_generation_state")
public class ProjectGenerationStateEntity {
    @Id
    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "is_generation_stopped", nullable = false)
    private boolean generationStopped = false;

    @Column(name = "stopped_at")
    private Instant stoppedAt;

    @Column(name = "last_orchestrated_at")
    private Instant lastOrchestratedAt;

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public boolean isGenerationStopped() {
        return generationStopped;
    }

    public void setGenerationStopped(boolean generationStopped) {
        this.generationStopped = generationStopped;
    }

    public Instant getStoppedAt() {
        return stoppedAt;
    }

    public void setStoppedAt(Instant stoppedAt) {
        this.stoppedAt = stoppedAt;
    }

    public Instant getLastOrchestratedAt() {
        return lastOrchestratedAt;
    }

    public void setLastOrchestratedAt(Instant lastOrchestratedAt) {
        this.lastOrchestratedAt = lastOrchestratedAt;
    }
}
