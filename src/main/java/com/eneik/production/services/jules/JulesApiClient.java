package com.eneik.production.services.jules;

import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.task.TaskTitleBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Component
public class JulesApiClient {
    private static final Logger log = LoggerFactory.getLogger(JulesApiClient.class);
    private static final int MAX_ACTIVITIES_RESPONSE_BYTES = 2 * 1024 * 1024;

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
        return createSessionDetailed(repoUrl, taskDescription, roleContext, apiKey).sessionName();
    }

    public CreateSessionResult createSessionDetailed(String repoUrl, String taskDescription, String roleContext, String apiKey) {
        return createSessionDetailed(repoUrl, taskDescription, roleContext, apiKey, TaskTitleBuilder.build("", taskDescription));
    }

    public CreateSessionResult createSessionDetailed(String repoUrl, String taskDescription, String roleContext, String apiKey, String title) {
        if (!settingsService.effectiveBoolean("jules_enabled")) {
            log.info("Jules integration disabled (JULES_ENABLED != true). Returning 'skipped'.");
            return new CreateSessionResult("skipped", 0, "jules_disabled");
        }

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Jules API key is not configured. Returning 'skipped'.");
            return new CreateSessionResult("skipped", 0, "missing_api_key");
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
            body.put("title", TaskTitleBuilder.enforceTwoOrThreeWords(title));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/sessions"))
                    .header("Content-Type", "application/json")
                    .header("X-Goog-Api-Key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Jules session creation failed: status={} body={}", response.statusCode(), response.body());
                return new CreateSessionResult(null, response.statusCode(), response.body());
            }

            JsonNode json = objectMapper.readTree(response.body());
            return new CreateSessionResult(json.path("name").asText(null), response.statusCode(), "");
        } catch (IOException | InterruptedException e) {
            log.error("Error creating Jules session", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new CreateSessionResult(null, 0, e.getMessage());
        }
    }

    public record CreateSessionResult(String sessionName, int statusCode, String errorBody) {
        public boolean dailyLimitOrQuota() {
            String lower = errorBody == null ? "" : errorBody.toLowerCase(java.util.Locale.ROOT);
            return statusCode == 429
                    || lower.contains("quota")
                    || lower.contains("daily")
                    || lower.contains("rate limit")
                    || lower.contains("resource_exhausted");
        }

        public boolean apiPreconditionOrAuthorizationBlocked() {
            String lower = errorBody == null ? "" : errorBody.toLowerCase(java.util.Locale.ROOT);
            return statusCode == 400
                    || statusCode == 401
                    || statusCode == 403
                    || lower.contains("failed_precondition")
                    || lower.contains("permission_denied")
                    || lower.contains("unauthorized")
                    || lower.contains("forbidden")
                    || lower.contains("access denied")
                    || lower.contains("precondition");
        }

        public String compactError() {
            if (errorBody == null || errorBody.isBlank()) {
                return "";
            }
            return errorBody.length() <= 500 ? errorBody : errorBody.substring(0, 500);
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

    public JsonNode getSessionActivities(String externalSessionId, String apiKey) {
        if (!settingsService.effectiveBoolean("jules_enabled") || apiKey == null || apiKey.isBlank() || "skipped".equals(externalSessionId) || externalSessionId == null) {
            return null;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/" + normalizeSessionPath(externalSessionId) + "/activities"))
                    .header("X-Goog-Api-Key", apiKey)
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Jules activities fetch failed: status={}", response.statusCode());
                return null;
            }

            byte[] body = readLimited(response.body(), MAX_ACTIVITIES_RESPONSE_BYTES);
            if (body == null) {
                log.warn("Jules activities response for session {} exceeded {} bytes; skipping activity scan to protect backend memory", externalSessionId, MAX_ACTIVITIES_RESPONSE_BYTES);
                ObjectNode overflow = objectMapper.createObjectNode();
                overflow.put("activitiesOverflow", true);
                overflow.put("maxBytes", MAX_ACTIVITIES_RESPONSE_BYTES);
                return overflow;
            }
            return objectMapper.readTree(body);
        } catch (IOException | InterruptedException e) {
            log.error("Error getting Jules session activities", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private byte[] readLimited(InputStream inputStream, int maxBytes) throws IOException {
        try (InputStream in = inputStream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    return null;
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
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

    public boolean sendMessage(String externalSessionId, String message) {
        String apiKey = settingsService.effectiveValue("jules_api_key");
        return sendMessage(externalSessionId, message, apiKey);
    }

    public boolean sendMessage(String externalSessionId, String message, String apiKey) {
        if (!settingsService.effectiveBoolean("jules_enabled") || apiKey == null || apiKey.isBlank() || "skipped".equals(externalSessionId) || externalSessionId == null) {
            return false;
        }

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("prompt", message);

            // Constructing URL for sessions/{session}:sendMessage
            // Note: externalSessionId usually starts with "sessions/"
            String url = apiBaseUrl + "/" + externalSessionId + ":sendMessage";
            if (!externalSessionId.startsWith("sessions/")) {
                url = apiBaseUrl + "/sessions/" + externalSessionId + ":sendMessage";
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("X-Goog-Api-Key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Jules send message failed: status={} body={}", response.statusCode(), response.body());
                return false;
            }

            return true;
        } catch (IOException | InterruptedException e) {
            log.error("Error sending message to Jules session", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
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

    private String normalizeSessionPath(String externalSessionId) {
        return externalSessionId.startsWith("sessions/")
                ? externalSessionId
                : "sessions/" + externalSessionId;
    }

    public boolean isEnabled() {
        return settingsService.effectiveBoolean("jules_enabled");
    }
}
