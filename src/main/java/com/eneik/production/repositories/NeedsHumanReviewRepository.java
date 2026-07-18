package com.eneik.production.repositories;

import com.eneik.production.models.persistence.NeedsHumanReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface NeedsHumanReviewRepository extends JpaRepository<NeedsHumanReviewEntity, UUID> {
    boolean existsByTaskId(UUID taskId);
}
