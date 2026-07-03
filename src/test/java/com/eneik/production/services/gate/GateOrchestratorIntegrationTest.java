package com.eneik.production.services.gate;

import com.eneik.production.models.persistence.LeanValue;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.repositories.TaskRepository;
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

    @Test
    void fullGateRunsDesignGateForUiTask() {
        ObjectNode payload = basePayload();
        ArrayNode screenshots = payload.putArray("screenshotUrls");
        screenshots.addObject().put("url", "desktop_1440.png").put("size", 3000);
        screenshots.addObject().put("url", "mobile_375.png").put("size", 1800);

        TaskEntity task = createTask("BARCAN-TAG-11", payload);

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
    void fullGateRunsBackendGateForBackendTask() {
        ObjectNode payload = basePayload();
        payload.putArray("changedFiles").add("src/test/java/com/eneik/LeadControllerTest.java");

        TaskEntity task = createTask("BARCAN-TAG-02", payload);

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

        gateOrchestrator.runQualityGate(task);

        TaskEntity refreshed = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(refreshed.isQualityGatePassed()).isTrue();
        assertThat(checkNames(refreshed)).containsExactlyElementsOf(BASE_CHECKS);
        assertThat(checkNames(refreshed)).doesNotContain("design_excellence", "backend_contract", "not applicable");
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
