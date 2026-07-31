package com.eneik.production.repositories;

import com.eneik.production.models.persistence.GeminiObserverJournalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GeminiObserverJournalRepository extends JpaRepository<GeminiObserverJournalEntity, UUID> {
    // Bounded continuity window (2026-07-25 redesign) - Gemini's own last few notes, not the whole
    // journal history, so continuity stays cheap regardless of how long the project has been observed.
    // Mixes real entries and skip markers (2026-07-30) - correct for stagnation-ratio and anomaly-
    // fingerprint history, which must keep accumulating even across silent skip cycles.
    List<GeminiObserverJournalEntity> findTop5ByProjectIdOrderByCreatedAtDesc(UUID projectId);

    // 2026-07-30: real-only counterparts, for her own continuity text and "since my last visit" - a run of
    // skip markers must never make it look like she was only just here, or truncate what she actually needs
    // to catch up on once a real cycle finally fires.
    List<GeminiObserverJournalEntity> findTop5ByProjectIdAndGeminiCalledTrueOrderByCreatedAtDesc(UUID projectId);
    Optional<GeminiObserverJournalEntity> findFirstByProjectIdAndGeminiCalledTrueOrderByCreatedAtDesc(UUID projectId);
}
