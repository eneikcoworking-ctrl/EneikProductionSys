package com.eneik.production.services.design.sovereign.service;

import com.eneik.production.services.design.sovereign.domain.CorrectionDirective;
import com.eneik.production.services.design.sovereign.domain.DeonticGateResult;
import com.eneik.production.services.design.sovereign.domain.SynthesizedDomDocument;
import com.eneik.production.services.design.sovereign.domain.ViewportCapture;
import com.eneik.production.services.design.sovereign.domain.ViewportType;
import com.eneik.production.services.design.sovereign.domain.VisionAuditReport;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Default implementation of {@link VisionAuditLoop} enforcing deontic quality gates BARCAN-TAG-11
 * (zero Leica/GPS visual noise, no mobile 375px horizontal overflow, focus-visible accessibility).
 */
@Component
public class DefaultVisionAuditLoop implements VisionAuditLoop {

    private static final List<String> FORBIDDEN_NOISE_PATTERNS = List.of(
            "leica", "50mm", "f/1.4", "f/1.8", "37°46'n", "122°25'w", "optical status", "gps-coordinates"
    );

    @Override
    public VisionAuditReport auditDocument(SynthesizedDomDocument document, List<ViewportCapture> captures) {
        Objects.requireNonNull(document, "document cannot be null");

        String lowerHtml = document.htmlContent().toLowerCase(Locale.ROOT);

        // 1. Gate: zero Leica / GPS / optical metadata noise
        boolean zeroLeicaGpsNoise = true;
        for (String pattern : FORBIDDEN_NOISE_PATTERNS) {
            if (lowerHtml.contains(pattern)) {
                zeroLeicaGpsNoise = false;
                break;
            }
        }

        // 2. Gate: no mobile horizontal scroll
        boolean noMobileHorizontalScroll = true;
        if (captures != null) {
            for (ViewportCapture capture : captures) {
                if (capture.viewport() == ViewportType.MOBILE_375 && capture.hasHorizontalOverflow()) {
                    noMobileHorizontalScroll = false;
                    break;
                }
            }
        }
        if (lowerHtml.contains("data-force-overflow-width") || (lowerHtml.contains("w-[1440px]") && !lowerHtml.contains("max-w-full"))) {
            noMobileHorizontalScroll = false;
        }

        // 3. Gate: focus-visible accessibility
        boolean focusVisibleAccessible = lowerHtml.contains("focus-visible:")
                || lowerHtml.contains("focus:ring-")
                || lowerHtml.contains("focus:outline-");

        // 4. Gate: contrast ratio valid
        boolean contrastRatioValid = !lowerHtml.contains("data-contrast-fail=\"true\"");

        DeonticGateResult gateResult = new DeonticGateResult(
                zeroLeicaGpsNoise,
                noMobileHorizontalScroll,
                focusVisibleAccessible,
                contrastRatioValid
        );

        List<CorrectionDirective> directives = new ArrayList<>();
        double score;

        if (gateResult.allGatesPassed()) {
            score = 9.6;
        } else {
            score = 7.2;
            if (!zeroLeicaGpsNoise) {
                directives.add(new CorrectionDirective(
                        ".optical-metadata, [data-optical-noise]",
                        "display: none !important;",
                        "BARCAN-TAG-11 violation: pseudo-optical Leica/GPS metadata forbidden",
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
                        "outline: 2px solid #6366f1; outline-offset: 2px;",
                        "Interactive elements missing focus-visible indicators",
                        document.roundIndex()
                ));
            }
            if (!contrastRatioValid) {
                directives.add(new CorrectionDirective(
                        "body",
                        "background-color: #08090C !important; color: #F3F4F6 !important;",
                        "WCAG AAA contrast violation: enforce dark obsidian palette",
                        document.roundIndex()
                ));
            }
        }

        return new VisionAuditReport(score, gateResult, List.copyOf(directives), document.roundIndex());
    }
}
