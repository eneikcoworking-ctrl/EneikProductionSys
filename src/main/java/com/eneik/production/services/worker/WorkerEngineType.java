package com.eneik.production.services.worker;

/**
 * Supported execution engines for factory tasks.
 * - JULES: Legacy Google Jules API cloud engine.
 * - DEEPSEEK_WORKTREE: Sovereign DeepSeek API (V3/R1) + ephemeral git worktree engine.
 * - HYBRID: Sovereign-first engine with seamless fallback to Jules on quota/rate-limits ("Cranes, not Skyhooks").
 */
public enum WorkerEngineType {
    JULES,
    DEEPSEEK_WORKTREE,
    HYBRID;

    public static WorkerEngineType parse(String value, WorkerEngineType fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return WorkerEngineType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
