package com.eneik.production.services.projectfactory;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Screen for constructive proof of repository URL
 * (NUEL_BELNAP_04_CONSTRUCTIVE_PROOF_OBJECT / D007 Constructive proof omission,
 *  DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT / D009 Substitution failure).
 *
 * Proof obligations:
 * 1. On skipped provisioning (disabled or missing token), repositoryUrl is strictly null.
 * 2. Pre-fabricated fallback URL is never substituted for remote identity.
 */
class GitHubProjectFactoryClientTest {

    private SystemSettingsService settingsService;
    private AccountRepository accountRepository;
    private GitHubProjectFactoryClient client;
    private final WorkspaceArtifacts artifacts = new WorkspaceArtifacts("readme", "env", "ci", "brief");

    @BeforeEach
    void setUp() {
        settingsService = mock(SystemSettingsService.class);
        accountRepository = mock(AccountRepository.class);
        client = new GitHubProjectFactoryClient(
                "eneik-org",
                "https://api.github.com",
                "",
                settingsService,
                accountRepository,
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("D007/D009: When GitHub provisioning is disabled, repositoryUrl is null (no phantom pre-constructed URL)")
    void disabledGitHubProvisioningYieldsNullRepositoryUrl() {
        when(settingsService.effectiveBoolean("github_enabled")).thenReturn(false);

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("Demo App");
        project.setRepositoryName("demo-app");

        GitHubProvisioningResult result = client.provision(project, artifacts);

        assertTrue(result.status().startsWith("skipped: GitHub provisioning disabled"));
        assertNull(result.repositoryUrl(), "Repository URL must be null when provisioning is disabled");
        assertNull(result.repositoryId(), "Repository ID must be null when provisioning is disabled");
    }

    @Test
    @DisplayName("D007/D009: When GITHUB_TOKEN is not configured, repositoryUrl is null (no phantom pre-constructed URL)")
    void missingTokenYieldsNullRepositoryUrl() {
        when(settingsService.effectiveBoolean("github_enabled")).thenReturn(true);
        when(settingsService.effectiveValue("github_token")).thenReturn(null);

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("Demo App");
        project.setRepositoryName("demo-app");

        GitHubProvisioningResult result = client.provision(project, artifacts);

        assertTrue(result.status().startsWith("skipped: GITHUB_TOKEN is not configured"));
        assertNull(result.repositoryUrl(), "Repository URL must be null when token is missing");
        assertNull(result.repositoryId(), "Repository ID must be null when token is missing");
    }

    @Test
    @DisplayName("D007/D009: When GITHUB_TOKEN is blank, repositoryUrl is null")
    void blankTokenYieldsNullRepositoryUrl() {
        when(settingsService.effectiveBoolean("github_enabled")).thenReturn(true);
        when(settingsService.effectiveValue("github_token")).thenReturn("   ");

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("Demo App");
        project.setRepositoryName("demo-app");

        GitHubProvisioningResult result = client.provision(project, artifacts);

        assertTrue(result.status().startsWith("skipped: GITHUB_TOKEN is not configured"));
        assertNull(result.repositoryUrl(), "Repository URL must be null when token is blank");
        assertNull(result.repositoryId(), "Repository ID must be null when token is blank");
    }
}
