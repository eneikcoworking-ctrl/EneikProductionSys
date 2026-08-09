package com.eneik.production.models.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One run of EvidenceCoherenceService's ECHO relaxation over a project's recent evidence nodes.
 * coherenceScore is the real, objective anchor other subsystems (Gemini's future agentic loop, Phase 5)
 * check against instead of trusting an LLM's own self-reported sense of "did I learn something new".
 */
@Entity
@Table(name = "coherence_runs")
public class CoherenceRunEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "ran_at", nullable = false, updatable = false)
    private Instant ranAt = Instant.now();

    @Column(name = "total_nodes", nullable = false)
    private int totalNodes;

    @Column(name = "accepted_nodes", nullable = false)
    private int acceptedNodes;

    @Column(name = "coherence_score", nullable = false)
    private double coherenceScore;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public Instant getRanAt() { return ranAt; }
    public void setRanAt(Instant ranAt) { this.ranAt = ranAt; }

    public int getTotalNodes() { return totalNodes; }
    public void setTotalNodes(int totalNodes) { this.totalNodes = totalNodes; }

    public int getAcceptedNodes() { return acceptedNodes; }
    public void setAcceptedNodes(int acceptedNodes) { this.acceptedNodes = acceptedNodes; }

    public double getCoherenceScore() { return coherenceScore; }
    public void setCoherenceScore(double coherenceScore) { this.coherenceScore = coherenceScore; }
}
