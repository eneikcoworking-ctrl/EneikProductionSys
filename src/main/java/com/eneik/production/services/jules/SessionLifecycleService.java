package com.eneik.production.services.jules;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Sole owner of "does Jules itself know this session is done" - 2026-08-01 (operator: "те сессии джулс,
 * которые не продуктовые а технические можно убирать в архив или удалять"). Confirmed live against
 * Jules's real API (https://jules.google/docs/api/reference/sessions/): DELETE /v1alpha/sessions/{session}
 * genuinely removes the session server-side (a follow-up GET returns a real 404, not just a locally-assumed
 * success) - before this, our own "cancelled" convention was purely local fiction, Jules was never told.
 *
 * Two triggers: a task reaching a terminal status, or its whole project closing - matching exactly what the
 * operator described doing by hand ("я вручную отправлял много активных сессий из закрытого проекта").
 */
@Service
public class SessionLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(SessionLifecycleService.class);

    private static final Set<TaskStatus> TERMINAL_TASK_STATUSES =
            EnumSet.of(TaskStatus.done, TaskStatus.failed, TaskStatus.spike_completed);
    private static final Set<ProjectStatus> CLOSED_PROJECT_STATUSES =
            EnumSet.of(ProjectStatus.frozen, ProjectStatus.accepted, ProjectStatus.archived);

    private final JulesSessionRepository julesSessionRepository;
    private final AccountRepository accountRepository;
    private final TaskRepository taskRepository;
    private final JulesApiClient julesApiClient;

    @Value("${jules.session-cleanup-batch-size:30}")
    private int cleanupBatchSize;

    public SessionLifecycleService(JulesSessionRepository julesSessionRepository,
                                    AccountRepository accountRepository,
                                    TaskRepository taskRepository,
                                    JulesApiClient julesApiClient) {
        this.julesSessionRepository = julesSessionRepository;
        this.accountRepository = accountRepository;
        this.taskRepository = taskRepository;
        this.julesApiClient = julesApiClient;
    }

    /**
     * Shared low-level primitive - the single choke point every "this session is done" call site should
     * use (JulesDispatchService.cancelSession, BranchGarbageCollectorService's stagnation retirement).
     * Deliberately does NOT touch task state - callers keep their own, legitimately different task-side
     * consequences (cancelSession fails/releases the task; Branch GC re-queues it) - this only makes sure
     * the SESSION itself is consistently retired, locally and on Jules's side, however it got here.
     */
    @Transactional
    public void retireSessionOnly(UUID sessionId, String reason) {
        JulesSessionEntity session = julesSessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return;
        }
        if (!"cancelled".equals(session.getStatus())) {
            session.setStatus("cancelled");
            session.setClosedAt(Instant.now());
            session.setClosureReason(reason);
            julesSessionRepository.save(session);
        }
        deleteRemote(session);
    }

    private void deleteRemote(JulesSessionEntity session) {
        if (session.getRemoteDeletedAt() != null) {
            return; // already confirmed gone
        }
        if (session.getExternalSessionId() == null || "skipped".equals(session.getExternalSessionId())) {
            return;
        }
        if (session.getAccountId() == null) {
            return;
        }
        AccountEntity account = accountRepository.findById(session.getAccountId()).orElse(null);
        if (account == null || account.getApiKey() == null || account.getApiKey().isBlank()) {
            return;
        }

        var result = julesApiClient.deleteSession(session.getExternalSessionId(), account.getApiKey());
        // A 404 here means Jules already considers it gone (deleted earlier through some other path, or
        // expired on its own) - equally good evidence of "really gone" as a fresh 200.
        if (result.success() || result.statusCode() == 404) {
            session.setRemoteDeletedAt(Instant.now());
            julesSessionRepository.save(session);
        } else {
            log.warn("SessionLifecycleService: failed to delete Jules session {} remotely: status={} body={}",
                    session.getExternalSessionId(), result.statusCode(), result.errorBody());
        }
    }

    /**
     * Periodic batch sweep - two triggers: the session's task reached a terminal status, or its whole
     * project closed. Batched (not all at once) - Jules's real rate limits for this endpoint aren't
     * documented, so this stays conservative rather than assuming it's safe to hammer.
     */
    @Scheduled(fixedRateString = "${jules.session-cleanup-rate-ms:1800000}")
    @Transactional
    public int cleanupEligibleSessions() {
        List<JulesSessionEntity> candidates = julesSessionRepository.findByRemoteDeletedAtIsNullAndExternalSessionIdIsNotNull();
        int processed = 0;
        for (JulesSessionEntity session : candidates) {
            if (processed >= cleanupBatchSize) {
                break;
            }
            TaskEntity task = session.getTaskId() == null ? null : taskRepository.findById(session.getTaskId()).orElse(null);
            if (task == null) {
                continue;
            }
            boolean taskTerminal = TERMINAL_TASK_STATUSES.contains(task.getStatus());
            ProjectEntity project = task.getProject();
            boolean projectClosed = project != null && CLOSED_PROJECT_STATUSES.contains(project.getStatus());
            if (!taskTerminal && !projectClosed) {
                continue;
            }
            deleteRemote(session);
            processed++;
        }
        if (processed > 0) {
            log.info("SessionLifecycleService: processed {} eligible session(s) for remote cleanup this cycle", processed);
        }
        return processed;
    }
}
