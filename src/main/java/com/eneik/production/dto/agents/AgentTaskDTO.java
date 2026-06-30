package com.eneik.production.dto.agents;

import com.eneik.production.models.persistence.AgentAccountEntity;
import com.eneik.production.models.persistence.AgentTaskEntity;
import com.eneik.production.models.persistence.AgentTaskStatus;

import java.time.Instant;
import java.util.UUID;

public class AgentTaskDTO {
    private final UUID id;
    private final UUID requirementId;
    private final String requirementTitle;
    private final String description;
    private final String agentTag;
    private final AgentTaskStatus status;
    private final String claimedByAccountCode;
    private final String claimedByDisplayName;
    private final Instant createdAt;
    private final Instant claimedAt;
    private final Instant updatedAt;

    public AgentTaskDTO(AgentTaskEntity task) {
        AgentAccountEntity claimedBy = task.getClaimedBy();
        this.id = task.getId();
        this.requirementId = task.getRequirementId();
        this.requirementTitle = task.getRequirementTitle();
        this.description = task.getDescription();
        this.agentTag = task.getAgentTag();
        this.status = task.getStatus();
        this.claimedByAccountCode = claimedBy == null ? null : claimedBy.getAccountCode();
        this.claimedByDisplayName = claimedBy == null ? null : claimedBy.getDisplayName();
        this.createdAt = task.getCreatedAt();
        this.claimedAt = task.getClaimedAt();
        this.updatedAt = task.getUpdatedAt();
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

    public String getClaimedByAccountCode() {
        return claimedByAccountCode;
    }

    public String getClaimedByDisplayName() {
        return claimedByDisplayName;
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
