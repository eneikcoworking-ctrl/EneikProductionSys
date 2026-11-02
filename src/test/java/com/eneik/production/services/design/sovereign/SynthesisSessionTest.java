package com.eneik.production.services.design.sovereign;

import com.eneik.production.services.design.sovereign.domain.CorrectionDirective;
import com.eneik.production.services.design.sovereign.domain.DeonticGateResult;
import com.eneik.production.services.design.sovereign.domain.DesignTokenSet;
import com.eneik.production.services.design.sovereign.domain.ProductArchetype;
import com.eneik.production.services.design.sovereign.domain.SynthesisSession;
import com.eneik.production.services.design.sovereign.domain.SynthesisStatus;
import com.eneik.production.services.design.sovereign.domain.SynthesizedDomDocument;
import com.eneik.production.services.design.sovereign.domain.VisionAuditReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SynthesisSessionTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-10-27T04:20:00Z");

    @Test
    @DisplayName("Initializes synthesis session in INITIALIZED status at round 0")
    void testSessionInitialization() {
        SynthesisSession session = SynthesisSession.create(ProductArchetype.DEV_TOOLS, FIXED_NOW);

        assertNotNull(session.getId());
        assertEquals(ProductArchetype.DEV_TOOLS, session.getArchetype());
        assertEquals(SynthesisStatus.INITIALIZED, session.getStatus());
        assertEquals(0, session.getCurrentRound());
        assertNull(session.getLatestDomDocument());
        assertNull(session.getLatestAuditReport());
    }

    @Test
    @DisplayName("Transitions cleanly through synthesis lifecycle to APPROVED on passing audit")
    void testSuccessfulApprovalOnFirstRound() {
        SynthesisSession session = SynthesisSession.create(ProductArchetype.FIN_TECH, FIXED_NOW);

        session.transitionToSynthesizing();
        assertEquals(SynthesisStatus.SYNTHESIZING, session.getStatus());

        SynthesizedDomDocument doc = SynthesizedDomDocument.create("<html><body>Mockup</body></html>", session.getCurrentRound());
        session.recordDomAndTransitionToAuditing(doc);
        assertEquals(SynthesisStatus.AUDITING, session.getStatus());
        assertEquals(doc, session.getLatestDomDocument());

        VisionAuditReport passingReport = new VisionAuditReport(
                9.5,
                new DeonticGateResult(true, true, true, true),
                List.of(),
                session.getCurrentRound()
        );

        SynthesisStatus finalStatus = session.evaluateAuditAndTransition(passingReport);
        assertEquals(SynthesisStatus.COMPLETED_APPROVED, finalStatus);
        assertEquals(SynthesisStatus.COMPLETED_APPROVED, session.getStatus());
        assertEquals(0, session.getCurrentRound());
        assertTrue(session.getStatus().isTerminal());
    }

    @Test
    @DisplayName("Iterates up to max 3 refinement rounds and terminates at COMPLETED_FALLBACK if score remains low")
    void testMaxRoundsLoopTermination() {
        SynthesisSession session = SynthesisSession.create(ProductArchetype.EDITORIAL, FIXED_NOW);

        DeonticGateResult failingGate = new DeonticGateResult(true, false, true, true);
        VisionAuditReport failingReport = new VisionAuditReport(
                8.0,
                failingGate,
                List.of(new CorrectionDirective(".hero", "overflow: hidden", "375px mobile scroll", 0)),
                0
        );

        // Round 0 -> 1
        session.transitionToSynthesizing();
        session.recordDomAndTransitionToAuditing(SynthesizedDomDocument.create("<html0>", 0));
        SynthesisStatus status1 = session.evaluateAuditAndTransition(failingReport);
        assertEquals(SynthesisStatus.REFINING, status1);
        assertEquals(1, session.getCurrentRound());

        // Round 1 -> 2
        session.transitionToSynthesizing();
        session.recordDomAndTransitionToAuditing(SynthesizedDomDocument.create("<html1>", 1));
        SynthesisStatus status2 = session.evaluateAuditAndTransition(failingReport);
        assertEquals(SynthesisStatus.REFINING, status2);
        assertEquals(2, session.getCurrentRound());

        // Round 2 -> 3
        session.transitionToSynthesizing();
        session.recordDomAndTransitionToAuditing(SynthesizedDomDocument.create("<html2>", 2));
        SynthesisStatus status3 = session.evaluateAuditAndTransition(failingReport);
        assertEquals(SynthesisStatus.REFINING, status3);
        assertEquals(3, session.getCurrentRound());

        // Round 3 -> COMPLETED_FALLBACK (Loop max limit reached)
        session.transitionToSynthesizing();
        session.recordDomAndTransitionToAuditing(SynthesizedDomDocument.create("<html3>", 3));
        SynthesisStatus status4 = session.evaluateAuditAndTransition(failingReport);

        assertEquals(SynthesisStatus.COMPLETED_FALLBACK, status4);
        assertEquals(SynthesisStatus.COMPLETED_FALLBACK, session.getStatus());
        assertEquals(3, session.getCurrentRound());
        assertTrue(session.getStatus().isTerminal());
    }

    @Test
    @DisplayName("Rejects illegal status transition with IllegalStateException")
    void testIllegalStateTransitions() {
        SynthesisSession session = SynthesisSession.create(ProductArchetype.CONSUMER, FIXED_NOW);

        // Cannot evaluate audit directly from INITIALIZED
        VisionAuditReport report = new VisionAuditReport(
                9.5,
                new DeonticGateResult(true, true, true, true),
                List.of(),
                0
        );
        assertThrows(IllegalStateException.class, () -> session.evaluateAuditAndTransition(report));

        // Cannot record DOM directly from INITIALIZED
        assertThrows(
                IllegalStateException.class,
                () -> session.recordDomAndTransitionToAuditing(SynthesizedDomDocument.create("<html></html>", 0))
        );
    }

    @Test
    @DisplayName("Enforces minimum 7.1 APCA/WCAG contrast ratio in DesignTokenSet")
    void testContrastRatioInvariant() {
        Map<String, String> clamps = Map.of("title", "clamp(1.5rem, 5vw, 3rem)");

        // Valid contrast ratio >= 7.0
        assertDoesNotThrow(() -> new DesignTokenSet(
                ProductArchetype.DEV_TOOLS,
                clamps,
                "#08090C",
                "#11141C",
                "border-white/[0.07]",
                7.5
        ));

        // Invalid contrast ratio < 7.0
        assertThrows(IllegalArgumentException.class, () -> new DesignTokenSet(
                ProductArchetype.DEV_TOOLS,
                clamps,
                "#08090C",
                "#11141C",
                "border-white/[0.07]",
                5.2
        ));
    }
}
