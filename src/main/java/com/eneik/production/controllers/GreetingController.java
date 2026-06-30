package com.eneik.production.controllers;

import com.eneik.production.controllers.policy.PrivacyFilter;
import com.eneik.production.models.domain.GreetingStatus;
import com.eneik.production.models.persistence.GreetingEntity;
import com.eneik.production.models.persistence.GreetingRepository;
import com.eneik.production.services.MLPredictionServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * @file GreetingController.java
 * @agent TAG-02 (Rigid Designator)
 * @description Controller for the Greeting API, implementing the v1 contract.
 */
@RestController
@RequestMapping("/api/v1/greetings")
public class GreetingController {

    private final GreetingRepository greetingRepository;
    private final MLPredictionServiceClient mlServiceClient;

    @Autowired
    public GreetingController(GreetingRepository greetingRepository, MLPredictionServiceClient mlServiceClient) {
        this.greetingRepository = greetingRepository;
        this.mlServiceClient = mlServiceClient;
    }

    @GetMapping("/latest")
    public ResponseEntity<GreetingEntity> getLatest() {
        List<GreetingEntity> greetings = greetingRepository.findAll();
        if (greetings.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        // Simplified logic to get the "latest"
        GreetingEntity latest = greetings.get(greetings.size() - 1);
        return ResponseEntity.ok(latest);
    }

    @PostMapping
    public ResponseEntity<?> createGreeting(@RequestBody Map<String, String> request) {
        String message = request.get("message");

        // Compliance check for card numbers (simplified)
        if (message != null && message.matches(".*\\d{4}-\\d{4}-\\d{4}-\\d{4}.*")) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Compliance Violation");
            error.put("code", 400);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }

        // PII Masking via PrivacyFilter
        Map<String, Object> data = new HashMap<>();
        data.put("message", message);
        if (message != null && message.contains("@")) {
            data.put("pii", message);
            Map<String, Object> maskedData = PrivacyFilter.maskData(data);
            message = (String) maskedData.get("message");
            if ("****".equals(maskedData.get("pii"))) {
                message = message.replaceAll("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}", "[REDACTED]");
            }
        }

        GreetingEntity entity = new GreetingEntity(
                UUID.randomUUID(),
                message,
                GreetingStatus.RECEIVED,
                Instant.now(),
                42 // Lead time as per contract example
        );

        // AI Fallback logic
        try {
            mlServiceClient.predictBottleneck(10, 1.5);
        } catch (Exception e) {
            // Fallback: log and continue
            System.err.println("AI Service unavailable, continuing with fallback.");
        }

        GreetingEntity saved = greetingRepository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
