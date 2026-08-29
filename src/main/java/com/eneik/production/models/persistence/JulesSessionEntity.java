package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jules_sessions")
public class JulesSessionEntity {

    /**
     * The statuses in which a session is still doing something (2026-08-29, plan §4.29). This is the one
     * definition of "live" for a session; JulesDispatchService.ACTIVE_SESSION_STATUSES delegates here, and
     * every reader that asks whether a session's evidence is still current asks {@link #isActive()}.
     *
     * <p>It used to be asked in three different ways. Dispatch asked this set; FlowSpineService and
     * OperationalTruthService each carried their own copy of {@code SUPERSEDED_SESSION_STATUS =
     * "cancelled"} and read "live" as "not literally cancelled". Measured that day on the live database:
     * failed 658, closed_terminal_task 262, closed_no_code 117, loop_closed 51, cancelled 39, running 3 -
     * so that reading excluded 39 sessions of 1130 and counted every other finished one as live. The
     * consequence was measured too: PlannedWorkRecoveryService deliberately reuses the original task
     * identity when it revives a failed task, so the previous attempt's closed_unmerged review stayed
     * attached to a non-terminal task, kept failingReviews above zero, and put the project back into
     * BLOCKED_BY_REVIEW one minute after the new attempt had already been dispatched.
     *
     * <p>The knowledge those two copies carried is kept, and is the same knowledge: a session retired with
     * "cancelled" by Branch GC or cancelSession is retired because its task is being fast-tracked to a
     * FRESH re-dispatch, not because the task reached a terminal status (2026-08-01, test-fortieth/PR#119),
     * so its review is evidence about a superseded attempt. Asking whether the session is active answers
     * that case and every other finished one at once, instead of naming one status out of six.
     */
    public static final java.util.Set<String> ACTIVE_STATUSES =
            java.util.Set.of("running", "queued", "revising", "pr_opened", "stuck");
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "external_session_id", length = 128)
    private String externalSessionId;

    @Column(nullable = false, length = 24)
    private String status = "queued";

    @Column(name = "pr_url", length = 256)
    private String prUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "last_status_check_at")
    private Instant lastStatusCheckAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Lob
    @Column(name = "closure_reason")
    private String closureReason;

    // Distinct from updatedAt, which Hibernate bumps on every save regardless of real change (see
    // preUpdate() below) - lastProgressAt only moves on a genuine forward-progress signal (a real status
    // transition, or a newly-seen agent question), so staleness checks keyed on it can actually detect a
    // session that Jules keeps reporting "RUNNING" on while nothing real is happening.
    @Column(name = "last_progress_at")
    private Instant lastProgressAt;

    // Consecutive poll cycles where the session's activity log was too large to scan (activitiesOverflow).
    @Column(name = "blind_cycle_count", nullable = false)
    private int blindCycleCount = 0;

    // Count of deterministic "decide for yourself and make a PR" messages sent by forceUnblockOverflowedSessions.
    @Column(name = "forced_unblock_attempts", nullable = false)
    private int forcedUnblockAttempts = 0;

    // 2026-08-01 (operator: "те сессии джулс, которые не продуктовые... можно убирать в архив или
    // удалять"): before this, our own "cancelled"/terminal session status was purely local fiction - Jules
    // itself was never told a session is done (confirmed live via JulesApiClient.deleteSession +
    // checkSessionRaw: DELETE returned 200, a follow-up GET then returned a real 404). Non-null here means
    // SessionLifecycleService confirmed the real remote deletion happened, not just that we attempted it -
    // testimony vs evidence, same discipline as everywhere else in this system.
    @Column(name = "remote_deleted_at")
    private Instant remoteDeletedAt;

    // 2026-08-03 (blind-cycle incident, test-forty-first): the last Jules activities.list nextPageToken
    // this session has fully scanned through. Null means "never scanned under the incremental-walk scheme
    // yet, start from page 1" - see JulesDispatchService.answerAgentQuestions. Without this, every poll
    // re-requested Jules's own default (oldest-first) page forever, so a long-running session's recent
    // activity was never actually looked at.
    @Column(name = "activities_page_cursor", length = 128)
    private String activitiesPageCursor;

    // 2026-08-14 (bug-hunt sweep): atomic mutual-exclusion claim for JulesDispatchService.
    // handlePrOpenedWorkflow - see V97 migration for the live double-processing race this closes. NULL
    // means available (including for legitimate crash-recovery retry); non-null means a concurrent
    // invocation is (or, if stale, recently was) processing this session's pr_opened completion.
    @Column(name = "pr_opened_workflow_claimed_at")
    private Instant prOpenedWorkflowClaimedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }

    public UUID getTaskId() { return taskId; }
    public void setTaskId(UUID taskId) { this.taskId = taskId; }

    public String getExternalSessionId() { return externalSessionId; }
    public void setExternalSessionId(String externalSessionId) { this.externalSessionId = externalSessionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPrUrl() { return prUrl; }
    public void setPrUrl(String prUrl) { this.prUrl = prUrl; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getLastStatusCheckAt() { return lastStatusCheckAt; }
    public void setLastStatusCheckAt(Instant lastStatusCheckAt) { this.lastStatusCheckAt = lastStatusCheckAt; }

    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }

    public String getClosureReason() { return closureReason; }
    public void setClosureReason(String closureReason) { this.closureReason = closureReason; }

    public Instant getLastProgressAt() { return lastProgressAt; }
    public void setLastProgressAt(Instant lastProgressAt) { this.lastProgressAt = lastProgressAt; }

    public int getBlindCycleCount() { return blindCycleCount; }
    public void setBlindCycleCount(int blindCycleCount) { this.blindCycleCount = blindCycleCount; }

    public int getForcedUnblockAttempts() { return forcedUnblockAttempts; }
    public void setForcedUnblockAttempts(int forcedUnblockAttempts) { this.forcedUnblockAttempts = forcedUnblockAttempts; }

    public Instant getRemoteDeletedAt() { return remoteDeletedAt; }
    public void setRemoteDeletedAt(Instant remoteDeletedAt) { this.remoteDeletedAt = remoteDeletedAt; }

    public String getActivitiesPageCursor() { return activitiesPageCursor; }
    public void setActivitiesPageCursor(String activitiesPageCursor) { this.activitiesPageCursor = activitiesPageCursor; }

    public Instant getPrOpenedWorkflowClaimedAt() { return prOpenedWorkflowClaimedAt; }
    public void setPrOpenedWorkflowClaimedAt(Instant prOpenedWorkflowClaimedAt) { this.prOpenedWorkflowClaimedAt = prOpenedWorkflowClaimedAt; }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    /** Whether this session is still doing something - the single definition, see {@link #ACTIVE_STATUSES}. */
    public boolean isActive() {
        return status != null && ACTIVE_STATUSES.contains(status);
    }
}
