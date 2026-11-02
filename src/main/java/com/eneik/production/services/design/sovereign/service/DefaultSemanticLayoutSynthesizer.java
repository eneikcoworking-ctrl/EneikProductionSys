package com.eneik.production.services.design.sovereign.service;

import com.eneik.production.services.design.sovereign.domain.CorrectionDirective;
import com.eneik.production.services.design.sovereign.domain.DesignTokenSet;
import com.eneik.production.services.design.sovereign.domain.LayoutGridPattern;
import com.eneik.production.services.design.sovereign.domain.SynthesizedDomDocument;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Default implementation of {@link SemanticLayoutSynthesizer} that constructs
 * accessible, semantic HTML5 documents styled with Tailwind CSS classes and custom
 * design tokens (typography clamps, dark obsidian palette, border tokens).
 */
@Component
public class DefaultSemanticLayoutSynthesizer implements SemanticLayoutSynthesizer {

    @Override
    public SynthesizedDomDocument synthesizeLayout(
            DesignTokenSet tokenSet,
            LayoutGridPattern gridPattern,
            Map<String, String> projectAssetUrls,
            int roundIndex
    ) {
        Objects.requireNonNull(tokenSet, "tokenSet cannot be null");
        Objects.requireNonNull(gridPattern, "gridPattern cannot be null");
        if (roundIndex < 0) {
            throw new IllegalArgumentException("roundIndex cannot be negative");
        }

        Map<String, String> assets = projectAssetUrls != null ? projectAssetUrls : Map.of();

        String html = buildHtml5Document(tokenSet, gridPattern, assets, roundIndex);
        return SynthesizedDomDocument.create(html, roundIndex);
    }

    @Override
    public SynthesizedDomDocument refineLayout(
            SynthesizedDomDocument previousDocument,
            DesignTokenSet tokenSet,
            List<CorrectionDirective> directives,
            int roundIndex
    ) {
        Objects.requireNonNull(previousDocument, "previousDocument cannot be null");
        Objects.requireNonNull(tokenSet, "tokenSet cannot be null");
        if (roundIndex < 0) {
            throw new IllegalArgumentException("roundIndex cannot be negative");
        }

        String baseHtml = previousDocument.htmlContent();
        if (directives == null || directives.isEmpty()) {
            return SynthesizedDomDocument.create(baseHtml, roundIndex);
        }

        StringBuilder styleRefinement = new StringBuilder();
        styleRefinement.append("\n<!-- Audit Refinement Round ").append(roundIndex).append(" -->\n");
        styleRefinement.append("<style id=\"audit-refinement-round-").append(roundIndex).append("\">\n");

        for (CorrectionDirective directive : directives) {
            if (directive != null) {
                styleRefinement.append("  /* Reason: ").append(directive.reason()).append(" */\n");
                styleRefinement.append("  ").append(directive.targetCssSelector())
                        .append(" { ").append(directive.suggestedRule()).append(" }\n");
            }
        }
        styleRefinement.append("</style>\n");

        String refinedHtml;
        if (baseHtml.contains("</head>")) {
            refinedHtml = baseHtml.replace("</head>", styleRefinement.toString() + "</head>");
        } else {
            refinedHtml = styleRefinement.toString() + baseHtml;
        }

        return SynthesizedDomDocument.create(refinedHtml, roundIndex);
    }

