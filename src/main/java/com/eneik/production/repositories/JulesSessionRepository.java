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

    // 2026-08-01: SessionLifecycleService's cleanup-candidate pool - a real remote external session that
    // we haven't yet confirmed deleted. Task/project eligibility (terminal task, or closed project) is
    // filtered afterward in Java - this is a low-frequency batch job, not a hot path, so a simple fetch +
    // stream filter is preferred over a complex three-way join query.
    List<JulesSessionEntity> findByRemoteDeletedAtIsNullAndExternalSessionIdIsNotNull();

    // 2026-08-14 (bug-hunt sweep, V97 migration): atomic compare-and-swap mutual-exclusion claim for
    // JulesDispatchService.handlePrOpenedWorkflow - same primitive/reasoning as WishlistRepository.
    // compareAndSetStatus. Only one concurrent caller's UPDATE can match the NULL predicate for a given
    // session id; the loser's call affects 0 rows.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE JulesSessionEntity s SET s.prOpenedWorkflowClaimedAt = :now WHERE s.id = :id AND s.prOpenedWorkflowClaimedAt IS NULL")
    int claimPrOpenedWorkflow(@Param("id") UUID id, @Param("now") Instant now);

    // Releases a claim taken above - called on failure (see handlePrOpenedWorkflow's try/catch) so a
    // legitimate retry (reconcileStrandedPrOpenedWorkflows' crash-recovery replay) is never permanently
    // blocked by a claim from an attempt that itself never finished.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE JulesSessionEntity s SET s.prOpenedWorkflowClaimedAt = NULL WHERE s.id = :id")
    void releasePrOpenedWorkflowClaim(@Param("id") UUID id);
}
