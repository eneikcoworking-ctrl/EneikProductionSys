package com.eneik.production.services.design.sovereign.service;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Default implementation of {@link SovereignProjectAssetResolver} resolving project media assets.
 */
@Component
public class DefaultSovereignProjectAssetResolver implements SovereignProjectAssetResolver {

    @Override
    public Map<String, String> resolveProjectAssets(String projectContextKey) {
        String baseKey = (projectContextKey != null && !projectContextKey.isBlank())
                ? projectContextKey
                : "default";
        return Map.of(
                "hero", "assets/images/" + baseKey + "-hero.webp",
                "logo", "assets/images/" + baseKey + "-logo.svg",
                "preview", "assets/images/" + baseKey + "-preview.svg"
        );
    }
}
