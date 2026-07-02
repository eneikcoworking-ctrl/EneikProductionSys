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
public class JulesClient {
    private static final Logger log = LoggerFactory.getLogger(JulesClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiBaseUrl;
    private final boolean enabled;

    public JulesClient(ObjectMapper objectMapper,
                       @Value("${jules.api-key:}") String apiKey,
                       @Value("${jules.api-base-url:https://jules.googleapis.com/v1alpha}") String apiBaseUrl,
                       @Value("${jules.enabled:false}") boolean enabled) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.apiBaseUrl = apiBaseUrl;
        this.enabled = enabled;
    }

    public JulesDispatchResult createSession(String sourceName,
                                             String startingBranch,
                                             String title,
                                             String prompt) {
        if (!enabled) {
            return new JulesDispatchResult(false, null, "Jules integration disabled");
        }
        if (apiKey == null || apiKey.isBlank()) {
            return new JulesDispatchResult(false, null, "JULES_API_KEY is not configured");
        }
        if (sourceName == null || sourceName.isBlank()) {
            return new JulesDispatchResult(false, null, "Jules source is not configured");
        }

        try {
            ObjectNode githubRepoContext = objectMapper.createObjectNode();
            githubRepoContext.put("startingBranch", startingBranch == null || startingBranch.isBlank() ? "main" : startingBranch);

            ObjectNode sourceContext = objectMapper.createObjectNode();
            sourceContext.put("source", sourceName);
            sourceContext.set("githubRepoContext", githubRepoContext);

            ObjectNode body = objectMapper.createObjectNode();
            body.put("prompt", prompt);
            body.set("sourceContext", sourceContext);
            body.put("automationMode", "AUTO_CREATE_PR");
            body.put("title", title);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/sessions"))
                    .header("Content-Type", "application/json")
                    .header("X-Goog-Api-Key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Jules session creation failed: status={} body={}", response.statusCode(), response.body());
                return new JulesDispatchResult(false, null, "Jules API status " + response.statusCode());
            }

            JsonNode json = objectMapper.readTree(response.body());
            return new JulesDispatchResult(true, json.path("name").asText(null), "Dispatched to Jules");
        } catch (IOException e) {
            log.warn("Jules session creation failed", e);
            return new JulesDispatchResult(false, null, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new JulesDispatchResult(false, null, "Interrupted while dispatching to Jules");
        }
    }
}
