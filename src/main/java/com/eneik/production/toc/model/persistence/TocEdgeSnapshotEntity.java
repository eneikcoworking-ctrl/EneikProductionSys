package com.eneik.production.toc.model.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable time series snapshot of a TOC Edge transition count in the state machine graph.
 *
 * Grounding:
 * - DEREK_PARFIT_01_PERSISTENCE_SNAPSHOT (D010 Data lineage loss):
 *   Execution graph topology and transition frequency are continuous across process restarts.
 */
@Entity
@Table(name = "toc_edge_snapshots")
public class TocEdgeSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "source_node", nullable = false, length = 128)
    private String sourceNode;

    @Column(name = "target_node", nullable = false, length = 128)
    private String targetNode;

    @Column(name = "transition_count", nullable = false)
    private long transitionCount;

    @Column(name = "snapshot_at", nullable = false)
    private Instant snapshotAt = Instant.now();

    public TocEdgeSnapshotEntity() {
    }

    public TocEdgeSnapshotEntity(String sourceNode, String targetNode, long transitionCount, Instant snapshotAt) {
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
        this.transitionCount = transitionCount;
        this.snapshotAt = snapshotAt != null ? snapshotAt : Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSourceNode() {
        return sourceNode;
    }

    public void setSourceNode(String sourceNode) {
        this.sourceNode = sourceNode;
    }

    public String getTargetNode() {
        return targetNode;
    }

    public void setTargetNode(String targetNode) {
        this.targetNode = targetNode;
    }

    public long getTransitionCount() {
        return transitionCount;
    }

    public void setTransitionCount(long transitionCount) {
        this.transitionCount = transitionCount;
    }

    public Instant getSnapshotAt() {
        return snapshotAt;
    }

    public void setSnapshotAt(Instant snapshotAt) {
        this.snapshotAt = snapshotAt;
    }
}
