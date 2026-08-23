package com.eneik.production.controllers;

import com.eneik.production.models.persistence.ClaimEntity;
import com.eneik.production.models.persistence.LinearIssueMetadataEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.LinearIssueMetadataRepository;
import com.eneik.production.repositories.TaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    public InternalTaskController(TaskRepository taskRepository,
                                  LinearIssueMetadataRepository metadataRepository,
                                  ClaimRepository claimRepository,
                                  JdbcTemplate jdbcTemplate,
                                  ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.metadataRepository = metadataRepository;
        this.claimRepository = claimRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<TaskEntity> getAllTasks() {
        return taskRepository.findAll();
    }

    /** Lightweight, project-scoped status counts - COUNT queries only, no task bodies serialized.
     * Built 2026-08-11 after a real incident: repeatedly polling the unscoped {@link #getAllTasks()}
     * (a 60MB, ~1100-row dump across every project) for status-check purposes contributed to an H2
     * OutOfMemoryError that crashed the database. Use this for any repeated/monitoring status check. */
    @GetMapping("/status-counts")
    public Map<String, Long> statusCounts(@RequestParam UUID projectId) {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (TaskStatus status : TaskStatus.values()) {
            long count = taskRepository.countByProjectIdAndStatus(projectId, status);
            if (count > 0) {
                counts.put(status.name(), count);
            }
        }
        return counts;
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
        if (updates.containsKey("title")) {
            task.setTitle((String) updates.get("title"));
        }
        // Content-rewrite fields for manual task consolidation (e.g. collapsing a multi-task epic
        // the compiler over-decomposed back down to the operator-intended scope) - same one-off
        // manual-correction pattern as dependsOnTaskId/featureId below, content still traces back to
        // real compiler output, only the task-boundary is hand-edited.
        if (updates.containsKey("description")) {
            task.setDescription((String) updates.get("description"));
        }
        if (updates.containsKey("payload")) {
            Object rawPayload = updates.get("payload");
            task.setPayload(rawPayload == null ? null : objectMapper.valueToTree(rawPayload));
        }
        if (updates.containsKey("status")) {
            task.setStatus(TaskStatus.valueOf((String) updates.get("status")));
        }
        // Manual correction for tasks whose cynefinDomain was mis-derived before the 2026-08-03 fix (see
        // TechnicalLeadCompiler.cynefinDomain) - restores the compiler's real original per-slice value.
        if (updates.containsKey("cynefinDomain")) {
            task.setCynefinDomain((String) updates.get("cynefinDomain"));
        }
        // Wiring fields for manually-inserted tasks (e.g. an operator patching a gap the compiler missed
        // into the existing graph) - the compiler's own buildTaskGraphFromSlices sets these the same way,
        // this just exposes the same two fields for a one-off manual correction.
        if (updates.containsKey("dependsOnTaskId")) {
            String rawId = (String) updates.get("dependsOnTaskId");
            if (rawId == null || rawId.isBlank()) {
                task.setDependsOn(null);
            } else {
                TaskEntity parent = taskRepository.findById(UUID.fromString(rawId)).orElse(null);
                if (parent == null) {
                    return ResponseEntity.badRequest().build();
                }
                task.setDependsOn(parent);
            }
        }
        if (updates.containsKey("featureId")) {
            String rawFeatureId = (String) updates.get("featureId");
            task.setFeatureId(rawFeatureId == null || rawFeatureId.isBlank() ? null : UUID.fromString(rawFeatureId));
        }
        // 2026-08-08 (manual correction, same class as featureId above): OpsAuditorService.createTargetedRecoveryTask
        // deliberately creates a recovery task with no sourceWishlistId (it is not itself a compiler-planned
        // slice) - correct for dispatch (isDependencySatisfied matches by role/featureId/semantic key, not
        // wishlist linkage), but it means a SUCCESSFUL recovery's real merge evidence can never be seen by
        // ClientDeliverableReadinessService's per-planned-item fulfillment check, which only ever looks at
        // tasks reachable via the original wishlist item's own sourceWishlistId. Confirmed live
        // (test-forty-third): task 48707ded (a successful recovery, real merged PR) has no sourceWishlistId,
        // so wishlist item d3ca9563's slot could never show fulfilled even though its real work shipped.
        // Wiring the successful recovery back to the original item it fulfilled is the honest fix - not
        // dismissing the item (which would falsely claim the scope was abandoned when it was actually
        // delivered, just under a different task record).
        if (updates.containsKey("sourceWishlistId")) {
            String rawWishlistId = (String) updates.get("sourceWishlistId");
            task.setSourceWishlistId(rawWishlistId == null || rawWishlistId.isBlank() ? null : UUID.fromString(rawWishlistId));
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
        return claimRepository.findFirstByTaskIdAndReleasedAtIsNullOrderByClaimedAtDesc(id)
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
