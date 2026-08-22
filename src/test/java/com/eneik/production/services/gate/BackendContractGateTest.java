package com.eneik.production.services.gate;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 2026-08-02 (Charter Pattern #12 - independent verification, not self-attestation): this gate used to
// read task.payload.changedFiles, a field self-reported by the implementer with no confirmed production
// writer - these tests used to stub that field directly. It now resolves the task's real PR via
// JulesSessionRepository and reads the actual unified diff via GitHubPullRequestService, so tests stub
// those instead of the payload field.
class BackendContractGateTest {
    private BackendContractGate gate;
    private JulesSessionRepository julesSessionRepository;
    private GitHubPullRequestService gitHubPullRequestService;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        julesSessionRepository = mock(JulesSessionRepository.class);
        gitHubPullRequestService = mock(GitHubPullRequestService.class);
        gate = new BackendContractGate(julesSessionRepository, gitHubPullRequestService);
        mapper = new ObjectMapper();
        // Default: no implementer session/PR found for any task, so realChangedFiles() resolves to
        // empty unless a test explicitly stubs one via stubRealDiff below.
        when(julesSessionRepository.findByTaskId(any())).thenReturn(List.of());
    }

    @Test
    // 2026-08-22 (ACP-105): this asserted that check() returns passed for a role the gate does
    // not apply to. GateOrchestrator never calls check() without supports(), so that path existed
    // only here - and it encoded the Evidence Algebra inverted: an unasked check counted as a
    // passed one, 5 where the corpus says 0. What production actually relies on is the line that
    // remains: supports() rejects the task, so the check is never reached.
    void doesNotSupportATaskOfAnotherRole() {
        TaskEntity task = createTask("BARCAN-TAG-11", null);
        assertThat(gate.supports(task)).isFalse();
    }

    @Test
    void shouldFailBackendTaskWithoutTestFile() {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("dod", "Should handle errors (400)");
        payload.put("acceptanceCriteria", "Validation is implemented");

        // No PR/diff stubbed - realChangedFiles() resolves to empty, same real-world shape as a task
        // whose PR genuinely has no test file.
        TaskEntity task = createTask("BARCAN-TAG-02", payload);
        GateResult result = gate.check(task);

        assertThat(result.passed()).isFalse();
        assertThat(result.failureReasons()).contains("missing test file (*Test.java)");
    }

    @Test
    void shouldFailBackendTaskWithoutErrorPatternsInDod() {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("dod", "Feature is complete");
        payload.put("acceptanceCriteria", "Validation is implemented");

        TaskEntity task = createTask("BARCAN-TAG-02", payload);
        stubRealDiff("ServiceTest.java");

        GateResult result = gate.check(task);

        assertThat(result.passed()).isFalse();
        assertThat(result.failureReasons()).contains("definition of done (dod) must mention error states (400, 401, 403, or error)");
    }

    @Test
    void shouldPassCorrectBackendTask() {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("dod", "Proper error handling (403)");
        payload.put("acceptanceCriteria", "Security auth check");

        TaskEntity task = createTask("BARCAN-TAG-07", payload);
        stubRealDiff("ServiceTest.java");

        assertThat(gate.supports(task)).isTrue();
        assertThat(gate.stage()).isEqualTo(GateStage.IMPLEMENTATION_RESULT);

        GateResult result = gate.check(task);

        assertThat(result.passed()).isTrue();
    }

    @Test
    void shouldFailWhenRealDiffHasNoTestFileRegardlessOfPayloadClaims() {
        // Regression guard for the self-attestation bug this gate used to have: even if a caller stuffed
        // a "changedFiles" claim into the payload, the gate must never look at it - only the real diff
        // decides. Stubbing a diff with no test file must still fail, independent of payload content.
        ObjectNode payload = mapper.createObjectNode();
        payload.putArray("changedFiles").add("ServiceTest.java");
        payload.put("dod", "Should handle errors (400)");
        payload.put("acceptanceCriteria", "Validation is implemented");

        TaskEntity task = createTask("BARCAN-TAG-02", payload);
        stubRealDiff("Service.java");

        GateResult result = gate.check(task);

        assertThat(result.passed()).isFalse();
        assertThat(result.failureReasons()).contains("missing test file (*Test.java)");
    }

    private void stubRealDiff(String... changedFilePaths) {
        JulesSessionEntity session = new JulesSessionEntity();
        session.setStatus("pr_opened");
        session.setPrUrl("https://github.com/acme/widgets/pull/42");
        when(julesSessionRepository.findByTaskId(any())).thenReturn(List.of(session));
        when(gitHubPullRequestService.parsePullNumber(anyString())).thenReturn(42);
        StringBuilder diff = new StringBuilder();
        for (String path : changedFilePaths) {
            diff.append("+++ b/").append(path).append("\n");
        }
        when(gitHubPullRequestService.fetchDiffText(any(), anyInt())).thenReturn(Optional.of(diff.toString()));
    }

    private TaskEntity createTask(String tag, ObjectNode payload) {
        TaskEntity task = new TaskEntity();
        RoleEntity role = new RoleEntity();
        role.setTag(tag);
        task.setRole(role);
        task.setPayload(payload);
        task.setProject(new ProjectEntity());
        return task;
    }
}
