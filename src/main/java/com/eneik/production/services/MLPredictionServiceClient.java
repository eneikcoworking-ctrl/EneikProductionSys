package com.eneik.production.services;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.eneik.production.services.settings.SystemSettingsService;

@Service
public class MLPredictionServiceClient {
    private static final Logger LOGGER = Logger.getLogger(MLPredictionServiceClient.class.getName());

    private final RestTemplate restTemplate;
    private final String mlServiceUrl;
    private final SystemSettingsService settingsService;

    public MLPredictionServiceClient(RestTemplateBuilder restTemplateBuilder,
                                     @Value("${ml.service.url}") String mlServiceUrl,
                                     SystemSettingsService settingsService) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
        this.mlServiceUrl = mlServiceUrl;
        this.settingsService = settingsService;
    }

    private String getGeminiApiKey() {
        if (settingsService != null) {
            try {
                return settingsService.effectiveValue("gemini_api_key");
            } catch (Exception e) {
                // Ignore
            }
        }
        return "";
    }

    public boolean checkSystemRisk(int activeTasks, double currentCycleTime) {
        String endpoint = mlServiceUrl + "/api/v1/predict/bottleneck";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> request = new HashMap<>();
            request.put("wip_count", activeTasks);
            request.put("avg_cycle_time", currentCycleTime);

            MLResponse response = restTemplate.postForObject(endpoint, new HttpEntity<>(request, headers), MLResponse.class);
            return response != null && response.isBottleneckPredicted();
        } catch (Exception e) {
            LOGGER.warning("ML service call failed: " + e.getMessage());
            return false;
        }
    }

    public Map<String, Object> predictBottleneck(int wipCount, double avgCycleTime) {
        boolean bottleneckPredicted = checkSystemRisk(wipCount, avgCycleTime);
        return Map.of("is_bottleneck_predicted", bottleneckPredicted);
    }

    public Map<String, Object> reviewPr(java.util.UUID projectId, java.util.UUID taskId, String prUrl) {
        String endpoint = mlServiceUrl + "/api/v1/review/pr";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> request = new HashMap<>();
            request.put("projectId", projectId.toString());
            request.put("taskId", taskId.toString());
            request.put("prUrl", prUrl);
            request.put("apiKey", getGeminiApiKey());
            request.put("githubToken", settingsService.effectiveValue("github_token"));

            return restTemplate.postForObject(endpoint, new HttpEntity<>(request, headers), Map.class);
        } catch (Exception e) {
            LOGGER.severe("ML service PR review call failed (Fail-Safe Triggered): " + e.getMessage());
            return Map.of(
                "approved", false,
                "remarks", "VERIFICATION_SERVICE_UNAVAILABLE: ML review pipeline connection failed. PR blocked until manual/AI recovery.",
                "newTasks", java.util.Collections.emptyList()
            );
        }
    }

    public Map<String, Object> checkRefusalCriteria(String prDiff, String refusalCriteria) {
        String endpoint = mlServiceUrl + "/api/v1/review/refusal-criteria";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> request = new HashMap<>();
            request.put("prDiff", prDiff);
            request.put("refusalCriteria", refusalCriteria);
            request.put("apiKey", getGeminiApiKey());

            return restTemplate.postForObject(endpoint, new HttpEntity<>(request, headers), Map.class);
        } catch (Exception e) {
            LOGGER.severe("ML service checkRefusalCriteria call failed (Fail-Safe Triggered): " + e.getMessage());
            return Map.of(
                "compliant", false,
                "reason", "VERIFICATION_SERVICE_UNAVAILABLE: ML verification pipeline offline. Blocked compliance audit."
            );
        }
    }

    public String chat(String prompt, String systemInstruction) {
        String endpoint = mlServiceUrl + "/api/v1/assistant/chat";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> request = new HashMap<>();
            request.put("prompt", prompt);
            request.put("systemInstruction", systemInstruction);
            request.put("apiKey", getGeminiApiKey());

            Map<String, Object> response = restTemplate.postForObject(endpoint, new HttpEntity<>(request, headers), Map.class);
            if (response != null && response.containsKey("text")) {
                return (String) response.get("text");
            }
            return "Ошибка: Неверный формат ответа от ИИ-ассистента.";
        } catch (Exception e) {
            LOGGER.severe("ML service chat call failed: " + e.getMessage());
            return "Ассистент временно недоступен. Ошибка подключения к ML-сервису: " + e.getMessage();
        }
    }


    public java.util.Map<String, Object> generateTaskMetadata(String wishlistContent) {
        String endpoint = mlServiceUrl + "/api/v1/predict/metadata";
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        java.util.Map<String, Object> request = new java.util.HashMap<>();
        request.put("content", wishlistContent);
        request.put("apiKey", getGeminiApiKey());

        try {
            return restTemplate.postForObject(endpoint, new org.springframework.http.HttpEntity<>(request, headers), java.util.Map.class);
        } catch (Exception e) {
            LOGGER.warning("ML service call to generateTaskMetadata failed: " + e.getMessage() + ". Using fallback.");
            java.util.Map<String, Object> fallback = new java.util.HashMap<>();
            fallback.put("jtbd", fallbackJtbd(wishlistContent));
            fallback.put("acceptanceCriteria", fallbackAcceptanceCriteria(wishlistContent));
            return fallback;
        }
    }

    private String fallbackJtbd(String wishlistContent) {
        String clean = compact(wishlistContent, 240);
        return "When I use the requested product capability, I want the system to deliver this client need: "
                + clean
                + ", so that the result can be verified end-to-end without relying on subjective interpretation.";
    }

    private String fallbackAcceptanceCriteria(String wishlistContent) {
        String clean = compact(wishlistContent, 180);
        return "Given the implemented feature for \"" + clean + "\", When the primary user follows the intended happy path, Then the user can complete the core workflow without client-side or server-side errors.\n"
                + "Given required input is missing or invalid, When the user submits the form or API request, Then the system returns a clear validation error and does not persist invalid data.\n"
                + "Given an authorized administrator uses the relevant management surface, When content or settings for this feature are changed, Then the public/user-facing experience reflects the saved change.\n"
                + "Given an unauthorized or unauthenticated user attempts a protected action, When the request is made, Then access is denied without exposing private data.\n"
                + "Given the feature is complete, When automated verification runs, Then the relevant unit, integration, and critical E2E checks pass and generated test artifacts are not committed.";
    }

    private String compact(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "the requested feature";
        }
        String compacted = value.replaceAll("\\s+", " ").trim();
        if (compacted.length() <= maxLength) {
            return compacted;
        }
        return compacted.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static class MLResponse {
        @JsonProperty("risk_score")
        private double riskScore;

        @JsonProperty("is_bottleneck_predicted")
        private boolean bottleneckPredicted;

        public double getRiskScore() {
            return riskScore;
        }

        public void setRiskScore(double riskScore) {
            this.riskScore = riskScore;
        }

        public boolean isBottleneckPredicted() {
            return bottleneckPredicted;
        }

        public void setBottleneckPredicted(boolean bottleneckPredicted) {
            this.bottleneckPredicted = bottleneckPredicted;
        }
    }
}
