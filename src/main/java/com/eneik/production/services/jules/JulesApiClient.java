package com.eneik.production.services.jules;

import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Component
public class JulesApiClient {
    private static final Logger log = LoggerFactory.getLogger(JulesApiClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiBaseUrl;
    private final SystemSettingsService settingsService;

    public JulesApiClient(ObjectMapper objectMapper,
                          @Value("${jules.api-base-url:https://jules.googleapis.com/v1alpha}") String apiBaseUrl,
                          SystemSettingsService settingsService) {
        this.httpClient = HttpClient.newBuilder().build();
        this.objectMapper = objectMapper;
        this.apiBaseUrl = apiBaseUrl;
        this.settingsService = settingsService;
    }

    public String createSession(String repoUrl, String taskDescription, String roleContext) {
        String apiKey = settingsService.effectiveValue("jules_api_key");
        return createSession(repoUrl, taskDescription, roleContext, apiKey);
    }

    public String createSession(String repoUrl, String taskDescription, String roleContext, String apiKey) {
        if (!settingsService.effectiveBoolean("jules_enabled")) {
            log.info("Jules integration disabled (JULES_ENABLED != true). Returning 'skipped'.");
            return "skipped";
        }

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Jules API key is not configured. Returning 'skipped'.");
            return "skipped";
        }

        try {
            ObjectNode githubRepoContext = objectMapper.createObjectNode();
            githubRepoContext.put("startingBranch", "main");

            ObjectNode sourceContext = objectMapper.createObjectNode();
            sourceContext.put("source", repoUrl);
            sourceContext.set("githubRepoContext", githubRepoContext);

            ObjectNode body = objectMapper.createObjectNode();
            body.put("prompt", taskDescription + "\n\nContext:\n" + roleContext);
            body.set("sourceContext", sourceContext);
            body.put("automationMode", "AUTO_CREATE_PR");
            body.put("title", "Jules Task Execution");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/sessions"))
                    .header("Content-Type", "application/json")
                    .header("X-Goog-Api-Key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Jules session creation failed: status={} body={}", response.statusCode(), response.body());
                return null;
            }

            JsonNode json = objectMapper.readTree(response.body());
            return json.path("name").asText(null);
        } catch (IOException | InterruptedException e) {
            log.error("Error creating Jules session", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    public String getSessionStatus(String externalSessionId) {
        String apiKey = settingsService.effectiveValue("jules_api_key");
        return getSessionStatus(externalSessionId, apiKey);
    }

    public String getSessionStatus(String externalSessionId, String apiKey) {
        if (!settingsService.effectiveBoolean("jules_enabled") || apiKey == null || apiKey.isBlank() || "skipped".equals(externalSessionId) || externalSessionId == null) {
            return null;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/" + externalSessionId))
                    .header("X-Goog-Api-Key", apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Jules status check failed: status={} body={}", response.statusCode(), response.body());
                return null;
            }

            JsonNode json = objectMapper.readTree(response.body());
            return json.path("state").asText(null);
        } catch (IOException | InterruptedException e) {
            log.error("Error getting Jules session status", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    public String getSessionPrUrl(String externalSessionId) {
        String apiKey = settingsService.effectiveValue("jules_api_key");
        return getSessionPrUrl(externalSessionId, apiKey);
    }

    public String getSessionPrUrl(String externalSessionId, String apiKey) {
        if (!settingsService.effectiveBoolean("jules_enabled") || apiKey == null || apiKey.isBlank() || "skipped".equals(externalSessionId) || externalSessionId == null) {
            return null;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/" + externalSessionId))
                    .header("X-Goog-Api-Key", apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }

            JsonNode json = objectMapper.readTree(response.body());
            return findPrUrl(json);
        } catch (Exception e) {
            return null;
        }
    }

    private String findPrUrl(JsonNode node) {
        if (node == null) return null;
        if (node.isTextual()) {
            String text = node.asText();
            if (text.contains("github.com") && text.contains("/pull/")) {
                return text;
            }
        }
        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                String found = findPrUrl(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    public boolean isEnabled() {
        return settingsService.effectiveBoolean("jules_enabled");
    }
}
