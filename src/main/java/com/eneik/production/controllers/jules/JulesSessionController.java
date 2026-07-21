package com.eneik.production.controllers.jules;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.services.jules.JulesDispatchService;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable UUID id, @RequestBody(required = false) CancelRequest request) {
        String reason = (request != null && request.reason() != null && !request.reason().isBlank())
                ? request.reason()
                : "Cancelled by operator";
        julesDispatchService.cancelSession(id, reason);
        return ResponseEntity.ok().build();
    }

    /**
     * One-off manual correction for a session whose locally-tracked status has drifted from reality (e.g.
     * a genuinely open PR exists but the session got marked into some terminal-ish status by an unrelated
     * circuit breaker) - never fabricates content, only re-points our own tracking at the real PR so the
     * normal pipeline (batch review, etc.) picks it up again. Same rationale/precedent as
     * InternalTaskController's dependsOnTaskId/description/payload PATCH fields added earlier tonight.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateSession(@PathVariable UUID id, @RequestBody java.util.Map<String, Object> updates) {
        JulesSessionEntity session = julesSessionRepository.findById(id).orElse(null);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        if (updates.containsKey("status")) {
            session.setStatus((String) updates.get("status"));
        }
        if (updates.containsKey("prUrl")) {
            session.setPrUrl((String) updates.get("prUrl"));
        }
        julesSessionRepository.save(session);
        return ResponseEntity.ok().build();
    }

    public record DispatchRequest(UUID taskId, UUID accountId) {}
    public record CancelRequest(String reason) {}
}