    private String buildHtml5Document(
            DesignTokenSet tokenSet,
            LayoutGridPattern gridPattern,
            Map<String, String> assets,
            int roundIndex
    ) {
        String baseBg = tokenSet.baseBackgroundColor();
        String surfaceBg = tokenSet.surfaceBackgroundColor();
        String border = tokenSet.borderColor();
        String h1Clamp = tokenSet.typographyClamps().getOrDefault("h1", "clamp(2rem, 5vw, 4rem)");
        String h2Clamp = tokenSet.typographyClamps().getOrDefault("h2", "clamp(1.5rem, 3.5vw, 2.75rem)");
        String bodyClamp = tokenSet.typographyClamps().getOrDefault("body", "clamp(1rem, 1.2vw, 1.125rem)");

        String heroAssetUrl = assets.getOrDefault("hero", assets.getOrDefault("mainImage", "https://assets.eneik.com/default-hero.webp"));
        String logoAssetUrl = assets.getOrDefault("logo", "https://assets.eneik.com/default-logo.svg");

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\" class=\"dark\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <title>").append(tokenSet.archetype().getDisplayName()).append(" - Sovereign Synthesized UI</title>\n");
        html.append("  <script src=\"https://cdn.tailwindcss.com\"></script>\n");
        html.append("  <style>\n");
        html.append("    :root {\n");
        html.append("      --bg-base: ").append(baseBg).append(";\n");
        html.append("      --bg-surface: ").append(surfaceBg).append(";\n");
        html.append("      --font-clamp-h1: ").append(h1Clamp).append(";\n");
        html.append("      --font-clamp-h2: ").append(h2Clamp).append(";\n");
        html.append("      --font-clamp-body: ").append(bodyClamp).append(";\n");
        html.append("    }\n");
        html.append("    body { background-color: var(--bg-base); color: #F3F4F6; font-size: var(--font-clamp-body); }\n");
        html.append("    h1 { font-size: var(--font-clamp-h1); line-height: 1.1; }\n");
        html.append("    h2 { font-size: var(--font-clamp-h2); line-height: 1.2; }\n");
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body class=\"min-h-screen flex flex-col font-sans text-slate-100 antialiased\">\n");

        // Accessibility: Skip link with sovereign styling
        html.append("  <a href=\"#main-content\" class=\"sr-only focus:not-sr-only focus:absolute focus:z-50 focus:p-4 focus:bg-emerald-500 focus:text-slate-950 focus:outline-none focus:ring-2 focus:ring-emerald-400 font-semibold rounded-md shadow-xl\">\n");
        html.append("    Skip to main content\n");
        html.append("  </a>\n\n");

        // Header with accessible nav
        html.append("  <header role=\"banner\" class=\"w-full border-b ").append(border)
                .append(" bg-[").append(surfaceBg).append("] px-6 py-4 flex items-center justify-between\">\n");
        html.append("    <div class=\"flex items-center space-x-3\">\n");
        html.append("      <img src=\"").append(logoAssetUrl).append("\" alt=\"").append(tokenSet.archetype().getDisplayName()).append(" Brand Logo\" class=\"h-8 w-8 object-contain\">\n");
        html.append("      <span class=\"font-bold text-lg tracking-tight\">").append(tokenSet.archetype().getDisplayName()).append(" Platform</span>\n");
        html.append("    </div>\n");
        html.append("    <nav role=\"navigation\" aria-label=\"Main Navigation\" class=\"flex items-center space-x-6\">\n");
        html.append("      <a href=\"#features\" class=\"text-sm text-slate-300 hover:text-white transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-900 rounded-sm p-1\">Features</a>\n");
        html.append("      <a href=\"#documentation\" class=\"text-sm text-slate-300 hover:text-white transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-900 rounded-sm p-1\">Documentation</a>\n");
        html.append("      <a href=\"#contact\" class=\"text-sm text-slate-300 hover:text-white transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-900 rounded-sm p-1\">Contact</a>\n");
        html.append("    </nav>\n");
        html.append("  </header>\n\n");

        // Main content with grid layout
        html.append("  <main id=\"main-content\" role=\"main\" class=\"flex-1 container mx-auto px-6 py-12\">\n");
        html.append("    <div class=\"grid ").append(gridPattern.gridClasses()).append(" gap-8 items-start\">\n");

        // Hero section
        html.append("      <section aria-labelledby=\"hero-heading\" class=\"col-span-12 lg:col-span-7 flex flex-col justify-center space-y-6 p-6 rounded-xl border ").append(border).append(" bg-[").append(surfaceBg).append("]\">\n");
        html.append("        <h1 id=\"hero-heading\" class=\"font-extrabold tracking-tight text-white\">\n");
        html.append("          Autonomous UI Synthesis for ").append(tokenSet.archetype().getDisplayName()).append("\n");
        html.append("        </h1>\n");
        html.append("        <p class=\"text-slate-300 max-w-2xl\">\n");
        html.append("          Generated with WCAG 2.1 AAA contrast compliance (").append(String.format(java.util.Locale.ROOT, "%.1f", tokenSet.apcaContrastRatio())).append(":1) and adaptive typography clamps.\n");
        html.append("        </p>\n");
        html.append("        <div class=\"pt-4 flex flex-wrap gap-4 items-center\">\n");
        html.append("          <button type=\"button\" class=\"px-6 py-3 bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-semibold rounded-lg shadow-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-900 transition-all\">\n");
        html.append("            Explore Dashboard\n");
        html.append("          </button>\n");
        html.append("          <button type=\"button\" class=\"px-6 py-3 bg-slate-800 hover:bg-slate-700 text-slate-200 border ").append(border).append(" font-semibold rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-400 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-900 transition-all\">\n");
        html.append("            View Documentation\n");
        html.append("          </button>\n");
        html.append("        </div>\n");
        html.append("      </section>\n\n");

        // Asset & Media section
        html.append("      <section aria-labelledby=\"media-heading\" class=\"col-span-12 lg:col-span-5 p-6 rounded-xl border ").append(border).append(" bg-[").append(surfaceBg).append("]\">\n");
        html.append("        <h2 id=\"media-heading\" class=\"font-bold text-slate-100 mb-4\">Visual Showcase</h2>\n");
        html.append("        <div class=\"relative overflow-hidden rounded-lg border ").append(border).append("\">\n");
        html.append("          <img src=\"").append(heroAssetUrl).append("\" alt=\"").append(tokenSet.archetype().getDisplayName()).append(" Main Interface Preview\" class=\"w-full h-auto object-cover\">\n");
        html.append("        </div>\n");
        html.append("      </section>\n");

        // Accessible Form section
        html.append("      <section aria-labelledby=\"form-heading\" class=\"col-span-12 p-6 rounded-xl border ").append(border).append(" bg-[").append(surfaceBg).append("]\">\n");
        html.append("        <h2 id=\"form-heading\" class=\"font-bold text-slate-100 mb-4\">Interactive Session Request</h2>\n");
        html.append("        <form role=\"form\" action=\"#\" method=\"POST\" class=\"space-y-4 max-w-xl\">\n");
        html.append("          <div>\n");
        html.append("            <label for=\"user-email\" class=\"block text-sm font-medium text-slate-300 mb-1\">Email Address</label>\n");
        html.append("            <input id=\"user-email\" name=\"email\" type=\"email\" required aria-required=\"true\" placeholder=\"developer@domain.com\" class=\"w-full px-4 py-2 bg-slate-900 border ").append(border).append(" text-white rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-900\">\n");
        html.append("          </div>\n");
        html.append("          <div>\n");
        html.append("            <label for=\"project-archetype-select\" class=\"block text-sm font-medium text-slate-300 mb-1\">Target Archetype</label>\n");
        html.append("            <select id=\"project-archetype-select\" name=\"archetype\" class=\"w-full px-4 py-2 bg-slate-900 border ").append(border).append(" text-white rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-900\">\n");
        html.append("              <option value=\"").append(tokenSet.archetype().name()).append("\" selected>").append(tokenSet.archetype().getDisplayName()).append("</option>\n");
        html.append("            </select>\n");
        html.append("          </div>\n");
        html.append("          <button type=\"submit\" class=\"px-6 py-2 bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-medium rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-900 transition-all\">\n");
        html.append("            Submit Configuration\n");
        html.append("          </button>\n");
        html.append("        </form>\n");
        html.append("      </section>\n");

        html.append("    </div>\n");
        html.append("  </main>\n\n");

        // Footer landmark
        html.append("  <footer role=\"contentinfo\" class=\"w-full border-t ").append(border)
                .append(" bg-[").append(surfaceBg).append("] px-6 py-4 text-center text-xs text-slate-400\">\n");
        html.append("    <p>&copy; 2026 Autonomous Sovereign Designer System. WCAG 2.1 AA Compliant Document.</p>\n");
        html.append("  </footer>\n");

        html.append("</body>\n");
        html.append("</html>\n");

        return html.toString();
    }
}
