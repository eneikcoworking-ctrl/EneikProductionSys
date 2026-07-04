package com.eneik.production.controllers;

import com.eneik.production.models.persistence.ClaimEntity;
import com.eneik.production.models.persistence.LinearIssueMetadataEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.LinearIssueMetadataRepository;
import com.eneik.production.repositories.TaskRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
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
    private final LinearIssueMetadataRepository metadataRepository;
    private final ClaimRepository claimRepository;
    private final JdbcTemplate jdbcTemplate;

    public InternalTaskController(TaskRepository taskRepository,
                                  LinearIssueMetadataRepository metadataRepository,
                                  ClaimRepository claimRepository,
                                  JdbcTemplate jdbcTemplate) {
        this.taskRepository = taskRepository;
        this.metadataRepository = metadataRepository;
        this.claimRepository = claimRepository;
        this.jdbcTemplate = jdbcTemplate;
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

    @GetMapping("/{id}/active-claim")
    public ResponseEntity<ClaimEntity> getActiveClaim(@PathVariable UUID id) {
        return claimRepository.findByTaskIdAndReleasedAtIsNull(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/metadata")
    public ResponseEntity<LinearIssueMetadataEntity> getMetadata(@PathVariable UUID id) {
        return metadataRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/metadata")
    @Transactional
    public ResponseEntity<Void> updateMetadata(@PathVariable UUID id, @RequestBody Map<String, Object> updates) {
        if (!taskRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        jdbcTemplate.update(
                """
                MERGE INTO linear_issue_metadata (task_id, linear_issue_id, blockers, dod_text, pr_url, last_synced_at)
                KEY (task_id)
                VALUES (
                    ?,
                    COALESCE(?, (SELECT linear_issue_id FROM linear_issue_metadata WHERE task_id = ?)),
                    COALESCE(?, (SELECT blockers FROM linear_issue_metadata WHERE task_id = ?)),
                    COALESCE(?, (SELECT dod_text FROM linear_issue_metadata WHERE task_id = ?)),
                    COALESCE(?, (SELECT pr_url FROM linear_issue_metadata WHERE task_id = ?)),
                    ?
                )
                """,
                id,
                (String) updates.get("linearIssueId"), id,
                (String) updates.get("blockers"), id,
                (String) updates.get("dodText"), id,
                (String) updates.get("prUrl"), id,
                Instant.now()
        );

        return ResponseEntity.ok().build();
    }
}
