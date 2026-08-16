package com.eneik.production.controllers.monitor;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.repositories.JulesSessionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/monitor/sessions")
public class JulesMonitorController {

    private final JulesSessionRepository julesSessionRepository;

    public JulesMonitorController(JulesSessionRepository julesSessionRepository) {
        this.julesSessionRepository = julesSessionRepository;
    }

    @GetMapping
    public List<JulesSessionEntity> getAllSessions() {
        return julesSessionRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<JulesSessionEntity> getSessionById(@PathVariable UUID id) {
        return julesSessionRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
