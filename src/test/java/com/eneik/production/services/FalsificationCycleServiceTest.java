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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
                mock(com.eneik.production.services.PersistentWorkerSessionService.class),
                mock(com.eneik.production.repositories.PrReviewRepository.class),
                mock(com.eneik.production.repositories.CodeIntegrityFindingRepository.class),
                mock(com.eneik.production.repositories.EvidenceNodeRepository.class), mock(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.class), mock(com.eneik.production.services.runtime.RuntimeLauncherClient.class)
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
        when(projectFlowService.dispatchFalsificationAudit(eq(project), any(), any(), any()))
                .thenReturn(new ProjectFlowService.AuditDispatchResult(UUID.randomUUID(), true));

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
        when(projectFlowService.dispatchFalsificationAudit(eq(project), any(), any(), any()))
                .thenReturn(new ProjectFlowService.AuditDispatchResult(UUID.randomUUID(), true));

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
                mock(com.eneik.production.services.PersistentWorkerSessionService.class),
                mock(com.eneik.production.repositories.PrReviewRepository.class),
                mock(com.eneik.production.repositories.CodeIntegrityFindingRepository.class),
                mock(com.eneik.production.repositories.EvidenceNodeRepository.class), mock(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.class), mock(com.eneik.production.services.runtime.RuntimeLauncherClient.class));
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
        com.eneik.production.repositories.CodeIntegrityFindingRepository codeIntegrityFindingRepository =
                mock(com.eneik.production.repositories.CodeIntegrityFindingRepository.class);
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
                mock(com.eneik.production.services.PersistentWorkerSessionService.class),
                mock(com.eneik.production.repositories.PrReviewRepository.class),
                codeIntegrityFindingRepository, mock(com.eneik.production.repositories.EvidenceNodeRepository.class), mock(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.class), mock(com.eneik.production.services.runtime.RuntimeLauncherClient.class));
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("bounded-cycle");
        List<FalsificationCycleService.AuditViolation> violations = List.of(
                new FalsificationCycleService.AuditViolation(
                        "BARCAN-TAG-02", "refusal_criteria", "API accepts invalid input",
                        "", "", "", "", "", "", null),
                new FalsificationCycleService.AuditViolation(
                        "BARCAN-TAG-11", "methodological", "",
                        "Davidson", "UI contradicts the shared contract", "3",
                        "render correct state", "keep latency visible", "add recovery affordance", null)
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
        // Neither violation above is stub/layer_violation - the code-integrity instrumentation must stay
        // silent for other violation types, not fabricate a finding row for unrelated categories.
        verify(codeIntegrityFindingRepository, never()).save(any());
    }

    // --- Code-integrity findings: stub/layer_violation -> per-feature attribution (2026-08-05) --------

    @Test
    void stubFindingWithResolvablePrNumberPersistsWithRealFeatureId() {
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        FalsificationRunRepository runs = mock(FalsificationRunRepository.class);
        com.eneik.production.repositories.PrReviewRepository prReviewRepository =
                mock(com.eneik.production.repositories.PrReviewRepository.class);
        com.eneik.production.repositories.CodeIntegrityFindingRepository codeIntegrityFindingRepository =
                mock(com.eneik.production.repositories.CodeIntegrityFindingRepository.class);
        com.eneik.production.repositories.JulesSessionRepository julesSessionRepository =
                mock(com.eneik.production.repositories.JulesSessionRepository.class);
        com.eneik.production.repositories.TaskRepository taskRepository =
                mock(com.eneik.production.repositories.TaskRepository.class);
        when(roles.findAll()).thenReturn(List.of(role("BARCAN-TAG-11")));
        when(wishlistRepository.save(any(WishlistEntity.class))).thenAnswer(invocation -> {
            WishlistEntity item = invocation.getArgument(0);
            item.setId(UUID.randomUUID());
            return item;
        });
        when(runs.save(any(FalsificationRunEntity.class))).thenAnswer(invocation -> {
            FalsificationRunEntity run = invocation.getArgument(0);
            run.setId(UUID.randomUUID());
            return run;
        });

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("attribution-project");

        UUID featureId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        com.eneik.production.models.persistence.PrReviewEntity review = new com.eneik.production.models.persistence.PrReviewEntity();
        review.setJulesSessionId(sessionId);
        review.setPrNumber(47);
        when(prReviewRepository.findAll()).thenReturn(List.of(review));

        com.eneik.production.models.persistence.JulesSessionEntity session = new com.eneik.production.models.persistence.JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        when(julesSessionRepository.findById(sessionId)).thenReturn(java.util.Optional.of(session));

        com.eneik.production.models.persistence.TaskEntity task = new com.eneik.production.models.persistence.TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setFeatureId(featureId);
        when(taskRepository.findById(taskId)).thenReturn(java.util.Optional.of(task));

        FalsificationCycleService service = new FalsificationCycleService(
                mock(ProjectRepository.class), roles, mock(RoleCapabilityLoader.class),
                wishlistRepository, runs, mock(SystemSettingsService.class),
                mock(GitHubPullRequestService.class), mock(ProjectFlowService.class),
                mock(ClientDeliverableReadinessService.class), mock(WishlistContentSimilarityMatcher.class),
                mock(com.eneik.production.services.GeminiContextService.class),
                taskRepository, julesSessionRepository,
                mock(com.eneik.production.services.PersistentWorkerSessionService.class),
                prReviewRepository, codeIntegrityFindingRepository, mock(com.eneik.production.repositories.EvidenceNodeRepository.class), mock(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.class), mock(com.eneik.production.services.runtime.RuntimeLauncherClient.class));

        List<FalsificationCycleService.AuditViolation> violations = List.of(
                new FalsificationCycleService.AuditViolation(
                        "BARCAN-TAG-11", "stub", "fakes success, no real work",
                        "", "", "", "", "", "", 47));

        service.applyAuditViolations(project, violations, 47);

        ArgumentCaptor<com.eneik.production.models.persistence.CodeIntegrityFindingEntity> findingCaptor =
                ArgumentCaptor.forClass(com.eneik.production.models.persistence.CodeIntegrityFindingEntity.class);
        verify(codeIntegrityFindingRepository, times(1)).save(findingCaptor.capture());
        var finding = findingCaptor.getValue();
        assertEquals(featureId, finding.getFeatureId(), "a cited PR that resolves to a real review must attribute the finding to that PR's own feature");
        assertEquals("stub", finding.getFindingType());
        assertEquals(project.getId(), finding.getProjectId());
        assertEquals(47, finding.getPrNumber());
    }

    // --- impact_coefficients Jidoka wiring (2026-08-07) ------------------------------------------------

    @Test
    void confirmedFindingBoostsQueuedSiblingTaskWithHighImpactCoefficient() {
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        FalsificationRunRepository runs = mock(FalsificationRunRepository.class);
        com.eneik.production.repositories.PrReviewRepository prReviewRepository =
                mock(com.eneik.production.repositories.PrReviewRepository.class);
        com.eneik.production.repositories.CodeIntegrityFindingRepository codeIntegrityFindingRepository =
                mock(com.eneik.production.repositories.CodeIntegrityFindingRepository.class);
        com.eneik.production.repositories.JulesSessionRepository julesSessionRepository =
                mock(com.eneik.production.repositories.JulesSessionRepository.class);
        com.eneik.production.repositories.TaskRepository taskRepository =
                mock(com.eneik.production.repositories.TaskRepository.class);
        when(roles.findAll()).thenReturn(List.of(role("BARCAN-TAG-08")));
        when(wishlistRepository.save(any(WishlistEntity.class))).thenAnswer(invocation -> {
            WishlistEntity item = invocation.getArgument(0);
            item.setId(UUID.randomUUID());
            return item;
        });
        when(runs.save(any(FalsificationRunEntity.class))).thenAnswer(invocation -> {
            FalsificationRunEntity run = invocation.getArgument(0);
            run.setId(UUID.randomUUID());
            return run;
        });

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("jidoka-project");

        UUID featureId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID offendingTaskId = UUID.randomUUID();

        com.eneik.production.models.persistence.PrReviewEntity review = new com.eneik.production.models.persistence.PrReviewEntity();
        review.setJulesSessionId(sessionId);
        review.setPrNumber(47);
        when(prReviewRepository.findAll()).thenReturn(List.of(review));

        com.eneik.production.models.persistence.JulesSessionEntity session = new com.eneik.production.models.persistence.JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(offendingTaskId);
        when(julesSessionRepository.findById(sessionId)).thenReturn(java.util.Optional.of(session));

        // The offending task: role BARCAN-TAG-08 (Data Schema), carries the impact matrix real tasks
        // already get from TechnicalLeadCompiler.createImpactMatrix - BARCAN-TAG-02 is a high-impact
        // (0.9) affected role, BARCAN-TAG-11 is low-impact (0.1).
        com.fasterxml.jackson.databind.node.ObjectNode impactMatrix =
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        impactMatrix.put("BARCAN-TAG-02", 0.9);
        impactMatrix.put("BARCAN-TAG-11", 0.1);
        com.fasterxml.jackson.databind.node.ObjectNode offendingPayload =
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        offendingPayload.set("impact_coefficients", impactMatrix);

        com.eneik.production.models.persistence.TaskEntity offendingTask = new com.eneik.production.models.persistence.TaskEntity();
        offendingTask.setId(offendingTaskId);
        offendingTask.setProject(project);
        offendingTask.setFeatureId(featureId);
        offendingTask.setRole(role("BARCAN-TAG-08"));
        offendingTask.setPayload(offendingPayload);
        when(taskRepository.findById(offendingTaskId)).thenReturn(java.util.Optional.of(offendingTask));

        com.eneik.production.models.persistence.TaskEntity highImpactSibling = new com.eneik.production.models.persistence.TaskEntity();
        highImpactSibling.setId(UUID.randomUUID());
        highImpactSibling.setProject(project);
        highImpactSibling.setFeatureId(featureId);
        highImpactSibling.setRole(role("BARCAN-TAG-02"));
        highImpactSibling.setStatus(com.eneik.production.models.persistence.TaskStatus.queued);
        highImpactSibling.setPriority(10);

        com.eneik.production.models.persistence.TaskEntity lowImpactSibling = new com.eneik.production.models.persistence.TaskEntity();
        lowImpactSibling.setId(UUID.randomUUID());
        lowImpactSibling.setProject(project);
        lowImpactSibling.setFeatureId(featureId);
        lowImpactSibling.setRole(role("BARCAN-TAG-11"));
        lowImpactSibling.setStatus(com.eneik.production.models.persistence.TaskStatus.queued);
        lowImpactSibling.setPriority(10);

        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()))
                .thenReturn(List.of(highImpactSibling, lowImpactSibling));

        FalsificationCycleService service = new FalsificationCycleService(
                mock(ProjectRepository.class), roles, mock(RoleCapabilityLoader.class),
                wishlistRepository, runs, mock(SystemSettingsService.class),
                mock(GitHubPullRequestService.class), mock(ProjectFlowService.class),
                mock(ClientDeliverableReadinessService.class), mock(WishlistContentSimilarityMatcher.class),
                mock(com.eneik.production.services.GeminiContextService.class),
                taskRepository, julesSessionRepository,
                mock(com.eneik.production.services.PersistentWorkerSessionService.class),
                prReviewRepository, codeIntegrityFindingRepository, mock(com.eneik.production.repositories.EvidenceNodeRepository.class), mock(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.class), mock(com.eneik.production.services.runtime.RuntimeLauncherClient.class));

        List<FalsificationCycleService.AuditViolation> violations = List.of(
                new FalsificationCycleService.AuditViolation(
                        "BARCAN-TAG-08", "stub", "fakes success, no real work",
                        "", "", "", "", "", "", 47));

        service.applyAuditViolations(project, violations, 47);

        assertEquals(100, highImpactSibling.getPriority(), "a sibling task in a role with impact coefficient >= 0.8 from the offending role must be boosted");
        assertEquals(10, lowImpactSibling.getPriority(), "a sibling task in a low-impact role must NOT be boosted");
        verify(taskRepository, times(1)).save(highImpactSibling);
        verify(taskRepository, never()).save(lowImpactSibling);
    }

    @Test
    void stubFindingWithUnresolvablePrNumberStillPersistsWithNullFeatureId() {
        // Regression guard: an unattributed finding must NOT be silently dropped just because its cited
        // PR number doesn't resolve to any known review - it still counts toward Product-layer Six Sigma,
        // just without a specific feature to sharpen.
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        FalsificationRunRepository runs = mock(FalsificationRunRepository.class);
        com.eneik.production.repositories.PrReviewRepository prReviewRepository =
                mock(com.eneik.production.repositories.PrReviewRepository.class);
        com.eneik.production.repositories.CodeIntegrityFindingRepository codeIntegrityFindingRepository =
                mock(com.eneik.production.repositories.CodeIntegrityFindingRepository.class);
        when(roles.findAll()).thenReturn(List.of(role("BARCAN-TAG-11")));
        when(wishlistRepository.save(any(WishlistEntity.class))).thenAnswer(invocation -> {
            WishlistEntity item = invocation.getArgument(0);
            item.setId(UUID.randomUUID());
            return item;
        });
        when(runs.save(any(FalsificationRunEntity.class))).thenAnswer(invocation -> {
            FalsificationRunEntity run = invocation.getArgument(0);
            run.setId(UUID.randomUUID());
            return run;
        });
        when(prReviewRepository.findAll()).thenReturn(List.of());

        FalsificationCycleService service = new FalsificationCycleService(
                mock(ProjectRepository.class), roles, mock(RoleCapabilityLoader.class),
                wishlistRepository, runs, mock(SystemSettingsService.class),
                mock(GitHubPullRequestService.class), mock(ProjectFlowService.class),
                mock(ClientDeliverableReadinessService.class), mock(WishlistContentSimilarityMatcher.class),
                mock(com.eneik.production.services.GeminiContextService.class),
                mock(com.eneik.production.repositories.TaskRepository.class),
                mock(com.eneik.production.repositories.JulesSessionRepository.class),
                mock(com.eneik.production.services.PersistentWorkerSessionService.class),
                prReviewRepository, codeIntegrityFindingRepository, mock(com.eneik.production.repositories.EvidenceNodeRepository.class), mock(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.class), mock(com.eneik.production.services.runtime.RuntimeLauncherClient.class));
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("unattributed-project");

        List<FalsificationCycleService.AuditViolation> violations = List.of(
                new FalsificationCycleService.AuditViolation(
                        "BARCAN-TAG-11", "layer_violation", "UI calls an endpoint that does not exist",
                        "", "", "", "", "", "", 999));

        service.applyAuditViolations(project, violations, null);

        ArgumentCaptor<com.eneik.production.models.persistence.CodeIntegrityFindingEntity> findingCaptor =
                ArgumentCaptor.forClass(com.eneik.production.models.persistence.CodeIntegrityFindingEntity.class);
        verify(codeIntegrityFindingRepository, times(1)).save(findingCaptor.capture());
        var finding = findingCaptor.getValue();
        assertEquals(null, finding.getFeatureId(), "an unresolvable PR citation must not be dropped - just left unattributed");
        assertEquals("layer_violation", finding.getFindingType());
    }

    @Test
    void buildAuditPromptContainsStubAndLayerViolationCategoriesWithPrNumberField() {
        GitHubPullRequestService gitHub = mock(GitHubPullRequestService.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        ProjectFlowService projectFlowService = mock(ProjectFlowService.class);
        FalsificationRunRepository runRepository = mock(FalsificationRunRepository.class);
        FalsificationCycleService service = newService(gitHub, roleRepository, projectFlowService, runRepository);

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("prompt-content-project");

        when(roleRepository.findAll()).thenReturn(List.of(role("BARCAN-TAG-02")));
        when(runRepository.findTopByProjectIdOrderByRunAtDesc(project.getId())).thenReturn(Optional.empty());
        GitHubPullRequestService.GitHubPullRequest mergedPr = new GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/org/repo/pull/47", 47, "Add feature", "jules-branch", "eneikdru", true, "main", false, java.time.Instant.now());
        when(gitHub.pullRequestSnapshot(project)).thenReturn(
                new GitHubPullRequestService.PullRequestSnapshot(true, "org", "repo", List.of(), List.of(mergedPr), ""));
        when(gitHub.fetchDiffText(project, 47)).thenReturn(Optional.of("diff --git a/src/Foo.java b/src/Foo.java\n+// real\n"));
        when(projectFlowService.dispatchFalsificationAudit(eq(project), any(), any(), any()))
                .thenReturn(new ProjectFlowService.AuditDispatchResult(UUID.randomUUID(), true));

        service.executeCycleForProject(project);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(projectFlowService).dispatchFalsificationAudit(eq(project), promptCaptor.capture(), any(), any());
        String prompt = promptCaptor.getValue();

        assertTrue(prompt.contains("Stub code"), "prompt must ask the auditor to look for stub code, role-independently");
        assertTrue(prompt.contains("Architectural layer violation"), "prompt must ask the auditor to look for layer violations, role-independently");
        assertTrue(prompt.contains("\"prNumber\""), "prompt's JSON shape example must show the prNumber field for stub/layer_violation findings");
        assertTrue(prompt.contains("\"type\": \"stub\""), "prompt's JSON shape example must show the stub type");
        assertTrue(prompt.contains("\"type\": \"layer_violation\""), "prompt's JSON shape example must show the layer_violation type");
    }

    // --- Philosophical falsification track (2026-07-25) ---------------------------------------------

    private FalsificationCycleService newPhilosophicalService(RoleRepository roleRepository,
                                                                ProjectFlowService projectFlowService,
                                                                WishlistRepository wishlistRepository,
                                                                SystemSettingsService settingsService,
                                                                FalsificationRunRepository runRepository) {
        return newPhilosophicalService(roleRepository, projectFlowService, wishlistRepository, settingsService,
                runRepository, mock(com.eneik.production.services.GeminiContextService.class));
    }

    private FalsificationCycleService newPhilosophicalService(RoleRepository roleRepository,
                                                                ProjectFlowService projectFlowService,
                                                                WishlistRepository wishlistRepository,
                                                                SystemSettingsService settingsService,
                                                                FalsificationRunRepository runRepository,
                                                                com.eneik.production.services.GeminiContextService geminiContextService) {
        ClientDeliverableReadinessService readinessService = mock(ClientDeliverableReadinessService.class);
        when(readinessService.computeForProject(any())).thenReturn(
                new ClientDeliverableReadinessService.Readiness(1, 1, 1, 1, 1.0, true));
        return new FalsificationCycleService(
                mock(ProjectRepository.class), roleRepository, mock(RoleCapabilityLoader.class),
                wishlistRepository, runRepository, settingsService,
                mock(GitHubPullRequestService.class), projectFlowService, readinessService,
                mock(WishlistContentSimilarityMatcher.class),
                geminiContextService,
                mock(com.eneik.production.repositories.TaskRepository.class),
                mock(com.eneik.production.repositories.JulesSessionRepository.class),
                mock(com.eneik.production.services.PersistentWorkerSessionService.class),
                mock(com.eneik.production.repositories.PrReviewRepository.class),
                mock(com.eneik.production.repositories.CodeIntegrityFindingRepository.class),
                mock(com.eneik.production.repositories.EvidenceNodeRepository.class), mock(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.class), mock(com.eneik.production.services.runtime.RuntimeLauncherClient.class));
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

    // 2026-08-09 (live incident, operator-flagged: a genuinely in-progress multi-turn philosophical
    // discussion sat idle for 4.5+ hours because continuing it was throttled by the SAME "start a brand new
    // 13-role discussion" cadence, "раз в 2 дня" - a deliberate operator choice from 2026-07-25/26 for a
    // different question). advanceInProgressPhilosophicalDiscussions must ONLY ever continue an ALREADY
    // active worker, on its own separate fast cadence, and must NEVER start a new discussion itself - that
    // decision stays exclusively with runWeeklyPhilosophicalFalsificationCycle.

    @Test
    void advanceInProgressPhilosophicalDiscussionsContinuesAnExistingWorkerWithTheRemainingRoleBatch() {
        RoleRepository roles = mock(RoleRepository.class);
        ProjectFlowService flow = mock(ProjectFlowService.class);
        SystemSettingsService settings = mock(SystemSettingsService.class);
        when(settings.effectiveBoolean("philosophical_falsification_enabled")).thenReturn(true);
        com.eneik.production.services.PersistentWorkerSessionService workerSessionService =
                mock(com.eneik.production.services.PersistentWorkerSessionService.class);
        com.eneik.production.repositories.ProjectRepository projectRepository =
                mock(com.eneik.production.repositories.ProjectRepository.class);
        com.eneik.production.repositories.TaskRepository taskRepository =
                mock(com.eneik.production.repositories.TaskRepository.class);

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("in-progress-project");
        project.setStatus(com.eneik.production.models.persistence.ProjectStatus.active);
        when(projectRepository.findAll()).thenReturn(List.of(project));

        RoleEntity roleOne = role("BARCAN-TAG-01");
        RoleEntity roleTwo = role("BARCAN-TAG-02");
        when(roles.findAll()).thenReturn(List.of(roleOne, roleTwo));

        UUID carrierTaskId = UUID.randomUUID();
        com.eneik.production.models.persistence.TaskEntity carrierTask =
                new com.eneik.production.models.persistence.TaskEntity();
        carrierTask.setId(carrierTaskId);
        when(taskRepository.findById(carrierTaskId)).thenReturn(Optional.of(carrierTask));

        UUID julesSessionId = UUID.randomUUID();
        com.eneik.production.models.persistence.PersistentWorkerSessionEntity worker =
                new com.eneik.production.models.persistence.PersistentWorkerSessionEntity();
        worker.setId(UUID.randomUUID());
        worker.setProjectId(project.getId());
        worker.setPurpose(com.eneik.production.models.persistence.PersistentWorkerPurpose.PHILOSOPHICAL_AUDIT);
        worker.setCarrierTaskId(carrierTaskId);
        worker.setCurrentJulesSessionId(julesSessionId);
        when(workerSessionService.findActiveWorker(project.getId(),
                com.eneik.production.models.persistence.PersistentWorkerPurpose.PHILOSOPHICAL_AUDIT))
                .thenReturn(Optional.of(worker));

        // 2026-08-09 fix (live incident: "covered" used to be an append-only marker written the moment a
        // batch was SENT, so it could race ahead of what Jules actually answered - confirmed live when
        // BARCAN-TAG-12 was declared "covered" and the whole discussion closed 9 seconds after being asked,
        // with no real critique for it ever written). Covered is now derived by parsing the report file's
        // real content, so this test drives it the same way: only BARCAN-TAG-01 genuinely has a critique on
        // the branch - BARCAN-TAG-02 is the real remaining work this call must send.
        com.eneik.production.models.persistence.JulesSessionEntity julesSession =
                new com.eneik.production.models.persistence.JulesSessionEntity();
        julesSession.setExternalSessionId("sessions/live-discussion");
        com.eneik.production.repositories.JulesSessionRepository julesSessionRepository =
                mock(com.eneik.production.repositories.JulesSessionRepository.class);
        when(julesSessionRepository.findById(julesSessionId)).thenReturn(Optional.of(julesSession));

        GitHubPullRequestService gitHub = mock(GitHubPullRequestService.class);
        GitHubPullRequestService.GitHubPullRequest openPr = new GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/eneikdru/x/pull/1", 1, "Philosophical Product Falsification",
                "task-falsification-live", "eneikdru", false, "main", false, Instant.now());
        when(gitHub.findOpenPullRequestBySession(project, "sessions/live-discussion")).thenReturn(Optional.of(openPr));
        when(gitHub.fetchFileContent(project, "task-falsification-live", ".eneik/records/philosophical-falsification-x.json"))
                .thenReturn(Optional.of("""
                        {"critiques":[{"roleTag":"BARCAN-TAG-01","philosopher":"Test Philosopher",
                        "worldview":"w","critique":"c","proposal":"p","dislike":"d","kanoClass":"must-be",
                        "confidence":"high","evidence":"e","screenshotFile":""}]}
                        """));

        when(flow.philosophicalAuditReportPath(carrierTask)).thenReturn(".eneik/records/philosophical-falsification-x.json");
        when(flow.dispatchToPhilosophicalAuditPersistentWorker(any(), any(), any(), any())).thenReturn(true);

        FalsificationCycleService service = new FalsificationCycleService(
                projectRepository, roles, mock(RoleCapabilityLoader.class),
                mock(WishlistRepository.class), mock(FalsificationRunRepository.class), settings,
                gitHub, flow,
                mock(ClientDeliverableReadinessService.class), mock(WishlistContentSimilarityMatcher.class),
                mock(com.eneik.production.services.GeminiContextService.class),
                taskRepository,
                julesSessionRepository,
                workerSessionService,
                mock(com.eneik.production.repositories.PrReviewRepository.class),
                mock(com.eneik.production.repositories.CodeIntegrityFindingRepository.class),
                mock(com.eneik.production.repositories.EvidenceNodeRepository.class), mock(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.class), mock(com.eneik.production.services.runtime.RuntimeLauncherClient.class));

        service.advanceInProgressPhilosophicalDiscussions();

        verify(flow).dispatchToPhilosophicalAuditPersistentWorker(
                eq(project), eq(List.of("BARCAN-TAG-02")), any(), eq(".eneik/records/philosophical-falsification-x.json"));
    }

    @Test
    void advanceInProgressPhilosophicalDiscussionsNeverSendsAFollowUpTooSoonAfterTheLastMessage() {
        // Live incident, 2026-08-09, found within minutes of this cron's own first deploy: Jules's raw
        // self-reported status can still read "pr_opened" (stale) for a real few seconds right after a
        // brand-new follow-up was just sent, falsely marking the worker idle-and-fresh again almost
        // immediately. Without this guard, this fast cron could re-fire every 15 minutes against that false
        // signal and inflate the covered-role count far ahead of Jules's real work. A worker messaged very
        // recently must never be re-messaged again this cycle, regardless of what its persisted status says.
        RoleRepository roles = mock(RoleRepository.class);
        ProjectFlowService flow = mock(ProjectFlowService.class);
        SystemSettingsService settings = mock(SystemSettingsService.class);
        when(settings.effectiveBoolean("philosophical_falsification_enabled")).thenReturn(true);
        com.eneik.production.services.PersistentWorkerSessionService workerSessionService =
                mock(com.eneik.production.services.PersistentWorkerSessionService.class);
        com.eneik.production.repositories.ProjectRepository projectRepository =
                mock(com.eneik.production.repositories.ProjectRepository.class);

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("just-messaged-project");
        project.setStatus(com.eneik.production.models.persistence.ProjectStatus.active);
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(roles.findAll()).thenReturn(List.of(role("BARCAN-TAG-01"), role("BARCAN-TAG-02")));

        com.eneik.production.models.persistence.PersistentWorkerSessionEntity worker =
                new com.eneik.production.models.persistence.PersistentWorkerSessionEntity();
        worker.setId(UUID.randomUUID());
        worker.setProjectId(project.getId());
        worker.setPurpose(com.eneik.production.models.persistence.PersistentWorkerPurpose.PHILOSOPHICAL_AUDIT);
        worker.setCarrierTaskId(UUID.randomUUID());
        // Messaged 30 seconds ago - nowhere near MIN_MINUTES_BETWEEN_PHILOSOPHICAL_TURNS (20 min).
        worker.setLastMessageSentAt(Instant.now().minusSeconds(30));
        when(workerSessionService.findActiveWorker(project.getId(),
                com.eneik.production.models.persistence.PersistentWorkerPurpose.PHILOSOPHICAL_AUDIT))
                .thenReturn(Optional.of(worker));

        FalsificationCycleService service = new FalsificationCycleService(
                projectRepository, roles, mock(RoleCapabilityLoader.class),
                mock(WishlistRepository.class), mock(FalsificationRunRepository.class), settings,
                mock(GitHubPullRequestService.class), flow,
                mock(ClientDeliverableReadinessService.class), mock(WishlistContentSimilarityMatcher.class),
                mock(com.eneik.production.services.GeminiContextService.class),
                mock(com.eneik.production.repositories.TaskRepository.class),
                mock(com.eneik.production.repositories.JulesSessionRepository.class),
                workerSessionService,
                mock(com.eneik.production.repositories.PrReviewRepository.class),
                mock(com.eneik.production.repositories.CodeIntegrityFindingRepository.class),
                mock(com.eneik.production.repositories.EvidenceNodeRepository.class), mock(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.class), mock(com.eneik.production.services.runtime.RuntimeLauncherClient.class));

        service.advanceInProgressPhilosophicalDiscussions();

        verify(flow, never()).dispatchToPhilosophicalAuditPersistentWorker(any(), any(), any(), any());
        // taskRepository.findById would only be reached inside continuePhilosophicalDiscussion itself -
        // confirming it was never even entered, not just that the final dispatch call didn't happen.
    }

    @Test
    void advanceInProgressPhilosophicalDiscussionsNeverStartsANewDiscussionWhenNoWorkerIsActive() {
        RoleRepository roles = mock(RoleRepository.class);
        ProjectFlowService flow = mock(ProjectFlowService.class);
        SystemSettingsService settings = mock(SystemSettingsService.class);
        when(settings.effectiveBoolean("philosophical_falsification_enabled")).thenReturn(true);
        com.eneik.production.services.PersistentWorkerSessionService workerSessionService =
                mock(com.eneik.production.services.PersistentWorkerSessionService.class);
        com.eneik.production.repositories.ProjectRepository projectRepository =
                mock(com.eneik.production.repositories.ProjectRepository.class);

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("no-worker-yet-project");
        project.setStatus(com.eneik.production.models.persistence.ProjectStatus.active);
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(roles.findAll()).thenReturn(List.of(role("BARCAN-TAG-01")));
        // No active worker (Mockito default: empty Optional) - this fast path must never fall through to
        // starting a brand new 13-role discussion; that stays exclusively on the slow cron.

        FalsificationCycleService service = new FalsificationCycleService(
                projectRepository, roles, mock(RoleCapabilityLoader.class),
                mock(WishlistRepository.class), mock(FalsificationRunRepository.class), settings,
                mock(GitHubPullRequestService.class), flow,
                mock(ClientDeliverableReadinessService.class), mock(WishlistContentSimilarityMatcher.class),
                mock(com.eneik.production.services.GeminiContextService.class),
                mock(com.eneik.production.repositories.TaskRepository.class),
                mock(com.eneik.production.repositories.JulesSessionRepository.class),
                workerSessionService,
                mock(com.eneik.production.repositories.PrReviewRepository.class),
                mock(com.eneik.production.repositories.CodeIntegrityFindingRepository.class),
                mock(com.eneik.production.repositories.EvidenceNodeRepository.class), mock(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.class), mock(com.eneik.production.services.runtime.RuntimeLauncherClient.class));

        service.advanceInProgressPhilosophicalDiscussions();

        verify(flow, never()).dispatchToPhilosophicalAuditPersistentWorker(any(), any(), any(), any());
    }

    @Test
    void advanceInProgressPhilosophicalDiscussionsSkippedWhenFeatureFlagDisabled() {
        RoleRepository roles = mock(RoleRepository.class);
        ProjectFlowService flow = mock(ProjectFlowService.class);
        SystemSettingsService settings = mock(SystemSettingsService.class);
        when(settings.effectiveBoolean("philosophical_falsification_enabled")).thenReturn(false);
        com.eneik.production.repositories.ProjectRepository projectRepository =
                mock(com.eneik.production.repositories.ProjectRepository.class);

        FalsificationCycleService service = new FalsificationCycleService(
                projectRepository, roles, mock(RoleCapabilityLoader.class),
                mock(WishlistRepository.class), mock(FalsificationRunRepository.class), settings,
                mock(GitHubPullRequestService.class), flow,
                mock(ClientDeliverableReadinessService.class), mock(WishlistContentSimilarityMatcher.class),
                mock(com.eneik.production.services.GeminiContextService.class),
                mock(com.eneik.production.repositories.TaskRepository.class),
                mock(com.eneik.production.repositories.JulesSessionRepository.class),
                mock(com.eneik.production.services.PersistentWorkerSessionService.class),
                mock(com.eneik.production.repositories.PrReviewRepository.class),
                mock(com.eneik.production.repositories.CodeIntegrityFindingRepository.class),
                mock(com.eneik.production.repositories.EvidenceNodeRepository.class), mock(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.class), mock(com.eneik.production.services.runtime.RuntimeLauncherClient.class));

        service.advanceInProgressPhilosophicalDiscussions();

        verify(projectRepository, never()).findAll();
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
        RoleEntity roleWithCharter = role("BARCAN-TAG-11");
        // GeminiContextService is its own unit under test (GeminiContextServiceTest) - here it's a plain
        // interaction mock, stubbed to return what the real service would produce for this role so this
        // test verifies FalsificationCycleService's own prompt-assembly logic, not RAG retrieval/fallback
        // internals.
        com.eneik.production.services.GeminiContextService geminiContextService =
                mock(com.eneik.production.services.GeminiContextService.class);
        when(geminiContextService.buildRoleScopedContext(eq(roleWithCharter), any(), anyInt()))
                .thenReturn("\n\n=== ROLE BARCAN-TAG-11 CHARTER ===\n| 1 | **Patricia Churchland** | neurophilosophy |\n");
        FalsificationCycleService service = newPhilosophicalService(
                roles, flow, wishlistRepository, settings, mock(FalsificationRunRepository.class), geminiContextService);
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("prompt-project");
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
                mock(com.eneik.production.services.PersistentWorkerSessionService.class),
                mock(com.eneik.production.repositories.PrReviewRepository.class),
                mock(com.eneik.production.repositories.CodeIntegrityFindingRepository.class),
                mock(com.eneik.production.repositories.EvidenceNodeRepository.class), mock(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.class), mock(com.eneik.production.services.runtime.RuntimeLauncherClient.class));
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
                mock(com.eneik.production.services.PersistentWorkerSessionService.class),
                mock(com.eneik.production.repositories.PrReviewRepository.class),
                mock(com.eneik.production.repositories.CodeIntegrityFindingRepository.class),
                mock(com.eneik.production.repositories.EvidenceNodeRepository.class), mock(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.class), mock(com.eneik.production.services.runtime.RuntimeLauncherClient.class));
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
