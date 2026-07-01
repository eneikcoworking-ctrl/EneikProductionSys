package com.eneik.production.repositories;

import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {
    List<TaskEntity> findByStatusAndRoleTag(TaskStatus status, String tag);

    @Query(value = "SELECT * FROM tasks " +
            "WHERE status = 'queued' AND tag IN (:capableTags) " +
            "ORDER BY created_at ASC " +
            "LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<TaskEntity> lockNextQueuedTask(@Param("capableTags") List<String> capableTags);

    long countByStatus(TaskStatus status);
}
