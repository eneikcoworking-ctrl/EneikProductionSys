package com.eneik.production.services.design;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.services.dashboard.ProjectOperationalContextService;
import com.eneik.production.services.github.GitHubPullRequestService;
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
                googleAiResourceService, stitchClient, settingsService, new ObjectMapper(),
                gitHubPullRequestService, new DesignConsistencyAuditService(), tempDir.toString()
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
        when(stitchClient.generateScreenFromText(eq("123456"), anyString(), anyString(), isNull()))
                .thenReturn(new StitchClient.GeneratedScreen(true, "ok",
                        "https://example.com/html", "https://example.com/shot.png", "screen-1", "Generated screen via Stitch."));
        when(stitchClient.download("https://example.com/html")).thenReturn("<html></html>".getBytes());
        when(stitchClient.download("https://example.com/shot.png")).thenReturn(new byte[]{1, 2, 3});

        DesignAssetService.DesignAssetResult result = designAssetService.generateAsset(
                project, null, "A login screen", "mockup", "fast", false
        );

        assertThat(result.available()).isTrue();
        assertThat(result.model()).isEqualTo("stitch");
        assertThat(result.imagePath()).isNotBlank();
        verify(googleAiResourceService, never()).callInteraction(anyString(), anyString(), anyList());

        // The generated mockup must be committed into the project's actual GitHub repo, not just the
        // Eneik backend's local disk - a Jules session can only ever see its own GitHub checkout, so a
        // local-only path is a dead reference (confirmed live in the test-twenty-fifth experiment).
        assertThat(result.repoDraftPath()).startsWith(DesignAssetService.DESIGN_DRAFT_ROOT + "/");
        verify(gitHubPullRequestService).commitFile(eq(project), contains("mockup.html"), any(), anyString());
        verify(gitHubPullRequestService).commitFile(eq(project), contains("mockup.png"), any(), anyString());
    }

    @Test
    void repoDraftPathIsBlankWhenGitHubCommitFails() {
        when(settingsService.effectiveBoolean("stitch_enabled")).thenReturn(true);
        when(stitchClient.hasStitchKey()).thenReturn(true);
        when(stitchClient.createProject(anyString())).thenReturn("123456");
        when(stitchClient.generateScreenFromText(eq("123456"), anyString(), anyString(), isNull()))
                .thenReturn(new StitchClient.GeneratedScreen(true, "ok",
                        "https://example.com/html", "https://example.com/shot.png", "screen-1", "Generated screen via Stitch."));
        when(stitchClient.download("https://example.com/html")).thenReturn("<html></html>".getBytes());
        when(stitchClient.download("https://example.com/shot.png")).thenReturn(new byte[]{1, 2, 3});
        // GitHub disabled/unreachable - commitFile fails for every call.
        when(gitHubPullRequestService.commitFile(any(), anyString(), any(), anyString())).thenReturn(false);

        DesignAssetService.DesignAssetResult result = designAssetService.generateAsset(
                project, null, "A login screen", "mockup", "fast", false
        );

        // Generation itself still succeeded (real local file), but there is no reachable repo reference -
        // callers must not hand this out as a DESIGN_MOCKUP_ASSET reference (see ProjectFlowService).
        assertThat(result.available()).isTrue();
        assertThat(result.repoDraftPath()).isEmpty();
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
        when(stitchClient.generateScreenFromText(eq("123456"), anyString(), anyString(), isNull()))
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

    @Test
    void rejectsAndDoesNotCommitAnOffTokenScreenWhenDeclaredTokensAreProvided() {
        when(settingsService.effectiveBoolean("stitch_enabled")).thenReturn(true);
        when(stitchClient.hasStitchKey()).thenReturn(true);
        when(stitchClient.createProject(anyString())).thenReturn("123456");
        when(stitchClient.generateScreenFromText(eq("123456"), anyString(), anyString(), eq("ds-42")))
                .thenReturn(new StitchClient.GeneratedScreen(true, "ok",
                        "https://example.com/html", "https://example.com/shot.png", "screen-1", "Generated screen via Stitch."));
        // real off-token colors, same as tonight's forge-factory-v2.html regression case
        when(stitchClient.download("https://example.com/html"))
                .thenReturn("<style>body{background:#090f13;color:#161c21;}</style>".getBytes());
        when(stitchClient.download("https://example.com/shot.png")).thenReturn(new byte[]{1, 2, 3});

        DesignAssetService.DesignAssetResult result = designAssetService.generateAsset(
                project, null, "A login screen", "mockup", "fast", false, "ds-42",
                java.util.List.of("#fbf9f1", "#7d8570", "#c99a2e"), java.util.List.of("IBM Plex Sans")
        );

        // generateAsset falls through to nano-banana (disabled in this test) after Stitch is
        // rejected, so the final public status is "unavailable" - the invariant that actually
        // matters is proven directly: the off-token screen is never committed to the repo.
        assertThat(result.available()).isFalse();
        verify(gitHubPullRequestService, never()).commitFile(any(), anyString(), any(), anyString());
    }

    @Test
    void acceptsAndCommitsAnOnTokenScreenWhenDeclaredTokensAreProvided() {
        when(settingsService.effectiveBoolean("stitch_enabled")).thenReturn(true);
        when(stitchClient.hasStitchKey()).thenReturn(true);
        when(stitchClient.createProject(anyString())).thenReturn("123456");
        when(stitchClient.generateScreenFromText(eq("123456"), anyString(), anyString(), eq("ds-42")))
                .thenReturn(new StitchClient.GeneratedScreen(true, "ok",
                        "https://example.com/html", "https://example.com/shot.png", "screen-1", "Generated screen via Stitch."));
        when(stitchClient.download("https://example.com/html"))
                .thenReturn("<style>body{background:#fbf9f1;color:#7d8570;}</style>".getBytes());
        when(stitchClient.download("https://example.com/shot.png")).thenReturn(new byte[]{1, 2, 3});

        DesignAssetService.DesignAssetResult result = designAssetService.generateAsset(
                project, null, "A login screen", "mockup", "fast", false, "ds-42",
                java.util.List.of("#fbf9f1", "#7d8570", "#c99a2e"), java.util.List.of("IBM Plex Sans")
        );

        assertThat(result.available()).isTrue();
        verify(gitHubPullRequestService).commitFile(eq(project), contains("mockup.html"), any(), anyString());
    }

    @Test
    void generateAssetPassesDeclaredBrandTokensToProducerPromptAndRecordsBothInMetadata() throws Exception {
        when(settingsService.effectiveBoolean("stitch_enabled")).thenReturn(true);
        when(stitchClient.hasStitchKey()).thenReturn(true);
        when(stitchClient.createProject(anyString())).thenReturn("123456");
        org.mockito.ArgumentCaptor<String> promptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        when(stitchClient.generateScreenFromText(eq("123456"), promptCaptor.capture(), anyString(), eq("ds-42")))
                .thenReturn(new StitchClient.GeneratedScreen(true, "ok",
                        "https://example.com/html", "https://example.com/shot.png", "screen-1", "Generated screen via Stitch."));
        when(stitchClient.download("https://example.com/html"))
                .thenReturn("<style>body{background:#fbf9f1;color:#7d8570;}</style>".getBytes());
        when(stitchClient.download("https://example.com/shot.png")).thenReturn(new byte[]{1, 2, 3});

        DesignAssetService.DesignAssetResult result = designAssetService.generateAsset(
                project, null, "A login screen", "mockup", "fast", false, "ds-42",
                java.util.List.of("#fbf9f1", "#7d8570"), java.util.List.of("IBM Plex Sans")
        );

        assertThat(result.available()).isTrue();
        // Producer received the declared tokens in prompt
        assertThat(promptCaptor.getValue()).contains("Brand colors: #fbf9f1, #7d8570");
        assertThat(promptCaptor.getValue()).contains("Brand fonts: IBM Plex Sans");

        // Metadata on disk captures both declared tokens and producer tokens
        assertThat(result.metadataPath()).isNotBlank();
        String metadataContent = java.nio.file.Files.readString(java.nio.file.Paths.get(result.metadataPath()));
        var node = new ObjectMapper().readTree(metadataContent);
        assertThat(node.has("declaredTokens")).isTrue();
        assertThat(node.has("producerTokens")).isTrue();
        java.util.List<String> declaredList = new java.util.ArrayList<>();
        node.path("declaredTokens").forEach(t -> declaredList.add(t.asText()));
        java.util.List<String> producerList = new java.util.ArrayList<>();
        node.path("producerTokens").forEach(t -> producerList.add(t.asText()));
        assertThat(declaredList).contains("#fbf9f1", "#7d8570", "ibm plex sans");
        assertThat(producerList).contains("#fbf9f1", "#7d8570", "ibm plex sans");
    }

    @Test
    void rejectionMessageIncludesBothDeclaredTokensAndProducerTokens() {
        when(settingsService.effectiveBoolean("stitch_enabled")).thenReturn(true);
        when(stitchClient.hasStitchKey()).thenReturn(true);
        when(stitchClient.createProject(anyString())).thenReturn("123456");
        when(stitchClient.generateScreenFromText(eq("123456"), anyString(), anyString(), eq("ds-42")))
                .thenReturn(new StitchClient.GeneratedScreen(true, "ok",
                        "https://example.com/html", "https://example.com/shot.png", "screen-1", "Generated screen via Stitch."));
        when(stitchClient.download("https://example.com/html"))
                .thenReturn("<style>body{background:#090f13;color:#161c21;}</style>".getBytes());
        when(stitchClient.download("https://example.com/shot.png")).thenReturn(new byte[]{1, 2, 3});

        DesignAssetService.DesignAssetResult result = designAssetService.generateAsset(
                project, null, "A login screen", "mockup", "fast", false, "ds-42",
                java.util.List.of("#fbf9f1", "#7d8570"), java.util.List.of("IBM Plex Sans"), true
        );

        assertThat(result.available()).isFalse();
        assertThat(result.message()).contains("Declared tokens:");
        assertThat(result.message()).contains("Producer tokens:");
        assertThat(result.message()).contains("#fbf9f1");
    }

    @Test
    void auditExistingDraftsExposesBothDeclaredTokensAndProducerTokensInReport() throws Exception {
        java.nio.file.Path projectDir = tempDir.resolve("test-project");
        java.nio.file.Files.createDirectories(projectDir);
        java.nio.file.Files.writeString(projectDir.resolve("mockup-1.html"),
                "<style>body{background:#fbf9f1;color:#7d8570;}</style>");
        var meta = new ObjectMapper().createObjectNode();
        var prod = meta.putArray("producerTokens");
        prod.add("#fbf9f1");
        prod.add("#7d8570");
        java.nio.file.Files.writeString(projectDir.resolve("mockup-1.json"), meta.toString());

        var reports = designAssetService.auditExistingDrafts(
                project,
                java.util.List.of("mockup-1"),
                java.util.List.of("#fbf9f1", "#7d8570"),
                java.util.List.of("IBM Plex Sans")
        );

        assertThat(reports).containsKey("mockup-1");
        var report = reports.get("mockup-1");
        assertThat(report.declaredTokens()).contains("#fbf9f1", "#7d8570", "ibm plex sans");
        assertThat(report.producerTokens()).contains("#fbf9f1", "#7d8570");
    }

    @Test
    void whenDeclaredTokensProvidedAndScreenIsSpaShellWithoutStylesPassesAsUnauditedWithoutAestheticDriftRejection() throws Exception {
        // Prescription 28 residual fence (CANNOT_JUDGE / UNDECIDABLE):
        // If an SPA shell has no extractable CSS tokens, the audit returns CANNOT_JUDGE.
        // It must NOT be rejected as "aesthetic_drift" (which was false red burning generation budget).
        // Instead, it passes through as an un-audited screen with auditVerdict="CANNOT_JUDGE" recorded in metadata.
        when(settingsService.effectiveBoolean("stitch_enabled")).thenReturn(true);
        when(stitchClient.hasStitchKey()).thenReturn(true);
        when(stitchClient.createProject(anyString())).thenReturn("123456");
        when(stitchClient.generateScreenFromText(eq("123456"), anyString(), anyString(), eq("ds-42")))
                .thenReturn(new StitchClient.GeneratedScreen(true, "ok",
                        "https://example.com/html", "https://example.com/shot.png", "screen-1", "Generated screen via Stitch."));
        // Raw SPA shell with no CSS styles
        when(stitchClient.download("https://example.com/html"))
                .thenReturn("<!DOCTYPE html><html><body><div id=\"root\"></div><script src=\"app.js\"></script></body></html>".getBytes());
        when(stitchClient.download("https://example.com/shot.png")).thenReturn(new byte[]{1, 2, 3});

        DesignAssetService.DesignAssetResult result = designAssetService.generateAsset(
                project, null, "A login screen", "mockup", "fast", false, "ds-42",
                java.util.List.of("#fbf9f1", "#7d8570"), java.util.List.of("IBM Plex Sans")
        );

        // Crucial falsification assertion: must NOT be rejected as aesthetic_drift
        assertThat(result.available()).isTrue();
        assertThat(result.status()).isEqualTo("ok");
        assertThat(result.model()).isEqualTo("stitch");
        verify(gitHubPullRequestService).commitFile(eq(project), contains("mockup.html"), any(), anyString());

        // Metadata records CANNOT_JUDGE verdict
        assertThat(result.metadataPath()).isNotBlank();
        String metadataContent = java.nio.file.Files.readString(java.nio.file.Paths.get(result.metadataPath()));
        var node = new ObjectMapper().readTree(metadataContent);
        assertThat(node.path("auditVerdict").asText()).isEqualTo("CANNOT_JUDGE");
        assertThat(node.path("auditVerdictDisplay").asText()).isEqualTo("не могу судить");
    }

    private String base64Png() {
        return java.util.Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
    }
}
