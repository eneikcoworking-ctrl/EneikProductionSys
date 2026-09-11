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
}
