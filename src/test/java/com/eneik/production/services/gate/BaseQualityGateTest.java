package com.eneik.production.services.gate;

import com.eneik.production.models.persistence.LeanValue;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BaseQualityGateTest {

    private BaseQualityGate.BusinessValueGate businessValueGate = new BaseQualityGate.BusinessValueGate();
    private BaseQualityGate.DoDGate dodGate = new BaseQualityGate.DoDGate();
    private BaseQualityGate.AcceptanceCriteriaGate acGate = new BaseQualityGate.AcceptanceCriteriaGate();
    private BaseQualityGate.RepoUrlGate repoUrlGate = new BaseQualityGate.RepoUrlGate();
    private BaseQualityGate.ActiveRoleGate activeRoleGate = new BaseQualityGate.ActiveRoleGate();

    private TaskEntity task;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setup() {
        task = new TaskEntity();
        ProjectEntity project = new ProjectEntity();
        project.setRepoUrl("http://github.com/test/repo");
        task.setProject(project);

        RoleEntity role = new RoleEntity();
        role.setTag("TEST-ROLE");
        role.setActive(true);
        task.setRole(role);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("lean_value", LeanValue.essential.name());
        payload.put("dod", "Test DoD");
        payload.put("acceptance_criteria", "Given something, When action, Then result");
        task.setPayload(payload);
    }

    @Test
    public void testBusinessValuePositive() {
        GateResult result = businessValueGate.check(task);
        assertTrue(result.passed());
    }

    @Test
    public void testBusinessValueNegative() {
        ((ObjectNode) task.getPayload()).put("lean_value", LeanValue.waste.name());
        GateResult result = businessValueGate.check(task);
        assertFalse(result.passed());
        assertTrue(result.failureReasons().contains("Business value cannot be 'waste'"));
    }

    @Test
    public void testDoDPositive() {
        GateResult result = dodGate.check(task);
        assertTrue(result.passed());
    }

    @Test
    public void testDoDNegative() {
        ((ObjectNode) task.getPayload()).put("dod", "");
        GateResult result = dodGate.check(task);
        assertFalse(result.passed());
        assertTrue(result.failureReasons().contains("Definition of Done (DoD) cannot be empty"));
    }

    @Test
    public void testAcceptanceCriteriaPositive() {
        GateResult result = acGate.check(task);
        assertTrue(result.passed());
    }

    @Test
    public void testAcceptanceCriteriaNegative() {
        ((ObjectNode) task.getPayload()).put("acceptance_criteria", "Invalid criteria");
        GateResult result = acGate.check(task);
        assertFalse(result.passed());
        assertTrue(result.failureReasons().contains("Acceptance criteria must contain Given/When/Then pattern"));
    }

    @Test
    public void testRepoUrlPositive() {
        GateResult result = repoUrlGate.check(task);
        assertTrue(result.passed());
    }

    @Test
    public void testRepoUrlNegative() {
        task.getProject().setRepoUrl(null);
        GateResult result = repoUrlGate.check(task);
        assertFalse(result.passed());
        assertTrue(result.failureReasons().contains("Project repository URL cannot be null"));
    }

    @Test
    public void testActiveRolePositive() {
        GateResult result = activeRoleGate.check(task);
        assertTrue(result.passed());
    }

    @Test
    public void testActiveRoleNegative() {
        task.getRole().setActive(false);
        GateResult result = activeRoleGate.check(task);
        assertFalse(result.passed());
        assertTrue(result.failureReasons().contains("Task role 'TEST-ROLE' is not active"));
    }
}
