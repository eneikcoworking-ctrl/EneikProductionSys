package com.eneik.production.services.design;

import com.eneik.production.kaizen.service.KaizenService;
import com.eneik.production.models.persistence.DesignShopCycleEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.repositories.DesignShopCycleRepository;
import com.eneik.production.services.runtime.RuntimeLauncherClient;
import com.eneik.production.services.settings.SystemSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DesignDriftMonitorServiceTest {

    private RuntimeLauncherClient launcherClient;
    private DesignConsistencyAuditService auditService;
    private SystemSettingsService settingsService;
    private KaizenService kaizenService;
    private DesignShopCycleRepository cycleRepository;
    private DesignDriftMonitorService service;
    private ProjectEntity project;

    @BeforeEach
    void setUp() {
        launcherClient = mock(RuntimeLauncherClient.class);
        auditService = spy(new DesignConsistencyAuditService());
        settingsService = mock(SystemSettingsService.class);
        kaizenService = mock(KaizenService.class);
        cycleRepository = mock(DesignShopCycleRepository.class);
        service = new DesignDriftMonitorService(launcherClient, auditService, settingsService, kaizenService, cycleRepository);

        project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("Test Project");

        when(settingsService.effectiveBoolean("design_shop_enabled")).thenReturn(true);
    }

    @Test
    void doesNothingWhenFlagDisabled() {
        when(settingsService.effectiveBoolean("design_shop_enabled")).thenReturn(false);

        service.checkLiveInstance(project, "http://localhost:8090/");

        verifyNoInteractions(launcherClient, cycleRepository, kaizenService);
    }

    @Test
    void skipsFetchAndComparisonWhenProjectHasNoEstablishedBaseline() {
        // Prescription 28 (FALSIFICATION_HARNESS / D008):
        // If no baseline exists, fetching the 70KB HTML body every cycle is pure unexecutable waste (muda).
        when(cycleRepository.findByProjectId(project.getId())).thenReturn(Optional.empty());

        service.checkLiveInstance(project, "http://localhost:8090/");

        verify(cycleRepository).findByProjectId(project.getId());
        verifyNoInteractions(launcherClient, kaizenService);
    }

    @Test
    void doesNothingWhenNoBodyFetched() {
        DesignShopCycleEntity cycle = new DesignShopCycleEntity();
        cycle.setDeclaredColors("#fbf9f1,#7d8570");
        cycle.setDeclaredFonts("Libre Caslon Text");
        when(cycleRepository.findByProjectId(project.getId())).thenReturn(Optional.of(cycle));

        when(launcherClient.fetchHtml(anyString()))
                .thenReturn(new RuntimeLauncherClient.FetchResult(null, null, 0, "connection refused"));

        service.checkLiveInstance(project, "http://localhost:8090/");

        verify(launcherClient).fetchHtml("http://localhost:8090/");
        verify(auditService, never()).audit(anyString(), any(), anyList());
    }

    @Test
    void whenBaselineExistsAndLivePageIsSpaShellDriftComparisonCannotBeJudgedWithoutFalseFailure() {
        // Prescription 28 (LEVEL_OF_ABSTRACTION_LOCK / D010 + TRUTH_STATUS_TABLE / D012):
        // Raw server HTML of an SPA shell with no CSS styles yields CANNOT_JUDGE ("не могу судить").
        DesignShopCycleEntity cycle = new DesignShopCycleEntity();
        cycle.setDeclaredColors("#fbf9f1,#7d8570");
        cycle.setDeclaredFonts("Libre Caslon Text");
        when(cycleRepository.findByProjectId(project.getId())).thenReturn(Optional.of(cycle));

        String spaHtml = "<!DOCTYPE html><html><head><title>SPA</title></head><body><div id=\"root\"></div><script src=\"/main.js\"></script></body></html>";
        when(launcherClient.fetchHtml(anyString()))
                .thenReturn(new RuntimeLauncherClient.FetchResult(200, spaHtml, spaHtml.length(), null));

        service.checkLiveInstance(project, "http://localhost:8090/");

        verify(launcherClient).fetchHtml("http://localhost:8090/");
        verify(auditService).audit(eq(spaHtml), any(), anyList());
        verifyNoInteractions(kaizenService);
    }

    @Test
    void whenBaselineExistsAndLivePageHasMatchingTokensComparisonSucceeds() {
        // Prescription 28: Comparison actually takes place against the project's real baseline.
        DesignShopCycleEntity cycle = new DesignShopCycleEntity();
        cycle.setDeclaredColors("#fbf9f1,#7d8570");
        cycle.setDeclaredFonts("Libre Caslon Text");
        when(cycleRepository.findByProjectId(project.getId())).thenReturn(Optional.of(cycle));

        String onBrandHtml = "<style>body{background:#fbf9f1;color:#7d8570;font-family:'Libre Caslon Text';}</style>";
        when(launcherClient.fetchHtml(anyString()))
                .thenReturn(new RuntimeLauncherClient.FetchResult(200, onBrandHtml, onBrandHtml.length(), null));

        service.checkLiveInstance(project, "http://localhost:8090/");

        verify(launcherClient).fetchHtml("http://localhost:8090/");
        verify(auditService).audit(eq(onBrandHtml), any(), anyList());
    }

    @Test
    void whenBaselineExistsAndLivePageDriftsLogsWarning() {
        DesignShopCycleEntity cycle = new DesignShopCycleEntity();
        cycle.setDeclaredColors("#fbf9f1,#7d8570");
        cycle.setDeclaredFonts("Libre Caslon Text");
        when(cycleRepository.findByProjectId(project.getId())).thenReturn(Optional.of(cycle));

        String offBrandHtml = "<style>body{background:#090f13;color:#161c21;}</style>";
        when(launcherClient.fetchHtml(anyString()))
                .thenReturn(new RuntimeLauncherClient.FetchResult(200, offBrandHtml, offBrandHtml.length(), null));

        service.checkLiveInstance(project, "http://localhost:8090/");

        verify(launcherClient).fetchHtml("http://localhost:8090/");
        verify(auditService).audit(eq(offBrandHtml), any(), anyList());
    }
}
