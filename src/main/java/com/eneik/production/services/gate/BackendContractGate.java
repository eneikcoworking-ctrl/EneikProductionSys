package com.eneik.production.services.gate;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.services.github.GitHubPullRequestService;
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

    private final JulesSessionRepository julesSessionRepository;
    private final GitHubPullRequestService gitHubPullRequestService;

    public BackendContractGate(JulesSessionRepository julesSessionRepository,
                                GitHubPullRequestService gitHubPullRequestService) {
        this.julesSessionRepository = julesSessionRepository;
        this.gitHubPullRequestService = gitHubPullRequestService;
    }

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
    public boolean isBuildPhaseExempt() {
        return true;
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

        // 1. Test-file presence, from the PR's own real diff (Charter Pattern #12 - independent
        // verification, not self-attestation: this used to read payload.changedFiles, a field no
        // production code path was ever found to actually populate - reading the real unified diff
        // instead of a self-reported/always-empty list closes both problems at once).
        List<String> changedFiles = realChangedFiles(task);
        boolean hasTestFile = changedFiles.stream().anyMatch(f -> f.endsWith("Test.java"));
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

    private List<String> realChangedFiles(TaskEntity task) {
        JulesSessionEntity session = resolveSessionWithPr(task);
        if (session == null || task.getProject() == null) {
            return List.of();
        }
        Integer pullNumber = gitHubPullRequestService.parsePullNumber(session.getPrUrl());
        if (pullNumber == null) {
            return List.of();
        }
        return gitHubPullRequestService.fetchDiffText(task.getProject(), pullNumber)
                .map(GitHubPullRequestService::changedFilePathsFromDiff)
                .orElse(List.of());
    }

    // Same implementer-session lookup used elsewhere (JulesDispatchService.applyReviewVerdictToTask):
    // prefer the session sitting at "pr_opened", else fall back to any session that already has a PR.
    private JulesSessionEntity resolveSessionWithPr(TaskEntity task) {
        List<JulesSessionEntity> sessions = julesSessionRepository.findByTaskId(task.getId());
        return sessions.stream()
                .filter(s -> "pr_opened".equals(s.getStatus()))
                .findFirst()
                .orElseGet(() -> sessions.stream().filter(s -> s.getPrUrl() != null).findFirst().orElse(null));
    }
}
