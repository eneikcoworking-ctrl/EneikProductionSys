package com.eneik.production.kaizen.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "defect_journal")
public class DefectJournalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "severity", nullable = false, length = 32)
    private String severity;

    @Column(name = "category", nullable = false, length = 64)
    private String category;

    @Column(name = "source_component", nullable = false, length = 128)
    private String sourceComponent;

    @Column(name = "defect_type", nullable = false, length = 128)
    private String defectType;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "metric_value")
    private Double metricValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public DefectJournalEntity() {}

    public DefectJournalEntity(UUID projectId, String severity, String category, String sourceComponent,
                               String defectType, String description, Double metricValue) {
        this.projectId = projectId;
        this.severity = severity;
        this.category = category;
        this.sourceComponent = sourceComponent;
        this.defectType = defectType;
        this.description = description;
        this.metricValue = metricValue;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSourceComponent() { return sourceComponent; }
    public void setSourceComponent(String sourceComponent) { this.sourceComponent = sourceComponent; }

    public String getDefectType() { return defectType; }
    public void setDefectType(String defectType) { this.defectType = defectType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getMetricValue() { return metricValue; }
    public void setMetricValue(Double metricValue) { this.metricValue = metricValue; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
