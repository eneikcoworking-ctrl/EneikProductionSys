package com.eneik.production.services.design.sovereign.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Synthesized semantic HTML5 / Tailwind document artifact.
 */
public record SynthesizedDomDocument(
        String htmlContent,
        int roundIndex,
        String domFingerprint
) {
    public SynthesizedDomDocument {
        Objects.requireNonNull(htmlContent, "htmlContent cannot be null");
        if (roundIndex < 0) {
            throw new IllegalArgumentException("roundIndex cannot be negative");
        }
        if (domFingerprint == null || domFingerprint.isBlank()) {
            domFingerprint = computeFingerprint(htmlContent);
        }
    }

    public static SynthesizedDomDocument create(String htmlContent, int roundIndex) {
        return new SynthesizedDomDocument(htmlContent, roundIndex, computeFingerprint(htmlContent));
    }

    private static String computeFingerprint(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }
}
