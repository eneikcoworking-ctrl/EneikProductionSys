package com.eneik.production.repositories;

import com.eneik.production.models.persistence.GeminiObserverJournalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GeminiObserverJournalRepository extends JpaRepository<GeminiObserverJournalEntity, UUID> {
    // Bounded continuity window (2026-07-25 redesign) - Gemini's own last few notes, not the whole
    // journal history, so continuity stays cheap regardless of how long the project has been observed.
    List<GeminiObserverJournalEntity> findTop5ByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
