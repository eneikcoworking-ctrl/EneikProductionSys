package com.eneik.production.services;

import com.eneik.production.dto.*;
import com.eneik.production.dto.dashboard.AgentDashboardDto;
import com.eneik.production.dto.dashboard.FeaturePullRequestSnapshotDto;
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
import com.fasterxml.jackson.databind.node.ArrayNode;
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
    private final FeatureService featureService;
    private final PersistentWorkerSessionService persistentWorkerSessionService;

    @Value("${jules.max-concurrent-sessions-per-account:3}")
    private int maxConcurrentJulesSessionsPerAccount;

    // Every account except the reserved compiler/falsification account (eneikdru) has a real Jules daily
    // session quota; enforcing it locally, proactively, means dispatch selection can budget for it instead
    // of only finding out reactively once Jules itself returns a quota error (AccountStatus.daily_limited).
    @Value("${jules.max-daily-sessions-per-account:15}")
    private int maxDailySessionsPerAccount;

    @Value("${orchestration.max-recovery-items-per-run:3}")
    private int maxRecoveryItemsPerRun;

    // Pull, not push: admission to the paid Jules compiler is gated by live capacity (Kanban WIP limits),
    // not by how many wishlist items happen to exist or how many ticks have passed. Every candidate not on
    // the cheap deterministic path (role_mismatch_followup, or an already-compiled-by-role item) is
    // collected across the whole orchestrate() cycle and admitted into ONE batched compiler dispatch by
    // dispatchBatchedWishlistCompiler, up to whichever of these two limits is tighter. A candidate that
    // doesn't fit stays `pending` and is simply reconsidered next cycle - nothing is lost, admission is
    // just capacity-driven instead of time-driven.
    //
    // Project-wide: how many wishlists this project may have genuinely in flight (status=compiling) at
    // once. More simultaneous compiler sessions is more surface area for the exact same-wishlist race this
    // project already found and fixed once, and burns the reserved compiler account's daily quota faster
    // than the work needs.
    @Value("${orchestration.wip-limit-project-compiling:2}")
    private int wipLimitProjectCompiling;

    // Per-feature: how many wishlists sharing one featureId may be pending+compiling at once. This is the
    // direct fix for "a chain of similar follow-ups piles up for one feature" (confirmed live: repeated
    // design-review concerns on the same mockup each independently queued their own compiler dispatch) -
    // generalizes the narrower, project-scoped MAX_PENDING_DESIGN_CONCERNS_PER_PROJECT check in
    // JulesDispatchService (kept as a cheap outer safety net, this is the real capacity control).
    @Value("${orchestration.wip-limit-feature-in-flight:2}")
    private int wipLimitFeatureInFlight;



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
                              ClientDeliverableReadinessService readinessService,
                              FeatureService featureService,
                              PersistentWorkerSessionService persistentWorkerSessionService) {
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
        this.featureService = featureService;
        this.persistentWorkerSessionService = persistentWorkerSessionService;
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

    // Frozen is the same status activateProject() already uses to sideline every other project when a new
    // one goes active - reusing it here means pausing gets the exact guarantee that matters: this project
    // drops out of ContinuousOrchestrationService.continuousOrchestrate's active-projects loop (wishlist
    // compilation, blocked-work recovery, queued dispatch) on its very next tick. It does NOT stop
    // already-dispatched Jules sessions or in-flight PR review/merge - those are cancelled separately.
    @Transactional
    public ProjectDto pauseProject(UUID projectId) {
        ProjectEntity project = requireProject(projectId);
        if (project.getStatus() == ProjectStatus.frozen) {
            return toProjectDto(project);
        }
        project.setStatus(ProjectStatus.frozen);
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
        if (source == com.eneik.production.models.persistence.WishlistSource.client
                && request.sourceRoleTag() != null && !request.sourceRoleTag().isBlank()) {
            throw new IllegalArgumentException("sourceRoleTag must be null when source is 'client'");
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
        java.util.List<com.eneik.production.models.persistence.WishlistEntity> compilerCandidates = new java.util.ArrayList<>();

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

                if (tryCompileWishlistCheaply(project, wishlist)) {
                    processedCount++;
                    log.info("ProjectFlowService: Synchronously compiled wishlist {} into atomic task slices", wishlist.getId());
                    continue;
                }

                // Not cheap-path eligible - needs the paid Jules compiler. Collected here rather than
                // dispatched immediately: dispatchBatchedWishlistCompiler (below, after this loop) admits
                // candidates together under the WIP-limit gates and compiles several in one batched Jules
                // session instead of one session per wishlist.
                compilerCandidates.add(wishlist);
            } catch (Exception e) {
                log.error("Failed to compile pending wishlist {} for project {}", wishlist.getId(), project.getId(), e);
            }
        }
        processedCount += dispatchBatchedWishlistCompiler(project, compilerCandidates);

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
        followUp.setFeatureId(task.getFeatureId());
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
                // role_mismatch_followup wishlists always take the cheap deterministic path inside
                // tryCompileWishlistCheaply, so they never need to go through the batched compiler dispatch.
                if (tryCompileWishlistCheaply(project, wishlist)) {
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

            // A blocked wishlist-compiler task is not "some role's work blocked" - the generic recovery
            // below writes a vague clarify-the-blocker wishlist that has no idea it should re-decompose the
            // client's actual brief (found live: it produced a nonsense "Delivery Plan" task while the real
            // brief sat orphaned in `compiling` forever, since this loop then considers the blocked task
            // "already covered" and never revisits it). Recover it the way that actually makes sense instead:
            // reopen the wishlist it was compiling so the normal dispatchWishlistCompiler path picks it up
            // fresh next cycle, and retire the stuck compiler task for good so it's never reconsidered here.
            if (isWishlistCompilerTask(task)) {
                // A batched compiler task can cover several wishlists at once - reopen every one that isn't
                // already genuinely finished (another wishlist in the same batch may have completed via a
                // different session in the meantime), so each gets picked up fresh next cycle.
                int reopened = 0;
                for (UUID targetWishlistId : compilerTaskWishlistIds(task)) {
                    WishlistEntity targetWishlist = wishlistRepository.findById(targetWishlistId).orElse(null);
                    if (targetWishlist != null && targetWishlist.getStatus() != WishlistStatus.converted_to_task
                            && targetWishlist.getStatus() != WishlistStatus.dismissed) {
                        targetWishlist.setStatus(WishlistStatus.pending);
                        wishlistRepository.save(targetWishlist);
                        reopened++;
                    }
                }
                log.warn("ProjectFlowService: blocked compiler task {} reopened {} wishlist(s) to pending for a fresh compiler dispatch",
                        task.getId(), reopened);
                task.setStatus(TaskStatus.failed);
                task.setJulesDispatchStatus("Compiler task blocked; wishlist(s) reopened for a fresh compiler dispatch instead of generic recovery");
                taskRepository.save(task);
                continue;
            }

            // Same problem, same reason, for the other system/internal task types (falsification audit, PR
            // review fallback, design review): none of them are "some role's feature work", so the generic
            // clarify-the-blocker template below is meaningless for them too (confirmed live: a blocked
            // design_review task produced the exact same kind of nonsense "Delivery Plan" follow-up). Each of
            // these already has its own retry/re-dispatch path when it naturally reaches pr_opened again
            // (e.g. a fresh mockup+design-review cycle, a fresh falsification pass) - so the correct recovery
            // here is simply to stop the noise, not to fabricate bespoke recovery logic for each.
            if (isFalsificationAuditTask(task) || isReviewFallbackTask(task) || isDesignReviewTask(task) || isCoverageAuditTask(task)) {
                task.setStatus(TaskStatus.failed);
                task.setJulesDispatchStatus("System task blocked; retired instead of generic clarify-wishlist recovery (not role feature work)");
                taskRepository.save(task);
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
            followUp.setFeatureId(task.getFeatureId());
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

    // Returns true if the wishlist was fully handled via the cheap, synchronous, non-Jules path - nothing
    // more to do this cycle. Returns false if it still needs the paid Jules compiler: the caller collects
    // it as a candidate for dispatchBatchedWishlistCompiler instead of dispatching it immediately here, so
    private java.util.List<MLPredictionServiceClient.EpicPlan> parseCompilerPlanContent(String jsonContent) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(jsonContent);
            com.fasterxml.jackson.databind.JsonNode rawEpics = root.path("epics");
            if (!rawEpics.isArray()) {
                return java.util.List.of();
            }
            java.util.List<MLPredictionServiceClient.EpicPlan> result = new java.util.ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode epicNode : rawEpics) {
                com.fasterxml.jackson.databind.JsonNode rawSlices = epicNode.path("slices");
                java.util.List<MLPredictionServiceClient.TaskSliceMetadata> slices = new java.util.ArrayList<>();
                if (rawSlices.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode slice : rawSlices) {
                        String leanValueRaw = slice.path("leanValue").asText("essential");
                        com.eneik.production.models.persistence.LeanValue leanValue;
                        try {
                            leanValue = com.eneik.production.models.persistence.LeanValue.valueOf(leanValueRaw);
                        } catch (Exception e) {
                            leanValue = com.eneik.production.models.persistence.LeanValue.essential;
                        }
                        slices.add(new MLPredictionServiceClient.TaskSliceMetadata(
                                slice.path("title").asText(""),
                                slice.path("jtbd").asText(""),
                                slice.path("acceptanceCriteria").asText(""),
                                slice.path("roleTag").asText(""),
                                leanValue,
                                slice.path("cynefinDomain").asText("clear"),
                                slice.path("tocConstraintRef").asText("TOC-CONSTRAINT-DECOMPOSITION"),
                                slice.path("sixSigmaMetric").asText("Escaped defects <= 5%"),
                                slice.path("hasUi").asBoolean(false)
                        ));
                    }
                }
                result.add(new MLPredictionServiceClient.EpicPlan(
                        epicNode.path("title").asText(""),
                        epicNode.path("jtbd").asText(""),
                        epicNode.path("kanoClass").asText("Must-Be"),
                        epicNode.path("cynefinDomain").asText("clear"),
                        epicNode.path("sixSigmaMetric").asText("Escaped defects <= 5%"),
                        epicNode.path("tocConstraintRef").asText("TOC-CONSTRAINT-DECOMPOSITION"),
                        epicNode.path("sourceIndex").asInt(0),
                        slices
                ));
            }
            return result;
        } catch (Exception e) {
            return java.util.List.of();
        }
    }
    private boolean tryCompileWishlistCheaply(ProjectEntity project, WishlistEntity wishlist) {
        java.util.Optional<String> planContent = gitHubPullRequestService.fetchFileContent(project, "main", ".eneik/task-plan.json");
        if (planContent.isPresent() && !planContent.get().isBlank()) {
            java.util.List<MLPredictionServiceClient.EpicPlan> epicPlans = parseCompilerPlanContent(planContent.get());
            if (!epicPlans.isEmpty()) {
                boolean built = buildTaskGraphFromSlices(project, java.util.List.of(wishlist), epicPlans);
                if (built) {
                    log.info("ProjectFlowService: Successfully compiled wishlist {} from .eneik/task-plan.json on main branch into real tasks", wishlist.getId());
                    return true;
                }
            }
        }

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
            compileSliceMetadata(project, wishlist.getId(), slice, ownerRole, null);
            // The follow-up wishlist should already carry the originating task's featureId (propagated at
            // creation time - see AutoMergeService/JulesDispatchService follow-up creation sites) so
            // recovery work is recognized as a continuation of the same feature, not a brand-new one. Falls
            // back to minting fresh only if that propagation is somehow missing - never fails outright.
            UUID recoveryFeatureId = featureService.resolveOrCreateFeatureId(wishlist, project.getId());
            technicalLeadCompiler.createTaskFromWishlist(
                    wishlist.getId(),
                    null,
                    emsGraphKey(recoveryFeatureId, "recovery"),
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
        // Not dispatched here - the caller (orchestrate()) collects this as a candidate and admits it
        // through dispatchBatchedWishlistCompiler under the WIP-limit gates, batched with any other
        // candidates from this same cycle.
        return false;
    }

    /**
     * Pull-based admission: candidates that fell through the cheap path (see tryCompileWishlistCheaply)
     * are admitted into ONE batched Jules compiler dispatch, up to whichever WIP limit is tighter - project
     * wide (how many wishlists may be genuinely in flight at once) or per-feature (how many follow-ups for
     * one feature may pile up at once). Anything not admitted simply stays `pending` and is reconsidered on
     * the next orchestrate() cycle - capacity-gated, not time-gated. Returns how many were actually admitted
     * (for the caller's processedCount bookkeeping).
     */
    // Package-private (not private) so tests in this package can call it directly - a CGLIB proxy (this
    // class is @Transactional) doesn't intercept private methods, so invoking one reflectively hits an
    // uninitialized proxy field, not the real bean's state.
    int dispatchBatchedWishlistCompiler(ProjectEntity project, java.util.List<WishlistEntity> candidates) {
        if (candidates.isEmpty()) {
            return 0;
        }

        // Operator directive (2026-07-21, test-thirty-second post-mortem): never compile a NEW wishlist
        // item - client-sourced or otherwise (coverage-audit gap, design-review concern, etc.) - while a
        // PREVIOUS client deliverable's derived tasks aren't fully merged yet. Planning more work on top
        // of an unstable, not-yet-landed foundation is exactly what burned Jules session budget tonight
        // for zero durable result: 3 of 4 original tasks are still unmerged, yet the compiler kept taking
        // on fresh wishlist items regardless. Reuses the same "genuinely merged, not just done" definition
        // FalsificationCycleService already gates on, applied one step earlier in the pipeline (at
        // compilation admission, not just at the falsification-cycle trigger).
        ClientDeliverableReadinessService.Readiness readiness = readinessService.computeForProject(project.getId());
        if (readiness.totalDeliverables() > 0 && readiness.mergedDeliverables() < readiness.totalDeliverables()) {
            log.info("ProjectFlowService: {} of {} client deliverable(s) for project {} not yet merged; "
                            + "holding {} new wishlist compile candidate(s) until the existing backlog lands on main",
                    readiness.totalDeliverables() - readiness.mergedDeliverables(), readiness.totalDeliverables(),
                    project.getId(), candidates.size());
            return 0;
        }

        long compilingNow = wishlistRepository.countByProjectIdAndStatus(project.getId(), WishlistStatus.compiling);
        int projectBudget = (int) Math.max(0, wipLimitProjectCompiling - compilingNow);
        if (projectBudget <= 0) {
            log.info("ProjectFlowService: project-wide compiling WIP limit ({}) reached for project {}; {} candidate(s) stay pending for the next cycle",
                    wipLimitProjectCompiling, project.getId(), candidates.size());
            return 0;
        }

        // Snapshot only already-`compiling` wishlists (genuinely in flight from a prior cycle) per featureId
        // - deliberately NOT `pending`, since every candidate in THIS batch is itself currently `pending`
        // and would otherwise count against its own admission (every feature with >=WIP_FEATURE pending
        // items would then permanently block all of them, since the queue itself would always already be
        // "at the limit"). Candidates admitted within this same loop are added to the count as they go, so
        // several candidates sharing one feature still correctly count against each other.
        java.util.Map<UUID, Long> inFlightByFeature = wishlistRepository
                .findByProjectIdAndStatus(project.getId(), WishlistStatus.compiling)
                .stream()
                .filter(w -> w.getFeatureId() != null)
                .collect(java.util.stream.Collectors.groupingBy(WishlistEntity::getFeatureId, java.util.stream.Collectors.counting()));

        java.util.List<WishlistEntity> admitted = new java.util.ArrayList<>();
        for (WishlistEntity candidate : candidates) {
            if (admitted.size() >= projectBudget) {
                break;
            }
            UUID featureId = candidate.getFeatureId();
            if (featureId != null) {
                long inFlight = inFlightByFeature.getOrDefault(featureId, 0L);
                if (inFlight >= wipLimitFeatureInFlight) {
                    log.info("ProjectFlowService: feature {} in-flight WIP limit ({}) reached; wishlist {} stays pending for the next cycle",
                            featureId, wipLimitFeatureInFlight, candidate.getId());
                    continue;
                }
                inFlightByFeature.put(featureId, inFlight + 1);
            }
            admitted.add(candidate);
        }

        if (admitted.isEmpty()) {
            return 0;
        }

        for (WishlistEntity w : admitted) {
            w.setStatus(WishlistStatus.compiling);
            wishlistRepository.save(w);
        }
        if (persistentWorkerSessionService.isEnabled()) {
            dispatchToCompilerPersistentWorker(project, admitted);
        } else {
            dispatchWishlistCompiler(project, admitted);
        }
        return admitted.size();
    }

    // Marks a carrier task (see PersistentWorkerSessionService) at creation time so completion routing
    // (isPersistentWorkerCarrierTask) works even if the worker DB row hasn't been registered yet - e.g. the
    // very first dispatch attempt hit no account capacity and only succeeded on a later retry via the
    // normal queued-task sweep (ProjectFlowService.dispatchQueuedTasks), by which point no row exists.
    // completePersistentWorkerCycle lazily registers the row in that case, using the carrier task's own
    // creation-time batch as "cycle 1".
    private static final String PERSISTENT_WORKER_CARRIER_MARKER_KEY = "persistentWorkerCarrier";

    public boolean isPersistentWorkerCarrierTask(TaskEntity task) {
        return task.getPayload() != null && task.getPayload().path(PERSISTENT_WORKER_CARRIER_MARKER_KEY).asBoolean(false);
    }

    /**
     * Persistent-worker equivalent of dispatchWishlistCompiler: reuses an existing idle worker's Jules
     * session (send a follow-up message, no new task/branch/PR) when one is available, otherwise creates a
     * fresh one exactly like the one-shot path used to unconditionally. See PersistentWorkerSessionService
     * for the busy/rotation bookkeeping this relies on.
     */
    private void dispatchToCompilerPersistentWorker(ProjectEntity project, java.util.List<WishlistEntity> admitted) {
        java.util.List<UUID> batchIds = admitted.stream().map(WishlistEntity::getId).toList();
        Optional<PersistentWorkerSessionEntity> existingOpt =
                persistentWorkerSessionService.findActiveWorker(project.getId(), PersistentWorkerPurpose.WISHLIST_COMPILER);

        if (existingOpt.isPresent()) {
            PersistentWorkerSessionEntity worker = existingOpt.get();
            if (persistentWorkerSessionService.needsRotation(worker)) {
                persistentWorkerSessionService.retire(worker, "cycle/age cap reached");
            } else if (persistentWorkerSessionService.isIdleAndFresh(worker)) {
                JulesSessionEntity session = worker.getCurrentJulesSessionId() != null
                        ? julesSessionRepository.findById(worker.getCurrentJulesSessionId()).orElse(null)
                        : null;
                if (session != null && julesDispatchService.sendFollowUpMessage(session, wishlistCompilerFollowUpPrompt(admitted))) {
                    persistentWorkerSessionService.recordBatchSent(worker, batchIds);
                    log.info("Sent follow-up compiler batch ({} wishlist(s)) to persistent worker {} (cycle {})",
                            admitted.size(), worker.getId(), worker.getCycleCount());
                    return;
                }
                log.warn("Persistent compiler worker {} exists but could not be messaged; reverting {} wishlist(s) to pending for the next cycle",
                        worker.getId(), admitted.size());
                revertWishlistsToPending(admitted);
                return;
            } else {
                // Busy (still processing a prior batch) - never queue a second message on top of an
                // unanswered one. Same "stays pending, retried next cycle" property as a WIP-limit miss.
                log.info("Persistent compiler worker {} is still busy; {} wishlist(s) stay pending for the next cycle",
                        worker.getId(), admitted.size());
                revertWishlistsToPending(admitted);
                return;
            }
        }

        createFreshCompilerPersistentWorker(project, admitted, batchIds);
    }

    private void revertWishlistsToPending(java.util.List<WishlistEntity> wishlists) {
        for (WishlistEntity w : wishlists) {
            w.setStatus(WishlistStatus.pending);
            wishlistRepository.save(w);
        }
    }

    private void createFreshCompilerPersistentWorker(ProjectEntity project, java.util.List<WishlistEntity> admitted,
            java.util.List<UUID> batchIds) {
        RoleEntity compilerRole = roleRepository.findById(ORCHESTRATOR_ROLE).orElse(null);
        if (compilerRole == null) {
            log.error("Cannot create persistent compiler worker for project {}: role {} not found", project.getId(), ORCHESTRATOR_ROLE);
            revertWishlistsToPending(admitted);
            return;
        }

        TaskEntity carrierTask = new TaskEntity();
        carrierTask.setProject(project);
        carrierTask.setRole(compilerRole);
        carrierTask.setTitle("Persistent wishlist compiler worker (" + shortId(project.getId()) + ")");
        carrierTask.setDescription(wishlistCompilerPromptBatch(admitted));
        carrierTask.setStatus(TaskStatus.queued);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put(WISHLIST_COMPILER_PAYLOAD_KEY, WISHLIST_COMPILER_TASK_TYPE);
        payload.put(PERSISTENT_WORKER_CARRIER_MARKER_KEY, true);
        ArrayNode idsArray = payload.putArray(WISHLIST_COMPILER_WISHLIST_IDS_KEY);
        for (WishlistEntity w : admitted) {
            idsArray.add(w.getId().toString());
        }
        carrierTask.setPayload(payload);
        carrierTask = taskRepository.save(carrierTask);

        dispatchCompilerTask(carrierTask);

        TaskEntity refreshed = taskRepository.findById(carrierTask.getId()).orElse(carrierTask);
        if (refreshed.getJulesSessionName() == null) {
            // No account capacity this cycle - task stays `queued`, picked up by the existing retry sweep
            // (dispatchQueuedTasks already knows how to redispatch a queued wishlist_compiler task). No
            // worker row is registered yet; completePersistentWorkerCycle lazily registers one on the first
            // pr_opened, using this task's own payload batch as cycle 1 - see PERSISTENT_WORKER_CARRIER_MARKER_KEY.
            log.warn("Persistent compiler worker carrier task {} could not be dispatched this cycle; will retry via the normal queued-task sweep",
                    carrierTask.getId());
            return;
        }
        List<JulesSessionEntity> sessions = julesSessionRepository.findByTaskId(carrierTask.getId());
        JulesSessionEntity newSession = sessions.stream()
                .max(java.util.Comparator.comparing(JulesSessionEntity::getCreatedAt))
                .orElse(null);
        if (newSession == null) {
            log.error("Persistent compiler worker carrier task {} dispatched but no JulesSessionEntity found", carrierTask.getId());
            return;
        }
        persistentWorkerSessionService.registerFreshWorker(project.getId(), PersistentWorkerPurpose.WISHLIST_COMPILER,
                carrierTask.getId(), newSession.getId(), batchIds);
        log.info("Created persistent compiler worker for project {}: carrier task {}, session {}",
                project.getId(), carrierTask.getId(), newSession.getId());
    }

    /**
     * Follow-up message for an existing persistent compiler worker's session (cycle 2+): reuses the same
     * per-brief formatting body as wishlistCompilerPromptBatch (including the design-concern correction and
     * follow-up-on-existing-functionality annotations), but tells Jules this is a new cycle and to
     * OVERWRITE .eneik/task-plan.json with only this cycle's batch - keeps the file small and keeps
     * JulesDispatchService.parseCompilerPlan completely unchanged (it just reads whatever is in the file).
     */
    private String wishlistCompilerFollowUpPrompt(java.util.List<WishlistEntity> wishlists) {
        String body = wishlistCompilerPromptBatch(wishlists);
        return """
                NEW CYCLE for the same persistent compiler worker session. The brief(s) below are a FRESH
                batch, unrelated to whatever you compiled in a previous cycle on this same branch.
                OVERWRITE `.eneik/task-plan.json` so it contains ONLY the slices for THIS cycle's brief(s) -
                do not keep, merge, or reference any previous cycle's content. Commit the update to the
                same branch/PR you already have open.

                %s
                """.formatted(body);
    }

    /**
     * Turns a resolved slice list into the dependency-graph of child wishlists/tasks that used to be
     * built inline here. Called by the Jules compiler result path
     * (JulesDispatchService.completeWishlistCompilation) once a compiler session's slices are parsed
     * and validated - possibly covering several source wishlists batched into one compiler session.
     * Each source wishlist's slices (grouped by TaskSliceMetadata.sourceIndex, matching the numbering
     * dispatchWishlistCompiler's prompt sent) are built into their own independent task graph - batching
     * is a dispatch-efficiency optimization, not a merge of unrelated briefs, so two different briefs'
     * tasks must never end up depending on each other just because they shared a compiler session.
     */
    public boolean buildTaskGraphFromSlices(ProjectEntity project, java.util.List<WishlistEntity> sourceWishlists,
            java.util.List<MLPredictionServiceClient.EpicPlan> epicPlans) {
        // Ф8 (2026-07-21, operator directive): a wishlist splits into as many эпики as the product needs -
        // grouped by epic.sourceIndex() now (was per-slice sourceIndex before эпики existed), and a single
        // wishlist can legitimately produce MULTIPLE epic entries here, not just one.
        java.util.Map<Integer, java.util.List<MLPredictionServiceClient.EpicPlan>> epicsBySource =
                new java.util.LinkedHashMap<>();
        for (MLPredictionServiceClient.EpicPlan epic : epicPlans) {
            epicsBySource.computeIfAbsent(epic.sourceIndex(), k -> new java.util.ArrayList<>()).add(epic);
        }

        boolean anyBuilt = false;
        for (int i = 0; i < sourceWishlists.size(); i++) {
            WishlistEntity wishlist = sourceWishlists.get(i);
            // Per-source idempotency: a wishlist already finished by another session in the meantime (see
            // JulesDispatchService.completeWishlistCompilation's batch-level check) must not be re-decomposed.
            if (wishlist.getStatus() == WishlistStatus.converted_to_task || wishlist.getStatus() == WishlistStatus.dismissed) {
                continue;
            }
            java.util.List<MLPredictionServiceClient.EpicPlan> myEpics = epicsBySource.getOrDefault(i, java.util.List.of());
            boolean wishlistBuiltAnything = false;
            for (MLPredictionServiceClient.EpicPlan epicPlan : myEpics) {
                if (buildTaskGraphForOneEpic(project, wishlist, epicPlan)) {
                    wishlistBuiltAnything = true;
                }
            }
            if (wishlistBuiltAnything) {
                anyBuilt = true;
            }
            // A wishlist is "converted" once every epic derived from it has been processed, regardless of
            // whether each individual epic produced tasks (an epic with an empty/invalid slice list after
            // EMS filtering is still a processed outcome, not a reason to re-decompose the whole wishlist
            // again next cycle).
            wishlist.setStatus(WishlistStatus.converted_to_task);
            wishlistRepository.save(wishlist);
        }
        return anyBuilt;
    }

    /**
     * Resolves the эпик (FeatureEntity) this epicPlan belongs to: reuses an existing one if the compiler
     * matched it semantically (existingEpicId, validated against this project - a hallucinated or
     * cross-project id falls back to creating new rather than attaching real tasks to the wrong эпик or
     * throwing), otherwise mints a brand-new one with the epic's own content.
     */
    private UUID resolveEpicFeatureId(ProjectEntity project, WishlistEntity wishlist,
            MLPredictionServiceClient.EpicPlan epicPlan) {
        if (epicPlan.existingEpicId() != null) {
            var existing = featureService.findExistingEpic(project.getId(), epicPlan.existingEpicId());
            if (existing.isPresent()) {
                return existing.get().getId();
            }
            log.warn("ProjectFlowService: compiler echoed existingEpicId {} for project {} but it doesn't "
                            + "resolve to a real эпик in this project; creating a new one instead of guessing.",
                    epicPlan.existingEpicId(), project.getId());
        }
        return featureService.createFeature(
                project.getId(),
                wishlist.getId(),
                epicPlan.title(),
                epicPlan.jtbd(),
                epicPlan.kanoClass(),
                epicPlan.cynefinDomain(),
                epicPlan.sixSigmaMetric(),
                epicPlan.tocConstraintRef()
        ).getId();
    }

    private boolean buildTaskGraphForOneEpic(ProjectEntity project, WishlistEntity wishlist,
            MLPredictionServiceClient.EpicPlan epicPlan) {
        java.util.List<MLPredictionServiceClient.TaskSliceMetadata> slices = epicPlan.slices();
        java.util.List<MLPredictionServiceClient.TaskSliceMetadata> graphSlices = emsGraphSlices(wishlist, slices);
        if (graphSlices.isEmpty()) {
            return false;
        }

        warnIfImplicitLayerMissing(wishlist, graphSlices);

        // Every эпик is its own dependency graph - stage anchoring (below) is scoped to THIS эпик's own
        // slices only, never spanning across sibling epics from the same wishlist, since two epics may be
        // entirely unrelated pieces of work that just happened to originate from one client brief.
        UUID featureId = resolveEpicFeatureId(project, wishlist, epicPlan);
        String graphKey = emsGraphKey(featureId, "flow");

        // graphSlices is already sorted by EmsFlowStage.graphOrderForRoleTag (see emsGraphSlices), so
        // grouping consecutive equal-order runs reconstructs the stage order without re-sorting.
        java.util.Map<Integer, java.util.List<MLPredictionServiceClient.TaskSliceMetadata>> byStage =
                new java.util.LinkedHashMap<>();
        for (MLPredictionServiceClient.TaskSliceMetadata slice : graphSlices) {
            int stageOrder = EmsFlowStage.graphOrderForRoleTag(targetRoleForSlice(wishlist, slice));
            byStage.computeIfAbsent(stageOrder, k -> new java.util.ArrayList<>()).add(slice);
        }

        // Every task in a stage depends on the same anchor - the last task created in the previous
        // non-empty stage - so tasks within a stage never depend on each other and can be claimed in
        // parallel (e.g. BARCAN-TAG-02 and BARCAN-TAG-11 both anchored on the same BARCAN-TAG-12
        // contract task). Schema-level limitation: TaskEntity.dependsOn is single-parent, so a stage
        // with multiple tasks only carries forward its LAST task as the next stage's anchor, not all of
        // them - acceptable here since the stages that matter for this graph (model, contract) are
        // almost always 0-1 tasks; a true multi-parent merge would need a schema change.
        TaskEntity stageAnchor = null;
        int index = 1;
        for (java.util.List<MLPredictionServiceClient.TaskSliceMetadata> stageSlices : byStage.values()) {
            TaskEntity lastInStage = null;
            for (MLPredictionServiceClient.TaskSliceMetadata slice : stageSlices) {
                WishlistEntity sliceWishlist = new WishlistEntity();
                sliceWishlist.setProjectId(project.getId());
                sliceWishlist.setSource(wishlist.getSource());
                String ownerRole = targetRoleForSlice(wishlist, slice);
                sliceWishlist.setSourceRoleTag(ownerRole);
                sliceWishlist.setContent(internalSliceContent(wishlist, slice, index));
                sliceWishlist.setStatus(WishlistStatus.pending);
                sliceWishlist.setFeatureId(featureId);
                sliceWishlist = wishlistRepository.save(sliceWishlist);
                compileSliceMetadata(project, sliceWishlist.getId(), slice, ownerRole, epicPlan.kanoClass());
                TaskEntity createdTask = technicalLeadCompiler.createTaskFromWishlist(
                        sliceWishlist.getId(),
                        stageAnchor,
                        graphKey,
                        index,
                        graphSlices.size(),
                        dependencyEdgeReason(stageAnchor, ownerRole)
                );
                lastInStage = createdTask != null ? createdTask : lastInStage;
                index++;
            }
            if (lastInStage != null) {
                stageAnchor = lastInStage;
            }
        }

        dispatchCoverageAuditIfClientBrief(project, wishlist, graphSlices, featureId);
        return true;
    }

    // Observability only, never fabrication: the compiler's own slice choices are trusted as-is, this
    // just surfaces when a plan looks like it skipped a structurally-implied layer so an operator can
    // review it, rather than silently letting a UI-only or drift-prone parallel split through.
    private void warnIfImplicitLayerMissing(WishlistEntity wishlist,
            java.util.List<MLPredictionServiceClient.TaskSliceMetadata> graphSlices) {
        boolean hasUiSlice = graphSlices.stream().anyMatch(MLPredictionServiceClient.TaskSliceMetadata::hasUi);
        boolean hasDataSlice = false;
        boolean hasApiSlice = false;
        boolean hasFrontendSlice = false;
        boolean hasContractSlice = false;
        for (MLPredictionServiceClient.TaskSliceMetadata slice : graphSlices) {
            String role = targetRoleForSlice(wishlist, slice);
            hasDataSlice = hasDataSlice || "BARCAN-TAG-08".equals(role);
            hasApiSlice = hasApiSlice || "BARCAN-TAG-02".equals(role);
            hasFrontendSlice = hasFrontendSlice || "BARCAN-TAG-11".equals(role);
            hasContractSlice = hasContractSlice || "BARCAN-TAG-12".equals(role);
        }
        if (hasUiSlice && !hasApiSlice && !hasDataSlice) {
            log.warn("Wishlist {} decomposed into a UI slice with no backend API or data-model slice - "
                    + "the compiler may have skipped an implicit structural dependency", wishlist.getId());
        }
        if (hasApiSlice && hasFrontendSlice && !hasContractSlice) {
            log.warn("Wishlist {} decomposed into parallel backend (BARCAN-TAG-02) and frontend "
                    + "(BARCAN-TAG-11) slices with no BARCAN-TAG-12 contract slice - the two sides may "
                    + "drift without an agreed contract", wishlist.getId());
        }
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
                        .comparingInt((MLPredictionServiceClient.TaskSliceMetadata slice) -> EmsFlowStage.graphOrderForRoleTag(targetRoleForSlice(wishlist, slice)))
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

    private String dependencyEdgeReason(TaskEntity stageAnchor, String ownerRole) {
        if (stageAnchor == null) {
            return "graph root: first stage of the flow, no predecessor stage";
        }
        String anchorRole = stageAnchor.getRole() != null ? stageAnchor.getRole().getTag() : "previous-stage";
        return "EMS staged flow: " + ownerRole + " waits for the " + anchorRole
                + " stage to provide a base, then runs in parallel with any other role in its own stage";
    }

    private String emsGraphKey(UUID featureId, String suffix) {
        String id = featureId == null ? UUID.randomUUID().toString() : featureId.toString();
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

    private void compileSliceMetadata(ProjectEntity project, UUID wishlistId, MLPredictionServiceClient.TaskSliceMetadata slice, String ownerRole, String epicKanoClass) {
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
                compiledDod(ownerRole, slice, epicKanoClass),
                acceptanceCriteria
        );
    }

    // Ф8 (2026-07-21, operator directive): Kano moved off the task level entirely (customer-value
    // classification only makes sense per эпик, never per task) - epicKanoClass is threaded through purely
    // as informational context ("this task belongs to a Must-Be эпик"), nullable for the cheap/recovery
    // compile path (tryCompileWishlistCheaply), which reuses an already-known featureId without loading
    // that эпик's own content back out.
    private String compiledDod(String ownerRole, MLPredictionServiceClient.TaskSliceMetadata slice, String epicKanoClass) {
        String roleSpecificReadiness = switch (ownerRole) {
            case "BARCAN-TAG-03" -> "UI/design readiness: follow docs/DESIGN_SYSTEM.md for layout, visual states, and interaction evidence. Deliverable is a committed HTML/CSS mockup file - a written brief or description with no mockup file is not acceptable.";
            case "BARCAN-TAG-11" -> "Frontend readiness: implement browser UI according to docs/DESIGN_SYSTEM.md and verify the user-visible interaction.";
            default -> "Role readiness: complete the smallest owner-role result without expanding scope.";
        };
        return "Compiled from English JTBD work item by Eneik Management System. Owner role: "
                + ownerRole + ". Role refusal criteria: " + ownerRole + ". Compiler role: BARCAN-TAG-09. "
                + "Parent эпик Kano: " + defaultText(epicKanoClass, "(unclassified)")
                + ". Cynefin: " + defaultText(slice.cynefinDomain(), "clear") + ". "
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
        if (value != null && value.matches("BARCAN-TAG-(0[0-9]|1[0-2])")) {
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
        if (source.contains("api contract") || source.contains("openapi") || source.contains("swagger")
                || source.contains("endpoint spec") || source.contains("contract-first")
                || source.contains("request/response schema")) {
            return "BARCAN-TAG-12";
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
    // Plural: one compiler task now covers a whole admitted batch (see dispatchBatchedWishlistCompiler),
    // not a single wishlist. Kept as a JSON array of UUID strings rather than one string.
    public static final String WISHLIST_COMPILER_WISHLIST_IDS_KEY = "compilesWishlistIds";

    private void dispatchWishlistCompiler(ProjectEntity project, java.util.List<WishlistEntity> wishlists) {
        // Caller (dispatchBatchedWishlistCompiler) already flipped every wishlist in this batch to
        // `compiling` before calling this method.
        RoleEntity compilerRole = roleRepository.findById(ORCHESTRATOR_ROLE).orElse(null);
        if (compilerRole == null) {
            log.error("Cannot dispatch wishlist compiler for {} wishlist(s): role {} not found",
                    wishlists.size(), ORCHESTRATOR_ROLE);
            return;
        }

        TaskEntity compilerTask = new TaskEntity();
        compilerTask.setProject(project);
        compilerTask.setRole(compilerRole);
        // Suffixed with a short id fragment of the first wishlist in the batch: an identical literal title
        // across multiple compiler dispatches in the same project (a normal, legitimate occurrence) was
        // tripping ContinuousOrchestrationService's duplicate-task-title alarm as a false positive.
        compilerTask.setTitle("Compile " + wishlists.size() + " wishlist(s) into task graph (" + shortId(wishlists.get(0).getId()) + ")");
        compilerTask.setDescription(wishlistCompilerPromptBatch(wishlists));
        compilerTask.setStatus(TaskStatus.queued);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put(WISHLIST_COMPILER_PAYLOAD_KEY, WISHLIST_COMPILER_TASK_TYPE);
        com.fasterxml.jackson.databind.node.ArrayNode idsArray = payload.putArray(WISHLIST_COMPILER_WISHLIST_IDS_KEY);
        for (WishlistEntity w : wishlists) {
            idsArray.add(w.getId().toString());
        }
        compilerTask.setPayload(payload);

        compilerTask = taskRepository.save(compilerTask);
        dispatchCompilerTask(compilerTask);
    }

    public boolean isWishlistCompilerTask(TaskEntity task) {
        return task.getPayload() != null
                && WISHLIST_COMPILER_TASK_TYPE.equals(task.getPayload().path(WISHLIST_COMPILER_PAYLOAD_KEY).asText(null));
    }

    private java.util.List<UUID> compilerTaskWishlistIds(TaskEntity task) {
        if (task.getPayload() == null) {
            return java.util.List.of();
        }
        JsonNode idsNode = task.getPayload().path(WISHLIST_COMPILER_WISHLIST_IDS_KEY);
        if (!idsNode.isArray()) {
            return java.util.List.of();
        }
        java.util.List<UUID> ids = new java.util.ArrayList<>();
        for (JsonNode idNode : idsNode) {
            try {
                ids.add(UUID.fromString(idNode.asText("")));
            } catch (IllegalArgumentException ignored) {
                // skip malformed entry, don't fail the whole batch over one bad id
            }
        }
        return ids;
    }

    private boolean isDesignConcernWishlist(WishlistEntity wishlist) {
        return wishlist.getSource() == WishlistSource.role && "BARCAN-TAG-03".equals(wishlist.getSourceRoleTag());
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

    // The reserved compiler account (dispatchCompilerTask above) is meant for genuinely low-frequency,
    // identity-sensitive work (the wishlist compiler itself, falsification audits). PR-review-fallback and
    // design-review both fire far more often than that assumption holds - PR-review-fallback fires on
    // EVERY implementer PR whenever Gemini is unavailable, which is not a rare event; design-review fires
    // on every new mockup. Stacking that volume onto one account alongside compiler traffic burns its daily
    // Jules session quota fast (confirmed live: the operator watched it happen) for tasks that have no
    // actual need for the reserved identity - any capable general-pool account can review a diff or a
    // mockup. This dispatches through the same general-pool selector implementer tasks already use.
    private void dispatchToGeneralPool(TaskEntity task) {
        Optional<AccountEntity> accountOpt = accountRepository.lockNextJulesAccountWithCapacity(
                task.getProject().getId(),
                task.getRole().getTag(),
                maxConcurrentJulesSessionsPerAccount,
                taskCompilerAccountName(),
                maxDailySessionsPerAccount
        );
        if (accountOpt.isEmpty()) {
            log.warn("No general-pool account has free capacity right now; task {} stays queued for the next cycle", task.getId());
            return;
        }

        AccountEntity account = accountOpt.get();
        try {
            claimService.claimSpecificTask(task.getId(), account.getId());
            TaskEntity savedTask = taskRepository.findById(task.getId()).orElse(task);
            JulesDispatchResult dispatch = julesDispatchService.dispatch(savedTask, account.getId());
            savedTask.setJulesSessionName(dispatch.sessionName());
            savedTask.setJulesDispatchStatus(dispatch.reason());
            taskRepository.save(savedTask);
            if (!dispatch.dispatched()) {
                claimService.releaseClaimToQueue(savedTask.getId(), dispatch.reason());
                log.warn("Failed to dispatch task {} to account {}: {}", savedTask.getId(), account.getName(), dispatch.reason());
                return;
            }
            log.info("Dispatched task {} to general-pool account {}", savedTask.getId(), account.getName());
        } catch (Exception e) {
            log.error("Failed to claim/dispatch task {} to account {}: {}", task.getId(), account.getName(), e.getMessage(), e);
        }
    }

    // Package-private for the same reason as dispatchBatchedWishlistCompiler above.
    /**
     * Ф8 (2026-07-21, operator directive): renders the project's existing эпики (id/title/jtbd only - not
     * their tasks) as the candidate set the compiler must semantically match new content against before
     * ever minting a brand-new эпик. Every compile cycle, not just the first, since a follow-up brief
     * routinely belongs to something the project already has.
     */
    private String existingEpicsPromptContext(UUID projectId) {
        java.util.List<com.eneik.production.models.persistence.FeatureEntity> epics = featureService.listExistingEpics(projectId);
        if (epics.isEmpty()) {
            return "(none yet - this project has no эпики/epics so far; every эпик you produce is new)";
        }
        StringBuilder sb = new StringBuilder();
        for (var epic : epics) {
            sb.append("- existingEpicId=\"").append(epic.getId()).append("\": ")
                    .append(defaultText(epic.getTitle(), "(untitled)")).append(" - ")
                    .append(defaultText(epic.getJtbd(), "(no jtbd recorded)")).append("\n");
        }
        return sb.toString();
    }

    String wishlistCompilerPromptBatch(java.util.List<WishlistEntity> wishlists) {
        StringBuilder briefsSection = new StringBuilder();
        for (int i = 0; i < wishlists.size(); i++) {
            WishlistEntity w = wishlists.get(i);
            briefsSection.append("Brief #").append(i).append(":");
            if (isDesignConcernWishlist(w)) {
                var matcher = DESIGN_CONCERN_APPROVED_PATH_PATTERN.matcher(defaultText(w.getContent(), ""));
                String approvedPath = matcher.find() ? matcher.group(1) : null;
                if (approvedPath != null) {
                    // Adequacy fix (confirmed root cause of a live chaining incident): without this,
                    // Jules has zero signal that this brief is a correction to an already-approved mockup,
                    // not new UI surface - it independently decides whether to generate a fresh mockup,
                    // which gets its own design review, which finds a new concern, forever.
                    briefsSection.append(" [CORRECTION TO ALREADY-APPROVED DESIGN - do not generate a new")
                            .append(" mockup or design review for this brief; implement the fix directly")
                            .append(" against ").append(approvedPath).append("/mockup.html]");
                }
            }
            // Confirmed root cause of a live incident (test-thirty-first, 2026-07-20): the implicit-layer
            // rule below ("if the feature needs data, add BARCAN-TAG-08...") is meant for a fresh client
            // brief starting a feature from nothing - it has no concept of "this project already has a
            // data model, API, and UI for this domain." Applied to a narrow follow-up item (a review
            // concern about a typo, a coverage-audit gap on one already-existing endpoint, chaotic debt,
            // etc.), it happily invents a brand-new schema+contract+backend+frontend set for what should
            // be a one-line patch - observed live producing 3 full duplicate mini-decompositions from
            // nothing more than "fix this typo" and "add pagination". Any non-client-sourced wishlist is
            // by definition a follow-up on functionality that already exists, so tell the compiler
            // explicitly not to apply the implicit-layer rule for it.
            if (w.getSource() != WishlistSource.client) {
                briefsSection.append(" [FOLLOW-UP ON ALREADY-EXISTING FUNCTIONALITY - do NOT create a new")
                        .append(" data schema, API contract, or UI slice for this; the data model, API, and")
                        .append(" UI for this feature already exist elsewhere in the project. Produce exactly")
                        .append(" ONE work item that patches the existing code directly, unless this literally")
                        .append(" cannot be done without a new layer.]");
            }
            briefsSection.append("\n").append(defaultText(w.getContent(), "(empty brief)")).append("\n\n");
        }

        UUID projectId = wishlists.get(0).getProjectId();
        return """
                You are Eneik Technical Lead, Product Owner, and Delivery Manager. Decompose EACH client
                brief below independently. Do NOT implement any product code, do not run builds or tests -
                this task only produces a decomposition plan.

                TWO-LEVEL decomposition - do this in order, for every brief:
                STEP 1 - split into эпики (epics): identify how many DISTINCT epics this brief's narrative
                actually contains (by theme, not by role - "notes CRUD" is one epic even though it needs
                backend+frontend+data roles; "notes CRUD" + "user profile settings" in the same brief is
                TWO epics). A brief may produce 1 epic or several - never assume exactly one.
                STEP 2 - for EACH epic, decide semantically against the EXISTING epics list below: does
                this epic's narrative genuinely match one already in the project, or is it new work? If it
                matches, set "existingEpicId" to that epic's exact id and do NOT invent a new title/jtbd/
                kano/cynefin for it (reuse the match, do not restate it). If nothing matches, set
                "existingEpicId" to null and provide fresh epic-level title/jtbd/kano/cynefin.
                STEP 3 - within each epic, decompose into 1-6 task slices with the existing role/graph
                rules below.

                EXISTING EPICS IN THIS PROJECT (match against these before creating a new one):
                %s

                Output rules:
                - All output must be in English, even when the source brief is written in another
                  language. Translate and normalize intent; never copy the raw brief text verbatim into
                  your output.
                - Every epic MUST include a "sourceIndex" field (integer) naming which Brief #N it
                  addresses. Never mix epics from two different briefs into one, and never split one
                  epic's slices across two different epic blocks.
                - Do not create QA or integration slices unless the brief explicitly asks to verify
                  existing code, fix merge hygiene, or review an already implemented slice.
                - For complex or ambiguous work, create a short BARCAN-TAG-09 or BARCAN-TAG-01
                  spike/decision slice instead of guessing at implementation.
                - Some layers are structurally required even if the brief's narrative never explicitly
                  asks for them: if the epic needs to persist or query structured data, you MUST include
                  a BARCAN-TAG-08 data/schema slice describing that model; if the epic needs to expose
                  functionality to a frontend, mobile client, or external integration, you MUST include a
                  BARCAN-TAG-02 API slice describing that contract. Infer these from what the epic needs
                  to actually work end-to-end, not only from what the client's words explicitly mention.
                - If the epic needs BOTH a BARCAN-TAG-02 backend slice and a BARCAN-TAG-11 frontend slice
                  that will be built in parallel against each other, you MUST also include a
                  BARCAN-TAG-12 slice that defines the shared API contract (endpoints, request/response
                  shape, DTOs) they both build against - sequence it before the parallel implementation
                  slices, not alongside them.
                - Epic-level "jtbd" is customer-facing: "When [the end customer]..., I want..., so
                  that...". Task-level "jtbd" is scoped to the EPIC, not the customer: "When implementing
                  [X] for this epic, I want [Y], so that [the epic's outcome/Z] is achieved" - never repeat
                  the customer-facing sentence verbatim at task level.
                - Each acceptanceCriteria field must contain 2-4 role-specific Given/When/Then lines.
                - Classify Kano at the EPIC level ONLY (Must-Be, Performance, or Attractive) - do not
                  repeat it per task slice.
                - Classify implementation-uncertainty Cynefin at BOTH levels (clear, complicated, complex,
                  or chaotic) - the epic's overall uncertainty and each task's own may differ.
                - sixSigmaMetric and tocConstraintRef exist at BOTH levels: the epic's is an aggregate
                  business metric/bottleneck for the whole epic, each task's is its own narrower technical
                  one - do not just copy the epic's value onto every task.
                - Choose roleTag from: BARCAN-TAG-00 integration/merge hygiene only; BARCAN-TAG-01
                  architecture; BARCAN-TAG-02 backend/API; BARCAN-TAG-03 UI/UX design; BARCAN-TAG-04
                  AI/ML/RAG; BARCAN-TAG-05 build/Docker/CI/deploy; BARCAN-TAG-06 QA/testing existing
                  implementation only; BARCAN-TAG-07 security/auth/access; BARCAN-TAG-08
                  data/schema/storage/parsing; BARCAN-TAG-09 delivery/spike/decision; BARCAN-TAG-10
                  compliance/legal/policy; BARCAN-TAG-11 frontend/browser implementation; BARCAN-TAG-12
                  API contract definition shared by a parallel backend+frontend pair only.
                - Set hasUi=true only when the slice needs visible browser UI/design work.
                - Every task slice MUST end in a concrete committed file, never only a decision, brief, or
                  discussion with nothing to show for it. Pick the artifact by domain: engineering work
                  (backend/frontend/data/AI/build/security) -> real source code; copywriting or content
                  work -> a text file containing the actual finished copy, not a description of what the
                  copy should say; marketing or pricing work -> a file containing the actual prices/offer
                  text, not a plan to define them later; UI/UX design work -> the design itself saved as
                  a committed HTML/CSS mockup file, never just a written brief describing a mockup someone
                  else should make. A BARCAN-TAG-09 delivery/spike/decision slice must still end in a
                  written decision-record file (e.g. a short architecture-decision markdown file) - "we
                  discussed it" is not a deliverable.

                Deliverable: create a new branch and open a PR that contains ONLY one file,
                `.eneik/task-plan.json`, with EXACTLY this shape and no other files changed:
                {"epics": [{"existingEpicId": null, "title": "short English epic title",
                "jtbd": "When [customer]..., I want..., so that...",
                "kanoClass": "Must-Be|Performance|Attractive",
                "cynefinDomain": "clear|complicated|complex|chaotic",
                "sixSigmaMetric": "measurable epic-level business metric",
                "tocConstraintRef": "epic-level bottleneck reference", "sourceIndex": 0,
                "slices": [{"title": "short English title", "roleTag": "BARCAN-TAG-02",
                "jtbd": "When implementing [X] for this epic, I want [Y], so that [epic outcome]...",
                "acceptanceCriteria": "Given..., When..., Then...\\nGiven...",
                "leanValue": "essential|valuable|waste",
                "cynefinDomain": "clear|complicated|complex|chaotic",
                "tocConstraintRef": "short task-level bottleneck reference",
                "sixSigmaMetric": "measurable task-level quality metric", "hasUi": true}]}]}
                Do not write, modify, or delete any other file.

                %d client brief(s) to decompose below (verbatim, may be in any language - read and
                understand each yourself, do not rely on it already being in English). Decompose each one
                separately into its own epic(s); tag every resulting epic with the matching "sourceIndex":
                %s
                """.formatted(existingEpicsPromptContext(projectId), wishlists.size(), briefsSection.toString());
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

    // Coverage audit: right after a CLIENT brief is decomposed into real tasks (buildTaskGraphForOneWishlist
    // below), dispatch one more Jules session that compares the ORIGINAL BRIEF against the RESULTING TASK
    // LIST and flags two kinds of gap: (1) something the brief actually asks for, however awkwardly phrased,
    // that no task addresses (semantic comparison, not keyword matching); (2) functionality that's a
    // well-known standard expectation for this category of product but was never explicitly stated (the
    // concrete trigger: a "Notes CRUD" brief mentioning "authenticated users" produced 4 tasks, none of
    // which built authentication itself - found manually on test-thirtieth, recorded as a deferred fix, now
    // built for real). Deliberately NOT an extension of FalsificationCycleService: falsification only
    // validates code that already exists against what was claimed; this has no code to inspect yet, it's a
    // semantic plan-vs-brief comparison that runs the moment the plan exists, before any implementation
    // starts. Gaps become new wishlist items (source=coverage_gap) that flow through the normal pull-based,
    // WIP-gated compiler cycle like any other wishlist item - no separate admission mechanism needed, and
    // they inherit the same featureId as the tasks they're auditing so they land in the same epic.
    public static final String COVERAGE_AUDIT_TASK_TYPE = "coverage_audit";
    public static final String COVERAGE_AUDIT_REPORT_PATH = ".eneik/coverage-audit.json";
    public static final String COVERAGE_AUDIT_WISHLIST_ID_KEY = "auditsWishlistId";
    public static final String COVERAGE_AUDIT_FEATURE_ID_KEY = "auditsFeatureId";

    public void dispatchCoverageAuditIfClientBrief(ProjectEntity project, WishlistEntity originalWishlist,
            java.util.List<MLPredictionServiceClient.TaskSliceMetadata> graphSlices, UUID featureId) {
        if (originalWishlist.getSource() != WishlistSource.client || graphSlices.isEmpty()) {
            return;
        }
        RoleEntity compilerRole = roleRepository.findById(ORCHESTRATOR_ROLE).orElse(null);
        if (compilerRole == null) {
            log.error("Cannot dispatch coverage audit for wishlist {}: role {} not found", originalWishlist.getId(), ORCHESTRATOR_ROLE);
            return;
        }

        StringBuilder taskList = new StringBuilder();
        int n = 1;
        for (MLPredictionServiceClient.TaskSliceMetadata slice : graphSlices) {
            taskList.append(n).append(". [").append(targetRoleForSlice(originalWishlist, slice)).append("] ")
                    .append(slice.title()).append("\n   JTBD: ").append(slice.jtbd())
                    .append("\n   Acceptance Criteria: ").append(slice.acceptanceCriteria()).append("\n\n");
            n++;
        }

        String prompt = """
                You are auditing a decomposition plan for completeness, BEFORE any implementation starts.
                Do NOT write or change any code, do not run builds or tests - this task only produces an
                audit report.

                Compare the ORIGINAL CLIENT BRIEF below against the RESULTING TASK LIST below (already
                decomposed and about to be built). Find two kinds of gap:

                1. COVERAGE gaps: something the brief actually asks for (read it yourself, in whatever
                   language or phrasing it uses - do not rely on keyword matching) that no task in the
                   list addresses.
                2. DOMAIN-STANDARD gaps: functionality that isn't mentioned in the brief at all, but that
                   any competent engineer would expect as a standard, well-known requirement for this
                   category of product given what the brief DOES ask for (e.g. a brief that implies
                   "accounts" or "authenticated users" needs an actual login/session mechanism even if it
                   never says the word "auth"; a public list endpoint usually needs pagination; etc.) -
                   only flag things clearly and concretely implied by the brief's own domain, never
                   speculative feature ideas.

                Be conservative: only report a gap you are genuinely confident about. Do NOT flag missing
                tests, missing documentation, missing CI/CD, or generic "nice to have" polish - those are
                not coverage gaps.

                Deliverable: create a new branch and open a PR that contains ONLY one file,
                `.eneik/coverage-audit.json`, with EXACTLY this shape and no other files changed:
                {"gaps": [
                  {"title": "short English title", "roleTag": "BARCAN-TAG-02", "jtbd": "When..., I want..., so that...", "acceptanceCriteria": "Given..., When..., Then...", "reason": "short explanation: literal brief requirement OR domain-standard expectation"}
                ]}
                If there are no real gaps, use an empty array: {"gaps": []}. Do not write, modify, or
                delete any other file.

                ORIGINAL CLIENT BRIEF (verbatim, may be in any language):
                %s

                RESULTING TASK LIST (%d task(s) about to be built from this brief):
                %s
                """.formatted(originalWishlist.getContent(), graphSlices.size(), taskList.toString());

        TaskEntity auditTask = new TaskEntity();
        auditTask.setProject(project);
        auditTask.setRole(compilerRole);
        auditTask.setTitle("Coverage audit: plan vs brief (" + shortId(originalWishlist.getId()) + ")");
        auditTask.setDescription(prompt);
        auditTask.setStatus(TaskStatus.queued);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put(WISHLIST_COMPILER_PAYLOAD_KEY, COVERAGE_AUDIT_TASK_TYPE);
        payload.put(COVERAGE_AUDIT_WISHLIST_ID_KEY, originalWishlist.getId().toString());
        if (featureId != null) {
            payload.put(COVERAGE_AUDIT_FEATURE_ID_KEY, featureId.toString());
        }
        auditTask.setPayload(payload);

        auditTask = taskRepository.save(auditTask);
        dispatchToGeneralPool(auditTask);
        log.info("Dispatched coverage audit task {} for client wishlist {} ({} task(s) to verify)",
                auditTask.getId(), originalWishlist.getId(), graphSlices.size());
    }

    public boolean isCoverageAuditTask(TaskEntity task) {
        return task.getPayload() != null
                && COVERAGE_AUDIT_TASK_TYPE.equals(task.getPayload().path(WISHLIST_COMPILER_PAYLOAD_KEY).asText(null));
    }

    public UUID coverageAuditTargetWishlistId(TaskEntity task) {
        if (task.getPayload() == null) {
            return null;
        }
        String raw = task.getPayload().path(COVERAGE_AUDIT_WISHLIST_ID_KEY).asText(null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public UUID coverageAuditFeatureId(TaskEntity task) {
        if (task.getPayload() == null || !task.getPayload().hasNonNull(COVERAGE_AUDIT_FEATURE_ID_KEY)) {
            return null;
        }
        try {
            return UUID.fromString(task.getPayload().path(COVERAGE_AUDIT_FEATURE_ID_KEY).asText());
        } catch (IllegalArgumentException e) {
            return null;
        }
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
    // Plural array, not a singular id: dispatchReviewFallbackBatch covers every PR that needed a Jules
    // fallback reviewer in one orchestrate() tick, in one Jules session - firing one session per PR was
    // the actual cause of the session-count blowup once Gemini's outage became persistent rather than
    // transient (every implementer PR fell back individually, defeating the whole point of the 15-minute
    // review batching, which only ever batched the Gemini call, never this fallback).
    public static final String PR_REVIEW_FALLBACK_TASK_IDS_KEY = "reviewsTaskIds";

    public UUID dispatchReviewFallbackBatch(List<TaskEntity> originalTasks, String prompt) {
        if (originalTasks.isEmpty()) {
            return null;
        }
        RoleEntity compilerRole = roleRepository.findById(ORCHESTRATOR_ROLE).orElse(null);
        if (compilerRole == null) {
            log.error("Cannot dispatch batched PR review fallback for {} task(s): role {} not found",
                    originalTasks.size(), ORCHESTRATOR_ROLE);
            return null;
        }

        TaskEntity reviewTask = new TaskEntity();
        reviewTask.setProject(originalTasks.get(0).getProject());
        reviewTask.setRole(compilerRole);
        // Suffixed with size + the first reviewed task's short id - same duplicate-title false-positive
        // reasoning as the compiler/audit task titles above; observed live triggering
        // ContinuousOrchestrationService's DUPLICATE TASK TITLES alarm after only 3 fallback dispatches.
        reviewTask.setTitle("PR review fallback (Gemini unavailable, " + originalTasks.size() + " PR(s), "
                + shortId(originalTasks.get(0).getId()) + ")");
        reviewTask.setDescription(prompt);
        reviewTask.setStatus(TaskStatus.queued);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put(WISHLIST_COMPILER_PAYLOAD_KEY, PR_REVIEW_FALLBACK_TASK_TYPE);
        ArrayNode idsArray = payload.putArray(PR_REVIEW_FALLBACK_TASK_IDS_KEY);
        for (TaskEntity t : originalTasks) {
            idsArray.add(t.getId().toString());
        }
        reviewTask.setPayload(payload);

        reviewTask = taskRepository.save(reviewTask);
        dispatchToGeneralPool(reviewTask);
        return reviewTask.getId();
    }

    /**
     * Same as dispatchReviewFallbackBatch, but marks the created task as a persistent-worker carrier (see
     * PersistentWorkerSessionService/isPersistentWorkerCarrierTask) so its Jules session gets reused across
     * cycles instead of discarded after this one batch. Called only from
     * JulesDispatchService.createFreshReviewFallbackPersistentWorker.
     */
    public UUID dispatchReviewFallbackBatchAsPersistentCarrier(List<TaskEntity> originalTasks, String prompt) {
        if (originalTasks.isEmpty()) {
            return null;
        }
        RoleEntity compilerRole = roleRepository.findById(ORCHESTRATOR_ROLE).orElse(null);
        if (compilerRole == null) {
            log.error("Cannot create persistent review-fallback worker for {} task(s): role {} not found",
                    originalTasks.size(), ORCHESTRATOR_ROLE);
            return null;
        }

        TaskEntity reviewTask = new TaskEntity();
        reviewTask.setProject(originalTasks.get(0).getProject());
        reviewTask.setRole(compilerRole);
        reviewTask.setTitle("Persistent PR review fallback worker (" + shortId(originalTasks.get(0).getProject().getId()) + ")");
        reviewTask.setDescription(prompt);
        reviewTask.setStatus(TaskStatus.queued);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put(WISHLIST_COMPILER_PAYLOAD_KEY, PR_REVIEW_FALLBACK_TASK_TYPE);
        payload.put(PERSISTENT_WORKER_CARRIER_MARKER_KEY, true);
        ArrayNode idsArray = payload.putArray(PR_REVIEW_FALLBACK_TASK_IDS_KEY);
        for (TaskEntity t : originalTasks) {
            idsArray.add(t.getId().toString());
        }
        reviewTask.setPayload(payload);

        reviewTask = taskRepository.save(reviewTask);
        dispatchToGeneralPool(reviewTask);
        return reviewTask.getId();
    }

    public boolean isReviewFallbackTask(TaskEntity task) {
        return task.getPayload() != null
                && PR_REVIEW_FALLBACK_TASK_TYPE.equals(task.getPayload().path(WISHLIST_COMPILER_PAYLOAD_KEY).asText(null));
    }

    public List<UUID> reviewFallbackTargetTaskIds(TaskEntity task) {
        if (task.getPayload() == null) {
            return List.of();
        }
        JsonNode idsNode = task.getPayload().path(PR_REVIEW_FALLBACK_TASK_IDS_KEY);
        if (!idsNode.isArray()) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>();
        for (JsonNode n : idsNode) {
            try {
                ids.add(UUID.fromString(n.asText()));
            } catch (IllegalArgumentException ignored) {
                // skip malformed entries rather than failing the whole batch
            }
        }
        return ids;
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
        dispatchToGeneralPool(reviewTask);
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

        // Ф-followup (2026-07-21, operator directive - the night's core complaint): review-fallback/
        // design-review/coverage-audit tasks share the SAME general account pool as real implementer work
        // (dispatchToGeneralPool, see below), with no priority separation from it - `priority` defaults to
        // 0 for both and is otherwise driven entirely by TOC-bottleneck matching (BottleneckAwarePriorityService),
        // which has no concept of "real client work vs. system housekeeping" at all. Confirmed by reading
        // every TaskEntity.setPriority(...) call site: system/carrier tasks never get one, so they only
        // ever outrank or tie with real work by coincidence. Rather than hand-tune priority numbers at 7
        // different task-creation sites (fragile, easy to silently regress), reorder THIS list so every
        // non-housekeeping task is tried for account capacity first - housekeeping only gets whatever
        // capacity is left over, every single cycle, structurally, not by chance. Compiler/falsification
        // tasks aren't included here - they're already isolated on their own reserved account, never
        // competing for the general pool at all (see the dispatchCompilerTask branch below).
        queuedTasks = queuedTasks.stream()
                .sorted(java.util.Comparator.comparing(
                        (TaskEntity t) -> isReviewFallbackTask(t) || isDesignReviewTask(t) || isCoverageAuditTask(t)))
                .toList();

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

            // `dependsOn` (set by buildTaskGraphFromSlices per EMS stage order - e.g. Data Schema ->
            // API Contract -> Backend/UI) was, until now, only ever checked by the unused
            // TaskRepository.lockNextQueuedTaskForProject path - this, the actual auto-dispatch loop,
            // never looked at it, so sibling-stage roles routinely started in true parallel before their
            // declared dependency was even done. Confirmed live 2026-07-21 (test-thirty-second): Backend
            // Endpoints (depends on API Contract, which depends on Data Schema) was dispatched and merged
            // while both of its dependencies were still mid-flight - three roles independently invented
            // three incompatible answers (two different tech stacks, two different OpenAPI contracts) for
            // the same feature. Enforcing the existing graph here means each stage's session starts only
            // after the previous stage's real, merged code is on main - it sees the actual decision
            // instead of guessing one of its own.
            //
            // Ф3 (2026-07-21 review): TaskStatus.done is set at review approval, independently of whether
            // the PR itself ever actually merged (see ClientDeliverableReadinessService's class doc) - a
            // dependency stuck in a merge conflict would still read as "done", letting the next stage start
            // before its code is really on main, exactly what this check exists to prevent.
            // Ф4/Д3: isDependencySatisfied also recognizes a merged REPLACEMENT task when the literal
            // dependency was abandoned (escalated/force-unblocked) - otherwise a dependsOn edge pointing at
            // a permanently-failed task would leave this task stuck in `queued` forever with no way out.
            if (task.getDependsOn() != null && !readinessService.isDependencySatisfied(task.getDependsOn())) {
                continue;
            }

            if (isWishlistCompilerTask(task) || isFalsificationAuditTask(task)) {
                // Compiler and falsification-audit tasks are deliberately pinned to the reserved compiler
                // account: both are low-frequency by design (WIP-gated batching, a multi-hour cron) and
                // share that account's capacity comfortably instead of contending with real product work.
                dispatchCompilerTask(task);
                continue;
            }

            if (isReviewFallbackTask(task) || isDesignReviewTask(task) || isCoverageAuditTask(task)) {
                // These fire once per PR / per mockup / per client brief - real per-project traffic, not
                // rare housekeeping - so they use the general round-robin pool like implementer tasks do
                // (see the account-concentration fix earlier this session, commit f005816: routing them
                // through dispatchCompilerTask here would silently re-pin them to the single reserved
                // account on this retry path even though their primary dispatch call already moved off it).
                dispatchToGeneralPool(task);
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
            // Defense in depth: system/carrier tasks (compiler, falsification audit, review fallback,
            // design review, coverage audit) should never reach here at all now that their completion
            // handlers explicitly mark themselves `done` (JulesDispatchService.markSystemTaskDone) instead
            // of relying on ClaimService.complete()'s two-call implementer/reviewer state machine, which
            // used to leave them permanently parked at `review`. This dispatcher has no concept of "this
            // isn't real implementer code" - it would happily redispatch the SAME compiler/design-review
            // prompt forever. Confirmed live on test-thirty-second: one design-review task got fully
            // re-completed 3 times over ~50 minutes before landing on `failed`. Kept as a safety net for
            // any task that predates this fix or slips through some other path.
            if (isWishlistCompilerTask(task) || isFalsificationAuditTask(task) || isReviewFallbackTask(task)
                    || isDesignReviewTask(task) || isCoverageAuditTask(task) || isPersistentWorkerCarrierTask(task)) {
                continue;
            }
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

    private static long countByStatus(List<TaskEntity> tasks, TaskStatus status) {
        return tasks.stream().filter(t -> t.getStatus() == status).count();
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

        // Ф-followup (2026-07-21, operator directive, found via a live screenshot audit): system/carrier
        // tasks (compiler, review-fallback, coverage-audit, design-review, falsification-audit) never
        // produce anything user-facing - they're internal bookkeeping, not a real deliverable. Confirmed
        // live on test-twenty-ninth: 9 failed pr_review_fallback tasks from a pre-fix incident cluttered
        // the Project Tasks widget, looking like duplicated cards. Excluded regardless of status - even a
        // successfully-completed compiler/audit task isn't something the operator asked to see here;
        // "Project Tasks" (and the pipeline/queue counts below, computed from this SAME filtered list
        // instead of separate unfiltered COUNT queries) should answer "what real work is happening", not
        // "what did the system do to itself".
        List<TaskEntity> allTaskEntities = taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        List<TaskEntity> taskEntities = allTaskEntities.stream()
                .filter(task -> !isWishlistCompilerTask(task) && !isFalsificationAuditTask(task)
                        && !isReviewFallbackTask(task) && !isDesignReviewTask(task) && !isCoverageAuditTask(task))
                .toList();

        QueueDashboardDto queue = new QueueDashboardDto(
                taskRepository.queuedGroupedByProjectAndTag(projectId),
                countByStatus(taskEntities, TaskStatus.queued)
        );
        PipelineDashboardDto pipeline = new PipelineDashboardDto(
                countByStatus(taskEntities, TaskStatus.queued),
                countByStatus(taskEntities, TaskStatus.claimed),
                countByStatus(taskEntities, TaskStatus.in_progress),
                countByStatus(taskEntities, TaskStatus.review),
                countByStatus(taskEntities, TaskStatus.done),
                countByStatus(taskEntities, TaskStatus.failed)
        );
        List<com.eneik.production.models.persistence.WishlistEntity> wishlistEntities = wishlistRepository.findByProjectId(projectId);
        List<com.eneik.production.dto.WishlistResponseDto> wishlist = wishlistEntities
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(w -> new com.eneik.production.dto.WishlistResponseDto(w.getId(), w.getProjectId(), w.getSource(), w.getSourceRoleTag(), w.getContent(), w.getStatus(), w.getCreatedAt()))
                .toList();
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
                emsMetricsService.build(allTaskEntities, wishlistEntities),
                agents,
                wishlist,
                tasks
        );
    }

    // Operator directive (2026-07-21): GitHubPullRequestService.pullRequestSnapshot() is shared by
    // FalsificationCycleService and ProjectOperationalContextService too, which need to see every PR
    // including system/carrier ones - so filtering can't live there. This wraps it for the frontend-facing
    // endpoint only: correlates each PR back to the TaskEntity that opened it (via
    // JulesSessionEntity.prUrl), drops anything that isn't a real (non-carrier) task, and attaches a clear
    // featureName the frontend can show instead of Jules's own raw 2-3-word PR title - the git branch/PR
    // title itself stays whatever the backend/Jules needs it to be. Confirmed live: unmatched PRs (no
    // owning task found, e.g. pre-dating this tracking) are dropped rather than shown as "unknown"
    // (operator decision).
    @Transactional(readOnly = true)
    public FeaturePullRequestSnapshotDto featurePullRequests(UUID projectId) {
        ProjectEntity project = requireProject(projectId);
        com.eneik.production.services.github.GitHubPullRequestService.PullRequestSnapshot snapshot =
                gitHubPullRequestService.pullRequestSnapshot(project);
        if (!snapshot.available()) {
            return new FeaturePullRequestSnapshotDto(false, List.of(), List.of(), snapshot.error());
        }

        List<TaskEntity> tasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        Map<UUID, TaskEntity> tasksById = new HashMap<>();
        for (TaskEntity t : tasks) {
            tasksById.put(t.getId(), t);
        }
        List<UUID> taskIds = new ArrayList<>(tasksById.keySet());
        List<JulesSessionEntity> sessions = taskIds.isEmpty() ? List.of() : julesSessionRepository.findByTaskIdIn(taskIds);
        Map<String, TaskEntity> taskByPrUrl = new HashMap<>();
        for (JulesSessionEntity session : sessions) {
            if (session.getPrUrl() != null) {
                TaskEntity task = tasksById.get(session.getTaskId());
                if (task != null) {
                    taskByPrUrl.put(session.getPrUrl(), task);
                }
            }
        }

        Map<UUID, FeatureEntity> featuresById = new HashMap<>();
        for (FeatureEntity feature : featureService.listExistingEpics(projectId)) {
            featuresById.put(feature.getId(), feature);
        }

        List<FeaturePullRequestSnapshotDto.FeaturePullRequestDto> open = new ArrayList<>();
        for (com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest pr : snapshot.open()) {
            FeaturePullRequestSnapshotDto.FeaturePullRequestDto dto = toFeaturePullRequest(pr, taskByPrUrl, featuresById);
            if (dto != null) {
                open.add(dto);
            }
        }
        List<FeaturePullRequestSnapshotDto.FeaturePullRequestDto> closed = new ArrayList<>();
        for (com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest pr : snapshot.closed()) {
            FeaturePullRequestSnapshotDto.FeaturePullRequestDto dto = toFeaturePullRequest(pr, taskByPrUrl, featuresById);
            if (dto != null) {
                closed.add(dto);
            }
        }
        return new FeaturePullRequestSnapshotDto(true, open, closed, null);
    }

    private FeaturePullRequestSnapshotDto.FeaturePullRequestDto toFeaturePullRequest(
            com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest pr,
            Map<String, TaskEntity> taskByPrUrl,
            Map<UUID, FeatureEntity> featuresById) {
        TaskEntity task = taskByPrUrl.get(pr.url());
        if (task == null) {
            return null;
        }
        if (isWishlistCompilerTask(task) || isFalsificationAuditTask(task) || isReviewFallbackTask(task)
                || isDesignReviewTask(task) || isCoverageAuditTask(task)) {
            return null;
        }
        FeatureEntity feature = task.getFeatureId() != null ? featuresById.get(task.getFeatureId()) : null;
        String featureName = (feature != null && feature.getTitle() != null && !feature.getTitle().isBlank())
                ? feature.getTitle()
                : TaskTitleBuilder.displayTitle(task);
        return new FeaturePullRequestSnapshotDto.FeaturePullRequestDto(
                pr.url(), pr.number(), pr.title(), featureName, pr.author(), pr.merged());
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
