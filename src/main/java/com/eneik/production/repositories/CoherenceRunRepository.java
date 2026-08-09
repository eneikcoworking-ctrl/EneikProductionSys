package com.eneik.production.repositories;

import com.eneik.production.models.persistence.CoherenceRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CoherenceRunRepository extends JpaRepository<CoherenceRunEntity, UUID> {
    List<CoherenceRunEntity> findByProjectIdOrderByRanAtDesc(UUID projectId);
}
