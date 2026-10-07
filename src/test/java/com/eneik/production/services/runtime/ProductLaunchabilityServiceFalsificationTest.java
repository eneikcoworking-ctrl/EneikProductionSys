package com.eneik.production.services.runtime;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.ClientDeliverableReadinessService;
import com.eneik.production.services.github.GitHubPullRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Popperian Falsification Suite for ProductLaunchabilityService.
 * Validates epistemic and architectural launchability invariants:
 * - D013 Wittgenstein Anti-Mirror Telemetry: Verifies physical repository files in GitHub (docker-compose, Dockerfile, datastore config); silence when unsure/day zero.
 * - D008 Popper Falsification Harness: Falsification of delivery readiness — a delivered project lacking a local runner or possessing contradicting datastore configs is refuted and blocked from false-green claims.
 * - D006 Raz Prohibition As Code: Deontic prohibition against direct code intervention in product repo; gaps route through deduplicated wishlist creation, checked at most once for bootstrap.
 */
class ProductLaunchabilityServiceFalsificationTest {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final WishlistRepository wishlistRepository = mock(WishlistRepository.class);
    private final GitHubPullRequestService gitHubPullRequestService = mock(GitHubPullRequestService.class);
    private final ClientDeliverableReadinessService readinessService = mock(ClientDeliverableReadinessService.class);

