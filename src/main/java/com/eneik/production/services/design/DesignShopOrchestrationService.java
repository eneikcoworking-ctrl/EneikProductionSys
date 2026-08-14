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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
    private final DesignConsistencyAuditService consistencyAuditService;

    // Self-injected proxy reference (2026-08-14, same pattern/reason as JulesDispatchService.self): a plain
    // `this.claimStartCycle(...)` call bypasses the Spring AOP proxy entirely, so @Transactional on it
    // would silently never activate. @Lazy breaks the constructor circular dependency this would otherwise
    // create.
    private final DesignShopOrchestrationService self;

    public DesignShopOrchestrationService(ProjectRepository projectRepository,
                                           DesignShopCycleRepository designShopCycleRepository,
                                           ClientDeliverableReadinessService readinessService,
                                           DesignAssetService designAssetService,
                                           ProjectFlowService projectFlowService,
                                           ProjectOperationalContextService contextService,
                                           GitHubPullRequestService gitHubPullRequestService,
                                           SystemSettingsService settingsService,
                                           DesignConsistencyAuditService consistencyAuditService,
                                           @org.springframework.context.annotation.Lazy DesignShopOrchestrationService self) {
        this.projectRepository = projectRepository;
        this.designShopCycleRepository = designShopCycleRepository;
        this.readinessService = readinessService;
        this.designAssetService = designAssetService;
        this.projectFlowService = projectFlowService;
        this.contextService = contextService;
        this.gitHubPullRequestService = gitHubPullRequestService;
        this.settingsService = settingsService;
        this.consistencyAuditService = consistencyAuditService;
        this.self = self;
    }

    // 2026-08-14 (bug-hunt sweep): used to be @Transactional, holding a DB transaction open across the
    // whole activeProjects loop AND, per project reaching a readiness edge, DesignAssetService.
    // generateAsset's real Stitch generation call - which itself polls with up to 20x Thread.sleep(15_000)
    // (DesignAssetService.generateViaStitch), i.e. up to 5 real minutes with a connection held open. Worst
    // instance of this bug class found tonight. No longer @Transactional here: every DB read/write below
    // flows through an explicit designShopCycleRepository.save(cycle) or taskRepository.save(...) call
    // (never implicit dirty-checking against a still-open transaction), so each one safely gets its own
    // short auto-transaction (Spring Data JPA default) with no transaction spanning the Stitch call.
    //
    // 2026-08-14 (bug-hunt sweep, separate bug, follow-up pass): this method takes no row lock and (until
    // now) had no per-project dispatch cooldown - if a tick() run took long enough to still be processing a
    // project when the next 5-minute cron fired (very plausible given the sleep-polling above), two
    // concurrent invocations could both read the same DesignShopCycleEntity with lastWasReady=false and
    // both call startCycle, dispatching two real design reviews for the same round. Closed with an atomic
    // compare-and-swap claim (DesignShopCycleEntity.startCycleClaimedAt, V98 migration) - see
    // processProject/claimStartCycle/releaseStartCycleClaim.
    @Scheduled(cron = "${design-shop.cron:0 */5 * * * ?}")
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
        DesignShopCycleEntity cycle = self.ensureCycleRow(project.getId());

        if (DesignShopCycleEntity.STAGE_AWAITING_REVIEW.equals(cycle.getStage())) {
            advanceAwaitingReview(project, cycle);
            return;
        }

        ClientDeliverableReadinessService.Readiness readiness = readinessService.computeForProject(project.getId());
        boolean isReady = readiness.decompositionComplete() && readiness.ratio() >= 1.0;

        if (isReady && !cycle.isLastWasReady()) {
            // 2026-08-14 (bug-hunt sweep, V98 migration): atomic claim closing a genuine double-dispatch
            // race - see this class's own tick() javadoc for the mechanism (overlapping tick() runs, up to
            // 5 real minutes of Stitch sleep-polling per attempt). Only the winner proceeds to startCycle;
            // the loser does nothing this tick (not even a log-worthy event - this is the routine, expected
            // outcome of two ticks racing, not a failure).
            if (self.claimStartCycle(project.getId()) == 0) {
                return;
            }
            try {
                startCycle(project, cycle, readiness);
            } catch (RuntimeException e) {
                // Matches the "no usable Stitch draft" branch inside startCycle: an unexpected exception
                // must not permanently strand this project's claim either, or every later tick would keep
                // seeing it as "in flight" forever even though nothing is actually running.
                self.releaseStartCycleClaim(project.getId());
                throw e;
            }
        } else if (!isReady && cycle.isLastWasReady()) {
            cycle.setLastWasReady(false);
            cycle.setUpdatedAt(Instant.now());
            designShopCycleRepository.save(cycle);
        }
    }

    // Idempotent get-or-create for this project's one cycle row (project_id is UNIQUE - see V93
    // migration). Two overlapping ticks racing to create the SAME brand-new project's first-ever row is
    // the only case this needs to handle: the loser's insert violates the unique constraint, caught here
    // and resolved by simply reading the winner's now-committed row instead.
    @Transactional
    DesignShopCycleEntity ensureCycleRow(UUID projectId) {
        return designShopCycleRepository.findByProjectId(projectId).orElseGet(() -> {
            DesignShopCycleEntity fresh = new DesignShopCycleEntity();
            fresh.setProjectId(projectId);
            try {
                return designShopCycleRepository.saveAndFlush(fresh);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                return designShopCycleRepository.findByProjectId(projectId)
                        .orElseThrow(() -> e);
            }
        });
    }

    @Transactional
    int claimStartCycle(UUID projectId) {
        return designShopCycleRepository.claimStartCycle(projectId, Instant.now());
    }

    @Transactional
    void releaseStartCycleClaim(UUID projectId) {
        designShopCycleRepository.releaseStartCycleClaim(projectId);
    }

    private void startCycle(ProjectEntity project, DesignShopCycleEntity cycle,
                             ClientDeliverableReadinessService.Readiness readiness) {
        String brief = "Full-product design refresh for " + project.getName() + " - round complete ("
                + readiness.completeFeatures() + "/" + readiness.totalFeatures() + " features delivered).";
        var context = contextService.build(project.getId(), project.getName());

        // Baseline bootstrap (2026-08-11): the FIRST generation for a project has no established
        // canonical palette to compare against (confirmed live 2026-08-10 - the factory's own "Verdant
        // Flow" tokens were wrongly used as a stand-in, false-rejecting a correct client screen), so it
        // stays un-audited, same as before. Every LATER generation reuses the real tokens captured from
        // that first screen instead - E(f) then checks against this project's own actual brand.
        boolean hasBaseline = cycle.getDeclaredColors() != null && !cycle.getDeclaredColors().isBlank();
        DesignAssetService.DesignAssetResult result = hasBaseline
                ? designAssetService.generateAsset(project, context, brief, "mockup", "fast", false,
                        null, cycle.declaredColorsList(), cycle.declaredFontsList())
                : designAssetService.generateAsset(project, context, brief, "mockup", "fast", false);

        // Stitch-only for this stage (operator directive 2026-08-10): the nano-banana fallback produces a
        // raw image with no HTML/CSS a Jules session could implement pixel-perfect against, and no
        // mockup.html for JulesDispatchService.completeDesignReview's promotion step to find - it exists
        // for future content-asset generation, not the design shop's mockup pipeline.
        if (!result.available() || !"stitch".equals(result.model())
                || result.repoDraftPath() == null || result.repoDraftPath().isBlank()) {
            // Left lastWasReady=false so the next tick retries while readiness is still true, instead of
            // silently losing this round - generation failures (rate limits, transient Stitch errors,
            // a nano-banana fallback we don't want) are exactly the kind of thing that resolves itself on
            // retry. The start-cycle claim must be released too, or that retry would be silently blocked
            // by an attempt that never actually finished.
            self.releaseStartCycleClaim(project.getId());
            log.warn("DesignShopOrchestrationService: no usable Stitch draft for project {} (model={}, message={}); will retry next tick",
                    project.getId(), result.model(), result.message());
            return;
        }

        if (!hasBaseline) {
            captureBaseline(project, cycle, result);
        }

        projectFlowService.dispatchDesignReview(project, result.repoDraftPath(), brief);

        cycle.setLastWasReady(true);
        cycle.setStage(DesignShopCycleEntity.STAGE_AWAITING_REVIEW);
        cycle.setDraftPath(result.repoDraftPath());
        cycle.setEditIterationCount(0);
        cycle.setUpdatedAt(Instant.now());
        designShopCycleRepository.save(cycle);
        log.info("DesignShopOrchestrationService: started design cycle for project {} (draft {})",
                project.getId(), result.repoDraftPath());
    }

    /** Fixes this project's canonical Tokens(f) domain from what Stitch actually produced on its first
     * screen - a fact, not an invention (see BARCAN-TAG-11's E(f): TraceRatio needs Tokens(f) to exist).
     * Best-effort: if the committed HTML can't be re-fetched, the baseline simply stays unset and the
     * next cycle tries again un-audited rather than ever failing the cycle over this. */
    private void captureBaseline(ProjectEntity project, DesignShopCycleEntity cycle, DesignAssetService.DesignAssetResult result) {
        cycle.setStitchProjectId(result.stitchProjectId());
        cycle.setStitchScreenId(result.stitchScreenId());
        gitHubPullRequestService.fetchFileBytes(project, project.getDefaultBranch(), result.repoDraftPath() + "/mockup.html")
                .ifPresent(bytes -> {
                    var used = consistencyAuditService.extractUsedTokens(new String(bytes, StandardCharsets.UTF_8));
                    cycle.setDeclaredColors(String.join(",", used.colors()));
                    cycle.setDeclaredFonts(String.join(",", used.fonts()));
                    log.info("DesignShopOrchestrationService: captured design baseline for project {} - colors={} fonts={}",
                            project.getId(), used.colors(), used.fonts());
                });
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
