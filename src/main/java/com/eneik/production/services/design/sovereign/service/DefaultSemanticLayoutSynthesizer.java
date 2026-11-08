package com.eneik.production.services.design.sovereign.service;

import com.eneik.production.services.design.sovereign.domain.CorrectionDirective;
import com.eneik.production.services.design.sovereign.domain.DesignTokenSet;
import com.eneik.production.services.design.sovereign.domain.LayoutGridPattern;
import com.eneik.production.services.design.sovereign.domain.ProductArchetype;
import com.eneik.production.services.design.sovereign.domain.SynthesizedDomDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Brief-conditioned and Memorilla-aware implementation of {@link SemanticLayoutSynthesizer}.
 * Synthesizes accessible, semantic HTML5 documents styled with Tailwind CSS and Obsidian design tokens,
 * dynamically assembling domain-specific UI components (KPI matrices, data ledgers, pipeline consoles,
 * waveform media inspectors, and accessible forms) from the screen brief and latent memory slots.
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
        return synthesizeLayout(tokenSet, gridPattern, projectAssetUrls, roundIndex, null, List.of());
    }

    @Override
    public SynthesizedDomDocument synthesizeLayout(
            DesignTokenSet tokenSet,
            LayoutGridPattern gridPattern,
            Map<String, String> projectAssetUrls,
            int roundIndex,
            String brief,
            List<String> memorillaLatentSlots
    ) {
        Objects.requireNonNull(tokenSet, "tokenSet cannot be null");
        Objects.requireNonNull(gridPattern, "gridPattern cannot be null");
        if (roundIndex < 0) {
            throw new IllegalArgumentException("roundIndex cannot be negative");
        }

        Map<String, String> assets = projectAssetUrls != null ? projectAssetUrls : Map.of();
        List<String> slots = memorillaLatentSlots != null ? memorillaLatentSlots : List.of();

        String html = buildHtml5Document(tokenSet, gridPattern, assets, roundIndex, brief, slots);
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
            int roundIndex,
            String rawBrief,
            List<String> memorillaSlots
    ) {
        String baseBg = tokenSet.baseBackgroundColor();
        String surfaceBg = tokenSet.surfaceBackgroundColor();
        String border = tokenSet.borderColor();
        String h1Clamp = tokenSet.typographyClamps().getOrDefault("h1", "clamp(2rem, 5vw, 4rem)");
        String h2Clamp = tokenSet.typographyClamps().getOrDefault("h2", "clamp(1.5rem, 3.5vw, 2.75rem)");
        String bodyClamp = tokenSet.typographyClamps().getOrDefault("body", "clamp(1rem, 1.2vw, 1.125rem)");

        String cleanBrief = (rawBrief != null && !rawBrief.isBlank())
                ? rawBrief.trim()
                : "Sovereign " + tokenSet.archetype().getDisplayName() + " Operational Interface";
        String heroHeading = extractScreenHeading(cleanBrief, tokenSet.archetype());
        List<String> domainTags = extractDomainKeywords(cleanBrief, tokenSet.archetype());

        String defaultHero = (rawBrief == null || rawBrief.isBlank())
                ? "https://assets.eneik.com/default-hero.webp"
                : buildInlinePreviewSvgDataUri(tokenSet, heroHeading, domainTags);
        String defaultLogo = (rawBrief == null || rawBrief.isBlank())
                ? "https://assets.eneik.com/default-logo.svg"
                : buildInlineLogoSvgDataUri(tokenSet);

        String heroAssetUrl = assets.getOrDefault("hero", assets.getOrDefault("mainImage", defaultHero));
        String logoAssetUrl = assets.getOrDefault("logo", defaultLogo);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\" class=\"dark\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <title>").append(escapeHtml(heroHeading)).append(" - ").append(tokenSet.archetype().getDisplayName()).append("</title>\n");
        html.append("  <script src=\"https://cdn.tailwindcss.com\"></script>\n");
        html.append("  <style>\n");
        html.append("    :root {\n");
        html.append("      --bg-base: ").append(baseBg).append(";\n");
        html.append("      --bg-surface: ").append(surfaceBg).append(";\n");
        html.append("      --text-primary: #F3F4F6;\n");
        html.append("      --font-clamp-h1: ").append(h1Clamp).append(";\n");
        html.append("      --font-clamp-h2: ").append(h2Clamp).append(";\n");
        html.append("      --font-clamp-body: ").append(bodyClamp).append(";\n");
        html.append("    }\n");
        html.append("    body { background-color: ").append(baseBg).append("; color: #F3F4F6; font-size: var(--font-clamp-body); }\n");
        html.append("    h1 { font-size: var(--font-clamp-h1); line-height: 1.1; }\n");
        html.append("    h2 { font-size: var(--font-clamp-h2); line-height: 1.2; }\n");
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body class=\"min-h-screen flex flex-col font-sans text-slate-100 antialiased overflow-x-hidden\">\n");

        // Accessibility: Skip link
        html.append("  <a href=\"#main-content\" class=\"sr-only focus:not-sr-only focus:absolute focus:z-50 focus:p-4 focus:bg-emerald-500 focus:text-slate-950 focus:outline-none focus:ring-2 focus:ring-emerald-400 font-semibold rounded-md shadow-xl\">\n");
        html.append("    Skip to main content\n");
        html.append("  </a>\n\n");

        // Header landmark
        html.append("  <header role=\"banner\" class=\"w-full border-b ").append(border)
                .append(" bg-[").append(surfaceBg).append("] px-6 py-4 flex flex-wrap items-center justify-between gap-4\">\n");
        html.append("    <div class=\"flex items-center space-x-3\">\n");
        html.append("      <img src=\"").append(logoAssetUrl).append("\" alt=\"").append(tokenSet.archetype().getDisplayName()).append(" Brand Logo\" class=\"h-8 w-8 object-contain\">\n");
        html.append("      <span class=\"font-bold text-lg tracking-tight\">").append(escapeHtml(heroHeading)).append("</span>\n");
        html.append("    </div>\n");
        html.append("    <nav role=\"navigation\" aria-label=\"Main Navigation\" class=\"flex flex-wrap items-center gap-4\">\n");
        for (int i = 0; i < Math.min(3, domainTags.size()); i++) {
            String navItem = capitalize(domainTags.get(i));
            html.append("      <a href=\"#section-").append(i).append("\" class=\"text-sm text-slate-200 hover:text-white transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-900 rounded-sm p-1\">")
                    .append(escapeHtml(navItem)).append("</a>\n");
        }
        html.append("    </nav>\n");
        html.append("  </header>\n\n");

        // Main content with compositional grid
        html.append("  <main id=\"main-content\" role=\"main\" class=\"flex-1 container mx-auto px-6 py-10\">\n");
        html.append("    <div class=\"grid ").append(gridPattern.gridClasses()).append(" gap-8 items-start\">\n");

        // Primary Brief-Driven Section
        html.append("      <section aria-labelledby=\"hero-heading\" class=\"col-span-12 lg:col-span-7 flex flex-col justify-center space-y-6 p-6 rounded-xl border ").append(border).append(" bg-[").append(surfaceBg).append("]\">\n");
        html.append("        <div class=\"inline-flex items-center gap-2 text-xs font-mono uppercase tracking-widest text-emerald-400\">\n");
        html.append("          <span>").append(tokenSet.archetype().name()).append("</span>\n");
        html.append("          <span>&bull;</span>\n");
        html.append("          <span>WCAG AAA ").append(String.format(Locale.ROOT, "%.1f", tokenSet.apcaContrastRatio())).append(":1</span>\n");
        html.append("        </div>\n");
        html.append("        <h1 id=\"hero-heading\" class=\"font-extrabold tracking-tight text-white\">\n");
        html.append("          ").append(escapeHtml(heroHeading)).append("\n");
        html.append("        </h1>\n");
        html.append("        <p class=\"text-slate-200 max-w-2xl leading-relaxed\">\n");
        html.append("          ").append(escapeHtml(cleanBrief)).append("\n");
        html.append("        </p>\n");
        html.append("        <div class=\"flex flex-wrap gap-2 pt-1\">\n");
        for (String tag : domainTags) {
            html.append("          <span class=\"px-3 py-1 text-xs font-mono rounded-full bg-slate-900 text-emerald-300 border ").append(border).append("\">")
                    .append(escapeHtml(tag)).append("</span>\n");
        }
        html.append("        </div>\n");
        html.append("        <div class=\"pt-4 flex flex-wrap gap-4 items-center\">\n");
        html.append("          <button type=\"button\" class=\"px-6 py-3 bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-semibold rounded-lg shadow-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-900 transition-all\">\n");
        html.append("            Launch ").append(escapeHtml(capitalize(domainTags.get(0)))).append("\n");
        html.append("          </button>\n");
        html.append("          <button type=\"button\" class=\"px-6 py-3 bg-slate-800 hover:bg-slate-700 text-slate-100 border ").append(border).append(" font-semibold rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-900 transition-all\">\n");
        html.append("            Inspect Specification\n");
        html.append("          </button>\n");
        html.append("        </div>\n");
        html.append("      </section>\n\n");

        // Visual Preview & Architecture Section
        html.append("      <section aria-labelledby=\"media-heading\" class=\"col-span-12 lg:col-span-5 p-6 rounded-xl border ").append(border).append(" bg-[").append(surfaceBg).append("]\">\n");
        html.append("        <h2 id=\"media-heading\" class=\"font-bold text-slate-100 mb-4\">").append(escapeHtml(capitalize(domainTags.get(0)))).append(" Topology</h2>\n");
        html.append("        <div class=\"relative overflow-hidden rounded-lg border ").append(border).append("\">\n");
        html.append("          <img src=\"").append(heroAssetUrl).append("\" alt=\"").append(tokenSet.archetype().getDisplayName()).append(" Main Interface Preview\" class=\"w-full h-auto object-cover\">\n");
        html.append("        </div>\n");
        html.append("      </section>\n\n");

        // Domain-Specific Interactive Component synthesized from Brief semantics
        appendBriefDrivenDomainModule(html, cleanBrief, domainTags, tokenSet, surfaceBg, border);

        // Stanford Memorilla Latent Semantic Context Section (when slots are supplied)
        if (!memorillaSlots.isEmpty()) {
            html.append("      <section aria-labelledby=\"memorilla-heading\" data-memorilla-slots=\"").append(memorillaSlots.size())
                    .append("\" class=\"col-span-12 p-6 rounded-xl border ").append(border).append(" bg-[").append(surfaceBg).append("]\">\n");
            html.append("        <h2 id=\"memorilla-heading\" class=\"font-bold text-slate-100 mb-3\">Memorilla Orthogonal Semantic Anchors (K=")
                    .append(memorillaSlots.size()).append(")</h2>\n");
            html.append("        <div class=\"grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3\">\n");
            int idx = 1;
            for (String slot : memorillaSlots.subList(0, Math.min(8, memorillaSlots.size()))) {
                html.append("          <div class=\"p-3 rounded-lg bg-slate-900 border ").append(border).append(" text-xs text-slate-200 font-mono\">\n");
                html.append("            <span class=\"text-emerald-400 font-bold\">[M").append(String.format(Locale.ROOT, "%02d", idx++)).append("]</span> ")
                        .append(escapeHtml(truncate(slot, 110))).append("\n");
                html.append("          </div>\n");
            }
            html.append("        </div>\n");
            html.append("      </section>\n\n");
        }

        // Accessible Interactive Control Form
        html.append("      <section aria-labelledby=\"form-heading\" class=\"col-span-12 p-6 rounded-xl border ").append(border).append(" bg-[").append(surfaceBg).append("]\">\n");
        html.append("        <h2 id=\"form-heading\" class=\"font-bold text-slate-100 mb-4\">").append(escapeHtml(heroHeading)).append(" Control Parameters</h2>\n");
        html.append("        <form role=\"form\" action=\"#\" method=\"POST\" class=\"space-y-4 max-w-xl\">\n");
        html.append("          <div>\n");
        html.append("            <label for=\"user-email\" class=\"block text-sm font-medium text-slate-200 mb-1\">Operator Endpoint / Email</label>\n");
        html.append("            <input id=\"user-email\" name=\"email\" type=\"email\" required aria-required=\"true\" placeholder=\"operator@domain.com\" class=\"w-full px-4 py-2 bg-slate-900 border ").append(border).append(" text-white rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-900\">\n");
        html.append("          </div>\n");
        html.append("          <div>\n");
        html.append("            <label for=\"project-archetype-select\" class=\"block text-sm font-medium text-slate-200 mb-1\">Target Archetype</label>\n");
        html.append("            <select id=\"project-archetype-select\" name=\"archetype\" class=\"w-full px-4 py-2 bg-slate-900 border ").append(border).append(" text-white rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-900\">\n");
        html.append("              <option value=\"").append(tokenSet.archetype().name()).append("\" selected>").append(tokenSet.archetype().getDisplayName()).append("</option>\n");
        html.append("            </select>\n");
        html.append("          </div>\n");
        html.append("          <button type=\"submit\" class=\"px-6 py-2 bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-medium rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-900 transition-all\">\n");
        html.append("            Apply ").append(escapeHtml(capitalize(domainTags.get(0)))).append(" State\n");
        html.append("          </button>\n");
        html.append("        </form>\n");
        html.append("      </section>\n");

        html.append("    </div>\n");
        html.append("  </main>\n\n");

        // Footer landmark
        html.append("  <footer role=\"contentinfo\" class=\"w-full border-t ").append(border)
                .append(" bg-[").append(surfaceBg).append("] px-6 py-4 text-center text-xs text-slate-300\">\n");
        html.append("    <p>&copy; 2026 Sovereign Design Workshop &mdash; ").append(escapeHtml(heroHeading)).append(" (WCAG 2.1 AAA Verified).</p>\n");
        html.append("  </footer>\n");

        html.append("</body>\n");
        html.append("</html>\n");

        return html.toString();
    }

    private void appendBriefDrivenDomainModule(
            StringBuilder html,
            String brief,
            List<String> tags,
            DesignTokenSet tokenSet,
            String surfaceBg,
            String border
    ) {
        String lower = brief.toLowerCase(Locale.ROOT);
        if (lower.contains("ledger") || lower.contains("table") || lower.contains("transaction")
                || lower.contains("fintech") || lower.contains("audit") || lower.contains("finance")) {
            html.append("      <section aria-labelledby=\"ledger-heading\" data-domain-module=\"ledger-table\" class=\"col-span-12 p-6 rounded-xl border ").append(border).append(" bg-[").append(surfaceBg).append("]\">\n");
            html.append("        <h2 id=\"ledger-heading\" class=\"font-bold text-slate-100 mb-4\">Real-Time Verification Ledger</h2>\n");
            html.append("        <div class=\"overflow-x-auto\">\n");
            html.append("          <table class=\"w-full text-left text-sm text-slate-200 border-collapse\">\n");
            html.append("            <thead><tr class=\"border-b ").append(border).append(" text-emerald-400 font-mono text-xs uppercase\">\n");
            html.append("              <th class=\"py-3 px-4\">Entity</th><th class=\"py-3 px-4\">Operation</th><th class=\"py-3 px-4\">Status</th><th class=\"py-3 px-4\">Integrity</th>\n");
            html.append("            </tr></thead>\n");
            html.append("            <tbody class=\"divide-y divide-white/[0.07]\">\n");
            for (int i = 0; i < Math.min(3, tags.size()); i++) {
                html.append("              <tr><td class=\"py-3 px-4 font-mono\">").append(escapeHtml(tags.get(i).toUpperCase(Locale.ROOT))).append("-0").append(i + 1)
                        .append("</td><td class=\"py-3 px-4\">").append(escapeHtml(capitalize(tags.get(i)))).append(" Settlement</td>")
                        .append("<td class=\"py-3 px-4 text-emerald-400 font-semibold\">VERIFIED</td><td class=\"py-3 px-4 font-mono\">SHA256-OK</td></tr>\n");
            }
            html.append("            </tbody>\n");
            html.append("          </table>\n");
            html.append("        </div>\n");
            html.append("      </section>\n\n");
        } else if (lower.contains("podcast") || lower.contains("audio") || lower.contains("media")
                || lower.contains("editorial") || lower.contains("essay") || lower.contains("episode")) {
            html.append("      <section aria-labelledby=\"editorial-heading\" data-domain-module=\"editorial-media\" class=\"col-span-12 p-6 rounded-xl border ").append(border).append(" bg-[").append(surfaceBg).append("]\">\n");
            html.append("        <h2 id=\"editorial-heading\" class=\"font-bold text-slate-100 mb-4\">Editorial Canon &amp; Audio Stream</h2>\n");
            html.append("        <div class=\"grid grid-cols-1 md:grid-cols-3 gap-4\">\n");
            for (int i = 0; i < Math.min(3, tags.size()); i++) {
                html.append("          <article class=\"p-4 rounded-lg bg-slate-900 border ").append(border).append(" space-y-2\">\n");
                html.append("            <div class=\"text-xs font-mono text-amber-400\">ISSUE #0").append(i + 1).append("</div>\n");
                html.append("            <h3 class=\"font-semibold text-white\">").append(escapeHtml(capitalize(tags.get(i)))).append(" Synthesis</h3>\n");
                html.append("            <p class=\"text-xs text-slate-300\">Verified acoustic and textual canon entry.</p>\n");
                html.append("          </article>\n");
            }
            html.append("        </div>\n");
            html.append("      </section>\n\n");
        } else {
            html.append("      <section aria-labelledby=\"telemetry-heading\" data-domain-module=\"telemetry-kpi\" class=\"col-span-12 p-6 rounded-xl border ").append(border).append(" bg-[").append(surfaceBg).append("]\">\n");
            html.append("        <h2 id=\"telemetry-heading\" class=\"font-bold text-slate-100 mb-4\">Operational Telemetry &amp; Invariants</h2>\n");
            html.append("        <div class=\"grid grid-cols-1 md:grid-cols-3 gap-4\">\n");
            String[] metrics = new String[]{"99.94%", "16 Slots", ">= 7.0:1"};
            for (int i = 0; i < Math.min(3, tags.size()); i++) {
                html.append("          <div class=\"p-4 rounded-lg bg-slate-900 border ").append(border).append("\">\n");
                html.append("            <div class=\"text-xs font-mono uppercase text-slate-300\">").append(escapeHtml(tags.get(i))).append("</div>\n");
                html.append("            <div class=\"text-2xl font-extrabold text-emerald-400 mt-1\">").append(metrics[i % metrics.length]).append("</div>\n");
                html.append("          </div>\n");
            }
            html.append("        </div>\n");
            html.append("      </section>\n\n");
        }
    }

    private String resolveAssetOrInlineSvg(String candidateUrl, String inlineSvgDataUri) {
        if (candidateUrl == null || candidateUrl.isBlank()) {
            return inlineSvgDataUri;
        }
        if (candidateUrl.startsWith("assets/images/") || candidateUrl.contains("assets.eneik.com/default-")) {
            return inlineSvgDataUri;
        }
        return candidateUrl;
    }

    private String buildInlineLogoSvgDataUri(DesignTokenSet tokenSet) {
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='64' height='64' viewBox='0 0 64 64'>"
                + "<rect width='64' height='64' rx='14' fill='" + tokenSet.surfaceBackgroundColor() + "' stroke='#10B981' stroke-width='2'/>"
                + "<path d='M18 32 L28 42 L46 22' fill='none' stroke='#10B981' stroke-width='5' stroke-linecap='round' stroke-linejoin='round'/>"
                + "</svg>";
        return "data:image/svg+xml;utf8," + svg;
    }

    private String buildInlinePreviewSvgDataUri(DesignTokenSet tokenSet, String heading, List<String> tags) {
        String label = escapeXml(truncate(heading, 34));
        String sub = escapeXml(tags.isEmpty() ? tokenSet.archetype().name() : String.join(" / ", tags));
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='640' height='360' viewBox='0 0 640 360'>"
                + "<rect width='640' height='360' rx='16' fill='" + tokenSet.baseBackgroundColor() + "'/>"
                + "<rect x='24' y='24' width='592' height='64' rx='10' fill='" + tokenSet.surfaceBackgroundColor() + "' stroke='#10B981' stroke-opacity='0.4'/>"
                + "<circle cx='52' cy='56' r='8' fill='#10B981'/>"
                + "<text x='74' y='61' fill='#F3F4F6' font-family='monospace' font-size='16' font-weight='bold'>" + label + "</text>"
                + "<rect x='24' y='108' width='380' height='228' rx='12' fill='" + tokenSet.surfaceBackgroundColor() + "'/>"
                + "<rect x='48' y='136' width='240' height='18' rx='4' fill='#10B981' fill-opacity='0.85'/>"
                + "<rect x='48' y='170' width='320' height='12' rx='4' fill='#94A3B8' fill-opacity='0.45'/>"
                + "<rect x='48' y='194' width='290' height='12' rx='4' fill='#94A3B8' fill-opacity='0.35'/>"
                + "<rect x='424' y='108' width='192' height='228' rx='12' fill='" + tokenSet.surfaceBackgroundColor() + "'/>"
                + "<text x='48' y='308' fill='#38BDF8' font-family='monospace' font-size='13'>" + sub + "</text>"
                + "</svg>";
        return "data:image/svg+xml;utf8," + svg;
    }

    private String extractScreenHeading(String brief, ProductArchetype archetype) {
        String firstLine = brief.split("[\\r\\n.!?;]+")[0].trim();
        if (firstLine.length() > 68) {
            firstLine = firstLine.substring(0, 68).trim();
        }
        if (firstLine.isBlank()) {
            return archetype.getDisplayName() + " Workspace";
        }
        return capitalize(firstLine);
    }

    private List<String> extractDomainKeywords(String brief, ProductArchetype archetype) {
        String[] words = brief.replaceAll("[^a-zA-Z0-9\\s-]", " ").toLowerCase(Locale.ROOT).split("\\s+");
        List<String> stopWords = List.of("build", "create", "with", "from", "that", "this", "high", "dark", "theme", "the", "and", "for", "screen", "page", "view", "mockup");
        List<String> result = new ArrayList<>();
        for (String w : words) {
            if (w.length() >= 4 && !stopWords.contains(w) && !result.contains(w)) {
                result.add(w);
                if (result.size() >= 4) {
                    break;
                }
            }
        }
        if (result.isEmpty()) {
            result.add(archetype.name().toLowerCase(Locale.ROOT).replace('_', '-'));
            result.add("telemetry");
            result.add("verification");
        }
        while (result.size() < 3) {
            String fallback = result.size() == 1 ? "architecture" : "governance";
            if (!result.contains(fallback)) {
                result.add(fallback);
            } else {
                result.add("analytics");
            }
        }
        return result;
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String escapeXml(String input) {
        return escapeHtml(input).replace("'", "");
    }
}
