package com.eneik.production.services;

import com.eneik.production.dto.*;
import com.eneik.production.dto.dashboard.AgentDashboardDto;
import com.eneik.production.dto.dashboard.FeaturePullRequestSnapshotDto;
import com.eneik.production.dto.dashboard.PipelineDashboardDto;
import com.eneik.production.dto.dashboard.ProductReadinessDto;
import com.eneik.production.dto.dashboard.BlockedItemDto;
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
import com.eneik.production.services.operational.OperationalAction;
import com.eneik.production.services.operational.OperationalPolicyService;
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
    // 2026-08-13 (live incident, test-forty-fourth): same cooldown idea as ORCHESTRATION_COOLDOWN_SECONDS
    // above, but scoped to one wishlist instead of the whole project - see dispatchBatchedWishlistCompiler's
    // admission loop. Nothing previously remembered "we just tried to compile this exact wishlist a moment
    // ago", so a wishlist bounced back to `pending` by any means (manual claim release, a retry, a bug) got
    // a brand-new real Jules session opened for it on the very next cycle - confirmed live: releasing the
    // same 3-wishlist batch repeatedly while orchestration kept running opened several real duplicate
    // "Compile 3 Wishlist" sessions against the daily Jules quota.
    private static final long WISHLIST_COMPILE_DISPATCH_COOLDOWN_SECONDS = 900L;
    // Dashboard "blocked for N hours" visibility (2026-07-25, operator directive) - generous enough that a
    // task legitimately mid-review/mid-dispatch never falsely shows up, same reasoning as this codebase's
    // other safety-net thresholds (e.g. MAX_PHILOSOPHICAL_PROPOSALS_PER_RUN).
    private static final double BLOCKED_ITEM_STALE_THRESHOLD_HOURS = 2.0;

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
    // Versioned market knowledge (market-corpus/) - what products of a kind must contain, independent of
    // what this particular client thought to ask for. Nullable by construction so the several test call
    // sites that build this service by hand keep working, and so a missing corpus degrades the compiler
    // prompt to its pre-corpus form instead of breaking decomposition.
    private final com.eneik.production.services.market.MarketCorpusService marketCorpusService;
    // Checks a finished plan against the corpus - see reportUncoveredStatutoryRequirements. Nullable for
    // the same reason as marketCorpusService: hand-built test instances and a missing corpus both stay working.
    private final com.eneik.production.services.market.MarketComplianceGate marketComplianceGate;
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
    private final SelfFalsificationEpicMatcher selfFalsificationEpicMatcher;
    private final OperationalPolicyService operationalPolicyService;
    private final com.eneik.production.repositories.ProjectFileClaimRepository projectFileClaimRepository;
    private final com.eneik.production.repositories.TaskConflictRepository taskConflictRepository;
    private final com.eneik.production.repositories.NeedsHumanReviewRepository needsHumanReviewRepository;
    private final com.eneik.production.repositories.LinearIssueMetadataRepository linearIssueMetadataRepository;
    private final com.eneik.production.repositories.FeatureRepository featureRepository;
    private final com.eneik.production.repositories.FeatureThreadRepository featureThreadRepository;

    @Value("${jules.max-concurrent-sessions-per-account:3}")
    private int maxConcurrentJulesSessionsPerAccount;

    // Every account except the reserved compiler/falsification account (eneikdru) has a real Jules daily
    // session quota; enforcing it locally, proactively, means dispatch selection can budget for it instead
    // of only finding out reactively once Jules itself returns a quota error (AccountStatus.daily_limited).
    @Value("${jules.max-daily-sessions-per-account:15}")
    private int maxDailySessionsPerAccount;

    @Value("${orchestration.max-recovery-items-per-run:3}")
    private int maxRecoveryItemsPerRun;

    @Value("${auto-recovery.followup.enabled:false}")
    private boolean autoRecoveryFollowupEnabled;

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
    @Value("${orchestration.wip-limit-project-compiling:3}")
    private int wipLimitProjectCompiling;

    // Per-feature: how many wishlists sharing one featureId may be pending+compiling at once. This is the
    // direct fix for "a chain of similar follow-ups piles up for one feature" (confirmed live: repeated
    // design-review concerns on the same mockup each independently queued their own compiler dispatch) -
    // generalizes the narrower, project-scoped MAX_PENDING_DESIGN_CONCERNS_PER_PROJECT check in
    // JulesDispatchService (kept as a cheap outer safety net, this is the real capacity control).
    @Value("${orchestration.wip-limit-feature-in-flight:3}")
    private int wipLimitFeatureInFlight;

    // Confirmed live incident 2026-08-02 (eneikdru account overload, 26 coverage-audit dispatches in ~5h):
    // the merge-watermark check alone (see checkAndDispatchCoverageAudits) has no time floor - a wishlist
    // that keeps toggling in and out of "fully merged" (new coverage_gap-sourced tasks landing under the
    // same featureId, each one re-crossing 100%) re-triggers a fresh audit on every single one of those
    // merges, with no cooldown. This does not replace the watermark - it's an additional floor: even when
    // new code has genuinely merged since the last audit, don't dispatch another one sooner than this.
    @Value("${orchestration.coverage-audit-min-interval-hours:4}")
    private int coverageAuditMinIntervalHours;

    @Value("${falsification.readiness-threshold:0.9}")
    private double falsificationReadinessThreshold;

    private final RequirementGroundingService requirementGroundingService;
    private final GeminiContextService geminiContextService;

    // Self-injected proxy reference (2026-08-07, same pattern/reason as ProcessControlService.self): a
    // plain `this.admitFalsificationAuditTask(...)` self-invocation bypasses the Spring AOP proxy entirely,
    // so @Transactional on that method would silently never activate. Routing the call through `self`
    // instead goes through the real proxy. @Lazy breaks the constructor circular dependency this would
    // otherwise create.
    private final ProjectFlowService self;


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
                              com.eneik.production.services.market.MarketCorpusService marketCorpusService,
                              com.eneik.production.services.market.MarketComplianceGate marketComplianceGate,
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
                              PersistentWorkerSessionService persistentWorkerSessionService,
                              SelfFalsificationEpicMatcher selfFalsificationEpicMatcher,
                              OperationalPolicyService operationalPolicyService,
                              ProjectFileClaimRepository projectFileClaimRepository,
                              RequirementGroundingService requirementGroundingService,
                              GeminiContextService geminiContextService,
                              com.eneik.production.repositories.TaskConflictRepository taskConflictRepository,
                              com.eneik.production.repositories.NeedsHumanReviewRepository needsHumanReviewRepository,
                              com.eneik.production.repositories.LinearIssueMetadataRepository linearIssueMetadataRepository,
                              com.eneik.production.repositories.FeatureRepository featureRepository,
                              com.eneik.production.repositories.FeatureThreadRepository featureThreadRepository,
                              @org.springframework.context.annotation.Lazy ProjectFlowService self) {
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
        this.marketCorpusService = marketCorpusService;
        this.marketComplianceGate = marketComplianceGate;
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
        this.selfFalsificationEpicMatcher = selfFalsificationEpicMatcher;
        this.operationalPolicyService = operationalPolicyService;
        this.projectFileClaimRepository = projectFileClaimRepository;
        this.requirementGroundingService = requirementGroundingService;
        this.geminiContextService = geminiContextService;
        this.taskConflictRepository = taskConflictRepository;
        this.needsHumanReviewRepository = needsHumanReviewRepository;
        this.linearIssueMetadataRepository = linearIssueMetadataRepository;
        this.featureRepository = featureRepository;
        this.featureThreadRepository = featureThreadRepository;
        this.self = self;
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
                    .ifPresent(p -> freezeProjectAndCancelWork(p,
                            "Project frozen: superseded by a new greenfield project"));
            // This system works one project at a time - freezing the old active project above stops the
            // orchestrator from touching it, but it does NOT release the claims its accounts were holding
            // (ContinuousOrchestrationService only iterates ProjectStatus.active, so a frozen/accepted
            // project's claims just sit there forever with releasedAt=null). That leaves accounts showing
            // as busy/leased against work belonging to a project nobody is doing anymore. Confirmed live
            // 2026-07-23: 5 unreleased claims from 4 different old projects (some frozen, some merely
            // "accepted" and never explicitly frozen either) were still occupying accounts when a brand
            // new greenfield project was created. Since a brand-new project can't have any claims of its
            // own yet, releasing every currently-unreleased claim here is safe - it just requeues those old
            // tasks (harmless, since their frozen/accepted projects are never processed again) and frees
            // the accounts for the new project.
            for (ClaimEntity staleClaim : claimRepository.findByReleasedAtIsNull()) {
                claimService.releaseClaimToQueue(staleClaim.getTask().getId(),
                        "Released: new greenfield project created, account freed from a stale prior-project claim");
            }
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
                        freezeProjectAndCancelWork(p, "Project frozen: superseded by another project being activated");
                    }
                });

        project.setStatus(ProjectStatus.active);
        return toProjectDto(projectRepository.save(project));
    }

    // Frozen means zero background activity in any form, not just "orchestrator stops picking new work"
    // (operator directive 2026-07-24: "софт предполагает ничего ни в каком виде не делать с замороженными
    // проектами - все задачи снимать"). Freezing alone only drops a project out of
    // ContinuousOrchestrationService.continuousOrchestrate's active-projects loop - several other scheduled
    // loops (pollActiveJulesSessions, JulesDispatchService.runSessionSafetyMaintenance,
    // reconcileStrandedPrOpenedWorkflows, AutoMergeService's merge-execution half) are project-status-BLIND,
    // keyed purely on Jules session/task/PR-review state, so a frozen project's in-flight sessions and open
    // PRs kept being polled/merged forever regardless of status. cancelAllActiveWorkForProject cancels every
    // non-terminal task's active session (which naturally drops it out of every session-status-filtered
    // loop above) and closes any still-open GitHub PRs, so those status-blind loops have nothing left to
    // find for this project.
    private void freezeProjectAndCancelWork(ProjectEntity project, String reason) {
        project.setStatus(ProjectStatus.frozen);
        projectRepository.save(project);
        cancelAllActiveWorkForProject(project, reason);
    }

    // Frozen is the same status activateProject() already uses to sideline every other project when a new
    // one goes active - reusing it here means pausing gets the exact guarantee that matters: this project
    // drops out of ContinuousOrchestrationService.continuousOrchestrate's active-projects loop (wishlist
    // compilation, blocked-work recovery, queued dispatch) on its very next tick, AND every already-
    // dispatched Jules session and in-flight PR is actively cancelled/closed (see freezeProjectAndCancelWork)
    // rather than left to finish on their own.
    @Transactional
    public ProjectDto pauseProject(UUID projectId) {
        ProjectEntity project = requireProject(projectId);
        if (project.getStatus() == ProjectStatus.frozen) {
            return toProjectDto(project);
        }
        freezeProjectAndCancelWork(project, "Project paused by operator");
        return toProjectDto(project);
    }

    // 2026-08-07 (operator directive: "надо будет удалить там результаты первой декомпозиции и полностью
    // с самого начала провести новую"): deletes every task/wishlist/feature produced by a project's first
    // decomposition attempt and re-submits the same brief as a fresh client wishlist, so the SAME
    // project/repo/GitHub collaborators (already provisioned, no need to redo that) get a clean second
    // decomposition run through the now-fixed grounding pipeline. Requires the project to already be
    // frozen (see pauseProject) - refuses to reset a project that might still have live orchestration
    // touching it, since this is a real, non-reversible delete, not a soft archive.
    //
    // Deletion order below follows the actual FK constraints in the schema (checked against the migration
    // files, not assumed): tasks.depends_on is a self-referencing FK with no cascade, so it must be nulled
    // out before any task in a dependency chain can be deleted; jules_sessions/claims/task_conflicts/
    // needs_human_review/linear_issue_metadata all reference tasks.id with no cascade, so each must be
    // deleted before the task itself; jules_activity_responses references jules_sessions.id with no
    // cascade and must go first; pr_reviews cascades automatically from jules_sessions (ON DELETE CASCADE,
    // V37) so it needs no manual delete. wishlist.source_wishlist_id and *.feature_id are all ON DELETE SET
    // NULL, so wishlist and features can be deleted without first touching tasks for those columns
    // specifically. feature_threads.feature_id is the one exception - V45 tightened it to NOT NULL after
    // V44 originally declared it ON DELETE SET NULL, so that cascade action can never actually fire; those
    // rows must be deleted before their feature. The whole thing runs in one transaction - if the order is
    // wrong anywhere, the real DB constraint throws and nothing is left half-deleted, rather than silently
    // corrupting state.
    @Transactional
    public ProjectDto resetProjectForRedecomposition(UUID projectId, String freshWishlistContent) {
        if (freshWishlistContent == null || freshWishlistContent.isBlank()) {
            throw new IllegalArgumentException("freshWishlistContent is required to redecompose a project");
        }
        ProjectEntity project = requireProject(projectId);
        if (project.getStatus() != ProjectStatus.frozen) {
            throw new IllegalStateException("Project must be paused/frozen before it can be reset for redecomposition (was: " + project.getStatus() + ")");
        }

        List<TaskEntity> tasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        List<UUID> taskIds = tasks.stream().map(TaskEntity::getId).toList();

        if (!taskIds.isEmpty()) {
            boolean anyDependsOn = false;
            for (TaskEntity t : tasks) {
                if (t.getDependsOn() != null) {
                    t.setDependsOn(null);
                    anyDependsOn = true;
                }
            }
            if (anyDependsOn) {
                taskRepository.saveAll(tasks);
            }

            claimRepository.deleteAll(claimRepository.findByTaskIdIn(taskIds));
            taskConflictRepository.deleteAll(taskConflictRepository.findByTaskIdIn(taskIds));
            needsHumanReviewRepository.deleteAll(needsHumanReviewRepository.findByTaskIdIn(taskIds));
            linearIssueMetadataRepository.deleteAllById(taskIds);

            List<JulesSessionEntity> sessions = julesSessionRepository.findByTaskIdIn(taskIds);
            List<UUID> sessionIds = sessions.stream().map(JulesSessionEntity::getId).toList();
            if (!sessionIds.isEmpty()) {
                julesActivityResponseRepository.deleteAll(julesActivityResponseRepository.findByJulesSessionIdIn(sessionIds));
            }
            julesSessionRepository.deleteAll(sessions);

            taskRepository.deleteAll(tasks);
        }

        List<WishlistEntity> wishlists = wishlistRepository.findByProjectId(projectId);
        // Drop the RAG index built for any root client brief this project already had (see
        // wishlistCompilerPromptBatch) - indexDocument(..., null) is the same delete-by-sourceRef path
        // reindexStandingKnowledge uses, no separate repository access needed here.
        for (WishlistEntity w : wishlists) {
            if (w.getSource() == WishlistSource.client && w.getOriginWishlistId() == null) {
                geminiContextService.indexDocument("client_brief_requirement", "client_brief:" + w.getId(), null);
            }
        }
        wishlistRepository.deleteAll(wishlists);
        // feature_threads.feature_id is NOT NULL (V45 tightened the original ON DELETE SET NULL column to
        // NOT NULL) - a real live constraint violation confirmed on the first run of this method
        // (H2: "NULL not allowed for column FEATURE_ID" while deleting a feature that still had a thread
        // row), so these must be deleted before the features they reference.
        featureThreadRepository.deleteAll(featureThreadRepository.findByProjectId(projectId));
        featureRepository.deleteAll(featureRepository.findByProjectId(projectId));

        project.setStatus(ProjectStatus.active);
        ProjectEntity saved = projectRepository.save(project);

        WishlistEntity freshWishlist = new WishlistEntity();
        freshWishlist.setProjectId(saved.getId());
        freshWishlist.setContent(freshWishlistContent.trim());
        freshWishlist.setSource(WishlistSource.client);
        freshWishlist.setStatus(WishlistStatus.pending);
        wishlistRepository.save(freshWishlist);

        log.warn("Project {} reset for redecomposition: deleted {} task(s), {} wishlist(s), started fresh with a new client brief ({} chars)",
                projectId, taskIds.size(), wishlists.size(), freshWishlistContent.trim().length());

        return toProjectDto(saved);
    }

    private void cancelAllActiveWorkForProject(ProjectEntity project, String reason) {
        List<TaskEntity> tasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId());
        for (TaskEntity task : tasks) {
            TaskStatus status = task.getStatus();
            if (status == TaskStatus.done || status == TaskStatus.failed || status == TaskStatus.spike_completed) {
                continue;
            }
            JulesSessionEntity activeSession = julesSessionRepository.findByTaskId(task.getId()).stream()
                    .filter(session -> {
                        String sessionStatus = session.getStatus();
                        return session.getExternalSessionId() != null
                                && !"skipped".equals(session.getExternalSessionId())
                                && ("queued".equals(sessionStatus)
                                || "running".equals(sessionStatus)
                                || "revising".equals(sessionStatus)
                                || "pr_opened".equals(sessionStatus)
                                || "stuck".equals(sessionStatus));
                    })
                    .findFirst()
                    .orElse(null);
            if (activeSession != null) {
                julesDispatchService.cancelSession(activeSession.getId(), reason);
            } else {
                claimService.closeTaskAsFailed(task.getId(), reason);
            }
        }
        try {
            gitHubPullRequestService.closeOpenPullRequests(project, reason);
        } catch (Exception e) {
            log.warn("ProjectFlowService: failed to close open GitHub PRs while freezing project {}: {}",
                    project.getId(), e.getMessage());
        }
    }

    @Transactional
    public com.eneik.production.dto.WishlistResponseDto addWishlistItem(UUID projectId, com.eneik.production.dto.WishlistRequestDto request) {
        ProjectEntity project = requireActiveProject(projectId);
        operationalPolicyService.requireAllowed(projectId, OperationalAction.ADD_WISHLIST);
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

        return new com.eneik.production.dto.WishlistResponseDto(item.getId(), item.getProjectId(), item.getSource(), item.getSourceRoleTag(), item.getContent(), item.getStatus(), item.getCreatedAt(), item.getFeatureId());
    }

    // 2026-08-14 (bug-hunt sweep, second attempt - first attempt reverted): used to be @Transactional,
    // holding a DB transaction/connection open across a real GitHub HTTP call per pending wishlist in the
    // loop below (tryCompileWishlistCheaply's raw HttpClient GET) plus a real Jules dispatch at the end of
    // dispatchBatchedWishlistCompiler. A first attempt at simply removing this annotation (mirroring 5
    // other methods fixed the same night) broke live dispatch: dispatchBatchedWishlistCompiler's wishlist
    // admission depends on WishlistRepository.compareAndSetStatus, a custom @Modifying JPQL query with no
    // @Transactional of its own - unlike plain save()/delete() (auto-wrapped by Spring Data), it needs an
    // ALREADY-ACTIVE writable transaction from its caller, and without this method's wrapper there wasn't
    // one (TransactionRequiredException, 3 ProjectFlowIntegrationTest failures caught by the full suite).
    // Fixed properly this time: that CAS step is now its own short REQUIRES_NEW transaction
    // (claimWishlistsForCompilation, via self) - see that method's javadoc - so it always has the real
    // transaction it needs regardless of this method's own state, while the GitHub/Jules network calls
    // below run with no transaction held open.
    public OrchestrationResultDto orchestrate(UUID projectId) {
        ProjectEntity project = requireActiveProject(projectId);
        operationalPolicyService.requireAllowed(projectId, OperationalAction.ORCHESTRATE);
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
                wishlistRepository.findByProjectId(project.getId()).stream()
                        .filter(w -> w.getStatus() == com.eneik.production.models.persistence.WishlistStatus.pending
                                || w.getStatus() == com.eneik.production.models.persistence.WishlistStatus.compiling)
                        .collect(java.util.stream.Collectors.toList());

        if (!pendingItems.isEmpty() && ensureEnvironmentBootstrapTask(project).isPresent()) {
            processedCount++;
        }

        for (com.eneik.production.models.persistence.WishlistEntity wishlist : pendingItems) {
            try {
                // Reload from repository to ensure we have the latest compiled data
                wishlist = wishlistRepository.findById(wishlist.getId()).orElse(wishlist);
                if (wishlist.getStatus() != com.eneik.production.models.persistence.WishlistStatus.pending
                        && wishlist.getStatus() != com.eneik.production.models.persistence.WishlistStatus.compiling) {
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
        operationalPolicyService.requireAllowed(projectId, OperationalAction.ORCHESTRATE);
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
        if (!committed) {
            log.warn("Environment bootstrap commit failed for project {}; leaving task queued for normal Jules dispatch as fallback", project.getId());
        }
        // The scaffold commits run BEFORE the task is marked done, and their results decide whether it is
        // marked at all (2026-08-15). Previously `done` was set on the strength of bootstrap.md alone and
        // these two calls ran afterwards fire-and-forget, their booleans reaching nothing but a log line.
        // Confirmed live on test-forty-sixth: bootstrap.md, pom.xml and application.properties landed while
        // the scaffold's own .gitignore did not, the task reported done, 21 dependent tasks were dispatched
        // against a boundary that was never fully established, and the missing target/ ignore rule made
        // every pair of compiling tasks conflict. A task that says done must mean its work was delivered.
        boolean backendScaffolded = commitDeterministicJavaScaffoldIfAbsent(project);
        boolean frontendScaffolded = commitDeterministicFrontendScaffoldIfAbsent(project);

        if (committed && backendScaffolded && frontendScaffolded) {
            task.setStatus(TaskStatus.done);
            taskRepository.save(task);
            log.info("Environment bootstrap for project {} completed deterministically by the backend (no Jules session needed): docs/architecture/bootstrap.md", project.getId());
        } else {
            log.warn("Environment bootstrap for project {} incomplete (doc={}, backend scaffold={}, frontend scaffold={}); "
                            + "leaving task queued so the normal Jules dispatch path can finish it instead of "
                            + "reporting a boundary that was never established",
                    project.getId(), committed, backendScaffolded, frontendScaffolded);
        }
    }

    // Found live (test-thirty-fifth, 2026-07-23): two separate epics each independently dispatched a
    // "Data Schema" implementer task with no shared dependency between them (dependsOn=null on both, by
    // design - the graph only sequences work WITHIN an epic). Both Jules sessions started from the same
    // near-empty main and each invented its own pom.xml/.gitignore/application.properties from scratch,
    // so the second one to try merging hit a real, unrecoverable GitHub merge conflict on those exact root
    // files. Committing a minimal, valid Maven skeleton once, deterministically, right after bootstrap
    // (before any epic's implementer task is ever dispatched) removes the race entirely: every later
    // session sees a real pom.xml already on main and extends it instead of re-inventing it, exactly like
    // bootstrapDocContent already tells it to. Only for greenfield projects, and only when no backend/
    // frontend manifest of ANY kind already exists - TechnicalLeadCompiler's own path-prediction fallback
    // already treats "no manifest present" as "assume Java Spring Boot" (see its isNextJsOrReact check), so
    // this reuses that same convention rather than inventing a new one. Never touches a brownfield repo or
    // a project that already committed to a different stack (Next.js, Python, etc.) - absence of ALL three
    // common manifests is required before writing anything.
    private boolean commitDeterministicJavaScaffoldIfAbsent(ProjectEntity project) {
        if (!"greenfield".equals(project.getOnboardingMode())) {
            // Brownfield repos are never scaffolded, so nothing was owed and nothing failed.
            return true;
        }
        boolean pomExists = gitHubPullRequestService.fetchFileContent(project, "main", "pom.xml").isPresent();
        boolean packageJsonExists = gitHubPullRequestService.fetchFileContent(project, "main", "package.json").isPresent();
        boolean requirementsExists = gitHubPullRequestService.fetchFileContent(project, "main", "requirements.txt").isPresent();
        if (pomExists || packageJsonExists || requirementsExists) {
            log.info("Skipping deterministic backend scaffold for project {}: a manifest already exists (pom.xml={}, package.json={}, requirements.txt={})",
                    project.getId(), pomExists, packageJsonExists, requirementsExists);
            // Nothing was owed, so nothing failed: a project that already carries a manifest is a
            // brownfield/other-stack repo this scaffold deliberately never touches.
            return true;
        }

        String artifactId = javaArtifactId(project);
        boolean pomCommitted = gitHubPullRequestService.upsertFile(
                project,
                "pom.xml",
                javaScaffoldPomXml(artifactId).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "EMS bootstrap: minimal Java/Spring Boot Maven skeleton (deterministic, avoids cross-epic scaffold collisions)"
        );
        boolean gitignoreCommitted = gitHubPullRequestService.upsertFile(
                project,
                ".gitignore",
                javaScaffoldGitignore().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "EMS bootstrap: standard Java/Maven .gitignore"
        );
        boolean applicationPropertiesCommitted = gitHubPullRequestService.upsertFile(
                project,
                "src/main/resources/application.properties",
                javaScaffoldApplicationProperties().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "EMS bootstrap: minimal application.properties (file-based H2, safe defaults)"
        );
        log.info("Deterministic backend scaffold for project {}: pom.xml={}, .gitignore={}, application.properties={}",
                project.getId(), pomCommitted, gitignoreCommitted, applicationPropertiesCommitted);

        recordGlobalFileClaimIfCommitted(project, "pom.xml", pomCommitted);
        recordGlobalFileClaimIfCommitted(project, ".gitignore", gitignoreCommitted);
        recordGlobalFileClaimIfCommitted(project, "src/main/resources/application.properties", applicationPropertiesCommitted);
        return pomCommitted && gitignoreCommitted && applicationPropertiesCommitted;
    }

    // Feeds TechnicalLeadCompiler.applyCrossEpicCollisionGuard: a project-wide claim (taskId=null,
    // featureId=null) always collides with every эпик's predicted fileScope, regardless of which эпик asks -
    // this is how the deterministic bootstrap scaffolds (backend and frontend) plug into the same general
    // collision-guard mechanism used for every other cross-эпик file collision, instead of needing their own
    // separate enforcement path.
    private void recordGlobalFileClaimIfCommitted(ProjectEntity project, String path, boolean committed) {
        if (!committed) {
            return;
        }
        com.eneik.production.models.persistence.ProjectFileClaimEntity claim =
                new com.eneik.production.models.persistence.ProjectFileClaimEntity();
        claim.setProjectId(project.getId());
        claim.setFilePath(path);
        claim.setTaskId(null);
        claim.setFeatureId(null);
        claim.setClaimedAt(Instant.now());
        projectFileClaimRepository.save(claim);
    }

    // Frontend sibling of commitDeterministicJavaScaffoldIfAbsent above - same root cause, same fix shape.
    // Found live (test-fortieth, 2026-07-31): three separate эпики each independently dispatched a
    // BARCAN-TAG-11 frontend task with no shared dependency between them (dependency graphs never span
    // sibling эпики - see buildTaskGraphForOneEpic), and each one rewrote frontend/src/App.svelte from
    // scratch as if no shell existed yet, producing real GitHub merge conflicts (PR#13/PR#21 both touching
    // the same file). Committing a minimal, working Svelte+Vite shell once, deterministically, right after
    // the backend scaffold (before any эпик's implementer task is ever dispatched) removes the race the
    // same way the backend fix already does: every later TAG-11 session sees a real shell already on main
    // and extends it instead of re-inventing it. The shell also commits frontend/src/routes.js - a small,
    // explicit routes registry - specifically so that adding a new эпик's UI later becomes "add one
    // component file and append one line to routes.js" instead of "edit App.svelte's shared layout",
    // shrinking the collision surface even after this one-time bootstrap. Only for greenfield projects, and
    // only when no frontend/backend manifest of ANY kind already exists yet, mirroring the same convention
    // the Java scaffold above already uses.
    // Package-private (not private) so it's directly unit-testable without wiring the whole bootstrap
    // task-creation pipeline, same convention already used for wishlistCompilerPromptBatch below.
    boolean commitDeterministicFrontendScaffoldIfAbsent(ProjectEntity project) {
        if (!"greenfield".equals(project.getOnboardingMode())) {
            // Brownfield repos are never scaffolded, so nothing was owed and nothing failed.
            return true;
        }
        boolean pomExists = gitHubPullRequestService.fetchFileContent(project, "main", "pom.xml").isPresent();
        boolean packageJsonExists = gitHubPullRequestService.fetchFileContent(project, "main", "package.json").isPresent();
        boolean frontendPackageJsonExists = gitHubPullRequestService.fetchFileContent(project, "main", "frontend/package.json").isPresent();
        boolean appSvelteExists = gitHubPullRequestService.fetchFileContent(project, "main", "frontend/src/App.svelte").isPresent();
        boolean requirementsExists = gitHubPullRequestService.fetchFileContent(project, "main", "requirements.txt").isPresent();
        if (pomExists || packageJsonExists || frontendPackageJsonExists || appSvelteExists || requirementsExists) {
            log.info("Skipping deterministic frontend scaffold for project {}: a manifest already exists "
                            + "(pom.xml={}, package.json={}, frontend/package.json={}, frontend/src/App.svelte={}, requirements.txt={})",
                    project.getId(), pomExists, packageJsonExists, frontendPackageJsonExists, appSvelteExists, requirementsExists);
            return true;
        }

        String appTitle = defaultText(project.getName(), "Generated App");
        boolean packageJsonCommitted = gitHubPullRequestService.upsertFile(
                project,
                "frontend/package.json",
                frontendScaffoldPackageJson(javaArtifactId(project)).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "EMS bootstrap: minimal Svelte/Vite package.json (deterministic, avoids cross-эпик scaffold collisions)"
        );
        boolean viteConfigCommitted = gitHubPullRequestService.upsertFile(
                project,
                "frontend/vite.config.js",
                frontendScaffoldViteConfig().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "EMS bootstrap: minimal Vite config"
        );
        boolean indexHtmlCommitted = gitHubPullRequestService.upsertFile(
                project,
                "frontend/index.html",
                frontendScaffoldIndexHtml(appTitle).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "EMS bootstrap: minimal index.html"
        );
        boolean mainJsCommitted = gitHubPullRequestService.upsertFile(
                project,
                "frontend/src/main.js",
                frontendScaffoldMainJs().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "EMS bootstrap: Svelte app entrypoint"
        );
        boolean routesJsCommitted = gitHubPullRequestService.upsertFile(
                project,
                "frontend/src/routes.js",
                frontendScaffoldRoutesJs().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "EMS bootstrap: routes registry (append-only extension point for later эпики's UI)"
        );
        boolean appSvelteCommitted = gitHubPullRequestService.upsertFile(
                project,
                "frontend/src/App.svelte",
                frontendScaffoldAppSvelte(appTitle).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "EMS bootstrap: minimal App.svelte shell driven by routes.js (deterministic, avoids cross-эпик scaffold collisions)"
        );
        log.info("Deterministic frontend scaffold for project {}: package.json={}, vite.config.js={}, index.html={}, "
                        + "main.js={}, routes.js={}, App.svelte={}",
                project.getId(), packageJsonCommitted, viteConfigCommitted, indexHtmlCommitted, mainJsCommitted,
                routesJsCommitted, appSvelteCommitted);

        recordGlobalFileClaimIfCommitted(project, "frontend/package.json", packageJsonCommitted);
        recordGlobalFileClaimIfCommitted(project, "frontend/vite.config.js", viteConfigCommitted);
        recordGlobalFileClaimIfCommitted(project, "frontend/index.html", indexHtmlCommitted);
        recordGlobalFileClaimIfCommitted(project, "frontend/src/main.js", mainJsCommitted);
        recordGlobalFileClaimIfCommitted(project, "frontend/src/routes.js", routesJsCommitted);
        recordGlobalFileClaimIfCommitted(project, "frontend/src/App.svelte", appSvelteCommitted);
        return packageJsonCommitted && viteConfigCommitted && indexHtmlCommitted
                && mainJsCommitted && routesJsCommitted && appSvelteCommitted;
    }

    private String frontendScaffoldPackageJson(String appName) {
        return """
                {
                  "name": "%s-frontend",
                  "version": "0.0.1",
                  "private": true,
                  "type": "module",
                  "scripts": {
                    "dev": "vite",
                    "build": "vite build",
                    "preview": "vite preview"
                  },
                  "devDependencies": {
                    "@sveltejs/vite-plugin-svelte": "^3.1.1",
                    "svelte": "^4.2.18",
                    "vite": "^5.3.5"
                  }
                }
                """.formatted(appName);
    }

    private String frontendScaffoldViteConfig() {
        return """
                import { defineConfig } from 'vite';
                import { svelte } from '@sveltejs/vite-plugin-svelte';

                export default defineConfig({
                    plugins: [svelte()],
                    server: {
                        port: 3000
                    }
                });
                """;
    }

    private String frontendScaffoldIndexHtml(String appTitle) {
        return """
                <!doctype html>
                <html lang="en">
                  <head>
                    <meta charset="UTF-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <title>%s</title>
                  </head>
                  <body>
                    <div id="app"></div>
                    <script type="module" src="/src/main.js"></script>
                  </body>
                </html>
                """.formatted(appTitle);
    }

    private String frontendScaffoldMainJs() {
        return """
                import App from './App.svelte';

                const app = new App({
                    target: document.getElementById('app')
                });

                export default app;
                """;
    }

    // The extension point every later эпик's TAG-11 slice should touch instead of App.svelte's shared
    // layout: one new component file, one appended entry here. Kept deliberately tiny and append-only.
    private String frontendScaffoldRoutesJs() {
        return """
                // Routes registry - append-only. Each эпик's UI adds one entry here and its own component
                // file under frontend/src/, instead of editing App.svelte's shared layout/nav directly.
                export const routes = [];
                """;
    }

    private String frontendScaffoldAppSvelte(String appTitle) {
        return """
                <script>
                  import { routes } from './routes.js';

                  const path = window.location.pathname;
                  const active = routes.find(r => r.path === path) ?? routes[0];
                </script>

                <main>
                  <nav>
                    <strong>%s</strong>
                    {#each routes as route}
                      <a href={route.path}>{route.title}</a>
                    {/each}
                  </nav>
                  {#if active}
                    <svelte:component this={active.component} />
                  {:else}
                    <p>No routes registered yet.</p>
                  {/if}
                </main>
                """.formatted(appTitle);
    }

    private String javaArtifactId(ProjectEntity project) {
        String source = project.getSlug() != null && !project.getSlug().isBlank() ? project.getSlug() : project.getName();
        String slug = source == null ? "generated-app" : source.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        return slug.isBlank() ? "generated-app" : slug;
    }

    private String javaScaffoldPomXml(String artifactId) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.3.2</version>
                        <relativePath/>
                    </parent>
                    <groupId>com.eneik.generated</groupId>
                    <artifactId>%s</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <packaging>jar</packaging>
                    <properties>
                        <java.version>21</java.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-data-jpa</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.security</groupId>
                            <artifactId>spring-security-crypto</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.flywaydb</groupId>
                            <artifactId>flyway-core</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>com.h2database</groupId>
                            <artifactId>h2</artifactId>
                            <scope>runtime</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-test</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """.formatted(artifactId);
    }

    private String javaScaffoldGitignore() {
        return """
                target/
                *.class
                .idea/
                *.iml
                .vscode/
                .DS_Store
                *.log
                .env
                node_modules/
                dist/
                """;
    }

    private String javaScaffoldApplicationProperties() {
        return """
                spring.application.name=app
                server.port=8080

                spring.datasource.url=jdbc:h2:file:./data/appdb;AUTO_SERVER=TRUE
                spring.datasource.driver-class-name=org.h2.Driver
                spring.jpa.hibernate.ddl-auto=validate
                spring.flyway.enabled=true
                """;
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
                .filter(this::isEnvironmentBootstrapWishlist)
                .findFirst();
    }

    private boolean isEnvironmentBootstrapWishlist(WishlistEntity wishlist) {
        return ENVIRONMENT_BOOTSTRAP_TOC.equals(wishlist.getTocConstraintRef())
                || (wishlist.getContent() != null
                && wishlist.getContent().contains("EMS bootstrap: define the repository execution boundary"));
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
        if (!autoRecoveryFollowupEnabled) {
            log.warn("ProjectFlowService: auto-recovery follow-up disabled; not creating session postmortem wishlist for session {}", sessionId);
            return Optional.empty();
        }

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
        followUp.setOriginFeatureId(task.getOriginFeatureId() != null ? task.getOriginFeatureId() : task.getFeatureId());
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

        int dismissed = 0;
        for (WishlistEntity wishlist : pendingRecovery) {
            wishlist.setStatus(WishlistStatus.dismissed);
            wishlistRepository.save(wishlist);
            dismissed++;
            log.warn("ProjectFlowService: dismissed out-of-cycle recovery wishlist {} for project {}; "
                            + "no task identity was created",
                    wishlist.getId(), project.getName());
        }
        if (dismissed > 0) {
            log.warn("ProjectFlowService: dismissed {} out-of-cycle recovery wishlist item(s) for project {}",
                    dismissed, project.getName());
        }
        return 0;
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

        int created = 0;
        for (TaskEntity task : blockedTasks) {
            if (created >= limit) {
                break;
            }
            if (hasActiveJulesSession(task.getId())) {
                continue;
            }

            if (isJulesSourceNotFound(task.getJulesDispatchStatus())) {
                log.warn("ProjectFlowService: blocked task {} is waiting for Jules source visibility; not fabricating recovery work",
                        task.getId());
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
            if (isFalsificationAuditTask(task) || isReviewFallbackTask(task) || isDesignReviewTask(task) || isCoverageAuditTask(task) || isPhilosophicalAuditTask(task)) {
                task.setStatus(TaskStatus.failed);
                task.setJulesDispatchStatus("System task blocked; retired instead of generic clarify-wishlist recovery (not role feature work)");
                taskRepository.save(task);
                continue;
            }

            log.warn("ProjectFlowService: retiring blocked task {} without creating a recovery wishlist/task; "
                            + "product recovery reuses an existing planned task ID or enters through self_falsification",
                    task.getId());
            task.setStatus(TaskStatus.failed);
            task.setJulesDispatchStatus("Blocked task retired by iteration-admission poka-yoke; no child work created");
            taskRepository.save(task);
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

    @Transactional
    public boolean ingestPlanFromContent(UUID projectId, String jsonContent) {
        ProjectEntity project = requireProject(projectId);
        java.util.List<WishlistEntity> wishlists = wishlistRepository.findByProjectId(projectId).stream()
                .filter(w -> w.getStatus() == WishlistStatus.pending || w.getStatus() == WishlistStatus.compiling)
                .toList();
        if (wishlists.isEmpty()) {
            wishlists = wishlistRepository.findByProjectId(projectId);
        }
        var epicPlans = parseCompilerPlanContent(jsonContent);
        if (epicPlans.isEmpty()) {
            return false;
        }
        return buildTaskGraphFromSlices(project, wishlists, epicPlans);
    }

    public java.util.List<MLPredictionServiceClient.EpicPlan> parseCompilerPlanContent(String jsonContent) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(jsonContent);
            com.fasterxml.jackson.databind.JsonNode rawEpics = root.path("epics");
            if (!rawEpics.isArray()) {
                return java.util.List.of();
            }
            // 2026-08-14: the prompt states the regulatory floor, but a prompt is a request, not a
            // guarantee - a compiler that ignores it produces a plan that looks perfectly well-formed. This
            // checks the finished plan against the corpus and reports statutory duties it shows no sign of
            // covering. Reporting only, deliberately: coverage is judged by keyword evidence, which is an
            // approximation, and blocking real work on an approximation trades a visible failure for an
            // invisible one. The findings make the false-positive rate measurable; blocking can follow once
            // it is known rather than assumed.
            reportUncoveredStatutoryRequirements(root);
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
                                slice.path("hasUi").asBoolean(false),
                                jsonStringList(slice.path("requirementRefs"))
                        ));
                    }
                }
                String existingEpicId = epicNode.has("existingEpicId") && !epicNode.path("existingEpicId").isNull()
                        ? epicNode.path("existingEpicId").asText()
                        : null;
                // 2026-08-14: kanoClass no longer silently defaults to "Must-Be" when the compiler omits
                // it. That default quietly made every unclassified epic maximally important, which is both
                // the least honest possible reading and the exact failure the philosophical-audit side
                // already guards against by hard-dropping critiques with a missing class (see
                // JulesDispatchService.parsePhilosophicalReport's javadoc: "silently defaulting here would
                // be exactly the 'system re-infers Kano and gets Must-Be' failure mode"). The two sides of
                // the flow now hold the same discipline: an absent classification is recorded as absent,
                // not invented. Downstream consumers must treat blank as "unknown", never as Must-Be.
                result.add(new MLPredictionServiceClient.EpicPlan(
                        existingEpicId,
                        epicNode.path("title").asText(""),
                        epicNode.path("jtbd").asText(""),
                        epicNode.path("kanoClass").asText(""),
                        epicNode.path("cynefinDomain").asText("clear"),
                        epicNode.path("sixSigmaMetric").asText("Escaped defects <= 5%"),
                        epicNode.path("tocConstraintRef").asText("TOC-CONSTRAINT-DECOMPOSITION"),
                        epicNode.path("sourceIndex").asInt(0),
                        jsonStringList(epicNode.path("requirements")),
                        epicNode.path("coverageComplete").asBoolean(false),
                        slices
                ));
            }
            return result;
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    private java.util.List<String> jsonStringList(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || !node.isArray()) {
            return java.util.List.of();
        }
        java.util.List<String> values = new java.util.ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonNode item : node) {
            String value = item.asText("").trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return java.util.List.copyOf(values);
    }
    private String projectOwner(ProjectEntity project) {
        if (project != null && project.getRepoUrl() != null && project.getRepoUrl().contains("github.com/")) {
            String[] parts = project.getRepoUrl().split("github.com/")[1].split("/");
            if (parts.length >= 1 && !parts[0].isBlank()) {
                return parts[0];
            }
        }
        return "eneikcoworking-ctrl";
    }

    private boolean tryCompileWishlistCheaply(ProjectEntity project, WishlistEntity wishlist) {
        try {
            String token = settingsService.effectiveValue("github_token");
            if (token != null && !token.isBlank() && settingsService.effectiveBoolean("github_enabled")) {
                java.net.URI uri = java.net.URI.create("https://raw.githubusercontent.com/" + projectOwner(project) + "/" + project.getRepositoryName() + "/main/.eneik/task-plan.json");
                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(20)).build();
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(uri)
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build();
                java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200 && resp.body() != null && !resp.body().isBlank()) {
                    var epicPlans = parseCompilerPlanContent(resp.body());
                    if (!epicPlans.isEmpty()) {
                        log.info("ProjectFlowService: found merged .eneik/task-plan.json on main for project {}; instantiating task graph", project.getId());
                        return buildTaskGraphFromSlices(project, java.util.List.of(wishlist), epicPlans);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("ProjectFlowService: error checking merged task-plan.json for project {}: {}", project.getId(), e.getMessage());
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

        boolean hasActiveCompilerTask = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()).stream()
                .filter(t -> t.getStatus() == TaskStatus.queued || t.getStatus() == TaskStatus.claimed)
                .anyMatch(this::isWishlistCompilerTask);
        if (hasActiveCompilerTask) {
            log.info("ProjectFlowService: An active compiler task already exists for project {}; holding {} wishlist compile candidate(s) until current compilation finishes",
                    project.getId(), candidates.size());
            return 0;
        }

        // Operator directive (2026-07-25): the "hold new compile candidates until the existing backlog
        // lands on main" gate below (built 2026-07-21 after a real post-mortem: the compiler kept taking
        // on fresh wishlist items while 3 of 4 original tasks were still unmerged, burning Jules budget on
        // an unstable foundation) is REMOVED - confirmed live the same night it was tested end-to-end: one
        // stuck task (a false-positive PR-review bug, since fixed) held the ENTIRE project's wishlist
        // queue hostage for hours, because the gate is scoped to "any deliverable anywhere in the
        // project", not to the specific feature a candidate actually depends on. Operator's explicit
        // choice, accepting the original 2026-07-21 risk in exchange for never letting one stuck item
        // block everything else - the WIP limit right below (wipLimitProjectCompiling) is a SEPARATE,
        // still-active safety net that independently caps how much can be compiling at once, so this
        // removal does not mean literally unbounded concurrent compilation.
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

        Instant compileCooldownFloor = Instant.now().minusSeconds(WISHLIST_COMPILE_DISPATCH_COOLDOWN_SECONDS);
        java.util.List<WishlistEntity> admitted = new java.util.ArrayList<>();
        for (WishlistEntity candidate : candidates) {
            if (admitted.size() >= projectBudget) {
                break;
            }
            Instant lastDispatched = candidate.getLastCompileDispatchedAt();
            if (lastDispatched != null && lastDispatched.isAfter(compileCooldownFloor)) {
                log.info("ProjectFlowService: wishlist {} was already dispatched for compilation {} second(s) ago "
                                + "(cooldown {}s); skipping this cycle to avoid opening a duplicate real Jules session",
                        candidate.getId(), Duration.between(lastDispatched, Instant.now()).getSeconds(),
                        WISHLIST_COMPILE_DISPATCH_COOLDOWN_SECONDS);
                continue;
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

        // Atomic compare-and-swap (2026-07-24 fix - see WishlistRepository.compareAndSetStatus javadoc):
        // only wishlists THIS call actually wins the pending->compiling transition for get dispatched. A
        // concurrent overlapping call (e.g. another admission cycle firing before this one's own DB write
        // committed) loses the race for any wishlist it also picked and correctly skips it here instead of
        // both callers independently dispatching a duplicate compiler session against the same content.
        //
        // 2026-08-14 (bug-hunt sweep): this CAS loop is now a separate REQUIRES_NEW call (via self) rather
        // than inline here. orchestrate() (this method's only caller) no longer wraps itself in
        // @Transactional - a first attempt at removing it broke live dispatch, because
        // compareAndSetStatus is a custom @Modifying JPQL query with no @Transactional of its own
        // (unlike plain save()/delete(), which Spring Data auto-wraps): it needs an ALREADY-ACTIVE writable
        // transaction from its caller, and without orchestrate()'s wrapper there wasn't one -
        // TransactionRequiredException on every real orchestrate() call (see the revert commit for detail).
        // Isolating just this claim step in its own short transaction gives it the active transaction it
        // genuinely needs, while dispatchWishlistCompiler/dispatchToCompilerPersistentWorker's real Jules
        // dispatch call below still runs with no transaction open.
        java.util.List<UUID> admittedIds = admitted.stream().map(WishlistEntity::getId).collect(java.util.stream.Collectors.toList());
        java.util.Set<UUID> wonIds = new java.util.HashSet<>(self.claimWishlistsForCompilation(admittedIds));
        java.util.List<WishlistEntity> won = new java.util.ArrayList<>();
        for (WishlistEntity w : admitted) {
            if (wonIds.contains(w.getId())) {
                w.setStatus(WishlistStatus.compiling);
                won.add(w);
            }
        }
        if (won.isEmpty()) {
            return 0;
        }
        if (persistentWorkerSessionService.isEnabled()) {
            dispatchToCompilerPersistentWorker(project, won);
        } else {
            dispatchWishlistCompiler(project, won);
        }
        return won.size();
    }

    // Extracted from dispatchBatchedWishlistCompiler (2026-08-14, bug-hunt sweep) so this specific
    // @Modifying-query-dependent claim step always runs inside a genuinely active transaction, regardless
    // of whether its caller (orchestrate()) has one open - see the comment at that call site for the live
    // incident this closes. Deliberately plain @Transactional (REQUIRED), not REQUIRES_NEW: orchestrate(),
    // this method's only real caller, has no ambient transaction of its own (removed for the same reason
    // dispatchReviewerFallbackBatch's lock was), so REQUIRED opens a fresh one here exactly like
    // REQUIRES_NEW would - but REQUIRED also correctly joins an already-open transaction when one exists
    // (e.g. AutonomousPipelineIntegrationTest's class-level @Transactional test-rollback wrapper calling
    // this method directly), rather than starting a genuinely separate transaction that can't see that
    // caller's own saveAndFlush'd-but-uncommitted fixture rows. REQUIRES_NEW was tried first and broke
    // exactly those tests (0 admitted instead of 1/3) for precisely this reason.
    @Transactional
    java.util.List<UUID> claimWishlistsForCompilation(java.util.List<UUID> candidateIds) {
        java.util.List<UUID> wonIds = new java.util.ArrayList<>();
        for (UUID id : candidateIds) {
            int updated = wishlistRepository.compareAndSetStatus(id, WishlistStatus.pending, WishlistStatus.compiling);
            if (updated == 1) {
                wonIds.add(id);
            } else {
                log.info("ProjectFlowService: wishlist {} was concurrently claimed by another compile-admission "
                        + "call; skipping here to avoid dispatching a duplicate compiler session", id);
            }
        }
        return wonIds;
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
        // Recorded once, up front, covering every exit path below (fresh message sent, worker busy/needs
        // revert, new worker registered) - see WISHLIST_COMPILE_DISPATCH_COOLDOWN_SECONDS above. An attempt
        // was made against this batch this cycle either way, which is what the cooldown needs to know.
        Instant compileAttemptAt = Instant.now();
        for (WishlistEntity w : admitted) {
            w.setLastCompileDispatchedAt(compileAttemptAt);
        }
        wishlistRepository.saveAll(admitted);

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
                // Same planPath as this worker's original dispatch (or its very first cycle's) - a
                // follow-up cycle OVERWRITES that same file/branch/PR, it must never generate a fresh path.
                TaskEntity workerCarrierTask = worker.getCarrierTaskId() != null
                        ? taskRepository.findById(worker.getCarrierTaskId()).orElse(null)
                        : null;
                String planPath = workerCarrierTask != null ? compilerPlanPath(workerCarrierTask) : WISHLIST_COMPILER_PLAN_PATH;
                if (session != null && julesDispatchService.sendFollowUpMessage(session, wishlistCompilerFollowUpPrompt(admitted, planPath))) {
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

        boolean hasExistingActiveCompilerTask = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()).stream()
                .filter(t -> t.getStatus() == TaskStatus.queued || t.getStatus() == TaskStatus.claimed)
                .anyMatch(t -> t.getPayload() != null && WISHLIST_COMPILER_TASK_TYPE.equals(t.getPayload().path(WISHLIST_COMPILER_PAYLOAD_KEY).asText()));
        if (hasExistingActiveCompilerTask) {
            log.info("ProjectFlowService: An active compiler task already exists for project {}; skipping duplicate carrier creation", project.getId());
            revertWishlistsToPending(admitted);
            return;
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
        String planPath = ".eneik/records/task-plan-" + UUID.randomUUID() + ".json";
        String carrierPrompt = wishlistCompilerPromptBatch(admitted, planPath);
        carrierTask.setDescription(carrierPrompt);
        carrierTask.setStatus(TaskStatus.queued);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put(WISHLIST_COMPILER_PAYLOAD_KEY, WISHLIST_COMPILER_TASK_TYPE);
        payload.put(PERSISTENT_WORKER_CARRIER_MARKER_KEY, true);
        payload.put(WISHLIST_COMPILER_PLAN_PATH_KEY, planPath);
        recordCorpusInjection(payload, carrierPrompt);
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
     * OVERWRITE the same per-worker planPath (see compilerPlanPath) with only this cycle's batch - keeps
     * the file small and keeps JulesDispatchService.parseCompilerPlan completely unchanged (it just reads
     * whatever is in the file at the path it's given).
     */
    private String wishlistCompilerFollowUpPrompt(java.util.List<WishlistEntity> wishlists, String planPath) {
        String body = wishlistCompilerPromptBatch(wishlists, planPath);
        return """
                NEW CYCLE for the same persistent compiler worker session. The brief(s) below are a FRESH
                batch, unrelated to whatever you compiled in a previous cycle on this same branch.
                OVERWRITE `%s` so it contains ONLY the slices for THIS cycle's brief(s) -
                do not keep, merge, or reference any previous cycle's content. Commit the update to the
                same branch/PR you already have open.

                %s
                """.formatted(planPath, body);
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
            // Poka-yoke (live bug, 2026-07-24): one LLM decomposition response can list the same эпик
            // theme twice under one sourceIndex (confirmed live - two epicPlans both titled "Campaign
            // Configuration & Ingestion UI" spawned two independently-running Jules session chains for the
            // same work). This list is scoped to THIS wishlist's own epic list only - reset every loop
            // iteration, never persisted across buildTaskGraphFromSlices calls - so later epicPlans in
            // myEpics can attach to a feature already created moments ago by an earlier epicPlan in the
            // same list, without touching cross-cycle behavior (see
            // EpicDecompositionIntegrationTest.clientSourcedWishlistNeverInvokesMatcherEvenWithDuplicateContent,
            // which exercises two SEPARATE calls and must keep creating 2 features there).
            List<FeatureEntity> epicsResolvedThisWishlist = new ArrayList<>();
            // Live incident, 2026-07-24 (found by monitoring, not speculative): reserving a Flyway version
            // number used to do its own read+write of ProjectEntity for every single Data-Schema task -
            // confirmed live on test-thirty-seventh as a real contributing factor to a
            // PessimisticLockingFailureException retry storm on the `projects` table (7 failed
            // reconcileStrandedPrOpenedWorkflows attempts, ~7 minutes, before one finally succeeded). This
            // holder lets every эпик in THIS wishlist's batch share one in-memory counter, persisted in
            // exactly one write via flushFlywayVersionReservation below instead of once per Data-Schema task.
            TechnicalLeadCompiler.FlywayVersionReservation flywayCache = new TechnicalLeadCompiler.FlywayVersionReservation();
            for (MLPredictionServiceClient.EpicPlan epicPlan : myEpics) {
                if (buildTaskGraphForOneEpic(project, wishlist, epicPlan, epicsResolvedThisWishlist, flywayCache)) {
                    wishlistBuiltAnything = true;
                }
            }
            technicalLeadCompiler.flushFlywayVersionReservation(project.getId(), flywayCache);
            if (wishlistBuiltAnything) {
                anyBuilt = true;
            }
            // A wishlist is "converted" once every epic derived from it has been processed - but only if
            // at least one epic actually produced new work. A wishlist whose every epic collapsed into
            // pre-existing features/tasks (same-batch duplicates, or an empty/invalid slice list after EMS
            // filtering) never converted into anything new; `dismissed` says that honestly instead of
            // falsely claiming a real conversion happened (Ф-honesty fix, 2026-07-24).
            wishlist.setStatus(wishlistBuiltAnything ? WishlistStatus.converted_to_task : WishlistStatus.dismissed);
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
            MLPredictionServiceClient.EpicPlan epicPlan, List<FeatureEntity> epicsResolvedThisWishlist) {
        if (epicPlan.existingEpicId() != null) {
            var existing = featureService.findExistingEpic(project.getId(), epicPlan.existingEpicId());
            if (existing.isPresent()) {
                return existing.get().getId();
            }
            log.warn("ProjectFlowService: compiler echoed existingEpicId {} for project {} but it doesn't "
                            + "resolve to a real эпик in this project; creating a new one instead of guessing.",
                    epicPlan.existingEpicId(), project.getId());
        }
        // Poka-yoke, scoped strictly to self_falsification: the compiler already claims "no existing эпик
        // matches" here, but falsification is by definition auditing already-shipped code, so a bad
        // compiler judgment call has a real and observed cost (duplicate эпики in the same domain). Every
        // other wishlist source (client, coverage_gap, role, etc.) is completely untouched by this check.
        if (wishlist.getSource() == WishlistSource.self_falsification) {
            try {
                List<FeatureEntity> candidates = featureService.listExistingEpics(project.getId());
                Optional<UUID> override = selfFalsificationEpicMatcher.findLikelyExistingEpic(candidates, epicPlan);
                if (override.isPresent()) {
                    log.info("Poka-yoke: self_falsification эпик '{}' deterministically matched existing эпик {}; "
                                    + "attaching instead of creating a duplicate.", epicPlan.title(), override.get());
                    return override.get();
                }
            } catch (Exception e) {
                log.warn("SelfFalsificationEpicMatcher failed for project {}; falling back to create-new: {}",
                        project.getId(), e.getMessage(), e);
            }
        }
        // Poka-yoke, universal to every WishlistSource (2026-07-24): same-batch dedup against эпики already
        // minted moments ago for THIS wishlist's own epic list. In-memory only, never a DB read, so unlike
        // the self_falsification block above it cannot see anything from a prior or later
        // buildTaskGraphFromSlices call - strictly same-batch. Reuses the matcher's existing deterministic
        // Jaccard scoring as-is (it's source-agnostic despite the class name).
        if (!epicsResolvedThisWishlist.isEmpty()) {
            Optional<UUID> sameBatchMatch =
                    selfFalsificationEpicMatcher.findLikelyExistingEpic(epicsResolvedThisWishlist, epicPlan);
            if (sameBatchMatch.isPresent()) {
                log.info("Poka-yoke: эпик '{}' matched another эпик already created earlier in this same "
                                + "decomposition batch ({}); attaching instead of creating a duplicate.",
                        epicPlan.title(), sameBatchMatch.get());
                return sameBatchMatch.get();
            }
        }
        FeatureEntity created = featureService.createFeature(
                project.getId(),
                wishlist.getId(),
                epicPlan.title(),
                epicPlan.jtbd(),
                epicPlan.kanoClass(),
                epicPlan.cynefinDomain(),
                epicPlan.sixSigmaMetric(),
                epicPlan.tocConstraintRef()
        );
        epicsResolvedThisWishlist.add(created);
        return created.getId();
    }

    private boolean buildTaskGraphForOneEpic(ProjectEntity project, WishlistEntity wishlist,
            MLPredictionServiceClient.EpicPlan epicPlan, List<FeatureEntity> epicsResolvedThisWishlist,
            TechnicalLeadCompiler.FlywayVersionReservation flywayCache) {
        java.util.List<MLPredictionServiceClient.TaskSliceMetadata> slices = epicPlan.slices();
        java.util.List<MLPredictionServiceClient.TaskSliceMetadata> graphSlices = emsGraphSlices(wishlist, slices);
        if (graphSlices.isEmpty()) {
            return false;
        }

        warnIfImplicitLayerMissing(wishlist, graphSlices);

        // Every эпик is its own dependency graph - stage anchoring (below) is scoped to THIS эпик's own
        // slices only, never spanning across sibling epics from the same wishlist, since two epics may be
        // entirely unrelated pieces of work that just happened to originate from one client brief.
        UUID featureId = resolveEpicFeatureId(project, wishlist, epicPlan, epicsResolvedThisWishlist);
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
                // Immutable lineage back to the original client brief (root wishlist), same "stamp once,
                // inherit if already stamped" rule as originFeatureId just below - lets buildTaskDescription
                // retrieve the relevant excerpt of the ROOT brief even if this slice's own parent is itself
                // already a slice (a compiler pass over a follow-up wishlist that was itself sliced).
                sliceWishlist.setOriginWishlistId(
                        wishlist.getOriginWishlistId() != null ? wishlist.getOriginWishlistId() : wishlist.getId());
                sliceWishlist.setStatus(WishlistStatus.pending);
                sliceWishlist.setFeatureId(featureId);
                // A feature's own originFeatureId always equals its own id by construction
                // (FeatureService stamps it that way at creation) - safe to use featureId directly here.
                sliceWishlist.setOriginFeatureId(featureId);
                // Persist the compiler's own real per-slice classification (not re-derived later from a
                // keyword search over the whole replicated brief text - see TechnicalLeadCompiler.
                // cynefinDomain's fix commit for the live incident this closes).
                sliceWishlist.setCynefinDomain(slice.cynefinDomain());
                sliceWishlist = wishlistRepository.save(sliceWishlist);
                compileSliceMetadata(project, sliceWishlist.getId(), slice, ownerRole, epicPlan.kanoClass());
                TaskEntity createdTask = technicalLeadCompiler.createTaskFromWishlist(
                        sliceWishlist.getId(),
                        stageAnchor,
                        graphKey,
                        index,
                        graphSlices.size(),
                        dependencyEdgeReason(stageAnchor, ownerRole),
                        flywayCache
                );
                lastInStage = createdTask != null ? createdTask : lastInStage;
                index++;
            }
            if (lastInStage != null) {
                stageAnchor = lastInStage;
            }
        }

        // Coverage audit no longer fires here (per epic, before any code exists) - it now runs once per
        // wishlist, after all its planned tasks are genuinely merged, checked against real code on main.
        // See checkAndDispatchCoverageAudits, called from ContinuousOrchestrationService each tick.
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
                            // Automatic design review task dispatch disabled to prevent non-product task clutter
                            // dispatchDesignReview(project, designResult.repoDraftPath(), brief);
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
        if (parent.getSource() == WishlistSource.self_falsification) {
            // One falsification audit produces one consolidated wishlist. The compiler may split its
            // findings across several owner roles; provenance remains BARCAN-TAG-09 on the parent, while
            // each implementation slice keeps its own explicit owner.
            return normalizeRoleTag(slice.roleTag(), slice);
        }
        if (parent.getSource() == WishlistSource.role_mismatch_followup
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

    // 2026-08-07 (Gricean quantity-optimal grounding, ACP-101): this used to append the parent's full raw
    // brief verbatim after the wrapper line, so every slice of one brief duplicated the entire document in
    // its own content column - and since TechnicalLeadCompiler.buildTaskDescription then truncated that at
    // a fixed 4000-character budget, every slice's "Original Brief" section silently cut off at the same
    // point in the document regardless of which part that specific slice actually needed (confirmed live,
    // test-forty-third: neither the financial-module slices nor the design-system slices ever received
    // their own relevant section of a 30,963-char multi-domain brief). The wrapper line alone is enough
    // here now - buildTaskDescription retrieves the relevant excerpt of the ROOT brief itself via
    // originWishlistId + GeminiContextService instead of reading it off this row's own content.
    private String internalSliceContent(WishlistEntity parent, MLPredictionServiceClient.TaskSliceMetadata slice, int index) {
        String uiMarker = (slice.hasUi()
                || looksLikeUi(slice.title() + " " + slice.jtbd() + " " + slice.acceptanceCriteria())) ? "UI " : "";
        return "Internal " + uiMarker + "work item " + index + " (" + targetRoleForSlice(parent, slice) + ") from wishlist " + parent.getId()
                + ": " + safeSliceTitle(slice.title());
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

    /**
     * Whole words, never substrings (2026-08-15).
     *
     * This used to call {@code contains(...)} for each term, so "ui" matched inside b<b>ui</b>ld,
     * req<b>ui</b>rement, g<b>ui</b>de, s<b>ui</b>te and circ<b>ui</b>t; "form" inside <b>form</b>at,
     * in<b>form</b>ation, trans<b>form</b> and per<b>form</b>; "design" inside <b>design</b>ed; and
     * "public" inside any mention of a public API. A "Material Data Schema" whose acceptance criteria said
     * "build the table" or "the schema must inform ..." was therefore UI work.
     *
     * That is not cosmetic: besides naming the item, this feeds inferRoleTag below, so a substring inside
     * an unrelated word could route backend work to a UI role. Same defect as the corpus keyword traps
     * found the same day ("quest" inside "request", "level" inside "access level") - a term is a word, and
     * asking whether a word occurs is not the same as asking whether its letters occur.
     */
    private static final java.util.regex.Pattern UI_TERMS = java.util.regex.Pattern.compile(
            "\\b(ui|ux|frontend|front-end|screen|screens|page|pages|form|forms|button|buttons|browser|"
                    + "svelte|design|designs|admin|panel|panels|portal|dashboard|dashboards|public)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    private boolean looksLikeUi(String value) {
        return value != null && UI_TERMS.matcher(value).find();
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public static final String WISHLIST_COMPILER_TASK_TYPE = "wishlist_compiler";
    public static final String WISHLIST_COMPILER_PAYLOAD_KEY = "taskType";
    // Plural: one compiler task now covers a whole admitted batch (see dispatchBatchedWishlistCompiler),
    // not a single wishlist. Kept as a JSON array of UUID strings rather than one string.
    public static final String WISHLIST_COMPILER_WISHLIST_IDS_KEY = "compilesWishlistIds";
    // Fixed-path collision fix (2026-07-24, same root cause diagnosed for PR_REVIEW_FALLBACK_VERDICT_PATH/
    // DESIGN_REVIEW_VERDICT_PATH/COVERAGE_AUDIT_REPORT_PATH): every compiler session used to write
    // `.eneik/task-plan.json`, a single shared path - any two compiler sessions open at the same time
    // (one-shot batch + persistent worker, or two persistent workers across projects sharing a branch
    // namespace) guaranteed a merge conflict on this exact file. Kept only as the fallback for tasks
    // dispatched before this fix (see compilerPlanPath); the real path is now generated fresh per one-shot
    // dispatch or per persistent-worker carrier (reused across that one worker's own follow-up cycles,
    // which is correct - the collision was always ACROSS different branches/workers, never within one) and
    // stashed in the dispatching task's own payload, same idiom as PR_REVIEW_FALLBACK_VERDICT_PATH_KEY.
    private static final String WISHLIST_COMPILER_PLAN_PATH = ".eneik/task-plan.json";
    public static final String WISHLIST_COMPILER_PLAN_PATH_KEY = "taskPlanPath";

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
        String planPath = ".eneik/records/task-plan-" + UUID.randomUUID() + ".json";
        String compilerPrompt = wishlistCompilerPromptBatch(wishlists, planPath);
        compilerTask.setDescription(compilerPrompt);
        compilerTask.setStatus(TaskStatus.queued);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put(WISHLIST_COMPILER_PAYLOAD_KEY, WISHLIST_COMPILER_TASK_TYPE);
        payload.put(WISHLIST_COMPILER_PLAN_PATH_KEY, planPath);
        recordCorpusInjection(payload, compilerPrompt);
        com.fasterxml.jackson.databind.node.ArrayNode idsArray = payload.putArray(WISHLIST_COMPILER_WISHLIST_IDS_KEY);
        for (WishlistEntity w : wishlists) {
            idsArray.add(w.getId().toString());
        }
        compilerTask.setPayload(payload);

        // Recorded here, at dispatch ATTEMPT time (not just success) - see
        // WISHLIST_COMPILE_DISPATCH_COOLDOWN_SECONDS above. Even a failed/blocked attempt still closes the
        // cooldown window, so a wishlist that can't currently be dispatched doesn't get retried in a tight
        // loop either.
        Instant now = Instant.now();
        for (WishlistEntity w : wishlists) {
            w.setLastCompileDispatchedAt(now);
        }
        wishlistRepository.saveAll(wishlists);

        compilerTask = taskRepository.save(compilerTask);
        dispatchCompilerTask(compilerTask);
    }

    /** Falls back to the old shared constant for tasks dispatched before this fix. */
    public String compilerPlanPath(TaskEntity task) {
        if (task.getPayload() == null) {
            return WISHLIST_COMPILER_PLAN_PATH;
        }
        String raw = task.getPayload().path(WISHLIST_COMPILER_PLAN_PATH_KEY).asText(null);
        return raw == null || raw.isBlank() ? WISHLIST_COMPILER_PLAN_PATH : raw;
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

    /**
     * @return true only when this attempt actually put the task in front of Jules (fresh dispatch or a
     * confirmed already-dispatched duplicate) - false for every other outcome (no capacity, blocked, Jules
     * rejected the request, an exception). Callers that report dispatch status (2026-08-07 fix, live
     * incident: FalsificationCycleService used to log "Dispatched" unconditionally right after this
     * returned void, even when the Jules call itself had just failed with HTTP 400) must use this, not just
     * assume success because a TaskEntity got created - the task legitimately stays `queued` on false and
     * will retry on the next compiler-dispatch cycle, which is real, not a bug in itself.
     */
    private boolean dispatchCompilerTask(TaskEntity compilerTask) {
        Optional<AccountEntity> accountOpt = self.claimAccountForTask(compilerTask.getId(), () ->
                accountRepository.lockAccountByNameWithCapacity(
                        taskCompilerAccountName(), maxConcurrentJulesSessionsPerAccount));
        if (accountOpt.isEmpty()) {
            log.warn("Wishlist compiler account '{}' has no free capacity right now; task {} stays queued for the next cycle",
                    taskCompilerAccountName(), compilerTask.getId());
            return false;
        }

        AccountEntity account = accountOpt.get();
        try {
            TaskEntity savedTask = taskRepository.findById(compilerTask.getId()).orElse(compilerTask);
            JulesDispatchResult dispatch = julesDispatchService.dispatch(savedTask, account.getId());
            savedTask.setJulesSessionName(dispatch.sessionName());
            savedTask.setJulesDispatchStatus(dispatch.reason());
            taskRepository.save(savedTask);
            if (!dispatch.dispatched()) {
                if (isJulesSourceNotFound(dispatch.reason())) {
                    claimService.closeTaskAsBlocked(savedTask.getId(), dispatch.reason());
                    log.warn("Blocked wishlist compiler task {} because Jules cannot see the repository source: {}",
                            savedTask.getId(), dispatch.reason());
                    return false;
                }
                claimService.releaseClaimToQueue(savedTask.getId(), dispatch.reason());
                log.warn("Failed to dispatch wishlist compiler task {} to account {}: {}",
                        savedTask.getId(), account.getName(), dispatch.reason());
                return false;
            }
            // JulesDispatchService.dispatch() reports dispatched=true both for a genuinely fresh dispatch
            // and for the "already dispatched, skip duplicate" no-op - logging both as "Dispatched compiler
            // task" made the no-op case indistinguishable from a real dispatch in the logs.
            if ("already dispatched, skipping duplicate".equals(dispatch.reason())) {
                log.info("Compiler task {} was already dispatched to account {}; skipped duplicate dispatch", savedTask.getId(), account.getName());
            } else {
                log.info("Dispatched compiler task {} to account {}", savedTask.getId(), account.getName());
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to claim/dispatch compiler task {} to account {}: {}",
                    compilerTask.getId(), account.getName(), e.getMessage(), e);
            return false;
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
    //
    // Operator directive (2026-07-23): the compiler account (eneikdru) now also participates in this and
    // every other general-pool selector below (reservedName passed as null, not taskCompilerAccountName())
    // - it is no longer excluded from implementer/reviewer/general-pool dispatch. Its own concurrency
    // ceiling is set via AccountEntity.maxConcurrentSessions (see V50 migration), independent of the
    // shared jules.max-concurrent-sessions-per-account default used by every other account.
    private void dispatchToGeneralPool(TaskEntity task) {
        dispatchToGeneralPool(task, java.util.Set.of());
    }

    // Charter Pattern #12 (independent verification, not self-attestation): review-fallback carrier
    // tasks must exclude the account(s) that implemented the code under review, so the general-pool
    // round-robin selector can't hand a PR's review back to the same account that wrote it - nothing
    // about the underlying capacity query otherwise prevents that. Callers with nothing to exclude
    // (plain implementer dispatch, design-review - see the call site comment at isDesignReviewTask)
    // use the no-arg overload above.
    private void dispatchToGeneralPool(TaskEntity task, java.util.Set<String> excludedAccountNames) {
        String excludedNamesCsv = excludedAccountNames.isEmpty() ? null : String.join(",", excludedAccountNames);
        String failedAccountName = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            String excludedForThisAttempt = failedAccountName;
            Optional<AccountEntity> accountOpt = self.claimAccountForTask(task.getId(), () ->
                    accountRepository.lockNextJulesAccountWithCapacity(
                            task.getProject().getId(),
                            task.getRole().getTag(),
                            maxConcurrentJulesSessionsPerAccount,
                            excludedForThisAttempt,
                            maxDailySessionsPerAccount,
                            excludedNamesCsv
                    ));
            if (accountOpt.isEmpty()) {
                log.warn("No general-pool account has free capacity right now; task {} stays queued for the next cycle", task.getId());
                return;
            }

            AccountEntity account = accountOpt.get();
            try {
                TaskEntity savedTask = taskRepository.findById(task.getId()).orElse(task);
                JulesDispatchResult dispatch = julesDispatchService.dispatch(savedTask, account.getId());
                savedTask.setJulesSessionName(dispatch.sessionName());
                savedTask.setJulesDispatchStatus(dispatch.reason());
                taskRepository.save(savedTask);
                if (!dispatch.dispatched()) {
                    claimService.releaseClaimToQueue(savedTask.getId(), dispatch.reason());
                    log.warn("Failed to dispatch task {} to account {} (attempt {}/3): {}. Rotating to next account...",
                            savedTask.getId(), account.getName(), attempt + 1, dispatch.reason());
                    failedAccountName = account.getName();
                    continue;
                }
                log.info("Dispatched task {} to general-pool account {}", savedTask.getId(), account.getName());
                return;
            } catch (Exception e) {
                log.error("Failed to claim/dispatch task {} to account {}: {}", task.getId(), account.getName(), e.getMessage(), e);
                failedAccountName = account.getName();
            }
        }
    }

    // Charter Pattern #12: resolves the account(s) that implemented the code a review-fallback batch is
    // about to review, so dispatchToGeneralPool can exclude them from picking up the review. Uses the
    // same implementer-session lookup JulesDispatchService.applyReviewVerdictToTask already uses to find
    // the PR being judged (prefer the session sitting at "pr_opened", else any session that has a PR).
    private java.util.Set<String> implementerAccountNamesForReviewFallback(TaskEntity reviewFallbackTask) {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (UUID originalTaskId : reviewFallbackTargetTaskIds(reviewFallbackTask)) {
            java.util.List<JulesSessionEntity> sessions = julesSessionRepository.findByTaskId(originalTaskId);
            JulesSessionEntity implementerSession = sessions.stream()
                    .filter(s -> "pr_opened".equals(s.getStatus()))
                    .findFirst()
                    .orElseGet(() -> sessions.stream().filter(s -> s.getPrUrl() != null).findFirst().orElse(null));
            if (implementerSession == null || implementerSession.getAccountId() == null) {
                continue;
            }
            accountRepository.findById(implementerSession.getAccountId()).ifPresent(a -> names.add(a.getName()));
        }
        return names;
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

    /**
     * Renders the regulatory floor from the versioned market corpus (market-corpus/capabilities.json)
     * rather than from text hard-coded in this prompt. Knowledge about what a market requires changes when
     * laws change, not when this class is rebuilt - keeping it in files means it can be reviewed in a diff
     * and corrected by a human without a deploy.
     *
     * Only statutory/standard/observed entries are rendered, enforced by
     * MarketCorpusService.influentialExpectations - a "hypothesis" entry (including anything an AI wrote
     * from general knowledge) is never allowed to shape what gets built.
     *
     * Returns an empty string when no corpus is available, which leaves the surrounding floor framing
     * intact and simply carries no itemised requirements - the flow degrades to its pre-corpus behaviour
     * instead of failing.
     */
    /**
     * Never throws: a compliance check that can break decomposition is worse than no check at all, since
     * the failure mode becomes "no plans at all" instead of "a plan missing a legal duty".
     */
    private void reportUncoveredStatutoryRequirements(com.fasterxml.jackson.databind.JsonNode planRoot) {
        if (marketComplianceGate == null) {
            return;
        }
        try {
            String planText = marketComplianceGate.flatten(planRoot);
            var findings = marketComplianceGate.uncoveredStatutoryRequirements(
                    planText, java.util.List.of("DE", "US"));
            for (var finding : findings) {
                log.warn("Compliance gap in decomposition plan: {} appears uncovered - {} (basis: {})",
                        finding.capabilityId(), finding.requirement(), finding.source());
            }
        } catch (Exception e) {
            log.warn("MarketComplianceGate check failed, continuing with the plan: {}", e.getMessage());
        }
    }

    /**
     * The known value chains per product kind, rendered for the completeness floor.
     *
     * This is the half of the decomposition the brief cannot supply. A client knows their business and not
     * what software of their class must contain, so a plan built purely from the brief reproduces the
     * brief's gaps with perfect fidelity - which was the original complaint this corpus was built to fix.
     *
     * Every chain here is 'derived': reasoned domain knowledge that states WHY it has the shape it does and
     * that carries no invented numbers (see market-corpus/README.md). Chains still marked hypothesis are
     * excluded, so the corpus can hold an idea without it steering anything.
     *
     * All kinds are rendered rather than one, because nothing in a wishlist reliably says which kind THIS
     * product is, and picking one here would be exactly the silent assumption the corpus exists to prevent.
     * The compiler is reading the brief and can tell.
     */
    private String valueChainsFromCorpus() {
        if (marketCorpusService == null) {
            return "";
        }
        com.fasterxml.jackson.databind.JsonNode profiles = marketCorpusService.profiles();
        StringBuilder out = new StringBuilder();
        for (com.fasterxml.jackson.databind.JsonNode profile : profiles) {
            if (!"derived".equalsIgnoreCase(profile.path("status").asText(""))) {
                continue;
            }
            StringBuilder chains = new StringBuilder();
            for (com.fasterxml.jackson.databind.JsonNode path : profile.path("valuePaths")) {
                java.util.List<String> links = new java.util.ArrayList<>();
                for (com.fasterxml.jackson.databind.JsonNode link : path.path("path")) {
                    links.add(link.asText(""));
                }
                if (links.isEmpty()) {
                    continue;
                }
                chains.append("                    - ").append(path.path("actor").asText("user"));
                String condition = path.path("appliesWhen").asText("");
                if (!condition.isBlank()) {
                    chains.append(" (only if ").append(condition).append(")");
                }
                chains.append(": ").append(String.join(" -> ", links)).append("\n");
            }
            if (chains.length() == 0) {
                continue;
            }
            out.append("                  ").append(profile.path("title").asText(profile.path("id").asText("")))
                    .append(":\n").append(chains);
        }
        if (out.length() == 0) {
            return "";
        }
        return "                  KNOWN CHAINS BY PRODUCT KIND (compare, do not copy):\n" + out;
    }


    /**
     * Records what the compiler prompt ACTUALLY contained, so the corpus's influence on a decomposition can
     * be checked afterwards instead of assumed (2026-08-15).
     *
     * Deliberately derived from the built prompt string rather than recomputed alongside it. A parallel
     * computation would be a second claim that can drift from the first - exactly the divergence that let
     * three epics be credited on 2026-08-14 to a corpus that had not yet become influential. Reading the
     * artifact is the only record that cannot disagree with it.
     *
     * The prompt itself is already persisted in full: `tasks.description` is TEXT and holds it verbatim.
     * What was missing is a way to ASK - "did the chains render, and which duties came with them" - without
     * reading several thousand characters.
     */
    private void recordCorpusInjection(ObjectNode payload, String prompt) {
        if (payload == null || prompt == null) {
            return;
        }
        ObjectNode injected = payload.putObject("corpusInjection");
        boolean chainsPresent = prompt.contains("KNOWN CHAINS BY PRODUCT KIND");
        injected.put("valueChainsRendered", chainsPresent);
        if (chainsPresent && marketCorpusService != null) {
            com.fasterxml.jackson.databind.node.ArrayNode kinds = injected.putArray("productKinds");
            for (com.fasterxml.jackson.databind.JsonNode profile : marketCorpusService.profiles()) {
                String title = profile.path("title").asText("");
                if (!title.isBlank() && prompt.contains(title + ":")) {
                    kinds.add(profile.path("id").asText(title));
                }
            }
        }
        if (marketCorpusService != null) {
            com.fasterxml.jackson.databind.node.ArrayNode duties = injected.putArray("regulatoryDuties");
            java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
            for (String market : java.util.List.of("DE", "US")) {
                for (var e : marketCorpusService.influentialExpectations(market)) {
                    String requirement = e.requirement();
                    if (requirement == null || requirement.isBlank()) {
                        continue;
                    }
                    // Match on a prefix rather than the whole line: the rendered line carries market tags,
                    // applicability conditions and the source, so the requirement is a substring of it.
                    String probe = requirement.length() > 60 ? requirement.substring(0, 60) : requirement;
                    if (prompt.contains(probe) && seen.add(e.capabilityId() + "|" + probe)) {
                        duties.add(e.capabilityId());
                    }
                }
            }
        }
    }

    private String regulatoryFloorFromCorpus() {
        if (marketCorpusService == null) {
            return "";
        }
        java.util.List<com.eneik.production.services.market.MarketCorpusService.Expectation> all =
                new java.util.ArrayList<>();
        // Both target markets are rendered together, each tagged, because nothing in a wishlist tells us
        // which market THIS project serves. Guessing it would be exactly the kind of silent assumption this
        // corpus exists to avoid; the compiler sees both and decides from the brief's own content.
        for (String market : java.util.List.of("DE", "US")) {
            all.addAll(marketCorpusService.influentialExpectations(market));
        }
        if (all.isEmpty()) {
            return "";
        }
        java.util.LinkedHashSet<String> lines = new java.util.LinkedHashSet<>();
        for (var e : all) {
            StringBuilder line = new StringBuilder("                  * ");
            if (e.market() != null && !e.market().isBlank()) {
                line.append("[").append(e.market()).append("] ");
            }
            // Applicability travels with the requirement instead of being resolved here. Nothing in this
            // service knows what kind of product a brief describes, and inferring it would misapply
            // consumer-sales duties (14-day withdrawal, destination sales tax) to an internal tool - the
            // scope inflation this floor explicitly forbids. The compiler is already reading the brief, so
            // it is the only component that can judge, and it needs the condition stated to do so.
            if (e.appliesWhen() != null && !e.appliesWhen().isBlank()) {
                line.append("APPLIES WHEN ").append(e.appliesWhen()).append(" - ");
            }
            if (e.appliesToProfiles() != null && !e.appliesToProfiles().contains("*")
                    && !e.appliesToProfiles().isEmpty()) {
                line.append("ONLY for products of kind ").append(String.join("/", e.appliesToProfiles()))
                        .append(" - ");
            }
            line.append(e.requirement());
            if (e.source() != null && !e.source().isBlank()) {
                line.append(" (basis: ").append(e.source()).append(")");
            }
            if (e.note() != null && !e.note().isBlank()) {
                line.append(" NOTE: ").append(e.note());
            }
            lines.add(line.toString());
        }
        return String.join("\n", lines);
    }

    String wishlistCompilerPromptBatch(java.util.List<WishlistEntity> wishlists, String planPath) {
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
            // philosophical_falsification is deliberately carved OUT of the suppression rule above (2026-07-25):
            // that rule exists to stop narrow corrective follow-ups (a typo fix, a coverage gap on one
            // existing endpoint) from inventing a whole new schema+contract+UI stack for what should be a
            // one-line patch. A philosophical critique is the opposite case - a genuinely NEW product
            // capability a real philosopher's worldview identified as missing - and the whole point of the
            // philosophical falsification track is that it CAN warrant new layers. Suppressing that here
            // would silently defeat the feature. It gets its own bounded directive instead, and its own
            // mandatory Kano-copy instruction (Layer 1 already embeds "Kano: X"/"Cynefin: complex" as plain
            // text in the brief - FalsificationCycleService.philosophicalWishlistContent - this directive is
            // what makes the compiler actually honor it instead of re-classifying from scratch).
            if (w.getSource() == WishlistSource.philosophical_falsification) {
                briefsSection.append(" [PHILOSOPHICAL PRODUCT CRITIQUE - this is a genuinely NEW product")
                        .append(" capability a named real philosopher's worldview identified as missing, not a")
                        .append(" defect. You MAY create the schema/contract/UI layers it genuinely needs, but")
                        .append(" keep it small - at most one epic. The Kano class for this epic is ALREADY")
                        .append(" DETERMINED and stated in the brief text below (\"Kano: ...\") - copy it")
                        .append(" verbatim into \"kanoClass\"; do not re-classify it or default it to Must-Be.]");
            } else if (w.getSource() != WishlistSource.client) {
                briefsSection.append(" [FOLLOW-UP ON ALREADY-EXISTING FUNCTIONALITY - do NOT create a new")
                        .append(" data schema, API contract, or UI slice for this; the data model, API, and")
                        .append(" UI for this feature already exist elsewhere in the project. Produce exactly")
                        .append(" ONE work item that patches the existing code directly, unless this literally")
                        .append(" cannot be done without a new layer.]");
            }
            // Precision-grounding (2026-08-07): only for genuinely client-authored text - a short brief and
            // a huge spec go through the exact same mechanism, just different volume. Other sources
            // (self_falsification, role follow-ups, philosophical_falsification) are already
            // system-generated text, not a client's own words, so grounding doesn't apply to them.
            // Computed lazily on first compilation and cached on the wishlist itself so a retry/second
            // compiler pass never re-pays the pattern-lookup cost.
            String briefText = w.getContent();
            if (w.getSource() == WishlistSource.client) {
                if (w.getGroundedContent() == null || w.getGroundedContent().isBlank()) {
                    String grounded = requirementGroundingService.ground(w.getContent());
                    w.setGroundedContent(grounded);
                    wishlistRepository.save(w);
                    // Gricean quantity-optimal grounding (ACP-101): index the grounded brief once, here,
                    // so each compiler-generated slice's task description can later retrieve only the
                    // excerpt relevant to its own JTBD (TechnicalLeadCompiler.buildTaskDescription) instead
                    // of every slice duplicating the whole brief and being truncated at a fixed length.
                    // Same opt-in gate as every other GeminiContextService caller (GeminiContextService's own
                    // class javadoc: "an operator who hasn't opted in gets byte-for-byte the old prompt
                    // behavior and pays zero extra Gemini cost") - indexDocument itself doesn't gate, so an
                    // ungated call here would embed every client brief even with the feature flag off.
                    if (settingsService.effectiveBoolean("gemini_context_learning_enabled")) {
                        geminiContextService.indexDocument("client_brief_requirement", "client_brief:" + w.getId(), grounded);
                    }
                }
                if (w.getGroundedContent() != null && !w.getGroundedContent().isBlank()) {
                    briefText = w.getGroundedContent();
                }
            }
            briefsSection.append("\n").append(defaultText(briefText, "(empty brief)")).append("\n\n");
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
                TWO epics). A brief may produce 1 epic or several - never assume exactly one. Before
                deciding epic count, actively enumerate the distinct, independently-describable
                capabilities/sub-features the brief mentions within each theme (not just the theme's
                headline) - a module that bundles several genuinely separate capabilities under one theme
                name is a signal to look closer at STEP 3's sizing, not a reason to treat it as one small
                unit of work.
                STEP 2 - for EACH epic, decide semantically against the EXISTING epics list below: does
                this epic's narrative genuinely match one already in the project, or is it new work? If it
                matches, set "existingEpicId" to that epic's exact id and do NOT invent a new title/jtbd/
                kano/cynefin for it (reuse the match, do not restate it). If nothing matches, set
                "existingEpicId" to null and provide fresh epic-level title/jtbd/kano/cynefin.
                STEP 3 - within each epic, extract an exhaustive numbered requirement list (R1, R2, ...)
                from the brief, then decompose it into 1-8 task slices with the existing role/graph rules
                below. Every requirement MUST be referenced by at least one task and every task MUST name
                the requirements it fulfils. Set coverageComplete=true only after checking that no explicit
                or structurally required requirement is omitted.

                EXISTING EPICS IN THIS PROJECT (match against these before creating a new one):
                %s

                Output rules:
                - All output must be in English, even when the source brief is written in another
                  language. Translate and normalize intent; never copy the raw brief text verbatim into
                  your output.
                - Every epic MUST include a "sourceIndex" field (integer) naming which Brief #N it
                  addresses. Never mix epics from two different briefs into one, and never split one
                  epic's slices across two different epic blocks.
                - Every input Brief #N MUST be represented by at least one epic. Omitting a brief is an
                  invalid plan.
                - "requirements" must contain stable entries formatted "R1: concrete requirement". Each
                  slice's "requirementRefs" may contain only ids declared by that epic. The union of all
                  slice requirementRefs MUST equal the epic requirement-id set.
                - Every epic MUST include at least one BARCAN-TAG-06 QA slice covering the epic's own new
                  work - this is a FLOOR like the BARCAN-TAG-08/BARCAN-TAG-02 structural rule below, never
                  omit it just because the brief itself never mentions testing. Its description and
                  acceptanceCriteria MUST span the full test pyramid the epic actually needs, not unit
                  tests alone: unit tests for pure logic, integration tests for API/data flows the epic
                  introduces, and E2E tests for the critical user journey the epic enables end-to-end -
                  omit a pyramid layer only when the epic genuinely has no surface at that layer (e.g. a
                  pure backend epic with no UI has no E2E layer). Do not create a separate BARCAN-TAG-00
                  integration/merge-hygiene slice unless the brief explicitly asks to verify existing
                  code, fix merge hygiene, or review an already implemented slice.
                  EXCEPTION (Gricean floor, not a loophole): when the epic's own cynefinDomain is "clear"
                  AND the epic has at most 2 requirements total, you MAY fold this QA coverage into the
                  implementation slice's own acceptanceCriteria/DoD instead of a separate BARCAN-TAG-06
                  slice - a genuinely trivial epic does not need a whole separate task/PR/Jules session
                  just to assert what the implementation slice's own tests already cover. Any epic with
                  more requirements, or a cynefinDomain of complicated/complex/chaotic, keeps the
                  mandatory separate slice - this exception narrows the floor, it never removes it for
                  real work.
                - For complex or ambiguous work, create a short BARCAN-TAG-09 or BARCAN-TAG-01
                  spike/decision slice instead of guessing at implementation.
                - Some layers are structurally required even if the brief's narrative never explicitly
                  asks for them: if the epic needs to persist or query structured data, you MUST include
                  a BARCAN-TAG-08 data/schema slice describing that model; if the epic needs to expose
                  functionality to a frontend, mobile client, or external integration, you MUST include a
                  BARCAN-TAG-02 API slice describing that contract. Infer these from what the epic needs
                  to actually work end-to-end, not only from what the client's words explicitly mention.
                  This rule is a FLOOR, not a CEILING: it guarantees each needed layer gets AT LEAST one
                  slice, it does not cap a role at exactly one. If a role (e.g. BARCAN-TAG-02) is needed
                  for several requirements that are not naturally one cohesive code change, give that role
                  multiple slices - one per genuinely separate requirement/capability - instead of
                  force-fitting everything the role touches into a single generic slice.
                - If the epic needs BOTH a BARCAN-TAG-02 backend slice and a BARCAN-TAG-11 frontend slice
                  that will be built in parallel against each other, you MUST also include a
                  BARCAN-TAG-12 slice that defines the shared API contract (endpoints, request/response
                  shape, DTOs) they both build against - sequence it before the parallel implementation
                  slices, not alongside them.
                  EXCEPTION (Gricean floor, not a loophole): when the epic's own cynefinDomain is "clear"
                  AND the shared surface is a single endpoint with no more than 2 fields each way, you MAY
                  have the backend slice state the tiny contract directly in its own acceptanceCriteria
                  instead of a separate BARCAN-TAG-12 slice - a one-endpoint, two-field contract does not
                  need its own task/PR/Jules session ahead of the implementation that already defines it.
                  Any wider or less certain contract, or a cynefinDomain of complicated/complex/chaotic,
                  keeps the mandatory separate slice.
                - Epic-level "jtbd" is customer-facing: "When [the end customer]..., I want..., so
                  that...". Task-level "jtbd" is scoped to the EPIC, not the customer: "When implementing
                  [X] for this epic, I want [Y], so that [the epic's outcome/Z] is achieved" - never repeat
                  the customer-facing sentence verbatim at task level.
                - Each acceptanceCriteria field must contain 2-4 role-specific Given/When/Then lines.
                - COMPLETENESS FLOOR - the most important rule here, because value along a product's main
                  path MULTIPLIES rather than adds. A shop where the customer can browse and cannot pay is
                  not "mostly done", it is worth zero; a booking system that takes bookings and never
                  reminds anyone loses them to no-shows, and that loss multiplies against everything else
                  built. So before writing epics, work out the ONE path the end user walks from intention to
                  outcome for THIS brief, and name its links explicitly as epic requirements. Build the path
                  from what the client actually described.
                  Then compare it against the known chains below for products of this kind. They are here
                  because a client writing a brief knows their business but not what software of their
                  class must contain, so briefs arrive with predictable holes and a plan built only from
                  the brief reproduces them faithfully. A link that appears below and NOT in your path is
                  the single most likely thing to be missing - so for each one, either cover it or state in
                  the epic requirements why this product genuinely does not need it. Note also that some
                  products have SEVERAL chains that fail independently: a marketplace where sellers cannot
                  list is dead however good the buying is. Never add a link the described product has no
                  use for - this list exists to stop you forgetting, not to pad the plan.
%s
                  Then check the path is unbroken, and cover every link you found. Three failure modes to
                  look for specifically, because they are what actually breaks delivered products:
                  * a missing link - the user reaches a point where the product simply cannot continue
                    (chose goods, no way to pay; submitted a request, nobody is notified it arrived);
                  * a link with no confirmation - the user acts and is left not knowing whether it worked;
                    silence after an action is indistinguishable from failure, and the user repeats it or
                    abandons;
                  * no recovery when a link fails - payment declines, upload breaks, the address is wrong,
                    and the product offers no way back. Every link that can fail needs its failure path
                    covered somewhere, or the product only works when nothing goes wrong.
                  If a link is genuinely outside this project's scope (the client handles it by hand, or
                  another system does it), say so explicitly in the epic requirements instead of leaving a
                  silent hole - a known handover is fine, an unnoticed gap is not. This rule exists to stop
                  you FORGETTING, not to make you add: never invent a link the described path does not
                  need.
                - QUALITY FLOOR - what separates a product that technically works from one worth using.
                  These are properties of the slices you already create, expressed in their own
                  acceptanceCriteria, not extra epics:
                  * usable on a phone - most real traffic is mobile, and a layout that only works on a
                    desktop is broken for the majority of its users;
                  * every action gets visible feedback - success, failure, or progress; nothing may leave
                    the user guessing;
                  * what the user typed survives - a failed submit, a lost connection or a validation error
                    must not silently discard the data they entered;
                  * destructive and expensive actions are reversible or confirmed before they happen.
                - REGULATORY FLOOR (same kind of rule as the mandatory QA slice above: required whether or
                  not the brief mentions it, because the client is buying software for the German and US
                  markets and is not expected to know what those markets legally require). Each item below
                  applies ONLY when its stated condition is true for this brief - never add one whose
                  condition does not hold, that is scope inflation, not compliance. Items marked [DE] apply
                  to products serving Germany, [US] to products serving the United States; unmarked items
                  apply to both. Deliver a requirement that is a property of every screen (accessibility)
                  inside each UI slice's own acceptanceCriteria - never as one separate "make it accessible"
                  epic bolted on at the end, which is how accessibility reliably fails to happen. Deliver a
                  requirement that is a distinct capability (data subject rights, consent, backup) as its
                  own epic.
                %s
                  Classify every epic created under this floor as Must-Be, because that is what it is: its
                  absence is a defect, its presence delights nobody.
                - MEASUREMENT FLOOR (unconditional - every product gets exactly one such epic, whatever it
                  is): the product must measure whether it is actually achieving what it exists for. Derive
                  this the Goal-Question-Metric way, never by installing a counter: read the brief for the
                  ONE outcome the client is really buying (more bookings, fewer support calls, faster order
                  handling, less manual re-typing), write the two or three questions whose answers would
                  tell you whether that outcome is happening, and only then define the metrics that answer
                  exactly those questions. State the goal and the questions explicitly in the epic's jtbd
                  and requirements, so the link from metric back to purpose stays visible and checkable.
                  A metric that answers none of the stated questions must not be built - "we might want it
                  later" is how dashboards become noise nobody reads. Instrumenting a funnel the client
                  never asked about is the same error in the other direction: measure the client's outcome,
                  not everything measurable. This epic is Must-Be: a product nobody can tell is working is
                  indistinguishable from one that is not.
                - Classify Kano at the EPIC level ONLY (Must-Be, Performance, Attractive, or Reverse) - do
                  not repeat it per task slice. "Reverse" means the client asked for something the product
                  would be actively WORSE for having - not merely low-value, but harmful: a mobile app for
                  a business its customers use twice a year, mandatory registration before checkout, an
                  in-product chat widget when customers already live in messaging apps, a recommendation
                  engine for a catalogue too small to learn from. You are expected to classify honestly
                  even when the client explicitly asked for it: the client knows their business, not what
                  software of this kind should contain. Still decompose it into slices as asked - marking
                  it Reverse records the judgement without silently overriding the brief.
                - Classify implementation-uncertainty Cynefin at BOTH levels (clear, complicated, complex,
                  or chaotic) - the epic's overall uncertainty and each task's own may differ.
                - sixSigmaMetric and tocConstraintRef exist at BOTH levels: the epic's is an aggregate
                  business metric/bottleneck for the whole epic, each task's is its own narrower technical
                  one - do not just copy the epic's value onto every task.
                - Choose roleTag from: BARCAN-TAG-00 integration/merge hygiene only; BARCAN-TAG-01
                  architecture; BARCAN-TAG-02 backend/API; BARCAN-TAG-03 UI/UX design; BARCAN-TAG-04
                  AI/ML/RAG; BARCAN-TAG-05 build/Docker/CI/deploy; BARCAN-TAG-06 QA/testing (verifies
                  this epic's own new work across the test pyramid, per the mandatory-QA-slice rule
                  above; also used to verify already-existing implementation when that's what a
                  non-client brief specifically asks for); BARCAN-TAG-07 security/auth/access; BARCAN-TAG-08
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
                `%s`, with EXACTLY this shape and no other files changed:
                {"epics": [{"existingEpicId": null, "title": "short English epic title",
                "jtbd": "When [customer]..., I want..., so that...",
                "kanoClass": "Must-Be|Performance|Attractive|Reverse",
                "cynefinDomain": "clear|complicated|complex|chaotic",
                "sixSigmaMetric": "measurable epic-level business metric",
                "tocConstraintRef": "epic-level bottleneck reference", "sourceIndex": 0,
                "requirements": ["R1: concrete requirement", "R2: concrete requirement"],
                "coverageComplete": true,
                "slices": [{"title": "short English title", "roleTag": "BARCAN-TAG-02",
                "jtbd": "When implementing [X] for this epic, I want [Y], so that [epic outcome]...",
                "acceptanceCriteria": "Given..., When..., Then...\\nGiven...",
                "leanValue": "essential|valuable|waste",
                "cynefinDomain": "clear|complicated|complex|chaotic",
                "tocConstraintRef": "short task-level bottleneck reference",
                "sixSigmaMetric": "measurable task-level quality metric", "hasUi": true,
                "requirementRefs": ["R1", "R2"]}]}]}
                Do not write, modify, or delete any other file.

                %d client brief(s) to decompose below (verbatim, may be in any language - read and
                understand each yourself, do not rely on it already being in English). Decompose each one
                separately into its own epic(s); tag every resulting epic with the matching "sourceIndex":
                %s
                """.formatted(existingEpicsPromptContext(projectId), valueChainsFromCorpus(),
                        regulatoryFloorFromCorpus(), planPath, wishlists.size(), briefsSection.toString());
    }

    // Deliberately Gemini-free, same reasoning as the wishlist compiler above: refusal-criteria and
    // methodological-falsification checks used to call Gemini once per active role per project on every
    // falsification cycle. Reuses the same reserved eneikdru account and dispatch plumbing
    // (dispatchCompilerTask is generic) - the falsification cron only fires every few hours, so it shares
    // that account's capacity comfortably instead of contending with real product-implementation dispatch.
    public static final String FALSIFICATION_AUDIT_TASK_TYPE = "falsification_audit";
    // Fixed-path collision fix (2026-07-24): kept only as the fallback for tasks dispatched before this fix
    // (see falsificationAuditReportPath) - the real path is now generated fresh per task and stashed in
    // payload. Low real-world risk for this one specifically (dispatchFalsificationAudit already refuses to
    // start a second audit while one is active per project), fixed anyway for consistency with every other
    // record-file type that shares this exact pattern.
    private static final String FALSIFICATION_AUDIT_REPORT_PATH = ".eneik/falsification-report.json";
    public static final String FALSIFICATION_AUDIT_HIGHEST_PR_KEY = "highestPrNumberAudited";
    public static final String FALSIFICATION_AUDIT_REPORT_PATH_KEY = "auditsReportPath";

    /**
     * @param taskId null only when admission itself refused (duplicate audit already active, or the
     *               compiler role is missing) - a genuine "nothing was created", not a dispatch failure.
     * @param dispatchedToJules true only when this attempt actually reached Jules this cycle. False does
     *               NOT mean failure of the whole mechanism: the task is real and stays `queued`, and the
     *               normal periodic compiler-dispatch cycle will retry it - see dispatchCompilerTask's own
     *               contract.
     */
    public record AuditDispatchResult(UUID taskId, boolean dispatchedToJules) {
        public static final AuditDispatchResult NOT_ADMITTED = new AuditDispatchResult(null, false);
    }

    /**
     * 2026-08-07 fix, live incident: this used to be one @Transactional method holding the project-row lock
     * across dispatchCompilerTask's own network call to Jules (a multi-second HTTP round trip) - confirmed
     * live as the direct cause of a real H2 "Timeout trying to lock table PROJECTS" failure (HikariPool
     * connection marked broken) once the falsification-audit prompt started retrying every ~77s after
     * hitting Jules's own HTTP 400 on an oversized prompt (separately fixed - see GeminiContextService).
     * Split: admitFalsificationAuditTask keeps the short @Transactional span (the check-then-INSERT
     * admission mutex genuinely needs it - see that method's own comment for why), dispatchCompilerTask now
     * runs after that transaction has already committed and released the lock. Also fixes a second, related
     * bug: the old code returned auditTask.getId() unconditionally, so a caller had no way to tell "reached
     * Jules" apart from "created and queued, Jules call itself failed" - FalsificationCycleService was
     * logging "Dispatched" on both. dispatchedToJules is real, checked evidence, not testimony.
     */
    public AuditDispatchResult dispatchFalsificationAudit(ProjectEntity project, String prompt, Integer highestPrNumber, String reportPath) {
        TaskEntity auditTask = self.admitFalsificationAuditTask(project, prompt, highestPrNumber, reportPath);
        if (auditTask == null) {
            return AuditDispatchResult.NOT_ADMITTED;
        }
        boolean dispatched = dispatchCompilerTask(auditTask);
        return new AuditDispatchResult(auditTask.getId(), dispatched);
    }

    // @Transactional so the project-row lock below is held for the whole check-then-create span, not just
    // for the single lockProjectForUpdate query - a check-then-INSERT race (this method's ID uniqueness
    // depends on no OTHER concurrent call also passing the auditAlreadyActive check before either commits)
    // cannot be closed by a per-row compare-and-swap the way a status UPDATE can, since the row being
    // raced over doesn't exist yet at check time. A second concurrent caller now blocks on the lock and
    // correctly observes "already active" once the first commits, instead of both creating a duplicate
    // audit task. Public + called via `self` (not `this`) so this actually goes through the Spring proxy -
    // see the `self` field's own comment.
    @Transactional
    public TaskEntity admitFalsificationAuditTask(ProjectEntity project, String prompt, Integer highestPrNumber, String reportPath) {
        operationalPolicyService.requireAllowed(project.getId(), OperationalAction.RUN_PROJECT_AUDIT_PIPELINE);
        projectRepository.lockProjectForUpdate(project.getId());
        boolean auditAlreadyActive = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()).stream()
                .filter(this::isFalsificationAuditTask)
                .anyMatch(task -> task.getStatus() == TaskStatus.queued
                        || task.getStatus() == TaskStatus.claimed
                        || task.getStatus() == TaskStatus.in_progress
                        || task.getStatus() == TaskStatus.pending_review
                        || task.getStatus() == TaskStatus.review
                        || task.getStatus() == TaskStatus.blocked);
        if (auditAlreadyActive) {
            log.info("Poka-yoke: project {} already has an active falsification audit; duplicate dispatch skipped",
                    project.getId());
            return null;
        }

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
        if (reportPath != null && !reportPath.isBlank()) {
            payload.put(FALSIFICATION_AUDIT_REPORT_PATH_KEY, reportPath);
        }
        auditTask.setPayload(payload);

        return taskRepository.save(auditTask);
    }

    public boolean isFalsificationAuditTask(TaskEntity task) {
        return task.getPayload() != null
                && FALSIFICATION_AUDIT_TASK_TYPE.equals(task.getPayload().path(WISHLIST_COMPILER_PAYLOAD_KEY).asText(null));
    }

    /** Falls back to the old shared constant for tasks dispatched before this fix. */
    public String falsificationAuditReportPath(TaskEntity task) {
        if (task.getPayload() == null) {
            return FALSIFICATION_AUDIT_REPORT_PATH;
        }
        String raw = task.getPayload().path(FALSIFICATION_AUDIT_REPORT_PATH_KEY).asText(null);
        return raw == null || raw.isBlank() ? FALSIFICATION_AUDIT_REPORT_PATH : raw;
    }

    // Philosophical falsification track (2026-07-25) - independent task type, independent admission mutex
    // from FALSIFICATION_AUDIT_TASK_TYPE above, same reasoning as dispatchFalsificationAudit's own
    // check-then-INSERT lock comment. Deliberately its own constant rather than reusing the formal audit's,
    // so an open philosophical audit never blocks (or is blocked by) an open formal audit.
    public static final String PHILOSOPHICAL_AUDIT_TASK_TYPE = "philosophical_audit";
    public static final String PHILOSOPHICAL_AUDIT_REPORT_PATH_KEY = "philosophicalAuditReportPath";

    // 2026-08-08 fix (live dispute-driven audit): this was the fifth confirmed instance of the same
    // bug already fixed for the formal falsification track (see admitFalsificationAuditTask's own comment,
    // 2026-08-07 - "held the project-row lock across dispatchCompilerTask's own network call to Jules...
    // confirmed live as the direct cause of a real H2 'Timeout trying to lock table PROJECTS' failure").
    // The philosophical track is a near-identical twin method that never received the same split. Same
    // fix: admitPhilosophicalFalsificationAuditTask keeps the short @Transactional span (the project lock
    // + check-then-INSERT admission mutex), dispatchCompilerTask now runs after that transaction has
    // already committed and released the lock.
    public UUID dispatchPhilosophicalAudit(ProjectEntity project, String prompt, String reportPath) {
        TaskEntity auditTask = self.admitPhilosophicalFalsificationAuditTask(project, prompt, reportPath);
        if (auditTask == null) {
            return null;
        }
        dispatchCompilerTask(auditTask);
        return auditTask.getId();
    }

    // @Transactional so the project-row lock below is held for the whole check-then-create span - same
    // check-then-INSERT race reasoning as admitFalsificationAuditTask's own comment. Public + called via
    // `self` (not `this`) so this actually goes through the Spring proxy - see the `self` field's own
    // comment.
    @Transactional
    public TaskEntity admitPhilosophicalFalsificationAuditTask(ProjectEntity project, String prompt, String reportPath) {
        projectRepository.lockProjectForUpdate(project.getId());
        boolean auditAlreadyActive = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()).stream()
                .filter(this::isPhilosophicalAuditTask)
                .anyMatch(task -> task.getStatus() == TaskStatus.queued
                        || task.getStatus() == TaskStatus.claimed
                        || task.getStatus() == TaskStatus.in_progress
                        || task.getStatus() == TaskStatus.pending_review
                        || task.getStatus() == TaskStatus.review
                        || task.getStatus() == TaskStatus.blocked);
        if (auditAlreadyActive) {
            log.info("Poka-yoke: project {} already has an active philosophical falsification audit; duplicate dispatch skipped",
                    project.getId());
            return null;
        }

        RoleEntity compilerRole = roleRepository.findById(ORCHESTRATOR_ROLE).orElse(null);
        if (compilerRole == null) {
            log.error("Cannot dispatch philosophical falsification audit for project {}: role {} not found",
                    project.getId(), ORCHESTRATOR_ROLE);
            return null;
        }

        TaskEntity auditTask = new TaskEntity();
        auditTask.setProject(project);
        auditTask.setRole(compilerRole);
        auditTask.setTitle("Philosophical falsification: product critique (" + shortId(UUID.randomUUID()) + ")");
        auditTask.setDescription(prompt);
        auditTask.setStatus(TaskStatus.queued);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put(WISHLIST_COMPILER_PAYLOAD_KEY, PHILOSOPHICAL_AUDIT_TASK_TYPE);
        if (reportPath != null && !reportPath.isBlank()) {
            payload.put(PHILOSOPHICAL_AUDIT_REPORT_PATH_KEY, reportPath);
        }
        auditTask.setPayload(payload);

        return taskRepository.save(auditTask);
    }

    public boolean isPhilosophicalAuditTask(TaskEntity task) {
        return task.getPayload() != null
                && PHILOSOPHICAL_AUDIT_TASK_TYPE.equals(task.getPayload().path(WISHLIST_COMPILER_PAYLOAD_KEY).asText(null));
    }

    // Used by FalsificationCycleService to rotate which single role's charter+philosopher-pattern content
    // gets embedded in the next audit dispatch (see the class javadoc there for why one request can no
    // longer cover every active role at once).
    public long countPastPhilosophicalAuditTasks(UUID projectId) {
        return taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .filter(this::isPhilosophicalAuditTask)
                .count();
    }

    public String philosophicalAuditReportPath(TaskEntity task) {
        if (task.getPayload() == null) {
            return null;
        }
        String raw = task.getPayload().path(PHILOSOPHICAL_AUDIT_REPORT_PATH_KEY).asText(null);
        return raw == null || raw.isBlank() ? null : raw;
    }

    // 2026-08-03: multi-turn philosophical audit (see FalsificationCycleService.
    // executePhilosophicalCycleForProject's class javadoc for the live incident this replaces - 13
    // fully isolated one-role sessions with no discussion between them). One continuous Jules session
    // per project, one role-batch (3 roles) per follow-up turn.
    //
    // 2026-08-09 (live incident, operator-flagged - "проверь что все философы высказались"): this used to
    // track "covered" role tags as an append-only marker on the carrier task's payload, written the moment a
    // batch was SENT (see PHILOSOPHICAL_AUDIT_COVERED_ROLES_KEY, removed) - not once Jules's answer was
    // actually verified to exist. Confirmed live: BARCAN-TAG-12 was asked about at 09:00:14 and this
    // bookkeeping already called it "covered" in the same call, before Jules had any real chance to answer; a
    // stale-status poll edge 9 seconds later found the marker already at 13/13 and closed the whole
    // discussion, merging a report that never actually contained a BARCAN-TAG-12 critique. There is no
    // separate bookkeeping anymore - FalsificationCycleService.continuePhilosophicalDiscussion and
    // JulesDispatchService.completePersistentPhilosophicalAuditCycle both derive "covered" directly by
    // parsing the report file's real current content (the same file, the same parse shape) - a role only
    // counts as covered once its critique genuinely exists on the branch, which by construction can never
    // race ahead of the truth.

    /**
     * Persistent-worker equivalent of dispatchPhilosophicalAudit: reuses an existing idle worker's
     * session (follow-up message, same branch/PR/report file) when one exists, otherwise creates a
     * fresh carrier task/session exactly like the one-shot path used to unconditionally. Unlike
     * dispatchToCompilerPersistentWorker (confirmed to have no row lock at all), this acquires
     * lockProjectForUpdate up front, same as the one-shot dispatchPhilosophicalAudit already does -
     * new code inherits that protection rather than reproducing the compiler path's gap.
     *
     * @param roleBatch the role tags this turn's message covers (already excludes previously-covered
     *                  roles - the caller, FalsificationCycleService, computes this)
     * @param message   the full turn message: role charters/philosopher content for a fresh dispatch,
     *                  or the follow-up text (next roles + prior-turns digest, or the final synthesis
     *                  instruction) for an existing worker
     * @param reportPath used only when creating a fresh worker (first turn)
     * @return true if the turn was sent/dispatched, false if skipped (busy) or failed
     */
    private record PhilosophicalWorkerDispatchDecision(String action, PersistentWorkerSessionEntity worker,
                                                        JulesSessionEntity session) {
        private static final String MESSAGE_EXISTING = "MESSAGE_EXISTING";
        private static final String DEFER = "DEFER";
        private static final String CREATE_NEW = "CREATE_NEW";
    }

    /**
     * 2026-08-07 fix, found during a deliberate sweep for the SAME bug class already fixed this morning
     * (dispatchFalsificationAudit/admitFalsificationAuditTask): this method used to be one @Transactional
     * block holding the project-row lock across TWO real network calls to Jules - sendFollowUpMessage and
     * (via createPhilosophicalAuditPersistentWorker) dispatchCompilerTask. This is the philosophical
     * falsification track specifically, which fires on every discussion turn (far more often than the
     * code-defect track), so this was very plausibly a bigger real contributor to lock contention than the
     * instance already fixed. Same split: admitPhilosophicalAuditWorkerDispatch keeps the short
     * @Transactional span (lock + read + the rotation-retire write, no network calls), this outer method
     * makes the actual network call only after that transaction has already committed and released the lock.
     */
    public boolean dispatchToPhilosophicalAuditPersistentWorker(ProjectEntity project,
            java.util.List<String> roleBatch, String message, String reportPath) {
        PhilosophicalWorkerDispatchDecision decision = self.admitPhilosophicalAuditWorkerDispatch(project);
        switch (decision.action()) {
            case PhilosophicalWorkerDispatchDecision.MESSAGE_EXISTING -> {
                PersistentWorkerSessionEntity worker = decision.worker();
                if (decision.session() != null && julesDispatchService.sendFollowUpMessage(decision.session(), message)) {
                    // currentBatchIds only needs to be non-empty to mark "in flight" for isIdleAndFresh's
                    // generic busy check - which roles are actually covered is derived by parsing the report
                    // file's real content (see this class's own comment above), never bookkept here at send
                    // time - a role only counts once Jules has genuinely answered it.
                    persistentWorkerSessionService.recordBatchSent(worker, java.util.List.of(UUID.randomUUID()));
                    log.info("Sent philosophical-audit follow-up turn (roles {}) to persistent worker {} (cycle {})",
                            roleBatch, worker.getId(), worker.getCycleCount());
                    return true;
                }
                log.warn("Persistent philosophical-audit worker {} exists but could not be messaged this cycle", worker.getId());
                return false;
            }
            case PhilosophicalWorkerDispatchDecision.DEFER -> {
                log.info("Persistent philosophical-audit worker {} is still busy; role batch {} deferred to next cycle",
                        decision.worker().getId(), roleBatch);
                return false;
            }
            default -> {
                return createPhilosophicalAuditPersistentWorker(project, roleBatch, message, reportPath);
            }
        }
    }

    // Public + called via `self` (not `this`) so this actually goes through the Spring proxy - see the
    // `self` field's own comment. No network calls in here, deliberately - only the lock-requiring
    // read-and-decide span, including the rotation-retire write (a plain DB update, safe under the lock).
    @Transactional
    public PhilosophicalWorkerDispatchDecision admitPhilosophicalAuditWorkerDispatch(ProjectEntity project) {
        projectRepository.lockProjectForUpdate(project.getId());
        Optional<PersistentWorkerSessionEntity> existingOpt =
                persistentWorkerSessionService.findActiveWorker(project.getId(), PersistentWorkerPurpose.PHILOSOPHICAL_AUDIT);
        if (existingOpt.isEmpty()) {
            return new PhilosophicalWorkerDispatchDecision(PhilosophicalWorkerDispatchDecision.CREATE_NEW, null, null);
        }
        PersistentWorkerSessionEntity worker = existingOpt.get();
        if (persistentWorkerSessionService.needsRotation(worker)) {
            // A philosophical-audit cycle has a real finite end (see completePersistentPhilosophicalAuditCycle) -
            // reaching the generic rotation cap without finishing is unexpected but handled the same
            // defensive way the other two purposes handle it: retire and start clean.
            persistentWorkerSessionService.retire(worker, "cycle/age cap reached before discussion finished");
            return new PhilosophicalWorkerDispatchDecision(PhilosophicalWorkerDispatchDecision.CREATE_NEW, null, null);
        }
        if (persistentWorkerSessionService.isIdleAndFresh(worker)) {
            JulesSessionEntity session = worker.getCurrentJulesSessionId() != null
                    ? julesSessionRepository.findById(worker.getCurrentJulesSessionId()).orElse(null)
                    : null;
            return new PhilosophicalWorkerDispatchDecision(PhilosophicalWorkerDispatchDecision.MESSAGE_EXISTING, worker, session);
        }
        return new PhilosophicalWorkerDispatchDecision(PhilosophicalWorkerDispatchDecision.DEFER, worker, null);
    }

    private boolean createPhilosophicalAuditPersistentWorker(ProjectEntity project,
            java.util.List<String> roleBatch, String prompt, String reportPath) {
        RoleEntity compilerRole = roleRepository.findById(ORCHESTRATOR_ROLE).orElse(null);
        if (compilerRole == null) {
            log.error("Cannot create persistent philosophical-audit worker for project {}: role {} not found",
                    project.getId(), ORCHESTRATOR_ROLE);
            return false;
        }

        TaskEntity carrierTask = new TaskEntity();
        carrierTask.setProject(project);
        carrierTask.setRole(compilerRole);
        carrierTask.setTitle("Persistent philosophical audit worker (" + shortId(project.getId()) + ")");
        carrierTask.setDescription(prompt);
        carrierTask.setStatus(TaskStatus.queued);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put(WISHLIST_COMPILER_PAYLOAD_KEY, PHILOSOPHICAL_AUDIT_TASK_TYPE);
        payload.put(PERSISTENT_WORKER_CARRIER_MARKER_KEY, true);
        if (reportPath != null && !reportPath.isBlank()) {
            payload.put(PHILOSOPHICAL_AUDIT_REPORT_PATH_KEY, reportPath);
        }
        carrierTask.setPayload(payload);
        carrierTask = taskRepository.save(carrierTask);

        dispatchCompilerTask(carrierTask);

        TaskEntity refreshed = taskRepository.findById(carrierTask.getId()).orElse(carrierTask);
        if (refreshed.getJulesSessionName() == null) {
            // No account capacity this cycle - stays queued for the normal retry sweep, same as the
            // compiler worker's own lazy-registration fallback (completePersistentWorkerCycle registers
            // the row on first pr_opened using this task's own payload as cycle 1).
            log.warn("Persistent philosophical-audit worker carrier task {} could not be dispatched this cycle; will retry via the normal queued-task sweep",
                    carrierTask.getId());
            return false;
        }
        List<JulesSessionEntity> sessions = julesSessionRepository.findByTaskId(carrierTask.getId());
        JulesSessionEntity newSession = sessions.stream()
                .max(java.util.Comparator.comparing(JulesSessionEntity::getCreatedAt))
                .orElse(null);
        if (newSession == null) {
            log.error("Persistent philosophical-audit worker carrier task {} dispatched but no JulesSessionEntity found", carrierTask.getId());
            return false;
        }
        persistentWorkerSessionService.registerFreshWorker(project.getId(), PersistentWorkerPurpose.PHILOSOPHICAL_AUDIT,
                carrierTask.getId(), newSession.getId(), java.util.List.of(UUID.randomUUID()));
        log.info("Created persistent philosophical-audit worker for project {}: carrier task {}, session {}, first roles {}",
                project.getId(), carrierTask.getId(), newSession.getId(), roleBatch);
        return true;
    }

    /**
     * True for implementation work compiled from a non-client iteration source. Public (not just used
     * internally by dispatchQueuedTasks's build-phase hold) so TaskWaitTimeService can classify a queued
     * task's wait-reason bucket using the exact same predicate the dispatch loop itself evaluates.
     */
    public boolean isSelfGeneratedWork(TaskEntity task) {
        if (task.getSourceWishlistId() == null) {
            return false;
        }
        return wishlistRepository.findById(task.getSourceWishlistId())
                .map(w -> w.getSource() != WishlistSource.client && !isEnvironmentBootstrapWishlist(w))
                .orElse(false);
    }

    private boolean isOutOfCycleGeneratedWork(TaskEntity task) {
        if (task.getSourceWishlistId() == null) {
            return false;
        }
        return wishlistRepository.findById(task.getSourceWishlistId())
                .map(w -> !isEnvironmentBootstrapWishlist(w)
                        && (w.getSource() == WishlistSource.role
                        || w.getSource() == WishlistSource.role_mismatch_followup))
                .orElse(false);
    }

    private void quarantineOutOfCycleGeneratedWork(TaskEntity task) {
        String reason = "Poka-yoke: out-of-cycle generated work is quarantined; product improvements "
                + "must enter through the bounded self_falsification iteration";
        task.setStatus(TaskStatus.failed);
        task.setJulesDispatchStatus(reason);
        taskRepository.save(task);
        wishlistRepository.findById(task.getSourceWishlistId()).ifPresent(wishlist -> {
            if (wishlist.getStatus() != WishlistStatus.dismissed) {
                wishlist.setStatus(WishlistStatus.dismissed);
                wishlistRepository.save(wishlist);
            }
        });
        log.warn("ProjectFlowService: quarantined out-of-cycle generated task {} from wishlist {}; "
                        + "no Jules session, replacement task, or new wishlist was created",
                task.getId(), task.getSourceWishlistId());
    }

    public Integer falsificationAuditHighestPrNumber(TaskEntity task) {
        if (task.getPayload() == null || !task.getPayload().hasNonNull(FALSIFICATION_AUDIT_HIGHEST_PR_KEY)) {
            return null;
        }
        return task.getPayload().path(FALSIFICATION_AUDIT_HIGHEST_PR_KEY).asInt();
    }

    // Coverage audit: operator directive (2026-07-23) - the original design dispatched one audit task per
    // EPIC, immediately after decomposition, comparing the brief against the (not-yet-built) planned task
    // list. Confirmed live (test-thirty-third) that this fires N audit tasks per wishlist (one per epic,
    // observed 3 for a single brief) and burns worker slots before any real code exists to actually check
    // against - the operator called this "громоздко с костылями" (clunky, hacky) and asked for a redesign:
    // exactly ONE audit task per WISHLIST (not per epic), dispatched only once ALL of that wishlist's
    // planned tasks are genuinely merged, comparing the brief against the REAL code now on main - not a
    // text description of a plan. Still deliberately NOT an extension of FalsificationCycleService: that
    // gate validates code against what was *claimed* about it; this validates the brief against what was
    // *actually built*, a different question, run once per wishlist rather than per-project. Idempotency:
    // checkAndDispatchCoverageAudits (called every orchestration tick, see ContinuousOrchestrationService)
    // never dispatches twice for the same wishlist - see isCoverageAuditTask/coverageAuditTargetWishlistId.
    public static final String COVERAGE_AUDIT_TASK_TYPE = "coverage_audit";
    // Fixed-path collision fix (2026-07-24): kept only as the fallback for tasks dispatched before this fix
    // (see coverageAuditReportPath) - the real path is now generated fresh per task and stashed in payload.
    private static final String COVERAGE_AUDIT_REPORT_PATH = ".eneik/coverage-audit.json";
    public static final String COVERAGE_AUDIT_WISHLIST_ID_KEY = "auditsWishlistId";
    public static final String COVERAGE_AUDIT_REPORT_PATH_KEY = "auditsReportPath";

    // Watermark, same idiom FalsificationCycleService already uses (FalsificationRunEntity.
    // highestPrNumberAudited): the highest merged PR number that existed on main at the moment THIS audit
    // was dispatched. Lets a wishlist be re-audited later if real new code has merged since - a coverage
    // audit is otherwise a one-shot-forever check (see isCoverageAuditTask/coverageAuditTargetWishlistId),
    // which was correct for "don't re-check the same shipped code repeatedly" but didn't anticipate a
    // wishlist being reported fully-merged while the actual code was still incomplete (confirmed live,
    // test-thirty-fifth 2026-07-23: a stacked-branch merge race left real backend code unmerged for over
    // an hour after the task tracker already showed 100%; the first audit ran against that incomplete
    // main and its 6 gap findings were mostly stale by the time the real code landed).
    public static final String COVERAGE_AUDIT_HIGHEST_PR_KEY = "auditsHighestPrNumber";

    // Lean-waste fix (2026-07-23, generalized 2026-07-24 from API-contract-only to every EmsFlowStage
    // "spec" stage - see EmsFlowStage.isSpecStage): marks a task that started before its spec-stage
    // dependency (decision/architecture/api-contract/compliance) actually merged (see the dependsOn gate
    // in dispatchQueuedTasks and ClientDeliverableReadinessService.isSpecDependencyPrOpenButUnmerged) -
    // read by AutoMergeService.notifyEarlyUnblockedDependents once that spec task finally merges, so the
    // dependent's still-active Jules session gets an FYI to reconcile against the finalized artifact.
    public static final String EARLY_UNBLOCK_SPEC_KEY = "earlyUnblockedSpecTask";

    private record DueCoverageAudit(ProjectEntity project, WishlistEntity wishlist, int highestMergedPrNumber) {}

    /**
     * 2026-08-07 fix, found during a deliberate sweep for the SAME bug class already fixed this morning
     * (dispatchFalsificationAudit/admitFalsificationAuditTask): this used to hold the project-row lock for
     * the whole method, including dispatchCoverageAuditForCompletedWishlist's real network call to Jules -
     * for potentially every due wishlist in the loop, one lock span covering N network round trips. Split:
     * admitDueCoverageAudits (below) keeps the short @Transactional span (the check-then-INSERT admission
     * mutex genuinely needs it - see its own comment for why), this outer method dispatches each due
     * wishlist only after that transaction has already committed and released the lock.
     */
    public void checkAndDispatchCoverageAudits(UUID projectId) {
        List<DueCoverageAudit> due = self.admitDueCoverageAudits(projectId);
        for (DueCoverageAudit d : due) {
            dispatchCoverageAuditForCompletedWishlist(d.project(), d.wishlist(), d.highestMergedPrNumber());
        }
    }

    // Same admission-mutex reasoning as dispatchFalsificationAudit: hasActiveAudit (below, per wishlist) is
    // a check-then-INSERT race across CONCURRENT invocations of this method for the same project (e.g. two
    // overlapping orchestration ticks) - a per-row CAS cannot guard a row that doesn't exist yet at check
    // time. Locking the project row for the whole read-and-decide span serializes concurrent calls so the
    // second one correctly re-reads "already active" after the first commits, instead of both dispatching a
    // duplicate audit for the same wishlist (the exact PR#56/#57 duplicate-implementation incident class,
    // 2026-07-24). Public + called via `self` (not `this`) so this actually goes through the Spring proxy -
    // see the `self` field's own comment. No network calls in here, deliberately.
    @Transactional
    public List<DueCoverageAudit> admitDueCoverageAudits(UUID projectId) {
        operationalPolicyService.requireAllowed(projectId, OperationalAction.CHECK_COVERAGE_AUDITS);
        projectRepository.lockProjectForUpdate(projectId);
        List<WishlistEntity> clientWishlists = wishlistRepository.findByProjectId(projectId).stream()
                .filter(w -> w.getSource() == WishlistSource.client)
                .filter(w -> w.getCompiledByRole() == null)
                .filter(w -> w.getStatus() == WishlistStatus.converted_to_task)
                .toList();
        if (clientWishlists.isEmpty()) {
            return List.of();
        }
        List<TaskEntity> existingAuditTasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .filter(this::isCoverageAuditTask)
                .toList();

        ProjectEntity project = requireProject(projectId);
        List<DueCoverageAudit> due = new java.util.ArrayList<>();

        for (WishlistEntity wishlist : clientWishlists) {
            // 2026-07-26 operator directive ("привязать к целому вишлисту"): scoped to THIS wishlist's own
            // features, not highestMergedPrNumber(project) (any product-code merge anywhere in the project).
            // The project-wide version already survived one self-triggering-loop fix (2026-07-24, see the
            // javadoc below) but still re-fired this wishlist's audit every time an UNRELATED wishlist's own
            // work merged - itself a slower version of the same tail-chasing bug, confirmed live tonight
            // (test-thirty-seventh: continuous merges elsewhere kept resetting decompositionComplete/
            // featureRatio, so philosophical falsification's readiness bar was never stably crossed).
            Integer currentHighestMergedPr = highestMergedPrNumberForWishlist(project, wishlist);
            List<TaskEntity> auditsForThisWishlist = existingAuditTasks.stream()
                    .filter(t -> wishlist.getId().equals(coverageAuditTargetWishlistId(t)))
                    .toList();
            boolean hasActiveAudit = auditsForThisWishlist.stream().anyMatch(t -> t.getStatus() != TaskStatus.done
                    && t.getStatus() != TaskStatus.failed && t.getStatus() != TaskStatus.blocked);
            if (hasActiveAudit) {
                // Duplicate protection: never dispatch a second audit while one is still in flight for
                // this wishlist, regardless of how stale the watermark looks.
                continue;
            }
            Integer lastAuditedPr = auditsForThisWishlist.stream()
                    .map(this::coverageAuditHighestPrNumber)
                    .filter(java.util.Objects::nonNull)
                    .max(Integer::compareTo)
                    .orElse(null);
            boolean alreadyAuditedAndStillCurrent = !auditsForThisWishlist.isEmpty()
                    && lastAuditedPr != null && currentHighestMergedPr != null
                    && currentHighestMergedPr <= lastAuditedPr;
            if (alreadyAuditedAndStillCurrent) {
                continue;
            }
            if (!auditsForThisWishlist.isEmpty() && (lastAuditedPr == null || currentHighestMergedPr == null)) {
                // No watermark on the previous audit (predates this fix) or no GitHub data available right
                // now to compare against - fail closed exactly like before: don't re-audit on a guess.
                continue;
            }
            // Minimum cooldown, independent of the watermark above (existingAuditTasks is already sorted
            // createdAt DESC, so auditsForThisWishlist.get(0) is the most recent dispatch for this wishlist).
            if (!auditsForThisWishlist.isEmpty()) {
                Instant lastDispatchedAt = auditsForThisWishlist.get(0).getCreatedAt();
                Instant cooldownUntil = lastDispatchedAt.plus(Duration.ofHours(coverageAuditMinIntervalHours));
                if (Instant.now().isBefore(cooldownUntil)) {
                    log.debug("Coverage audit for wishlist {} is watermark-stale but still within the {}h cooldown "
                            + "(last dispatched {}) - deferring to a later tick", wishlist.getId(),
                            coverageAuditMinIntervalHours, lastDispatchedAt);
                    continue;
                }
            }

            ClientDeliverableReadinessService.Readiness readiness =
                    readinessService.computeForProject(projectId, wishlist.getId());
            boolean fullyMerged = readiness.decompositionComplete()
                    && readiness.totalDeliverables() > 0
                    && readiness.mergedDeliverables() >= readiness.totalDeliverables();
            if (!fullyMerged) {
                continue;
            }
            if (!auditsForThisWishlist.isEmpty()) {
                log.info("Coverage audit for wishlist {} is stale (previously audited up to PR #{}, now #{} merged) - dispatching a fresh audit",
                        wishlist.getId(), lastAuditedPr, currentHighestMergedPr);
            }
            if (currentHighestMergedPr == null) {
                currentHighestMergedPr = 0;
            }
            due.add(new DueCoverageAudit(project, wishlist, currentHighestMergedPr));
        }
        return due;
    }

    /**
     * Live incident, 2026-07-24 (operator: "ковер важнее сейчас. он сломан?" - confirmed yes): coverage
     * audits were chasing their own tail. This watermark is "has anything new merged for this project since
     * the last audit" - but it counted EVERY merged PR project-wide, including the audit's own record-only
     * report PR (`.eneik/records/coverage-audit-*.json`, never product code). Each audit's own merge bumped
     * the watermark, immediately qualifying as "stale" and re-dispatching another audit, forever - confirmed
     * live: PR#52 (audit 1's report) triggered audit 2, whose own PR#53 triggered audit 3, with no end
     * condition. Fixed by excluding PRs whose owning task carries the "taskType" system-task payload marker
     * (coverage_audit, wishlist_compiler, pr_review_fallback, design_review, falsification_audit - same
     * signal EmsMetricsService.isSystemMetaTask already uses elsewhere) - only a REAL product-code merge
     * should ever re-trigger a fresh audit.
     */
    private Integer highestMergedPrNumber(ProjectEntity project) {
        var snapshot = gitHubPullRequestService.pullRequestSnapshot(project);
        if (!snapshot.available()) {
            return null;
        }
        List<JulesSessionEntity> projectSessions = julesSessionRepository.findAll().stream()
                .filter(s -> s.getTaskId() != null)
                .filter(s -> {
                    TaskEntity t = taskRepository.findById(s.getTaskId()).orElse(null);
                    return t != null && t.getProject() != null && project.getId().equals(t.getProject().getId());
                })
                .toList();
        return snapshot.closed().stream()
                .filter(com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest::merged)
                .filter(pr -> !isSystemRecordPr(pr, projectSessions))
                .map(com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest::number)
                .max(Integer::compareTo)
                .orElse(null);
    }

    /**
     * 2026-07-26 operator directive ("привязать к целому вишлисту"): same watermark as
     * {@link #highestMergedPrNumber(ProjectEntity)}, but scoped to sessions whose task belongs to THIS
     * wishlist's own features (via {@link ClientDeliverableReadinessService#listTasksForRootWishlist}),
     * not any task anywhere in the project. The project-wide version still let one client wishlist's audit
     * be perpetually re-triggered by merges belonging to a completely different wishlist's own work, which
     * kept that wishlist's readiness/decompositionComplete from ever stabilizing long enough for the
     * philosophical falsification readiness gate to be crossed.
     */
    private Integer highestMergedPrNumberForWishlist(ProjectEntity project, WishlistEntity wishlist) {
        var snapshot = gitHubPullRequestService.pullRequestSnapshot(project);
        if (!snapshot.available()) {
            return null;
        }
        Set<UUID> wishlistTaskIds = readinessService.listTasksForRootWishlist(project.getId(), wishlist.getId())
                .stream()
                .map(TaskEntity::getId)
                .collect(java.util.stream.Collectors.toSet());
        List<JulesSessionEntity> wishlistSessions = julesSessionRepository.findAll().stream()
                .filter(s -> s.getTaskId() != null && wishlistTaskIds.contains(s.getTaskId()))
                .toList();
        return snapshot.closed().stream()
                .filter(com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest::merged)
                .filter(pr -> !isSystemRecordPr(pr, wishlistSessions))
                .map(com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest::number)
                .max(Integer::compareTo)
                .orElse(null);
    }

    private boolean isSystemRecordPr(com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest pr,
            List<JulesSessionEntity> candidateSessions) {
        JulesSessionEntity delimitedMatch = null;
        JulesSessionEntity substringMatch = null;
        for (JulesSessionEntity session : candidateSessions) {
            if (!com.eneik.production.services.github.GitHubPullRequestService.matchesSessionToken(pr, session.getExternalSessionId())) {
                continue;
            }
            String token = session.getExternalSessionId() == null ? "" : session.getExternalSessionId();
            if (token.startsWith("sessions/")) {
                token = token.substring("sessions/".length());
            }
            boolean delimited = !token.isBlank() && java.util.regex.Pattern
                    .compile("(?<![A-Za-z0-9])" + java.util.regex.Pattern.quote(token) + "(?![A-Za-z0-9])")
                    .matcher(pr.headRef() == null ? "" : pr.headRef()).find();
            if (delimited) {
                delimitedMatch = session;
                break;
            }
            if (substringMatch == null) {
                substringMatch = session;
            }
        }
        JulesSessionEntity matched = delimitedMatch != null ? delimitedMatch : substringMatch;
        if (matched == null) {
            return false;
        }
        TaskEntity task = taskRepository.findById(matched.getTaskId()).orElse(null);
        return task != null && task.getPayload() != null && task.getPayload().has(WISHLIST_COMPILER_PAYLOAD_KEY);
    }

    private void dispatchCoverageAuditForCompletedWishlist(ProjectEntity project, WishlistEntity originalWishlist, Integer highestMergedPrNumber) {
        RoleEntity compilerRole = roleRepository.findById(ORCHESTRATOR_ROLE).orElse(null);
        if (compilerRole == null) {
            log.error("Cannot dispatch coverage audit for wishlist {}: role {} not found", originalWishlist.getId(), ORCHESTRATOR_ROLE);
            return;
        }
        String reportPath = ".eneik/records/coverage-audit-" + UUID.randomUUID() + ".json";

        String prompt = """
                You are auditing SHIPPED product code for completeness against the original client brief.
                Every planned task for this brief has already been merged to main - do NOT write or change
                any code, do not run builds or tests, this task only produces an audit report.

                Check out `main` and read the ACTUAL code now present in the repository (not a task list,
                not a plan - the real files). Compare it against the ORIGINAL CLIENT BRIEF below. Find two
                kinds of gap:

                1. COVERAGE gaps: something the brief actually asks for (read it yourself, in whatever
                   language or phrasing it uses - do not rely on keyword matching) that the real code does
                   not implement.
                2. DOMAIN-STANDARD gaps: functionality that isn't mentioned in the brief at all, but that
                   any competent engineer would expect as a standard, well-known requirement for this
                   category of product given what the brief DOES ask for (e.g. a brief that implies
                   "accounts" or "authenticated users" needs an actual login/session mechanism even if it
                   never says the word "auth"; a public list endpoint usually needs pagination; etc.) -
                   only flag things clearly and concretely implied by the brief's own domain, never
                   speculative feature ideas.

                Be conservative: only report a gap you are genuinely confident about, and only if you
                verified it's genuinely missing by reading the real code, not by assumption. Do NOT flag
                missing tests, missing documentation, missing CI/CD, or generic "nice to have" polish -
                those are not coverage gaps.

                Deliverable: create a new branch and open a PR that contains ONLY one file, `%s`
                (this EXACT path - it is unique to this task, do not use any other path), with EXACTLY
                this shape and no other files changed:
                {"gaps": [
                  {"title": "short English title", "roleTag": "BARCAN-TAG-02", "jtbd": "When..., I want..., so that...", "acceptanceCriteria": "Given..., When..., Then...", "reason": "short explanation: literal brief requirement OR domain-standard expectation"}
                ]}
                If there are no real gaps, use an empty array: {"gaps": []}. Do not write, modify, or
                delete any other file.

                ORIGINAL CLIENT BRIEF (verbatim, may be in any language):
                %s
                """.formatted(reportPath, originalWishlist.getContent());

        TaskEntity auditTask = new TaskEntity();
        auditTask.setProject(project);
        auditTask.setRole(compilerRole);
        auditTask.setTitle("Coverage audit: brief vs shipped code (" + shortId(originalWishlist.getId()) + ")");
        auditTask.setDescription(prompt);
        auditTask.setStatus(TaskStatus.queued);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put(WISHLIST_COMPILER_PAYLOAD_KEY, COVERAGE_AUDIT_TASK_TYPE);
        payload.put(COVERAGE_AUDIT_WISHLIST_ID_KEY, originalWishlist.getId().toString());
        payload.put(COVERAGE_AUDIT_REPORT_PATH_KEY, reportPath);
        if (highestMergedPrNumber != null) {
            payload.put(COVERAGE_AUDIT_HIGHEST_PR_KEY, highestMergedPrNumber);
        }
        auditTask.setPayload(payload);

        auditTask = taskRepository.save(auditTask);
        dispatchToGeneralPool(auditTask);
        log.info("Dispatched coverage audit task {} for fully-merged client wishlist {} (one task per wishlist, checked against real code on main, watermark PR #{})",
                auditTask.getId(), originalWishlist.getId(), highestMergedPrNumber);
    }

    public Integer coverageAuditHighestPrNumber(TaskEntity task) {
        if (task.getPayload() == null || !task.getPayload().has(COVERAGE_AUDIT_HIGHEST_PR_KEY)) {
            return null;
        }
        return task.getPayload().path(COVERAGE_AUDIT_HIGHEST_PR_KEY).asInt();
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

    /** Falls back to the old shared constant for tasks dispatched before this fix. */
    public String coverageAuditReportPath(TaskEntity task) {
        if (task.getPayload() == null) {
            return COVERAGE_AUDIT_REPORT_PATH;
        }
        String raw = task.getPayload().path(COVERAGE_AUDIT_REPORT_PATH_KEY).asText(null);
        return raw == null || raw.isBlank() ? COVERAGE_AUDIT_REPORT_PATH : raw;
    }


    // Fallback reviewer, used only when Gemini's PR review reports VERIFICATION_SERVICE_UNAVAILABLE - so
    // a real outage (or a permanently depleted quota) never leaves an implementer PR stuck unreviewed
    // forever. Dispatches a standalone Jules eneikdru session (same reserved account and generic dispatch
    // plumbing as the compiler/audit above) that reads the real diff and writes a soft verdict: it only
    // blocks for a small enumerated set of critical problems, everything else is approved with concerns
    // recorded as follow-up wishlist items - so work never stalls waiting on a reviewer's opinion, it only
    // ever accumulates improvement backlog.
    public static final String PR_REVIEW_FALLBACK_TASK_TYPE = "pr_review_fallback";
    // Fixed-path collision fix (2026-07-24, the live incident that started this whole investigation - see
    // OBSERVER_LOG.md): kept only as the fallback for tasks dispatched before this fix (see
    // reviewFallbackVerdictPath). The real path is now generated fresh per dispatch (one-shot batch) or per
    // persistent-worker carrier (reused across that one worker's follow-up cycles, which is correct - the
    // collision was always ACROSS different branches/workers, never within one) and stashed in the
    // dispatching task's own payload.
    private static final String PR_REVIEW_FALLBACK_VERDICT_PATH = ".eneik/review-verdict.json";
    public static final String PR_REVIEW_FALLBACK_VERDICT_PATH_KEY = "reviewsVerdictPath";
    // Plural array, not a singular id: dispatchReviewFallbackBatch covers every PR that needed a Jules
    // fallback reviewer in one orchestrate() tick, in one Jules session - firing one session per PR was
    // the actual cause of the session-count blowup once Gemini's outage became persistent rather than
    // transient (every implementer PR fell back individually, defeating the whole point of the 15-minute
    // review batching, which only ever batched the Gemini call, never this fallback).
    public static final String PR_REVIEW_FALLBACK_TASK_IDS_KEY = "reviewsTaskIds";

    // Parallel array to PR_REVIEW_FALLBACK_TASK_IDS_KEY (same index = same target task), recording which PR
    // this specific dispatch reviewed. Needed because a task can legitimately get a brand new PR later (a
    // merge-conflict rebase, an old cancel+redispatch recovery) - without recording the PR URL,
    // "has this task ever been reviewed" and "has this task's CURRENT PR been reviewed" are conflated, and
    // a task whose only review ever targeted a now-abandoned PR gets permanently skipped even though its
    // real, current PR was never reviewed at all. Confirmed live (test-thirty-fifth, task 210e4ca6,
    // 2026-07-23): PR#3 was reviewed and then superseded by PR#8 after a conflict rebase; PR#8 sat
    // `pending_review` forever because reviewFallbackTargetsEverAttempted only knew the task id, not which
    // PR it was for.
    public static final String PR_REVIEW_FALLBACK_PR_URLS_KEY = "reviewsPrUrls";

    // Parallel array recording the reviewed diff's content hash per target - see the dedup key comment in
    // JulesDispatchService.dispatchReviewerFallbackBatch. Same prUrl can recur with genuinely new content
    // after a "blocked" review's correction round (new commits, same PR) - the hash is what distinguishes
    // "already covered" from "needs re-review" in that case, since the URL alone doesn't change.
    public static final String PR_REVIEW_FALLBACK_DIFF_HASH_KEY = "reviewsDiffHash";

    // Fixes the "исчерпаный ретри на ревью" incident (2026-07-26, test-thirty-eighth, tasks 41dc3324/
    // c43575c5/4db2f25e/64d2fc08/e4caba98): "this target was included in a dispatched fallback-review batch"
    // (recorded forever on the CARRIER via PR_REVIEW_FALLBACK_TASK_IDS_KEY) and "this target's verdict
    // actually came back and got applied" were wrongly treated as the same thing. A batch that completed
    // with no verdict entry for one specific target left that target in pending_review with the poka-yoke
    // key already burned, so it could never be automatically re-reviewed - permanently frozen with zero
    // recovery path. Stored on the TARGET task's own payload (not the carrier's) so it survives across
    // however many different carrier tasks end up attempting it. reviewFallbackTargetsEverAttempted excludes
    // a target's keys from the blocking set while this counter is below the cap, so processPendingReviewBatch
    // legitimately re-dispatches it; once the cap is hit, applyReviewVerdictToTask stops retrying and marks
    // the task blocked instead - which createRecoveryWishlistForOrphanedBlockedTasks already retires to
    // failed with a clear reason, letting the normal falsification/coverage-audit gap-detection recreate the
    // work rather than inventing a bespoke recovery path for this one case.
    public static final String PR_REVIEW_FALLBACK_NULL_VERDICT_RETRY_KEY = "reviewFallbackNullVerdictRetries";
    public static final int PR_REVIEW_FALLBACK_MAX_NULL_VERDICT_RETRIES = 3;

    public int reviewFallbackNullVerdictRetryCount(TaskEntity task) {
        if (task.getPayload() == null) {
            return 0;
        }
        return task.getPayload().path(PR_REVIEW_FALLBACK_NULL_VERDICT_RETRY_KEY).asInt(0);
    }

    /** Increments and persists the counter on the target task's own payload; returns the new count. */
    public int recordReviewFallbackNullVerdict(TaskEntity task) {
        ObjectNode payload = task.getPayload() instanceof ObjectNode existing ? existing : objectMapper.createObjectNode();
        int next = payload.path(PR_REVIEW_FALLBACK_NULL_VERDICT_RETRY_KEY).asInt(0) + 1;
        payload.put(PR_REVIEW_FALLBACK_NULL_VERDICT_RETRY_KEY, next);
        task.setPayload(payload);
        return next;
    }

    /** Called once a real verdict is found and applied, so a later unrelated pending_review episode for the same task starts fresh. */
    public void clearReviewFallbackNullVerdictRetries(TaskEntity task) {
        if (task.getPayload() instanceof ObjectNode payload && payload.has(PR_REVIEW_FALLBACK_NULL_VERDICT_RETRY_KEY)) {
            payload.remove(PR_REVIEW_FALLBACK_NULL_VERDICT_RETRY_KEY);
            task.setPayload(payload);
        }
    }

    public UUID dispatchReviewFallbackBatch(List<TaskEntity> originalTasks, List<String> prUrls, List<String> diffHashes, String prompt, String verdictPath) {
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
        if (verdictPath != null && !verdictPath.isBlank()) {
            payload.put(PR_REVIEW_FALLBACK_VERDICT_PATH_KEY, verdictPath);
        }
        ArrayNode idsArray = payload.putArray(PR_REVIEW_FALLBACK_TASK_IDS_KEY);
        for (TaskEntity t : originalTasks) {
            idsArray.add(t.getId().toString());
        }
        ArrayNode prUrlsArray = payload.putArray(PR_REVIEW_FALLBACK_PR_URLS_KEY);
        for (int i = 0; i < originalTasks.size(); i++) {
            prUrlsArray.add(prUrls != null && i < prUrls.size() ? prUrls.get(i) : "");
        }
        ArrayNode diffHashArray = payload.putArray(PR_REVIEW_FALLBACK_DIFF_HASH_KEY);
        for (int i = 0; i < originalTasks.size(); i++) {
            diffHashArray.add(diffHashes != null && i < diffHashes.size() ? diffHashes.get(i) : "");
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
    public UUID dispatchReviewFallbackBatchAsPersistentCarrier(List<TaskEntity> originalTasks, List<String> prUrls, List<String> diffHashes, String prompt, String verdictPath) {
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
        if (verdictPath != null && !verdictPath.isBlank()) {
            payload.put(PR_REVIEW_FALLBACK_VERDICT_PATH_KEY, verdictPath);
        }
        ArrayNode idsArray = payload.putArray(PR_REVIEW_FALLBACK_TASK_IDS_KEY);
        for (TaskEntity t : originalTasks) {
            idsArray.add(t.getId().toString());
        }
        ArrayNode prUrlsArray = payload.putArray(PR_REVIEW_FALLBACK_PR_URLS_KEY);
        for (int i = 0; i < originalTasks.size(); i++) {
            prUrlsArray.add(prUrls != null && i < prUrls.size() ? prUrls.get(i) : "");
        }
        ArrayNode diffHashArray = payload.putArray(PR_REVIEW_FALLBACK_DIFF_HASH_KEY);
        for (int i = 0; i < originalTasks.size(); i++) {
            diffHashArray.add(diffHashes != null && i < diffHashes.size() ? diffHashes.get(i) : "");
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

    // Parallel to reviewFallbackTargetTaskIds - see PR_REVIEW_FALLBACK_PR_URLS_KEY. Absent (older tasks
    // predating this field, or a malformed entry) yields "" at that index rather than shifting the array,
    // so callers can still zip this 1:1 against reviewFallbackTargetTaskIds by index.
    public List<String> reviewFallbackTargetPrUrls(TaskEntity task) {
        if (task.getPayload() == null) {
            return List.of();
        }
        JsonNode urlsNode = task.getPayload().path(PR_REVIEW_FALLBACK_PR_URLS_KEY);
        if (!urlsNode.isArray()) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        for (JsonNode n : urlsNode) {
            urls.add(n.asText(""));
        }
        return urls;
    }

    // Parallel to reviewFallbackTargetTaskIds/reviewFallbackTargetPrUrls - see PR_REVIEW_FALLBACK_DIFF_HASH_KEY.
    public List<String> reviewFallbackTargetDiffHashes(TaskEntity task) {
        if (task.getPayload() == null) {
            return List.of();
        }
        JsonNode hashesNode = task.getPayload().path(PR_REVIEW_FALLBACK_DIFF_HASH_KEY);
        if (!hashesNode.isArray()) {
            return List.of();
        }
        List<String> hashes = new ArrayList<>();
        for (JsonNode n : hashesNode) {
            hashes.add(n.asText(""));
        }
        return hashes;
    }

    /** Falls back to the old shared constant for tasks dispatched before this fix. */
    public String reviewFallbackVerdictPath(TaskEntity task) {
        if (task.getPayload() == null) {
            return PR_REVIEW_FALLBACK_VERDICT_PATH;
        }
        String raw = task.getPayload().path(PR_REVIEW_FALLBACK_VERDICT_PATH_KEY).asText(null);
        return raw == null || raw.isBlank() ? PR_REVIEW_FALLBACK_VERDICT_PATH : raw;
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
    // Fixed-path collision fix (2026-07-24): this used to be a single shared constant every design-review
    // session wrote to - two concurrently-open design-review PRs guaranteed a merge conflict on this one
    // path, the same root cause diagnosed live for PR_REVIEW_FALLBACK_VERDICT_PATH. Kept only as the label
    // prefix now; the real path is generated fresh per task (designReviewVerdictPath) and stashed in the
    // task's own payload, same idiom as DESIGN_REVIEW_DRAFT_PATH_KEY.
    private static final String DESIGN_REVIEW_VERDICT_LABEL = "design-review-verdict";
    private static final String DESIGN_REVIEW_VERDICT_PATH = ".eneik/design-review-verdict.json";
    public static final String DESIGN_REVIEW_DRAFT_PATH_KEY = "designDraftPath";
    public static final String DESIGN_REVIEW_VERDICT_PATH_KEY = "designVerdictPath";
    private static final String DESIGNER_ROLE = "BARCAN-TAG-03";

    public void dispatchDesignReview(ProjectEntity project, String draftPath, String brief) {
        RoleEntity compilerRole = roleRepository.findById(ORCHESTRATOR_ROLE).orElse(null);
        if (compilerRole == null) {
            log.error("Cannot dispatch design review for project {}: role {} not found", project.getId(), ORCHESTRATOR_ROLE);
            return;
        }
        String charter = readRawRoleRules(DESIGNER_ROLE);
        String verdictPath = ".eneik/records/" + DESIGN_REVIEW_VERDICT_LABEL + "-" + UUID.randomUUID() + ".json";

        TaskEntity reviewTask = new TaskEntity();
        reviewTask.setProject(project);
        reviewTask.setRole(compilerRole);
        reviewTask.setTitle("Design review (" + shortId(project.getId()) + "-" + FILE_TIME_SUFFIX.format(java.time.Instant.now()) + ")");
        reviewTask.setDescription(designReviewPrompt(draftPath, brief, charter, verdictPath));
        reviewTask.setStatus(TaskStatus.queued);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put(WISHLIST_COMPILER_PAYLOAD_KEY, DESIGN_REVIEW_TASK_TYPE);
        payload.put(DESIGN_REVIEW_DRAFT_PATH_KEY, draftPath);
        payload.put(DESIGN_REVIEW_VERDICT_PATH_KEY, verdictPath);
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

    /** Falls back to the old shared constant for tasks dispatched before this fix - never a collision risk
     * for THOSE historical tasks specifically (there's at most one still in flight at upgrade time), only
     * going forward would repeated collisions occur, and every new task gets its own unique path. */
    public String designReviewVerdictPath(TaskEntity task) {
        if (task.getPayload() == null) {
            return DESIGN_REVIEW_VERDICT_PATH;
        }
        String raw = task.getPayload().path(DESIGN_REVIEW_VERDICT_PATH_KEY).asText(null);
        return raw == null || raw.isBlank() ? DESIGN_REVIEW_VERDICT_PATH : raw;
    }

    // Design shop Stage 3 (DesignShopOrchestrationService): once a mockup has cleared the review above
    // and been promoted to design/approved/, the pixel-perfect implementation itself is a normal
    // BARCAN-TAG-11 task dispatched through the same general-pool capacity-claiming logic as every other
    // task - deliberately NOT a bespoke dispatch path, so it gets the same account fairness/retry
    // behaviour as everything else without duplicating dispatchToGeneralPool's private locking logic.
    private static final String DESIGN_IMPLEMENTATION_ROLE = "BARCAN-TAG-11";

    public void dispatchDesignImplementation(ProjectEntity project, String approvedDesignPath, String jtbd) {
        RoleEntity designerRole = roleRepository.findById(DESIGN_IMPLEMENTATION_ROLE).orElse(null);
        if (designerRole == null) {
            log.error("Cannot dispatch design implementation for project {}: role {} not found", project.getId(), DESIGN_IMPLEMENTATION_ROLE);
            return;
        }
        TaskEntity implementationTask = new TaskEntity();
        implementationTask.setProject(project);
        implementationTask.setRole(designerRole);
        implementationTask.setTitle("Design implementation (" + shortId(project.getId()) + "-" + FILE_TIME_SUFFIX.format(java.time.Instant.now()) + ")");
        implementationTask.setDescription("Implement the following pixel-perfect against the already-approved design reference. " + jtbd
                + "\n\nDESIGN_MOCKUP_ASSET (already approved - implement directly against it, no new mockup or design review needed): "
                + approvedDesignPath + "/mockup.html");
        implementationTask.setStatus(TaskStatus.queued);

        implementationTask = taskRepository.save(implementationTask);
        dispatchToGeneralPool(implementationTask);
        log.info("Dispatched design implementation task {} for approved design {} in project {}",
                implementationTask.getId(), approvedDesignPath, project.getName());
    }

    // Design shop Stage 2.5 (2026-08-11, operator directive): raw review concerns must never just sit
    // in a log - they get triaged by a SECOND, independent BARCAN-TAG-11 pass into a structured spec
    // (JTBD/acceptance-criteria/edit-instruction, classified by Cynefin/Kano/Lean - the same frameworks
    // TechnicalLeadCompiler already uses everywhere else, not a new vocabulary) - both for reporting
    // (a real task, visible in the dashboard, not a vanished log line) and so JulesDispatchService can
    // mechanically decide what to do with the result (edit the still-open mockup, or open a real
    // wishlist if the design already shipped).
    public static final String DESIGN_CONCERN_TRIAGE_TASK_TYPE = "design_concern_triage";
    public static final String DESIGN_CONCERN_TRIAGE_MOCKUP_PATH_KEY = "designConcernMockupPath";
    public static final String DESIGN_CONCERN_TRIAGE_RECORD_PATH_KEY = "designConcernRecordPath";
    private static final String DESIGN_CONCERN_TRIAGE_RECORD_LABEL = "design-concern-triage";

    public void dispatchDesignConcernTriage(ProjectEntity project, String mockupPath, String rawConcernsText) {
        RoleEntity triageRole = roleRepository.findById(DESIGN_IMPLEMENTATION_ROLE).orElse(null);
        if (triageRole == null) {
            log.error("Cannot dispatch design concern triage for project {}: role {} not found", project.getId(), DESIGN_IMPLEMENTATION_ROLE);
            return;
        }
        String charter = readRawRoleRules(DESIGN_IMPLEMENTATION_ROLE);
        String recordPath = ".eneik/records/" + DESIGN_CONCERN_TRIAGE_RECORD_LABEL + "-" + UUID.randomUUID() + ".json";

        TaskEntity triageTask = new TaskEntity();
        triageTask.setProject(project);
        triageTask.setRole(triageRole);
        triageTask.setTitle("Design concern triage (" + shortId(project.getId()) + "-" + FILE_TIME_SUFFIX.format(java.time.Instant.now()) + ")");
        triageTask.setDescription(designConcernTriagePrompt(mockupPath, rawConcernsText, charter, recordPath));
        triageTask.setStatus(TaskStatus.queued);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put(WISHLIST_COMPILER_PAYLOAD_KEY, DESIGN_CONCERN_TRIAGE_TASK_TYPE);
        payload.put(DESIGN_CONCERN_TRIAGE_MOCKUP_PATH_KEY, mockupPath);
        payload.put(DESIGN_CONCERN_TRIAGE_RECORD_PATH_KEY, recordPath);
        triageTask.setPayload(payload);

        triageTask = taskRepository.save(triageTask);
        dispatchToGeneralPool(triageTask);
        log.info("Dispatched design concern triage task {} for mockup {} in project {}",
                triageTask.getId(), mockupPath, project.getName());
    }

    public boolean isDesignConcernTriageTask(TaskEntity task) {
        return task.getPayload() != null
                && DESIGN_CONCERN_TRIAGE_TASK_TYPE.equals(task.getPayload().path(WISHLIST_COMPILER_PAYLOAD_KEY).asText(null));
    }

    public String designConcernTriageMockupPath(TaskEntity task) {
        if (task.getPayload() == null) {
            return null;
        }
        String raw = task.getPayload().path(DESIGN_CONCERN_TRIAGE_MOCKUP_PATH_KEY).asText(null);
        return raw == null || raw.isBlank() ? null : raw;
    }

    public String designConcernTriageRecordPath(TaskEntity task) {
        if (task.getPayload() == null) {
            return null;
        }
        String raw = task.getPayload().path(DESIGN_CONCERN_TRIAGE_RECORD_PATH_KEY).asText(null);
        return raw == null || raw.isBlank() ? null : raw;
    }

    private String designConcernTriagePrompt(String mockupPath, String rawConcernsText, String charter, String recordPath) {
        return """
                You are Eneik's BARCAN-TAG-11 (Frontend Engineer / Client-Perception) role, acting as an
                independent design-concern triage specialist - a SECOND, independent pass after a design
                review already raised these concerns, not the same reviewer re-confirming itself. Do NOT
                implement, fix, or change any product code, and do not run builds or tests; this task
                only produces a structured triage record.

                MOCKUP UNDER REVIEW: `%s/mockup.html` (and `%s/mockup.png` if present) - read it
                directly from your checkout.

                RAW REVIEWER CONCERNS (unstructured, verbatim from the design review):
                %s

                YOUR ROLE CHARTER:
                %s

                For EACH concern above, produce one structured entry with these exact fields:
                  - "concern": the original concern text, verbatim
                  - "jtbd": "When ..., I want ..., so that ..." - the real underlying need
                  - "acceptanceCriteria": "Given/When/Then" - how to verify it's actually fixed
                  - "editInstruction": a concrete, self-contained instruction that could be sent
                    directly to an AI design tool to fix ONLY this concern on the existing mockup - not
                    a rewrite of the whole screen
                  - "kanoClass": one of must_be | performance | attractive | indifferent
                  - "cynefinDomain": one of clear | complicated | complex | chaotic
                  - "leanValue": one of essential | valuable | waste - be honest, not every concern is
                    essential; a genuinely low-value nitpick should be marked "waste"

                Deliverable: create a new branch and open a PR that contains ONLY one file, `%s`
                (this EXACT path - it is unique to this task, do not use any other path), containing a
                JSON array of these objects and nothing else - no markdown fences, no other text.
                Do not write, modify, or delete any other file.
                """.formatted(mockupPath, mockupPath, rawConcernsText, charter, recordPath);
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

    private String designReviewPrompt(String draftPath, String brief, String charter, String verdictPath) {
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

                Deliverable: create a new branch and open a PR that contains ONLY one file, `%s`
                (this EXACT path - it is unique to this task, do not use any other path), with EXACTLY
                this shape and no other files changed. Each concern carries its own "severity"
                (critical|high|medium|low) - a real accessibility failure is higher severity than a
                debatable color choice, make that judgment explicit instead of flattening every concern
                to the same weight:
                {"verdict": "approve", "reason": "", "concerns": [{"text": "short concern 1", "severity": "low"}]}
                or, only for a genuine severe blocker:
                {"verdict": "reject", "reason": "concrete, specific reason tied to the file", "concerns": []}
                Do not write, modify, or delete any other file.

                Design brief this mockup was generated for:
                %s

                Your role charter:
                %s
                """.formatted(draftPath, draftPath, verdictPath, brief, charter);
    }

    // Engineering invariant #11 follow-up (2026-08-08, live dispute-driven audit of test-forty-third's
    // dispatch starvation): a FOR UPDATE SKIP LOCKED row lock's held duration must match its causal
    // purpose - proving "this account is free, and is now claimed" is an atomic, instantaneous fact about
    // database state, and must never extend across a temporally-extended, non-deterministic external
    // process (a Jules API round trip). This is the single canonical "lock the account, claim the task,
    // nothing else" primitive - the caller supplies HOW to find the candidate account (the different
    // capacity queries need different parameters), this method guarantees the lock and the claim happen in
    // the SAME short transaction, and returns before any network call ever begins. Confirmed live:
    // dispatchQueuedTasks's own inline block, dispatchCompilerTask, and dispatchToGeneralPool all held this
    // exact anti-pattern independently (accounts read as `idle` with 0/3 concurrent sessions, yet
    // lockNextJulesAccountWithCapacity still returned empty - real capacity was never the cause; the row
    // was FOR-UPDATE-locked by an earlier iteration of the SAME still-open transaction, blocking dispatch
    // for hours even though nothing was actually running). Public + called via `self` (not `this`) so this
    // actually goes through the Spring proxy - see the `self` field's own comment.
    @Transactional
    public Optional<AccountEntity> claimAccountForTask(UUID taskId, java.util.function.Supplier<Optional<AccountEntity>> accountLookup) {
        Optional<AccountEntity> accountOpt = accountLookup.get();
        accountOpt.ifPresent(account -> claimService.claimSpecificTask(taskId, account.getId()));
        return accountOpt;
    }

    // No longer @Transactional (2026-08-08 fix, see claimAccountForTask above for the invariant): this
    // loop can process many queued tasks in one tick, each ending in a real Jules network call. Wrapping
    // the whole method in one transaction meant every account row examined by ANY earlier task in the loop
    // stayed FOR-UPDATE-locked until the entire method returned - genuinely starving concurrent dispatch
    // attempts for hours even when accounts were truly idle, and risked a rollback silently orphaning an
    // already-created (irreversible) Jules session if a later iteration threw. Each task's lock-and-claim
    // now happens in its own short transaction (claimAccountForTask); the network call below runs with no
    // transaction open at all.
    public void dispatchQueuedTasks(UUID projectId) {
        ProjectEntity project = requireActiveProject(projectId);
        operationalPolicyService.requireAllowed(projectId, OperationalAction.DISPATCH_QUEUED_TASKS);
        List<TaskEntity> queuedTasks = taskRepository.findByProjectIdAndStatusOrderByPriorityDescCreatedAtAsc(project.getId(), TaskStatus.queued);
        boolean buildPhase = readinessService.isBuildPhase(project.getId());
        boolean clientScopeDecompositionOpen =
                wishlistRepository.countByProjectIdAndStatus(project.getId(), WishlistStatus.pending) > 0
                        || wishlistRepository.countByProjectIdAndStatus(project.getId(), WishlistStatus.compiling) > 0;

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
                .sorted(java.util.Comparator.comparingInt(this::queuedDispatchClass))
                .toList();

        for (TaskEntity task : queuedTasks) {
            if (clientScopeDecompositionOpen && isHousekeepingCarrierTask(task) && !isWishlistCompilerTask(task)) {
                String waitingStatus = "Waiting for client-scope decomposition to finish";
                if (!waitingStatus.equals(task.getJulesDispatchStatus())) {
                    task.setJulesDispatchStatus(waitingStatus);
                    taskRepository.save(task);
                }
                continue;
            }

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
            // Live regression (2026-08-08, found during monitoring, same day as the fix that caused it):
            // task.getDependsOn() is a lazy @ManyToOne proxy - reading it beyond .getId() (which Hibernate
            // resolves from the already-known FK column without a session) requires the Hibernate Session
            // that loaded the enclosing `task` to still be open. Removing @Transactional from this method
            // (the transaction-scope fix earlier today) meant the initial query's session closes as soon as
            // that repository call returns, well before this loop reaches a later task - every subsequent
            // dependency.getStatus() call threw LazyInitializationException ("no Session"), confirmed live
            // (every ~60s cycle, ContinuousOrchestrationService: Failed for project ...). Re-fetching by id
            // gets a fresh, fully-initialized entity via its own short auto-committing repository call - no
            // enclosing transaction needed, so this does not reintroduce the account-lock-across-network-call
            // bug the @Transactional removal was fixing in the first place.
            TaskEntity rawDependency = task.getDependsOn();
            TaskEntity dependency = rawDependency != null
                    ? taskRepository.findById(rawDependency.getId()).orElse(null) : null;
            if (dependency != null && !readinessService.isDependencySatisfied(dependency)) {
                // Lean-waste fix (2026-07-23, generalized 2026-07-24): a dependency on a spec-stage task
                // (decision/architecture/api-contract/compliance) is only ever a small reference document,
                // not "a huge chunk of code" - the dependent only needs the artifact's content, which
                // exists as soon as its PR is open. Every other dependency edge is completely unaffected -
                // this early-unblock is provably scoped to
                // ClientDeliverableReadinessService.isSpecDependencyPrOpenButUnmerged, which itself is
                // scoped to EmsFlowStage.isSpecStage alone.
                if (readinessService.isSpecDependencyPrOpenButUnmerged(dependency)) {
                    JsonNode payloadNode = task.getPayload();
                    if (payloadNode instanceof ObjectNode payload
                            && !payload.path(EARLY_UNBLOCK_SPEC_KEY).asBoolean(false)) {
                        payload.put(EARLY_UNBLOCK_SPEC_KEY, true);
                        taskRepository.save(task);
                        log.info("Poka-yoke Lean fix: task {} early-unblocked on spec task {} (PR open, "
                                        + "not yet merged) - starting in parallel instead of waiting for merge.",
                                task.getId(), dependency.getId());
                    }
                } else {
                    if (dependency.getStatus() == TaskStatus.failed) {
                        String waitingStatus = "Waiting for failed dependency " + dependency.getId()
                                + "; no replacement task or wishlist was created";
                        if (!waitingStatus.equals(task.getJulesDispatchStatus())) {
                            task.setJulesDispatchStatus(waitingStatus);
                            taskRepository.save(task);
                            log.warn("ProjectFlowService: kept planned task {} queued behind failed dependency {}; "
                                            + "bounded recovery may resume the same dependency once, but no child work is created",
                                    task.getId(), dependency.getId());
                        }
                    }
                    continue;
                }
            }

            if (isWishlistCompilerTask(task) || isFalsificationAuditTask(task) || isPhilosophicalAuditTask(task) || isCoverageAuditTask(task)) {
                // Compiler, coverage-audit, falsification-audit, and philosophical-audit tasks are deliberately pinned to the
                // reserved compiler account: all of them are low-frequency by design (WIP-gated batching, a
                // multi-hour or weekly cron) and share that account's capacity comfortably instead of
                // contending with real product work.
                dispatchCompilerTask(task);
                continue;
            }

            if (isReviewFallbackTask(task) || isDesignReviewTask(task)) {
                // These fire once per PR / per mockup - real per-project traffic, not
                // rare housekeeping - so they use the general round-robin pool like implementer tasks do.
                // Review-fallback excludes the implementer's own account (Charter Pattern #12 -
                // independent verification, not self-attestation: the account that wrote a PR must not
                // also be handed that PR's review by an accident of round-robin ordering). Design-review
                // has no equivalent implementer-account concept - mockup drafts come from design
                // generation, not a Jules implementer session claiming/dispatching a code task - so it
                // gets no exclusion.
                java.util.Set<String> excludedAccountNames = isReviewFallbackTask(task)
                        ? implementerAccountNamesForReviewFallback(task)
                        : java.util.Set.of();
                dispatchToGeneralPool(task, excludedAccountNames);
                continue;
            }

            if (isOutOfCycleGeneratedWork(task)) {
                quarantineOutOfCycleGeneratedWork(task);
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
            Optional<AccountEntity> accountOpt = self.claimAccountForTask(task.getId(), () ->
                    accountRepository.lockNextJulesAccountWithCapacity(
                            project.getId(),
                            roleTag,
                            maxConcurrentJulesSessionsPerAccount,
                            null,
                            maxDailySessionsPerAccount,
                            null
                    ));
            if (accountOpt.isPresent()) {
                AccountEntity account = accountOpt.get();
                try {
                    // Refresh task state after claim
                    TaskEntity savedTask = taskRepository.findById(task.getId()).orElse(task);

                    JulesDispatchResult dispatch = julesDispatchService.dispatch(savedTask, account.getId());
                    savedTask.setJulesSessionName(dispatch.sessionName());
                    savedTask.setJulesDispatchStatus(dispatch.reason());
                    taskRepository.save(savedTask);
                    if (!dispatch.dispatched()) {
                        if (isJulesSourceNotFound(dispatch.reason())) {
                            claimService.closeTaskAsBlocked(savedTask.getId(), dispatch.reason());
                            log.warn("Blocked queued task {} of project {} because Jules cannot see the repository source: {}",
                                    savedTask.getId(), project.getName(), dispatch.reason());
                            continue;
                        }
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

    private int queuedDispatchClass(TaskEntity task) {
        if (isWishlistCompilerTask(task)) {
            return 0;
        }
        if (isFalsificationAuditTask(task) || isPhilosophicalAuditTask(task) || isCoverageAuditTask(task)
                || isReviewFallbackTask(task) || isDesignReviewTask(task)) {
            return 2;
        }
        return 1;
    }

    private boolean isJulesSourceNotFound(String reason) {
        return reason != null && reason.toLowerCase(Locale.ROOT).contains("jules_source_not_found");
    }

    private boolean isHousekeepingCarrierTask(TaskEntity task) {
        return isFalsificationAuditTask(task)
                || isPhilosophicalAuditTask(task)
                || isCoverageAuditTask(task)
                || isReviewFallbackTask(task)
                || isDesignReviewTask(task);
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

    // No longer @Transactional (2026-08-08 fix, same audit as dispatchQueuedTasks/claimAccountForTask
    // above): this loop's own lockNextJulesAccountWithCapacity call was held open across
    // julesDispatchService.dispatch's network round trip for every task in the review queue, one
    // enclosing transaction at a time - same starvation mechanism, different dispatcher. No separate
    // claimSpecificTask call happens in this method (the REVIEWER-mode dispatch overload handles that
    // itself), so removing the outer transaction is sufficient here - the lock query already executes as
    // its own short, auto-committing unit via the repository proxy, with no claim step to keep atomic
    // alongside it.
    public void dispatchReviewTasks(UUID projectId) {
        ProjectEntity project = requireActiveProject(projectId);
        operationalPolicyService.requireAllowed(projectId, OperationalAction.DISPATCH_REVIEW_TASKS);
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
                    || isDesignReviewTask(task) || isCoverageAuditTask(task) || isPersistentWorkerCarrierTask(task)
                    || isPhilosophicalAuditTask(task)) {
                continue;
            }
            // Bug fix (2026-07-23, confirmed live on test-thirty-fifth): this used its own incomplete
            // status list ("running"/"queued" only). A first attempt at this fix reused this class's own
            // findActiveJulesSession helper, which turned out to ALSO be missing "pr_opened" - still wrong,
            // confirmed still looping live after that deploy. The only genuinely complete list is
            // JulesDispatchService.ACTIVE_SESSION_STATUSES (its dispatch() method's own duplicate-check),
            // now exposed via hasActiveSession() specifically so this never has its own separate copy of
            // that list to drift out of sync again. A task whose only session sits at "pr_opened" - the
            // normal, common state while its PR awaits merge - was wrongly read as having NO active session,
            // so this fired dispatch() again every single tick; dispatch() itself always correctly no-oped,
            // but its `dispatched` field is true for BOTH a real new dispatch and an already-active skip, so
            // checking it does not distinguish the two either - confirmed live, the misleading "Auto-
            // dispatched reviewer" log kept repeating even after that first (insufficient) fix attempt.
            boolean hasActiveReviewSession = julesDispatchService.hasActiveSession(task.getId());

            if (!hasActiveReviewSession) {
                // Find any idle capable account to act as reviewer
                String roleTag = task.getRole().getTag();
                Optional<AccountEntity> accountOpt = accountRepository.lockNextJulesAccountWithCapacity(
                        project.getId(),
                        roleTag,
                        maxConcurrentJulesSessionsPerAccount,
                        null,
                        maxDailySessionsPerAccount,
                        null
                );
                if (accountOpt.isPresent()) {
                    AccountEntity account = accountOpt.get();
                    try {
                        JulesDispatchResult result = julesDispatchService.dispatch(task, account.getId(), "REVIEWER");
                        if (result.dispatched()) {
                            log.info("Auto-dispatched reviewer for task {} of project {} to account {}",
                                    task.getId(), project.getName(), account.getName());
                        } else {
                            log.info("Reviewer dispatch for task {} of project {} did not start a new session ({})",
                                    task.getId(), project.getName(), result.reason());
                        }
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
        projectRepository.save(project);

        // Operator directive 2026-07-24 (same immediate-stop treatment as freezeProjectAndCancelWork,
        // confirmed to apply here too): accepted is a terminal "client took final delivery" status, same
        // as frozen it must mean zero further background activity, not "let whatever's in flight finish
        // on its own." Cancels every non-terminal task's active session and closes any open GitHub PRs -
        // see cancelAllActiveWorkForProject's own comment for why this is necessary (several scheduled
        // loops are project-status-blind and would otherwise keep polling/merging this project forever).
        cancelAllActiveWorkForProject(project, "Project accepted: client took final delivery");
        return toProjectDto(project);
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
                        && !isReviewFallbackTask(task) && !isDesignReviewTask(task) && !isCoverageAuditTask(task)
                        && !isPhilosophicalAuditTask(task))
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
                .map(w -> new com.eneik.production.dto.WishlistResponseDto(w.getId(), w.getProjectId(), w.getSource(), w.getSourceRoleTag(), w.getContent(), w.getStatus(), w.getCreatedAt(), w.getFeatureId()))
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

        ClientDeliverableReadinessService.Readiness productReadiness = readinessService.computeForProject(projectId);
        // Operator directive 2026-07-24 ("надо считать по фичам!"), explicit choice via AskUserQuestion
        // over 3 concrete alternatives (task ratio 66.7%, feature ratio 25%, thread-closeout ratio 75%):
        // falsification readiness now gates on EPIC completion (completeFeatures/totalFeatures), not task
        // merge ratio. Deliberately stricter than the old task-based gate for a project with many
        // multi-task epics still in progress - a feature only counts once EVERY one of its code-producing
        // items has reached main (see ClientDeliverableReadinessService.computeForSources), not just "some
        // task somewhere merged". The dashboard exposes both numbers separately: mergedRatio is the
        // deliverable-level merge ratio; featureReadinessRatio is the feature-level gate.
        double featureRatio = productReadiness.totalFeatures() > 0
                ? (double) productReadiness.completeFeatures() / productReadiness.totalFeatures()
                : 0.0;
        double deliverableMergeRatio = deliverableMergeRatio(productReadiness);
        boolean falsificationEligible = productReadiness.decompositionComplete()
                && featureRatio >= falsificationReadinessThreshold;
        ProductReadinessDto productReadinessDto = new ProductReadinessDto(
                productReadiness.totalFeatures(),
                productReadiness.completeFeatures(),
                productReadiness.totalDeliverables(),
                productReadiness.mergedDeliverables(),
                deliverableMergeRatio,
                featureRatio,
                productReadiness.decompositionComplete(),
                falsificationReadinessThreshold,
                falsificationEligible,
                falsificationEligible ? "ready_for_falsification"
                        : (productReadiness.decompositionComplete() ? "building" : "decomposing"),
                computeBlockedItems(allTaskEntities)
        );

        return new ProjectDashboardDto(
                toProjectDto(project),
                agents.size(),
                wishlistRepository.findByProjectIdAndStatus(projectId, com.eneik.production.models.persistence.WishlistStatus.pending).size(),
                queue,
                pipeline,
                productReadinessDto,
                emsMetricsService.build(allTaskEntities, wishlistEntities),
                agents,
                wishlist,
                tasks
        );
    }

    /**
     * Dashboard "N of M not merged" + "blocked for N hours" visibility (2026-07-25, operator directive) -
     * two distinct reasons a task shows up: still non-terminal, no active claim, hasn't moved in over
     * {@link #BLOCKED_ITEM_STALE_THRESHOLD_HOURS}; or its own status says done but
     * {@link ClientDeliverableReadinessService#reachedMain} says the work never actually landed in main
     * (the exact shape of the 44%-orphaned-PR incident, and of today's manually-found feature-thread
     * closeout deadlock - both were only found by hand, this is meant to make the next one visible without
     * a SQL session).
     */
    private List<BlockedItemDto> computeBlockedItems(List<TaskEntity> allTaskEntities) {
        java.time.Instant now = java.time.Instant.now();
        List<BlockedItemDto> blocked = new java.util.ArrayList<>();
        for (TaskEntity task : allTaskEntities) {
            if (!isActionableBlockedStatus(task.getStatus())) {
                continue; // terminal and already explains itself - not an actionable "blocked" signal
            }
            String reason;
            if (task.getStatus() == TaskStatus.done) {
                // Live finding (2026-07-25): a DECISION-stage/"complex"-cynefin task is never expected to
                // reach main on its own (ClientDeliverableReadinessService excludes it from its own
                // deliverable-ratio the same way) - without this check this widget flagged spec/decision
                // work as "blocked" when it was actually just correctly-done, non-mergeable work.
                if (readinessService.reachedMain(task) || readinessService.isAuxiliaryTask(task)) {
                    continue;
                }
                reason = "done_not_reached_main";
            } else {
                boolean stale = task.getUpdatedAt() != null
                        && java.time.Duration.between(task.getUpdatedAt(), now).toMinutes() / 60.0 >= BLOCKED_ITEM_STALE_THRESHOLD_HOURS;
                if (!stale || claimService.hasActiveClaim(task.getId())) {
                    continue;
                }
                reason = "stale_in_progress";
            }
            double hours = task.getUpdatedAt() == null ? 0.0
                    : java.time.Duration.between(task.getUpdatedAt(), now).toMinutes() / 60.0;
            blocked.add(new BlockedItemDto(
                    task.getId(),
                    com.eneik.production.services.task.TaskTitleBuilder.displayTitle(task),
                    task.getStatus().name(),
                    Math.round(hours * 10) / 10.0,
                    task.getRole() != null ? task.getRole().getTag() : null,
                    reason
            ));
        }
        blocked.sort((a, b) -> Double.compare(b.hoursSinceUpdate(), a.hoursSinceUpdate()));
        return blocked;
    }

    static double deliverableMergeRatio(ClientDeliverableReadinessService.Readiness readiness) {
        if (readiness == null || readiness.totalDeliverables() == 0) {
            return 1.0;
        }
        return (double) readiness.mergedDeliverables() / readiness.totalDeliverables();
    }

    static boolean isActionableBlockedStatus(TaskStatus status) {
        return status != TaskStatus.failed && status != TaskStatus.spike_completed;
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
                || isDesignReviewTask(task) || isCoverageAuditTask(task) || isPhilosophicalAuditTask(task)) {
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
                String owner = gitHubProjectFactoryClient.repositoryOwnerFromUrl(project.getRepositoryUrl());
                for (var node : collaboratorsNode) {
                    String username = node.get("username").asText();
                    CollaboratorProvisioningResult result = gitHubProjectFactoryClient.inviteCollaborator(
                            owner, project.getRepositoryName(), username, token);
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
        Set<String> seenUsernames = new HashSet<>();
        if (project.getFactoryReport() != null) {
            try {
                ObjectNode report = (ObjectNode) objectMapper.readTree(project.getFactoryReport());
                if (report.has("collaborators")) {
                    for (JsonNode node : report.get("collaborators")) {
                        String username = node.get("username").asText();
                        if (!seenUsernames.add(username)) {
                            continue;
                        }
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
                                username,
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

        long uniqueAccountCount = accountRepository.findByEnabledTrueAndProjectIsNullAndGithubUsernameIsNotNullOrderByNameAsc().stream()
                .map(a -> a.getGithubUsername() != null ? a.getGithubUsername().trim() : "")
                .filter(u -> !u.isBlank())
                .distinct()
                .count();

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
                uniqueAccountCount,
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
