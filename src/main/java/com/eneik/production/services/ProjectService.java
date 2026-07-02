package com.eneik.production.services;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final AccountRepository accountRepository;

    public ProjectService(ProjectRepository projectRepository, AccountRepository accountRepository) {
        this.projectRepository = projectRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public ProjectEntity createProject(String name) {
        ProjectEntity project = new ProjectEntity();
        project.setName(name);
        project.setStatus("active");
        project.setCreatedAt(Instant.now());

        ProjectEntity savedProject = projectRepository.save(project);
        accountRepository.assignFreeAccountsToProject(savedProject.getId());

        return savedProject;
    }

    public Optional<ProjectEntity> getProject(UUID id) {
        return projectRepository.findById(id);
    }

    public List<ProjectEntity> listProjects() {
        return projectRepository.findAll();
    }

    @Transactional
    public Optional<ProjectEntity> acceptProject(UUID id) {
        return projectRepository.findById(id).map(project -> {
            project.setStatus("accepted");
            project.setAcceptedAt(Instant.now());
            return projectRepository.save(project);
        });
    }

    public long getAccountCount(UUID projectId) {
        return accountRepository.countByCurrentProjectId(projectId);
    }
}
