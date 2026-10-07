package com.eneik.production.services.gate;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.services.design.LayoutGeometryAuditService;
import com.eneik.production.services.github.GitHubPullRequestService;
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
 * Popperian Falsification Suite for DesignExcellenceGate.
 * Validates epistemic invariants:
 * - D013: Wittgenstein Anti-Mirror (telemetry/bytes override claims/headers).
 * - D012: Popper Truth Status Table (CANNOT_JUDGE / геометрия не выводима withholds points).
 * - D006: Raz Prohibition As Code (viewport scalability and bounding-box collisions fail gate).
 */
class DesignExcellenceGateFalsificationTest {
    private static final String PR_URL = "https://github.com/acme/ui-widget/pull/99";
    private static final String HEAD_REF = "feature/redesign-nav";

    private DesignExcellenceGate gate;
    private JulesSessionRepository julesSessionRepository;
    private GitHubPullRequestService gitHubPullRequestService;
    private LayoutGeometryAuditService layoutGeometryAuditService;

    @BeforeEach
    void setUp() {
        julesSessionRepository = mock(JulesSessionRepository.class);
        gitHubPullRequestService = mock(GitHubPullRequestService.class);
        layoutGeometryAuditService = new LayoutGeometryAuditService();
        gate = new DesignExcellenceGate(julesSessionRepository, gitHubPullRequestService, layoutGeometryAuditService);
        when(julesSessionRepository.findByTaskId(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("D013 Wittgenstein Anti-Mirror: Real screenshot bytes fetched from GitHub override claims and grant pass")
    void falsifyWittgensteinAntiMirror_realScreenshotBytesFetchedFromGitHubOverridesClaims() {
        TaskEntity task = createUiTask("BARCAN-TAG-03");
        stubRealPrWithFiles(task,
                List.of("desktop-1440.png", "mobile-375.png"),
                List.of(DesignExcellenceGate.layoutCheckPath(task)));
        stubFileBytes(task, "desktop-1440.png", 2500);
        stubFileBytes(task, "mobile-375.png", 1800);

        String validLayoutJson = """
                [
                    {"id": "header", "left": 0, "top": 0, "width": 375, "height": 60},
                    {"id": "content", "left": 0, "top": 70, "width": 375, "height": 300}
                ]
                """;
        when(gitHubPullRequestService.fetchFileContent(any(), eq(HEAD_REF), eq(DesignExcellenceGate.layoutCheckPath(task))))
                .thenReturn(Optional.of(validLayoutJson));

        GateResult result = gate.check(task);

        assertThat(result.passed()).isTrue();
        assertThat(result.failureReasons()).isEmpty();
        assertThat(result.checkName()).isEqualTo(DesignExcellenceGate.CHECK_NAME);
    }

    @Test
    @DisplayName("D013 Wittgenstein Anti-Mirror: Path mentioned in PR diff without fetchable bytes fails closed")
    void falsifyWittgensteinAntiMirror_screenshotMentionedInDiffWithoutFetchableBytesFailsCheck() {
        TaskEntity task = createUiTask("BARCAN-TAG-11");
        // PR diff lists mobile-375.png, but byte fetch fails (e.g. deleted or header-only)
        stubRealPrWithFiles(task,
                List.of("desktop-1440.png", "mobile-375.png"),
                List.of(DesignExcellenceGate.layoutCheckPath(task)));
        stubFileBytes(task, "desktop-1440.png", 2500);
        // mobile-375.png bytes are not stubbed -> Optional.empty()

        GateResult result = gate.check(task);

        assertThat(result.passed()).isFalse();
        assertThat(result.failureReasons()).anyMatch(reason ->
                reason.contains("missing real mobile screenshot at " + DesignExcellenceGate.designCheckDir(task) + "mobile-375.png"));
    }

    @Test
    @DisplayName("D012 Popper Truth Status Table: Missing layout evidence yields CANNOT_JUDGE and withholds 40 points")
    void falsifyPopperTruthStatusTable_missingLayoutCheckJsonYieldsCannotJudgeAndWithholdsPoints() {
        TaskEntity task = createUiTask("BARCAN-TAG-03");
        stubRealPrWithFiles(task,
                List.of("desktop-1440.png", "mobile-375.png"),
                List.of()); // No layout-check.json and no markup files
        stubFileBytes(task, "desktop-1440.png", 2500);
        stubFileBytes(task, "mobile-375.png", 1800);

        GateResult result = gate.check(task);

        // Score: 30 (has_both) + 0 (responsive withheld) + 30 (visual_qa) = 60 < 70 -> fails closed
        assertThat(result.passed()).isFalse();
        assertThat(result.failureReasons()).anyMatch(reason ->
                reason.contains("responsive check cannot be judged: геометрия не выводима")
                        && reason.contains("40 баллов за отзывчивость не начислены"));
    }

    @Test
    @DisplayName("D006 Raz Prohibition As Code: Viewport user-scalable prohibition and bounding collisions fail gate")
    void falsifyRazProhibition_viewportScalabilityProhibitionOrCollisionsFailGate() {
        TaskEntity task = createUiTask("BARCAN-TAG-11");
        stubRealPrWithFiles(task,
                List.of("desktop-1440.png", "mobile-375.png"),
                List.of(DesignExcellenceGate.layoutCheckPath(task), "frontend/index.html"));
        stubFileBytes(task, "desktop-1440.png", 2500);
        stubFileBytes(task, "mobile-375.png", 1800);

        // 1. Collisions in layout-check.json
        String collidingLayoutJson = """
                [
                    {"id": "menu-drawer", "left": 10, "top": 20, "width": 200, "height": 60},
                    {"id": "header-banner", "left": 50, "top": 40, "width": 200, "height": 100}
                ]
                """;
        when(gitHubPullRequestService.fetchFileContent(any(), eq(HEAD_REF), eq(DesignExcellenceGate.layoutCheckPath(task))))
                .thenReturn(Optional.of(collidingLayoutJson));

        // 2. Scalability prohibition in HTML
        String prohibitedHtml = """
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                </head>
                <body><div style="left:0; top:0; width:375px; height:200px;">Content</div></body>
                </html>
                """;
        when(gitHubPullRequestService.fetchFileContent(any(), eq(HEAD_REF), eq("frontend/index.html")))
                .thenReturn(Optional.of(prohibitedHtml));

        GateResult result = gate.check(task);

        assertThat(result.passed()).isFalse();
        assertThat(result.failureReasons()).anyMatch(r -> r.contains("viewport scalability prohibited in frontend/index.html"));
        assertThat(result.failureReasons()).anyMatch(r -> r.contains("responsive check failed: layout collision detected in " + DesignExcellenceGate.layoutCheckPath(task)));
    }

    @Test
    @DisplayName("D006 Raz Duty Matrix: supports() restricted strictly to UI tags, fails closed when PR absent")
    void falsifyRazDutyMatrix_supportsRestrictedStrictlyToUiTagsAndFailsClosedWithoutPr() {
        TaskEntity uiTask1 = createUiTask("BARCAN-TAG-03");
        TaskEntity uiTask2 = createUiTask("BARCAN-TAG-11");
        TaskEntity nonUiTask1 = createUiTask("BARCAN-TAG-01");
        TaskEntity nonUiTask2 = createUiTask("BARCAN-TAG-02");
        TaskEntity nullRoleTask = new TaskEntity();
        nullRoleTask.setId(UUID.randomUUID());

        assertThat(gate.supports(uiTask1)).isTrue();
        assertThat(gate.supports(uiTask2)).isTrue();
        assertThat(gate.supports(nonUiTask1)).isFalse();
        assertThat(gate.supports(nonUiTask2)).isFalse();
        assertThat(gate.supports(nullRoleTask)).isFalse();
        assertThat(gate.supports(null)).isFalse();

        // If UI task has no PR session opened, fails closed with clear reason
        GateResult resultWithoutPr = gate.check(uiTask1);
        assertThat(resultWithoutPr.passed()).isFalse();
        assertThat(resultWithoutPr.failureReasons()).contains("no PR found to verify design screenshots against");
    }

    @Test
    @DisplayName("Contract Invariant: stage is IMPLEMENTATION_RESULT and isBuildPhaseExempt is true")
    void falsifyGateCheckContract_stageIsImplementationResultAndBuildPhaseExemptIsTrue() {
        assertThat(gate.stage()).isEqualTo(GateStage.IMPLEMENTATION_RESULT);
        assertThat(gate.isBuildPhaseExempt()).isTrue();
    }

    private TaskEntity createUiTask(String tag) {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        RoleEntity role = new RoleEntity();
        role.setTag(tag);
        task.setRole(role);
        task.setProject(new ProjectEntity());
        return task;
    }

    private void stubRealPrWithFiles(TaskEntity task, List<String> committedScreenshotBasenames, List<String> otherFiles) {
        JulesSessionEntity session = new JulesSessionEntity();
        session.setStatus("pr_opened");
        session.setPrUrl(PR_URL);
        when(julesSessionRepository.findByTaskId(eq(task.getId()))).thenReturn(List.of(session));
        when(gitHubPullRequestService.parsePullNumber(anyString())).thenReturn(99);

        StringBuilder diff = new StringBuilder();
        String dir = DesignExcellenceGate.designCheckDir(task);
        for (String basename : committedScreenshotBasenames) {
            diff.append("+++ b/").append(dir).append(basename).append("\n");
        }
        for (String file : otherFiles) {
            diff.append("+++ b/").append(file).append("\n");
        }
        when(gitHubPullRequestService.fetchDiffText(any(), anyInt())).thenReturn(Optional.of(diff.toString()));

        GitHubPullRequestService.GitHubPullRequest pr =
                new GitHubPullRequestService.GitHubPullRequest(PR_URL, 99, "title", HEAD_REF, "author", false, "main", false, null);
        when(gitHubPullRequestService.fetchPullRequestByNumber(any(), anyInt())).thenReturn(Optional.of(pr));
    }

    private void stubFileBytes(TaskEntity task, String basename, int size) {
        String path = DesignExcellenceGate.designCheckDir(task) + basename;
        when(gitHubPullRequestService.fetchFileBytes(any(), eq(HEAD_REF), eq(path)))
                .thenReturn(Optional.of(new byte[size]));
    }
}
