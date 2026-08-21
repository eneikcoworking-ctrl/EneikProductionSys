package com.eneik.production.services;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.JulesActivityResponseRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.ProjectFinalReportRepository;
import com.eneik.production.repositories.ProjectGenerationStateRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.compiler.TechnicalLeadCompiler;
import com.eneik.production.services.dashboard.ClientDeliveryService;
import com.eneik.production.services.dashboard.EmsMetricsService;
import com.eneik.production.services.dashboard.ProjectOperationalContextService;
import com.eneik.production.services.design.DesignAssetService;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.jules.JulesDispatchService;
import com.eneik.production.services.onboarding.OnboardingAuditService;
import com.eneik.production.services.operational.OperationalPolicyService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.projectfactory.GitHubProjectFactoryClient;
import com.eneik.production.services.projectfactory.ProjectFactoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectFlowServiceTest {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final GitHubPullRequestService gitHubPullRequestService = mock(GitHubPullRequestService.class);
    private final com.eneik.production.repositories.ProjectFileClaimRepository projectFileClaimRepository =
            mock(com.eneik.production.repositories.ProjectFileClaimRepository.class);

    @Test
    void deliverableMergeRatioUsesMergedPlannedTasksNotFeatureReadiness() {
        var readiness = new ClientDeliverableReadinessService.Readiness(
                5,
                3,
                19,
                17,
                0.6,
                true);

        assertEquals(17.0 / 19.0, ProjectFlowService.deliverableMergeRatio(readiness), 0.0001);
    }

    @Test
    void terminalSpikeCompletedIsNotAnActionableBlockedStatus() {
        assertFalse(ProjectFlowService.isActionableBlockedStatus(TaskStatus.failed));
        assertFalse(ProjectFlowService.isActionableBlockedStatus(TaskStatus.spike_completed));
        assertTrue(ProjectFlowService.isActionableBlockedStatus(TaskStatus.claimed));
        assertTrue(ProjectFlowService.isActionableBlockedStatus(TaskStatus.done));
    }

    // Smart-decomposition fix (2026-07-31), Part A: mirrors the already-proven
    // commitDeterministicJavaScaffoldIfAbsent backend fix (test-thirty-fifth) for the frontend side
    // (test-fortieth: three эпики each independently rewrote frontend/src/App.svelte from scratch).

    @Test
    void frontendScaffoldSkippedForBrownfieldProject() {
        ProjectFlowService service = service();
        ProjectEntity project = greenfieldProject();
        project.setOnboardingMode("brownfield");

        service.commitDeterministicFrontendScaffoldIfAbsent(project);

        verify(gitHubPullRequestService, never()).upsertFile(eq(project), anyString(), any(), anyString());
    }

    @Test
    void frontendScaffoldSkippedWhenAppSvelteAlreadyExists() {
        ProjectFlowService service = service();
        ProjectEntity project = greenfieldProject();
        stubNoManifestsExist(project);
        when(gitHubPullRequestService.fetchFileContent(project, "main", "frontend/src/App.svelte"))
                .thenReturn(Optional.of("<script>already here</script>"));

        service.commitDeterministicFrontendScaffoldIfAbsent(project);

        verify(gitHubPullRequestService, never()).upsertFile(eq(project), anyString(), any(), anyString());
    }

    @Test
    void frontendScaffoldSkippedWhenFrontendPackageJsonAlreadyExists() {
        ProjectFlowService service = service();
        ProjectEntity project = greenfieldProject();
        stubNoManifestsExist(project);
        when(gitHubPullRequestService.fetchFileContent(project, "main", "frontend/package.json"))
                .thenReturn(Optional.of("{}"));

        service.commitDeterministicFrontendScaffoldIfAbsent(project);

        verify(gitHubPullRequestService, never()).upsertFile(eq(project), anyString(), any(), anyString());
    }

    @Test
    void frontendScaffoldSkippedWhenBackendManifestAlreadyExists() {
        ProjectFlowService service = service();
        ProjectEntity project = greenfieldProject();
        stubNoManifestsExist(project);
        when(gitHubPullRequestService.fetchFileContent(project, "main", "pom.xml"))
                .thenReturn(Optional.of("<project></project>"));

        service.commitDeterministicFrontendScaffoldIfAbsent(project);

        verify(gitHubPullRequestService, never()).upsertFile(eq(project), anyString(), any(), anyString());
    }

    @Test
    void frontendScaffoldCommitsShellAndRoutesRegistryWhenAbsent() {
        ProjectFlowService service = service();
        ProjectEntity project = greenfieldProject();
        stubNoManifestsExist(project);
        when(gitHubPullRequestService.upsertFile(eq(project), anyString(), any(), anyString())).thenReturn(true);

        service.commitDeterministicFrontendScaffoldIfAbsent(project);

        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(gitHubPullRequestService, times(6)).upsertFile(eq(project), pathCaptor.capture(), any(), anyString());
        List<String> committedPaths = pathCaptor.getAllValues();

        assertTrue(committedPaths.contains("frontend/package.json"));
        assertTrue(committedPaths.contains("frontend/vite.config.js"));
        assertTrue(committedPaths.contains("frontend/index.html"));
        assertTrue(committedPaths.contains("frontend/src/main.js"));
        assertTrue(committedPaths.contains("frontend/src/routes.js"));
        assertTrue(committedPaths.contains("frontend/src/App.svelte"));
    }

    // Smart-decomposition v2 (2026-07-31): the bootstrap scaffold now feeds the general cross-эпик
    // collision-guard ledger directly, instead of relying on an LLM prompt rule - every path it commits
    // becomes a project-wide claim (taskId=null, featureId=null) that TechnicalLeadCompiler's
    // applyCrossEpicCollisionGuard will strip out of any эпик's predicted fileScope, regardless of эпик.
    @Test
    void frontendScaffoldRecordsGlobalClaimsForEveryCommittedPath() {
        ProjectFlowService service = service();
        ProjectEntity project = greenfieldProject();
        stubNoManifestsExist(project);
        when(gitHubPullRequestService.upsertFile(eq(project), anyString(), any(), anyString())).thenReturn(true);

        service.commitDeterministicFrontendScaffoldIfAbsent(project);

        ArgumentCaptor<com.eneik.production.models.persistence.ProjectFileClaimEntity> claimCaptor =
                ArgumentCaptor.forClass(com.eneik.production.models.persistence.ProjectFileClaimEntity.class);
        verify(projectFileClaimRepository, times(6)).save(claimCaptor.capture());
        List<com.eneik.production.models.persistence.ProjectFileClaimEntity> claims = claimCaptor.getAllValues();

        assertTrue(claims.stream().anyMatch(c -> "frontend/src/App.svelte".equals(c.getFilePath())));
        assertTrue(claims.stream().anyMatch(c -> "frontend/src/routes.js".equals(c.getFilePath())));
        assertTrue(claims.stream().allMatch(c -> c.getTaskId() == null && c.getFeatureId() == null));
    }

    private void stubNoManifestsExist(ProjectEntity project) {
        when(gitHubPullRequestService.fetchFileContent(eq(project), eq("main"), anyString()))
                .thenReturn(Optional.empty());
    }

    private ProjectEntity greenfieldProject() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("Test Project");
        project.setSlug("test-project");
        project.setOnboardingMode("greenfield");
        return project;
    }

    private ProjectFlowService service() {
        ProjectFlowService service = new ProjectFlowService(
                projectRepository,
                mock(WishlistRepository.class),
                mock(AccountRepository.class),
                mock(TaskRepository.class),
                mock(ClaimRepository.class),
                mock(RoleRepository.class),
                mock(ClaimService.class),
                mock(JulesDispatchService.class),
                mock(ProjectFactoryService.class),
                mock(GitHubProjectFactoryClient.class),
                mock(SystemSettingsService.class),
                null,
                null,
                mock(TechnicalLeadCompiler.class),
                mock(ClientDeliveryService.class),
                mock(ProjectFinalReportRepository.class),
                mock(JulesSessionRepository.class),
                mock(JulesActivityResponseRepository.class),
                mock(ProjectGenerationStateRepository.class),
                new ObjectMapper(),
                "eneik-test-org",
                mock(OnboardingAuditService.class),
                mock(EmsMetricsService.class),
                mock(ProjectOperationalContextService.class),
                mock(DesignAssetService.class),
                gitHubPullRequestService,
                mock(ClientDeliverableReadinessService.class),
                mock(FeatureService.class),
                mock(PersistentWorkerSessionService.class),
                mock(SelfFalsificationEpicMatcher.class),
                mock(OperationalPolicyService.class),
                projectFileClaimRepository,
                mock(RequirementGroundingService.class),
                mock(GeminiContextService.class),
                mock(com.eneik.production.repositories.TaskConflictRepository.class),
                mock(com.eneik.production.repositories.NeedsHumanReviewRepository.class),
                mock(com.eneik.production.repositories.LinearIssueMetadataRepository.class),
                mock(com.eneik.production.repositories.FeatureRepository.class),
                mock(com.eneik.production.repositories.FeatureThreadRepository.class),
                null);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "self", service);
        return service;
    }
}
