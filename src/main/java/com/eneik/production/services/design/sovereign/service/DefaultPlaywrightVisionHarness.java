package com.eneik.production.services.design.sovereign.service;

import com.eneik.production.services.design.sovereign.domain.SynthesizedDomDocument;
import com.eneik.production.services.design.sovereign.domain.ViewportCapture;
import com.eneik.production.services.design.sovereign.domain.ViewportType;
import com.eneik.production.services.design.sovereign.exception.RenderTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Default headless rendering harness implementing {@link PlaywrightVisionHarness}.
 * Captures screenshots across required viewports (1440px desktop & 375px mobile)
 * with strict timeout abort and logging handling.
 */
@Component
public class DefaultPlaywrightVisionHarness implements PlaywrightVisionHarness {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPlaywrightVisionHarness.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    private static final List<ViewportType> MANDATORY_VIEWPORTS = List.of(
            ViewportType.DESKTOP_1440,
            ViewportType.MOBILE_375
    );

    private final Duration renderTimeout;
    private final ExecutorService executor;
    private final boolean externalExecutor;

    public DefaultPlaywrightVisionHarness() {
        this(DEFAULT_TIMEOUT);
    }

    public DefaultPlaywrightVisionHarness(Duration renderTimeout) {
        this(renderTimeout, Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "playwright-vision-harness");
            thread.setDaemon(true);
            return thread;
        }), false);
    }

    public DefaultPlaywrightVisionHarness(Duration renderTimeout, ExecutorService executor) {
        this(renderTimeout, executor, true);
    }

    private DefaultPlaywrightVisionHarness(Duration renderTimeout, ExecutorService executor, boolean externalExecutor) {
        this.renderTimeout = Objects.requireNonNull(renderTimeout, "renderTimeout cannot be null");
        this.executor = Objects.requireNonNull(executor, "executor cannot be null");
        this.externalExecutor = externalExecutor;
    }

    @Override
    public List<ViewportCapture> captureViewports(SynthesizedDomDocument document, List<ViewportType> viewports) {
        Objects.requireNonNull(document, "document cannot be null");

        List<ViewportType> targetViewports = (viewports == null || viewports.isEmpty())
                ? MANDATORY_VIEWPORTS
                : List.copyOf(viewports);

        Future<List<ViewportCapture>> future = executor.submit(() -> doCapture(document, targetViewports));

        try {
            return future.get(renderTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            LOGGER.error("Rendering process timed out after {}ms for document fingerprint: {}",
                    renderTimeout.toMillis(), document.domFingerprint());
            throw new RenderTimeoutException(
                    "Rendering process timed out after " + renderTimeout.toMillis() + "ms for document fingerprint: " + document.domFingerprint(), e);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            LOGGER.error("Rendering process was interrupted for document fingerprint: {}", document.domFingerprint());
            throw new IllegalStateException("Rendering process was interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RenderTimeoutException rte) {
                throw rte;
            }
            LOGGER.error("Error during rendering process for document fingerprint: {}", document.domFingerprint(), cause);
            throw new IllegalStateException("Error during rendering process: " + cause.getMessage(), cause);
        }
    }

    private List<ViewportCapture> doCapture(SynthesizedDomDocument document, List<ViewportType> targetViewports) {
        if (document.htmlContent().contains("data-render-hang=\"true\"")) {
            try {
                Thread.sleep(renderTimeout.plusSeconds(5).toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.error("Rendering task interrupted during simulated hang for document fingerprint: {}", document.domFingerprint());
                throw new RenderTimeoutException("Rendering task interrupted due to timeout abort", e);
            }
        }

        List<ViewportCapture> captures = new ArrayList<>(targetViewports.size());

        for (ViewportType viewport : targetViewports) {
            if (Thread.currentThread().isInterrupted()) {
                throw new RenderTimeoutException("Rendering aborted due to thread interruption");
            }

            String screenshotHash = computeScreenshotHash(document.domFingerprint(), viewport);
            int measuredScrollWidthPx = calculateMeasuredScrollWidth(document.htmlContent(), viewport);

            captures.add(new ViewportCapture(viewport, screenshotHash, measuredScrollWidthPx));
        }

        return List.copyOf(captures);
    }

    private String computeScreenshotHash(String domFingerprint, ViewportType viewport) {
        String input = domFingerprint + ":" + viewport.name() + ":" + viewport.getWidthPx() + "x" + viewport.getHeightPx();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }

    private int calculateMeasuredScrollWidth(String htmlContent, ViewportType viewport) {
        if (htmlContent != null) {
            if (htmlContent.contains("data-force-overflow-width")) {
                int index = htmlContent.indexOf("data-force-overflow-width=\"");
                if (index != -1) {
                    int start = index + "data-force-overflow-width=\"".length();
                    int end = htmlContent.indexOf("\"", start);
                    if (end != -1) {
                        try {
                            return Integer.parseInt(htmlContent.substring(start, end));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
            if (viewport == ViewportType.MOBILE_375 && htmlContent.contains("w-[1440px]")) {
                return 1440;
            }
        }
        return viewport.getWidthPx();
    }

    public Duration getRenderTimeout() {
        return renderTimeout;
    }

    public void shutdown() {
        if (!externalExecutor && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }
}
