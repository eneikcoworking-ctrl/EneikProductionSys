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
    List<TaskEntity> findByStatus(TaskStatus status);
    List<TaskEntity> findByStatusAndRoleTag(TaskStatus status, String tag);

    long countByStatus(TaskStatus status);
    long countByProjectIdAndStatus(UUID projectId, TaskStatus status);
    List<TaskEntity> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    @Query("SELECT new com.eneik.production.dto.dashboard.QueueDashboardDto$TagCountDto(" +
            "t.role.tag, COUNT(t), " +
            "TIMESTAMPDIFF(MINUTE, MIN(t.createdAt), CURRENT_TIMESTAMP)) " +
            "FROM TaskEntity t WHERE t.status = com.eneik.production.models.persistence.TaskStatus.queued " +
            "GROUP BY t.role.tag")
    List<QueueDashboardDto.TagCountDto> queuedGroupedByTag();

    @Query(value = "SELECT t.* FROM tasks t " +
            "JOIN projects p ON t.project_id = p.id " +
            "WHERE t.status = 'queued' AND t.tag IN (:capableTags) AND p.status = 'active' " +
            "ORDER BY t.priority DESC, t.created_at ASC " +
            "LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<TaskEntity> lockNextQueuedTask(@Param("capableTags") List<String> capableTags);

    @Query(value = "SELECT t.* FROM tasks t " +
            "JOIN projects p ON t.project_id = p.id " +
            "WHERE t.project_id = :projectId AND t.status = 'queued' AND p.status = 'active' " +
            "ORDER BY t.priority DESC, t.created_at ASC " +
            "LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<TaskEntity> lockNextQueuedTaskForProject(@Param("projectId") UUID projectId);

    @Query("SELECT new com.eneik.production.dto.dashboard.QueueDashboardDto$TagCountDto(" +
            "t.role.tag, COUNT(t), " +
            "TIMESTAMPDIFF(MINUTE, MIN(t.createdAt), CURRENT_TIMESTAMP)) " +
           "FROM TaskEntity t WHERE t.project.id = :projectId " +
           "AND t.status = com.eneik.production.models.persistence.TaskStatus.queued " +
           "GROUP BY t.role.tag")
    List<QueueDashboardDto.TagCountDto> queuedGroupedByProjectAndTag(@Param("projectId") UUID projectId);
}
