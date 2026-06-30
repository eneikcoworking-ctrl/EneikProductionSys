package com.eneik.production.services.task;

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
public class TaskClaimServiceTest {

    @Autowired
    private TaskClaimService taskClaimService;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void testValidateTaskAvailability() {
        // Setup
        RoleEntity role = new RoleEntity();
        role.setTag("TAG-TEST");
        role.setRulesPath("rules.md");
        roleRepository.saveAndFlush(role);

        TaskEntity task = new TaskEntity();
        task.setRole(role);
        task.setDescription("Test Task");
        taskRepository.saveAndFlush(task);

        AccountEntity account = new AccountEntity();
        account.setName("Test Agent");
        account.setCapabilities("TAG-TEST");
        accountRepository.saveAndFlush(account);

        // 1. Initial State: Available
        assertDoesNotThrow(() -> taskClaimService.validateTaskAvailability(task.getId()));

        // 2. Claimed State: Not Available
        ClaimEntity claim = new ClaimEntity();
        claim.setTask(task);
        claim.setAccount(account);
        claim.setRole(role);
        claim.setLeaseExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        claimRepository.saveAndFlush(claim);

        assertThrows(IllegalStateException.class, () -> taskClaimService.validateTaskAvailability(task.getId()));

        // 3. Released State: Available Again
        claim.setReleasedAt(Instant.now());
        claimRepository.saveAndFlush(claim);

        assertDoesNotThrow(() -> taskClaimService.validateTaskAvailability(task.getId()));
    }
}
