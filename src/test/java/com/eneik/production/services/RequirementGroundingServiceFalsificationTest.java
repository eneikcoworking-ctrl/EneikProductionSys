package com.eneik.production.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Falsification tests for RequirementGroundingService anchored in Barcan philosopher patterns:
 * 1. DEVID_CHALMERS_02_INTENSION_COMPATIBILITY (D001 Semantic drift, Chalmers):
 *    Primary intension (client's original wording) is strictly preserved verbatim; secondary intension
 *    (rigorous mathematical/architectural concept capsule) is only appended, never replacing or mutating
 *    client intent.
 * 2. ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE (D014 RAG hallucination, Church):
 *    RAG capsule cites exact sourceRef items without hallucinated pattern names; RAG exceptions degrade
 *    gracefully to ungrounded raw text without crashing wishlist processing.
 */
class RequirementGroundingServiceFalsificationTest {

    @Test
    @DisplayName("Chalmers D001: Primary intension is preserved verbatim at position 0; capsule only appends")
    void falsifyChalmersPrimaryIntensionPreservation() {
        GeminiContextService geminiContextService = mock(GeminiContextService.class);
        when(geminiContextService.retrieveRelevantContextBySourceTypes(anyString(), anyInt(), anyList()))
                .thenReturn(List.of(
                        new GeminiContextService.RetrievedChunk("BARCAN_MODAL_INVARIANTS.md", "Rigid designator invariant", 0.95),
                        new GeminiContextService.RetrievedChunk("CHURCH_COMPUTABILITY.md", "Referential transparency invariant", 0.88)
                ));

        RequirementGroundingService service = new RequirementGroundingService(geminiContextService);

        String clientRawText = "User must be able to export audit logs to S3 without duplicating existing batches.";
        String grounded = service.ground(clientRawText);

        // Verbatim preservation: client text must exist intact and precede any RAG annotations
        assertThat(grounded).startsWith(clientRawText);
        assertThat(grounded).contains("[Grounded in established pattern(s): BARCAN_MODAL_INVARIANTS.md; CHURCH_COMPUTABILITY.md - apply the precise, rigorous definition from the referenced pattern, not just its name]");
        assertThat(grounded.indexOf(clientRawText)).isEqualTo(0);
        assertThat(grounded.indexOf("[Grounded in established pattern(s):")).isGreaterThan(clientRawText.length());
    }

    @Test
    @DisplayName("Chalmers D001: Null, blank, and empty input pass through unchanged without synthetic mutations")
    void falsifyChalmersIntensionCompatibilityBlankInputs() {
        GeminiContextService geminiContextService = mock(GeminiContextService.class);
        RequirementGroundingService service = new RequirementGroundingService(geminiContextService);

        assertThat(service.ground(null)).isNull();
        assertThat(service.ground("")).isEmpty();
        assertThat(service.ground("   \n\t   ")).isEqualTo("   \n\t   ");
    }

    @Test
    @DisplayName("Church D014: RAG grounding capsule formats exact sourceRef citations without hallucinating references")
    void falsifyChurchRagGroundingCapsuleCitation() {
        GeminiContextService geminiContextService = mock(GeminiContextService.class);
        when(geminiContextService.retrieveRelevantContextBySourceTypes(anyString(), anyInt(), anyList()))
                .thenReturn(List.of(
                        new GeminiContextService.RetrievedChunk("ENGINEERING_INVARIANTS_CHARTER.md", "Idempotent mutation", 0.91)
                ));

        RequirementGroundingService service = new RequirementGroundingService(geminiContextService);

        String raw = "Ensure payments are idempotent across repeated network calls.";
        String grounded = service.ground(raw);

        assertThat(grounded).contains("ENGINEERING_INVARIANTS_CHARTER.md");
        assertThat(grounded).doesNotContain("UNVERIFIED_SOURCE");
        assertThat(grounded).doesNotContain("HALLUCINATED_PATTERN");
    }

    @Test
    @DisplayName("Church D014: RAG context lookup exception gracefully degrades to verbatim ungrounded text")
    void falsifyChurchRagGroundingCapsuleGracefulDegradationOnException() {
        GeminiContextService geminiContextService = mock(GeminiContextService.class);
        when(geminiContextService.retrieveRelevantContextBySourceTypes(anyString(), anyInt(), anyList()))
                .thenThrow(new IllegalStateException("Gemini vector store down or quota exhausted"));

        RequirementGroundingService service = new RequirementGroundingService(geminiContextService);

        String raw = "Client requirement submitted during RAG outage.";
        String grounded = service.ground(raw);

        assertThat(grounded).isEqualTo(raw);
        assertThat(grounded).doesNotContain("[Grounded in established pattern(s):");
    }

    @Test
    @DisplayName("Church D014 / Chalmers D001: Short paragraphs grouped into coherent requirement unit (< 400 chars)")
    void falsifyGroupingFloorPreservesMultiParagraphCoherence() {
        String paragraph1 = "The user initiates the KYC verification flow.";
        String paragraph2 = "The system checks existing records in the central registry.";
        String paragraph3 = "If unverified, the user is prompted to upload an identity document.";

        String composite = paragraph1 + "\n\n" + paragraph2 + "\n\n" + paragraph3;
        List<String> units = RequirementGroundingService.splitIntoRequirementUnits(composite);

        assertThat(units).hasSize(1);
        assertThat(units.get(0)).contains(paragraph1, paragraph2, paragraph3);
    }
}
