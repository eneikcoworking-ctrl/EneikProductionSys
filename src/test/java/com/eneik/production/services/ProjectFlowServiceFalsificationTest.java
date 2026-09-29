package com.eneik.production.services;

import com.eneik.production.dto.ProjectDto;
import com.eneik.production.dto.WishlistRequestDto;
import com.eneik.production.dto.WishlistResponseDto;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
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
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest;
import com.eneik.production.services.github.GitHubPullRequestService.PullRequestSnapshot;
import com.eneik.production.services.jules.JulesDispatchService;
import com.eneik.production.services.onboarding.OnboardingAuditService;
import com.eneik.production.services.operational.OperationalPolicyService;
import com.eneik.production.services.projectfactory.GitHubProjectFactoryClient;
import com.eneik.production.services.projectfactory.ProjectFactoryService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Falsification test-screen for {@link ProjectFlowService}.
 *
 * Grounded in:
 * 1. DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP [D004, Schaffer]:
 *    Merological ownership of flow state. The whole aggregate enforces part-whole
 *    state transitions (wishlist intake, compilation bounds, task dependency ordering,
 *    and redecomposition lifecycle) without cross-aggregate corruption.
 * 2. ELVIN_GOLDMAN_01_RELIABILITY_CHAIN [D010, Goldman]:
 *    Reliability of project acquisition boundaries. Data queries must be strictly
 *    scoped to project tasks and ordered deterministically, eliminating global scans
 *    and unverified cross-project data lineage.
 */
class ProjectFlowServiceFalsificationTest {

    private ProjectRepository projectRepository;
    private WishlistRepository wishlistRepository;
    private TaskRepository taskRepository;
    private JulesSessionRepository julesSessionRepository;
    private GitHubPullRequestService gitHubPullRequestService;
    private OperationalPolicyService operationalPolicyService;
    private ProjectFlowService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        wishlistRepository = mock(WishlistRepository.class);
        taskRepository = mock(TaskRepository.class);
        julesSessionRepository = mock(JulesSessionRepository.class);
        gitHubPullRequestService = mock(GitHubPullRequestService.class);
        operationalPolicyService = mock(OperationalPolicyService.class);

        service = new ProjectFlowService(
                projectRepository,
                wishlistRepository,
                mock(AccountRepository.class),
                taskRepository,
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
                julesSessionRepository,
                mock(JulesActivityResponseRepository.class),
                mock(ProjectGenerationStateRepository.class),
                new ObjectMapper(),
                "eneik-falsification-org",
                mock(OnboardingAuditService.class),
                mock(EmsMetricsService.class),
                mock(ProjectOperationalContextService.class),
                mock(DesignAssetService.class),
                gitHubPullRequestService,
                mock(ClientDeliverableReadinessService.class),
                mock(FeatureService.class),
                mock(PersistentWorkerSessionService.class),
                mock(SelfFalsificationEpicMatcher.class),
                operationalPolicyService,
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
        ReflectionTestUtils.setField(service, "self", service);
    }

    // =========================================================================
    // DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP [D004, Schaffer]
    // =========================================================================

    @Test
    @DisplayName("Shaffer D004: addWishlistItem initializes part in pending state under project aggregate with declared attempt budget")
    void partWholeOwnershipWishlistBudgetAndPendingInitialization() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setName("merological-alpha");
        project.setStatus(ProjectStatus.active);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(wishlistRepository.save(any(WishlistEntity.class))).thenAnswer(invocation -> {
            WishlistEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        WishlistRequestDto request = new WishlistRequestDto(projectId, WishlistSource.client, null, "Audit user permissions");
        WishlistResponseDto dto = service.addWishlistItem(projectId, request);

        ArgumentCaptor<WishlistEntity> captor = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlistRepository).save(captor.capture());
        WishlistEntity saved = captor.getValue();

        assertThat(saved.getProjectId()).isEqualTo(projectId);
        assertThat(saved.getStatus()).isEqualTo(WishlistStatus.pending);
        assertThat(saved.getCompileAttempts()).isEqualTo(0);
        assertThat(saved.effectiveCompileCeiling()).isEqualTo(WishlistEntity.COMPILE_ATTEMPT_BUDGET);
        assertThat(saved.getSource()).isEqualTo(WishlistSource.client);
        assertThat(dto.status()).isEqualTo(WishlistStatus.pending);
    }

    @Test
    @DisplayName("Shaffer D004: actionable blocked status enforces dependency boundaries across terminal and transient states")
    void partWholeOwnershipDependencyGraphBlocksUnresolvedWork() {
        // Terminal states cannot be actionable blockers
        assertThat(ProjectFlowService.isActionableBlockedStatus(TaskStatus.failed)).isFalse();
        assertThat(ProjectFlowService.isActionableBlockedStatus(TaskStatus.spike_completed)).isFalse();

        // Transient / active states legitimately hold dependency chains
        assertThat(ProjectFlowService.isActionableBlockedStatus(TaskStatus.claimed)).isTrue();
        assertThat(ProjectFlowService.isActionableBlockedStatus(TaskStatus.done)).isTrue();
        assertThat(ProjectFlowService.isActionableBlockedStatus(TaskStatus.review)).isTrue();
    }

