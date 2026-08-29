package com.eneik.production.repositories;

import com.eneik.production.models.persistence.JulesSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface JulesSessionRepository extends JpaRepository<JulesSessionEntity, UUID> {
    List<JulesSessionEntity> findByTaskId(UUID taskId);
    List<JulesSessionEntity> findByTaskIdIn(List<UUID> taskIds);
    List<JulesSessionEntity> findByStatus(String status);
    List<JulesSessionEntity> findByStatusIn(List<String> statuses);

    /** Pushed down from AutoMergeService, which loaded every session four times per 60s tick. */
    List<JulesSessionEntity> findByPrUrlIn(List<String> prUrls);

    List<JulesSessionEntity> findByExternalSessionIdIsNotNull();

    // 2026-08-01: SessionLifecycleService's cleanup-candidate pool - a real remote external session that
    // we haven't yet confirmed deleted. Task/project eligibility (terminal task, or closed project) is
    // filtered afterward in Java - this is a low-frequency batch job, not a hot path, so a simple fetch +
    // stream filter is preferred over a complex three-way join query.
    List<JulesSessionEntity> findByRemoteDeletedAtIsNullAndExternalSessionIdIsNotNull();

    // 2026-08-25: Monotonic Epoch Lease with bounded temporal window (tau_lease).
    // An unfinished attempt or crashed thread must NEVER permanently deadlock the state machine (Liveness invariant).
    // The claim succeeds if the lease is either unheld (NULL) or expired (< staleThreshold).
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE JulesSessionEntity s SET s.prOpenedWorkflowClaimedAt = :now WHERE s.id = :id AND (s.prOpenedWorkflowClaimedAt IS NULL OR s.prOpenedWorkflowClaimedAt < :staleThreshold)")
    int claimPrOpenedWorkflow(@Param("id") UUID id, @Param("now") Instant now, @Param("staleThreshold") Instant staleThreshold);

    // Releases a claim taken above - called on failure (see handlePrOpenedWorkflow's try/catch) so a
    // legitimate retry (reconcileStrandedPrOpenedWorkflows' crash-recovery replay) is never permanently
    // blocked by a claim from an attempt that itself never finished.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE JulesSessionEntity s SET s.prOpenedWorkflowClaimedAt = NULL WHERE s.id = :id")
    void releasePrOpenedWorkflowClaim(@Param("id") UUID id);

    // §12: the observed point at which Jules refused for capacity. A count, not a read - the answer is one
    // number and the question must not cost the table (the invariant ScheduledQueryCostInvariantTest guards).
    long countByAccountIdAndStatusIn(java.util.UUID accountId, java.util.List<String> statuses);

    // 2026-08-29, action plan 4.1: how many times Jules refused to create a session for this task at all.
    // dispatchInternal writes exactly one row per attempt, and on refusal that row keeps a NULL
    // external_session_id with status 'failed' - so this count IS the number of attempts that produced
    // nothing, already persisted, with no new column. It is monotone: session rows are deleted only on
    // project reset (which deletes the tasks with them) and for the 'skipped' placeholder, never as a side
    // effect of the dispatch loop itself, which is what invariant 7 requires of a mark a loop is bounded
    // by. A count, not a read - the answer is one number and must not cost the table.
    long countByTaskIdAndExternalSessionIdIsNullAndStatus(UUID taskId, String status);

    // 2026-08-29, action plan 4.2: the last moment the channel demonstrably accepted a session for this
    // project. An external session id is written only when Jules actually created one, so this is the
    // channel's own record that it was live - not the dispatcher's testimony that it tried. 'skipped' is
    // excluded because it is the placeholder written when the Jules integration is switched off, which is
    // the opposite of the event this asks about. An aggregate, not a read: one row comes back however much
    // history stands behind it, so the cost is proportional to the answer.
    // 2026-08-29, action plan 4.10. Scoped to ONE account, deliberately. The project-wide form this
    // replaced advanced on any accepted session anywhere in the project, and six healthy accounts kept
    // advancing it while the compiler's own account had accepted nothing since 28.08 21:36 - so an
    // exhausted brief's budget was restored forever and the compiler cycle repeated every ~13 minutes with
    // fourteen refusals in it. The condition that stopped a brief from reaching the compiler is the
    // compiler account's availability, so that is the channel the evidence has to come from. Any accepted
    // session on that account counts, not only a compiler one: requiring a compiler session would deadlock
    // (no budget, no compiler task; no compiler task, no compiler session; no session, no evidence).
    @Query("SELECT MAX(s.createdAt) FROM JulesSessionEntity s, AccountEntity a "
            + "WHERE a.id = s.accountId AND a.name = :accountName "
            + "AND s.externalSessionId IS NOT NULL AND s.externalSessionId <> 'skipped'")
    Instant latestAcceptedSessionAtForAccount(@Param("accountName") String accountName);
}
