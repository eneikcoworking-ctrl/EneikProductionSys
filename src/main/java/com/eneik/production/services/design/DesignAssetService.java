package com.eneik.production.services.design;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.services.dashboard.ProjectOperationalContextService.ProjectOperationalContext;
import com.eneik.production.services.googleai.GoogleAiResourceService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.stitch.StitchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
public class DesignAssetService {
    private static final Logger log = LoggerFactory.getLogger(DesignAssetService.class);
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    public static final String DESIGN_DRAFT_ROOT = "design/draft";
    public static final String DESIGN_APPROVED_ROOT = "design/approved";

    private final GoogleAiResourceService googleAiResourceService;
    private final StitchClient stitchClient;
    private final SystemSettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final com.eneik.production.services.github.GitHubPullRequestService gitHubPullRequestService;
    private final Path assetRoot;

    public DesignAssetService(GoogleAiResourceService googleAiResourceService,
                              StitchClient stitchClient,
                              SystemSettingsService settingsService,
                              ObjectMapper objectMapper,
                              com.eneik.production.services.github.GitHubPullRequestService gitHubPullRequestService,
                              @Value("${design-service.asset-root:./data/design-assets}") String assetRoot) {
        this.googleAiResourceService = googleAiResourceService;
        this.stitchClient = stitchClient;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.gitHubPullRequestService = gitHubPullRequestService;
        this.assetRoot = Paths.get(assetRoot == null || assetRoot.isBlank() ? "./data/design-assets" : assetRoot)
                .toAbsolutePath()
                .normalize();
    }

    /**
     * Commits a design asset's real bytes into the project's actual GitHub repository under
     * design/draft/{basename}/ - local disk (./data/design-assets/...) is only reachable from the Eneik
     * backend's own container, never from a Jules session (which only ever sees its GitHub checkout).
     * Without this, DESIGN_MOCKUP_ASSET references handed to implementer/reviewer tasks were dead paths -
     * confirmed live in the test-twenty-fifth experiment. Best-effort: returns "" (not an error) if GitHub
     * isn't configured, since local generation still succeeded and the caller can fall back to that.
     */
    /**
     * Removes rejected draft folders from design/draft/ on the repo's main branch - a plain forward
     * commit per file (never a history rewrite). Each basename maps to the same
     * design/draft/{basename}/ layout commitDraftToGitHub writes: mockup.html (if present) and
     * mockup.png or mockup.jpg. Tries all known extensions per basename since the caller does not
     * track which one was actually generated; deleteFile treats a missing file as success.
     */
    public java.util.Map<String, Boolean> deleteDraftFolders(ProjectEntity project, List<String> basenames) {
        java.util.Map<String, Boolean> results = new java.util.LinkedHashMap<>();
        for (String basename : basenames) {
            String dir = DESIGN_DRAFT_ROOT + "/" + basename;
            boolean htmlOk = gitHubPullRequestService.deleteFile(project, dir + "/mockup.html",
                    "Remove rejected design draft: " + basename);
            boolean pngOk = gitHubPullRequestService.deleteFile(project, dir + "/mockup.png",
                    "Remove rejected design draft: " + basename);
            boolean jpgOk = gitHubPullRequestService.deleteFile(project, dir + "/mockup.jpg",
                    "Remove rejected design draft: " + basename);
            results.put(basename, htmlOk && pngOk && jpgOk);
        }
        return results;
    }

    private String commitDraftToGitHub(ProjectEntity project, String basename, byte[] htmlBytes, byte[] imageBytes, String imageExtension) {
        if (project == null) {
            return "";
        }
        String draftDir = DESIGN_DRAFT_ROOT + "/" + basename;
        boolean committedAny = false;
        if (htmlBytes != null && htmlBytes.length > 0) {
            committedAny |= gitHubPullRequestService.commitFile(project, draftDir + "/mockup.html", htmlBytes,
                    "Add design draft: " + basename);
        }
        if (imageBytes != null && imageBytes.length > 0) {
            committedAny |= gitHubPullRequestService.commitFile(project, draftDir + "/mockup" + imageExtension, imageBytes,
                    "Add design draft screenshot: " + basename);
        }
        return committedAny ? draftDir : "";
    }

