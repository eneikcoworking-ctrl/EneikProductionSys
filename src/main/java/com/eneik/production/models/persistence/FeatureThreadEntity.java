package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * One live continuation branch per feature - not per (feature, role). A feature can involve many roles
 * (backend, frontend, design) and a role can work on many unrelated features; the unit that owns a
 * branch is the feature, and any role can pick up and continue on it as the feature's dependency chain
 * moves to the next slice. {@code lastRoleTag} is informational only (which role most recently shipped
 * code here), never part of how a thread is looked up - only project, feature, and (see
 * JulesDispatchService.dispatchInternal) the owning account gate continuation.
 */
@Entity
@Table(name = "feature_threads")
public class FeatureThreadEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "feature_id", nullable = false)
    private UUID featureId;

    @Column(name = "last_role_tag")
    private String lastRoleTag;

    @Column(name = "branch_name", nullable = false, length = 256)
    private String branchName;

    // The Jules account that actually authenticated the branch's PR - continuation is only valid when
    // this exact account is dispatched again (see JulesDispatchService.dispatchInternal).
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "last_pr_url", length = 256)
    private String lastPrUrl;

    @Column(length = 2000)
    private String summary;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public UUID getFeatureId() { return featureId; }
    public void setFeatureId(UUID featureId) { this.featureId = featureId; }
    public String getLastRoleTag() { return lastRoleTag; }
    public void setLastRoleTag(String lastRoleTag) { this.lastRoleTag = lastRoleTag; }
    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }
    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }
    public String getLastPrUrl() { return lastPrUrl; }
    public void setLastPrUrl(String lastPrUrl) { this.lastPrUrl = lastPrUrl; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
