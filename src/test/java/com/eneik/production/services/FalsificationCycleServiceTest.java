package com.eneik.production.services;

import com.eneik.production.models.persistence.FalsificationRunEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
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
import static org.mockito.Mockito.times;
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
                readinessService,
                mock(WishlistContentSimilarityMatcher.class),
                mock(com.eneik.production.services.GeminiContextService.class),
                mock(com.eneik.production.repositories.TaskRepository.class),
                mock(com.eneik.production.repositories.JulesSessionRepository.class),
                mock(com.eneik.production.services.PersistentWorkerSessionService.class)
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
                "https://github.com/org/repo/pull/42", 42, "Implement spintax parser", "jules-branch", "eneikdru", true, "main", false, java.time.Instant.now());
        when(gitHub.pullRequestSnapshot(project)).thenReturn(
                new GitHubPullRequestService.PullRequestSnapshot(true, "org", "repo", List.of(), List.of(mergedPr), ""));
        String realDiff = "diff --git a/src/Spintax.java b/src/Spintax.java\n+public class Spintax {\n+    // real code change\n+}\n";
        when(gitHub.fetchDiffText(project, 42)).thenReturn(Optional.of(realDiff));
        when(projectFlowService.dispatchFalsificationAudit(eq(project), any(), any(), any())).thenReturn(UUID.randomUUID());

        service.executeCycleForProject(project);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> prNumberCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(projectFlowService).dispatchFalsificationAudit(eq(project), promptCaptor.capture(), prNumberCaptor.capture(), any());
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
                "https://github.com/org/repo/pull/42", 42, "Implement spintax parser", "jules-branch", "eneikdru", true, "main", false, java.time.Instant.now());
        GitHubPullRequestService.GitHubPullRequest newlyMerged = new GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/org/repo/pull/43", 43, "Fix account proxy binding", "jules-branch-2", "eneikdru", true, "main", false, java.time.Instant.now());
        when(gitHub.pullRequestSnapshot(project)).thenReturn(
                new GitHubPullRequestService.PullRequestSnapshot(true, "org", "repo", List.of(), List.of(alreadyAudited, newlyMerged), ""));
        when(gitHub.fetchDiffText(project, 43)).thenReturn(Optional.of("diff --git a/src/Proxy.java b/src/Proxy.java\n+// proxy fix\n"));
        when(projectFlowService.dispatchFalsificationAudit(eq(project), any(), any(), any())).thenReturn(UUID.randomUUID());

        service.executeCycleForProject(project);

        verify(gitHub, never()).fetchDiffText(project, 42);
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(projectFlowService).dispatchFalsificationAudit(eq(project), promptCaptor.capture(), eq(43), any());
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

        verify(projectFlowService, never()).dispatchFalsificationAudit(any(), any(), any(), any());
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

        verify(projectFlowService, never()).dispatchFalsificationAudit(any(), any(), any(), any());
    }

    @Test
    void incompleteDecompositionBlocksAuditEvenWhenCurrentMergeRatioIsOne() {
        GitHubPullRequestService gitHub = mock(GitHubPullRequestService.class);
        RoleRepository roles = mock(RoleRepository.class);
        ProjectFlowService flow = mock(ProjectFlowService.class);
        ClientDeliverableReadinessService readiness = mock(ClientDeliverableReadinessService.class);
        when(readiness.computeForProject(any())).thenReturn(
                new ClientDeliverableReadinessService.Readiness(1, 1, 1, 1, 1.0, false));
        FalsificationCycleService service = new FalsificationCycleService(
                mock(ProjectRepository.class), roles, mock(RoleCapabilityLoader.class),
                mock(WishlistRepository.class), mock(FalsificationRunRepository.class),
                mock(SystemSettingsService.class), gitHub, flow, readiness,
                mock(WishlistContentSimilarityMatcher.class),
                mock(com.eneik.production.services.GeminiContextService.class),
                mock(com.eneik.production.repositories.TaskRepository.class),
                mock(com.eneik.production.repositories.JulesSessionRepository.class),
                mock(com.eneik.production.services.PersistentWorkerSessionService.class));
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("still-decomposing");

        service.executeCycleForProject(project);

        verify(flow, never()).dispatchFalsificationAudit(any(), any(), any(), any());
        verify(gitHub, never()).pullRequestSnapshot(any());
    }

    @Test
    void oneAuditCreatesOneConsolidatedWishlistForSeveralViolations() {
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        FalsificationRunRepository runs = mock(FalsificationRunRepository.class);
        when(roles.findAll()).thenReturn(List.of(role("BARCAN-TAG-02"), role("BARCAN-TAG-11")));
        when(wishlistRepository.save(any(WishlistEntity.class))).thenAnswer(invocation -> {
            WishlistEntity item = invocation.getArgument(0);
            item.setId(UUID.randomUUID());
            return item;
        });
        FalsificationCycleService service = new FalsificationCycleService(
                mock(ProjectRepository.class), roles, mock(RoleCapabilityLoader.class),
                wishlistRepository, runs, mock(SystemSettingsService.class),
                mock(GitHubPullRequestService.class), mock(ProjectFlowService.class),
                mock(ClientDeliverableReadinessService.class), mock(WishlistContentSimilarityMatcher.class),
                mock(com.eneik.production.services.GeminiContextService.class),
                mock(com.eneik.production.repositories.TaskRepository.class),
                mock(com.eneik.production.repositories.JulesSessionRepository.class),
                mock(com.eneik.production.services.PersistentWorkerSessionService.class));
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("bounded-cycle");
        List<FalsificationCycleService.AuditViolation> violations = List.of(
                new FalsificationCycleService.AuditViolation(
                        "BARCAN-TAG-02", "refusal_criteria", "API accepts invalid input",
                        "", "", "", "", "", ""),
                new FalsificationCycleService.AuditViolation(
                        "BARCAN-TAG-11", "methodological", "",
                        "Davidson", "UI contradicts the shared contract", "3",
                        "render correct state", "keep latency visible", "add recovery affordance")
        );

        service.applyAuditViolations(project, violations, 41);

        ArgumentCaptor<WishlistEntity> wishlistCaptor = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlistRepository, times(1)).save(wishlistCaptor.capture());
        WishlistEntity created = wishlistCaptor.getValue();
        assertEquals(WishlistSource.self_falsification, created.getSource());
        assertEquals("BARCAN-TAG-09", created.getSourceRoleTag());
        assertEquals(null, created.getCompiledByRole(), "the new cycle must pass through feature/task decomposition");
        assertTrue(created.getContent().contains("Finding 1 [BARCAN-TAG-02/refusal_criteria]"));
        assertTrue(created.getContent().contains("Finding 2 [BARCAN-TAG-11/methodological]"));
    }

    // --- Philosophical falsification track (2026-07-25) ---------------------------------------------

    private FalsificationCycleService newPhilosophicalService(RoleRepository roleRepository,
                                                                ProjectFlowService projectFlowService,
                                                                WishlistRepository wishlistRepository,
                                                                SystemSettingsService settingsService,
                                                                FalsificationRunRepository runRepository) {
        ClientDeliverableReadinessService readinessService = mock(ClientDeliverableReadinessService.class);
        when(readinessService.computeForProject(any())).thenReturn(
                new ClientDeliverableReadinessService.Readiness(1, 1, 1, 1, 1.0, true));
        return new FalsificationCycleService(
                mock(ProjectRepository.class), roleRepository, mock(RoleCapabilityLoader.class),
                wishlistRepository, runRepository, settingsService,
                mock(GitHubPullRequestService.class), projectFlowService, readinessService,
                mock(WishlistContentSimilarityMatcher.class),
                mock(com.eneik.production.services.GeminiContextService.class),
                mock(com.eneik.production.repositories.TaskRepository.class),
                mock(com.eneik.production.repositories.JulesSessionRepository.class),
                mock(com.eneik.production.services.PersistentWorkerSessionService.class));
    }

    @Test
    void weeklyCycleSkippedWhenFeatureFlagDisabled() {
        RoleRepository roles = mock(RoleRepository.class);
        ProjectFlowService flow = mock(ProjectFlowService.class);
        SystemSettingsService settings = mock(SystemSettingsService.class);
        when(settings.effectiveBoolean("philosophical_falsification_enabled")).thenReturn(false);
        FalsificationCycleService service = newPhilosophicalService(
                roles, flow, mock(WishlistRepository.class), settings, mock(FalsificationRunRepository.class));
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("flag-off-project");

        service.executePhilosophicalCycleForProject(project);

        verify(flow, never()).dispatchToPhilosophicalAuditPersistentWorker(any(), any(), any(), any());
    }

    @Test
    void philosophicalCycleSkippedWhenPendingWishlistCapReached() {
        RoleRepository roles = mock(RoleRepository.class);
        ProjectFlowService flow = mock(ProjectFlowService.class);
        SystemSettingsService settings = mock(SystemSettingsService.class);
        when(settings.effectiveBoolean("philosophical_falsification_enabled")).thenReturn(true);
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        FalsificationCycleService service = newPhilosophicalService(
                roles, flow, wishlistRepository, settings, mock(FalsificationRunRepository.class));
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("capped-project");
        // No active worker in flight yet (Mockito default: empty Optional) - the pending-wishlist cap only
        // gates STARTING a brand new discussion, so at least one active role must exist or the method
        // returns before ever reaching that check.
        when(roles.findAll()).thenReturn(List.of(role("BARCAN-TAG-01")));
        // Must actually reach MAX_PENDING_PHILOSOPHICAL_WISHLISTS (10) - a smaller stubbed count here would
        // let the cap check pass through without binding, silently testing nothing (the pre-existing bug
        // this exact mistake would reproduce: this test used to "pass" only because roles.findAll() was
        // left unstubbed/empty, short-circuiting on the unrelated active-roles-empty check before the cap
        // check ever ran).
        when(wishlistRepository.countByProjectIdAndSourceAndStatus(
                project.getId(), WishlistSource.philosophical_falsification, com.eneik.production.models.persistence.WishlistStatus.pending))
                .thenReturn(10L);

        service.executePhilosophicalCycleForProject(project);

        verify(flow, never()).dispatchToPhilosophicalAuditPersistentWorker(any(), any(), any(), any());
    }

    @Test
    void philosophicalCyclePromptInstructsGenuineReasoningForcedKanoAndCleanCommits() {
        RoleRepository roles = mock(RoleRepository.class);
        ProjectFlowService flow = mock(ProjectFlowService.class);
        SystemSettingsService settings = mock(SystemSettingsService.class);
        when(settings.effectiveBoolean("philosophical_falsification_enabled")).thenReturn(true);
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        FalsificationCycleService service = newPhilosophicalService(
                roles, flow, wishlistRepository, settings, mock(FalsificationRunRepository.class));
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("prompt-project");
        RoleEntity roleWithCharter = role("BARCAN-TAG-11");
        try {
            java.nio.file.Path tempCharter = java.nio.file.Files.createTempFile("barcan-tag-11-charter", ".md");
            java.nio.file.Files.writeString(tempCharter, "| 1 | **Patricia Churchland** | neurophilosophy |\n");
            tempCharter.toFile().deleteOnExit();
            roleWithCharter.setRulesPath(tempCharter.toString());
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        when(roles.findAll()).thenReturn(List.of(roleWithCharter));
        when(flow.dispatchToPhilosophicalAuditPersistentWorker(any(), any(), any(), any())).thenReturn(true);

        service.executePhilosophicalCycleForProject(project);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(flow).dispatchToPhilosophicalAuditPersistentWorker(eq(project), eq(List.of("BARCAN-TAG-11")), promptCaptor.capture(), any());
        // Normalize whitespace: the prompt is a Java text block wrapped at ~100 chars for source
        // readability, so a phrase asserted here may legitimately contain an embedded newline where the
        // text block happened to wrap - collapse all whitespace runs to a single space before matching so
        // the assertions test the actual words, not incidental source formatting.
        String prompt = promptCaptor.getValue().replaceAll("\\s+", " ");

        assertTrue(prompt.contains("reason as that actual historical thinker"),
                "prompt must ask for genuine reasoning, not the charter's narrow application column");
        assertTrue(prompt.contains("no default"), "prompt must forbid a default/omitted Kano class");
        assertTrue(prompt.contains("do NOT commit") || prompt.contains("do not commit") || prompt.toLowerCase(java.util.Locale.ROOT).contains("do not commit"),
                "prompt must warn against committing playwright-report/test-results clutter");
        assertTrue(prompt.contains("ROLE BARCAN-TAG-11 CHARTER"), "prompt must inject the real charter verbatim");
    }

    @Test
    void clusteringGroupsConvergingVoicesAndMajorityVoteDecidesKanoIncludingMustBe() {
        // Operator directive (2026-07-25): no per-critique Kano/confidence filtering - every voice is
        // clustered by similarity, and a cluster's Kano is the MAJORITY vote among its members (ties broken
        // toward the more assertive class), not a hard gate that discards Must-Be/low-confidence outright.
        // Uses the REAL matcher (not a mock) so actual Jaccard clustering runs - a mock would return an
        // empty cluster list by default and silently make this test meaningless.
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        FalsificationRunRepository runRepository = mock(FalsificationRunRepository.class);
        when(wishlistRepository.save(any(WishlistEntity.class))).thenAnswer(invocation -> {
            WishlistEntity item = invocation.getArgument(0);
            item.setId(UUID.randomUUID());
            return item;
        });
        when(wishlistRepository.findByProjectIdAndSourceAndStatusIn(any(), eq(WishlistSource.philosophical_falsification), any()))
                .thenReturn(List.of());
        when(wishlistRepository.countByProjectIdAndSourceAndStatus(any(), eq(WishlistSource.philosophical_falsification), any()))
                .thenReturn(0L);
        FalsificationCycleService service = new FalsificationCycleService(
                mock(ProjectRepository.class), mock(RoleRepository.class), mock(RoleCapabilityLoader.class),
                wishlistRepository, runRepository, mock(SystemSettingsService.class),
                mock(GitHubPullRequestService.class), mock(ProjectFlowService.class),
                mock(ClientDeliverableReadinessService.class), new WishlistContentSimilarityMatcher(),
                mock(com.eneik.production.services.GeminiContextService.class),
                mock(com.eneik.production.repositories.TaskRepository.class),
                mock(com.eneik.production.repositories.JulesSessionRepository.class),
                mock(com.eneik.production.services.PersistentWorkerSessionService.class));
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("clustering-project");

        String onboardingProposal = "Add a trust building onboarding tour explaining account activation and permissions to new users clearly and calmly";
        String onboardingCritique = "The current interface leaves new users uncertain about why account access is being requested";
        String errorToneProposal = "Rewrite error messages to sound calmer and less alarming for users encountering failures during checkout";
        String errorToneCritique = "The current error tone feels punitive and increases user anxiety during failures";
        String paginationProposal = "Leave pagination controls exactly as they are since users have not expressed any confusion";
        String paginationCritique = "Pagination behavior is already clear and consistent across the product";
        String darkModeProposal = "Add a dark mode theme toggle to reduce eye strain for users working at night";
        String darkModeCritique = "Some users have mentioned bright screens are uncomfortable in low light settings";

        List<FalsificationCycleService.PhilosophicalCritique> critiques = List.of(
                // Cluster A: onboarding/trust theme, 3 voices, 2 Attractive + 1 Must-Be -> majority Attractive
                critiqueWithText("BARCAN-TAG-11", "Patricia Churchland", "Attractive", onboardingProposal, onboardingCritique),
                critiqueWithText("BARCAN-TAG-11", "Martha Nussbaum", "Attractive", onboardingProposal, onboardingCritique),
                critiqueWithText("BARCAN-TAG-09", "Robert Brandom", "Must-Be", onboardingProposal, onboardingCritique),
                // Cluster B: error-message tone theme, 2 voices, BOTH Must-Be -> majority Must-Be (this is
                // the direct test of the reversed decision: Must-Be must now be able to become a wishlist)
                critiqueWithText("BARCAN-TAG-06", "Karl Popper", "Must-Be", errorToneProposal, errorToneCritique),
                critiqueWithText("BARCAN-TAG-07", "Timothy Williamson", "Must-Be", errorToneProposal, errorToneCritique),
                // Cluster C: pagination theme, 2 voices, BOTH Indifferent -> no wishlist (aggregated "nothing to do")
                critiqueWithText("BARCAN-TAG-11", "Ned Block", "Indifferent", paginationProposal, paginationCritique),
                critiqueWithText("BARCAN-TAG-03", "Andy Clark", "Indifferent", paginationProposal, paginationCritique),
                // Singleton D: dark mode, unrelated topic, 1 voice only -> still becomes its own wishlist,
                // proving a lone voice is never discarded just for lacking corroboration
                critiqueWithText("BARCAN-TAG-11", "Jaegwon Kim", "Attractive", darkModeProposal, darkModeCritique)
        );

        service.applyPhilosophicalCritiques(project, critiques, null);

        ArgumentCaptor<WishlistEntity> wishlistCaptor = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlistRepository, times(3)).save(wishlistCaptor.capture());
        List<WishlistEntity> saved = wishlistCaptor.getAllValues();
        for (WishlistEntity w : saved) {
            assertEquals(WishlistSource.philosophical_falsification, w.getSource());
        }
        assertTrue(saved.stream().anyMatch(w -> w.getContent().contains("Kano: Attractive") && w.getContent().contains("Voice 3")),
                "the onboarding cluster (3 voices, majority Attractive) must produce one wishlist listing all 3 voices");
        assertTrue(saved.stream().anyMatch(w -> w.getContent().contains("Kano: Must-Be") && w.getContent().contains("Karl Popper")),
                "a cluster whose members are ALL Must-Be must still produce a wishlist now - Must-Be is no longer discarded");
        assertTrue(saved.stream().anyMatch(w -> w.getContent().contains("Jaegwon Kim")),
                "a single unclustered voice must still become its own wishlist, not be dropped for lacking corroboration");
        assertTrue(saved.stream().noneMatch(w -> w.getContent().contains("Ned Block")),
                "a cluster whose majority is Indifferent must NOT produce a wishlist - that is the aggregated conclusion");
    }

    private FalsificationCycleService.PhilosophicalCritique critiqueWithText(
            String roleTag, String philosopher, String kanoClass, String proposal, String critiqueText) {
        return new FalsificationCycleService.PhilosophicalCritique(
                roleTag, philosopher, "a real worldview summary", critiqueText,
                proposal, "", kanoClass, "high", "main dashboard screen", "");
    }

    @Test
    void applyPhilosophicalCritiquesNeverTouchesTheFormalTracksWatermark() {
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        FalsificationRunRepository runRepository = mock(FalsificationRunRepository.class);
        when(wishlistRepository.save(any(WishlistEntity.class))).thenAnswer(invocation -> {
            WishlistEntity item = invocation.getArgument(0);
            item.setId(UUID.randomUUID());
            return item;
        });
        when(wishlistRepository.findByProjectIdAndSourceAndStatusIn(any(), eq(WishlistSource.philosophical_falsification), any()))
                .thenReturn(List.of());
        FalsificationCycleService service = new FalsificationCycleService(
                mock(ProjectRepository.class), mock(RoleRepository.class), mock(RoleCapabilityLoader.class),
                wishlistRepository, runRepository, mock(SystemSettingsService.class),
                mock(GitHubPullRequestService.class), mock(ProjectFlowService.class),
                mock(ClientDeliverableReadinessService.class), mock(WishlistContentSimilarityMatcher.class),
                mock(com.eneik.production.services.GeminiContextService.class),
                mock(com.eneik.production.repositories.TaskRepository.class),
                mock(com.eneik.production.repositories.JulesSessionRepository.class),
                mock(com.eneik.production.services.PersistentWorkerSessionService.class));
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("watermark-independence-project");

        service.applyPhilosophicalCritiques(project,
                List.of(critique("BARCAN-TAG-11", "Philosopher X", "Attractive", "high")), null);

        verify(runRepository, never()).save(any());
        verify(runRepository, never()).findTopByProjectIdOrderByRunAtDesc(any());
    }

    private FalsificationCycleService.PhilosophicalCritique critique(String roleTag, String philosopher, String kanoClass, String confidence) {
        return new FalsificationCycleService.PhilosophicalCritique(
                roleTag, philosopher, "a real worldview summary", "a genuine critique",
                "a concrete proposal for " + philosopher, "", kanoClass, confidence, "main dashboard screen", "");
    }
}
