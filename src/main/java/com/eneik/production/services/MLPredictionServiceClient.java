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

@Service
public class MLPredictionServiceClient {
    private static final Logger LOGGER = Logger.getLogger(MLPredictionServiceClient.class.getName());

    private final RestTemplate restTemplate;
    private final String mlServiceUrl;

    public MLPredictionServiceClient(RestTemplateBuilder restTemplateBuilder,
                                     @Value("${ml.service.url}") String mlServiceUrl) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(5000))
                .setReadTimeout(Duration.ofMillis(5000))
                .build();
        this.mlServiceUrl = mlServiceUrl;
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

            return restTemplate.postForObject(endpoint, new HttpEntity<>(request, headers), Map.class);
        } catch (Exception e) {
            LOGGER.severe("ML service PR review call failed: " + e.getMessage());
            return Map.of(
                "approved", false,
                "remarks", "VERIFICATION_SERVICE_UNAVAILABLE",
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

            return restTemplate.postForObject(endpoint, new HttpEntity<>(request, headers), Map.class);
        } catch (Exception e) {
            LOGGER.severe("ML service checkRefusalCriteria call failed: " + e.getMessage());
            return Map.of(
                "compliant", false,
                "reason", "VERIFICATION_SERVICE_UNAVAILABLE"
            );
        }
    }


    public java.util.Map<String, Object> generateTaskMetadata(String wishlistContent) {
        String endpoint = mlServiceUrl + "/api/v1/predict/metadata";
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        java.util.Map<String, Object> request = new java.util.HashMap<>();
        request.put("content", wishlistContent);

        return restTemplate.postForObject(endpoint, new org.springframework.http.HttpEntity<>(request, headers), java.util.Map.class);
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
