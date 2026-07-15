package com.eneik.production.services.googleai;

import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GoogleAiResourceService {
    private final SystemSettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String interactionsUrl;
    private final String modelsUrl;
    private final int requestTimeoutSeconds;

    public GoogleAiResourceService(SystemSettingsService settingsService,
                                   ObjectMapper objectMapper,
                                   @Value("${google-ai.interactions-url:https://generativelanguage.googleapis.com/v1beta/interactions}") String interactionsUrl,
                                   @Value("${google-ai.models-url:https://generativelanguage.googleapis.com/v1beta/models}") String modelsUrl,
                                   @Value("${google-ai.request-timeout-seconds:120}") int requestTimeoutSeconds) {
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.interactionsUrl = blank(interactionsUrl) ? "https://generativelanguage.googleapis.com/v1beta/interactions" : interactionsUrl.trim();
        this.modelsUrl = blank(modelsUrl) ? "https://generativelanguage.googleapis.com/v1beta/models" : modelsUrl.trim();
        this.requestTimeoutSeconds = Math.max(10, Math.min(900, requestTimeoutSeconds));
    }

    public List<Map<String, Object>> resourceMatrix() {
        boolean googleKey = hasGoogleAiKey();
        String credentialSource = googleAiCredentialSource();
        boolean geminiEnabled = settingsService.effectiveBoolean("gemini_enabled");
        boolean antigravityEnabled = settingsService.effectiveBoolean("antigravity_enabled");
        boolean searchEnabled = settingsService.effectiveBoolean("google_search_grounding_enabled");
        boolean urlEnabled = settingsService.effectiveBoolean("url_context_enabled");
        boolean designEnabled = settingsService.effectiveBoolean("design_service_enabled");
        boolean nanoEnabled = settingsService.effectiveBoolean("nano_banana_enabled");
        boolean veoEnabled = settingsService.effectiveBoolean("veo_enabled");

        List<Map<String, Object>> resources = new ArrayList<>();
        resources.add(resource(
                "gemini_text",
                "Gemini Text",
                geminiEnabled && googleKey,
                "google_ai_interaction",
                model("gemini_model", "gemini-3.5-flash"),
                "assistant_chat, wishlist_compile, task_metadata",
                googleKey ? "ready" : "missing Google AI key",
                credentialSource
        ));
        resources.add(resource(
                "gemini_pro",
                "Gemini Pro",
                geminiEnabled && googleKey,
                "google_ai_interaction",
                model("gemini_pro_model", "gemini-3.1-pro-preview"),
                "critical_review, Jules dialogue, architecture decisions",
                googleKey ? "ready" : "missing Google AI key",
                credentialSource
        ));
        resources.add(resource(
                "structured_planning",
                "Structured Planning",
                geminiEnabled && googleKey,
                "structured_outputs",
                model("gemini_pro_model", "gemini-3.1-pro-preview"),
                "JTBD, Kano, Cynefin, DoD, dependency graph",
                googleKey ? "ready" : "missing Google AI key",
                credentialSource
        ));
        resources.add(resource(
                "google_search_grounding",
                "Google Search Grounding",
                searchEnabled && googleKey,
                "google_search",
                model("gemini_model", "gemini-3.5-flash"),
                "market, legal, technical freshness",
                searchEnabled ? googleKey ? "ready" : "missing Google AI key" : "disabled",
                credentialSource
        ));
        resources.add(resource(
                "url_context",
                "URL Context",
                urlEnabled && googleKey,
                "url_context",
                model("gemini_model", "gemini-3.5-flash"),
                "page/PDF/repository docs context",
                urlEnabled ? googleKey ? "ready" : "missing Google AI key" : "disabled",
                credentialSource
        ));
        resources.add(resource(
                "nano_banana_design",
                "Nano Banana Design",
                designEnabled && nanoEnabled && googleKey,
                "image_generation",
                model("nano_banana_model", "gemini-3.1-flash-image"),
                "site hero, banner, mockup, visual asset",
                designEnabled && nanoEnabled ? googleKey ? "ready" : "missing Google AI key" : "disabled",
                credentialSource
        ));
        resources.add(resource(
                "nano_banana_pro",
                "Nano Banana Pro",
                designEnabled && nanoEnabled && googleKey,
                "image_generation",
                model("nano_banana_pro_model", "gemini-3-pro-image"),
                "brand-sensitive visual asset",
                designEnabled && nanoEnabled ? googleKey ? "ready" : "missing Google AI key" : "disabled",
                credentialSource
        ));
        resources.add(resource(
                "veo_video",
                "Veo Video",
                veoEnabled && googleKey,
                "video_generation",
                model("veo_model", "veo-3.1-generate-preview"),
                "demo/promo video planning",
                veoEnabled ? googleKey ? "configured; execution endpoint not wired in phase 1" : "missing Google AI key" : "disabled",
                credentialSource
        ));
        resources.add(resource(
                "antigravity",
                "Antigravity Diagnostic Worker",
                antigravityEnabled && googleKey,
                "agentic_code_execution",
                model("antigravity_agent", "antigravity-preview-05-2026"),
                "deep repository diagnostics, local repair, diagnostic branch",
                antigravityEnabled ? googleKey ? "ready" : "missing Google AI key" : "disabled",
                credentialSource
        ));
        return resources;
    }

    public Map<String, Object> probeModels() {
        String apiKey = googleAiApiKey();
        if (apiKey.isBlank()) {
            return Map.of(
                    "available", false,
                    "status", "missing_key",
                    "message", "Google AI key is not configured."
            );
        }
        try {
            String separator = modelsUrl.contains("?") ? "&" : "?";
            URI uri = URI.create(modelsUrl + separator + "key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(Math.min(requestTimeoutSeconds, 60)))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Map.of(
                        "available", false,
                        "status", "api_error",
                        "message", "ListModels HTTP " + response.statusCode() + ": " + preview(redact(response.body(), apiKey), 1_500)
                );
            }
            JsonNode root = objectMapper.readTree(response.body());
            List<Map<String, Object>> models = new ArrayList<>();
            for (JsonNode model : root.path("models")) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", model.path("name").asText(""));
                item.put("displayName", model.path("displayName").asText(""));
                item.put("description", preview(model.path("description").asText(""), 240));
                List<String> methods = new ArrayList<>();
                for (JsonNode method : model.path("supportedGenerationMethods")) {
                    methods.add(method.asText(""));
                }
                item.put("supportedGenerationMethods", methods);
                models.add(item);
                if (models.size() >= 80) {
                    break;
                }
            }
            return Map.of(
                    "available", true,
                    "status", "ok",
                    "models", models
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Map.of("available", false, "status", "interrupted", "message", "Model probe interrupted.");
        } catch (Exception e) {
            return Map.of("available", false, "status", "error", "message", e.getMessage());
        }
    }

    public InteractionResult googleSearchResearch(String question, String context) {
        if (!settingsService.effectiveBoolean("google_search_grounding_enabled")) {
            return InteractionResult.unavailable("Google Search grounding is disabled.");
        }
        String input = """
                You are Eneik grounded research analyst.
                Work in English. Return concise, source-grounded operational findings.
                Do not invent facts. Mark NOT AVAILABLE when evidence is insufficient.

                Question:
                %s

                Project context:
                %s
                """.formatted(safe(question), preview(context, 8_000));
        return callInteraction(model("gemini_model", "gemini-3.5-flash"), input, List.of("google_search"));
    }

    public InteractionResult urlContextResearch(String url, String question, String context) {
        if (!settingsService.effectiveBoolean("url_context_enabled")) {
            return InteractionResult.unavailable("URL Context is disabled.");
        }
        String input = """
                You are Eneik URL evidence analyst.
                Work in English. Use the URL as evidence and return exact operational findings.
                Do not invent facts. Mark NOT AVAILABLE when evidence is insufficient.

                URL:
                %s

                Question:
                %s

                Project context:
                %s
                """.formatted(safe(url), safe(question), preview(context, 8_000));
        return callInteraction(model("gemini_model", "gemini-3.5-flash"), input, List.of("url_context"));
    }

    public InteractionResult callInteraction(String model, String input, List<String> toolTypes) {
        String apiKey = googleAiApiKey();
        if (apiKey.isBlank()) {
            return InteractionResult.unavailable("Google AI key is not configured.");
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", blank(model) ? "gemini-3.5-flash" : model.trim());
            ArrayNode inputArray = body.putArray("input");
            inputArray.addObject()
                    .put("type", "text")
                    .put("text", input == null ? "" : input);
            if (toolTypes != null && !toolTypes.isEmpty()) {
                ArrayNode tools = body.putArray("tools");
                for (String toolType : toolTypes) {
                    if (!blank(toolType)) {
                        tools.addObject().put("type", toolType.trim());
                    }
                }
            }

            HttpRequest request = HttpRequest.newBuilder(URI.create(interactionsUrl))
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String redacted = redact(response.body(), apiKey);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new InteractionResult(
                        false,
                        "api_error",
                        model,
                        "",
                        "",
                        "",
                        "Interactions API HTTP " + response.statusCode() + ": " + preview(redacted, 1_500)
                );
            }
            JsonNode root = objectMapper.readTree(response.body());
            ImageBlock image = findImage(root);
            return new InteractionResult(
                    true,
                    "ok",
                    model,
                    outputText(root, redacted),
                    image.data(),
                    image.mimeType(),
                    preview(redacted, 4_000)
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new InteractionResult(false, "interrupted", model, "", "", "", "Interaction interrupted.");
        } catch (Exception e) {
            return new InteractionResult(false, "error", model, "", "", "", e.getMessage());
        }
    }

    public boolean hasGoogleAiKey() {
        return !googleAiApiKey().isBlank();
    }

    public String googleAiApiKey() {
        return firstNonBlank(
                settingsService.effectiveValue("google_ai_api_key"),
                settingsService.effectiveValue("gemini_api_key")
        );
    }

    public String googleAiCredentialSource() {
        if (!blank(settingsService.effectiveValue("google_ai_api_key"))) {
            return "google_ai_api_key";
        }
        if (!blank(settingsService.effectiveValue("gemini_api_key"))) {
            return "gemini_api_key_fallback";
        }
        return "none";
    }

    public String model(String settingKey, String fallback) {
        String value = settingsService.effectiveValue(settingKey);
        return blank(value) ? fallback : value.trim();
    }

    private Map<String, Object> resource(String id,
                                         String name,
                                         boolean enabled,
                                         String toolType,
                                         String model,
                                         String operatorUse,
                                         String status,
                                         String credentialSource) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", id);
        resource.put("name", name);
        resource.put("enabled", enabled);
        resource.put("toolType", toolType);
        resource.put("model", model);
        resource.put("operatorUse", operatorUse);
        resource.put("status", status);
        resource.put("credentialSource", credentialSource);
        return resource;
    }

    private String outputText(JsonNode root, String fallback) {
        String outputText = firstNonBlank(
                root.path("output_text").asText(""),
                root.path("outputText").asText(""),
                root.path("text").asText("")
        );
        if (!blank(outputText)) {
            return outputText;
        }
        List<String> texts = new ArrayList<>();
        collectText(root, texts);
        return texts.isEmpty() ? fallback : String.join("\n", texts.stream().limit(20).toList());
    }

    private void collectText(JsonNode node, List<String> texts) {
        if (node == null || node.isMissingNode() || node.isNull() || texts.size() >= 40) {
            return;
        }
        if (node.isObject()) {
            JsonNode text = node.get("text");
            if (text != null && text.isTextual() && !blank(text.asText())) {
                texts.add(text.asText());
            }
            node.fields().forEachRemaining(entry -> collectText(entry.getValue(), texts));
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectText(child, texts));
        }
    }

    private ImageBlock findImage(JsonNode root) {
        ImageBlock direct = imageAt(root.path("output_image"));
        if (!direct.data().isBlank()) {
            return direct;
        }
        direct = imageAt(root.path("outputImage"));
        if (!direct.data().isBlank()) {
            return direct;
        }
        return findImageRecursive(root);
    }

    private ImageBlock findImageRecursive(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return ImageBlock.empty();
        }
        ImageBlock here = imageAt(node);
        if (!here.data().isBlank()) {
            return here;
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                ImageBlock found = findImageRecursive(fields.next().getValue());
                if (!found.data().isBlank()) {
                    return found;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                ImageBlock found = findImageRecursive(child);
                if (!found.data().isBlank()) {
                    return found;
                }
            }
        }
        return ImageBlock.empty();
    }

    private ImageBlock imageAt(JsonNode node) {
        if (node == null || !node.isObject()) {
            return ImageBlock.empty();
        }
        String data = firstNonBlank(node.path("data").asText(""), node.path("imageData").asText(""));
        String mimeType = firstNonBlank(
                node.path("mime_type").asText(""),
                node.path("mimeType").asText(""),
                node.path("mediaType").asText("")
        );
        if (data.length() > 200 && (blank(mimeType) || mimeType.toLowerCase().startsWith("image/"))) {
            return new ImageBlock(data, blank(mimeType) ? "image/png" : mimeType);
        }
        return ImageBlock.empty();
    }

    private String redact(String text, String... secrets) {
        if (text == null) {
            return "";
        }
        String redacted = text;
        for (String secret : secrets) {
            if (!blank(secret)) {
                redacted = redacted.replace(secret, "[REDACTED]");
            }
        }
        return redacted;
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private String preview(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() <= maxChars ? compact : compact.substring(0, Math.max(0, maxChars - 15)) + "... [truncated]";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!blank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record ImageBlock(String data, String mimeType) {
        static ImageBlock empty() {
            return new ImageBlock("", "");
        }
    }

    public record InteractionResult(
            boolean available,
            String status,
            String model,
            String outputText,
            String outputImageBase64,
            String outputImageMimeType,
            String rawPreview
    ) {
        static InteractionResult unavailable(String reason) {
            return new InteractionResult(false, "unavailable", "", reason, "", "", reason);
        }
    }
}
