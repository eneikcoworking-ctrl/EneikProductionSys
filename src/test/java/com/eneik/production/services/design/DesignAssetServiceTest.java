package com.eneik.production.services.design;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.services.dashboard.ProjectOperationalContextService;
import com.eneik.production.services.googleai.GoogleAiResourceService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.stitch.StitchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DesignAssetServiceTest {

    private GoogleAiResourceService googleAiResourceService;
    private StitchClient stitchClient;
    private SystemSettingsService settingsService;
    private DesignAssetService designAssetService;
    private ProjectEntity project;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        googleAiResourceService = mock(GoogleAiResourceService.class);
        stitchClient = mock(StitchClient.class);
        settingsService = mock(SystemSettingsService.class);
        when(settingsService.effectiveBoolean("design_service_enabled")).thenReturn(true);

        designAssetService = new DesignAssetService(
                googleAiResourceService, stitchClient, settingsService, new ObjectMapper(), tempDir.toString()
        );

        project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("Test Project");
        project.setSlug("test-project");
    }

    @Test
    void usesStitchWhenConfiguredAndAvailable() {
        when(settingsService.effectiveBoolean("stitch_enabled")).thenReturn(true);
        when(stitchClient.hasStitchKey()).thenReturn(true);
        when(stitchClient.createProject(anyString())).thenReturn("123456");
        when(stitchClient.generateScreenFromText(eq("123456"), anyString(), anyString()))
                .thenReturn(new StitchClient.GeneratedScreen(true, "ok",
                        "https://example.com/html", "https://example.com/shot.png", "Generated screen via Stitch."));
        when(stitchClient.download("https://example.com/html")).thenReturn("<html></html>".getBytes());
        when(stitchClient.download("https://example.com/shot.png")).thenReturn(new byte[]{1, 2, 3});

        DesignAssetService.DesignAssetResult result = designAssetService.generateAsset(
                project, null, "A login screen", "mockup", "fast", false
        );

        assertThat(result.available()).isTrue();
        assertThat(result.model()).isEqualTo("stitch");
        assertThat(result.imagePath()).isNotBlank();
        verify(googleAiResourceService, never()).callInteraction(anyString(), anyString(), anyList());
    }

    @Test
    void fallsBackToNanoBananaWhenStitchNotConfigured() {
        when(settingsService.effectiveBoolean("stitch_enabled")).thenReturn(false);
        when(settingsService.effectiveBoolean("nano_banana_enabled")).thenReturn(true);
        when(googleAiResourceService.hasGoogleAiKey()).thenReturn(true);
        when(googleAiResourceService.model(anyString(), anyString())).thenReturn("gemini-3.1-flash-image");
        var interaction = new GoogleAiResourceService.InteractionResult(
                true, "ok", "gemini-3.1-flash-image", "", base64Png(), "image/png", "", "", "ok"
        );
        when(googleAiResourceService.callInteraction(anyString(), anyString(), anyList())).thenReturn(interaction);

        DesignAssetService.DesignAssetResult result = designAssetService.generateAsset(
                project, null, "A login screen", "mockup", "fast", false
        );

        assertThat(result.available()).isTrue();
        assertThat(result.model()).isEqualTo("gemini-3.1-flash-image");
        verify(stitchClient, never()).createProject(anyString());
    }

    @Test
    void fallsBackToNanoBananaWhenStitchGenerationFails() {
        when(settingsService.effectiveBoolean("stitch_enabled")).thenReturn(true);
        when(stitchClient.hasStitchKey()).thenReturn(true);
        when(stitchClient.createProject(anyString())).thenReturn("123456");
        when(stitchClient.generateScreenFromText(eq("123456"), anyString(), anyString()))
                .thenReturn(StitchClient.GeneratedScreen.unavailable("Stitch call failed."));
        when(settingsService.effectiveBoolean("nano_banana_enabled")).thenReturn(true);
        when(googleAiResourceService.hasGoogleAiKey()).thenReturn(true);
        when(googleAiResourceService.model(anyString(), anyString())).thenReturn("gemini-3.1-flash-image");
        var interaction = new GoogleAiResourceService.InteractionResult(
                true, "ok", "gemini-3.1-flash-image", "", base64Png(), "image/png", "", "", "ok"
        );
        when(googleAiResourceService.callInteraction(anyString(), anyString(), anyList())).thenReturn(interaction);

        DesignAssetService.DesignAssetResult result = designAssetService.generateAsset(
                project, null, "A login screen", "mockup", "fast", false
        );

        assertThat(result.available()).isTrue();
        assertThat(result.model()).isEqualTo("gemini-3.1-flash-image");
    }

    private String base64Png() {
        return java.util.Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
    }
}
