package com.eneik.production.services.projectfactory;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectHotspotFileEntity;
import com.eneik.production.repositories.ProjectHotspotFileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Falsification test-screen for {@link ProjectFactoryService}.
 *
 * Grounded in:
 * 1. NUEL_BELNAP_06_SUBSTITUTION_ORACLE [D009, Belnap]:
 *    Substitution failure prevention. If GitHub provisioning fails or is skipped,
 *    the service must never substitute an unbacked phantom URL. Real external
 *    URL is preserved if and only if verified by the upstream provisioning client.
 * 2. ELVIN_GOLDMAN_01_RELIABILITY_CHAIN [D010, Goldman]:
 *    Epistemic reliability of the provisioning pipeline:
 *    - Name conflict boundary between greenfield and brownfield onboarding.
 *    - Canonical hotspot file registration linked to the project ID.
 *    - Full data lineage aggregation into factory status and JSON audit report.
 */
class ProjectFactoryServiceFalsificationTest {

    private ProjectWorkspaceFactoryService workspaceFactoryService;
    private GitHubProjectFactoryClient gitHubProjectFactoryClient;
    private LinearProjectFactoryClient linearProjectFactoryClient;
    private ProjectHotspotFileRepository hotspotFileRepository;
    private ObjectMapper objectMapper;
    private ProjectFactoryService service;

    private final WorkspaceArtifacts sampleArtifacts = new WorkspaceArtifacts(
            "# Readme", "PROFILE=test", "ci-spec", "brief text"
    );

    @BeforeEach
    void setUp() {
        workspaceFactoryService = mock(ProjectWorkspaceFactoryService.class);
        gitHubProjectFactoryClient = mock(GitHubProjectFactoryClient.class);
        linearProjectFactoryClient = mock(LinearProjectFactoryClient.class);
        hotspotFileRepository = mock(ProjectHotspotFileRepository.class);
        objectMapper = new ObjectMapper();

        service = new ProjectFactoryService(
                workspaceFactoryService,
                gitHubProjectFactoryClient,
                linearProjectFactoryClient,
                objectMapper,
                hotspotFileRepository
        );
    }

    // =========================================================================
    // NUEL_BELNAP_06_SUBSTITUTION_ORACLE [D009, Belnap]
    // =========================================================================

    @Test
    @DisplayName("Belnap D009: Null or missing GitHub URL is strictly preserved without phantom substitution")
    void substitutionOracleRejectsPhantomUrlOnDisabledOrSkippedGitHub() {
        ProjectEntity project = createProject("phoenix-project", "greenfield");
        project.setRepositoryUrl("https://github.com/phantom-org/phantom-repo");

        when(workspaceFactoryService.provision(project))
                .thenReturn(new WorkspaceProvisioningResult("/workspaces/phoenix-project", sampleArtifacts, "workspace_ready"));
        when(gitHubProjectFactoryClient.provision(project, sampleArtifacts))
                .thenReturn(new GitHubProvisioningResult("skipped: GITHUB_TOKEN not configured", null, null));
        when(linearProjectFactoryClient.provision(eq(project), isNull()))
                .thenReturn(new LinearProvisioningResult("skipped: Linear disabled", null, null));

        ProjectFactoryResult result = service.provision(project);

        // Prove that pre-existing phantom URL was rejected and NOT substituted
        assertThat(result.repositoryUrl()).isNull();
        verify(linearProjectFactoryClient).provision(project, null);
    }

    @Test
    @DisplayName("Belnap D009: Verified GitHub repository URL is preserved truthfully into Linear and result")
    void substitutionOraclePreservesVerifiedGitHubUrl() {
        ProjectEntity project = createProject("stellar-project", "greenfield");
        String verifiedUrl = "https://github.com/eneikdru/stellar-project";

        when(workspaceFactoryService.provision(project))
                .thenReturn(new WorkspaceProvisioningResult("/workspaces/stellar-project", sampleArtifacts, "workspace_ready"));
        when(gitHubProjectFactoryClient.provision(project, sampleArtifacts))
                .thenReturn(new GitHubProvisioningResult(
                        "created repository and configured protections",
                        verifiedUrl,
                        "repo-777",
                        List.of(),
                        List.of(new CollaboratorProvisioningResult("agent1", "invitation_sent", 201, "ok"))
                ));
        when(linearProjectFactoryClient.provision(project, verifiedUrl))
                .thenReturn(new LinearProvisioningResult("created: LIN-100", "lin-proj-id-100", "https://linear.app/eneik/proj-100"));

        ProjectFactoryResult result = service.provision(project);

        assertThat(result.repositoryUrl()).isEqualTo(verifiedUrl);
        assertThat(result.githubRepositoryId()).isEqualTo("repo-777");
        assertThat(result.linearProjectId()).isEqualTo("lin-proj-id-100");
        assertThat(result.factoryStatus()).isEqualTo("ready_external");
        verify(linearProjectFactoryClient).provision(project, verifiedUrl);
    }

    // =========================================================================
    // ELVIN_GOLDMAN_01_RELIABILITY_CHAIN [D010, Goldman]
    // =========================================================================

