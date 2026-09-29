package com.eneik.production.repositories;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Falsification test-screen for AccountRepository.
 *
 * Epistemic & Deontic Grounding:
 * - BARCAN-TAG-00_CODE-GUARDIAN:03:rut-barkan-markus / RUT_BARKAN_MARKUS_01_DE_RE_MODALITY_CHECK [D002, Marcus]:
 *   De re modality of account capacity: capacity limits, learned Bayesian quotas, and capabilities are
 *   inherent properties of the concrete account entity (de re), overriding abstract global defaults (de dicto).
 * - BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE:02:elvin-goldman / ELVIN_GOLDMAN_01_RELIABILITY_CHAIN [D008, Goldman]:
 *   Reliability of CAS acquisition and FOR UPDATE SKIP LOCKED: the selection mechanism provides a verified
 *   chain of reliability against concurrent double-locking and race conditions.
 * - BARCAN-TAG-10_DEONTIC-PROHIBITION:03:dzhozef-raz / DZHOZEF_RAZ_21_PENALTY_AS_ORDERING [D006, Raz]:
 *   Penalty as ordering, not exclusion: refusal runs demote queue priority but never prevent dispatch when
 *   capacity is needed; an accepted session resets the penalty immediately.
 */
@DataJpaTest
@ActiveProfiles("test")
class AccountRepositoryFalsificationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanState() {
        entityManager.getEntityManager().createQuery("DELETE FROM JulesSessionEntity").executeUpdate();
        entityManager.getEntityManager().createQuery("DELETE FROM TaskEntity").executeUpdate();
        entityManager.getEntityManager().createQuery("DELETE FROM AccountEntity").executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    private RoleEntity ensureRole(String tag) {
        RoleEntity existing = entityManager.find(RoleEntity.class, tag);
        if (existing != null) {
            return existing;
        }
        RoleEntity role = new RoleEntity();
        role.setTag(tag);
        role.setRulesPath("rules/" + tag);
        role.setActive(true);
        entityManager.persist(role);
        entityManager.flush();
        return role;
    }

    private AccountEntity createAccount(String name, String capabilities, int maxConcurrent, Integer estimatedConcurrent,
                                       int dispatchedToday, Integer estimatedDaily) {
        AccountEntity a = new AccountEntity();
        a.setName(name);
        a.setCapabilities(capabilities);
        a.setStatus(AccountStatus.idle);
        a.setApiKey("key-" + name);
        a.setEnabled(true);
        a.setMaxConcurrentSessions(maxConcurrent);
        a.setEstimatedConcurrentCapacity(estimatedConcurrent);
        a.setSessionsDispatchedToday(dispatchedToday);
        a.setEstimatedDailyCapacity(estimatedDaily);
        a.setLastHeartbeat(Instant.now());
        entityManager.persist(a);
        entityManager.flush();
        return a;
    }

    private TaskEntity createTask(RoleEntity role, TaskStatus status) {
        TaskEntity task = new TaskEntity();
        task.setRole(role);
        task.setDescription("Test task " + UUID.randomUUID());
        task.setStatus(status);
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        entityManager.persist(task);
        entityManager.flush();
        return task;
    }

    private JulesSessionEntity createSession(UUID accountId, UUID taskId, String status, String externalSessionId) {
        JulesSessionEntity session = new JulesSessionEntity();
        session.setAccountId(accountId);
        session.setTaskId(taskId);
        session.setStatus(status);
        session.setExternalSessionId(externalSessionId);
        session.setCreatedAt(Instant.now());
        session.setUpdatedAt(Instant.now());
        entityManager.persist(session);
        entityManager.flush();
        return session;
    }

