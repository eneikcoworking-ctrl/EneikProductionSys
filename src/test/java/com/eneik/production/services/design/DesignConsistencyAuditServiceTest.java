package com.eneik.production.services.design;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DesignConsistencyAuditServiceTest {

    private final DesignConsistencyAuditService service = new DesignConsistencyAuditService();

    private static final DesignConsistencyAuditService.TokenSet VERDANT_FLOW_TOKENS =
            DesignConsistencyAuditService.TokenSet.of(
                    List.of("#fbf9f1", "#7d8570", "#3f7d32", "#d97b29", "#e0342f", "#c99a2e"),
                    List.of("Libre Caslon Text", "IBM Plex Sans"));

    @Test
    void extractUsedTokensFindsHexColorsAndFontFamilies() {
        String html = "<style>body{background:#FBF9F1;color:#7D8570;font-family: 'Libre Caslon Text', serif;}</style>";
        var used = service.extractUsedTokens(html);
        assertThat(used.colors()).contains("#fbf9f1", "#7d8570");
        assertThat(used.fonts()).contains("libre caslon text", "serif");
    }

    @Test
    void traceRatioIsOneWhenEveryUsedValueIsOnToken() {
        String html = "<style>body{background:#fbf9f1;} h1{color:#7d8570;} .gold{color:#c99a2e;}</style>";
        var used = service.extractUsedTokens(html);
        assertThat(service.traceRatio(used, VERDANT_FLOW_TOKENS)).isEqualTo(1.0);
    }

    @Test
    void traceRatioIsNearZeroWhenPaletteIsCompletelyOffToken() {
        // real colors pulled from tonight's forge-factory-v2.html (black/gold register)
        String html = "<style>body{background:#090f13;} .card{background:#161c21;color:#201c00;}</style>";
        var used = service.extractUsedTokens(html);
        assertThat(service.traceRatio(used, VERDANT_FLOW_TOKENS)).isZero();
    }

    @Test
    void traceRatioIsPartialWhenSomeValuesAreOnTokenAndSomeAreNot() {
        String html = "<style>body{background:#fbf9f1;} .off{color:#0053db;}</style>";
        var used = service.extractUsedTokens(html);
        assertThat(service.traceRatio(used, VERDANT_FLOW_TOKENS)).isEqualTo(0.5);
    }

    @Test
    void jaccardIsOneForIdenticalTokenSets() {
        var a = service.extractUsedTokens("<style>body{background:#fbf9f1;color:#7d8570;}</style>");
        var b = service.extractUsedTokens("<style>div{background:#fbf9f1;} h1{color:#7d8570;}</style>");
        assertThat(service.jaccard(a, b)).isEqualTo(1.0);
    }

    @Test
    void jaccardIsZeroForDisjointTokenSets() {
        var a = service.extractUsedTokens("<style>body{background:#fbf9f1;}</style>");
        var b = service.extractUsedTokens("<style>body{background:#090f13;}</style>");
        assertThat(service.jaccard(a, b)).isZero();
    }

    @Test
    void auditAcceptsOnTokenScreenWithNoSiblings() {
        String html = "<style>body{background:#fbf9f1;color:#7d8570;font-family:'IBM Plex Sans';}</style>";
        var report = service.audit(html, VERDANT_FLOW_TOKENS, List.of());
        assertThat(report.traceAccepted()).isTrue();
        assertThat(report.crossScreenAccepted()).isTrue();
        assertThat(report.offTokenValues()).isEmpty();
    }

    @Test
    void auditRejectsOffTokenScreen() {
        String html = "<style>body{background:#090f13;color:#161c21;}</style>";
        var report = service.audit(html, VERDANT_FLOW_TOKENS, List.of());
        assertThat(report.traceAccepted()).isFalse();
        assertThat(report.offTokenValues()).contains("#090f13", "#161c21");
    }

    /**
     * Regression fixture for the real 2026-08-10 incident: three Forge screens generated under the
     * same designSystemId came back in three disjoint color registers (black/gold, navy/purple,
     * cream) - hex values below are pulled directly from that night's actual generated HTML files
     * (data/design-assets/, gitignored, not committed - inlined here so the regression is
     * reproducible without depending on that gitignored directory).
     */
    @Test
    void crossScreenJaccardCatchesTheRealAugust10Incident() {
        String factoryHtml = "<style>body{background:#090f13;} .a{background:#161c21;} .b{color:#201c00;}</style>";
        String deliveryHtml = "<style>body{background:#00174b;} .a{background:#002a78;} .b{color:#0053db;}</style>";
        String productHtml = "<style>body{background:#000000;} .a{background:#1a1c1f;} .b{color:#38485d;}</style>";

        var report = service.audit(factoryHtml, VERDANT_FLOW_TOKENS, List.of(deliveryHtml, productHtml));

        assertThat(report.traceAccepted()).isFalse();
        assertThat(report.crossScreenAccepted()).isFalse();
        assertThat(report.avgCrossScreenJaccard()).isLessThan(DesignConsistencyAuditService.MIN_CROSS_SCREEN_JACCARD);
    }

    @Test
    void auditAcceptsConsistentSiblingScreens() {
        String screenA = "<style>body{background:#fbf9f1;} .a{color:#7d8570;} .b{color:#c99a2e;}</style>";
        String screenB = "<style>body{background:#fbf9f1;} .a{color:#7d8570;} .c{color:#3f7d32;}</style>";

        var report = service.audit(screenA, VERDANT_FLOW_TOKENS, List.of(screenB));

        assertThat(report.traceAccepted()).isTrue();
        assertThat(report.crossScreenAccepted()).isTrue();
    }

    @Test
    void emptyHtmlTrivializesToAcceptedWithNoTokens() {
        var report = service.audit("", VERDANT_FLOW_TOKENS, List.of());
        assertThat(report.traceRatio()).isEqualTo(1.0);
        assertThat(report.traceAccepted()).isTrue();
    }
}
