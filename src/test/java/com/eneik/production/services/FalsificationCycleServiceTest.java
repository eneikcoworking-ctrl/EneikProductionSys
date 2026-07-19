package com.eneik.production.services;

import com.eneik.production.models.persistence.FalsificationRunEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.repositories.FalsificationRunRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.settings.SystemSettingsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Isolated (non-Spring) coverage for the falsification audit's data source. Root cause of the bug being
 * fixed: the old implementation, when no local git workspace was available (the normal case for
 * GitHub-based projects), fell back to a database query that returned a PR-review VERDICT string (e.g.
 * "CORE ARCHITECTURE VERIFIED. APPROVED...") and handed it to the Jules auditor labelled as "the diff to
 * audit" - a category error, not a missing-data gap. Confirmed live in the test-twenty-fifth experiment.
 */
class FalsificationCycleServiceTest {

    private FalsificationCycleService newService(GitHubPullRequestService gitHubPullRequestService,
                                                  RoleRepository roleRepository,
                                                  ProjectFlowService projectFlowService,
                                                  FalsificationRunRepository falsificationRunRepository) {
        // Readiness-gated by default now (see ClientDeliverableReadinessService) - these tests exercise
        // the diff-fetching/dedup/skip logic downstream of that gate, not the gate itself, so stub it as
        // always-ready regardless of which project id is asked about.
        ClientDeliverableReadinessService readinessService = mock(ClientDeliverableReadinessService.class);
        when(readinessService.computeForProject(any())).thenReturn(
                new ClientDeliverableReadinessService.Readiness(1, 1, 1.0));
        return new FalsificationCycleService(
                mock(ProjectRepository.class),
                roleRepository,
                mock(RoleCapabilityLoader.class),
                mock(WishlistRepository.class),
                falsificationRunRepository,
                mock(SystemSettingsService.class),
                gitHubPullRequestService,
                projectFlowService,
                readinessService
        );
    }

    private RoleEntity role(String tag) {
        RoleEntity role = new RoleEntity();
        role.setTag(tag);
        role.setActive(true);
        role.setRulesPath(null); // charter loading is covered elsewhere; irrelevant to this test
        return role;
    }

