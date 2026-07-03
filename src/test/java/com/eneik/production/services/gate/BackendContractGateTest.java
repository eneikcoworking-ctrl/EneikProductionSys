package com.eneik.production.services.gate;

import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BackendContractGateTest {
    private BackendContractGate gate;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        gate = new BackendContractGate();
        mapper = new ObjectMapper();
    }

    @Test
    void shouldPassNonBackendTask() {
        TaskEntity task = createTask("BARCAN-TAG-11", null);
        assertThat(gate.supports(task)).isFalse();

        GateResult result = gate.check(task);
        assertThat(result.passed()).isTrue();
        assertThat(result.checkName()).isEqualTo("not applicable");
    }

    @Test
    void shouldFailBackendTaskWithoutTestFile() {
        ObjectNode payload = mapper.createObjectNode();
        payload.putArray("changedFiles").add("Service.java");
        payload.put("dod", "Should handle errors (400)");
        payload.put("acceptanceCriteria", "Validation is implemented");

        TaskEntity task = createTask("BARCAN-TAG-02", payload);
        GateResult result = gate.check(task);

        assertThat(result.passed()).isFalse();
        assertThat(result.failureReasons()).contains("missing test file (*Test.java)");
    }

    @Test
    void shouldFailBackendTaskWithoutErrorPatternsInDod() {
        ObjectNode payload = mapper.createObjectNode();
        payload.putArray("changedFiles").add("ServiceTest.java");
        payload.put("dod", "Feature is complete");
        payload.put("acceptanceCriteria", "Validation is implemented");

        TaskEntity task = createTask("BARCAN-TAG-02", payload);
        GateResult result = gate.check(task);

        assertThat(result.passed()).isFalse();
        assertThat(result.failureReasons()).contains("definition of done (dod) must mention error states (400, 401, 403, or error)");
    }

    @Test
    void shouldPassCorrectBackendTask() {
        ObjectNode payload = mapper.createObjectNode();
        payload.putArray("changedFiles").add("ServiceTest.java");
        payload.put("dod", "Proper error handling (403)");
        payload.put("acceptanceCriteria", "Security auth check");

        TaskEntity task = createTask("BARCAN-TAG-07", payload);
        assertThat(gate.supports(task)).isTrue();
        assertThat(gate.stage()).isEqualTo(GateStage.IMPLEMENTATION_RESULT);

        GateResult result = gate.check(task);

        assertThat(result.passed()).isTrue();
    }

    private TaskEntity createTask(String tag, ObjectNode payload) {
        TaskEntity task = new TaskEntity();
        RoleEntity role = new RoleEntity();
        role.setTag(tag);
        task.setRole(role);
        task.setPayload(payload);
        return task;
    }
}
