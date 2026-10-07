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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Stage 4 empirical falsification test suite for {@link BackendContractGate}.
 * <p>
 * Grounded in:
 * <ul>
 *   <li>LUDVIG_VITGENSHTEYN_01_ANTI_MIRROR_TELEMETRY [D013, Wittgenstein]: Real GitHub unified diff inspection
 *       overrides self-attested payload claims; fake changedFiles payload arrays are ignored.</li>
 *   <li>KARL_POPPER_01_FALSIFICATION_HARNESS [D008, Popper]: Multi-criteria refutation: missing *Test.java,
 *       missing 400/401/403/error in DoD, and missing auth/validation in Acceptance Criteria.</li>
 *   <li>DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX [D006, Raz]: Deontic scoping to backend roles (BARCAN-TAG-02/07),
 *       fail-closed on null payload or missing implementer PR session, and buildPhaseExempt=true.</li>
 * </ul>
 */
public class BackendContractGateFalsificationTest {

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
        when(julesSessionRepository.findByTaskId(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Falsify Wittgenstein: Real unified diff inspection overrides payload claims")
    void falsifyWittgensteinAntiMirror_realDiffInspectionOverridesSelfAttestedPayloadClaims() {
        // Agent injects a self-serving payload claiming Test.java exists
        ObjectNode payload = mapper.createObjectNode();
        payload.putArray("changedFiles").add("OrderServiceTest.java");
        payload.put("dod", "Handles HTTP 400 validation error");
        payload.put("acceptanceCriteria", "Auth and validation required");

        TaskEntity task = createTask("BARCAN-TAG-02", payload);

        // But empirical physical reality on GitHub diff has NO test file
        stubRealDiff(task, "src/main/java/com/eneik/OrderService.java");

        GateResult result = gate.check(task);

        assertFalse(result.passed(), "Gate must fail when real GitHub diff lacks *Test.java despite payload claims");
        assertThat(result.failureReasons()).contains("missing test file (*Test.java)");
    }

    @Test
    @DisplayName("Falsify Popper: Contract rejects absence of test file, error codes in DoD, or auth in criteria")
    void falsifyPopperFalsificationHarness_rejectsIncompleteContractSpecifications() {
        TaskEntity task = createTask("BARCAN-TAG-02", mapper.createObjectNode());
        stubRealDiff(task, "src/main/java/com/eneik/Controller.java"); // No test file

        // Payload with no error pattern in DoD and no auth pattern in acceptanceCriteria
        ObjectNode payload = (ObjectNode) task.getPayload();
        payload.put("dod", "Feature is implemented cleanly");
        payload.put("acceptance_criteria", "User sees happy path dashboard");

        GateResult result = gate.check(task);

        assertFalse(result.passed());
        assertEquals(3, result.failureReasons().size(), "All 3 missing contract dimensions must be recorded as failures");
        assertThat(result.failureReasons()).contains("missing test file (*Test.java)");
        assertThat(result.failureReasons()).contains("definition of done (dod) must mention error states (400, 401, 403, or error)");
        assertThat(result.failureReasons()).contains("acceptance criteria must mention auth or validation");
    }

    @Test
    @DisplayName("Falsify Popper: Full contract compliance passes with flying colors across alternative field names")
    void falsifyPopperFalsificationHarness_passesWhenAllEmpiricalContractConditionsAreMet() {
        // Both acceptanceCriteria and acceptance_criteria supported
        ObjectNode payload = mapper.createObjectNode();
        payload.put("dod", "When validation fails, return 401 error");
        payload.put("acceptance_criteria", "Given valid credentials, auth validation passes");

        TaskEntity task = createTask("BARCAN-TAG-07", payload);
        stubRealDiff(task, "src/main/java/com/eneik/SecurityFilter.java", "src/test/java/com/eneik/SecurityFilterTest.java");

        GateResult result = gate.check(task);

        assertTrue(result.passed(), "Must pass when real diff has *Test.java, DoD has 401/error, and criteria has auth/validation");
        assertThat(result.failureReasons()).isEmpty();
    }

    @Test
    @DisplayName("Falsify Raz Matrix: Deontic role scoping restricts support strictly to BARCAN-TAG-02 and BARCAN-TAG-07")
    void falsifyRazDutyMatrix_supportsRestrictedToBackendTagsOnly() {
        assertTrue(gate.supports(createTask("BARCAN-TAG-02", null)));
        assertTrue(gate.supports(createTask("BARCAN-TAG-07", null)));

        assertFalse(gate.supports(createTask("BARCAN-TAG-01", null)));
        assertFalse(gate.supports(createTask("BARCAN-TAG-05", null)));
        assertFalse(gate.supports(createTask("BARCAN-TAG-09", null)));
        assertFalse(gate.supports(createTask("BARCAN-TAG-11", null)));
        assertFalse(gate.supports(createTask("BARCAN-TAG-13", null)));
        assertFalse(gate.supports(null));

        TaskEntity taskNoRole = new TaskEntity();
        assertFalse(gate.supports(taskNoRole));
    }

    @Test
    @DisplayName("Falsify Raz Matrix: Fail-closed on missing payload or missing implementer PR session")
    void falsifyRazDutyMatrix_failClosedOnMissingPayloadOrMissingSession() {
        // 1. Missing payload
        TaskEntity taskNullPayload = createTask("BARCAN-TAG-02", null);
        GateResult nullPayloadResult = gate.check(taskNullPayload);
        assertFalse(nullPayloadResult.passed());
        assertThat(nullPayloadResult.failureReasons()).contains("task payload is missing");

        // 2. Missing implementer session/PR
        ObjectNode payload = mapper.createObjectNode();
        payload.put("dod", "error 400");
        payload.put("acceptanceCriteria", "auth validation");
        TaskEntity taskNoSession = createTask("BARCAN-TAG-02", payload);
        when(julesSessionRepository.findByTaskId(taskNoSession.getId())).thenReturn(List.of());

        GateResult noSessionResult = gate.check(taskNoSession);
        assertFalse(noSessionResult.passed());
        // Real changed files resolves to empty -> fails missing test file
        assertThat(noSessionResult.failureReasons()).contains("missing test file (*Test.java)");
    }

    @Test
    @DisplayName("Falsify GateCheck Contract: Stage is IMPLEMENTATION_RESULT and build phase exempt is true")
    void falsifyGateCheckContract_stageAndBuildPhaseExemptAreRigid() {
        assertEquals(GateStage.IMPLEMENTATION_RESULT, gate.stage());
        assertTrue(gate.isBuildPhaseExempt(), "BackendContractGate is a mechanical polish gate, exempt during build phase");
    }

    private void stubRealDiff(TaskEntity task, String... changedFilePaths) {
        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTaskId(task.getId());
        session.setStatus("pr_opened");
        session.setPrUrl("https://github.com/eneikcoworking-ctrl/test-fiftieth/pull/105");

        when(julesSessionRepository.findByTaskId(task.getId())).thenReturn(List.of(session));
        when(gitHubPullRequestService.parsePullNumber(session.getPrUrl())).thenReturn(105);

        StringBuilder diff = new StringBuilder();
        for (String path : changedFilePaths) {
            diff.append("+++ b/").append(path).append("\n");
        }
        when(gitHubPullRequestService.fetchDiffText(task.getProject(), 105))
                .thenReturn(Optional.of(diff.toString()));
    }

    private TaskEntity createTask(String tag, ObjectNode payload) {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        if (tag != null) {
            RoleEntity role = new RoleEntity();
            role.setTag(tag);
            role.setActive(true);
            task.setRole(role);
        }
        task.setPayload(payload);
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setRepoUrl("https://github.com/eneikcoworking-ctrl/test-fiftieth");
        task.setProject(project);
        return task;
    }
}
