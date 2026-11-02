package com.eneik.production.services.design.sovereign.domain;

import java.util.Objects;

/**
 * Compositional pattern model for registry lookup.
 */
public record LayoutPattern(
        String id,
        String name,
        String gridType,
        String cssClasses,
        String borderStyle,
        String backgroundColor
) {
    public LayoutPattern {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(gridType, "gridType must not be null");
    }
}
