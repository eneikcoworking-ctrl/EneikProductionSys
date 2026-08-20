package com.eneik.production.services.runtime;

import com.eneik.production.models.persistence.CapabilityObservationEntity;
import com.eneik.production.models.persistence.FeatureEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.repositories.CapabilityObservationRepository;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.services.github.GitHubPullRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductCapabilityServiceTest {

    private static final String CONTRACT = """
            openapi: 3.0.0
            info:
              title: Epidemiological Protocols
            paths:
              /protocols:
                get:
                  summary: List protocols
                post:
                  summary: Create
              /materials:
                get:
                  summary: List materials
              /materials/{id}:
                get:
                  summary: One material
              /admin/purge:
                delete:
                  summary: Purge
            components:
              schemas: {}
            """;

    // The declared set must come from what the product asserts, and only from operations that can be
    // checked without inventing anything: a POST needs a body and a templated path needs a value, and a
    // capability we cannot check without inventing something is a guess, not evidence.
    @Test
    void readsOnlyTheGetRoutesTheContractDeclares() {
        List<String> routes = ProductCapabilityService.getRoutesOf(CONTRACT);
        assertEquals(List.of("/protocols", "/materials", "/materials/{id}"), routes);
    }

    @Test
    void anEmptyOrAbsentContractDeclaresNothing() {
        assertTrue(ProductCapabilityService.getRoutesOf("").isEmpty());
        assertTrue(ProductCapabilityService.getRoutesOf(null).isEmpty());
        assertTrue(ProductCapabilityService.getRoutesOf("openapi: 3.0.0\ninfo:\n  title: x\n").isEmpty());
    }

    private static ProductCapabilityService serviceWith(FeatureRepository features,
                                                         GitHubPullRequestService github,
                                                         RuntimeLauncherClient launcher,
                                                         CapabilityObservationRepository observations) {
        ProductCapabilityService service = new ProductCapabilityService(features, github, launcher, observations);
        ReflectionTestUtils.setField(service, "confidenceThreshold", 0.5);
        ReflectionTestUtils.setField(service, "maxCapabilitiesPerObservation", 40);
        return service;
    }

    private static ProjectEntity project() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setDefaultBranch("main");
        return project;
    }

    private static FeatureEntity feature(String title) {
        FeatureEntity feature = new FeatureEntity();
        feature.setProjectId(UUID.randomUUID());
        feature.setTitle(title);
        return feature;
    }

    // A templated path is skipped rather than probed with an invented id.
    @Test
    void probesEveryConcreteDeclaredRouteAndSkipsTemplatedOnes() {
        var features = mock(FeatureRepository.class);
        var github = mock(GitHubPullRequestService.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var observations = mock(CapabilityObservationRepository.class);
        ProjectEntity project = project();

        when(features.findByProjectId(project.getId())).thenReturn(List.of(feature("Protocols API")));
        when(github.fetchFileContent(any(), any(), any())).thenReturn(Optional.of(CONTRACT));
        when(launcher.fetchHtml(any())).thenReturn(new RuntimeLauncherClient.FetchResult(200, "ok", 5, null));

        int satisfied = serviceWith(features, github, launcher, observations).probeAll(project, "http://localhost:18080");

        assertEquals(2, satisfied);
        verify(launcher).fetchHtml("http://localhost:18080/protocols");
        verify(launcher).fetchHtml("http://localhost:18080/materials");
        verify(launcher, never()).fetchHtml("http://localhost:18080/materials/{id}");
    }

    // A feature whose contract does not exist is excluded from the denominator, and that exclusion is
    // visible rather than absorbed - Charter invariant 8.
    @Test
    void aFeatureWithNoDeclaredContractIsNotCounted() {
        var features = mock(FeatureRepository.class);
        var github = mock(GitHubPullRequestService.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var observations = mock(CapabilityObservationRepository.class);
        ProjectEntity project = project();

        when(features.findByProjectId(project.getId())).thenReturn(List.of(feature("Undeclared Feature")));
        when(github.fetchFileContent(any(), any(), any())).thenReturn(Optional.empty());

        var service = serviceWith(features, github, launcher, observations);
        assertTrue(service.declaredCapabilities(project).isEmpty());
        assertEquals(0, service.probeAll(project, "http://localhost:18080"));
        verify(launcher, never()).fetchHtml(any());
    }

    private static CapabilityObservationEntity obs(UUID projectId, String key, boolean satisfied) {
        CapabilityObservationEntity row = new CapabilityObservationEntity();
        row.setProjectId(projectId);
        row.setCapabilityKey(key);
        row.setSatisfied(satisfied);
        return row;
    }

    // The mean rewards ignorance: one success would score 0.67 having proved nothing. The lower bound
    // makes confidence something evidence has to earn, so a single success is not yet a working capability.
    @Test
    void oneSuccessDoesNotYetMakeACapabilityCount() {
        var observations = mock(CapabilityObservationRepository.class);
        UUID projectId = UUID.randomUUID();
        when(observations.findByProjectIdOrderByObservedAtDesc(projectId))
                .thenReturn(List.of(obs(projectId, "GET /protocols", true)));

        var value = serviceWith(mock(FeatureRepository.class), mock(GitHubPullRequestService.class),
                mock(RuntimeLauncherClient.class), observations).currentValue(projectId);

        assertEquals(1, value.declaredCapabilities());
        assertEquals(0, value.workingCapabilities());
    }

    @Test
    void aSustainedRunOfSuccessesEarnsTheCapabilityItsPlaceInTheCount() {
        var observations = mock(CapabilityObservationRepository.class);
        UUID projectId = UUID.randomUUID();
        List<CapabilityObservationEntity> rows = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            rows.add(obs(projectId, "GET /protocols", true));
        }
        when(observations.findByProjectIdOrderByObservedAtDesc(projectId)).thenReturn(rows);

        var value = serviceWith(mock(FeatureRepository.class), mock(GitHubPullRequestService.class),
                mock(RuntimeLauncherClient.class), observations).currentValue(projectId);

        assertEquals(1, value.workingCapabilities());
    }

    // Popper: the measure must be able to fall. A capability that stops working leaves the count again.
    @Test
    void aCapabilityThatStopsWorkingLeavesTheCountAgain() {
        var observations = mock(CapabilityObservationRepository.class);
        UUID projectId = UUID.randomUUID();
        List<CapabilityObservationEntity> rows = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            rows.add(obs(projectId, "GET /protocols", true));
        }
        for (int i = 0; i < 8; i++) {
            rows.add(obs(projectId, "GET /protocols", false));
        }
        when(observations.findByProjectIdOrderByObservedAtDesc(projectId)).thenReturn(rows);

        var value = serviceWith(mock(FeatureRepository.class), mock(GitHubPullRequestService.class),
                mock(RuntimeLauncherClient.class), observations).currentValue(projectId);

        assertEquals(0, value.workingCapabilities());
    }

    // One observation of one capability is a Six Sigma opportunity; a capability that did not work is a
    // defect. This is the product-layer population SixSigmaAuditService never had.
    @Test
    void everyObservationIsAnOpportunityAndEveryFailureADefect() {
        var observations = mock(CapabilityObservationRepository.class);
        UUID projectId = UUID.randomUUID();
        when(observations.findByProjectIdOrderByObservedAtDesc(projectId)).thenReturn(List.of(
                obs(projectId, "GET /protocols", true),
                obs(projectId, "GET /protocols", false),
                obs(projectId, "GET /materials", true),
                obs(projectId, "GET /materials", true)));

        var value = serviceWith(mock(FeatureRepository.class), mock(GitHubPullRequestService.class),
                mock(RuntimeLauncherClient.class), observations).currentValue(projectId);

        assertEquals(4, value.opportunities());
        assertEquals(1, value.defects());
        assertEquals(250_000.0, value.dpmo(), 0.001);
    }

    @Test
    void aProductNeverProbedHasNoDefectsAndNoValue() {
        var observations = mock(CapabilityObservationRepository.class);
        UUID projectId = UUID.randomUUID();
        when(observations.findByProjectIdOrderByObservedAtDesc(projectId)).thenReturn(List.of());

        var value = serviceWith(mock(FeatureRepository.class), mock(GitHubPullRequestService.class),
                mock(RuntimeLauncherClient.class), observations).currentValue(projectId);

        assertEquals(0, value.workingCapabilities());
        assertEquals(0, value.opportunities());
        assertEquals(0.0, value.dpmo(), 0.001);
    }
}
