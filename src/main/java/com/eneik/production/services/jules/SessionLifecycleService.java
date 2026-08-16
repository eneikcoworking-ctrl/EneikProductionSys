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
 * Sole owner of "does Jules itself know this session is done" - 2026-08-01 (operator: "those Jules sessions that
 * are technical rather than product ones can be archived or deleted"). Confirmed live against
 * Jules's real API (https://jules.google/docs/api/reference/sessions/): DELETE /v1alpha/sessions/{session}
 * genuinely removes the session server-side (a follow-up GET returns a real 404, not just a locally-assumed
 * success) - before this, our own "cancelled" convention was purely local fiction, Jules was never told.
 *
 * Two triggers: a task reaching a terminal status, or its whole project closing - matching exactly what the
 * operator described doing by hand ("I was manually retiring many active sessions from a closed project").
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

    // Self-injected proxy reference (2026-08-14, same pattern/reason as JulesDispatchService.self and
    // ProjectFlowService.self): a plain `this.xxx(...)` call bypasses the Spring AOP proxy entirely, so
    // @Transactional(REQUIRES_NEW) on prepareRemoteDeleteContext/recordRemoteDeleted/applyLocalCancelStatus
    // would silently never activate. @Lazy breaks the constructor circular dependency this would otherwise
    // create.
    private final SessionLifecycleService self;

    @Value("${jules.session-cleanup-batch-size:30}")
    private int cleanupBatchSize;

    public SessionLifecycleService(JulesSessionRepository julesSessionRepository,
                                    AccountRepository accountRepository,
                                    TaskRepository taskRepository,
                                    JulesApiClient julesApiClient,
                                    @org.springframework.context.annotation.Lazy SessionLifecycleService self) {
        this.julesSessionRepository = julesSessionRepository;
        this.accountRepository = accountRepository;
        this.taskRepository = taskRepository;
        this.julesApiClient = julesApiClient;
        this.self = self;
    }

    /**
     * Shared low-level primitive - the single choke point every "this session is done" call site should
     * use (JulesDispatchService.cancelSession, BranchGarbageCollectorService's stagnation retirement).
     * Deliberately does NOT touch task state - callers keep their own, legitimately different task-side
     * consequences (cancelSession fails/releases the task; Branch GC re-queues it) - this only makes sure
     * the SESSION itself is consistently retired, locally and on Jules's side, however it got here.
     */
    // 2026-08-14 (bug-hunt sweep): this and deleteRemote used to run as one @Transactional method/call
    // chain, holding a DB transaction open across the real julesApiClient.deleteSession HTTP call below.
    // Same bug class as the 2026-08-07 lock-timeout incident. Split into: a short REQUIRES_NEW transaction
    // for the local status write, a short REQUIRES_NEW transaction to gather what the network call needs,
    // the real network call with NO transaction open, then a short REQUIRES_NEW transaction to record the
    // result - no step holds a connection across the HTTP round trip.
    public void retireSessionOnly(UUID sessionId, String reason) {
        self.applyLocalCancelStatus(sessionId, reason);
        deleteRemote(sessionId);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void applyLocalCancelStatus(UUID sessionId, String reason) {
        JulesSessionEntity session = julesSessionRepository.findById(sessionId).orElse(null);
        if (session == null || "cancelled".equals(session.getStatus())) {
            return;
        }
        session.setStatus("cancelled");
        session.setClosedAt(Instant.now());
        session.setClosureReason(reason);
        julesSessionRepository.save(session);
    }

    private record RemoteDeleteContext(String externalSessionId, String apiKey) {
    }

    private void deleteRemote(UUID sessionId) {
        RemoteDeleteContext context = self.prepareRemoteDeleteContext(sessionId);
        if (context == null) {
            return;
        }
        var result = julesApiClient.deleteSession(context.externalSessionId(), context.apiKey());
        // A 404 here means Jules already considers it gone (deleted earlier through some other path, or
        // expired on its own) - equally good evidence of "really gone" as a fresh 200.
        if (result.success() || result.statusCode() == 404) {
            self.recordRemoteDeleted(sessionId);
        } else {
            log.warn("SessionLifecycleService: failed to delete Jules session {} remotely: status={} body={}",
                    context.externalSessionId(), result.statusCode(), result.errorBody());
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public RemoteDeleteContext prepareRemoteDeleteContext(UUID sessionId) {
        JulesSessionEntity session = julesSessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.getRemoteDeletedAt() != null) {
            return null; // gone, or already confirmed gone
        }
        if (session.getExternalSessionId() == null || "skipped".equals(session.getExternalSessionId())) {
            return null;
        }
        if (session.getAccountId() == null) {
            return null;
        }
        AccountEntity account = accountRepository.findById(session.getAccountId()).orElse(null);
        if (account == null || account.getApiKey() == null || account.getApiKey().isBlank()) {
            return null;
        }
        return new RemoteDeleteContext(session.getExternalSessionId(), account.getApiKey());
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordRemoteDeleted(UUID sessionId) {
        JulesSessionEntity session = julesSessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return;
        }
        session.setRemoteDeletedAt(Instant.now());
        julesSessionRepository.save(session);
    }

    /**
     * Periodic batch sweep - two triggers: the session's task reached a terminal status, or its whole
     * project closed. Batched (not all at once) - Jules's real rate limits for this endpoint aren't
     * documented, so this stays conservative rather than assuming it's safe to hammer.
     */
    // 2026-08-14 (bug-hunt sweep): no longer @Transactional at this level - it used to hold one DB
    // transaction open across up to cleanupBatchSize sequential real julesApiClient.deleteSession HTTP
    // calls. julesSessionRepository/taskRepository reads below are each independently transactional
    // (Spring Data JPA default), and TaskEntity.project is an eager @ManyToOne so it's already loaded by
    // the time findById returns - safe to read outside a transaction. deleteRemote's own short REQUIRES_NEW
    // spans (via self) handle the actual writes.
    @Scheduled(fixedRateString = "${jules.session-cleanup-rate-ms:1800000}")
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
            deleteRemote(session.getId());
            processed++;
        }
        if (processed > 0) {
            log.info("SessionLifecycleService: processed {} eligible session(s) for remote cleanup this cycle", processed);
        }
        return processed;
    }
}
