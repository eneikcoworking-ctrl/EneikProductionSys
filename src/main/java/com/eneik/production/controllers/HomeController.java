package com.eneik.production.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HomeController {
    @GetMapping("/")
    public Map<String, Object> index() {
        return Map.of(
                "service", "EneikProductionSys backend",
                "status", "running",
                "frontend", "http://localhost:3000",
                "api", "/api/v1/greetings/latest",
                "mlDocs", "http://localhost:8000/docs"
        );
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "timestamp", Instant.now().toString()
        );
    }
}
