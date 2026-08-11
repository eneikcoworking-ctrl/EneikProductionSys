package com.eneik.production.services.design;

import com.eneik.production.models.persistence.DesignShopCycleEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.repositories.DesignShopCycleRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.services.ClientDeliverableReadinessService;
import com.eneik.production.services.ProjectFlowService;
import com.eneik.production.services.dashboard.ProjectOperationalContextService;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.settings.SystemSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DesignShopOrchestrationServiceTest {

    private ProjectRepository projectRepository;
    private DesignShopCycleRepository designShopCycleRepository;
    private ClientDeliverableReadinessService readinessService;
    private DesignAssetService designAssetService;
    private ProjectFlowService projectFlowService;
    private ProjectOperationalContextService contextService;
    private GitHubPullRequestService gitHubPullRequestService;
    private SystemSettingsService settingsService;
    private DesignShopOrchestrationService service;
    private ProjectEntity project;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        designShopCycleRepository = mock(DesignShopCycleRepository.class);
        readinessService = mock(ClientDeliverableReadinessService.class);
        designAssetService = mock(DesignAssetService.class);
        projectFlowService = mock(ProjectFlowService.class);
        contextService = mock(ProjectOperationalContextService.class);
        gitHubPullRequestService = mock(GitHubPullRequestService.class);
        settingsService = mock(SystemSettingsService.class);

        service = new DesignShopOrchestrationService(projectRepository, designShopCycleRepository,
                readinessService, designAssetService, projectFlowService, contextService,
                gitHubPullRequestService, settingsService);

        project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("Test Project");
        project.setSlug("test-project");

        when(settingsService.effectiveBoolean("design_shop_enabled")).thenReturn(true);
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
    }

    @Test
    void doesNothingWhenFlagDisabled() {
        when(settingsService.effectiveBoolean("design_shop_enabled")).thenReturn(false);

        service.tick();

        verifyNoInteractions(projectRepository, readinessService, designAssetService, projectFlowService);
    }

    @Test
    void startsACycleOnTheRisingEdgeOfReadiness() {
        when(designShopCycleRepository.findByProjectId(project.getId())).thenReturn(Optional.empty());
        when(readinessService.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(5, 5, 5, 5, 1.0, true));
        DesignAssetService.DesignAssetResult result = new DesignAssetService.DesignAssetResult(
                true, "ok", "stitch", "/tmp/x.png", "", "image/png", "", "design/draft/round-1");
        when(designAssetService.generateAsset(eq(project), any(), anyString(), eq("mockup"), eq("fast"), eq(false)))
                .thenReturn(result);

        service.tick();

        verify(projectFlowService).dispatchDesignReview(eq(project), eq("design/draft/round-1"), anyString());
        ArgumentCaptor<DesignShopCycleEntity> saved = ArgumentCaptor.forClass(DesignShopCycleEntity.class);
        verify(designShopCycleRepository).save(saved.capture());
        assertThat(saved.getValue().isLastWasReady()).isTrue();
        assertThat(saved.getValue().getStage()).isEqualTo(DesignShopCycleEntity.STAGE_AWAITING_REVIEW);
        assertThat(saved.getValue().getDraftPath()).isEqualTo("design/draft/round-1");
    }

    @Test
    void doesNotStartASecondCycleWhileStillReady() {
        DesignShopCycleEntity cycle = new DesignShopCycleEntity();
        cycle.setProjectId(project.getId());
        cycle.setLastWasReady(true);
        cycle.setStage(DesignShopCycleEntity.STAGE_DONE);
        when(designShopCycleRepository.findByProjectId(project.getId())).thenReturn(Optional.of(cycle));
        when(readinessService.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(5, 5, 5, 5, 1.0, true));

        service.tick();

        verifyNoInteractions(designAssetService);
        verify(designShopCycleRepository, never()).save(any());
    }

    @Test
    void resetsLastWasReadyOnceReadinessDropsAgain() {
        DesignShopCycleEntity cycle = new DesignShopCycleEntity();
        cycle.setProjectId(project.getId());
        cycle.setLastWasReady(true);
        cycle.setStage(DesignShopCycleEntity.STAGE_DONE);
        when(designShopCycleRepository.findByProjectId(project.getId())).thenReturn(Optional.of(cycle));
        when(readinessService.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(5, 3, 5, 3, 0.6, true));

        service.tick();

        ArgumentCaptor<DesignShopCycleEntity> saved = ArgumentCaptor.forClass(DesignShopCycleEntity.class);
        verify(designShopCycleRepository).save(saved.capture());
        assertThat(saved.getValue().isLastWasReady()).isFalse();
    }

    @Test
    void leavesLastWasReadyFalseWhenGenerationFailsSoItRetriesNextTick() {
        when(designShopCycleRepository.findByProjectId(project.getId())).thenReturn(Optional.empty());
        when(readinessService.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(5, 5, 5, 5, 1.0, true));
        when(designAssetService.generateAsset(eq(project), any(), anyString(), eq("mockup"), eq("fast"), eq(false)))
                .thenReturn(DesignAssetService.DesignAssetResult.unavailable("drift"));

        service.tick();

        verify(projectFlowService, never()).dispatchDesignReview(any(), any(), any());
        verify(designShopCycleRepository, never()).save(any());
    }

    @Test
    void rejectsANanoBananaFallbackResultEvenWhenAvailable() {
        // Confirmed live 2026-08-10 (test-forty-third): the design shop must only ever accept a real
        // Stitch draft - nano-banana produces a raw image with no HTML/CSS to review or implement
        // against, and no mockup.html for the promotion step to find later.
        when(designShopCycleRepository.findByProjectId(project.getId())).thenReturn(Optional.empty());
        when(readinessService.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(5, 5, 5, 5, 1.0, true));
        DesignAssetService.DesignAssetResult nanoBananaResult = new DesignAssetService.DesignAssetResult(
                true, "ok", "gemini-3.1-flash-image", "/tmp/x.png", "", "image/png", "", "design/draft/round-1");
        when(designAssetService.generateAsset(eq(project), any(), anyString(), eq("mockup"), eq("fast"), eq(false)))
                .thenReturn(nanoBananaResult);

        service.tick();

        verify(projectFlowService, never()).dispatchDesignReview(any(), any(), any());
        verify(designShopCycleRepository, never()).save(any());
    }

    @Test
    void dispatchesImplementationOnceTheDraftIsApproved() {
        DesignShopCycleEntity cycle = new DesignShopCycleEntity();
        cycle.setProjectId(project.getId());
        cycle.setLastWasReady(true);
        cycle.setStage(DesignShopCycleEntity.STAGE_AWAITING_REVIEW);
        cycle.setDraftPath("design/draft/round-1");
        cycle.setUpdatedAt(Instant.now());
        when(designShopCycleRepository.findByProjectId(project.getId())).thenReturn(Optional.of(cycle));
        when(gitHubPullRequestService.fetchFileBytes(eq(project), any(), eq("design/approved/round-1/mockup.html")))
                .thenReturn(Optional.of(new byte[]{1}));

        service.tick();

        verify(projectFlowService).dispatchDesignImplementation(eq(project), eq("design/approved/round-1"), anyString());
        ArgumentCaptor<DesignShopCycleEntity> saved = ArgumentCaptor.forClass(DesignShopCycleEntity.class);
        verify(designShopCycleRepository).save(saved.capture());
        assertThat(saved.getValue().getStage()).isEqualTo(DesignShopCycleEntity.STAGE_DONE);
        verifyNoInteractions(readinessService);
    }

    @Test
    void staysAwaitingReviewWhileNotYetApprovedAndNotTimedOut() {
        DesignShopCycleEntity cycle = new DesignShopCycleEntity();
        cycle.setProjectId(project.getId());
        cycle.setLastWasReady(true);
        cycle.setStage(DesignShopCycleEntity.STAGE_AWAITING_REVIEW);
        cycle.setDraftPath("design/draft/round-1");
        cycle.setUpdatedAt(Instant.now());
        when(designShopCycleRepository.findByProjectId(project.getId())).thenReturn(Optional.of(cycle));
        when(gitHubPullRequestService.fetchFileBytes(eq(project), any(), eq("design/approved/round-1/mockup.html")))
                .thenReturn(Optional.empty());

        service.tick();

        verify(projectFlowService, never()).dispatchDesignImplementation(any(), any(), any());
        verify(designShopCycleRepository, never()).save(any());
    }

    @Test
    void abandonsAnAwaitingReviewCycleAfterTheTimeoutWindow() {
        DesignShopCycleEntity cycle = new DesignShopCycleEntity();
        cycle.setProjectId(project.getId());
        cycle.setLastWasReady(true);
        cycle.setStage(DesignShopCycleEntity.STAGE_AWAITING_REVIEW);
        cycle.setDraftPath("design/draft/round-1");
        cycle.setUpdatedAt(Instant.now().minus(java.time.Duration.ofHours(49)));
        when(designShopCycleRepository.findByProjectId(project.getId())).thenReturn(Optional.of(cycle));
        when(gitHubPullRequestService.fetchFileBytes(eq(project), any(), eq("design/approved/round-1/mockup.html")))
                .thenReturn(Optional.empty());

        service.tick();

        verify(projectFlowService, never()).dispatchDesignImplementation(any(), any(), any());
        ArgumentCaptor<DesignShopCycleEntity> saved = ArgumentCaptor.forClass(DesignShopCycleEntity.class);
        verify(designShopCycleRepository).save(saved.capture());
        assertThat(saved.getValue().getStage()).isEqualTo(DesignShopCycleEntity.STAGE_DONE);
    }
}
