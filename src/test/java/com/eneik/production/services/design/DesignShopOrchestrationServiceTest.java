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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private DesignConsistencyAuditService consistencyAuditService;
    private final com.eneik.production.repositories.WishlistRepository wishlistRepository =
            org.mockito.Mockito.mock(com.eneik.production.repositories.WishlistRepository.class);
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

        service = new DesignShopOrchestrationService(projectRepository, designShopCycleRepository,
                readinessService, designAssetService, projectFlowService, contextService,
                gitHubPullRequestService, settingsService, consistencyAuditService, wishlistRepository, null);
        // 2026-08-14 (bug-hunt sweep): ensureCycleRow/claimStartCycle/releaseStartCycleClaim are called via
        // a self-proxy field (REQUIRED transaction, same pattern as JulesDispatchService.self) - wired to
        // the instance itself here since there's no real Spring proxy in a plain unit test.
        org.springframework.test.util.ReflectionTestUtils.setField(service, "self", service);

        project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("Test Project");
        project.setSlug("test-project");

        when(settingsService.effectiveBoolean("design_shop_enabled")).thenReturn(true);
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        // Defaults so every pre-existing test below still reaches its real assertions: ensureCycleRow's
        // save-fresh-row path just echoes back what it was given, and the start-cycle claim always
        // succeeds by default - Mockito's own default for an unstubbed int-returning method is 0, which
        // would silently skip startCycle for every test that doesn't explicitly stub this. A dedicated test
        // overrides this to exercise the "already claimed" path.
        when(designShopCycleRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(designShopCycleRepository.claimStartCycle(any(), any())).thenReturn(1);
        when(settingsService.effectiveDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));
    }

    @Test
    void doesNothingWhenFlagDisabled() {
        when(settingsService.effectiveBoolean("design_shop_enabled")).thenReturn(false);

        service.tick();

        verifyNoInteractions(projectRepository, readinessService, designAssetService, projectFlowService);
    }

    // 2026-08-14 (bug-hunt sweep): the novel, risk-bearing logic of this fix - a genuinely concurrent
    // second tick() for the same project's readiness edge must do NO Stitch/dispatch work at all, since the
    // whole point is closing the window where two overlapping ticks both start a real design cycle for the
    // same round.
    @Test
    void skipsStartCycleWhenTheClaimIsAlreadyHeldByAConcurrentTick() {
        when(designShopCycleRepository.findByProjectId(project.getId())).thenReturn(Optional.empty());
        when(readinessService.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(5, 5, 5, 5, 1.0, true));
        when(designShopCycleRepository.claimStartCycle(eq(project.getId()), any())).thenReturn(0);

        service.tick();

        verifyNoInteractions(designAssetService, projectFlowService);
        verify(designShopCycleRepository, never()).releaseStartCycleClaim(any());
    }

    @Test
    void startsACycleOnTheRisingEdgeOfReadinessAndCapturesTheDesignBaselineOnFirstGeneration() {
        when(designShopCycleRepository.findByProjectId(project.getId())).thenReturn(Optional.empty());
        when(readinessService.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(5, 5, 5, 5, 1.0, true));
        DesignAssetService.DesignAssetResult result = new DesignAssetService.DesignAssetResult(
                true, "ok", "stitch", "/tmp/x.png", "", "image/png", "", "design/draft/round-1", "stitch-proj-1", "screen-1");
        when(designAssetService.generateAsset(eq(project), any(), anyString(), eq("mockup"), eq("fast"), eq(false),
                isNull(), eq(List.of()), eq(List.of()), eq(true)))
                .thenReturn(result);
        when(gitHubPullRequestService.fetchFileBytes(eq(project), any(), eq("design/draft/round-1/mockup.html")))
                .thenReturn(Optional.of("<style>body{background:#2e3a8c;font-family:'Hanken Grotesk';}</style>".getBytes()));

        service.tick();

        verify(projectFlowService).dispatchDesignReview(eq(project), eq("design/draft/round-1"), anyString());
        ArgumentCaptor<DesignShopCycleEntity> saved = ArgumentCaptor.forClass(DesignShopCycleEntity.class);
        verify(designShopCycleRepository).save(saved.capture());
        assertThat(saved.getValue().isLastWasReady()).isTrue();
        assertThat(saved.getValue().getStage()).isEqualTo(DesignShopCycleEntity.STAGE_AWAITING_REVIEW);
        assertThat(saved.getValue().getDraftPath()).isEqualTo("design/draft/round-1");
        // Bootstrap baseline (2026-08-11): the project's first real Stitch draft fixes its own canonical
        // Tokens(f) domain - captured from what Stitch actually produced, not invented.
        assertThat(saved.getValue().getStitchProjectId()).isEqualTo("stitch-proj-1");
        assertThat(saved.getValue().getStitchScreenId()).isEqualTo("screen-1");
        assertThat(saved.getValue().getDeclaredColors()).contains("#2e3a8c");
        assertThat(saved.getValue().getDeclaredFonts()).contains("hanken grotesk");
    }

    @Test
    void reusesTheStoredBaselineOnASubsequentGeneration() {
        DesignShopCycleEntity cycle = new DesignShopCycleEntity();
        cycle.setProjectId(project.getId());
        cycle.setLastWasReady(false);
        cycle.setStage(DesignShopCycleEntity.STAGE_DONE);
        cycle.setStitchProjectId("stitch-proj-1");
        cycle.setStitchScreenId("screen-1");
        cycle.setDeclaredColors("#2e3a8c,#14b8a6");
        cycle.setDeclaredFonts("hanken grotesk");
        when(designShopCycleRepository.findByProjectId(project.getId())).thenReturn(Optional.of(cycle));
        when(readinessService.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(5, 5, 5, 5, 1.0, true));
        DesignAssetService.DesignAssetResult result = new DesignAssetService.DesignAssetResult(
                true, "ok", "stitch", "/tmp/x.png", "", "image/png", "", "design/draft/round-2", "stitch-proj-1", "screen-2");
        when(designAssetService.generateAsset(eq(project), any(), anyString(), eq("mockup"), eq("fast"), eq(false),
                isNull(), eq(List.of("#2e3a8c", "#14b8a6")), eq(List.of("hanken grotesk")), eq(true)))
                .thenReturn(result);
        // 2026-08-15: the draft is now verified to carry implementable HTML before the cycle proceeds -
        // the gate asks after the artifact's property rather than the generator's name - so a usable draft
        // has to actually be present for this path to run at all.
        when(gitHubPullRequestService.fetchFileBytes(eq(project), any(), eq("design/draft/round-2/mockup.html")))
                .thenReturn(Optional.of("<style>body{background:#2e3a8c;}</style>".getBytes()));

        service.tick();

        verify(designAssetService).generateAsset(eq(project), any(), anyString(), eq("mockup"), eq("fast"), eq(false),
                isNull(), eq(List.of("#2e3a8c", "#14b8a6")), eq(List.of("hanken grotesk")), eq(true));
        verify(projectFlowService).dispatchDesignReview(eq(project), eq("design/draft/round-2"), anyString());
        // The already-established baseline must not be re-captured on a later cycle. Asserted on the
        // stored tokens rather than on whether the file was read: since 2026-08-15 the same file is also
        // read to verify the draft carries implementable HTML, which is a different question from
        // re-extracting a palette, so "was it read" no longer expresses the claim.
        ArgumentCaptor<DesignShopCycleEntity> savedCycle = ArgumentCaptor.forClass(DesignShopCycleEntity.class);
        verify(designShopCycleRepository, atLeastOnce()).save(savedCycle.capture());
        assertThat(savedCycle.getAllValues())
                .allSatisfy(c -> assertThat(c.getDeclaredColors()).isEqualTo("#2e3a8c,#14b8a6"));
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
        when(designAssetService.generateAsset(eq(project), any(), anyString(), eq("mockup"), eq("fast"), eq(false),
                isNull(), eq(List.of()), eq(List.of()), eq(true)))
                .thenReturn(DesignAssetService.DesignAssetResult.unavailable("drift"));

        service.tick();

        verify(projectFlowService, never()).dispatchDesignReview(any(), any(), any());
        verify(designShopCycleRepository, never()).save(any());
        // 2026-08-14 (bug-hunt sweep): the claim taken before this generation attempt must be released on
        // failure too, or the next tick's retry (the whole point of leaving lastWasReady=false) would be
        // silently blocked by a claim from an attempt that never actually finished.
        verify(designShopCycleRepository).releaseStartCycleClaim(project.getId());
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
                true, "ok", "gemini-3.1-flash-image", "/tmp/x.png", "", "image/png", "", "design/draft/round-1", "", "");
        when(designAssetService.generateAsset(eq(project), any(), anyString(), eq("mockup"), eq("fast"), eq(false),
                isNull(), eq(List.of()), eq(List.of()), eq(true)))
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

    /**
     * Model rule 8.11 O8: every hold leaves a readable record of its reason. Measured 2026-08-30 - both
     * design-shop flags true in the database, this cron running every five minutes, and the cycle row
     * untouched for 45 hours with nothing anywhere able to say whether it was never polled or polled and
     * held. The hold itself is correct (8.13 fires on the rising edge of readiness, and the front had not
     * arrived); what was wrong is that it was invisible.
     */
    @Test
    void aShopThatHoldsSaysWhyItHeld() {
        DesignShopCycleEntity cycle = new DesignShopCycleEntity();
        cycle.setProjectId(project.getId());
        cycle.setStage(DesignShopCycleEntity.STAGE_IDLE);
        cycle.setLastWasReady(false);
        when(designShopCycleRepository.findByProjectId(project.getId())).thenReturn(java.util.Optional.of(cycle));
        when(readinessService.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(9, 5, 19, 13, 0.684, true));

        Logs logs = Logs.capture(DesignShopOrchestrationService.class);
        try {
            service.tick();
        } finally {
            logs.stop();
        }

        assertTrue(logs.contains("the readiness front has not occurred"), "a hold must be readable");
        assertTrue(logs.contains("ratio=0.684"), "and must carry the value that held it");
    }

    /** Model rule 8.11 O9: an unchanging fact is written once, not on every one of the 288 daily ticks. */
    @Test
    void anUnchangedHoldIsNotRepeatedOnTheNextTick() {
        DesignShopCycleEntity cycle = new DesignShopCycleEntity();
        cycle.setProjectId(project.getId());
        cycle.setStage(DesignShopCycleEntity.STAGE_IDLE);
        cycle.setLastWasReady(false);
        when(designShopCycleRepository.findByProjectId(project.getId())).thenReturn(java.util.Optional.of(cycle));
        when(readinessService.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(9, 5, 19, 13, 0.684, true));
        service.tick();

        Logs logs = Logs.capture(DesignShopOrchestrationService.class);
        try {
            service.tick();
        } finally {
            logs.stop();
        }

        assertFalse(logs.contains("the readiness front has not occurred"));
    }

    private static final class Logs {
        private final ch.qos.logback.classic.Logger logger;
        private final ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();

        private Logs(Class<?> type) {
            logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(type);
            appender.start();
            logger.addAppender(appender);
        }

        static Logs capture(Class<?> type) {
            return new Logs(type);
        }

        boolean contains(String fragment) {
            return appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains(fragment));
        }

        void stop() {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
