package com.eneik.production.services.design.sovereign.domain;

/**
 * Status lifecycle of a design synthesis session.
 */
public enum SynthesisStatus {
    INITIALIZED(false),
    SYNTHESIZING(false),
    AUDITING(false),
    REFINING(false),
    COMPLETED_APPROVED(true),
    COMPLETED_FALLBACK(true),
    FAILED(true);

    private final boolean terminal;

    SynthesisStatus(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
