package com.eneik.production.services.gate;

import com.eneik.production.models.persistence.TaskEntity;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@Order(210)
public class BackendContractGate implements GateCheck {
    private static final Set<String> BACKEND_TAGS = Set.of("BARCAN-TAG-02", "BARCAN-TAG-07");
    private static final String CHECK_NAME = "backend_contract";
    private static final Pattern ERROR_PATTERN = Pattern.compile("400|401|403|error", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTH_VALIDATION_PATTERN = Pattern.compile("auth|validation", Pattern.CASE_INSENSITIVE);

    @Override
    public GateStage stage() {
        return GateStage.IMPLEMENTATION_RESULT;
    }

    @Override
    public boolean supports(TaskEntity task) {
        return task != null
                && task.getRole() != null
                && BACKEND_TAGS.contains(task.getRole().getTag());
    }

    @Override
    public GateResult check(TaskEntity task) {
        String roleTag = task.getRole().getTag();
        if (!BACKEND_TAGS.contains(roleTag)) {
            return new GateResult(true, "not applicable", List.of());
        }

        List<String> failureReasons = new ArrayList<>();
        JsonNode payload = task.getPayload();
        if (payload == null) {
            return new GateResult(false, CHECK_NAME, List.of("task payload is missing"));
        }

        // 1. Проверить наличие файла теста (*Test.java)
        JsonNode changedFiles = payload.get("changedFiles");
        boolean hasTestFile = false;
        if (changedFiles != null && changedFiles.isArray()) {
            for (JsonNode file : changedFiles) {
                if (file.asText().endsWith("Test.java")) {
                    hasTestFile = true;
                    break;
                }
            }
        }
        if (!hasTestFile) {
            failureReasons.add("missing test file (*Test.java)");
        }

        // 2. Наличие паттернов error-состояний в task.dod (текстовый поиск "400"|"401"|"403"|"error")
        JsonNode dod = payload.get("dod");
        if (dod == null || !ERROR_PATTERN.matcher(dod.asText()).find()) {
            failureReasons.add("definition of done (dod) must mention error states (400, 401, 403, or error)");
        }

        // 3. Наличие упоминания auth/validation в acceptance_criteria
        JsonNode acceptanceCriteria = payload.has("acceptanceCriteria")
                ? payload.get("acceptanceCriteria")
                : payload.get("acceptance_criteria");
        if (acceptanceCriteria == null || !AUTH_VALIDATION_PATTERN.matcher(acceptanceCriteria.asText()).find()) {
            failureReasons.add("acceptance criteria must mention auth or validation");
        }

        boolean passed = failureReasons.isEmpty();
        return new GateResult(passed, CHECK_NAME, failureReasons);
    }
}
