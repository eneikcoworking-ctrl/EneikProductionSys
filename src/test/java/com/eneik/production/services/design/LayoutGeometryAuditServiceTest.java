package com.eneik.production.services.design;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GROUPING_PROXIMITY_GATE (D011) & FALSIFICATION_HARNESS (D008) tests.
 */
class LayoutGeometryAuditServiceTest {

    private LayoutGeometryAuditService service;

    @BeforeEach
    void setUp() {
        service = new LayoutGeometryAuditService();
    }

    @Test
    void markupWithOverlappingRectanglesFailsAuditWithCollisionReason() {
        String markup = """
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body>
                    <div id="nav-menu" style="left: 20px; top: 30px; width: 150px; height: 80px;">Menu</div>
                    <div id="hero-banner" style="left: 100px; top: 60px; width: 250px; height: 120px;">Hero</div>
                </body>
                </html>
                """;

        LayoutGeometryAuditService.LayoutAuditResult result = service.auditLayout(markup);

        assertThat(result.passed()).isFalse();
        assertThat(result.hasCollisions()).isTrue();
        assertThat(result.collisions()).hasSize(1);
        assertThat(result.collisions().get(0).elementA()).isEqualTo("nav-menu");
        assertThat(result.collisions().get(0).elementB()).isEqualTo("hero-banner");
        assertThat(result.verdictReason()).contains("layout collision");
    }

    @Test
    void markupWithNonOverlappingRectanglesPassesAudit() {
        String markup = """
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body>
                    <div id="header" style="left: 0px; top: 0px; width: 375px; height: 60px;">Header</div>
                    <div id="content" style="left: 0px; top: 70px; width: 375px; height: 250px;">Content</div>
                </body>
                </html>
                """;

        LayoutGeometryAuditService.LayoutAuditResult result = service.auditLayout(markup);

        assertThat(result.passed()).isTrue();
        assertThat(result.hasCollisions()).isFalse();
        assertThat(result.scalable()).isTrue();
    }

    @Test
    void markupWithUserScalableNoFailsScalabilityCheck() {
        String markup = """
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                </head>
                <body>
                    <div id="content" style="left: 0px; top: 0px; width: 375px; height: 200px;">Text</div>
                </body>
                </html>
                """;

        LayoutGeometryAuditService.LayoutAuditResult result = service.auditLayout(markup);

        assertThat(result.passed()).isFalse();
        assertThat(result.scalable()).isFalse();
        assertThat(result.verdictReason()).contains("user-scalable=no");
    }

    @Test
    void markupWithMaximumScale1FailsScalabilityCheck() {
        String markup = """
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
                </head>
                <body>
                    <div id="content" style="left: 0px; top: 0px; width: 375px; height: 200px;">Text</div>
                </body>
                </html>
                """;

        LayoutGeometryAuditService.LayoutAuditResult result = service.auditLayout(markup);

        assertThat(result.passed()).isFalse();
        assertThat(result.scalable()).isFalse();
        assertThat(result.verdictReason()).contains("maximum-scale=1.0");
    }

    @Test
    void markupWithStandardResponsiveViewportPassesScalabilityCheck() {
        String markup = """
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body>
                    <div id="content" style="left: 0px; top: 0px; width: 375px; height: 200px;">Text</div>
                </body>
                </html>
                """;

        LayoutGeometryAuditService.ViewportScalabilityResult result = service.auditViewportScalability(markup);

        assertThat(result.scalable()).isTrue();
        assertThat(result.violation()).isNull();
    }

    @Test
    void svgRectanglesWithCollisionAreDetected() {
        String svg = """
                <svg width="375" height="600">
                    <rect id="button-1" x="10" y="10" width="100" height="40" />
                    <rect id="button-2" x="80" y="20" width="100" height="40" />
                </svg>
                """;

        List<LayoutGeometryAuditService.BoundingBox> boxes = service.extractBoundingBoxes(svg);
        assertThat(boxes).hasSize(2);

        LayoutGeometryAuditService.LayoutAuditResult result = service.auditBoxes(boxes);
        assertThat(result.passed()).isFalse();
        assertThat(result.hasCollisions()).isTrue();
        assertThat(result.collisions().get(0).elementA()).isEqualTo("button-1");
        assertThat(result.collisions().get(0).elementB()).isEqualTo("button-2");
    }

    @Test
    void groupingProximityGate_verifiesGestaltSpatialCloseness() {
        // Nav items close to each other (distance 30), footer item far away (distance 200)
        LayoutGeometryAuditService.BoundingBox nav1 = new LayoutGeometryAuditService.BoundingBox("nav1", "nav", 10, 10, 40, 20);
        LayoutGeometryAuditService.BoundingBox nav2 = new LayoutGeometryAuditService.BoundingBox("nav2", "nav", 60, 10, 40, 20);
        LayoutGeometryAuditService.BoundingBox footer = new LayoutGeometryAuditService.BoundingBox("footer", "footer", 10, 250, 200, 30);

        LayoutGeometryAuditService.LayoutAuditResult result = service.auditBoxes(List.of(nav1, nav2, footer));
        assertThat(result.passed()).isTrue();
        assertThat(result.proximityRatio()).isLessThan(1.0);
    }

