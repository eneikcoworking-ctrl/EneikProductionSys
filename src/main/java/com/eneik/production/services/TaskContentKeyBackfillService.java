package com.eneik.production.services;

import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.TaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Startup backfill service for {@code tasks.content_key} column (V137, DEREK_PARFIT_01_PERSISTENCE_SNAPSHOT / D010).
 *
 * <p>Prescription 54:
 * Derives and materializes work identity (content_key) for historical compiler task rows created prior to
 * migration V137, where the underlying work (wishlist batch compilation or persistent compiler worker) is
 * derivable from payload markers ({@code compilesWishlistIds} or {@code persistentWorkerCarrier}).
 *
 * <p>Invariant:
 * The backfill is executed ONCE. Upon completion, a completion marker is persisted in system_settings.
 * Subsequent application startups check the marker and skip scanning the tasks table entirely.
 */
@Service
public class TaskContentKeyBackfillService {

    private static final Logger log = LoggerFactory.getLogger(TaskContentKeyBackfillService.class);
    public static final String BACKFILL_SETTING_KEY = "content_key_backfill_completed";

    private final TaskRepository taskRepository;
    private final JdbcTemplate jdbcTemplate;

    public TaskContentKeyBackfillService(TaskRepository taskRepository, JdbcTemplate jdbcTemplate) {
        this.taskRepository = taskRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationReady() {
        backfillContentKeyColumn();
    }

    public boolean isBackfillCompleted() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM system_settings WHERE \"key\" = ? AND \"value\" = 'true'",
                    Integer.class,
                    BACKFILL_SETTING_KEY
            );
            return count != null && count > 0;
        } catch (Exception e) {
            log.debug("[CONTENT-KEY-BACKFILL] Failed to check backfill status from system_settings: {}", e.getMessage());
            return false;
        }
    }

    @Transactional
    public int backfillContentKeyColumn() {
        if (isBackfillCompleted()) {
            log.info("[CONTENT-KEY-BACKFILL] Content key backfill already marked completed in system_settings; skipping task scan.");
            return 0;
        }

        List<TaskEntity> candidates = taskRepository.findContentKeyBackfillCandidates();
        int updated = 0;

        for (TaskEntity task : candidates) {
            if (task.getContentKey() != null || task.getPayload() == null) {
                continue;
            }
            if (task.getProject() == null || task.getProject().getId() == null) {
                continue;
            }

            UUID projectId = task.getProject().getId();
            JsonNode payload = task.getPayload();
            String contentKey = deriveContentKeyFromPayload(projectId, task.getTitle(), payload);

            if (contentKey != null) {
                task.setContentKey(contentKey);
                taskRepository.save(task);
                updated++;
            }
        }

        if (updated > 0) {
            log.info("[CONTENT-KEY-BACKFILL] Materialized content_key for {} legacy compiler tasks from payload work definition", updated);
        }

        markBackfillCompleted();
        return updated;
    }

    /**
     * Derives work identity for historical compiler tasks:
     * - Persistent worker: "compile-worker:<projectId>"
     * - Batch wishlist compiler: "compile:<projectId>:<sha256Hex(sortedIds)>"
     */
    public static String deriveContentKeyFromPayload(UUID projectId, String title, JsonNode payload) {
        if (projectId == null || payload == null) {
            return null;
        }

        // 1. Persistent compiler worker
        if (payload.path("persistentWorkerCarrier").asBoolean(false)
                || (title != null && title.startsWith("Persistent wishlist compiler worker"))) {
            return "compile-worker:" + projectId;
        }

        // 2. Batch wishlist compiler
        JsonNode idsNode = payload.path(ProjectFlowService.WISHLIST_COMPILER_WISHLIST_IDS_KEY);
        if (idsNode.isArray() && !idsNode.isEmpty()) {
            List<UUID> wishlistIds = new ArrayList<>();
            for (JsonNode idNode : idsNode) {
                try {
                    wishlistIds.add(UUID.fromString(idNode.asText("")));
                } catch (IllegalArgumentException ignored) {
                    // skip malformed id
                }
            }
            if (!wishlistIds.isEmpty()) {
                return ProjectFlowService.compilerContentKeyFromIds(projectId, wishlistIds);
            }
        }

        return null;
    }

    private void markBackfillCompleted() {
        try {
            jdbcTemplate.update("DELETE FROM system_settings WHERE \"key\" = ?", BACKFILL_SETTING_KEY);
            jdbcTemplate.update(
                    "INSERT INTO system_settings (\"key\", \"value\", updated_at) VALUES (?, 'true', CURRENT_TIMESTAMP)",
                    BACKFILL_SETTING_KEY
            );
            log.info("[CONTENT-KEY-BACKFILL] Recorded '{}' marker in system_settings.", BACKFILL_SETTING_KEY);
        } catch (Exception e) {
            log.warn("[CONTENT-KEY-BACKFILL] Could not record backfill marker in system_settings: {}", e.getMessage());
        }
    }
}
