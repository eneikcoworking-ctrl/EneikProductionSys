package com.eneik.production.services.design;

import com.eneik.production.kaizen.service.KaizenService;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.services.runtime.RuntimeLauncherClient;
import com.eneik.production.services.settings.SystemSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DesignDriftMonitorServiceTest {

    private RuntimeLauncherClient launcherClient;
    private DesignConsistencyAuditService auditService;
    private SystemSettingsService settingsService;
    private KaizenService kaizenService;
    private DesignDriftMonitorService service;
    private ProjectEntity project;

    @BeforeEach
    void setUp() {
        launcherClient = mock(RuntimeLauncherClient.class);
        auditService = new DesignConsistencyAuditService();
        settingsService = mock(SystemSettingsService.class);
        kaizenService = mock(KaizenService.class);
        service = new DesignDriftMonitorService(launcherClient, auditService, settingsService, kaizenService);

        project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("Test Project");

        when(settingsService.effectiveBoolean("design_shop_enabled")).thenReturn(true);
    }

    @Test
    void doesNothingWhenFlagDisabled() {
        when(settingsService.effectiveBoolean("design_shop_enabled")).thenReturn(false);

        service.checkLiveInstance(project, "http://localhost:8090/");

        verifyNoInteractions(launcherClient, kaizenService);
    }

    @Test
    void doesNothingWhenNoBodyFetched() {
        when(launcherClient.fetchHtml(anyString()))
                .thenReturn(new RuntimeLauncherClient.FetchResult(null, null, 0, "connection refused"));

        service.checkLiveInstance(project, "http://localhost:8090/");

        verifyNoInteractions(kaizenService);
    }

    @Test
    void recordsAKaizenFindingWhenTheLiveServedPageIsOffToken() {
        when(launcherClient.fetchHtml(anyString())).thenReturn(new RuntimeLauncherClient.FetchResult(
                200, "<style>body{background:#090f13;color:#161c21;}</style>", 40, null));

        service.checkLiveInstance(project, "http://localhost:8090/");

        verify(kaizenService).recordProductRuntimeDefectProposal(eq(project.getId()), eq(project.getName()),
                anyString(), anyString());
    }

    @Test
    void doesNotRecordAFindingWhenTheLiveServedPageIsOnToken() {
        when(launcherClient.fetchHtml(anyString())).thenReturn(new RuntimeLauncherClient.FetchResult(
                200, "<style>body{background:#fbf9f1;color:#7d8570;}</style>", 40, null));

        service.checkLiveInstance(project, "http://localhost:8090/");

        verifyNoInteractions(kaizenService);
    }
}
