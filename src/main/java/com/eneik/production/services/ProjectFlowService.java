package com.eneik.production.services;

import com.eneik.production.dto.*;
import com.eneik.production.dto.dashboard.AgentDashboardDto;
import com.eneik.production.dto.dashboard.PipelineDashboardDto;
import com.eneik.production.dto.dashboard.QueueDashboardDto;
import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.*;

@Service
public class ProjectFlowService {
    private static final List<String> JULES_NAMES = List.of(
            "Jules-01", "Jules-02", "Jules-03", "Jules-04", "Jules-05", "Jules-06", "Jules-07"
    );
    private static final String UNIVERSAL_CAPABILITIES = "*";
    private static final String ORCHESTRATOR_ROLE = "BARCAN-TAG-09";

    private final ProjectRepository projectRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final AccountRepository accountRepository;
    private final TaskRepository taskRepository;
    private final ClaimRepository claimRepository;
    private final RoleRepository roleRepository;
    private final ObjectMapper objectMapper;

    public ProjectFlowService(ProjectRepository projectRepository,
                              WishlistItemRepository wishlistItemRepository,
                              AccountRepository accountRepository,
                              TaskRepository taskRepository,
                              ClaimRepository claimRepository,
                              RoleRepository roleRepository,
                              ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.wishlistItemRepository = wishlistItemRepository;
        this.accountRepository = accountRepository;
        this.taskRepository = taskRepository;
        this.claimRepository = claimRepository;
        this.roleRepository = roleRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ProjectDto createProject(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Project name is required");
        }

        ProjectEntity project = new ProjectEntity();
        project.setName(name.trim());
        project.setSlug(uniqueSlug(name));
        project.setRepositoryName(project.getSlug());
        project.setRepositoryUrl("https://github.com/eneikcoworking-ctrl/" + project.getSlug());
        project.setLinearProjectKey(project.getSlug().toUpperCase(Locale.ROOT).replace("-", "_"));
        ProjectEntity saved = projectRepository.save(project);

        JULES_NAMES.forEach(namePart -> {
            AccountEntity account = new AccountEntity();
            account.setProject(saved);
            account.setName(saved.getSlug() + "-" + namePart);
            account.setCapabilities(UNIVERSAL_CAPABILITIES);
            account.setStatus(AccountStatus.idle);
            account.setLastHeartbeat(Instant.now());
            accountRepository.save(account);
        });

        return toProjectDto(saved);
    }

    @Transactional
    public WishlistItemDto addWishlistItem(UUID projectId, WishlistItemRequestDto request) {
        ProjectEntity project = requireActiveProject(projectId);
        if (request == null || request.text() == null || request.text().isBlank()) {
            throw new IllegalArgumentException("Wishlist text is required");
        }

        WishlistItemEntity item = new WishlistItemEntity();
        item.setProject(project);
        item.setText(request.text().trim());
        item.setType(request.type() != null ? request.type() : WishlistItemType.client_wish);
        item.setSourceRoleTag(request.sourceRoleTag());
        item.setStatus(WishlistItemStatus.open);
        return toWishlistDto(wishlistItemRepository.save(item));
    }