    @Test
    @DisplayName("Goldman D010: Name conflict on existing repo raises error for greenfield but allows brownfield")
    void reliabilityChainDifferentiatesGreenfieldAndBrownfieldConflicts() {
        ProjectEntity greenfield = createProject("existing-repo", "greenfield");
        when(workspaceFactoryService.provision(greenfield))
                .thenReturn(new WorkspaceProvisioningResult("/workspaces/existing-repo", sampleArtifacts, "ready"));
        when(gitHubProjectFactoryClient.provision(greenfield, sampleArtifacts))
                .thenReturn(new GitHubProvisioningResult("exists: repository already present on remote", "https://github.com/eneikdru/existing-repo", "123"));

        // Greenfield project must fail fast on remote repo existence
        assertThatThrownBy(() -> service.provision(greenfield))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name_conflict");

        // Brownfield project with existing repo must be admitted cleanly
        ProjectEntity brownfield = createProject("existing-repo", "brownfield");
        when(workspaceFactoryService.provision(brownfield))
                .thenReturn(new WorkspaceProvisioningResult("/workspaces/existing-repo", sampleArtifacts, "ready"));
        when(gitHubProjectFactoryClient.provision(brownfield, sampleArtifacts))
                .thenReturn(new GitHubProvisioningResult("exists: repository already present on remote", "https://github.com/eneikdru/existing-repo", "123"));
        when(linearProjectFactoryClient.provision(any(), any()))
                .thenReturn(new LinearProvisioningResult("skipped: disabled", null, null));

        ProjectFactoryResult brownfieldResult = service.provision(brownfield);
        assertThat(brownfieldResult).isNotNull();
        assertThat(brownfieldResult.repositoryUrl()).isEqualTo("https://github.com/eneikdru/existing-repo");
    }

    @Test
    @DisplayName("Goldman D010: Registers standard hotspot files with strict project attribution")
    void reliabilityChainRegistersCanonicalProjectHotspots() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = createProject("hotspot-probe", "greenfield");
        project.setId(projectId);

        when(workspaceFactoryService.provision(project))
                .thenReturn(new WorkspaceProvisioningResult("/workspaces/hotspot-probe", sampleArtifacts, "ready"));
        when(gitHubProjectFactoryClient.provision(project, sampleArtifacts))
                .thenReturn(new GitHubProvisioningResult("skipped", null, null));
        when(linearProjectFactoryClient.provision(any(), any()))
                .thenReturn(new LinearProvisioningResult("skipped", null, null));

        service.provision(project);

        ArgumentCaptor<ProjectHotspotFileEntity> captor = ArgumentCaptor.forClass(ProjectHotspotFileEntity.class);
        verify(hotspotFileRepository, times(4)).save(captor.capture());

        List<ProjectHotspotFileEntity> savedHotspots = captor.getAllValues();
        assertThat(savedHotspots).extracting(ProjectHotspotFileEntity::getProjectId)
                .containsOnly(projectId);
        assertThat(savedHotspots).extracting(ProjectHotspotFileEntity::getFilePath)
                .containsExactlyInAnyOrder(
                        "frontend/src/App.svelte",
                        "package.json",
                        "src/main/resources/application.properties",
                        "docker-compose.yml"
                );
    }

    @Test
    @DisplayName("Goldman D010: Collaborator failure demotes factory status to waiting")
    void reliabilityChainCollaboratorFailureDemotesToWaiting() {
        ProjectEntity project = createProject("collaborator-probe", "greenfield");

        when(workspaceFactoryService.provision(project))
                .thenReturn(new WorkspaceProvisioningResult("/workspaces/collab", sampleArtifacts, "ready"));
        when(gitHubProjectFactoryClient.provision(project, sampleArtifacts))
                .thenReturn(new GitHubProvisioningResult(
                        "created repository with warnings",
                        "https://github.com/eneikdru/collab",
                        "456",
                        List.of("collaborator failed"),
                        List.of(new CollaboratorProvisioningResult("agent-x", "not_found", 404, "User not found"))
                ));
        when(linearProjectFactoryClient.provision(any(), any()))
                .thenReturn(new LinearProvisioningResult("skipped", null, null));

        ProjectFactoryResult result = service.provision(project);

        assertThat(result.factoryStatus()).isEqualTo("waiting");
    }

    @Test
    @DisplayName("Goldman D010: Complete factory report carries verifiable JSON lineage of all subsystems")
    void reliabilityChainCompleteFactoryReportPreservesSubsystemLineage() throws Exception {
        ProjectEntity project = createProject("lineage-probe", "greenfield");

        when(workspaceFactoryService.provision(project))
                .thenReturn(new WorkspaceProvisioningResult("/workspaces/lineage-probe", sampleArtifacts, "workspace_ready"));
        when(gitHubProjectFactoryClient.provision(project, sampleArtifacts))
                .thenReturn(new GitHubProvisioningResult(
                        "created repository",
                        "https://github.com/eneikdru/lineage-probe",
                        "999",
                        List.of("sample warning"),
                        List.of(new CollaboratorProvisioningResult("operator-bot", "invitation_sent", 201, "invited"))
                ));
        when(linearProjectFactoryClient.provision(any(), any()))
                .thenReturn(new LinearProvisioningResult("created", "lin-999", "https://linear.app/proj/lin-999"));

        ProjectFactoryResult result = service.provision(project);

        JsonNode reportJson = objectMapper.readTree(result.factoryReport());
        assertThat(reportJson.get("factoryStatus").asText()).isEqualTo("ready_external");
        assertThat(reportJson.get("workspacePath").asText()).isEqualTo("/workspaces/lineage-probe");
        assertThat(reportJson.get("githubRepositoryUrl").asText()).isEqualTo("https://github.com/eneikdru/lineage-probe");
        assertThat(reportJson.get("linearProjectId").asText()).isEqualTo("lin-999");
        assertThat(reportJson.get("githubWarnings").get(0).asText()).isEqualTo("sample warning");
        assertThat(reportJson.get("collaborators").get(0).get("username").asText()).isEqualTo("operator-bot");
        assertThat(reportJson.get("collaborators").get(0).get("status").asText()).isEqualTo("invitation_sent");
    }

    private ProjectEntity createProject(String name, String onboardingMode) {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName(name);
        project.setSlug(name);
        project.setRepositoryName(name);
        project.setOnboardingMode(onboardingMode);
        return project;
    }
}
