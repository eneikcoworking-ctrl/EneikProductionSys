package com.eneik.production.repositories;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {
    List<AccountEntity> findByProjectIdOrderByNameAsc(UUID projectId);

    List<AccountEntity> findByEnabledTrueAndProjectIsNullAndGithubUsernameIsNotNullOrderByNameAsc();

    @Query("SELECT a FROM AccountEntity a WHERE " +
            "a.status <> com.eneik.production.models.persistence.AccountStatus.decommissioned AND " +
            "(a.currentProjectId IS NULL OR a.currentProjectId = :projectId) " +
            "ORDER BY LOWER(a.name) ASC")
    List<AccountEntity> findAvailableForProjectOrderByNameAsc(@Param("projectId") UUID projectId);

    @Query("SELECT COUNT(a) > 0 FROM AccountEntity a WHERE " +
           "a.lastHeartbeat > :threshold AND " +
           "(:tag IS NULL OR a.capabilities = '*' OR CONCAT(',', a.capabilities, ',') LIKE CONCAT('%,', :tag, ',%'))")
    boolean existsOnlineWithCapability(@Param("tag") String tag, @Param("threshold") Instant threshold);

    @Modifying
    @Query("UPDATE AccountEntity a SET a.status = :status WHERE a.id = :id")
    void updateStatus(@Param("id") UUID id, @Param("status") AccountStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE AccountEntity a SET a.status = com.eneik.production.models.persistence.AccountStatus.idle " +
            "WHERE a.status = com.eneik.production.models.persistence.AccountStatus.daily_limited")
    int resetDailyLimitedAccounts();

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE accounts
            SET status = 'api_blocked'
            WHERE status = 'daily_limited'
              AND id IN (
                  SELECT DISTINCT account_id
                  FROM jules_sessions s
                  WHERE s.account_id IS NOT NULL
                    AND s.updated_at = (
                        SELECT MAX(s2.updated_at)
                        FROM jules_sessions s2
                        WHERE s2.account_id = s.account_id
                          AND s2.status = 'failed'
                    )
                    AND (
                        LOWER(COALESCE(s.closure_reason, '')) LIKE '%failed_precondition%'
                        OR LOWER(COALESCE(s.closure_reason, '')) LIKE '%precondition%'
                    )
              )
            """, nativeQuery = true)
    int reclassifyPreconditionDailyLimitedAccounts();

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
            "AND (:tag IS NULL OR capabilities = '*' OR ',' || capabilities || ',' LIKE '%,' || :tag || ',%') " +
            "ORDER BY last_heartbeat DESC LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<AccountEntity> lockNextIdleAccountForProjectAndCapability(@Param("projectId") UUID projectId, @Param("tag") String tag);

    @Query(value = """
            SELECT * FROM accounts a
            WHERE a.enabled = true
              AND a.status NOT IN ('decommissioned', 'offline', 'daily_limited', 'api_blocked')
              AND (a.current_project_id IS NULL OR a.current_project_id = :projectId)
              AND (:tag IS NULL OR a.capabilities = '*' OR ',' || a.capabilities || ',' LIKE '%,' || :tag || ',%')
              AND (:reservedName IS NULL OR a.name <> :reservedName)
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
                                                             @Param("maxSessions") int maxSessions,
                                                             @Param("reservedName") String reservedName);

    @Query(value = """
            SELECT * FROM accounts a
            WHERE a.name = :name
              AND a.enabled = true
              AND a.status NOT IN ('decommissioned', 'offline', 'daily_limited', 'api_blocked')
              AND (
                  SELECT COUNT(*)
                  FROM jules_sessions s
                  JOIN tasks t ON t.id = s.task_id
                  WHERE s.account_id = a.id
                    AND s.status IN ('queued', 'running', 'revising', 'stuck')
                    AND t.status NOT IN ('done', 'failed')
              ) < :maxSessions
            ORDER BY a.last_heartbeat DESC LIMIT 1 FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<AccountEntity> lockAccountByNameWithCapacity(@Param("name") String name, @Param("maxSessions") int maxSessions);

    @Query(value = """
            SELECT COUNT(*) > 0 FROM accounts a
            WHERE a.enabled = true
              AND a.status NOT IN ('decommissioned', 'offline', 'daily_limited', 'api_blocked')
              AND (:tag IS NULL OR a.capabilities = '*' OR ',' || a.capabilities || ',' LIKE '%,' || :tag || ',%')
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
