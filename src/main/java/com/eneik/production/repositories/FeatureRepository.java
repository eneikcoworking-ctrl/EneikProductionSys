package com.eneik.production.repositories;

import com.eneik.production.models.persistence.FeatureEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FeatureRepository extends JpaRepository<FeatureEntity, UUID> {
    List<FeatureEntity> findByProjectId(UUID projectId);
}
