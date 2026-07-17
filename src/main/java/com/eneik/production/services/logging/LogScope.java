package com.eneik.production.services.logging;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Tags every log line emitted on the current thread with a scope - SYSTEM (the orchestrator/factory
 * itself: dispatch, circuit breakers, account maintenance) or PROJECT:{id} (work belonging to one
 * specific built project). Backed by SLF4J's MDC, so existing log.info()/log.warn() calls anywhere in
 * the call stack need no changes - only the entry points that start processing a project's work call
 * project(id), and must call clear() in a finally block when that unit of work ends.
 */
public final class LogScope {
    public static final String MDC_KEY = "scope";
    public static final String SYSTEM = "SYSTEM";

    private LogScope() {
    }

    public static void system() {
        MDC.put(MDC_KEY, SYSTEM);
    }

    public static void project(UUID projectId) {
        MDC.put(MDC_KEY, "PROJECT:" + projectId);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