    @Transactional
    public OrchestrationResultDto orchestrate(UUID projectId) {
        ProjectEntity project = requireActiveProject(projectId);
        List<WishlistItemEntity> openItems = wishlistItemRepository
                .findByProjectIdAndStatusOrderByCreatedAtAsc(project.getId(), WishlistItemStatus.open);

        List<TaskShortDto> createdTasks = new ArrayList<>();
        for (WishlistItemEntity item : openItems) {
            List<String> tags = chooseBusinessNecessaryRoles(item.getText());
            if (tags.isEmpty()) {
                item.setStatus(WishlistItemStatus.ignored);
                wishlistItemRepository.save(item);
                continue;
            }

            for (String tag : tags) {
                RoleEntity role = roleRepository.findById(tag)
                        .orElseThrow(() -> new IllegalStateException("Role not found: " + tag));
                TaskEntity task = new TaskEntity();
                task.setProject(project);
                task.setRole(role);
                task.setDescription("[" + tag + "] " + item.getText());
                task.setStatus(TaskStatus.queued);

                ObjectNode payload = objectMapper.createObjectNode();
                payload.put("projectId", project.getId().toString());
                payload.put("wishlistItemId", item.getId().toString());
                payload.put("businessNeed", item.getText());
                payload.put("orchestratedBy", ORCHESTRATOR_ROLE);
                task.setPayload(payload);

                TaskEntity savedTask = taskRepository.save(task);
                createdTasks.add(new TaskShortDto(savedTask.getId(), tag, savedTask.getDescription()));
            }
            item.setStatus(WishlistItemStatus.converted);
            wishlistItemRepository.save(item);
        }

        String message = createdTasks.isEmpty()
                ? "No business-necessary tasks found. Waiting for more wishlist input or project acceptance."
                : "Created business-necessary tasks from wishlist.";
        return new OrchestrationResultDto(project.getId(), openItems.size(), createdTasks, message);
    }

    @Transactional
    public ProjectDto acceptProject(UUID projectId) {
        ProjectEntity project = requireProject(projectId);
        project.setStatus(ProjectStatus.accepted);
        project.setAcceptedAt(Instant.now());
        return toProjectDto(projectRepository.save(project));
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
        List<WishlistItemDto> wishlist = wishlistItemRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(this::toWishlistDto)
                .toList();
        List<TaskDto> tasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(task -> new TaskDto(
                        task.getId(),
                        task.getRole().getTag(),
                        task.getDescription(),
                        task.getStatus(),
                        task.getPayload()
                ))
                .toList();

        return new ProjectDashboardDto(
                toProjectDto(project),
                agents.size(),
                wishlistItemRepository.countByProjectIdAndStatus(projectId, WishlistItemStatus.open),
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

    private ProjectEntity requireActiveProject(UUID projectId) {
        ProjectEntity project = requireProject(projectId);
        if (project.getStatus() == ProjectStatus.accepted) {
            throw new IllegalStateException("Project is accepted and cannot receive new work");
        }
        return project;
    }

    private ProjectEntity requireProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
    }

    private List<String> chooseBusinessNecessaryRoles(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        tags.add("BARCAN-TAG-09");

        if (containsAny(lower, "design", "ui", "ux", "frontend", "screen", "page", "site", "сайт", "дизайн", "экран")) {
            tags.add("BARCAN-TAG-11");
            tags.add("BARCAN-TAG-03");
        }
        if (containsAny(lower, "api", "backend", "database", "db", "login", "auth", "endpoint", "бекенд", "база")) {
            tags.add("BARCAN-TAG-02");
        }
        if (containsAny(lower, "deploy", "docker", "ci", "linear", "github", "repo", "интегра", "деплой")) {
            tags.add("BARCAN-TAG-05");
        }
        if (containsAny(lower, "test", "bug", "fix", "qa", "ошиб", "провер")) {
            tags.add("BARCAN-TAG-06");
        }
        tags.add("BARCAN-TAG-00");
        return List.copyOf(tags);
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
        return new ProjectDto(
                project.getId(),
                project.getName(),
                project.getSlug(),
                project.getRepositoryName(),
                project.getRepositoryUrl(),
                project.getLinearProjectKey(),
                project.getStatus(),
                project.getCreatedAt(),
                project.getAcceptedAt()
        );
    }

    private WishlistItemDto toWishlistDto(WishlistItemEntity item) {
        return new WishlistItemDto(
                item.getId(),
                item.getProject().getId(),
                item.getText(),
                item.getType(),
                item.getStatus(),
                item.getSourceRoleTag(),
                item.getCreatedAt()
        );
    }
}
