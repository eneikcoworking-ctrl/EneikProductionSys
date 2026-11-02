package com.eneik.production.services.worker;

/**
 * Supported execution engines for factory tasks.
 * - JULES: Legacy Google Jules API cloud engine.
 * - DEEPSEEK_WORKTREE: Sovereign DeepSeek API (V3/R1) + ephemeral git worktree engine.
 * - LOCAL_AGY: Sovereign zero-cost local agy CLI + ephemeral git worktree engine.
 * - LOCAL_AGY_CLI: Direct CLI variant alias for LOCAL_AGY.
 * - HYBRID: Sovereign-first engine with seamless fallback across LOCAL_AGY <-> DEEPSEEK <-> JULES ("Cranes, not Skyhooks").
 */
public enum WorkerEngineType {
    JULES,
    DEEPSEEK_WORKTREE,
    LOCAL_AGY,
    LOCAL_AGY_CLI,
    HYBRID;

    public static WorkerEngineType parse(String value, WorkerEngineType fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String clean = value.trim().toUpperCase();
        if ("LOCAL_AGY_WORKTREE".equals(clean) || "LOCAL_AGY".equals(clean)) {
            return LOCAL_AGY;
        }
        if ("LOCAL_AGY_CLI".equals(clean)) {
            return LOCAL_AGY_CLI;
        }
        try {
            return WorkerEngineType.valueOf(clean);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
