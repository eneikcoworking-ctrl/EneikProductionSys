package com.eneik.production.repositories;

import com.eneik.production.models.persistence.ReviewConcernEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewConcernRepository extends JpaRepository<ReviewConcernEntity, UUID> {
    List<ReviewConcernEntity> findByFeatureId(UUID featureId);

    List<ReviewConcernEntity> findByProjectId(UUID projectId);
}
