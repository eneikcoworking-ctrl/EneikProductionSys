package com.eneik.production.repositories;

import com.eneik.production.models.persistence.JulesSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JulesSessionRepository extends JpaRepository<JulesSessionEntity, UUID> {
    List<JulesSessionEntity> findByTaskId(UUID taskId);
    List<JulesSessionEntity> findByStatus(String status);
    List<JulesSessionEntity> findByStatusIn(List<String> statuses);
}
