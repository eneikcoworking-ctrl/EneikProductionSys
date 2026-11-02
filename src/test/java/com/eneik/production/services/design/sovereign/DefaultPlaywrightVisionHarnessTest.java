package com.eneik.production.services.design.sovereign;

import com.eneik.production.services.design.sovereign.domain.SynthesizedDomDocument;
import com.eneik.production.services.design.sovereign.domain.ViewportCapture;
import com.eneik.production.services.design.sovereign.domain.ViewportType;
import com.eneik.production.services.design.sovereign.exception.RenderTimeoutException;
import com.eneik.production.services.design.sovereign.service.DefaultPlaywrightVisionHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultPlaywrightVisionHarnessTest {

    private DefaultPlaywrightVisionHarness harness;

    @BeforeEach
    void setUp() {
        harness = new DefaultPlaywrightVisionHarness(Duration.ofSeconds(3));
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.shutdown();
        }
    }

    @Test
    @DisplayName("Should capture screenshots at mandatory 1440px desktop and 375px mobile viewports by default")
    void testCaptureViewportsMandatoryDefaults() {
        SynthesizedDomDocument document = SynthesizedDomDocument.create("<html><body><header>Nav</header></body></html>", 0);

        List<ViewportCapture> captures = harness.captureViewports(document, null);

        assertNotNull(captures);
        assertEquals(2, captures.size());

        ViewportCapture desktopCapture = captures.stream()
                .filter(c -> c.viewport() == ViewportType.DESKTOP_1440)
                .findFirst()
                .orElseThrow();

        assertEquals(1440, desktopCapture.viewport().getWidthPx());
        assertEquals(900, desktopCapture.viewport().getHeightPx());
        assertEquals(1440, desktopCapture.measuredScrollWidthPx());
        assertFalse(desktopCapture.hasHorizontalOverflow());
        assertNotNull(desktopCapture.screenshotHash());
        assertFalse(desktopCapture.screenshotHash().isBlank());

        ViewportCapture mobileCapture = captures.stream()
                .filter(c -> c.viewport() == ViewportType.MOBILE_375)
                .findFirst()
                .orElseThrow();

        assertEquals(375, mobileCapture.viewport().getWidthPx());
        assertEquals(812, mobileCapture.viewport().getHeightPx());
        assertEquals(375, mobileCapture.measuredScrollWidthPx());
        assertFalse(mobileCapture.hasHorizontalOverflow());
        assertNotNull(mobileCapture.screenshotHash());
        assertFalse(mobileCapture.screenshotHash().isBlank());
    }

    @Test
    @DisplayName("Should detect horizontal overflow on mobile 375px viewport when forced in DOM")
    void testDetectHorizontalOverflow() {
        SynthesizedDomDocument document = SynthesizedDomDocument.create(
                "<html><body><div data-force-overflow-width=\"500\">Wide</div></body></html>",
                0
        );

        List<ViewportCapture> captures = harness.captureViewports(document, List.of(ViewportType.MOBILE_375));

        assertNotNull(captures);
        assertEquals(1, captures.size());

        ViewportCapture capture = captures.get(0);
        assertEquals(500, capture.measuredScrollWidthPx());
        assertTrue(capture.hasHorizontalOverflow(), "Should have horizontal overflow when width 500 > 375");
    }

    @Test
    @DisplayName("Should abort and throw RenderTimeoutException when rendering exceeds timeout")
    void testRenderTimeoutAbort() {
        DefaultPlaywrightVisionHarness quickTimeoutHarness = new DefaultPlaywrightVisionHarness(Duration.ofMillis(200));

        try {
            SynthesizedDomDocument hangingDoc = SynthesizedDomDocument.create(
                    "<html><body><div data-render-hang=\"true\">Hanging</div></body></html>",
                    0
            );

            RenderTimeoutException ex = assertThrows(
                    RenderTimeoutException.class,
                    () -> quickTimeoutHarness.captureViewports(hangingDoc, null)
            );

            assertNotNull(ex.getMessage());
            assertTrue(ex.getMessage().contains("timed out after 200ms"));
        } finally {
            quickTimeoutHarness.shutdown();
        }
    }
}
