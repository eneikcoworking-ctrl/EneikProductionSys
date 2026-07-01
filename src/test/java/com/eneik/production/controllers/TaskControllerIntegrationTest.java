package com.eneik.production.controllers;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.repositories.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class TaskControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void testHeartbeat() throws Exception {
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
        account.setStatus(AccountStatus.offline); // Test that it comes back from offline
        account = accountRepository.save(account);

        ClaimEntity claim = new ClaimEntity();
        claim.setTask(task);
        claim.setAccount(account);
        claim.setRole(role);
        Instant initialExpiry = Instant.now().plus(1, ChronoUnit.MINUTES);
        claim.setLeaseExpiresAt(initialExpiry);
        claim = claimRepository.save(claim);

        // Act
        mockMvc.perform(post("/api/tasks/{claimId}/heartbeat", claim.getId()))
                .andExpect(status().isOk());

        // Assert
        ClaimEntity updatedClaim = claimRepository.findById(claim.getId()).orElseThrow();
        assertTrue(updatedClaim.getLeaseExpiresAt().isAfter(initialExpiry));

        AccountEntity updatedAccount = accountRepository.findById(account.getId()).orElseThrow();
        assertNotNull(updatedAccount.getLastHeartbeat());
        assertEquals(AccountStatus.busy, updatedAccount.getStatus());
    }
}
