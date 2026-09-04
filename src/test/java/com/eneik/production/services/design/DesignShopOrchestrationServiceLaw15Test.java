package com.eneik.production.services.design;

import com.eneik.production.models.persistence.DesignShopCycleEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.repositories.DesignShopCycleRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.ClientDeliverableReadinessService;
import com.eneik.production.services.ProjectFlowService;
import com.eneik.production.services.dashboard.ProjectOperationalContextService;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.settings.SystemSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Law 15 (Закон готовности):
 * ready(P, t) <=> decompositionComplete(P, t) & (ratio(P, t) >= theta || selfFalsificationReadyRatio >= 1.0)
 * trigger(P, t) <=> ready(P, t) & !ready(P, t-1)
 *
 * Verifies that the design shop operates on the readiness front (theta = 0.80 by default)
 * instead of holding infinitely on an unreachable 1.0 in brownfield projects,
 * triggers on the rising edge, and re-arms when scope expands and clears again.
 */
class DesignShopOrchestrationServiceLaw15Test {

    private ProjectRepository projectRepository;
    private DesignShopCycleRepository designShopCycleRepository;
    private ClientDeliverableReadinessService readinessService;
    private DesignAssetService designAssetService;
    private ProjectFlowService projectFlowService;
    private ProjectOperationalContextService contextService;
    private GitHubPullRequestService gitHubPullRequestService;
    private SystemSettingsService settingsService;
    private DesignConsistencyAuditService consistencyAuditService;
    private WishlistRepository wishlistRepository;
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
        consistencyAuditService = new DesignConsistencyAuditService();
        wishlistRepository = mock(WishlistRepository.class);

        service = new DesignShopOrchestrationService(projectRepository, designShopCycleRepository,
                readinessService, designAssetService, projectFlowService, contextService,
                gitHubPullRequestService, settingsService, consistencyAuditService, wishlistRepository, null);
        ReflectionTestUtils.setField(service, "self", service);

        project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("Law 15 Brownfield Project");
        project.setSlug("law-15-brownfield");