    @Test
    @DisplayName("RUT_BARKAN_MARKUS_01: De Re concurrent capacity bounds dispatch and liberates on task completion")
    void deReConcurrentCapacityBoundsDispatchAndLiberatesOnTaskCompletion() {
        RoleEntity role = ensureRole("BARCAN-TAG-11");
        // Account has de re estimated_concurrent_capacity = 2
        AccountEntity account = createAccount("de-re-concurrent", "BARCAN-TAG-11", 5, 2, 0, 100);

        TaskEntity task1 = createTask(role, TaskStatus.in_progress);
        TaskEntity task2 = createTask(role, TaskStatus.in_progress);
        createSession(account.getId(), task1.getId(), "running", "ext-1");
        createSession(account.getId(), task2.getId(), "running", "ext-2");

        entityManager.flush();
        entityManager.clear();

        // 1. Both slots occupied (2/2) -> De Re capacity exhausted, lock returns empty
        Optional<AccountEntity> lockedExhausted = accountRepository.lockNextJulesAccountWithCapacity(
                null, "BARCAN-TAG-11", 5, null, 100, null);
        assertThat(lockedExhausted).isEmpty();

        // 2. Complete task1 -> de re slot liberated (1/2)
        task1.setStatus(TaskStatus.done);
        entityManager.merge(task1);
        entityManager.flush();
        entityManager.clear();

        // 3. Lock must now succeed
        Optional<AccountEntity> lockedLiberated = accountRepository.lockNextJulesAccountWithCapacity(
                null, "BARCAN-TAG-11", 5, null, 100, null);
        assertThat(lockedLiberated).isPresent();
        assertThat(lockedLiberated.get().getId()).isEqualTo(account.getId());
    }

    @Test
    @DisplayName("RUT_BARKAN_MARKUS_01: Blocked task sessions are excluded from de re capacity consumption")
    void deReBlockedTaskSessionsExcludedFromCapacity() {
        RoleEntity role = ensureRole("BARCAN-TAG-11");
        AccountEntity account = createAccount("blocked-exclusion-acc", "BARCAN-TAG-11", 1, 1, 0, 100);

        TaskEntity blockedTask = createTask(role, TaskStatus.blocked);
        createSession(account.getId(), blockedTask.getId(), "stuck", "ext-stuck");

        entityManager.flush();
        entityManager.clear();

        // Dead session on blocked task must not block account dispatch
        Optional<AccountEntity> locked = accountRepository.lockNextJulesAccountWithCapacity(
                null, "BARCAN-TAG-11", 1, null, 100, null);
        assertThat(locked).isPresent();
        assertThat(locked.get().getId()).isEqualTo(account.getId());
    }

    @Test
    @DisplayName("RUT_BARKAN_MARKUS_01: De Re learned daily capacity strictly overrides global configuration default")
    void deReLearnedDailyCapacityOverridesGlobalDefault() {
        RoleEntity role = ensureRole("BARCAN-TAG-11");

        // Account 1: dispatched 10, learned daily capacity = 12 (global default = 5)
        // Under global default alone it would be rejected (10 >= 5), but de re learned capacity (12 > 10) admits it.
        AccountEntity learnedHigh = createAccount("learned-high", "BARCAN-TAG-11", 5, 5, 10, 12);

        Optional<AccountEntity> lockedHigh = accountRepository.lockNextJulesAccountWithCapacity(
                null, "BARCAN-TAG-11", 5, null, 5, null);
        assertThat(lockedHigh).isPresent();
        assertThat(lockedHigh.get().getId()).isEqualTo(learnedHigh.getId());

        // Account 2: dispatched 5, learned daily capacity = 4 (global default = 10)
        // Under global default alone it would be admitted (5 < 10), but de re learned capacity (4 <= 5) denies it.
        AccountEntity learnedLow = createAccount("learned-low", "BARCAN-TAG-11", 5, 5, 5, 4);

        Optional<AccountEntity> lockedLow = accountRepository.lockNextJulesAccountWithCapacity(
                null, "BARCAN-TAG-11", 5, "learned-high", 10, null);
        assertThat(lockedLow).isEmpty();
    }

    @Test
    @DisplayName("RUT_BARKAN_MARKUS_01: Capability tag matching strictly enforces modal boundaries")
    void deReCapabilityTagBoundary() {
        RoleEntity tag11 = ensureRole("BARCAN-TAG-11");
        RoleEntity tag08 = ensureRole("BARCAN-TAG-08");

        AccountEntity frontendOnly = createAccount("frontend-only", "BARCAN-TAG-11", 5, 5, 0, 100);

        // Matching capability -> Admitted
        assertThat(accountRepository.lockNextJulesAccountWithCapacity(
                null, "BARCAN-TAG-11", 5, null, 100, null)).isPresent();

        // Mismatched capability -> Rejected
        assertThat(accountRepository.lockNextJulesAccountWithCapacity(
                null, "BARCAN-TAG-08", 5, null, 100, null)).isEmpty();
    }

