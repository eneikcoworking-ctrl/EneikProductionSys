package com.eneik.production.services.jules;

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
    private final String apiKey;
    private final String apiBaseUrl;
    private final boolean enabled;

    public JulesApiClient(ObjectMapper objectMapper,
                          @Value("${JULES_API_KEY:}") String apiKey,
                          @Value("${jules.api-base-url:https://jules.googleapis.com/v1alpha}") String apiBaseUrl,
                          @Value("${JULES_ENABLED:false}") String enabledStr) {
        this.httpClient = HttpClient.newBuilder().build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.apiBaseUrl = apiBaseUrl;
        this.enabled = "true".equalsIgnoreCase(enabledStr);
    }

    public String createSession(String repoUrl, String taskDescription, String roleContext) {
        if (!enabled) {
            log.info("Jules integration disabled (JULES_ENABLED != true). Returning 'skipped'.");
            return "skipped";
        }

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("JULES_API_KEY is not configured. Returning 'skipped'.");
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
        if (!enabled || "skipped".equals(externalSessionId) || externalSessionId == null) {
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

    public boolean isEnabled() {
        return enabled;
    }
}
