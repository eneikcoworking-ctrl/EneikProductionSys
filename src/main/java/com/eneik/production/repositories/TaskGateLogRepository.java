package com.eneik.production.repositories;

import com.eneik.production.models.persistence.TaskGateLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

@Repository
public interface TaskGateLogRepository extends JpaRepository<TaskGateLogEntity, UUID> {
    List<TaskGateLogEntity> findByTaskIdOrderByCreatedAtDesc(UUID taskId);
}
