package com.eneik.production.services.audit;

import com.eneik.production.kaizen.model.DefectJournalEntity;
import com.eneik.production.kaizen.repository.DefectJournalRepository;
import com.eneik.production.kaizen.service.DefectJournalService;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.EvidenceNodeRepository;
import com.eneik.production.services.logging.ProjectLogFlushQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test shield enforcing Prescription 50 / John Searle's INSTITUTIONAL_FACT_REGISTER (D007, Searle 1995):
 * Every triggered institutional prohibition on terminal task status overwrite MUST leave a durable,
 * queryable trace in the institutional fact register, defect journal, and project event log.
 */
public class TaskTerminalOverwriteAuditTest {

    @BeforeEach
    @AfterEach
    void cleanState() {
        TaskTerminalOverwriteAudit.clear();
        ProjectLogFlushQueue.drain(10_000);
    }

    @Test
    @DisplayName("Falsification harness: Attempt to overwrite terminal status leaves queryable trace in register")
    void testTerminalStatusOverwriteLeavesQueryableAuditTrace() {
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setStatus(TaskStatus.done);

        assertTrue(task.isTerminal());
        assertEquals(TaskStatus.done, task.getStatus());
        assertTrue(TaskTerminalOverwriteAudit.getAttemptsForTask(taskId).isEmpty(),
                "Initial terminal transition must not be recorded as a prohibited overwrite attempt");

        // Attempt prohibited overwrite: done -> queued
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> task.setStatus(TaskStatus.queued));
        assertTrue(ex.getMessage().contains("cannot be overwritten with queued"));

        // Verify trace in institutional fact register
        List<TaskTerminalOverwriteAudit.AttemptRecord> attempts = TaskTerminalOverwriteAudit.getAttemptsForTask(taskId);
        assertEquals(1, attempts.size(), "Triggered prohibition must leave exactly one audit entry in register");

        TaskTerminalOverwriteAudit.AttemptRecord record = attempts.get(0);
        assertEquals(taskId, record.taskId());
        assertEquals(projectId, record.projectId());
        assertEquals(TaskStatus.done, record.currentStatus());
        assertEquals(TaskStatus.queued, record.attemptedStatus());
        assertEquals(TaskTerminalOverwriteAudit.RULE_NAME, record.rule());
        assertNotNull(record.timestamp());

        // Verify queued into ProjectLogFlushQueue for project event log
        List<ProjectLogFlushQueue.PendingEntry> projectEntries = ProjectLogFlushQueue.drain(10);
        assertEquals(1, projectEntries.size(), "Project log queue must receive audit entry");
        ProjectLogFlushQueue.PendingEntry logEntry = projectEntries.get(0);
        assertEquals(projectId, logEntry.projectId());
        assertEquals("WARN", logEntry.level());
        assertTrue(logEntry.message().contains("Institutional prohibition triggered"));
        assertTrue(logEntry.message().contains(taskId.toString()));
    }

    @Test
    @DisplayName("All terminal statuses (done, failed, spike_completed) record prohibitions across varied targets")
    void testAllTerminalStatusesAreAudited() {
        TaskStatus[] terminalStatuses = {TaskStatus.done, TaskStatus.failed, TaskStatus.spike_completed};
        TaskStatus[] targetStatuses = {TaskStatus.queued, TaskStatus.claimed, TaskStatus.in_progress, TaskStatus.blocked};

        for (TaskStatus terminal : terminalStatuses) {
            UUID taskId = UUID.randomUUID();
            TaskEntity task = new TaskEntity();
            task.setId(taskId);
            task.setStatus(terminal);

            for (TaskStatus target : targetStatuses) {
                assertThrows(IllegalStateException.class, () -> task.setStatus(target));
            }

            List<TaskTerminalOverwriteAudit.AttemptRecord> attempts = TaskTerminalOverwriteAudit.getAttemptsForTask(taskId);
            assertEquals(targetStatuses.length, attempts.size(),
                    "Each illegal attempt against terminal status " + terminal + " must be recorded");
            for (int i = 0; i < targetStatuses.length; i++) {
                assertEquals(terminal, attempts.get(i).currentStatus());
            }
        }
    }

    @Test
    @DisplayName("Idempotent and valid non-terminal transitions do NOT trigger audit records")
    void testValidTransitionsDoNotPolluteAuditRegister() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(taskId);

        // Valid non-terminal progression
        task.setStatus(TaskStatus.claimed);
        task.setStatus(TaskStatus.in_progress);
        task.setStatus(TaskStatus.pending_review);
        task.setStatus(TaskStatus.review);
        task.setStatus(TaskStatus.blocked);
        task.setStatus(TaskStatus.queued);

        // Move to terminal
        task.setStatus(TaskStatus.done);

        // Idempotent assignment
        task.setStatus(TaskStatus.done);

        assertTrue(TaskTerminalOverwriteAudit.getAttemptsForTask(taskId).isEmpty(),
                "Valid and idempotent transitions must not create prohibited audit records");
    }

    @Test
    @DisplayName("TaskTerminalOverwriteAuditService bridges attempts into DefectJournalService as INSTITUTIONAL_AUDIT")
    void testBridgePersistsToDefectJournal() {
        DefectJournalRepository mockRepo = mock(DefectJournalRepository.class);
        EvidenceNodeRepository mockEvidenceRepo = mock(EvidenceNodeRepository.class);
        DefectJournalService defectJournalService = new DefectJournalService(mockRepo, mockEvidenceRepo);

        TaskTerminalOverwriteAuditService auditService = new TaskTerminalOverwriteAuditService(defectJournalService);
        auditService.init();

        try {
            UUID taskId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            ProjectEntity project = new ProjectEntity();
            project.setId(projectId);

            TaskEntity task = new TaskEntity();
            task.setId(taskId);
            task.setProject(project);
            task.setStatus(TaskStatus.failed);

            // Trigger prohibition
            assertThrows(IllegalStateException.class, () -> task.setStatus(TaskStatus.in_progress));

            ArgumentCaptor<DefectJournalEntity> captor = ArgumentCaptor.forClass(DefectJournalEntity.class);
            verify(mockRepo, atLeastOnce()).save(captor.capture());

            DefectJournalEntity saved = captor.getValue();
            assertEquals(projectId, saved.getProjectId());
            assertEquals("TaskEntity", saved.getSourceComponent());
            assertEquals("TERMINAL_STATUS_OVERWRITE_PROHIBITION", saved.getDefectType());
            assertEquals("INSTITUTIONAL_AUDIT", saved.getCategory());
            assertEquals("INFO", saved.getSeverity());
            assertTrue(saved.getDescription().contains("Attempted illegal overwrite of terminal status failed"));
            assertTrue(saved.getDescription().contains(taskId.toString()));

            // Verify that INSTITUTIONAL_AUDIT is filtered out from Kaizen defect metric window (category hygiene / Ryle D002)
            assertTrue(DefectJournalService.NON_DEFECT_AUDIT_CATEGORIES.contains(saved.getCategory()),
                    "Institutional audit must be classified in NON_DEFECT_AUDIT_CATEGORIES");
        } finally {
            auditService.destroy();
        }
    }
}
