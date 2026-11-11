package com.eneik.production.services.worker.verification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
        assertTrue(report.detectedSimulacra().size() >= 2);
        assertTrue(report.detectedSimulacra().stream().anyMatch(s -> s.contains("Tautological mock/stub check")));
        assertTrue(report.detectedSimulacra().stream().anyMatch(s -> s.contains("Generic boilerplate UI slop")));
    }

    @Test
    @DisplayName("D010 & D008: Detects simulated async assistant delays and streaming stubs")
    void testDetectsSimulatedAssistantStreamingStubs() {
        String stubSvelteContent = """
                <script>
                  let messages = [];
                  let chatQuery = '';
                  let isSubmitting = false;

                  function sendChatMessage() {
                    isSubmitting = true;
                    // Simulate streaming / responsive DeepSeek assistant integration
                    setTimeout(() => {
                      let replyText = 'По вашему запросу проверена нормативная база.';
                      let citations = ['ГОСТ Р 7.0.97-2016'];
                      messages = [...messages, { sender: 'assistant', text: replyText, citations }];
                      isSubmitting = false;
                    }, 600);
                  }
                </script>
                """;

        SimulationStubDetector.StubAuditReport report = detector.auditArtifact(stubSvelteContent, "Internal.svelte");

        assertFalse(report.cleanOfStubs());
        assertTrue(report.hasSimulacra());
        assertTrue(report.detailedFindings().size() >= 2);
        assertTrue(report.detailedFindings().stream().anyMatch(f -> f.violationType().equals("SIMULATED_ASYNC_DELAY")));
        assertTrue(report.detailedFindings().stream().anyMatch(f -> f.violationType().equals("TAUTOLOGICAL_MOCK_CHECK")));
    }

    @Test
    @DisplayName("D010 & D008: Audits entire repository source tree ignoring tests")
    void testAuditRepositorySources(@TempDir Path tempDir) throws IOException {
        Path frontendSrc = tempDir.resolve("frontend/src/components");
        Files.createDirectories(frontendSrc);
        Path testDir = tempDir.resolve("frontend/src/__tests__");
        Files.createDirectories(testDir);

        // Offending production file
        Path prodFile = frontendSrc.resolve("Assistant.svelte");
        Files.writeString(prodFile, "// Simulate assistant response\nconst x = 1;");

        // Allowed test file with fixtures
        Path testFile = testDir.resolve("Assistant.spec.ts");
        Files.writeString(testFile, "// Mock response for unit test\nconst testFixture = 1;");

        List<SimulationStubDetector.SimulacrumFinding> findings = detector.auditRepositorySources(tempDir);

        assertFalse(findings.isEmpty());
        assertTrue(findings.stream().allMatch(f -> f.artifactPath().equals("frontend/src/components/Assistant.svelte")));
        assertTrue(findings.stream().anyMatch(f -> f.violationType().equals("TAUTOLOGICAL_MOCK_CHECK")));
    }

    @Test
    @DisplayName("D010 Mechanism #8: Detects and rejects leaky model branding and document counter stubs in UI")
    void testDetectsLeakyModelBrandingAndCounters() {
        String leakyUiContent = """
                <div class="chat-header">
                  <h2>Интеллектуальный ассистент на базе DeepSeek-V3</h2>
                  <span class="badge">1713 документов в базе</span>
                  <a href="/archive.zip" class="btn">Скачать архив (15 документов)</a>
                </div>
                """;

        SimulationStubDetector.StubAuditReport report = detector.auditArtifact(leakyUiContent, "frontend/src/App.svelte");

        assertFalse(report.cleanOfStubs());
        assertTrue(report.hasSimulacra());
        assertTrue(report.detailedFindings().stream().anyMatch(f -> f.violationType().equals("LEAKY_MODEL_BRANDING")));
        assertTrue(report.detailedFindings().stream().anyMatch(f -> f.violationType().equals("HARDCODED_DOCUMENT_COUNTER")));
        assertTrue(report.detailedFindings().stream().anyMatch(f -> f.violationType().equals("STATIC_MOCK_ARCHIVE")));
    }

    @Test
    @DisplayName("D002 Mechanism #8: Detects ephemeral chat state without session persistence")
    void testDetectsEphemeralChatWithoutPersistence() {
        String ephemeralChat = """
                <script>
                  let messages = [];
                  function sendChatMessage(text) {
                    messages.push({ role: 'user', content: text });
                  }
                </script>
                """;

        SimulationStubDetector.StubAuditReport report = detector.auditArtifact(ephemeralChat, "frontend/src/Chat.svelte");

        assertFalse(report.cleanOfStubs());
        assertTrue(report.detailedFindings().stream().anyMatch(f -> f.violationType().equals("EPHEMERAL_CHAT_NO_PERSISTENCE")));

        // Persistent chat should pass clean of this check
        String persistentChat = """
                <script>
                  let messages = JSON.parse(localStorage.getItem('chat_history') || '[]');
                  function sendChatMessage(text) {
                    messages.push({ role: 'user', content: text });
                    localStorage.setItem('chat_history', JSON.stringify(messages));
                  }
                </script>
                """;
        SimulationStubDetector.StubAuditReport cleanReport = detector.auditArtifact(persistentChat, "frontend/src/Chat.svelte");
        assertFalse(cleanReport.detailedFindings().stream().anyMatch(f -> f.violationType().equals("EPHEMERAL_CHAT_NO_PERSISTENCE")));
    }
}
