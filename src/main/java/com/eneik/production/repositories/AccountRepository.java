package com.eneik.production.repositories;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {
    List<AccountEntity> findByProjectIdOrderByNameAsc(UUID projectId);

    List<AccountEntity> findByEnabledTrueAndProjectIsNullAndGithubUsernameIsNotNullOrderByNameAsc();

    @Query("SELECT COUNT(a) > 0 FROM AccountEntity a WHERE " +
           "a.lastHeartbeat > :threshold AND " +
           "(:tag IS NULL OR :tag IS NOT NULL)")
    boolean existsOnlineWithCapability(@Param("tag") String tag, @Param("threshold") Instant threshold);

    @Modifying
    @Query("UPDATE AccountEntity a SET a.status = :status WHERE a.id = :id")
    void updateStatus(@Param("id") UUID id, @Param("status") AccountStatus status);

    @Modifying
    @Query("UPDATE AccountEntity a SET a.currentProjectId = :newProjectId " +
           "WHERE a.currentProjectId IS NULL " +
           "OR a.currentProjectId IN (SELECT p.id FROM ProjectEntity p WHERE p.status = com.eneik.production.models.persistence.ProjectStatus.accepted)")
    void assignFreeAccountsToProject(@Param("newProjectId") UUID newProjectId);

    @Query(value = "SELECT * FROM accounts WHERE status = 'idle' AND enabled = true " +
            "AND (current_project_id IS NULL OR current_project_id = :projectId) " +
            "ORDER BY last_heartbeat DESC LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<AccountEntity> lockNextIdleAccountForProject(@Param("projectId") UUID projectId);

    @Query(value = "SELECT * FROM accounts WHERE status = 'idle' AND enabled = true " +
            "AND (current_project_id IS NULL OR current_project_id = :projectId) " +
            "AND (:tag IS NULL OR :tag IS NOT NULL) " +
            "ORDER BY last_heartbeat DESC LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<AccountEntity> lockNextIdleAccountForProjectAndCapability(@Param("projectId") UUID projectId, @Param("tag") String tag);

    @Query(value = """
            SELECT * FROM accounts a
            WHERE a.enabled = true
              AND a.status <> 'decommissioned'
              AND (a.current_project_id IS NULL OR a.current_project_id = :projectId)
              AND (:tag IS NULL OR :tag IS NOT NULL)
              AND (
                  SELECT COUNT(*)
                  FROM jules_sessions s
                  JOIN tasks t ON t.id = s.task_id
                  WHERE s.account_id = a.id
                    AND s.status IN ('queued', 'running', 'revising', 'stuck')
                    AND t.status NOT IN ('done', 'failed')
              ) < :maxSessions
            ORDER BY (
                  SELECT COUNT(*)
                  FROM jules_sessions s
                  JOIN tasks t ON t.id = s.task_id
                  WHERE s.account_id = a.id
                    AND s.status IN ('queued', 'running', 'revising', 'stuck')
                    AND t.status NOT IN ('done', 'failed')
              ) ASC, a.last_heartbeat DESC
            LIMIT 1 FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<AccountEntity> lockNextJulesAccountWithCapacity(@Param("projectId") UUID projectId,
                                                             @Param("tag") String tag,
                                                             @Param("maxSessions") int maxSessions);

    @Query(value = """
            SELECT COUNT(*) > 0 FROM accounts a
            WHERE a.enabled = true
              AND a.status <> 'decommissioned'
              AND (:tag IS NULL OR :tag IS NOT NULL)
              AND (
                  SELECT COUNT(*)
                  FROM jules_sessions s
                  JOIN tasks t ON t.id = s.task_id
                  WHERE s.account_id = a.id
                    AND s.status IN ('queued', 'running', 'revising', 'stuck')
                    AND t.status NOT IN ('done', 'failed')
              ) < :maxSessions
            """, nativeQuery = true)
    boolean existsJulesAccountWithCapacity(@Param("tag") String tag,
                                           @Param("maxSessions") int maxSessions);

    long countByCurrentProjectId(UUID currentProjectId);
}
