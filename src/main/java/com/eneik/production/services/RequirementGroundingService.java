package com.eneik.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Precision-grounding for client-authored wishlist text (2026-08-07, operator directive: any client input -
 * a short brief or a huge spec, same mechanism either way - should be checked against the SAME already-
 * indexed mathematical/philosophical pattern corpus already used for role-charter RAG retrieval, and where a
 * vague requirement genuinely matches an established rigorous concept (idempotency, atomicity, referential
 * transparency, etc.), that concept gets attached as extra context BEFORE decomposition - never replacing
 * the client's own wording, only sharpening what the decomposer and later Jules see. Deliberately NOT a
 * lookup against a separate hand-built "common requirement types" library (auth, CRUD, ...) - that library
 * doesn't exist and building one would be a different, riskier feature (silently swapping in a canned
 * template can drop a client-specific detail). This only ever adds a precise label next to the client's own
 * text; the actual decomposition prompt still sees both.
 *
 * Framework-level implementation patterns (how to structure a Spring Boot controller, a Svelte component)
 * are explicitly out of scope - Jules already knows its frameworks. This only concerns requirement-level
 * rigor (what precisely must hold), not implementation idiom.
 */
@Service
public class RequirementGroundingService {
    private static final Logger log = LoggerFactory.getLogger(RequirementGroundingService.class);

    // Requirement-unit grouping threshold (2026-08-07): deliberately NOT GeminiContextService.chunkText's
    // ~1400-char paragraph chunking, which optimizes for retrieval fragments where an arbitrary cut is fine
    // (the top-K ranking absorbs it) - a requirement chunk needs to stay a genuinely coherent whole (a
    // client's single request often spans several paragraphs), so short paragraphs are grouped together up
    // to this floor before being treated as one grounding unit. This is an honest first-pass heuristic
    // (group-by-size), not real section/heading detection - a big spec with real headings would deserve a
    // smarter split later, not built here yet.
    private static final int MIN_UNIT_CHARS = 400;
    private static final int PATTERN_MATCH_TOP_K = 2;
    private static final List<String> PATTERN_SOURCE_TYPES = List.of("engineering_charter", "philosopher_pattern_common");

    private final GeminiContextService geminiContextService;

    public RequirementGroundingService(GeminiContextService geminiContextService) {
        this.geminiContextService = geminiContextService;
    }

    /**
     * Returns rawText unchanged (never null-unsafe, never throws) when there is nothing to ground - blank
     * input, or the RAG layer is unavailable (flag off, empty corpus, embedding call failed - see
     * GeminiContextService.retrieveRelevantContextBySourceTypes's own no-throw contract). A caller can
     * always safely treat the result as "the best available text to compile from."
     */
    public String ground(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return rawText;
        }
        List<String> units = splitIntoRequirementUnits(rawText);
        if (units.isEmpty()) {
            return rawText;
        }
        StringBuilder result = new StringBuilder();
        int grounded = 0;
        for (String unit : units) {
            result.append(unit);
            List<GeminiContextService.RetrievedChunk> matches;
            try {
                matches = geminiContextService.retrieveRelevantContextBySourceTypes(unit, PATTERN_MATCH_TOP_K, PATTERN_SOURCE_TYPES);
            } catch (Exception e) {
                log.warn("RequirementGroundingService: pattern lookup failed for one requirement unit, leaving it ungrounded: {}", e.getMessage());
                matches = List.of();
            }
            if (!matches.isEmpty()) {
                grounded++;
                result.append("\n[Grounded in established pattern(s): ");
                for (int i = 0; i < matches.size(); i++) {
                    if (i > 0) {
                        result.append("; ");
                    }
                    result.append(matches.get(i).sourceRef());
                }
                result.append(" - apply the precise, rigorous definition from the referenced pattern, not just its name]\n");
            }
            result.append("\n\n");
        }
        log.info("RequirementGroundingService: grounded {} of {} requirement unit(s)", grounded, units.size());
        return result.toString().strip();
    }

    static List<String> splitIntoRequirementUnits(String text) {
        String[] paragraphs = text.split("\\n\\s*\\n");
        List<String> units = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            String trimmed = paragraph.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(trimmed);
            if (current.length() >= MIN_UNIT_CHARS) {
                units.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            units.add(current.toString());
        }
        return units;
    }
}
