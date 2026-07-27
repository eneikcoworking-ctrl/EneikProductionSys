package com.eneik.production.toc.model;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Represents a token (execution instance) moving through state machine nodes in a scenario.
 */
public class TocToken {

    public enum TokenStatus {
        ACTIVE,
        COMPLETED,
        CYCLE_ABORTED,
        STALLED,
        DEADLOCK_ABORTED,
        THROTTLED,
        FAILED
    }

    private final String tokenId;
    private final String scenarioName;
    private final int priority;
    private final Instant createdAt;

    private volatile String activeNode;
    private volatile Instant activeNodeEnteredAt;
    private volatile TokenStatus status;

    // Active uncompleted call stack (for directed cycle detection)
    private final List<String> callStack = new CopyOnWriteArrayList<>();
    // History of steps visited
    private final List<String> visitedHistory = new CopyOnWriteArrayList<>();

    // Wait-For Graph (WFG) resource tracking
    private final Set<String> heldResources = ConcurrentHashMap.newKeySet();
    private volatile String waitingResource;

    public TocToken(String tokenId, String scenarioName, int priority) {
        this.tokenId = tokenId;
        this.scenarioName = scenarioName;
        this.priority = priority;
        this.createdAt = Instant.now();
        this.status = TokenStatus.ACTIVE;
    }

    public String getTokenId() {
        return tokenId;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public int getPriority() {
        return priority;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getActiveNode() {
        return activeNode;
    }

    public Instant getActiveNodeEnteredAt() {
        return activeNodeEnteredAt;
    }

    public TokenStatus getStatus() {
        return status;
    }

    public void setStatus(TokenStatus status) {
        this.status = status;
    }

    public List<String> getCallStack() {
        return Collections.unmodifiableList(callStack);
    }

    public List<String> getVisitedHistory() {
        return Collections.unmodifiableList(visitedHistory);
    }

    public Set<String> getHeldResources() {
        return Collections.unmodifiableSet(heldResources);
    }

    public String getWaitingResource() {
        return waitingResource;
    }

    public void setWaitingResource(String waitingResource) {
        this.waitingResource = waitingResource;
    }

    public synchronized boolean pushNode(String nodeName) {
        // If nodeName is already in callStack, we have a cycle!
        if (callStack.contains(nodeName)) {
            return false; // Cycle detected
        }
        callStack.add(nodeName);
        visitedHistory.add(nodeName);
        this.activeNode = nodeName;
        this.activeNodeEnteredAt = Instant.now();
        return true;
    }

    public synchronized void popNode(String nodeName) {
        if (!callStack.isEmpty() && callStack.get(callStack.size() - 1).equals(nodeName)) {
            callStack.remove(callStack.size() - 1);
        } else {
            callStack.remove(nodeName);
        }
        if (!callStack.isEmpty()) {
            this.activeNode = callStack.get(callStack.size() - 1);
            this.activeNodeEnteredAt = Instant.now();
        } else {
            this.activeNode = null;
            this.activeNodeEnteredAt = null;
        }
    }

    public void acquireResource(String resourceId) {
        heldResources.add(resourceId);
        if (resourceId.equals(this.waitingResource)) {
            this.waitingResource = null;
        }
    }

    public void releaseResource(String resourceId) {
        heldResources.remove(resourceId);
    }

    public long getActiveNodeDwellTimeMs() {
        if (activeNodeEnteredAt == null) {
            return 0L;
        }
        return Instant.now().toEpochMilli() - activeNodeEnteredAt.toEpochMilli();
    }
}
