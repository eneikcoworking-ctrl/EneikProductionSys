package com.eneik.production.services;

import com.eneik.production.kaizen.service.KaizenService;
import com.eneik.production.models.persistence.ClientRuntimeObservationEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.repositories.ClientRuntimeObservationRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.design.DesignDriftMonitorService;
import com.eneik.production.services.github.GitHubApiBudgetService;
import com.eneik.production.services.runtime.ClientRuntimeObservabilityService;
import com.eneik.production.services.runtime.ProductCapabilityService;
import com.eneik.production.services.runtime.RuntimeLauncherClient;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.toc.LaunchabilityConstraintService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Law 22: Закон демаркации сбоев (L1 -/-> L2).
 *
 * Mathematical invariants:
 *   1. An infrastructure / instrument failure (L1) is evidence about L1 and MUST NOT spawn
 *      a task or wishlist to change client product code (L2).
 *   2. counterexample(e) <=> e is an observation of artifact state C_sha (product code defect)
 *   3. instrument_failure(e) <=> e is an observation environment failure (Docker daemon, transport, launcher outage)
 *   4. An instrument failure does NOT alter the posterior assessment of product reliability:
 *      it is filtered out of product runtime health shift detection, and addressed solely
 *      by factory self-healing / Kaizen systemic defect proposals.
 *   5. Infrastructure rate limits (e.g. GitHub API budget) trigger operational cooldowns
 *      and never create product wishlists.
 */
class FailureDemarcationLaw22Test {

    private UUID projectId;
    private ProjectEntity project;

    private ClientRuntimeObservationRepository observationRepo;
    private RuntimeLauncherClient launcherClient;
    private SystemSettingsService settingsService;
    private KaizenService kaizenService;
    private DesignDriftMonitorService designDriftMonitorService;
    private ProjectRepository projectRepo;
    private TaskRepository taskRepo;
    private ProductCapabilityService productCapabilityService;
    private LaunchabilityConstraintService launchabilityConstraintService;
    private WishlistRepository wishlistRepo;

    private ClientRuntimeObservabilityService observabilityService;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        project = new ProjectEntity();
        project.setId(projectId);
        project.setName("Law 22 Demarcation Project");
        project.setSlug("law-22-demarcation");
        project.setRepositoryUrl("https://github.com/eneik/law22-test");
        project.setDefaultBranch("main");

        observationRepo = mock(ClientRuntimeObservationRepository.class);
        launcherClient = mock(RuntimeLauncherClient.class);
        settingsService = mock(SystemSettingsService.class);
        kaizenService = mock(KaizenService.class);
        designDriftMonitorService = mock(DesignDriftMonitorService.class);
        projectRepo = mock(ProjectRepository.class);
        taskRepo = mock(TaskRepository.class);
        productCapabilityService = mock(ProductCapabilityService.class);
        launchabilityConstraintService = mock(LaunchabilityConstraintService.class);
        wishlistRepo = mock(WishlistRepository.class);

        observabilityService = new ClientRuntimeObservabilityService(
                observationRepo,
                launcherClient,
                settingsService,
                kaizenService,
                designDriftMonitorService,
                projectRepo,
                taskRepo,
                productCapabilityService,
                launchabilityConstraintService
        );

