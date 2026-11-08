package com.eneik.production.services.design.sovereign.service;

import com.eneik.production.services.design.sovereign.domain.CorrectionDirective;
import com.eneik.production.services.design.sovereign.domain.DesignTokenSet;
import com.eneik.production.services.design.sovereign.domain.LayoutGridPattern;
import com.eneik.production.services.design.sovereign.domain.SynthesizedDomDocument;

import java.util.List;
import java.util.Map;

/**
 * Service synthesizing semantic HTML5 documents structured with Tailwind CSS classes,
 * custom design tokens, brief-driven domain components, and Stanford Memorilla latent slots.
 */
public interface SemanticLayoutSynthesizer {

    SynthesizedDomDocument synthesizeLayout(
            DesignTokenSet tokenSet,
            LayoutGridPattern gridPattern,
            Map<String, String> projectAssetUrls,
            int roundIndex
    );

    default SynthesizedDomDocument synthesizeLayout(
            DesignTokenSet tokenSet,
            LayoutGridPattern gridPattern,
            Map<String, String> projectAssetUrls,
            int roundIndex,
            String brief,
            List<String> memorillaLatentSlots
    ) {
        return synthesizeLayout(tokenSet, gridPattern, projectAssetUrls, roundIndex);
    }

    SynthesizedDomDocument refineLayout(
            SynthesizedDomDocument previousDocument,
            DesignTokenSet tokenSet,
            List<CorrectionDirective> directives,
            int roundIndex
    );
}
