package com.eneik.production.services.design.sovereign.domain;

import java.util.Objects;

/**
 * Compositional grid pattern specification.
 */
public record LayoutGridPattern(
        String patternName,
        String gridClasses,
        String borderToken,
        boolean isAsymmetric
) {
    public static final String SWISS_7_5 = "SWISS_ASYMMETRY_7_5";
    public static final String SWISS_8_4 = "SWISS_ASYMMETRY_8_4";
    public static final String BENTO_GRID_2_0 = "BENTO_GRID_2_0";

    public LayoutGridPattern {
        Objects.requireNonNull(patternName, "patternName cannot be null");
        Objects.requireNonNull(gridClasses, "gridClasses cannot be null");
        Objects.requireNonNull(borderToken, "borderToken cannot be null");
    }
}
