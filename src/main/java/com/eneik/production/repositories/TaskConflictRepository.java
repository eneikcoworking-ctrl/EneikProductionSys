package com.eneik.production.repositories;

import com.eneik.production.models.persistence.TaskConflictEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface TaskConflictRepository extends JpaRepository<TaskConflictEntity, UUID> {
    Optional<TaskConflictEntity> findFirstByTaskIdAndResolutionStatus(UUID taskId, String resolutionStatus);
    List<TaskConflictEntity> findByResolutionStatusNot(String resolutionStatus);
    List<TaskConflictEntity> findByResolutionStatusIsNull();
    long countByDetectedAtAfter(Instant detectedAt);

    @EntityGraph(attributePaths = "task")
    @Query("select c from TaskConflictEntity c where c.resolutionStatus is null or c.resolutionStatus <> :resolutionStatus")
    List<TaskConflictEntity> findActiveByResolutionStatusNot(@Param("resolutionStatus") String resolutionStatus);

    @Query("select c.conflictType as name, count(c) as defects from TaskConflictEntity c group by c.conflictType")
    List<ParetoRow> countByConflictType();

    @Query("select c.resolutionStatus as name, count(c) as defects from TaskConflictEntity c group by c.resolutionStatus")
    List<ParetoRow> countByResolutionStatus();

    /** Pushed down from AutoMergeService (2026-08-28): it read the whole table each 60s tick. */
    List<TaskConflictEntity> findByResolutionStatusIgnoreCase(String resolutionStatus);
    List<TaskConflictEntity> findByTaskIdIn(List<UUID> taskIds);

    interface ParetoRow {
        String getName();
        long getDefects();
    }
}
