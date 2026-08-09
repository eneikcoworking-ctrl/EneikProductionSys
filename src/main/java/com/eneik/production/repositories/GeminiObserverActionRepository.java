package com.eneik.production.repositories;

import com.eneik.production.models.persistence.GeminiObserverActionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface GeminiObserverActionRepository extends JpaRepository<GeminiObserverActionEntity, UUID> {
    // 2026-08-08: same audit purpose as GeminiObserverJournalRepository's time-window query - lets a real
    // incident window be checked directly against what she actually DID, not just her journal prose.
    List<GeminiObserverActionEntity> findByProjectIdAndCreatedAtBetweenOrderByCreatedAtAsc(
            UUID projectId, Instant from, Instant to);
    List<GeminiObserverActionEntity> findTop5ByProjectIdOrderByCreatedAtDesc(UUID projectId);

    // Mandatory follow-up gate (2026-07-30): an action she took whose outcome she has not yet been shown
    // in a real cycle. Existence alone is reason enough to force a real call next cycle, regardless of
    // whether anything else in the project changed.
    boolean existsByProjectIdAndVerifiedFalse(UUID projectId);
    List<GeminiObserverActionEntity> findByProjectIdAndVerifiedFalse(UUID projectId);
}
