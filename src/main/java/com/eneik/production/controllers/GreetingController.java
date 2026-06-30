package com.eneik.production.controllers;

import com.eneik.production.controllers.policy.PrivacyFilter;
import com.eneik.production.models.domain.Greeting;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * @file GreetingController.java
 * @agent TAG-02 (Rigid Designator)
 * @description Controller for EneikProductionSys Greetings, integrated with PrivacyFilter.
 */
@RestController
@RequestMapping("/api/v1/greetings")
@CrossOrigin(origins = "http://localhost:3000")
public class GreetingController {

    @GetMapping("/latest")
    public ResponseEntity<Greeting> getLatest() {
        Greeting latest = new Greeting(
            UUID.randomUUID(),
            "Welcome to EneikProductionSys",
            "COMPLETED",
            Instant.now(),
            42
        );
        return ResponseEntity.ok(latest);
    }

    @PostMapping
    public ResponseEntity<Greeting> createGreeting(@RequestBody Map<String, String> request) {
        String rawMessage = request.get("message");

        // TAG-10: Apply Privacy Filter before anything else
        String safeMessage = PrivacyFilter.maskSensitiveData(rawMessage);

        Greeting created = new Greeting(
            UUID.randomUUID(),
            safeMessage,
            "RECEIVED",
            Instant.now(),
            0
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
