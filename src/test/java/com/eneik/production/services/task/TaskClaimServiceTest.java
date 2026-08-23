package com.eneik.production.services.task;

import com.eneik.production.dto.ClaimDto;
import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.ClaimService;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

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
    private FeatureRepository featureRepository;

    @Autowired
    private JulesSessionRepository julesSessionRepository;

    @Autowired
    private PrReviewRepository prReviewRepository;

    // 2026-08-02 (Charter Pattern #12): BackendContractGate no longer reads a self-reported
    // payload.changedFiles list - it resolves the task's real implementer session and fetches the real
    // PR diff via GitHubPullRequestService. Mocked here so completeRunsBackendGateAfterBackendClaimCycle
    // PastBuildPhase can simulate a real diff without a live GitHub call.
    @MockBean
    private GitHubPullRequestService gitHubPullRequestService;

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
        TaskEntity task = createQueuedTask("BARCAN-TAG-11", basePayload());
        markProjectPastBuildPhase(task.getProject());
        AccountEntity account = createAccount(task.getProject(), "BARCAN-TAG-11");
        stubDesignScreenshotsForTask(task);

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
        TaskEntity task = createQueuedTask("BARCAN-TAG-02", payload);
        markProjectPastBuildPhase(task.getProject());
        AccountEntity account = createAccount(task.getProject(), "BARCAN-TAG-02");
        stubRealDiffForTask(task, "src/test/java/com/eneik/LeadControllerTest.java");

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
        assertThat(claimRepository.findFirstByTaskIdAndReleasedAtIsNullOrderByClaimedAtDesc(task.getId())).isEmpty();
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
        assertThat(claimRepository.findFirstByTaskIdAndReleasedAtIsNullOrderByClaimedAtDesc(task.getId())).isEmpty();
    }

    // Proves the atomic guard TaskRepository.writeStatusUnlessTerminal actually closes the race window
    // that the check-then-act version of these methods left open: a task observed as non-terminal at
    // method entry, but that reaches a terminal status via a CONCURRENT write before this method's own
    // status write executes (e.g. reapExpiredLeases and a normal completion callback interleaving on two
    // scheduler threads). This is deliberately independent of ClaimService's own isTerminal() pre-check,
    // which only proves the row was non-terminal at the READ - not at the WRITE.
    @Test
    void writeStatusUnlessTerminalRefusesOnceARowReachesTerminal() {
        TaskEntity task = createQueuedTask("BARCAN-TAG-02", basePayload());
        assertThat(taskRepository.writeStatusUnlessTerminal(task.getId(), TaskStatus.done)).isEqualTo(1);
        assertThat(taskRepository.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.done);

        // A second writer racing in behind it (simulating a concurrent transaction) must be refused - 0
        // rows affected, done must not be overwritten back to queued.
        assertThat(taskRepository.writeStatusUnlessTerminal(task.getId(), TaskStatus.queued)).isEqualTo(0);
        assertThat(taskRepository.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.done);
    }


    private void markProjectPastBuildPhase(ProjectEntity project) {
        WishlistEntity root = new WishlistEntity();
        root.setProjectId(project.getId());
        root.setSource(WishlistSource.client);
        root.setStatus(WishlistStatus.converted_to_task);
        root.setContent("Compiled client brief used by the build-phase fixture.");
        root = wishlistRepository.saveAndFlush(root);

        FeatureEntity feature = new FeatureEntity();
        feature.setProjectId(project.getId());
        feature.setRootWishlistId(root.getId());
        feature.setTitle("Fixture feature");
        feature = featureRepository.saveAndFlush(feature);

        WishlistEntity deliverable = new WishlistEntity();
        deliverable.setProjectId(project.getId());
        deliverable.setSource(WishlistSource.client);
        deliverable.setStatus(WishlistStatus.converted_to_task);
        deliverable.setFeatureId(feature.getId());
        deliverable.setContent("Client deliverable used to simulate a merged build phase.");
        deliverable.setCompiledByRole("BARCAN-TAG-09");
        deliverable = wishlistRepository.saveAndFlush(deliverable);

        // Deliberately a code-producing role (BARCAN-TAG-02), not BARCAN-TAG-09: the readiness ratio
        // (2026-07-24 fix) no longer counts DECISION-stage/non-code work toward "shipped", so simulating a
        // real merged client deliverable requires a role the engine actually recognizes as code-producing.
        RoleEntity role = roleRepository.findById("BARCAN-TAG-02").orElseThrow();
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
        review.setHasCode(true);
        prReviewRepository.saveAndFlush(review);
    }

    // 2026-08-02 (Charter Pattern #12): simulates a real implementer PR whose diff genuinely contains
    // the given changed file path, so BackendContractGate's real-diff lookup has something to find.
    private void stubRealDiffForTask(TaskEntity task, String changedFilePath) {
        JulesSessionEntity session = new JulesSessionEntity();
        session.setTaskId(task.getId());
        session.setExternalSessionId("sessions/fixture-backend-gate-" + task.getId());
        session.setStatus("pr_opened");
        session.setPrUrl("https://github.com/example/repo/pull/99");
        julesSessionRepository.save(session);
        when(gitHubPullRequestService.parsePullNumber("https://github.com/example/repo/pull/99")).thenReturn(99);
        when(gitHubPullRequestService.fetchDiffText(any(), eq(99)))
                .thenReturn(Optional.of("+++ b/" + changedFilePath + "\n"));
    }

    // 2026-08-14 (bug-hunt sweep): completeRunsDesignGateAfterUiClaimCyclePastBuildPhase used to stub a
    // now-dead payload.screenshotUrls field from before the 2026-08-03 DesignExcellenceGate rewrite (see
    // that class's own doc comment) - with no PR/session stub at all, the gate deterministically failed
    // with "no PR found to verify design screenshots against" and the task stayed queued instead of moving
    // to review. Mirrors stubRealDiffForTask's real-PR-diff pattern, plus the two real screenshot-file
    // fetches DesignExcellenceGate.check actually performs.
    private void stubDesignScreenshotsForTask(TaskEntity task) {
        JulesSessionEntity session = new JulesSessionEntity();
        session.setTaskId(task.getId());
        session.setExternalSessionId("sessions/fixture-design-gate-" + task.getId());
        session.setStatus("pr_opened");
        session.setPrUrl("https://github.com/example/repo/pull/50");
        julesSessionRepository.save(session);

        String designCheckDir = com.eneik.production.services.gate.DesignExcellenceGate.designCheckDir(task);
        String desktopPath = designCheckDir + "desktop-1440.png";
        String mobilePath = designCheckDir + "mobile-375.png";
        String headRef = "feature/design-" + task.getId();

        when(gitHubPullRequestService.parsePullNumber("https://github.com/example/repo/pull/50")).thenReturn(50);
        when(gitHubPullRequestService.fetchDiffText(any(), eq(50)))
                .thenReturn(Optional.of("+++ b/" + desktopPath + "\n+++ b/" + mobilePath + "\n"));
        when(gitHubPullRequestService.fetchPullRequestByNumber(any(), eq(50)))
                .thenReturn(Optional.of(new GitHubPullRequestService.GitHubPullRequest(
                        "https://github.com/example/repo/pull/50", 50, "Design gate fixture PR", headRef,
                        "fixture-author", false, "main", false, java.time.Instant.now())));
        when(gitHubPullRequestService.fetchFileBytes(any(), eq(headRef), eq(desktopPath)))
                .thenReturn(Optional.of(new byte[3000]));
        when(gitHubPullRequestService.fetchFileBytes(any(), eq(headRef), eq(mobilePath)))
                .thenReturn(Optional.of(new byte[1800]));
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
