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

    public ContinuousOrchestrationService(ProjectRepository projectRepository, ProjectFlowService projectFlowService) {
        this.projectRepository = projectRepository;
        this.projectFlowService = projectFlowService;
    }

    @Scheduled(fixedRateString = "${orchestration.rate-ms:60000}")
    public void continuousOrchestrate() {
        List<ProjectEntity> activeProjects = projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active);
        for (ProjectEntity project : activeProjects) {
            try {
                log.info("Continuous Orchestration: Processing project {}", project.getName());
                projectFlowService.orchestrate(project.getId());
            } catch (Exception e) {
                log.error("Continuous Orchestration: Failed for project {}", project.getId(), e);
            }
        }
    }
}
