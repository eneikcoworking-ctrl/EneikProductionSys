package com.eneik.production.services.gate;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
 * Phase C (design/QA acceptance redesign, 2026-08-04): structured exactly like DesignExcellenceGateTest -
 * real bytes fetched from GitHub at the PR's real head ref, never a self-attested payload field. The
 * zero-file-diff case matters most here: it is the literal shape of the live incident this gate exists to
 * fix (a real, correct QA merge with nothing else changed except the verification report itself).
 */
class VerificationEvidenceGateTest {
    private static final String PR_URL = "https://github.com/acme/widgets/pull/53";
    private static final String HEAD_REF = "feature/some-qa-branch";

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
    void shouldPassNonQaTask() {
        TaskEntity task = createTask("BARCAN-TAG-02");
        assertThat(gate.supports(task)).isFalse();

        GateResult result = gate.check(task);
        assertThat(result.passed()).isTrue();
        assertThat(result.failureReasons()).contains("not applicable to this role");
    }

    @Test
    void shouldFailWhenNoPrExistsYet() {
        TaskEntity task = createTask("BARCAN-TAG-06");
        GateResult result = gate.check(task);

        assertThat(result.passed()).isFalse();
        assertThat(result.failureReasons()).contains("no PR found to verify verification evidence against");
    }

    @Test
    void shouldFailWhenReportFileIsMissingFromTheDiff() {
        TaskEntity task = createTask("BARCAN-TAG-06");
        stubRealPr(task, List.of()); // PR opened, but the report path never appears in the diff

        GateResult result = gate.check(task);

        assertThat(result.passed()).isFalse();
        assertThat(result.failureReasons()).contains(
                "missing real verification report at " + VerificationEvidenceGate.verificationReportPath(task));
    }

    @Test
    void shouldPassAZeroFileDiffQaMergeAsLongAsTheReportItselfIsCommittedAndValid() {
        // The literal live-incident shape: a real, correct QA merge whose ONLY changed file is its own
        // verification report - CodeChangeClassifier.hasCode() would call this "no code", but it's a
        // legitimate, complete QA outcome.
        TaskEntity task = createTask("BARCAN-TAG-06");
        String reportPath = VerificationEvidenceGate.verificationReportPath(task);
        stubRealPr(task, List.of(reportPath));
        stubReportContent(task, """
                {"testsRun":3,"testsPassed":3,"testsFailed":0,
                 "acceptanceCriteriaVerified":["role boundaries enforced","russian-only UI"],
                 "coverageDeltaPercent":null,"verdict":"pass","notes":"confirmed existing behavior"}
                """);

        GateResult result = gate.check(task);

        assertThat(result.passed()).isTrue();
    }

    @Test
    void shouldFailWhenReportIsInternallyInconsistent() {
        TaskEntity task = createTask("BARCAN-TAG-06");
        String reportPath = VerificationEvidenceGate.verificationReportPath(task);
        stubRealPr(task, List.of(reportPath));
        stubReportContent(task, """
                {"testsRun":3,"testsPassed":1,"testsFailed":1,
                 "acceptanceCriteriaVerified":["one thing"],"verdict":"pass"}
                """);

        GateResult result = gate.check(task);

        assertThat(result.passed()).isFalse();
        assertThat(result.failureReasons()).anyMatch(reason -> reason.contains("internally inconsistent"));
    }

    @Test
    void shouldFailWhenAcceptanceCriteriaListIsEmpty() {
        TaskEntity task = createTask("BARCAN-TAG-06");
        String reportPath = VerificationEvidenceGate.verificationReportPath(task);
        stubRealPr(task, List.of(reportPath));
        stubReportContent(task, """
                {"testsRun":1,"testsPassed":1,"testsFailed":0,"acceptanceCriteriaVerified":[],"verdict":"pass"}
                """);

        GateResult result = gate.check(task);

        assertThat(result.passed()).isFalse();
        assertThat(result.failureReasons()).contains("verification report lists no acceptanceCriteriaVerified");
    }

    @Test
    void shouldFailWhenVerdictIsNotPass() {
        TaskEntity task = createTask("BARCAN-TAG-06");
        String reportPath = VerificationEvidenceGate.verificationReportPath(task);
        stubRealPr(task, List.of(reportPath));
        stubReportContent(task, """
                {"testsRun":2,"testsPassed":1,"testsFailed":1,
                 "acceptanceCriteriaVerified":["something"],"verdict":"fail"}
                """);

        GateResult result = gate.check(task);

        assertThat(result.passed()).isFalse();
        assertThat(result.failureReasons()).anyMatch(reason -> reason.contains("verdict is 'fail'"));
    }

    @Test
    void shouldFailWhenReportFileCannotActuallyBeFetchedDespiteAppearingInTheDiff() {
        TaskEntity task = createTask("BARCAN-TAG-06");
        String reportPath = VerificationEvidenceGate.verificationReportPath(task);
        stubRealPr(task, List.of(reportPath));
        when(gitHubPullRequestService.fetchFileContent(any(), eq(HEAD_REF), eq(reportPath)))
                .thenReturn(Optional.empty());

        GateResult result = gate.check(task);

        assertThat(result.passed()).isFalse();
        assertThat(result.failureReasons()).anyMatch(reason -> reason.contains("could not be fetched"));
    }

    private void stubRealPr(TaskEntity task, List<String> changedPaths) {
        JulesSessionEntity session = new JulesSessionEntity();
        session.setStatus("pr_opened");
        session.setPrUrl(PR_URL);
        when(julesSessionRepository.findByTaskId(eq(task.getId()))).thenReturn(List.of(session));
        when(gitHubPullRequestService.parsePullNumber(anyString())).thenReturn(53);

        StringBuilder diff = new StringBuilder();
        for (String path : changedPaths) {
            diff.append("+++ b/").append(path).append("\n");
        }
        when(gitHubPullRequestService.fetchDiffText(any(), anyInt())).thenReturn(Optional.of(diff.toString()));

        GitHubPullRequestService.GitHubPullRequest pr =
                new GitHubPullRequestService.GitHubPullRequest(PR_URL, 53, "title", HEAD_REF, "author", false, "main", false, null);
        when(gitHubPullRequestService.fetchPullRequestByNumber(any(), anyInt())).thenReturn(Optional.of(pr));
    }

    private void stubReportContent(TaskEntity task, String json) {
        String path = VerificationEvidenceGate.verificationReportPath(task);
        when(gitHubPullRequestService.fetchFileContent(any(), eq(HEAD_REF), eq(path)))
                .thenReturn(Optional.of(json));
    }

    private TaskEntity createTask(String tag) {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        RoleEntity role = new RoleEntity();
        role.setTag(tag);
        task.setRole(role);
        task.setProject(new ProjectEntity());
        return task;
    }
}
