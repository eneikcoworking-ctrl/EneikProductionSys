package com.eneik.production.services.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                false);

        assertTrue(GitHubPullRequestService.matchesSessionToken(
                pullRequest, "sessions/12568286363758467645"));
        assertFalse(GitHubPullRequestService.matchesSessionToken(
                pullRequest, "sessions/10145587924572151150"));
        assertFalse(GitHubPullRequestService.matchesSessionToken(pullRequest, "skipped"));
    }
}