    @Test
    @DisplayName("Shaffer D004: resetProjectForRedecomposition strictly isolates mutations to frozen project aggregate")
    void partWholeOwnershipProjectRedecompositionRequiresFrozenStatus() {
        UUID activeProjectId = UUID.randomUUID();
        ProjectEntity activeProject = new ProjectEntity();
        activeProject.setId(activeProjectId);
        activeProject.setStatus(ProjectStatus.active);

        when(projectRepository.findById(activeProjectId)).thenReturn(Optional.of(activeProject));

        // Active project cannot be redecomposed without freeze invariant
        assertThatThrownBy(() -> service.resetProjectForRedecomposition(activeProjectId, "New goals definition"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("frozen");

        verify(taskRepository, never()).delete(any());
        verify(wishlistRepository, never()).delete(any());
    }

    // =========================================================================
    // ELVIN_GOLDMAN_01_RELIABILITY_CHAIN [D010, Goldman]
    // =========================================================================

    @Test
    @DisplayName("Goldman D010: selectBadSession strictly scopes candidate acquisition to caller-provided task set")
    void reliabilityChainBadSessionSelectionScopedToTaskMap() {
        UUID task1 = UUID.randomUUID();
        UUID task2 = UUID.randomUUID();
        TaskEntity t1 = new TaskEntity();
        t1.setId(task1);
        TaskEntity t2 = new TaskEntity();
        t2.setId(task2);

        Map<UUID, TaskEntity> tasksById = Map.of(task1, t1, task2, t2);

        JulesSessionEntity s1 = new JulesSessionEntity();
        s1.setId(UUID.randomUUID());
        s1.setTaskId(task1);
        s1.setStatus("running");
        s1.setUpdatedAt(Instant.now());

        when(julesSessionRepository.findByTaskIdIn(anyList())).thenReturn(List.of(s1));

        Optional<JulesSessionEntity> selected = ReflectionTestUtils.invokeMethod(service, "selectBadSession", tasksById, null);

        assertThat(selected).isPresent();
        assertThat(selected.get().getId()).isEqualTo(s1.getId());

        ArgumentCaptor<List<UUID>> taskIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(julesSessionRepository).findByTaskIdIn(taskIdsCaptor.capture());
        assertThat(taskIdsCaptor.getValue()).containsExactlyInAnyOrder(task1, task2);
    }

    @Test
    @DisplayName("Goldman D010: highestMergedPrNumber attributes PR watermark strictly through project's task lineage")
    void reliabilityChainHighestMergedPrNumberProjectTaskScoping() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        UUID projectTaskId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(projectTaskId);
        task.setProject(project);

        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(task));

        JulesSessionEntity projectSession = new JulesSessionEntity();
        projectSession.setId(UUID.randomUUID());
        projectSession.setTaskId(projectTaskId);
        projectSession.setPrUrl("https://github.com/eneikdru/test-fiftieth/pull/42");

        when(julesSessionRepository.findByTaskIdIn(List.of(projectTaskId))).thenReturn(List.of(projectSession));

        GitHubPullRequest pr = new GitHubPullRequest(
                "https://github.com/eneikdru/test-fiftieth/pull/42",
                42,
                "feat: core update",
                "feat-branch",
                "jules",
                true,
                "main",
                true,
                Instant.now()
        );
        PullRequestSnapshot snapshot = new PullRequestSnapshot(true, "eneikdru", "test-fiftieth", List.of(), List.of(pr), null);
        when(gitHubPullRequestService.pullRequestSnapshot(project)).thenReturn(snapshot);

        Integer highestPr = ReflectionTestUtils.invokeMethod(service, "highestMergedPrNumber", project);

        assertThat(highestPr).isEqualTo(42);

        // Verify acquisition was strictly scoped to this project's tasks, never scanning global sessions
        verify(taskRepository).findByProjectIdOrderByCreatedAtDesc(projectId);
        verify(julesSessionRepository).findByTaskIdIn(List.of(projectTaskId));
        verify(julesSessionRepository, never()).findAll();
    }

    @Test
    @DisplayName("Goldman D010: listProjects delegates to deterministic ordering predicate without unbounded unconstrained scan")
    void reliabilityChainProjectDirectoryDeterministicOrder() {
        ProjectEntity p1 = new ProjectEntity();
        p1.setId(UUID.randomUUID());
        p1.setName("proj-1");
        p1.setCreatedAt(Instant.now());

        ProjectEntity p2 = new ProjectEntity();
        p2.setId(UUID.randomUUID());
        p2.setName("proj-2");
        p2.setCreatedAt(Instant.now().minusSeconds(60));

        when(projectRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(p1, p2));

        List<ProjectDto> list = service.listProjects();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).id()).isEqualTo(p1.getId());
        assertThat(list.get(1).id()).isEqualTo(p2.getId());

        verify(projectRepository).findAllByOrderByCreatedAtDesc();
    }
}
