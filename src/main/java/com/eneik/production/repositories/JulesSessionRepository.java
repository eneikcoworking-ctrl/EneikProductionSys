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
}
