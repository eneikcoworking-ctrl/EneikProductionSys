package com.eneik.production.repositories;

import com.eneik.production.models.persistence.OperationalRealityFindingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OperationalRealityFindingRepository extends JpaRepository<OperationalRealityFindingEntity, UUID> {
    List<OperationalRealityFindingEntity> findByTaskId(UUID taskId);

    // Engineering invariant #14 (2026-08-08): batched lookup so GeminiProjectObserverService's
    // stuck-candidate detection can fold in "belief revised recently" evidence for a whole project's task
    // list in one query, instead of one findByTaskId call per task.
    List<OperationalRealityFindingEntity> findByTaskIdInAndDetectedAtAfter(List<UUID> taskIds, Instant after);
}
