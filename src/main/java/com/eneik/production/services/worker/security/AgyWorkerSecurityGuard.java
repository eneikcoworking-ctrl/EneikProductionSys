package com.eneik.production.services.worker.security;

import com.eneik.production.services.worker.worktree.ExclusionaryReasonViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Joseph Raz's Deontic Exclusionary Reason Guard (D006 / Goldman D010).
 * Enforces strict epistemic and operational separation between Factory Sovereign Core (L1/L2)
 * and Product Worker execution profiles.
 *
 * Under no circumstances may L2 Engineer (remotecli) or L1 Philosopher (remotecli2)
 * be conscripted into product execution pools.
 */
public final class AgyWorkerSecurityGuard {

    private static final Logger log = LoggerFactory.getLogger(AgyWorkerSecurityGuard.class);

    public static final Set<String> FORBIDDEN_WORKER_USERS = Set.of("remotecli", "remotecli2");
    public static final Set<String> PERMITTED_WORKER_USERS = Set.of("remotecli3", "root");

    private AgyWorkerSecurityGuard() {
        // utility class
    }

    /**
     * Validates that the requested worker execution user is permitted to run product tasks.
     * Throws ExclusionaryReasonViolationException if user is in FORBIDDEN_WORKER_USERS.
     */
    public static void validateWorkerExecutionUser(String executionUser) {
        if (executionUser == null || executionUser.isBlank()) {
            throw new IllegalArgumentException("Worker execution user cannot be null or blank");
        }
        String normalized = executionUser.trim().toLowerCase();
        if (FORBIDDEN_WORKER_USERS.contains(normalized)) {
            String auditMsg = String.format(
                    "Raz Deontic Gate Violation (D006): execution user '%s' belongs to Factory Sovereign Core (L1/L2) and is STRICTLY FORBIDDEN from executing product worker tasks!",
                    normalized
            );
            log.error("DEONTIC SECURITY AUDIT: {}", auditMsg);
            throw new ExclusionaryReasonViolationException(auditMsg);
        }
    }

    public static boolean isPermitted(String executionUser) {
        if (executionUser == null || executionUser.isBlank()) {
            return false;
        }
        return !FORBIDDEN_WORKER_USERS.contains(executionUser.trim().toLowerCase());
    }
}
