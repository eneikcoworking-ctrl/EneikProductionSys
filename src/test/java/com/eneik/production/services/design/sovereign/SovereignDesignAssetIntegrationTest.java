package com.eneik.production.services.design.sovereign;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.services.design.DesignAssetService;
import com.eneik.production.services.design.DesignConsistencyAuditService;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.googleai.GoogleAiResourceService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.stitch.StitchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SovereignDesignAssetIntegrationTest {

    private GoogleAiResourceService googleAiResourceService;
    private StitchClient stitchClient;
    private SystemSettingsService settingsService;
    private GitHubPullRequestService gitHubPullRequestService;
    private DesignAssetService designAssetService;
    private ProjectEntity project;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        googleAiResourceService = mock(GoogleAiResourceService.class);
        stitchClient = mock(StitchClient.class);
        settingsService = mock(SystemSettingsService.class);
        gitHubPullRequestService = mock(GitHubPullRequestService.class);

        when(settingsService.effectiveBoolean("design_service_enabled")).thenReturn(true);
        when(gitHubPullRequestService.commitFile(any(), anyString(), any(), anyString())).thenReturn(true);

        designAssetService = new DesignAssetService(
                googleAiResourceService,
                stitchClient,
                settingsService,
                new ObjectMapper(),
                gitHubPullRequestService,
                new DesignConsistencyAuditService(),
                tempDir.toString()
        );

        project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("Autonomous Verification Project");
        project.setSlug("auto-verify");
    }

    @Test
    @DisplayName("Verification: when stitch_enabled=false, designAssetService generates implementable HTML via Sovereign Designer without external dependencies")
    void testSovereignDesignerGenerationWhenStitchDisabled() {
        when(settingsService.effectiveBoolean("stitch_enabled")).thenReturn(false);

        DesignAssetService.DesignAssetResult result = designAssetService.generateAsset(
                project,
                null,
                "High performance developer portal",
                "mockup",
                "fast",
                false,
                null,
                List.of(),
                List.of(),
                true
        );

        assertThat(result.available()).isTrue();
        assertThat(result.model()).isEqualTo("sovereign-designer");
        assertThat(result.status()).isEqualTo("ok");
        assertThat(result.repoDraftPath()).isNotBlank();
        assertThat(result.repoDraftPath()).startsWith(DesignAssetService.DESIGN_DRAFT_ROOT + "/");
        verify(gitHubPullRequestService, atLeastOnce()).commitFile(eq(project), contains("mockup.html"), any(), anyString());
    }
}
