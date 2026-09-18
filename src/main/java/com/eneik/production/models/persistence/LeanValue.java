package com.eneik.production.models.persistence;

import java.util.Locale;

/**
 * Four-valued Lean logic representation
 * (NUEL_BELNAP_03_TRUTH_STATUS_TABLE [D012] / GILBERT_RAYL_03_CATEGORY_ERROR_SCAN [D002]).
 *
 * <p>Values:
 * <ul>
 *   <li>{@code essential} - non-negotiable core client value (Must-Be).</li>
 *   <li>{@code valuable} - differentiating value (Performance, Attractive).</li>
 *   <li>{@code waste} - non-value-adding muda (Reverse/Waste) to be eliminated.</li>
 *   <li>{@code undetermined} - unverified or unparsed, neither valuable nor essential.</li>
 * </ul>
 */
public enum LeanValue {
    essential, valuable, waste, undetermined;

    /**
     * Parse raw string to LeanValue. Unknown, blank, or malformed strings
     * resolve strictly to {@link #undetermined}, never to an affirmative value.
     */
    public static LeanValue parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return undetermined;
        }
        try {
            return LeanValue.valueOf(raw.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return undetermined;
        }
    }

    /**
     * Whether this value represents actionable work (essential or valuable).
     * Undetermined and waste are NOT actionable.
     */
    public boolean isActionable() {
        return this == essential || this == valuable;
    }

    public boolean isWaste() {
        return this == waste;
    }

    public boolean isUndetermined() {
        return this == undetermined;
    }
}
