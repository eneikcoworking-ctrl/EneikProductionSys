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
                                  String screenshotDownloadUrl, String screenId, String message) {
        public static GeneratedScreen unavailable(String message) {
            return new GeneratedScreen(false, "unavailable", "", "", "", message);
        }
    }

    /**
     * 2026-08-09/10 root-caused live (confirmed against the real Stitch MCP docs, not guessed): the
     * immediate generate_screen_from_text response reliably carries the screenshot but the full HTML/CSS
     * code synthesis lags behind it - htmlCode.downloadUrl is routinely still blank at this point even
     * though the screen itself generated successfully. The bare screen id ("name", same
     * "projects/{p}/screens/{s}" resource-name convention as create_project) is captured here so the
     * caller can follow up with {@link #getScreen} once code synthesis has had time to finish, instead of
     * silently accepting "image only" as the final answer.
     */
    public GeneratedScreen generateScreenFromText(String projectId, String prompt, String modelId) {
        return generateScreenFromText(projectId, prompt, modelId, null);
    }

    /** Same as {@link #generateScreenFromText(String, String, String)}, reusing an existing design system id for consistency across screens. */
    public GeneratedScreen generateScreenFromText(String projectId, String prompt, String modelId, String designSystemId) {
        java.util.Map<String, Object> arguments = new java.util.HashMap<>();
        arguments.put("projectId", projectId);
        arguments.put("prompt", prompt == null ? "" : prompt);
        arguments.put("modelId", modelId == null || modelId.isBlank() ? "GEMINI_3_FLASH" : modelId);
        if (designSystemId != null && !designSystemId.isBlank()) {
            arguments.put("designSystem", designSystemId);
        }
        JsonNode result = callTool("generate_screen_from_text", arguments);
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
                String name = screen.path("name").asText("");
                int slash = name.lastIndexOf('/');
                String screenId = slash >= 0 ? name.substring(slash + 1) : name;
                // 2026-08-10 diagnostic (temporary, operator asked to look for a status/state field we
                // might be ignoring instead of guessing readiness from URL presence alone) - log the
                // COMPLETE screen object, not just the two fields we parse.
                log.info("StitchClient: generate_screen_from_text raw screen object: {}", screen.toString());
                if (!htmlUrl.isBlank() || !screenshotUrl.isBlank()) {
                    return new GeneratedScreen(true, "ok", htmlUrl, screenshotUrl, screenId, "Generated screen via Stitch.");
                }
            }
        }
        return GeneratedScreen.unavailable("Stitch response contained no generated screen.");
    }

    /**
     * Fetches the complete, current state of an already-generated screen - the follow-up call
     * generateScreenFromText's own response cannot substitute for (see its doc comment). Same
     * name/slash-stripping convention as createProject.
     */
    public GeneratedScreen getScreen(String projectId, String screenId) {
        JsonNode result = callTool("get_screen", java.util.Map.of(
                "projectId", projectId,
                "screenId", screenId == null ? "" : screenId
        ));
        if (result == null) {
            return GeneratedScreen.unavailable("Stitch get_screen call failed.");
        }
        log.info("StitchClient: get_screen raw response: {}", result.toString());
        String htmlUrl = result.path("htmlCode").path("downloadUrl").asText("");
        String screenshotUrl = result.path("screenshot").path("downloadUrl").asText("");
        if (htmlUrl.isBlank() && screenshotUrl.isBlank()) {
            return GeneratedScreen.unavailable("Stitch get_screen response had no code or screenshot yet.");
        }
        return new GeneratedScreen(true, "ok", htmlUrl, screenshotUrl, screenId, "Fetched screen via Stitch.");
    }

    // 2026-08-10: field shape confirmed live against the real Stitch MCP tools/list schema.
    // create_design_system takes a structured `designSystem: {displayName, theme: {...}}` object, not
    // a free-text prompt - theme.headlineFont/bodyFont/labelFont must be one of the real font enum
    // values (e.g. LIBRE_CASLON_TEXT, IBM_PLEX_SANS), colorMode is LIGHT/DARK, colors are hex strings.

    public record DesignSystemResult(boolean available, String status, String designSystemId, String message) {
        public static DesignSystemResult unavailable(String message) {
            return new DesignSystemResult(false, "unavailable", "", message);
        }
    }

    /** Creates a Stitch design system from structured theme fields (real MCP schema, not free text). */
    public DesignSystemResult createDesignSystem(String projectId, String displayName, String headlineFont,
                                                  String bodyFont, String primaryColorHex, String secondaryColorHex) {
        ObjectNode theme = objectMapper.createObjectNode();
        theme.put("colorMode", "LIGHT");
        theme.put("headlineFont", headlineFont);
        theme.put("bodyFont", bodyFont);
        theme.put("roundness", "ROUND_EIGHT");
        theme.put("customColor", primaryColorHex);
        theme.put("overridePrimaryColor", primaryColorHex);
        if (secondaryColorHex != null && !secondaryColorHex.isBlank()) {
            theme.put("overrideSecondaryColor", secondaryColorHex);
        }
        ObjectNode designSystem = objectMapper.createObjectNode();
        designSystem.put("displayName", displayName == null ? "Eneik design" : displayName);
        designSystem.set("theme", theme);

        java.util.Map<String, Object> arguments = new java.util.HashMap<>();
        arguments.put("designSystem", objectMapper.convertValue(designSystem, java.util.Map.class));
        if (projectId != null && !projectId.isBlank()) {
            arguments.put("projectId", projectId);
        }
        JsonNode result = callTool("create_design_system", arguments);
        if (result == null) {
            return DesignSystemResult.unavailable("Stitch create_design_system call failed.");
        }
        String name = result.path("name").asText("");
        if (name.isBlank()) {
            return DesignSystemResult.unavailable("Stitch response contained no design system.");
        }
        int slash = name.lastIndexOf('/');
        String id = slash >= 0 ? name.substring(slash + 1) : name;
        return new DesignSystemResult(true, "ok", id, "Created Stitch design system.");
    }

    public record ApplyDesignSystemResult(boolean available, String status, String message) {}

    /** Applies an existing design system to the given screens (or every screen in the project if null/empty). */
    public ApplyDesignSystemResult applyDesignSystem(String projectId, String designSystemId, java.util.List<String> screenIds) {
        JsonNode result = callTool("apply_design_system", java.util.Map.of(
                "projectId", projectId,
                "designSystemId", designSystemId == null ? "" : designSystemId,
                "screenIds", screenIds == null ? java.util.List.of() : screenIds));
        if (result == null) {
            return new ApplyDesignSystemResult(false, "unavailable", "Stitch apply_design_system call failed.");
        }
        return new ApplyDesignSystemResult(true, "ok", "Applied design system.");
    }

    /** Lists the bare IDs of every design system already created for a Stitch project. */
    public java.util.List<String> listDesignSystems(String projectId) {
        JsonNode result = callTool("list_design_systems", java.util.Map.of("projectId", projectId));
        if (result == null || !result.path("designSystems").isArray()) {
            return java.util.List.of();
        }
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (JsonNode ds : result.path("designSystems")) {
            String name = ds.path("name").asText("");
            if (!name.isBlank()) {
                int slash = name.lastIndexOf('/');
                ids.add(slash >= 0 ? name.substring(slash + 1) : name);
            }
        }
        return ids;
    }

    /** Raw MCP tools/list call - returns each tool's name, description and full inputSchema as JSON text. */
    public String listToolsRaw() {
        String apiKey = stitchApiKey();
        if (apiKey.isBlank()) {
            return "{\"error\":\"no api key configured\"}";
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("jsonrpc", "2.0");
            body.put("id", requestId.getAndIncrement());
            body.put("method", "tools/list");
            body.set("params", objectMapper.createObjectNode());

            HttpRequest request = HttpRequest.newBuilder(URI.create(mcpUrl))
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("X-Goog-Api-Key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.body();
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
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
