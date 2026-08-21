package com.eneik.production.services.runtime;

import com.eneik.production.models.persistence.ClientRuntimeObservationEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.repositories.ClientRuntimeObservationRepository;
import com.eneik.production.services.settings.SystemSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Phase 1 of docs/reports/PLAN_client_runtime_observability_2026-08-09.md. These tests pin the
// operator's own two hardest-won requirements: (1) never a hard-coded schedule - due-ness is always
// derived from BetaPosterior's real math on this project's own history, (2) never observes a project
// Phase 0 hasn't cleared yet.
class ClientRuntimeObservabilityServiceTest {

    private ProjectEntity project(boolean launchabilityChecked) {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setSlug("test-project");
        project.setDefaultBranch("main");
        project.setRepositoryUrl("https://github.com/eneikdru/test-project.git");
        if (launchabilityChecked) {
            project.setLaunchabilityCheckedAt(Instant.now().minusSeconds(3600));
        }
        return project;
    }

    @Test
    void doesNothingWhenTheFeatureFlagIsOff() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(false);
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings, mock(com.eneik.production.kaizen.service.KaizenService.class), mock(com.eneik.production.services.design.DesignDriftMonitorService.class), projects, tasks, mock(ProductCapabilityService.class));

        service.maybeObserve(project(true));

        verify(launcher, never()).launch(any(), any(), any());
        verify(observations, never()).save(any());
    }

    @Test
    void doesNothingWhenPhase0LaunchabilityHasNotBeenCheckedYet() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings, mock(com.eneik.production.kaizen.service.KaizenService.class), mock(com.eneik.production.services.design.DesignDriftMonitorService.class), projects, tasks, mock(ProductCapabilityService.class));

        service.maybeObserve(project(false));

        verify(launcher, never()).launch(any(), any(), any());
    }

    @Test
    void observesImmediatelyWhenNoHistoryExistsYet() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        ProjectEntity proj = project(true);
        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId())).thenReturn(List.of());
        when(launcher.launch(any(), any(), any())).thenReturn(new RuntimeLauncherClient.LaunchResult(true, 5000, null, 18080, true, "sha-test"));
        when(launcher.healthcheck(any())).thenReturn(new RuntimeLauncherClient.HealthCheckResult(200, 50, null));
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings, mock(com.eneik.production.kaizen.service.KaizenService.class), mock(com.eneik.production.services.design.DesignDriftMonitorService.class), projects, tasks, mock(ProductCapabilityService.class));
        ReflectionTestUtils.setField(service, "baseDelayHours", 24L);
        ReflectionTestUtils.setField(service, "minimumDelayHours", 1L);

        service.maybeObserve(proj);

        verify(launcher, times(1)).launch(proj.getRepositoryUrl(), "main", "test-project");
        // 2026-08-11 (bounded live-preview window): a successful launch is no longer torn down right
        // away - it stays up so the dashboard link and a philosophical audit's live-fetch have something
        // real to reach. Instead, the project's own preview-tracking fields get recorded.
        verify(launcher, never()).teardown();
        verify(observations, times(1)).save(any(ClientRuntimeObservationEntity.class));
        verify(projects, times(1)).save(proj);
        assertTrue(proj.getLastRuntimePreviewLaunchedAt() != null);
        assertTrue(proj.getLastRuntimePreviewPort() == 18080);
    }

    @Test
    void skipsWhenNotYetDuePerTheAdaptiveCadenceFormula() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        ProjectEntity proj = project(true);

        // One single recent success just 5 minutes ago: with only 1 observation, the credible interval
        // is still wide (~0.28 - see BetaPosteriorTest), so the next check with a 24h base delay is
        // still hours away - definitely not due after only 5 minutes.
        ClientRuntimeObservationEntity recent = new ClientRuntimeObservationEntity();
        recent.setObservedAt(Instant.now().minusSeconds(300));
        recent.setLaunchSuccess(true);
        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId())).thenReturn(List.of(recent));
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings, mock(com.eneik.production.kaizen.service.KaizenService.class), mock(com.eneik.production.services.design.DesignDriftMonitorService.class), projects, tasks, mock(ProductCapabilityService.class));
        ReflectionTestUtils.setField(service, "baseDelayHours", 24L);
        ReflectionTestUtils.setField(service, "minimumDelayHours", 1L);

        service.maybeObserve(proj);

        verify(launcher, never()).launch(any(), any(), any());
    }

    @Test
    void observesAgainOnceEnoughRealTimeHasPassedPerTheFormula() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        ProjectEntity proj = project(true);

        // One observation, but a very long time ago - even a wide (~0.95, uninformative-prior-level)
        // interval times a 24h base delay is nowhere near this many days.
        ClientRuntimeObservationEntity old = new ClientRuntimeObservationEntity();
        old.setObservedAt(Instant.now().minusSeconds(30L * 24 * 3600));
        old.setLaunchSuccess(true);
        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId())).thenReturn(List.of(old));
        when(launcher.launch(any(), any(), any())).thenReturn(new RuntimeLauncherClient.LaunchResult(true, 5000, null, 18080, true, "sha-test"));
        when(launcher.healthcheck(any())).thenReturn(new RuntimeLauncherClient.HealthCheckResult(200, 50, null));
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings, mock(com.eneik.production.kaizen.service.KaizenService.class), mock(com.eneik.production.services.design.DesignDriftMonitorService.class), projects, tasks, mock(ProductCapabilityService.class));
        ReflectionTestUtils.setField(service, "baseDelayHours", 24L);
        ReflectionTestUtils.setField(service, "minimumDelayHours", 1L);

        service.maybeObserve(proj);

        verify(launcher, times(1)).launch(any(), any(), any());
    }

    /**
     * 2026-08-11 (live incident, test-forty-third): runtime-launcher now remaps every published host
     * port to avoid colliding with this factory's own services (see launcher.py's port override) and
     * reports the real external port back on the launch result - the health check must use THAT port,
     * not the old hardcoded default, or it will never reach the actually-running instance.
     */
    @Test
    void healthCheckUsesTheExternalPortReportedByTheLauncher() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        ProjectEntity proj = project(true);
        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId())).thenReturn(List.of());
        when(launcher.launch(any(), any(), any())).thenReturn(new RuntimeLauncherClient.LaunchResult(true, 5000, null, 18080, true, "sha-test"));
        when(launcher.healthcheck(any())).thenReturn(new RuntimeLauncherClient.HealthCheckResult(200, 50, null));
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings, mock(com.eneik.production.kaizen.service.KaizenService.class), mock(com.eneik.production.services.design.DesignDriftMonitorService.class), projects, tasks, mock(ProductCapabilityService.class));
        ReflectionTestUtils.setField(service, "healthCheckPath", "/health");
        ReflectionTestUtils.setField(service, "healthCheckPort", 8090);

        service.maybeObserve(proj);

        verify(launcher).healthcheck("http://localhost:18080/health");
    }

    @Test
    void healthCheckFallsBackToTheConfiguredPortWhenTheLauncherReportsNone() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        ProjectEntity proj = project(true);
        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId())).thenReturn(List.of());
        when(launcher.launch(any(), any(), any())).thenReturn(new RuntimeLauncherClient.LaunchResult(true, 5000, null, null, true, "sha-test"));
        when(launcher.healthcheck(any())).thenReturn(new RuntimeLauncherClient.HealthCheckResult(200, 50, null));
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings, mock(com.eneik.production.kaizen.service.KaizenService.class), mock(com.eneik.production.services.design.DesignDriftMonitorService.class), projects, tasks, mock(ProductCapabilityService.class));
        ReflectionTestUtils.setField(service, "healthCheckPath", "/health");
        ReflectionTestUtils.setField(service, "healthCheckPort", 8090);

        service.maybeObserve(proj);

        verify(launcher).healthcheck("http://localhost:8090/health");
    }

    @Test
    void aFailedLaunchIsRecordedAsFailureAndNeverAttemptsAHealthCheck() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        ProjectEntity proj = project(true);
        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId())).thenReturn(List.of());
        when(launcher.launch(any(), any(), any())).thenReturn(new RuntimeLauncherClient.LaunchResult(false, 1200, "docker compose up failed", null, true, "sha-test"));
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings, mock(com.eneik.production.kaizen.service.KaizenService.class), mock(com.eneik.production.services.design.DesignDriftMonitorService.class), projects, tasks, mock(ProductCapabilityService.class));

        service.maybeObserve(proj);

        verify(launcher, never()).healthcheck(any());
        verify(launcher, times(1)).teardown();
        assertTrue(true); // teardown always still runs, even after a failed launch - never leaks a partial stack
    }

    // Phase 2+3: a genuine statistical shift, confirmed after the new observation lands, surfaces as a
    // real, evidence-citing Kaizen proposal - not a vague alarm.

    @Test
    void aRealStatisticalShiftAfterTheNewObservationCreatesAKaizenProposal() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        var kaizen = mock(com.eneik.production.kaizen.service.KaizenService.class);
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        ProjectEntity proj = project(true);
        proj.setName("test-project");

        List<ClientRuntimeObservationEntity> shiftedHistory = new java.util.ArrayList<>();
        Instant t0 = Instant.now().minusSeconds(20L * 24 * 3600);
        for (int i = 0; i < 19; i++) {
            shiftedHistory.add(healthyObservation(t0.plusSeconds(i * 3600L)));
        }
        shiftedHistory.add(unhealthyObservation(t0.plusSeconds(19 * 3600L)));
        // 4 unhealthy in the most recent window of 5, against a real 5% baseline - matches
        // RuntimeHealthShiftDetectorTest's independently hand-verified clearly-significant case.
        for (int i = 0; i < 4; i++) {
            shiftedHistory.add(unhealthyObservation(Instant.now().minusSeconds((4 - i) * 60L)));
        }
        // Reverse to match findByProjectIdOrderByObservedAtDesc's real contract (newest first).
        List<ClientRuntimeObservationEntity> newestFirst = new java.util.ArrayList<>(shiftedHistory);
        java.util.Collections.reverse(newestFirst);

        // First call (cadence check) sees nothing yet - observes immediately; second call (post-save
        // shift check) sees the full shifted history including the just-landed observation.
        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId()))
                .thenReturn(List.of())
                .thenReturn(newestFirst);
        when(launcher.launch(any(), any(), any())).thenReturn(new RuntimeLauncherClient.LaunchResult(true, 5000, null, 18080, true, "sha-test"));
        when(launcher.healthcheck(any())).thenReturn(new RuntimeLauncherClient.HealthCheckResult(500, 50, null));
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings, kaizen, mock(com.eneik.production.services.design.DesignDriftMonitorService.class), projects, tasks, mock(ProductCapabilityService.class));

        service.maybeObserve(proj);

        verify(kaizen, times(1)).recordProductRuntimeDefectProposal(
                org.mockito.ArgumentMatchers.eq(proj.getId()), org.mockito.ArgumentMatchers.eq("test-project"),
                any(), any());
    }

    /**
     * 2026-08-11 (live incident, test-forty-third): the exact real scenario - 3 total observations,
     * ALL launch failures, from the very first one. RuntimeHealthShiftDetector.detect() (relative) never
     * fires here (needs 10+ baseline samples); the new absolute test must catch it instead.
     */
    @Test
    void threeConsecutiveLaunchFailuresFromTheStartTriggersTheAbsoluteTestPath() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        var kaizen = mock(com.eneik.production.kaizen.service.KaizenService.class);
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        ProjectEntity proj = project(true);
        proj.setName("test-forty-third");

        List<ClientRuntimeObservationEntity> failedHistory = new java.util.ArrayList<>();
        Instant t0 = Instant.now().minusSeconds(3600);
        for (int i = 0; i < 3; i++) {
            ClientRuntimeObservationEntity o = new ClientRuntimeObservationEntity();
            o.setObservedAt(t0.plusSeconds(i * 60L));
            o.setLaunchSuccess(false);
            failedHistory.add(o);
        }
        List<ClientRuntimeObservationEntity> newestFirst = new java.util.ArrayList<>(failedHistory);
        java.util.Collections.reverse(newestFirst);

        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId()))
                .thenReturn(List.of())
                .thenReturn(newestFirst);
        when(launcher.launch(any(), any(), any())).thenReturn(new RuntimeLauncherClient.LaunchResult(false, 1000, "docker: not found", null, true, "sha-test"));
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings, kaizen, mock(com.eneik.production.services.design.DesignDriftMonitorService.class), projects, tasks, mock(ProductCapabilityService.class));

        service.maybeObserve(proj);

        verify(kaizen, times(1)).recordProductRuntimeDefectProposal(
                org.mockito.ArgumentMatchers.eq(proj.getId()), org.mockito.ArgumentMatchers.eq("test-forty-third"),
                any(), any());
    }

    @Test
    void aStableHistoryAfterTheNewObservationNeverCreatesAKaizenProposal() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        var kaizen = mock(com.eneik.production.kaizen.service.KaizenService.class);
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        ProjectEntity proj = project(true);

        List<ClientRuntimeObservationEntity> stableHistory = new java.util.ArrayList<>();
        Instant t0 = Instant.now().minusSeconds(20L * 24 * 3600);
        for (int i = 0; i < 24; i++) {
            stableHistory.add(healthyObservation(t0.plusSeconds(i * 3600L)));
        }
        List<ClientRuntimeObservationEntity> newestFirst = new java.util.ArrayList<>(stableHistory);
        java.util.Collections.reverse(newestFirst);

        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId()))
                .thenReturn(List.of())
                .thenReturn(newestFirst);
        when(launcher.launch(any(), any(), any())).thenReturn(new RuntimeLauncherClient.LaunchResult(true, 5000, null, 18080, true, "sha-test"));
        when(launcher.healthcheck(any())).thenReturn(new RuntimeLauncherClient.HealthCheckResult(200, 50, null));
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings, kaizen, mock(com.eneik.production.services.design.DesignDriftMonitorService.class), projects, tasks, mock(ProductCapabilityService.class));

        service.maybeObserve(proj);

        verify(kaizen, never()).recordProductRuntimeDefectProposal(any(), any(), any(), any());
    }

    /**
     * 2026-08-11 (bounded live-preview window): the reaper runs on every tick, before the due-ness check,
     * and only tears down a lingering preview once its window has genuinely expired - never eagerly.
     */
    @Test
    void reaperLeavesAStillFreshLivePreviewRunning() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        ProjectEntity proj = project(true);
        proj.setLastRuntimePreviewLaunchedAt(Instant.now().minusSeconds(120));
        proj.setLastRuntimePreviewPort(18080);
        // Recent observation, not yet due for a new one - isolates the reaper's own behavior.
        ClientRuntimeObservationEntity recent = new ClientRuntimeObservationEntity();
        recent.setObservedAt(Instant.now().minusSeconds(60));
        recent.setLaunchSuccess(true);
        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId())).thenReturn(List.of(recent));
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings, mock(com.eneik.production.kaizen.service.KaizenService.class), mock(com.eneik.production.services.design.DesignDriftMonitorService.class), projects, tasks, mock(ProductCapabilityService.class));
        ReflectionTestUtils.setField(service, "livePreviewIdleMinutes", 15L);
        ReflectionTestUtils.setField(service, "baseDelayHours", 24L);
        ReflectionTestUtils.setField(service, "minimumDelayHours", 1L);

        service.maybeObserve(proj);

        verify(launcher, never()).teardown();
        assertTrue(proj.getLastRuntimePreviewLaunchedAt() != null);
    }

    @Test
    void reaperTearsDownAnExpiredLivePreview() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        ProjectEntity proj = project(true);
        proj.setLastRuntimePreviewLaunchedAt(Instant.now().minusSeconds(20 * 60L));
        proj.setLastRuntimePreviewPort(18080);
        ClientRuntimeObservationEntity recent = new ClientRuntimeObservationEntity();
        recent.setObservedAt(Instant.now().minusSeconds(60));
        recent.setLaunchSuccess(true);
        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId())).thenReturn(List.of(recent));
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings, mock(com.eneik.production.kaizen.service.KaizenService.class), mock(com.eneik.production.services.design.DesignDriftMonitorService.class), projects, tasks, mock(ProductCapabilityService.class));
        ReflectionTestUtils.setField(service, "livePreviewIdleMinutes", 15L);
        ReflectionTestUtils.setField(service, "baseDelayHours", 24L);
        ReflectionTestUtils.setField(service, "minimumDelayHours", 1L);

        service.maybeObserve(proj);

        verify(launcher, times(1)).teardown();
        verify(projects, times(1)).save(proj);
        assertTrue(proj.getLastRuntimePreviewLaunchedAt() == null);
        assertTrue(proj.getLastRuntimePreviewPort() == null);
    }

    /**
     * 2026-08-11: the dashboard link (ProductTree.svelte) and a philosophical audit's live-fetch both
     * read this same summarize()/currentLiveUrl() projection - it must reflect the real window, not just
     * "was ever launched successfully."
     */
    @Test
    void summarizeReportsALiveUrlWhileWithinTheWindow() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        ProjectEntity proj = project(true);
        proj.setLastRuntimePreviewLaunchedAt(Instant.now().minusSeconds(60));
        proj.setLastRuntimePreviewPort(18080);
        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId())).thenReturn(List.of());
        when(projects.findById(proj.getId())).thenReturn(java.util.Optional.of(proj));
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings, mock(com.eneik.production.kaizen.service.KaizenService.class), mock(com.eneik.production.services.design.DesignDriftMonitorService.class), projects, tasks, mock(ProductCapabilityService.class));
        ReflectionTestUtils.setField(service, "livePreviewIdleMinutes", 15L);

        var summary = service.summarize(proj.getId());

        org.junit.jupiter.api.Assertions.assertEquals("http://localhost:18080/", summary.liveUrl());
    }

    @Test
    void summarizeReportsNoLiveUrlOnceTheWindowHasExpired() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        ProjectEntity proj = project(true);
        proj.setLastRuntimePreviewLaunchedAt(Instant.now().minusSeconds(20 * 60L));
        proj.setLastRuntimePreviewPort(18080);
        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId())).thenReturn(List.of());
        when(projects.findById(proj.getId())).thenReturn(java.util.Optional.of(proj));
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings, mock(com.eneik.production.kaizen.service.KaizenService.class), mock(com.eneik.production.services.design.DesignDriftMonitorService.class), projects, tasks, mock(ProductCapabilityService.class));
        ReflectionTestUtils.setField(service, "livePreviewIdleMinutes", 15L);

        var summary = service.summarize(proj.getId());

        assertTrue(summary.liveUrl() == null);
    }

    private ClientRuntimeObservationEntity healthyObservation(Instant at) {
        ClientRuntimeObservationEntity o = new ClientRuntimeObservationEntity();
        o.setObservedAt(at);
        o.setLaunchSuccess(true);
        o.setHealthStatusCode(200);
        return o;
    }

    private ClientRuntimeObservationEntity unhealthyObservation(Instant at) {
        ClientRuntimeObservationEntity o = new ClientRuntimeObservationEntity();
        o.setObservedAt(at);
        o.setLaunchSuccess(true);
        o.setHealthStatusCode(500);
        return o;
    }

    // 2026-08-19: an unanswered launch call is a MISSING observation, not a negative one. Before this,
    // RuntimeLauncherClient returned success=false for both "the launcher said the launch failed" and
    // "the launcher never answered", and the second went into the product's own history. Measured live on
    // test-forty-ninth: the 16:42Z launcher timeout moved the posterior from Beta(1,3) to Beta(1,4), which
    // pushed the next check from 7.2 hours to 9.7 - so the more often the instrument failed, the less
    // often the product was tried. Feedback with the wrong sign.

    @Test
    void anUnansweredLaunchIsRecordedAsAnInstrumentFailure() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        ProjectEntity proj = project(true);
        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId())).thenReturn(List.of());
        when(launcher.launch(any(), any(), any())).thenReturn(
                RuntimeLauncherClient.LaunchResult.unobserved("runtime-launcher unreachable: Read timed out"));
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings,
                mock(com.eneik.production.kaizen.service.KaizenService.class),
                mock(com.eneik.production.services.design.DesignDriftMonitorService.class), projects, tasks, mock(ProductCapabilityService.class));

        service.maybeObserve(proj);

        ArgumentCaptor<ClientRuntimeObservationEntity> saved =
                ArgumentCaptor.forClass(ClientRuntimeObservationEntity.class);
        verify(observations).save(saved.capture());
        assertTrue(saved.getValue().isInstrumentFailure(),
                "no answer from the launcher says nothing about the product");
    }

    @Test
    void aLaunchTheLauncherItselfReportsAsFailedIsStillARealObservation() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        ProjectEntity proj = project(true);
        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId())).thenReturn(List.of());
        when(launcher.launch(any(), any(), any())).thenReturn(
                RuntimeLauncherClient.LaunchResult.answered(false, 1200, "docker compose up failed", null, "sha-test"));
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings,
                mock(com.eneik.production.kaizen.service.KaizenService.class),
                mock(com.eneik.production.services.design.DesignDriftMonitorService.class), projects, tasks, mock(ProductCapabilityService.class));

        service.maybeObserve(proj);

        ArgumentCaptor<ClientRuntimeObservationEntity> saved =
                ArgumentCaptor.forClass(ClientRuntimeObservationEntity.class);
        verify(observations).save(saved.capture());
        assertFalse(saved.getValue().isInstrumentFailure(),
                "the launcher answered - this is a genuine negative observation of the product");
    }

    // The cadence clock must run from the last time the product was really looked at. With only an
    // instrument failure on record, the previous real observation is what decides whether a check is due -
    // otherwise a failed instrument silently buys itself another full delay.
    @Test
    void theCadenceClockIgnoresInstrumentFailuresWhenDecidingWhetherACheckIsDue() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        ProjectEntity proj = project(true);

        ClientRuntimeObservationEntity instrumentFault = new ClientRuntimeObservationEntity();
        instrumentFault.setProjectId(proj.getId());
        instrumentFault.setObservedAt(java.time.Instant.now().minusSeconds(60));
        instrumentFault.setLaunchSuccess(false);
        instrumentFault.setInstrumentFailure(true);

        ClientRuntimeObservationEntity realOne = new ClientRuntimeObservationEntity();
        realOne.setProjectId(proj.getId());
        realOne.setObservedAt(java.time.Instant.now().minusSeconds(60 * 60 * 30));
        realOne.setLaunchSuccess(false);

        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId()))
                .thenReturn(List.of(instrumentFault, realOne));
        when(launcher.launch(any(), any(), any())).thenReturn(
                RuntimeLauncherClient.LaunchResult.answered(false, 1200, "docker compose up failed", null, "sha-test"));
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings,
                mock(com.eneik.production.kaizen.service.KaizenService.class),
                mock(com.eneik.production.services.design.DesignDriftMonitorService.class), projects, tasks, mock(ProductCapabilityService.class));

        service.maybeObserve(proj);

        verify(launcher, times(1)).launch(any(), any(), any());
    }


    // 2026-08-20: the posterior is a belief about a specific object, and a merge to main replaces that
    // object. Before this the only thing that could make an observation due was elapsed time, so a fix
    // that had already landed stayed invisible for up to a full delay - measured on test-forty-ninth,
    // where a merged MinIO fix waited while the cadence pointed at 09:11Z the next morning.
    @Test
    void aTaskReachingDoneSinceTheLastObservationPullsTheCheckForward() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        ProjectEntity proj = project(true);

        // Two hours ago: well past the one-hour floor, but far short of the delay a healthy posterior earns.
        ClientRuntimeObservationEntity healthy = new ClientRuntimeObservationEntity();
        healthy.setProjectId(proj.getId());
        healthy.setObservedAt(Instant.now().minusSeconds(2 * 60 * 60));
        healthy.setLaunchSuccess(true);
        healthy.setHealthStatusCode(200);
        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId())).thenReturn(List.of(healthy));
        when(tasks.countByProjectIdAndStatusAndUpdatedAtAfter(any(), any(), any())).thenReturn(3L);
        when(launcher.launch(any(), any(), any())).thenReturn(
                RuntimeLauncherClient.LaunchResult.answered(false, 1000, "docker compose up failed", null, "sha-test"));

        var service = new ClientRuntimeObservabilityService(observations, launcher, settings,
                mock(com.eneik.production.kaizen.service.KaizenService.class),
                mock(com.eneik.production.services.design.DesignDriftMonitorService.class), projects, tasks, mock(ProductCapabilityService.class));
        ReflectionTestUtils.setField(service, "baseDelayHours", 24L);
        ReflectionTestUtils.setField(service, "minimumDelayHours", 1L);
        service.maybeObserve(proj);

        verify(launcher, times(1)).launch(any(), any(), any());
    }

    // The floor still binds. An event may pull the check forward TO the minimum delay, never below it -
    // otherwise a busy merge stream launches the product on every tick, which is the same overproduction
    // the adaptive cadence exists to prevent. The trigger changes when we look, not how often we may.
    @Test
    void aMergeWithinTheFloorDoesNotTriggerAnObservation() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        ProjectEntity proj = project(true);

        ClientRuntimeObservationEntity justNow = new ClientRuntimeObservationEntity();
        justNow.setProjectId(proj.getId());
        justNow.setObservedAt(Instant.now().minusSeconds(120)); // two minutes - inside the one-hour floor
        justNow.setLaunchSuccess(true);
        justNow.setHealthStatusCode(200);
        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId())).thenReturn(List.of(justNow));
        when(tasks.countByProjectIdAndStatusAndUpdatedAtAfter(any(), any(), any())).thenReturn(9L);

        var service = new ClientRuntimeObservabilityService(observations, launcher, settings,
                mock(com.eneik.production.kaizen.service.KaizenService.class),
                mock(com.eneik.production.services.design.DesignDriftMonitorService.class), projects, tasks, mock(ProductCapabilityService.class));
        ReflectionTestUtils.setField(service, "baseDelayHours", 24L);
        ReflectionTestUtils.setField(service, "minimumDelayHours", 1L);
        service.maybeObserve(proj);

        verify(launcher, never()).launch(any(), any(), any());
    }

    // With nothing merged the ordinary timed cadence still governs - the event is an addition to the rule,
    // never a replacement for it.
    @Test
    void withNothingMergedTheTimerStillGoverns() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        ProjectEntity proj = project(true);

        ClientRuntimeObservationEntity healthy = new ClientRuntimeObservationEntity();
        healthy.setProjectId(proj.getId());
        healthy.setObservedAt(Instant.now().minusSeconds(2 * 60 * 60));
        healthy.setLaunchSuccess(true);
        healthy.setHealthStatusCode(200);
        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId())).thenReturn(List.of(healthy));
        when(tasks.countByProjectIdAndStatusAndUpdatedAtAfter(any(), any(), any())).thenReturn(0L);

        var service = new ClientRuntimeObservabilityService(observations, launcher, settings,
                mock(com.eneik.production.kaizen.service.KaizenService.class),
                mock(com.eneik.production.services.design.DesignDriftMonitorService.class), projects, tasks, mock(ProductCapabilityService.class));
        ReflectionTestUtils.setField(service, "baseDelayHours", 24L);
        ReflectionTestUtils.setField(service, "minimumDelayHours", 1L);
        service.maybeObserve(proj);

        verify(launcher, never()).launch(any(), any(), any());
    }


    // ---------------------------------------------------------------------------------------------
    // ACP-103 (plan section 9.3): the cadence clock limits ATTEMPTS, so it is clocked on attempts.
    // Measured refutation: on 2026-08-20 the launcher stopped answering at 11:40 and the observation
    // rate went from 1/hour to 28/hour, because lastRealObservation froze and every tick looked due.
    // ---------------------------------------------------------------------------------------------

    private ClientRuntimeObservationEntity instrumentFailureAt(Instant at) {
        ClientRuntimeObservationEntity row = new ClientRuntimeObservationEntity();
        row.setObservedAt(at);
        row.setLaunchSuccess(false);
        row.setInstrumentFailure(true);
        row.setErrorText("runtime-launcher unreachable: I/O error on POST");
        return row;
    }

    private ClientRuntimeObservationEntity realObservationAt(Instant at, boolean launched) {
        ClientRuntimeObservationEntity row = new ClientRuntimeObservationEntity();
        row.setObservedAt(at);
        row.setLaunchSuccess(launched);
        row.setInstrumentFailure(false);
        return row;
    }

    @Test
    void anUnansweredLauncherCallStillSpendsTheRateLimitersClock() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        ProjectEntity proj = project(true);

        // The exact live shape: the product was last really looked at ten hours ago, and since then every
        // attempt has been an instrument failure - the newest one a minute ago. Clocked on real
        // observations this is long overdue and fires on every tick; clocked on attempts it is not due.
        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId())).thenReturn(List.of(
                instrumentFailureAt(Instant.now().minusSeconds(60)),
                instrumentFailureAt(Instant.now().minusSeconds(180)),
                realObservationAt(Instant.now().minusSeconds(36000), false)));
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings, mock(com.eneik.production.kaizen.service.KaizenService.class), mock(com.eneik.production.services.design.DesignDriftMonitorService.class), projects, tasks, mock(ProductCapabilityService.class));
        ReflectionTestUtils.setField(service, "baseDelayHours", 24L);
        ReflectionTestUtils.setField(service, "minimumDelayHours", 1L);

        service.maybeObserve(proj);

        verify(launcher, never()).launch(any(), any(), any());
    }

    @Test
    void theInstrumentIsRetriedOnceTheFloorHasPassedSinceTheLastAttempt() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        ProjectEntity proj = project(true);

        // Rate limited, never stopped: BetaPosterior's contract forbids ever functionally halting, and
        // this fix must not weaken it. Two hours past the one-hour floor, the attempt is due again.
        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId())).thenReturn(List.of(
                instrumentFailureAt(Instant.now().minusSeconds(7200)),
                realObservationAt(Instant.now().minusSeconds(36000), false)));
        when(launcher.launch(any(), any(), any()))
                .thenReturn(RuntimeLauncherClient.LaunchResult.unobserved("runtime-launcher unreachable"));
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings, mock(com.eneik.production.kaizen.service.KaizenService.class), mock(com.eneik.production.services.design.DesignDriftMonitorService.class), projects, tasks, mock(ProductCapabilityService.class));
        ReflectionTestUtils.setField(service, "baseDelayHours", 24L);
        ReflectionTestUtils.setField(service, "minimumDelayHours", 1L);

        service.maybeObserve(proj);

        verify(launcher).launch(any(), any(), any());
    }

    @Test
    void aRunOfUnansweredLauncherCallsBecomesAFactoryFindingAgainstTheLauncher() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        var kaizen = mock(com.eneik.production.kaizen.service.KaizenService.class);
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        ProjectEntity proj = project(true);

        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId())).thenReturn(List.of());
        when(launcher.launch(any(), any(), any()))
                .thenReturn(RuntimeLauncherClient.LaunchResult.unobserved("runtime-launcher unreachable"));
        when(observations.findByProjectIdOrderByObservedAtDesc(eq(proj.getId()), any(org.springframework.data.domain.Limit.class)))
                .thenReturn(List.of(
                        instrumentFailureAt(Instant.now()),
                        instrumentFailureAt(Instant.now().minusSeconds(3600)),
                        instrumentFailureAt(Instant.now().minusSeconds(7200))));
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings, kaizen, mock(com.eneik.production.services.design.DesignDriftMonitorService.class), projects, tasks, mock(ProductCapabilityService.class));
        ReflectionTestUtils.setField(service, "baseDelayHours", 24L);
        ReflectionTestUtils.setField(service, "minimumDelayHours", 1L);
        ReflectionTestUtils.setField(service, "instrumentOutageThreshold", 3);

        service.maybeObserve(proj);

        // Factory level, not product: the launcher is this factory's instrument. Keyed to the launcher so
        // the Kaizen read path holds one standing finding rather than one per failed call.
        verify(kaizen).recordSystemicDefectProposal(isNull(), eq("Global"), eq("runtime-launcher"),
                anyString(), anyString());
        // And the product's own accounting is untouched - nothing was learned about the product.
        verify(kaizen, never()).recordProductRuntimeDefectProposal(any(), any(), anyString(), anyString());
    }

    @Test
    void asingleUnansweredCallIsABlipAndIsNotReported() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        var kaizen = mock(com.eneik.production.kaizen.service.KaizenService.class);
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        ProjectEntity proj = project(true);

        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId())).thenReturn(List.of());
        when(launcher.launch(any(), any(), any()))
                .thenReturn(RuntimeLauncherClient.LaunchResult.unobserved("runtime-launcher unreachable"));
        when(observations.findByProjectIdOrderByObservedAtDesc(eq(proj.getId()), any(org.springframework.data.domain.Limit.class)))
                .thenReturn(List.of(
                        instrumentFailureAt(Instant.now()),
                        realObservationAt(Instant.now().minusSeconds(3600), true),
                        instrumentFailureAt(Instant.now().minusSeconds(7200))));
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings, kaizen, mock(com.eneik.production.services.design.DesignDriftMonitorService.class), projects, tasks, mock(ProductCapabilityService.class));
        ReflectionTestUtils.setField(service, "baseDelayHours", 24L);
        ReflectionTestUtils.setField(service, "minimumDelayHours", 1L);
        ReflectionTestUtils.setField(service, "instrumentOutageThreshold", 3);

        service.maybeObserve(proj);

        verify(kaizen, never()).recordSystemicDefectProposal(any(), anyString(), anyString(), anyString(), anyString());
    }

    // ---------------------------------------------------------------------------------------------
    // V109: the posterior is a belief about an artifact. De Finetti licenses the Beta-Bernoulli model
    // only over an exchangeable sequence, and between merges the sequence is constant, not exchangeable.
    // ---------------------------------------------------------------------------------------------

    private ClientRuntimeObservationEntity unhealthyOf(String sha, long secondsAgo) {
        ClientRuntimeObservationEntity row = new ClientRuntimeObservationEntity();
        row.setObservedAt(Instant.now().minusSeconds(secondsAgo));
        row.setLaunchSuccess(true);
        row.setHealthStatusCode(null);
        row.setInstrumentFailure(false);
        row.setCommitSha(sha);
        return row;
    }

    private ClientRuntimeObservabilityService summarizingService(ClientRuntimeObservationRepository observations) {
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(com.eneik.production.repositories.ProjectRepository.class);
        var tasks = mock(com.eneik.production.repositories.TaskRepository.class);
        return new ClientRuntimeObservabilityService(observations, launcher, settings, mock(com.eneik.production.kaizen.service.KaizenService.class), mock(com.eneik.production.services.design.DesignDriftMonitorService.class), projects, tasks, mock(ProductCapabilityService.class));
    }

    @Test
    void repeatedReadingsOfOneUnchangedArtifactAreOneDrawNotMany() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        UUID projectId = UUID.randomUUID();
        // Newest first, as the repository returns them: four readings of commit A, then one of commit B.
        when(observations.findByProjectIdOrderByObservedAtDesc(projectId)).thenReturn(List.of(
                unhealthyOf("bbb", 100),
                unhealthyOf("aaa", 200),
                unhealthyOf("aaa", 300),
                unhealthyOf("aaa", 400),
                unhealthyOf("aaa", 500)));

        var summary = summarizingService(observations).summarize(projectId);

        // Two distinct artifacts observed, both unhealthy -> Beta(1,3), mean 0.25.
        // Counting all five readings would give Beta(1,6), mean ~0.1429 - a belief made confident by
        // repetition of one fact rather than by evidence.
        assertEquals(0.25, summary.posteriorMean(), 1e-9);
        // The row count is untouched: five observations really did happen, and the summary still says so.
        assertEquals(5, summary.observationCount());
    }

    @Test
    void observationsWithAnUnknownArtifactAreNeverCollapsed() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        UUID projectId = UUID.randomUUID();
        // Every row written before V109 carries a null commit. Treating them as one artifact would be the
        // exact substitution ACP-102 forbids: nothing recorded which artifact they observed.
        when(observations.findByProjectIdOrderByObservedAtDesc(projectId)).thenReturn(List.of(
                unhealthyOf(null, 100), unhealthyOf(null, 200), unhealthyOf(null, 300)));

        var summary = summarizingService(observations).summarize(projectId);

        assertEquals(1.0 / 5.0, summary.posteriorMean(), 1e-9); // Beta(1,4): three separate draws
    }

    @Test
    void anArtifactObservedAgainAfterAnotherOneIsANewDraw() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        UUID projectId = UUID.randomUUID();
        // A, then B, then A again. Only CONSECUTIVE readings of one artifact are the same fact; a return
        // to a previous commit is a genuinely new observation of it at a new time.
        when(observations.findByProjectIdOrderByObservedAtDesc(projectId)).thenReturn(List.of(
                unhealthyOf("aaa", 100), unhealthyOf("bbb", 200), unhealthyOf("aaa", 300)));

        var summary = summarizingService(observations).summarize(projectId);

        assertEquals(1.0 / 5.0, summary.posteriorMean(), 1e-9); // Beta(1,4): three draws
    }
}
