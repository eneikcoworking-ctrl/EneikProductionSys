package com.eneik.production.repositories;

import com.eneik.production.models.persistence.EvidenceNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EvidenceNodeRepository extends JpaRepository<EvidenceNodeEntity, UUID> {
    List<EvidenceNodeEntity> findByProjectId(UUID projectId);
    List<EvidenceNodeEntity> findByProjectIdAndCreatedAtAfter(UUID projectId, Instant createdAfter);
    List<EvidenceNodeEntity> findByCreatedAtAfter(Instant createdAfter);
    List<EvidenceNodeEntity> findByProjectIdAndFeatureId(UUID projectId, UUID featureId);
    List<EvidenceNodeEntity> findByProjectIdAndPrNumber(UUID projectId, Integer prNumber);

    /**
     * The evidence nodes derived from one operational-reality finding. Used by DeliveryRealityProducerService
     * to keep a STANDING condition's evidence current without adding rows: both readers of the graph select
     * by createdAt after a window start, so a node reporting a condition that is still true must be
     * refreshed rather than left to age out. Refreshing the same row also preserves the observer's tool-loop
     * termination signal, which counts unseen node IDs - a new node every sweep would defeat it.
     */
    List<EvidenceNodeEntity> findByOperationalRealityFindingId(UUID operationalRealityFindingId);
}
