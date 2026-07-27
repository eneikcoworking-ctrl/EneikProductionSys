package com.eneik.production.toc.engine;

import com.eneik.production.toc.model.TocEdge;
import com.eneik.production.toc.model.TocNode;
import com.eneik.production.toc.model.TocToken;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic Execution State Machine & Dependency Graph Engine.
 * Thread-safe runtime store for steps, tokens, edges, and statistics.
 */
@Component
public class TocExecutionGraph {

    private final Map<String, TocNode> nodes = new ConcurrentHashMap<>();
    private final Map<String, TocToken> activeTokens = new ConcurrentHashMap<>();
    private final Map<String, TocEdge> edges = new ConcurrentHashMap<>();

    private final AtomicLong totalTokensAdmitted = new AtomicLong(0);
    private final long startTimeMillis = System.currentTimeMillis();

    public TocNode getOrCreateNode(String nodeName) {
        return nodes.computeIfAbsent(nodeName, TocNode::new);
    }

    public Collection<TocNode> getAllNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public TocNode getNode(String nodeName) {
        return nodes.get(nodeName);
    }

    public void registerToken(TocToken token) {
        activeTokens.put(token.getTokenId(), token);
        totalTokensAdmitted.incrementAndGet();
    }

    public TocToken getToken(String tokenId) {
        return activeTokens.get(tokenId);
    }

    public TocToken unregisterToken(String tokenId) {
        return activeTokens.remove(tokenId);
    }

    public Collection<TocToken> getActiveTokens() {
        return Collections.unmodifiableCollection(activeTokens.values());
    }

    public void recordTransition(String sourceNode, String targetNode) {
        if (sourceNode == null || targetNode == null || sourceNode.equals(targetNode)) {
            return;
        }
        String key = sourceNode + "->" + targetNode;
        edges.computeIfAbsent(key, k -> new TocEdge(sourceNode, targetNode)).incrementTransition();
    }

    public Collection<TocEdge> getEdges() {
        return Collections.unmodifiableCollection(edges.values());
    }

    public double getGlobalArrivalRatePerSec() {
        double elapsedSec = Math.max(1.0, (System.currentTimeMillis() - startTimeMillis) / 1000.0);
        return totalTokensAdmitted.get() / elapsedSec;
    }

    public long getCompletedCountAllNodes() {
        return nodes.values().stream().mapToLong(TocNode::getCompletedCount).sum();
    }

    public void resetGraph() {
        nodes.clear();
        activeTokens.clear();
        edges.clear();
        totalTokensAdmitted.set(0);
    }
}