        when(settingsService.effectiveBoolean("design_shop_enabled")).thenReturn(true);
        when(settingsService.effectiveDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        when(designShopCycleRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(designShopCycleRepository.claimStartCycle(any(), any())).thenReturn(1);
    }

    @Test
    void isReadinessReachedReturnsFalseWhenDecompositionIncomplete() {
        ClientDeliverableReadinessService.Readiness readiness =
                new ClientDeliverableReadinessService.Readiness(10, 9, 20, 19, 0.90, false, 0.90);
        assertFalse(service.isReadinessReached(readiness));
    }

    @Test
    void isReadinessReachedReturnsFalseWhenTotalDeliverablesAndFeaturesZero() {
        ClientDeliverableReadinessService.Readiness readiness =
                new ClientDeliverableReadinessService.Readiness(0, 0, 0, 0, 1.0, true, 1.0);
        assertFalse(service.isReadinessReached(readiness));
    }

    @Test
    void isReadinessReachedReturnsTrueAtOrAboveConfiguredThreshold() {
        // Default threshold is 0.80
        ClientDeliverableReadinessService.Readiness readiness80 =
                new ClientDeliverableReadinessService.Readiness(10, 8, 20, 16, 0.80, true, 0.80);
        assertTrue(service.isReadinessReached(readiness80));

        ClientDeliverableReadinessService.Readiness readiness79 =
                new ClientDeliverableReadinessService.Readiness(10, 7, 20, 15, 0.79, true, 0.79);
        assertFalse(service.isReadinessReached(readiness79));
    }

    @Test
    void isReadinessReachedHonorsCustomConfiguredThreshold() {
        when(settingsService.effectiveDouble(eq("design_shop_readiness_threshold"), anyDouble()))
                .thenReturn(0.90);

        ClientDeliverableReadinessService.Readiness readiness85 =
                new ClientDeliverableReadinessService.Readiness(10, 8, 20, 17, 0.85, true, 0.85);
        assertFalse(service.isReadinessReached(readiness85));

        ClientDeliverableReadinessService.Readiness readiness90 =
                new ClientDeliverableReadinessService.Readiness(10, 9, 20, 18, 0.90, true, 0.90);
        assertTrue(service.isReadinessReached(readiness90));
    }

    @Test
    void isReadinessReachedReturnsTrueWhenBrownfieldReachesSelfFalsificationTerminalFrontier() {
        // Ratio is below 0.80 (0.70), but selfFalsificationReadyRatio is 1.0 (all dead ends resolved)
        ClientDeliverableReadinessService.Readiness brownfieldTerminal =
                new ClientDeliverableReadinessService.Readiness(10, 7, 20, 14, 0.70, true, 1.0);
        assertTrue(service.isReadinessReached(brownfieldTerminal));
    }

    @Test
    void firesOnRisingEdgeAtBrownfieldThresholdAndDoesNotRefireOnSteadyState() {
        when(designShopCycleRepository.findByProjectId(project.getId())).thenReturn(Optional.empty());
        when(readinessService.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(10, 8, 20, 16, 0.80, true, 0.80));

        DesignAssetService.DesignAssetResult result = new DesignAssetService.DesignAssetResult(
                true, "ok", "stitch", "/tmp/x.png", "", "image/png", "", "design/draft/round-1", "stitch-proj", "screen-1");
        when(designAssetService.generateAsset(eq(project), any(), anyString(), eq("mockup"), eq("fast"), eq(false),
                isNull(), eq(List.of()), eq(List.of()), eq(true)))
                .thenReturn(result);
        when(gitHubPullRequestService.fetchFileBytes(eq(project), any(), eq("design/draft/round-1/mockup.html")))
                .thenReturn(Optional.of("<style>body{background:#fff;}</style>".getBytes()));

        // Tick 1: rising edge (lastWasReady was false) -> starts cycle
        service.tick();

        verify(projectFlowService).dispatchDesignReview(eq(project), eq("design/draft/round-1"), anyString());
        ArgumentCaptor<DesignShopCycleEntity> savedCaptor = ArgumentCaptor.forClass(DesignShopCycleEntity.class);
        verify(designShopCycleRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().isLastWasReady()).isTrue();

        // Tick 2: steady state (cycle now has lastWasReady = true) -> holds, does not call generateAsset again
        reset(designAssetService, projectFlowService);
        DesignShopCycleEntity steadyCycle = savedCaptor.getValue();
        steadyCycle.setStage(DesignShopCycleEntity.STAGE_IDLE); // review completed
        when(designShopCycleRepository.findByProjectId(project.getId())).thenReturn(Optional.of(steadyCycle));

        service.tick();

        verifyNoInteractions(designAssetService, projectFlowService);
    }

    @Test
    void reArmsWhenReadinessDropsAndRefiresOnSecondFront() {
        DesignShopCycleEntity cycle = new DesignShopCycleEntity();
        cycle.setProjectId(project.getId());
        cycle.setLastWasReady(true);
        cycle.setStage(DesignShopCycleEntity.STAGE_DONE);
        when(designShopCycleRepository.findByProjectId(project.getId())).thenReturn(Optional.of(cycle));

        // Step 1: new work arrives, ratio drops to 0.60 -> resets lastWasReady to false
        when(readinessService.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(10, 6, 20, 12, 0.60, true, 0.60));

        service.tick();

        ArgumentCaptor<DesignShopCycleEntity> resetCaptor = ArgumentCaptor.forClass(DesignShopCycleEntity.class);
        verify(designShopCycleRepository).save(resetCaptor.capture());
        assertThat(resetCaptor.getValue().isLastWasReady()).isFalse();

        // Step 2: new work is delivered, ratio rises to 0.85 -> second rising edge fires!
        cycle.setLastWasReady(false);
        when(readinessService.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(10, 9, 20, 17, 0.85, true, 0.85));

        DesignAssetService.DesignAssetResult result = new DesignAssetService.DesignAssetResult(
                true, "ok", "stitch", "/tmp/x.png", "", "image/png", "", "design/draft/round-2", "stitch-proj", "screen-2");
        when(designAssetService.generateAsset(eq(project), any(), anyString(), eq("mockup"), eq("fast"), eq(false),
                isNull(), eq(List.of()), eq(List.of()), eq(true)))
                .thenReturn(result);
        when(gitHubPullRequestService.fetchFileBytes(eq(project), any(), eq("design/draft/round-2/mockup.html")))
                .thenReturn(Optional.of("<style>body{background:#111;}</style>".getBytes()));

        service.tick();

        verify(projectFlowService).dispatchDesignReview(eq(project), eq("design/draft/round-2"), anyString());
    }
}