    private ProductLaunchabilityService service;
    private ProjectEntity project;
    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProductLaunchabilityService(projectRepository, wishlistRepository, gitHubPullRequestService, readinessService, null);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "self", service);

        project = new ProjectEntity();
        project.setId(projectId);
        project.setDefaultBranch("main");
        project.setStatus(ProjectStatus.active);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    }

    @Test
    @DisplayName("D013 Wittgenstein Anti-Mirror: GitHub absence of docker-compose.yml refutes launchability and produces deduplicated gap wishlist")
    void falsifyWittgensteinAntiMirror_missingComposeCreatesDedupedGapWishlist() {
        // Readiness says something has shipped (mergedDeliverables > 0)
        when(readinessService.computeForProject(projectId))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(1, 1, 1, 1, 1.0, true));

        // GitHub physical file check: docker-compose.yml is absent
        when(gitHubPullRequestService.fetchFileContent(eq(project), eq("main"), eq("docker-compose.yml")))
                .thenReturn(Optional.empty());
        when(wishlistRepository.existsByProjectIdAndSource(projectId, WishlistSource.runtime_observability_gap))
                .thenReturn(false);

        service.checkOnce(project);

        // Verification: Exactly one wishlist item is generated through the normal wishlist path (Raz D006)
        ArgumentCaptor<WishlistEntity> captor = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlistRepository, times(1)).save(captor.capture());
        WishlistEntity wishlist = captor.getValue();

        assertThat(wishlist.getSource()).isEqualTo(WishlistSource.runtime_observability_gap);
        assertThat(wishlist.getTargetContext()).isEqualTo(TargetContext.PRODUCT_CODEBASE);
        assertThat(wishlist.getContent()).contains("docker-compose.yml");

        // Project launchabilityCheckedAt timestamp is recorded
        verify(projectRepository).save(project);
        assertThat(project.getLaunchabilityCheckedAt()).isNotNull();
    }

    @Test
    @DisplayName("D008 Popper Falsification: Datastore disagreement between compose (postgres) and app config (h2) is refuted")
    void falsifyPopperFalsificationHarness_datastoreDisagreementBetweenComposeAndAppPropsIsRefuted() {
        String composePostgres = """
                services:
                  app:
                    environment:
                      - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/app
                  db:
                    image: postgres:15-alpine
                """;
        String appPropsH2 = "spring.datasource.url=jdbc:h2:mem:testdb\n";
        String pom = "<project><dependency><groupId>org.postgresql</groupId></dependency></project>";

        when(wishlistRepository.existsByProjectIdAndSource(projectId, WishlistSource.datastore_artifacts_disagree))
                .thenReturn(false);
        when(gitHubPullRequestService.fetchFileContent(eq(project), eq("main"), eq("docker-compose.yml")))
                .thenReturn(Optional.of(composePostgres));
        when(gitHubPullRequestService.fetchFileContent(eq(project), eq("main"), eq("src/main/resources/application.properties")))
                .thenReturn(Optional.of(appPropsH2));
        when(gitHubPullRequestService.fetchFileContent(eq(project), eq("main"), eq("pom.xml")))
                .thenReturn(Optional.of(pom));
        when(gitHubPullRequestService.fetchFileContent(eq(project), eq("main"), eq("docs/architecture/adr-002-runtime-contract.md")))
                .thenReturn(Optional.empty());
        when(gitHubPullRequestService.fetchFileContent(eq(project), eq("main"), eq("src/test/resources/application.properties")))
                .thenReturn(Optional.empty());

        service.checkDatastoreAgreement(project);

        // Verification: The false-green belief is falsified, producing datastore_artifacts_disagree wishlist
        ArgumentCaptor<WishlistEntity> captor = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlistRepository).save(captor.capture());
        WishlistEntity item = captor.getValue();
        assertThat(item.getSource()).isEqualTo(WishlistSource.datastore_artifacts_disagree);
        assertThat(item.getContent()).contains("defaults the application to **h2**, while `docker-compose.yml` provides **postgresql**");
    }

    @Test
    @DisplayName("D008 Popper Falsification: Unbacked domain records in frontend Svelte file are refuted")
    void falsifyPopperFalsificationHarness_unbackedFrontendDomainRecordsAreRefuted() {
        String fakeRecordsSvelte = """
                const documents = [
                  { id: '123e4567-e89b-12d3-a456-426614174000', title: 'Standard Protocol for Influenza', author: 'Epidemiology Research Group' },
                  { id: '223e4567-e89b-12d3-a456-426614174001', title: 'Q3 Surveillance Report 2023', author: 'WHO' }
                ];
                """;

        when(wishlistRepository.existsByProjectIdAndSource(projectId, WishlistSource.frontend_unbacked_records))
                .thenReturn(false);
        when(gitHubPullRequestService.listFilePaths(eq(project), eq("main"), eq("frontend/src")))
                .thenReturn(List.of("frontend/src/MaterialSearch.svelte"));
        when(gitHubPullRequestService.fetchFileContent(eq(project), eq("main"), eq("frontend/src/MaterialSearch.svelte")))
                .thenReturn(Optional.of(fakeRecordsSvelte));

        service.checkFrontendRendersOnlyProducedRecords(project);

        // Verification: Discovers fake frontend records and creates frontend_unbacked_records wishlist
        ArgumentCaptor<WishlistEntity> captor = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlistRepository).save(captor.capture());
        WishlistEntity item = captor.getValue();
        assertThat(item.getSource()).isEqualTo(WishlistSource.frontend_unbacked_records);
        assertThat(item.getContent()).contains("MaterialSearch.svelte");
        assertThat(item.getSourceRoleTag()).isEqualTo("BARCAN-TAG-11");
    }

    @Test
    @DisplayName("D006 Raz Prohibition As Code: Once checked, checkOnce does not re-fetch from GitHub")
    void falsifyRazProhibitionAsCode_alreadyCheckedProjectIsNeverRechecked() {
        project.setLaunchabilityCheckedAt(Instant.now());

        service.checkOnce(project);

        // Invariant: No GitHub calls, no DB saves
        verify(gitHubPullRequestService, never()).fetchFileContent(any(), any(), any());
        verify(projectRepository, never()).save(any());
        verify(wishlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("D013 Wittgenstein Anti-Mirror: Greenfield project with zero shipped deliverables stays silent without calls to GitHub")
    void falsifyWittgensteinAntiMirror_zeroShippedDeliverablesStaysSilent() {
        // Greenfield: mergedDeliverables == 0
        when(readinessService.computeForProject(projectId))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(0, 0, 0, 0, 0.0, false));

        service.checkOnce(project);

        // Invariant: Stays silent, does not touch GitHub, leaves launchabilityCheckedAt null for later retry
        verify(gitHubPullRequestService, never()).fetchFileContent(any(), any(), any());
        verify(projectRepository, never()).save(any());
        verify(wishlistRepository, never()).save(any());
        assertThat(project.getLaunchabilityCheckedAt()).isNull();
    }
}
