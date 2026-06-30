package com.eneik.production.controllers;

import com.eneik.production.dto.agents.AgentOrchestratorSnapshotDTO;
import com.eneik.production.dto.agents.RequirementRequestDTO;
import com.eneik.production.dto.agents.TaskStatusRequestDTO;
import com.eneik.production.models.persistence.AgentTaskStatus;
import com.eneik.production.services.AgentOrchestratorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agents")
@CrossOrigin(origins = "http://localhost:3000")
public class AgentOrchestratorController {
    private final AgentOrchestratorService orchestratorService;

    public AgentOrchestratorController(AgentOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @GetMapping("/snapshot")
    public AgentOrchestratorSnapshotDTO snapshot() {
        return orchestratorService.snapshot();
    }

    @PostMapping("/requirements")
    public ResponseEntity<?> createRequirement(@RequestBody RequirementRequestDTO request) {
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Requirement title is required", "code", 400));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orchestratorService.createRequirement(request.getTitle(), request.getDescription()));
    }

    @PostMapping("/accounts/{accountCode}/claim")
    public AgentOrchestratorSnapshotDTO claimNext(@PathVariable String accountCode) {
        return orchestratorService.claimNextForAccount(accountCode);
    }

    @PostMapping("/auto-claim")
    public AgentOrchestratorSnapshotDTO autoClaim() {
        return orchestratorService.autoClaimTodoTasks();
    }

    @PostMapping("/tasks/{taskId}/status")
    public AgentOrchestratorSnapshotDTO updateStatus(@PathVariable UUID taskId,
                                                     @RequestBody TaskStatusRequestDTO request) {
        AgentTaskStatus status = AgentTaskStatus.valueOf(request.getStatus());
        return orchestratorService.updateTaskStatus(taskId, status);
    }
}
