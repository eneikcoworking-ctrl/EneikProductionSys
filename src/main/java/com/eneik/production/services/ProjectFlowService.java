package com.eneik.production.services;

import com.eneik.production.dto.*;
import com.eneik.production.dto.dashboard.AgentDashboardDto;
import com.eneik.production.dto.dashboard.PipelineDashboardDto;
import com.eneik.production.dto.dashboard.QueueDashboardDto;
import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.*;
import com.eneik.production.dto.dashboard.ClientDeliveryDto;
import com.eneik.production.services.dashboard.EmsMetricsService;
import com.eneik.production.services.compiler.TechnicalLeadCompiler;
import com.eneik.production.services.dashboard.ClientDeliveryService;
import com.eneik.production.services.jules.JulesDispatchResult;
import com.eneik.production.services.jules.JulesDispatchService;
import com.eneik.production.services.projectfactory.CollaboratorProvisioningResult;
import com.eneik.production.services.projectfactory.GitHubProjectFactoryClient;
import com.eneik.production.services.projectfactory.ProjectFactoryResult;
import com.eneik.production.services.projectfactory.ProjectFactoryService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.task.TaskTitleBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Duration;
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
    private static final String ENVIRONMENT_BOOTSTRAP_TOC = "BOOTSTRAP-ENVIRONMENT-BOUNDARY";
    private static final long ORCHESTRATION_COOLDOWN_SECONDS = 300L;

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
    private final JulesActivityResponseRepository julesActivityResponseRepository;
    private final ProjectGenerationStateRepository projectGenerationStateRepository;
    private final ObjectMapper objectMapper;
    private final String githubOrganization;
    private final com.eneik.production.services.onboarding.OnboardingAuditService onboardingAuditService;
    private final EmsMetricsService emsMetricsService;
    private final com.eneik.production.services.dashboard.ProjectOperationalContextService contextService;
    private final com.eneik.production.services.design.DesignAssetService designAssetService;
    private final com.eneik.production.services.github.GitHubPullRequestService gitHubPullRequestService;
    private final ClientDeliverableReadinessService readinessService;

    @Value("${jules.max-concurrent-sessions-per-account:3}")
    private int maxConcurrentJulesSessionsPerAccount;

    // Every account except the reserved compiler/falsification account (eneikdru) has a real Jules daily
    // session quota; enforcing it locally, proactively, means dispatch selection can budget for it instead
    // of only finding out reactively once Jules itself returns a quota error (AccountStatus.daily_limited).
    @Value("${jules.max-daily-sessions-per-account:15}")
    private int maxDailySessionsPerAccount;

    @Value("${orchestration.max-recovery-items-per-run:3}")
    private int maxRecoveryItemsPerRun;



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
                              JulesActivityResponseRepository julesActivityResponseRepository,
                              ProjectGenerationStateRepository projectGenerationStateRepository,
                              ObjectMapper objectMapper,
                              @Value("${github.org}") String githubOrganization,
                              com.eneik.production.services.onboarding.OnboardingAuditService onboardingAuditService,
                              EmsMetricsService emsMetricsService,
                              com.eneik.production.services.dashboard.ProjectOperationalContextService contextService,
                              com.eneik.production.services.design.DesignAssetService designAssetService,
                              com.eneik.production.services.github.GitHubPullRequestService gitHubPullRequestService,
                              ClientDeliverableReadinessService readinessService) {
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
        this.julesActivityResponseRepository = julesActivityResponseRepository;
        this.projectGenerationStateRepository = projectGenerationStateRepository;
        this.objectMapper = objectMapper;
        this.githubOrganization = githubOrganization;
        this.onboardingAuditService = onboardingAuditService;
        this.emsMetricsService = emsMetricsService;
        this.contextService = contextService;
        this.designAssetService = designAssetService;
        this.gitHubPullRequestService = gitHubPullRequestService;
        this.readinessService = readinessService;
    }

    @Transactional
    public ProjectDto createProject(String name, String onboardingMode, String initialWishlist) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Project name is required");
        }
        // A project with no wishlist is a project the orchestrator will eventually generate a bootstrap
        // task for just to have something to do (the exact wasted-work pattern the Lean bootstrap-deferral
        // fix targeted) - requiring real client content at creation time means that gap can never open in
        // the first place. Fail before any GitHub repo/workspace provisioning happens, not after.
        if (initialWishlist == null || initialWishlist.isBlank()) {
            throw new IllegalArgumentException("initialWishlist is required - a project cannot be created without a client brief");
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
        ensureProjectGenerationState(saved.getId());
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

        WishlistEntity firstWishlist = new WishlistEntity();
        firstWishlist.setProjectId(saved.getId());
        firstWishlist.setContent(initialWishlist.trim());
        firstWishlist.setSource(WishlistSource.client);
        firstWishlist.setStatus(WishlistStatus.pending);
        wishlistRepository.save(firstWishlist);

        return toProjectDto(saved);
    }

    private void ensureProjectGenerationState(UUID projectId) {
        if (projectGenerationStateRepository.existsById(projectId)) {
            return;
        }
        ProjectGenerationStateEntity state = new ProjectGenerationStateEntity();
        state.setProjectId(projectId);
        projectGenerationStateRepository.save(state);
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
        com.eneik.production.models.persistence.WishlistSource source =
                request.source() != null ? request.source() : com.eneik.production.models.persistence.WishlistSource.client;
        if (source == com.eneik.production.models.persistence.WishlistSource.role
                && (request.sourceRoleTag() == null || request.sourceRoleTag().isBlank())) {
            throw new IllegalArgumentException("sourceRoleTag is required when source is 'role'");
        }
        item.setSource(source);
        item.setSourceRoleTag(source == com.eneik.production.models.persistence.WishlistSource.client ? null : request.sourceRoleTag());
        item.setStatus(com.eneik.production.models.persistence.WishlistStatus.pending);
        item = wishlistRepository.save(item);

        return new com.eneik.production.dto.WishlistResponseDto(item.getId(), item.getProjectId(), item.getSource(), item.getSourceRoleTag(), item.getContent(), item.getStatus(), item.getCreatedAt());
    }

    @Transactional
    public OrchestrationResultDto orchestrate(UUID projectId) {
        ProjectEntity project = requireActiveProject(projectId);
        recordOrchestrationStartOrThrow(projectId);

        // Group similar pending wishlist items using graph theory connected components
        groupSimilarWishlistItems(project.getId());

        // 1. Record existing task IDs of this project
        java.util.List<TaskEntity> existingTasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        java.util.Set<UUID> existingIds = new java.util.HashSet<>();
        for (TaskEntity t : existingTasks) {
            existingIds.add(t.getId());
        }

        int processedCount = 0;

        // 2. Fetch pending wishlists
        java.util.List<com.eneik.production.models.persistence.WishlistEntity> pendingItems =
                wishlistRepository.findByProjectIdAndStatus(project.getId(), com.eneik.production.models.persistence.WishlistStatus.pending);

        // Lean: the bootstrap task used to fire unconditionally on the first orchestrate() call, which
        // ContinuousOrchestrationService triggers automatically every ~2-3 minutes for every active
        // project - a brand-new project with no wishlist yet got a real Jules session dispatched
        // (consuming an account slot and real cost) before the operator had asked for anything at all.
        // Confirmed live: this confused the operator during the test-twenty-fifth experiment ("I haven't
        // written a wishlist or pressed orchestrate yet - why is a session already running?"). Gating it
        // on real pending demand is classic overproduction waste avoidance: don't build the scaffold until
        // there's an actual first thing to scaffold for. The idempotency check inside
        // ensureEnvironmentBootstrapTask still means it only ever fires once per project either way.
        if (!pendingItems.isEmpty() && ensureEnvironmentBootstrapTask(project).isPresent()) {
            processedCount++;
        }

        for (com.eneik.production.models.persistence.WishlistEntity wishlist : pendingItems) {
            try {
                // Reload from repository to ensure we have the latest compiled data
                wishlist = wishlistRepository.findById(wishlist.getId()).orElse(wishlist);
                if (wishlist.getStatus() != com.eneik.production.models.persistence.WishlistStatus.pending) {
                    continue;
                }

                if (compileWishlistIntoAtomicSlices(project, wishlist)) {
                    processedCount++;
                    log.info("ProjectFlowService: Synchronously compiled wishlist {} into atomic task slices", wishlist.getId());
                    continue;
                } else if (wishlist.getStatus() == com.eneik.production.models.persistence.WishlistStatus.compiling) {
                    // Already handed off to the async Jules compiler dispatch (dispatchWishlistCompiler) -
                    // the legacy synchronous fallback below must not also compile this same wishlist.
                    continue;
                } else if (wishlist.getCompiledByRole() == null) {
                    // Gemini-free: Jules reads the real original brief directly (see
                    // TechnicalLeadCompiler.buildTaskDescription's "Original Brief" section), so no AI
                    // pre-summarization is needed here either.
                    String jtbd = fallbackTaskSlice(wishlist.getContent()).jtbd();
                    String ac = fallbackTaskSlice(wishlist.getContent()).acceptanceCriteria();

                    technicalLeadCompiler.compile(
                        wishlist.getId(),
                        "BARCAN-TAG-09",
                        jtbd,
                        com.eneik.production.models.persistence.LeanValue.essential,
                        "TOC-CONSTRAINT-DECOMPOSITION",
                        "Defect Rate <= 5%",
                        "Compiled from English JTBD slice by Eneik Management System. Role refusal criteria: BARCAN-TAG-09.",
                        ac
                    );

                    // Re-fetch after compile to get the updated entity
                    wishlist = wishlistRepository.findById(wishlist.getId()).orElse(wishlist);
                }
                if (wishlist.getLeanValue() != com.eneik.production.models.persistence.LeanValue.waste) {
                    technicalLeadCompiler.createTaskFromWishlist(wishlist.getId());
                    processedCount++;
                    log.info("ProjectFlowService: Synchronously compiled wishlist {} into task", wishlist.getId());
                }
            } catch (Exception e) {
                log.error("Failed to compile pending wishlist {} for project {}", wishlist.getId(), project.getId(), e);
            }
        }

        // 3. Find all newly created tasks
        java.util.List<TaskEntity> currentTasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        java.util.List<TaskShortDto> createdTasks = new java.util.ArrayList<>();
        for (TaskEntity t : currentTasks) {
            if (!existingIds.contains(t.getId())) {
                createdTasks.add(new TaskShortDto(t.getId(), t.getRole().getTag(), TaskTitleBuilder.displayTitle(t), t.getDescription()));
            }
        }

        return new OrchestrationResultDto(
            project.getId(),
            processedCount,
            createdTasks,
            "Orchestrated " + processedCount + " wishlist items. " + createdTasks.size() + " tasks created."
        );
    }

    @Transactional
    public Optional<UUID> ensureEnvironmentBootstrapWork(UUID projectId) {
        ProjectEntity project = requireActiveProject(projectId);
        return ensureEnvironmentBootstrapTask(project).map(TaskEntity::getId);
    }

    private Optional<TaskEntity> ensureEnvironmentBootstrapTask(ProjectEntity project) {
        if (findEnvironmentBootstrapTask(project.getId()).isPresent()) {
            return Optional.empty();
        }

        WishlistEntity bootstrap = findEnvironmentBootstrapWishlist(project.getId())
                .orElseGet(() -> {
                    WishlistEntity item = new WishlistEntity();
                    item.setProjectId(project.getId());
                    item.setSource(WishlistSource.role);
                    item.setSourceRoleTag("BARCAN-TAG-01");
                    item.setStatus(WishlistStatus.pending);
                    return item;
                });

        bootstrap.setContent("EMS bootstrap: define the repository execution boundary and local runtime contract before feature implementation.");
        bootstrap.setCompiledByRole(ORCHESTRATOR_ROLE);
        bootstrap.setJtbd("When a new project starts, I want the repository structure, runtime commands, and backend/frontend boundaries to be explicit, so that Jules can execute short role-owned tasks without guessing where code belongs.");
        bootstrap.setLeanValue(LeanValue.essential);
        bootstrap.setTocConstraintRef(ENVIRONMENT_BOOTSTRAP_TOC);
        bootstrap.setSixSigmaMetric("Reduce setup and dispatch defects by verifying the project scaffold before role-specific feature work.");
        bootstrap.setDod("Repository/runtime boundary contract exists in README.md or docs/architecture/bootstrap.md and names setup, run, test, backend, frontend, and handoff boundaries. Role: BARCAN-TAG-01. Compiler role: BARCAN-TAG-09.");
        bootstrap.setAcceptanceCriteria("Given Jules starts any implementation task, When the agent inspects the repository, Then it can identify the project root, install command, run command, test command, backend boundary, frontend boundary, and where new code must be placed without asking the human operator.");
        if (bootstrap.getStatus() == WishlistStatus.converted_to_task) {
            bootstrap.setStatus(WishlistStatus.pending);
        }

        WishlistEntity saved = wishlistRepository.save(bootstrap);
        TaskEntity task = technicalLeadCompiler.createTaskFromWishlist(
                saved.getId(),
                null,
                "EMS-bootstrap-" + shortId(saved.getId()),
                1,
                1,
                "environment bootstrap is required before feature flow dispatch"
        );
        if (task != null) {
            completeBootstrapDeterministically(project, task);
        }
        return Optional.ofNullable(task);
    }

    // This task's own Definition of Done never referenced wishlist content - it only asks for a
    // repository/runtime boundary doc naming setup, run, test, backend, and frontend boundaries. That is
    // knowable directly from the project's own identity fields and the multi-stack CI template every
    // project already gets (ProjectWorkspaceFactoryService's ciWorkflow), without needing an AI session to
    // "figure it out" - and this exact task was one of the most common places a real Jules session got
    // stuck in review-rejection loops (confirmed live in test-twenty-seventh: sat in revising for 1+ hour).
    // Deterministic and honest about its own limits: if the commit fails for any reason, the task is left
    // queued so it falls back to the normal Jules dispatch path rather than silently losing the work.
    private void completeBootstrapDeterministically(ProjectEntity project, TaskEntity task) {
        String content = bootstrapDocContent(project);
        boolean committed = gitHubPullRequestService.commitFile(
                project,
                "docs/architecture/bootstrap.md",
                content.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "EMS bootstrap: repository execution boundary and runtime contract"
        );
        if (committed) {
            task.setStatus(TaskStatus.done);
            taskRepository.save(task);
            log.info("Environment bootstrap for project {} completed deterministically by the backend (no Jules session needed): docs/architecture/bootstrap.md", project.getId());
        } else {
            log.warn("Environment bootstrap commit failed for project {}; leaving task queued for normal Jules dispatch as fallback", project.getId());
        }
    }

    private String bootstrapDocContent(ProjectEntity project) {
        return """
                # Repository Execution Boundary and Runtime Contract

                Generated deterministically at project bootstrap - not by a Jules session. This documents
                the same structure every Eneik-generated project starts from; it does not depend on the
                client wishlist.

                ## Identity

                - Project: %s
                - Repository: %s

                ## Setup, run, test

                Detected by manifest file presence (matches .github/workflows/ci.yml):
                - `pom.xml` present -> Java/Maven backend: `mvn test` to verify, `mvn spring-boot:run` (or the
                  project's documented entrypoint) to run.
                - `package.json` present -> Node/frontend: `npm ci`, `npm test --if-present`, `npm run build --if-present`.
                - `requirements.txt` present -> Python service: `pip install -r requirements.txt`, `pytest`.

                Until a role's first PR introduces one of these manifests, that boundary is not yet
                established - implementers should create the manifest as part of their first real change,
                not assume one already exists.

                ## Backend / frontend / handoff boundaries

                - Backend code belongs under a top-level backend source root (e.g. `src/main/java` for Java).
                - Frontend code belongs under `frontend/` (e.g. `frontend/src` for a Svelte/Node frontend).
                - Cross-cutting docs belong under `docs/`.
                - New code must be placed under the boundary matching its role; do not mix backend and
                  frontend concerns in the same source root.
                """.formatted(project.getName(), project.getRepositoryUrl());
    }

    private Optional<WishlistEntity> findEnvironmentBootstrapWishlist(UUID projectId) {
        return wishlistRepository.findByProjectId(projectId).stream()
                .filter(wishlist -> ENVIRONMENT_BOOTSTRAP_TOC.equals(wishlist.getTocConstraintRef())
                        || (wishlist.getContent() != null && wishlist.getContent().contains("EMS bootstrap: define the repository execution boundary")))
                .findFirst();
    }

    private Optional<TaskEntity> findEnvironmentBootstrapTask(UUID projectId) {
        return taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .filter(task -> task.getPayload() != null)
                .filter(task -> ENVIRONMENT_BOOTSTRAP_TOC.equals(task.getPayload().path("toc_constraint_ref").asText()))
                .findFirst();
    }

    private String shortId(UUID id) {
        String value = id == null ? UUID.randomUUID().toString() : id.toString();
        return value.substring(0, Math.min(8, value.length()));
    }

    @Transactional
    public Map<String, Object> closeBadJulesSession(UUID projectId, UUID sessionId, String reason) {
        ProjectEntity project = requireActiveProject(projectId);
        Map<UUID, TaskEntity> tasksById = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(TaskEntity::getId, task -> task, (a, b) -> a));

        Optional<JulesSessionEntity> target = selectBadSession(tasksById, sessionId);
        if (target.isEmpty()) {
            return Map.of(
                    "status", "none",
                    "message", "No active Jules session was eligible for bad-session closure.",
                    "projectId", project.getId()
            );
        }

        JulesSessionEntity session = target.get();
        TaskEntity task = tasksById.get(session.getTaskId());
        String closureReason = firstNonBlank(reason,
                "operator_bad_session_closed: destructive loop, stale work, irrelevant activity, or no concrete next action");
        session.setStatus("loop_closed");
        session.setClosedAt(Instant.now());
        session.setClosureReason(closureReason);
        julesSessionRepository.save(session);

        if (task != null && task.getStatus() != TaskStatus.done && task.getStatus() != TaskStatus.failed) {
            claimService.closeTaskAsBlocked(task.getId(), closureReason);
        }

        Optional<UUID> postmortemWishlistId = createSessionPostmortemWishlist(project.getId(), session.getId(), closureReason);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "closed");
        result.put("sessionId", session.getId());
        result.put("externalSessionId", session.getExternalSessionId());
        result.put("taskId", session.getTaskId());
        result.put("taskRole", task != null && task.getRole() != null ? task.getRole().getTag() : null);
        result.put("closureReason", closureReason);
        result.put("postmortemWishlistId", postmortemWishlistId.orElse(null));
        result.put("message", "Closed one bad Jules session and created a postmortem wishlist for fresh atomic recovery work.");
        return result;
    }

    @Transactional
    public Optional<UUID> createSessionPostmortemWishlist(UUID projectId, UUID sessionId, String reason) {
        ProjectEntity project = requireActiveProject(projectId);
        JulesSessionEntity session = julesSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Jules session not found: " + sessionId));
        TaskEntity task = taskRepository.findById(session.getTaskId())
                .orElseThrow(() -> new IllegalArgumentException("Task not found for session: " + session.getTaskId()));
        if (task.getProject() == null || !project.getId().equals(task.getProject().getId())) {
            throw new IllegalArgumentException("Jules session does not belong to project " + project.getId());
        }

        String marker = "Operator postmortem source session: " + session.getId();
        boolean exists = wishlistRepository.findByProjectId(project.getId()).stream()
                .map(WishlistEntity::getContent)
                .anyMatch(content -> content != null && content.contains(marker));
        if (exists) {
            return Optional.empty();
        }

        List<JulesActivityResponseEntity> responses =
                julesActivityResponseRepository.findByJulesSessionIdOrderByCreatedAtDesc(session.getId());
        String roleTag = task.getRole() != null ? task.getRole().getTag() : ORCHESTRATOR_ROLE;
        String latestQuestion = responses.stream()
                .map(JulesActivityResponseEntity::getQuestion)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("No recorded Jules question was available.");

        WishlistEntity followUp = new WishlistEntity();
        followUp.setProjectId(project.getId());
        followUp.setSource(WishlistSource.role_mismatch_followup);
        followUp.setSourceRoleTag(roleTag);
        followUp.setStatus(WishlistStatus.pending);
        followUp.setContent(truncate("""
                [Operator postmortem from bad Jules session]
                Operator postmortem source session: %s
                External Jules session: %s
                Original task: %s
                Original role: %s
                Closure reason: %s

                Six Sigma CTQ:
                - Defect type: bad Jules session / loop / stale blocker / non-actionable dialogue.
                - Cost of poor quality: one blocked task plus wasted Jules interaction budget.

                Kano: Must-Be
                Cynefin: complicated
                JTBD: When a Jules session becomes destructive or non-actionable, I want the smallest recoverable work item to be replanned, so the project flow resumes without repeating the same loop.

                New short Jules session:
                - Inspect only the original task context and the latest blocker evidence.
                - Choose exactly one atomic fix, verification, or repository-hygiene action.
                - Open one branch and one PR.
                - If the root cause is still ambiguous, document one precise blocker and stop.

                DoD:
                - One concrete owner-role result is completed or one precise blocker is written.
                - Verification command/result is included in the PR or blocker note.
                - No broad redesign, no duplicate task generation, no generated artifacts.
                - Dialogue budget remains below 8 orchestrator replies.

                Latest blocker evidence:
                %s
                """.formatted(
                session.getId(),
                valueOrUnset(session.getExternalSessionId()),
                task.getId(),
                roleTag,
                firstNonBlank(reason, valueOrUnset(session.getClosureReason())),
                truncate(latestQuestion, 1_200)
        ), 6_000));
        WishlistEntity saved = wishlistRepository.save(followUp);
        return Optional.of(saved.getId());
    }

    private Optional<JulesSessionEntity> selectBadSession(Map<UUID, TaskEntity> tasksById, UUID sessionId) {
        if (sessionId != null) {
            return julesSessionRepository.findById(sessionId)
                    .filter(session -> tasksById.containsKey(session.getTaskId()))
                    .filter(this::isActiveJulesSession);
        }
        return julesSessionRepository.findAll().stream()
                .filter(session -> tasksById.containsKey(session.getTaskId()))
                .filter(this::isActiveJulesSession)
                .sorted(Comparator
                        .comparingInt((JulesSessionEntity session) -> badSessionRisk(session)).reversed()
                        .thenComparing(JulesSessionEntity::getUpdatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .findFirst();
    }

    private boolean isActiveJulesSession(JulesSessionEntity session) {
        String status = session.getStatus();
        return "queued".equals(status)
                || "running".equals(status)
                || "revising".equals(status)
                || "stuck".equals(status);
    }

    private int badSessionRisk(JulesSessionEntity session) {
        int score = switch (session.getStatus()) {
            case "stuck" -> 100;
            case "revising" -> 70;
            case "running" -> 50;
            case "queued" -> 20;
            default -> 0;
        };
        if (session.getUpdatedAt() != null) {
            long ageMinutes = Duration.between(session.getUpdatedAt(), Instant.now()).toMinutes();
            score += (int) Math.min(80, Math.max(0, ageMinutes / 5));
        }
        int responses = julesActivityResponseRepository.findByJulesSessionIdOrderByCreatedAtDesc(session.getId()).size();
        score += Math.min(80, responses * 8);
        return score;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private void recordOrchestrationStartOrThrow(UUID projectId) {
        Instant now = Instant.now();
        ProjectGenerationStateEntity state = projectGenerationStateRepository.findById(projectId)
                .orElseGet(() -> {
                    ProjectGenerationStateEntity newState = new ProjectGenerationStateEntity();
                    newState.setProjectId(projectId);
                    return newState;
                });

        Instant last = state.getLastOrchestratedAt();
        if (last != null) {
            long elapsedSeconds = Duration.between(last, now).getSeconds();
            if (elapsedSeconds < ORCHESTRATION_COOLDOWN_SECONDS) {
                throw new OrchestrationCooldownException(ORCHESTRATION_COOLDOWN_SECONDS - elapsedSeconds);
            }
        }

        state.setLastOrchestratedAt(now);
        projectGenerationStateRepository.saveAndFlush(state);
    }

    @Transactional
    public int recoverBlockedWork(UUID projectId) {
        ProjectEntity project = requireActiveProject(projectId);
        int budget = Math.max(1, maxRecoveryItemsPerRun);
        int created = compilePendingRecoveryWishlist(project, budget);

        int remaining = budget - created;
        if (remaining > 0) {
            int wishlistCreated = createRecoveryWishlistForOrphanedBlockedTasks(project, remaining);
            if (wishlistCreated > 0) {
                created += compilePendingRecoveryWishlist(project, remaining);
            }
        }

        if (created > 0) {
            log.info("ProjectFlowService: recovered {} blocked work item(s) for project {}", created, project.getName());
        }
        return created;
    }

    private int compilePendingRecoveryWishlist(ProjectEntity project, int limit) {
        if (limit <= 0) {
            return 0;
        }
        List<WishlistEntity> pendingRecovery = wishlistRepository
                .findByProjectIdAndStatus(project.getId(), WishlistStatus.pending)
                .stream()
                .filter(this::isAutonomousRecoveryWishlist)
                .limit(limit)
                .toList();

        int created = 0;
        for (WishlistEntity wishlist : pendingRecovery) {
            try {
                List<UUID> before = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())
                        .stream()
                        .map(TaskEntity::getId)
                        .toList();
                if (compileWishlistIntoAtomicSlices(project, wishlist)) {
                    long newTasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())
                            .stream()
                            .map(TaskEntity::getId)
                            .filter(id -> !before.contains(id))
                            .count();
                    created += Math.max(1, (int) newTasks);
                }
            } catch (Exception e) {
                log.error("ProjectFlowService: failed to compile recovery wishlist {} for project {}",
                        wishlist.getId(), project.getName(), e);
            }
        }
        return created;
    }

    private boolean isAutonomousRecoveryWishlist(WishlistEntity wishlist) {
        return wishlist.getSource() == WishlistSource.role_mismatch_followup;
    }

    private int createRecoveryWishlistForOrphanedBlockedTasks(ProjectEntity project, int limit) {
        if (limit <= 0) {
            return 0;
        }
        List<TaskEntity> blockedTasks = taskRepository
                .findByProjectIdAndStatusOrderByPriorityDescCreatedAtAsc(project.getId(), TaskStatus.blocked);
        if (blockedTasks.isEmpty()) {
            return 0;
        }

        List<String> existingRecoveryContent = wishlistRepository.findByProjectId(project.getId())
                .stream()
                .filter(this::isAutonomousRecoveryWishlist)
                .map(WishlistEntity::getContent)
                .filter(Objects::nonNull)
                .toList();

        int created = 0;
        for (TaskEntity task : blockedTasks) {
            if (created >= limit) {
                break;
            }
            if (hasActiveJulesSession(task.getId())) {
                continue;
            }
            String taskId = task.getId().toString();
            boolean alreadyCovered = existingRecoveryContent.stream().anyMatch(content -> content.contains(taskId));
            if (alreadyCovered) {
                continue;
            }

            WishlistEntity followUp = new WishlistEntity();
            followUp.setProjectId(project.getId());
            followUp.setSource(WishlistSource.role_mismatch_followup);
            followUp.setSourceRoleTag(task.getRole() != null ? task.getRole().getTag() : ORCHESTRATOR_ROLE);
            followUp.setStatus(WishlistStatus.pending);
            followUp.setContent(truncate("""
                    [Auto recovery from blocked task]
                    Auto recovery source task: %s
                    Original role: %s
                    Previous dispatch status: %s
                    Previous retry count: %s

                    Goal:
                    Replace the blocked attempt with one fresh, short Jules session.

                    Scope rule:
                    - Do not continue the old branch conversation.
                    - Do not revive the old oversized task as-is.
                    - Start one atomic slice with one objective acceptance criterion.
                    - If more work is discovered, write it as a separate wishlist item.

                    Original task:
                    %s

                    DoD:
                    One small branch, one PR, at most two tightly related source areas, explicit verification command, no generated artifacts.
                    """.formatted(
                    taskId,
                    task.getRole() != null ? task.getRole().getTag() : "unknown-role",
                    valueOrUnset(task.getJulesDispatchStatus()),
                    task.getRetryCount(),
                    task.getDescription()
            ), 7_500));
            wishlistRepository.save(followUp);
            existingRecoveryContent = new ArrayList<>(existingRecoveryContent);
            existingRecoveryContent.add(followUp.getContent());
            created++;
            log.info("ProjectFlowService: created recovery wishlist for blocked task {} in project {}",
                    task.getId(), project.getName());
        }
        return created;
    }

    private boolean hasActiveJulesSession(UUID taskId) {
        return julesSessionRepository.findByTaskId(taskId).stream()
                .anyMatch(session -> {
                    String status = session.getStatus();
                    return session.getExternalSessionId() != null
                            && !"skipped".equals(session.getExternalSessionId())
                            && ("queued".equals(status)
                            || "running".equals(status)
                            || "revising".equals(status)
                            || "pr_opened".equals(status)
                            || "stuck".equals(status));
                });
    }

    private String valueOrUnset(String value) {
        return value == null || value.isBlank() ? "<unset>" : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength));
    }

    private boolean compileWishlistIntoAtomicSlices(ProjectEntity project, WishlistEntity wishlist) {
        if (wishlist.getCompiledByRole() != null) {
            if (wishlist.getLeanValue() != LeanValue.waste) {
                technicalLeadCompiler.createTaskFromWishlist(wishlist.getId());
                return true;
            }
            return false;
        }

        if (wishlist.getSource() == WishlistSource.role_mismatch_followup) {
            // System-generated circuit-breaker recovery text, not real client content - stays on the
            // fast deterministic path instead of spending the paid compiler account's limited capacity.
            java.util.List<MLPredictionServiceClient.TaskSliceMetadata> slices = resolveTaskSlices(wishlist);
            if (slices.isEmpty()) {
                return false;
            }
            MLPredictionServiceClient.TaskSliceMetadata slice = slices.get(0);
            String ownerRole = targetRoleForSlice(wishlist, slice);
            wishlist.setSourceRoleTag(ownerRole);
            wishlistRepository.save(wishlist);
            compileSliceMetadata(project, wishlist.getId(), slice, ownerRole);
            technicalLeadCompiler.createTaskFromWishlist(
                    wishlist.getId(),
                    null,
                    emsGraphKey(wishlist, "recovery"),
                    1,
                    1,
                    "circuit-breaker recovery starts a fresh one-node graph"
            );
            return true;
        }

        // Real client-originated content must never reach an implementer directly - a poorly written
        // wishlist would go straight into work with no correction step. It is routed through a dedicated
        // Jules compiler session (dispatchWishlistCompiler) that decomposes it into a proper
        // JTBD/Kano/Cynefin-classified task graph - the same job Gemini used to do before its billing
        // ran out. The compiler's own PR result is what eventually calls buildTaskGraphFromSlices.
        if (wishlist.getStatus() == WishlistStatus.pending) {
            dispatchWishlistCompiler(project, wishlist);
        }
        return false;
    }

    /**
     * Turns a resolved slice list into the dependency-graph of child wishlists/tasks that used to be
     * built inline here. Called by the Jules compiler result path
     * (JulesDispatchService.completeWishlistCompilation) once a compiler session's slices are parsed
     * and validated.
     */
    public boolean buildTaskGraphFromSlices(ProjectEntity project, WishlistEntity wishlist,
            java.util.List<MLPredictionServiceClient.TaskSliceMetadata> slices) {
        java.util.List<MLPredictionServiceClient.TaskSliceMetadata> graphSlices = emsGraphSlices(wishlist, slices);
        if (graphSlices.isEmpty()) {
            wishlist.setStatus(WishlistStatus.converted_to_task);
            wishlistRepository.save(wishlist);
            return false;
        }

        String graphKey = emsGraphKey(wishlist, "flow");
        TaskEntity previousTask = null;
        int index = 1;
        for (MLPredictionServiceClient.TaskSliceMetadata slice : graphSlices) {
            WishlistEntity sliceWishlist = new WishlistEntity();
            sliceWishlist.setProjectId(project.getId());
            sliceWishlist.setSource(wishlist.getSource());
            String ownerRole = targetRoleForSlice(wishlist, slice);
            sliceWishlist.setSourceRoleTag(ownerRole);
            sliceWishlist.setContent(internalSliceContent(wishlist, slice, index));
            sliceWishlist.setStatus(WishlistStatus.pending);
            sliceWishlist = wishlistRepository.save(sliceWishlist);
            compileSliceMetadata(project, sliceWishlist.getId(), slice, ownerRole);
            TaskEntity createdTask = technicalLeadCompiler.createTaskFromWishlist(
                    sliceWishlist.getId(),
                    previousTask,
                    graphKey,
                    index,
                    graphSlices.size(),
                    dependencyEdgeReason(previousTask, ownerRole)
            );
            previousTask = createdTask != null ? createdTask : previousTask;
            index++;
        }

        wishlist.setStatus(WishlistStatus.converted_to_task);
        wishlistRepository.save(wishlist);
        return true;
    }

    private java.util.List<MLPredictionServiceClient.TaskSliceMetadata> emsGraphSlices(
            WishlistEntity wishlist,
            java.util.List<MLPredictionServiceClient.TaskSliceMetadata> slices) {
        java.util.Map<String, MLPredictionServiceClient.TaskSliceMetadata> unique = new java.util.LinkedHashMap<>();
        for (MLPredictionServiceClient.TaskSliceMetadata slice : slices) {
            if (slice.leanValue() == LeanValue.waste) {
                continue;
            }
            String key = sliceSemanticKey(wishlist, slice);
            unique.putIfAbsent(key, slice);
        }
        return unique.values().stream()
                .sorted(java.util.Comparator
                        .comparingInt((MLPredictionServiceClient.TaskSliceMetadata slice) -> emsStageOrder(targetRoleForSlice(wishlist, slice)))
                        .thenComparing(slice -> normalizeForGraph(slice.title()))
                        .thenComparing(slice -> normalizeForGraph(slice.jtbd())))
                .toList();
    }

    private String sliceSemanticKey(WishlistEntity wishlist, MLPredictionServiceClient.TaskSliceMetadata slice) {
        return targetRoleForSlice(wishlist, slice) + "|"
                + normalizeForGraph(slice.jtbd()) + "|"
                + normalizeForGraph(slice.acceptanceCriteria());
    }

    private String normalizeForGraph(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private int emsStageOrder(String roleTag) {
        return switch (roleTag) {
            case "BARCAN-TAG-09" -> 10;
            case "BARCAN-TAG-01" -> 20;
            case "BARCAN-TAG-08", "BARCAN-TAG-07", "BARCAN-TAG-04", "BARCAN-TAG-02" -> 30;
            case "BARCAN-TAG-03", "BARCAN-TAG-11" -> 40;
            case "BARCAN-TAG-05" -> 50;
            case "BARCAN-TAG-10" -> 55;
            case "BARCAN-TAG-06" -> 60;
            case "BARCAN-TAG-00" -> 70;
            default -> 35;
        };
    }

    private String dependencyEdgeReason(TaskEntity previousTask, String ownerRole) {
        if (previousTask == null) {
            return "graph root: first owner-role slice from the wishlist";
        }
        String previousRole = previousTask.getRole() != null ? previousTask.getRole().getTag() : "previous-role";
        return "EMS ordered flow: " + ownerRole + " waits for " + previousRole + " to finish the previous verifiable slice";
    }

    private String emsGraphKey(WishlistEntity wishlist, String suffix) {
        String id = wishlist.getId() == null ? UUID.randomUUID().toString() : wishlist.getId().toString();
        return "EMS-" + suffix + "-" + id.substring(0, Math.min(8, id.length()));
    }

    // Deliberately Gemini-free: task compilation used to route through mlPredictionServiceClient's
    // Gemini-backed slice/metadata generation, which silently fell back to a generic, fabricated slice
    // on any failure (including the Gemini billing exhaustion this system has been running under) -
    // producing plausible-looking but content-free tasks with zero connection to the real wishlist.
    // The user does not want to pay for Gemini generations; Jules itself (already paid for, already
    // working) is the one that reads and compiles the real brief now - it receives the full original
    // wishlist content verbatim via TechnicalLeadCompiler.buildTaskDescription's "Original Brief"
    // section, not a pre-digested AI summary.
    private static final String DEFAULT_TASK_COMPILER_ACCOUNT_NAME = "eneikdru";

    private String taskCompilerAccountName() {
        String configured = settingsService.effectiveValue("task_compiler_account_name");
        return configured == null || configured.isBlank() ? DEFAULT_TASK_COMPILER_ACCOUNT_NAME : configured;
    }

    private java.util.List<MLPredictionServiceClient.TaskSliceMetadata> resolveTaskSlices(WishlistEntity wishlist) {
        return java.util.List.of(fallbackTaskSlice(wishlist.getContent()));
    }

    // Matches the exact follow-up content JulesDispatchService.completeDesignReview writes for each
    // non-blocking concern: "Design reviewer concern (non-blocking) on design/approved/{path}: {text}".
    private static final java.util.regex.Pattern DESIGN_CONCERN_APPROVED_PATH_PATTERN =
            java.util.regex.Pattern.compile("Design reviewer concern \\(non-blocking\\) on (design/approved/[^:]+):");

    // A slice compiled from a design-review concern is a correction to an already-approved mockup, not
    // new UI surface - re-running Stitch and a fresh design-review session for it never converges: the
    // new mockup gets its own review, which finds a new concern on some other element, which spawns
    // another correction slice, forever (confirmed live in test-twenty-sixth: 5 chained design-review
    // cycles, same "touch target" class of finding recurring on a different element each time, still
    // generating new pending concerns when the operator stopped the run). The parent chain's content is
    // preserved verbatim through internalSliceContent's wrapping, so this matches regardless of how many
    // compiler generations deep the concern has travelled.
    private String approvedDesignPathFromFollowUp(UUID wishlistId) {
        WishlistEntity wishlist = wishlistRepository.findById(wishlistId).orElse(null);
        if (wishlist == null || wishlist.getContent() == null) {
            return null;
        }
        var matcher = DESIGN_CONCERN_APPROVED_PATH_PATTERN.matcher(wishlist.getContent());
        return matcher.find() ? matcher.group(1) : null;
    }

    private void compileSliceMetadata(ProjectEntity project, UUID wishlistId, MLPredictionServiceClient.TaskSliceMetadata slice, String ownerRole) {
        String acceptanceCriteria = defaultText(slice.acceptanceCriteria(), fallbackTaskSlice("").acceptanceCriteria());
        if (slice.hasUi() || "BARCAN-TAG-11".equals(ownerRole) || "BARCAN-TAG-03".equals(ownerRole)) {
            String approvedDesignPath = approvedDesignPathFromFollowUp(wishlistId);
            if (approvedDesignPath != null) {
                acceptanceCriteria = acceptanceCriteria + "\n\nDESIGN_MOCKUP_ASSET (already approved - this is a correction to existing design, not new UI; implement directly against it, no new mockup or design review needed): "
                        + approvedDesignPath + "/mockup.html";
            } else {
                try {
                    var context = contextService.build(project.getId(), project.getName());
                    String brief = "Create visual reference mockup for: " + slice.jtbd();
                    var designResult = designAssetService.generateAsset(
                            project,
                            context,
                            brief,
                            "mockup",
                            "fast",
                            false
                    );
                    if (designResult.available() && "ok".equals(designResult.status())) {
                        // Only reference the GitHub-committed draft path - a Jules session has no access to
                        // the Eneik backend's own local disk (designResult.imagePath()), so a local-only path
                        // is a dead reference (confirmed live in the test-twenty-fifth experiment). If the
                        // GitHub commit itself failed, skip the reference entirely rather than hand Jules
                        // something it cannot open.
                        if (!designResult.repoDraftPath().isBlank()) {
                            acceptanceCriteria = acceptanceCriteria + "\n\nDESIGN_MOCKUP_ASSET (draft, pending design review - read directly from this repo checkout): "
                                    + designResult.repoDraftPath() + "/mockup.html";
                            dispatchDesignReview(project, designResult.repoDraftPath(), brief);
                        }
                    }
                } catch (Exception e) {
                    log.warn("DesignAsset pre-generation failed: " + e.getMessage());
                }
            }
        }

        technicalLeadCompiler.compile(
                wishlistId,
                ORCHESTRATOR_ROLE,
                defaultText(slice.jtbd(), fallbackTaskSlice("").jtbd()),
                slice.leanValue() != null ? slice.leanValue() : LeanValue.essential,
                defaultText(slice.tocConstraintRef(), "TOC-CONSTRAINT-DECOMPOSITION"),
                defaultText(slice.sixSigmaMetric(), "Escaped defects <= 5%"),
                compiledDod(ownerRole, slice),
                acceptanceCriteria
        );
    }

    private String compiledDod(String ownerRole, MLPredictionServiceClient.TaskSliceMetadata slice) {
        String roleSpecificReadiness = switch (ownerRole) {
            case "BARCAN-TAG-03" -> "UI/design readiness: follow docs/DESIGN_SYSTEM.md for layout, visual states, and interaction evidence. Deliverable is a committed HTML/CSS mockup file - a written brief or description with no mockup file is not acceptable.";
            case "BARCAN-TAG-11" -> "Frontend readiness: implement browser UI according to docs/DESIGN_SYSTEM.md and verify the user-visible interaction.";
            default -> "Role readiness: complete the smallest owner-role result without expanding scope.";
        };
        return "Compiled from English JTBD work item by Eneik Management System. Owner role: "
                + ownerRole + ". Role refusal criteria: " + ownerRole + ". Compiler role: BARCAN-TAG-09. Kano: "
                + defaultText(slice.kanoClass(), "Must-Be") + ". Cynefin: " + defaultText(slice.cynefinDomain(), "clear") + ". "
                + roleSpecificReadiness;
    }

    private String targetRoleForSlice(WishlistEntity parent, MLPredictionServiceClient.TaskSliceMetadata slice) {
        if (parent.getSource() == WishlistSource.role_mismatch_followup
                || parent.getSource() == WishlistSource.self_falsification
                || parent.getSource() == WishlistSource.chaotic_debt) {
            return normalizeRoleTag(parent.getSourceRoleTag(), slice);
        }
        return normalizeRoleTag(slice.roleTag(), slice);
    }

    private String normalizeRoleTag(String value, MLPredictionServiceClient.TaskSliceMetadata slice) {
        if (value != null && value.matches("BARCAN-TAG-(0[0-9]|1[0-1])")) {
            return value;
        }
        return inferRoleTag(slice);
    }

    private String inferRoleTag(MLPredictionServiceClient.TaskSliceMetadata slice) {
        String source = ((slice.title() != null ? slice.title() : "") + " "
                + (slice.jtbd() != null ? slice.jtbd() : "") + " "
                + (slice.acceptanceCriteria() != null ? slice.acceptanceCriteria() : ""));
        return inferRoleTag(source, slice.hasUi());
    }

    private String inferRoleTag(String sourceText, boolean hasUi) {
        String source = sourceText == null ? "" : sourceText.toLowerCase(Locale.ROOT);
        if (source.contains("merge") || source.contains("integration") || source.contains("repository hygiene")
                || source.contains("generated artifact") || source.contains("pr diff")) {
            return "BARCAN-TAG-00";
        }
        if (source.contains("architecture") || source.contains("mvc") || source.contains("microservice")
                || source.contains("service boundary") || source.contains("adr")) {
            return "BARCAN-TAG-01";
        }
        if (source.contains("security") || source.contains("auth") || source.contains("credential")
                || source.contains("permission") || source.contains("access-control") || source.contains("login")) {
            return "BARCAN-TAG-07";
        }
        if (source.contains("database") || source.contains("schema") || source.contains("migration")
                || source.contains("storage") || source.contains("csv") || source.contains("pdf")
                || source.contains("parse") || source.contains("upload")) {
            return "BARCAN-TAG-08";
        }
        if (source.contains("ai") || source.contains("llm") || source.contains("model")
                || source.contains("prompt") || source.contains("rag") || source.contains("embedding")) {
            return "BARCAN-TAG-04";
        }
        if (source.contains("legal") || source.contains("tax law") || source.contains("compliance")
                || source.contains("regulatory") || source.contains("disclaimer")) {
            return "BARCAN-TAG-10";
        }
        if (source.contains("test") || source.contains("qa") || source.contains("verify")
                || source.contains("verification") || source.contains("e2e")) {
            return "BARCAN-TAG-06";
        }
        if (source.contains("docker") || source.contains("deploy") || source.contains("ci")
                || source.contains("build") || source.contains("pipeline")) {
            return "BARCAN-TAG-05";
        }
        if (source.contains("design") || source.contains("mockup") || source.contains("wireframe")
                || source.contains("ux")) {
            return "BARCAN-TAG-03";
        }
        if (hasUi || source.contains("frontend") || source.contains("svelte") || source.contains("browser")
                || source.contains("screen") || source.contains("page") || source.contains("button")
                || source.contains("form") || source.contains("ui")) {
            return "BARCAN-TAG-11";
        }
        return "BARCAN-TAG-02";
    }

    // The short wrapper line stays for human traceability (which parent wishlist, which role, which
    // index), but the parent's real full content is appended below it, unmodified - this is what
    // ultimately reaches TechnicalLeadCompiler.buildTaskDescription's "Original Brief" section via
    // wishlist.getContent() on this child wishlist. Previously this method replaced the real content
    // with just the (now honest-but-still-truncated) slice title, so even a correctly non-fabricated
    // title never carried the actual client text through to what Jules reads.
    private String internalSliceContent(WishlistEntity parent, MLPredictionServiceClient.TaskSliceMetadata slice, int index) {
        String uiMarker = (slice.hasUi()
                || looksLikeUi(slice.title() + " " + slice.jtbd() + " " + slice.acceptanceCriteria())) ? "UI " : "";
        String wrapper = "Internal " + uiMarker + "work item " + index + " (" + targetRoleForSlice(parent, slice) + ") from wishlist " + parent.getId()
                + ": " + safeSliceTitle(slice.title());
        String parentContent = parent.getContent();
        if (parentContent == null || parentContent.isBlank()) {
            return wrapper;
        }
        return wrapper + "\n\n" + parentContent;
    }

    private String safeSliceTitle(String title) {
        if (title == null || title.isBlank()) {
            return "client-requested capability";
        }
        String compact = title.replaceAll("\\s+", " ").trim();
        if (compact.length() <= 90) {
            return compact;
        }
        return compact.substring(0, 87) + "...";
    }

    private MLPredictionServiceClient.TaskSliceMetadata fallbackTaskSlice(String wishlistContent) {
        String label = featureLabel(wishlistContent);
        return new MLPredictionServiceClient.TaskSliceMetadata(
                label,
                "When I use the " + label + " slice, I want one small verifiable capability completed, so project progress can be validated without a long Jules session.",
                "Given this slice is implemented, When the primary happy path is exercised, Then it completes without client-side or server-side errors.\n"
                        + "Given invalid or missing input is submitted, When validation runs, Then the system rejects the request without persisting invalid data.\n"
                        + "Given the PR is ready, When verification runs, Then the relevant command passes and no generated artifacts are committed.",
                inferRoleTag(label + " " + wishlistContent, looksLikeUi(wishlistContent)),
                LeanValue.essential,
                "Must-Be",
                "clear",
                "TOC-CONSTRAINT-DECOMPOSITION",
                "Escaped defects <= 5%",
                looksLikeUi(wishlistContent)
        );
    }

    // Never collapse real content into a generic placeholder string, in any language. The old
    // English-only word-extraction silently discarded non-English (e.g. Cyrillic) briefs entirely,
    // producing the same literal "client-requested capability" label for every non-English wishlist -
    // which then got mistaken for a real, derived title. A short, honest excerpt of the real content
    // (whatever language it's in) is always more truthful than a generic label, since the full original
    // text now also always reaches the task description (see TechnicalLeadCompiler.buildTaskDescription).
    private String featureLabel(String wishlistContent) {
        if (wishlistContent == null || wishlistContent.isBlank()) {
            return "client-requested capability";
        }
        String compact = wishlistContent.replaceAll("\\s+", " ").trim();
        if (compact.isEmpty()) {
            return "client-requested capability";
        }
        return compact.length() <= 60 ? compact : compact.substring(0, 57) + "...";
    }

    private boolean looksLikeUi(String value) {
        String lower = value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("ui") || lower.contains("ux") || lower.contains("frontend")
                || lower.contains("screen") || lower.contains("page") || lower.contains("form")
                || lower.contains("button") || lower.contains("browser") || lower.contains("svelte")
                || lower.contains("design") || lower.contains("admin") || lower.contains("panel")
                || lower.contains("portal") || lower.contains("dashboard") || lower.contains("public");
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public static final String WISHLIST_COMPILER_TASK_TYPE = "wishlist_compiler";
    public static final String WISHLIST_COMPILER_PAYLOAD_KEY = "taskType";
    public static final String WISHLIST_COMPILER_WISHLIST_ID_KEY = "compilesWishlistId";

    private void dispatchWishlistCompiler(ProjectEntity project, WishlistEntity wishlist) {
        wishlist.setStatus(WishlistStatus.compiling);
        wishlistRepository.save(wishlist);

        RoleEntity compilerRole = roleRepository.findById(ORCHESTRATOR_ROLE).orElse(null);
        if (compilerRole == null) {
            log.error("Cannot dispatch wishlist compiler for wishlist {}: role {} not found",
                    wishlist.getId(), ORCHESTRATOR_ROLE);
            return;
        }

        TaskEntity compilerTask = new TaskEntity();
        compilerTask.setProject(project);
        compilerTask.setRole(compilerRole);
        // Suffixed with a short wishlist id fragment: an identical literal title across multiple compiler
        // dispatches in the same project (a normal, legitimate occurrence) was tripping
        // ContinuousOrchestrationService's duplicate-task-title alarm as a false positive.
        compilerTask.setTitle("Compile wishlist into task graph (" + shortId(wishlist.getId()) + ")");
        compilerTask.setDescription(wishlistCompilerPrompt(wishlist));
        compilerTask.setStatus(TaskStatus.queued);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put(WISHLIST_COMPILER_PAYLOAD_KEY, WISHLIST_COMPILER_TASK_TYPE);
        payload.put(WISHLIST_COMPILER_WISHLIST_ID_KEY, wishlist.getId().toString());
        compilerTask.setPayload(payload);

        compilerTask = taskRepository.save(compilerTask);
        dispatchCompilerTask(compilerTask);
    }

    public boolean isWishlistCompilerTask(TaskEntity task) {
        return task.getPayload() != null
                && WISHLIST_COMPILER_TASK_TYPE.equals(task.getPayload().path(WISHLIST_COMPILER_PAYLOAD_KEY).asText(null));
    }

    private void dispatchCompilerTask(TaskEntity compilerTask) {
        Optional<AccountEntity> accountOpt = accountRepository.lockAccountByNameWithCapacity(
                taskCompilerAccountName(), maxConcurrentJulesSessionsPerAccount);
        if (accountOpt.isEmpty()) {
            log.warn("Wishlist compiler account '{}' has no free capacity right now; task {} stays queued for the next cycle",
                    taskCompilerAccountName(), compilerTask.getId());
            return;
        }

        AccountEntity account = accountOpt.get();
        try {
            claimService.claimSpecificTask(compilerTask.getId(), account.getId());
            TaskEntity savedTask = taskRepository.findById(compilerTask.getId()).orElse(compilerTask);
            JulesDispatchResult dispatch = julesDispatchService.dispatch(savedTask, account.getId());
            savedTask.setJulesSessionName(dispatch.sessionName());
            savedTask.setJulesDispatchStatus(dispatch.reason());
            taskRepository.save(savedTask);
            if (!dispatch.dispatched()) {
                claimService.releaseClaimToQueue(savedTask.getId(), dispatch.reason());
                log.warn("Failed to dispatch wishlist compiler task {} to account {}: {}",
                        savedTask.getId(), account.getName(), dispatch.reason());
                return;
            }
            // JulesDispatchService.dispatch() reports dispatched=true both for a genuinely fresh dispatch
            // and for the "already dispatched, skip duplicate" no-op - logging both as "Dispatched compiler
            // task" made the no-op case indistinguishable from a real dispatch in the logs.
            if ("already dispatched, skipping duplicate".equals(dispatch.reason())) {
                log.info("Compiler task {} was already dispatched to account {}; skipped duplicate dispatch", savedTask.getId(), account.getName());
            } else {
                log.info("Dispatched compiler task {} to account {}", savedTask.getId(), account.getName());
            }
        } catch (Exception e) {
            log.error("Failed to claim/dispatch compiler task {} to account {}: {}",
                    compilerTask.getId(), account.getName(), e.getMessage(), e);
        }
    }

    private String wishlistCompilerPrompt(WishlistEntity wishlist) {
        return """
                You are Eneik Technical Lead, Product Owner, and Delivery Manager. Decompose the client
                brief below into executable work items. Do NOT implement any product code, do not run
                builds or tests - this task only produces a decomposition plan.

                Output rules:
                - All output must be in English, even when the source brief is written in another
                  language. Translate and normalize intent; never copy the raw brief text verbatim into
                  your output.
                - Produce 1-6 work items. Each item must have exactly one owner role and one concrete
                  result. Do not multiply one JTBD across roles.
                - Do not create QA or integration items unless the brief explicitly asks to verify
                  existing code, fix merge hygiene, or review an already implemented slice.
                - For complex or ambiguous work, create a short BARCAN-TAG-09 or BARCAN-TAG-01
                  spike/decision item instead of guessing at implementation.
                - Each jtbd must be one sentence: "When..., I want..., so that...".
                - Each acceptanceCriteria field must contain 2-4 role-specific Given/When/Then lines.
                - Classify each item with Kano: Must-Be, Performance, or Attractive.
                - Classify implementation uncertainty with Cynefin: clear, complicated, complex, or chaotic.
                - Choose roleTag from: BARCAN-TAG-00 integration/merge hygiene only; BARCAN-TAG-01
                  architecture; BARCAN-TAG-02 backend/API; BARCAN-TAG-03 UI/UX design; BARCAN-TAG-04
                  AI/ML/RAG; BARCAN-TAG-05 build/Docker/CI/deploy; BARCAN-TAG-06 QA/testing existing
                  implementation only; BARCAN-TAG-07 security/auth/access; BARCAN-TAG-08
                  data/schema/storage/parsing; BARCAN-TAG-09 delivery/spike/decision; BARCAN-TAG-10
                  compliance/legal/policy; BARCAN-TAG-11 frontend/browser implementation.
                - Set hasUi=true only when the item needs visible browser UI/design work.
                - Avoid broad platform epics; split them into smaller user/admin/data/API items.
                - Every item MUST end in a concrete committed file, never only a decision, brief, or
                  discussion with nothing to show for it. Pick the artifact by domain: engineering work
                  (backend/frontend/data/AI/build/security) -> real source code; copywriting or content
                  work -> a text file containing the actual finished copy, not a description of what the
                  copy should say; marketing or pricing work -> a file containing the actual prices/offer
                  text, not a plan to define them later; UI/UX design work -> the design itself saved as
                  a committed HTML/CSS mockup file, never just a written brief describing a mockup someone
                  else should make. A BARCAN-TAG-09 delivery/spike/decision item must still end in a
                  written decision-record file (e.g. a short architecture-decision markdown file) - "we
                  discussed it" is not a deliverable.

                Deliverable: create a new branch and open a PR that contains ONLY one file,
                `.eneik/task-plan.json`, with EXACTLY this shape and no other files changed:
                {"slices": [{"title": "short English title", "roleTag": "BARCAN-TAG-02",
                "jtbd": "When..., I want..., so that...",
                "acceptanceCriteria": "Given..., When..., Then...\\nGiven...",
                "leanValue": "essential|valuable|waste", "kanoClass": "Must-Be|Performance|Attractive",
                "cynefinDomain": "clear|complicated|complex|chaotic",
                "tocConstraintRef": "short bottleneck reference",
                "sixSigmaMetric": "measurable quality metric", "hasUi": true}]}
                Do not write, modify, or delete any other file.

                Client brief to decompose (verbatim, may be in any language - read and understand it
                yourself, do not rely on it already being in English):
                %s
                """.formatted(defaultText(wishlist.getContent(), "(empty brief)"));
    }

    // Deliberately Gemini-free, same reasoning as the wishlist compiler above: refusal-criteria and
    // methodological-falsification checks used to call Gemini once per active role per project on every
    // falsification cycle. Reuses the same reserved eneikdru account and dispatch plumbing
    // (dispatchCompilerTask is generic) - the falsification cron only fires every few hours, so it shares
    // that account's capacity comfortably instead of contending with real product-implementation dispatch.
    public static final String FALSIFICATION_AUDIT_TASK_TYPE = "falsification_audit";
    public static final String FALSIFICATION_AUDIT_REPORT_PATH = ".eneik/falsification-report.json";
    public static final String FALSIFICATION_AUDIT_HIGHEST_PR_KEY = "highestPrNumberAudited";

    public UUID dispatchFalsificationAudit(ProjectEntity project, String prompt, Integer highestPrNumber) {
        RoleEntity compilerRole = roleRepository.findById(ORCHESTRATOR_ROLE).orElse(null);
        if (compilerRole == null) {
            log.error("Cannot dispatch falsification audit for project {}: role {} not found",
                    project.getId(), ORCHESTRATOR_ROLE);
            return null;
        }

        TaskEntity auditTask = new TaskEntity();
        auditTask.setProject(project);
        auditTask.setRole(compilerRole);
        // Suffixed with a short timestamp fragment for the same reason as the compiler task title above -
        // avoid tripping the duplicate-task-title false positive across separate legitimate audit runs.
        auditTask.setTitle("Falsification audit: refusal criteria & methodological review (" + shortId(UUID.randomUUID()) + ")");
        auditTask.setDescription(prompt);
        auditTask.setStatus(TaskStatus.queued);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put(WISHLIST_COMPILER_PAYLOAD_KEY, FALSIFICATION_AUDIT_TASK_TYPE);
        if (highestPrNumber != null) {
            payload.put(FALSIFICATION_AUDIT_HIGHEST_PR_KEY, highestPrNumber);
        }
        auditTask.setPayload(payload);

        auditTask = taskRepository.save(auditTask);
        dispatchCompilerTask(auditTask);
        return auditTask.getId();
    }

    public boolean isFalsificationAuditTask(TaskEntity task) {
        return task.getPayload() != null
                && FALSIFICATION_AUDIT_TASK_TYPE.equals(task.getPayload().path(WISHLIST_COMPILER_PAYLOAD_KEY).asText(null));
    }

    /** True for implementation work compiled from a role/role_mismatch_followup/self_falsification/etc.
     *  wishlist item rather than the client's own brief - the category the BUILD-phase gate defers. Tasks
     *  with no recorded source wishlist (bootstrap, one-off infra tasks) are never gated here. */
    private boolean isSelfGeneratedWork(TaskEntity task) {
        if (task.getSourceWishlistId() == null) {
            return false;
        }
        return wishlistRepository.findById(task.getSourceWishlistId())
                .map(w -> w.getSource() != WishlistSource.client)
                .orElse(false);
    }

    public Integer falsificationAuditHighestPrNumber(TaskEntity task) {
        if (task.getPayload() == null || !task.getPayload().hasNonNull(FALSIFICATION_AUDIT_HIGHEST_PR_KEY)) {
            return null;
        }
        return task.getPayload().path(FALSIFICATION_AUDIT_HIGHEST_PR_KEY).asInt();
    }

    // Fallback reviewer, used only when Gemini's PR review reports VERIFICATION_SERVICE_UNAVAILABLE - so
    // a real outage (or a permanently depleted quota) never leaves an implementer PR stuck unreviewed
    // forever. Dispatches a standalone Jules eneikdru session (same reserved account and generic dispatch
    // plumbing as the compiler/audit above) that reads the real diff and writes a soft verdict: it only
    // blocks for a small enumerated set of critical problems, everything else is approved with concerns
    // recorded as follow-up wishlist items - so work never stalls waiting on a reviewer's opinion, it only
    // ever accumulates improvement backlog.
    public static final String PR_REVIEW_FALLBACK_TASK_TYPE = "pr_review_fallback";
    public static final String PR_REVIEW_FALLBACK_VERDICT_PATH = ".eneik/review-verdict.json";
    public static final String PR_REVIEW_FALLBACK_TASK_ID_KEY = "reviewsTaskId";

    public UUID dispatchReviewFallback(TaskEntity originalTask, String prompt) {
        RoleEntity compilerRole = roleRepository.findById(ORCHESTRATOR_ROLE).orElse(null);
        if (compilerRole == null) {
            log.error("Cannot dispatch PR review fallback for task {}: role {} not found",
                    originalTask.getId(), ORCHESTRATOR_ROLE);
            return null;
        }

        TaskEntity reviewTask = new TaskEntity();
        reviewTask.setProject(originalTask.getProject());
        reviewTask.setRole(compilerRole);
        // Suffixed with the reviewed task's short id - same duplicate-title false-positive reasoning as
        // the compiler/audit task titles above; observed live triggering
        // ContinuousOrchestrationService's DUPLICATE TASK TITLES alarm after only 3 fallback dispatches.
        reviewTask.setTitle("PR review fallback (Gemini unavailable, " + shortId(originalTask.getId()) + ")");
        reviewTask.setDescription(prompt);
        reviewTask.setStatus(TaskStatus.queued);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put(WISHLIST_COMPILER_PAYLOAD_KEY, PR_REVIEW_FALLBACK_TASK_TYPE);
        payload.put(PR_REVIEW_FALLBACK_TASK_ID_KEY, originalTask.getId().toString());
        reviewTask.setPayload(payload);

        reviewTask = taskRepository.save(reviewTask);
        dispatchCompilerTask(reviewTask);
        return reviewTask.getId();
    }

    public boolean isReviewFallbackTask(TaskEntity task) {
        return task.getPayload() != null
                && PR_REVIEW_FALLBACK_TASK_TYPE.equals(task.getPayload().path(WISHLIST_COMPILER_PAYLOAD_KEY).asText(null));
    }

    public UUID reviewFallbackTargetTaskId(TaskEntity task) {
        if (task.getPayload() == null) {
            return null;
        }
        String raw = task.getPayload().path(PR_REVIEW_FALLBACK_TASK_ID_KEY).asText(null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // A generated design mockup used to be an orphaned artifact: DesignAssetService wrote it to the Eneik
    // backend's own local disk, and the only "reference" a task ever got was that local path pasted into
    // Acceptance Criteria text - unreachable by any Jules session, which only ever sees its own GitHub
    // checkout. Confirmed live in the test-twenty-fifth experiment: the mockup was real, the reference to
    // it was structurally dead. Fixed two ways: (1) DesignAssetService now commits the real file into the
    // project's own repo under design/draft/, so a Jules session can read it directly; (2) this method
    // dispatches a real Jules review (role BARCAN-TAG-03) against that draft, applying the same
    // composition/philosophical "attack" a human designer would - only a genuinely severe problem blocks
    // promotion, everything else is approved with concerns recorded as follow-up wishlist work (same soft
    // philosophy as the PR review fallback above: work never stalls waiting on a reviewer's opinion).
    public static final String DESIGN_REVIEW_TASK_TYPE = "design_review";
    public static final String DESIGN_REVIEW_VERDICT_PATH = ".eneik/design-review-verdict.json";
    public static final String DESIGN_REVIEW_DRAFT_PATH_KEY = "designDraftPath";
    private static final String DESIGNER_ROLE = "BARCAN-TAG-03";

    public void dispatchDesignReview(ProjectEntity project, String draftPath, String brief) {
        RoleEntity compilerRole = roleRepository.findById(ORCHESTRATOR_ROLE).orElse(null);
        if (compilerRole == null) {
            log.error("Cannot dispatch design review for project {}: role {} not found", project.getId(), ORCHESTRATOR_ROLE);
            return;
        }
        String charter = readRawRoleRules(DESIGNER_ROLE);

        TaskEntity reviewTask = new TaskEntity();
        reviewTask.setProject(project);
        reviewTask.setRole(compilerRole);
        reviewTask.setTitle("Design review (" + shortId(project.getId()) + "-" + FILE_TIME_SUFFIX.format(java.time.Instant.now()) + ")");
        reviewTask.setDescription(designReviewPrompt(draftPath, brief, charter));
        reviewTask.setStatus(TaskStatus.queued);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put(WISHLIST_COMPILER_PAYLOAD_KEY, DESIGN_REVIEW_TASK_TYPE);
        payload.put(DESIGN_REVIEW_DRAFT_PATH_KEY, draftPath);
        reviewTask.setPayload(payload);

        reviewTask = taskRepository.save(reviewTask);
        dispatchCompilerTask(reviewTask);
        log.info("Dispatched design review task {} for draft {} in project {}", reviewTask.getId(), draftPath, project.getName());
    }

    public boolean isDesignReviewTask(TaskEntity task) {
        return task.getPayload() != null
                && DESIGN_REVIEW_TASK_TYPE.equals(task.getPayload().path(WISHLIST_COMPILER_PAYLOAD_KEY).asText(null));
    }

    public String designReviewDraftPath(TaskEntity task) {
        if (task.getPayload() == null) {
            return null;
        }
        String raw = task.getPayload().path(DESIGN_REVIEW_DRAFT_PATH_KEY).asText(null);
        return raw == null || raw.isBlank() ? null : raw;
    }

    private static final java.time.format.DateTimeFormatter FILE_TIME_SUFFIX =
            java.time.format.DateTimeFormatter.ofPattern("HHmmssSSS").withZone(java.time.ZoneOffset.UTC);

    private String readRawRoleRules(String roleTag) {
        RoleEntity role = roleRepository.findById(roleTag).orElse(null);
        if (role == null || role.getRulesPath() == null || role.getRulesPath().isBlank()) {
            return "";
        }
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(role.getRulesPath());
            if (java.nio.file.Files.exists(path)) {
                return java.nio.file.Files.readString(path);
            }
        } catch (Exception e) {
            log.warn("Failed to read raw rules for role {}: {}", roleTag, e.getMessage());
        }
        return "";
    }

    private String designReviewPrompt(String draftPath, String brief, String charter) {
        return """
                You are the design reviewer for this project (BARCAN-TAG-03 role - UI/UX Designer). A
                draft mockup was just generated and committed to THIS repository at `%s/mockup.html`
                (and `%s/mockup.png` if present). Read it directly from your checkout - do NOT
                implement, fix, or change any product code, and do not run builds or tests; this task
                only produces a review verdict.

                Apply your role charter below: composition, WCAG contrast where determinable from the
                markup/CSS, Gestalt principles, information density (Miller's Law), and the
                philosophical framing in your charter. This is a single generated screen, not a
                desktop/mobile pair - do not reject it solely for missing a second resolution; judge
                what is actually checkable from this one file.

                Be lenient by design: work must never stall waiting on your opinion. Reject
                ("verdict":"reject") ONLY for a small set of genuinely severe problems: the file is
                empty, corrupted, or unreadable; contrast is badly broken (illegible text); the layout
                is fundamentally incoherent; or it has nothing to do with the brief below. Anything else
                - taste, minor spacing, a debatable color choice - is NOT a blocker: approve it and list
                it as a "concern" instead, so it becomes a follow-up improvement item rather than
                stopped work.

                Deliverable: create a new branch and open a PR that contains ONLY one file,
                `.eneik/design-review-verdict.json`, with EXACTLY this shape and no other files changed:
                {"verdict": "approve", "reason": "", "concerns": ["short concern 1"]}
                or, only for a genuine severe blocker:
                {"verdict": "reject", "reason": "concrete, specific reason tied to the file", "concerns": []}
                Do not write, modify, or delete any other file.

                Design brief this mockup was generated for:
                %s

                Your role charter:
                %s
                """.formatted(draftPath, draftPath, brief, charter);
    }

    @Transactional
    public void dispatchQueuedTasks(UUID projectId) {
        ProjectEntity project = requireActiveProject(projectId);
        List<TaskEntity> queuedTasks = taskRepository.findByProjectIdAndStatusOrderByPriorityDescCreatedAtAsc(project.getId(), TaskStatus.queued);
        boolean buildPhase = readinessService.isBuildPhase(project.getId());

        for (TaskEntity task : queuedTasks) {
            Optional<JulesSessionEntity> existingSession = findActiveJulesSession(task.getId());
            if (existingSession.isPresent() && existingSession.get().getAccountId() != null) {
                JulesSessionEntity session = existingSession.get();
                try {
                    claimService.claimSpecificTask(task.getId(), session.getAccountId());
                    TaskEntity savedTask = taskRepository.findById(task.getId()).orElse(task);
                    savedTask.setJulesSessionName(session.getExternalSessionId());
                    savedTask.setJulesDispatchStatus("already dispatched, skipping duplicate");
                    taskRepository.save(savedTask);
                    log.info("Reconnected queued task {} of project {} to existing Jules session {}",
                            savedTask.getId(), project.getName(), session.getExternalSessionId());
                } catch (Exception e) {
                    log.error("Failed to reconnect queued task {} to existing Jules session {}: {}",
                            task.getId(), session.getId(), e.getMessage(), e);
                }
                continue;
            }

            if (isWishlistCompilerTask(task) || isFalsificationAuditTask(task) || isReviewFallbackTask(task) || isDesignReviewTask(task)) {
                // Compiler, falsification-audit, review-fallback, and design-review tasks are pinned to
                // the reserved compiler account, never the round-robin pool.
                dispatchCompilerTask(task);
                continue;
            }

            if (buildPhase && isSelfGeneratedWork(task)) {
                // BUILD phase: only work traceable to the client's own brief is allowed to dispatch. Design
                // review/role-mismatch-followup/self-falsification-derived work stays queued (not dropped -
                // it simply waits) until the project has actually shipped its first
                // buildPhaseDeliverableCount client deliverables. See test-twenty-eighth post-mortem §6.4:
                // this kind of self-generated backlog made up 82% of dispatched capacity while the two real
                // ТЗ items sat starved of retries.
                continue;
            }

            String roleTag = task.getRole().getTag();

            // Complex/chaotic/retried/defect-work tasks used to bypass Jules for a separate autonomous
            // worker; Jules now has universal role capability across every BARCAN-TAG role, so all tasks
            // flow through the same dispatch path below regardless of cynefin domain or retry count.
            Optional<AccountEntity> accountOpt = accountRepository.lockNextJulesAccountWithCapacity(
                    project.getId(),
                    roleTag,
                    maxConcurrentJulesSessionsPerAccount,
                    taskCompilerAccountName(),
                    maxDailySessionsPerAccount
            );
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
                    if (!dispatch.dispatched()) {
                        claimService.releaseClaimToQueue(savedTask.getId(), dispatch.reason());
                        log.warn("Failed to dispatch queued task {} of project {} to account {}: {}",
                                savedTask.getId(), project.getName(), account.getName(), dispatch.reason());
                        continue;
                    }
                    log.info("Dispatched queued task {} of project {} to account {}", savedTask.getId(), project.getName(), account.getName());
                } catch (Exception e) {
                    log.error("Failed to claim/dispatch queued task {} to account {}: {}", task.getId(), account.getName(), e.getMessage(), e);
                }
            } else {
                task.setJulesDispatchStatus("No free Jules shared session slot available for role context " + roleTag);
                taskRepository.save(task);
            }
        }
    }

    private Optional<JulesSessionEntity> findActiveJulesSession(UUID taskId) {
        return julesSessionRepository.findByTaskId(taskId).stream()
                .filter(session -> session.getExternalSessionId() != null)
                .filter(session -> !"skipped".equals(session.getExternalSessionId()))
                .filter(session -> {
                    String status = session.getStatus();
                    return "queued".equals(status)
                            || "running".equals(status)
                            || "revising".equals(status)
                            || "stuck".equals(status);
                })
                .findFirst();
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
                Optional<AccountEntity> accountOpt = accountRepository.lockNextJulesAccountWithCapacity(
                        project.getId(),
                        roleTag,
                        maxConcurrentJulesSessionsPerAccount,
                        taskCompilerAccountName(),
                        maxDailySessionsPerAccount
                );
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
        List<AgentDashboardDto> agents = accountRepository.findAvailableForProjectOrderByNameAsc(projectId).stream()
                .map(account -> {
                    ClaimEntity activeClaim = claimRepository
                            .findByAccountIdAndTaskProjectIdAndReleasedAtIsNullOrderByClaimedAtDesc(account.getId(), projectId)
                            .stream()
                            .findFirst()
                            .orElse(null);
                    return new AgentDashboardDto(
                            account.getId(),
                            account.getName(),
                            account.getStatus(),
                            activeClaim != null ? activeClaim.getRole().getTag() : null,
                            activeClaim != null ? TaskTitleBuilder.displayTitle(activeClaim.getTask()) : null,
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
        List<com.eneik.production.models.persistence.WishlistEntity> wishlistEntities = wishlistRepository.findByProjectId(projectId);
        List<com.eneik.production.dto.WishlistResponseDto> wishlist = wishlistEntities
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(w -> new com.eneik.production.dto.WishlistResponseDto(w.getId(), w.getProjectId(), w.getSource(), w.getSourceRoleTag(), w.getContent(), w.getStatus(), w.getCreatedAt()))
                .toList();
        List<TaskEntity> taskEntities = taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        List<TaskDto> tasks = taskEntities.stream()
                .map(task -> new TaskDto(
                        task.getId(),
                        task.getRole().getTag(),
                        TaskTitleBuilder.displayTitle(task),
                        task.getDescription(),
                        task.getStatus(),
                        task.getPayload(),
                        task.getJulesSessionName(),
                        task.getJulesDispatchStatus(),
                        task.getDependsOn() != null ? task.getDependsOn().getId() : null,
                        task.isQualityGatePassed(),
                        task.getPriority(),
                        task.getCynefinDomain()
                ))
                .toList();

        return new ProjectDashboardDto(
                toProjectDto(project),
                agents.size(),
                wishlistRepository.findByProjectIdAndStatus(projectId, com.eneik.production.models.persistence.WishlistStatus.pending).size(),
                queue,
                pipeline,
                emsMetricsService.build(taskEntities, wishlistEntities),
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

    private void groupSimilarWishlistItems(java.util.UUID projectId) {
        java.util.List<com.eneik.production.models.persistence.WishlistEntity> pending =
                wishlistRepository.findByProjectIdAndStatus(projectId, com.eneik.production.models.persistence.WishlistStatus.pending);
        if (pending.size() <= 1) {
            return;
        }

        int n = pending.size();
        java.util.Map<Integer, java.util.List<Integer>> adj = new java.util.HashMap<>();
        for (int i = 0; i < n; i++) {
            adj.put(i, new java.util.ArrayList<>());
        }

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (areWishlistItemsSimilar(pending.get(i), pending.get(j))) {
                    adj.get(i).add(j);
                    adj.get(j).add(i);
                }
            }
        }

        boolean[] visited = new boolean[n];
        java.util.List<java.util.List<Integer>> components = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                java.util.List<Integer> component = new java.util.ArrayList<>();
                java.util.Queue<Integer> queue = new java.util.LinkedList<>();
                queue.add(i);
                visited[i] = true;
                while (!queue.isEmpty()) {
                    int curr = queue.poll();
                    component.add(curr);
                    for (int neighbor : adj.get(curr)) {
                        if (!visited[neighbor]) {
                            visited[neighbor] = true;
                            queue.add(neighbor);
                        }
                    }
                }
                components.add(component);
            }
        }

        for (java.util.List<Integer> componentIndices : components) {
            if (componentIndices.size() > 1) {
                com.eneik.production.models.persistence.WishlistEntity survivor = pending.get(componentIndices.get(0));
                java.util.Set<String> uniqueContents = new java.util.LinkedHashSet<>();
                java.util.Set<String> uniqueRoleTags = new java.util.LinkedHashSet<>();
                java.util.Set<String> uniqueDods = new java.util.LinkedHashSet<>();
                java.util.Set<String> uniqueAcceptanceCriteria = new java.util.LinkedHashSet<>();
                java.util.Set<String> uniqueJtbds = new java.util.LinkedHashSet<>();
                
                for (int idx : componentIndices) {
                    com.eneik.production.models.persistence.WishlistEntity item = pending.get(idx);
                    if (item.getContent() != null) {
                        uniqueContents.add(item.getContent().trim());
                    }
                    if (item.getDod() != null) {
                        uniqueDods.add(item.getDod().trim());
                    }
                    if (item.getAcceptanceCriteria() != null) {
                        uniqueAcceptanceCriteria.add(item.getAcceptanceCriteria().trim());
                    }
                    if (item.getJtbd() != null) {
                        uniqueJtbds.add(item.getJtbd().trim());
                    }
                    if (item.getSourceRoleTag() != null && !item.getSourceRoleTag().isBlank()) {
                        for (String tag : item.getSourceRoleTag().split(",")) {
                            uniqueRoleTags.add(tag.trim());
                        }
                    }
                }

                survivor.setContent(String.join("\n---\n", uniqueContents));
                if (!uniqueDods.isEmpty()) {
                    survivor.setDod(String.join("; ", uniqueDods));
                }
                if (!uniqueAcceptanceCriteria.isEmpty()) {
                    survivor.setAcceptanceCriteria(String.join("; ", uniqueAcceptanceCriteria));
                }
                if (!uniqueJtbds.isEmpty()) {
                    survivor.setJtbd(String.join("; ", uniqueJtbds));
                }
                wishlistRepository.save(survivor);
                log.info("ProjectFlowService: Grouped and merged {} similar wishlist items into survivor {}", 
                        componentIndices.size(), survivor.getId());

                for (int k = 1; k < componentIndices.size(); k++) {
                    com.eneik.production.models.persistence.WishlistEntity duplicate = pending.get(componentIndices.get(k));
                    duplicate.setStatus(com.eneik.production.models.persistence.WishlistStatus.dismissed);
                    wishlistRepository.save(duplicate);
                    log.info("ProjectFlowService: Dismissed duplicate wishlist item {} (merged into {})", 
                            duplicate.getId(), survivor.getId());
                }
            }
        }
    }

    private boolean areWishlistItemsSimilar(com.eneik.production.models.persistence.WishlistEntity item1, com.eneik.production.models.persistence.WishlistEntity item2) {
        String text1 = item1.getContent();
        String text2 = item2.getContent();
        if (text1 == null || text2 == null) {
            return false;
        }

        java.util.Set<String> tokens1 = getCleanTokens(text1);
        java.util.Set<String> tokens2 = getCleanTokens(text2);

        if (tokens1.isEmpty() || tokens2.isEmpty()) {
            return false;
        }

        java.util.Set<String> intersection = new java.util.HashSet<>(tokens1);
        intersection.retainAll(tokens2);

        java.util.Set<String> union = new java.util.HashSet<>(tokens1);
        union.addAll(tokens2);

        double similarity = (double) intersection.size() / union.size();
        return similarity >= 0.25;
    }

    private java.util.Set<String> getCleanTokens(String text) {
        if (text == null) {
            return java.util.Collections.emptySet();
        }
        
        String cleaned = text.toLowerCase()
                .replaceAll("[^a-zA-Z0-9а-яА-Я\\s-]", " ")
                .replaceAll("\\s+", " ");
        
        String[] words = cleaned.split(" ");
        java.util.Set<String> tokens = new java.util.HashSet<>();
        
        java.util.Set<String> stopWords = java.util.Set.of(
            "compliance", "violation", "detected", "for", "role", "violates", 
            "methodological", "contradiction", "confirmed", "by", "philosopher", 
            "thesis", "score", "must-be", "performance", "attractive", "given", 
            "when", "then", "requirement", "is", "fulfilled", "and", "the", "with", 
            "from", "into", "a", "an", "of", "in", "on", "at", "to", "or",
            "соответствие", "фальсификация", "нарушение", "противоречие", "подтверждено", "роль"
        );
        
        for (String word : words) {
            word = word.trim();
            if (word.contains("tag-")) {
                continue;
            }
            if (word.endsWith("s") && word.length() > 3) {
                word = word.substring(0, word.length() - 1);
            }
            if (word.length() > 2 && !stopWords.contains(word)) {
                tokens.add(word);
            }
        }
        return tokens;
    }

}