    public DesignAssetResult generateAsset(ProjectEntity project,
                                           ProjectOperationalContext context,
                                           String brief,
                                           String assetType,
                                           String quality,
                                           boolean useGoogleSearch) {
        return generateAsset(project, context, brief, assetType, quality, useGoogleSearch, null);
    }

    /** Same as {@link #generateAsset}, reusing an existing Stitch design system id for cross-screen consistency. */
    public DesignAssetResult generateAsset(ProjectEntity project,
                                           ProjectOperationalContext context,
                                           String brief,
                                           String assetType,
                                           String quality,
                                           boolean useGoogleSearch,
                                           String designSystemId) {
        if (!settingsService.effectiveBoolean("design_service_enabled")) {
            log.info("DesignAssetService: design service is disabled; skipping mockup generation for project {}",
                    project == null ? "unknown" : project.getId());
            return DesignAssetResult.unavailable("Design service is disabled.");
        }

        // Stitch generates real UI screens (HTML + screenshot) from Gemini models but is billed/
        // rate-limited independently from the main Gemini Developer API prepay balance, so it is
        // preferred here when configured - it keeps working even when the nano-banana/Gemini image
        // path is blocked by a depleted prepay balance.
        if (settingsService.effectiveBoolean("stitch_enabled") && stitchClient.hasStitchKey()) {
            DesignAssetResult stitchResult = generateViaStitch(project, brief, assetType, designSystemId);
            if (stitchResult.available()) {
                return stitchResult;
            }
            log.warn("DesignAssetService: Stitch generation failed ({}), falling back to nano-banana: {}",
                    stitchResult.status(), stitchResult.message());
        }

        if (!settingsService.effectiveBoolean("nano_banana_enabled")) {
            log.info("DesignAssetService: Nano Banana image generation is disabled; skipping mockup generation for project {}",
                    project == null ? "unknown" : project.getId());
            return DesignAssetResult.unavailable("Nano Banana image generation is disabled.");
        }
        if (!googleAiResourceService.hasGoogleAiKey()) {
            log.warn("DesignAssetService: Gemini API key is not configured; skipping mockup generation for project {}",
                    project == null ? "unknown" : project.getId());
            return DesignAssetResult.unavailable("Gemini API key is not configured.");
        }

        String normalizedQuality = quality == null ? "" : quality.toLowerCase(Locale.ROOT);
        String model = normalizedQuality.contains("pro")
                ? googleAiResourceService.model("nano_banana_pro_model", "gemini-3-pro-image")
                : googleAiResourceService.model("nano_banana_model", "gemini-3.1-flash-image");
        boolean allowSearch = useGoogleSearch && settingsService.effectiveBoolean("google_search_grounding_enabled");

        String prompt = designPrompt(project, context, brief, assetType, normalizedQuality);
        var interaction = googleAiResourceService.callInteraction(
                model,
                prompt,
                allowSearch ? List.of("google_search") : List.of()
        );
        if (!interaction.available()) {
            log.warn("DesignAssetService: mockup generation via model {} failed (status={}): {}",
                    model, interaction.status(), interaction.outputText());
            return new DesignAssetResult(false, interaction.status(), model, "", "", "", interaction.outputText(), "");
        }
        if (interaction.outputImageBase64().isBlank()) {
            log.warn("DesignAssetService: model {} returned no image block for project {}",
                    model, project == null ? "unknown" : project.getId());
            return new DesignAssetResult(
                    false,
                    "no_image",
                    model,
                    "",
                    "",
                    "",
                    "The image model returned no image block. Text output: " + interaction.outputText()
                            + "\nRaw preview: " + interaction.rawPreview(),
                    ""
            );
        }

        try {
            String projectSlug = slug(project == null ? "unknown-project" : firstNonBlank(project.getSlug(), project.getName(), project.getId().toString()));
            String kind = slug(firstNonBlank(assetType, "asset"));
            Path directory = assetRoot.resolve(projectSlug).normalize();
            Files.createDirectories(directory);
            String basename = FILE_TIME.format(Instant.now()) + "-" + kind;
            String extension = extension(interaction.outputImageMimeType());
            Path imagePath = directory.resolve(basename + extension).normalize();
            Path metadataPath = directory.resolve(basename + ".json").normalize();

            byte[] imageBytes = Base64.getDecoder().decode(interaction.outputImageBase64());
            Files.write(imagePath, imageBytes);

            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("projectId", project == null ? "" : String.valueOf(project.getId()));
            metadata.put("projectName", project == null ? "" : project.getName());
            metadata.put("model", model);
            metadata.put("assetType", assetType == null ? "" : assetType);
            metadata.put("quality", normalizedQuality.isBlank() ? "fast" : normalizedQuality);
            metadata.put("googleSearchUsed", allowSearch);
            metadata.put("mimeType", interaction.outputImageMimeType());
            metadata.put("createdAt", Instant.now().toString());
            metadata.put("brief", brief == null ? "" : brief);
            metadata.put("textOutput", interaction.outputText());
            Files.writeString(metadataPath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(metadata), StandardCharsets.UTF_8);

            String repoDraftPath = commitDraftToGitHub(project, basename, null, imageBytes, extension);

            return new DesignAssetResult(
                    true,
                    "ok",
                    model,
                    imagePath.toString(),
                    metadataPath.toString(),
                    interaction.outputImageMimeType(),
                    "Generated design asset and metadata.",
                    repoDraftPath
            );
        } catch (Exception e) {
            log.warn("DesignAssetService: failed to write generated mockup to disk: {}", e.getMessage());
            return new DesignAssetResult(false, "write_error", model, "", "", "", e.getMessage(), "");
        }
    }

