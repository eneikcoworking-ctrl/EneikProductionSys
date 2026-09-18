package com.eneik.production.controllers.ai;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.services.design.DesignAssetService;
import com.eneik.production.services.googleai.GoogleAiResourceService;
import com.eneik.production.services.stitch.StitchClient;
import com.eneik.production.services.video.VideoAssetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full Spring MVC integration security tests verifying that all 5 mutating surfaces
 * of GoogleAiResourceController are unreachable without valid operator credentials,
 * enforcing DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX and BOUNDARY_TOPOLOGY (Section XXXVIII, D006).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GoogleAiResourceSecurityIntegrationTest {

    private static final String VALID_KEY = "eneik-test-secret-key-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GoogleAiResourceService googleAiResourceService;

    @MockBean
    private DesignAssetService designAssetService;

    @MockBean
    private VideoAssetService videoAssetService;

    @MockBean
    private StitchClient stitchClient;

    private UUID testProjectId;

    @BeforeEach
    void setUp() {
        ProjectEntity project = new ProjectEntity();
        project.setName("AI Test Project");
        project.setSlug("ai-test-project-" + UUID.randomUUID());
        project.setRepositoryName("ai-test-project");
        testProjectId = projectRepository.save(project).getId();
    }

    @Test
    @DisplayName("1. POST /design-drafts-cleanup without credentials is rejected with 401 UNAUTHORIZED")
    void designDraftsCleanupWithoutCredentialsIsRejectedWith401() throws Exception {
        mockMvc.perform(post("/api/ai/resources/design-drafts-cleanup")
                        .param("projectId", testProjectId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of("draft-1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error").value(containsString("Authorization required")));

        verifyNoInteractions(designAssetService);
    }

    @Test
    @DisplayName("2. POST /probe-models without credentials is rejected with 401 UNAUTHORIZED")
    void probeModelsWithoutCredentialsIsRejectedWith401() throws Exception {
        mockMvc.perform(post("/api/ai/resources/probe-models"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error").value(containsString("Authorization required")));

        verifyNoInteractions(googleAiResourceService);
    }

    @Test
    @DisplayName("3. POST /design-assets without credentials is rejected with 401 UNAUTHORIZED")
    void generateDesignAssetWithoutCredentialsIsRejectedWith401() throws Exception {
        var req = new GoogleAiResourceController.DesignAssetRequest(
                "Design a modern dark header", "asset", "fast", false, null, null, null);

        mockMvc.perform(post("/api/ai/resources/design-assets")
                        .param("projectId", testProjectId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error").value(containsString("Authorization required")));

        verifyNoInteractions(designAssetService);
    }

    @Test
    @DisplayName("4. POST /stitch-design-system without credentials is rejected with 401 UNAUTHORIZED")
    void createStitchDesignSystemWithoutCredentialsIsRejectedWith401() throws Exception {
        var req = new GoogleAiResourceController.CreateDesignSystemRequest(
                "Brand System", "Inter", "Roboto", "#111111", "#eeeeee");

        mockMvc.perform(post("/api/ai/resources/stitch-design-system")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error").value(containsString("Authorization required")));

        verifyNoInteractions(stitchClient);
    }

    @Test
    @DisplayName("5. POST /video-assets without credentials is rejected with 401 UNAUTHORIZED")
    void generateVideoAssetWithoutCredentialsIsRejectedWith401() throws Exception {
        var req = new GoogleAiResourceController.VideoAssetRequest(
                "Short 5s intro animation", "video", "standard", false);

        mockMvc.perform(post("/api/ai/resources/video-assets")
                        .param("projectId", testProjectId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error").value(containsString("Authorization required")));

        verifyNoInteractions(videoAssetService);
    }

    @Test
    @DisplayName("6. Mutating POST with invalid X-API-Key is rejected with 403 FORBIDDEN")
    void mutatingWithInvalidApiKeyIsRejectedWith403() throws Exception {
        mockMvc.perform(post("/api/ai/resources/probe-models")
                        .header("X-API-Key", "invalid-key-xyz"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error").value(containsString("invalid authorization credentials")));

        verifyNoInteractions(googleAiResourceService);
    }

    @Test
    @DisplayName("7. Mutating POST with invalid Bearer token is rejected with 403 FORBIDDEN")
    void mutatingWithInvalidBearerIsRejectedWith403() throws Exception {
        mockMvc.perform(post("/api/ai/resources/probe-models")
                        .header("Authorization", "Bearer invalid-bearer-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error").value(containsString("invalid authorization credentials")));

        verifyNoInteractions(googleAiResourceService);
    }

    @Test
    @DisplayName("8. Safe GET /api/ai/resources without credentials is allowed (200 OK)")
    void safeReadResourcesWithoutCredentialsIsAllowed() throws Exception {
        when(googleAiResourceService.resourceMatrix()).thenReturn(List.of(Map.of("model", "gemini-1.5-pro")));

        mockMvc.perform(get("/api/ai/resources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resources").exists())
                .andExpect(jsonPath("$.modelProbe.status").value("not_run"));
    }

    @Test
    @DisplayName("9. Safe GET /stitch-tools-debug without credentials is allowed (200 OK)")
    void safeReadStitchToolsDebugWithoutCredentialsIsAllowed() throws Exception {
        when(stitchClient.listToolsRaw()).thenReturn("[]");

        mockMvc.perform(get("/api/ai/resources/stitch-tools-debug"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("10. Safe GET /video-assets/{slug} without credentials is allowed (200 OK)")
    void safeReadVideoAssetsWithoutCredentialsIsAllowed() throws Exception {
        mockMvc.perform(get("/api/ai/resources/video-assets/sample-slug"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("11. Valid X-API-Key authorizes mutating POST /probe-models and reaches owner service")
    void probeModelsWithValidApiKeySucceeds() throws Exception {
        when(googleAiResourceService.probeModels()).thenReturn(Map.of("status", "ok", "probeCount", 3));

        mockMvc.perform(post("/api/ai/resources/probe-models")
                        .header("X-API-Key", VALID_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.probeCount").value(3));

        verify(googleAiResourceService).probeModels();
    }

    @Test
    @DisplayName("12. Valid Bearer token authorizes mutating POST /stitch-design-system and reaches owner service")
    void createStitchDesignSystemWithValidBearerSucceeds() throws Exception {
        when(stitchClient.createDesignSystem(any(), any(), any(), any(), any(), any()))
                .thenReturn(new StitchClient.DesignSystemResult(true, "created", "ds-999", "Created"));

        var req = new GoogleAiResourceController.CreateDesignSystemRequest(
                "Audit Brand", "Inter", "Roboto", "#222222", "#dddddd");

        mockMvc.perform(post("/api/ai/resources/stitch-design-system")
                        .header("Authorization", "Bearer " + VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designSystemId").value("ds-999"))
                .andExpect(jsonPath("$.status").value("created"));

        verify(stitchClient).createDesignSystem(null, "Audit Brand", "Inter", "Roboto", "#222222", "#dddddd");
    }
}
