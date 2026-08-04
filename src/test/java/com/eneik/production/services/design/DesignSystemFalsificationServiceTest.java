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

    private final DesignSystemFalsificationService service = new DesignSystemFalsificationService(
            projectRepository, readinessService, stitchClient, wishlistRepository, settingsService);

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

        when(stitchClient.createDesignSystem(eq(project.getId().toString()), anyString()))
                .thenReturn(new StitchClient.DesignSystemResult(true, "ok", "ds-42", "Created Stitch design system."));
        when(stitchClient.applyDesignSystem(eq(project.getId().toString()), eq("ds-42"), any()))
                .thenReturn(new StitchClient.ApplyDesignSystemResult(true, "ok", "Applied design system."));

        service.applyDesignSystemsToShippedEpics();

        verify(stitchClient, times(1)).createDesignSystem(eq(project.getId().toString()), anyString());
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

        verify(stitchClient, never()).createDesignSystem(any(), any());
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
        when(stitchClient.createDesignSystem(any(), any()))
                .thenReturn(StitchClient.DesignSystemResult.unavailable("failed"));

        service.applyDesignSystemsToShippedEpics();

        verify(stitchClient, never()).applyDesignSystem(any(), any(), any());
        verify(wishlistRepository, never()).save(any());
    }
}
