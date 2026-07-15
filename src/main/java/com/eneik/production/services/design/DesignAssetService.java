package com.eneik.production.services.design;

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
public class DesignAssetService {
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final GoogleAiResourceService googleAiResourceService;
    private final SystemSettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final Path assetRoot;

    public DesignAssetService(GoogleAiResourceService googleAiResourceService,
                              SystemSettingsService settingsService,
                              ObjectMapper objectMapper,
                              @Value("${design-service.asset-root:./data/design-assets}") String assetRoot) {
        this.googleAiResourceService = googleAiResourceService;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.assetRoot = Paths.get(assetRoot == null || assetRoot.isBlank() ? "./data/design-assets" : assetRoot)
                .toAbsolutePath()
                .normalize();
    }

    public DesignAssetResult generateAsset(ProjectEntity project,
                                           ProjectOperationalContext context,
                                           String brief,
                                           String assetType,
                                           String quality,
                                           boolean useGoogleSearch) {
        if (!settingsService.effectiveBoolean("design_service_enabled")) {
            return DesignAssetResult.unavailable("Design service is disabled.");
        }
        if (!settingsService.effectiveBoolean("nano_banana_enabled")) {
            return DesignAssetResult.unavailable("Nano Banana image generation is disabled.");
        }
        if (!googleAiResourceService.hasGoogleAiKey()) {
            return DesignAssetResult.unavailable("Google AI key is not configured.");
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
            return new DesignAssetResult(false, interaction.status(), model, "", "", "", interaction.outputText());
        }
        if (interaction.outputImageBase64().isBlank()) {
            return new DesignAssetResult(
                    false,
                    "no_image",
                    model,
                    "",
                    "",
                    "",
                    "The image model returned no image block. Text output: " + interaction.outputText()
                            + "\nRaw preview: " + interaction.rawPreview()
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

            return new DesignAssetResult(
                    true,
                    "ok",
                    model,
                    imagePath.toString(),
                    metadataPath.toString(),
                    interaction.outputImageMimeType(),
                    "Generated design asset and metadata."
            );
        } catch (Exception e) {
            return new DesignAssetResult(false, "write_error", model, "", "", "", e.getMessage());
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
            String message
    ) {
        static DesignAssetResult unavailable(String reason) {
            return new DesignAssetResult(false, "unavailable", "", "", "", "", reason);
        }
    }
}
