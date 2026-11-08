package com.eneik.production.services.worker.verification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Goldman Level of Abstraction Lock (D010) & Baudrillard Simulacra Detector.
 * Audits code patches, synthesized DOMs, and generated project artifacts across all factory
 * workshops to detect and reject infantile stub patterns (cardboard mocks, 1x1 pixel stubs,
 * fake DNS domains, and tautological checks).
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

    // 3. Tautological checks that mock measurement instead of computing reality
    private static final Pattern TAUTOLOGICAL_MOCK_CHECKS_REGEX = Pattern.compile(
            "data-contrast-fail=\"true\"|contains\\(\"w-\\[1440px\\]\"\\)|//\\s*stub\\b|//\\s*mock\\b|TODO:?\\s*implement"
    );

    // 4. Generic boilerplate UI slop masquerading as domain logic
    private static final Pattern GENERIC_SLOP_STRINGS_REGEX = Pattern.compile(
            "(?i)\\b(lorem ipsum dolor sit amet|explore dashboard|view documentation|interactive session request)\\b"
    );

    public record StubAuditReport(
            boolean cleanOfStubs,
            List<String> detectedSimulacra,
            double realityScore
    ) {
        public boolean hasSimulacra() {
            return !cleanOfStubs;
        }
    }

    public StubAuditReport auditArtifact(String content, String artifactName) {
        if (content == null || content.isBlank()) {
            return new StubAuditReport(true, List.of(), 10.0);
        }

        List<String> findings = new ArrayList<>();

        if (ONE_PIXEL_STUB_REGEX.matcher(content).find()) {
            findings.add("D010 VIOLATION: 1x1 fallback pixel stub detected in " + artifactName);
        }

        if (DEAD_MOCK_DOMAINS_REGEX.matcher(content).find()) {
            findings.add("D010 VIOLATION: Dead placeholder domain detected in " + artifactName);
        }

        if (TAUTOLOGICAL_MOCK_CHECKS_REGEX.matcher(content).find()) {
            findings.add("D010 VIOLATION: Tautological mock/stub check detected in " + artifactName);
        }

        if (GENERIC_SLOP_STRINGS_REGEX.matcher(content).find()) {
            findings.add("D010 VIOLATION: Generic boilerplate UI slop detected in " + artifactName);
        }

        boolean clean = findings.isEmpty();
        double score = Math.max(0.0, 10.0 - (findings.size() * 2.5));

        if (!clean) {
            log.warn("Baudrillard Simulacra Detector flagged {} stubs in {}: {}", findings.size(), artifactName, findings);
        }

        return new StubAuditReport(clean, List.copyOf(findings), score);
    }
}
