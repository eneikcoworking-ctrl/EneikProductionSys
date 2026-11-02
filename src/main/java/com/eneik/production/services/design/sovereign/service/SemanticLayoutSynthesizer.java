package com.eneik.production.services.design.sovereign.service;

import com.eneik.production.services.design.sovereign.domain.CorrectionDirective;
import com.eneik.production.services.design.sovereign.domain.DesignTokenSet;
import com.eneik.production.services.design.sovereign.domain.LayoutGridPattern;
import com.eneik.production.services.design.sovereign.domain.SynthesizedDomDocument;

import java.util.List;
import java.util.Map;

/**
 * Service synthesizing semantic HTML5 documents structured with Tailwind CSS classes,
 * custom design tokens, and accessibility landmarks.
 */
public interface SemanticLayoutSynthesizer {

    /**
     * Synthesizes an initial HTML5 layout document.
     */
    SynthesizedDomDocument synthesizeLayout(
            DesignTokenSet tokenSet,
            LayoutGridPattern gridPattern,
            Map<String, String> projectAssetUrls,
            int roundIndex
    );

    /**
     * Refines an existing layout document using audit directives.
     */
    SynthesizedDomDocument refineLayout(
            SynthesizedDomDocument previousDocument,
            DesignTokenSet tokenSet,
            List<CorrectionDirective> directives,
            int roundIndex
    );
}
