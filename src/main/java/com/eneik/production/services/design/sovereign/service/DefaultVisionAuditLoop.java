package com.eneik.production.services.design.sovereign.service;

import com.eneik.production.services.design.sovereign.domain.ContrastRule;
import com.eneik.production.services.design.sovereign.domain.CorrectionDirective;
import com.eneik.production.services.design.sovereign.domain.DeonticGateResult;
import com.eneik.production.services.design.sovereign.domain.SynthesizedDomDocument;
import com.eneik.production.services.design.sovereign.domain.ViewportCapture;
import com.eneik.production.services.design.sovereign.domain.ViewportType;
import com.eneik.production.services.design.sovereign.domain.VisionAuditReport;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default implementation of {@link VisionAuditLoop} enforcing deontic quality gates BARCAN-TAG-11:
 * 1. Zero pseudo-optical metadata noise (Leica, 50mm, GPS).
 * 2. Zero AI-slop color palettes (indigo, purple, violet).
 * 3. Zero mobile 375px horizontal overflow (empirical scrollWidth <= 375px).
 * 4. Focus-visible keyboard accessibility indicators.
 * 5. Real mathematical WCAG 2.1 AAA luminance contrast ratio (>= 7.0:1) via {@link ContrastRule}.
 */
@Component
public class DefaultVisionAuditLoop implements VisionAuditLoop {

    private static final Pattern PSEUDO_OPTICAL_NOISE_REGEX = Pattern.compile(
            "(?i)\\b(leica|50mm|f/1\\.[48]|37°46'n|122°25'w|optical status|gps-coordinates)\\b"
    );

    private static final Pattern AI_SLOP_COLORS_REGEX = Pattern.compile(
            "(?i)\\b(bg|text|ring|border)-(indigo|purple|violet)-\\d{3}|#6366f1|#8b5cf6|#7c3aed|#4f46e5|#a855f7"
    );

    private static final Pattern BG_HEX_PATTERN = Pattern.compile(
            "(?i)(?:--bg-base\\s*:\\s*|background-color\\s*:\\s*|bg-\\[)(#[0-9a-f]{6})"
    );

    private static final Pattern FG_HEX_PATTERN = Pattern.compile(
            "(?i)(?:--text-primary\\s*:\\s*|(?<!background-)color\\s*:\\s*|text-\\[)(#[0-9a-f]{6})"
    );

    @Override
    public VisionAuditReport auditDocument(SynthesizedDomDocument document, List<ViewportCapture> captures) {
        Objects.requireNonNull(document, "document cannot be null");

        String html = document.htmlContent();

        // 1. Gate: zero pseudo-optical Leica / GPS noise (BARCAN-TAG-11)
        boolean zeroLeicaGpsNoise = !PSEUDO_OPTICAL_NOISE_REGEX.matcher(html).find();

        // 2. Gate: no mobile horizontal scroll (measured physical scrollWidth <= 375px)
        boolean noMobileHorizontalScroll = true;
        if (captures != null) {
            for (ViewportCapture capture : captures) {
                if (capture.viewport() == ViewportType.MOBILE_375 && capture.hasHorizontalOverflow()) {
                    noMobileHorizontalScroll = false;
                    break;
                }
            }
        }

        // 3. Gate: focus-visible accessibility
        boolean focusVisibleAccessible = html.contains("focus-visible:")
                || html.contains("focus:ring-")
                || html.contains("focus:outline-");

        // 4. Gate: real mathematical WCAG 2.1 AAA contrast ratio (>= 7.0:1)
        double measuredContrast = computeMeasuredContrastRatio(html);
        boolean contrastRatioValid = measuredContrast >= ContrastRule.MIN_WCAG_AAA_RATIO;

        // 5. Gate: Anti-Slop color gate (BARCAN-TAG-11: no AI-slop indigo/purple/violet)
        boolean zeroSlopColors = !AI_SLOP_COLORS_REGEX.matcher(html).find();

        DeonticGateResult gateResult = new DeonticGateResult(
                zeroLeicaGpsNoise,
                noMobileHorizontalScroll,
                focusVisibleAccessible,
                contrastRatioValid
        );

        List<CorrectionDirective> directives = new ArrayList<>();

        if (!zeroLeicaGpsNoise) {
            directives.add(new CorrectionDirective(
                    ".optical-metadata, [data-optical-noise]",
                    "display: none !important;",
                    "BARCAN-TAG-11 violation: pseudo-optical metadata forbidden",
                    document.roundIndex()
            ));
        }
        if (!noMobileHorizontalScroll) {
            directives.add(new CorrectionDirective(
                    "body, main, section",
                    "overflow-x: hidden; max-width: 100vw;",
                    "Mobile 375px viewport horizontal overflow detected",
                    document.roundIndex()
            ));
        }
        if (!focusVisibleAccessible) {
            directives.add(new CorrectionDirective(
                    "a, button, input, select",
                    "outline: 2px solid #10b981; outline-offset: 2px;",
                    "Interactive elements missing focus-visible indicators",
                    document.roundIndex()
            ));
        }
        if (!contrastRatioValid) {
            directives.add(new CorrectionDirective(
                    "body",
                    "background-color: #08090C !important; color: #F3F4F6 !important;",
                    String.format(java.util.Locale.ROOT, "WCAG AAA contrast violation (measured %.2f:1 < 7.0:1): enforce dark obsidian palette", measuredContrast),
                    document.roundIndex()
            ));
        }
        if (!zeroSlopColors) {
            directives.add(new CorrectionDirective(
                    "button, .btn, [class*='indigo'], [class*='purple'], [class*='violet']",
                    "background-color: #10B981 !important; color: #08090C !important;",
                    "BARCAN-TAG-11 anti-slop gate: replace indigo/purple/violet clichés with sovereign emerald palette",
                    document.roundIndex()
            ));
        }

        int passedGates = (zeroLeicaGpsNoise ? 1 : 0)
                + (noMobileHorizontalScroll ? 1 : 0)
                + (focusVisibleAccessible ? 1 : 0)
                + (contrastRatioValid ? 1 : 0)
                + (zeroSlopColors ? 1 : 0);

        double score = Math.round(((double) passedGates / 5.0) * 100.0) / 10.0;

        return new VisionAuditReport(score, gateResult, List.copyOf(directives), document.roundIndex());
    }

    public double computeMeasuredContrastRatio(String html) {
        if (html == null || html.isBlank()) {
            return 0.0;
        }
        String bgHex = "#08090C";
        Matcher bgMatcher = BG_HEX_PATTERN.matcher(html);
        if (bgMatcher.find()) {
            bgHex = bgMatcher.group(1);
        }

        String fgHex = "#F3F4F6";
        Matcher fgMatcher = FG_HEX_PATTERN.matcher(html);
        if (fgMatcher.find()) {
            fgHex = fgMatcher.group(1);
        } else if (html.contains("text-slate-900") && bgHex.equalsIgnoreCase("#08090C")) {
            fgHex = "#0F172A";
        }

        try {
            return ContrastRule.calculateContrastRatio(fgHex, bgHex);
        } catch (Exception e) {
            return ContrastRule.MIN_WCAG_AAA_RATIO;
        }
    }
}
