package com.eneik.production.models.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** One evidence node's accept/reject outcome within one CoherenceRunEntity. */
@Entity
@Table(name = "coherence_run_node_results")
public class CoherenceRunNodeResultEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "coherence_run_id", nullable = false)
    private UUID coherenceRunId;

    @Column(name = "evidence_node_id", nullable = false)
    private UUID evidenceNodeId;

    @Column(nullable = false)
    private boolean accepted;

    @Column(name = "final_activation", nullable = false)
    private double finalActivation;

    // Bovens & Hartmann Bayesian corroboration (Phase 4) - null unless this node is part of an accepted,
    // agreeing cluster with something real to combine confidence over.
    @Column
    private Double confidence;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCoherenceRunId() { return coherenceRunId; }
    public void setCoherenceRunId(UUID coherenceRunId) { this.coherenceRunId = coherenceRunId; }

    public UUID getEvidenceNodeId() { return evidenceNodeId; }
    public void setEvidenceNodeId(UUID evidenceNodeId) { this.evidenceNodeId = evidenceNodeId; }

    public boolean isAccepted() { return accepted; }
    public void setAccepted(boolean accepted) { this.accepted = accepted; }

    public double getFinalActivation() { return finalActivation; }
    public void setFinalActivation(double finalActivation) { this.finalActivation = finalActivation; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
}
