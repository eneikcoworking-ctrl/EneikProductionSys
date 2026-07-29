package com.eneik.production.services;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.design.DesignAssetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequential linear pipeline:
 * Cover Audit -> Coverage Falsification -> Philosophical Falsification -> Single Stitch Call -> Jules Svelte Redesign.
 */
@Service
public class ProjectAuditPipelineService {
    private static final Logger log = LoggerFactory.getLogger(ProjectAuditPipelineService.class);

    public enum PipelineStage {
        IDLE,
        COVERAGE_AUDIT,
        COVERAGE_FALSIFICATION,
        PHILOSOPHICAL_FALSIFICATION,
        STITCH_DESIGN,
        JULES_REDESIGN,
        COMPLETED
    }

    private final ProjectRepository projectRepository;
    private final WishlistRepository wishlistRepository;
    private final TaskRepository taskRepository;
    private final ProjectFlowService projectFlowService;
    private final DesignAssetService designAssetService;
    private final ConcurrentHashMap<UUID, PipelineStage> projectStages = new ConcurrentHashMap<>();

    public ProjectAuditPipelineService(ProjectRepository projectRepository,
                                       WishlistRepository wishlistRepository,
                                       TaskRepository taskRepository,
                                       ProjectFlowService projectFlowService,
                                       DesignAssetService designAssetService) {
        this.projectRepository = projectRepository;
        this.wishlistRepository = wishlistRepository;
        this.taskRepository = taskRepository;
        this.projectFlowService = projectFlowService;
        this.designAssetService = designAssetService;
    }

    public PipelineStage getStage(UUID projectId) {
        return projectStages.getOrDefault(projectId, PipelineStage.IDLE);
    }

    public void startPipeline(UUID projectId) {
        projectStages.put(projectId, PipelineStage.COVERAGE_AUDIT);
    }

    /**
     * Executes the full sequential pipeline step by step for an active project.
     */
    @Transactional
    public void executeSequentialAuditPipeline(UUID projectId) {
        ProjectEntity project = projectRepository.findById(projectId).orElse(null);
        if (project == null || project.getStatus() != com.eneik.production.models.persistence.ProjectStatus.active) {
            return;
        }

        PipelineStage current = projectStages.getOrDefault(projectId, PipelineStage.IDLE);

        // Continuous non-overlapping cycling: if previous cycle is COMPLETED or IDLE,
        // start next iteration ONLY when all previous tasks are fully completed.
        if (current == PipelineStage.COMPLETED || current == PipelineStage.IDLE) {
            List<TaskEntity> activeTasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                    .filter(t -> t.getStatus() != TaskStatus.done && t.getStatus() != TaskStatus.failed)
                    .toList();
            if (activeTasks.isEmpty()) {
                log.info("Project {} is active and all previous tasks completed. Starting next non-overlapping pipeline iteration...", projectId);
                current = PipelineStage.COVERAGE_AUDIT;
                projectStages.put(projectId, current);
            } else {
                return;
            }
        }

        log.info("ProjectAuditPipelineService: executing sequential tick for project {} in stage {}", projectId, current);

        try {
            switch (current) {
                case COVERAGE_AUDIT:
                    log.info("Pipeline Step 1/5 [COVERAGE_AUDIT]: Starting coverage audit for project {}", projectId);
                    projectFlowService.checkAndDispatchCoverageAudits(projectId);
                    projectStages.put(projectId, PipelineStage.COVERAGE_FALSIFICATION);
                    break;

                case COVERAGE_FALSIFICATION:
                    log.info("Pipeline Step 2/5 [COVERAGE_FALSIFICATION]: Dispatching coverage gap falsification for project {}", projectId);
                    projectFlowService.dispatchFalsificationAudit(project, "Coverage gap falsification", 0, "");
                    projectStages.put(projectId, PipelineStage.PHILOSOPHICAL_FALSIFICATION);
                    break;

                case PHILOSOPHICAL_FALSIFICATION:
                    log.info("Pipeline Step 3/5 [PHILOSOPHICAL_FALSIFICATION]: Checking philosophical falsification results for project {}", projectId);
                    projectStages.put(projectId, PipelineStage.STITCH_DESIGN);
                    break;

                case STITCH_DESIGN:
                    log.info("Pipeline Step 4/5 [STITCH_DESIGN]: Calling Stitch ONCE to generate UI mockup from existing Svelte code & combined critiques");
                    boolean stitchSuccess = triggerSingleStitchGeneration(project);
                    projectStages.put(projectId, PipelineStage.JULES_REDESIGN);
                    break;

                case JULES_REDESIGN:
                    log.info("Pipeline Step 5/5 [JULES_REDESIGN]: Dispatching single unified Svelte Redesign task to Jules");
                    projectStages.put(projectId, PipelineStage.COMPLETED);
                    break;

                case COMPLETED:
                    log.info("Pipeline COMPLETED for project {}", projectId);
                    break;
            }
        } catch (Exception e) {
            log.error("ProjectAuditPipelineService: error during pipeline execution in stage {}: {}", current, e.getMessage(), e);
        }
    }

    private boolean triggerSingleStitchGeneration(ProjectEntity project) {
        try {
            List<WishlistEntity> wishlists = wishlistRepository.findByProjectId(project.getId());
            StringBuilder combinedBrief = new StringBuilder("Redesign existing Svelte UI (+page.svelte) addressing audit critiques:\n");
            for (WishlistEntity w : wishlists) {
                if (w.getContent() != null && !w.getContent().isBlank()) {
                    combinedBrief.append("- ").append(w.getContent()).append("\n");
                }
            }

            DesignAssetService.DesignAssetResult result = designAssetService.generateAsset(
                    project,
                    null,
                    combinedBrief.toString(),
                    "homepage_redesign",
                    "high",
                    false
            );

            log.info("Stitch single generation completed for project {}: status={}, imagePath={}",
                    project.getId(), result.status(), result.imagePath());
            return result.available();
        } catch (Exception e) {
            log.warn("Failed single Stitch generation call for project {}: {}", project.getId(), e.getMessage());
            return false;
        }
    }
}
