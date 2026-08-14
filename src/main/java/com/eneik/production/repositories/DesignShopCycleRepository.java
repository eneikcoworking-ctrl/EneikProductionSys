package com.eneik.production.repositories;

import com.eneik.production.models.persistence.DesignShopCycleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DesignShopCycleRepository extends JpaRepository<DesignShopCycleEntity, UUID> {
    Optional<DesignShopCycleEntity> findByProjectId(UUID projectId);

    // 2026-08-14 (bug-hunt sweep, V98 migration): atomic compare-and-swap mutual-exclusion claim for
    // DesignShopOrchestrationService.startCycle - same primitive/reasoning as WishlistRepository.
    // compareAndSetStatus. The lastWasReady = false condition re-validates the readiness-edge decision
    // against the DB's current truth at claim time (defense in depth against a stale in-memory read),
    // not just against the claim flag alone.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE DesignShopCycleEntity c SET c.startCycleClaimedAt = :now "
            + "WHERE c.projectId = :projectId AND c.startCycleClaimedAt IS NULL AND c.lastWasReady = false")
    int claimStartCycle(@Param("projectId") UUID projectId, @Param("now") Instant now);

    // Releases a claim taken above - called when startCycle's Stitch generation doesn't succeed, so the
    // next tick can still retry while readiness remains true (same intent as the pre-existing "leave
    // lastWasReady=false" comment in startCycle - this claim must never turn a transient generation
    // failure into a permanently un-retryable project).
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE DesignShopCycleEntity c SET c.startCycleClaimedAt = NULL WHERE c.projectId = :projectId")
    void releaseStartCycleClaim(@Param("projectId") UUID projectId);
}
