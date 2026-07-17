package com.eneik.production.services.stitch;

import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal JSON-RPC client for Stitch's remote MCP server (https://stitch.googleapis.com/mcp).
 * Stitch generates UI screens (HTML + screenshot) from text prompts using Gemini models, but is
 * billed/rate-limited independently from the main Gemini Developer API prepay balance - it is used
 * here as a free alternative to nano-banana image generation for UI mockups.
 */
@Service
public class StitchClient {
    private static final Logger log = LoggerFactory.getLogger(StitchClient.class);

    private final SystemSettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String mcpUrl;
    private final int requestTimeoutSeconds;
    private final AtomicLong requestId = new AtomicLong(1);

    public StitchClient(SystemSettingsService settingsService,
                        ObjectMapper objectMapper,
                        @Value("${stitch.mcp-url:https://stitch.googleapis.com/mcp}") String mcpUrl,
                        @Value("${stitch.request-timeout-seconds:280}") int requestTimeoutSeconds) {
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.mcpUrl = mcpUrl == null || mcpUrl.isBlank() ? "https://stitch.googleapis.com/mcp" : mcpUrl.trim();
        this.requestTimeoutSeconds = Math.max(30, Math.min(600, requestTimeoutSeconds));
    }

    public boolean hasStitchKey() {
        return !stitchApiKey().isBlank();
    }

    private String stitchApiKey() {
        String value = settingsService.effectiveValue("stitch_api_key");
        return value == null ? "" : value.trim();
    }

    /**
     * Creates a new Stitch project and returns its bare ID (without the "projects/" prefix),
     * or null if the call failed.
     */
    public String createProject(String title) {
        JsonNode result = callTool("create_project", java.util.Map.of("title", title == null ? "Eneik design" : title));
        if (result == null) {
            return null;
        }
        String name = result.path("name").asText("");
        if (name.isBlank()) {
            return null;
        }
        int slash = name.lastIndexOf('/');
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    public record GeneratedScreen(boolean available, String status, String htmlDownloadUrl,
                                  String screenshotDownloadUrl, String message) {
        public static GeneratedScreen unavailable(String message) {
            return new GeneratedScreen(false, "unavailable", "", "", message);
        }
    }

    public GeneratedScreen generateScreenFromText(String projectId, String prompt, String modelId) {
        JsonNode result = callTool("generate_screen_from_text", java.util.Map.of(
                "projectId", projectId,
                "prompt", prompt == null ? "" : prompt,
                "modelId", modelId == null || modelId.isBlank() ? "GEMINI_3_FLASH" : modelId
        ));
        if (result == null) {
            return GeneratedScreen.unavailable("Stitch generate_screen_from_text call failed.");
        }
        for (JsonNode component : result.path("outputComponents")) {
            JsonNode design = component.path("design");
            JsonNode screens = design.path("screens");
            if (screens.isArray() && !screens.isEmpty()) {
                JsonNode screen = screens.get(0);
                String htmlUrl = screen.path("htmlCode").path("downloadUrl").asText("");
                String screenshotUrl = screen.path("screenshot").path("downloadUrl").asText("");
                if (!htmlUrl.isBlank() || !screenshotUrl.isBlank()) {
                    return new GeneratedScreen(true, "ok", htmlUrl, screenshotUrl, "Generated screen via Stitch.");
                }
            }
        }
        return GeneratedScreen.unavailable("Stitch response contained no generated screen.");
    }

    /** Downloads a Stitch file (screenshot image or HTML) using the same API key as authentication. */
    public byte[] download(String downloadUrl) {
        String apiKey = stitchApiKey();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl))
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .header("X-Goog-Api-Key", apiKey)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("StitchClient: download failed with HTTP {} for {}", response.statusCode(), redactUrl(downloadUrl));
                return null;
            }
            return response.body();
        } catch (Exception e) {
            log.warn("StitchClient: download failed: {}", e.getMessage());
            return null;
        }
    }

    private JsonNode callTool(String toolName, java.util.Map<String, Object> arguments) {
        String apiKey = stitchApiKey();
        if (apiKey.isBlank()) {
            log.warn("StitchClient: Stitch API key is not configured; skipping tool call {}", toolName);
            return null;
        }
        try {
            ObjectNode params = objectMapper.createObjectNode();
            params.put("name", toolName);
            params.set("arguments", objectMapper.valueToTree(arguments));

            ObjectNode body = objectMapper.createObjectNode();
            body.put("jsonrpc", "2.0");
            body.put("id", requestId.getAndIncrement());
            body.put("method", "tools/call");
            body.set("params", params);

            HttpRequest request = HttpRequest.newBuilder(URI.create(mcpUrl))
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("X-Goog-Api-Key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("StitchClient: tool call {} failed with HTTP {}: {}", toolName, response.statusCode(), preview(response.body()));
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (root.has("error")) {
                log.warn("StitchClient: tool call {} returned JSON-RPC error: {}", toolName, root.path("error").toString());
                return null;
            }
            JsonNode content = root.path("result").path("content");
            if (content.isArray() && !content.isEmpty()) {
                JsonNode first = content.get(0);
                boolean isError = root.path("result").path("isError").asBoolean(false);
                String text = first.path("text").asText("");
                if (isError) {
                    log.warn("StitchClient: tool call {} returned an error result: {}", toolName, text);
                    return null;
                }
                return objectMapper.readTree(text);
            }
            return null;
        } catch (Exception e) {
            log.warn("StitchClient: tool call {} failed: {}", toolName, e.getMessage());
            return null;
        }
    }

    private String preview(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 500 ? value : value.substring(0, 500) + "...";
    }

    private String redactUrl(String url) {
        if (url == null) {
            return "";
        }
        int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) : url;
    }
}
