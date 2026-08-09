package com.eneik.production.repositories;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class AccountRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AccountRepository accountRepository;

    // 2026-08-01: the fixed-cooldown bulk UPDATE (recoverStaleBlockedAccounts) was replaced by per-account
    // exponential backoff (ContinuousOrchestrationService.recoverStaleBlockedAccounts computes the cooldown
    // in Java from AccountEntity.consecutiveApiBlockCount) - this repository now only exposes the two
    // building blocks that logic needs: fetch candidates, then a compare-and-swap single-account reset.
    // See ContinuousOrchestrationServiceTest for the exponential-cooldown math itself.
    @Test
    void findByStatusAndEnabledTrueReturnsOnlyMatchingEnabledAccounts() {
        AccountEntity blocked = persistAccount("blocked", AccountStatus.api_blocked, Instant.now());
        AccountEntity idle = persistAccount("idle", AccountStatus.idle, Instant.now());
        AccountEntity blockedDisabled = persistAccount("blocked-disabled", AccountStatus.api_blocked, Instant.now());
        blockedDisabled.setEnabled(false);
        entityManager.persist(blockedDisabled);
        entityManager.flush();
        entityManager.clear();

        var found = accountRepository.findByStatusAndEnabledTrue(AccountStatus.api_blocked);

        assertEquals(1, found.size());
        assertEquals(blocked.getId(), found.get(0).getId());
    }

    @Test
    void resetSingleAccountFromApiBlockedIsCompareAndSwapGuarded() {
        AccountEntity blocked = persistAccount("blocked", AccountStatus.api_blocked, Instant.now());
        AccountEntity idle = persistAccount("already-idle", AccountStatus.idle, Instant.now());
        entityManager.flush();
        entityManager.clear();

        int firstAttempt = accountRepository.resetSingleAccountFromApiBlocked(blocked.getId());
        int secondAttempt = accountRepository.resetSingleAccountFromApiBlocked(blocked.getId());
        int onAlreadyIdle = accountRepository.resetSingleAccountFromApiBlocked(idle.getId());
        entityManager.clear();

        assertEquals(1, firstAttempt);
        assertEquals(0, secondAttempt, "already-idle account must not be reported as freshly reset");
        assertEquals(0, onAlreadyIdle);
        assertEquals(AccountStatus.idle, accountRepository.findById(blocked.getId()).orElseThrow().getStatus());
    }

    // Live incident (2026-08-08, test-forty-third): a task left in TaskStatus.blocked (a genuine dead end -
    // ClaimService.closeTaskAsBlocked only ever sets this when Jules can never resume the task on its own)
    // still had a lingering jules_sessions row in 'stuck' status, and the capacity query only excluded
    // ('done', 'failed') - so this permanently-dead task kept occupying the account's one concurrent-session
    // slot forever, starving real, active work of the same role from ever dispatching. Confirmed live across
    // 6 such tasks, all belonging to projects that were themselves frozen/accepted weeks earlier.
    @Test
    void aBlockedTasksLingeringSessionNeverCountsAgainstAccountCapacity() {
        RoleEntity role = persistRole("BARCAN-TAG-11");
        AccountEntity account = persistAccountWithCapacity("blocked-holder", 1);
        TaskEntity blockedTask = persistTask(role, TaskStatus.blocked);
        persistSession(account.getId(), blockedTask.getId(), "stuck");
        entityManager.flush();
        entityManager.clear();

        Optional<AccountEntity> available = accountRepository.lockNextJulesAccountWithCapacity(
                null, "BARCAN-TAG-11", 1, null, 999, null);

        assertTrue(available.isPresent(), "a blocked task's dead session must not consume the account's capacity slot");
        assertEquals(account.getId(), available.get().getId());
    }

    @Test
    void aGenuinelyActiveTaskStillConsumesAccountCapacity() {
        RoleEntity role = persistRole("BARCAN-TAG-11");
        AccountEntity account = persistAccountWithCapacity("busy-holder", 1);
        TaskEntity activeTask = persistTask(role, TaskStatus.in_progress);
        persistSession(account.getId(), activeTask.getId(), "running");
        entityManager.flush();
        entityManager.clear();

        Optional<AccountEntity> available = accountRepository.lockNextJulesAccountWithCapacity(
                null, "BARCAN-TAG-11", 1, null, 999, null);

        assertTrue(available.isEmpty(), "a real in-progress session must still count against capacity");
    }

    // Engineering invariant #15 (2026-08-08): estimated_daily_capacity (AccountHealthService's Popperian/
    // Bayesian per-account belief, revised only by real Jules evidence) must win over the global
    // :maxDailySessions config constant once an account has been falsified/probed past the default -
    // otherwise every account is throttled identically regardless of its own real, individually-learned quota.
    @Test
    void anAccountsLearnedDailyCapacityOverridesTheGlobalConfigDefault() {
        AccountEntity account = persistAccountWithCapacity("learned-capacity-holder", 3);
        account.setSessionsDispatchedToday(20);
        account.setEstimatedDailyCapacity(25);
        entityManager.persist(account);
        entityManager.flush();
        entityManager.clear();

        // Global default passed in (15) would reject this account outright (20 >= 15) if the per-account
        // estimate weren't consulted first.
        Optional<AccountEntity> available = accountRepository.lockNextJulesAccountWithCapacity(
                null, "BARCAN-TAG-11", 3, null, 15, null);

        assertTrue(available.isPresent(), "an account with a real, learned daily capacity above the global "
                + "default must not be throttled by that default");
    }

    @Test
    void anAccountWithNoLearnedCapacityStillFallsBackToTheGlobalConfigDefault() {
        AccountEntity account = persistAccountWithCapacity("unlearned-capacity-holder", 3);
        account.setSessionsDispatchedToday(20);
        entityManager.persist(account);
        entityManager.flush();
        entityManager.clear();

        Optional<AccountEntity> available = accountRepository.lockNextJulesAccountWithCapacity(
                null, "BARCAN-TAG-11", 3, null, 15, null);

        assertTrue(available.isEmpty(), "an account with no falsified belief yet must still respect the "
                + "conservative global default (20 >= 15)");
    }

    // Roles are Flyway-seeded (V3/V46) for every real BARCAN-TAG-*, so this test reuses the already-seeded
    // row rather than persisting a duplicate (which would violate the roles.tag primary key).
    private RoleEntity persistRole(String tag) {
        RoleEntity existing = entityManager.find(RoleEntity.class, tag);
        if (existing != null) {
            return existing;
        }
        RoleEntity role = new RoleEntity();
        role.setTag(tag);
        role.setRulesPath(tag + ".md");
        entityManager.persist(role);
        return role;
    }

    private AccountEntity persistAccountWithCapacity(String name, int maxConcurrentSessions) {
        AccountEntity account = new AccountEntity();
        account.setName(name);
        account.setCapabilities("*");
        account.setStatus(AccountStatus.idle);
        account.setApiKey("test-key");
        account.setMaxConcurrentSessions(maxConcurrentSessions);
        entityManager.persist(account);
        return account;
    }

    private TaskEntity persistTask(RoleEntity role, TaskStatus status) {
        TaskEntity task = new TaskEntity();
        task.setRole(role);
        task.setDescription("test task");
        task.setStatus(status);
        entityManager.persist(task);
        return task;
    }

    private void persistSession(java.util.UUID accountId, java.util.UUID taskId, String status) {
        JulesSessionEntity session = new JulesSessionEntity();
        session.setAccountId(accountId);
        session.setTaskId(taskId);
        session.setStatus(status);
        entityManager.persist(session);
    }

    private AccountEntity persistAccount(String name, AccountStatus status, Instant statusChangedAt) {
        AccountEntity account = new AccountEntity();
        account.setName(name);
        account.setCapabilities("*");
        account.setStatus(status);
        entityManager.persist(account);
        // setStatus() stamps "now" - overwrite directly so the test can control staleness precisely.
        entityManager.getEntityManager()
                .createQuery("UPDATE AccountEntity a SET a.statusChangedAt = :ts WHERE a.id = :id")
                .setParameter("ts", statusChangedAt)
                .setParameter("id", account.getId())
                .executeUpdate();
        return account;
    }
}
