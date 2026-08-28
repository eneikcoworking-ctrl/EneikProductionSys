package com.eneik.production.repositories;

import com.eneik.production.models.persistence.TaskConflictEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface TaskConflictRepository extends JpaRepository<TaskConflictEntity, UUID> {
    Optional<TaskConflictEntity> findFirstByTaskIdAndResolutionStatus(UUID taskId, String resolutionStatus);
    List<TaskConflictEntity> findByResolutionStatusNot(String resolutionStatus);
    List<TaskConflictEntity> findByResolutionStatusIsNull();

    /** Pushed down from AutoMergeService (2026-08-28): it read the whole table each 60s tick. */
    List<TaskConflictEntity> findByResolutionStatusIgnoreCase(String resolutionStatus);
    List<TaskConflictEntity> findByTaskIdIn(List<UUID> taskIds);
}
