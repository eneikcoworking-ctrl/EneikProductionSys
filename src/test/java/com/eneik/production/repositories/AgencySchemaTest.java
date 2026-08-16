package com.eneik.production.repositories;

import com.eneik.production.models.persistence.*;
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
public class AgencySchemaTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ClaimRepository claimRepository;

    @Test
    void testSchemaMappingAndSeedData() {
        // 1. Verify Seed Data
        assertTrue(roleRepository.existsById("BARCAN-TAG-00"));
        assertTrue(roleRepository.existsById("BARCAN-TAG-11"));
        assertTrue(roleRepository.existsById("BARCAN-TAG-12"));
        assertEquals(13, roleRepository.count());

        // 2. Test CRUD Flow
        RoleEntity role = roleRepository.findById("BARCAN-TAG-01").orElseThrow();

        TaskEntity task = new TaskEntity();
        task.setRole(role);
        task.setDescription("Test Architectural Task");
        task.setStatus(TaskStatus.queued);
        TaskEntity savedTask = taskRepository.save(task);
        assertNotNull(savedTask.getId());

        AccountEntity account = new AccountEntity();
        account.setName("Primary Architect");
        account.setCapabilities("BARCAN-TAG-01,BARCAN-TAG-02");
        AccountEntity savedAccount = accountRepository.save(account);
        assertNotNull(savedAccount.getId());

        ClaimEntity claim = new ClaimEntity();
        claim.setTask(savedTask);
        claim.setAccount(savedAccount);
        claim.setRole(role);
        claim.setLeaseExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        ClaimEntity savedClaim = claimRepository.save(claim);
        assertNotNull(savedClaim.getId());

        // 3. Verify Relationships
        ClaimEntity fetchedClaim = claimRepository.findById(savedClaim.getId()).orElseThrow();
        assertEquals(savedTask.getId(), fetchedClaim.getTask().getId());
        assertEquals(savedAccount.getId(), fetchedClaim.getAccount().getId());
        assertEquals("BARCAN-TAG-01", fetchedClaim.getRole().getTag());
    }
}
