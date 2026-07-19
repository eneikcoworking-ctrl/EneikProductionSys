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
}
