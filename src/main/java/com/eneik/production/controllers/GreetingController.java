package com.eneik.production.controllers;

import com.eneik.production.dto.GreetingResponseDTO;
import com.eneik.production.mappers.GreetingMapper;
import com.eneik.production.models.domain.Greeting;
import com.eneik.production.models.persistence.GreetingEntity;
import com.eneik.production.repositories.GreetingRepository;
import com.eneik.production.services.MLPredictionServiceClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/greetings")
public class GreetingController {
    private final GreetingRepository greetingRepository;
    private final GreetingMapper greetingMapper;
    private final MLPredictionServiceClient mlPredictionServiceClient;

    public GreetingController(GreetingRepository greetingRepository,
                              GreetingMapper greetingMapper,
                              MLPredictionServiceClient mlPredictionServiceClient) {
        this.greetingRepository = greetingRepository;
        this.greetingMapper = greetingMapper;
        this.mlPredictionServiceClient = mlPredictionServiceClient;
    }

    @GetMapping("/latest")
    public ResponseEntity<GreetingResponseDTO> getLatest() {
        return greetingRepository.findFirstByOrderByCreatedAtDesc()
                .map(greetingMapper::toDomain)
                .map(greetingMapper::toResponseDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> payload) {
        String message = payload.get("message");
        if (message == null || message.trim().isEmpty() || containsCardNumber(message)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Compliance Violation", "code", 400));
        }

        GreetingEntity entity = new GreetingEntity(maskEmail(message.trim()));
        GreetingEntity saved = greetingRepository.save(entity);
        mlPredictionServiceClient.checkSystemRisk(1, 1.0);

        Greeting domain = greetingMapper.toDomain(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(greetingMapper.toResponseDTO(domain));
    }

    private boolean containsCardNumber(String message) {
        return message.matches(".*\\d{4}-\\d{4}-\\d{4}-\\d{4}.*");
    }

    private String maskEmail(String message) {
        return message.replaceAll("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}", "[REDACTED]");
    }
}
