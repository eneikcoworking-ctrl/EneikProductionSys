package com.eneik.production.services;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.repositories.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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

    public ContinuousOrchestrationService(ProjectRepository projectRepository,
                                         ProjectFlowService projectFlowService,
                                         com.eneik.production.repositories.JulesSessionRepository julesSessionRepository,
                                         com.eneik.production.services.jules.JulesDispatchService julesDispatchService,
                                         com.eneik.production.repositories.WishlistRepository wishlistRepository,
                                         com.eneik.production.services.compiler.TechnicalLeadCompiler technicalLeadCompiler) {
        this.projectRepository = projectRepository;
        this.projectFlowService = projectFlowService;
        this.julesSessionRepository = julesSessionRepository;
        this.julesDispatchService = julesDispatchService;
        this.wishlistRepository = wishlistRepository;
        this.technicalLeadCompiler = technicalLeadCompiler;
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
                        if (wishlist.getCompiledByRole() == null) {
                            technicalLeadCompiler.compile(
                                wishlist.getId(),
                                "BARCAN-TAG-09",
                                "Когда задача завершена, мы хотим получить совет роли, чтобы улучшить систему",
                                com.eneik.production.models.persistence.LeanValue.essential,
                                "TOC-CONSTRAINT-BARCAN-TAG-09",
                                "Defect Rate <= 5%",
                                "Выполнено согласно правилам роли BARCAN-TAG-09",
                                "Given task merged, Then compile advice task"
                            );
                        }
                        if (wishlist.getLeanValue() != com.eneik.production.models.persistence.LeanValue.waste) {
                            technicalLeadCompiler.createTaskFromWishlist(wishlist.getId());
                            log.info("ContinuousOrchestrationService: Automatically compiled wishlist {} into task", wishlist.getId());
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to compile pending wishlists for project {}", project.getId(), e);
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
