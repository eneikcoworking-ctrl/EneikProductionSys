package com.eneik.production.toc.service;

import com.eneik.production.toc.engine.TocExecutionGraph;
import com.eneik.production.toc.model.TocEdge;
import com.eneik.production.toc.model.TocNode;
import com.eneik.production.toc.model.persistence.TocEdgeSnapshotEntity;
import com.eneik.production.toc.model.persistence.TocNodeSnapshotEntity;
import com.eneik.production.toc.repository.TocEdgeSnapshotRepository;
import com.eneik.production.toc.repository.TocNodeSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Theory of Constraints (TOC) State Persistence Service.
 * Persists and restores Welford online variance series and edge transition topology.
 *
 * Grounding:
 * - DEREK_PARFIT_01_PERSISTENCE_SNAPSHOT (D010 Data lineage loss):
 *   Durable time series across process restarts. Eliminates cold-start single-sample
 *   distortion where constraint decisions are made on a sample of size 1 instead of history.
 */
@Service
public class TocPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(TocPersistenceService.class);

    private final TocNodeSnapshotRepository nodeSnapshotRepository;
    private final TocEdgeSnapshotRepository edgeSnapshotRepository;

    @Autowired
    public TocPersistenceService(TocNodeSnapshotRepository nodeSnapshotRepository,
                                 TocEdgeSnapshotRepository edgeSnapshotRepository) {
        this.nodeSnapshotRepository = nodeSnapshotRepository;
        this.edgeSnapshotRepository = edgeSnapshotRepository;
    }

    /**
     * Restores the execution graph from the latest historical snapshots in database.
     *
     * @param graph the in-memory execution graph to populate
     * @return count of restored nodes
     */
    @Transactional(readOnly = true)
    public int restoreHistoricalState(TocExecutionGraph graph) {
        if (nodeSnapshotRepository == null || graph == null) {
            return 0;
        }

        int restoredNodes = 0;
        try {
            List<TocNodeSnapshotEntity> latestNodes = nodeSnapshotRepository.findLatestSnapshotsForAllNodes();
            for (TocNodeSnapshotEntity s : latestNodes) {
                TocNode node = graph.getOrCreateNode(s.getNodeName());
                node.restoreSnapshot(
                        s.getCompletedCount(),
                        s.getErrorCount(),
                        s.getTotalDurationNanos(),
                        s.getMeanDurationMs(),
                        s.getM2Ms(),
                        s.getStdDevMs()
                );
                restoredNodes++;
                log.info("[TOC-PERSISTENCE][RESTORE_NODE] Restored node '{}' from snapshot: count={}, mean={} ms, stdDev={} ms",
                        s.getNodeName(), s.getCompletedCount(), String.format("%.2f", s.getMeanDurationMs()), String.format("%.2f", s.getStdDevMs()));
            }

            if (edgeSnapshotRepository != null) {
                List<TocEdgeSnapshotEntity> latestEdges = edgeSnapshotRepository.findLatestSnapshotsForAllEdges();
                for (TocEdgeSnapshotEntity e : latestEdges) {
                    TocEdge edge = graph.getOrCreateEdge(e.getSourceNode(), e.getTargetNode());
                    if (edge != null) {
                        edge.restoreTransitionCount(e.getTransitionCount());
                        log.info("[TOC-PERSISTENCE][RESTORE_EDGE] Restored edge '{}->{}' from snapshot: transitions={}",
                                e.getSourceNode(), e.getTargetNode(), e.getTransitionCount());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[TOC-PERSISTENCE] Failed to restore historical state from snapshots: {}", e.getMessage());
        }

        return restoredNodes;
    }

    /**
     * Persists a durable snapshot of the given TOC Node.
     */
    @Transactional
    public TocNodeSnapshotEntity saveNodeSnapshot(TocNode node) {
        if (nodeSnapshotRepository == null || node == null) {
            return null;
        }

        double timeoutLimit = node.getDynamicTimeoutLimitMs(2.0, 5000.0);
        TocNodeSnapshotEntity snapshot = new TocNodeSnapshotEntity(
                node.getName(),
                node.getCompletedCount(),
                node.getErrorCount(),
                node.getTotalDurationNanos(),
                node.getMeanDurationMs(),
                node.getM2Ms(),
                node.getStdDevMs(),
                timeoutLimit,
                Instant.now()
        );

        return nodeSnapshotRepository.save(snapshot);
    }

    /**
     * Persists a durable snapshot of a TOC Edge transition.
     */
    @Transactional
    public TocEdgeSnapshotEntity saveEdgeSnapshot(String sourceNode, String targetNode, long transitionCount) {
        if (edgeSnapshotRepository == null || sourceNode == null || targetNode == null) {
            return null;
        }

        TocEdgeSnapshotEntity snapshot = new TocEdgeSnapshotEntity(
                sourceNode,
                targetNode,
                transitionCount,
                Instant.now()
        );

        return edgeSnapshotRepository.save(snapshot);
    }

    /**
     * Saves snapshots for all nodes and edges in the graph.
     */
    @Transactional
    public void saveGraphSnapshot(TocExecutionGraph graph) {
        if (graph == null) {
            return;
        }
        for (TocNode node : graph.getAllNodes()) {
            saveNodeSnapshot(node);
        }
        for (TocEdge edge : graph.getEdges()) {
            saveEdgeSnapshot(edge.getSourceNode(), edge.getTargetNode(), edge.getTransitionCount());
        }
    }
}