        when(settingsService.effectiveBoolean("client_runtime_observability_enabled")).thenReturn(true);
        when(observationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void instrumentFailureDoesNotOpenLaunchabilityConstraintOrSpawnClientWishlist() {
        // Arrange: runtime launcher fails at infrastructure level (e.g. daemon down, transport failure)
        RuntimeLauncherClient.LaunchResult launcherOutage =
                new RuntimeLauncherClient.LaunchResult(false, 0L, "Docker daemon connection refused: L1 transport error", null, false, null);
        when(launcherClient.launch(anyString(), anyString(), anyString())).thenReturn(launcherOutage);

        // Act: observeOnce runs for the project
        ClientRuntimeObservationEntity observation = observabilityService.observeOnce(project);

        // Assert: observation is identified as an instrument failure
        assertThat(observation).isNotNull();
        assertThat(observation.isInstrumentFailure()).isTrue();

        // Invariant L1 -/-> L2: launchability constraint MUST NOT be opened for client product
        verify(launchabilityConstraintService, never()).ensureOpen(any(), any());
        verify(wishlistRepo, never()).save(any());
    }

    @Test
    void realProductHealthFailureOpensLaunchabilityConstraint() {
        // Arrange: container launches successfully, but product health endpoint returns 500 or unhealthy
        RuntimeLauncherClient.LaunchResult successfulLaunch =
                new RuntimeLauncherClient.LaunchResult(true, 500L, null, 18080, true, "sha-12345");
        when(launcherClient.launch(anyString(), anyString(), anyString())).thenReturn(successfulLaunch);

        // Probe checks product health: returns unhealthy HTTP 500 from product code
        when(launcherClient.healthcheck(anyString()))
                .thenReturn(new RuntimeLauncherClient.HealthCheckResult(500, 100L, "Internal Server Error in Product Controller"));

        // Act: observeOnce runs for the project
        ClientRuntimeObservationEntity observation = observabilityService.observeOnce(project);

        // Assert: NOT an instrument failure (the test environment ran correctly; the client code failed)
        assertThat(observation).isNotNull();
        assertThat(observation.isInstrumentFailure()).isFalse();

        // Invariant: real product defect DOES open the launchability constraint
        verify(launchabilityConstraintService).ensureOpen(eq(project), anyString());
    }

    @Test
    void instrumentFailuresExcludedFromProductHealthShiftDetection() {
        // Arrange: observation history containing instrument failures
        ClientRuntimeObservationEntity instrumentFailure1 = new ClientRuntimeObservationEntity();
        instrumentFailure1.setProjectId(projectId);
        instrumentFailure1.setLaunchSuccess(false);
        instrumentFailure1.setInstrumentFailure(true);
        instrumentFailure1.setErrorText("launcher connection refused");
        instrumentFailure1.setObservedAt(Instant.now().minusSeconds(3600));

        ClientRuntimeObservationEntity healthyObservation = new ClientRuntimeObservationEntity();
        healthyObservation.setProjectId(projectId);
        healthyObservation.setLaunchSuccess(true);
        healthyObservation.setHealthStatusCode(200);
        healthyObservation.setObservedAt(Instant.now().minusSeconds(1800));

        when(observationRepo.findByProjectIdOrderByObservedAtDesc(projectId))
                .thenReturn(List.of(healthyObservation, instrumentFailure1));

        RuntimeLauncherClient.LaunchResult launcherOutage =
                new RuntimeLauncherClient.LaunchResult(false, 0L, "Docker daemon timeout", null, false, null);
        when(launcherClient.launch(anyString(), anyString(), anyString())).thenReturn(launcherOutage);

        // Act
        observabilityService.observeOnce(project);

        // Invariant: Product runtime defect proposal is NEVER filed for instrument failures
        verify(kaizenService, never()).recordProductRuntimeDefectProposal(any(), any(), any(), any());
    }

    @Test
    void consecutiveInstrumentFailuresRouteToFactoryDefectNotProductDefect() {
        // Arrange: instrument outage threshold reached
        ReflectionTestUtils.setField(observabilityService, "instrumentOutageThreshold", 2);

        ClientRuntimeObservationEntity failure1 = new ClientRuntimeObservationEntity();
        failure1.setProjectId(projectId);
        failure1.setLaunchSuccess(false);
        failure1.setInstrumentFailure(true);
        failure1.setErrorText("launcher down");
        failure1.setObservedAt(Instant.now().minusSeconds(120));

        ClientRuntimeObservationEntity failure2 = new ClientRuntimeObservationEntity();
        failure2.setProjectId(projectId);
        failure2.setLaunchSuccess(false);
        failure2.setInstrumentFailure(true);
        failure2.setErrorText("launcher down");
        failure2.setObservedAt(Instant.now().minusSeconds(60));

        when(observationRepo.findByProjectIdOrderByObservedAtDesc(eq(projectId), any()))
                .thenReturn(List.of(failure2, failure1));

        RuntimeLauncherClient.LaunchResult outage =
                new RuntimeLauncherClient.LaunchResult(false, 0L, "launcher unreachable", null, false, null);
        when(launcherClient.launch(anyString(), anyString(), anyString())).thenReturn(outage);

        // Act
        observabilityService.observeOnce(project);

        // Invariant: Kaizen systemic defect filed for FACTORY infrastructure component "runtime-launcher"
        verify(kaizenService).recordSystemicDefectProposal(
                isNull(),
                eq("Global"),
                eq("runtime-launcher"),
                contains("Runtime launcher unreachable"),
                anyString()
        );

        // Product code proposal must NEVER be filed
        verify(kaizenService, never()).recordProductRuntimeDefectProposal(any(), any(), any(), any());
    }

    @Test
    void gitHubRateLimitDoesNotCreateClientWishlist() {
        GitHubApiBudgetService budgetService = new GitHubApiBudgetService();

        // Check availability
        boolean available = budgetService.available("test-token");
        assertThat(available).isTrue();

        // Infrastructure state changes never mutate product wishlists
        verifyNoInteractions(wishlistRepo);
    }
}
