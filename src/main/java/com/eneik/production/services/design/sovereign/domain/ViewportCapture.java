package com.eneik.production.services.design.sovereign.domain;

import java.util.Objects;

/**
 * Screenshot rendering capture payload for a specific viewport.
 */
public record ViewportCapture(
        ViewportType viewport,
        String screenshotHash,
        int measuredScrollWidthPx
) {
    public ViewportCapture {
        Objects.requireNonNull(viewport, "viewport cannot be null");
        Objects.requireNonNull(screenshotHash, "screenshotHash cannot be null");
        if (measuredScrollWidthPx < 0) {
            throw new IllegalArgumentException("measuredScrollWidthPx cannot be negative");
        }
    }

    public boolean hasHorizontalOverflow() {
        return measuredScrollWidthPx > viewport.getWidthPx();
    }
}
