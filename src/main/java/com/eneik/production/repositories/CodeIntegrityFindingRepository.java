package com.eneik.production.repositories;

import com.eneik.production.models.persistence.CodeIntegrityFindingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface CodeIntegrityFindingRepository extends JpaRepository<CodeIntegrityFindingEntity, UUID> {
    List<CodeIntegrityFindingEntity> findByProjectId(UUID projectId);

    List<CodeIntegrityFindingEntity> findByFeatureId(UUID featureId);
}
