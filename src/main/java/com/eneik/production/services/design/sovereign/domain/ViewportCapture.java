package com.eneik.production.services.design.sovereign.domain;

import java.util.Base64;
import java.util.Objects;

/**
 * Screenshot rendering capture payload for a specific viewport, carrying both
 * the cryptographic SHA-256 hash and the real PNG bytes (Base64-encoded).
 */
public record ViewportCapture(
        ViewportType viewport,
        String screenshotHash,
        int measuredScrollWidthPx,
        String screenshotBase64
) {
    public ViewportCapture {
        Objects.requireNonNull(viewport, "viewport cannot be null");
        Objects.requireNonNull(screenshotHash, "screenshotHash cannot be null");
        if (measuredScrollWidthPx < 0) {
            throw new IllegalArgumentException("measuredScrollWidthPx cannot be negative");
        }
    }

    public ViewportCapture(ViewportType viewport, String screenshotHash, int measuredScrollWidthPx) {
        this(viewport, screenshotHash, measuredScrollWidthPx, null);
    }

    public boolean hasHorizontalOverflow() {
        return measuredScrollWidthPx > viewport.getWidthPx();
    }

    public byte[] decodedScreenshotBytes() {
        if (screenshotBase64 == null || screenshotBase64.isBlank()) {
            return new byte[0];
        }
        try {
            return Base64.getDecoder().decode(screenshotBase64);
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }
}
