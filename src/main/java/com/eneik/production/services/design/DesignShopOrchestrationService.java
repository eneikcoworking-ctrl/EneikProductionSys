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
    private final com.eneik.production.repositories.WishlistRepository wishlistRepository;

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
                                           com.eneik.production.repositories.WishlistRepository wishlistRepository,
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
        this.wishlistRepository = wishlistRepository;
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

    /**
     * Whether the committed draft actually carries HTML a Jules session could implement pixel-perfect
     * against - the property this stage needs, asked directly instead of inferred from the generator's
     * name. `mockup.html` is the exact filename `DesignAssetService.commitDraftToGitHub` writes and
     * `JulesDispatchService.completeDesignReview` later promotes, so its presence is the whole criterion.
     *
     * A read failure returns false, which routes to the wrong-kind branch rather than to a retry: if the
     * file cannot be read there is nothing for the next stage to consume either way, and a retry that
     * cannot observe its own precondition is the loop this method exists to prevent.
     */
    boolean hasImplementableHtml(ProjectEntity project, String repoDraftPath) {
        try {
            // fetchFileBytes, not fetchFileContent: this is the accessor the rest of this class already
            // uses for this exact file (captureBaseline reads the same mockup.html to extract tokens), so
            // the property is read one way throughout instead of two.
            return gitHubPullRequestService
                    .fetchFileBytes(project, "main", repoDraftPath + "/mockup.html")
                    .filter(bytes -> bytes.length > 0)
                    .isPresent();
        } catch (Exception e) {
            log.warn("DesignShopOrchestrationService: could not read {}/mockup.html for project {}: {}",
                    repoDraftPath, project.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Makes a wrong-kind generation visible instead of leaving it as a warning nobody reads.
     *
     * Deliberately a wishlist rather than a silent counter: a degraded generator is a real product-affecting
     * condition - the project simply stops getting design rounds - and the flow's own way of surfacing such
     * a thing is to create work for it. Never throws: a failure to record a degradation must not become a
     * second failure on top of the first.
     */
    private void recordUnusableDraftConcern(ProjectEntity project, DesignAssetService.DesignAssetResult result) {
        try {
            // One per project: the condition is a degraded generator, not a per-attempt event, so a second
            // identical row would be the same unbounded repetition in another form.
            if (wishlistRepository.existsByProjectIdAndSource(project.getId(),
                    com.eneik.production.models.persistence.WishlistSource.design_review_concern_pattern)) {
                return;
            }
            com.eneik.production.models.persistence.WishlistEntity wishlist =
                    new com.eneik.production.models.persistence.WishlistEntity();
            wishlist.setProjectId(project.getId());
            wishlist.setSource(com.eneik.production.models.persistence.WishlistSource.design_review_concern_pattern);
            wishlist.setStatus(com.eneik.production.models.persistence.WishlistStatus.pending);
            wishlist.setLeanValue(com.eneik.production.models.persistence.LeanValue.essential);
            wishlist.setCynefinDomain("clear");
            wishlist.setContent("Design generation produced no implementable HTML: the generator answered "
                    + "with model=" + result.model() + " and message=\"" + result.message() + "\", and the "
                    + "committed draft carries no mockup.html for a session to implement against. The design "
                    + "shop cannot proceed for this project until generation returns an implementable mockup. "
                    + "Not retried automatically - retrying cannot change the kind of artifact a generator "
                    + "produces.");
            wishlist.setJtbd("When the design generator degrades to producing raw images, I want that "
                    + "surfaced as work rather than repeated silently, so the project stops losing design "
                    + "rounds without anyone knowing");
            wishlistRepository.save(wishlist);
        } catch (Exception e) {
            log.warn("DesignShopOrchestrationService: could not record unusable-draft concern for project {}: {}",
                    project.getId(), e.getMessage());
        }
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

        // What this stage needs is a mockup a Jules session can implement pixel-perfect against, and that
        // JulesDispatchService.completeDesignReview's promotion step can find: implementable HTML at a known
        // path. That is a property OF THE ARTIFACT. The previous gate asked instead WHO PRODUCED IT -
        // `"stitch".equals(result.model())` - which is a proxy, and proxies fail in both directions: a
        // future model emitting real HTML would be rejected on its name, and Stitch emitting a bare image
        // would be accepted on its name.
        //
        // The failure branch then assumed transience: "generation failures ... are exactly the kind of thing
        // that resolves itself on retry". That is a modal claim and it is false for one of the two failure
        // kinds. Retrying changes WHICH WORLD we are in - the service may be free next time - but it cannot
        // change WHAT KIND of thing was produced. Confirmed live on test-forty-sixth 2026-08-15: six
        // identical rejections across four hours (10:24, 11:15, 11:24, 13:40, 13:45, 13:54), each one a real
        // generation call, with `model=gemini-3.1-flash-image` and `message=Generated design asset and
        // metadata.` - the generation SUCCEEDED every time and was discarded on the model's name.
        //
        // So failures are split by modality, and the loop closes because no quantity of retries changes the
        // kind of a thing - not because a counter caps it.
        boolean generationReached = result.available();
        boolean implementableDraft = result.repoDraftPath() != null && !result.repoDraftPath().isBlank()
                && hasImplementableHtml(project, result.repoDraftPath());

        if (!generationReached) {
            // Unavailable: about THIS world - rate limit, timeout, transport. Retrying is rational, so the
            // claim is released and the next tick tries again while readiness still holds.
            self.releaseStartCycleClaim(project.getId());
            log.warn("DesignShopOrchestrationService: design generation unavailable for project {} (model={}, message={}); "
                            + "retrying next tick",
                    project.getId(), result.model(), result.message());
            return;
        }
        if (!implementableDraft) {
            // Wrong kind: the generator answered, and what it produced is not the kind of artifact this
            // stage consumes. No retry - the same call would produce the same kind. Recorded as a concern so
            // the degradation is visible instead of being an endless warning nobody reads.
            self.releaseStartCycleClaim(project.getId());
            recordUnusableDraftConcern(project, result);
            log.warn("DesignShopOrchestrationService: design generation for project {} produced no implementable HTML "
                            + "(model={}, draftPath={}, message={}); NOT retrying - retrying cannot change the kind of "
                            + "artifact produced. Recorded for review.",
                    project.getId(), result.model(), result.repoDraftPath(), result.message());
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
