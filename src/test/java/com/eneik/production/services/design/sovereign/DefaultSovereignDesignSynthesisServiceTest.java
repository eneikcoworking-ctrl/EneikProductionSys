package com.eneik.production.services.design.sovereign;

import com.eneik.production.services.design.sovereign.domain.CorrectionDirective;
import com.eneik.production.services.design.sovereign.domain.DeonticGateResult;
import com.eneik.production.services.design.sovereign.domain.DesignTokenSet;
import com.eneik.production.services.design.sovereign.domain.LayoutGridPattern;
import com.eneik.production.services.design.sovereign.domain.ProductArchetype;
import com.eneik.production.services.design.sovereign.domain.SynthesisSession;
import com.eneik.production.services.design.sovereign.domain.SynthesisStatus;
import com.eneik.production.services.design.sovereign.domain.SynthesizedDomDocument;
import com.eneik.production.services.design.sovereign.domain.ViewportCapture;
import com.eneik.production.services.design.sovereign.domain.ViewportType;
import com.eneik.production.services.design.sovereign.domain.VisionAuditReport;
import com.eneik.production.services.design.sovereign.exception.RenderTimeoutException;
import com.eneik.production.services.design.sovereign.service.DefaultSovereignDesignSynthesisService;
import com.eneik.production.services.design.sovereign.service.DesignTokenCompiler;
import com.eneik.production.services.design.sovereign.service.LayoutPatternRegistry;
import com.eneik.production.services.design.sovereign.service.PlaywrightVisionHarness;
import com.eneik.production.services.design.sovereign.service.PrimaryAssetManagementSystem;
import com.eneik.production.services.design.sovereign.service.SemanticLayoutSynthesizer;
import com.eneik.production.services.design.sovereign.service.SovereignProjectAssetResolver;
import com.eneik.production.services.design.sovereign.service.VisionAuditLoop;
import com.eneik.production.services.design.sovereign.telemetry.DesignTelemetryCollector;
import com.eneik.production.services.design.sovereign.telemetry.DesignTelemetrySummary;
import com.eneik.production.services.design.sovereign.telemetry.InMemoryDesignTelemetryCollector;
import com.eneik.production.services.design.sovereign.telemetry.TelemetryEvent;
import com.eneik.production.services.design.sovereign.telemetry.TelemetryEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultSovereignDesignSynthesisServiceTest {

    private DesignTokenCompiler tokenCompiler;
    private LayoutPatternRegistry patternRegistry;
    private SovereignProjectAssetResolver assetService;
    private SemanticLayoutSynthesizer layoutSynthesizer;
    private PlaywrightVisionHarness visionHarness;
    private VisionAuditLoop auditLoop;
    private DesignTelemetryCollector telemetryCollector;
    private PrimaryAssetManagementSystem primaryAssetManagementSystem;
    private DefaultSovereignDesignSynthesisService synthesisService;

    // To track calls to PrimaryAssetManagementSystem
    private boolean assetsSubmitted;
    private String submittedContextKey;
    private SynthesizedDomDocument submittedDocument;
    private List<ViewportCapture> submittedCaptures;

    @BeforeEach
    void setUp() {
        assetsSubmitted = false;
        submittedContextKey = null;
        submittedDocument = null;
        submittedCaptures = null;

        tokenCompiler = archetype -> new DesignTokenSet(
                archetype,
                Map.of("h1", "clamp(2rem, 5vw, 4rem)"),
                "#08090C",
                "#11141C",
                "border-white/[0.07]",
                7.5
        );
        patternRegistry = new LayoutPatternRegistry() {
            @Override
            public LayoutGridPattern resolveDefaultPattern(ProductArchetype archetype) {
                return new LayoutGridPattern("SWISS_7_5", "grid-cols-12", "border-white/[0.07]", true);
            }

            @Override
            public List<LayoutGridPattern> listAvailablePatterns() {
                return List.of(new LayoutGridPattern("SWISS_7_5", "grid-cols-12", "border-white/[0.07]", true));
            }
        };
        assetService = contextKey -> Map.of("hero", "https://assets.eneik.com/hero.png");
        layoutSynthesizer = new SemanticLayoutSynthesizer() {
            @Override
            public SynthesizedDomDocument synthesizeLayout(DesignTokenSet tokenSet, LayoutGridPattern gridPattern, Map<String, String> projectAssetUrls, int roundIndex) {
                return new SynthesizedDomDocument("<html><body>Initial</body></html>", roundIndex, "hash1");
            }

            @Override
            public SynthesizedDomDocument refineLayout(SynthesizedDomDocument previousDocument, DesignTokenSet tokenSet, List<CorrectionDirective> directives, int roundIndex) {
                return new SynthesizedDomDocument("<html><body>Refined</body></html>", roundIndex, "hash2");
            }
        };
        visionHarness = (document, viewports) -> List.of(
                new ViewportCapture(ViewportType.DESKTOP_1440, "img1440", 1440),
                new ViewportCapture(ViewportType.MOBILE_375, "img375", 375)
        );

        Clock fixedClock = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneId.of("UTC"));
        telemetryCollector = new InMemoryDesignTelemetryCollector(fixedClock);

        primaryAssetManagementSystem = (ctx, doc, caps) -> {
            assetsSubmitted = true;
            submittedContextKey = ctx;
            submittedDocument = doc;
            submittedCaptures = caps;
        };
    }

    @Test
    @DisplayName("Should execute synthesis pipeline to approval and record success and audit telemetry events")
    void testExecuteDesignSynthesisApproval() {
        auditLoop = (document, captures) -> new VisionAuditReport(
                9.5,
                new DeonticGateResult(true, true, true, true),
                List.of(),
                0
        );

        synthesisService = new DefaultSovereignDesignSynthesisService(
                tokenCompiler, patternRegistry, assetService, layoutSynthesizer,
                visionHarness, auditLoop, telemetryCollector, primaryAssetManagementSystem
        );

        SynthesisSession session = synthesisService.executeDesignSynthesis(ProductArchetype.DEV_TOOLS, "ctx-123");

        assertNotNull(session);
        assertEquals(SynthesisStatus.COMPLETED_APPROVED, session.getStatus());
        assertEquals(0, session.getCurrentRound());

        List<TelemetryEvent> events = telemetryCollector.getRecordedEvents();
        assertEquals(2, events.size());

        TelemetryEvent auditEvent = events.get(0);
        assertEquals(TelemetryEventType.AUDIT_COMPLETED, auditEvent.eventType());
        assertEquals(ProductArchetype.DEV_TOOLS, auditEvent.archetype());
        assertEquals(9.5, auditEvent.auditScore(), 0.0001);
        assertTrue(auditEvent.deonticGatePassed());

        TelemetryEvent successEvent = events.get(1);
        assertEquals(TelemetryEventType.GENERATION_SUCCESS, successEvent.eventType());
        assertEquals(ProductArchetype.DEV_TOOLS, successEvent.archetype());
        assertEquals(0, successEvent.iterationCount());
        assertEquals(9.5, successEvent.auditScore(), 0.0001);

        DesignTelemetrySummary summary = telemetryCollector.getSummary();
        assertEquals(1, summary.totalRequests());
        assertEquals(1, summary.successCount());
        assertEquals(0, summary.failureCount());
        assertEquals(1, summary.auditCount());
        assertEquals(1.0, summary.overallSuccessRate(), 0.0001);

        assertTrue(assetsSubmitted, "Assets should have been submitted");
        assertEquals("ctx-123", submittedContextKey);
        assertNotNull(submittedDocument);
        assertNotNull(submittedCaptures);
    }

    @Test
    @DisplayName("Should execute synthesis iterative refinement loop up to approval and record multiple audit telemetry events")
    void testExecuteDesignSynthesisRefinementLoop() {
        auditLoop = (document, captures) -> {
            if (document.roundIndex() == 0) {
                return new VisionAuditReport(8.0, new DeonticGateResult(true, true, true, true), List.of(), 0);
            } else {
                return new VisionAuditReport(9.3, new DeonticGateResult(true, true, true, true), List.of(), 1);
            }
        };

        synthesisService = new DefaultSovereignDesignSynthesisService(
                tokenCompiler, patternRegistry, assetService, layoutSynthesizer,
                visionHarness, auditLoop, telemetryCollector, primaryAssetManagementSystem
        );

        SynthesisSession session = synthesisService.executeDesignSynthesis(ProductArchetype.FIN_TECH, "ctx-fin");

        assertEquals(SynthesisStatus.COMPLETED_APPROVED, session.getStatus());
        assertEquals(1, session.getCurrentRound());

        List<TelemetryEvent> events = telemetryCollector.getRecordedEvents();
        assertEquals(3, events.size()); // Audit round 0, Audit round 1, Success

        assertEquals(TelemetryEventType.AUDIT_COMPLETED, events.get(0).eventType());
        assertEquals(0, events.get(0).iterationCount());

        assertEquals(TelemetryEventType.AUDIT_COMPLETED, events.get(1).eventType());
        assertEquals(1, events.get(1).iterationCount());

        assertEquals(TelemetryEventType.GENERATION_SUCCESS, events.get(2).eventType());
        assertEquals(1, events.get(2).iterationCount());

        assertTrue(assetsSubmitted, "Assets should have been submitted after refinement");
        assertEquals("ctx-fin", submittedContextKey);
        assertNotNull(submittedDocument);
    }

    @Test
    @DisplayName("Given a mockup requiring multiple audit iterations, generation completion metric correctly reports exact number of iterations performed")
    void testMultiIterationAuditLoopIterationCountTelemetry() {
        // Audit loop requires 3 rounds (round index 0, 1, 2) before approval on round 2
        auditLoop = (document, captures) -> {
            if (document.roundIndex() == 0) {
                return new VisionAuditReport(8.0, new DeonticGateResult(true, true, true, true), List.of(), 0);
            } else if (document.roundIndex() == 1) {
                return new VisionAuditReport(8.9, new DeonticGateResult(true, true, true, true), List.of(), 1);
            } else {
                return new VisionAuditReport(9.5, new DeonticGateResult(true, true, true, true), List.of(), 2);
            }
        };

        synthesisService = new DefaultSovereignDesignSynthesisService(
                tokenCompiler, patternRegistry, assetService, layoutSynthesizer,
                visionHarness, auditLoop, telemetryCollector, primaryAssetManagementSystem
        );

        SynthesisSession session = synthesisService.executeDesignSynthesis(ProductArchetype.EDITORIAL, "ctx-multi");

        assertEquals(SynthesisStatus.COMPLETED_APPROVED, session.getStatus());
        assertEquals(2, session.getCurrentRound());

        List<TelemetryEvent> events = telemetryCollector.getRecordedEvents();
        // 3 audit completion events (round 0, 1, 2) + 1 generation success event
        assertEquals(4, events.size());

        // Verify iteration indices in recorded audit events
        assertEquals(0, events.get(0).iterationCount());
        assertEquals(1, events.get(1).iterationCount());
        assertEquals(2, events.get(2).iterationCount());

        // Verify generation success event metric reports exact iteration count (2)
        TelemetryEvent successEvent = events.get(3);
        assertEquals(TelemetryEventType.GENERATION_SUCCESS, successEvent.eventType());
        assertEquals(2, successEvent.iterationCount());

        DesignTelemetrySummary summary = telemetryCollector.getSummary();
        assertEquals(2.0, summary.averageIterations(), 0.0001);
    }

    @Test
    @DisplayName("Should record generation failure telemetry event when pipeline encounters unhandled exception")
    void testExecuteDesignSynthesisExceptionFailure() {
        auditLoop = (document, captures) -> {
            throw new RuntimeException("Playwright vision harness connection timeout");
        };

        synthesisService = new DefaultSovereignDesignSynthesisService(
                tokenCompiler, patternRegistry, assetService, layoutSynthesizer,
                visionHarness, auditLoop, telemetryCollector, primaryAssetManagementSystem
        );

        SynthesisSession session = synthesisService.executeDesignSynthesis(ProductArchetype.CONSUMER, "ctx-err");

        assertEquals(SynthesisStatus.FAILED, session.getStatus());
        assertEquals("Playwright vision harness connection timeout", session.getFailureReason());

        List<TelemetryEvent> events = telemetryCollector.getRecordedEvents();
        assertEquals(1, events.size());

        TelemetryEvent failEvent = events.get(0);
        assertEquals(TelemetryEventType.GENERATION_FAILURE, failEvent.eventType());
        assertEquals(ProductArchetype.CONSUMER, failEvent.archetype());
        assertEquals("Playwright vision harness connection timeout", failEvent.metadata().get("failureReason"));

        DesignTelemetrySummary summary = telemetryCollector.getSummary();
        assertEquals(1, summary.totalRequests());
        assertEquals(0, summary.successCount());
        assertEquals(1, summary.failureCount());
        assertEquals(0.0, summary.overallSuccessRate(), 0.0001);

        assertFalse(assetsSubmitted, "Assets should not have been submitted on failure");
    }

    @Test
    @DisplayName("Should handle rendering timeout exception gracefully and transition session to FAILED without submitting assets")
    void testExecuteDesignSynthesisRenderTimeoutRecovery() {
        PlaywrightVisionHarness timingOutHarness = (doc, viewports) -> {
            throw new RenderTimeoutException("Rendering timed out after 3000ms");
        };

        auditLoop = (document, captures) -> new VisionAuditReport(
                9.5, new DeonticGateResult(true, true, true, true), List.of(), 0
        );

        synthesisService = new DefaultSovereignDesignSynthesisService(
                tokenCompiler, patternRegistry, assetService, layoutSynthesizer,
                timingOutHarness, auditLoop, telemetryCollector, primaryAssetManagementSystem
        );

        SynthesisSession session = synthesisService.executeDesignSynthesis(ProductArchetype.CONSUMER, "ctx-timeout");

        assertEquals(SynthesisStatus.FAILED, session.getStatus());
        assertEquals("Rendering timed out after 3000ms", session.getFailureReason());

        List<TelemetryEvent> events = telemetryCollector.getRecordedEvents();
        assertEquals(1, events.size());
        assertEquals(TelemetryEventType.GENERATION_FAILURE, events.get(0).eventType());

        assertFalse(assetsSubmitted, "Assets should not have been submitted when render timeout occurs");
    }

    @Test
    @DisplayName("Should transition session to COMPLETED_FALLBACK and submit assets when audit iteration limit (3 rounds) is reached without passing score")
    void testExecuteDesignSynthesisAuditIterationLimitFallback() {
        // Failing audit report continuously
        auditLoop = (document, captures) -> new VisionAuditReport(
                7.5,
                new DeonticGateResult(true, false, true, true), // horizontal scroll failed
                List.of(new CorrectionDirective(".container", "overflow-x: hidden", "Fix scroll overflow", document.roundIndex())),
                document.roundIndex()
        );

        synthesisService = new DefaultSovereignDesignSynthesisService(
                tokenCompiler, patternRegistry, assetService, layoutSynthesizer,
                visionHarness, auditLoop, telemetryCollector, primaryAssetManagementSystem
        );

        SynthesisSession session = synthesisService.executeDesignSynthesis(ProductArchetype.FIN_TECH, "ctx-limit");

        assertEquals(SynthesisStatus.COMPLETED_FALLBACK, session.getStatus());
        assertEquals(3, session.getCurrentRound());

        List<TelemetryEvent> events = telemetryCollector.getRecordedEvents();
        // 4 audit completion events (rounds 0, 1, 2, 3) + 1 generation success event (fallback)
        assertEquals(5, events.size());

        TelemetryEvent successEvent = events.get(4);
        assertEquals(TelemetryEventType.GENERATION_SUCCESS, successEvent.eventType());
        assertEquals(3, successEvent.iterationCount());

        assertTrue(assetsSubmitted, "Fallback assets should be submitted to asset service");
        assertEquals("ctx-limit", submittedContextKey);
        assertNotNull(submittedDocument);
    }
}
