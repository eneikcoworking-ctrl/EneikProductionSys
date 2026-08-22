package com.eneik.production.services.gate;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Phase C (design/QA acceptance redesign, 2026-08-04, live incident: "Core Knowledge Base Portal" epic
 * stuck at 6/11 - 2 of the 5 unfulfilled items were BARCAN-TAG-06 QA tasks whose real, correct outcome was
 * a merged PR with ZERO file changes: pure verification, nothing new to add. CodeChangeClassifier.hasCode
 * returns false unconditionally for an empty diff, so QA work could never satisfy the readiness formula's
 * hasCode requirement - unlike a code-producing task, QA is Delivery-layer work (verifying the product),
 * not Product-layer work (producing it), and needs its own acceptance criterion grounded in real
 * verification evidence, not a source-code diff.
 *
 * Structured exactly like {@link DesignExcellenceGate}: real bytes fetched from GitHub at the PR's real
 * head ref, never a self-attested payload field (Charter Pattern #12 - independent verification, not
 * self-attestation). Deliberately {@code isBuildPhaseExempt()=false}, unlike DesignExcellenceGate/
 * BackendContractGate (both true): those two are mechanical-polish gates the build-phase "trust is
 * maximal" philosophy is meant to relax. This gate is the ONLY objective merge evidence BARCAN-TAG-06 has
 * at all once ClientDeliverableReadinessService.hasRequiredMergeEvidence reads it - a real zero-file-diff
 * QA PR provides nothing else to check, and the live incident sits squarely in build phase.
 */
@Service
@Order(220)
public class VerificationEvidenceGate implements GateCheck {
    public static final Set<String> QA_TAGS = Set.of("BARCAN-TAG-06");
    public static final String CHECK_NAME = "verification_evidence";

    private final JulesSessionRepository julesSessionRepository;
    private final GitHubPullRequestService gitHubPullRequestService;
    private final ObjectMapper objectMapper;

    public VerificationEvidenceGate(JulesSessionRepository julesSessionRepository,
                                     GitHubPullRequestService gitHubPullRequestService,
                                     ObjectMapper objectMapper) {
        this.julesSessionRepository = julesSessionRepository;
        this.gitHubPullRequestService = gitHubPullRequestService;
        this.objectMapper = objectMapper;
    }

    @Override
    public GateStage stage() {
        return GateStage.IMPLEMENTATION_RESULT;
    }

    @Override
    public boolean supports(TaskEntity task) {
        return task != null
                && task.getRole() != null
                && QA_TAGS.contains(task.getRole().getTag());
    }

    @Override
    public boolean isBuildPhaseExempt() {
        return false;
    }

    public static String verificationReportPath(TaskEntity task) {
        return ".eneik/records/qa-verification-" + task.getId() + ".json";
    }

    @Override
    public GateResult check(TaskEntity task) {
        // 2026-08-22 (ACP-105): the "not applicable -> passed" branch was removed. GateOrchestrator
        // already filters by supports(), so it was unreachable through the only caller - but it encoded
        // the idea that an unasked check is a passed one, which is the Evidence Algebra inverted: the
        // absence of a check is 0, never 5. Nothing is lost; supports() does this work.
        JulesSessionEntity session = resolveSessionWithPr(task);
        Integer pullNumber = session != null ? gitHubPullRequestService.parsePullNumber(session.getPrUrl()) : null;
        if (task.getProject() == null || pullNumber == null) {
            return new GateResult(false, CHECK_NAME, List.of("no PR found to verify verification evidence against"));
        }

        List<String> changedFiles = gitHubPullRequestService.fetchDiffText(task.getProject(), pullNumber)
                .map(GitHubPullRequestService::changedFilePathsFromDiff)
                .orElse(List.of());
        String headRef = gitHubPullRequestService.fetchPullRequestByNumber(task.getProject(), pullNumber)
                .map(GitHubPullRequestService.GitHubPullRequest::headRef)
                .orElse(null);

        String reportPath = verificationReportPath(task);
        List<String> failureReasons = new ArrayList<>();

        if (headRef == null || !changedFiles.contains(reportPath)) {
            failureReasons.add("missing real verification report at " + reportPath);
            return new GateResult(false, CHECK_NAME, failureReasons);
        }

        Optional<String> content = gitHubPullRequestService.fetchFileContent(task.getProject(), headRef, reportPath);
        if (content.isEmpty()) {
            failureReasons.add("verification report at " + reportPath + " could not be fetched from GitHub");
            return new GateResult(false, CHECK_NAME, failureReasons);
        }

        JsonNode report;
        try {
            report = objectMapper.readTree(content.get());
        } catch (Exception e) {
            failureReasons.add("verification report at " + reportPath + " is not valid JSON: " + e.getMessage());
            return new GateResult(false, CHECK_NAME, failureReasons);
        }

        int testsRun = report.path("testsRun").asInt(-1);
        int testsPassed = report.path("testsPassed").asInt(-1);
        int testsFailed = report.path("testsFailed").asInt(-1);
        String verdict = report.path("verdict").asText("");
        boolean hasCriteria = report.path("acceptanceCriteriaVerified").isArray()
                && !report.path("acceptanceCriteriaVerified").isEmpty();

        if (testsRun < 1) {
            failureReasons.add("verification report claims testsRun=" + testsRun + " - no tests were actually run");
        }
        if (testsRun != testsPassed + testsFailed) {
            failureReasons.add("verification report is internally inconsistent: testsRun=" + testsRun
                    + " but testsPassed+testsFailed=" + (testsPassed + testsFailed));
        }
        if (!hasCriteria) {
            failureReasons.add("verification report lists no acceptanceCriteriaVerified");
        }
        if (!"pass".equals(verdict)) {
            failureReasons.add("verification report verdict is '" + verdict + "', not 'pass'");
        }

        return new GateResult(failureReasons.isEmpty(), CHECK_NAME, failureReasons);
    }

    // Same implementer-session lookup used elsewhere (DesignExcellenceGate, BackendContractGate,
    // JulesDispatchService.applyReviewVerdictToTask): prefer the session sitting at "pr_opened", else fall
    // back to any session that already has a PR.
    private JulesSessionEntity resolveSessionWithPr(TaskEntity task) {
        List<JulesSessionEntity> sessions = julesSessionRepository.findByTaskId(task.getId());
        return sessions.stream()
                .filter(s -> "pr_opened".equals(s.getStatus()))
                .findFirst()
                .orElseGet(() -> sessions.stream().filter(s -> s.getPrUrl() != null).findFirst().orElse(null));
    }
}
