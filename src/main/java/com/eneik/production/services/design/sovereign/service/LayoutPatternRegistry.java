package com.eneik.production.services.design.sovereign.service;

import com.eneik.production.services.design.sovereign.domain.LayoutGridPattern;
import com.eneik.production.services.design.sovereign.domain.ProductArchetype;

import java.util.List;

/**
 * Registry service providing layout grid patterns (Swiss editorial 7/5 & 8/4, Bento Grid 2.0).
 */
public interface LayoutPatternRegistry {

    /**
     * Resolves default layout pattern for a product archetype.
     */
    LayoutGridPattern resolveDefaultPattern(ProductArchetype archetype);

    /**
     * Lists available layout patterns.
     */
    List<LayoutGridPattern> listAvailablePatterns();
}
