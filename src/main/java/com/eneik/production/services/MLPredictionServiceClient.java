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
            return "ERROR: Invalid AI assistant response format.";
        } catch (Exception e) {
            LOGGER.severe("ML service chat call failed: " + e.getMessage());
            return "The assistant is temporarily unavailable. ML service connection error: " + e.getMessage();
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

    public java.util.List<TaskSliceMetadata> generateTaskSlices(String wishlistContent) {
        String endpoint = mlServiceUrl + "/api/v1/predict/task-slices";
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        java.util.Map<String, Object> request = new java.util.HashMap<>();
        request.put("content", wishlistContent);
        request.put("apiKey", getGeminiApiKey());

        try {
            java.util.Map<String, Object> response = restTemplate.postForObject(
                    endpoint,
                    new org.springframework.http.HttpEntity<>(request, headers),
                    java.util.Map.class
            );
            java.util.List<TaskSliceMetadata> slices = normalizeTaskSlices(response, wishlistContent);
            if (!slices.isEmpty()) {
                return slices;
            }
        } catch (Exception e) {
            LOGGER.warning("ML service call to generateTaskSlices failed: " + e.getMessage() + ". Using safe fallback slice.");
        }
        return java.util.List.of(fallbackSlice(wishlistContent));
    }

    private java.util.List<TaskSliceMetadata> normalizeTaskSlices(java.util.Map<String, Object> response, String wishlistContent) {
        if (response == null) {
            return java.util.Collections.emptyList();
        }
        Object rawSlices = response.get("slices");
        java.util.List<TaskSliceMetadata> result = new java.util.ArrayList<>();
        if (rawSlices instanceof java.util.List<?> list) {
            int index = 1;
            for (Object item : list) {
                if (item instanceof java.util.Map<?, ?> map) {
                    String title = mapValue(map, "title", "Slice " + index);
                    String jtbd = mapValue(map, "jtbd", fallbackJtbd(wishlistContent));
                    String acceptance = mapValue(map, "acceptanceCriteria", fallbackAcceptanceCriteria(wishlistContent));
                    String kanoClass = mapValue(map, "kanoClass", "Must-Be");
                    String cynefinDomain = mapValue(map, "cynefinDomain", "clear");
                    String toc = mapValue(map, "tocConstraintRef", "TOC-CONSTRAINT-DECOMPOSITION");
                    String metric = mapValue(map, "sixSigmaMetric", "Escaped defects <= 5%");
                    boolean hasUi = booleanMapValue(map, "hasUi", false)
                            || looksLikeUi(title + " " + jtbd + " " + acceptance);
                    result.add(new TaskSliceMetadata(
                            englishSafeSource(title, 90),
                            englishSafeMetadata(jtbd, fallbackJtbd(wishlistContent), 420),
                            englishSafeMetadata(acceptance, fallbackAcceptanceCriteria(wishlistContent), 1_000),
                            leanValue(mapValue(map, "leanValue", "essential")),
                            englishSafeSource(kanoClass, 40),
                            englishSafeSource(cynefinDomain, 40),
                            englishSafeSource(toc, 120),
                            englishSafeSource(metric, 120),
                            hasUi
                    ));
                    index++;
                }
            }
        }
        if (result.isEmpty() && response.containsKey("jtbd")) {
            result.add(new TaskSliceMetadata(
                    featureLabel(wishlistContent),
                    englishSafeMetadata(String.valueOf(response.get("jtbd")), fallbackJtbd(wishlistContent), 420),
                    englishSafeMetadata(String.valueOf(response.getOrDefault("acceptanceCriteria", "")), fallbackAcceptanceCriteria(wishlistContent), 1_000),
                    LeanValue.essential,
                    "Must-Be",
                    "clear",
                    "TOC-CONSTRAINT-DECOMPOSITION",
                    "Escaped defects <= 5%",
                    looksLikeUi(wishlistContent)
            ));
        }
        return result;
    }

    private String mapValue(java.util.Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private boolean booleanMapValue(java.util.Map<?, ?> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value != null) {
            return Boolean.parseBoolean(String.valueOf(value));
        }
        return fallback;
    }

    private LeanValue leanValue(String value) {
        if (value == null) {
            return LeanValue.essential;
        }
        try {
            return LeanValue.valueOf(value.trim().toLowerCase(java.util.Locale.ROOT));
        } catch (Exception ignored) {
            return LeanValue.essential;
        }
    }

    private TaskSliceMetadata fallbackSlice(String wishlistContent) {
        return new TaskSliceMetadata(
                featureLabel(wishlistContent),
                fallbackJtbd(wishlistContent),
                fallbackAcceptanceCriteria(wishlistContent),
                LeanValue.essential,
                "Must-Be",
                looksLikeUncertain(wishlistContent) ? "complex" : "clear",
                "TOC-CONSTRAINT-DECOMPOSITION",
                "Escaped defects <= 5%",
                looksLikeUi(wishlistContent)
        );
    }

    private String fallbackJtbd(String wishlistContent) {
        String label = featureLabel(wishlistContent);
        return "When I use the " + label + " slice, I want one small verifiable capability completed, so project progress can be validated without a long Jules session.";
    }

    private String fallbackAcceptanceCriteria(String wishlistContent) {
        String label = featureLabel(wishlistContent);
        return "Given the " + label + " slice is implemented, When the primary happy path is exercised, Then it completes without client-side or server-side errors.\n"
                + "Given invalid or missing input is submitted, When validation runs, Then the system rejects the request without persisting invalid data.\n"
                + "Given the PR is ready, When verification runs, Then the relevant unit, integration, or E2E command passes and no generated artifacts are committed.";
    }

    private String englishSafeMetadata(String value, String fallback, int maxLength) {
        String safe = englishSafeSource(value, maxLength);
        if ("the client-provided wishlist translated and normalized into English task metadata".equals(safe)
                || "the requested feature".equals(safe)) {
            return fallback;
        }
        return safe;
    }

    private String englishSafeSource(String value, int maxLength) {
        String compacted = compact(value, maxLength);
        if (containsNonEnglishSignal(compacted)) {
            return "the client-provided wishlist translated and normalized into English task metadata";
        }
        return compacted;
    }

    private String featureLabel(String wishlistContent) {
        String compacted = compact(wishlistContent, 160);
        if (containsNonEnglishSignal(compacted)) {
            return "client-requested capability";
        }
        String lower = compacted.toLowerCase(java.util.Locale.ROOT);
        java.util.List<String> words = new java.util.ArrayList<>();
        java.util.Set<String> stopWords = java.util.Set.of(
                "the", "and", "for", "with", "that", "this", "from", "into", "need", "want",
                "make", "create", "build", "add", "implement", "please", "system", "feature"
        );
        for (String word : lower.split("[^a-z0-9]+")) {
            if (word.length() >= 3 && !stopWords.contains(word)) {
                words.add(word);
            }
            if (words.size() == 4) {
                break;
            }
        }
        if (words.isEmpty()) {
            return "client-requested capability";
        }
        return String.join(" ", words);
    }

    private boolean looksLikeUi(String value) {
        String lower = value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("ui") || lower.contains("ux") || lower.contains("frontend")
                || lower.contains("screen") || lower.contains("page") || lower.contains("form")
                || lower.contains("button") || lower.contains("browser") || lower.contains("svelte")
                || lower.contains("design") || lower.contains("admin") || lower.contains("panel")
                || lower.contains("portal") || lower.contains("dashboard") || lower.contains("public");
    }

    private boolean looksLikeUncertain(String value) {
        String lower = value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("research") || lower.contains("unknown") || lower.contains("spike")
                || lower.contains("explore") || lower.contains("unclear");
    }

    private boolean containsNonEnglishSignal(String value) {
        if (value == null) {
            return false;
        }
        return value.matches(".*[\\p{IsCyrillic}].*")
                || value.contains("\u00d0")
                || value.contains("\u00d1");
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

    public record TaskSliceMetadata(
            String title,
            String jtbd,
            String acceptanceCriteria,
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
