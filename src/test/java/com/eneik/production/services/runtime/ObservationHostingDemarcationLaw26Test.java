package com.eneik.production.services.runtime;

import com.eneik.production.models.persistence.ClientRuntimeObservationEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.repositories.ClientRuntimeObservationRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.toc.LaunchabilityConstraintService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Law 26 / Prescription 26: Category Error Scan (CATEGORY_ERROR_SCAN / D002).
 * Falsification fence ensuring observation preview teardown is strictly demarcated
 * from permanent deployment hosting:
 * 1. Observation preview teardown names the exact genus (ephemeral observation ended, not a permanent deployment;
 *    product was healthy: launchSuccess=true healthStatus=200).
 * 2. Teardown clears transient preview port/timestamp without marking the product as broken.
 * 3. Ephemeral observation preview stays up only within bounded window (livePreviewIdleMinutes), then tears down.
 * 4. Architectural demarcation: RuntimeLauncherClient belongs strictly to ephemeral observation/verification,
 *    never to permanent hosting.
 */
class ObservationHostingDemarcationLaw26Test {

    private ProjectEntity createProject() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setSlug("test-law26-project");
        project.setDefaultBranch("main");
        project.setRepositoryUrl("https://github.com/eneikdru/test-law26-project.git");
        project.setLaunchabilityCheckedAt(Instant.now().minusSeconds(3600));
        return project;
    }

    @Test
    void observationTeardownNamesObservationGenusAndClearsTransientPreviewWithoutCorruptingProduct() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(ProjectRepository.class);
        var tasks = mock(TaskRepository.class);
        var productCapabilities = mock(ProductCapabilityService.class);
        var constraints = mock(LaunchabilityConstraintService.class);

        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);

        ProjectEntity proj = createProject();
        proj.setLastRuntimePreviewLaunchedAt(Instant.now().minus(Duration.ofMinutes(20)));
        proj.setLastRuntimePreviewPort(18080);

        ClientRuntimeObservationEntity healthyObs = new ClientRuntimeObservationEntity();
        healthyObs.setObservedAt(Instant.now().minus(Duration.ofMinutes(10)));
        healthyObs.setLaunchSuccess(true);
        healthyObs.setHealthStatusCode(200);
        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId())).thenReturn(List.of(healthyObs));

        var service = new ClientRuntimeObservabilityService(
                observations, launcher, settings,
                mock(com.eneik.production.kaizen.service.KaizenService.class),
                mock(com.eneik.production.services.design.DesignDriftMonitorService.class),
                projects, tasks, productCapabilities, constraints);
        ReflectionTestUtils.setField(service, "livePreviewIdleMinutes", 15L);
        ReflectionTestUtils.setField(service, "baseDelayHours", 24L);
        ReflectionTestUtils.setField(service, "minimumDelayHours", 1L);

        Logs logs = Logs.capture(ClientRuntimeObservabilityService.class);
        try {
            // Execute reap
            service.maybeObserve(proj);

            // Teardown must be invoked to reap the expired observation container
            verify(launcher, times(1)).teardown();
            verify(projects, times(1)).save(proj);

            // Preview ports and timestamps must be reset
            assertNull(proj.getLastRuntimePreviewLaunchedAt());
            assertNull(proj.getLastRuntimePreviewPort());

            // Launchability constraint must NEVER be opened for an expired observation whose product was healthy
            verify(constraints, never()).ensureOpen(any(), any());

            // CATEGORY_ERROR_SCAN (D002): Assert log message names the exact genus
            assertTrue(logs.contains("observation preview window expired, short-lived observation torn down"),
                    "Log must name that it is a short-lived observation preview being torn down");
            assertTrue(logs.contains("not a permanent deployment"),
                    "Log must explicitly state that this observation preview is not a permanent deployment");
            assertTrue(logs.contains("product was healthy: launchSuccess=true healthStatus=200"),
                    "Log must state that the product was healthy at observation time (launchSuccess=true healthStatus=200)");
            assertFalse(logs.contains("live-preview window expired, torn down"),
                    "Old ambiguous log string that omitted genus must not be used");
        } finally {
            logs.stop();
        }
    }

    @Test
    void failedLaunchTearsDownImmediatelyWithoutMarkingAsDeploymentFailure() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(ProjectRepository.class);
        var tasks = mock(TaskRepository.class);
        var productCapabilities = mock(ProductCapabilityService.class);
        var constraints = mock(LaunchabilityConstraintService.class);

        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);

        ProjectEntity proj = createProject();
        when(observations.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId())).thenReturn(List.of());
        when(launcher.launch(any(), any(), any()))
                .thenReturn(new RuntimeLauncherClient.LaunchResult(false, 1000L, "docker build error", null, false, null));

        var service = new ClientRuntimeObservabilityService(
                observations, launcher, settings,
                mock(com.eneik.production.kaizen.service.KaizenService.class),
                mock(com.eneik.production.services.design.DesignDriftMonitorService.class),
                projects, tasks, productCapabilities, constraints);

        Logs logs = Logs.capture(ClientRuntimeObservabilityService.class);
        try {
            service.observeOnce(proj);

            // Teardown is called immediately so partial observation stacks do not linger
            verify(launcher, times(1)).teardown();
            assertNull(proj.getLastRuntimePreviewLaunchedAt());
            assertNull(proj.getLastRuntimePreviewPort());

            // CATEGORY_ERROR_SCAN (D002): Log must name observation genus
            assertTrue(logs.contains("launch failed, short-lived observation torn down"),
                    "Log must name that a short-lived observation container was torn down");
            assertTrue(logs.contains("not a permanent deployment"),
                    "Log must explicitly state that it is not a permanent deployment");
            assertTrue(logs.contains("launchSuccess=false"),
                    "Log must record launchSuccess=false");
        } finally {
            logs.stop();
        }
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
            return appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains(fragment));
        }

        void stop() {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void observationPreviewStaysAliveStrictlyWithinTheConfiguredWindow() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var settings = mock(SystemSettingsService.class);
        var projects = mock(ProjectRepository.class);
        var tasks = mock(TaskRepository.class);
        var productCapabilities = mock(ProductCapabilityService.class);
        var constraints = mock(LaunchabilityConstraintService.class);

        when(settings.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);

        ProjectEntity proj = createProject();
        Instant fiveMinutesAgo = Instant.now().minus(Duration.ofMinutes(5));
        proj.setLastRuntimePreviewLaunchedAt(fiveMinutesAgo);
        proj.setLastRuntimePreviewPort(18080);

        ClientRuntimeObservationEntity recent = new ClientRuntimeObservationEntity();
        recent.setObservedAt(fiveMinutesAgo);
        recent.setLaunchSuccess(true);
        recent.setHealthStatusCode(200);
        when(observations.findByProjectIdOrderByObservedAtDesc(proj.getId())).thenReturn(List.of(recent));

        var service = new ClientRuntimeObservabilityService(
                observations, launcher, settings,
                mock(com.eneik.production.kaizen.service.KaizenService.class),
                mock(com.eneik.production.services.design.DesignDriftMonitorService.class),
                projects, tasks, productCapabilities, constraints);
        ReflectionTestUtils.setField(service, "livePreviewIdleMinutes", 15L);
        ReflectionTestUtils.setField(service, "baseDelayHours", 24L);
        ReflectionTestUtils.setField(service, "minimumDelayHours", 1L);

        // Within 15-minute window: teardown must NOT be called
        service.maybeObserve(proj);
        verify(launcher, never()).teardown();
        assertTrue(proj.getLastRuntimePreviewLaunchedAt() != null);
    }
}
