package com.eneik.production.services.runtime;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.mockito.ArgumentCaptor;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.ClientDeliverableReadinessService;
import com.eneik.production.services.github.GitHubPullRequestService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Phase 0 of docs/reports/PLAN_client_runtime_observability_2026-08-09.md: a one-shot,
// non-repeating check of whether the active project has a documented way to run itself. These
// tests pin down the three real requirements the operator pushed back on twice before accepting
// this plan: (1) never checked before "delivered", (2) checked at most once ever, (3) a missing
// compose file creates exactly one dedup-guarded wishlist item through the normal compiler path,
// never code written directly into the client repo.
class ProductLaunchabilityServiceTest {

    private ProjectEntity deliveredProject() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setDefaultBranch("main");
        return project;
    }

    // 2026-08-14 (bug-hunt sweep): checkOnce now delegates the decision + writes to
    // recordLaunchabilityResult via a self-proxy field (REQUIRES_NEW, same pattern as
    // JulesDispatchService.self) - wired to the instance itself here since there's no real Spring proxy in
    // a plain unit test.
    private ProductLaunchabilityService newService(ProjectRepository projects, WishlistRepository wishlists,
                                                     GitHubPullRequestService gitHub,
                                                     ClientDeliverableReadinessService readiness) {
        ProductLaunchabilityService service = new ProductLaunchabilityService(projects, wishlists, gitHub, readiness, null);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "self", service);
        return service;
    }

    // --- the datastore-agreement check, 2026-08-22 ------------------------------------------------------
    //
    // Four tests, and the two that matter most are the ones asserting SILENCE. A check that fires on a
    // greenfield repository would deadlock every new project, and a check that fires on a correctly
    // configured one would train everybody to ignore it.

    private static final String COMPOSE_POSTGRES = """
            services:
              app:
                environment:
                  - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/app
              db:
                image: postgres:15-alpine
            """;

    private ProductLaunchabilityService agreementService(WishlistRepository wishlists,
                                                          GitHubPullRequestService gitHub) {
        return newService(mock(ProjectRepository.class), wishlists, gitHub,
                mock(ClientDeliverableReadinessService.class));
    }

    private void stubContract(GitHubPullRequestService gitHub, ProjectEntity project, String contract) {
        when(gitHub.fetchFileContent(eq(project), eq("main"),
                eq("docs/architecture/adr-002-runtime-contract.md")))
                .thenReturn(java.util.Optional.ofNullable(contract));
    }

    private void stubRepoFiles(GitHubPullRequestService gitHub, ProjectEntity project,
                                String compose, String props, String pom) {
        stubContract(gitHub, project, null); // no contract unless a test says otherwise
        when(gitHub.fetchFileContent(eq(project), eq("main"), eq("docker-compose.yml")))
                .thenReturn(java.util.Optional.ofNullable(compose));
        when(gitHub.fetchFileContent(eq(project), eq("main"), eq("src/main/resources/application.properties")))
                .thenReturn(java.util.Optional.ofNullable(props));
        when(gitHub.fetchFileContent(eq(project), eq("main"), eq("pom.xml")))
                .thenReturn(java.util.Optional.ofNullable(pom));
    }

    @Test
    void anApplicationDefaultingToADifferentEngineThanComposeShipsIsFiled() {
        // The measured defect: application.properties said H2, compose shipped PostgreSQL, so every test
        // and review exercised H2 while delivery ran PostgreSQL - and CREATE ALIAS, valid only in H2,
        // passed 144 merged reviews and killed the product at character 8 of its first migration.
        var wishlists = mock(WishlistRepository.class);
        var gitHub = mock(GitHubPullRequestService.class);
        var service = agreementService(wishlists, gitHub);
        ProjectEntity project = deliveredProject();
        stubRepoFiles(gitHub, project, COMPOSE_POSTGRES,
                "spring.datasource.url=jdbc:h2:file:./data/appdb\nspring.jpa.hibernate.ddl-auto=validate\n",
                "<project><dependency><groupId>org.postgresql</groupId></dependency></project>");

        service.checkDatastoreAgreement(project);

        ArgumentCaptor<WishlistEntity> captor = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlists).save(captor.capture());
        WishlistEntity filed = captor.getValue();
        assertEquals(WishlistSource.datastore_artifacts_disagree, filed.getSource());
        assertTrue(filed.getContent().contains("h2") && filed.getContent().contains("postgresql"),
                "the finding must name both engines, or the reader has to re-derive it: " + filed.getContent());
        // The assembly is the work - the same routing product_not_launchable uses.
        assertEquals("BARCAN-TAG-00", filed.getSourceRoleTag());
    }

    @Test
    void aMissingDriverForTheShippedEngineIsFiled() {
        var wishlists = mock(WishlistRepository.class);
        var gitHub = mock(GitHubPullRequestService.class);
        var service = agreementService(wishlists, gitHub);
        ProjectEntity project = deliveredProject();
        stubRepoFiles(gitHub, project, COMPOSE_POSTGRES,
                "spring.datasource.url=jdbc:postgresql://db:5432/app\n",
                "<project><dependency><groupId>com.h2database</groupId></dependency></project>");

        service.checkDatastoreAgreement(project);

        ArgumentCaptor<WishlistEntity> captor = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlists).save(captor.capture());
        assertTrue(captor.getValue().getContent().contains("no driver"),
                "was: " + captor.getValue().getContent());
    }

    @Test
    void aProjectWithNoComposeFileIsNeverFiled() {
        // Day zero. A greenfield repository has nothing to disagree with, and a check that fired here
        // would put a constraint on every project the factory ever creates, before its first client wish.
        var wishlists = mock(WishlistRepository.class);
        var gitHub = mock(GitHubPullRequestService.class);
        var service = agreementService(wishlists, gitHub);
        ProjectEntity project = deliveredProject();
        stubRepoFiles(gitHub, project, null,
                "spring.datasource.url=jdbc:h2:file:./data/appdb\n", "<project/>");

        service.checkDatastoreAgreement(project);

        verify(wishlists, never()).save(any());
    }

    @Test
    void aProjectWhoseArtifactsAgreeIsNeverFiled() {
        // The check must be quiet when the product is right, or it becomes noise that everyone learns to
        // skip - and a finding nobody reads is not a finding.
        var wishlists = mock(WishlistRepository.class);
        var gitHub = mock(GitHubPullRequestService.class);
        var service = agreementService(wishlists, gitHub);
        ProjectEntity project = deliveredProject();
        stubRepoFiles(gitHub, project, COMPOSE_POSTGRES,
                "spring.datasource.url=jdbc:postgresql://db:5432/app\n",
                "<project><dependency><groupId>org.postgresql</groupId></dependency></project>");

        service.checkDatastoreAgreement(project);

        verify(wishlists, never()).save(any());
    }

    @Test
    void aStackThatShipsAnEngineTheContractHasNotDeclaredIsFiled() {
        // ACP-104 caught at the moment it happens rather than five days later: the bootstrap wrote the
        // question, compose answered it, and the contract - which owns the decision - was never told.
        var wishlists = mock(WishlistRepository.class);
        var gitHub = mock(GitHubPullRequestService.class);
        var service = agreementService(wishlists, gitHub);
        ProjectEntity project = deliveredProject();
        stubRepoFiles(gitHub, project, COMPOSE_POSTGRES,
                "spring.datasource.url=jdbc:postgresql://db:5432/app\n",
                "<project><dependency><groupId>org.postgresql</groupId></dependency></project>");
        stubContract(gitHub, project, "```yaml\ndatastore: UNDECLARED\n```\n");

        service.checkDatastoreAgreement(project);

        ArgumentCaptor<WishlistEntity> captor = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlists).save(captor.capture());
        assertTrue(captor.getValue().getContent().contains("UNDECLARED"),
                "the finding must say the decision was taken outside the contract: "
                        + captor.getValue().getContent());
    }

    @Test
    void aStackMatchingItsDeclaredContractIsNeverFiled() {
        // The whole point: once the contract declares the engine and the artifacts follow from it, the
        // check is silent. Anything else would punish a product for being correct.
        var wishlists = mock(WishlistRepository.class);
        var gitHub = mock(GitHubPullRequestService.class);
        var service = agreementService(wishlists, gitHub);
        ProjectEntity project = deliveredProject();
        stubRepoFiles(gitHub, project, COMPOSE_POSTGRES,
                "spring.datasource.url=jdbc:postgresql://db:5432/app\n",
                "<project><dependency><groupId>org.postgresql</groupId></dependency></project>");
        stubContract(gitHub, project, "```yaml\ndatastore: postgresql:15\n```\n");

        service.checkDatastoreAgreement(project);

        verify(wishlists, never()).save(any());
    }

    @Test
    void neverChecksAProjectThatIsNotYetDelivered() {
        var projects = mock(ProjectRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var gitHub = mock(GitHubPullRequestService.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var service = newService(projects, wishlists, gitHub, readiness);

        ProjectEntity project = deliveredProject();
        when(readiness.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(1, 0, 1, 0, 0.0, false));

        service.checkOnce(project);

        verify(gitHub, never()).fetchFileContent(any(), any(), any());
        verify(projects, never()).save(any());
        assertNull(project.getLaunchabilityCheckedAt());
    }

    @Test
    void aDeliveredProjectWithNoComposeFileGetsExactlyOneDedupGuardedWishlist() {
        var projects = mock(ProjectRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var gitHub = mock(GitHubPullRequestService.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var service = newService(projects, wishlists, gitHub, readiness);

        ProjectEntity project = deliveredProject();
        when(readiness.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(1, 1, 1, 1, 1.0, true));
        when(gitHub.fetchFileContent(eq(project), eq("main"), eq("docker-compose.yml")))
                .thenReturn(Optional.empty());
        when(wishlists.existsByProjectIdAndSource(project.getId(), WishlistSource.runtime_observability_gap))
                .thenReturn(false);
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));

        service.checkOnce(project);

        verify(wishlists, times(1)).save(any(WishlistEntity.class));
        verify(projects).save(project);
        assertNotNull(project.getLaunchabilityCheckedAt());
    }

    @Test
    void aDeliveredProjectThatAlreadyHasAWishlistItemNeverGetsASecondOne() {
        var projects = mock(ProjectRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var gitHub = mock(GitHubPullRequestService.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var service = newService(projects, wishlists, gitHub, readiness);

        ProjectEntity project = deliveredProject();
        when(readiness.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(1, 1, 1, 1, 1.0, true));
        when(gitHub.fetchFileContent(eq(project), eq("main"), eq("docker-compose.yml")))
                .thenReturn(Optional.empty());
        when(wishlists.existsByProjectIdAndSource(project.getId(), WishlistSource.runtime_observability_gap))
                .thenReturn(true);
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));

        service.checkOnce(project);

        verify(wishlists, never()).save(any());
        assertNotNull(project.getLaunchabilityCheckedAt());
    }

    @Test
    void aDeliveredProjectWithAComposeFileCreatesNoWishlistAndIsMarkedCheckedForever() {
        var projects = mock(ProjectRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var gitHub = mock(GitHubPullRequestService.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var service = newService(projects, wishlists, gitHub, readiness);

        ProjectEntity project = deliveredProject();
        when(readiness.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(1, 1, 1, 1, 1.0, true));
        when(gitHub.fetchFileContent(eq(project), eq("main"), eq("docker-compose.yml")))
                .thenReturn(Optional.of("services: {}"));
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));

        service.checkOnce(project);

        verify(wishlists, never()).save(any());
        assertNotNull(project.getLaunchabilityCheckedAt());
    }

    /**
     * 2026-08-11 (live incident, test-forty-third): a Dockerfile doing `COPY target/*.jar` with no build
     * stage looks fine until someone actually runs `docker compose up --build` on a fresh clone - target/
     * is gitignored, nothing ever builds the artifact. This must be caught the same tick the compose file
     * itself is confirmed present, dedup-guarded exactly like runtime_observability_gap.
     */
    @Test
    void aDockerfileThatCopiesAPrebuiltJarWithNoBuildStageCreatesADedicatedWishlist() {
        var projects = mock(ProjectRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var gitHub = mock(GitHubPullRequestService.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var service = newService(projects, wishlists, gitHub, readiness);

        ProjectEntity project = deliveredProject();
        when(readiness.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(1, 1, 1, 1, 1.0, true));
        when(gitHub.fetchFileContent(eq(project), eq("main"), eq("docker-compose.yml")))
                .thenReturn(Optional.of("services: {}"));
        when(gitHub.fetchFileContent(eq(project), eq("main"), eq("Dockerfile")))
                .thenReturn(Optional.of("FROM eclipse-temurin:21-jre-alpine\nCOPY target/app.jar app.jar\n"));
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));

        service.checkOnce(project);

        verify(wishlists, times(1)).save(any(WishlistEntity.class));
    }

    @Test
    void aRealMultiStageDockerfileCreatesNoBuildStageWishlist() {
        var projects = mock(ProjectRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var gitHub = mock(GitHubPullRequestService.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var service = newService(projects, wishlists, gitHub, readiness);

        ProjectEntity project = deliveredProject();
        when(readiness.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(1, 1, 1, 1, 1.0, true));
        when(gitHub.fetchFileContent(eq(project), eq("main"), eq("docker-compose.yml")))
                .thenReturn(Optional.of("services: {}"));
        when(gitHub.fetchFileContent(eq(project), eq("main"), eq("Dockerfile")))
                .thenReturn(Optional.of("FROM maven:3.9-eclipse-temurin-17 AS build\nRUN mvn package\n"
                        + "FROM eclipse-temurin:21-jre-alpine\nCOPY --from=build /app/target/app.jar app.jar\n"));
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));

        service.checkOnce(project);

        verify(wishlists, never()).save(any(WishlistEntity.class));
    }

    @Test
    void aNonSelfBuildableDockerfileNeverGetsASecondDedupGuardedWishlist() {
        var projects = mock(ProjectRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var gitHub = mock(GitHubPullRequestService.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var service = newService(projects, wishlists, gitHub, readiness);

        ProjectEntity project = deliveredProject();
        when(readiness.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(1, 1, 1, 1, 1.0, true));
        when(gitHub.fetchFileContent(eq(project), eq("main"), eq("docker-compose.yml")))
                .thenReturn(Optional.of("services: {}"));
        when(gitHub.fetchFileContent(eq(project), eq("main"), eq("Dockerfile")))
                .thenReturn(Optional.of("FROM eclipse-temurin:21-jre-alpine\nCOPY target/app.jar app.jar\n"));
        when(wishlists.existsByProjectIdAndSource(project.getId(), WishlistSource.dockerfile_missing_build_stage))
                .thenReturn(true);
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));

        service.checkOnce(project);

        // 2026-08-14 (bug-hunt sweep): the Dockerfile fetch itself is no longer skippable when already
        // deduped - all GitHub reads now happen up front, before the decision transaction, so the fetch
        // isn't gated on this dedup check anymore (see checkOnce/recordLaunchabilityResult split). The
        // real invariant this test protects - never a second wishlist for an already-flagged Dockerfile -
        // still holds.
        verify(wishlists, never()).save(any());
    }

    /**
     * 2026-08-11 (live incident, test-forty-third): frontend/ exists in the repo but the Dockerfile never
     * builds or serves it - the deployable image is backend-only, so a real user has nothing to look at.
     */
    @Test
    void aFrontendDirectoryNeverReferencedByTheDockerfileCreatesADedicatedWishlist() {
        var projects = mock(ProjectRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var gitHub = mock(GitHubPullRequestService.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var service = newService(projects, wishlists, gitHub, readiness);

        ProjectEntity project = deliveredProject();
        when(readiness.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(1, 1, 1, 1, 1.0, true));
        when(gitHub.fetchFileContent(eq(project), eq("main"), eq("docker-compose.yml")))
                .thenReturn(Optional.of("services: {}"));
        when(gitHub.fetchFileContent(eq(project), eq("main"), eq("Dockerfile")))
                .thenReturn(Optional.of("FROM maven:3.9-eclipse-temurin-17 AS build\nRUN mvn package\n"
                        + "FROM eclipse-temurin:21-jre-alpine\nCOPY --from=build /app/target/app.jar app.jar\n"));
        when(gitHub.fetchFileContent(eq(project), eq("main"), eq("frontend/package.json")))
                .thenReturn(Optional.of("{\"name\": \"frontend\"}"));
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));

        service.checkOnce(project);

        verify(wishlists, times(1)).save(any(WishlistEntity.class));
    }

    @Test
    void aDockerfileThatReferencesFrontendCreatesNoFrontendWishlist() {
        var projects = mock(ProjectRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var gitHub = mock(GitHubPullRequestService.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var service = newService(projects, wishlists, gitHub, readiness);

        ProjectEntity project = deliveredProject();
        when(readiness.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(1, 1, 1, 1, 1.0, true));
        when(gitHub.fetchFileContent(eq(project), eq("main"), eq("docker-compose.yml")))
                .thenReturn(Optional.of("services: {}"));
        when(gitHub.fetchFileContent(eq(project), eq("main"), eq("Dockerfile")))
                .thenReturn(Optional.of("FROM maven:3.9-eclipse-temurin-17 AS build\nCOPY frontend frontend\n"
                        + "RUN cd frontend && npm ci && npm run build\n"
                        + "FROM eclipse-temurin:21-jre-alpine\nCOPY --from=build /app/target/app.jar app.jar\n"));
        when(gitHub.fetchFileContent(eq(project), eq("main"), eq("frontend/package.json")))
                .thenReturn(Optional.of("{\"name\": \"frontend\"}"));
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));

        service.checkOnce(project);

        verify(wishlists, never()).save(any());
    }

    @Test
    void noFrontendDirectoryCreatesNoFrontendWishlist() {
        var projects = mock(ProjectRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var gitHub = mock(GitHubPullRequestService.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var service = newService(projects, wishlists, gitHub, readiness);

        ProjectEntity project = deliveredProject();
        when(readiness.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(1, 1, 1, 1, 1.0, true));
        when(gitHub.fetchFileContent(eq(project), eq("main"), eq("docker-compose.yml")))
                .thenReturn(Optional.of("services: {}"));
        when(gitHub.fetchFileContent(eq(project), eq("main"), eq("Dockerfile")))
                .thenReturn(Optional.of("FROM maven:3.9-eclipse-temurin-17 AS build\nRUN mvn package\n"
                        + "FROM eclipse-temurin:21-jre-alpine\nCOPY --from=build /app/target/app.jar app.jar\n"));
        when(gitHub.fetchFileContent(eq(project), eq("main"), eq("frontend/package.json")))
                .thenReturn(Optional.empty());
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));

        service.checkOnce(project);

        verify(wishlists, never()).save(any());
    }

    @Test
    void anAlreadyCheckedProjectIsNeverCheckedAgain() {
        var projects = mock(ProjectRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var gitHub = mock(GitHubPullRequestService.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var service = newService(projects, wishlists, gitHub, readiness);

        ProjectEntity project = deliveredProject();
        project.setLaunchabilityCheckedAt(Instant.now());

        service.checkOnce(project);

        verify(readiness, never()).computeForProject(any());
        verify(gitHub, never()).fetchFileContent(any(), any(), any());
        verify(projects, never()).save(any());
    }
}
