package com.eneik.production.repositories;

import com.eneik.production.models.persistence.CoherenceRunNodeResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CoherenceRunNodeResultRepository extends JpaRepository<CoherenceRunNodeResultEntity, UUID> {
    List<CoherenceRunNodeResultEntity> findByCoherenceRunId(UUID coherenceRunId);
    List<CoherenceRunNodeResultEntity> findByEvidenceNodeId(UUID evidenceNodeId);
}
