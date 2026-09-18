package com.eneik.production.controllers.ai;

import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.services.dashboard.ProjectOperationalContextService;
import com.eneik.production.services.design.DesignAssetService;
import com.eneik.production.services.googleai.GoogleAiResourceService;
import com.eneik.production.services.stitch.StitchClient;
import com.eneik.production.services.video.VideoAssetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class GoogleAiResourceControllerTest {

    private final GoogleAiResourceService googleAiResourceService = mock(GoogleAiResourceService.class);
    private final DesignAssetService designAssetService = mock(DesignAssetService.class);
    private final VideoAssetService videoAssetService = mock(VideoAssetService.class);
    private final ProjectOperationalContextService contextService = mock(ProjectOperationalContextService.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final StitchClient stitchClient = mock(StitchClient.class);

    private final GoogleAiResourceController controller = new GoogleAiResourceController(
            googleAiResourceService,
            designAssetService,
            videoAssetService,
            contextService,
            projectRepository,
            stitchClient
    );

    @Test
    @DisplayName("Path traversal attack via projectSlug is sanitized and rejected (H3 audit finding)")
    void listVideoAssetsPreventsPathTraversal() {
        // Attempt traversal to parent directories
        List<Map<String, String>> resultDotDot = controller.listVideoAssets("..");
        assertNotNull(resultDotDot);
        assertTrue(resultDotDot.isEmpty(), "Path traversal '..' must return empty list and never list root data folder");

        List<Map<String, String>> resultNested = controller.listVideoAssets("../../etc");
        assertNotNull(resultNested);
        assertTrue(resultNested.isEmpty(), "Nested traversal must return empty list");

        List<Map<String, String>> resultNonExistent = controller.listVideoAssets("non-existent-project-xyz");
        assertNotNull(resultNonExistent);
        assertTrue(resultNonExistent.isEmpty());
    }

    @Test
    @DisplayName("designDraftsCleanup returns 404 NOT_FOUND when projectId does not exist")
    void designDraftsCleanupReturnsNotFoundForMissingProject() {
        java.util.UUID missingId = java.util.UUID.randomUUID();
        org.mockito.Mockito.when(projectRepository.findById(missingId)).thenReturn(java.util.Optional.empty());

        org.springframework.http.ResponseEntity<?> response = controller.designDraftsCleanup(missingId, List.of("draft1"));
        org.junit.jupiter.api.Assertions.assertEquals(404, response.getStatusCode().value());
        org.mockito.Mockito.verifyNoInteractions(designAssetService);
    }

    @Test
    @DisplayName("generateDesignAsset returns 404 NOT_FOUND when projectId does not exist")
    void generateDesignAssetReturnsNotFoundForMissingProject() {
        java.util.UUID missingId = java.util.UUID.randomUUID();
        org.mockito.Mockito.when(projectRepository.findById(missingId)).thenReturn(java.util.Optional.empty());

        org.springframework.http.ResponseEntity<?> response = controller.generateDesignAsset(missingId,
                new GoogleAiResourceController.DesignAssetRequest("brief", "asset", "fast", false, null, null, null));
        org.junit.jupiter.api.Assertions.assertEquals(404, response.getStatusCode().value());
        org.mockito.Mockito.verifyNoInteractions(designAssetService);
    }

    @Test
    @DisplayName("generateVideoAsset returns 404 NOT_FOUND when projectId does not exist")
    void generateVideoAssetReturnsNotFoundForMissingProject() {
        java.util.UUID missingId = java.util.UUID.randomUUID();
        org.mockito.Mockito.when(projectRepository.findById(missingId)).thenReturn(java.util.Optional.empty());

        org.springframework.http.ResponseEntity<?> response = controller.generateVideoAsset(missingId,
                new GoogleAiResourceController.VideoAssetRequest("brief", "video", "standard", false));
        org.junit.jupiter.api.Assertions.assertEquals(404, response.getStatusCode().value());
        org.mockito.Mockito.verifyNoInteractions(videoAssetService);
    }

    @Test
    @DisplayName("probeModels delegates to GoogleAiResourceService")
    void probeModelsDelegatesToService() {
        org.mockito.Mockito.when(googleAiResourceService.probeModels()).thenReturn(Map.of("status", "ok"));

        Map<String, Object> result = controller.probeModels();
        org.junit.jupiter.api.Assertions.assertEquals("ok", result.get("status"));
        org.mockito.Mockito.verify(googleAiResourceService).probeModels();
    }

    @Test
    @DisplayName("createStitchDesignSystem delegates to StitchClient")
    void createStitchDesignSystemDelegatesToClient() {
        GoogleAiResourceController.CreateDesignSystemRequest req =
                new GoogleAiResourceController.CreateDesignSystemRequest("My System", "Inter", "Roboto", "#000", "#fff");
        org.mockito.Mockito.when(stitchClient.createDesignSystem(org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("My System"),
                org.mockito.ArgumentMatchers.eq("Inter"),
                org.mockito.ArgumentMatchers.eq("Roboto"),
                org.mockito.ArgumentMatchers.eq("#000"),
                org.mockito.ArgumentMatchers.eq("#fff")))
                .thenReturn(new StitchClient.DesignSystemResult(true, "ok", "sys-123", "Created"));

        org.springframework.http.ResponseEntity<?> response = controller.createStitchDesignSystem(req);
        org.junit.jupiter.api.Assertions.assertEquals(200, response.getStatusCode().value());
        org.mockito.Mockito.verify(stitchClient).createDesignSystem(null, "My System", "Inter", "Roboto", "#000", "#fff");
    }
}
