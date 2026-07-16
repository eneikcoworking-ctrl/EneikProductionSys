package com.eneik.production.services.task;

import com.eneik.production.dto.ClaimDto;
import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.ClaimService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class TaskClaimServiceTest {

    @Autowired
    private ClaimService taskClaimService;

    @Autowired
    private ClaimRepository claimRepository;

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

    @Test
    void completeRunsDesignGateAfterUiClaimCycle() {
        ObjectNode payload = basePayload();
        ArrayNode screenshots = payload.putArray("screenshotUrls");
        screenshots.addObject().put("url", "desktop_1440.png").put("size", 3000);
        screenshots.addObject().put("url", "mobile_375.png").put("size", 1800);
        TaskEntity task = createQueuedTask("BARCAN-TAG-11", payload);
        AccountEntity account = createAccount(task.getProject(), "BARCAN-TAG-11");

        ClaimDto claim = taskClaimService.claimForProject(task.getProject().getId(), account.getId());
        taskClaimService.complete(claim.taskId());

        TaskEntity completed = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(TaskStatus.review);
        assertThat(completed.isQualityGatePassed()).isTrue();
        assertThat(checkNames(completed)).contains("design_excellence");
        assertThat(checkNames(completed)).doesNotContain("backend_contract", "not applicable");
    }

    @Test
    void completeRunsBackendGateAfterBackendClaimCycle() {
        ObjectNode payload = basePayload();
        payload.putArray("changedFiles").add("src/test/java/com/eneik/LeadControllerTest.java");
        TaskEntity task = createQueuedTask("BARCAN-TAG-02", payload);
        AccountEntity account = createAccount(task.getProject(), "BARCAN-TAG-02");

        ClaimDto claim = taskClaimService.claimForProject(task.getProject().getId(), account.getId());
        taskClaimService.complete(claim.taskId());

        TaskEntity completed = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(TaskStatus.review);
        assertThat(completed.isQualityGatePassed()).isTrue();
        assertThat(checkNames(completed)).contains("backend_contract");
        assertThat(checkNames(completed)).doesNotContain("design_excellence", "not applicable");
    }

    private ObjectNode basePayload() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("lean_value", LeanValue.essential.name());
        payload.put("dod", "Given API error 400 is handled, When auth validation fails, Then the task is done");
        payload.put("acceptance_criteria", "Given valid auth, When validation runs, Then success is visible");
        return payload;
    }

    private TaskEntity createQueuedTask(String roleTag, ObjectNode payload) {
        ProjectEntity project = new ProjectEntity();
        String suffix = UUID.randomUUID().toString();
        project.setName("Claim Gate Test " + suffix);
        project.setSlug("claim-gate-test-" + suffix);
        project.setRepositoryName("claim-gate-test-repo-" + suffix);
        project.setRepoUrl("https://github.com/eneikcoworking-ctrl/claim-gate-test-" + suffix);
        project.setStatus(ProjectStatus.active);
        project = projectRepository.saveAndFlush(project);

        RoleEntity role = roleRepository.findById(roleTag).orElseThrow();

        TaskEntity task = new TaskEntity();
        task.setProject(project);
        task.setRole(role);
        task.setDescription("Claim completion quality gate test");
        task.setPayload(payload);
        task.setStatus(TaskStatus.queued);
        return taskRepository.saveAndFlush(task);
    }

    private AccountEntity createAccount(ProjectEntity project, String capabilities) {
        AccountEntity account = new AccountEntity();
        account.setProject(project);
        account.setName("Claim Gate Agent " + UUID.randomUUID());
        account.setCapabilities(capabilities);
        return accountRepository.saveAndFlush(account);
    }

    private List<String> checkNames(TaskEntity task) {
        JsonNode checks = task.getQualityGateReport().path("checks");
        return StreamSupport.stream(checks.spliterator(), false)
                .map(node -> node.path("name").asText())
                .toList();
    }
}
