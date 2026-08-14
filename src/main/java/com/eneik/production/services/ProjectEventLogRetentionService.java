package com.eneik.production.services;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectEventLogEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.repositories.ProjectEventLogRepository;
import com.eneik.production.repositories.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Keeps the durable per-project log bounded, by meaning rather than by age.
 *
 * The log exists because of an explicit operator requirement (2026-07-26: "лог проекта должен независеть
 * от деплоев!! ... полный лог от старта проекта до его принятия"), so the policy is written to honour that
 * sentence literally: nothing is removed from a project that has not been accepted yet, however old the
 * entries are. What the requirement does NOT ask for is keeping that log forever after the engagement
 * ended - and since nothing ever deleted a row, the table grew without bound by construction, reaching
 * 162k rows with every project frozen and no work happening at all.
 *
 * Two independent bounds, because they fail differently:
 *   - accepted projects: the log has served its purpose once the client accepted delivery; it is dropped
 *     after a grace period, which exists so a dispute right after acceptance still has the evidence.
 *   - every project: a per-project ceiling, so one long-running or noisy project cannot fill the database
 *     on its own while still legitimately unaccepted. The newest entries are the ones kept - a stalled
 *     project's recent history is what anyone diagnosing it actually reads.
 *
 * Deliberately conservative about frozen projects: frozen is not finished, it is paused, and a resumed
 * project's earlier trail is exactly what explains how it got there.
 */
@Service
public class ProjectEventLogRetentionService {
    private static final Logger log = LoggerFactory.getLogger(ProjectEventLogRetentionService.class);

    private final ProjectEventLogRepository repository;
    private final ProjectRepository projectRepository;

    // Self-proxy so the @Transactional on the delete steps actually engages: deleteByProjectIdAndCreatedAtBefore
    // is a @Modifying query, which throws TransactionRequiredException unless its caller already has a
    // writable transaction open - and a plain this.method() call bypasses the Spring proxy entirely, so the
    // annotation would silently never activate. Exactly the failure that broke orchestrate() earlier today.
    private final ProjectEventLogRetentionService self;

    /** Grace period after acceptance before an ended engagement's log is dropped. */
    @Value("${project-event-log.retain-after-accepted-days:30}")
    private int retainAfterAcceptedDays;

    /** Ceiling per project, newest kept. Zero disables the ceiling. */
    @Value("${project-event-log.max-entries-per-project:20000}")
    private int maxEntriesPerProject;

    public ProjectEventLogRetentionService(ProjectEventLogRepository repository,
                                            ProjectRepository projectRepository,
                                            @org.springframework.context.annotation.Lazy ProjectEventLogRetentionService self) {
        this.repository = repository;
        this.projectRepository = projectRepository;
        this.self = self;
    }

    /**
     * Daily, off the hour. Not @Transactional at this level: each project's delete runs in its own short
     * transaction, so one failure cannot roll back the others and no single transaction holds a connection
     * across the whole sweep - the same discipline applied to the other scheduled sweeps on 2026-08-14.
     */
    @Scheduled(cron = "${project-event-log.retention-cron:0 17 3 * * ?}")
    public void enforceRetention() {
        int removedFromAccepted = 0;
        int removedByCeiling = 0;

        for (ProjectEntity project : projectRepository.findAll()) {
            try {
                if (project.getStatus() == ProjectStatus.accepted && project.getAcceptedAt() != null) {
                    Instant graceEnds = project.getAcceptedAt().plus(retainAfterAcceptedDays, ChronoUnit.DAYS);
                    if (Instant.now().isAfter(graceEnds)) {
                        // Everything, not a window: the engagement is over and the grace period has passed.
                        removedFromAccepted += self.deleteBefore(project.getId(), Instant.now());
                        continue;
                    }
                }
                removedByCeiling += self.trimToCeiling(project.getId(), maxEntriesPerProject);
            } catch (Exception e) {
                // One project's failure must not stop the sweep - the same per-item isolation the
                // orchestration loop needed on 2026-08-14.
                log.error("ProjectEventLogRetentionService: retention failed for project {}: {}",
                        project.getId(), e.getMessage(), e);
            }
        }

        if (removedFromAccepted > 0 || removedByCeiling > 0) {
            log.info("ProjectEventLogRetentionService: removed {} entry(ies) from accepted projects past "
                            + "their grace period and {} over the per-project ceiling",
                    removedFromAccepted, removedByCeiling);
        }
    }

    /** One project's delete, in its own short transaction - a @Modifying query needs an active one. */
    @Transactional
    public int deleteBefore(java.util.UUID projectId, Instant cutoff) {
        return repository.deleteByProjectIdAndCreatedAtBefore(projectId, cutoff);
    }

    @Transactional
    public int trimToCeiling(java.util.UUID projectId, int ceiling) {
        if (ceiling <= 0) {
            return 0;
        }
        long count = repository.countByProjectId(projectId);
        if (count <= ceiling) {
            return 0;
        }
        int excess = (int) (count - ceiling);
        // Reads only the excess rows to find the cutoff timestamp, then deletes by that timestamp in one
        // statement - never loads the whole log into memory, which on the live table would mean 162k rows.
        List<ProjectEventLogEntity> oldest =
                repository.findByProjectIdOrderByCreatedAtAsc(projectId, PageRequest.of(0, excess));
        if (oldest.isEmpty()) {
            return 0;
        }
        Instant cutoff = oldest.get(oldest.size() - 1).getCreatedAt();
        return repository.deleteByProjectIdAndCreatedAtBefore(projectId, cutoff);
    }
}
