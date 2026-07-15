package com.eneik.production.services.video;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.services.dashboard.ProjectOperationalContextService.ProjectOperationalContext;
import com.eneik.production.services.googleai.GoogleAiResourceService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
public class VideoAssetService {
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final GoogleAiResourceService googleAiResourceService;
    private final SystemSettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final Path assetRoot;

    public VideoAssetService(GoogleAiResourceService googleAiResourceService,
                             SystemSettingsService settingsService,
                             ObjectMapper objectMapper,
                             @Value("${video-service.asset-root:./data/video-assets}") String assetRoot) {
        this.googleAiResourceService = googleAiResourceService;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.assetRoot = Paths.get(assetRoot == null || assetRoot.isBlank() ? "./data/video-assets" : assetRoot)
                .toAbsolutePath()
                .normalize();
    }

    public VideoAssetResult generateAsset(ProjectEntity project,
                                          ProjectOperationalContext context,
                                          String brief,
                                          String assetType,
                                          String quality,
                                          boolean useGoogleSearch) {
        if (!settingsService.effectiveBoolean("veo_enabled")) {
            return VideoAssetResult.unavailable("Veo video generation is disabled.");
        }
        if (!googleAiResourceService.hasGoogleAiKey()) {
            return VideoAssetResult.unavailable("Gemini API key is not configured.");
        }

        String normalizedQuality = quality == null || quality.isBlank() ? "standard" : quality.toLowerCase(Locale.ROOT);
        String model = googleAiResourceService.model("veo_model", "veo-3.1-generate-preview");
        boolean allowSearch = useGoogleSearch && settingsService.effectiveBoolean("google_search_grounding_enabled");
        String prompt = videoPrompt(project, context, brief, assetType, normalizedQuality);
        var interaction = googleAiResourceService.callInteraction(
                model,
                prompt,
                allowSearch ? List.of("google_search", "video_generation") : List.of("video_generation")
        );
        if (!interaction.available()) {
            return new VideoAssetResult(false, interaction.status(), model, "", "", "", interaction.outputText());
        }

        try {
            String projectSlug = slug(project == null ? "unknown-project" : firstNonBlank(project.getSlug(), project.getName(), project.getId().toString()));
            String kind = slug(firstNonBlank(assetType, "video"));
            Path directory = assetRoot.resolve(projectSlug).normalize();
            Files.createDirectories(directory);
            String basename = FILE_TIME.format(Instant.now()) + "-" + kind;
            Path metadataPath = directory.resolve(basename + ".json").normalize();

            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("projectId", project == null ? "" : String.valueOf(project.getId()));
            metadata.put("projectName", project == null ? "" : project.getName());
            metadata.put("model", model);
            metadata.put("assetType", assetType == null ? "" : assetType);
            metadata.put("quality", normalizedQuality);
            metadata.put("googleSearchUsed", allowSearch);
            metadata.put("createdAt", Instant.now().toString());
            metadata.put("brief", brief == null ? "" : brief);
            metadata.put("textOutput", interaction.outputText());
            metadata.put("rawPreview", interaction.rawPreview());

            if (interaction.outputVideoBase64().isBlank()) {
                metadata.put("status", "no_video");
                Files.writeString(metadataPath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(metadata), StandardCharsets.UTF_8);
                return new VideoAssetResult(
                        false,
                        "no_video",
                        model,
                        "",
                        metadataPath.toString(),
                        "",
                        "The video model returned no video media block. Metadata and raw preview were saved."
                );
            }

            String mimeType = interaction.outputVideoMimeType().isBlank() ? "video/mp4" : interaction.outputVideoMimeType();
            String extension = extension(mimeType);
            Path videoPath = directory.resolve(basename + extension).normalize();
            byte[] videoBytes = Base64.getDecoder().decode(interaction.outputVideoBase64());
            Files.write(videoPath, videoBytes);

            metadata.put("status", "ok");
            metadata.put("mimeType", mimeType);
            metadata.put("videoPath", videoPath.toString());
            Files.writeString(metadataPath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(metadata), StandardCharsets.UTF_8);

            return new VideoAssetResult(
                    true,
                    "ok",
                    model,
                    videoPath.toString(),
                    metadataPath.toString(),
                    mimeType,
                    "Generated video asset and metadata."
            );
        } catch (Exception e) {
            return new VideoAssetResult(false, "write_error", model, "", "", "", e.getMessage());
        }
    }

    private String videoPrompt(ProjectEntity project,
                               ProjectOperationalContext context,
                               String brief,
                               String assetType,
                               String quality) {
        return """
                You are Eneik Video Asset Service.
                Produce one short, production-ready project video asset.

                HARD RULES:
                - Work in English.
                - Return video output when the model/tool supports it.
                - Keep the result useful for the selected project: demo, onboarding, promo, walkthrough, or user-flow explanation.
                - Avoid tiny unreadable text. If text appears, keep it short and high contrast.
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
                firstNonBlank(assetType, "project video asset"),
                quality == null || quality.isBlank() ? "standard" : quality,
                brief == null || brief.isBlank() ? "Create a short useful video asset for the current project." : brief,
                context == null ? "" : preview(context.promptJson(), 6_000)
        );
    }

    private String extension(String mimeType) {
        String lower = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        if (lower.contains("webm")) {
            return ".webm";
        }
        if (lower.contains("quicktime") || lower.contains("mov")) {
            return ".mov";
        }
        return ".mp4";
    }

    private String slug(String value) {
        String clean = value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        return clean.isBlank() ? "video" : clean;
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

    public record VideoAssetResult(
            boolean available,
            String status,
            String model,
            String videoPath,
            String metadataPath,
            String mimeType,
            String message
    ) {
        static VideoAssetResult unavailable(String reason) {
            return new VideoAssetResult(false, "unavailable", "", "", "", "", reason);
        }
    }
}
