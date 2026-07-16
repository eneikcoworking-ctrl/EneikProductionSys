package com.eneik.production.services;

import com.eneik.production.dto.ClaimDto;
import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.repositories.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class TocPriorityClaimTest {

    @Autowired
    private ClaimService claimService;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testClaimRespectsTocPriorityOverFifo() throws InterruptedException {
        // 1. Setup project and role
        ProjectEntity project = new ProjectEntity();
        project.setName("TOC Test Project");
        project.setSlug("toc-test");
        project.setRepositoryName("toc-repo");
        project.setRepoUrl("http://github.com/test/toc");
        project.setStatus(ProjectStatus.active);
        project = projectRepository.saveAndFlush(project);

        RoleEntity role = roleRepository.findById("BARCAN-TAG-02").orElseThrow();

        // 2. Create Task A (older, priority 0)
        TaskEntity taskA = new TaskEntity();
        taskA.setProject(project);
        taskA.setRole(role);
        taskA.setDescription("Task A - FIFO first");
        taskA.setStatus(TaskStatus.queued);
        taskA.setPriority(0);
        taskA.setCreatedAt(Instant.now().minusSeconds(3600)); // 1 hour ago
        taskA = taskRepository.saveAndFlush(taskA);

        // 3. Create Task B (newer, priority 100)
        TaskEntity taskB = new TaskEntity();
        taskB.setProject(project);
        taskB.setRole(role);
        taskB.setDescription("Task B - Priority first");
        taskB.setStatus(TaskStatus.queued);
        taskB.setPriority(100);
        taskB.setCreatedAt(Instant.now()); // Just now
        taskB = taskRepository.saveAndFlush(taskB);

        // 4. Setup account
        AccountEntity account = new AccountEntity();
        account.setName("TOC Agent");
        account.setCapabilities("BARCAN-TAG-02");
        account.setLastHeartbeat(Instant.now());
        account = accountRepository.saveAndFlush(account);

        // 5. Claim task
        ClaimDto claim = claimService.claim(account.getId(), List.of("BARCAN-TAG-02"));

        // 6. Assert Task B was claimed instead of Task A despite being newer
        assertThat(claim).isNotNull();
        assertThat(claim.taskId()).isEqualTo(taskB.getId());

        // 7. Claim next task
        ClaimDto claim2 = claimService.claim(account.getId(), List.of("BARCAN-TAG-02"));
        assertThat(claim2).isNotNull();
        assertThat(claim2.taskId()).isEqualTo(taskA.getId());
    }
}
