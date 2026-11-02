package com.eneik.production.services.design.sovereign.domain;

import java.util.List;
import java.util.Map;

/**
 * High-level design tokens aggregate containing color palette, typography constraints, and contrast rules.
 */
public record DesignTokens(
        ProductArchetype archetype,
        Map<String, String> colors,
        List<TypographyConstraint> typography,
        ContrastRule contrastRule
) {
}
