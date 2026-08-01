package com.eneik.production.repositories;

import com.eneik.production.models.persistence.JulesSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JulesSessionRepository extends JpaRepository<JulesSessionEntity, UUID> {
    List<JulesSessionEntity> findByTaskId(UUID taskId);
    List<JulesSessionEntity> findByTaskIdIn(List<UUID> taskIds);
    List<JulesSessionEntity> findByStatus(String status);
    List<JulesSessionEntity> findByStatusIn(List<String> statuses);

    // 2026-08-01: SessionLifecycleService's cleanup-candidate pool - a real remote external session that
    // we haven't yet confirmed deleted. Task/project eligibility (terminal task, or closed project) is
    // filtered afterward in Java - this is a low-frequency batch job, not a hot path, so a simple fetch +
    // stream filter is preferred over a complex three-way join query.
    List<JulesSessionEntity> findByRemoteDeletedAtIsNullAndExternalSessionIdIsNotNull();
}
