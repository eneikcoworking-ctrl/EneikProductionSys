package com.eneik.production.services;

import java.util.List;
import java.util.Locale;

/**
 * Charter Pattern #12 (independent verification, not self-attestation): a deterministic, non-LLM
 * cross-check on {@link GeminiProjectObserverService}'s own self-reported finding scope ("platform"
 * vs "product"). Confirmed live incident (test-fortieth, 2026-08-02): findings whose evidence was
 * self-evidently about EneikProductionSys' own pipeline (e.g. "fix the state transition bug, so
 * that the pipeline queue resumes processing") were misclassified as scope=="product" and polluted
 * a client project's own feature list with fake epics - 14 of 18 "epics" in that project turned out
 * to be this kind of noise, not real client JTBDs. Gemini's own self-classification is one signal,
 * not the sole authority on it - this scans the same evidence/summary text for vocabulary no
 * legitimate client-product finding could plausibly contain, since it names EneikProductionSys' own
 * internals (this factory's orchestrator, dispatch machinery, quality-audit types), never anything a
 * target project's own codebase could be.
 */
public final class PlatformSelfReferenceDetector {

    private static final List<String> PLATFORM_VOCABULARY = List.of(
            "pipeline queue", "the orchestrator", "orchestrator bug", "jules session",
            "wishlist compiler", "dispatch loop", "dispatchqueuedtasks", "eneikproductionsys",
            "coverage audit", "falsification audit", "philosophical audit", "gemini observer",
            "kaizen", "review fallback", "design review draft", "claim service",
            "session lifecycle", "account_id", "accounthealthservice", "task graph",
            "featureid", "jules_api_blocked", "the factory system", "this system's own"
    );

    private PlatformSelfReferenceDetector() {
    }

    /** True if the text contains vocabulary specific to this factory's own internals, not a client product. */
    public static boolean looksLikePlatformFinding(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return PLATFORM_VOCABULARY.stream().anyMatch(normalized::contains);
    }
}
