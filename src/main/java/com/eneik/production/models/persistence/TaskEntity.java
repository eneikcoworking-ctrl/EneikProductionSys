package com.eneik.production.models.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tasks")
public class TaskEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "project_id")
    private ProjectEntity project;

    @ManyToOne
    @JoinColumn(name = "tag", nullable = false)
    private RoleEntity role;

    @Column(nullable = false)
    private String description;

    @Column(length = 80)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.queued;

    private String linearIssueId;

    private String julesSessionName;

    @Column(length = 2048)
    private String julesDispatchStatus;

    @Column(name = "quality_gate_passed")
    private boolean qualityGatePassed = false;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(nullable = false)
    private int priority = 0;

    @Column(name = "file_scope", columnDefinition = "TEXT")
    private String fileScope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "depends_on")
    private TaskEntity dependsOn;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quality_gate_report")
    private JsonNode qualityGateReport;

    @Column(name = "cynefin_domain")
    private String cynefinDomain;

    // Real FK mirror of payload.source_wishlist_id (JSON) - see V40 migration. Lets readiness/phase
    // gating join tasks back to their originating client wishlist item without a JSON text search.
    @Column(name = "source_wishlist_id")
    private UUID sourceWishlistId;

    // The feature this task belongs to - always populated at creation (TechnicalLeadCompiler.
    // createAndSaveTask via FeatureService), either inherited from the wishlist that produced it or
    // minted fresh if nothing set one. Scopes FeatureThreadEntity continuation so unrelated features never
    // share a branch just because the same role worked on both.
    @Column(name = "feature_id")
    private UUID featureId;

    // 2026-08-04 (3-layer Factory/Delivery/Product model): immutable lineage - set once, at the same
    // moment featureId is first assigned at task creation, and never touched again by any of the several
    // places that freely rewrite featureId afterward (admin repair endpoints, follow-up-task inheritance,
    // closeout wiring). featureId still means "what this task currently counts toward"; this field means
    // "where it actually came from" - Layer 2 delivery history is grouped by this, not by featureId.
    @Column(name = "origin_feature_id")
    private UUID originFeatureId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_context")
    private TargetContext targetContext = TargetContext.PRODUCT_CODEBASE;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ProjectEntity getProject() { return project; }
    public void setProject(ProjectEntity project) { this.project = project; }
    public RoleEntity getRole() { return role; }
    public void setRole(RoleEntity role) { this.role = role; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public JsonNode getPayload() { return payload; }
    public void setPayload(JsonNode payload) { this.payload = payload; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public String getLinearIssueId() { return linearIssueId; }
    public void setLinearIssueId(String linearIssueId) { this.linearIssueId = linearIssueId; }
    public String getJulesSessionName() { return julesSessionName; }
    public void setJulesSessionName(String julesSessionName) { this.julesSessionName = julesSessionName; }
    public String getJulesDispatchStatus() { return julesDispatchStatus; }
    public void setJulesDispatchStatus(String julesDispatchStatus) { this.julesDispatchStatus = julesDispatchStatus; }
    public boolean isQualityGatePassed() { return qualityGatePassed; }
    public void setQualityGatePassed(boolean qualityGatePassed) { this.qualityGatePassed = qualityGatePassed; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    /**
     * Was this task verified for DELIVERY, as opposed to verified for being well specified?
     *
     * `qualityGatePassed` cannot answer that. GateOrchestrator has two entry points writing the same
     * boolean into the same field: runTaskSpecGate at task CREATION, and runQualityGate when an
     * implementer FINISHES. Both writes are true statements, which is what makes the substitution
     * invisible - it cannot be caught by asking whether the value is correct, only by asking what it is
     * about (ACP-102). Measured on test-forty-ninth: every task in the project has a gate log written
     * 2-5 seconds after creation, so a `done` task with the flag raised may never have been looked at
     * after its work was produced.
     *
     * The subject is already recorded - GateOrchestrator writes the stages into the report - and the
     * 2026-08-18 change that added it said plainly that no reader had been changed to ask. This is that
     * reader. A verdict about specification is not evidence about delivery, and by the Evidence Algebra
     * the absence of a check is 0, never 5.
     */
    public boolean isVerifiedForDelivery() {
        if (qualityGateReport == null || !qualityGatePassed) {
            return false;
        }
        JsonNode stages = qualityGateReport.path("stages");
        if (!stages.isArray()) {
            return false;
        }
        for (JsonNode stage : stages) {
            if ("IMPLEMENTATION_RESULT".equals(stage.asText(""))) {
                return true;
            }
        }
        return false;
    }

    /** True when nothing has ever asked this task's delivery question - neither pass nor fail. */
    public boolean isDeliveryVerificationAbsent() {
        if (qualityGateReport == null) {
            return true;
        }
        JsonNode stages = qualityGateReport.path("stages");
        if (!stages.isArray()) {
            return true;
        }
        for (JsonNode stage : stages) {
            if ("IMPLEMENTATION_RESULT".equals(stage.asText(""))) {
                return false;
            }
        }
        return true;
    }

    public JsonNode getQualityGateReport() { return qualityGateReport; }
    public void setQualityGateReport(JsonNode qualityGateReport) { this.qualityGateReport = qualityGateReport; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public String getFileScope() { return fileScope; }
    public void setFileScope(String fileScope) { this.fileScope = fileScope; }
    public TaskEntity getDependsOn() { return dependsOn; }
    public void setDependsOn(TaskEntity dependsOn) { this.dependsOn = dependsOn; }
    public String getCynefinDomain() { return cynefinDomain; }
    public void setCynefinDomain(String cynefinDomain) { this.cynefinDomain = cynefinDomain; }
    public UUID getSourceWishlistId() { return sourceWishlistId; }
    public void setSourceWishlistId(UUID sourceWishlistId) { this.sourceWishlistId = sourceWishlistId; }
    public UUID getFeatureId() { return featureId; }
    public void setFeatureId(UUID featureId) { this.featureId = featureId; }
    public UUID getOriginFeatureId() { return originFeatureId; }
    public void setOriginFeatureId(UUID originFeatureId) { this.originFeatureId = originFeatureId; }
    public TargetContext getTargetContext() { return targetContext == null ? TargetContext.PRODUCT_CODEBASE : targetContext; }
    public void setTargetContext(TargetContext targetContext) { this.targetContext = targetContext; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
