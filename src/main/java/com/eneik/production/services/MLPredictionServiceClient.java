package com.eneik.production.services;

import org.springframework.stereotype.Service;
import java.util.Map;

/**
 * @file MLPredictionServiceClient.java
 * @agent TAG-04 (Modal Quantifier)
 * @description Client for the AI Prediction Service.
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

/**
 * @file MLPredictionServiceClient.java
 * @agent TAG-04 (ML-Ops)
 * @description Client for the FastAPI AI Prediction Service.
 */
@Service
public class MLPredictionServiceClient {

    public Map<String, Object> predictBottleneck(int wipCount, double avgCycleTime) {
        // In a real implementation, this would call http://localhost:8000/api/v1/predict/bottleneck
        // For now, it's a skeleton for testing fallback.
        return Map.of(
            "risk_score", 0.15,
            "is_bottleneck_predicted", false
        );
    private static final Logger LOGGER = Logger.getLogger(MLPredictionServiceClient.class.getName());

    private final RestTemplate restTemplate;
    private final String mlServiceUrl;

    public MLPredictionServiceClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${ml.service.url}") String mlServiceUrl) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(500))
                .setReadTimeout(Duration.ofMillis(500))
                .build();
        this.mlServiceUrl = mlServiceUrl;
    }

    /**
     * Calls the ML service to check for system risk.
     * @param activeTasks wip_count
     * @param currentCycleTime avg_cycle_time
     * @return true if risk is predicted, false otherwise or on error.
     */
    public boolean checkSystemRisk(int activeTasks, double currentCycleTime) {
        String endpoint = mlServiceUrl + "/api/v1/predict/bottleneck";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> request = new HashMap<>();
            request.put("wip_count", activeTasks);
            request.put("avg_cycle_time", currentCycleTime);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            MLResponse response = restTemplate.postForObject(endpoint, entity, MLResponse.class);

            return response != null && response.isBottleneckPredicted();
        } catch (Exception e) {
            LOGGER.warning("ML Service Call Failed: " + e.getMessage() + ". Falling back to false.");
            return false;
        }
    }

    private static class MLResponse {
        @JsonProperty("risk_score")
        private double riskScore;

        @JsonProperty("is_bottleneck_predicted")
        private boolean isBottleneckPredicted;

        public double getRiskScore() {
            return riskScore;
        }

        public void setRiskScore(double riskScore) {
            this.riskScore = riskScore;
        }

        public boolean isBottleneckPredicted() {
            return isBottleneckPredicted;
        }

        public void setBottleneckPredicted(boolean bottleneckPredicted) {
            isBottleneckPredicted = bottleneckPredicted;
        }
    }
}
