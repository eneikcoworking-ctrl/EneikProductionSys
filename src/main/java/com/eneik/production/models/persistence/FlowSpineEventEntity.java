package com.eneik.production.models.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "flow_spine_events")
public class FlowSpineEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "previous_state", length = 64)
    private String previousState;

    @Column(name = "current_state", nullable = false, length = 64)
    private String currentState;

    @Column(name = "next_state", length = 64)
    private String nextState;

    @Column(name = "value_status", nullable = false, length = 64)
    private String valueStatus;

    @Column(name = "bottleneck_type", length = 64)
    private String bottleneckType;

    @Column(name = "bottleneck_severity", length = 32)
    private String bottleneckSeverity;

    @Column(name = "age_in_state_minutes", nullable = false)
    private long ageInStateMinutes;

    @Column(length = 128)
    private String owner;

    @Column(name = "transition_action", columnDefinition = "CLOB")
    private String transitionAction;

    @Column(name = "evidence_hash", nullable = false, length = 64)
    private String evidenceHash;

    @Column(name = "evidence_summary", nullable = false, columnDefinition = "CLOB")
    private String evidenceSummary;

    @Column(name = "blocking_reason", columnDefinition = "CLOB")
    private String blockingReason;

    @Column(nullable = false, length = 32)
    private String mode;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public UUID getCycleId() { return cycleId; }
    public void setCycleId(UUID cycleId) { this.cycleId = cycleId; }
    public Instant getObservedAt() { return observedAt; }
    public void setObservedAt(Instant observedAt) { this.observedAt = observedAt; }
    public String getPreviousState() { return previousState; }
    public void setPreviousState(String previousState) { this.previousState = previousState; }
    public String getCurrentState() { return currentState; }
    public void setCurrentState(String currentState) { this.currentState = currentState; }
    public String getNextState() { return nextState; }
    public void setNextState(String nextState) { this.nextState = nextState; }
    public String getValueStatus() { return valueStatus; }
    public void setValueStatus(String valueStatus) { this.valueStatus = valueStatus; }
    public String getBottleneckType() { return bottleneckType; }
    public void setBottleneckType(String bottleneckType) { this.bottleneckType = bottleneckType; }
    public String getBottleneckSeverity() { return bottleneckSeverity; }
    public void setBottleneckSeverity(String bottleneckSeverity) { this.bottleneckSeverity = bottleneckSeverity; }
    public long getAgeInStateMinutes() { return ageInStateMinutes; }
    public void setAgeInStateMinutes(long ageInStateMinutes) { this.ageInStateMinutes = ageInStateMinutes; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public String getTransitionAction() { return transitionAction; }
    public void setTransitionAction(String transitionAction) { this.transitionAction = transitionAction; }
    public String getEvidenceHash() { return evidenceHash; }
    public void setEvidenceHash(String evidenceHash) { this.evidenceHash = evidenceHash; }
    public String getEvidenceSummary() { return evidenceSummary; }
    public void setEvidenceSummary(String evidenceSummary) { this.evidenceSummary = evidenceSummary; }
    public String getBlockingReason() { return blockingReason; }
    public void setBlockingReason(String blockingReason) { this.blockingReason = blockingReason; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
}
