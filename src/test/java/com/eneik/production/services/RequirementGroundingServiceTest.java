package com.eneik.production.services;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Precision-grounding: verifies the client's own wording is always preserved verbatim, a real pattern match
 * only ever ADDS an annotation (never replaces anything), and RAG-layer failures degrade to "no grounding",
 * never an exception - a wishlist submission must never fail just because this optional step couldn't run.
 */
class RequirementGroundingServiceTest {

    @Test
    void nullAndBlankInputPassThroughUnchanged() {
        GeminiContextService geminiContextService = mock(GeminiContextService.class);
        RequirementGroundingService service = new RequirementGroundingService(geminiContextService);

        assertThat(service.ground(null)).isNull();
        assertThat(service.ground("")).isEmpty();
        assertThat(service.ground("   ")).isEqualTo("   ");
    }

    @Test
    void preservesClientWordingVerbatimWhenNoPatternMatches() {
        GeminiContextService geminiContextService = mock(GeminiContextService.class);
        when(geminiContextService.retrieveRelevantContextBySourceTypes(anyString(), anyInt(), anyList()))
                .thenReturn(List.of());
        RequirementGroundingService service = new RequirementGroundingService(geminiContextService);

        String raw = "The system must let a user reset their own password.";
        String grounded = service.ground(raw);

        assertThat(grounded).contains(raw);
        assertThat(grounded).doesNotContain("[Grounded in established pattern(s):");
    }

    @Test
    void appendsPatternAnnotationWithoutReplacingOriginalTextWhenARealMatchExists() {
        GeminiContextService geminiContextService = mock(GeminiContextService.class);
        when(geminiContextService.retrieveRelevantContextBySourceTypes(anyString(), anyInt(), anyList()))
                .thenReturn(List.of(new GeminiContextService.RetrievedChunk(
                        "ENGINEERING_INVARIANTS_CHARTER.md", "Idempotency: applying an operation twice must have the same effect as once.", 0.81)));
        RequirementGroundingService service = new RequirementGroundingService(geminiContextService);

        String raw = "A payment must never be charged twice even if the request is retried.";
        String grounded = service.ground(raw);

        assertThat(grounded).contains(raw);
        assertThat(grounded).contains("[Grounded in established pattern(s): ENGINEERING_INVARIANTS_CHARTER.md");
        // The client's exact words appear BEFORE the annotation - augmentation, not a rewrite that could
        // reorder or drop the original meaning.
        assertThat(grounded.indexOf(raw)).isLessThan(grounded.indexOf("[Grounded in"));
    }

    @Test
    void degradesToUngroundedTextWhenRagLookupThrows() {
        GeminiContextService geminiContextService = mock(GeminiContextService.class);
        when(geminiContextService.retrieveRelevantContextBySourceTypes(anyString(), anyInt(), anyList()))
                .thenThrow(new RuntimeException("embedding service unreachable"));
        RequirementGroundingService service = new RequirementGroundingService(geminiContextService);

        String raw = "A short client requirement.";
        String grounded = service.ground(raw);

        assertThat(grounded).contains(raw);
        assertThat(grounded).doesNotContain("[Grounded in established pattern(s):");
    }

    @Test
    void groupsShortParagraphsIntoOneRequirementSizedUnitInsteadOfOneUnitPerParagraph() {
        String small = "First short sentence.\n\nSecond short sentence.\n\nThird short sentence.";
        List<String> units = RequirementGroundingService.splitIntoRequirementUnits(small);

        // All three paragraphs together are well under the grouping floor, so they must merge into exactly
        // one unit - a real requirement is often several short paragraphs, not one paragraph = one requirement.
        assertThat(units).hasSize(1);
        assertThat(units.get(0)).contains("First short sentence.", "Second short sentence.", "Third short sentence.");
    }

    @Test
    void splitsIntoMultipleUnitsOnceTheGroupingFloorIsReached() {
        String longParagraph = "x".repeat(500);
        String text = longParagraph + "\n\n" + longParagraph;

        List<String> units = RequirementGroundingService.splitIntoRequirementUnits(text);

        assertThat(units).hasSize(2);
    }
}
