package com.eneik.production.repositories;

import com.eneik.production.dto.dashboard.QueueDashboardDto;
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

    long countByStatus(TaskStatus status);
    long countByProjectIdAndStatus(UUID projectId, TaskStatus status);
    List<TaskEntity> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    @Query("SELECT new com.eneik.production.dto.dashboard.QueueDashboardDto$TagCountDto(" +
           "t.role.tag, COUNT(t), 0L) " +
           "FROM TaskEntity t WHERE t.status = com.eneik.production.models.persistence.TaskStatus.queued " +
           "GROUP BY t.role.tag")
    List<QueueDashboardDto.TagCountDto> queuedGroupedByTag();

    @Query(value = "SELECT * FROM tasks " +
            "WHERE status = 'queued' AND tag IN (:capableTags) " +
            "ORDER BY created_at ASC " +
            "LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<TaskEntity> lockNextQueuedTask(@Param("capableTags") List<String> capableTags);

    @Query(value = "SELECT * FROM tasks " +
            "WHERE project_id = :projectId AND status = 'queued' " +
            "ORDER BY created_at ASC " +
            "LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<TaskEntity> lockNextQueuedTaskForProject(@Param("projectId") UUID projectId);

    @Query("SELECT new com.eneik.production.dto.dashboard.QueueDashboardDto$TagCountDto(" +
           "t.role.tag, COUNT(t), 0L) " +
           "FROM TaskEntity t WHERE t.project.id = :projectId " +
           "AND t.status = com.eneik.production.models.persistence.TaskStatus.queued " +
           "GROUP BY t.role.tag")
    List<QueueDashboardDto.TagCountDto> queuedGroupedByProjectAndTag(@Param("projectId") UUID projectId);
}
