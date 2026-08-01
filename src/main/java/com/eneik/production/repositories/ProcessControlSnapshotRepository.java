package com.eneik.production.repositories;

import com.eneik.production.models.persistence.ProcessControlSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProcessControlSnapshotRepository extends JpaRepository<ProcessControlSnapshotEntity, UUID> {
    List<ProcessControlSnapshotEntity> findByProjectIdAndStreamOrderBySequenceIndexAsc(UUID projectId, String stream);

    List<ProcessControlSnapshotEntity> findByProjectIdOrderBySequenceIndexAsc(UUID projectId);

    List<ProcessControlSnapshotEntity> findByFeatureIdAndStream(UUID featureId, String stream);
}