    @Test
    void dispatchesWithRealMergedPrDiffWhenGitHubHasRecentMerges() {
        GitHubPullRequestService gitHub = mock(GitHubPullRequestService.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        ProjectFlowService projectFlowService = mock(ProjectFlowService.class);
        FalsificationRunRepository runRepository = mock(FalsificationRunRepository.class);
        FalsificationCycleService service = newService(gitHub, roleRepository, projectFlowService, runRepository);

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("real-diff-project");

        when(roleRepository.findAll()).thenReturn(List.of(role("BARCAN-TAG-02")));
        when(runRepository.findTopByProjectIdOrderByRunAtDesc(project.getId())).thenReturn(Optional.empty());

        GitHubPullRequestService.GitHubPullRequest mergedPr = new GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/org/repo/pull/42", 42, "Implement spintax parser", "jules-branch", "eneikdru", true);
        when(gitHub.pullRequestSnapshot(project)).thenReturn(
                new GitHubPullRequestService.PullRequestSnapshot(true, "org", "repo", List.of(), List.of(mergedPr), ""));
        String realDiff = "diff --git a/src/Spintax.java b/src/Spintax.java\n+public class Spintax {\n+    // real code change\n+}\n";
        when(gitHub.fetchDiffText(project, 42)).thenReturn(Optional.of(realDiff));
        when(projectFlowService.dispatchFalsificationAudit(eq(project), any(), any())).thenReturn(UUID.randomUUID());

        service.executeCycleForProject(project);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> prNumberCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(projectFlowService).dispatchFalsificationAudit(eq(project), promptCaptor.capture(), prNumberCaptor.capture());
        String prompt = promptCaptor.getValue();

        assertTrue(prompt.contains("real code change"), "prompt must contain the actual fetched diff content");
        assertTrue(prompt.contains("PR #42"), "prompt must reference the real PR being audited");
        assertFalse(prompt.contains("CORE ARCHITECTURE VERIFIED"),
                "prompt must never contain a PR-review verdict string mistaken for a diff - that was the bug");
        assertEquals(42, prNumberCaptor.getValue(), "the highest audited PR number must be tracked so a later run can skip already-covered work");
    }

    @Test
    void skipsAlreadyAuditedPrsAndOnlyIncludesNewOnesSinceLastRun() {
        // Lean: don't re-fetch/re-audit the same merged PR every cycle if nothing new merged since the
        // last run - real GitHub API calls and a real Jules session should not be spent on unchanged code.
        GitHubPullRequestService gitHub = mock(GitHubPullRequestService.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        ProjectFlowService projectFlowService = mock(ProjectFlowService.class);
        FalsificationRunRepository runRepository = mock(FalsificationRunRepository.class);
        FalsificationCycleService service = newService(gitHub, roleRepository, projectFlowService, runRepository);

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("dedup-project");

        when(roleRepository.findAll()).thenReturn(List.of(role("BARCAN-TAG-02")));
        FalsificationRunEntity previousRun = new FalsificationRunEntity();
        previousRun.setHighestPrNumberAudited(42);
        when(runRepository.findTopByProjectIdOrderByRunAtDesc(project.getId())).thenReturn(Optional.of(previousRun));

        GitHubPullRequestService.GitHubPullRequest alreadyAudited = new GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/org/repo/pull/42", 42, "Implement spintax parser", "jules-branch", "eneikdru", true);
        GitHubPullRequestService.GitHubPullRequest newlyMerged = new GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/org/repo/pull/43", 43, "Fix account proxy binding", "jules-branch-2", "eneikdru", true);
        when(gitHub.pullRequestSnapshot(project)).thenReturn(
                new GitHubPullRequestService.PullRequestSnapshot(true, "org", "repo", List.of(), List.of(alreadyAudited, newlyMerged), ""));
        when(gitHub.fetchDiffText(project, 43)).thenReturn(Optional.of("diff --git a/src/Proxy.java b/src/Proxy.java\n+// proxy fix\n"));
        when(projectFlowService.dispatchFalsificationAudit(eq(project), any(), any())).thenReturn(UUID.randomUUID());

        service.executeCycleForProject(project);

        verify(gitHub, never()).fetchDiffText(project, 42);
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(projectFlowService).dispatchFalsificationAudit(eq(project), promptCaptor.capture(), eq(43));
        assertTrue(promptCaptor.getValue().contains("proxy fix"));
        assertFalse(promptCaptor.getValue().contains("PR #42"), "already-audited PR #42 must not be re-fetched or re-included");
    }

    @Test
    void skipsHonestlyWhenGitHubHasNoMergedPrsAndNoLocalWorkspace() {
        GitHubPullRequestService gitHub = mock(GitHubPullRequestService.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        ProjectFlowService projectFlowService = mock(ProjectFlowService.class);
        FalsificationRunRepository runRepository = mock(FalsificationRunRepository.class);
        FalsificationCycleService service = newService(gitHub, roleRepository, projectFlowService, runRepository);

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("empty-project");
        project.setWorkspacePath(null);

        when(roleRepository.findAll()).thenReturn(List.of(role("BARCAN-TAG-02")));
        when(runRepository.findTopByProjectIdOrderByRunAtDesc(project.getId())).thenReturn(Optional.empty());
        when(gitHub.pullRequestSnapshot(project)).thenReturn(
                new GitHubPullRequestService.PullRequestSnapshot(true, "org", "repo", List.of(), List.of(), ""));

        service.executeCycleForProject(project);

        verify(projectFlowService, never()).dispatchFalsificationAudit(any(), any(), any());
    }

    @Test
    void skipsHonestlyWhenGitHubUnavailableInsteadOfFabricatingADiff() {
        GitHubPullRequestService gitHub = mock(GitHubPullRequestService.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        ProjectFlowService projectFlowService = mock(ProjectFlowService.class);
        FalsificationRunRepository runRepository = mock(FalsificationRunRepository.class);
        FalsificationCycleService service = newService(gitHub, roleRepository, projectFlowService, runRepository);

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("github-disabled-project");
        project.setWorkspacePath(null);

        when(roleRepository.findAll()).thenReturn(List.of(role("BARCAN-TAG-02")));
        when(runRepository.findTopByProjectIdOrderByRunAtDesc(project.getId())).thenReturn(Optional.empty());
        when(gitHub.pullRequestSnapshot(project)).thenReturn(
                new GitHubPullRequestService.PullRequestSnapshot(false, "", "", List.of(), List.of(), "GitHub integration disabled or token missing"));

        service.executeCycleForProject(project);

        verify(projectFlowService, never()).dispatchFalsificationAudit(any(), any(), any());
    }
}
