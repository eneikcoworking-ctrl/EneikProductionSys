package com.eneik.production.services.gate;

import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DesignExcellenceGateTest {
    private DesignExcellenceGate gate;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        gate = new DesignExcellenceGate();
        mapper = new ObjectMapper();
    }

    @Test
    void shouldPassNonUiTask() {
        TaskEntity task = createTask("BARCAN-TAG-02", null);
        GateResult result = gate.check(task);
        assertThat(result.passed()).isTrue();
        assertThat(result.failureReasons()).contains("not applicable to this role");
    }

    @Test
    void shouldFailUiTaskWithoutMobile() {
        ObjectNode payload = mapper.createObjectNode();
        ArrayNode screenshots = payload.putArray("screenshotUrls");
        screenshots.addObject().put("url", "desktop_1440.png").put("size", 2000);

        TaskEntity task = createTask("BARCAN-TAG-11", payload);
        GateResult result = gate.check(task);

        assertThat(result.passed()).isFalse();
        assertThat(result.failureReasons()).contains("missing mobile screenshot (375px)");
    }

    @Test
    void shouldPassUiTaskWithBothScreenshotsAndCorrectSizes() {
        ObjectNode payload = mapper.createObjectNode();
        ArrayNode screenshots = payload.putArray("screenshotUrls");
        screenshots.addObject().put("url", "desktop_1440.png").put("size", 2000);
        screenshots.addObject().put("url", "mobile_375.png").put("size", 1500);

        TaskEntity task = createTask("BARCAN-TAG-11", payload);
        GateResult result = gate.check(task);

        assertThat(result.passed()).isTrue();
    }

    @Test
    void shouldFailIfScreenshotsHaveSameSize() {
        ObjectNode payload = mapper.createObjectNode();
        ArrayNode screenshots = payload.putArray("screenshotUrls");
        screenshots.addObject().put("url", "desktop_1440.png").put("size", 2000);
        screenshots.addObject().put("url", "mobile_375.png").put("size", 2000);

        TaskEntity task = createTask("BARCAN-TAG-11", payload);
        GateResult result = gate.check(task);

        // score: 30 (has_both) + 0 (responsive_ok) + 30 (visual_qa_ok) = 60 < 70
        assertThat(result.passed()).isFalse();
        assertThat(result.failureReasons()).contains("responsive check failed: screenshots have identical file sizes");
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
