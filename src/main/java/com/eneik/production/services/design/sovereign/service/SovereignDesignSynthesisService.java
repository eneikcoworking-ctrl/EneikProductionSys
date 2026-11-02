package com.eneik.production.services.design.sovereign.service;

import com.eneik.production.services.design.sovereign.domain.ProductArchetype;
import com.eneik.production.services.design.sovereign.domain.SynthesisSession;

/**
 * Top-level design synthesis pipeline orchestrator service boundary.
 */
public interface SovereignDesignSynthesisService {

    /**
     * Executes autonomous design synthesis for a product archetype and context key.
     * Operates iterative refinement up to MAX_AUDIT_ROUNDS (3) until score >= 9.2 or max rounds reached.
     *
     * @param archetype target product archetype
     * @param projectContextKey project asset context key
     * @return completed synthesis session
     */
    SynthesisSession executeDesignSynthesis(ProductArchetype archetype, String projectContextKey);
}
