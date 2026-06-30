package com.eneik.production.controllers;

import com.eneik.production.dto.GreetingResponseDTO;
import com.eneik.production.mappers.GreetingMapper;
import com.eneik.production.models.domain.Greeting;
import com.eneik.production.models.persistence.GreetingEntity;
import com.eneik.production.repositories.GreetingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for managing Greetings and monitoring Lean metrics.
 * Implements the REST contract defined in 00_INTEGRATION_CONTRACT.md.
 */
@RestController
@RequestMapping("/api/v1/greetings")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class GreetingController {

    private final GreetingRepository greetingRepository;
    private final GreetingMapper greetingMapper;

    /**
     * Retrieves the latest greeting from the system.
     *
     * @return 200 OK with the latest greeting DTO.
     */
    @GetMapping("/latest")
    public ResponseEntity<GreetingResponseDTO> getLatest() {
        return greetingRepository.findFirstByOrderByCreatedAtDesc()
            .map(greetingMapper::toDomain)
            .map(greetingMapper::toResponseDTO)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Creates a new greeting in the system with initial status RECEIVED.
     *
     * @param payload JSON containing the greeting message.
     * @return 201 Created with the created greeting DTO, or 400 for compliance violations.
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> payload) {
        String message = payload.get("message");
        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Compliance Violation", "code", 400));
        }

        GreetingEntity entity = new GreetingEntity(message);
        GreetingEntity saved = greetingRepository.save(entity);

        Greeting domain = greetingMapper.toDomain(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(greetingMapper.toResponseDTO(domain));
    }
}
