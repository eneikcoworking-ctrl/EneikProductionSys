package com.eneik.production.repositories;

import com.eneik.production.models.persistence.FeatureThreadEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeatureThreadRepository extends JpaRepository<FeatureThreadEntity, UUID> {
    Optional<FeatureThreadEntity> findByProjectIdAndFeatureId(UUID projectId, UUID featureId);
    List<FeatureThreadEntity> findByProjectIdAndMergedToMainAtIsNullAndAbandonedAtIsNull(UUID projectId);
    List<FeatureThreadEntity> findByProjectId(UUID projectId);
}
