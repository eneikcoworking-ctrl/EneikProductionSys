package com.eneik.production.toc.model;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Directed edge between two nodes in the execution state machine graph.
 */
public class TocEdge {
    private final String sourceNode;
    private final String targetNode;
    private final AtomicLong transitionCount = new AtomicLong(0);

    public TocEdge(String sourceNode, String targetNode) {
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
    }

    public String getSourceNode() {
        return sourceNode;
    }

    public String getTargetNode() {
        return targetNode;
    }

    public long getTransitionCount() {
        return transitionCount.get();
    }

    public void incrementTransition() {
        transitionCount.incrementAndGet();
    }
}
