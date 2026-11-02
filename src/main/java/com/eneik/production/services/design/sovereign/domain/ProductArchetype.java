package com.eneik.production.services.design.sovereign.domain;

import java.util.Arrays;

/**
 * Product archetype classifier determining design system aesthetic rules and token defaults.
 */
public enum ProductArchetype {
    DEV_TOOLS("DevTools", "Dark minimalist obsidian theme with mono typography and sharp borders"),
    B2B_SAAS("B2B SaaS", "High-density enterprise utility with calm slate surfaces and deep accents"),
    FIN_TECH("FinTech", "High-contrast precision editorial layout with obsidian accents"),
    CONSUMER("Consumer", "Bento Grid 2.0 with layered depth and subtle glassmorphic borders"),
    CREATIVE_AGENCY("Creative Agency", "Bold expressive typography with asymmetrical editorial layouts"),
    HEALTHCARE("Healthcare", "Clean clinical clarity, accessible high-contrast navigation and calm tones"),
    AI_AGENTIC_SYSTEMS("AI Agentic Systems", "Dense obsidian console, emerald telemetry status and reactive surfaces"),
    EDITORIAL("Editorial", "Swiss magazine asymmetry (7/5 and 8/4 grid ratios) with bold serif accents"),
    DEFAULT("Default System", "Universal multi-level obsidian dark theme with WCAG AAA contrast");

    private final String displayName;
    private final String description;

    ProductArchetype(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static ProductArchetype fromString(String name) {
        if (name == null || name.isBlank()) {
            return DEFAULT;
        }
        String normalized = name.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        return Arrays.stream(values())
                .filter(a -> a.name().equals(normalized))
                .findFirst()
                .orElse(DEFAULT);
    }
}
