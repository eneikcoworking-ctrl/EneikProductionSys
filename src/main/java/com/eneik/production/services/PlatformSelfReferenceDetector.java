package com.eneik.production.services;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

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
 *
 * Second, independent signal (2026-08-09, patient-zero trace of the test-forty-third contamination,
 * PR #97): the vocabulary list above is necessarily incomplete - it missed the exact seeding finding,
 * whose evidence quoted a real EneikProductionSys log line verbatim ("...[WARN] reconcileTaskStatus-
 * AgainstGitHubTruth: task 0bcb9d29... is marked done but PR#53 closed without merge") without using
 * any word from the list. EneikProductionSys has no channel to observe a client's own running product
 * except through Gemini's own findings - it can never legitimately quote ITS OWN internal log format
 * back at itself. Any evidence text shaped like an internal log line (ISO-8601 timestamp immediately
 * followed by a level marker) is platform-self-reference BY CONSTRUCTION, independent of which words
 * appear in it - this closes the gap by structure, not by enumerating more phrases after the fact.
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

    /**
     * ISO-8601 timestamp (date + time, optional fractional seconds, optional Z) followed - within a
     * short gap that covers both known real formats, {@code "TIMESTAMP LEVEL logger - message"}
     * (ScopedBufferAppender) and {@code "TIMESTAMP [LEVEL] message"} (raw container log output) - by
     * a standard log level marker. This is the shape of a log line, not its wording, so no legitimate
     * client-product finding can match it: EneikProductionSys never has direct visibility into a
     * client's own runtime logs.
     */
    private static final Pattern INTERNAL_LOG_LINE_SHAPE = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z?\\s{1,3}\\[?(TRACE|DEBUG|INFO|WARN|ERROR)\\]?"
    );

    private PlatformSelfReferenceDetector() {
    }

    /** True if the text contains vocabulary specific to this factory's own internals, not a client product. */
    public static boolean looksLikePlatformFinding(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return PLATFORM_VOCABULARY.stream().anyMatch(normalized::contains) || looksLikeInternalLogLine(text);
    }

    /** True if the text contains a substring shaped like one of this factory's own internal log lines. */
    public static boolean looksLikeInternalLogLine(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return INTERNAL_LOG_LINE_SHAPE.matcher(text).find();
    }
}
