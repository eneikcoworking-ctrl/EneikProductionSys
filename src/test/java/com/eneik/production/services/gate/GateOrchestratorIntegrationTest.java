package com.eneik.production.services.gate;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.FeatureEntity;
import com.eneik.production.models.persistence.LeanValue;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GateOrchestratorIntegrationTest {

    private static final List<String> BASE_CHECKS = List.of(
            "Business Value Check",
            "DoD Check",
            "Acceptance Criteria Check",
            "Repo URL Check",
            "Active Role Check"
    );

    @Autowired
    private GateOrchestrator gateOrchestrator;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private RoleRepository roleRepository;

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
    // PR diff via GitHubPullRequestService. Mocked here (real GitHub calls are disabled in the test
    // profile anyway) so fullGateRunsBackendGateForBackendTaskPastBuildPhase can simulate a real diff.
    @MockBean
    private GitHubPullRequestService gitHubPullRequestService;

    @Test
    void fullGateRunsDesignGateForUiTaskPastBuildPhase() {
        TaskEntity task = createTask("BARCAN-TAG-11", basePayload());
        markProjectPastBuildPhase(task.getProject());
        stubDesignScreenshotsForTask(task);

        gateOrchestrator.runQualityGate(task);

        TaskEntity refreshed = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(refreshed.isQualityGatePassed()).isTrue();
        assertThat(refreshed.getQualityGateReport().path("passed").asBoolean()).isTrue();
        assertThat(checkNames(refreshed)).containsExactlyElementsOf(
                append(BASE_CHECKS, "design_excellence")
        );
        assertThat(checkNames(refreshed)).doesNotContain("backend_contract", "not applicable");
    }

    @Test
    void fullGateRunsBackendGateForBackendTaskPastBuildPhase() {
        ObjectNode payload = basePayload();

        TaskEntity task = createTask("BARCAN-TAG-02", payload);
        markProjectPastBuildPhase(task.getProject());
        stubRealDiffForTask(task, "src/test/java/com/eneik/LeadControllerTest.java");

        gateOrchestrator.runQualityGate(task);

        TaskEntity refreshed = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(refreshed.isQualityGatePassed()).isTrue();
        assertThat(refreshed.getQualityGateReport().path("passed").asBoolean()).isTrue();
        assertThat(checkNames(refreshed)).containsExactlyElementsOf(
                append(BASE_CHECKS, "backend_contract")
        );
        assertThat(checkNames(refreshed)).doesNotContain("design_excellence", "not applicable");
    }

    @Test
    void fullGateSkipsSpecializedGatesForIrrelevantRole() {
        TaskEntity task = createTask("BARCAN-TAG-05", basePayload());
        markProjectPastBuildPhase(task.getProject());

        gateOrchestrator.runQualityGate(task);

        TaskEntity refreshed = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(refreshed.isQualityGatePassed()).isTrue();
        assertThat(checkNames(refreshed)).containsExactlyElementsOf(BASE_CHECKS);
        assertThat(checkNames(refreshed)).doesNotContain("design_excellence", "backend_contract", "not applicable");
    }

    // Trust is maximal by design during a project's build phase (no merged client deliverables yet) -
    // GateOrchestrator must skip the mechanical polish gates (design_excellence, backend_contract)
    // entirely, even for a role/payload that would otherwise trigger and pass them, leaving only the
    // foundational sanity checks (BASE_CHECKS) and the role's own philosophical refusal-criteria filter
    // (enforced separately in AutoMergeService) active.
    @Test
    void buildPhaseSkipsPolishGatesEvenForOtherwisePassingUiTask() {
        ObjectNode payload = basePayload();
        ArrayNode screenshots = payload.putArray("screenshotUrls");
        screenshots.addObject().put("url", "desktop_1440.png").put("size", 3000);
        screenshots.addObject().put("url", "mobile_375.png").put("size", 1800);

        TaskEntity task = createTask("BARCAN-TAG-11", payload);
        // Fresh project, zero merged client deliverables - still in build phase by default.

        gateOrchestrator.runQualityGate(task);

        TaskEntity refreshed = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(refreshed.isQualityGatePassed()).isTrue();
        assertThat(checkNames(refreshed)).containsExactlyElementsOf(BASE_CHECKS);
        assertThat(checkNames(refreshed)).doesNotContain("design_excellence", "backend_contract");
    }

    @Test
    void buildPhaseSkipsPolishGatesEvenForOtherwiseFailingBackendTask() {
        // No changedFiles, no *Test.java - would normally fail backend_contract outright.
        TaskEntity task = createTask("BARCAN-TAG-02", basePayload());
        // Fresh project, zero merged client deliverables - still in build phase by default.

        gateOrchestrator.runQualityGate(task);

        TaskEntity refreshed = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(refreshed.isQualityGatePassed()).isTrue();
        assertThat(checkNames(refreshed)).containsExactlyElementsOf(BASE_CHECKS);
        assertThat(checkNames(refreshed)).doesNotContain("design_excellence", "backend_contract");
    }

    private void markProjectPastBuildPhase(ProjectEntity project) {
        WishlistEntity root = new WishlistEntity();
        root.setProjectId(project.getId());
        root.setSource(WishlistSource.client);
        root.setStatus(WishlistStatus.converted_to_task);
        root.setContent("Compiled client brief used by the build-phase fixture.");
        root = wishlistRepository.save(root);

        FeatureEntity feature = new FeatureEntity();
        feature.setProjectId(project.getId());
        feature.setRootWishlistId(root.getId());
        feature.setTitle("Fixture feature");
        feature = featureRepository.save(feature);

        WishlistEntity deliverable = new WishlistEntity();
        deliverable.setProjectId(project.getId());
        deliverable.setSource(WishlistSource.client);
        deliverable.setStatus(WishlistStatus.converted_to_task);
        deliverable.setFeatureId(feature.getId());
        deliverable.setContent("Client deliverable used to simulate a merged build phase.");
        deliverable.setCompiledByRole("BARCAN-TAG-09");
        deliverable = wishlistRepository.save(deliverable);

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
        mergedTask = taskRepository.save(mergedTask);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setTaskId(mergedTask.getId());
        session.setExternalSessionId("sessions/fixture");
        session.setStatus("completed");
        session = julesSessionRepository.save(session);

        PrReviewEntity review = new PrReviewEntity();
        review.setJulesSessionId(session.getId());
        review.setPrUrl("https://github.com/example/repo/pull/1");
        review.setCiStatus("success");
        review.setRiskLevel("low");
        review.setMerged(true);
        review.setHasCode(true);
        prReviewRepository.save(review);
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

    // 2026-08-14 (bug-hunt sweep): fullGateRunsDesignGateForUiTaskPastBuildPhase used to stub a now-dead
    // payload.screenshotUrls field from before the 2026-08-03 DesignExcellenceGate rewrite (see that
    // class's own doc comment) - with no PR/session stub at all, the gate deterministically failed with
    // "no PR found to verify design screenshots against" and this test was silently exercising nothing.
    // Mirrors stubRealDiffForTask's real-PR-diff pattern, plus the two real screenshot-file fetches
    // DesignExcellenceGate.check actually performs.
    private void stubDesignScreenshotsForTask(TaskEntity task) {
        JulesSessionEntity session = new JulesSessionEntity();
        session.setTaskId(task.getId());
        session.setExternalSessionId("sessions/fixture-design-gate-" + task.getId());
        session.setStatus("pr_opened");
        session.setPrUrl("https://github.com/example/repo/pull/50");
        julesSessionRepository.save(session);

        String designCheckDir = DesignExcellenceGate.designCheckDir(task);
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

    // 2026-08-18: the same boolean is written by two entry points answering two different questions -
    // runTaskSpecGate at task CREATION ("is this well specified?") and runQualityGate at implementer
    // COMPLETION ("did the work pass every applicable check?"). Both writes are true, which is why the
    // substitution cannot be caught by checking whether the value is correct. These two tests check what
    // the verdict is ABOUT.
    @Test
    void specGateVerdictSaysItCoversOnlyTheSpecification() {
        TaskEntity task = createTask("BARCAN-TAG-01", basePayload());

        gateOrchestrator.runTaskSpecGate(task);

        TaskEntity refreshed = taskRepository.findById(task.getId()).orElseThrow();
        JsonNode stages = refreshed.getQualityGateReport().path("stages");
        assertThat(stages.isArray()).isTrue();
        assertThat(StreamSupport.stream(stages.spliterator(), false).map(JsonNode::asText).toList())
                .containsExactly("TASK_SPEC");
        // The point of the finding: this task can be perfectly specified and still have delivered nothing.
        // BARCAN-TAG-01 is the tag the live blocking task f163e834 carries.
        assertThat(refreshed.isQualityGatePassed()).isTrue();
    }

    @Test
    void fullGateVerdictSaysItCoversEveryStage() {
        TaskEntity task = createTask("BARCAN-TAG-01", basePayload());

        gateOrchestrator.runQualityGate(task);

        TaskEntity refreshed = taskRepository.findById(task.getId()).orElseThrow();
        JsonNode stages = refreshed.getQualityGateReport().path("stages");
        assertThat(StreamSupport.stream(stages.spliterator(), false).map(JsonNode::asText).toList())
                .containsExactlyInAnyOrderElementsOf(
                        java.util.Arrays.stream(GateStage.values()).map(Enum::name).toList());
    }

    private TaskEntity createTask(String roleTag, ObjectNode payload) {
        ProjectEntity project = new ProjectEntity();
        String suffix = UUID.randomUUID().toString();
        project.setName("Gate Test " + suffix);
        project.setSlug("gate-test-" + suffix);
        project.setRepositoryName("gate-test-repo-" + suffix);
        project.setRepoUrl("https://github.com/eneikcoworking-ctrl/gate-test-" + suffix);
        project = projectRepository.save(project);

        RoleEntity role = roleRepository.findById(roleTag).orElseThrow();

        TaskEntity task = new TaskEntity();
        task.setProject(project);
        task.setRole(role);
        task.setDescription("Gate orchestration test");
        task.setPayload(payload);
        task.setStatus(TaskStatus.queued);
        return taskRepository.save(task);
    }

    private List<String> checkNames(TaskEntity task) {
        JsonNode checks = task.getQualityGateReport().path("checks");
        return StreamSupport.stream(checks.spliterator(), false)
                .map(node -> node.path("name").asText())
                .toList();
    }

    private List<String> append(List<String> checks, String specializedCheck) {
        List<String> orderedChecks = new ArrayList<>(checks);
        orderedChecks.add(specializedCheck);
        return orderedChecks;
    }
}
