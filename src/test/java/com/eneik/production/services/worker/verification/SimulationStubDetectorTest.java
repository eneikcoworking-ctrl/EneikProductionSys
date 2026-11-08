package com.eneik.production.services.worker.verification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimulationStubDetectorTest {

    private SimulationStubDetector detector;

    @BeforeEach
    void setUp() {
        detector = new SimulationStubDetector();
    }

    @Test
    @DisplayName("D010: Passes clean authentic production artifacts with reality score 10.0")
    void testCleanArtifactPasses() {
        String authenticContent = """
                <!DOCTYPE html>
                <html>
                <body>
                  <h1>FinTech Settlement Ledger</h1>
                  <table class="w-full">
                    <tr><td>TX-001</td><td>VERIFIED</td></tr>
                  </table>
                  <img src="data:image/svg+xml;utf8,<svg></svg>" alt="Topology Preview">
                </body>
                </html>
                """;

        SimulationStubDetector.StubAuditReport report = detector.auditArtifact(authenticContent, "mockup.html");

        assertTrue(report.cleanOfStubs());
        assertFalse(report.hasSimulacra());
        assertEquals(10.0, report.realityScore(), 0.001);
        assertTrue(report.detectedSimulacra().isEmpty());
    }

    @Test
    @DisplayName("D010: Detects and rejects 1x1 pixel fallback stubs")
    void testDetectsOnePixelStub() {
        String stubContent = "byte[] screenshot = Base64.getDecoder().decode(\"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==\");";

        SimulationStubDetector.StubAuditReport report = detector.auditArtifact(stubContent, "DesignAssetService.java");

        assertFalse(report.cleanOfStubs());
        assertTrue(report.hasSimulacra());
        assertTrue(report.detectedSimulacra().stream().anyMatch(s -> s.contains("1x1 fallback pixel")));
        assertTrue(report.realityScore() < 10.0);
    }

    @Test
    @DisplayName("D010: Detects and rejects dead mock placeholder domains")
    void testDetectsDeadMockDomains() {
        String mockDomainContent = "<img src=\"https://assets.eneik.com/default-hero.webp\" alt=\"Hero\">";

        SimulationStubDetector.StubAuditReport report = detector.auditArtifact(mockDomainContent, "mockup.html");

        assertFalse(report.cleanOfStubs());
        assertTrue(report.detectedSimulacra().stream().anyMatch(s -> s.contains("Dead placeholder domain")));
    }

    @Test
    @DisplayName("D010: Detects and rejects tautological checks and dummy strings")
    void testDetectsTautologicalChecksAndBoilerplateSlop() {
        String slopContent = """
                if (html.contains("data-contrast-fail=\\"true\\"")) {
                    return false;
                }
                // TODO: implement real contrast calculation later
                return "<button>Explore Dashboard</button>";
                """;

        SimulationStubDetector.StubAuditReport report = detector.auditArtifact(slopContent, "DefaultVisionAuditLoop.java");

        assertFalse(report.cleanOfStubs());
        assertEquals(2, report.detectedSimulacra().size());
        assertTrue(report.detectedSimulacra().stream().anyMatch(s -> s.contains("Tautological mock/stub check")));
        assertTrue(report.detectedSimulacra().stream().anyMatch(s -> s.contains("Generic boilerplate UI slop")));
    }
}
