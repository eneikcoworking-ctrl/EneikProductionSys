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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void fullGateRunsDesignGateForUiTaskPastBuildPhase() {
        ObjectNode payload = basePayload();
        ArrayNode screenshots = payload.putArray("screenshotUrls");
        screenshots.addObject().put("url", "desktop_1440.png").put("size", 3000);
        screenshots.addObject().put("url", "mobile_375.png").put("size", 1800);

        TaskEntity task = createTask("BARCAN-TAG-11", payload);
        markProjectPastBuildPhase(task.getProject());

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
        payload.putArray("changedFiles").add("src/test/java/com/eneik/LeadControllerTest.java");

        TaskEntity task = createTask("BARCAN-TAG-02", payload);
        markProjectPastBuildPhase(task.getProject());

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

        RoleEntity role = roleRepository.findById("BARCAN-TAG-09").orElseThrow();
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
        prReviewRepository.save(review);
    }

    private ObjectNode basePayload() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("lean_value", LeanValue.essential.name());
        payload.put("dod", "Given API error 400 is handled, When auth validation fails, Then the task is done");
        payload.put("acceptance_criteria", "Given valid auth, When validation runs, Then success is visible");
        return payload;
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
