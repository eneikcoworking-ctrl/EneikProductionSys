package com.eneik.production.controllers.ai;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.services.design.DesignAssetService;
import com.eneik.production.services.dashboard.ProjectOperationalContextService;
import com.eneik.production.services.googleai.GoogleAiResourceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai/resources")
public class GoogleAiResourceController {
    private final GoogleAiResourceService googleAiResourceService;
    private final DesignAssetService designAssetService;
    private final ProjectOperationalContextService contextService;
    private final ProjectRepository projectRepository;

    public GoogleAiResourceController(GoogleAiResourceService googleAiResourceService,
                                      DesignAssetService designAssetService,
                                      ProjectOperationalContextService contextService,
                                      ProjectRepository projectRepository) {
        this.googleAiResourceService = googleAiResourceService;
        this.designAssetService = designAssetService;
        this.contextService = contextService;
        this.projectRepository = projectRepository;
    }

    @GetMapping
    public Map<String, Object> resources() {
        return Map.of(
                "resources", googleAiResourceService.resourceMatrix(),
                "modelProbe", Map.of("available", false, "status", "not_run")
        );
    }

    @PostMapping("/probe-models")
    public Map<String, Object> probeModels() {
        return googleAiResourceService.probeModels();
    }

    @PostMapping("/design-assets")
    public ResponseEntity<?> generateDesignAsset(@RequestParam UUID projectId,
                                                 @RequestBody DesignAssetRequest request) {
        ProjectEntity project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return ResponseEntity.notFound().build();
        }
        var context = contextService.build(project.getId(), project.getName());
        var result = designAssetService.generateAsset(
                project,
                context,
                request == null ? "" : request.brief(),
                request == null ? "asset" : request.assetType(),
                request == null ? "fast" : request.quality(),
                request != null && request.useGoogleSearch()
        );
        return ResponseEntity.ok(result);
    }

    public record DesignAssetRequest(
            String brief,
            String assetType,
            String quality,
            boolean useGoogleSearch
    ) {
    }
}
