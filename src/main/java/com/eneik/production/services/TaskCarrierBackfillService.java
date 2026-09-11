package com.eneik.production.services;

import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Startup backfill service for TaskEntity.carrier column (V138, ELVIN_GOLDMAN_01_RELIABILITY_CHAIN / D010).
 * Parses existing task payloads via Jackson in Java (preventing H2/Postgres SQL JSON dialect incompatibilities)
 * and materializes the carrier flag for older task rows.
 */
@Service
public class TaskCarrierBackfillService {
    private static final Logger log = LoggerFactory.getLogger(TaskCarrierBackfillService.class);

    private final TaskRepository taskRepository;

    public TaskCarrierBackfillService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationReady() {
        backfillCarrierColumn();
    }

    @Transactional
    public int backfillCarrierColumn() {
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
        return updated;
    }
}
