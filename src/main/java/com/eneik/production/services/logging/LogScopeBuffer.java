package com.eneik.production.services.logging;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory, per-project ring buffer of recent log lines, populated by {@link ScopedBufferAppender}.
 * Deliberately only ever stores PROJECT:{id} scoped events (never SYSTEM-scoped ones) - so the factory's
 * own operational noise (dispatch internals, circuit breakers, AI-resource plumbing) can never leak into
 * project-facing context such as the falsification cycle, which reads this to give roles more current
 * operational context about their own project's recent activity, not about Eneik itself.
 *
 * Bounded and in-memory only: it resets on restart and is not meant as a durable log store - just a
 * cheap "what just happened for this project" window for the next falsification pass.
 */
public final class LogScopeBuffer {
    private static final int MAX_LINES_PER_PROJECT = 200;
    private static final Map<String, ConcurrentLinkedDeque<String>> BUFFERS = new ConcurrentHashMap<>();

    private LogScopeBuffer() {
    }

    static void append(String projectId, String line) {
        ConcurrentLinkedDeque<String> buffer = BUFFERS.computeIfAbsent(projectId, id -> new ConcurrentLinkedDeque<>());
        buffer.addLast(line);
        while (buffer.size() > MAX_LINES_PER_PROJECT) {
            buffer.pollFirst();
        }
    }

    public static List<String> recent(UUID projectId, int limit) {
        if (projectId == null) {
            return List.of();
        }
        ConcurrentLinkedDeque<String> buffer = BUFFERS.get(projectId.toString());
        if (buffer == null || buffer.isEmpty()) {
            return List.of();
        }
        List<String> snapshot = buffer.stream().toList();
        int from = Math.max(0, snapshot.size() - limit);
        return snapshot.subList(from, snapshot.size());
    }
}
