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
    Optional<TaskEntity> findByProjectIdAndDescription(UUID projectId, String description);

    @Query("SELECT new com.eneik.production.dto.dashboard.QueueDashboardDto$TagCountDto(" +
            "t.role.tag, COUNT(t), " +
            "TIMESTAMPDIFF(MINUTE, MIN(t.createdAt), CURRENT_TIMESTAMP)) " +
            "FROM TaskEntity t WHERE t.status = com.eneik.production.models.persistence.TaskStatus.queued " +
            "GROUP BY t.role.tag")
    List<QueueDashboardDto.TagCountDto> queuedGroupedByTag();

    @Query(value = "SELECT t.* FROM tasks t WHERE t.id = :id AND t.status = 'queued' FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<TaskEntity> lockTaskByIdForUpdate(@Param("id") UUID id);

    @Query(value = "SELECT t.* FROM tasks t " +
            "JOIN projects p ON t.project_id = p.id " +
            "WHERE t.status = 'queued' AND t.tag IN (:capableTags) AND p.status = 'active' " +
            "ORDER BY t.priority DESC, t.created_at ASC", nativeQuery = true)
    List<TaskEntity> findCandidatesByCapableTags(@Param("capableTags") List<String> capableTags);

    @Query(value = "SELECT t.* FROM tasks t " +
            "JOIN projects p ON t.project_id = p.id " +
            "WHERE t.project_id = :projectId AND t.status = 'queued' AND p.status = 'active' " +
            "ORDER BY t.priority DESC, t.created_at ASC", nativeQuery = true)
    List<TaskEntity> findCandidatesByProject(@Param("projectId") UUID projectId);

    @Query("SELECT t FROM TaskEntity t WHERE t.project.id = :projectId AND (t.status IN (com.eneik.production.models.persistence.TaskStatus.claimed, com.eneik.production.models.persistence.TaskStatus.in_progress, com.eneik.production.models.persistence.TaskStatus.review) OR t.julesDispatchStatus = 'pr_opened')")
    List<TaskEntity> findActiveTasksByProject(@Param("projectId") UUID projectId);

    default Optional<TaskEntity> lockNextQueuedTask(List<String> capableTags) {
        List<TaskEntity> candidates = findCandidatesByCapableTags(capableTags);
        for (TaskEntity candidate : candidates) {
            if (hasFileScopeConflict(candidate) || hasUnresolvedDependency(candidate)) {
                continue;
            }
            Optional<TaskEntity> locked = lockTaskByIdForUpdate(candidate.getId());
            if (locked.isPresent()) {
                return locked;
            }
        }
        return Optional.empty();
    }

    default Optional<TaskEntity> lockNextQueuedTaskForProject(UUID projectId) {
        List<TaskEntity> candidates = findCandidatesByProject(projectId);
        for (TaskEntity candidate : candidates) {
            if (hasFileScopeConflict(candidate) || hasUnresolvedDependency(candidate)) {
                continue;
            }
            Optional<TaskEntity> locked = lockTaskByIdForUpdate(candidate.getId());
            if (locked.isPresent()) {
                return locked;
            }
        }
        return Optional.empty();
    }

    default boolean hasUnresolvedDependency(TaskEntity candidate) {
        if (candidate.getDependsOn() != null) {
            return candidate.getDependsOn().getStatus() != TaskStatus.done;
        }
        return false;
    }

    default boolean hasFileScopeConflict(TaskEntity candidate) {
        if (candidate.getProject() == null) {
            return false;
        }
        String candScope = candidate.getFileScope();
        if (candScope == null || candScope.trim().isEmpty() || "[]".equals(candScope.trim())) {
            return false;
        }
        List<TaskEntity> activeTasks = findActiveTasksByProject(candidate.getProject().getId());
        for (TaskEntity activeTask : activeTasks) {
            if (activeTask.getId().equals(candidate.getId())) {
                continue;
            }
            String activeScope = activeTask.getFileScope();
            if (activeScope != null && !activeScope.trim().isEmpty() && !"[]".equals(activeScope.trim())) {
                if (fileScopesIntersect(candScope, activeScope)) {
                    return true;
                }
            }
        }
        return false;
    }

    default boolean fileScopesIntersect(String scopeJson1, String scopeJson2) {
        if (scopeJson1 == null || scopeJson2 == null) return false;
        if (scopeJson1.trim().isEmpty() || scopeJson2.trim().isEmpty()) return false;
        if ("[]".equals(scopeJson1.trim()) || "[]".equals(scopeJson2.trim())) return false;
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<String> list1 = mapper.readValue(scopeJson1, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            List<String> list2 = mapper.readValue(scopeJson2, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            
            for (String p1 : list1) {
                for (String p2 : list2) {
                    if (pathsIntersect(p1, p2)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // fallback
        }
        return false;
    }

    default boolean pathsIntersect(String path1, String path2) {
        path1 = path1.replace("\\", "/");
        path2 = path2.replace("\\", "/");
        if (path1.equals(path2)) return true;
        
        if (path1.contains("...") || path2.contains("...")) {
            String regex1 = patternToRegex(path1);
            String regex2 = patternToRegex(path2);
            if (path2.matches(regex1) || path1.matches(regex2)) return true;
        }
        
        if (path1.endsWith("/")) {
            if (path2.startsWith(path1)) return true;
        } else {
            if (path2.startsWith(path1 + "/")) return true;
        }
        
        if (path2.endsWith("/")) {
            if (path1.startsWith(path2)) return true;
        } else {
            if (path1.startsWith(path2 + "/")) return true;
        }
        
        return false;
    }

    default String patternToRegex(String pattern) {
        String[] parts = pattern.split("\\.\\.\\.");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            sb.append(java.util.regex.Pattern.quote(parts[i]));
            if (i < parts.length - 1) {
                sb.append(".*");
            }
        }
        if (pattern.endsWith("...")) {
            sb.append(".*");
        }
        return "^" + sb.toString() + "$";
    }

    @Query("SELECT new com.eneik.production.dto.dashboard.QueueDashboardDto$TagCountDto(" +
            "t.role.tag, COUNT(t), " +
            "TIMESTAMPDIFF(MINUTE, MIN(t.createdAt), CURRENT_TIMESTAMP)) " +
           "FROM TaskEntity t WHERE t.project.id = :projectId " +
           "AND t.status = com.eneik.production.models.persistence.TaskStatus.queued " +
           "GROUP BY t.role.tag")
    List<QueueDashboardDto.TagCountDto> queuedGroupedByProjectAndTag(@Param("projectId") UUID projectId);

    List<TaskEntity> findByProjectIdAndStatusOrderByPriorityDescCreatedAtAsc(UUID projectId, TaskStatus status);
    List<TaskEntity> findBySourceWishlistIdIn(List<UUID> sourceWishlistIds);
}