    private DesignAssetResult generateViaStitch(ProjectEntity project, String brief, String assetType, String designSystemId) {
        String title = project == null ? "Eneik design" : firstNonBlank(project.getName(), project.getSlug(), "Eneik design");
        String stitchProjectId = stitchClient.createProject(title);
        if (stitchProjectId == null) {
            return new DesignAssetResult(false, "stitch_project_error", "stitch", "", "", "",
                    "Stitch did not return a project ID for create_project.", "");
        }

        String prompt = brief == null || brief.isBlank()
                ? "Create a UI screen for: " + firstNonBlank(assetType, "the current project feature")
                : brief;
        StitchClient.GeneratedScreen screen = stitchClient.generateScreenFromText(stitchProjectId, prompt, "GEMINI_3_FLASH", designSystemId);
        if (!screen.available()) {
            return new DesignAssetResult(false, screen.status(), "stitch", "", "", "", screen.message(), "");
        }
        // 2026-08-10: the immediate generate response reliably carries the screenshot before the full
        // HTML/CSS code synthesis has finished - confirmed live (5/5 real design calls came back
        // htmlCode-empty on the first response) and against Stitch's own documented get_screen flow.
        // Poll the same screen a bounded number of times instead of accepting "image only" as final.
        log.info("DesignAssetService: generate_screen_from_text returned screenId='{}' htmlUrl-blank={} screenshotUrl-blank={}",
                screen.screenId(), screen.htmlDownloadUrl().isBlank(), screen.screenshotDownloadUrl().isBlank());
        if (screen.htmlDownloadUrl().isBlank() && !screen.screenId().isBlank()) {
            // 2026-08-10 diagnostic run (operator's explicit request for a genuinely patient, thorough
            // test before concluding anything): 20 attempts x 15s = up to 5 real minutes, not 1.
            for (int attempt = 0; attempt < 20 && screen.htmlDownloadUrl().isBlank(); attempt++) {
                try {
                    Thread.sleep(15_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                StitchClient.GeneratedScreen polled = stitchClient.getScreen(stitchProjectId, screen.screenId());
                log.info("DesignAssetService: get_screen poll attempt {} -> available={} htmlUrl-blank={} status={} message={}",
                        attempt, polled.available(), polled.htmlDownloadUrl().isBlank(), polled.status(), polled.message());
                if (polled.available() && !polled.htmlDownloadUrl().isBlank()) {
                    screen = polled;
                }
            }
        }

        try {
            String projectSlug = slug(project == null ? "unknown-project" : firstNonBlank(project.getSlug(), project.getName(), project.getId().toString()));
            String kind = slug(firstNonBlank(assetType, "asset"));
            Path directory = assetRoot.resolve(projectSlug).normalize();
            Files.createDirectories(directory);
            String basename = FILE_TIME.format(Instant.now()) + "-" + kind;

            String imagePath = "";
            byte[] screenshotBytes = null;
            if (!screen.screenshotDownloadUrl().isBlank()) {
                screenshotBytes = stitchClient.download(screen.screenshotDownloadUrl());
                if (screenshotBytes != null) {
                    Path resolvedImagePath = directory.resolve(basename + ".png").normalize();
                    Files.write(resolvedImagePath, screenshotBytes);
                    imagePath = resolvedImagePath.toString();
                }
            }

            String htmlPath = "";
            byte[] htmlFileBytes = null;
            if (!screen.htmlDownloadUrl().isBlank()) {
                htmlFileBytes = stitchClient.download(screen.htmlDownloadUrl());
                if (htmlFileBytes != null) {
                    Path resolvedHtmlPath = directory.resolve(basename + ".html").normalize();
                    Files.write(resolvedHtmlPath, htmlFileBytes);
                    htmlPath = resolvedHtmlPath.toString();
                }
            }

            if (imagePath.isBlank() && htmlPath.isBlank()) {
                return new DesignAssetResult(false, "stitch_download_error", "stitch", "", "", "",
                        "Stitch generated a screen but its files could not be downloaded.", "");
            }

            Path metadataPath = directory.resolve(basename + ".json").normalize();
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("projectId", project == null ? "" : String.valueOf(project.getId()));
            metadata.put("projectName", project == null ? "" : project.getName());
            metadata.put("provider", "stitch");
            metadata.put("stitchProjectId", stitchProjectId);
            metadata.put("assetType", assetType == null ? "" : assetType);
            metadata.put("createdAt", Instant.now().toString());
            metadata.put("brief", brief == null ? "" : brief);
            metadata.put("htmlPath", htmlPath);
            Files.writeString(metadataPath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(metadata), StandardCharsets.UTF_8);

            String repoDraftPath = commitDraftToGitHub(project, basename, htmlFileBytes, screenshotBytes, ".png");

            return new DesignAssetResult(
                    true,
                    "ok",
                    "stitch",
                    imagePath.isBlank() ? htmlPath : imagePath,
                    metadataPath.toString(),
                    imagePath.isBlank() ? "text/html" : "image/png",
                    "Generated design asset via Stitch.",
                    repoDraftPath
            );
        } catch (Exception e) {
            log.warn("DesignAssetService: failed to write Stitch-generated asset to disk: {}", e.getMessage());
            return new DesignAssetResult(false, "write_error", "stitch", "", "", "", e.getMessage(), "");
        }
    }

    private String designPrompt(ProjectEntity project,
                                ProjectOperationalContext context,
                                String brief,
                                String assetType,
                                String quality) {
        return """
                You are Eneik Design Asset Service.
                Produce one production-ready visual asset for the selected project.

                HARD RULES:
                - Work in English.
                - Return image output, not only text.
                - Do not invent a brand logo unless the brief explicitly asks for one.
                - Avoid unreadable tiny text. If text is needed, keep it short and high contrast.
                - Make the asset directly useful for the project, not generic stock imagery.
                - Prefer clean domain-specific composition over decorative gradients.
                - No unrelated mascots, fantasy creatures, or joke imagery.

                PROJECT:
                - name: %s
                - repository: %s

                ASSET:
                - type: %s
                - quality: %s

                BRIEF:
                %s

                PROJECT FACTS:
                %s
                """.formatted(
                project == null ? "" : project.getName(),
                project == null ? "" : firstNonBlank(project.getRepositoryName(), project.getRepositoryUrl(), project.getRepoUrl()),
                firstNonBlank(assetType, "project visual asset"),
                quality == null || quality.isBlank() ? "fast" : quality,
                brief == null || brief.isBlank() ? "Create a useful visual asset for the current project." : brief,
                context == null ? "" : preview(context.promptJson(), 6_000)
        );
    }

    private String extension(String mimeType) {
        String lower = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        if (lower.contains("jpeg") || lower.contains("jpg")) {
            return ".jpg";
        }
        if (lower.contains("webp")) {
            return ".webp";
        }
        return ".png";
    }

    private String slug(String value) {
        String clean = value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        return clean.isBlank() ? "asset" : clean;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String preview(String value, int maxChars) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= maxChars ? compact : compact.substring(0, Math.max(0, maxChars - 15)) + "... [truncated]";
    }

    public record DesignAssetResult(
            boolean available,
            String status,
            String model,
            String imagePath,
            String metadataPath,
            String mimeType,
            String message,
            String repoDraftPath
    ) {
        static DesignAssetResult unavailable(String reason) {
            return new DesignAssetResult(false, "unavailable", "", "", "", "", reason, "");
        }
    }
}
