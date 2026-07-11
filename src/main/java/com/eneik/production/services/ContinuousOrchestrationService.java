package com.eneik.production.services;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.repositories.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.eneik.production.services.MLPredictionServiceClient;

import java.util.List;

@Service
public class ContinuousOrchestrationService {
    private static final Logger log = LoggerFactory.getLogger(ContinuousOrchestrationService.class);

    private final ProjectRepository projectRepository;
    private final ProjectFlowService projectFlowService;
    private final com.eneik.production.repositories.JulesSessionRepository julesSessionRepository;
    private final com.eneik.production.services.jules.JulesDispatchService julesDispatchService;
    private final com.eneik.production.repositories.WishlistRepository wishlistRepository;
    private final com.eneik.production.services.compiler.TechnicalLeadCompiler technicalLeadCompiler;
    private final MLPredictionServiceClient mlPredictionServiceClient;

    public ContinuousOrchestrationService(ProjectRepository projectRepository,
                                         ProjectFlowService projectFlowService,
                                         com.eneik.production.repositories.JulesSessionRepository julesSessionRepository,
                                         com.eneik.production.services.jules.JulesDispatchService julesDispatchService,
                                         com.eneik.production.repositories.WishlistRepository wishlistRepository,
                                         com.eneik.production.services.compiler.TechnicalLeadCompiler technicalLeadCompiler,
                                         MLPredictionServiceClient mlPredictionServiceClient) {
        this.projectRepository = projectRepository;
        this.projectFlowService = projectFlowService;
        this.julesSessionRepository = julesSessionRepository;
        this.julesDispatchService = julesDispatchService;
        this.wishlistRepository = wishlistRepository;
        this.technicalLeadCompiler = technicalLeadCompiler;
        this.mlPredictionServiceClient = mlPredictionServiceClient;
    }

    @Scheduled(fixedRateString = "${orchestration.rate-ms:60000}")
    public void continuousOrchestrate() {
        List<ProjectEntity> activeProjects = projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active);
        for (ProjectEntity project : activeProjects) {
            try {
                log.info("Continuous Orchestration: Processing project {}", project.getName());
                projectFlowService.orchestrate(project.getId());
                projectFlowService.dispatchQueuedTasks(project.getId());
                projectFlowService.dispatchReviewTasks(project.getId());

                // Auto-compile pending wishlists from the new wishlist table
                try {
                    List<com.eneik.production.models.persistence.WishlistEntity> pendingWishlists = wishlistRepository.findByProjectIdAndStatus(project.getId(), com.eneik.production.models.persistence.WishlistStatus.pending);
                    for (com.eneik.production.models.persistence.WishlistEntity wishlist : pendingWishlists) {
                        // Reload from repository to ensure we have the latest compiled data
                        wishlist = wishlistRepository.findById(wishlist.getId()).orElse(wishlist);

                        if (wishlist.getCompiledByRole() == null) {
                            java.util.Map<String, Object> aiMeta = new java.util.HashMap<>();
                            if (mlPredictionServiceClient != null) {
                                try {
                                    aiMeta = mlPredictionServiceClient.generateTaskMetadata(wishlist.getContent());
                                } catch (Exception e) {
                                    log.error("Failed to generate AI metadata for wishlist {}: {}", wishlist.getId(), e.getMessage());
                                    // Skip this wishlist item so it can be retried later
                                    continue;
                                }
                            }
                            String jtbd = aiMeta != null && aiMeta.containsKey("jtbd") ? aiMeta.get("jtbd").toString() : "Automate and transform";
                            String ac = aiMeta != null && aiMeta.containsKey("acceptanceCriteria") ? aiMeta.get("acceptanceCriteria").toString() : "Given task merged, Then verify feature";

                            technicalLeadCompiler.compile(
                                wishlist.getId(),
                                "BARCAN-TAG-09",
                                jtbd,
                                com.eneik.production.models.persistence.LeanValue.essential,
                                "TOC-CONSTRAINT-DECOMPOSITION",
                                "Defect Rate <= 5%",
                                "Compiled automatically by technical lead. Ссылается на Refusal Criteria роли (BARCAN-TAG-09).",
                                ac
                            );

                            // Re-fetch after compile to get the updated entity
                            wishlist = wishlistRepository.findById(wishlist.getId()).orElse(wishlist);
                        }
                        if (wishlist.getLeanValue() != com.eneik.production.models.persistence.LeanValue.waste) {
                            technicalLeadCompiler.createTaskFromWishlist(wishlist.getId());
                            log.info("ContinuousOrchestrationService: Automatically compiled wishlist {} into task", wishlist.getId());
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to compile pending wishlists for project {}", project.getId(), e); throw new RuntimeException(e);
                }
            } catch (Exception e) {
                log.error("Continuous Orchestration: Failed for project {}", project.getId(), e);
            }
        }

        // Poll all active Jules sessions to update status and handle PR transitions
        try {
            List<com.eneik.production.models.persistence.JulesSessionEntity> activeSessions = julesSessionRepository.findAll().stream()
                    .filter(s -> "running".equals(s.getStatus()) || "queued".equals(s.getStatus()))
                    .toList();
            for (com.eneik.production.models.persistence.JulesSessionEntity session : activeSessions) {
                try {
                    julesDispatchService.pollStatus(session.getId());
                } catch (Exception e) {
                    log.error("Failed to poll status for Jules session {}", session.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to poll active Jules sessions", e);
        }
    }
}
