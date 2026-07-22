package com.eneik.production.services.task;

import com.eneik.production.dto.ClaimDto;
import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
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

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private JulesSessionRepository julesSessionRepository;

    @Autowired
    private PrReviewRepository prReviewRepository;

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
    void completeRunsDesignGateAfterUiClaimCyclePastBuildPhase() {
        ObjectNode payload = basePayload();
        ArrayNode screenshots = payload.putArray("screenshotUrls");
        screenshots.addObject().put("url", "desktop_1440.png").put("size", 3000);
        screenshots.addObject().put("url", "mobile_375.png").put("size", 1800);
        TaskEntity task = createQueuedTask("BARCAN-TAG-11", payload);
        markProjectPastBuildPhase(task.getProject());
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
    void completeRunsBackendGateAfterBackendClaimCyclePastBuildPhase() {
        ObjectNode payload = basePayload();
        payload.putArray("changedFiles").add("src/test/java/com/eneik/LeadControllerTest.java");
        TaskEntity task = createQueuedTask("BARCAN-TAG-02", payload);
        markProjectPastBuildPhase(task.getProject());
        AccountEntity account = createAccount(task.getProject(), "BARCAN-TAG-02");

        ClaimDto claim = taskClaimService.claimForProject(task.getProject().getId(), account.getId());
        taskClaimService.complete(claim.taskId());

        TaskEntity completed = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(TaskStatus.review);
        assertThat(completed.isQualityGatePassed()).isTrue();
        assertThat(checkNames(completed)).contains("backend_contract");
        assertThat(checkNames(completed)).doesNotContain("design_excellence", "not applicable");
    }

    @Test
    void completeSkipsDesignGateDuringBuildPhaseEvenForUiTask() {
        ObjectNode payload = basePayload();
        ArrayNode screenshots = payload.putArray("screenshotUrls");
        screenshots.addObject().put("url", "desktop_1440.png").put("size", 3000);
        screenshots.addObject().put("url", "mobile_375.png").put("size", 1800);
        TaskEntity task = createQueuedTask("BARCAN-TAG-11", payload);
        // Fresh project, zero merged client deliverables - still in build phase by default.
        AccountEntity account = createAccount(task.getProject(), "BARCAN-TAG-11");

        ClaimDto claim = taskClaimService.claimForProject(task.getProject().getId(), account.getId());
        taskClaimService.complete(claim.taskId());

        TaskEntity completed = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(completed.isQualityGatePassed()).isTrue();
        assertThat(checkNames(completed)).doesNotContain("design_excellence", "backend_contract");
    }

    @Test
    void reaperReleasesClaimWithoutResurrectingTerminalTask() {
        TaskEntity task = createQueuedTask("BARCAN-TAG-02", basePayload());
        AccountEntity account = createAccount(task.getProject(), "BARCAN-TAG-02");
        ClaimDto claimDto = taskClaimService.claimForProject(task.getProject().getId(), account.getId());

        ClaimEntity claim = claimRepository.findById(claimDto.claimId()).orElseThrow();
        claim.setClaimedAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        claim.setLeaseExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        claimRepository.saveAndFlush(claim);

        task.setStatus(TaskStatus.done);
        taskRepository.saveAndFlush(task);

        taskClaimService.reapExpiredLeases();

        TaskEntity preserved = taskRepository.findById(task.getId()).orElseThrow();
        ClaimEntity released = claimRepository.findById(claim.getId()).orElseThrow();
        assertThat(preserved.getStatus()).isEqualTo(TaskStatus.done);
        assertThat(released.getReleasedAt()).isNotNull();
        assertThat(released.getResultStatus()).isEqualTo(ClaimResultStatus.done);
        assertThat(claimRepository.findByTaskIdAndReleasedAtIsNull(task.getId())).isEmpty();
    }

    @Test
    void releasingOneTerminalClaimKeepsAccountBusyWhileAnotherClaimIsActive() {
        TaskEntity first = createQueuedTask("BARCAN-TAG-02", basePayload());
        TaskEntity second = new TaskEntity();
        second.setProject(first.getProject());
        second.setRole(first.getRole());
        second.setDescription("Second concurrent claim");
        second.setPayload(basePayload());
        second.setStatus(TaskStatus.queued);
        second = taskRepository.saveAndFlush(second);
        AccountEntity account = createAccount(first.getProject(), "BARCAN-TAG-02");

        taskClaimService.claimSpecificTask(first.getId(), account.getId());
        taskClaimService.claimSpecificTask(second.getId(), account.getId());

        first.setStatus(TaskStatus.done);
        taskRepository.saveAndFlush(first);
        taskClaimService.releaseTerminalClaim(first.getId());
        assertThat(accountRepository.findById(account.getId()).orElseThrow().getStatus()).isEqualTo(AccountStatus.busy);

        second.setStatus(TaskStatus.done);
        taskRepository.saveAndFlush(second);
        taskClaimService.releaseTerminalClaim(second.getId());
        assertThat(accountRepository.findById(account.getId()).orElseThrow().getStatus()).isEqualTo(AccountStatus.idle);
    }

    @Test
    void lateFailureCallbacksCannotReopenDoneTask() {
        TaskEntity task = createQueuedTask("BARCAN-TAG-02", basePayload());
        AccountEntity account = createAccount(task.getProject(), "BARCAN-TAG-02");
        taskClaimService.claimSpecificTask(task.getId(), account.getId());
        task.setStatus(TaskStatus.done);
        taskRepository.saveAndFlush(task);

        taskClaimService.closeTaskAsFailed(task.getId(), "late failed callback");
        taskClaimService.closeTaskAsBlocked(task.getId(), "late blocked callback");
        taskClaimService.releaseClaimToQueue(task.getId(), "late retry callback");
        taskClaimService.fail(task.getId());

        assertThat(taskRepository.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.done);
        assertThat(claimRepository.findByTaskIdAndReleasedAtIsNull(task.getId())).isEmpty();
    }

    private void markProjectPastBuildPhase(ProjectEntity project) {
        WishlistEntity deliverable = new WishlistEntity();
        deliverable.setProjectId(project.getId());
        deliverable.setSource(WishlistSource.client);
        deliverable.setContent("Client deliverable used to simulate a merged build phase.");
        deliverable.setCompiledByRole("BARCAN-TAG-09");
        deliverable = wishlistRepository.saveAndFlush(deliverable);

        RoleEntity role = roleRepository.findById("BARCAN-TAG-09").orElseThrow();
        TaskEntity mergedTask = new TaskEntity();
        mergedTask.setProject(project);
        mergedTask.setRole(role);
        mergedTask.setDescription("Deliverable task backing the merged build-phase simulation");
        mergedTask.setSourceWishlistId(deliverable.getId());
        mergedTask.setStatus(TaskStatus.done);
        mergedTask = taskRepository.saveAndFlush(mergedTask);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setTaskId(mergedTask.getId());
        session.setExternalSessionId("sessions/fixture");
        session.setStatus("completed");
        session = julesSessionRepository.saveAndFlush(session);

        PrReviewEntity review = new PrReviewEntity();
        review.setJulesSessionId(session.getId());
        review.setPrUrl("https://github.com/example/repo/pull/1");
        review.setCiStatus("success");
        review.setRiskLevel("low");
        review.setMerged(true);
        prReviewRepository.saveAndFlush(review);
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
