package com.eneik.production.services.projectfactory;

import com.eneik.production.models.persistence.ProjectEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Falsification test-screen for {@link ProjectWorkspaceFactoryService}.
 *
 * Grounded in:
 * 1. AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY [D006, Varzi]:
 *    Boundary topology and path traversal protection. A workspace path must
 *    never escape the configured workspaceRoot, rejecting malicious or
 *    relative slugs with an explicit boundary error.
 * 2. ELVIN_GOLDMAN_01_RELIABILITY_CHAIN [D010, Goldman]:
 *    Reliability of non-destructive brownfield onboarding. Pre-existing files
 *    and client work in brownfield mode must never be overwritten, preserving
 *    data lineage and repository truth.
 */
class ProjectWorkspaceFactoryServiceFalsificationTest {

    @TempDir
    Path tempDir;

    // =========================================================================
    // AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY [D006, Varzi]
    // =========================================================================

    @Test
    @DisplayName("Varzi D006: Path traversal attempt outside workspaceRoot throws IllegalStateException")
    void boundaryTopologyRejectsPathTraversalOutsideRoot() {
        ProjectWorkspaceFactoryService service = new ProjectWorkspaceFactoryService(tempDir.toString());
        ProjectEntity escapingProject = new ProjectEntity();
        escapingProject.setId(UUID.randomUUID());
        escapingProject.setName("Escape Artist");
        escapingProject.setSlug("../../escaped-location");
        escapingProject.setOnboardingMode("greenfield");

        assertThatThrownBy(() -> service.provision(escapingProject))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Workspace path escaped configured root");
    }

    @Test
    @DisplayName("Varzi D006: Valid project slug provisions strictly inside workspaceRoot hierarchy")
    void boundaryTopologyConformsWorkspacePathToRoot() {
        ProjectWorkspaceFactoryService service = new ProjectWorkspaceFactoryService(tempDir.toString());
        ProjectEntity project = createProject("alpha-service", "greenfield");

        WorkspaceProvisioningResult result = service.provision(project);

        Path workspacePath = Path.of(result.workspacePath());
        assertThat(workspacePath).startsWith(tempDir.toAbsolutePath().normalize());
        assertThat(Files.isDirectory(workspacePath)).isTrue();
        assertThat(Files.isDirectory(workspacePath.resolve(".github/workflows"))).isTrue();
        assertThat(Files.isDirectory(workspacePath.resolve("docs"))).isTrue();
    }

    // =========================================================================
    // ELVIN_GOLDMAN_01_RELIABILITY_CHAIN [D010, Goldman]
    // =========================================================================

    @Test
    @DisplayName("Goldman D010: Brownfield onboarding does not overwrite pre-existing repository files")
    void reliabilityChainBrownfieldPreservesPreExistingFiles() throws IOException {
        ProjectWorkspaceFactoryService service = new ProjectWorkspaceFactoryService(tempDir.toString());
        String slug = "existing-brownfield";
        Path workspace = tempDir.resolve(slug);
        Files.createDirectories(workspace);

        Path existingReadme = workspace.resolve("README.md");
        String customContent = "# Pre-existing Custom Client Readme\nDo not touch!";
        Files.writeString(existingReadme, customContent);

        ProjectEntity project = createProject(slug, "brownfield");
        WorkspaceProvisioningResult result = service.provision(project);

        assertThat(result.status()).isEqualTo("workspace ready");
        // Verify custom file was not overwritten
        assertThat(Files.readString(existingReadme)).isEqualTo(customContent);
        // Verify other files were not created in brownfield mode
        assertThat(Files.exists(workspace.resolve(".env.example"))).isFalse();
        assertThat(Files.exists(workspace.resolve(".github/workflows/ci.yml"))).isFalse();
        assertThat(Files.exists(workspace.resolve("docs/PROJECT_BRIEF.md"))).isFalse();
    }

    @Test
    @DisplayName("Goldman D010: Greenfield onboarding generates all four canonical template artifacts")
    void reliabilityChainGreenfieldGeneratesFullArtifactSuite() throws IOException {
        ProjectWorkspaceFactoryService service = new ProjectWorkspaceFactoryService(tempDir.toString());
        String slug = "greenfield-product";
        ProjectEntity project = createProject(slug, "greenfield");
        project.setRepositoryUrl("https://github.com/eneikdru/greenfield-product");
        project.setLinearProjectKey("GP");

        WorkspaceProvisioningResult result = service.provision(project);

        Path workspace = Path.of(result.workspacePath());
        Path readme = workspace.resolve("README.md");
        Path envExample = workspace.resolve(".env.example");
        Path ciWorkflow = workspace.resolve(".github/workflows/ci.yml");
        Path brief = workspace.resolve("docs/PROJECT_BRIEF.md");

        assertThat(Files.exists(readme)).isTrue();
        assertThat(Files.exists(envExample)).isTrue();
        assertThat(Files.exists(ciWorkflow)).isTrue();
        assertThat(Files.exists(brief)).isTrue();

        assertThat(Files.readString(readme))
                .contains(project.getName())
                .contains("Slug: " + slug)
                .contains("Repository: https://github.com/eneikdru/greenfield-product")
                .contains("Linear project key: GP");

        assertThat(Files.readString(envExample))
                .contains("PROJECT_NAME=\"Greenfield Product\"")
                .contains("PROJECT_SLUG=" + slug);

        assertThat(Files.readString(ciWorkflow))
                .contains("Eneik Project CI")
                .contains("setup-node")
                .contains("setup-java")
                .contains("setup-python");

        assertThat(Files.readString(brief))
                .contains("Project Brief")
                .contains("Production Constraints");
    }

    @Test
    @DisplayName("Goldman D010: Workspace artifacts record carries faithful model representations")
    void reliabilityChainArtifactsRecordModelFaithfulness() {
        ProjectWorkspaceFactoryService service = new ProjectWorkspaceFactoryService(tempDir.toString());
        ProjectEntity project = createProject("model-faithfulness", "greenfield");

        WorkspaceProvisioningResult result = service.provision(project);
        WorkspaceArtifacts artifacts = result.artifacts();

        assertThat(artifacts.readme()).contains("model-faithfulness");
        assertThat(artifacts.envExample()).contains("model-faithfulness");
        assertThat(artifacts.ciWorkflow()).contains("Eneik Project CI");
        assertThat(artifacts.projectBrief()).contains("Project Brief");
    }

    private ProjectEntity createProject(String slug, String onboardingMode) {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("Greenfield Product");
        project.setSlug(slug);
        project.setRepositoryName(slug);
        project.setOnboardingMode(onboardingMode);
        return project;
    }
}
