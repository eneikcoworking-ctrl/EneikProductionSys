package com.eneik.production.services.worker.verification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Goldman Level of Abstraction Lock (D010) & Baudrillard Simulacra Detector.
 * Audits code patches, synthesized DOMs, and generated project artifacts across all factory
 * workshops to detect and reject infantile stub patterns (cardboard mocks, 1x1 pixel stubs,
 * fake DNS domains, tautological checks, simulated timeouts, and mock streaming).
 */
@Component
public class SimulationStubDetector {

    private static final Logger log = LoggerFactory.getLogger(SimulationStubDetector.class);

    // 1. One-pixel transparent PNG/GIF stubs
    private static final Pattern ONE_PIXEL_STUB_REGEX = Pattern.compile(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42m|R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"
    );

    // 2. Dead placeholder/unresolved mock domains
    private static final Pattern DEAD_MOCK_DOMAINS_REGEX = Pattern.compile(
            "(?i)https?://(?:assets\\.eneik\\.com/default-|via\\.placeholder\\.com|example\\.com/fake)"
    );

    // 3. Tautological checks that mock measurement instead of computing reality & comments declaring stubs/simulations
    private static final Pattern TAUTOLOGICAL_MOCK_CHECKS_REGEX = Pattern.compile(
            "(?i)(data-contrast-fail=\"true\"|contains\\(\"w-\\[1440px\\]\"\\)|//\\s*(?:stub|mock|fake|dummy|simulate|simulated|simulation|placeholder|fixture|hardcoded)\\b|/\\*\\s*(?:stub|mock|fake|dummy|simulate|placeholder)\\b|TODO:?\\s*(?:implement|mock|fake|stub|fixme))"
    );

    // 4. Simulated timeouts or fake async responses masquerading as real backend integration
    private static final Pattern SIMULATED_DELAY_OR_STREAMING_REGEX = Pattern.compile(
            "(?i)(?:setTimeout\\s*\\(\\s*\\(\\)\\s*=>\\s*\\{[^}]*(?:replyText|assistantMsg|mock|fake|citations|isSubmitting)|//\\s*simulate\\s+(?:streaming|assistant|response))"
    );

    // 5. Generic boilerplate UI slop masquerading as domain logic
    private static final Pattern GENERIC_SLOP_STRINGS_REGEX = Pattern.compile(
            "(?i)\\b(lorem ipsum dolor sit amet|explore dashboard|view documentation|interactive session request)\\b"
    );

    public record SimulacrumFinding(
            String artifactPath,
            int lineNumber,
            String violationType,
            String snippet
    ) {}

    public record StubAuditReport(
            boolean cleanOfStubs,
            List<String> detectedSimulacra,
            List<SimulacrumFinding> detailedFindings,
            double realityScore
    ) {
        public boolean hasSimulacra() {
            return !cleanOfStubs;
        }

        public StubAuditReport(boolean cleanOfStubs, List<String> detectedSimulacra, double realityScore) {
            this(cleanOfStubs, detectedSimulacra, List.of(), realityScore);
        }
    }

    public StubAuditReport auditArtifact(String content, String artifactName) {
        if (content == null || content.isBlank()) {
            return new StubAuditReport(true, List.of(), List.of(), 10.0);
        }

        List<String> findings = new ArrayList<>();
        List<SimulacrumFinding> detailedFindings = new ArrayList<>();

        if (ONE_PIXEL_STUB_REGEX.matcher(content).find()) {
            String desc = "D010 VIOLATION: 1x1 fallback pixel stub detected in " + artifactName;
            findings.add(desc);
            detailedFindings.add(new SimulacrumFinding(artifactName, 1, "ONE_PIXEL_STUB", desc));
        }

        if (DEAD_MOCK_DOMAINS_REGEX.matcher(content).find()) {
            String desc = "D010 VIOLATION: Dead placeholder domain detected in " + artifactName;
            findings.add(desc);
            detailedFindings.add(new SimulacrumFinding(artifactName, 1, "DEAD_MOCK_DOMAIN", desc));
        }

        if (SIMULATED_DELAY_OR_STREAMING_REGEX.matcher(content).find()) {
            String desc = "D010 VIOLATION: Simulated async/streaming delay stub detected in " + artifactName;
            findings.add(desc);
            detailedFindings.add(new SimulacrumFinding(artifactName, 1, "SIMULATED_ASYNC_DELAY", desc));
        }

        if (GENERIC_SLOP_STRINGS_REGEX.matcher(content).find()) {
            String desc = "D010 VIOLATION: Generic boilerplate UI slop detected in " + artifactName;
            findings.add(desc);
            detailedFindings.add(new SimulacrumFinding(artifactName, 1, "GENERIC_SLOP", desc));
        }

        String[] lines = content.split("\r?\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;

            Matcher tautologyMatcher = TAUTOLOGICAL_MOCK_CHECKS_REGEX.matcher(line);
            if (tautologyMatcher.find()) {
                String desc = "D010 VIOLATION: Tautological mock/stub check detected in " + artifactName;
                if (!findings.contains(desc)) {
                    findings.add(desc);
                }
                detailedFindings.add(new SimulacrumFinding(artifactName, lineNum, "TAUTOLOGICAL_MOCK_CHECK", line.trim()));
            }
        }

        boolean clean = findings.isEmpty();
        double score = Math.max(0.0, 10.0 - (findings.size() * 2.5));

        if (!clean) {
            log.warn("Baudrillard Simulacra Detector flagged {} stubs in {}: {}", findings.size(), artifactName, findings);
        }

        return new StubAuditReport(clean, List.copyOf(findings), List.copyOf(detailedFindings), score);
    }

    /**
     * Scans all production source files in a repository directory, ignoring tests, build directories,
     * and version control metadata.
     */
    public List<SimulacrumFinding> auditRepositorySources(Path repoRoot) {
        if (repoRoot == null || !Files.isDirectory(repoRoot)) {
            return List.of();
        }

        List<SimulacrumFinding> allFindings = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(repoRoot)) {
            stream.filter(Files::isRegularFile)
                  .filter(this::isProductSourceFile)
                  .forEach(file -> {
                      try {
                          String relPath = repoRoot.relativize(file).toString().replace('\\', '/');
                          String content = Files.readString(file);
                          StubAuditReport report = auditArtifact(content, relPath);
                          if (report.hasSimulacra()) {
                              allFindings.addAll(report.detailedFindings());
                          }
                      } catch (Exception e) {
                          log.debug("Error auditing {}: {}", file, e.getMessage());
                      }
                  });
        } catch (IOException e) {
            log.error("Failed to audit repository sources at {}: {}", repoRoot, e.getMessage());
        }

        return allFindings;
    }

    private boolean isProductSourceFile(Path path) {
        String p = path.toString().toLowerCase(java.util.Locale.ROOT).replace('\\', '/');
        if (p.contains("/test/") || p.contains("/tests/") || p.contains(".test.") || p.contains(".spec.")
                || p.contains("__tests__") || p.contains("/target/") || p.contains("/node_modules/")
                || p.contains("/.git/") || p.contains("/.eneik/") || p.contains("/dist/") || p.contains("/build/")) {
            return false;
        }
        return p.endsWith(".svelte") || p.endsWith(".vue") || p.endsWith(".jsx") || p.endsWith(".tsx")
                || p.endsWith(".js") || p.endsWith(".ts") || p.endsWith(".html") || p.endsWith(".java")
                || p.endsWith(".py") || p.endsWith(".go") || p.endsWith(".rs");
    }
}
