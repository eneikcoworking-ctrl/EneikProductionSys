package com.eneik.production.services;

import com.eneik.production.models.persistence.DesignShopCycleEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.DesignShopCycleRepository;
import com.eneik.production.repositories.FalsificationRunRepository;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.FeatureThreadRepository;
import com.eneik.production.repositories.JulesActivityResponseRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.LinearIssueMetadataRepository;
import com.eneik.production.repositories.ProjectFileClaimRepository;
import com.eneik.production.repositories.ProjectFinalReportRepository;
import com.eneik.production.repositories.ProjectGenerationStateRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.compiler.TechnicalLeadCompiler;
import com.eneik.production.services.dashboard.ClientDeliveryService;
import com.eneik.production.services.dashboard.EmsMetricsService;
import com.eneik.production.services.dashboard.ProjectOperationalContextService;
import com.eneik.production.services.design.DesignAssetService;
import com.eneik.production.services.design.DesignConsistencyAuditService;
import com.eneik.production.services.design.DesignShopOrchestrationService;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.jules.JulesDispatchService;
import com.eneik.production.services.onboarding.OnboardingAuditService;
import com.eneik.production.services.operational.OperationalPolicyService;
import com.eneik.production.services.projectfactory.GitHubProjectFactoryClient;
import com.eneik.production.services.projectfactory.ProjectFactoryService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.toc.LaunchabilityConstraintService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Law 21: Закон непараллельности круга и мнения.
 *
 * Mathematical invariants:
 *   1. |{w : source(w) = self_falsification & w is open}| <= 1 per project
 *   2. |{tau : coverage_audit(w) & !terminal(tau)}| <= 1 per wishlist
 *   3. Three gates before dispatch:
 *      G1: readiness (decompositionComplete & selfFalsificationReadyRatio >= theta)
 *      G2: no open self_falsification wishlist in {pending, compiling}
 *      G3: new merged PR code exists (diff(merged PR > highest_pr_number_audited) != empty)
 *   4. Watermark monotonicity for coverage audits (only new merged PRs trigger audit)
 *   5. Bounded wait on opinions: opinion waiting times out and closes unpromoted rather than halting flow.
 */
class NonConcurrencyLaw21Test {

