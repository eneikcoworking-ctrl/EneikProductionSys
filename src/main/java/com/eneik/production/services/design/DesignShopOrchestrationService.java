package com.eneik.production.services.design;

import com.eneik.production.models.persistence.DesignShopCycleEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.repositories.DesignShopCycleRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.services.ClientDeliverableReadinessService;
import com.eneik.production.services.ProjectFlowService;
import com.eneik.production.services.dashboard.ProjectOperationalContextService;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.logging.LogScope;
import com.eneik.production.services.settings.SystemSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The "design shop": a parallel factory department, wired into the real autonomous pipeline without
 * editing it. Reuses the exact readiness signal ProductLaunchabilityService already uses
 * (ClientDeliverableReadinessService.computeForProject) - but unlike that one-shot check, this treats
 * "100% ready" as "ready to assemble this round," not "product permanently done": product completeness
 * is unachievable by design here (falsification cycles keep adding work while the project is active), so
 * this fires on every false->true edge of readiness, not just the first one ever.
 *
 * Stage 0 (this class): detect the edge, per project, via DesignShopCycleEntity.lastWasReady.
 * Stage 1: DesignAssetService.generateAsset(...) - already E(f)-gated, built earlier.
 * Stage 2: ProjectFlowService.dispatchDesignReview(...) - already-existing, Jules-driven, no human step.
 * Stage 3: once design/approved/.../mockup.html exists, ProjectFlowService.dispatchDesignImplementation
 * creates a normal BARCAN-TAG-11 task through the existing dispatch/gate/automerge pipeline, untouched.
 *
 * Deliberately its own @Scheduled bean, not wired into ContinuousOrchestrationService's tick - zero
 * edits to that class or to JulesDispatchService.dispatch()'s shared logic.
 */
@Service
public class DesignShopOrchestrationService {
    private static final Logger log = LoggerFactory.getLogger(DesignShopOrchestrationService.class);

    // The project's real, established design tokens (Verdant Flow) - same literals already used live
    // by DesignSystemFalsificationService.applyDesignSystemsForProject and the DesignConsistencyAuditService
    // regression fixtures, reused here rather than invented anew.
    private static final List<String> DESIGN_TOKEN_COLORS = List.of(
            "#fbf9f1", "#7d8570", "#3f7d32", "#d97b29", "#e0342f", "#c99a2e");
    private static final List<String> DESIGN_TOKEN_FONTS = List.of("Libre Caslon Text", "IBM Plex Sans");

    // If a dispatched design review is neither approved nor abandoned within this window, the cycle is
    // closed out unpromoted rather than left wedged forever - same soft philosophy as completeDesignReview
    // itself ("a design opinion never stalls work"): the next readiness edge (a later falsification round)
    // gets a fresh attempt instead of this one blocking it indefinitely.
    private static final Duration AWAITING_REVIEW_TIMEOUT = Duration.ofHours(48);

    private final ProjectRepository projectRepository;
    private final DesignShopCycleRepository designShopCycleRepository;
    private final ClientDeliverableReadinessService readinessService;
    private final DesignAssetService designAssetService;
    private final ProjectFlowService projectFlowService;
    private final ProjectOperationalContextService contextService;
    private final GitHubPullRequestService gitHubPullRequestService;
    private final SystemSettingsService settingsService;

    public DesignShopOrchestrationService(ProjectRepository projectRepository,
                                           DesignShopCycleRepository designShopCycleRepository,
                                           ClientDeliverableReadinessService readinessService,
                                           DesignAssetService designAssetService,
                                           ProjectFlowService projectFlowService,
                                           ProjectOperationalContextService contextService,
                                           GitHubPullRequestService gitHubPullRequestService,
                                           SystemSettingsService settingsService) {
        this.projectRepository = projectRepository;
        this.designShopCycleRepository = designShopCycleRepository;
        this.readinessService = readinessService;
        this.designAssetService = designAssetService;
        this.projectFlowService = projectFlowService;
        this.contextService = contextService;
        this.gitHubPullRequestService = gitHubPullRequestService;
        this.settingsService = settingsService;
    }

