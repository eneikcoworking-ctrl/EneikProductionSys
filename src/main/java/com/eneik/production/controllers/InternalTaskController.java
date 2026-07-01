package com.eneik.production.controllers;

import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.TaskRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Internal API for synchronization scripts.
 * Restricted to localhost in production via filter/security (omitted for brevity in this task).
 */
@RestController
@RequestMapping("/internal/tasks")
public class InternalTaskController {

    private final TaskRepository taskRepository;

    public InternalTaskController(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @GetMapping
    public List<TaskEntity> getAllTasks() {
        return taskRepository.findAll();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateTask(@PathVariable UUID id, @RequestBody Map<String, Object> updates) {
        TaskEntity task = taskRepository.findById(id).orElse(null);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }

        if (updates.containsKey("linearIssueId")) {
            task.setLinearIssueId((String) updates.get("linearIssueId"));
        }
        if (updates.containsKey("status")) {
            task.setStatus(TaskStatus.valueOf((String) updates.get("status")));
        }

        taskRepository.save(task);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/by-linear-id/{linearIssueId}")
    public ResponseEntity<TaskEntity> getTaskByLinearId(@PathVariable String linearIssueId) {
        // Simple scan for demo purposes, could add a repo method
        return taskRepository.findAll().stream()
                .filter(t -> linearIssueId.equals(t.getLinearIssueId()))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
