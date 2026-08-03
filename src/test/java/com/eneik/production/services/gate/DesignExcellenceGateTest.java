package com.eneik.production.services.gate;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.services.github.GitHubPullRequestService;
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

// 2026-08-03 (Charter Pattern #12 - independent verification, not self-attestation): this gate used to
// read task.payload.screenshotUrls, a field self-reported by the implementer with no confirmed production
// writer - these tests used to stub that field directly. It now resolves the task's real PR via
// JulesSessionRepository, checks the real diff for the two fixed screenshot paths, and reads their real
// byte size via GitHubPullRequestService.fetchFileBytes, so tests stub those instead of the payload field.
class DesignExcellenceGateTest {
    private static final String PR_URL = "https://github.com/acme/widgets/pull/42";
    private static final String HEAD_REF = "feature/some-ui-branch";

    private DesignExcellenceGate gate;
    private JulesSessionRepository julesSessionRepository;
    private GitHubPullRequestService gitHubPullRequestService;

    @BeforeEach
    void setUp() {
        julesSessionRepository = mock(JulesSessionRepository.class);
        gitHubPullRequestService = mock(GitHubPullRequestService.class);
        gate = new DesignExcellenceGate(julesSessionRepository, gitHubPullRequestService);
        // Default: no implementer session/PR found for any task.
        when(julesSessionRepository.findByTaskId(any())).thenReturn(List.of());
    }

    @Test
    void shouldPassNonUiTask() {
        TaskEntity task = createTask("BARCAN-TAG-02", null);
        assertThat(gate.supports(task)).isFalse();

        GateResult result = gate.check(task);
        assertThat(result.passed()).isTrue();
        assertThat(result.failureReasons()).contains("not applicable to this role");
    }

    @Test
    void shouldFailWhenNoPrExistsYet() {
        TaskEntity task = createTask("BARCAN-TAG-11", null);
        GateResult result = gate.check(task);

        assertThat(result.passed()).isFalse();
        assertThat(result.failureReasons()).contains("no PR found to verify design screenshots against");
    }

    @Test
    void shouldFailUiTaskWithoutMobile() {
        TaskEntity task = createTask("BARCAN-TAG-11", null);
        stubRealPr(task, "desktop-1440.png");
        stubFileBytes(task, "desktop-1440.png", 2000);

        GateResult result = gate.check(task);

        assertThat(result.passed()).isFalse();
        assertThat(result.failureReasons()).contains("missing real mobile screenshot at " + DesignExcellenceGate.designCheckDir(task) + "mobile-375.png");
    }

    @Test
    void shouldPassUiTaskWithBothRealScreenshotsAndDifferentSizes() {
        TaskEntity task = createTask("BARCAN-TAG-11", null);
        stubRealPr(task, "desktop-1440.png", "mobile-375.png");
        stubFileBytes(task, "desktop-1440.png", 2000);
        stubFileBytes(task, "mobile-375.png", 1500);

        assertThat(gate.supports(task)).isTrue();
        assertThat(gate.stage()).isEqualTo(GateStage.IMPLEMENTATION_RESULT);

        GateResult result = gate.check(task);

        assertThat(result.passed()).isTrue();
    }

    @Test
    void shouldFailIfScreenshotsHaveSameSize() {
        TaskEntity task = createTask("BARCAN-TAG-11", null);
        stubRealPr(task, "desktop-1440.png", "mobile-375.png");
        stubFileBytes(task, "desktop-1440.png", 2000);
        stubFileBytes(task, "mobile-375.png", 2000);

        GateResult result = gate.check(task);

        // score: 30 (has_both) + 0 (responsive_ok) + 30 (visual_qa_ok) = 60 < 70
        assertThat(result.passed()).isFalse();
        assertThat(result.failureReasons()).contains("responsive check failed: screenshots have identical file sizes");
    }

    @Test
    void shouldFailWhenDiffMentionsPathButFileIsNotActuallyFetchable() {
        // Regression guard for the self-attestation bug this gate used to have: a path merely appearing
        // in the diff header (e.g. a rename or delete) must not count as real evidence without the bytes
        // actually being fetchable from GitHub at the PR's real head ref.
        TaskEntity task = createTask("BARCAN-TAG-11", null);
        stubRealPr(task, "desktop-1440.png", "mobile-375.png");
        stubFileBytes(task, "desktop-1440.png", 2000);
        // mobile-375.png deliberately NOT stubbed as fetchable - fetchFileBytes returns empty for it.

        GateResult result = gate.check(task);

        assertThat(result.passed()).isFalse();
        assertThat(result.failureReasons()).contains("missing real mobile screenshot at " + DesignExcellenceGate.designCheckDir(task) + "mobile-375.png");
    }

    private void stubRealPr(TaskEntity task, String... committedScreenshotBasenames) {
        JulesSessionEntity session = new JulesSessionEntity();
        session.setStatus("pr_opened");
        session.setPrUrl(PR_URL);
        when(julesSessionRepository.findByTaskId(eq(task.getId()))).thenReturn(List.of(session));
        when(gitHubPullRequestService.parsePullNumber(anyString())).thenReturn(42);

        StringBuilder diff = new StringBuilder();
        String dir = DesignExcellenceGate.designCheckDir(task);
        for (String basename : committedScreenshotBasenames) {
            diff.append("+++ b/").append(dir).append(basename).append("\n");
        }
        when(gitHubPullRequestService.fetchDiffText(any(), anyInt())).thenReturn(Optional.of(diff.toString()));

        GitHubPullRequestService.GitHubPullRequest pr =
                new GitHubPullRequestService.GitHubPullRequest(PR_URL, 42, "title", HEAD_REF, "author", false, "main", false, null);
        when(gitHubPullRequestService.fetchPullRequestByNumber(any(), anyInt())).thenReturn(Optional.of(pr));
    }

    private void stubFileBytes(TaskEntity task, String basename, int size) {
        String path = DesignExcellenceGate.designCheckDir(task) + basename;
        when(gitHubPullRequestService.fetchFileBytes(any(), eq(HEAD_REF), eq(path)))
                .thenReturn(Optional.of(new byte[size]));
    }

    private TaskEntity createTask(String tag, Object unused) {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        RoleEntity role = new RoleEntity();
        role.setTag(tag);
        task.setRole(role);
        task.setProject(new ProjectEntity());
        return task;
    }
}
