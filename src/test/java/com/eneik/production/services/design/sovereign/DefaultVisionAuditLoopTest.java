package com.eneik.production.services.design.sovereign;

import com.eneik.production.services.design.sovereign.domain.SynthesizedDomDocument;
import com.eneik.production.services.design.sovereign.domain.ViewportCapture;
import com.eneik.production.services.design.sovereign.domain.ViewportType;
import com.eneik.production.services.design.sovereign.domain.VisionAuditReport;
import com.eneik.production.services.design.sovereign.service.DefaultVisionAuditLoop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultVisionAuditLoopTest {

    private DefaultVisionAuditLoop auditLoop;

    @BeforeEach
    void setUp() {
        auditLoop = new DefaultVisionAuditLoop();
    }

    @Test
    @DisplayName("Should pass all gates for clean obsidian document with focus-visible and no slop")
    void testCleanDocumentPassesAllGates() {
        String cleanHtml = """
                <!DOCTYPE html>
                <html lang="en" class="dark">
                <head><title>Sovereign Design</title></head>
                <body class="bg-[#08090C] text-[#F3F4F6]">
                  <header role="banner">
                    <nav role="navigation">
                      <a href="#main" class="focus-visible:ring-2 focus-visible:ring-emerald-400">Link</a>
                    </nav>
                  </header>
                  <main role="main" id="main">
                    <button class="bg-emerald-500 text-slate-950 focus-visible:outline-none focus-visible:ring-2">Click</button>
                  </main>
                </body>
                </html>
                """;

        SynthesizedDomDocument doc = SynthesizedDomDocument.create(cleanHtml, 0);
        List<ViewportCapture> captures = List.of(
                new ViewportCapture(ViewportType.DESKTOP_1440, "hash1", 1440),
                new ViewportCapture(ViewportType.MOBILE_375, "hash2", 375)
        );

        VisionAuditReport report = auditLoop.auditDocument(doc, captures);

        assertNotNull(report);
        assertTrue(report.deonticGateResult().allGatesPassed());
        assertEquals(10.0, report.overallScore());
        assertTrue(report.correctionDirectives().isEmpty());
    }

    @Test
    @DisplayName("BARCAN-TAG-11: Should reject pseudo-optical noise and issue correction directives")
    void testDetectPseudoOpticalNoise() {
        String noiseHtml = "<html><body><span class=\"optical-metadata\">Shot on Leica 50mm f/1.4 GPS 37°46'N</span></body></html>";
        SynthesizedDomDocument doc = SynthesizedDomDocument.create(noiseHtml, 0);

        VisionAuditReport report = auditLoop.auditDocument(doc, List.of());

        assertFalse(report.deonticGateResult().zeroLeicaGpsNoise());
        assertFalse(report.deonticGateResult().allGatesPassed());
        assertTrue(report.overallScore() < 10.0);
        assertTrue(report.correctionDirectives().stream().anyMatch(d -> d.reason().contains("BARCAN-TAG-11")));
    }

    @Test
    @DisplayName("BARCAN-TAG-11: Should reject AI-slop color palettes (indigo/purple/violet)")
    void testDetectAiSlopColors() {
        String slopHtml = "<html><body><button class=\"bg-indigo-600 hover:bg-indigo-500 focus-visible:ring-indigo-400\">Click</button></body></html>";
        SynthesizedDomDocument doc = SynthesizedDomDocument.create(slopHtml, 0);

        VisionAuditReport report = auditLoop.auditDocument(doc, List.of());

        assertTrue(report.correctionDirectives().stream().anyMatch(d -> d.reason().contains("anti-slop")));
    }

    @Test
    @DisplayName("Should detect mobile horizontal overflow and issue correction directive")
    void testDetectMobileHorizontalOverflow() {
        String html = "<html><body><div style=\"width:500px\">Wide Content</div></body></html>";
        SynthesizedDomDocument doc = SynthesizedDomDocument.create(html, 0);
        List<ViewportCapture> captures = List.of(
                new ViewportCapture(ViewportType.MOBILE_375, "hash", 500)
        );

        VisionAuditReport report = auditLoop.auditDocument(doc, captures);

        assertFalse(report.deonticGateResult().noMobileHorizontalScroll());
        assertTrue(report.correctionDirectives().stream().anyMatch(d -> d.reason().contains("Mobile 375px viewport horizontal overflow")));
    }
}
