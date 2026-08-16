package com.eneik.production.repositories;

import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface WishlistRepository extends JpaRepository<WishlistEntity, UUID> {
    // Live incident, 2026-07-24 (operator: "когда ты уже починишь окончательно дубли"): the compiler
    // batch-admission path used to be read-then-write - load candidates still `pending`, decide which to
    // admit, THEN separately flip each to `compiling` via a plain save(). Two overlapping calls to
    // dispatchBatchedWishlistCompiler for the same project (confirmed live: the coverage-audit self-loop,
    // now fixed, fired admission checks rapidly enough to overlap a normal ~60s orchestration tick) could
    // both load the SAME wishlist while it was still `pending`, both decide to admit it, and both dispatch
    // their own independent compiler session against identical content - confirmed live producing PR#56 and
    // PR#57, byte-identical "Implement Daily Outbound Messaging Rate Limiter" work. This is a real
    // compare-and-swap: only the FIRST caller to reach this update for a given wishlist gets affectedRows=1
    // and may proceed; every later concurrent caller gets 0 and must skip that wishlist, no matter how far
    // along its own in-memory admission decision already was.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE WishlistEntity w SET w.status = :newStatus WHERE w.id = :id AND w.status = :expectedStatus")
    int compareAndSetStatus(@Param("id") UUID id, @Param("expectedStatus") WishlistStatus expectedStatus,
            @Param("newStatus") WishlistStatus newStatus);

    List<WishlistEntity> findByProjectId(UUID projectId);
    List<WishlistEntity> findByProjectIdAndStatus(UUID projectId, WishlistStatus status);
    long countByProjectIdAndStatus(UUID projectId, WishlistStatus status);
    boolean existsByProjectIdAndSource(UUID projectId, WishlistSource source);
    boolean existsByProjectIdAndSourceRoleTagAndSourceAndCreatedAtAfter(
            UUID projectId, String sourceRoleTag, WishlistSource source, Instant after);
    long countByProjectIdAndSourceAndSourceRoleTagAndStatus(
            UUID projectId, WishlistSource source, String sourceRoleTag, WishlistStatus status);
    long countByProjectIdAndStatusAndContentStartingWith(UUID projectId, WishlistStatus status, String contentPrefix);
    List<WishlistEntity> findByProjectIdAndStatusAndContentStartingWith(
            UUID projectId, WishlistStatus status, String contentPrefix);
    long countByProjectIdAndSourceAndStatus(UUID projectId, WishlistSource source, WishlistStatus status);
    List<WishlistEntity> findByProjectIdAndSourceAndStatus(UUID projectId, WishlistSource source, WishlistStatus status);

    // Semantic-duplication guard (2026-07-24): the "live" set for WishlistContentSimilarityMatcher - every
    // status except `dismissed` (a dismissed row never led to any real work, safe to ignore). Deliberately
    // INCLUDES converted_to_task, unlike the pending-only scope the pre-existing substring checks used -
    // a requirement that was already successfully turned into a task is exactly the case a later audit
    // re-identifying "the same" gap needs to be compared against, not just other still-pending candidates.
    List<WishlistEntity> findByProjectIdAndSourceAndStatusIn(UUID projectId, WishlistSource source, List<WishlistStatus> statuses);

    // Feature-thread closeout (2026-07-24): is any wishlist for this feature still being turned into a
    // task? See ClientDeliverableReadinessService.isFeatureReadyForCloseout.
    List<WishlistEntity> findByFeatureId(UUID featureId);

    // Idempotency check for DesignSystemFalsificationService (2026-08-04, Phase B): has this epic already
    // had a design-system pass recorded, so the per-epic cron never reapplies one twice.
    boolean existsByFeatureIdAndSource(UUID featureId, WishlistSource source);
}
