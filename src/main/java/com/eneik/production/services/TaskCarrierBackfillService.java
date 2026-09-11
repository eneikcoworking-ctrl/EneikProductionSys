package com.eneik.production.services;

import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Startup backfill service for TaskEntity.carrier column (V138, ELVIN_GOLDMAN_01_RELIABILITY_CHAIN / D010).
 * Parses existing task payloads via Jackson in Java (preventing H2/Postgres SQL JSON dialect incompatibilities)
 * and materializes the carrier flag for older task rows.
 *
 * Invariant:
 * The backfill is executed ONCE. Upon completion, a completion marker is persisted in system_settings.
 * Subsequent application startups check the marker and skip scanning the tasks table entirely.
 */
@Service
public class TaskCarrierBackfillService {
    private static final Logger log = LoggerFactory.getLogger(TaskCarrierBackfillService.class);
    public static final String BACKFILL_SETTING_KEY = "carrier_backfill_completed";

    private final TaskRepository taskRepository;
    private final JdbcTemplate jdbcTemplate;

    public TaskCarrierBackfillService(TaskRepository taskRepository, JdbcTemplate jdbcTemplate) {
        this.taskRepository = taskRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationReady() {
        backfillCarrierColumn();
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
            log.debug("[CARRIER-BACKFILL] Failed to check backfill status from system_settings: {}", e.getMessage());
            return false;
        }
    }

    @Transactional
    public int backfillCarrierColumn() {
        if (isBackfillCompleted()) {
            log.info("[CARRIER-BACKFILL] Carrier backfill already marked completed in system_settings; skipping task scan.");
            return 0;
        }

        List<TaskEntity> candidates = taskRepository.findCarrierBackfillCandidates();
        int updated = 0;
        for (TaskEntity task : candidates) {
            if (task.getPayload() != null && task.getPayload().hasNonNull(TaskEntity.CARRIER_PAYLOAD_KEY)) {
                task.setCarrier(true);
                taskRepository.save(task);
                updated++;
            }
        }
        if (updated > 0) {
            log.info("[CARRIER-BACKFILL] Synchronized carrier=true for {} legacy tasks from JSON payload", updated);
        }

        markBackfillCompleted();
        return updated;
    }

    private void markBackfillCompleted() {
        try {
            jdbcTemplate.update("DELETE FROM system_settings WHERE \"key\" = ?", BACKFILL_SETTING_KEY);
            jdbcTemplate.update(
                    "INSERT INTO system_settings (\"key\", \"value\", updated_at) VALUES (?, 'true', CURRENT_TIMESTAMP)",
                    BACKFILL_SETTING_KEY
            );
            log.info("[CARRIER-BACKFILL] Recorded '{}' marker in system_settings.", BACKFILL_SETTING_KEY);
        } catch (Exception e) {
            log.warn("[CARRIER-BACKFILL] Could not record backfill marker in system_settings: {}", e.getMessage());
        }
    }
}
