package com.eneik.production.repositories;

import com.eneik.production.models.persistence.EvidenceNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EvidenceNodeRepository extends JpaRepository<EvidenceNodeEntity, UUID> {
    List<EvidenceNodeEntity> findByProjectIdAndCreatedAtAfter(UUID projectId, Instant createdAfter);
    List<EvidenceNodeEntity> findByCreatedAtAfter(Instant createdAfter);
    List<EvidenceNodeEntity> findByProjectIdAndFeatureId(UUID projectId, UUID featureId);
    List<EvidenceNodeEntity> findByProjectIdAndPrNumber(UUID projectId, Integer prNumber);
}
