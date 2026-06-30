package com.eneik.production.dto.agents;

import com.eneik.production.models.persistence.AgentAccountEntity;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public class AgentAccountDTO {
    private final UUID id;
    private final String accountCode;
    private final String displayName;
    private final Set<String> focusTags;
    private final String status;
    private final Instant createdAt;
    private final Instant lastClaimedAt;

    public AgentAccountDTO(AgentAccountEntity account) {
        this.id = account.getId();
        this.accountCode = account.getAccountCode();
        this.displayName = account.getDisplayName();
        this.focusTags = account.getFocusTagSet();
        this.status = account.getStatus();
        this.createdAt = account.getCreatedAt();
        this.lastClaimedAt = account.getLastClaimedAt();
    }

    public UUID getId() {
        return id;
    }

    public String getAccountCode() {
        return accountCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<String> getFocusTags() {
        return focusTags;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastClaimedAt() {
        return lastClaimedAt;
    }
}
