package com.eneik.production.services.worker.worktree;

import java.util.List;

/**
 * Thrown when Joseph Raz's Deontic Exclusionary Reason (D006) is violated.
 * Occurs when modified files exceed the declared task.fileScope.
 * The operation is immediately aborted and rolled back.
 */
public class ExclusionaryReasonViolationException extends RuntimeException {
    private final List<String> violatingFiles;
    private final List<String> declaredScope;

    public ExclusionaryReasonViolationException(List<String> violatingFiles, List<String> declaredScope) {
        super("Raz Deontic Gate (D006): modified files " + violatingFiles + " are outside declared scope " + declaredScope);
        this.violatingFiles = violatingFiles;
        this.declaredScope = declaredScope;
    }

    public List<String> getViolatingFiles() {
        return violatingFiles;
    }

    public List<String> getDeclaredScope() {
        return declaredScope;
    }
}
