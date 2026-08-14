package com.eneik.production.services.github;

import com.eneik.production.config.GithubConfig;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class GitHubPullRequestServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void checkRunsRequireAtLeastOneCompletedGreenCheck() throws Exception {
        GitHubPullRequestService.PullRequestChecks result = GitHubPullRequestService.evaluateCheckRuns(
                objectMapper.readTree("[]"));

        assertFalse(result.successful());
        assertEquals("pending", result.status());
    }

    @Test
    void checkRunsFailClosedWhenAnyCheckIsRed() throws Exception {
        JsonNode checks = objectMapper.readTree("""
                [
                  {"name":"quality","status":"completed","conclusion":"failure"},
                  {"name":"lint","status":"completed","conclusion":"success"}
                ]
                """);

        GitHubPullRequestService.PullRequestChecks result = GitHubPullRequestService.evaluateCheckRuns(checks);

        assertFalse(result.successful());
        assertEquals("failure", result.status());
        assertTrue(result.detail().contains("quality=failure"));
    }

    @Test
    void checkRunsStayPendingUntilEveryCheckCompletes() throws Exception {
        JsonNode checks = objectMapper.readTree("""
                [
                  {"name":"quality","status":"in_progress","conclusion":""},
                  {"name":"lint","status":"completed","conclusion":"success"}
                ]
                """);

        GitHubPullRequestService.PullRequestChecks result = GitHubPullRequestService.evaluateCheckRuns(checks);

        assertFalse(result.successful());
        assertEquals("pending", result.status());
    }

    @Test
    void checkRunsAcceptSuccessNeutralAndSkippedConclusions() throws Exception {
        JsonNode checks = objectMapper.readTree("""
                [
                  {"name":"quality","status":"completed","conclusion":"success"},
                  {"name":"advisory","status":"completed","conclusion":"neutral"},
                  {"name":"optional","status":"completed","conclusion":"skipped"}
                ]
                """);

        GitHubPullRequestService.PullRequestChecks result = GitHubPullRequestService.evaluateCheckRuns(checks);

        assertTrue(result.successful());
        assertEquals("success", result.status());
    }

    @Test
    void pullRequestOwnershipRequiresExactSessionTokenInBranch() {
        GitHubPullRequestService.GitHubPullRequest pullRequest = new GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/org/repo/pull/21",
                21,
                "Implement schema",
                "jules/sessions-12568286363758467645-schema",
                "jules",
                false,
                "main",
                false,
                java.time.Instant.now());

        assertTrue(GitHubPullRequestService.matchesSessionToken(
                pullRequest, "sessions/12568286363758467645"));
        assertFalse(GitHubPullRequestService.matchesSessionToken(
                pullRequest, "sessions/10145587924572151150"));
        assertFalse(GitHubPullRequestService.matchesSessionToken(pullRequest, "skipped"));
    }

    /**
     * Regression coverage for the 2026-08-04 incident (test-forty-first task 1fbb3086): findOpenPullRequestBySession
     * used to fall back to matching ANY open PR whose branch/title merely looked like a compiler/task-plan
     * pattern once the exact-token match failed - it silently attributed an unrelated compiler session's PR
     * to a completely different, long-dead session with no PR of its own. That fallback is now removed
     * entirely; a session with no exact token match in any open PR's branch must get back empty, even when
     * an unrelated compiler-pattern PR exists in the same project.
     */
    @Test
    void findOpenPullRequestBySessionNeverFallsBackToAnUnrelatedCompilerPatternPr() {
        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        GitHubPullRequestService service = org.mockito.Mockito.spy(new GitHubPullRequestService(
                mock(GithubConfig.class), settingsService, objectMapper, mock(GitHubApiBudgetService.class)));

        GitHubPullRequestService.GitHubPullRequest unrelatedCompilerPr = new GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/org/repo/pull/52",
                52,
                "Compile 1 Wishlist",
                "task-plan-500b9d0c-9092139308873395481",
                "jules",
                false,
                "main",
                false,
                java.time.Instant.now());
        GitHubPullRequestService.PullRequestSnapshot snapshot = new GitHubPullRequestService.PullRequestSnapshot(
                true, "org", "repo", java.util.List.of(unrelatedCompilerPr), java.util.List.of(), null);

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setStatus(ProjectStatus.active);
        org.mockito.Mockito.doReturn(snapshot).when(service).pullRequestSnapshot(project);

        var result = service.findOpenPullRequestBySession(project, "sessions/8898497391970975137");

        assertTrue(result.isEmpty());
    }

    // --- GitHub budget guard (2026-07-31): single choke point for every project-aware caller -------------

    @Test
    void pullRequestSnapshotIsUnavailableForANonActiveProjectWithoutTouchingSettings() {
        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        GitHubPullRequestService service = new GitHubPullRequestService(
                mock(GithubConfig.class), settingsService, objectMapper, mock(GitHubApiBudgetService.class));
        ProjectEntity frozenProject = new ProjectEntity();
        frozenProject.setId(UUID.randomUUID());
        frozenProject.setStatus(ProjectStatus.frozen);

        GitHubPullRequestService.PullRequestSnapshot snapshot = service.pullRequestSnapshot(frozenProject);

        assertFalse(snapshot.available());
        verifyNoInteractions(settingsService);
    }

    @Test
    void fetchOpenPullRequestsReturnsEmptyForANonActiveProject() {
        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        GitHubPullRequestService service = new GitHubPullRequestService(
                mock(GithubConfig.class), settingsService, objectMapper, mock(GitHubApiBudgetService.class));
        ProjectEntity acceptedProject = new ProjectEntity();
        acceptedProject.setId(UUID.randomUUID());
        acceptedProject.setStatus(ProjectStatus.accepted);

        assertTrue(service.fetchOpenPullRequests(acceptedProject).isEmpty());
        verifyNoInteractions(settingsService);
    }
}
