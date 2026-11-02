package com.eneik.production.services.design.sovereign;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.services.dashboard.ProjectOperationalContextService;
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
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DesignAssetServiceSovereignFallbackTest {

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
        project.setName("Sovereign Demo Project");
        project.setSlug("sovereign-demo");
    }

    @Test
    @DisplayName("When Stitch is disabled and implementable HTML is required, Sovereign Designer successfully synthesizes valid HTML5/Tailwind mockup")
    void whenStitchIsDisabled_thenSovereignDesignerProducesValidHtml5Mockup() {
        when(settingsService.effectiveBoolean("stitch_enabled")).thenReturn(false);

        var context = new ProjectOperationalContextService.ProjectOperationalContext(
                project.getId(), project.getName(), java.util.Map.of(), "{}", null
        );

        DesignAssetService.DesignAssetResult result = designAssetService.generateAsset(
                project,
                context,
                "Build developer tools infrastructure console with high contrast dark theme",
                "mockup",
                "fast",
                false,
                null,
                List.of(),
                List.of(),
                true
        );

        assertThat(result.available()).isTrue();
        assertThat(result.status()).isEqualTo("ok");
        assertThat(result.model()).isEqualTo("sovereign-designer");
        assertThat(result.mimeType()).isEqualTo("text/html");
        assertThat(result.repoDraftPath()).startsWith(DesignAssetService.DESIGN_DRAFT_ROOT + "/");

        ArgumentCaptor<byte[]> htmlCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(gitHubPullRequestService).commitFile(eq(project), contains("mockup.html"), htmlCaptor.capture(), anyString());
        verify(gitHubPullRequestService).commitFile(eq(project), contains("mockup.png"), any(), anyString());

        String generatedHtml = new String(htmlCaptor.getValue(), StandardCharsets.UTF_8);
        assertThat(generatedHtml).contains("<!DOCTYPE html>");
        assertThat(generatedHtml).contains("<html lang=\"en\" class=\"dark\">");
        assertThat(generatedHtml).contains("cdn.tailwindcss.com");
        assertThat(generatedHtml).contains("role=\"main\"");
        assertThat(generatedHtml).contains("focus-visible:");
        assertThat(generatedHtml).doesNotContain("Leica");
        assertThat(generatedHtml).doesNotContain("37°46'N");
    }

    @Test
    @DisplayName("When Stitch fails during generation and implementable HTML is required, service falls back to Sovereign Designer")
    void whenStitchFails_thenFallbackToSovereignDesignerSucceeds() {
        when(settingsService.effectiveBoolean("stitch_enabled")).thenReturn(true);
        when(stitchClient.hasStitchKey()).thenReturn(true);
        when(stitchClient.createProject(anyString())).thenReturn(null); // Stitch project creation fails

        DesignAssetService.DesignAssetResult result = designAssetService.generateAsset(
                project,
                null,
                "B2B SaaS Enterprise analytics dashboard",
                "mockup",
                "fast",
                false,
                null,
                List.of(),
                List.of(),
                true
        );

        assertThat(result.available()).isTrue();
        assertThat(result.status()).isEqualTo("ok");
        assertThat(result.model()).isEqualTo("sovereign-designer");
        assertThat(result.repoDraftPath()).isNotBlank();
        verify(gitHubPullRequestService).commitFile(eq(project), contains("mockup.html"), any(), anyString());
    }

    @Test
    @DisplayName("Popper Falsification Invariant: System never returns 'unavailable' due to missing external paid API")
    void popperFalsificationInvariant_noExternalApiKeyNeededForHtmlGeneration() {
        when(settingsService.effectiveBoolean("stitch_enabled")).thenReturn(false);
        when(settingsService.effectiveBoolean("nano_banana_enabled")).thenReturn(false);
        when(googleAiResourceService.hasGoogleAiKey()).thenReturn(false);
        when(stitchClient.hasStitchKey()).thenReturn(false);

        DesignAssetService.DesignAssetResult result = designAssetService.generateAsset(
                project,
                null,
                "FinTech investment ledger",
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
        assertThat(result.repoDraftPath()).isNotEmpty();
    }
}
