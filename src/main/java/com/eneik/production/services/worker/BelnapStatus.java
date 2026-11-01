package com.eneik.production.services.worker;

/**
 * Nuel Belnap 4-Valued Truth Logic for Autonomous Worker Lifecycle (D012).
 * Eliminates simplistic boolean traps and cartesian theatre stuck states.
 *
 * Values:
 * - NEITHER (N / None): Session queued or executing, generation/verification in progress (timeout-bounded).
 * - TRUE (T / Truth): Severe Popperian test passed, file scope verified clean, PR opened successfully.
 * - FALSE (F / Falsified): Patch failed syntax/tests after reflection; rolled back with zero dirty branches.
 * - BOTH (B / Conflict): Locally valid patch, but upstream branch diverged or merge contradiction detected.
 */
public enum BelnapStatus {
    NEITHER("N", "In Progress / Unverified"),
    TRUE("T", "Falsification Passed / Success"),
    FALSE("F", "Falsified / Rejected"),
    BOTH("B", "Contradiction / Conflict");

    private final String symbol;
    private final String description;

    BelnapStatus(String symbol, String description) {
        this.symbol = symbol;
        this.description = description;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getDescription() {
        return description;
    }

    public boolean isTerminal() {
        return this != NEITHER;
    }

    public boolean isSuccess() {
        return this == TRUE;
    }

    public boolean isConflict() {
        return this == BOTH;
    }

    public boolean isFalsified() {
        return this == FALSE;
    }

    public static BelnapStatus fromString(String name) {
        if (name == null || name.isBlank()) {
            return NEITHER;
        }
        for (BelnapStatus status : values()) {
            if (status.name().equalsIgnoreCase(name) || status.symbol.equalsIgnoreCase(name)) {
                return status;
            }
        }
        return NEITHER;
    }
}
