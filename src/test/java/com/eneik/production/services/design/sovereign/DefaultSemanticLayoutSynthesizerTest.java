package com.eneik.production.services.design.sovereign;

import com.eneik.production.services.design.sovereign.domain.CorrectionDirective;
import com.eneik.production.services.design.sovereign.domain.DesignTokenSet;
import com.eneik.production.services.design.sovereign.domain.LayoutGridPattern;
import com.eneik.production.services.design.sovereign.domain.ProductArchetype;
import com.eneik.production.services.design.sovereign.domain.SynthesizedDomDocument;
import com.eneik.production.services.design.sovereign.service.DefaultSemanticLayoutSynthesizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultSemanticLayoutSynthesizerTest {

    private DefaultSemanticLayoutSynthesizer synthesizer;
    private DesignTokenSet tokenSet;
    private LayoutGridPattern gridPattern;

    @BeforeEach
    void setUp() {
        synthesizer = new DefaultSemanticLayoutSynthesizer();
        tokenSet = new DesignTokenSet(
                ProductArchetype.DEV_TOOLS,
                Map.of(
                        "h1", "clamp(2.5rem, 6vw, 4.5rem)",
                        "h2", "clamp(1.75rem, 4vw, 3rem)",
                        "body", "clamp(1rem, 1.2vw, 1.25rem)"
                ),
                "#08090C",
                "#11141C",
                "border-white/[0.07]",
                8.2
        );
        gridPattern = new LayoutGridPattern(
                LayoutGridPattern.SWISS_7_5,
                "grid-cols-12",
                "border-white/[0.07]",
                true
        );
    }

    @Test
    @DisplayName("Should synthesize valid HTML5 document with Tailwind CSS script and custom design tokens")
    void testSynthesizeLayoutStructureAndTokens() {
        Map<String, String> assets = Map.of(
                "hero", "https://assets.eneik.com/hero-devtools.png",
                "logo", "https://assets.eneik.com/logo-devtools.svg"
        );

        SynthesizedDomDocument doc = synthesizer.synthesizeLayout(tokenSet, gridPattern, assets, 0);

        assertNotNull(doc);
        assertEquals(0, doc.roundIndex());
        assertNotNull(doc.domFingerprint());
        assertFalse(doc.domFingerprint().isBlank());

        String html = doc.htmlContent();
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("<html lang=\"en\" class=\"dark\">"));
        assertTrue(html.contains("<script src=\"https://cdn.tailwindcss.com\"></script>"));

        // Design tokens check
        assertTrue(html.contains("--bg-base: #08090C;"));
        assertTrue(html.contains("--bg-surface: #11141C;"));
        assertTrue(html.contains("--font-clamp-h1: clamp(2.5rem, 6vw, 4.5rem);"));
        assertTrue(html.contains("border-white/[0.07]"));
        assertTrue(html.contains("grid-cols-12"));
    }

    @Test
    @DisplayName("Should enforce WCAG 2.1 AA accessibility features (skip link, ARIA landmarks, labelled controls, focus rings)")
    void testAccessibilityCompliance() {
        SynthesizedDomDocument doc = synthesizer.synthesizeLayout(tokenSet, gridPattern, Map.of(), 0);
        String html = doc.htmlContent();

        // Skip to main content link
        assertTrue(html.contains("href=\"#main-content\""));
        assertTrue(html.contains("Skip to main content"));

        // ARIA landmark roles
        assertTrue(html.contains("role=\"banner\""));
        assertTrue(html.contains("role=\"navigation\""));
        assertTrue(html.contains("role=\"main\""));
        assertTrue(html.contains("role=\"contentinfo\""));
        assertTrue(html.contains("role=\"form\""));

        // Explicitly labelled form controls
        assertTrue(html.contains("<label for=\"user-email\""));
        assertTrue(html.contains("id=\"user-email\""));
        assertTrue(html.contains("aria-required=\"true\""));
        assertTrue(html.contains("<label for=\"project-archetype-select\""));
        assertTrue(html.contains("id=\"project-archetype-select\""));

        // Visible focus indicators (focus-visible:)
        assertTrue(html.contains("focus-visible:ring-2"));
        assertTrue(html.contains("focus-visible:outline-none"));

        // Image accessibility (alt text)
        assertTrue(html.contains("alt=\"DevTools Brand Logo\""));
        assertTrue(html.contains("alt=\"DevTools Main Interface Preview\""));
    }

    @Test
    @DisplayName("Should embed custom project assets when supplied, or fall back to defaults")
    void testAssetEmbeddingAndFallback() {
        Map<String, String> customAssets = Map.of(
                "hero", "https://cdn.example.com/custom-hero.jpg",
                "logo", "https://cdn.example.com/custom-logo.svg"
        );

        SynthesizedDomDocument customDoc = synthesizer.synthesizeLayout(tokenSet, gridPattern, customAssets, 0);
        assertTrue(customDoc.htmlContent().contains("src=\"https://cdn.example.com/custom-hero.jpg\""));
        assertTrue(customDoc.htmlContent().contains("src=\"https://cdn.example.com/custom-logo.svg\""));

        SynthesizedDomDocument fallbackDoc = synthesizer.synthesizeLayout(tokenSet, gridPattern, Map.of(), 0);
        assertTrue(fallbackDoc.htmlContent().contains("src=\"https://assets.eneik.com/default-hero.webp\""));
        assertTrue(fallbackDoc.htmlContent().contains("src=\"https://assets.eneik.com/default-logo.svg\""));
    }

    @Test
    @DisplayName("Should refine DOM document by injecting correction directives into style block")
    void testRefineLayoutWithCorrectionDirectives() {
        SynthesizedDomDocument initialDoc = synthesizer.synthesizeLayout(tokenSet, gridPattern, Map.of(), 0);

        List<CorrectionDirective> directives = List.of(
                new CorrectionDirective("#user-email", "border-color: #EF4444;", "Fix low input border contrast on dark surface", 1),
                new CorrectionDirective("button[type=\"submit\"]", "outline: 2px solid #6366F1;", "Enhance active focus boundary", 1)
        );

        SynthesizedDomDocument refinedDoc = synthesizer.refineLayout(initialDoc, tokenSet, directives, 1);

        assertNotNull(refinedDoc);
        assertEquals(1, refinedDoc.roundIndex());
        assertNotEquals(initialDoc.domFingerprint(), refinedDoc.domFingerprint());

        String refinedHtml = refinedDoc.htmlContent();
        assertTrue(refinedHtml.contains("<style id=\"audit-refinement-round-1\">"));
        assertTrue(refinedHtml.contains("#user-email { border-color: #EF4444; }"));
        assertTrue(refinedHtml.contains("button[type=\"submit\"] { outline: 2px solid #6366F1; }"));
        assertTrue(refinedHtml.contains("Fix low input border contrast on dark surface"));
    }

    @Test
    @DisplayName("Should validate parameters and throw IllegalArgumentException on invalid input")
    void testParameterValidation() {
        assertThrows(NullPointerException.class, () -> synthesizer.synthesizeLayout(null, gridPattern, Map.of(), 0));
        assertThrows(NullPointerException.class, () -> synthesizer.synthesizeLayout(tokenSet, null, Map.of(), 0));
        assertThrows(IllegalArgumentException.class, () -> synthesizer.synthesizeLayout(tokenSet, gridPattern, Map.of(), -1));

        SynthesizedDomDocument initialDoc = synthesizer.synthesizeLayout(tokenSet, gridPattern, Map.of(), 0);
        assertThrows(NullPointerException.class, () -> synthesizer.refineLayout(null, tokenSet, List.of(), 1));
        assertThrows(NullPointerException.class, () -> synthesizer.refineLayout(initialDoc, null, List.of(), 1));
        assertThrows(IllegalArgumentException.class, () -> synthesizer.refineLayout(initialDoc, tokenSet, List.of(), -1));
    }
}
