package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "code_integrity_findings")
public class CodeIntegrityFindingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    // Nullable: set only when the finding's cited prNumber resolved to a real feature via
    // PrReviewEntity.prNumber -> JulesSessionEntity.taskId -> TaskEntity.featureId. Left null when Jules
    // cited no PR, or the cited number didn't resolve - the finding is still recorded, just unattributed,
    // rather than silently dropped (see FalsificationCycleService.applyAuditViolations).
    @Column(name = "feature_id")
    private UUID featureId;

    @Column(name = "falsification_run_id", nullable = false)
    private UUID falsificationRunId;

    @Column(name = "finding_type", nullable = false, length = 32)
    private String findingType;

    @Column(name = "pr_number")
    private Integer prNumber;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "found_at", nullable = false, updatable = false)
    private Instant foundAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public UUID getFeatureId() { return featureId; }
    public void setFeatureId(UUID featureId) { this.featureId = featureId; }

    public UUID getFalsificationRunId() { return falsificationRunId; }
    public void setFalsificationRunId(UUID falsificationRunId) { this.falsificationRunId = falsificationRunId; }

    public String getFindingType() { return findingType; }
    public void setFindingType(String findingType) { this.findingType = findingType; }

    public Integer getPrNumber() { return prNumber; }
    public void setPrNumber(Integer prNumber) { this.prNumber = prNumber; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Instant getFoundAt() { return foundAt; }
    public void setFoundAt(Instant foundAt) { this.foundAt = foundAt; }
}
