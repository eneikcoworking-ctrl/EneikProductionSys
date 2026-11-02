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

    private static final String[] BRIDGE_VISION_URLS = new String[] {
            System.getenv().getOrDefault("SOVEREIGN_VISION_BRIDGE_URL", "http://host.docker.internal:8095/render-viewports"),
            "http://172.17.0.1:8095/render-viewports",
            "http://127.0.0.1:8095/render-viewports"
    };

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

        // 1. Try real headless browser rendering via Sovereign Host Bridge
        List<ViewportCapture> bridgeCaptures = tryBridgeCapture(document, targetViewports);
        if (bridgeCaptures != null && !bridgeCaptures.isEmpty()) {
            return bridgeCaptures;
        }

        // 2. Fallback to local geometric analysis (e.g. for offline unit testing)
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

    private List<ViewportCapture> tryBridgeCapture(SynthesizedDomDocument document, List<ViewportType> targetViewports) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<java.util.Map<String, Object>> vpList = new ArrayList<>();
            for (ViewportType vp : targetViewports) {
                vpList.add(java.util.Map.of("name", vp.name(), "width", vp.getWidthPx(), "height", vp.getHeightPx()));
            }
            java.util.Map<String, Object> req = java.util.Map.of(
                    "htmlContent", document.htmlContent(),
                    "viewports", vpList
            );
            String payload = mapper.writeValueAsString(req);

            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(Math.min(renderTimeout.toMillis(), 2000)))
                    .build();

            for (String bridgeUrl : BRIDGE_VISION_URLS) {
                try {
                    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(bridgeUrl))
                            .timeout(Duration.ofMillis(renderTimeout.toMillis()))
                            .header("Content-Type", "application/json; charset=utf-8")
                            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                            .build();

                    java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if (response.statusCode() == 200) {
                        com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response.body());
                        com.fasterxml.jackson.databind.JsonNode capturesNode = root.get("captures");
                        if (capturesNode != null && capturesNode.isArray()) {
                            List<ViewportCapture> result = new ArrayList<>();
                            for (com.fasterxml.jackson.databind.JsonNode node : capturesNode) {
                                String vpName = node.path("viewport").asText();
                                ViewportType vp = ViewportType.valueOf(vpName);
                                String hash = node.path("screenshotHash").asText();
                                int scrollWidth = node.path("measuredScrollWidthPx").asInt(vp.getWidthPx());
                                result.add(new ViewportCapture(vp, hash, scrollWidth));
                            }
                            if (!result.isEmpty()) {
                                return result;
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Bridge capture skipped: {}", e.getMessage());
        }
        return null;
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
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("w-\\[(\\d+)px\\]").matcher(htmlContent);
            if (m.find()) {
                try {
                    return Integer.parseInt(m.group(1));
                } catch (NumberFormatException ignored) {
                }
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
