package com.eneik.production.services.design.sovereign.service;

import java.util.Map;

/**
 * Service providing real media assets and static resources for inclusion during DOM synthesis.
 */
public interface SovereignProjectAssetResolver {

    /**
     * Resolves project media asset URLs by category or context key.
     */
    Map<String, String> resolveProjectAssets(String projectContextKey);
}
