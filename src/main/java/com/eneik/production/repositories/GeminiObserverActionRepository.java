package com.eneik.production.repositories;

import com.eneik.production.models.persistence.GeminiObserverActionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GeminiObserverActionRepository extends JpaRepository<GeminiObserverActionEntity, UUID> {
    List<GeminiObserverActionEntity> findTop5ByProjectIdOrderByCreatedAtDesc(UUID projectId);

    // Mandatory follow-up gate (2026-07-30): an action she took whose outcome she has not yet been shown
    // in a real cycle. Existence alone is reason enough to force a real call next cycle, regardless of
    // whether anything else in the project changed.
    boolean existsByProjectIdAndVerifiedFalse(UUID projectId);
    List<GeminiObserverActionEntity> findByProjectIdAndVerifiedFalse(UUID projectId);
}
