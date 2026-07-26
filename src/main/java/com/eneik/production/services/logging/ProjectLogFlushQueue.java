package com.eneik.production.services.logging;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * In-memory hand-off between {@link DurableProjectLogAppender} (runs on whatever thread is logging, must
 * never block on a DB write) and {@link com.eneik.production.services.ProjectEventLogService}'s scheduled
 * flush (batches into the DB). Bounded so a DB outage degrades to "recent history missing", never an OOM -
 * the durable log is a forensic aid, not a system it's acceptable to crash over.
 */
public final class ProjectLogFlushQueue {
    private static final int MAX_QUEUE_SIZE = 20_000;
    private static final ConcurrentLinkedQueue<PendingEntry> QUEUE = new ConcurrentLinkedQueue<>();

    private ProjectLogFlushQueue() {
    }

    public record PendingEntry(UUID projectId, Instant createdAt, String level, String logger, String message) {
    }

    static void offer(PendingEntry entry) {
        if (QUEUE.size() >= MAX_QUEUE_SIZE) {
            return;
        }
        QUEUE.add(entry);
    }

    public static List<PendingEntry> drain(int max) {
        List<PendingEntry> batch = new ArrayList<>(Math.min(max, QUEUE.size()));
        PendingEntry entry;
        while (batch.size() < max && (entry = QUEUE.poll()) != null) {
            batch.add(entry);
        }
        return batch;
    }
}
