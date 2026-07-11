package com.eneik.production.services;

import com.eneik.production.dto.*;
import com.eneik.production.dto.dashboard.AgentDashboardDto;
import com.eneik.production.dto.dashboard.PipelineDashboardDto;
import com.eneik.production.dto.dashboard.QueueDashboardDto;
import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.*;
import com.eneik.production.dto.dashboard.ClientDeliveryDto;
import com.eneik.production.services.compiler.TechnicalLeadCompiler;
import com.eneik.production.services.dashboard.ClientDeliveryService;
import com.eneik.production.services.jules.JulesDispatchResult;
import com.eneik.production.services.jules.JulesDispatchService;
import com.eneik.production.services.projectfactory.CollaboratorProvisioningResult;
import com.eneik.production.services.projectfactory.GitHubProjectFactoryClient;
import com.eneik.production.services.projectfactory.ProjectFactoryResult;
import com.eneik.production.services.projectfactory.ProjectFactoryService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.*;

@Service
public class ProjectFlowService {
    private static final Logger log = LoggerFactory.getLogger(ProjectFlowService.class);
    private static final List<String> JULES_NAMES = List.of(
            "Jules-01", "Jules-02", "Jules-03", "Jules-04", "Jules-05", "Jules-06", "Jules-07"
    );
    private static final String UNIVERSAL_CAPABILITIES = "*";
    private static final String ORCHESTRATOR_ROLE = "BARCAN-TAG-09";

    private final ProjectRepository projectRepository;
    private final WishlistRepository wishlistRepository;
    private final AccountRepository accountRepository;
    private final TaskRepository taskRepository;
    private final ClaimRepository claimRepository;
    private final RoleRepository roleRepository;
    private final ClaimService claimService;
    private final JulesDispatchService julesDispatchService;
    private final ProjectFactoryService projectFactoryService;
    private final GitHubProjectFactoryClient gitHubProjectFactoryClient;
    private final SystemSettingsService settingsService;
    private final TechnicalLeadCompiler technicalLeadCompiler;
    private final ClientDeliveryService clientDeliveryService;
    private final ProjectFinalReportRepository projectFinalReportRepository;
    private final JulesSessionRepository julesSessionRepository;
    private final ObjectMapper objectMapper;
    private final String githubOrganization;
    private final com.eneik.production.services.onboarding.OnboardingAuditService onboardingAuditService;



    public ProjectFlowService(ProjectRepository projectRepository,
                              WishlistRepository wishlistRepository,
                              AccountRepository accountRepository,
                              TaskRepository taskRepository,
                              ClaimRepository claimRepository,
                              RoleRepository roleRepository,
                              ClaimService claimService,
                              JulesDispatchService julesDispatchService,
                              ProjectFactoryService projectFactoryService,
                              GitHubProjectFactoryClient gitHubProjectFactoryClient,
                              SystemSettingsService settingsService,
                              TechnicalLeadCompiler technicalLeadCompiler,
                              ClientDeliveryService clientDeliveryService,
                              ProjectFinalReportRepository projectFinalReportRepository,
                              JulesSessionRepository julesSessionRepository,
                              ObjectMapper objectMapper,
                              @Value("${github.org}") String githubOrganization,
                              com.eneik.production.services.onboarding.OnboardingAuditService onboardingAuditService) {
        this.projectRepository = projectRepository;
        this.wishlistRepository = wishlistRepository;
        this.accountRepository = accountRepository;
        this.taskRepository = taskRepository;
        this.claimRepository = claimRepository;
        this.roleRepository = roleRepository;
        this.claimService = claimService;
        this.julesDispatchService = julesDispatchService;
        this.projectFactoryService = projectFactoryService;
        this.gitHubProjectFactoryClient = gitHubProjectFactoryClient;
        this.settingsService = settingsService;
        this.technicalLeadCompiler = technicalLeadCompiler;
        this.clientDeliveryService = clientDeliveryService;
        this.projectFinalReportRepository = projectFinalReportRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.objectMapper = objectMapper;
        this.githubOrganization = githubOrganization;
        this.onboardingAuditService = onboardingAuditService;
    }

    @Transactional
    public ProjectDto createProject(String name) {
        return createProject(name, "greenfield");
    }

