package com.eneik.production.services.design.sovereign.service;

import com.eneik.production.services.design.sovereign.domain.ProductArchetype;
import com.eneik.production.services.design.sovereign.domain.SynthesisSession;

import java.util.List;

/**
 * Top-level design synthesis pipeline orchestrator service boundary.
 */
public interface SovereignDesignSynthesisService {

    SynthesisSession executeDesignSynthesis(ProductArchetype archetype, String projectContextKey);

    default SynthesisSession executeDesignSynthesis(
            ProductArchetype archetype,
            String projectContextKey,
            String brief,
            List<String> memorillaLatentSlots
    ) {
        return executeDesignSynthesis(archetype, projectContextKey);
    }
}
