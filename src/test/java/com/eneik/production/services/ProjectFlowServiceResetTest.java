package com.eneik.production.services;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.FeatureThreadRepository;
import com.eneik.production.repositories.JulesActivityResponseRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.LinearIssueMetadataRepository;
import com.eneik.production.repositories.NeedsHumanReviewRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * resetProjectForRedecomposition (2026-08-07, operator directive): a frozen project's first decomposition
 * must be fully deleted - in an order that respects the schema's real (non-cascading) FK constraints - and
 * replaced with a fresh client wishlist, on the SAME project row (repo/collaborators already provisioned).
 */
class ProjectFlowServiceResetTest {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final WishlistRepository wishlistRepository = mock(WishlistRepository.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final ClaimRepository claimRepository = mock(ClaimRepository.class);
    private final JulesSessionRepository julesSessionRepository = mock(JulesSessionRepository.class);
    private final JulesActivityResponseRepository julesActivityResponseRepository = mock(JulesActivityResponseRepository.class);
    private final TaskConflictRepository taskConflictRepository = mock(TaskConflictRepository.class);
    private final NeedsHumanReviewRepository needsHumanReviewRepository = mock(NeedsHumanReviewRepository.class);
    private final LinearIssueMetadataRepository linearIssueMetadataRepository = mock(LinearIssueMetadataRepository.class);
    private final FeatureRepository featureRepository = mock(FeatureRepository.class);
    private final FeatureThreadRepository featureThreadRepository = mock(FeatureThreadRepository.class);
    private final GeminiContextService geminiContextService = mock(GeminiContextService.class);

    private ProjectFlowService service() {
        return new ProjectFlowService(
                projectRepository,
                wishlistRepository,
                mock(AccountRepository.class),
                taskRepository,
                claimRepository,
                mock(RoleRepository.class),
                mock(ClaimService.class),
                mock(JulesDispatchService.class),
                mock(ProjectFactoryService.class),
                mock(GitHubProjectFactoryClient.class),
                mock(SystemSettingsService.class),
                null,
                mock(TechnicalLeadCompiler.class),
                mock(ClientDeliveryService.class),
                mock(ProjectFinalReportRepository.class),
                julesSessionRepository,
                julesActivityResponseRepository,
                mock(ProjectGenerationStateRepository.class),
                new ObjectMapper(),
                "eneik-test-org",
                mock(OnboardingAuditService.class),
                mock(EmsMetricsService.class),
                mock(ProjectOperationalContextService.class),
                mock(DesignAssetService.class),
                mock(GitHubPullRequestService.class),
                mock(ClientDeliverableReadinessService.class),
                mock(FeatureService.class),
                mock(PersistentWorkerSessionService.class),
                mock(SelfFalsificationEpicMatcher.class),
                mock(OperationalPolicyService.class),
                mock(com.eneik.production.repositories.ProjectFileClaimRepository.class),
                mock(RequirementGroundingService.class),
                geminiContextService,
                taskConflictRepository,
                needsHumanReviewRepository,
                linearIssueMetadataRepository,
                featureRepository,
                featureThreadRepository,
                null);
    }

    private ProjectEntity frozenProject(UUID id) {
        ProjectEntity project = new ProjectEntity();
        project.setId(id);
        project.setName("test-forty-third");
        project.setSlug("test-forty-third");
        project.setStatus(ProjectStatus.frozen);
        return project;
    }

    @Test
    void refusesToResetAProjectThatIsNotFrozen() {
        ProjectFlowService service = service();
        UUID projectId = UUID.randomUUID();
        ProjectEntity active = frozenProject(projectId);
        active.setStatus(ProjectStatus.active);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(active));

        assertThrows(IllegalStateException.class,
                () -> service.resetProjectForRedecomposition(projectId, "fresh brief"));

        verify(taskRepository, never()).deleteAll(anyList());
    }

    @Test
    void refusesAnEmptyFreshBrief() {
        ProjectFlowService service = service();
        UUID projectId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> service.resetProjectForRedecomposition(projectId, "   "));

        verify(projectRepository, never()).findById(any());
    }

    @Test
    void deletesDependentRowsInFkSafeOrderThenReactivatesWithAFreshWishlist() {
        ProjectFlowService service = service();
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = frozenProject(projectId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID rootWishlistId = UUID.randomUUID();
        WishlistEntity rootWishlist = new WishlistEntity();
        rootWishlist.setId(rootWishlistId);
        rootWishlist.setProjectId(projectId);
        rootWishlist.setSource(WishlistSource.client);
        // originWishlistId left null - this IS the root brief.

        UUID sliceTaskId1 = UUID.randomUUID();
        UUID sliceTaskId2 = UUID.randomUUID();
        TaskEntity task1 = new TaskEntity();
        task1.setId(sliceTaskId1);
        TaskEntity task2 = new TaskEntity();
        task2.setId(sliceTaskId2);
        // task2 depends on task1 - the self-referencing FK that must be nulled out before delete.
        task2.setDependsOn(task1);

        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(task1, task2));
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(List.of(rootWishlist));

        UUID sessionId = UUID.randomUUID();
        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(sliceTaskId1);
        when(julesSessionRepository.findByTaskIdIn(List.of(sliceTaskId1, sliceTaskId2))).thenReturn(List.of(session));
        when(claimRepository.findByTaskIdIn(List.of(sliceTaskId1, sliceTaskId2))).thenReturn(List.of());
        when(taskConflictRepository.findByTaskIdIn(List.of(sliceTaskId1, sliceTaskId2))).thenReturn(List.of());
        when(needsHumanReviewRepository.findByTaskIdIn(List.of(sliceTaskId1, sliceTaskId2))).thenReturn(List.of());
        when(julesActivityResponseRepository.findByJulesSessionIdIn(List.of(sessionId))).thenReturn(List.of());
        when(featureRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(featureThreadRepository.findByProjectId(projectId)).thenReturn(List.of());

        service.resetProjectForRedecomposition(projectId, "  the exact same original brief  ");

        // depends_on nulled and saved before the task carrying it is deleted.
        assertThat(task2.getDependsOn()).isNull();
        verify(taskRepository).saveAll(List.of(task1, task2));

        verify(linearIssueMetadataRepository).deleteAllById(List.of(sliceTaskId1, sliceTaskId2));
        verify(julesSessionRepository).deleteAll(List.of(session));
        verify(taskRepository).deleteAll(List.of(task1, task2));
        verify(wishlistRepository).deleteAll(List.of(rootWishlist));
        // The root brief's RAG index is cleared (indexDocument's own delete-by-sourceRef path), not just
        // the DB row - otherwise the old index would silently linger under a wishlist id nothing points to.
        verify(geminiContextService).indexDocument("client_brief_requirement", "client_brief:" + rootWishlistId, null);

        ArgumentCaptor<WishlistEntity> savedWishlist = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlistRepository).save(savedWishlist.capture());
        assertThat(savedWishlist.getValue().getContent()).isEqualTo("the exact same original brief");
        assertThat(savedWishlist.getValue().getSource()).isEqualTo(WishlistSource.client);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.active);
    }
}