    @Transactional
    public ProjectDto createProject(String name, String onboardingMode) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Project name is required");
        }

        String mode = onboardingMode != null ? onboardingMode.trim() : "greenfield";

        // 1. Freeze current active project only if greenfield
        if ("greenfield".equalsIgnoreCase(mode)) {
            projectRepository.findFirstByStatusOrderByCreatedAtDesc(ProjectStatus.active)
                    .ifPresent(p -> {
                        p.setStatus(ProjectStatus.frozen);
                        projectRepository.save(p);
                    });
        }

        // 2. Create new project
        ProjectEntity project = new ProjectEntity();
        project.setName(name.trim());
        project.setSlug(uniqueSlug(name));
        project.setOnboardingMode(mode);
        if ("brownfield".equalsIgnoreCase(mode)) {
            project.setStatus(ProjectStatus.analyzing);
        } else {
            project.setStatus(ProjectStatus.active);
        }
        project.setRepositoryName(project.getSlug());
        project.setRepositoryUrl("https://github.com/" + githubOrganization + "/" + project.getSlug());
        project.setRepoUrl(project.getRepositoryUrl());
        project.setLinearProjectKey(project.getSlug().toUpperCase(Locale.ROOT).replace("-", "_"));
        
        ProjectEntity saved = projectRepository.save(project);
        ProjectFactoryResult factoryResult = projectFactoryService.provision(saved);
        saved.setRepositoryUrl(factoryResult.repositoryUrl());
        saved.setRepoUrl(factoryResult.repositoryUrl());
        saved.setGithubRepositoryStatus(factoryResult.githubRepositoryStatus());
        saved.setGithubRepositoryId(factoryResult.githubRepositoryId());
        saved.setLinearProjectStatus(factoryResult.linearProjectStatus());
        saved.setLinearProjectId(factoryResult.linearProjectId());
        saved.setWorkspacePath(factoryResult.workspacePath());
        saved.setFactoryStatus(factoryResult.factoryStatus());
        saved.setFactoryReport(factoryResult.factoryReport());
        if ("waiting".equals(factoryResult.factoryStatus())) {
            saved.setStatus(ProjectStatus.waiting);
        }
        saved = projectRepository.save(saved);

        // Run onboarding audit if brownfield
        if ("brownfield".equalsIgnoreCase(mode)) {
            try {
                onboardingAuditService.runOnboardingAudit(saved);
            } catch (Exception e) {
                log.error("Failed to run onboarding audit for project {}", saved.getId(), e);
            }
        }

        return toProjectDto(saved);
    }

    @Transactional
    public ProjectDto activateProject(UUID projectId) {
        ProjectEntity project = requireProject(projectId);
        if (project.getStatus() == ProjectStatus.accepted || project.getStatus() == ProjectStatus.archived) {
            throw new IllegalStateException("Cannot activate " + project.getStatus() + " project");
        }

        if (project.getStatus() == ProjectStatus.active) {
            return toProjectDto(project);
        }

        // Freeze other active projects
        projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)
                .forEach(p -> {
                    if (!p.getId().equals(projectId)) {
                        p.setStatus(ProjectStatus.frozen);
                        projectRepository.save(p);
                    }
                });

        project.setStatus(ProjectStatus.active);
        return toProjectDto(projectRepository.save(project));
    }



    @Transactional
    public com.eneik.production.dto.WishlistResponseDto addWishlistItem(UUID projectId, com.eneik.production.dto.WishlistRequestDto request) {
        ProjectEntity project = requireActiveProject(projectId);
        if (project.getStatus() == com.eneik.production.models.persistence.ProjectStatus.analyzing) {
            throw new IllegalStateException("Cannot add wishlist to a project in analyzing state");
        }
        if (request == null || request.content() == null || request.content().isBlank()) {
            throw new IllegalArgumentException("Wishlist text is required");
        }
        com.eneik.production.models.persistence.WishlistEntity item = new com.eneik.production.models.persistence.WishlistEntity();
        item.setProjectId(project.getId());
        item.setContent(request.content().trim());
        item.setSourceRoleTag(request.sourceRoleTag() != null ? request.sourceRoleTag() : "BARCAN-TAG-00");
        item.setSource(request.source() != null ? request.source() : com.eneik.production.models.persistence.WishlistSource.client);
        item.setStatus(com.eneik.production.models.persistence.WishlistStatus.pending);
        item = wishlistRepository.save(item);

        return new com.eneik.production.dto.WishlistResponseDto(item.getId(), item.getProjectId(), item.getSource(), item.getSourceRoleTag(), item.getContent(), item.getStatus(), item.getCreatedAt());
    }

    public OrchestrationResultDto orchestrate(UUID projectId) {
        ProjectEntity project = requireActiveProject(projectId);
        java.util.List<com.eneik.production.models.persistence.WishlistEntity> pendingItems = wishlistRepository.findByProjectIdAndStatus(project.getId(), com.eneik.production.models.persistence.WishlistStatus.pending);
        return new OrchestrationResultDto(project.getId(), pendingItems.size(), new java.util.ArrayList<>(), "Orchestration request received.");
    }

    @Transactional
    public void dispatchQueuedTasks(UUID projectId) {
        ProjectEntity project = requireActiveProject(projectId);
        List<TaskEntity> queuedTasks = taskRepository.findByProjectIdAndStatusOrderByPriorityDescCreatedAtAsc(project.getId(), TaskStatus.queued);

        for (TaskEntity task : queuedTasks) {
            String roleTag = task.getRole().getTag();
            Optional<AccountEntity> accountOpt = accountRepository.lockNextIdleAccountForProjectAndCapability(project.getId(), roleTag);
            if (accountOpt.isPresent()) {
                AccountEntity account = accountOpt.get();
                try {
                    claimService.claimSpecificTask(task.getId(), account.getId());

                    // Refresh task state after claim
                    TaskEntity savedTask = taskRepository.findById(task.getId()).orElse(task);

                    JulesDispatchResult dispatch = julesDispatchService.dispatch(savedTask, account.getId());
                    savedTask.setJulesSessionName(dispatch.sessionName());
                    savedTask.setJulesDispatchStatus(dispatch.reason());
                    taskRepository.save(savedTask);
                    log.info("Dispatched queued task {} of project {} to account {}", savedTask.getId(), project.getName(), account.getName());
                } catch (Exception e) {
                    log.error("Failed to claim/dispatch queued task {} to account {}: {}", task.getId(), account.getName(), e.getMessage(), e);
                }
            } else {
                task.setJulesDispatchStatus("No idle accounts with capability " + roleTag + " available");
                taskRepository.save(task);
            }
        }
    }

    @Transactional
    public void dispatchReviewTasks(UUID projectId) {
        ProjectEntity project = requireActiveProject(projectId);
        List<TaskEntity> reviewTasks = taskRepository.findByProjectIdAndStatusOrderByPriorityDescCreatedAtAsc(project.getId(), TaskStatus.review);

        for (TaskEntity task : reviewTasks) {
            List<JulesSessionEntity> sessions = julesSessionRepository.findByTaskId(task.getId());
            boolean hasActiveReviewSession = sessions.stream()
                    .anyMatch(s -> {
                        String status = s.getStatus();
                        return "running".equals(status) || "queued".equals(status);
                    });

            if (!hasActiveReviewSession) {
                // Find any idle capable account to act as reviewer
                String roleTag = task.getRole().getTag();
                Optional<AccountEntity> accountOpt = accountRepository.lockNextIdleAccountForProjectAndCapability(project.getId(), roleTag);
                if (accountOpt.isPresent()) {
                    AccountEntity account = accountOpt.get();
                    try {
                        julesDispatchService.dispatch(task, account.getId(), "REVIEWER");
                        log.info("Auto-dispatched reviewer for task {} of project {} to account {}", task.getId(), project.getName(), account.getName());
                    } catch (Exception e) {
                        log.error("Failed to auto-dispatch reviewer for task {}: {}", task.getId(), e.getMessage());
                    }
                }
            }
        }
    }

    @Transactional
    public ProjectDto acceptProject(UUID projectId) {
        ProjectEntity project = requireProject(projectId);

        // 1. Stop generation
        technicalLeadCompiler.stopGeneration(projectId);

        // 2. Linear sync (check for close issues method)
        // Based on exploration, LinearProjectFactoryClient doesn't have it.
        // We'll log the skip as requested.
        log.info("linear sync not available, skipped");

        // 3. Save snapshot
        ClientDeliveryDto snapshot = clientDeliveryService.getDelivery(projectId);
        saveFinalReport(projectId, snapshot);

        project.setStatus(ProjectStatus.accepted);
        project.setAcceptedAt(Instant.now());
        return toProjectDto(projectRepository.save(project));
    }

    private void saveFinalReport(UUID projectId, ClientDeliveryDto snapshot) {
        try {
            ProjectFinalReportEntity report = new ProjectFinalReportEntity();
            report.setProjectId(projectId);
            report.setTotalTasksCompleted(snapshot.delivered().size());
            report.setTotalWishlistItems(snapshot.requested().size());
            report.setReportContent(objectMapper.valueToTree(snapshot));
            projectFinalReportRepository.save(report);
        } catch (Exception e) {
            log.error("Failed to save final report snapshot for project {}", projectId, e);
        }
    }

    @Transactional(readOnly = true)
    public ProjectDashboardDto dashboard(UUID projectId) {
        ProjectEntity project = requireProject(projectId);
        List<AgentDashboardDto> agents = accountRepository.findByProjectIdOrderByNameAsc(projectId).stream()
                .map(account -> {
                    ClaimEntity activeClaim = claimRepository
                            .findByAccountIdAndTaskProjectIdAndReleasedAtIsNull(account.getId(), projectId)
                            .orElse(null);
                    return new AgentDashboardDto(
                            account.getId(),
                            account.getName(),
                            account.getStatus(),
                            activeClaim != null ? activeClaim.getRole().getTag() : null,
                            activeClaim != null ? activeClaim.getTask().getDescription() : null,
                            activeClaim != null ? activeClaim.getClaimedAt() : null,
                            activeClaim != null ? activeClaim.getLeaseExpiresAt() : null,
                            account.getLastHeartbeat()
                    );
                })
                .toList();

        QueueDashboardDto queue = new QueueDashboardDto(
                taskRepository.queuedGroupedByProjectAndTag(projectId),
                taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.queued)
        );
        PipelineDashboardDto pipeline = new PipelineDashboardDto(
                taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.queued),
                taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.claimed),
                taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.in_progress),
                taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.review),
                taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.done),
                taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.failed)
        );
        List<com.eneik.production.dto.WishlistResponseDto> wishlist = wishlistRepository.findByProjectId(projectId)
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(w -> new com.eneik.production.dto.WishlistResponseDto(w.getId(), w.getProjectId(), w.getSource(), w.getSourceRoleTag(), w.getContent(), w.getStatus(), w.getCreatedAt()))
                .toList();
        List<TaskDto> tasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(task -> new TaskDto(
                        task.getId(),
                        task.getRole().getTag(),
                        task.getDescription(),
                        task.getStatus(),
                        task.getPayload(),
                        task.getJulesSessionName(),
                        task.getJulesDispatchStatus()
                ))
                .toList();

        return new ProjectDashboardDto(
                toProjectDto(project),
                agents.size(),
                wishlistRepository.findByProjectIdAndStatus(projectId, com.eneik.production.models.persistence.WishlistStatus.pending).size(),
                queue,
                pipeline,
                agents,
                wishlist,
                tasks
        );
    }

    @Transactional(readOnly = true)
    public List<ProjectDto> listProjects() {
        return projectRepository.findAll().stream().map(this::toProjectDto).toList();
    }

    @Transactional
    public ProjectDto refreshCollaborators(UUID projectId) {
        ProjectEntity project = requireProject(projectId);
        String reportJson = project.getFactoryReport();
        if (reportJson == null || reportJson.isBlank()) {
            return toProjectDto(project);
        }

        try {
            ObjectNode report = (ObjectNode) objectMapper.readTree(reportJson);
            var collaboratorsNode = report.get("collaborators");
            if (collaboratorsNode != null && collaboratorsNode.isArray()) {
                String token = settingsService.effectiveValue("github_token");

                List<CollaboratorProvisioningResult> newResults = new ArrayList<>();
                for (var node : collaboratorsNode) {
                    String username = node.get("username").asText();
                    CollaboratorProvisioningResult result = gitHubProjectFactoryClient.inviteCollaborator(
                            project.getRepositoryName(), username, token);
                    newResults.add(result);
                }

                report.remove("collaborators");
                var newCollaboratorsNode = report.putArray("collaborators");
                for (CollaboratorProvisioningResult res : newResults) {
                    ObjectNode item = newCollaboratorsNode.addObject();
                    item.put("username", res.username());
                    item.put("status", res.status());
                    item.put("githubStatus", res.githubStatus());
                    item.put("detail", res.detail());
                }
                project.setFactoryReport(report.toString());
                projectRepository.save(project);
            }
        } catch (Exception e) {
            log.error("Failed to refresh collaborators for project {}", projectId, e);
        }

        return toProjectDto(project);
    }

    public ProjectEntity requireActiveProject(UUID projectId) {
        ProjectEntity project = requireProject(projectId);
        if (project.getStatus() == ProjectStatus.accepted) {
            throw new IllegalStateException("Project is accepted and cannot receive new work");
        }
        if (project.getStatus() == ProjectStatus.analyzing) {
            throw new IllegalStateException("Project is analyzing and cannot receive new work");
        }
        return project;
    }

    public ProjectEntity requireProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
    }


    private String getRoleSpecificAssignment(String wishlistText, String roleTag) {
        boolean isChess = wishlistText.toLowerCase(Locale.ROOT).contains("шахмат") || 
                          wishlistText.toLowerCase(Locale.ROOT).contains("chess");
        
        if (isChess) {
            switch (roleTag) {
                case "BARCAN-TAG-03":
                    return "Спроектировать 3D-сцену шахматной доски, включая материалы фигур, параметры камеры и освещения в едином визуальном стиле.";
                case "BARCAN-TAG-02":
                    return "Реализовать логику шахматных правил и алгоритм ИИ с 3 уровнями сложности (через глубину поиска или оценочную функцию).";
                case "BARCAN-TAG-11":
                    return "Подключить 3D-визуализацию к логике игры: обработка кликов по фигурам, подсветка доступных ходов, отправка хода в движок.";
                case "BARCAN-TAG-06":
                    return "Разработать автоматизированный E2E тест на сквозной игровой процесс против компьютера.";
            }
        }
        
        switch (roleTag) {
            case "BARCAN-TAG-03":
                return "Спроектировать пользовательский интерфейс, макеты экранов и дизайн-элементы для функции: \"" + wishlistText + "\" согласно docs/DESIGN_SYSTEM.md.";
            case "BARCAN-TAG-02":
                return "Разработать серверную бизнес-логику, API эндпоинты, миграции базы данных и юнит-тесты для функции: \"" + wishlistText + "\".";
            case "BARCAN-TAG-11":
                return "Реализовать фронтенд-компоненты на Svelte, интерактивное взаимодействие и интеграцию с API для функции: \"" + wishlistText + "\" согласно docs/DESIGN_SYSTEM.md.";
            case "BARCAN-TAG-06":
                return "Написать автоматизированные E2E и интеграционные тесты для верификации функции: \"" + wishlistText + "\".";
            case "BARCAN-TAG-05":
                return "Настроить CI/CD пайплайн, Dockerfile, конфигурации сборки и окружения для деплоя функции: \"" + wishlistText + "\".";
            default:
                return "Реализовать технические требования для роли " + roleTag + " по пожеланию клиента: \"" + wishlistText + "\".";
        }
    }



    private boolean containsAny(String text, String... needles) {
        return Arrays.stream(needles).anyMatch(text::contains);
    }

    private String uniqueSlug(String name) {
        String base = Normalizer.normalize(name.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (base.isBlank()) {
            base = "project";
        }
        String slug = base;
        int suffix = 2;
        while (projectRepository.existsBySlug(slug)) {
            slug = base + "-" + suffix++;
        }
        return slug;
    }

    private ProjectDto toProjectDto(ProjectEntity project) {
        String statusLabel = project.getStatus().name().toUpperCase();
        String uiColorToken = switch (project.getStatus()) {
            case active -> "text-success";
            case frozen -> "text-warning";
            case accepted -> "text-primary";
            case archived -> "text-secondary";
            default -> "text-neutral-500";
        };

        List<CollaboratorDto> collaborators = new ArrayList<>();
        if (project.getFactoryReport() != null) {
            try {
                ObjectNode report = (ObjectNode) objectMapper.readTree(project.getFactoryReport());
                if (report.has("collaborators")) {
                    for (JsonNode node : report.get("collaborators")) {
                        String status = node.get("status").asText();
                        String label = switch (status) {
                            case "invitation_sent" -> "Invitation sent";
                            case "already_has_access" -> "Collaborator";
                            case "validation_failed_or_pending" -> "Pending or validation warning";
                            case "not_found" -> "GitHub user not found";
                            default -> status;
                        };
                        String tone = switch (status) {
                            case "invitation_sent", "already_has_access" -> "idle";
                            case "validation_failed_or_pending" -> "offline";
                            default -> "busy";
                        };
                        collaborators.add(new CollaboratorDto(
                                node.get("username").asText(),
                                status,
                                node.get("githubStatus").asText(),
                                node.get("detail").asText(),
                                label,
                                tone
                        ));
                    }
                }
            } catch (Exception e) {
                log.error("Failed to parse factory report for project {}", project.getId(), e);
            }
        }

        return new ProjectDto(
                project.getId(),
                project.getName(),
                project.getSlug(),
                project.getRepositoryName(),
                project.getRepositoryUrl(),
                project.getRepoUrl(),
                project.getLinearProjectKey(),
                project.getGithubRepositoryStatus(),
                project.getGithubRepositoryId(),
                project.getLinearProjectStatus(),
                project.getLinearProjectId(),
                project.getWorkspacePath(),
                project.getFactoryStatus(),
                project.getFactoryReport(),
                project.getStatus(),
                project.getOnboardingMode(),
                project.getCreatedAt(),
                project.getAcceptedAt(),
                accountRepository.findByEnabledTrueAndProjectIsNullAndGithubUsernameIsNotNullOrderByNameAsc().size(),
                statusLabel,
                uiColorToken,
                collaborators
        );
    }

}
