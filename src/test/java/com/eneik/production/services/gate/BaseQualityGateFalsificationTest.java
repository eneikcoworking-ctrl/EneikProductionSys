package com.eneik.production.services.gate;

import com.eneik.production.models.persistence.LeanValue;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 4 empirical falsification test suite for {@link BaseQualityGate} and {@link GateCheck}.
 * <p>
 * Grounded in:
 * <ul>
 *   <li>DZHOZEF_RAZ_01_PROHIBITION_AS_CODE [D006, Raz]: Deontic prohibition of Lean waste and undetermined values in BusinessValueGate.</li>
 *   <li>KARL_POPPER_01_FALSIFICATION_HARNESS [D008, Popper]: Empirical refutability of specifications via Given/When/Then in AcceptanceCriteriaGate.</li>
 *   <li>DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX [D006, Raz]: Strict jural requirements for DoD, RepoUrl, and ActiveRole.</li>
 * </ul>
 */
public class BaseQualityGateFalsificationTest {

    private BaseQualityGate.BusinessValueGate businessValueGate;
    private BaseQualityGate.DoDGate dodGate;
    private BaseQualityGate.AcceptanceCriteriaGate acGate;
    private BaseQualityGate.RepoUrlGate repoUrlGate;
    private BaseQualityGate.ActiveRoleGate activeRoleGate;

    private TaskEntity task;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        businessValueGate = new BaseQualityGate.BusinessValueGate();
        dodGate = new BaseQualityGate.DoDGate();
        acGate = new BaseQualityGate.AcceptanceCriteriaGate();
        repoUrlGate = new BaseQualityGate.RepoUrlGate();
        activeRoleGate = new BaseQualityGate.ActiveRoleGate();

