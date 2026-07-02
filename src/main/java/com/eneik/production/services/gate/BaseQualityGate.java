package com.eneik.production.services.gate;

import com.eneik.production.models.persistence.LeanValue;
import com.eneik.production.models.persistence.TaskEntity;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

public class BaseQualityGate {

    @Component
    public static class BusinessValueGate implements GateCheck {
        @Override
        public GateResult check(TaskEntity task) {
            List<String> failures = new ArrayList<>();
            JsonNode payload = task.getPayload();
            if (payload == null || !payload.has("lean_value")) {
                failures.add("Missing lean_value in payload");
            } else {
                String leanValue = payload.get("lean_value").asText();
                if (LeanValue.waste.name().equals(leanValue)) {
                    failures.add("Business value cannot be 'waste'");
                }
            }
            return new GateResult(failures.isEmpty(), "Business Value Check", failures);
        }
    }

    @Component
    public static class DoDGate implements GateCheck {
        @Override
        public GateResult check(TaskEntity task) {
            List<String> failures = new ArrayList<>();
            JsonNode payload = task.getPayload();
            if (payload == null || !payload.has("dod") || payload.get("dod").asText().trim().isEmpty()) {
                failures.add("Definition of Done (DoD) cannot be empty");
            }
            return new GateResult(failures.isEmpty(), "DoD Check", failures);
        }
    }

    @Component
    public static class AcceptanceCriteriaGate implements GateCheck {
        @Override
        public GateResult check(TaskEntity task) {
            List<String> failures = new ArrayList<>();
            JsonNode payload = task.getPayload();
            if (payload == null || !payload.has("acceptance_criteria")) {
                failures.add("Missing acceptance_criteria in payload");
            } else {
                String ac = payload.get("acceptance_criteria").asText();
                if (!ac.contains("Given") || !ac.contains("When") || !ac.contains("Then")) {
                    failures.add("Acceptance criteria must contain Given/When/Then pattern");
                }
            }
            return new GateResult(failures.isEmpty(), "Acceptance Criteria Check", failures);
        }
    }

    @Component
    public static class RepoUrlGate implements GateCheck {
        @Override
        public GateResult check(TaskEntity task) {
            List<String> failures = new ArrayList<>();
            if (task.getProject() == null || task.getProject().getRepoUrl() == null || task.getProject().getRepoUrl().trim().isEmpty()) {
                failures.add("Project repository URL cannot be null");
            }
            return new GateResult(failures.isEmpty(), "Repo URL Check", failures);
        }
    }

    @Component
    public static class ActiveRoleGate implements GateCheck {
        @Override
        public GateResult check(TaskEntity task) {
            List<String> failures = new ArrayList<>();
            if (task.getRole() == null) {
                failures.add("Task must have an assigned role");
            } else if (!task.getRole().isActive()) {
                failures.add("Task role '" + task.getRole().getTag() + "' is not active");
            }
            return new GateResult(failures.isEmpty(), "Active Role Check", failures);
        }
    }
}