    @Test
    void groupingProximityGate_rejectsWhenRelatedElementsAreFartherThanUnrelatedElement() {
        // Nav1 and Nav2 are 300px apart, but Nav1 is only 20px from external element Ad
        LayoutGeometryAuditService.BoundingBox nav1 = new LayoutGeometryAuditService.BoundingBox("nav1", "nav", 10, 10, 40, 20);
        LayoutGeometryAuditService.BoundingBox ad = new LayoutGeometryAuditService.BoundingBox("ad", "promo", 10, 40, 40, 20);
        LayoutGeometryAuditService.BoundingBox nav2 = new LayoutGeometryAuditService.BoundingBox("nav2", "nav", 10, 350, 40, 20);

        LayoutGeometryAuditService.LayoutAuditResult result = service.auditBoxes(List.of(nav1, nav2, ad));
        assertThat(result.passed()).isFalse();
        assertThat(result.proximityRatio()).isGreaterThanOrEqualTo(1.0);
        assertThat(result.verdictReason()).contains("Gestalt proximity gate violated");
    }

    @Test
    void emptyBoxesReturnsCannotJudgeVerdict() {
        LayoutGeometryAuditService.LayoutAuditResult result = service.auditBoxes(List.of());
        assertThat(result.passed()).isFalse();
        assertThat(result.verdict()).isEqualTo(LayoutGeometryAuditService.GeometryVerdict.CANNOT_JUDGE);
        assertThat(result.isCannotJudge()).isTrue();
        assertThat(result.isFailed()).isFalse();
        assertThat(result.verdictReason()).contains("геометрия не выводима: нет элементов для аудита");
    }

    @Test
    void markupWithoutGeometryElementsReturnsCannotJudgeVerdict() {
        String markup = """
                <div class="header">
                    <h1>Title</h1>
                    <p>Description text</p>
                </div>
                """;
        LayoutGeometryAuditService.LayoutAuditResult result = service.auditLayout(markup);
        assertThat(result.passed()).isFalse();
        assertThat(result.verdict()).isEqualTo(LayoutGeometryAuditService.GeometryVerdict.CANNOT_JUDGE);
        assertThat(result.isCannotJudge()).isTrue();
        assertThat(result.isFailed()).isFalse();
        assertThat(result.verdictReason()).contains("геометрия не выводима");
    }

    @Test
    void jsonArrayWithBoxesIsAuditedCorrectly() {
        String json = """
                [
                    {"id": "box-1", "left": 0, "top": 0, "width": 100, "height": 50},
                    {"id": "box-2", "left": 0, "top": 60, "width": 100, "height": 50}
                ]
                """;
        LayoutGeometryAuditService.LayoutAuditResult result = service.auditLayout(json);
        assertThat(result.passed()).isTrue();
        assertThat(result.verdict()).isEqualTo(LayoutGeometryAuditService.GeometryVerdict.PASSED);
        assertThat(result.hasCollisions()).isFalse();
    }

    @Test
    void multiResolutionJsonDesktopAndMobileAudited() {
        String json = """
                {
                    "desktop": [
                        {"id": "sidebar", "left": 0, "top": 0, "width": 300, "height": 800},
                        {"id": "main", "left": 310, "top": 0, "width": 800, "height": 800}
                    ],
                    "mobile": [
                        {"id": "sidebar", "left": 0, "top": 0, "width": 375, "height": 200},
                        {"id": "main", "left": 0, "top": 210, "width": 375, "height": 600}
                    ]
                }
                """;
        LayoutGeometryAuditService.LayoutAuditResult result = service.auditLayout(json);
        assertThat(result.passed()).isTrue();
        assertThat(result.verdict()).isEqualTo(LayoutGeometryAuditService.GeometryVerdict.PASSED);
        assertThat(result.hasCollisions()).isFalse();
    }

    @Test
    void multiResolutionJsonWithMobileCollisionFailsAudit() {
        String json = """
                {
                    "desktop": [
                        {"id": "sidebar", "left": 0, "top": 0, "width": 300, "height": 800},
                        {"id": "main", "left": 310, "top": 0, "width": 800, "height": 800}
                    ],
                    "mobile": [
                        {"id": "sidebar", "left": 0, "top": 0, "width": 375, "height": 200},
                        {"id": "main", "left": 0, "top": 150, "width": 375, "height": 600}
                    ]
                }
                """;
        LayoutGeometryAuditService.LayoutAuditResult result = service.auditLayout(json);
        assertThat(result.passed()).isFalse();
        assertThat(result.verdict()).isEqualTo(LayoutGeometryAuditService.GeometryVerdict.FAILED);
        assertThat(result.hasCollisions()).isTrue();
    }
}

