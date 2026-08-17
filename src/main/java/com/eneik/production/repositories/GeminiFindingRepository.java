package com.eneik.production.repositories;

import com.eneik.production.models.persistence.GeminiFindingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface GeminiFindingRepository extends JpaRepository<GeminiFindingEntity, UUID> {

    List<GeminiFindingEntity> findByProjectIdAndCreatedAtAfter(UUID projectId, Instant after);

    /**
     * Platform-scope findings, which carry no projectId because the factory is not a project. Separate from
     * the project query rather than a null-argument variant of it: factory and project scope are different
     * types, and a null argument to a project query is the ambiguity that made factory-scope Kaizen
     * proposals unreadable (F68) until GET /api/kaizen/factory gave that scope its own route.
     */
    List<GeminiFindingEntity> findByProjectIdIsNullAndCreatedAtAfter(Instant after);
}
