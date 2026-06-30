package com.eneik.production.models.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "agent_accounts")
public class AgentAccountEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_code", nullable = false, unique = true)
    private String accountCode;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "focus_tags", nullable = false, length = 500)
    private String focusTags;

    @Column(nullable = false)
    private String status = "IDLE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_claimed_at")
    private Instant lastClaimedAt;

    protected AgentAccountEntity() {
    }

    public AgentAccountEntity(String accountCode, String displayName, String focusTags) {
        this.accountCode = accountCode;
        this.displayName = displayName;
        this.focusTags = focusTags;
    }

    public boolean canHandle(String agentTag) {
        return getFocusTagSet().contains(agentTag);
    }

    public Set<String> getFocusTagSet() {
        return Arrays.stream(focusTags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .collect(Collectors.toSet());
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

    public String getFocusTags() {
        return focusTags;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastClaimedAt() {
        return lastClaimedAt;
    }

    public void setLastClaimedAt(Instant lastClaimedAt) {
        this.lastClaimedAt = lastClaimedAt;
    }
}
