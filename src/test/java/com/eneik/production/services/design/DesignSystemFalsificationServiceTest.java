package com.eneik.production.services.design;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.ClientDeliverableReadinessService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.stitch.StitchClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Phase B (design/QA acceptance redesign, 2026-08-04): DesignSystemFalsificationService applies Stitch's
 * design system to an epic's already-shipped real UI (see ClientDeliverableReadinessService.
 * listEpicsWithMergedUiCode for the eligibility check this consumes) and records an audit-trail wishlist
 * row - never a compiler input, purely for idempotency and dashboard visibility.
 */
class DesignSystemFalsificationServiceTest {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ClientDeliverableReadinessService readinessService = mock(ClientDeliverableReadinessService.class);
    private final StitchClient stitchClient = mock(StitchClient.class);
    private final WishlistRepository wishlistRepository = mock(WishlistRepository.class);
    private final SystemSettingsService settingsService = mock(SystemSettingsService.class);
    // F45 (2026-08-16): the service now reads the STITCH project id from the design-shop cycle row rather
    // than passing Eneik's own project id, which Stitch rightly answered "Requested entity was not found".
    // Returning empty here is the case that matters most: no Stitch project exists yet, and the argument
    // must then be omitted rather than filled with something else.
    private final com.eneik.production.repositories.DesignShopCycleRepository designShopCycleRepository =
            mock(com.eneik.production.repositories.DesignShopCycleRepository.class);

