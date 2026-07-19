package com.eneik.production.repositories;

import com.eneik.production.models.persistence.FeatureThreadEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeatureThreadRepository extends JpaRepository<FeatureThreadEntity, UUID> {
    Optional<FeatureThreadEntity> findByProjectIdAndFeatureId(UUID projectId, UUID featureId);
}