        objectMapper = new ObjectMapper();
        task = new TaskEntity();
        task.setId(UUID.randomUUID());

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setRepoUrl("https://github.com/eneikcoworking-ctrl/test-fiftieth");
        task.setProject(project);

        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-02");
        role.setActive(true);
        task.setRole(role);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("lean_value", LeanValue.essential.name());
        payload.put("dod", "Given order placed, When payment confirmed, Then invoice emitted");
        payload.put("acceptance_criteria", "Given valid cart, When checkout submitted, Then 200 OK returned");
        task.setPayload(payload);
    }

    @Test
    @DisplayName("Falsify Raz: BusinessValueGate strictly prohibits 'waste', 'undetermined' and unrecognized values")
    void falsifyRazProhibition_businessValueGateStrictlyProhibitsWasteAndUndetermined() {
        ObjectNode payload = (ObjectNode) task.getPayload();

        // 1. Prohibit waste
        payload.put("lean_value", "waste");
        GateResult wasteResult = businessValueGate.check(task);
        assertFalse(wasteResult.passed());
        assertThat(wasteResult.failureReasons()).contains("Business value cannot be 'waste'");

        // 2. Prohibit undetermined
        payload.put("lean_value", "undetermined");
        GateResult undeterminedResult = businessValueGate.check(task);
        assertFalse(undeterminedResult.passed());
        assertThat(undeterminedResult.failureReasons()).contains("Business value is undetermined; cannot verify lean value");

        // 3. Prohibit unrecognized / bogus value
        payload.put("lean_value", "nice_to_have");
        GateResult bogusResult = businessValueGate.check(task);
        assertFalse(bogusResult.passed());
        assertThat(bogusResult.failureReasons().get(0)).contains("Unrecognized business value 'nice_to_have'");

        // 4. Missing lean_value
        payload.remove("lean_value");
        GateResult missingResult = businessValueGate.check(task);
        assertFalse(missingResult.passed());
        assertThat(missingResult.failureReasons()).contains("Missing lean_value in payload");

        // 5. Permitted actionable values: essential and valuable
        payload.put("lean_value", "essential");
        assertTrue(businessValueGate.check(task).passed());

        payload.put("lean_value", "valuable");
        assertTrue(businessValueGate.check(task).passed());
    }

    @Test
    @DisplayName("Falsify Popper: AcceptanceCriteriaGate rejects criteria without full Given/When/Then falsification harness")
    void falsifyPopperFalsificationHarness_acceptanceCriteriaRequiresGivenWhenThenPattern() {
        ObjectNode payload = (ObjectNode) task.getPayload();

        // Missing Given
        payload.put("acceptance_criteria", "When button clicked, Then alert appears");
        GateResult missingGiven = acGate.check(task);
        assertFalse(missingGiven.passed());
        assertThat(missingGiven.failureReasons()).contains("Acceptance criteria must contain Given/When/Then pattern");

        // Missing When
        payload.put("acceptance_criteria", "Given user is admin, Then permission granted");
        GateResult missingWhen = acGate.check(task);
        assertFalse(missingWhen.passed());

        // Missing Then
        payload.put("acceptance_criteria", "Given valid user, When login submitted");
        GateResult missingThen = acGate.check(task);
        assertFalse(missingThen.passed());

        // Completely missing field
        payload.remove("acceptance_criteria");
        GateResult missingField = acGate.check(task);
        assertFalse(missingField.passed());
        assertThat(missingField.failureReasons()).contains("Missing acceptance_criteria in payload");

        // Full Given/When/Then satisfies Popper's falsification criterion
        payload.put("acceptance_criteria", "Given an auth token, When calling /api, Then return 200");
        assertTrue(acGate.check(task).passed());
    }

    @Test
    @DisplayName("Falsify Raz Matrix: DoDGate prohibits empty, whitespace or missing Definition of Done")
    void falsifyRazDutyMatrix_dodGateProhibitsBlankOrMissingDefinitionOfDone() {
        ObjectNode payload = (ObjectNode) task.getPayload();

        payload.put("dod", "");
        GateResult emptyResult = dodGate.check(task);
        assertFalse(emptyResult.passed());
        assertThat(emptyResult.failureReasons()).contains("Definition of Done (DoD) cannot be empty");

        payload.put("dod", "   ");
        GateResult whitespaceResult = dodGate.check(task);
        assertFalse(whitespaceResult.passed());

        payload.remove("dod");
        GateResult missingResult = dodGate.check(task);
        assertFalse(missingResult.passed());

        payload.put("dod", "Tests green and deployed");
        assertTrue(dodGate.check(task).passed());
    }

    @Test
    @DisplayName("Falsify Raz Matrix: RepoUrlGate prohibits missing project or blank repository URL")
    void falsifyRazDutyMatrix_repoUrlGateProhibitsMissingProjectOrBlankUrl() {
        task.setProject(null);
        GateResult nullProjectResult = repoUrlGate.check(task);
        assertFalse(nullProjectResult.passed());
        assertThat(nullProjectResult.failureReasons()).contains("Project repository URL cannot be null");

        ProjectEntity p = new ProjectEntity();
        task.setProject(p);

        p.setRepoUrl(null);
        assertFalse(repoUrlGate.check(task).passed());

        p.setRepoUrl("   ");
        assertFalse(repoUrlGate.check(task).passed());

        p.setRepoUrl("https://github.com/example/repo");
        assertTrue(repoUrlGate.check(task).passed());
    }

    @Test
    @DisplayName("Falsify Raz Matrix: ActiveRoleGate prohibits unassigned or inactive task roles")
    void falsifyRazDutyMatrix_activeRoleGateProhibitsMissingOrInactiveRole() {
        task.setRole(null);
        GateResult nullRoleResult = activeRoleGate.check(task);
        assertFalse(nullRoleResult.passed());
        assertThat(nullRoleResult.failureReasons()).contains("Task must have an assigned role");

        RoleEntity role = new RoleEntity();
        role.setTag("INACTIVE-ROLE-TAG");
        role.setActive(false);
        task.setRole(role);

        GateResult inactiveResult = activeRoleGate.check(task);
        assertFalse(inactiveResult.passed());
        assertThat(inactiveResult.failureReasons()).contains("Task role 'INACTIVE-ROLE-TAG' is not active");

        role.setActive(true);
        assertTrue(activeRoleGate.check(task).passed());
    }

    @Test
    @DisplayName("Falsify GateCheck Interface Contract: Default methods enforce TASK_SPEC stage, universal support and non-exempt status")
    void falsifyGateCheckInterfaceContract_defaultMethodsAreRigid() {
        // Base quality gates inherit default interface contract
        assertEquals(GateStage.TASK_SPEC, businessValueGate.stage());
        assertEquals(GateStage.TASK_SPEC, dodGate.stage());
        assertEquals(GateStage.TASK_SPEC, acGate.stage());
        assertEquals(GateStage.TASK_SPEC, repoUrlGate.stage());
        assertEquals(GateStage.TASK_SPEC, activeRoleGate.stage());

        assertTrue(businessValueGate.supports(task));
        assertTrue(dodGate.supports(task));
        assertTrue(acGate.supports(task));
        assertTrue(repoUrlGate.supports(task));
        assertTrue(activeRoleGate.supports(task));

        // Base quality gates are foundational specs, NEVER build-phase exempt
        assertFalse(businessValueGate.isBuildPhaseExempt());
        assertFalse(dodGate.isBuildPhaseExempt());
        assertFalse(acGate.isBuildPhaseExempt());
        assertFalse(repoUrlGate.isBuildPhaseExempt());
        assertFalse(activeRoleGate.isBuildPhaseExempt());
    }
}