    @Scheduled(cron = "${design-shop.cron:0 */5 * * * ?}")
    @Transactional
    public void tick() {
        if (!settingsService.effectiveBoolean("design_shop_enabled")) {
            return;
        }
        List<ProjectEntity> activeProjects = projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active);
        for (ProjectEntity project : activeProjects) {
            LogScope.project(project.getId());
            try {
                processProject(project);
            } catch (Exception e) {
                log.error("DesignShopOrchestrationService: cycle tick failed for project {}: {}",
                        project.getId(), e.getMessage(), e);
            } finally {
                LogScope.clear();
            }
        }
    }

    private void processProject(ProjectEntity project) {
        DesignShopCycleEntity cycle = designShopCycleRepository.findByProjectId(project.getId())
                .orElseGet(() -> {
                    DesignShopCycleEntity fresh = new DesignShopCycleEntity();
                    fresh.setProjectId(project.getId());
                    return fresh;
                });

        if (DesignShopCycleEntity.STAGE_AWAITING_REVIEW.equals(cycle.getStage())) {
            advanceAwaitingReview(project, cycle);
            return;
        }

        ClientDeliverableReadinessService.Readiness readiness = readinessService.computeForProject(project.getId());
        boolean isReady = readiness.decompositionComplete() && readiness.ratio() >= 1.0;

        if (isReady && !cycle.isLastWasReady()) {
            startCycle(project, cycle, readiness);
        } else if (!isReady && cycle.isLastWasReady()) {
            cycle.setLastWasReady(false);
            cycle.setUpdatedAt(Instant.now());
            designShopCycleRepository.save(cycle);
        }
    }

    private void startCycle(ProjectEntity project, DesignShopCycleEntity cycle,
                             ClientDeliverableReadinessService.Readiness readiness) {
        String brief = "Full-product design refresh for " + project.getName() + " - round complete ("
                + readiness.completeFeatures() + "/" + readiness.totalFeatures() + " features delivered).";
        var context = contextService.build(project.getId(), project.getName());
        DesignAssetService.DesignAssetResult result = designAssetService.generateAsset(
                project, context, brief, "mockup", "fast", false,
                null, DESIGN_TOKEN_COLORS, DESIGN_TOKEN_FONTS);

        if (!result.available() || result.repoDraftPath() == null || result.repoDraftPath().isBlank()) {
            // Left lastWasReady=false so the next tick retries while readiness is still true, instead of
            // silently losing this round - generation failures (rate limits, transient Stitch errors,
            // aesthetic_drift rejection) are exactly the kind of thing that resolves itself on retry.
            log.warn("DesignShopOrchestrationService: design generation unavailable for project {} ({}); will retry next tick",
                    project.getId(), result.message());
            return;
        }

        projectFlowService.dispatchDesignReview(project, result.repoDraftPath(), brief);

        cycle.setLastWasReady(true);
        cycle.setStage(DesignShopCycleEntity.STAGE_AWAITING_REVIEW);
        cycle.setDraftPath(result.repoDraftPath());
        cycle.setUpdatedAt(Instant.now());
        designShopCycleRepository.save(cycle);
        log.info("DesignShopOrchestrationService: started design cycle for project {} (draft {})",
                project.getId(), result.repoDraftPath());
    }

    private void advanceAwaitingReview(ProjectEntity project, DesignShopCycleEntity cycle) {
        String draftPath = cycle.getDraftPath();
        if (draftPath == null || draftPath.isBlank()) {
            cycle.setStage(DesignShopCycleEntity.STAGE_DONE);
            designShopCycleRepository.save(cycle);
            return;
        }
        String basename = draftPath.startsWith(DesignAssetService.DESIGN_DRAFT_ROOT + "/")
                ? draftPath.substring(DesignAssetService.DESIGN_DRAFT_ROOT.length() + 1)
                : draftPath;
        String approvedPath = DesignAssetService.DESIGN_APPROVED_ROOT + "/" + basename;

        boolean approved = gitHubPullRequestService
                .fetchFileBytes(project, project.getDefaultBranch(), approvedPath + "/mockup.html")
                .isPresent();

        if (approved) {
            projectFlowService.dispatchDesignImplementation(project, approvedPath,
                    "Implement the design shop's approved mockup for this round.");
            cycle.setStage(DesignShopCycleEntity.STAGE_DONE);
            cycle.setUpdatedAt(Instant.now());
            designShopCycleRepository.save(cycle);
            log.info("DesignShopOrchestrationService: design {} approved for project {}; dispatched implementation task",
                    approvedPath, project.getId());
            return;
        }

        if (Duration.between(cycle.getUpdatedAt(), Instant.now()).compareTo(AWAITING_REVIEW_TIMEOUT) > 0) {
            log.warn("DesignShopOrchestrationService: design review for {} in project {} timed out unpromoted after {}h; closing cycle",
                    draftPath, project.getId(), AWAITING_REVIEW_TIMEOUT.toHours());
            cycle.setStage(DesignShopCycleEntity.STAGE_DONE);
            cycle.setUpdatedAt(Instant.now());
            designShopCycleRepository.save(cycle);
        }
    }
}
