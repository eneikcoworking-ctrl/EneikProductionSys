package com.eneik.production.repositories;

import com.eneik.production.models.persistence.PrReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrReviewRepository extends JpaRepository<PrReviewEntity, UUID> {
    Optional<PrReviewEntity> findFirstByJulesSessionIdAndPrUrlOrderByCreatedAtDesc(UUID julesSessionId, String prUrl);
    boolean existsByJulesSessionId(UUID julesSessionId);
    boolean existsByJulesSessionIdInAndMergedTrue(java.util.List<UUID> julesSessionIds);
    java.util.List<PrReviewEntity> findByJulesSessionIdInAndMergedTrue(java.util.List<UUID> julesSessionIds);
    // Testimony-vs-evidence (2026-07-25, live incident): a session's own self-reported status ("failed")
    // can lag or contradict the real evidence that its PR is still open and unmerged - see
    // JulesDispatchService.latestOpenPrSession.
    java.util.List<PrReviewEntity> findByJulesSessionId(UUID julesSessionId);
    java.util.List<PrReviewEntity> findByMergedFalseOrMergedIsNull();

    // Added 2026-08-28 with the query-cost work in AutoMergeService, which had been calling all three of
    // these against an interface that declared none of them - the caller was changed and the repository
    // was not, so nothing compiled. Each one carries the predicate its single caller used to apply in
    // Java after loading the whole table:
    //   reconcileMergedTaskOutcomes  - merged rows that have a session
    //   the done-task repair loop    - rows for a known set of sessions
    //   the session-to-review map    - rows that have a session at all
    // Charter: the cost of a question is proportional to its answer, not to the accumulated history.
    java.util.List<PrReviewEntity> findByMergedTrueAndJulesSessionIdIsNotNull();
    java.util.List<PrReviewEntity> findByJulesSessionIdIn(java.util.List<UUID> julesSessionIds);
    java.util.List<PrReviewEntity> findByJulesSessionIdIsNotNull();
    // FalsificationCycleService.resolveTaskForPrNumber filtered the whole table down to one PR number.
    java.util.List<PrReviewEntity> findByPrNumber(Integer prNumber);
}
