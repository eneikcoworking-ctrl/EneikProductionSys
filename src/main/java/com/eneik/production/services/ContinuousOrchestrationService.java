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
        try {
            julesDispatchService.runSessionSafetyMaintenance();
        } catch (Exception e) {
            log.error("Continuous Orchestration: Failed to run Jules safety maintenance", e);
        }

        pollActiveJulesSessions();

        List<ProjectEntity> activeProjects = projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active);
        for (ProjectEntity project : activeProjects) {
            try {
                log.info("Continuous Orchestration: Processing project {}", project.getName());
                projectFlowService.dispatchQueuedTasks(project.getId());
                projectFlowService.dispatchReviewTasks(project.getId());
                projectFlowService.orchestrate(project.getId());
            } catch (Exception e) {
                log.error("Continuous Orchestration: Failed for project {}", project.getId(), e);
            }
        }
    }

    private void pollActiveJulesSessions() {
        try {
            List<com.eneik.production.models.persistence.JulesSessionEntity> activeSessions = julesSessionRepository.findAll().stream()
                    .filter(s -> "running".equals(s.getStatus())
                            || "queued".equals(s.getStatus())
                            || "revising".equals(s.getStatus())
                            || "stuck".equals(s.getStatus()))
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