    private UUID projectId;
    private ProjectEntity project;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        project = new ProjectEntity();
        project.setId(projectId);
        project.setName("Law 21 Project");
        project.setSlug("law-21-project");
    }

    @Test
    void invariantG2_falsificationCycleBlocksParallelOpenWishlistPerProject() {
        WishlistRepository wishlistRepo = mock(WishlistRepository.class);
        ProjectFlowService projectFlowService = mock(ProjectFlowService.class);
        ClientDeliverableReadinessService readinessService = mock(ClientDeliverableReadinessService.class);

        // G1 passes
        when(readinessService.computeForProject(projectId))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(10, 10, 20, 20, 1.0, true, 1.0));

        // G2 fails: an open self_falsification wishlist already exists in pending
        when(wishlistRepo.countByProjectIdAndSourceAndStatus(projectId, WishlistSource.self_falsification, WishlistStatus.pending))
                .thenReturn(1L);

        FalsificationCycleService service = createFalsificationService(wishlistRepo, projectFlowService, readinessService, mock(GitHubPullRequestService.class));

        service.executeCycleForProject(project);

        // Verify: cycle held; no audit task dispatched, no new wishlist created
        verify(projectFlowService, never()).dispatchFalsificationAudit(any(), any(), anyInt(), any());
        verify(wishlistRepo, never()).save(any());
    }

    @Test
    void invariantG1_falsificationCycleBlocksWhenReadinessRatioBelowThreshold() {
        WishlistRepository wishlistRepo = mock(WishlistRepository.class);
        ProjectFlowService projectFlowService = mock(ProjectFlowService.class);
        ClientDeliverableReadinessService readinessService = mock(ClientDeliverableReadinessService.class);

        // G1 fails: ratio is 0.70 < default threshold 0.80
        when(readinessService.computeForProject(projectId))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(10, 7, 20, 14, 0.70, true, 0.70));

        when(wishlistRepo.countByProjectIdAndSourceAndStatus(eq(projectId), eq(WishlistSource.self_falsification), any()))
                .thenReturn(0L);

        FalsificationCycleService service = createFalsificationService(wishlistRepo, projectFlowService, readinessService, mock(GitHubPullRequestService.class));

        service.executeCycleForProject(project);

        // Verify: G1 holds the cycle
        verify(projectFlowService, never()).dispatchFalsificationAudit(any(), any(), anyInt(), any());
    }

    @Test
    void invariantG3_falsificationCycleBlocksWhenNoNewMergedCodeChanges() {
        WishlistRepository wishlistRepo = mock(WishlistRepository.class);
        ProjectFlowService projectFlowService = mock(ProjectFlowService.class);
        ClientDeliverableReadinessService readinessService = mock(ClientDeliverableReadinessService.class);
        GitHubPullRequestService ghService = mock(GitHubPullRequestService.class);

        // G1 and G2 pass
        when(readinessService.computeForProject(projectId))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(10, 10, 20, 20, 1.0, true, 1.0));
        when(wishlistRepo.countByProjectIdAndSourceAndStatus(eq(projectId), eq(WishlistSource.self_falsification), any()))
                .thenReturn(0L);

        // G3 fails: snapshot has no PRs, no diff text
        when(ghService.pullRequestSnapshot(project)).thenReturn(
                new GitHubPullRequestService.PullRequestSnapshot(true, "owner", "repo", List.of(), List.of(), null));

        FalsificationCycleService service = createFalsificationService(wishlistRepo, projectFlowService, readinessService, ghService);

        service.executeCycleForProject(project);

        // Verify: G3 holds the cycle when diff is blank
        verify(projectFlowService, never()).dispatchFalsificationAudit(any(), any(), anyInt(), any());
    }

    @Test
    void falsificationAuditTask_enforcesSingleActiveFlightPerProject() {
        TaskRepository taskRepo = mock(TaskRepository.class);
        ProjectRepository projectRepo = mock(ProjectRepository.class);
        OperationalPolicyService policyService = mock(OperationalPolicyService.class);
        RoleRepository roleRepo = mock(RoleRepository.class);

        ProjectFlowService flowService = createProjectFlowService(taskRepo, mock(WishlistRepository.class),
                projectRepo, policyService, mock(ClientDeliverableReadinessService.class), mock(GitHubPullRequestService.class), roleRepo);

        TaskEntity activeAudit = new TaskEntity();
        activeAudit.setProject(project);
        activeAudit.initializeStatus(TaskStatus.in_progress);
        var payload = new ObjectMapper().createObjectNode();
        payload.put(ProjectFlowService.WISHLIST_COMPILER_PAYLOAD_KEY, ProjectFlowService.FALSIFICATION_AUDIT_TASK_TYPE);
        activeAudit.setPayload(payload);

        when(taskRepo.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(activeAudit));

        // Attempting to admit a second audit while one is in_progress returns null
        TaskEntity result = flowService.admitFalsificationAuditTask(project, "prompt", 42, "report.json");
        assertThat(result).isNull();
        verify(taskRepo, never()).save(any());
    }

    @Test
    void coverageAudit_enforcesAtMostOneNonTerminalAuditPerWishlist() {
        TaskRepository taskRepo = mock(TaskRepository.class);
        WishlistRepository wishlistRepo = mock(WishlistRepository.class);
        ProjectRepository projectRepo = mock(ProjectRepository.class);
        OperationalPolicyService policyService = mock(OperationalPolicyService.class);
        ClientDeliverableReadinessService readinessService = mock(ClientDeliverableReadinessService.class);
        GitHubPullRequestService ghService = mock(GitHubPullRequestService.class);

        ProjectFlowService flowService = createProjectFlowService(taskRepo, wishlistRepo,
                projectRepo, policyService, readinessService, ghService, mock(RoleRepository.class));

        UUID wishlistId = UUID.randomUUID();
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(wishlistId);
        wishlist.setProjectId(projectId);
        wishlist.setSource(WishlistSource.client);
        wishlist.setStatus(WishlistStatus.converted_to_task);

        when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));
        when(wishlistRepo.findByProjectId(projectId)).thenReturn(List.of(wishlist));

        // An active (in_progress) coverage audit task already exists for this wishlist
        TaskEntity activeAudit = new TaskEntity();
        activeAudit.setProject(project);
        activeAudit.initializeStatus(TaskStatus.in_progress);
        var payload = new ObjectMapper().createObjectNode();
        payload.put(ProjectFlowService.WISHLIST_COMPILER_PAYLOAD_KEY, ProjectFlowService.COVERAGE_AUDIT_TASK_TYPE);
        payload.put(ProjectFlowService.COVERAGE_AUDIT_WISHLIST_ID_KEY, wishlistId.toString());
        payload.put(ProjectFlowService.COVERAGE_AUDIT_HIGHEST_PR_KEY, 10);
        activeAudit.setPayload(payload);

        when(taskRepo.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(activeAudit));

        GitHubPullRequestService.PullRequestSnapshot snapshot =
                new GitHubPullRequestService.PullRequestSnapshot(true, "owner", "repo", List.of(), List.of(), null);

        var due = flowService.admitDueCoverageAudits(projectId, snapshot);

        // Must be empty: |{tau : coverage_audit(w) & !terminal(tau)}| <= 1 holds
        assertThat(due).isEmpty();
    }

    @Test
    void coverageAudit_enforcesWatermarkMonotonicity() {
        TaskRepository taskRepo = mock(TaskRepository.class);
        WishlistRepository wishlistRepo = mock(WishlistRepository.class);
        ProjectRepository projectRepo = mock(ProjectRepository.class);
        OperationalPolicyService policyService = mock(OperationalPolicyService.class);
        ClientDeliverableReadinessService readinessService = mock(ClientDeliverableReadinessService.class);
        GitHubPullRequestService ghService = mock(GitHubPullRequestService.class);

        ProjectFlowService flowService = createProjectFlowService(taskRepo, wishlistRepo,
                projectRepo, policyService, readinessService, ghService, mock(RoleRepository.class));

        UUID wishlistId = UUID.randomUUID();
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(wishlistId);
        wishlist.setProjectId(projectId);
        wishlist.setSource(WishlistSource.client);
        wishlist.setStatus(WishlistStatus.converted_to_task);

        when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));
        when(wishlistRepo.findByProjectId(projectId)).thenReturn(List.of(wishlist));

        // Previous audit is terminal (done), and its audited PR watermark was 25
        TaskEntity pastAudit = new TaskEntity();
        pastAudit.setProject(project);
        pastAudit.initializeStatus(TaskStatus.done);
        pastAudit.setCreatedAt(Instant.now().minus(Duration.ofHours(5)));
        var payload = new ObjectMapper().createObjectNode();
        payload.put(ProjectFlowService.WISHLIST_COMPILER_PAYLOAD_KEY, ProjectFlowService.COVERAGE_AUDIT_TASK_TYPE);
        payload.put(ProjectFlowService.COVERAGE_AUDIT_WISHLIST_ID_KEY, wishlistId.toString());
        payload.put(ProjectFlowService.COVERAGE_AUDIT_HIGHEST_PR_KEY, 25);
        pastAudit.setPayload(payload);

        when(taskRepo.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(pastAudit));

        // Current highest merged PR in snapshot is 25 (no new code merged since last audit)
        GitHubPullRequestService.GitHubPullRequest mergedPr =
                new GitHubPullRequestService.GitHubPullRequest("https://github.com/owner/repo/pull/25", 25, "Feature 25", "branch-25", "author", true, "main", true, Instant.now());
        GitHubPullRequestService.PullRequestSnapshot snapshot =
                new GitHubPullRequestService.PullRequestSnapshot(true, "owner", "repo", List.of(), List.of(mergedPr), null);

        var due = flowService.admitDueCoverageAudits(projectId, snapshot);

        // Watermark monotonicity: currentHighestMergedPr (25) <= lastAuditedPr (25) => no audit
        assertThat(due).isEmpty();
    }

    @Test
    void designShopCycle_opinionWaitIsTimeBounded() {
        ProjectRepository projectRepo = mock(ProjectRepository.class);
        DesignShopCycleRepository cycleRepo = mock(DesignShopCycleRepository.class);
        GitHubPullRequestService ghService = mock(GitHubPullRequestService.class);
        SystemSettingsService settingsService = mock(SystemSettingsService.class);

        DesignShopOrchestrationService designShop = new DesignShopOrchestrationService(
                projectRepo, cycleRepo, mock(ClientDeliverableReadinessService.class),
                mock(DesignAssetService.class), mock(ProjectFlowService.class),
                mock(ProjectOperationalContextService.class),
                ghService, settingsService, new DesignConsistencyAuditService(),
                mock(WishlistRepository.class), null);
        ReflectionTestUtils.setField(designShop, "self", designShop);

        when(settingsService.effectiveBoolean("design_shop_enabled")).thenReturn(true);
        when(projectRepo.findByStatusOrderByCreatedAtDesc(any())).thenReturn(List.of(project));

        DesignShopCycleEntity cycle = new DesignShopCycleEntity();
        cycle.setProjectId(projectId);
        cycle.setStage(DesignShopCycleEntity.STAGE_AWAITING_REVIEW);
        cycle.setDraftPath("design/draft/screen-1");
        // Updated 50 hours ago (> AWAITING_REVIEW_TIMEOUT of 48h)
        cycle.setUpdatedAt(Instant.now().minus(Duration.ofHours(50)));
        when(cycleRepo.findByProjectId(projectId)).thenReturn(Optional.of(cycle));

        // The review was not promoted to approved
        when(ghService.fetchFileBytes(any(), any(), any())).thenReturn(Optional.empty());

        designShop.tick();

        // Cycle must be closed with STAGE_DONE to prevent infinite blocking of the flow
        verify(cycleRepo).save(cycle);
        assertThat(cycle.getStage()).isEqualTo(DesignShopCycleEntity.STAGE_DONE);
    }

    private ProjectFlowService createProjectFlowService(TaskRepository taskRepo,
                                                        WishlistRepository wishlistRepo,
                                                        ProjectRepository projectRepo,
                                                        OperationalPolicyService policyService,
                                                        ClientDeliverableReadinessService readinessService,
                                                        GitHubPullRequestService ghService,
                                                        RoleRepository roleRepo) {
        ProjectFlowService service = new ProjectFlowService(
                projectRepo,
                wishlistRepo,
                mock(AccountRepository.class),
                taskRepo,
                mock(ClaimRepository.class),
                roleRepo,
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
                "eneik-org",
                mock(OnboardingAuditService.class),
                mock(EmsMetricsService.class),
                mock(ProjectOperationalContextService.class),
                mock(DesignAssetService.class),
                ghService,
                readinessService,
                mock(FeatureService.class),
                mock(PersistentWorkerSessionService.class),
                mock(SelfFalsificationEpicMatcher.class),
                policyService,
                mock(ProjectFileClaimRepository.class),
                mock(RequirementGroundingService.class),
                mock(GeminiContextService.class),
                mock(TaskConflictRepository.class),
                mock(LinearIssueMetadataRepository.class),
                mock(FeatureRepository.class),
                mock(FeatureThreadRepository.class),
                mock(PlannedWorkRecoveryService.class),
                null
        );
        ReflectionTestUtils.setField(service, "coverageAuditMinIntervalHours", 0);
        return service;
    }

    private FalsificationCycleService createFalsificationService(WishlistRepository wishlistRepo,
                                                                ProjectFlowService projectFlowService,
                                                                ClientDeliverableReadinessService readinessService,
                                                                GitHubPullRequestService ghService) {
        RoleRepository roleRepo = mock(RoleRepository.class);
        RoleEntity role = new RoleEntity();
        role.setTag("backend");
        role.setActive(true);
        when(roleRepo.findAll()).thenReturn(List.of(role));

        SystemSettingsService settings = mock(SystemSettingsService.class);
        when(settings.effectiveDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));

        FalsificationCycleService service = new FalsificationCycleService(
                mock(ProjectRepository.class),
                roleRepo,
                mock(RoleCapabilityLoader.class),
                wishlistRepo,
                mock(FalsificationRunRepository.class),
                settings,
                ghService,
                projectFlowService,
                readinessService,
                mock(WishlistContentSimilarityMatcher.class),
                mock(com.eneik.production.services.GeminiContextService.class),
                mock(TaskRepository.class),
                mock(com.eneik.production.repositories.JulesSessionRepository.class),
                mock(PersistentWorkerSessionService.class),
                mock(com.eneik.production.repositories.PrReviewRepository.class),
                mock(com.eneik.production.repositories.CodeIntegrityFindingRepository.class),
                mock(com.eneik.production.repositories.EvidenceNodeRepository.class),
                mock(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.class),
                mock(com.eneik.production.services.runtime.RuntimeLauncherClient.class),
                new LaunchabilityConstraintService(wishlistRepo)
        );
        ReflectionTestUtils.setField(service, "readinessThreshold", 0.80);
        return service;
    }
}
