package com.eneik.production.controllers.jules;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.services.jules.JulesDispatchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/jules-sessions")
public class JulesSessionController {

    private final JulesDispatchService julesDispatchService;
    private final JulesSessionRepository julesSessionRepository;

    public JulesSessionController(JulesDispatchService julesDispatchService,
                                  JulesSessionRepository julesSessionRepository) {
        this.julesDispatchService = julesDispatchService;
        this.julesSessionRepository = julesSessionRepository;
    }

    @PostMapping("/dispatch")
    public JulesSessionEntity dispatch(@RequestBody DispatchRequest request) {
        // dispatch(UUID, UUID) inside service now handles ClaimService call correctly
        return julesDispatchService.dispatch(request.taskId(), request.accountId());
    }

    @GetMapping("/{id}")
    public JulesSessionEntity getSession(@PathVariable UUID id) {
        return julesDispatchService.pollStatus(id);
    }

    @GetMapping
    public List<JulesSessionEntity> listSessions(@RequestParam(required = false) UUID taskId) {
        if (taskId != null) {
            return julesSessionRepository.findByTaskId(taskId);
        }
        return julesSessionRepository.findAll();
    }

    public record DispatchRequest(UUID taskId, UUID accountId) {}
}
