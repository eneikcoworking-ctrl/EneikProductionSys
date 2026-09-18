package com.eneik.production.services.audit;

import com.eneik.production.kaizen.service.DefectJournalService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * Spring service bridging {@link TaskTerminalOverwriteAudit} events into {@link DefectJournalService}.
 * Ensures that whenever a prohibited attempt to overwrite a terminal task status occurs,
 * an institutional fact record (category INSTITUTIONAL_AUDIT) is persisted in the database.
 */
@Service
public class TaskTerminalOverwriteAuditService {

    private static final Logger log = LoggerFactory.getLogger(TaskTerminalOverwriteAuditService.class);

    private final DefectJournalService defectJournalService;
    private final Consumer<TaskTerminalOverwriteAudit.AttemptRecord> listener = this::onAttempt;

    public TaskTerminalOverwriteAuditService(DefectJournalService defectJournalService) {
        this.defectJournalService = defectJournalService;
    }

    @PostConstruct
    public void init() {
        TaskTerminalOverwriteAudit.registerListener(listener);
        log.info("[INSTITUTIONAL-AUDIT] TaskTerminalOverwriteAuditService initialized and registered listener.");
    }

    @PreDestroy
    public void destroy() {
        TaskTerminalOverwriteAudit.unregisterListener(listener);
        log.info("[INSTITUTIONAL-AUDIT] TaskTerminalOverwriteAuditService unregistered listener.");
    }

    private void onAttempt(TaskTerminalOverwriteAudit.AttemptRecord record) {
        try {
            String description = String.format(
                    "Attempted illegal overwrite of terminal status %s of task %s to %s",
                    record.currentStatus(), record.taskId(), record.attemptedStatus()
            );
            defectJournalService.recordInstitutionalAudit(
                    record.projectId(),
                    "TaskEntity",
                    record.rule(),
                    description,
                    1.0
            );
        } catch (Exception e) {
            log.error("Failed to persist terminal status overwrite audit to defect journal", e);
        }
    }
}
