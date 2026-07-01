package com.eneik.production.services;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.repositories.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class LeaseWatchdogTest {

    @Autowired
    private LeaseWatchdog leaseWatchdog;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void testReapExpiredLeases() {
        // Arrange
        RoleEntity role = roleRepository.findById("BARCAN-TAG-01").orElseThrow();

        TaskEntity task = new TaskEntity();
        task.setRole(role);
        task.setDescription("Test Task");
        task.setStatus(TaskStatus.in_progress);
        task = taskRepository.save(task);

        AccountEntity account = new AccountEntity();
        account.setName("Test Account");
        account.setCapabilities("BARCAN-TAG-01");
        account.setStatus(AccountStatus.busy);
        account = accountRepository.save(account);

        ClaimEntity claim = new ClaimEntity();
        claim.setTask(task);
        claim.setAccount(account);
        claim.setRole(role);
        // Set lease to expire in the past
        claim.setLeaseExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        claim = claimRepository.save(claim);

        // Act
        leaseWatchdog.reapExpiredLeases();

        // Assert
        ClaimEntity updatedClaim = claimRepository.findById(claim.getId()).orElseThrow();
        assertNotNull(updatedClaim.getReleasedAt());
        assertEquals(ClaimResultStatus.expired, updatedClaim.getResultStatus());

        TaskEntity updatedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.queued, updatedTask.getStatus());

        AccountEntity updatedAccount = accountRepository.findById(account.getId()).orElseThrow();
        assertEquals(AccountStatus.offline, updatedAccount.getStatus());
    }
}
