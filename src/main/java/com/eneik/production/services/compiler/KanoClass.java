package com.eneik.production.services.compiler;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The epic-level Kano vocabulary, and the single rule for reading one off a compiler plan.
 *
 * This class exists because the rule lived in two parsers and they disagreed. On 2026-08-14
 * ProjectFlowService.parseCompilerPlanContent stopped defaulting an absent class to "Must-Be" and its
 * comment recorded that "the two sides of the flow now hold the same discipline". That was not true:
 * JulesDispatchService.parseCompilerPlan - the path a Jules-delivered plan actually travels - still read
 * {@code .asText("Must-Be")}, which is why all five epics of test-forty-sixth came out Must-Be after the
 * fix. The two parsers also disagreed about how to SPELL an absent class, one writing blank and one about
 * to write a marker, so unifying the default without unifying the vocabulary would have left the same
 * defect in a quieter form.
 *
 * So the vocabulary and the reading rule are here, once, and both parsers call it. A fix that leaves the
 * concept in two places is the defect, not the repair.
 */
public final class KanoClass {

    /**
     * The four the compiler prompt actually offers at epic level. "Indifferent" belongs to the
     * philosophical-critique vocabulary and is deliberately not here - a critique may find a feature
     * irrelevant, but no epic is planned as one.
     */
    private static final Map<String, String> CANONICAL = Map.of(
            "must-be", "Must-Be",
            "performance", "Performance",
            "attractive", "Attractive",
            "reverse", "Reverse");

    /**
     * Recorded when the compiler did not choose, so "not classified" stops being spelled "Must-Be".
     *
     * Deliberately not one of the four, and deliberately not blank either: a marker that is also a valid
     * class is not a marker, and a marker that is also the empty string is indistinguishable from a column
     * nobody ever wrote to.
     */
    public static final String UNCLASSIFIED = "Unclassified";

    private KanoClass() {
    }

    /** The four valid values, for messages that need to say what was expected. */
    public static List<String> valid() {
        return List.copyOf(CANONICAL.values());
    }

    /**
     * @return the canonical spelling, or {@link #UNCLASSIFIED} when the compiler omitted it or wrote
     *         something outside the vocabulary. Never guesses - guessing here is the whole failure mode.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return UNCLASSIFIED;
        }
        return CANONICAL.getOrDefault(raw.trim().toLowerCase(Locale.ROOT), UNCLASSIFIED);
    }

    public static boolean isUnclassified(String value) {
        return value == null || value.isBlank() || UNCLASSIFIED.equalsIgnoreCase(value.trim());
    }
}
