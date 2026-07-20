package com.eneik.production.repositories;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@ActiveProfiles("test")
class AccountRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void recoverStaleBlockedAccountsResetsOnlyBlocksOlderThanCutoff() {
        AccountEntity stale = persistAccount("stale-blocked", AccountStatus.api_blocked, Instant.now().minus(2, ChronoUnit.HOURS));
        AccountEntity recent = persistAccount("recent-blocked", AccountStatus.api_blocked, Instant.now());
        AccountEntity neverTimestamped = persistAccountWithNullStatusChangedAt("legacy-blocked", AccountStatus.api_blocked);
        AccountEntity untouched = persistAccount("already-idle", AccountStatus.idle, Instant.now().minus(2, ChronoUnit.HOURS));
        entityManager.flush();

        int recovered = accountRepository.recoverStaleBlockedAccounts(Instant.now().minus(30, ChronoUnit.MINUTES));
        entityManager.flush();
        entityManager.clear();

        assertEquals(2, recovered);
        assertEquals(AccountStatus.idle, accountRepository.findById(stale.getId()).orElseThrow().getStatus());
        assertEquals(AccountStatus.idle, accountRepository.findById(neverTimestamped.getId()).orElseThrow().getStatus());
        assertEquals(AccountStatus.api_blocked, accountRepository.findById(recent.getId()).orElseThrow().getStatus());
        assertEquals(AccountStatus.idle, accountRepository.findById(untouched.getId()).orElseThrow().getStatus());
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

    private AccountEntity persistAccountWithNullStatusChangedAt(String name, AccountStatus status) {
        AccountEntity account = persistAccount(name, status, Instant.now());
        entityManager.getEntityManager()
                .createQuery("UPDATE AccountEntity a SET a.statusChangedAt = NULL WHERE a.id = :id")
                .setParameter("id", account.getId())
                .executeUpdate();
        return account;
    }
}
