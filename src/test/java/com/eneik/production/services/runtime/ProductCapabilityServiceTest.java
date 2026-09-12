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
import java.util.Set;
import java.util.UUID;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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

        when(github.listDirectoryFiles(project, "main", "docs/contracts"))
                .thenReturn(Optional.of(Set.of("protocols.openapi.yaml")));
        when(github.fetchFileContent(project, "main", "docs/contracts/protocols.openapi.yaml"))
                .thenReturn(Optional.of(CONTRACT));
        when(launcher.fetchHtml(any())).thenReturn(new RuntimeLauncherClient.FetchResult(200, "ok", 5, null));

        int satisfied = serviceWith(features, github, launcher, observations).probeAll(project, "http://localhost:18080");

        assertEquals(2, satisfied);
        verify(launcher).fetchHtml("http://localhost:18080/protocols");
        verify(launcher).fetchHtml("http://localhost:18080/materials");
        verify(launcher, never()).fetchHtml("http://localhost:18080/materials/{id}");
    }

    // When the contracts directory has no contracts, declared capabilities are empty and nothing is probed.
    @Test
    void aFeatureWithNoDeclaredContractIsNotCounted() {
        var features = mock(FeatureRepository.class);
        var github = mock(GitHubPullRequestService.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var observations = mock(CapabilityObservationRepository.class);
        ProjectEntity project = project();

        when(github.listDirectoryFiles(project, "main", "docs/contracts"))
                .thenReturn(Optional.of(Set.of()));

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

        // The raw population only. The rate belongs to SixSigmaAuditService's Layer 3, where capability
        // observations are a fourth defect category - a second dpmo here would be a parallel truth.
        assertEquals(4, value.opportunities());
        assertEquals(1, value.defects());
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
        assertEquals(0, value.defects());
    }

    /**
     * INUS_FACTOR_CHECK (D007), DZH_L_MAKKI_03_INUS_FACTOR_CHECK / RUT_BARKAN_MARKUS_04_BOUNDARY_TOPOLOGY:
     * Falsification barrier for Prescription 25: A second pass of ProductCapabilityService on an unchanged
     * main ref makes zero calls to GitHub. Absent contracts (404) are remembered until main changes.
     */
    @Test
    void falsificationHarness_secondPassMakesZeroGitHubCallsWhenMainUnchanged() {
        var features = mock(FeatureRepository.class);
        var github = mock(GitHubPullRequestService.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var observations = mock(CapabilityObservationRepository.class);
        ProjectEntity project = project();

        when(features.findByProjectId(project.getId())).thenReturn(List.of(
                feature("Feature One"),
                feature("Feature Two")
        ));
        // docs/contracts directory returns 404 (empty set of files):
        when(github.listDirectoryFiles(project, "main", "docs/contracts"))
                .thenReturn(Optional.of(Set.of()));

        var service = serviceWith(features, github, launcher, observations);

        // First pass: queries GitHub directory once; makes 0 file content requests since directory has no contracts.
        List<ProductCapabilityService.DeclaredCapability> pass1 = service.declaredCapabilities(project);
        assertTrue(pass1.isEmpty());
        verify(github, times(1)).listDirectoryFiles(project, "main", "docs/contracts");
        verify(github, never()).fetchFileContent(any(), any(), any());

        // Second pass: with main unchanged, MUST make zero GitHub calls.
        List<ProductCapabilityService.DeclaredCapability> pass2 = service.declaredCapabilities(project);
        assertTrue(pass2.isEmpty());
        verifyNoMoreInteractions(github);
    }

    @Test
    void batchDirectoryListingFetchesOnlyPresentFilesAndCachesResult() {
        var features = mock(FeatureRepository.class);
        var github = mock(GitHubPullRequestService.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var observations = mock(CapabilityObservationRepository.class);
        ProjectEntity project = project();

        when(features.findByProjectId(project.getId())).thenReturn(List.of(
                feature("Protocols API"),
                feature("Missing API")
        ));
        // docs/contracts contains only protocols-api.openapi.yaml
        when(github.listDirectoryFiles(project, "main", "docs/contracts"))
                .thenReturn(Optional.of(Set.of("protocols-api.openapi.yaml")));
        when(github.fetchFileContent(project, "main", "docs/contracts/protocols-api.openapi.yaml"))
                .thenReturn(Optional.of(CONTRACT));

        var service = serviceWith(features, github, launcher, observations);

        // Pass 1: fetches directory (1 call), fetches ONLY protocols-api (1 call), NEVER calls for missing-api (0 calls).
        List<ProductCapabilityService.DeclaredCapability> pass1 = service.declaredCapabilities(project, "sha-1");
        assertEquals(3, pass1.size()); // /protocols, /materials, /materials/{id}
        verify(github, times(1)).listDirectoryFiles(project, "main", "docs/contracts");
        verify(github, times(1)).fetchFileContent(project, "main", "docs/contracts/protocols-api.openapi.yaml");
        verify(github, never()).fetchFileContent(project, "main", "docs/contracts/missing-api.openapi.yaml");

        // Pass 2 with same commitSha: 0 GitHub calls.
        List<ProductCapabilityService.DeclaredCapability> pass2 = service.declaredCapabilities(project, "sha-1");
        assertEquals(3, pass2.size());
        verifyNoMoreInteractions(github);

        // Pass 3 with a new commitSha: invalidates and re-queries.
        List<ProductCapabilityService.DeclaredCapability> pass3 = service.declaredCapabilities(project, "sha-2");
        assertEquals(3, pass3.size());
        verify(github, times(2)).listDirectoryFiles(project, "main", "docs/contracts");
        verify(github, times(2)).fetchFileContent(project, "main", "docs/contracts/protocols-api.openapi.yaml");
    }

    @Test
    void cacheInvalidationForcesRequery() {
        var features = mock(FeatureRepository.class);
        var github = mock(GitHubPullRequestService.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var observations = mock(CapabilityObservationRepository.class);
        ProjectEntity project = project();

        when(features.findByProjectId(project.getId())).thenReturn(List.of(feature("Undeclared Feature")));
        when(github.listDirectoryFiles(project, "main", "docs/contracts"))
                .thenReturn(Optional.of(Set.of()));

        var service = serviceWith(features, github, launcher, observations);

        service.declaredCapabilities(project);
        verify(github, times(1)).listDirectoryFiles(project, "main", "docs/contracts");

        // Explicit invalidation
        service.invalidateCache(project.getId());

        service.declaredCapabilities(project);
        verify(github, times(2)).listDirectoryFiles(project, "main", "docs/contracts");
    }

    @Test
    void probeAllReusesCacheAcrossLaunchesWithSameCommitSha() {
        var features = mock(FeatureRepository.class);
        var github = mock(GitHubPullRequestService.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var observations = mock(CapabilityObservationRepository.class);
        ProjectEntity project = project();

        when(features.findByProjectId(project.getId())).thenReturn(List.of(feature("Protocols API")));
        when(github.listDirectoryFiles(project, "main", "docs/contracts"))
                .thenReturn(Optional.of(Set.of("protocols-api.openapi.yaml")));
        when(github.fetchFileContent(project, "main", "docs/contracts/protocols-api.openapi.yaml"))
                .thenReturn(Optional.of(CONTRACT));
        when(launcher.fetchHtml(any())).thenReturn(new RuntimeLauncherClient.FetchResult(200, "ok", 5, null));

        var service = serviceWith(features, github, launcher, observations);

        int satisfied1 = service.probeAll(project, "http://localhost:18080", "sha-abc");
        assertEquals(2, satisfied1);
        verify(github, times(1)).listDirectoryFiles(project, "main", "docs/contracts");
        verify(github, times(1)).fetchFileContent(project, "main", "docs/contracts/protocols-api.openapi.yaml");

        // Second launch on same commit sha: zero GitHub calls
        int satisfied2 = service.probeAll(project, "http://localhost:18080", "sha-abc");
        assertEquals(2, satisfied2);
        verifyNoMoreInteractions(github);
    }

    /**
     * DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT (D009) / LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY (D013):
     * Falsification barrier: Declared capabilities are derived directly from all contract files in docs/contracts,
     * without guessing file names from feature titles.
     * Prevents: declaredCapabilities = 0 when docs/contracts contains domain contracts like StrainManagement.openapi.yaml.
     */
    @Test
    void falsificationHarness_allContractsInDirectoryParsedWithoutFeatureTitleGuessing() {
        var features = mock(FeatureRepository.class);
        var github = mock(GitHubPullRequestService.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var observations = mock(CapabilityObservationRepository.class);
        ProjectEntity project = project();

        // Factory has DB features with mismatched names that do not match contract file names
        when(features.findByProjectId(project.getId())).thenReturn(List.of(
                feature("Strain Management API"),
                feature("Moodle SSO Connector")
        ));

        // Repo contains domain-named OpenAPI contracts and a non-contract file
        when(github.listDirectoryFiles(project, "main", "docs/contracts"))
                .thenReturn(Optional.of(Set.of(
                        "StrainManagement.openapi.yaml",
                        "EmployeeDossier.openapi.yaml",
                        "README.md"
                )));

        String strainContract = """
                openapi: 3.0.3
                info:
                  title: Strain Management
                paths:
                  /strains:
                    get:
                      summary: List strains
                """;
        String dossierContract = """
                openapi: 3.0.3
                info:
                  title: Employee Dossier
                paths:
                  /dossiers:
                    get:
                      summary: List dossiers
                  /dossiers/{id}:
                    get:
                      summary: Get dossier
                """;

        when(github.fetchFileContent(project, "main", "docs/contracts/StrainManagement.openapi.yaml"))
                .thenReturn(Optional.of(strainContract));
        when(github.fetchFileContent(project, "main", "docs/contracts/EmployeeDossier.openapi.yaml"))
                .thenReturn(Optional.of(dossierContract));

        var service = serviceWith(features, github, launcher, observations);

        List<ProductCapabilityService.DeclaredCapability> declared = service.declaredCapabilities(project);

        // Falsification check: declaredCapabilities MUST NOT be empty (proves defect is killed)
        assertEquals(3, declared.size());
        assertEquals("GET /dossiers", declared.get(0).key());
        assertEquals("/dossiers", declared.get(0).path());
        assertEquals("docs/contracts/EmployeeDossier.openapi.yaml", declared.get(0).sourceContract());

        assertEquals("GET /dossiers/{id}", declared.get(1).key());
        assertEquals("/dossiers/{id}", declared.get(1).path());

        assertEquals("GET /strains", declared.get(2).key());
        assertEquals("/strains", declared.get(2).path());
        assertEquals("docs/contracts/StrainManagement.openapi.yaml", declared.get(2).sourceContract());

        // README.md was never fetched as a contract
        verify(github, never()).fetchFileContent(project, "main", "docs/contracts/README.md");
        // No kebab guessed files were fetched
        verify(github, never()).fetchFileContent(project, "main", "docs/contracts/strain-management-api.openapi.yaml");
    }

    /**
     * DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT (D009) / LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY (D013):
     * When directory listing fails (Optional.empty()), declared capabilities must be empty (undecidable).
     * The service MUST NEVER fall back to guessing contract file names from feature titles.
     * Falsification check: zero calls to fetchFileContent for any guessed paths.
     */
    @Test
    void falsificationHarness_directoryReadFailureYieldsEmptyCapabilitiesWithZeroGuessedRequests() {
        var features = mock(FeatureRepository.class);
        var github = mock(GitHubPullRequestService.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var observations = mock(CapabilityObservationRepository.class);
        ProjectEntity project = project();

        when(features.findByProjectId(project.getId())).thenReturn(List.of(
                feature("Strain Management API"),
                feature("Moodle SSO Connector")
        ));
        // GitHub directory listing failed / returned Optional.empty()
        when(github.listDirectoryFiles(project, "main", "docs/contracts"))
                .thenReturn(Optional.empty());

        var service = serviceWith(features, github, launcher, observations);

        List<ProductCapabilityService.DeclaredCapability> declared = service.declaredCapabilities(project);

        // Must be empty (undecidable), not guessed
        assertTrue(declared.isEmpty());
        // Falsification check: ZERO fetchFileContent calls must be made (proves no feature-guessing fallback)
        verify(github, never()).fetchFileContent(any(), any(), any());
    }

    /**
     * INUS_FACTOR_CHECK (D007): Second pass at unchanged main makes zero GitHub calls.
     */
    @Test
    void secondPassWithUnchangedMainMakesZeroGitHubCalls() {
        var features = mock(FeatureRepository.class);
        var github = mock(GitHubPullRequestService.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var observations = mock(CapabilityObservationRepository.class);
        ProjectEntity project = project();

        when(features.findByProjectId(project.getId())).thenReturn(List.of(feature("Protocols API")));
        when(github.listDirectoryFiles(project, "main", "docs/contracts"))
                .thenReturn(Optional.of(Set.of("protocols-api.openapi.yaml")));
        when(github.fetchFileContent(project, "main", "docs/contracts/protocols-api.openapi.yaml"))
                .thenReturn(Optional.of(CONTRACT));

        var service = serviceWith(features, github, launcher, observations);

        List<ProductCapabilityService.DeclaredCapability> pass1 = service.declaredCapabilities(project);
        assertEquals(3, pass1.size());
        verify(github, times(1)).listDirectoryFiles(project, "main", "docs/contracts");
        verify(github, times(1)).fetchFileContent(project, "main", "docs/contracts/protocols-api.openapi.yaml");

        // Second pass: exactly zero GitHub calls
        List<ProductCapabilityService.DeclaredCapability> pass2 = service.declaredCapabilities(project);
        assertEquals(3, pass2.size());
        verifyNoMoreInteractions(github);
    }

    /**
     * TRUTH_STATUS_TABLE (D012) / INUS_FACTOR_CHECK (D007):
     * A failed directory read (Optional.empty()) must NOT be cached as "zero capabilities".
     * If previous knowledge exists in cache, it must be preserved.
     */
    @Test
    void failedDirectoryListingDoesNotPolluteCacheAndPreservesExistingKnowledge() {
        var features = mock(FeatureRepository.class);
        var github = mock(GitHubPullRequestService.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var observations = mock(CapabilityObservationRepository.class);
        ProjectEntity project = project();

        when(features.findByProjectId(project.getId())).thenReturn(List.of(feature("Protocols API")));
        when(github.listDirectoryFiles(project, "main", "docs/contracts"))
                .thenReturn(Optional.of(Set.of("protocols-api.openapi.yaml")));
        when(github.fetchFileContent(project, "main", "docs/contracts/protocols-api.openapi.yaml"))
                .thenReturn(Optional.of(CONTRACT));

        var service = serviceWith(features, github, launcher, observations);

        // Populate cache with successful read
        List<ProductCapabilityService.DeclaredCapability> pass1 = service.declaredCapabilities(project, "sha-1");
        assertEquals(3, pass1.size());

        // Now simulate GitHub failure on subsequent pass with different sha
        when(github.listDirectoryFiles(project, "main", "docs/contracts"))
                .thenReturn(Optional.empty());

        List<ProductCapabilityService.DeclaredCapability> pass2 = service.declaredCapabilities(project, "sha-2");
        // Preserves previous cache rather than wiping out to empty
        assertEquals(3, pass2.size());

        // For a project with no cache, an error yields empty list without polluting cache
        ProjectEntity uncachedProject = project();
        List<ProductCapabilityService.DeclaredCapability> passUncached1 = service.declaredCapabilities(uncachedProject);
        assertTrue(passUncached1.isEmpty());

        // Recover GitHub
        when(github.listDirectoryFiles(uncachedProject, "main", "docs/contracts"))
                .thenReturn(Optional.of(Set.of("protocols-api.openapi.yaml")));
        when(github.fetchFileContent(uncachedProject, "main", "docs/contracts/protocols-api.openapi.yaml"))
                .thenReturn(Optional.of(CONTRACT));

        // Because error was NOT cached, next call re-queries and succeeds
        List<ProductCapabilityService.DeclaredCapability> passUncached2 = service.declaredCapabilities(uncachedProject);
        assertEquals(3, passUncached2.size());
    }

    /**
     * TRUTH_STATUS_TABLE (D012):
     * When the product returns 401 or 403 (unauthenticated access blocked by product SecurityConfig)
     * or connection failure, the observation must be flagged as instrumentFailure and excluded
     * from both opportunities and defects in currentValue().
     */
    @Test
    void probeReceiving401Or403MarksInstrumentFailureAndExcludesFromDefects() {
        var features = mock(FeatureRepository.class);
        var github = mock(GitHubPullRequestService.class);
        var launcher = mock(RuntimeLauncherClient.class);
        var observations = mock(CapabilityObservationRepository.class);
        ProjectEntity project = project();

        when(features.findByProjectId(project.getId())).thenReturn(List.of(feature("Protocols API")));
        when(github.listDirectoryFiles(project, "main", "docs/contracts"))
                .thenReturn(Optional.of(Set.of("protocols-api.openapi.yaml")));
        when(github.fetchFileContent(project, "main", "docs/contracts/protocols-api.openapi.yaml"))
                .thenReturn(Optional.of(CONTRACT));

        // Product SecurityConfig returns 401 Unauthorized for all routes
        when(launcher.fetchHtml(any())).thenReturn(new RuntimeLauncherClient.FetchResult(401, "Unauthorized", 10, "Access Denied"));

        ArgumentCaptor<CapabilityObservationEntity> captor = ArgumentCaptor.forClass(CapabilityObservationEntity.class);

        var service = serviceWith(features, github, launcher, observations);

        int satisfied = service.probeAll(project, "http://localhost:18080", "sha-live");
        assertEquals(0, satisfied);

        verify(observations, times(2)).save(captor.capture());
        List<CapabilityObservationEntity> savedRows = captor.getAllValues();
        assertEquals(2, savedRows.size());
        for (CapabilityObservationEntity row : savedRows) {
            assertFalse(row.isSatisfied());
            assertTrue(row.isInstrumentFailure(), "401 must be recorded as instrument failure");
            assertEquals(401, row.getStatusCode());
        }

        // When currentValue is evaluated against these observations, instrument failures MUST be excluded
        when(observations.findByProjectIdOrderByObservedAtDesc(project.getId())).thenReturn(savedRows);
        ProductCapabilityService.ProductValue value = service.currentValue(project.getId());

        assertEquals(0, value.opportunities(), "Instrument failures must not inflate opportunities");
        assertEquals(0, value.defects(), "Instrument failures must not be counted as product defects");
        assertEquals(0, value.workingCapabilities());
    }
}
