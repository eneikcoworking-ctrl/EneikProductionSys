package com.eneik.production.repositories;

import com.eneik.production.models.persistence.GeminiObserverActionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GeminiObserverActionRepository extends JpaRepository<GeminiObserverActionEntity, UUID> {
    List<GeminiObserverActionEntity> findTop5ByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