    @Test
    @DisplayName("ELVIN_GOLDMAN_01: Concurrent FOR UPDATE SKIP LOCKED provides mutual exclusion without double-locking")
    void reliabilityChainConcurrentSkipLockedPreventsDoubleLocking() throws Exception {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // Commit role and account in a dedicated transaction so parallel threads on separate connections can see it
        txTemplate.execute(status -> {
            ensureRole("BARCAN-TAG-11");
            createAccount("sole-contender", "BARCAN-TAG-11", 5, 5, 0, 100);
            return null;
        });

        CountDownLatch thread1Locked = new CountDownLatch(1);
        CountDownLatch thread2Attempted = new CountDownLatch(1);
        AtomicBoolean thread1GotAccount = new AtomicBoolean(false);
        AtomicReference<Optional<AccountEntity>> thread2Result = new AtomicReference<>(Optional.empty());

        // Thread 1: Acquires lock and holds transaction open until Thread 2 attempts
        Thread thread1 = new Thread(() -> {
            txTemplate.execute(status -> {
                Optional<AccountEntity> locked = accountRepository.lockNextJulesAccountWithCapacity(
                        null, "BARCAN-TAG-11", 5, null, 100, null);
                if (locked.isPresent()) {
                    thread1GotAccount.set(true);
                }
                thread1Locked.countDown();
                try {
                    thread2Attempted.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
        });

        // Thread 2: Concurrently attempts to lock the same single account while Thread 1 holds lock
        Thread thread2 = new Thread(() -> {
            try {
                thread1Locked.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            txTemplate.execute(status -> {
                Optional<AccountEntity> locked = accountRepository.lockNextJulesAccountWithCapacity(
                        null, "BARCAN-TAG-11", 5, null, 100, null);
                thread2Result.set(locked);
                thread2Attempted.countDown();
                return null;
            });
        });

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        assertThat(thread1GotAccount.get()).as("Thread 1 must successfully lock the account").isTrue();
        assertThat(thread2Result.get()).as("Thread 2 must receive empty because row is locked by Thread 1 (SKIP LOCKED)").isEmpty();

        // 3. After Thread 1 committed and released, Thread 2 can now lock the account
        Optional<AccountEntity> lockAfterRelease = txTemplate.execute(status ->
                accountRepository.lockNextJulesAccountWithCapacity(null, "BARCAN-TAG-11", 5, null, 100, null));
        assertThat(lockAfterRelease).as("Account is selectable again after previous transaction commits").isPresent();

        // 4. Clean up committed entity so subsequent test suites start with clean state
        txTemplate.execute(status -> {
            entityManager.getEntityManager().createQuery("DELETE FROM AccountEntity WHERE name = 'sole-contender'").executeUpdate();
            return null;
        });
    }

    @Test
    @DisplayName("DZHOZEF_RAZ_21: Penalty as ordering demotes refusal runs behind clean accounts but does not exclude")
    void penaltyAsOrderingDemotesRefusalRunWithoutExclusion() {
        RoleEntity role = ensureRole("BARCAN-TAG-11");
        TaskEntity task = createTask(role, TaskStatus.in_progress);

        AccountEntity clean = createAccount("clean-acc", "BARCAN-TAG-11", 5, 5, 0, 100);
        AccountEntity penalized = createAccount("penalized-acc", "BARCAN-TAG-11", 5, 5, 0, 100);

        // Record 3 failed sessions for penalized
        for (int i = 0; i < 3; i++) {
            createSession(penalized.getId(), task.getId(), "failed", null);
        }

        // Avoid probe tick (modulo 5 == 0)
        long totalSessions = entityManager.getEntityManager()
                .createQuery("SELECT COUNT(s) FROM JulesSessionEntity s", Long.class)
                .getSingleResult();
        while (totalSessions % 5 == 0) {
            createSession(clean.getId(), task.getId(), "done", "pad-" + totalSessions);
            totalSessions++;
        }

        entityManager.flush();
        entityManager.clear();

        // 1. Clean account is chosen over penalized account
        Optional<AccountEntity> firstChoice = accountRepository.lockNextJulesAccountWithCapacity(
                null, "BARCAN-TAG-11", 5, null, 100, null);
        assertThat(firstChoice).isPresent();
        assertThat(firstChoice.get().getId()).isEqualTo(clean.getId());

        // 2. When clean account is excluded, penalized account is still selectable (penalty is ordering, NOT exclusion)
        Optional<AccountEntity> secondChoice = accountRepository.lockNextJulesAccountWithCapacity(
                null, "BARCAN-TAG-11", 5, "clean-acc", 100, null);
        assertThat(secondChoice).isPresent();
        assertThat(secondChoice.get().getId()).isEqualTo(penalized.getId());
    }

    @Test
    @DisplayName("DZHOZEF_RAZ_21: One accepted session resets the refusal penalty immediately")
    void acceptedSessionResetsRefusalPenaltyImmediately() {
        RoleEntity role = ensureRole("BARCAN-TAG-11");
        TaskEntity task = createTask(role, TaskStatus.in_progress);

        AccountEntity rehabilitated = createAccount("rehab-acc", "BARCAN-TAG-11", 5, 5, 0, 100);
        rehabilitated.setLastHeartbeat(Instant.now().minusSeconds(3600)); // older heartbeat -> wins ties
        entityManager.persist(rehabilitated);

        AccountEntity competitor = createAccount("competitor-acc", "BARCAN-TAG-11", 5, 5, 0, 100);
        competitor.setLastHeartbeat(Instant.now().minusSeconds(10));
        entityManager.persist(competitor);

        // Penalize rehabilitated with a failure
        createSession(rehabilitated.getId(), task.getId(), "failed", null);

        // Immediate subsequent accepted session resets the refusal count
        createSession(rehabilitated.getId(), task.getId(), "done", "ext-accepted");

        long totalSessions = entityManager.getEntityManager()
                .createQuery("SELECT COUNT(s) FROM JulesSessionEntity s", Long.class)
                .getSingleResult();
        while (totalSessions % 5 == 0) {
            createSession(competitor.getId(), task.getId(), "done", "pad-" + totalSessions);
            totalSessions++;
        }

        entityManager.flush();
        entityManager.clear();

        // Since refusal penalty is reset, tie between 0-refusal accounts breaks on last_heartbeat ASC
        // rehab-acc (3600s ago) < competitor-acc (10s ago) -> rehab-acc is selected first
        Optional<AccountEntity> chosen = accountRepository.lockNextJulesAccountWithCapacity(
                null, "BARCAN-TAG-11", 5, null, 100, null);
        assertThat(chosen).isPresent();
        assertThat(chosen.get().getId()).isEqualTo(rehabilitated.getId());
    }

    @Test
    @DisplayName("ELVIN_GOLDMAN_01: CAS status reset and countOperationalAccounts semantic boundary")
    void casStatusResetAndOperationalPoolAccounting() {
        AccountEntity idle = createAccount("acc-idle", "*", 5, 5, 0, 100);
        AccountEntity blocked = createAccount("acc-blocked", "*", 5, 5, 0, 100);
        blocked.setStatus(AccountStatus.api_blocked);
        entityManager.persist(blocked);

        AccountEntity decommissioned = createAccount("acc-decomm", "*", 5, 5, 0, 100);
        decommissioned.setStatus(AccountStatus.decommissioned);
        entityManager.persist(decommissioned);

        entityManager.flush();
        entityManager.clear();

        // countLiveAccounts excludes only decommissioned (2 live: idle, blocked)
        assertThat(accountRepository.countLiveAccounts()).isEqualTo(2);

        // countOperationalAccounts excludes decommissioned and api_blocked (1 operational: idle)
        assertThat(accountRepository.countOperationalAccounts()).isEqualTo(1);

        // CAS reset from api_blocked: first attempt succeeds, second returns 0
        int reset1 = accountRepository.resetSingleAccountFromApiBlocked(blocked.getId());
        int reset2 = accountRepository.resetSingleAccountFromApiBlocked(blocked.getId());
        entityManager.clear();

        assertThat(reset1).isEqualTo(1);
        assertThat(reset2).isEqualTo(0);
        assertThat(accountRepository.findById(blocked.getId()).orElseThrow().getStatus()).isEqualTo(AccountStatus.idle);

        // After reset, operational accounts count increases to 2
        assertThat(accountRepository.countOperationalAccounts()).isEqualTo(2);
    }
}
