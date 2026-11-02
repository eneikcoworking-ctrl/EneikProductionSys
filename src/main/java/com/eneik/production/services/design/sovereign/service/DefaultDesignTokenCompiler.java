package com.eneik.production.services.design.sovereign.service;

import com.eneik.production.services.design.sovereign.domain.ContrastRule;
import com.eneik.production.services.design.sovereign.domain.DesignTokenSet;
import com.eneik.production.services.design.sovereign.domain.DesignTokens;
import com.eneik.production.services.design.sovereign.domain.ProductArchetype;
import com.eneik.production.services.design.sovereign.domain.TypographyConstraint;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link DesignTokenCompiler} compiling design token sets
 * and aggregates for all product archetypes with adaptive typography clamps and WCAG AAA compliance.
 */
@Component
public class DefaultDesignTokenCompiler implements DesignTokenCompiler {

    public ProductArchetype classifyArchetype(String inputArchetype) {
        return ProductArchetype.fromString(inputArchetype);
    }

    @Override
    public DesignTokenSet compileTokens(ProductArchetype archetype) {
        ProductArchetype resolved = (archetype != null) ? archetype : ProductArchetype.DEFAULT;
        Map<String, String> colors = buildColorPalette(resolved);
        String fg = colors.getOrDefault("text", "#FFFFFF");
        String baseBg = colors.getOrDefault("background", "#08090C");
        String surfaceBg = colors.getOrDefault("surface", "#11141C");
        String border = colors.getOrDefault("border", "border-white/[0.07]");

        ContrastRule contrastRule = ContrastRule.calculate(fg, baseBg);
        double contrastRatio = contrastRule.contrastRatio();
        if (contrastRatio < DesignTokenSet.MINIMUM_CONTRAST_RATIO) {
            fg = "#FFFFFF";
            baseBg = "#08090C";
            contrastRule = ContrastRule.calculate(fg, baseBg);
            contrastRatio = contrastRule.contrastRatio();
        }

        Map<String, String> typographyClamps = new LinkedHashMap<>();
        for (TypographyConstraint tc : buildTypographyConstraints(resolved)) {
            typographyClamps.put(tc.fontSizeCategory(), tc.clampExpression());
        }

        return new DesignTokenSet(
                resolved,
                typographyClamps,
                baseBg,
                surfaceBg,
                border,
                Math.max(contrastRatio, DesignTokenSet.MINIMUM_CONTRAST_RATIO)
        );
    }

    public DesignTokens compileTokensAggregate(String archetypeInput) {
        ProductArchetype archetype = classifyArchetype(archetypeInput);
        return compileTokensAggregate(archetype);
    }

    public DesignTokens compileTokensAggregate(ProductArchetype archetype) {
        ProductArchetype resolvedArchetype = (archetype != null) ? archetype : ProductArchetype.DEFAULT;
        Map<String, String> colors = buildColorPalette(resolvedArchetype);
        List<TypographyConstraint> typography = buildTypographyConstraints(resolvedArchetype);

        String fg = colors.getOrDefault("text", "#FFFFFF");
        String bg = colors.getOrDefault("background", "#08090C");

        ContrastRule contrastRule = ContrastRule.calculate(fg, bg);
        if (!contrastRule.meetsWcagAaa()) {
            fg = "#FFFFFF";
            bg = "#08090C";
            contrastRule = ContrastRule.calculate(fg, bg);
        }

        return new DesignTokens(resolvedArchetype, colors, typography, contrastRule);
    }

    private Map<String, String> buildColorPalette(ProductArchetype archetype) {
        return switch (archetype) {
            case DEV_TOOLS -> Map.of(
                    "background", "#08090C",
                    "surface", "#11141C",
                    "text", "#F3F4F6",
                    "primary", "#6366F1",
                    "accent", "#22C55E",
                    "border", "border-white/[0.07]"
            );
            case B2B_SAAS -> Map.of(
                    "background", "#0B0F19",
                    "surface", "#111827",
                    "text", "#F9FAFB",
                    "primary", "#4F46E5",
                    "accent", "#06B6D4",
                    "border", "border-white/[0.08]"
            );
            case FIN_TECH -> Map.of(
                    "background", "#0B0F19",
                    "surface", "#111827",
                    "text", "#F9FAFB",
                    "primary", "#10B981",
                    "accent", "#3B82F6",
                    "border", "border-white/[0.07]"
            );
            case CONSUMER -> Map.of(
                    "background", "#18181B",
                    "surface", "#27272A",
                    "text", "#FAFAFA",
                    "primary", "#EC4899",
                    "accent", "#8B5CF6",
                    "border", "border-white/[0.06]"
            );
            case CREATIVE_AGENCY -> Map.of(
                    "background", "#120E16",
                    "surface", "#1F1A24",
                    "text", "#FDFDFD",
                    "primary", "#F43F5E",
                    "accent", "#A855F7",
                    "border", "border-white/[0.08]"
            );
            case HEALTHCARE -> Map.of(
                    "background", "#0A1128",
                    "surface", "#101D42",
                    "text", "#FFFFFF",
                    "primary", "#0284C7",
                    "accent", "#14B8A6",
                    "border", "border-white/[0.07]"
            );
            case AI_AGENTIC_SYSTEMS -> Map.of(
                    "background", "#08090C",
                    "surface", "#0F121A",
                    "text", "#F3F4F6",
                    "primary", "#10B981",
                    "accent", "#6366F1",
                    "border", "border-white/[0.08]"
            );
            case EDITORIAL -> Map.of(
                    "background", "#0F172A",
                    "surface", "#1E293B",
                    "text", "#F8FAFC",
                    "primary", "#E11D48",
                    "accent", "#F59E0B",
                    "border", "border-white/[0.07]"
            );
            case DEFAULT -> Map.of(
                    "background", "#08090C",
                    "surface", "#11141C",
                    "text", "#FFFFFF",
                    "primary", "#6366F1",
                    "accent", "#38BDF8",
                    "border", "border-white/[0.07]"
            );
        };
    }

    private List<TypographyConstraint> buildTypographyConstraints(ProductArchetype archetype) {
        return List.of(
                TypographyConstraint.create("h1", "2rem", "5vw", "4rem"),
                TypographyConstraint.create("h2", "1.5rem", "3.5vw", "2.75rem"),
                TypographyConstraint.create("h3", "1.25rem", "2.5vw", "2rem"),
                TypographyConstraint.create("body", "1rem", "1.2vw", "1.125rem"),
                TypographyConstraint.create("small", "0.75rem", "0.9vw", "0.875rem")
        );
    }
}
