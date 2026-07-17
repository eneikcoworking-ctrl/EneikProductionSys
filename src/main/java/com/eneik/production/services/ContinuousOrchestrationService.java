package com.eneik.production.services;

import com.eneik.production.dto.OrchestrationResultDto;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.eneik.production.services.MLPredictionServiceClient;

import java.util.List;

@Service
public class ContinuousOrchestrationService {
    private static final Logger log = LoggerFactory.getLogger(ContinuousOrchestrationService.class);

    private final ProjectRepository projectRepository;
    private final ProjectFlowService projectFlowService;
    private final AccountRepository accountRepository;
    private final com.eneik.production.repositories.JulesSessionRepository julesSessionRepository;
    private final com.eneik.production.services.jules.JulesDispatchService julesDispatchService;
    private final com.eneik.production.repositories.WishlistRepository wishlistRepository;
    private final com.eneik.production.services.compiler.TechnicalLeadCompiler technicalLeadCompiler;
    private final MLPredictionServiceClient mlPredictionServiceClient;

    public ContinuousOrchestrationService(ProjectRepository projectRepository,
                                         ProjectFlowService projectFlowService,
                                         AccountRepository accountRepository,
                                         com.eneik.production.repositories.JulesSessionRepository julesSessionRepository,
                                         com.eneik.production.services.jules.JulesDispatchService julesDispatchService,
                                         com.eneik.production.repositories.WishlistRepository wishlistRepository,
                                         com.eneik.production.services.compiler.TechnicalLeadCompiler technicalLeadCompiler,
                                         MLPredictionServiceClient mlPredictionServiceClient) {
        this.projectRepository = projectRepository;
        this.projectFlowService = projectFlowService;
        this.accountRepository = accountRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.julesDispatchService = julesDispatchService;
        this.wishlistRepository = wishlistRepository;
        this.technicalLeadCompiler = technicalLeadCompiler;
        this.mlPredictionServiceClient = mlPredictionServiceClient;
    }

    @Scheduled(fixedRateString = "${orchestration.rate-ms:60000}")
    public void continuousOrchestrate() {
        repairMisclassifiedJulesAccountLimits();

        try {
            julesDispatchService.runSessionSafetyMaintenance();
        } catch (Exception e) {
            log.error("Continuous Orchestration: Failed to run Jules safety maintenance", e);
        }

        pollActiveJulesSessions();

        List<ProjectEntity> activeProjects = projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active);
        for (ProjectEntity project : activeProjects) {
            log.info("Continuous Orchestration: Processing project {}", project.getName());

            // Compile any pending wishlist items into tasks. This is the step that used to only run when a
            // human clicked "Orchestrate" or chatted with the operator — without it, autonomous operation
            // stalls the moment the queue drains down to a wishlist item that isn't a circuit-breaker
            // recovery follow-up. orchestrate() is self-rate-limited (5 min/project cooldown), so calling it
            // every cycle is cheap and safe; it's a separate try/catch so its cooldown never skips the
            // recovery/dispatch calls below.
            try {
                OrchestrationResultDto orchestration = projectFlowService.orchestrate(project.getId());
                if (orchestration.processedWishlistItems() > 0) {
                    log.info("Continuous Orchestration: Compiled {} wishlist item(s) into {} task(s) for project {}",
                            orchestration.processedWishlistItems(), orchestration.createdTasks().size(), project.getName());
                }
            } catch (OrchestrationCooldownException e) {
                log.debug("Continuous Orchestration: Orchestration on cooldown for project {} ({}s remaining)",
                        project.getId(), e.getRetryAfterSeconds());
            } catch (Exception e) {
                log.error("Continuous Orchestration: Failed to compile wishlist for project {}", project.getId(), e);
            }

            try {
                int recovered = projectFlowService.recoverBlockedWork(project.getId());
                if (recovered > 0) {
                    log.info("Continuous Orchestration: Recovered {} blocked work item(s) for project {}",
                            recovered, project.getName());
                }
                projectFlowService.dispatchQueuedTasks(project.getId());
                projectFlowService.dispatchReviewTasks(project.getId());
            } catch (OrchestrationCooldownException e) {
                log.info("Continuous Orchestration: Skipping project {} for {} seconds because orchestration is on cooldown",
                        project.getId(), e.getRetryAfterSeconds());
            } catch (Exception e) {
                log.error("Continuous Orchestration: Failed for project {}", project.getId(), e);
            }
        }
    }

    @Scheduled(cron = "${jules.daily-limit-reset-cron:0 5 0 * * ?}")
    @Transactional
    public void resetDailyLimitedAccounts() {
        int reset = accountRepository.resetDailyLimitedAccounts();
        if (reset > 0) {
            log.info("Continuous Orchestration: Reset {} Jules account(s) from daily_limited to idle", reset);
        }
    }

    @Transactional
    public void repairMisclassifiedJulesAccountLimits() {
        int repaired = accountRepository.reclassifyPreconditionDailyLimitedAccounts();
        if (repaired > 0) {
            log.warn("Continuous Orchestration: Reclassified {} Jules account(s) from daily_limited to api_blocked because the latest evidence is precondition/API refusal, not quota", repaired);
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
