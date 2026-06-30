package com.eneik.production.controllers;

import com.eneik.production.models.domain.Greeting;
import com.eneik.production.services.MLPredictionServiceClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * @file GreetingController.java
 * @agent TAG-02 (Rigid Designator)
 * @description Controller for the Hello World greeting, enriched with AI risk prediction.
 */
@RestController
@RequestMapping("/api/v1/greetings")
public class GreetingController {

    private final MLPredictionServiceClient mlClient;

    public GreetingController(MLPredictionServiceClient mlClient) {
        this.mlClient = mlClient;
    }

    @GetMapping("/latest")
    public Greeting getLatest() {
        // Rigidly designated ID and Message according to Contract
        UUID fixedId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        String message = "Hello World: The Agency is Operational.";

        Greeting greeting = new Greeting(fixedId, message, "COMPLETED", 42);

        // Enrich with system risk prediction (TAG-04 Integration)
        // Simulated inputs for demonstration
        boolean risk = mlClient.checkSystemRisk(50, 1800.0);
        greeting.setHighRiskPredicted(risk);

        return greeting;
    }
}
