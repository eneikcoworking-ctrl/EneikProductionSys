package com.eneik.production.services;

import com.eneik.production.models.persistence.ProjectEventLogEntity;
import com.eneik.production.repositories.ProjectEventLogRepository;
import com.eneik.production.services.logging.ProjectLogFlushQueue;
import com.eneik.production.services.settings.SystemSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Restoration (2026-07-26, operator directive: "the project log must not depend on deployments!! this is a huge
 * omission. I definitely asked for a full log from project start to its acceptance") of the
 * durable per-project log deleted in V58 (2026-07-25). That deletion over-corrected a real, narrower
 * complaint - Gemini's observer should not CONSUME the backend's own internal log as if it were the
 * project - by removing the capture pipeline entirely instead of just cutting Gemini off from it. This
 * service persists it again, DB-backed (survives any redeploy, unlike docker's stdout buffer - confirmed
 * live 2026-07-26 losing the trail on a real bug because a redeploy erased the only copy), and stays
 * strictly external-agent/operator-facing: {@link GeminiProjectObserverService} still only ever sees its
 * own evidence snapshot + journal, never this.
 */
@Service
public class ProjectEventLogService {
    private static final Logger log = LoggerFactory.getLogger(ProjectEventLogService.class);
    private static final int FLUSH_BATCH_SIZE = 500;

    private final ProjectEventLogRepository repository;
    private final SystemSettingsService settingsService;

    public ProjectEventLogService(ProjectEventLogRepository repository, SystemSettingsService settingsService) {
        this.repository = repository;
        this.settingsService = settingsService;
    }

    /**
     * Drains the in-memory hand-off queue into the DB every few seconds. Runs (and drains) even when the
     * feature flag is off, so a disabled flag can't leak into unbounded queue growth - it just discards
     * instead of persisting.
     */
    @Scheduled(fixedRate = 5000)
    public void flush() {
        List<ProjectLogFlushQueue.PendingEntry> batch = ProjectLogFlushQueue.drain(FLUSH_BATCH_SIZE);
        if (batch.isEmpty()) {
            return;
        }
        if (!settingsService.effectiveBoolean("project_event_log_enabled")) {
            return;
        }
        List<ProjectEventLogEntity> entities = new ArrayList<>(batch.size());
        for (ProjectLogFlushQueue.PendingEntry entry : batch) {
            ProjectEventLogEntity entity = new ProjectEventLogEntity();
            entity.setProjectId(entry.projectId());
            entity.setCreatedAt(entry.createdAt());
            entity.setLevel(entry.level());
            entity.setLogger(entry.logger());
            entity.setMessage(entry.message());
            entities.add(entity);
        }
        try {
            repository.saveAll(entities);
        } catch (Exception e) {
            log.warn("ProjectEventLogService: failed to flush {} log entries: {}", entities.size(), e.getMessage());
        }
    }

    /** Most recent entries first, bounded - for a human/external agent skimming recent project history. */
    public List<ProjectEventLogEntity> recent(UUID projectId, int limit) {
        int bounded = Math.max(1, Math.min(limit, 5000));
        return repository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(0, bounded));
    }

    /** Chronological, everything since a given point - for reconstructing "what happened between X and Y". */
    public List<ProjectEventLogEntity> since(UUID projectId, Instant since) {
        return repository.findByProjectIdAndCreatedAtAfterOrderByCreatedAtAsc(projectId, since);
    }
}
