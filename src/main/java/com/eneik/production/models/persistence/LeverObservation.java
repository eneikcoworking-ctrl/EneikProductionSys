package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lever_observations")
public class LeverObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "lever_key", nullable = false, length = 64)
    private String leverKey;

    @Column(name = "subject_id", length = 128)
    private String subjectId;

    @Column(name = "incumbent_decision", columnDefinition = "TEXT")
    private String incumbentDecision;

    @Column(name = "candidate_decision", columnDefinition = "TEXT")
    private String candidateDecision;

    @Column(nullable = false, length = 16)
    private String agreement;

    @Column(name = "ground_truth_outcome", length = 64)
    private String groundTruthOutcome;

    @Column(name = "observed_at", nullable = false, updatable = false)
    private Instant observedAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getLeverKey() { return leverKey; }
    public void setLeverKey(String leverKey) { this.leverKey = leverKey; }

    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }

    public String getIncumbentDecision() { return incumbentDecision; }
    public void setIncumbentDecision(String incumbentDecision) { this.incumbentDecision = incumbentDecision; }

    public String getCandidateDecision() { return candidateDecision; }
    public void setCandidateDecision(String candidateDecision) { this.candidateDecision = candidateDecision; }

    public String getAgreement() { return agreement; }
    public void setAgreement(String agreement) { this.agreement = agreement; }

    public String getGroundTruthOutcome() { return groundTruthOutcome; }
    public void setGroundTruthOutcome(String groundTruthOutcome) { this.groundTruthOutcome = groundTruthOutcome; }

    public Instant getObservedAt() { return observedAt; }
    public void setObservedAt(Instant observedAt) { this.observedAt = observedAt; }
}
