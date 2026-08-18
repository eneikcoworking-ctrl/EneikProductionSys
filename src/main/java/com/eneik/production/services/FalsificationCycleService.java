package com.eneik.production.services;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.*;
import com.eneik.production.services.settings.SystemSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class FalsificationCycleService {
    private static final Logger log = LoggerFactory.getLogger(FalsificationCycleService.class);

    private static final int MAX_MERGED_PRS_PER_AUDIT = 5;
    private static final int MAX_DIFF_CHARS_PER_PR = 6000;
    // Live incident, 2026-08-05 (test-forty-first): buildAuditPrompt's client-brief section had no size
    // bound at all, unlike the diff-fetching logic right above it (properly capped via MAX_DIFF_CHARS_PER_PR
    // * MAX_MERGED_PRS_PER_AUDIT). A project that accumulates many client-sourced wishlist rows over time
    // (confirmed: 43 for this project) got every one of them concatenated in full, unbounded, into a single
    // audit prompt - measured live at 2,680,368 characters for one dispatch. Jules's API correctly rejected
    // it (HTTP 400 INVALID_ARGUMENT: "Request contains an invalid argument"), and the account-health logic
    // then misread that single oversized-payload rejection as "the whole account is blocked", disabling
    // dispatch for the account's other 14 legitimate concurrent slots too. Capped the same way diffs
    // already are - same size class as MAX_DIFF_CHARS_PER_PR, this is prose text not code, no reason for a
    // different order of magnitude.
    private static final int MAX_CLIENT_BRIEF_CHARS_TOTAL = 20000;

    // Philosophical falsification track (2026-07-25, operator directive): the formal track above answers
    // "does the shipped code contradict its own charters?" - this answers "is the shipped PRODUCT genuinely
    // what users need?", per philosopher (up to 13 roles x 6 philosophers = 78 voices total, covered ONE
    // role's 6 philosophers per audit dispatch - see the rotation cursor in executePhilosophicalCycleForProject
    // - not all 13 roles in a single request), evaluated in Kano terms. Deliberately generative, not
    // corrective - see WishlistSource.philosophical_falsification and applyPhilosophicalCritiques below for
    // why it cannot share self_falsification's gating/dedup/Cynefin semantics.
    //
    // These two numbers are deliberately NOT the noise-control mechanism - clustering (see
    // applyPhilosophicalCritiques/WishlistContentSimilarityMatcher.clusterBySimilarity) is: converging
    // voices merge into one wishlist per theme instead of any voice being individually judged and discarded.
    // They exist purely as a last-resort safety net for a genuinely degenerate run (e.g. clustering produces
    // far more orthogonal themes than normal), generous enough to almost never bind in practice.
    private static final int MAX_PHILOSOPHICAL_PROPOSALS_PER_RUN = 8;
    private static final int MAX_PENDING_PHILOSOPHICAL_WISHLISTS = 10;

    // 2026-08-07 fix (RAG role-context unification): replaces raw, unbounded per-role charter/philosopher-
    // pattern file reads (readRawRules + inline directory globs, now removed) with GeminiContextService's
    // already-indexed corpus. Code-defect audit covers all ~13 active roles in one request, so its per-role
    // budget is tighter; the philosophical track batches only a few roles per turn, so it can afford more.
    private static final int CODE_DEFECT_AUDIT_ROLE_CONTEXT_TOP_K = 4;
    private static final int PHILOSOPHICAL_AUDIT_ROLE_CONTEXT_TOP_K = 6;
    // 2026-08-11 (client runtime observability plan, Phase 5): caps how much of a live-fetched page gets
    // spliced into the audit prompt - same order of magnitude as MAX_DIFF_CHARS_PER_PR above, a real page
    // body can be arbitrarily large and this is meant as grounding evidence, not the whole document.
    private static final int LIVE_EVIDENCE_MAX_CHARS = 6000;

    private final ProjectRepository projectRepository;
    private final RoleRepository roleRepository;
    private final RoleCapabilityLoader roleCapabilityLoader;
    private final WishlistRepository wishlistRepository;
    private final FalsificationRunRepository falsificationRunRepository;
    private final SystemSettingsService settingsService;

    // 2026-08-14: the market corpus reached decomposition but not the audit, so the flow could plan a
    // legal duty and never check whether the built product actually has it. The audit is the better place
    // to catch that, because it looks at the RUNNING product rather than at a plan - a missing Impressum
    // is visible on the real page in a way it never is in a task description.
    //
    // Injected as an optional field rather than a constructor parameter on purpose: this class is built by
    // hand in 15 places across its own test, and widening the constructor for an optional read-only
    // dependency would mean touching all of them for no behavioural gain. Null when absent - the prompt
    // then simply carries no regulatory section, exactly as before this existed.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.eneik.production.services.market.MarketCorpusService marketCorpusService;
    private final com.eneik.production.services.github.GitHubPullRequestService gitHubPullRequestService;
    private final com.eneik.production.services.ProjectFlowService projectFlowService;
    private final ClientDeliverableReadinessService readinessService;
    private final WishlistContentSimilarityMatcher wishlistContentSimilarityMatcher;
    private final GeminiContextService geminiContextService;
    private final TaskRepository taskRepository;
    private final JulesSessionRepository julesSessionRepository;
    private final PersistentWorkerSessionService persistentWorkerSessionService;
    private final com.eneik.production.repositories.PrReviewRepository prReviewRepository;
    private final com.eneik.production.repositories.CodeIntegrityFindingRepository codeIntegrityFindingRepository;
    private final com.eneik.production.repositories.EvidenceNodeRepository evidenceNodeRepository;
    private final com.eneik.production.services.runtime.ClientRuntimeObservabilityService clientRuntimeObservabilityService;
    private final com.eneik.production.services.runtime.RuntimeLauncherClient runtimeLauncherClient;

    @org.springframework.beans.factory.annotation.Value("${falsification.readiness-threshold:0.9}")
    private double readinessThreshold;

    // 2026-07-26 operator directive ("2 70% достаточно. не 90%. первый раз провести на 90%. потом раз в 2
    // дня, но с 70%"): the philosophical track's own two-tier bar - separate from readinessThreshold above,
    // which stays 90% for the formal/corrective cycle only. A project's FIRST philosophical run still
    // requires 90% (there should be something substantial worth critiquing before the very first pass), but
    // every run after that only needs 70% - waiting for near-total completion every 2 days meant the cycle
    // almost never actually fired in practice (coverage-audit re-triggers kept resetting readiness before it
    // could stay above 90% long enough).
    @org.springframework.beans.factory.annotation.Value("${philosophical-falsification.first-run-readiness-threshold:0.9}")
    private double philosophicalFirstRunReadinessThreshold;

    @org.springframework.beans.factory.annotation.Value("${philosophical-falsification.subsequent-run-readiness-threshold:0.7}")
    private double philosophicalSubsequentRunReadinessThreshold;

    public FalsificationCycleService(ProjectRepository projectRepository,
                                     RoleRepository roleRepository,
                                     RoleCapabilityLoader roleCapabilityLoader,
                                     WishlistRepository wishlistRepository,
                                     FalsificationRunRepository falsificationRunRepository,
                                     SystemSettingsService settingsService,
                                     com.eneik.production.services.github.GitHubPullRequestService gitHubPullRequestService,
                                     @org.springframework.context.annotation.Lazy com.eneik.production.services.ProjectFlowService projectFlowService,
                                     ClientDeliverableReadinessService readinessService,
                                     WishlistContentSimilarityMatcher wishlistContentSimilarityMatcher,
                                     GeminiContextService geminiContextService,
                                     TaskRepository taskRepository,
                                     JulesSessionRepository julesSessionRepository,
                                     PersistentWorkerSessionService persistentWorkerSessionService,
                                     com.eneik.production.repositories.PrReviewRepository prReviewRepository,
                                     com.eneik.production.repositories.CodeIntegrityFindingRepository codeIntegrityFindingRepository,
                                     com.eneik.production.repositories.EvidenceNodeRepository evidenceNodeRepository,
                                     com.eneik.production.services.runtime.ClientRuntimeObservabilityService clientRuntimeObservabilityService,
                                     com.eneik.production.services.runtime.RuntimeLauncherClient runtimeLauncherClient) {
        this.projectRepository = projectRepository;
        this.roleRepository = roleRepository;
        this.roleCapabilityLoader = roleCapabilityLoader;
        this.wishlistRepository = wishlistRepository;
        this.falsificationRunRepository = falsificationRunRepository;
        this.settingsService = settingsService;
        this.gitHubPullRequestService = gitHubPullRequestService;
        this.projectFlowService = projectFlowService;
        this.readinessService = readinessService;
        this.wishlistContentSimilarityMatcher = wishlistContentSimilarityMatcher;
        this.geminiContextService = geminiContextService;
        this.taskRepository = taskRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.persistentWorkerSessionService = persistentWorkerSessionService;
        this.prReviewRepository = prReviewRepository;
        this.codeIntegrityFindingRepository = codeIntegrityFindingRepository;
        this.evidenceNodeRepository = evidenceNodeRepository;
        this.clientRuntimeObservabilityService = clientRuntimeObservabilityService;
        this.runtimeLauncherClient = runtimeLauncherClient;
    }

    @Scheduled(cron = "${falsification-cycle.cron:0 0 2 * * ?}")
    public void runDailyFalsificationCycle() {
        if (!settingsService.effectiveBoolean("falsification_cycle_enabled")) {
            log.info("FalsificationCycleService: Falsification cycle is disabled via feature flag.");
            return;
        }

        log.info("FalsificationCycleService: Starting daily falsification cycle check...");
        List<ProjectEntity> projects = projectRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProjectStatus.active)
                .toList();

        for (ProjectEntity project : projects) {
            try {
                executeCycleForProject(project);
            } catch (Exception e) {
                log.error("FalsificationCycleService: Failed for project {}: {}", project.getId(), e.getMessage(), e);
            }
        }
    }

    // Separate, less frequent cron from the formal cycle above (operator directive, 2026-07-25, cadence
    // revised 2026-07-26 - "потом раз в 2 дня": weekly-only meant the cycle essentially never actually ran,
    // since readiness kept getting reset by coverage-audit re-triggers between Sundays). 03:00 still
    // deliberately never collides with the formal cron's hour, since both dispatch through the same reserved
    // eneikdru compiler-account capacity (dispatchCompilerTask).
    /**
     * 2026-08-09 (live incident, operator-flagged: a genuinely in-progress multi-turn discussion sat idle
     * for 4.5+ hours between turns): "how often should a brand-new 13-role discussion be allowed to start"
     * (a deliberate operator choice, 2026-07-25/26, "раз в 2 дня") and "how often should an ALREADY-STARTED
     * discussion be allowed to advance by one more role-batch" got conflated into the same entry point once
     * the 2026-08-03 persistent-multi-turn-worker rewrite landed - an integration oversight between two
     * decisions made on different days, not a deliberate throttle. continuePhilosophicalDiscussion is
     * already safe to call often on its own terms (dispatchToPhilosophicalAuditPersistentWorker no-ops if
     * the worker is still mid-turn, and follow-up turns are deliberately NOT re-gated by readiness/pending
     * thresholds - see continuePhilosophicalDiscussion's own comment). This runs far more often than the
     * "start a new discussion" cron below and ONLY ever continues a worker that already exists - it never
     * starts a new 13-role sweep, so the operator's original every-2-days intent for THAT decision is
     * untouched.
     *
     * 2026-08-09 addendum, found live within minutes of this cron's own first deploy (operator: "не сломай,
     * это ядро"): Jules's raw self-reported status can still read "pr_opened" (stale, unchanged) for a real
     * few seconds immediately after we just sent it a brand-new follow-up message - pollStatus's edge
     * detector (oldStatus="revising" from sendFollowUpMessage -> mappedStatus="pr_opened" from that stale
     * read) cannot tell that apart from Jules genuinely having finished the new batch already, and fires
     * completePersistentWorkerCycle for real - which unconditionally clears the "batch in flight" marker
     * (PersistentWorkerSessionService.consumeCurrentBatch), making the worker look idle-and-fresh again
     * within seconds. Confirmed live: a "turn complete, 9 of 13 covered" log line appeared 9 seconds after
     * dispatch, while the real GitHub PR still showed zero new commits since 03:08 - the covered-role
     * bookkeeping (appendCoveredPhilosophicalAuditRoles, written at DISPATCH time per its own javadoc) had
     * raced ahead of Jules's real work. Left unguarded, this fast cron could re-fire every 15 minutes
     * against the same false-idle signal, inflating the covered-role count to 13/13 before Jules genuinely
     * wrote most of the real critique content, triggering a premature close/synthesis on an incomplete
     * report. MIN_MINUTES_BETWEEN_PHILOSOPHICAL_TURNS is a deliberately simple, targeted guard (only on this
     * new code path, not the shared PersistentWorkerSessionService.isIdleAndFresh used by the other two
     * persistent-worker purposes, which are driven by their own separate cadences and aren't known to have
     * this exposure) - gives Jules a realistic minimum window to actually respond before the next follow-up
     * can be sent, regardless of how early a stale-status edge makes the worker look idle again. Does not fix
     * the underlying stale-poll race itself (a deeper, riskier change to pollStatus's edge detection, not
     * attempted here under time pressure) - only bounds its worst consequence.
     */
    private static final int MIN_MINUTES_BETWEEN_PHILOSOPHICAL_TURNS = 20;

    @Scheduled(cron = "${philosophical-falsification-continuation.cron:0 */15 * * * ?}")
    public void advanceInProgressPhilosophicalDiscussions() {
        if (!settingsService.effectiveBoolean("philosophical_falsification_enabled")) {
            return;
        }
        List<RoleEntity> activeRoles = roleRepository.findAll().stream()
                .filter(RoleEntity::isActive)
                .toList();
        if (activeRoles.isEmpty()) {
            return;
        }
        List<ProjectEntity> projects = projectRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProjectStatus.active)
                .toList();
        for (ProjectEntity project : projects) {
            try {
                persistentWorkerSessionService.findActiveWorker(project.getId(), PersistentWorkerPurpose.PHILOSOPHICAL_AUDIT)
                        .ifPresent(worker -> {
                            if (worker.getLastMessageSentAt() != null
                                    && worker.getLastMessageSentAt().isAfter(
                                            Instant.now().minus(java.time.Duration.ofMinutes(MIN_MINUTES_BETWEEN_PHILOSOPHICAL_TURNS)))) {
                                log.info("FalsificationCycleService: Philosophical-audit worker {} last messaged too recently "
                                                + "({}); giving Jules more real time before the next follow-up",
                                        worker.getId(), worker.getLastMessageSentAt());
                                return;
                            }
                            continuePhilosophicalDiscussion(project, worker, activeRoles);
                        });
            } catch (Exception e) {
                log.error("FalsificationCycleService: Failed to advance in-progress philosophical discussion for project {}: {}",
                        project.getId(), e.getMessage(), e);
            }
        }
    }

    @Scheduled(cron = "${philosophical-falsification.cron:0 0 3 */2 * ?}")
    public void runWeeklyPhilosophicalFalsificationCycle() {
        // Feature-flag check lives inside executePhilosophicalCycleForProject (not here), so the manual
        // admin-trigger endpoint (ProjectController) enforces the same kill switch as the cron instead of
        // silently bypassing it - a single source of truth for "should this run at all" regardless of caller.
        log.info("FalsificationCycleService: Starting philosophical falsification cycle check...");
        List<ProjectEntity> projects = projectRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProjectStatus.active)
                .toList();

        for (ProjectEntity project : projects) {
            try {
                executePhilosophicalCycleForProject(project);
            } catch (Exception e) {
                log.error("FalsificationCycleService: Philosophical cycle failed for project {}: {}", project.getId(), e.getMessage(), e);
            }
        }
    }

    private long pendingPhilosophicalWishlistCount(UUID projectId) {
        return wishlistRepository.countByProjectIdAndSourceAndStatus(
                projectId, WishlistSource.philosophical_falsification, WishlistStatus.pending);
    }

    public record PhilosophicalReadinessInfo(double applicableThreshold, boolean hasRunBefore) {
    }

    /**
     * Exposed (2026-07-26, operator directive: cheap observer improvements after live test-thirty-eighth
     * findings) so {@link GeminiProjectObserverService} can tell the observer the actual gate threshold
     * up front, instead of her repeatedly proposing {@code triggerFalsificationRun} and discovering the
     * same "26% < 90%" skip every cycle (confirmed live: she retried it twice in a row, 08:00 and 09:00,
     * with identical reasoning each time). Single source of truth shared with the real gate check below -
     * never duplicate the threshold-selection logic.
     */
    public PhilosophicalReadinessInfo philosophicalReadinessInfo(ProjectEntity project) {
        boolean hasRunBefore = wishlistRepository.existsByProjectIdAndSource(
                project.getId(), WishlistSource.philosophical_falsification);
        double applicableThreshold = hasRunBefore
                ? philosophicalSubsequentRunReadinessThreshold
                : philosophicalFirstRunReadinessThreshold;
        return new PhilosophicalReadinessInfo(applicableThreshold, hasRunBefore);
    }

    /**
     * The regular (code-defect) self_falsification cycle's own readiness threshold - exposed for the same
     * reason as philosophicalReadinessInfo above, so GeminiProjectObserverService can show her this gate's
     * real state instead of it being invisible to her (2026-08-06: she had no way to see or trigger this
     * cycle at all before - only the philosophical track had a Gemini-callable tool, so a project stuck
     * behind THIS gate - see Readiness.selfFalsificationReadyRatio's own javadoc - had no path to recovery
     * except a human noticing and waiting for the daily cron).
     */
    public double codeDefectReadinessThreshold() {
        return readinessThreshold;
    }

    /**
     * Public so the manual-trigger endpoint (ProjectController) can run this out-of-cycle without waiting
     * for the weekly cron - same "force" idiom already used elsewhere in this codebase for the onboarding
     * report re-run.
     */
    // 2026-08-03: batch size for the multi-turn discussion below. Per-role payload measured at ~128-129KB
    // (charter + 6 philosopher pattern files); 3 roles plus the ~25KB common file and a compact prior-turns
    // digest stays comfortably under the ~1.7MB size that made the old all-13-roles-at-once request fail
    // (see the incident note this replaces, kept on PersistentWorkerPurpose.PHILOSOPHICAL_AUDIT's javadoc),
    // while keeping a full 13-role sweep to 5 turns instead of 13.
    private static final int PHILOSOPHICAL_AUDIT_ROLE_BATCH_SIZE = 3;

    public void executePhilosophicalCycleForProject(ProjectEntity project) {
        executePhilosophicalCycleForProject(project, false);
    }

    /**
     * 2026-08-03 rewrite: a philosophical audit is now one continuous, multi-turn Jules session per project
     * (a persistent worker, PersistentWorkerPurpose.PHILOSOPHICAL_AUDIT) rather than a single one-shot
     * dispatch - see PersistentWorkerPurpose's javadoc for the live incident (test-forty-first) this
     * replaces: cramming every active role's full charter+philosopher content into one ~1.7MB request got
     * flatly rejected by Jules's API, and a first fix (isolated one-role sessions) accidentally destroyed
     * the track's actual point - philosophers genuinely discussing the same product together, each turn
     * able to see and respond to everything earlier turns already said. This method now just decides, each
     * cycle, whether to start a brand new discussion or advance an existing one by one role-batch.
     */
    public void executePhilosophicalCycleForProject(ProjectEntity project, boolean force) {
        if (!settingsService.effectiveBoolean("philosophical_falsification_enabled")) {
            log.info("FalsificationCycleService: Philosophical falsification track is disabled via feature flag; skipping project {}",
                    project.getName());
            return;
        }

        List<RoleEntity> activeRoles = roleRepository.findAll().stream()
                .filter(RoleEntity::isActive)
                .toList();
        if (activeRoles.isEmpty()) {
            return;
        }

        java.util.Optional<PersistentWorkerSessionEntity> existingWorkerOpt =
                persistentWorkerSessionService.findActiveWorker(project.getId(), PersistentWorkerPurpose.PHILOSOPHICAL_AUDIT);
        if (existingWorkerOpt.isPresent()) {
            continuePhilosophicalDiscussion(project, existingWorkerOpt.get(), activeRoles);
            return;
        }

        // No discussion in flight: starting a brand new one is gated by the same readiness/pending-proposal
        // checks the old one-shot dispatch used. A follow-up turn of an already-started discussion is
        // deliberately NOT re-gated by these (see continuePhilosophicalDiscussion) - a discussion once
        // begun should be allowed to finish even if readiness or pending-wishlist counts shift mid-way.
        if (!force) {
            PhilosophicalReadinessInfo readinessInfo = philosophicalReadinessInfo(project);
            boolean hasRunBefore = readinessInfo.hasRunBefore();
            double applicableThreshold = readinessInfo.applicableThreshold();

            ClientDeliverableReadinessService.Readiness readiness = readinessService.computeForProject(project.getId());
            // selfFalsificationReadyRatio, not ratio(): a permanently-failed task with no replacement must
            // not keep this gate below threshold forever (see the field's own javadoc - the exact same
            // self-referential deadlock class applies to the philosophical track's own readiness check).
            if (!readiness.decompositionComplete() || readiness.selfFalsificationReadyRatio() < applicableThreshold) {
                log.info("FalsificationCycleService: Project {} not ready for philosophical falsification yet "
                                + "({}% < {}% threshold, {} run); skipping",
                        project.getName(), Math.round(readiness.selfFalsificationReadyRatio() * 100), Math.round(applicableThreshold * 100),
                        hasRunBefore ? "subsequent" : "first");
                return;
            }
        }

        long pendingCount = pendingPhilosophicalWishlistCount(project.getId());
        if (pendingCount >= MAX_PENDING_PHILOSOPHICAL_WISHLISTS) {
            log.info("FalsificationCycleService: Project {} already has {} pending philosophical wishlist item(s) "
                            + "(cap {}); skipping this cycle instead of piling on more unconsumed proposals",
                    project.getName(), pendingCount, MAX_PENDING_PHILOSOPHICAL_WISHLISTS);
            return;
        }

        // TOC subordination (2026-08-11, reliability-strengthening plan): launchability is the real
        // constraint on this whole pipeline (see ProductLaunchabilityService's own javadoc - "everything
        // else is meaningless if the product can't even be started"). A philosophical review of a
        // product that isn't currently launching/healthy would be reasoning about nothing real - only
        // ever gates STARTING a new discussion (never interrupts one already in flight, same discipline
        // as the readiness/pending-count gates above). Reuses the exact same signal Phase 5's
        // liveEvidenceBlock already draws on - never a second, independently-derived source of truth.
        var runtimeHealth = clientRuntimeObservabilityService.summarize(project.getId());
        if (runtimeHealth != null && Boolean.FALSE.equals(runtimeHealth.lastObservationHealthy())) {
            ensureProductNotLaunchableWishlist(project, runtimeHealth);
            log.info("FalsificationCycleService: Project {} is not currently launchable/healthy - "
                            + "subordinating philosophical review to that constraint (TOC) instead of "
                            + "auditing a broken product this cycle",
                    project.getName());
            return;
        }

        List<RoleEntity> firstBatch = activeRoles.subList(0, Math.min(PHILOSOPHICAL_AUDIT_ROLE_BATCH_SIZE, activeRoles.size()));
        String runId = UUID.randomUUID().toString();
        String reportPath = ".eneik/records/philosophical-falsification-" + runId + ".json";
        String screenshotDir = ".eneik/records/philosophical-falsification-" + runId + "-screenshots/";
        String prompt = buildPhilosophicalAuditPrompt(project, firstBatch, activeRoles.size(), reportPath, screenshotDir);

        List<String> roleTags = firstBatch.stream().map(RoleEntity::getTag).toList();
        boolean dispatched = projectFlowService.dispatchToPhilosophicalAuditPersistentWorker(project, roleTags, prompt, reportPath);
        if (!dispatched) {
            log.warn("FalsificationCycleService: Could not start philosophical falsification discussion for project {} this cycle",
                    project.getName());
            return;
        }
        log.info("FalsificationCycleService: Started multi-turn philosophical falsification discussion for project {} - "
                        + "turn 1 covers {} of {} active role(s): {}",
                project.getName(), firstBatch.size(), activeRoles.size(), roleTags);
    }

    /**
     * Advances an already-started discussion by one turn: either the next role-batch (with a digest of
     * every critique reported so far, so the new voices can genuinely respond to earlier ones) or, once
     * every active role has already spoken, the closing synthesis turn. dispatchToPhilosophicalAuditPersistentWorker
     * silently no-ops (returns false) if the worker is currently busy - this cycle just tries again next time.
     */
    private void continuePhilosophicalDiscussion(ProjectEntity project, PersistentWorkerSessionEntity worker, List<RoleEntity> activeRoles) {
        TaskEntity carrierTask = worker.getCarrierTaskId() != null
                ? taskRepository.findById(worker.getCarrierTaskId()).orElse(null) : null;
        if (carrierTask == null) {
            log.warn("FalsificationCycleService: Persistent philosophical-audit worker {} for project {} has no "
                    + "resolvable carrier task; skipping this cycle", worker.getId(), project.getId());
            return;
        }

        // 2026-08-09 (live incident: BARCAN-TAG-12 was asked about at 09:00:14 and the discussion was closed/
        // merged by 09:00:23 - 9 seconds later, with the final archived report never containing a single
        // critique for that role tag). Root cause: "covered" used to be an append-only marker written the
        // moment a role batch was SENT (ProjectFlowService.appendCoveredPhilosophicalAuditRoles, now removed),
        // not when Jules's answer was actually verified to contain it - a fast-enough poll (or the stale-status
        // race MIN_MINUTES_BETWEEN_PHILOSOPHICAL_TURNS only bounds, doesn't fix) could treat a role as spoken
        // before Jules had written one word. Covered is now derived exclusively from what the report file on
        // the branch actually contains right now - no separate bookkeeping to drift out of sync with reality.
        String reportPath = projectFlowService.philosophicalAuditReportPath(carrierTask);
        List<PhilosophicalCritique> priorCritiques = fetchInProgressReportCritiques(project, worker, reportPath);
        java.util.Set<String> covered = priorCritiques.stream()
                .map(PhilosophicalCritique::roleTag)
                .collect(java.util.stream.Collectors.toSet());
        List<RoleEntity> remainingRoles = activeRoles.stream().filter(r -> !covered.contains(r.getTag())).toList();

        String message;
        List<String> nextRoleTags;
        if (remainingRoles.isEmpty()) {
            message = buildPhilosophicalSynthesisPrompt(priorCritiques, reportPath);
            nextRoleTags = List.of();
        } else {
            List<RoleEntity> nextBatch = remainingRoles.subList(0, Math.min(PHILOSOPHICAL_AUDIT_ROLE_BATCH_SIZE, remainingRoles.size()));
            nextRoleTags = nextBatch.stream().map(RoleEntity::getTag).toList();
            message = buildPhilosophicalFollowUpPrompt(nextBatch, priorCritiques, reportPath);
        }

        boolean sent = projectFlowService.dispatchToPhilosophicalAuditPersistentWorker(project, nextRoleTags, message, reportPath);
        if (!sent) {
            log.info("FalsificationCycleService: Philosophical-audit follow-up deferred for project {} this cycle "
                    + "(worker busy or unavailable)", project.getName());
            return;
        }
        if (nextRoleTags.isEmpty()) {
            log.info("FalsificationCycleService: Sent closing-synthesis turn to philosophical-audit discussion for "
                    + "project {} - all {} active role(s) already covered", project.getName(), activeRoles.size());
        } else {
            log.info("FalsificationCycleService: Sent follow-up turn (roles {}) to philosophical-audit discussion "
                            + "for project {} ({} of {} active role(s) covered so far)",
                    nextRoleTags, project.getName(), covered.size(), activeRoles.size());
        }
    }

    /**
     * Mid-discussion peek at the report file this worker's session has been building across all its prior
     * turns, used only to render the "what earlier turns already said" digest for the next follow-up
     * prompt. Deliberately separate from JulesDispatchService.parsePhilosophicalReport (the authoritative
     * final parse used once the discussion closes and critiques get applied to wishlists) - this one is
     * advisory only, feeding a prompt rather than driving any state change, so a parse failure here just
     * means a thinner digest, not a broken cycle.
     */
    private List<PhilosophicalCritique> fetchInProgressReportCritiques(ProjectEntity project,
            PersistentWorkerSessionEntity worker, String reportPath) {
        if (reportPath == null || worker.getCurrentJulesSessionId() == null) {
            return List.of();
        }
        JulesSessionEntity session = julesSessionRepository.findById(worker.getCurrentJulesSessionId()).orElse(null);
        if (session == null || session.getExternalSessionId() == null) {
            return List.of();
        }
        return gitHubPullRequestService.findOpenPullRequestBySession(project, session.getExternalSessionId())
                .map(pr -> parseInProgressPhilosophicalReport(project, pr.headRef(), reportPath))
                .orElseGet(List::of);
    }

    private List<PhilosophicalCritique> parseInProgressPhilosophicalReport(ProjectEntity project, String headRef, String reportPath) {
        java.util.Optional<String> content = gitHubPullRequestService.fetchFileContent(project, headRef, reportPath);
        if (content.isEmpty()) {
            return List.of();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(content.get());
            com.fasterxml.jackson.databind.JsonNode rawCritiques = root.path("critiques");
            if (!rawCritiques.isArray()) {
                return List.of();
            }
            // 2026-08-14: "reverse" added - it was missing, and its absence was a real gap in what this
            // system could even express. Kano's model has five categories, not four: reverse quality is
            // the one where PRESENCE of a feature lowers satisfaction (a mobile app nobody installs, a
            // chat widget nobody opens, forced registration before checkout). Without this word a
            // philosopher could say "this doesn't matter" (indifferent) but had no way to say "this
            // actively harms the product" - so the single most valuable critique a falsification round can
            // produce was silently unrepresentable and got dropped by this very validator.
            java.util.Set<String> validKano = java.util.Set.of("must-be", "performance", "attractive", "indifferent", "reverse");
            List<PhilosophicalCritique> result = new java.util.ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode c : rawCritiques) {
                String roleTag = c.path("roleTag").asText("");
                String philosopher = c.path("philosopher").asText("");
                String proposal = c.path("proposal").asText("");
                String kanoClass = c.path("kanoClass").asText("");
                if (roleTag.isBlank() || philosopher.isBlank() || proposal.isBlank()
                        || kanoClass.isBlank() || !validKano.contains(kanoClass.toLowerCase(java.util.Locale.ROOT))) {
                    continue;
                }
                result.add(new PhilosophicalCritique(roleTag, philosopher, c.path("worldview").asText(""),
                        c.path("critique").asText(""), proposal, c.path("dislike").asText(""), kanoClass,
                        c.path("confidence").asText(""), c.path("evidence").asText(""), c.path("screenshotFile").asText("")));
            }
            return result;
        } catch (Exception e) {
            log.warn("FalsificationCycleService: failed to parse in-progress philosophical report for project {}: {}",
                    project.getId(), e.getMessage());
            return List.of();
        }
    }

    private String renderCritiqueDigest(List<PhilosophicalCritique> critiques) {
        if (critiques.isEmpty()) {
            return "(no critiques reported in earlier turns of this discussion yet)";
        }
        StringBuilder digest = new StringBuilder();
        for (PhilosophicalCritique c : critiques) {
            String proposal = c.proposal() == null ? "" : c.proposal();
            digest.append("- [").append(c.roleTag()).append("] ").append(c.philosopher())
                    .append(" (Kano: ").append(c.kanoClass()).append("): ")
                    .append(proposal.length() <= 220 ? proposal : proposal.substring(0, 220) + "...")
                    .append("\n");
        }
        return digest.toString();
    }

    private String buildPhilosophicalFollowUpPrompt(List<RoleEntity> roleBatch, List<PhilosophicalCritique> priorCritiques, String reportPath) {
        String digest = renderCritiqueDigest(priorCritiques);
        String retrievalQuery = "philosophical product falsification discussion turn - roles: "
                + roleBatch.stream().map(RoleEntity::getTag).reduce("", (a, b) -> a.isEmpty() ? b : a + ", " + b);
        StringBuilder charters = new StringBuilder();
        for (RoleEntity role : roleBatch) {
            charters.append(geminiContextService.buildRoleScopedContext(role, retrievalQuery, PHILOSOPHICAL_AUDIT_ROLE_CONTEXT_TOP_K));
        }

        return """
                This is the next turn of the SAME ongoing philosophical product falsification discussion, not
                a new audit - keep using the understanding of the real product (UI, backend, data model,
                business logic) you already built in your first message this conversation; only re-examine
                something specific if genuinely relevant to these new roles.

                Here is what earlier turns of this discussion already reported, so your new voices can
                genuinely agree, disagree, or build on what came before - a real discussion, not an isolated
                monologue:
                %s

                Now bring in %d more role(s)' worth of philosophers - 6 real historical thinkers per role,
                same standards as before: reason as each actual thinker using their real published worldview
                (not a narrow paraphrase of any "application" column); most or all may have nothing to say
                about this product, which is the correct honest outcome, not a failure to find something;
                every reported critique needs an explicit "kanoClass" chosen from exactly Must-Be,
                Performance, Attractive, Indifferent, or Reverse - where Reverse means a feature the
                product is actively WORSE for having (not merely useless, which is Indifferent), and is
                the most valuable verdict available here because it is the only one that tells the system
                what to remove.

                Role charters for this turn (each contains its own philosophy table):
                %s

                Update the SAME report file `%s` on your SAME branch/PR: read what is already there and
                APPEND your new critiques for this turn's roles to the existing "critiques" array - do not
                remove, rewrite, or renumber anything already reported by earlier turns.
                """.formatted(digest, roleBatch.size(), charters, reportPath);
    }

    /**
     * Final turn once every active role has already spoken. Deliberately light-touch ("confirm and
     * consolidate", not "write new critique") - every voice was already appended to the report file
     * incrementally by its own turn (buildPhilosophicalFollowUpPrompt's instruction above), so asking for
     * heavy new synthesis text here would only risk Jules inventing or garbling something instead of just
     * faithfully closing out what is already a complete, real discussion.
     */
    private String buildPhilosophicalSynthesisPrompt(List<PhilosophicalCritique> allCritiquesSoFar, String reportPath) {
        String digest = renderCritiqueDigest(allCritiquesSoFar);
        return """
                Every active role has now spoken in this philosophical product falsification discussion. This
                is the FINAL turn: no new roles, no new product examination.

                For your own reference, here is a compact summary of every critique reported across all turns
                of this discussion (the report file on your branch is the authoritative full version):
                %s

                Re-read the full report file `%s` you have been building on your branch across this entire
                conversation and confirm/commit its final state:
                - Every critique from every turn is still present, verbatim, unchanged.
                - Nothing is deleted, summarized away, or merged into a different voice - each philosopher's
                  individual critique stays exactly as they gave it.
                - The JSON shape stays exactly {"critiques": [...]} - no new required fields, no restructuring.

                Commit this final version to the SAME branch/PR you have used for this whole conversation. Do
                not open a new PR or start a new branch.
                """.formatted(digest, reportPath);
    }

    private String buildPhilosophicalAuditPrompt(ProjectEntity project, List<RoleEntity> activeRoles, int totalActiveRoleCount,
            String reportPath, String screenshotDir) {
        String retrievalQuery = "philosophical product falsification of " + project.getName();
        StringBuilder charters = new StringBuilder();
        for (RoleEntity role : activeRoles) {
            charters.append(geminiContextService.buildRoleScopedContext(role, retrievalQuery, PHILOSOPHICAL_AUDIT_ROLE_CONTEXT_TOP_K));
        }
        charters.append(geminiContextService.buildCommonPatternContext(retrievalQuery, PHILOSOPHICAL_AUDIT_ROLE_CONTEXT_TOP_K));

        // RAG augmentation (2026-07-25, operator directive): surface relevant standing knowledge (prior
        // incidents, known architecture gaps, engineering invariants) the auditing session should know
        // about before critiquing - retrieval degrades to "" whenever unavailable (flag off, empty corpus,
        // embedding call failed), so this is always safe to splice in unconditionally.
        String knownContext = geminiContextService.buildContextBlock(
                "philosophical falsification of " + project.getName()
                        + " - known architecture gaps, standing engineering principles, prior incidents");

        return """
                You are running a PHILOSOPHICAL PRODUCT FALSIFICATION for this project. This is NOT a charter-
                compliance audit - a separate audit already does that. Do not write, fix, or refactor any
                product code, and do not report charter-rule violations here.

                STEP 1 - see the real product AS A WHOLE SYSTEM, not just its visual surface. The product is
                everything the user's experience depends on: the UI, the backend behavior, the data model, and
                the business logic - a missing validation rule, a data model that can't represent what the
                client actually needs, or an API that silently does the wrong thing are just as real a product
                gap as a confusing screen, and a philosopher whose lens is about data, backend, security, or
                delivery may have essentially nothing to reason about from a screenshot alone.
                  (a) UI: if this repository has a runnable frontend, install and start it using whatever its
                      own README/package.json declares. Using Playwright (or an equivalent browser-automation
                      tool already available in this environment), capture screenshots at 1440px and 375px
                      width of every distinct primary screen (cap at 8 screens), and note console errors or
                      interaction dead-ends. Save screenshots as PNG files under `%s` (create this directory) -
                      do NOT commit them anywhere else, and do NOT commit `playwright-report/`, `test-results/`,
                      `.webm`, `.trace`, or `node_modules`.
                  (b) Backend/data/logic: read the real backend source - API endpoints/controllers, the data
                      model and migrations, the business logic and services. If the backend is runnable,
                      start it and exercise its real API with a few genuine requests to see actual behavior,
                      not just what the code claims to do.
                  If any part (frontend, backend) has nothing runnable, or fails to start after a genuine
                  attempt: say so honestly in the report, reason only from what you could actually examine
                  (code you read, or the parts that did run), and never invent or assume behavior you did not
                  observe. If NOTHING at all is examinable, return "critiques": [].

                STEP 1b - walk the main path end to end, before any philosophy. Whatever this product is
                for, its user follows one path from intention to outcome, and value along that path
                MULTIPLIES: a shop that cannot take payment is not partly working, it is worth zero, no
                matter how good the catalogue is. So walk that path yourself in the running product and
                report where it breaks. Three things to look for, in this order of severity:
                  * a dead end - the path simply cannot continue from here;
                  * an action with no confirmation - you did something and the product never told you
                    whether it worked; silence is indistinguishable from failure;
                  * no way back when something fails - a rejected payment, a failed upload, a wrong value
                    with no correction path.
                A single genuine break here outweighs any number of refinements elsewhere, so report it as
                Must-Be and say plainly which link broke and what you observed. If the path is unbroken,
                say that too - it is real evidence, not an absence of findings.

                STEP 1c - check the obligations this product cannot legally ship without. These are not
                opinions and are not subject to a philosopher's taste: they are duties for the German and
                US markets this product is built for, and the client is not expected to have asked for
                them. Look for each in the RUNNING product and in its code, and report every one you find
                missing as a Must-Be critique with the act named as evidence. A missing legal disclosure is
                not a style question - in Germany it is a standard target for paid cease-and-desist
                letters, and the bill goes to the client.
                %s

                LIVE EVIDENCE - already fetched from the factory's own currently-running instance of this
                product (a separate, independent launch from the one you attempt in STEP 1 above - use this
                as ADDITIONAL grounding alongside your own attempt, never as a replacement for it, since it
                may be unavailable or may not reach every screen/endpoint your own attempt can):
                %s

                STEP 2 - the 6-voice pass for each role below. This is turn 1 of an ongoing SINGLE conversation
                covering %d of this project's %d active roles; the remaining roles will join in follow-up
                messages later in this SAME conversation and will be able to see everything you report here -
                a real, sequential discussion, not an isolated one-off report. Each role charter below names 6
                real philosophers in its "ФИЛОСОФСКИЙ ФУНДАМЕНТ" table. For EACH of these 6 (per role)
                philosophers individually, reason as that actual historical thinker, using their real
                published worldview - explicitly NOT the narrow pre-baked "application" column in the table
                (e.g. if a table's "application" column only mentions a 100ms latency threshold, the real
                philosopher's worldview is much broader than that one column - use the whole of what they
                actually thought, not just this system's narrow paraphrase of it). Looking at the WHOLE
                product you just examined in STEP 1 - its UI where you could see it, AND its backend
                behavior, data model, and business logic - ask genuinely: what would THIS philosopher find
                missing, wrong, or worth adding about the product as a whole, judged by their own real
                standards? A philosopher whose lens is about data integrity, security, or business value
                should be reasoning about the backend/data-model evidence, not straining to say something
                about a screenshot. Most or all of the 6 may have nothing to say about this particular
                product - that is the expected, correct, honest outcome. Do not manufacture an opinion to
                have something to report.

                STEP 3 - forced Kano classification. Every critique you report MUST carry an explicit
                "kanoClass" chosen from exactly: "Must-Be", "Performance", "Attractive", "Indifferent",
                "Reverse". There is no default - a critique without an explicit, deliberately-chosen class
                is invalid; drop it yourself rather than omit the field or guess.

                On "Reverse" specifically, because it is the one most often missed: it means a feature
                whose PRESENCE lowers satisfaction - not one that is merely useless (that is Indifferent),
                but one the product is actively worse for having. Real examples: a mobile app for a
                business customers use twice a year, forced registration before checkout, an in-product
                chat widget when customers already live in messaging apps, a recommendation engine trained
                on a catalogue too small to learn anything, a settings screen so configurable nobody
                completes setup. If you see something in THIS product that fits, classifying it Reverse is
                among the most valuable things this audit can produce - it is the only way the system can
                learn what to remove rather than what to add. Do not soften it to Indifferent.

                Deliverable: create a new branch and open a PR containing ONLY the report file `%s` (this
                EXACT path) plus, if you produced any, the screenshot PNGs under `%s` - no other files
                changed. Report shape:
                {"critiques": [
                  {"roleTag": "BARCAN-TAG-11", "philosopher": "Patricia Churchland",
                   "worldview": "one sentence on who this thinker actually is",
                   "critique": "what she would genuinely find looking at this product",
                   "proposal": "what she would suggest adding or changing",
                   "dislike": "what she would object to, if anything",
                   "kanoClass": "Attractive", "confidence": "high",
                   "evidence": "what you examined this is about - a screen, an endpoint, a migration file, a
                                service class, etc. - whatever grounds this specific critique",
                   "screenshotFile": "screen-2.png, or empty if this critique is not UI-grounded"}
                ]}
                Use "critiques": [] if nothing survives honest scrutiny.

                Role charters (each contains its own philosophy table - reason from the real thinkers, not
                just the table's narrow application column):
                %s

                %s
                """.formatted(screenshotDir, regulatoryChecklistFromCorpus(), liveEvidenceBlock(project),
                        activeRoles.size(), totalActiveRoleCount,
                        reportPath, screenshotDir, charters, knownContext);
    }

    /**
     * 2026-08-11 (reliability-strengthening plan, TOC subordination): one dedup-guarded, Must-Be-by-
     * construction wishlist per project - same bounded, one-shot-per-issue discipline as
     * ProductLaunchabilityService's dockerfile_missing_build_stage/frontend_not_deployed (never a second
     * one while the first is still unresolved, never invented scope - only a concrete, already-observed
     * fact: the last real observation failed).
     */
    /**
     * The most recent observation's error text, or blank when there is none.
     *
     * Reads the summary already fetched by the caller rather than issuing a second query: two derivations
     * of the same fact are two things that can disagree, and this method exists precisely to stop a claim
     * and its witness from drifting apart.
     */
    private String latestErrorText(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.RuntimeHealthSummary runtimeHealth) {
        if (runtimeHealth == null || runtimeHealth.recentObservations() == null
                || runtimeHealth.recentObservations().isEmpty()) {
            return "";
        }
        String text = runtimeHealth.recentObservations().get(0).getErrorText();
        return text == null ? "" : text.trim();
    }

    private void ensureProductNotLaunchableWishlist(ProjectEntity project,
                                                    com.eneik.production.services.runtime.ClientRuntimeObservabilityService.RuntimeHealthSummary runtimeHealth) {
        if (wishlistRepository.existsByProjectIdAndSource(project.getId(), WishlistSource.product_not_launchable)) {
            return;
        }
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setProjectId(project.getId());
        wishlist.setSource(WishlistSource.product_not_launchable);
        wishlist.setStatus(WishlistStatus.pending);
        wishlist.setLeanValue(LeanValue.essential);
        wishlist.setCynefinDomain("clear");
        // 2026-08-19: carry the OBSERVED CAUSE, not only the fact of failure. The launcher already
        // records exactly why the launch died in ClientRuntimeObservationEntity.errorText, and that text
        // sits in this very summary's recentObservations - measured live on test-forty-ninth:
        // "object-storage Error failed to resolve reference minio/minio:RELEASE.2023-09-20T22-40-07Z:
        // not found". Filing "it is not healthy" while holding that string asks the worker to rediscover
        // what the system already knows, and it is the same defect the auditor's ABSTAIN fix removed on
        // 2026-08-18: a claim must arrive with its witness, or the reader cannot act on it.
        String observedCause = latestErrorText(runtimeHealth);
        wishlist.setContent("The delivered product's most recent runtime observation was not healthy "
                + "(launch failed, or launched but its health check failed). Fix this before any further "
                + "philosophical review - reviewing a product that doesn't actually run produces no real "
                + "evidence, only guesses."
                + (observedCause.isBlank() ? ""
                        : "\n\nObserved failure, exactly as the launcher recorded it - this is evidence, not a "
                                + "hypothesis, so start here rather than by re-deriving it:\n" + observedCause));
        wishlist.setJtbd("When the product doesn't currently launch or respond healthily, I want that "
                + "fixed before anything else, so all other evaluation (philosophical, design, feature "
                + "work) is grounded in a real, working product");
        wishlist.setAcceptanceCriteria("Given the project's runtime observation history, When the next "
                + "observation cycle runs, Then launchSuccess=true and the health check returns 2xx");
        wishlist.setDod("The product launches successfully and its health check passes");
        wishlistRepository.save(wishlist);
        log.info("FalsificationCycleService: created product_not_launchable wishlist for project {}", project.getId());
    }

    /**
     * 2026-08-11 (client runtime observability plan, Phase 5): reuses the SAME bounded live-preview
     * window ClientRuntimeObservabilityService already maintains for the dashboard link
     * (currentLiveUrl) - never opens its own separate launch, never blocks the audit if nothing is
     * currently live (most of the time, by design - the window is short and this cycle runs on its own
     * schedule). Same "backend gathers evidence, the role reasons over it" discipline as the Gemini
     * observer's evidence snapshot - never a live network call the role itself has to make blind.
     */
    private String liveEvidenceBlock(ProjectEntity project) {
        var liveUrl = clientRuntimeObservabilityService.currentLiveUrl(project.getId());
        if (liveUrl.isEmpty()) {
            return "No live running instance is currently available this cycle - proceed with STEP 1's own attempt only.";
        }
        var fetch = runtimeLauncherClient.fetchHtml(liveUrl.get());
        if (fetch.error() != null || fetch.statusCode() == null) {
            return "Attempted to fetch " + liveUrl.get() + " but it was not reachable ("
                    + (fetch.error() != null ? fetch.error() : "no response") + ") - proceed with STEP 1's own attempt only.";
        }
        String body = fetch.body() != null ? fetch.body() : "";
        if (body.length() > LIVE_EVIDENCE_MAX_CHARS) {
            body = body.substring(0, LIVE_EVIDENCE_MAX_CHARS) + "... (truncated)";
        }
        return "Fetched " + liveUrl.get() + " (HTTP " + fetch.statusCode() + "):\n" + body;
    }

    public record PhilosophicalCritique(
            String roleTag,
            String philosopher,
            String worldview,
            String critique,
            String proposal,
            String dislike,
            String kanoClass,
            String confidence,
            String evidence,
            String screenshotFile
    ) {
    }

    // Kano's own original survey methodology never treats one respondent's answer as authoritative on its
    // own - it tabulates many respondents' answers and takes the modal (most frequent) classification.
    // clusterKano below reproduces exactly that: majority vote across a cluster's members, tie-broken toward
    // the more assertive class (operator directive, 2026-07-25).
    // 2026-08-14: "Reverse" added, and deliberately placed LAST. Without it here, normalizeKano below
    // silently rewrote every Reverse critique into "Must-Be" (its fallback for unrecognised values) - so
    // adding Reverse to the parser's validKano without adding it here would have inverted the meaning of
    // the single most valuable verdict the audit can produce: "remove this" would have become "this is
    // mandatory". Last position, not first, because this list is the tie-break order and Reverse is the
    // only class whose action is destructive: at an even split it must lose, so removing something needs a
    // real majority while adding something does not. That is the same asymmetry-of-cost reasoning as the
    // gate threshold - a wrongly-removed feature is invisible and unrecoverable, a wrongly-kept one is
    // visible and cheap.
    private static final List<String> KANO_ASSERTIVENESS_ORDER = List.of("Attractive", "Performance", "Must-Be", "Indifferent", "Reverse");

    /**
     * The statutory duties for the markets this factory builds for, rendered from the versioned corpus.
     * Only statutory entries appear - MarketCorpusService.influentialExpectations enforces that, so the
     * unverified guesses in the corpus can never be presented to an auditor as legal obligations.
     * Empty string when no corpus is available, which leaves the audit exactly as it was before.
     */
    private String regulatoryChecklistFromCorpus() {
        if (marketCorpusService == null) {
            return "";
        }
        java.util.LinkedHashSet<String> lines = new java.util.LinkedHashSet<>();
        for (String market : List.of("DE", "US")) {
            for (var expectation : marketCorpusService.influentialExpectations(market)) {
                if (!"statutory".equals(expectation.status())) {
                    continue;
                }
                StringBuilder line = new StringBuilder("                  * ");
                if (expectation.market() != null && !expectation.market().isBlank()) {
                    line.append("[").append(expectation.market()).append("] ");
                }
                if (expectation.appliesWhen() != null && !expectation.appliesWhen().isBlank()) {
                    line.append("if ").append(expectation.appliesWhen()).append(": ");
                }
                line.append(expectation.requirement());
                if (expectation.source() != null && !expectation.source().isBlank()) {
                    line.append(" (basis: ").append(expectation.source()).append(")");
                }
                lines.add(line.toString());
            }
        }
        return String.join("\n", lines);
    }

    private String normalizeKano(String raw) {
        for (String known : KANO_ASSERTIVENESS_ORDER) {
            if (known.equalsIgnoreCase(raw)) {
                return known;
            }
        }
        return "Must-Be";
    }

    /**
     * winningClass is the mode (maximum-membership defuzzification, ties broken toward the more assertive
     * class); voteBreakdown is the full distribution BEFORE that collapse - kept and surfaced in the
     * wishlist content (not discarded) so a reviewer can see e.g. "Attractive: 3, Must-Be: 2" instead of
     * just the winning label, per the operator's fuzzy-logic framing (2026-07-25): defuzzify to one decidable
     * class for the compiler to act on, but never hide the underlying spread that produced it.
     */
    private record ClusterKano(String winningClass, String voteBreakdown) {
    }

    private ClusterKano clusterKano(List<PhilosophicalCritique> members) {
        java.util.Map<String, Long> counts = members.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        c -> normalizeKano(c.kanoClass()), java.util.stream.Collectors.counting()));
        long maxCount = counts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        String winner = KANO_ASSERTIVENESS_ORDER.stream()
                .filter(k -> counts.getOrDefault(k, 0L) == maxCount)
                .findFirst()
                .orElse("Must-Be");
        String breakdown = KANO_ASSERTIVENESS_ORDER.stream()
                .filter(counts::containsKey)
                .map(k -> k + ": " + counts.get(k))
                .collect(java.util.stream.Collectors.joining(", "));
        return new ClusterKano(winner, breakdown);
    }

    /**
     * Deliberately distinct from applyAuditViolations above: philosophical critiques are DISTINCT product
     * feature proposals, not defects in one shipped iteration, so blindly consolidating ALL of a run's
     * critiques into one wishlist would force the compiler to invent one epic covering many unrelated ideas.
     *
     * No per-critique Kano/confidence filtering (operator directive, 2026-07-25, reversing an earlier design
     * that discarded Must-Be/low-confidence critiques as "noise control" - the operator's objection: "зачем
     * мы так стараемся внедрить мысли великих умов чтобы выкинуть их?" - why go to all this trouble to bring
     * in great minds' thoughts just to throw them away?). Every reported critique is clustered via
     * WishlistContentSimilarityMatcher.clusterBySimilarity (single-linkage / union-find over the same
     * Jaccard metric used for dedup elsewhere) instead of being individually judged - noise is absorbed by
     * grouping converging voices into one wishlist per theme, not by discarding any one voice. A cluster's
     * Kano class is the majority vote among its members (clusterKano above); a cluster whose majority is
     * Indifferent creates no wishlist, not because it was filtered out, but because that IS what the
     * aggregated voice concluded - there is nothing to propose.
     *
     * Never touches falsificationRunRepository - that watermark belongs solely to the formal cycle's PR-dedup
     * logic; a philosophical run has nothing to do with which PRs have been audited for charter compliance,
     * and writing it here would silently cause the formal audit to skip real merged PRs on its next run.
     */
    @Transactional
    public void applyPhilosophicalCritiques(ProjectEntity project, List<PhilosophicalCritique> critiques, String screenshotDir) {
        if (critiques.isEmpty()) {
            log.info("FalsificationCycleService: Philosophical falsification audit for project {} - no critiques reported this run.",
                    project.getName());
            return;
        }

        List<String> candidateTexts = critiques.stream()
                .map(c -> c.philosopher() + ": " + nullToEmpty(c.proposal()) + " " + nullToEmpty(c.critique()))
                .toList();
        // Largest-cluster-first (spirit of Pareto/portfolio ranking, operator's proposed framework,
        // 2026-07-25): only matters when the per-run safety cap actually binds - a theme 5 philosophers
        // independently converged on is stronger evidence than one 2 philosophers converged on, so if
        // anything has to wait for a future run, it should be the weaker-support clusters, not whichever
        // happened to come first out of union-find's arbitrary root ordering.
        List<List<Integer>> clusters = new java.util.ArrayList<>(wishlistContentSimilarityMatcher.clusterBySimilarity(candidateTexts));
        clusters.sort(java.util.Comparator.<List<Integer>>comparingInt(List::size).reversed());

        List<WishlistEntity> livePhilosophicalWishlists = wishlistRepository.findByProjectIdAndSourceAndStatusIn(
                project.getId(), WishlistSource.philosophical_falsification,
                List.of(WishlistStatus.pending, WishlistStatus.compiling, WishlistStatus.converted_to_task));

        long pendingCount = pendingPhilosophicalWishlistCount(project.getId());
        int created = 0;
        int skippedIndifferent = 0;
        for (List<Integer> clusterIndices : clusters) {
            if (created >= MAX_PHILOSOPHICAL_PROPOSALS_PER_RUN) {
                log.info("FalsificationCycleService: Philosophical audit for project {} - reached the per-run safety cap ({} clusters); "
                                + "remaining clusters will resurface on a future audit if still genuinely warranted",
                        project.getName(), MAX_PHILOSOPHICAL_PROPOSALS_PER_RUN);
                break;
            }
            if (pendingCount >= MAX_PENDING_PHILOSOPHICAL_WISHLISTS) {
                log.info("FalsificationCycleService: Philosophical audit for project {} - reached the project-wide pending safety cap ({})",
                        project.getName(), MAX_PENDING_PHILOSOPHICAL_WISHLISTS);
                break;
            }

            List<PhilosophicalCritique> members = clusterIndices.stream().map(critiques::get).toList();
            ClusterKano kano = clusterKano(members);
            if ("Indifferent".equals(kano.winningClass())) {
                skippedIndifferent++;
                log.info("FalsificationCycleService: Philosophical audit for project {} - cluster of {} philosopher(s) ({}) "
                                + "converged on Indifferent ({}); no wishlist created, that is the aggregated conclusion, not a filter",
                        project.getName(), members.size(),
                        members.stream().map(PhilosophicalCritique::philosopher).collect(java.util.stream.Collectors.joining(", ")),
                        kano.voteBreakdown());
                continue;
            }

            String candidateContent = philosophicalClusterWishlistContent(project, members, kano, screenshotDir);
            java.util.Optional<UUID> semanticDuplicate =
                    wishlistContentSimilarityMatcher.findLikelyDuplicate(livePhilosophicalWishlists, candidateContent);
            if (semanticDuplicate.isPresent()) {
                log.info("FalsificationCycleService: Philosophical audit for project {} - skipping a cluster of {} philosopher(s), "
                                + "matches existing wishlist {} from a prior run",
                        project.getName(), members.size(), semanticDuplicate.get());
                continue;
            }

            String distinctRoles = members.stream().map(PhilosophicalCritique::roleTag).distinct()
                    .collect(java.util.stream.Collectors.joining(", "));
            String distinctPhilosophers = members.stream().map(PhilosophicalCritique::philosopher).distinct()
                    .collect(java.util.stream.Collectors.joining(", "));

            WishlistEntity wishlist = new WishlistEntity();
            wishlist.setProjectId(project.getId());
            wishlist.setSource(WishlistSource.philosophical_falsification);
            wishlist.setSourceRoleTag(distinctRoles);
            wishlist.setStatus(WishlistStatus.pending);
            wishlist.setLeanValue(LeanValue.valuable);
            wishlist.setTocConstraintRef("Product-philosophy cluster of " + members.size() + " philosopher(s): " + distinctPhilosophers);
            wishlist.setSixSigmaMetric("philosophical_falsification_proposal_rate");
            wishlist.setContent(candidateContent);
            wishlist.setJtbd("When " + distinctPhilosophers + "'s converging worldviews are applied honestly to the live product, "
                    + "I want the genuine gap they identify addressed, so the product is closer to what users actually need");
            wishlist.setAcceptanceCriteria("Given this cluster's critique, When this brief is compiled, "
                    + "Then the resulting epic keeps the stated Kano class (" + kano.winningClass() + ") verbatim rather than re-classifying it");
            wishlist.setDod("Philosophical product-critique cluster (" + distinctPhilosophers + ") resolved or genuinely superseded");
            wishlist = wishlistRepository.save(wishlist);
            pendingCount++;
            created++;
            log.info("FalsificationCycleService: Created philosophical_falsification wishlist {} from a cluster of {} philosopher(s) ({}), Kano={} ({})",
                    wishlist.getId(), members.size(), distinctPhilosophers, kano.winningClass(), kano.voteBreakdown());
        }

        log.info("FalsificationCycleService: Completed philosophical falsification audit for project {}. "
                        + "Critiques reported: {}, clusters formed: {}, Indifferent clusters (no action needed): {}, wishlist(s) created: {}",
                project.getName(), critiques.size(), clusters.size(), skippedIndifferent, created);
    }

    /**
     * Layer 1 of the forced-Kano mechanism (see ProjectFlowService.wishlistCompilerPromptBatch's
     * philosophical-falsification branch for Layer 2, the mandatory bracketed directive): literal "Kano: X"
     * / "Cynefin: complex" text, the same precedent ProjectFlowService.createSessionPostmortemWishlist
     * already uses for role_mismatch_followup wishlists. TechnicalLeadCompiler.kanoClass/cynefinDomain
     * substring-match these literally on the cheap-compile path. Lists every cluster member (not just one) -
     * a converging cluster of 5 philosophers is stronger evidence than any single voice, and the compiler/
     * reviewer should see the full convergence, not a summary that hides how many independently agreed.
     */
    private String philosophicalClusterWishlistContent(ProjectEntity project, List<PhilosophicalCritique> members, ClusterKano kano, String screenshotDir) {
        StringBuilder content = new StringBuilder();
        content.append("Philosophical product falsification - a cluster of ").append(members.size())
                .append(" philosopher(s) independently converged on the same theme, evaluated against the live product. ")
                .append("Kano: ").append(kano.winningClass()).append(" (vote distribution before the majority collapse: ")
                .append(kano.voteBreakdown()).append("). Cynefin: complex.\n\n");
        java.util.Set<String> seenScreenshots = new java.util.LinkedHashSet<>();
        int index = 1;
        for (PhilosophicalCritique critique : members) {
            content.append("Voice ").append(index++).append(" - ").append(critique.philosopher())
                    .append(" (role ").append(critique.roleTag()).append(", their own Kano: ").append(critique.kanoClass()).append("):\n");
            content.append("  Worldview: ").append(nullToEmpty(critique.worldview())).append("\n");
            content.append("  Critique: ").append(nullToEmpty(critique.critique())).append("\n");
            content.append("  Proposal: ").append(nullToEmpty(critique.proposal())).append("\n");
            if (critique.dislike() != null && !critique.dislike().isBlank()) {
                content.append("  Objection: ").append(critique.dislike()).append("\n");
            }
            if (critique.evidence() != null && !critique.evidence().isBlank()) {
                content.append("  Evidence: ").append(critique.evidence()).append("\n");
            }
            String screenshotUrl = rawScreenshotUrl(project, screenshotDir, critique.screenshotFile());
            if (screenshotUrl != null) {
                seenScreenshots.add(screenshotUrl);
            }
        }
        for (String screenshotUrl : seenScreenshots) {
            content.append("Screenshot: ").append(screenshotUrl).append("\n");
        }
        return content.toString();
    }

    /**
     * Dashboard visibility (operator directive, 2026-07-25): "раз мы всё равно показываем скриншоты для
     * оценки - хорошо бы их как-то видеть на нашем фронтенде." No new binary storage - the report PR already
     * merges the screenshot into the project's own `main` branch (record-only merge, same as the JSON report
     * itself), so a plain raw.githubusercontent.com URL is enough; the frontend just needs an &lt;img&gt; tag.
     * Same owner/repo parsing GitHubPullRequestService.repoRef uses, duplicated here rather than exposing
     * that private helper - this is the only caller outside that service.
     */
    private String rawScreenshotUrl(ProjectEntity project, String screenshotDir, String screenshotFile) {
        if (screenshotFile == null || screenshotFile.isBlank()) {
            return null;
        }
        String repositoryUrl = project.getRepositoryUrl();
        if (repositoryUrl == null || !repositoryUrl.startsWith("https://github.com/")) {
            return null;
        }
        String ownerRepo = repositoryUrl.replace("https://github.com/", "").replaceAll("/+$", "");
        String path = (screenshotDir.endsWith("/") ? screenshotDir : screenshotDir + "/") + screenshotFile.trim();
        return "https://raw.githubusercontent.com/" + ownerRepo + "/main/" + path;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean hasOpenFalsificationWishlist(UUID projectId) {
        return wishlistRepository.countByProjectIdAndSourceAndStatus(
                projectId, WishlistSource.self_falsification, WishlistStatus.pending) > 0
                || wishlistRepository.countByProjectIdAndSourceAndStatus(
                        projectId, WishlistSource.self_falsification, WishlistStatus.compiling) > 0;
    }

    // Deliberately Gemini-free: refusal-criteria and methodological-falsification checks used to call
    // Gemini directly, once per active role per project, every cycle. Now dispatches a single Jules
    // eneikdru audit session per project (ProjectFlowService.dispatchFalsificationAudit) that reads the
    // real current diff and every active role's real charter file, then writes one JSON report;
    // completion (applyAuditViolations below) is driven asynchronously by JulesDispatchService once that
    // session opens its report PR. This cycle only fires every few hours, so it comfortably shares the
    // reserved eneikdru account with wishlist compilation instead of contending with real
    // product-implementation dispatch.
    public void executeCycleForProject(ProjectEntity project) {
        ClientDeliverableReadinessService.Readiness readiness = readinessService.computeForProject(project.getId());
        // selfFalsificationReadyRatio, not ratio(): this cycle is the ONLY mechanism authorized to create
        // replacement work for a task the iteration-admission poka-yoke retired without one (see
        // ProjectFlowService.createRecoveryWishlistForOrphanedBlockedTasks and the field's own javadoc) -
        // gating it on strict ratio() made that exact recovery permanently unreachable (live incident,
        // test-forty-second, 2026-08-06: task 5ac0b91b retired with "no child work created", 2 dependents
        // stuck queued forever, SYSTEM_STALLED for 280+ minutes).
        if (!readiness.decompositionComplete() || readiness.selfFalsificationReadyRatio() < readinessThreshold) {
            // Auditing before there's a real object to audit just spends a reserved eneikdru session on
            // whatever process/design artifacts happen to be in main yet (confirmed live in
            // test-twenty-eighth: the first cycle ran against zero merged product code and found only
            // metadata-formatting nitpicks). Wait until most of what the client actually asked for has
            // really shipped (merged, not just review-approved - see ClientDeliverableReadinessService)
            // before spending capacity looking for violations in it.
            log.info("FalsificationCycleService: Project {} not ready for falsification yet ({}/{} planned task(s) merged, "
                            + "{}/{} feature(s) complete, decompositionComplete={}, {}% (falsification-ready) < {}% threshold); "
                            + "skipping this cycle instead of auditing an incomplete product iteration",
                    project.getName(), readiness.mergedDeliverables(), readiness.totalDeliverables(),
                    readiness.completeFeatures(), readiness.totalFeatures(), readiness.decompositionComplete(),
                    Math.round(readiness.selfFalsificationReadyRatio() * 100), Math.round(readinessThreshold * 100));
            return;
        }

        if (hasOpenFalsificationWishlist(project.getId())) {
            log.info("FalsificationCycleService: Project {} already has an open self_falsification wishlist; "
                    + "skipping instead of creating a parallel improvement cycle", project.getName());
            return;
        }

        List<RoleEntity> activeRoles = roleRepository.findAll().stream()
                .filter(RoleEntity::isActive)
                .toList();

        RecentChanges recentChanges = getRecentCodeChangesForAudit(project);
        if (recentChanges.text().isBlank()) {
            // No real code to audit yet is an honest, valid state (brand-new project, GitHub disabled,
            // nothing merged, or - Lean - nothing NEW merged since the last audit) - dispatching a Jules
            // session against an empty/stale prompt would just waste capacity and risk it inventing
            // violations to have something to report. Skip and retry next cycle instead of faking a diff
            // (this is the same bug this method already fixed once: the old fallback silently substituted
            // an unrelated PR-review remark string for a real diff).
            log.info("FalsificationCycleService: No new merged PR diffs (or local workspace diff) available for project {} since the last audit; skipping this cycle",
                    project.getName());
            return;
        }

        String reportPath = ".eneik/records/falsification-report-" + java.util.UUID.randomUUID() + ".json";
        String prompt = buildAuditPrompt(project, activeRoles, recentChanges.text(), reportPath);

        ProjectFlowService.AuditDispatchResult result =
                projectFlowService.dispatchFalsificationAudit(project, prompt, recentChanges.highestPrNumber(), reportPath);
        if (result.taskId() == null) {
            log.warn("FalsificationCycleService: Could not dispatch falsification audit for project {}", project.getName());
            return;
        }
        // 2026-08-07 fix: only claim "Dispatched" when the Jules call actually succeeded this cycle - the
        // old code logged this unconditionally right after dispatchFalsificationAudit's own Jules call had
        // just failed with HTTP 400, a real testimony-vs-evidence gap. dispatchedToJules=false is not an
        // error: the task is real and queued, and retries automatically on the next compiler-dispatch cycle.
        if (result.dispatchedToJules()) {
            log.info("FalsificationCycleService: Dispatched falsification audit task {} for project {} covering {} active role(s)",
                    result.taskId(), project.getName(), activeRoles.size());
        } else {
            log.info("FalsificationCycleService: Falsification audit task {} created and queued for project {} covering {} "
                            + "active role(s); Jules dispatch not yet confirmed this cycle - will retry automatically",
                    result.taskId(), project.getName(), activeRoles.size());
        }
    }

    private String buildAuditPrompt(ProjectEntity project, List<RoleEntity> activeRoles, String latestDiff, String reportPath) {
        StringBuilder briefSection = new StringBuilder();
        // Live incident, 2026-08-05 (test-forty-first): source==client alone is NOT "this is genuine client
        // input" - confirmed live, this project's wishlist table held exactly 1 real root brief
        // (sourceRoleTag blank) plus 42 rows titled "Internal [UI] work item N (ROLE) from wishlist
        // <rootId>: ..." - internal per-role decomposition artifacts that inherited source=client from their
        // parent but each embed the ENTIRE parent brief's text again inside their own content (the actual
        // repeated-decomposition duplication bug class already fixed once at the task level in 2026-07-20 -
        // this is the same root behavior surfacing at the wishlist-item level instead, uncaught by that
        // fix). sourceRoleTag is the field that actually discriminates "real client submission" (blank) from
        // "derived internal artifact" (a role tag) - verified against every one of this project's 43 rows,
        // not assumed. MAX_CLIENT_BRIEF_CHARS_TOTAL below stays as defense-in-depth even after this filter -
        // a single genuinely oversized real brief should still never be sent unbounded.
        List<WishlistEntity> clientBriefs = wishlistRepository.findByProjectId(project.getId()).stream()
                .filter(w -> w.getSource() == WishlistSource.client)
                .filter(w -> w.getSourceRoleTag() == null || w.getSourceRoleTag().isBlank())
                .toList();
        if (!clientBriefs.isEmpty()) {
            briefSection.append("\n\n=== ORIGINAL CLIENT SPECIFICATION & DECOMPOSITION COVERAGE ===\n");
            int briefsIncluded = 0;
            for (WishlistEntity brief : clientBriefs) {
                if (briefSection.length() >= MAX_CLIENT_BRIEF_CHARS_TOTAL) {
                    break;
                }
                briefSection.append("Client Brief Content:\n")
                        .append(truncate(brief.getContent(), MAX_DIFF_CHARS_PER_PR))
                        .append("\n---\n");
                briefsIncluded++;
            }
            if (briefsIncluded < clientBriefs.size()) {
                briefSection.append("[").append(clientBriefs.size() - briefsIncluded)
                        .append(" older client brief(s) omitted - size budget reached]\n");
            }
            briefSection.append("Audit Objective: Compare the above client specification against all merged PRs and actual code implementation below. Verify whether any requirements were missed, incomplete, or deviated from.\n");
        }

        // RAG role context (2026-08-07 fix, live incident: this used to concatenate every active role's
        // FULL raw charter + FULL philosopher-pattern files, unbounded - measured ~1.5-2MB for 13 roles,
        // which Jules rejected outright with HTTP 400 on every retry, forever. Scoped per-role retrieval
        // against GeminiContextService's already-indexed corpus (see buildRoleScopedContext) instead - the
        // diff itself is the retrieval query, so each role gets the charter/pattern passages actually
        // relevant to what's being audited, not the whole document.
        StringBuilder charters = new StringBuilder();
        for (RoleEntity role : activeRoles) {
            charters.append(geminiContextService.buildRoleScopedContext(role, latestDiff, CODE_DEFECT_AUDIT_ROLE_CONTEXT_TOP_K));
        }
        charters.append(geminiContextService.buildCommonPatternContext(latestDiff, CODE_DEFECT_AUDIT_ROLE_CONTEXT_TOP_K));

        return """
                You are the falsification auditor for this project (BARCAN-TAG-09 role). Audit the CURRENT
                real code, merged PRs, and client specification coverage below against every role charter provided.
                Do NOT implement, fix, or change any product code, and do not run builds or tests - this task only
                produces an audit report.

                Report only violations you can point to concretely in the diff/logs below - never invent a
                violation to have something to report, and never omit a real one. An empty violations list
                is a completely valid, honest result if nothing is actually wrong.

                For each role charter and client specification:
                1. Refusal criteria: does the current code/diff violate that role's stated REFUSAL CRITERIA?
                2. Methodological falsification: applying that charter's philosophical framing, is there a
                   confirmed systemic contradiction (not a stylistic nitpick)?
                3. Specification & Coverage Audit: compare merged PRs and actual codebase against the client brief.
                4. Stub code (role-independent - check this regardless of what any specific charter says):
                   does any function, endpoint handler, or pipeline stage fake success without doing real
                   work - a hardcoded/fixture response, a TODO-only body, a log line claiming completion with
                   no underlying logic, a try/catch that swallows a real failure and returns a plausible-
                   looking success? Report every instance you find, even if no charter explicitly names it.
                5. Architectural layer violation (same role-independent class as #4): does a UI slice exist
                   with nothing real behind it (calls an endpoint that doesn't exist, or a mocked one), or is
                   a claimed integration between two layers actually unwired (e.g. a "connected" backend call
                   that's never invoked, a described data flow that silently drops)?
                6. Causal justification (Pearl's ladder of causation - association vs intervention vs
                   counterfactual): where a PR's own description or commit message claims a fix addresses a
                   root cause ("fixes the stall", "resolves the race"), does the diff actually change the
                   mechanism that produces the symptom, or does it only correlate with the symptom going away
                   (a retry added around a call whose real failure was never identified, a timeout raised
                   without evidence the timeout was the actual constraint, a status reset with no change to
                   whatever put it in that status repeatedly)? Report this as "causal_unjustified" only when
                   the diff or its own stated rationale gives you concrete grounds to say the claimed cause
                   was never actually traced - an honest "no PR made an unsupported causal claim this cycle"
                   is correct far more often than not.

                For findings in categories 4-6: set "roleTag" to whichever role charter's area of ownership
                the stub/violation sits in (e.g. BARCAN-TAG-02 for a backend stub, BARCAN-TAG-11 for a
                frontend one); if no single role clearly owns it, use "BARCAN-TAG-09" (this auditor's own
                role) rather than skip the finding. Also set "prNumber" to the real PR number shown in the
                "=== PR #N ..." section header above the code you found the issue in - cite the exact number
                you were shown, never guess or invent one; omit the field entirely if the issue isn't tied to
                any specific merged PR.

                Deliverable: create a new branch and open a PR that contains ONLY one file, `%s`
                (this EXACT path - it is unique to this task, do not use any other path), with EXACTLY
                this shape and no other files changed:
                {"violations": [
                  {"roleTag": "BARCAN-TAG-02", "type": "refusal_criteria", "reason": "concrete reason tied to the diff"},
                  {"roleTag": "BARCAN-TAG-11", "type": "stub", "prNumber": 47, "reason": "concrete: which function/file, what it fakes, what real work is missing"},
                  {"roleTag": "BARCAN-TAG-02", "type": "layer_violation", "prNumber": 47, "reason": "concrete: which two layers, what's unwired or missing"},
                  {"roleTag": "BARCAN-TAG-05", "type": "causal_unjustified", "prNumber": 47, "reason": "concrete: what the PR claimed as the cause, what the diff actually changed, why that gap means the mechanism was never traced"},
                  {"roleTag": "BARCAN-TAG-07", "type": "methodological", "philosopher": "name", "thesis": "...",
                   "score": "3", "mustBe": "...", "performance": "...", "attractive": "..."}
                ]}
                Use "violations": [] if you find nothing wrong. Do not write, modify, or delete any other file.

                Client Specification & Coverage Input:
                %s

                Recent diff and operational activity to audit:
                %s

                Role charters to audit against:
                %s
                """.formatted(reportPath, briefSection.toString(), latestDiff, charters);
    }

    public record AuditViolation(
            String roleTag,
            String type,
            String reason,
            String philosopher,
            String thesis,
            String score,
            String mustBe,
            String performance,
            String attractive,
            // Real PR number cited by the auditor for "stub"/"layer_violation" findings, copied verbatim
            // from the "=== PR #N ..." section header it was shown - null for other types, or when the
            // finding isn't tied to a specific merged PR. Never derived by guessing/fuzzy-matching text.
            Integer prNumber
    ) {
    }

    @Transactional
    public void applyAuditViolations(ProjectEntity project, List<AuditViolation> violations, Integer highestPrNumberAudited) {
        int rolesCheckedCount = (int) roleRepository.findAll().stream().filter(RoleEntity::isActive).count();
        int violationsFoundCount = 0;
        int followUpsCreatedCount = 0;
        List<AuditViolation> validViolations = violations.stream()
                .filter(v -> v.roleTag() != null && !v.roleTag().isBlank())
                .toList();

        // Semantic-duplication guard (2026-07-24), same class as the coverage-audit fix and same reason:
        // hasOpenFalsificationWishlist only ever blocks while a self_falsification wishlist is still OPEN;
        // once it converts to a task (the successful case), a later audit re-confirming "the same"
        // contradiction in different wording sails through unchecked. Lower urgency than coverage-audit
        // here (already gated to at most one consolidated wishlist per call), added for symmetry/rigor.
        List<WishlistEntity> liveFalsificationWishlists = wishlistRepository.findByProjectIdAndSourceAndStatusIn(
                project.getId(), WishlistSource.self_falsification,
                List.of(WishlistStatus.pending, WishlistStatus.compiling, WishlistStatus.converted_to_task));

        for (AuditViolation violation : violations) {
            String roleTag = violation.roleTag();
            if (roleTag == null || roleTag.isBlank()) {
                continue;
            }
            violationsFoundCount++;

            if (followUpsCreatedCount > 0 || hasOpenFalsificationWishlist(project.getId())) {
                log.info("FalsificationCycleService: Skipping duplicate finding for role {}; "
                        + "this audit already created or found an open consolidated self_falsification wishlist", roleTag);
                continue;
            }
            String consolidatedContent = consolidatedViolationContent(validViolations);
            java.util.Optional<UUID> semanticDuplicate =
                    wishlistContentSimilarityMatcher.findLikelyDuplicate(liveFalsificationWishlists, consolidatedContent);
            if (semanticDuplicate.isPresent()) {
                log.info("FalsificationCycleService: skipping consolidated finding for role {} - content matches existing wishlist {}",
                        roleTag, semanticDuplicate.get());
                continue;
            }

            WishlistEntity wishlist = new WishlistEntity();
            wishlist.setProjectId(project.getId());
            wishlist.setSource(WishlistSource.self_falsification);
            wishlist.setSourceRoleTag("BARCAN-TAG-09");
            wishlist.setStatus(WishlistStatus.pending);
            wishlist.setLeanValue(LeanValue.essential);
            wishlist.setTocConstraintRef("HIGH_PRIORITY_DEBT");
            wishlist.setSixSigmaMetric("falsification_run_rate");
            wishlist.setDod("BARCAN-TAG-09: Falsification regression fixed");

            if ("methodological".equalsIgnoreCase(violation.type())) {
                String philosopher = violation.philosopher();
                String thesis = violation.thesis();
                String content = "Methodological contradiction confirmed by " + philosopher + ": " + thesis + "\n" +
                        "Score: " + violation.score() + "\n" +
                        "[Must-Be]: " + violation.mustBe() + "\n" +
                        "[Performance]: " + violation.performance() + "\n" +
                        "[Attractive]: " + violation.attractive();
                wishlist.setContent(content);
                wishlist.setJtbd("Resolve methodological contradiction identified by " + philosopher);
                wishlist.setAcceptanceCriteria("Given methodological contradiction by " + philosopher
                        + ", When resolving, Then Must-Be requirement is fulfilled: " + violation.mustBe());
                log.warn("FalsificationCycleService: Methodological contradiction confirmed for role {} by philosopher {}: {}",
                        roleTag, philosopher, thesis);
            } else {
                wishlist.setContent("Compliance violation detected for role " + roleTag + ". Violates: " + violation.reason());
                wishlist.setJtbd("Fix role refusal criteria violation detected by falsification cycle");
                wishlist.setAcceptanceCriteria("Refusal criteria check passes successfully");
                log.warn("FalsificationCycleService: Code violation detected for role {}: {}", roleTag, violation.reason());
            }

            wishlist.setContent(consolidatedViolationContent(validViolations));
            wishlist.setJtbd("When a product iteration is mostly shipped, I want confirmed contradictions fixed, "
                    + "so that the next iteration improves the real product without expanding blindly");
            wishlist.setAcceptanceCriteria("Given the confirmed findings, When this wishlist is compiled, "
                    + "Then every finding maps to an explicit feature requirement and bounded task; "
                    + "Given those tasks merge, When readiness is recalculated, Then every finding has merge evidence");
            wishlist.setDod("All confirmed falsification findings are decomposed into bounded features and merged fixes");
            wishlist = wishlistRepository.save(wishlist);
            followUpsCreatedCount++;
            log.info("FalsificationCycleService: Created one consolidated self_falsification wishlist item {} for {} confirmed violation(s)",
                    wishlist.getId(), validViolations.size());
        }

        // Never regress the dedup watermark: if this particular run only found local-workspace content
        // (no GitHub PR numbers involved), incoming is null - keep whatever the last real PR-based run
        // recorded rather than resetting it and re-auditing everything again next cycle.
        Integer previousHighest = falsificationRunRepository.findTopByProjectIdOrderByRunAtDesc(project.getId())
                .map(FalsificationRunEntity::getHighestPrNumberAudited)
                .orElse(null);
        // Deliberately if/else, not a chained ternary (2026-08-05, found live by a new test with no prior
        // run history AND no cited PR number - both null simultaneously): a ternary whose two branches are
        // one Integer and one int-producing expression (Math.max(...)) forces the WHOLE conditional
        // expression's static type to int per JLS binary numeric promotion, which silently unboxes the
        // Integer branch EVEN WHEN IT'S THE ONE TAKEN - `previousHighest == null ? previousHighest : ...`
        // still calls previousHighest.intValue() on a null reference. A category error (conflating "an
        // Integer reference" with "an int value" across a branch that was never supposed to unbox), not a
        // missing-data gap - explicit branches sidestep the promotion entirely.
        Integer watermark;
        if (highestPrNumberAudited == null) {
            watermark = previousHighest;
        } else if (previousHighest == null) {
            watermark = highestPrNumberAudited;
        } else {
            watermark = Math.max(previousHighest, highestPrNumberAudited);
        }

        FalsificationRunEntity run = new FalsificationRunEntity();
        run.setProjectId(project.getId());
        run.setRunAt(Instant.now());
        run.setRolesCheckedCount(rolesCheckedCount);
        run.setViolationsFoundCount(violationsFoundCount);
        run.setTasksCreatedCount(followUpsCreatedCount);
        run.setHighestPrNumberAudited(watermark);
        run = falsificationRunRepository.save(run);

        // Product-layer Six Sigma instrumentation (2026-08-05): persist one row per stub/layer_violation
        // finding, independent of the consolidated-wishlist dedup above (that logic only ever creates ONE
        // wishlist per call and skips every violation after the first - this must not inherit that
        // short-circuit, every real finding needs its own row for accurate per-feature counting). Additive
        // only - does not replace or interact with the existing self_falsification wishlist/task path.
        for (AuditViolation violation : validViolations) {
            // 2026-08-05: "causal_unjustified" (Pearl's ladder of causation category, added the same night as
            // the philosopher corpus work) was missing here - the audit prompt asked for it and the JSON
            // shape example showed it, but this persistence filter was never updated, so every such finding
            // was silently dropped from code-integrity stats even though it was a real, valid violation type.
            if (!"stub".equalsIgnoreCase(violation.type())
                    && !"layer_violation".equalsIgnoreCase(violation.type())
                    && !"causal_unjustified".equalsIgnoreCase(violation.type())) {
                continue;
            }
            CodeIntegrityFindingEntity finding = new CodeIntegrityFindingEntity();
            finding.setProjectId(project.getId());
            finding.setFalsificationRunId(run.getId());
            finding.setFindingType(violation.type().toLowerCase(java.util.Locale.ROOT));
            finding.setPrNumber(violation.prNumber());
            finding.setReason(violation.reason());
            java.util.Optional<TaskEntity> offendingTask = resolveTaskForPrNumber(project.getId(), violation.prNumber());
            finding.setFeatureId(offendingTask.map(TaskEntity::getFeatureId).orElse(null));
            codeIntegrityFindingRepository.save(finding);
            offendingTask.ifPresent(this::boostImpactedSiblingTasks);

            // Additive write to the shared evidence graph (EvidenceCoherenceService/Thagard) - every
            // code-integrity finding is inherently a negative signal (a real violation was confirmed), same
            // structural-polarity rule as the other two producers.
            EvidenceNodeEntity node = new EvidenceNodeEntity();
            node.setProjectId(project.getId());
            node.setFeatureId(finding.getFeatureId());
            node.setPrNumber(violation.prNumber());
            node.setPolarity(EvidenceNodeEntity.Polarity.NEGATIVE_FINDING);
            node.setSummaryText(finding.getFindingType() + " (" + violation.roleTag() + "): " + violation.reason());
            node.setCodeIntegrityFindingId(finding.getId());
            evidenceNodeRepository.save(node);
        }

        log.info("FalsificationCycleService: Completed audit for project {}. Checked roles: {}, Violations: {}, Follow-up wishlist items created: {}",
                project.getName(), rolesCheckedCount, violationsFoundCount, followUpsCreatedCount);
    }

    // Resolves a code-integrity finding's PR citation to the real task it belongs to, via
    // PrReviewEntity -> JulesSessionEntity.taskId -> TaskEntity chain SixSigmaAuditService's own
    // computePrConflictCounts already uses for a different purpose - exact attribution from data the system
    // already computes, never a fuzzy/guessed match. Empty (unattributed, not dropped) when no prNumber was
    // cited, or the cited number doesn't resolve to any review owned by this project.
    private java.util.Optional<TaskEntity> resolveTaskForPrNumber(UUID projectId, Integer prNumber) {
        if (prNumber == null) {
            return java.util.Optional.empty();
        }
        return prReviewRepository.findAll().stream()
                .filter(review -> prNumber.equals(review.getPrNumber()))
                .filter(review -> review.getJulesSessionId() != null)
                .map(review -> julesSessionRepository.findById(review.getJulesSessionId()))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .map(session -> taskRepository.findById(session.getTaskId()))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .filter(task -> task.getProject() != null && projectId.equals(task.getProject().getId()))
                .findFirst();
    }

    private static final double IMPACT_BOOST_COEFFICIENT_THRESHOLD = 0.8;
    // Same priority floor GeminiObserverActionService.boostPriority already uses for "bump this above
    // normal dispatch order" - one shared semantic, not a second invented number.
    private static final int IMPACT_BOOST_PRIORITY = 100;

    // 2026-08-07 (Kaizen/Jidoka wiring): impact_coefficients is a full role-to-role blast-radius matrix
    // already computed for every task at compile time (TechnicalLeadCompiler.createImpactMatrix) and,
    // before this, read back exactly once - for a dashboard "role doctrine readiness" score, never to
    // change what the system actually does (confirmed live audit, 2026-08-07). A confirmed code-integrity
    // finding (this method's caller) is real evidence that the offending task's OWN role just shipped a
    // real defect - Toyota-style Jidoka says an abnormality should trigger inspection of what else is
    // exposed to it, not just a local fix. Boosts queued/claimed sibling tasks in the SAME feature whose
    // role has a high (>=0.8, the matrix's own "affected role" tier - see createImpactMatrix) coefficient
    // from the offending role, so they get picked up and re-verified sooner instead of silently building on
    // top of a role that was just caught with a real defect.
    private void boostImpactedSiblingTasks(TaskEntity offendingTask) {
        if (offendingTask.getFeatureId() == null || offendingTask.getRole() == null
                || offendingTask.getPayload() == null || offendingTask.getProject() == null) {
            return;
        }
        com.fasterxml.jackson.databind.JsonNode coefficients = offendingTask.getPayload().path("impact_coefficients");
        if (!coefficients.isObject()) {
            return;
        }
        String offendingRoleTag = offendingTask.getRole().getTag();
        List<TaskEntity> siblings = taskRepository.findByProjectIdOrderByCreatedAtDesc(offendingTask.getProject().getId()).stream()
                .filter(t -> offendingTask.getFeatureId().equals(t.getFeatureId()))
                .filter(t -> t.getStatus() == TaskStatus.queued || t.getStatus() == TaskStatus.claimed)
                .filter(t -> t.getRole() != null && !t.getRole().getTag().equals(offendingRoleTag))
                .toList();
        for (TaskEntity sibling : siblings) {
            double coefficient = coefficients.path(sibling.getRole().getTag()).asDouble(0.0);
            if (coefficient >= IMPACT_BOOST_COEFFICIENT_THRESHOLD && sibling.getPriority() < IMPACT_BOOST_PRIORITY) {
                sibling.setPriority(IMPACT_BOOST_PRIORITY);
                taskRepository.save(sibling);
                log.info("FalsificationCycleService: boosted task {} (role {}) to priority {} - confirmed code-integrity "
                                + "finding on role {} (impact coefficient {}) means this task's work is exposed to that defect",
                        sibling.getId(), sibling.getRole().getTag(), IMPACT_BOOST_PRIORITY, offendingRoleTag, coefficient);
            }
        }
    }

    private String consolidatedViolationContent(List<AuditViolation> violations) {
        StringBuilder content = new StringBuilder(
                "Self-falsification improvement cycle. Resolve only the confirmed findings below; do not invent adjacent scope.\n");
        int index = 1;
        for (AuditViolation violation : violations) {
            content.append("\nFinding ").append(index++).append(" [")
                    .append(violation.roleTag()).append("/").append(violation.type()).append("]: ");
            if ("methodological".equalsIgnoreCase(violation.type())) {
                content.append(violation.philosopher()).append(" - ").append(violation.thesis())
                        .append("; Must-Be: ").append(violation.mustBe())
                        .append("; Performance: ").append(violation.performance())
                        .append("; Attractive: ").append(violation.attractive());
            } else {
                content.append(violation.reason());
            }
        }
        return content.toString();
    }

    /**
     * Was "getLatestProjectDiff" - renamed because its old fallback was a category error, not just a
     * missing-data gap: when neither a local workspace nor a real Git diff was available (the normal case
     * for GitHub-based projects, which never keep a synced local clone), it queried
     * {@code pr_reviews.diff_summary} and handed that to the falsification auditor labelled as "the diff
     * to audit". That column has been repurposed system-wide to hold review VERDICT TEXT ("CORE
     * ARCHITECTURE VERIFIED. APPROVED...", "REVIEW REJECTED...") rather than an actual diff - so the
     * auditor was reading someone else's approval remark and being told it was the code. Confirmed live in
     * the test-twenty-fifth experiment: the fetched "diff" was a one-line PR-review-fallback remark, not a
     * single line of real code.
     *
     * Real code now comes from the actual GitHub API: the unified diffs of the most recently merged PRs
     * for this project (GitHubPullRequestService.fetchDiffText, the same method the PR-review fallback
     * uses to see real diffs). Falls back to a real local `git diff` only for projects still running
     * without GitHub. If neither yields anything, returns blank - the caller skips the cycle honestly
     * instead of auditing nothing.
     */
    public record RecentChanges(String text, Integer highestPrNumber) {
        static RecentChanges empty() {
            return new RecentChanges("", null);
        }
    }

    private RecentChanges getRecentCodeChangesForAudit(ProjectEntity project) {
        StringBuilder changes = new StringBuilder();
        Integer highestPrNumberThisBatch = null;

        // Lean: don't re-fetch and re-audit PRs already covered by a previous run - real GitHub API calls
        // and a real Jules session spent auditing code that hasn't changed since it was last checked.
        Integer lastAuditedPrNumber = falsificationRunRepository.findTopByProjectIdOrderByRunAtDesc(project.getId())
                .map(FalsificationRunEntity::getHighestPrNumberAudited)
                .orElse(null);

        var snapshot = gitHubPullRequestService.pullRequestSnapshot(project);
        if (snapshot.available()) {
            List<com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest> recentMerges =
                    snapshot.closed().stream()
                            .filter(com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest::merged)
                            .filter(pr -> lastAuditedPrNumber == null || pr.number() > lastAuditedPrNumber)
                            .sorted(java.util.Comparator.comparingInt(
                                    com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest::number).reversed())
                            .limit(MAX_MERGED_PRS_PER_AUDIT)
                            .toList();
            for (var pr : recentMerges) {
                java.util.Optional<String> diff = gitHubPullRequestService.fetchDiffText(project, pr.number());
                if (diff.isPresent() && !diff.get().isBlank()) {
                    changes.append("\n\n=== PR #").append(pr.number()).append(" \"").append(pr.title()).append("\" (merged) ===\n");
                    changes.append(truncate(diff.get(), MAX_DIFF_CHARS_PER_PR));
                    if (highestPrNumberThisBatch == null || pr.number() > highestPrNumberThisBatch) {
                        highestPrNumberThisBatch = pr.number();
                    }
                }
            }
            if (!recentMerges.isEmpty()) {
                log.info("FalsificationCycleService: Fetched real diffs for {} newly merged PR(s) for project {} (since PR #{})",
                        recentMerges.size(), project.getName(), lastAuditedPrNumber == null ? "none audited yet" : String.valueOf(lastAuditedPrNumber));
            }
        }

        if (changes.isEmpty() && project.getWorkspacePath() != null && !project.getWorkspacePath().isBlank()) {
            java.io.File workspaceDir = new java.io.File(project.getWorkspacePath());
            if (workspaceDir.exists() && workspaceDir.isDirectory()) {
                try {
                    ProcessBuilder pb = new ProcessBuilder("git", "diff", "HEAD~1");
                    pb.directory(workspaceDir);
                    pb.redirectErrorStream(true);
                    Process process = pb.start();
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)
                    );
                    StringBuilder diffSb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        diffSb.append(line).append("\n");
                    }
                    process.waitFor();
                    if (process.exitValue() == 0 && diffSb.length() > 0) {
                        log.info("FalsificationCycleService: Retrieved local Git diff for project {}", project.getName());
                        changes.append("\n\n=== Local workspace diff (HEAD~1) ===\n").append(truncate(diffSb.toString(), MAX_DIFF_CHARS_PER_PR));
                    }
                } catch (Exception e) {
                    log.warn("FalsificationCycleService: Failed to retrieve Git diff from workspace {}: {}",
                            project.getWorkspacePath(), e.getMessage());
                }
            }
        }

        if (changes.isEmpty()) {
            return RecentChanges.empty();
        }

        // 2026-08-09 (live incident, test-forty-third, ~38h of compounding contamination traced to its
        // exact origin): this used to also append LogScopeBuffer.recent(project.getId(), 60) here, under
        // the label "RECENT PROJECT OPERATIONAL ACTIVITY". LogScopeBuffer is correctly scoped (per its own
        // javadoc) to only ever hold PROJECT:{id}-tagged log lines, never SYSTEM-wide noise - but every
        // PROJECT-scoped line that exists is itself a record of EneikProductionSys's OWN orchestration
        // mechanics acting on this project (dispatch, PR reconciliation, branch GC, claim/session
        // bookkeeping - literally ScopedBufferAppender.append copying event.getLoggerName() +
        // event.getFormattedMessage() verbatim), never the delivered product's own runtime - Eneik has no
        // visibility into that at all. Labelling this "your project's recent activity" and handing it to a
        // Jules session whose only write access is the CLIENT repo was a category error: Jules read real
        // sentences about JulesApiClient, PipelineTelemetryService, Flow Core, and PR/task reconciliation,
        // and - having nowhere else to put a "fix" for what it was reading - fabricated matching classes
        // inside test-forty-third's own repo. Confirmed as the exact entry point: this is the ONLY place in
        // the codebase that ever hands LogScopeBuffer content to a project-scoped Jules prompt (the other
        // caller, ProjectController's debug endpoint, is human-facing only). Removed outright rather than
        // filtered/relabelled - there is no version of "here is Eneik's own orchestration log" that belongs
        // in a brief for a session that can only write to the client's product code.
        return new RecentChanges(changes.toString(), highestPrNumberThisBatch);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n... [truncated at " + maxLength + " chars]";
    }
}
