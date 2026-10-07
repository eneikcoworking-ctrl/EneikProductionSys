package com.eneik.production.services.gate;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Popperian Falsification Suite for VerificationEvidenceGate.
 * Validates epistemic invariants:
 * - D010: Goldman Reliability Chain (fetching real report content from GitHub at head ref).
 * - D008: Popper Falsification Harness (testsRun >= 1, arithmetic coherence, verified criteria, pass verdict).
 * - D006: Raz Rights & Duties Matrix (BARCAN-TAG-06 scope, non-exemption in buildPhase: isBuildPhaseExempt=false).
 */
class VerificationEvidenceGateFalsificationTest {
    private static final String PR_URL = "https://github.com/acme/qa-repo/pull/77";
    private static final String HEAD_REF = "feature/qa-verification-77";

    private VerificationEvidenceGate gate;
    private JulesSessionRepository julesSessionRepository;
    private GitHubPullRequestService gitHubPullRequestService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        julesSessionRepository = mock(JulesSessionRepository.class);
        gitHubPullRequestService = mock(GitHubPullRequestService.class);
        gate = new VerificationEvidenceGate(julesSessionRepository, gitHubPullRequestService, objectMapper);
        when(julesSessionRepository.findByTaskId(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("D010 Goldman Reliability Chain: Valid verification report fetched from GitHub at headRef passes gate")
    void falsifyGoldmanReliabilityChain_realReportContentFetchedFromGitHubOverridesClaimsAndPasses() {
        TaskEntity task = createQaTask("BARCAN-TAG-06");
        String reportPath = VerificationEvidenceGate.verificationReportPath(task);
        stubRealPr(task, List.of(reportPath));
        stubReportContent(task, """
                {
                    "testsRun": 5,
                    "testsPassed": 5,
                    "testsFailed": 0,
                    "acceptanceCriteriaVerified": ["API contract validated", "Auth boundary tested"],
                    "verdict": "pass"
                }
                """);

        GateResult result = gate.check(task);

        assertThat(result.passed()).isTrue();
        assertThat(result.failureReasons()).isEmpty();
        assertThat(result.checkName()).isEqualTo(VerificationEvidenceGate.CHECK_NAME);
    }

    @Test
    @DisplayName("D010 Goldman Reliability Chain: Report in diff but unfetchable from GitHub fails closed")
    void falsifyGoldmanReliabilityChain_reportInDiffHeaderWithoutFetchableContentFailsClosed() {
        TaskEntity task = createQaTask("BARCAN-TAG-06");
        String reportPath = VerificationEvidenceGate.verificationReportPath(task);
        stubRealPr(task, List.of(reportPath));
        // GitHub API returns empty content
        when(gitHubPullRequestService.fetchFileContent(any(), eq(HEAD_REF), eq(reportPath)))
                .thenReturn(Optional.empty());

        GateResult result = gate.check(task);

        assertThat(result.passed()).isFalse();
        assertThat(result.failureReasons()).anyMatch(r ->
                r.contains("verification report at " + reportPath + " could not be fetched from GitHub"));
    }

    @Test
    @DisplayName("D010 Goldman Reliability Chain: Corrupted/malformed JSON in verification report fails closed")
    void falsifyGoldmanReliabilityChain_malformedJsonInReportFailsClosed() {
        TaskEntity task = createQaTask("BARCAN-TAG-06");
        String reportPath = VerificationEvidenceGate.verificationReportPath(task);
        stubRealPr(task, List.of(reportPath));
        stubReportContent(task, "{ invalid json content: 123 ");

        GateResult result = gate.check(task);

        assertThat(result.passed()).isFalse();
        assertThat(result.failureReasons()).anyMatch(r ->
                r.contains("is not valid JSON"));
    }

    @Test
    @DisplayName("D008 Popper Falsification Harness: Zero tests run, arithmetic mismatch, empty criteria, non-pass fail gate")
    void falsifyPopperFalsificationHarness_testCoherenceInvariantsEnforced() {
        TaskEntity task = createQaTask("BARCAN-TAG-06");
        String reportPath = VerificationEvidenceGate.verificationReportPath(task);
        stubRealPr(task, List.of(reportPath));

        // 1. Zero tests run
        stubReportContent(task, """
                {"testsRun": 0, "testsPassed": 0, "testsFailed": 0,
                 "acceptanceCriteriaVerified": ["c1"], "verdict": "pass"}
                """);
        GateResult rZeroTests = gate.check(task);
        assertThat(rZeroTests.passed()).isFalse();
        assertThat(rZeroTests.failureReasons()).anyMatch(r -> r.contains("no tests were actually run"));

        // 2. Arithmetic inconsistency: testsRun != testsPassed + testsFailed
        stubReportContent(task, """
                {"testsRun": 5, "testsPassed": 3, "testsFailed": 1,
                 "acceptanceCriteriaVerified": ["c1"], "verdict": "pass"}
                """);
        GateResult rArithmetic = gate.check(task);
        assertThat(rArithmetic.passed()).isFalse();
        assertThat(rArithmetic.failureReasons()).anyMatch(r -> r.contains("internally inconsistent: testsRun=5 but testsPassed+testsFailed=4"));

        // 3. Empty acceptanceCriteriaVerified
        stubReportContent(task, """
                {"testsRun": 3, "testsPassed": 3, "testsFailed": 0,
                 "acceptanceCriteriaVerified": [], "verdict": "pass"}
                """);
        GateResult rEmptyCriteria = gate.check(task);
        assertThat(rEmptyCriteria.passed()).isFalse();
        assertThat(rEmptyCriteria.failureReasons()).contains("verification report lists no acceptanceCriteriaVerified");

        // 4. Non-pass verdict
        stubReportContent(task, """
                {"testsRun": 2, "testsPassed": 1, "testsFailed": 1,
                 "acceptanceCriteriaVerified": ["c1"], "verdict": "failed"}
                """);
        GateResult rNonPass = gate.check(task);
        assertThat(rNonPass.passed()).isFalse();
        assertThat(rNonPass.failureReasons()).anyMatch(r -> r.contains("verdict is 'failed', not 'pass'"));
    }

    @Test
    @DisplayName("D006 Raz Duty Matrix: supports() strictly restricted to QA tag BARCAN-TAG-06; fails closed without PR")
    void falsifyRazDutyMatrix_supportsRestrictedStrictlyToQaRoleAndFailsClosedWithoutPr() {
        TaskEntity qaTask = createQaTask("BARCAN-TAG-06");
        TaskEntity backendTask = createQaTask("BARCAN-TAG-02");
        TaskEntity uiTask = createQaTask("BARCAN-TAG-03");
        TaskEntity frontendTask = createQaTask("BARCAN-TAG-11");
        TaskEntity nullRoleTask = new TaskEntity();
        nullRoleTask.setId(UUID.randomUUID());

        assertThat(gate.supports(qaTask)).isTrue();
        assertThat(gate.supports(backendTask)).isFalse();
        assertThat(gate.supports(uiTask)).isFalse();
        assertThat(gate.supports(frontendTask)).isFalse();
        assertThat(gate.supports(nullRoleTask)).isFalse();
        assertThat(gate.supports(null)).isFalse();

        // When PR is absent, fails closed with clear reason
        GateResult resultNoPr = gate.check(qaTask);
        assertThat(resultNoPr.passed()).isFalse();
        assertThat(resultNoPr.failureReasons()).contains("no PR found to verify verification evidence against");
    }

    @Test
    @DisplayName("D006 Raz Duty Matrix: isBuildPhaseExempt is strictly FALSE and stage is IMPLEMENTATION_RESULT")
    void falsifyRazDutyMatrix_isBuildPhaseExemptIsStrictlyFalseAndStageIsImplementationResult() {
        // Essential invariant: QA evidence cannot be excused in build phase!
        // While mechanical polish gates (BackendContractGate, DesignExcellenceGate) are exempt,
        // VerificationEvidenceGate is the ONLY objective evidence BARCAN-TAG-06 has.
        assertThat(gate.isBuildPhaseExempt()).isFalse();
        assertThat(gate.stage()).isEqualTo(GateStage.IMPLEMENTATION_RESULT);
    }

    private TaskEntity createQaTask(String tag) {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        RoleEntity role = new RoleEntity();
        role.setTag(tag);
        task.setRole(role);
        task.setProject(new ProjectEntity());
        return task;
    }

    private void stubRealPr(TaskEntity task, List<String> changedPaths) {
        JulesSessionEntity session = new JulesSessionEntity();
        session.setStatus("pr_opened");
        session.setPrUrl(PR_URL);
        when(julesSessionRepository.findByTaskId(eq(task.getId()))).thenReturn(List.of(session));
        when(gitHubPullRequestService.parsePullNumber(anyString())).thenReturn(77);

        StringBuilder diff = new StringBuilder();
        for (String path : changedPaths) {
            diff.append("+++ b/").append(path).append("\n");
        }
        when(gitHubPullRequestService.fetchDiffText(any(), anyInt())).thenReturn(Optional.of(diff.toString()));

        GitHubPullRequestService.GitHubPullRequest pr =
                new GitHubPullRequestService.GitHubPullRequest(PR_URL, 77, "title", HEAD_REF, "author", false, "main", false, null);
        when(gitHubPullRequestService.fetchPullRequestByNumber(any(), anyInt())).thenReturn(Optional.of(pr));
    }

    private void stubReportContent(TaskEntity task, String json) {
        String path = VerificationEvidenceGate.verificationReportPath(task);
        when(gitHubPullRequestService.fetchFileContent(any(), eq(HEAD_REF), eq(path)))
                .thenReturn(Optional.of(json));
    }
}