    private final DesignSystemFalsificationService service = new DesignSystemFalsificationService(
            projectRepository, readinessService, stitchClient, designShopCycleRepository, wishlistRepository,
            settingsService, null);
    // 2026-08-14 (bug-hunt sweep): recordAuditTrail is now called via a self-proxy field (REQUIRES_NEW,
    // same pattern as JulesDispatchService.self) - wired to the instance itself here since there's no real
    // Spring proxy in a plain unit test.
    {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "self", service);
    }

    private ProjectEntity activeProject() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setStatus(ProjectStatus.active);
        return project;
    }

    @Test
    void skipsEntirelyWhenFeatureFlagIsOff() {
        when(settingsService.effectiveBoolean("design_system_falsification_enabled")).thenReturn(false);
        when(stitchClient.hasStitchKey()).thenReturn(true);

        service.applyDesignSystemsToShippedEpics();

        verifyNoInteractions(projectRepository);
    }

    @Test
    void skipsEntirelyWhenStitchKeyIsMissing() {
        when(settingsService.effectiveBoolean("design_system_falsification_enabled")).thenReturn(true);
        when(stitchClient.hasStitchKey()).thenReturn(false);

        service.applyDesignSystemsToShippedEpics();

        verifyNoInteractions(projectRepository);
    }

    /**
     * The second half of commit 7e1df40's contract: with no Stitch project on record the id is OMITTED,
     * never substituted with something Stitch cannot resolve. Sending a wrong reference is worse than
     * sending none - one fails loudly every cycle, the other silently attaches the design system to
     * nothing. Nothing covered this branch, which is part of why the first half could go red unnoticed.
     */
    @Test
    void withNoStitchProjectOnRecordTheDesignSystemIsCreatedUnattached() {
        when(settingsService.effectiveBoolean("design_system_falsification_enabled")).thenReturn(true);
        when(stitchClient.hasStitchKey()).thenReturn(true);
        ProjectEntity project = activeProject();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));

        UUID featureId = UUID.randomUUID();
        ClientDeliverableReadinessService.UiCodeEpic epic =
                new ClientDeliverableReadinessService.UiCodeEpic(featureId, "Core Knowledge Base Portal");
        when(readinessService.listEpicsWithMergedUiCode(project.getId())).thenReturn(List.of(epic));
        when(wishlistRepository.existsByFeatureIdAndSource(featureId, WishlistSource.design_system_falsification))
                .thenReturn(false);
        when(designShopCycleRepository.findByProjectId(project.getId())).thenReturn(java.util.Optional.empty());
        when(stitchClient.createDesignSystem(isNull(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new StitchClient.DesignSystemResult(true, "ok", "ds-43", "Created Stitch design system."));
        when(stitchClient.applyDesignSystem(isNull(), eq("ds-43"), any()))
                .thenReturn(new StitchClient.ApplyDesignSystemResult(true, "ok", "Applied design system."));

        service.applyDesignSystemsToShippedEpics();

        verify(stitchClient, times(1)).createDesignSystem(isNull(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void eligibleEpicTriggersExactlyOneCreateAndApplyPairAndOneWishlistRecord() {
        when(settingsService.effectiveBoolean("design_system_falsification_enabled")).thenReturn(true);
        when(stitchClient.hasStitchKey()).thenReturn(true);
        ProjectEntity project = activeProject();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));

        UUID featureId = UUID.randomUUID();
        ClientDeliverableReadinessService.UiCodeEpic epic =
                new ClientDeliverableReadinessService.UiCodeEpic(featureId, "Core Knowledge Base Portal");
        when(readinessService.listEpicsWithMergedUiCode(project.getId())).thenReturn(List.of(epic));
        when(wishlistRepository.existsByFeatureIdAndSource(featureId, WishlistSource.design_system_falsification))
                .thenReturn(false);

        // 2026-08-16, commit 7e1df40 ("send Stitch its own project id, or none at all"): the first argument
        // is Stitch's project id, read off the design-shop cycle row - not this factory's own project UUID,
        // which Stitch has never heard of. This test asserted the old contract and had been red since.
        var cycle = new com.eneik.production.models.persistence.DesignShopCycleEntity();
        cycle.setStitchProjectId("stitch-proj-7");
        when(designShopCycleRepository.findByProjectId(project.getId())).thenReturn(java.util.Optional.of(cycle));

        when(stitchClient.createDesignSystem(eq("stitch-proj-7"), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new StitchClient.DesignSystemResult(true, "ok", "ds-42", "Created Stitch design system."));
        when(stitchClient.applyDesignSystem(eq(project.getId().toString()), eq("ds-42"), any()))
                .thenReturn(new StitchClient.ApplyDesignSystemResult(true, "ok", "Applied design system."));

        service.applyDesignSystemsToShippedEpics();

        verify(stitchClient, times(1)).createDesignSystem(eq("stitch-proj-7"), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(stitchClient, times(1)).applyDesignSystem(eq(project.getId().toString()), eq("ds-42"), any());
        ArgumentCaptor<WishlistEntity> captor = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlistRepository).save(captor.capture());
        assertEquals(featureId, captor.getValue().getFeatureId());
        assertEquals(WishlistSource.design_system_falsification, captor.getValue().getSource());
    }

    @Test
    void alreadyProcessedEpicIsSkippedIdempotently() {
        when(settingsService.effectiveBoolean("design_system_falsification_enabled")).thenReturn(true);
        when(stitchClient.hasStitchKey()).thenReturn(true);
        ProjectEntity project = activeProject();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));

        UUID featureId = UUID.randomUUID();
        ClientDeliverableReadinessService.UiCodeEpic epic =
                new ClientDeliverableReadinessService.UiCodeEpic(featureId, "Already Processed Epic");
        when(readinessService.listEpicsWithMergedUiCode(project.getId())).thenReturn(List.of(epic));
        when(wishlistRepository.existsByFeatureIdAndSource(featureId, WishlistSource.design_system_falsification))
                .thenReturn(true);

        service.applyDesignSystemsToShippedEpics();

        verify(stitchClient, never()).createDesignSystem(any(), any(), any(), any(), any(), any());
        verify(wishlistRepository, never()).save(any());
    }

    @Test
    void createDesignSystemFailureSkipsApplyAndSkipsRecordingAudit() {
        when(settingsService.effectiveBoolean("design_system_falsification_enabled")).thenReturn(true);
        when(stitchClient.hasStitchKey()).thenReturn(true);
        ProjectEntity project = activeProject();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));

        UUID featureId = UUID.randomUUID();
        ClientDeliverableReadinessService.UiCodeEpic epic =
                new ClientDeliverableReadinessService.UiCodeEpic(featureId, "Some Epic");
        when(readinessService.listEpicsWithMergedUiCode(project.getId())).thenReturn(List.of(epic));
        when(wishlistRepository.existsByFeatureIdAndSource(featureId, WishlistSource.design_system_falsification))
                .thenReturn(false);
        when(stitchClient.createDesignSystem(any(), any(), any(), any(), any(), any()))
                .thenReturn(StitchClient.DesignSystemResult.unavailable("failed"));

        service.applyDesignSystemsToShippedEpics();

        verify(stitchClient, never()).applyDesignSystem(any(), any(), any());
        verify(wishlistRepository, never()).save(any());
    }
}
