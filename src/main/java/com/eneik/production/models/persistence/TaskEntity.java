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

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.queued;

    private String linearIssueId;

    private String julesSessionName;

    private String julesDispatchStatus;

    @Column(name = "quality_gate_passed")
    private boolean qualityGatePassed = false;

    @Column(nullable = false)
    private int priority = 0;

    @Column(name = "file_scope", columnDefinition = "TEXT")
    private String fileScope;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quality_gate_report")
    private JsonNode qualityGateReport;

    @Column(name = "depends_on")
    private UUID dependsOn;

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
    public JsonNode getQualityGateReport() { return qualityGateReport; }
    public void setQualityGateReport(JsonNode qualityGateReport) { this.qualityGateReport = qualityGateReport; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public String getFileScope() { return fileScope; }
    public void setFileScope(String fileScope) { this.fileScope = fileScope; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public UUID getDependsOn() { return dependsOn; }
    public void setDependsOn(UUID dependsOn) { this.dependsOn = dependsOn; }
}
