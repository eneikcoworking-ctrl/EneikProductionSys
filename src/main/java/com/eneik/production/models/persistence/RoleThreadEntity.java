package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "role_threads")
public class RoleThreadEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "role_tag", nullable = false)
    private String roleTag;

    @Column(name = "branch_name", nullable = false, length = 256)
    private String branchName;

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
    public String getRoleTag() { return roleTag; }
    public void setRoleTag(String roleTag) { this.roleTag = roleTag; }
    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }
    public String getLastPrUrl() { return lastPrUrl; }
    public void setLastPrUrl(String lastPrUrl) { this.lastPrUrl = lastPrUrl; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
