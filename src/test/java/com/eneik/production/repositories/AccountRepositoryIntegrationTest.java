package com.eneik.production.repositories;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
