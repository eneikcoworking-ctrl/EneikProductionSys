package com.eneik.production.services.logging;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory, per-project ring buffer of recent log lines, populated by {@link ScopedBufferAppender}.
 * Deliberately only ever stores {@code PROJECT:{id}} scoped events (never {@code SYSTEM}-scoped ones)
 * to keep the factory's own background operational noise (dispatch internals, circuit breakers,
 * AI-resource plumbing) isolated.
 *
 * Bounded (up to 200 lines per project) and in-memory only: it resets on backend restart and is not
 * meant as a durable log store.
 *
 * The sole external reader of this buffer is {@link com.eneik.production.controllers.projects.ProjectController#recentActivity}
 * (endpoint {@code GET /api/projects/{projectId}/recent-activity}) for human operator observation
 * and debug inspection. It is NOT read by {@code FalsificationCycleService} or any agent coding prompt:
 * consumption by the falsification cycle was excised on 2026-08-09 to prevent internal factory orchestration
 * events from leaking into client repository prompts (Gricean conversational maxim / D007).
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
