package com.eneik.production.repositories;

import com.eneik.production.models.persistence.FeatureEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FeatureRepository extends JpaRepository<FeatureEntity, UUID> {
    List<FeatureEntity> findByProjectId(UUID projectId);

    // 2026-08-04 (3-layer model): "active" readiness/dashboard views must exclude soft-dismissed features
    // (see deleteValuelessEpicsForProject) - the row itself survives for lineage (originFeatureId), just
    // stops counting toward what the client/product layer sees as in-progress work.
    List<FeatureEntity> findByProjectIdAndDismissedAtIsNull(UUID projectId);
}
