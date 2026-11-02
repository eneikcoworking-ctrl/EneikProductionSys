package com.eneik.production.services.design.sovereign.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable design tokens computed for a product archetype.
 */
public record DesignTokenSet(
        ProductArchetype archetype,
        Map<String, String> typographyClamps,
        String baseBackgroundColor,
        String surfaceBackgroundColor,
        String borderColor,
        double apcaContrastRatio
) {
    public static final String DEFAULT_OBSIDIAN_BASE = "#08090C";
    public static final String DEFAULT_OBSIDIAN_SURFACE = "#11141C";
    public static final String DEFAULT_OBSIDIAN_BORDER = "border-white/[0.07]";
    public static final double MINIMUM_CONTRAST_RATIO = 7.0;

    public DesignTokenSet {
        Objects.requireNonNull(archetype, "archetype cannot be null");
        Objects.requireNonNull(typographyClamps, "typographyClamps cannot be null");
        Objects.requireNonNull(baseBackgroundColor, "baseBackgroundColor cannot be null");
        Objects.requireNonNull(surfaceBackgroundColor, "surfaceBackgroundColor cannot be null");
        Objects.requireNonNull(borderColor, "borderColor cannot be null");

        if (apcaContrastRatio < MINIMUM_CONTRAST_RATIO) {
            throw new IllegalArgumentException(
                    "Contrast ratio " + apcaContrastRatio + " is below minimum requirement of " + MINIMUM_CONTRAST_RATIO + ":1"
            );
        }
    }
}
