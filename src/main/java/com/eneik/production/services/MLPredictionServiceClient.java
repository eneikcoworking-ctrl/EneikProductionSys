package com.eneik.production.services;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.eneik.production.models.persistence.LeanValue;
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
    private final com.eneik.production.services.monitor.AiHealthTracker aiHealthTracker;

    public MLPredictionServiceClient(RestTemplateBuilder restTemplateBuilder,
                                     @Value("${ml.service.url}") String mlServiceUrl,
                                     SystemSettingsService settingsService,
                                     com.eneik.production.services.monitor.AiHealthTracker aiHealthTracker) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
        this.mlServiceUrl = mlServiceUrl;
        this.settingsService = settingsService;
        this.aiHealthTracker = aiHealthTracker;
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

    private String modelOverrideForTier(String modelTier) {
        if (settingsService == null) {
            return "";
        }
        try {
            boolean pro = modelTier != null && "pro".equalsIgnoreCase(modelTier.trim());
            String primary = settingsService.effectiveValue(pro ? "gemini_pro_model" : "gemini_model");
            String fallback = settingsService.effectiveValue(pro ? "gemini_pro_fallback_models" : "gemini_fallback_models");
            StringBuilder candidates = new StringBuilder();
            if (primary != null && !primary.isBlank()) {
                candidates.append(primary.trim());
            }
            if (fallback != null && !fallback.isBlank()) {
                if (!candidates.isEmpty()) {
                    candidates.append(',');
                }
                candidates.append(fallback.trim());
            }
            return candidates.toString();
        } catch (Exception e) {
            return "";
        }
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
            request.put("modelTier", "pro");
            request.put("modelOverride", modelOverrideForTier("pro"));

            Map<String, Object> result = restTemplate.postForObject(endpoint, new HttpEntity<>(request, headers), Map.class);
            aiHealthTracker.recordSuccess("reviewPr");
            return result;
        } catch (Exception e) {
            LOGGER.severe("ML service PR review call failed (Fail-Safe Triggered): " + e.getMessage());
            aiHealthTracker.recordFailure("reviewPr", e.getMessage());
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
            request.put("modelOverride", modelOverrideForTier(""));

            Map<String, Object> result = restTemplate.postForObject(endpoint, new HttpEntity<>(request, headers), Map.class);
            aiHealthTracker.recordSuccess("checkRefusalCriteria");
            return result;
        } catch (Exception e) {
            LOGGER.severe("ML service checkRefusalCriteria call failed (Fail-Safe Triggered): " + e.getMessage());
            aiHealthTracker.recordFailure("checkRefusalCriteria", e.getMessage());
            return Map.of(
                "compliant", false,
                "reason", "VERIFICATION_SERVICE_UNAVAILABLE: ML verification pipeline offline. Blocked compliance audit."
            );
        }
    }

    @SuppressWarnings("unchecked")
    public java.util.List<Map<String, Object>> checkMethodologicalFalsification(String prDiff, String charterRules) {
        String endpoint = mlServiceUrl + "/api/v1/review/methodological-falsification";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> request = new HashMap<>();
            request.put("prDiff", prDiff);
            request.put("charterRules", charterRules);
            request.put("apiKey", getGeminiApiKey());
            request.put("modelOverride", modelOverrideForTier(""));

            Map<String, Object> response = restTemplate.postForObject(endpoint, new HttpEntity<>(request, headers), Map.class);
            aiHealthTracker.recordSuccess("checkMethodologicalFalsification");
            if (response != null && response.containsKey("results")) {
                return (java.util.List<Map<String, Object>>) response.get("results");
            }
        } catch (Exception e) {
            LOGGER.severe("ML service checkMethodologicalFalsification call failed: " + e.getMessage());
            aiHealthTracker.recordFailure("checkMethodologicalFalsification", e.getMessage());
        }
        return java.util.Collections.emptyList();
    }

    public String chat(String prompt, String systemInstruction) {
        return chatWithTier(prompt, systemInstruction, "");
    }

    public String chatCritical(String prompt, String systemInstruction) {
        return chatWithTier(prompt, systemInstruction, "pro");
    }

    private String chatWithTier(String prompt, String systemInstruction, String modelTier) {
        String endpoint = mlServiceUrl + "/api/v1/assistant/chat";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> request = new HashMap<>();
            request.put("prompt", prompt);
            request.put("systemInstruction", systemInstruction);
            request.put("apiKey", getGeminiApiKey());
            if (modelTier != null && !modelTier.isBlank()) {
                request.put("modelTier", modelTier);
            }
            request.put("modelOverride", modelOverrideForTier(modelTier));

            Map<String, Object> response = restTemplate.postForObject(endpoint, new HttpEntity<>(request, headers), Map.class);
            if (response != null && response.containsKey("text")) {
                aiHealthTracker.recordSuccess("chat");
                return (String) response.get("text");
            }
            aiHealthTracker.recordFailure("chat", "invalid response format");
            return "ERROR: Invalid AI assistant response format.";
        } catch (Exception e) {
            LOGGER.severe("ML service chat call failed: " + e.getMessage());
            aiHealthTracker.recordFailure("chat", e.getMessage());
            return "The assistant is temporarily unavailable. ML service connection error: " + e.getMessage();
        }
    }


    public record TaskSliceMetadata(
            String title,
            String jtbd,
            String acceptanceCriteria,
            String roleTag,
            LeanValue leanValue,
            String kanoClass,
            String cynefinDomain,
            String tocConstraintRef,
            String sixSigmaMetric,
            boolean hasUi
    ) {}

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
