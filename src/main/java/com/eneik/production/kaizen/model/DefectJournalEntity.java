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

    // 2026-08-01: subgroup for the u-chart layer (ProcessControlService) - an эпик's own, bounded task set,
    // never a calendar-date bucket or a cross-project aggregate (see ENGINEERING_INVARIANTS_CHARTER "one
    // project = one closed experimental run"). Nullable: not every defect (e.g. a TOC buffer breach) is
    // attributable to a single эпик.
    @Column(name = "feature_id")
    private UUID featureId;

    // 2026-08-01: links this defect to a numbered pattern in docs/ENGINEERING_INVARIANTS_CHARTER.md (1-12),
    // or null when the root cause hasn't been triaged yet - what makes Pareto analysis by CAUSE possible
    // instead of only by which subsystem happened to notice the symptom.
    @Column(name = "root_cause_pattern_id")
    private Integer rootCausePatternId;

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
        this(projectId, null, null, severity, category, sourceComponent, defectType, description, metricValue);
    }

    public DefectJournalEntity(UUID projectId, UUID featureId, Integer rootCausePatternId, String severity,
                               String category, String sourceComponent, String defectType, String description,
                               Double metricValue) {
        this.projectId = projectId;
        this.featureId = featureId;
        this.rootCausePatternId = rootCausePatternId;
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

    public UUID getFeatureId() { return featureId; }
    public void setFeatureId(UUID featureId) { this.featureId = featureId; }

    public Integer getRootCausePatternId() { return rootCausePatternId; }
    public void setRootCausePatternId(Integer rootCausePatternId) { this.rootCausePatternId = rootCausePatternId; }

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
