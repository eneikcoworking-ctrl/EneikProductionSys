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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(false);
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings, mock(com.eneik.production.kaizen.service.KaizenService.class), mock(com.eneik.production.services.design.DesignDriftMonitorService.class));

        service.maybeObserve(project(true));

        verify(launcher, never()).launch(any(), any(), any());
        verify(observations, never()).save(any());
    }

    @Test
    void doesNothingWhenPhase0LaunchabilityHasNotBeenCheckedYet() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings, mock(com.eneik.production.kaizen.service.KaizenService.class), mock(com.eneik.production.services.design.DesignDriftMonitorService.class));

        service.maybeObserve(project(false));

        verify(launcher, never()).launch(any(), any(), any());
    }

    @Test
    void observesImmediatelyWhenNoHistoryExistsYet() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        ProjectEntity proj = project(true);
        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId())).thenReturn(List.of());
        when(launcher.launch(any(), any(), any())).thenReturn(new RuntimeLauncherClient.LaunchResult(true, 5000, null));
        when(launcher.healthcheck(any())).thenReturn(new RuntimeLauncherClient.HealthCheckResult(200, 50, null));
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings, mock(com.eneik.production.kaizen.service.KaizenService.class), mock(com.eneik.production.services.design.DesignDriftMonitorService.class));
        ReflectionTestUtils.setField(service, "baseDelayHours", 24L);
        ReflectionTestUtils.setField(service, "minimumDelayHours", 1L);

        service.maybeObserve(proj);

        verify(launcher, times(1)).launch(proj.getRepositoryUrl(), "main", "test-project");
        verify(launcher, times(1)).teardown();
        verify(observations, times(1)).save(any(ClientRuntimeObservationEntity.class));
    }

    @Test
    void skipsWhenNotYetDuePerTheAdaptiveCadenceFormula() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        ProjectEntity proj = project(true);

        // One single recent success just 5 minutes ago: with only 1 observation, the credible interval
        // is still wide (~0.28 - see BetaPosteriorTest), so the next check with a 24h base delay is
        // still hours away - definitely not due after only 5 minutes.
        ClientRuntimeObservationEntity recent = new ClientRuntimeObservationEntity();
        recent.setObservedAt(Instant.now().minusSeconds(300));
        recent.setLaunchSuccess(true);
        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId())).thenReturn(List.of(recent));
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings, mock(com.eneik.production.kaizen.service.KaizenService.class), mock(com.eneik.production.services.design.DesignDriftMonitorService.class));
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
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        ProjectEntity proj = project(true);

        // One observation, but a very long time ago - even a wide (~0.95, uninformative-prior-level)
        // interval times a 24h base delay is nowhere near this many days.
        ClientRuntimeObservationEntity old = new ClientRuntimeObservationEntity();
        old.setObservedAt(Instant.now().minusSeconds(30L * 24 * 3600));
        old.setLaunchSuccess(true);
        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId())).thenReturn(List.of(old));
        when(launcher.launch(any(), any(), any())).thenReturn(new RuntimeLauncherClient.LaunchResult(true, 5000, null));
        when(launcher.healthcheck(any())).thenReturn(new RuntimeLauncherClient.HealthCheckResult(200, 50, null));
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings, mock(com.eneik.production.kaizen.service.KaizenService.class), mock(com.eneik.production.services.design.DesignDriftMonitorService.class));
        ReflectionTestUtils.setField(service, "baseDelayHours", 24L);
        ReflectionTestUtils.setField(service, "minimumDelayHours", 1L);

        service.maybeObserve(proj);

        verify(launcher, times(1)).launch(any(), any(), any());
    }

    @Test
    void aFailedLaunchIsRecordedAsFailureAndNeverAttemptsAHealthCheck() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        ProjectEntity proj = project(true);
        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId())).thenReturn(List.of());
        when(launcher.launch(any(), any(), any())).thenReturn(new RuntimeLauncherClient.LaunchResult(false, 1200, "docker compose up failed"));
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings, mock(com.eneik.production.kaizen.service.KaizenService.class), mock(com.eneik.production.services.design.DesignDriftMonitorService.class));

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
        when(launcher.launch(any(), any(), any())).thenReturn(new RuntimeLauncherClient.LaunchResult(true, 5000, null));
        when(launcher.healthcheck(any())).thenReturn(new RuntimeLauncherClient.HealthCheckResult(500, 50, null));
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings, kaizen, mock(com.eneik.production.services.design.DesignDriftMonitorService.class));

        service.maybeObserve(proj);

        verify(kaizen, times(1)).recordProductRuntimeDefectProposal(
                org.mockito.ArgumentMatchers.eq(proj.getId()), org.mockito.ArgumentMatchers.eq("test-project"),
                any(), any());
    }

    @Test
    void aStableHistoryAfterTheNewObservationNeverCreatesAKaizenProposal() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
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
        when(launcher.launch(any(), any(), any())).thenReturn(new RuntimeLauncherClient.LaunchResult(true, 5000, null));
        when(launcher.healthcheck(any())).thenReturn(new RuntimeLauncherClient.HealthCheckResult(200, 50, null));
        var service = new ClientRuntimeObservabilityService(observations, launcher, settings, kaizen, mock(com.eneik.production.services.design.DesignDriftMonitorService.class));

        service.maybeObserve(proj);

        verify(kaizen, never()).recordProductRuntimeDefectProposal(any(), any(), any(), any());
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
}
