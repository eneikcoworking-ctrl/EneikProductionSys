package com.eneik.production.models.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_tasks")
public class AgentTaskEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "requirement_id", nullable = false)
    private UUID requirementId;

    @Column(name = "requirement_title", nullable = false)
    private String requirementTitle;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "agent_tag", nullable = false)
    private String agentTag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentTaskStatus status = AgentTaskStatus.TODO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claimed_by_id")
    private AgentAccountEntity claimedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected AgentTaskEntity() {
    }

    public AgentTaskEntity(UUID requirementId, String requirementTitle, String description, String agentTag) {
        this.requirementId = requirementId;
        this.requirementTitle = requirementTitle;
        this.description = description;
        this.agentTag = agentTag;
    }

    public void claim(AgentAccountEntity account) {
        Instant now = Instant.now();
        this.claimedBy = account;
        this.status = AgentTaskStatus.CLAIMED;
        this.claimedAt = now;
        this.updatedAt = now;
        account.setStatus("ACTIVE");
        account.setLastClaimedAt(now);
    }

    public void updateStatus(AgentTaskStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequirementId() {
        return requirementId;
    }

    public String getRequirementTitle() {
        return requirementTitle;
    }

    public String getDescription() {
        return description;
    }

    public String getAgentTag() {
        return agentTag;
    }

    public AgentTaskStatus getStatus() {
        return status;
    }

    public AgentAccountEntity getClaimedBy() {
        return claimedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
