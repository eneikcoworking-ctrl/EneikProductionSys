package com.eneik.production.services.design.sovereign;

import com.eneik.production.services.design.sovereign.domain.LayoutPattern;
import com.eneik.production.services.design.sovereign.domain.MediaAsset;
import com.eneik.production.services.design.sovereign.service.DefaultLayoutPatternRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LayoutPatternRegistryTest {

    private DefaultLayoutPatternRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DefaultLayoutPatternRegistry();
    }

    @Test
    void whenInitialized_thenDefaultPatternsAndMediaAssetsAreLoaded() {
        List<LayoutPattern> patterns = registry.getPatterns();
        assertFalse(patterns.isEmpty(), "Layout patterns should be preloaded upon initialization");

        List<MediaAsset> mediaAssets = registry.getSeedMediaAssets();
        assertFalse(mediaAssets.isEmpty(), "Seed media assets should be preloaded upon initialization");

        assertTrue(patterns.stream().anyMatch(p -> p.id().equals("swiss-asymmetry-7-5")));
        assertTrue(patterns.stream().anyMatch(p -> p.id().equals("swiss-asymmetry-8-4")));
        assertTrue(patterns.stream().anyMatch(p -> p.id().equals("bento-grid-2")));
        assertTrue(patterns.stream().anyMatch(p -> p.id().equals("multilevel-obsidian")));

        assertTrue(mediaAssets.stream().anyMatch(m -> m.id().equals("seed-hero-banner")));
    }

    @Test
    void whenQueryingExistingPattern_thenReturnsPatternDetails() {
        Optional<LayoutPattern> bentoOpt = registry.findPatternById("bento-grid-2");
        assertTrue(bentoOpt.isPresent());

        LayoutPattern bento = bentoOpt.get();
        assertEquals("Bento Grid 2.0", bento.name());
        assertNotNull(bento.cssClasses());
    }

    @Test
    void whenQueryingExistingMediaAsset_thenReturnsAssetDetails() {
        Optional<MediaAsset> heroOpt = registry.findMediaAssetById("seed-hero-banner");
        assertTrue(heroOpt.isPresent());

        MediaAsset hero = heroOpt.get();
        assertEquals("Hero Abstract Dark Grid", hero.title());
        assertEquals("HERO", hero.category());
    }
}
