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

    // Philosophical falsification track (2026-07-25, operator directive): the formal track above answers
    // "does the shipped code contradict its own charters?" - this answers "is the shipped PRODUCT genuinely
    // what users need?", per philosopher (up to 13 roles x 6 philosophers = 78 voices), evaluated in Kano
    // terms. Deliberately generative, not corrective - see WishlistSource.philosophical_falsification and
    // applyPhilosophicalCritiques below for why it cannot share self_falsification's gating/dedup/Cynefin
    // semantics.
    //
    // These two numbers are deliberately NOT the noise-control mechanism - clustering (see
    // applyPhilosophicalCritiques/WishlistContentSimilarityMatcher.clusterBySimilarity) is: converging
    // voices merge into one wishlist per theme instead of any voice being individually judged and discarded.
    // They exist purely as a last-resort safety net for a genuinely degenerate run (e.g. clustering produces
    // far more orthogonal themes than normal), generous enough to almost never bind in practice.
    private static final int MAX_PHILOSOPHICAL_PROPOSALS_PER_RUN = 8;
    private static final int MAX_PENDING_PHILOSOPHICAL_WISHLISTS = 10;

    private final ProjectRepository projectRepository;
    private final RoleRepository roleRepository;
    private final RoleCapabilityLoader roleCapabilityLoader;
    private final WishlistRepository wishlistRepository;
    private final FalsificationRunRepository falsificationRunRepository;
    private final SystemSettingsService settingsService;
    private final com.eneik.production.services.github.GitHubPullRequestService gitHubPullRequestService;
    private final com.eneik.production.services.ProjectFlowService projectFlowService;
    private final ClientDeliverableReadinessService readinessService;
    private final WishlistContentSimilarityMatcher wishlistContentSimilarityMatcher;
    private final GeminiContextService geminiContextService;

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
                                     GeminiContextService geminiContextService) {
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
     * Public so the manual-trigger endpoint (ProjectController) can run this out-of-cycle without waiting
     * for the weekly cron - same "force" idiom already used elsewhere in this codebase for the onboarding
     * report re-run.
     */
    public void executePhilosophicalCycleForProject(ProjectEntity project) {
        executePhilosophicalCycleForProject(project, false);
    }

    public void executePhilosophicalCycleForProject(ProjectEntity project, boolean force) {
        if (!settingsService.effectiveBoolean("philosophical_falsification_enabled")) {
            log.info("FalsificationCycleService: Philosophical falsification track is disabled via feature flag; skipping project {}",
                    project.getName());
            return;
        }

        if (!force) {
            PhilosophicalReadinessInfo readinessInfo = philosophicalReadinessInfo(project);
            boolean hasRunBefore = readinessInfo.hasRunBefore();
            double applicableThreshold = readinessInfo.applicableThreshold();

            ClientDeliverableReadinessService.Readiness readiness = readinessService.computeForProject(project.getId());
            if (!readiness.decompositionComplete() || readiness.ratio() < applicableThreshold) {
                log.info("FalsificationCycleService: Project {} not ready for philosophical falsification yet "
                                + "({}% < {}% threshold, {} run); skipping",
                        project.getName(), Math.round(readiness.ratio() * 100), Math.round(applicableThreshold * 100),
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

        List<RoleEntity> activeRoles = roleRepository.findAll().stream()
                .filter(RoleEntity::isActive)
                .toList();
        if (activeRoles.isEmpty()) {
            return;
        }

        String runId = UUID.randomUUID().toString();
        String reportPath = ".eneik/records/philosophical-falsification-" + runId + ".json";
        String screenshotDir = ".eneik/records/philosophical-falsification-" + runId + "-screenshots/";
        String prompt = buildPhilosophicalAuditPrompt(project, activeRoles, reportPath, screenshotDir);

        UUID taskId = projectFlowService.dispatchPhilosophicalAudit(project, prompt, reportPath);
        if (taskId == null) {
            log.warn("FalsificationCycleService: Could not dispatch philosophical falsification audit for project {}", project.getName());
            return;
        }
        log.info("FalsificationCycleService: Dispatched philosophical falsification audit task {} for project {} covering {} active role(s) ({} philosopher voices)",
                taskId, project.getName(), activeRoles.size(), activeRoles.size() * 6);
    }

    private String buildPhilosophicalAuditPrompt(ProjectEntity project, List<RoleEntity> activeRoles, String reportPath, String screenshotDir) {
        StringBuilder charters = new StringBuilder();
        for (RoleEntity role : activeRoles) {
            String rawRules = readRawRules(role);
            if (rawRules == null || rawRules.isBlank()) {
                continue;
            }
            charters.append("\n\n=== ROLE ").append(role.getTag()).append(" CHARTER ===\n").append(rawRules);
        }

        // Direct Grounding: attach common patterns and per-role philosopher patterns
        try {
            java.nio.file.Path commonFile = java.nio.file.Paths.get("docs/philosopher-patterns/00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md");
            if (java.nio.file.Files.exists(commonFile)) {
                charters.append("\n\n=== COMMON ANALYTIC PROGRAMMING PATTERNS ===\n")
                        .append(java.nio.file.Files.readString(commonFile)).append("\n");
            }
            java.nio.file.Path philosophersDir = java.nio.file.Paths.get("docs/philosopher-patterns/philosophers");
            if (java.nio.file.Files.isDirectory(philosophersDir)) {
                for (RoleEntity role : activeRoles) {
                    try (var stream = java.nio.file.Files.newDirectoryStream(philosophersDir, role.getTag() + "*.md")) {
                        for (java.nio.file.Path philFile : stream) {
                            charters.append("\n\n=== PHILOSOPHER PATTERN: ").append(philFile.getFileName()).append(" ===\n")
                                    .append(java.nio.file.Files.readString(philFile)).append("\n");
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("FalsificationCycleService: failed to attach philosopher pattern files: {}", e.getMessage());
        }

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
                gap as a confusing screen, and most of the 13 roles' philosophers (data, backend, security,
                delivery) have essentially nothing to reason about from a screenshot alone.
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

                STEP 2 - the 78-voice pass. Each role charter below names 6 real philosophers in its
                "ФИЛОСОФСКИЙ ФУНДАМЕНТ" table. For EACH of these philosophers individually, reason as that
                actual historical thinker, using their real published worldview - explicitly NOT the narrow
                pre-baked "application" column in the table (e.g. if a table's "application" column only
                mentions a 100ms latency threshold, the real philosopher's worldview is much broader than
                that one column - use the whole of what they actually thought, not just this system's narrow
                paraphrase of it). Looking at the WHOLE product you just examined in STEP 1 - its UI where you
                could see it, AND its backend behavior, data model, and business logic - ask genuinely: what
                would THIS philosopher find missing, wrong, or worth adding about the product as a whole,
                judged by their own real standards? A philosopher whose lens is about data integrity, security,
                or business value should be reasoning about the backend/data-model evidence, not straining to
                say something about a screenshot. Most of the 78 will have nothing to say about this particular
                product - that is the expected, correct, honest outcome. Do not manufacture an opinion to have
                something to report.

                STEP 3 - forced Kano classification. Every critique you report MUST carry an explicit
                "kanoClass" chosen from exactly: "Must-Be", "Performance", "Attractive", "Indifferent". There
                is no default - a critique without an explicit, deliberately-chosen class is invalid; drop it
                yourself rather than omit the field or guess.

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
                """.formatted(screenshotDir, reportPath, screenshotDir, charters, knownContext);
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
    private static final List<String> KANO_ASSERTIVENESS_ORDER = List.of("Attractive", "Performance", "Must-Be", "Indifferent");

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
        if (!readiness.decompositionComplete() || readiness.ratio() < readinessThreshold) {
            // Auditing before there's a real object to audit just spends a reserved eneikdru session on
            // whatever process/design artifacts happen to be in main yet (confirmed live in
            // test-twenty-eighth: the first cycle ran against zero merged product code and found only
            // metadata-formatting nitpicks). Wait until most of what the client actually asked for has
            // really shipped (merged, not just review-approved - see ClientDeliverableReadinessService)
            // before spending capacity looking for violations in it.
            log.info("FalsificationCycleService: Project {} not ready for falsification yet ({}/{} planned task(s) merged, "
                            + "{}/{} feature(s) complete, decompositionComplete={}, {}% < {}% threshold); "
                            + "skipping this cycle instead of auditing an incomplete product iteration",
                    project.getName(), readiness.mergedDeliverables(), readiness.totalDeliverables(),
                    readiness.completeFeatures(), readiness.totalFeatures(), readiness.decompositionComplete(),
                    Math.round(readiness.ratio() * 100), Math.round(readinessThreshold * 100));
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

        UUID taskId = projectFlowService.dispatchFalsificationAudit(project, prompt, recentChanges.highestPrNumber(), reportPath);
        if (taskId == null) {
            log.warn("FalsificationCycleService: Could not dispatch falsification audit for project {}", project.getName());
            return;
        }
        log.info("FalsificationCycleService: Dispatched falsification audit task {} for project {} covering {} active role(s)",
                taskId, project.getName(), activeRoles.size());
    }

    private String buildAuditPrompt(ProjectEntity project, List<RoleEntity> activeRoles, String latestDiff, String reportPath) {
        StringBuilder briefSection = new StringBuilder();
        List<WishlistEntity> clientBriefs = wishlistRepository.findByProjectId(project.getId()).stream()
                .filter(w -> w.getSource() == WishlistSource.client)
                .toList();
        if (!clientBriefs.isEmpty()) {
            briefSection.append("\n\n=== ORIGINAL CLIENT SPECIFICATION & DECOMPOSITION COVERAGE ===\n");
            for (WishlistEntity brief : clientBriefs) {
                briefSection.append("Client Brief Content:\n").append(brief.getContent()).append("\n---\n");
            }
            briefSection.append("Audit Objective: Compare the above client specification against all merged PRs and actual code implementation below. Verify whether any requirements were missed, incomplete, or deviated from.\n");
        }

        StringBuilder charters = new StringBuilder();
        for (RoleEntity role : activeRoles) {
            String rawRules = readRawRules(role);
            if (rawRules == null || rawRules.isBlank()) {
                continue;
            }
            charters.append("\n\n=== ROLE ").append(role.getTag()).append(" CHARTER ===\n").append(rawRules);
        }

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

                Deliverable: create a new branch and open a PR that contains ONLY one file, `%s`
                (this EXACT path - it is unique to this task, do not use any other path), with EXACTLY
                this shape and no other files changed:
                {"violations": [
                  {"roleTag": "BARCAN-TAG-02", "type": "refusal_criteria", "reason": "concrete reason tied to the diff"},
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
            String attractive
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
        Integer watermark = highestPrNumberAudited == null ? previousHighest
                : (previousHighest == null ? highestPrNumberAudited : Math.max(previousHighest, highestPrNumberAudited));

        FalsificationRunEntity run = new FalsificationRunEntity();
        run.setProjectId(project.getId());
        run.setRunAt(Instant.now());
        run.setRolesCheckedCount(rolesCheckedCount);
        run.setViolationsFoundCount(violationsFoundCount);
        run.setTasksCreatedCount(followUpsCreatedCount);
        run.setHighestPrNumberAudited(watermark);
        falsificationRunRepository.save(run);

        log.info("FalsificationCycleService: Completed audit for project {}. Checked roles: {}, Violations: {}, Follow-up wishlist items created: {}",
                project.getName(), rolesCheckedCount, violationsFoundCount, followUpsCreatedCount);
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

    private String readRawRules(RoleEntity role) {
        if (role.getRulesPath() == null || role.getRulesPath().isBlank()) {
            return "";
        }
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(role.getRulesPath());
            if (java.nio.file.Files.exists(path)) {
                return java.nio.file.Files.readString(path);
            }
        } catch (Exception e) {
            log.warn("FalsificationCycleService: Failed to read raw rules for role {}: {}", role.getTag(), e.getMessage());
        }
        return "";
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

        java.util.List<String> recentActivity = com.eneik.production.services.logging.LogScopeBuffer.recent(project.getId(), 60);
        if (!recentActivity.isEmpty()) {
            changes.append("\n\n--- RECENT PROJECT OPERATIONAL ACTIVITY (last ").append(recentActivity.size()).append(" scoped log lines) ---\n");
            for (String line : recentActivity) {
                changes.append(line).append("\n");
            }
        }

        return new RecentChanges(changes.toString(), highestPrNumberThisBatch);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n... [truncated at " + maxLength + " chars]";
    }
}
