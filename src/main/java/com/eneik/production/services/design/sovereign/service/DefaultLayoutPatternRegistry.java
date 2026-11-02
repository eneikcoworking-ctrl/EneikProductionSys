package com.eneik.production.services.design.sovereign.service;

import com.eneik.production.services.design.sovereign.domain.LayoutGridPattern;
import com.eneik.production.services.design.sovereign.domain.LayoutPattern;
import com.eneik.production.services.design.sovereign.domain.MediaAsset;
import com.eneik.production.services.design.sovereign.domain.ProductArchetype;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default implementation of {@link LayoutPatternRegistry} registering compositional grids
 * (Swiss Asymmetry 7/5, 8/4, Bento Grid 2.0, Multi-level Obsidian).
 */
@Component
public class DefaultLayoutPatternRegistry implements LayoutPatternRegistry {

    private final List<LayoutPattern> patterns = new CopyOnWriteArrayList<>();
    private final List<MediaAsset> seedMediaAssets = new CopyOnWriteArrayList<>();

    public DefaultLayoutPatternRegistry() {
        initializeDefaults();
    }

    public void initializeDefaults() {
        patterns.clear();
        seedMediaAssets.clear();

        // Swiss Editorial Asymmetry 7/5
        patterns.add(new LayoutPattern(
                "swiss-asymmetry-7-5",
                "Swiss Editorial Asymmetry 7/5",
                "grid-cols-12",
                "col-span-7 md:col-span-7 col-span-5 md:col-span-5 gap-6",
                "1px solid rgba(255, 255, 255, 0.07)",
                "#08090C"
        ));

        // Swiss Editorial Asymmetry 8/4
        patterns.add(new LayoutPattern(
                "swiss-asymmetry-8-4",
                "Swiss Editorial Asymmetry 8/4",
                "grid-cols-12",
                "col-span-8 md:col-span-8 col-span-4 md:col-span-4 gap-8",
                "1px solid rgba(255, 255, 255, 0.07)",
                "#08090C"
        ));

        // Bento Grid 2.0
        patterns.add(new LayoutPattern(
                "bento-grid-2",
                "Bento Grid 2.0",
                "bento-grid",
                "grid grid-cols-1 md:grid-cols-3 gap-4 auto-rows-[250px]",
                "1px solid rgba(255, 255, 255, 0.07)",
                "#11141C"
        ));

        // Multi-level Obsidian Dark Theme (#08090C -> #11141C)
        patterns.add(new LayoutPattern(
                "multilevel-obsidian",
                "Multi-level Obsidian",
                "layered-obsidian",
                "bg-[#08090C] text-white divide-y divide-white/[0.07] border border-white/[0.07]",
                "1px solid rgba(255, 255, 255, 0.07)",
                "#08090C"
        ));

        // Seed media assets
        seedMediaAssets.add(new MediaAsset(
                "seed-hero-banner",
                "Hero Abstract Dark Grid",
                "assets/images/hero-dark-grid.webp",
                "HERO",
                "image/webp"
        ));

        seedMediaAssets.add(new MediaAsset(
                "seed-avatar-placeholder",
                "Executive Avatar Seed",
                "assets/images/avatar-executive.png",
                "AVATAR",
                "image/png"
        ));

        seedMediaAssets.add(new MediaAsset(
                "seed-preview-mockup",
                "System UI Interface Preview",
                "assets/images/ui-mockup-preview.svg",
                "MOCKUP",
                "image/svg+xml"
        ));
    }

    @Override
    public LayoutGridPattern resolveDefaultPattern(ProductArchetype archetype) {
        ProductArchetype resolved = (archetype != null) ? archetype : ProductArchetype.DEFAULT;
        return switch (resolved) {
            case DEV_TOOLS, AI_AGENTIC_SYSTEMS -> new LayoutGridPattern(LayoutGridPattern.SWISS_7_5, "grid-cols-12", "border-white/[0.07]", true);
            case EDITORIAL, CREATIVE_AGENCY -> new LayoutGridPattern(LayoutGridPattern.SWISS_8_4, "grid-cols-12", "border-white/[0.07]", true);
            case CONSUMER, B2B_SAAS, FIN_TECH, HEALTHCARE, DEFAULT -> new LayoutGridPattern(LayoutGridPattern.BENTO_GRID_2_0, "grid-cols-1 md:grid-cols-3", "border-white/[0.07]", false);
        };
    }

    @Override
    public List<LayoutGridPattern> listAvailablePatterns() {
        return List.of(
                new LayoutGridPattern(LayoutGridPattern.SWISS_7_5, "grid-cols-12", "border-white/[0.07]", true),
                new LayoutGridPattern(LayoutGridPattern.SWISS_8_4, "grid-cols-12", "border-white/[0.07]", true),
                new LayoutGridPattern(LayoutGridPattern.BENTO_GRID_2_0, "grid-cols-1 md:grid-cols-3", "border-white/[0.07]", false)
        );
    }

    public List<LayoutPattern> getPatterns() {
        return Collections.unmodifiableList(new ArrayList<>(patterns));
    }

    public List<MediaAsset> getSeedMediaAssets() {
        return Collections.unmodifiableList(new ArrayList<>(seedMediaAssets));
    }

    public Optional<LayoutPattern> findPatternById(String id) {
        return patterns.stream()
                .filter(p -> p.id().equalsIgnoreCase(id))
                .findFirst();
    }

    public Optional<MediaAsset> findMediaAssetById(String id) {
        return seedMediaAssets.stream()
                .filter(m -> m.id().equalsIgnoreCase(id))
                .findFirst();
    }
}
