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

    // Ф-followup (2026-07-21, operator directive - found via a live screenshot audit of the admin
    // dashboard): lastHeartbeat used to only update at account creation and via a dedicated /heartbeat
    // endpoint nothing in the real dispatch/claim/completion cycle ever called - confirmed live, all 5
    // active accounts showed the identical, week-old timestamp while one was genuinely `busy` at that
    // moment. This is the single most-hit status-transition point in the whole claim lifecycle (claim,
    // complete, fail, release all funnel through it), so stamping lastHeartbeat here makes "Last Activity"
    // in the admin UI finally track real usage instead of being permanently stale.
    // CURRENT_TIMESTAMP was tried first but Hibernate 6.4's HQL type-checker rejects it here (it resolves
    // to java.sql.Timestamp, which can't be assigned to the Instant-typed lastHeartbeat path) - confirmed
    // live, this broke Spring context startup entirely (SemanticException at bean-creation time). Passing
    // the instant in as a bound parameter sidesteps the type mismatch and lets the caller's clock be the
    // single source of truth, same as every other "now" in this codebase.
    @Modifying
    @Query("UPDATE AccountEntity a SET a.status = :status, a.lastHeartbeat = :now WHERE a.id = :id")
    void updateStatus(@Param("id") UUID id, @Param("status") AccountStatus status, @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("UPDATE AccountEntity a SET a.status = com.eneik.production.models.persistence.AccountStatus.idle " +
            "WHERE a.status = com.eneik.production.models.persistence.AccountStatus.daily_limited")
    int resetDailyLimitedAccounts();

    // api_blocked has no clean "resets at midnight" signal like daily_limited does, so instead of a fixed
    // cron this is retried periodically once the block has sat for at least the cooldown - self-correcting:
    // if the account is still genuinely blocked, the very next real dispatch attempt flips it right back to
    // api_blocked (and resets statusChangedAt via AccountEntity.setStatus), so this can never get stuck
    // claiming a false recovery. Also catches accounts left blocked from unrelated past projects that
    // nothing else would ever re-check.
    @Modifying
    @Transactional
    @Query("UPDATE AccountEntity a SET a.status = com.eneik.production.models.persistence.AccountStatus.idle " +
            "WHERE a.status = com.eneik.production.models.persistence.AccountStatus.api_blocked " +
            "AND a.enabled = true " +
            "AND (a.statusChangedAt IS NULL OR a.statusChangedAt < :staleBefore)")
    int recoverStaleBlockedAccounts(@Param("staleBefore") Instant staleBefore);

    @Modifying
    @Transactional
    @Query("UPDATE AccountEntity a SET a.sessionsDispatchedToday = 0")
    int resetDailySessionCounts();

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
              AND a.sessions_dispatched_today < :maxDailySessions
              AND (
                  SELECT COUNT(*)
                  FROM jules_sessions s
                  JOIN tasks t ON t.id = s.task_id
                  WHERE s.account_id = a.id
                    AND s.status IN ('queued', 'running', 'revising', 'stuck')
                    AND t.status NOT IN ('done', 'failed')
              ) < COALESCE(a.max_concurrent_sessions, :maxSessions)
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
                                                             @Param("reservedName") String reservedName,
                                                             @Param("maxDailySessions") int maxDailySessions);

    // No daily-session-budget check here: this method is only ever called with the configured reserved
    // compiler/falsification account's own name (ProjectFlowService.taskCompilerAccountName()), so every
    // row it could possibly return already is that exempt account by construction - unlike
    // lockNextJulesAccountWithCapacity's general pool, there is no other account this query could match.
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
              ) < COALESCE(a.max_concurrent_sessions, :maxSessions)
            ORDER BY a.last_heartbeat DESC LIMIT 1 FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<AccountEntity> lockAccountByNameWithCapacity(@Param("name") String name,
                                                          @Param("maxSessions") int maxSessions);

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
