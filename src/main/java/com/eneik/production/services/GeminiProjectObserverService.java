package com.eneik.production.services;

import com.eneik.production.models.persistence.GeminiObserverActionEntity;
import com.eneik.production.models.persistence.GeminiObserverJournalEntity;
import com.eneik.production.models.persistence.LeanValue;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.GeminiObserverActionRepository;
import com.eneik.production.repositories.GeminiObserverJournalRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.logging.LogScope;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Periodic Gemini observer (2026-07-25, operator directive: "нужен был именно наблюдатель, джемини,
 * который подключается раз в 30 минут и анализирует лог создания проекта"). Redesigned the same day after
 * a sharp correction: the original version fed Gemini the backend's OWN internal Logback log every cycle -
 * a raw, ever-growing technical dump, and a direct contradiction of the no-full-resend principle
 * {@link GeminiContextService} was built around. This version follows {@link OpsAuditorService}'s
 * already-proven shape instead: the backend gathers a structured, bounded EVIDENCE SNAPSHOT of the
 * project's real current state (task/wishlist histograms, deliverable readiness, what changed since last
 * cycle, and now duration-based stagnation signals) - never internal log lines - and Gemini keeps her OWN
 * journal ({@link GeminiObserverJournalEntity}) for cross-cycle continuity, written by her, read back to her.
 *
 * 2026-07-26 extension (operator directive: "даем ей все полномочия - кроме кода"): she is no longer
 * report-only. She may propose a small set of real, reversible, non-code actions (see
 * {@link GeminiObserverActionService}) - dismiss dead wishlists, nudge a stuck session, abandon a dead
 * conflict, boost priority, pull a falsification pass forward. Every action is capped, scoped to her own
 * project, and independently audited in {@code gemini_observer_actions} regardless of what she claims.
 * Writing/deploying source code stays permanently off-limits to any autonomous agent (system-repo code is
 * Claude/operator-only; Jules sessions never touch anything but client project repos).
 */
@Service
public class GeminiProjectObserverService {
    private static final Logger log = LoggerFactory.getLogger(GeminiProjectObserverService.class);
    // Safety net, not the primary noise control (that's the system instruction's own "be conservative"
    // framing plus dedup against already-live wishlists) - same philosophy as the other generative
    // tracks' own per-run caps.
    private static final int MAX_FINDINGS_PER_RUN = 5;
    // Lower than findings on purpose - these mutate real state, findings only ever propose a wishlist.
    private static final int MAX_ACTIONS_PER_RUN = 3;
    // Only "done"/"failed" tasks are worth surfacing as a recency signal - everything else (queued,
    // in_progress, review, blocked) is normal, expected, and already visible in the status histogram below.
    private static final List<TaskStatus> NOTABLE_RECENT_STATUSES = List.of(TaskStatus.done, TaskStatus.failed);
    // 2026-07-26 operator directive ("проект стоит... применяла свои инструменты"): review/pending_review
    // added - confirmed live (test-thirty-eighth) these were the OLDEST-stuck tasks in the whole project
    // (11+ hours untouched, since minutes after project creation) but were invisible to her because only
    // blocked/queued counted as "stuck candidates". A task waiting on review with no forward motion for
    // hours is exactly the kind of thing she should be able to notice and act on.
    private static final List<TaskStatus> STUCK_CANDIDATE_STATUSES =
            List.of(TaskStatus.blocked, TaskStatus.queued, TaskStatus.review, TaskStatus.pending_review);
    // Lowered from 24h (2026-07-26, same directive): 24h meant NOTHING could ever qualify during a young
    // project's entire first day, no matter how long a task had genuinely been sitting untouched relative
    // to the project's own pace - confirmed live, a project ~11.5h old had tasks stuck ~11h+ with zero
    // candidates surfaced. 4h is still well above normal task turnover, not a false-alarm threshold.
    private static final Duration TASK_STUCK_THRESHOLD = Duration.ofHours(4);
    private static final Duration WISHLIST_STALE_THRESHOLD = Duration.ofHours(4);
    // How many of her own last journal entries must show a near-identical readiness ratio, while the
    // project is still incomplete, before it counts as genuine stagnation rather than normal short-term
    // noise - fewer than this and a temporarily flat ratio (e.g. between two merges) looks the same as a
    // real stall, which would make the signal fire too eagerly to be useful.
    private static final int STAGNATION_MIN_MATCHING_CYCLES = 3;
    private static final double STAGNATION_EPSILON = 0.001;
    private static final int MAX_STALE_CANDIDATES_LISTED = 5;
    // 2026-07-26 cost control (operator: "общая цифра быстро кончается"): a hard, code-enforced cap on her
    // own journalEntry length - the instruction below already asks for "one short paragraph", but nothing
    // previously stopped a verbose response from compounding every cycle (she re-reads her own last 5
    // entries every time).
    private static final int MAX_JOURNAL_ENTRY_CHARS = 500;

    private final ProjectRepository projectRepository;
    private final WishlistRepository wishlistRepository;
    private final TaskRepository taskRepository;
    private final ClientDeliverableReadinessService readinessService;
    private final GeminiObserverJournalRepository journalRepository;
    private final GeminiObserverActionRepository actionRepository;
    private final GeminiContextService geminiContextService;
    private final MLPredictionServiceClient mlPredictionServiceClient;
    private final WishlistContentSimilarityMatcher wishlistContentSimilarityMatcher;
    private final GeminiObserverActionService actionService;
    private final FalsificationCycleService falsificationCycleService;
    private final SystemSettingsService settingsService;
    private final com.eneik.production.services.github.GitHubApiBudgetService gitHubApiBudgetService;
    private final com.eneik.production.services.operational.OperationalFlowCoreService operationalFlowCoreService;
    private final com.eneik.production.kaizen.service.KaizenService kaizenService;
    private final com.eneik.production.services.orchestration.BranchGarbageCollectorService branchGarbageCollectorService;
    private final com.eneik.production.services.ProjectEventLogService projectEventLogService;
    private final com.eneik.production.services.audit.SixSigmaAuditService sixSigmaAuditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 2026-08-03: loggers worth her forensic attention for defect root-causing - the same three services
    // involved in every incident traced by hand this session (session/PR lifecycle and reconciliation).
    // Deliberately NOT the whole durable log - that would repeat the exact mistake the 2026-07-25/26 fix
    // corrected (feeding her the raw, ever-growing internal log as if it were the project).
    private static final Set<String> FORENSIC_LOGGERS = Set.of(
            "com.eneik.production.services.orchestration.BranchGarbageCollectorService",
            "com.eneik.production.services.AutoMergeService",
            "com.eneik.production.services.jules.JulesDispatchService");
    private static final int MAX_FORENSIC_LOG_LINES = 40;

    public GeminiProjectObserverService(ProjectRepository projectRepository,
                                         WishlistRepository wishlistRepository,
                                         TaskRepository taskRepository,
                                         ClientDeliverableReadinessService readinessService,
                                         GeminiObserverJournalRepository journalRepository,
                                         GeminiObserverActionRepository actionRepository,
                                         GeminiContextService geminiContextService,
                                         MLPredictionServiceClient mlPredictionServiceClient,
                                         WishlistContentSimilarityMatcher wishlistContentSimilarityMatcher,
                                         GeminiObserverActionService actionService,
                                         FalsificationCycleService falsificationCycleService,
                                         SystemSettingsService settingsService,
                                         com.eneik.production.services.github.GitHubApiBudgetService gitHubApiBudgetService,
                                         com.eneik.production.services.operational.OperationalFlowCoreService operationalFlowCoreService,
                                         com.eneik.production.kaizen.service.KaizenService kaizenService,
                                         com.eneik.production.services.orchestration.BranchGarbageCollectorService branchGarbageCollectorService,
                                         com.eneik.production.services.ProjectEventLogService projectEventLogService,
                                         com.eneik.production.services.audit.SixSigmaAuditService sixSigmaAuditService) {
        this.projectRepository = projectRepository;
        this.wishlistRepository = wishlistRepository;
        this.taskRepository = taskRepository;
        this.readinessService = readinessService;
        this.journalRepository = journalRepository;
        this.actionRepository = actionRepository;
        this.geminiContextService = geminiContextService;
        this.mlPredictionServiceClient = mlPredictionServiceClient;
        this.wishlistContentSimilarityMatcher = wishlistContentSimilarityMatcher;
        this.actionService = actionService;
        this.falsificationCycleService = falsificationCycleService;
        this.settingsService = settingsService;
        this.gitHubApiBudgetService = gitHubApiBudgetService;
        this.operationalFlowCoreService = operationalFlowCoreService;
        this.kaizenService = kaizenService;
        this.branchGarbageCollectorService = branchGarbageCollectorService;
        this.projectEventLogService = projectEventLogService;
        this.sixSigmaAuditService = sixSigmaAuditService;
    }

    // Widened from every 30 min to hourly (2026-07-26, operator: "общая цифра быстро кончается" - reduce
    // spend). The "nothing changed, skip" path already covers idle projects cheaply; this halves the call
    // count for an ACTIVE project too, which is where most of tonight's spend actually came from (skip
    // rarely triggers when there's real ongoing work). Offset to :20 rather than :00 purely to avoid
    // landing on the same minute as other schedules in this app (GeminiContextService's 3am reindex,
    // falsification's 4h ticks) - no real contention risk in a single-tenant app, just tidiness.
    @Scheduled(cron = "${gemini-project-observer.cron:0 20 * * * ?}")
    public void runObserverCycle() {
        if (!settingsService.effectiveBoolean("gemini_project_observer_enabled")) {
            return;
        }
        List<ProjectEntity> activeProjects = projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active);
        for (ProjectEntity project : activeProjects) {
            LogScope.project(project.getId());
            try {
                observeProject(project);
            } catch (Exception e) {
                log.error("GeminiProjectObserverService: observation failed for project {}: {}", project.getId(), e.getMessage(), e);
            } finally {
                LogScope.clear();
            }
        }
    }

    private void observeProject(ProjectEntity project) {
        // Mixed real+skip window (2026-07-30) - correct for stagnation-ratio and anomaly-fingerprint
        // history, which must keep accumulating even across cycles that skip the actual Gemini call.
        List<GeminiObserverJournalEntity> recentJournalAny =
                journalRepository.findTop5ByProjectIdOrderByCreatedAtDesc(project.getId());
        // Real-only counterparts for her own continuity: a long run of skip markers must never look like
        // "she was just here" or truncate what she actually needs to catch up on.
        List<GeminiObserverJournalEntity> recentRealJournal =
                journalRepository.findTop5ByProjectIdAndGeminiCalledTrueOrderByCreatedAtDesc(project.getId());
        Instant sinceRealVisit = journalRepository.findFirstByProjectIdAndGeminiCalledTrueOrderByCreatedAtDesc(project.getId())
                .map(GeminiObserverJournalEntity::getCreatedAt)
                .orElse(project.getCreatedAt());
        boolean hasPriorJournal = !recentRealJournal.isEmpty();

        Set<String> lastKnownFingerprints = recentJournalAny.stream()
                .max(Comparator.comparing(GeminiObserverJournalEntity::getCreatedAt))
                .map(e -> deserializeFingerprints(e.getAnomalyFingerprints()))
                .orElse(Set.of());
        // Mandatory follow-up gate (2026-07-30): if she took an action last cycle, the very next cycle must
        // consult her again to show its real outcome, regardless of whether anything else changed - an
        // unresolved defect she tried to act on must never silently drop off the radar just because the
        // fingerprint of the thing she acted on hasn't moved yet.
        boolean hasUnverifiedActions = actionRepository.existsByProjectIdAndVerifiedFalse(project.getId());

        EvidenceSnapshot snapshot = buildEvidenceSnapshot(
                project, sinceRealVisit, hasPriorJournal, recentJournalAny, lastKnownFingerprints, hasUnverifiedActions);
        if (snapshot.nothingChanged()) {
            // Real cost lever (2026-07-25, operator directive: maximize token savings without weakening
            // Gemini's reasoning): most cycles for a stable/idle project have nothing new at all - skip the
            // Gemini call entirely rather than paying for a round trip whose answer is structurally
            // guaranteed to be "nothing notable". A cheap, code-only marker is still written (2026-07-30)
            // so the readiness-ratio and anomaly-fingerprint history keeps accumulating across the skip -
            // otherwise the stagnation detector can never gather the 3+ observations it needs, because the
            // very act of skipping used to prevent any further history from ever existing.
            log.debug("GeminiProjectObserverService: project {} - nothing changed since last visit, skipping Gemini call", project.getId());
            writeSkipMarker(project, snapshot);
            return;
        }

        String journalBlock = formatJournalForPrompt(recentRealJournal);
        // 2026-07-26 addition: her own last few ACTIONS with their real outcome, not just her journal prose.
        // Confirmed live gap (test-thirty-eighth, 08:00 and 09:00): she proposed triggerFalsificationRun
        // twice in a row with near-identical reasoning, because the only continuity she had was her own
        // journal text, which never mentioned the attempt - the actions themselves live in a separate,
        // code-only audit table she was never shown. This closes that loop cheaply (a few extra lines in an
        // already-sent prompt, not a new call).
        String actionsBlock = formatRecentActionsForPrompt(
                actionRepository.findTop5ByProjectIdOrderByCreatedAtDesc(project.getId()));
        // 2026-07-26: the retrieval query is now anomaly-aware instead of a fixed generic template - if the
        // snapshot found something concrete (duplicates, stagnation), that text is folded into the query so
        // retrieval actually surfaces the most relevant known pattern/prior incident for THIS situation,
        // not just whatever is generically closest to "operational observation of project X".
        String retrievalQuery = "operational observation of project " + project.getName()
                + " - known failure patterns, standing engineering principles, prior incidents"
                + (snapshot.anomalySummary().isBlank() ? "" : ". Specifically: " + snapshot.anomalySummary());
        String retrievedContext = geminiContextService.buildContextBlock(retrievalQuery);

        // Static, byte-identical across every project and every cycle - deliberately kept free of anything
        // call-specific (RAG context, evidence, journal) so it can be explicitly cached (2026-07-25).
        // retrievedContext moves into the PROMPT instead of being appended here - appending it here would
        // change this text on every call and defeat the cache (each distinct systemInstruction gets its
        // own cache entry).
        String systemInstruction = """
                You are an external, autonomous observer of ONE software project's real, current state. You
                run every hour. You do NOT see the backend's internal implementation logs or any source
                code - you are given a structured snapshot of the project's actual current state (task/
                wishlist counts, deliverable readiness, what changed since your last visit, stagnation
                signals) and your OWN journal entries from previous cycles. Keep your own continuity: read
                your prior journal entries below before deciding what is genuinely new this cycle.
                Your job is to notice things a human operator would genuinely want to know about that are NOT
                already obviously being handled: a task failing once and being retried normally is NOT a
                finding; a task stuck the same way across many cycles, a real recurring failure pattern, or a
                concerning anomaly IS. Be conservative - most cycles should find nothing new.
                If the snapshot contains a "DUPLICATE TASK WARNING" section, treat it as a near-certain real
                bug (a task-generation loop or a broken decomposition, not a coincidence) and raise it as a
                finding with severity at least medium, unless your own prior journal entries show you already
                reported this exact duplicate group.
                If the snapshot contains a "STAGNATION WARNING", the project's real progress has not moved
                across several consecutive visits of yours - this is exactly the kind of thing a report-only
                observer would silently let sit. Prefer actually doing something about it (see ACTIONS below)
                over merely reporting it, when the snapshot gives you a concrete, listed candidate to act on.
                Do NOT raise a new finding for a stagnation (or duplicate-task) situation you already raised
                in a prior cycle and nothing about it has changed - check "Your recent actions" and your own
                journal below first; repeating the same finding every cycle just spawns redundant tasks for
                the same underlying issue.
                Every finding has a "scope": "product" (something wrong or missing in THIS project's own
                client-facing application - the normal case) or "platform" (something wrong in the
                orchestrator/factory system itself that runs and dispatches every project, e.g. a task stuck
                in the same status for a mechanical/administrative reason unrelated to this project's own
                code, an obviously wrong or self-contradictory readiness/state number, a dispatch decision
                that makes no sense given the evidence). Get this right - it changes where the finding goes.
                A "platform" finding is NEVER turned into work dispatched against this project's own
                repository (there is nothing in this project's own codebase that could fix an orchestrator
                bug) - it goes to a separate, human-reviewed queue instead. When genuinely unsure, use
                "platform", not "product": a wrongly-"platform" finding only delays a real product issue
                into that human-reviewed queue, recoverable at the next review pass; a wrongly-"product"
                finding becomes a live task dispatched straight into this project's own repository - and
                if it's actually about this factory's own pipeline/orchestrator, that task can never
                succeed, since the thing it's meant to fix does not exist in this project's codebase. Your
                own scope call here is also cross-checked against a deterministic list of this factory's
                own internal vocabulary (pipeline, dispatch, orchestrator, Jules session, wishlist compiler,
                and similar) - classify honestly based on what your evidence actually names, not by guessing
                which label sends it where you'd prefer.
                Money is being spent on every cycle you run whether or not the project moves forward - going
                quiet ("I will just keep observing") is only acceptable when the snapshot genuinely gives you
                NO candidate to act on. If triggerFalsificationRun is gated shut (per the Falsification
                readiness line or your own recent actions) but the snapshot lists ANY stuck/blocked task or
                stale wishlist candidate, try a DIFFERENT tool against that candidate instead of only
                retrying the same blocked one or falling silent - a stalled project with idle candidates
                sitting unused in the snapshot is the exact failure this authority was built to prevent.

                Below "Your recent actions" lists your own last real actions and their ACTUAL outcome (from
                the audit trail, not your memory) - check it before proposing an action: if you already tried
                something and it was skipped/failed for a reason that hasn't changed (e.g. a readiness gate
                that is still not met), proposing the identical action again will just fail identically -
                only retry if something concrete changed since.

                ACTIONS - you may take a small number of real, reversible operations directly, in addition to
                (or instead of) raising a finding. Never invent a target id - only ever use an id that
                literally appears in the CURRENT EVIDENCE SNAPSHOT below (never an id from "RELEVANT SYSTEM
                KNOWLEDGE" or your own past journal text - those describe other times and sometimes other
                projects entirely; an id from there is never a valid target here even if it looks right).
                Every action is logged and independently audited, so only propose one when the evidence in
                the snapshot genuinely supports it - these mutate real state, unlike findings, which are only
                ever a suggestion.
                - dismissWishlist: cancel a wishlist item that is genuinely dead weight (listed as a stale
                  candidate, or a duplicate/superseded finding) - targetId = the wishlist id.
                - nudgeStuckSession: push a stuck task's live Jules session to respond now instead of waiting
                  - targetId = the task id, for any task listed as a STUCK/BLOCKED TASK CANDIDATE (blocked,
                  queued, review, or pending_review - all four appear there). Safe to try even if it turns
                  out there is no live session left (it just reports that back, nothing breaks).
                - abandonConflict: give up on a conflict that is clearly beyond resolving - targetId = the
                  conflict id (only if one is explicitly listed in the snapshot).
                - boostPriority: raise a genuinely bottlenecked queued task above normal priority - targetId
                  = the task id, for a task listed as a STUCK/BLOCKED TASK CANDIDATE with status queued.
                - triggerFalsificationRun: pull the philosophical falsification pass forward instead of
                  waiting for its own schedule - targetId = the project id (given below). Check the
                  "Falsification readiness" line in the snapshot first - if the current ratio is already
                  below the required threshold, triggering this will just be gated and do nothing; only
                  propose it when the ratio has actually reached the threshold, or you have a genuine reason
                  to believe the gate itself is being evaluated incorrectly.
                - reviveFailedTask: requeue a task listed under FAILED TASK candidates for a fresh attempt -
                  targetId = the task id. Only ever tried once per task (the backend enforces this, not you)
                  and only for a task whose failure came from its PR closing without merging on GitHub with
                  nothing left actively working it - never for a task that failed for a content/quality
                  reason unrelated to that. Safe to try even if it turns out not eligible.
                - resolveOrphanedPr: close and re-queue a PR listed under ORPHANED PR WARNING - its owning
                  session already ended terminal (cancelled/closed_terminal_task/failed), so nothing else in
                  the system will ever pick this back up on its own; targetId = the task id. Re-checked
                  against real current state before acting, so safe to try even if it already resolved
                  itself between when you were shown it and now.

                Return ONLY JSON: {"journalEntry": "one short paragraph, your own notes for your future self -
                what you checked and concluded this cycle, even if nothing notable happened",
                "findings": [{"summary": "one sentence", "evidence": "what in the snapshot shows this",
                "severity": "low|medium|high", "scope": "product|platform"}],
                "actions": [{"tool": "dismissWishlist|nudgeStuckSession|abandonConflict|boostPriority|triggerFalsificationRun|reviveFailedTask|resolveOrphanedPr",
                "targetId": "an id copied exactly from the snapshot", "reason": "one sentence, why this is justified"}]}
                Use empty arrays when nothing genuinely warrants them - still always write a journalEntry.
                """;

        String prompt = (retrievedContext.isBlank() ? "" : retrievedContext + "\n\n")
                + "Your own journal from previous cycles:\n" + journalBlock
                + "\n\nYour recent actions (real audit trail, not your memory):\n" + actionsBlock
                + "\n\nProject id: " + project.getId()
                + "\nCurrent evidence snapshot for project \"" + project.getName() + "\":\n\n" + snapshot.text();

        // Flash tier, not chatCritical/pro (2026-07-25, operator directive: maximize token savings without
        // weakening reasoning quality). Unlike PR-review/merge-gating calls, this is a conservative,
        // structured-evidence-only noticing task - exactly the shape a cheaper model tier is suited for, and
        // the system instruction already asks for restraint regardless of model tier. Explicit caching (same
        // key every cycle) means the instruction above is billed at Gemini's reduced cached-token rate after
        // the first call instead of resent in full every cycle for every active project.
        String response = mlPredictionServiceClient.chat(prompt, systemInstruction, "gemini_project_observer_system_instruction");
        ObserverResponse parsed = parseResponse(response);
        if (parsed == null) {
            // Response genuinely could not be parsed as her own words - do NOT write a Claude-authored
            // fallback into her journal (that would be exactly the violation the operator flagged: "ты сам
            // ничего не пиши в его лог"). Skip this cycle entirely; "since" stays at the last real entry, so
            // nothing that happened during this failed cycle is lost - it just gets picked up next time.
            log.warn("GeminiProjectObserverService: project {} - Gemini response could not be parsed, skipping this cycle (not writing to her journal)", project.getId());
            return;
        }

        List<WishlistEntity> existingLive = wishlistRepository.findByProjectId(project.getId()).stream()
                .filter(w -> w.getStatus() == WishlistStatus.pending || w.getStatus() == WishlistStatus.compiling)
                .toList();

        int created = 0;
        for (Finding finding : parsed.findings()) {
            if (created >= MAX_FINDINGS_PER_RUN) {
                break;
            }
            String content = "Gemini project observer finding (severity: " + finding.severity() + "): " + finding.summary()
                    + "\nEvidence from the project's current state: " + finding.evidence();
            // 2026-08-01: a "platform" finding is about EneikProductionSys' own orchestrator code, not this
            // project's - dispatching it as a normal wishlist would create a task whose Jules session works
            // in THIS project's repo, where the thing it's meant to fix does not exist (confirmed live:
            // several such findings became permanently-unfixable "API Slice" tasks). Routed to a review-only
            // Kaizen proposal instead - never auto-applied, never touches source code autonomously.
            //
            // 2026-08-02 (Charter Pattern #12 - independent verification, not self-attestation): her own
            // scope self-classification is one signal, not the sole authority on it. Confirmed live in
            // test-fortieth: findings self-reported as "product" whose evidence was self-evidently about
            // this factory's own pipeline ("fix the state transition bug, so that the pipeline queue
            // resumes processing") polluted the project's real feature list with fake epics - 14 of 18
            // "epics" in that project turned out to be this kind of noise, not real client JTBDs. A
            // deterministic vocabulary scan (PlatformSelfReferenceDetector) cross-checks every
            // self-reported "product" finding; when it disagrees, don't trust either side blind - route
            // through the same review-only Kaizen path, tagged disputed, instead of creating a live
            // wishlist item.
            boolean selfReportedPlatform = "platform".equals(finding.scope());
            boolean looksLikePlatform = !selfReportedPlatform
                    && PlatformSelfReferenceDetector.looksLikePlatformFinding(finding.summary() + " " + finding.evidence());
            if (selfReportedPlatform || looksLikePlatform) {
                String label = selfReportedPlatform
                        ? "Gemini observer (platform): "
                        : "Gemini observer (disputed - self-reported product, but evidence names this factory's own internals): ";
                kaizenService.recordSystemicDefectProposal(project.getId(), project.getName(),
                        label + finding.summary(), content);
                created++;
                continue;
            }
            if (wishlistContentSimilarityMatcher.findLikelyDuplicate(existingLive, content).isPresent()) {
                continue;
            }
            WishlistEntity wishlist = new WishlistEntity();
            wishlist.setProjectId(project.getId());
            wishlist.setSource(WishlistSource.gemini_observer);
            wishlist.setSourceRoleTag("BARCAN-TAG-09");
            wishlist.setStatus(WishlistStatus.pending);
            wishlist.setLeanValue(LeanValue.valuable);
            wishlist.setTocConstraintRef("gemini_project_observer");
            wishlist.setSixSigmaMetric("observer_finding_rate");
            wishlist.setContent(content);
            wishlist.setJtbd("When the project's own current state shows a real, recurring problem, "
                    + "I want it surfaced and turned into a fix, so it stops silently recurring unnoticed");
            wishlist.setAcceptanceCriteria("Given this finding, When it is compiled into a task, "
                    + "Then the task addresses the specific evidence cited, not a generic restatement of the summary");
            wishlist.setDod("Observer finding resolved, or confirmed as a non-issue and dismissed");
            wishlistRepository.save(wishlist);
            created++;
        }

        int actionsTaken = 0;
        for (ProposedAction action : parsed.actions()) {
            if (actionsTaken >= MAX_ACTIONS_PER_RUN) {
                break;
            }
            if (action.tool() == null || action.targetId() == null || action.targetId().isBlank()) {
                continue;
            }
            String outcome = switch (action.tool()) {
                case "dismissWishlist" -> actionService.dismissWishlist(project, action.targetId(), action.reason());
                case "nudgeStuckSession" -> actionService.nudgeStuckSession(project, action.targetId(), action.reason());
                case "abandonConflict" -> actionService.abandonConflict(project, action.targetId(), action.reason());
                case "boostPriority" -> actionService.boostPriority(project, action.targetId(), action.reason());
                case "triggerFalsificationRun" -> actionService.triggerFalsificationRun(project, action.targetId(), action.reason());
                case "reviveFailedTask" -> actionService.reviveFailedTask(project, action.targetId(), action.reason());
                case "resolveOrphanedPr" -> actionService.resolveOrphanedPr(project, action.targetId(), action.reason());
                default -> null;
            };
            if (outcome == null) {
                log.warn("GeminiProjectObserverService: project {} proposed unknown tool '{}', ignored", project.getId(), action.tool());
                continue;
            }
            actionsTaken++;
        }

        GeminiObserverJournalEntity journalEntry = new GeminiObserverJournalEntity();
        journalEntry.setProjectId(project.getId());
        journalEntry.setCreatedAt(Instant.now());
        journalEntry.setEntry(parsed.journalEntry());
        journalEntry.setFindingsCount(created);
        journalEntry.setReadinessRatio(snapshot.readinessRatio());
        journalEntry.setGeminiCalled(true);
        journalEntry.setAnomalyFingerprints(serializeFingerprints(snapshot.anomalyFingerprints()));
        journalRepository.save(journalEntry);

        // She has now been shown (or had the opportunity to be shown, via "Your recent actions" above) the
        // real outcome of every action pending verification - close the loop instead of leaving it to
        // depend on something else also having changed this cycle.
        List<GeminiObserverActionEntity> unverified = actionRepository.findByProjectIdAndVerifiedFalse(project.getId());
        if (!unverified.isEmpty()) {
            for (GeminiObserverActionEntity action : unverified) {
                action.setVerified(true);
            }
            actionRepository.saveAll(unverified);
        }

        if (created > 0 || actionsTaken > 0) {
            log.info("GeminiProjectObserverService: project {} - {} new finding(s), {} action(s) taken",
                    project.getName(), created, actionsTaken);
        }
    }

    /**
     * Cheap, code-only journal marker for a cycle that skipped the real Gemini call - carries no LLM cost.
     * Keeps the stagnation-ratio and anomaly-fingerprint history accumulating across a silent stretch so the
     * detectors that depend on 3+ observations (see {@link #isReadinessStagnant}) are not structurally
     * starved by the very act of skipping.
     */
    private void writeSkipMarker(ProjectEntity project, EvidenceSnapshot snapshot) {
        GeminiObserverJournalEntity marker = new GeminiObserverJournalEntity();
        marker.setProjectId(project.getId());
        marker.setCreatedAt(Instant.now());
        marker.setEntry("");
        marker.setFindingsCount(0);
        marker.setReadinessRatio(snapshot.readinessRatio());
        marker.setGeminiCalled(false);
        marker.setAnomalyFingerprints(serializeFingerprints(snapshot.anomalyFingerprints()));
        journalRepository.save(marker);
    }

    private String serializeFingerprints(Set<String> fingerprints) {
        try {
            return objectMapper.writeValueAsString(fingerprints);
        } catch (Exception e) {
            return "[]";
        }
    }

    private Set<String> deserializeFingerprints(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            List<String> list = objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
            return new LinkedHashSet<>(list);
        } catch (Exception e) {
            return Set.of();
        }
    }

    private record EvidenceSnapshot(String text, boolean nothingChanged, double readinessRatio, String anomalySummary,
                                     Set<String> anomalyFingerprints) {
    }

    /**
     * Structured, bounded facts about the project's real current state - never raw internal log lines. Cost
     * stays O(current state), not O(total project history): the "what changed" section only looks back to
     * Gemini's own last journal entry, not the whole project lifetime.
     */
    private EvidenceSnapshot buildEvidenceSnapshot(ProjectEntity project, Instant since, boolean hasPriorJournal,
                                                     List<GeminiObserverJournalEntity> recentJournal,
                                                     Set<String> lastKnownFingerprints, boolean hasUnverifiedActions) {
        List<TaskEntity> tasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId());
        Map<TaskStatus, Long> taskHistogram = new EnumMap<>(TaskStatus.class);
        for (TaskEntity task : tasks) {
            taskHistogram.merge(task.getStatus(), 1L, Long::sum);
        }

        List<WishlistEntity> wishlists = wishlistRepository.findByProjectId(project.getId());
        Map<WishlistStatus, Long> wishlistHistogram = new EnumMap<>(WishlistStatus.class);
        for (WishlistEntity wishlist : wishlists) {
            wishlistHistogram.merge(wishlist.getStatus(), 1L, Long::sum);
        }

        ClientDeliverableReadinessService.Readiness readiness = readinessService.computeForProject(project.getId());
        List<ClientDeliverableReadinessService.EpicDiagnostic> epics = readinessService.listEpicDiagnostics(project.getId());
        long incompleteEpics = epics.stream().filter(e -> !e.complete()).count();

        List<TaskEntity> recentlyResolved = tasks.stream()
                .filter(t -> NOTABLE_RECENT_STATUSES.contains(t.getStatus()))
                .filter(t -> t.getUpdatedAt() != null && t.getUpdatedAt().isAfter(since))
                .toList();

        List<Map.Entry<String, Long>> duplicateDescriptionGroups = detectDuplicateDescriptions(tasks);

        Instant now = Instant.now();
        List<TaskEntity> stuckTasks = tasks.stream()
                .filter(t -> STUCK_CANDIDATE_STATUSES.contains(t.getStatus()))
                .filter(t -> t.getUpdatedAt() != null && t.getUpdatedAt().isBefore(now.minus(TASK_STUCK_THRESHOLD)))
                .limit(MAX_STALE_CANDIDATES_LISTED)
                .toList();
        List<WishlistEntity> staleWishlists = wishlists.stream()
                .filter(w -> w.getStatus() == WishlistStatus.pending)
                .filter(w -> w.getCreatedAt() != null && w.getCreatedAt().isBefore(now.minus(WISHLIST_STALE_THRESHOLD)))
                .limit(MAX_STALE_CANDIDATES_LISTED)
                .toList();

        boolean stagnant = isReadinessStagnant(readiness, recentJournal);

        StringBuilder sb = new StringBuilder();
        sb.append("Task status counts: ");
        taskHistogram.forEach((status, count) -> sb.append(status).append('=').append(count).append(' '));
        sb.append("\nWishlist status counts: ");
        wishlistHistogram.forEach((status, count) -> sb.append(status).append('=').append(count).append(' '));
        // 2026-07-26 operator directive ("считать по фичам, а не по таскам!"): readiness.ratio() now
        // reflects completeFeatures/totalFeatures, not mergedDeliverables/totalDeliverables - show both
        // numbers explicitly so she (and anyone reading her journal later) never confuses the two.
        sb.append("\nFeature readiness: ").append(readiness.completeFeatures()).append('/')
                .append(readiness.totalFeatures()).append(" features complete (this drives the ratio below)");
        sb.append("\nDeliverable detail: ").append(readiness.mergedDeliverables()).append('/')
                .append(readiness.totalDeliverables()).append(" individual work items merged, decompositionComplete=")
                .append(readiness.decompositionComplete());
        sb.append("\nEpics: ").append(epics.size()).append(" total, ").append(incompleteEpics).append(" incomplete");
        // 2026-07-26 addition: the actual triggerFalsificationRun gate, spelled out, so she can reason about
        // it instead of discovering it by trial and error (confirmed live: she retried the same gated action
        // twice in a row, test-thirty-eighth 08:00 and 09:00).
        FalsificationCycleService.PhilosophicalReadinessInfo falsificationInfo =
                falsificationCycleService.philosophicalReadinessInfo(project);
        sb.append("\nFalsification readiness: current ratio ").append(Math.round(readiness.ratio() * 100))
                .append("%, required ").append(Math.round(falsificationInfo.applicableThreshold() * 100))
                .append("% (").append(falsificationInfo.hasRunBefore() ? "subsequent" : "first").append(" run) - ")
                .append(readiness.ratio() >= falsificationInfo.applicableThreshold() ? "GATE MET" : "gate not met yet");
        // 2026-08-01 addition: without this she could never distinguish "genuinely nothing to do" from
        // "orchestration is denying its own actions" - confirmed live, test-fortieth's SYSTEM_STALLED state
        // self-blocked the very actions that would have cleared it, invisible to every earlier snapshot
        // version since it only ever showed readiness numbers, never Flow Core's own current state/reason.
        // Best-effort, like the hotspot lookups elsewhere in this codebase: a failure here must never break
        // the whole evidence-gathering pass, it should just fall back to not having this one extra signal.
        try {
            var flowCore = operationalFlowCoreService.build(project.getId());
            sb.append("\nFlow Core state: ").append(flowCore.snapshot().currentState());
            if (flowCore.snapshot().blockingReason() != null && !flowCore.snapshot().blockingReason().isBlank()) {
                sb.append(" - ").append(flowCore.snapshot().blockingReason());
            }
        } catch (Exception e) {
            log.debug("GeminiProjectObserverService: could not read Flow Core state for project {}: {}", project.getId(), e.getMessage());
        }
        // 2026-08-01 addition: same incident - coverage/review dispatch looked identical to "nothing to do"
        // when the real cause was the shared GitHub API budget being exhausted by another project entirely.
        try {
            var githubBudget = gitHubApiBudgetService.snapshot();
            sb.append("\nGitHub API budget: ").append(githubBudget.status())
                    .append(" (").append(githubBudget.remaining() == null ? "?" : githubBudget.remaining())
                    .append('/').append(githubBudget.limit() == null ? "?" : githubBudget.limit()).append(")");
        } catch (Exception e) {
            log.debug("GeminiProjectObserverService: could not read GitHub API budget for project {}: {}", project.getId(), e.getMessage());
        }
        // 2026-08-04 (3-layer Factory/Delivery/Product model, operator directive): a genuine cross-project
        // signal - is the WHOLE factory healthy, not just this one project - which she has never had
        // before. Everything else in this snapshot is project-scoped (Layer 2); this one line is Layer 1.
        // Uses calculateFullSixSigmaAudit, which - as of this same change - genuinely aggregates across
        // every project rather than silently resolving to one "active" project (a real, separate bug fixed
        // as part of building this).
        try {
            var factoryWide = sixSigmaAuditService.calculateFullSixSigmaAudit();
            sb.append("\nFactory-wide (Layer 1, all projects) Six Sigma: ").append(factoryWide.sigmaLevel())
                    .append("σ, DPMO ").append(factoryWide.dpmo()).append(", tier ").append(factoryWide.qualityTier())
                    .append(" - this is cross-project context, not evidence about THIS project specifically.");
        } catch (Exception e) {
            log.debug("GeminiProjectObserverService: could not read factory-wide Six Sigma audit: {}", e.getMessage());
        }
        sb.append("\nResolved since your last visit (").append(since).append("): ");
        if (recentlyResolved.isEmpty()) {
            sb.append("none");
        } else {
            for (TaskEntity task : recentlyResolved) {
                sb.append("\n  - [").append(task.getStatus()).append("] ")
                        .append(task.getTitle() != null ? task.getTitle() : task.getDescription())
                        .append(task.getStatus() == TaskStatus.failed && task.getJulesDispatchStatus() != null
                                ? " (" + task.getJulesDispatchStatus() + ")" : "");
            }
        }
        // Deterministic, code-only check (2026-07-26 operator directive: "плохо что джемини мониторинг
        // пропускает такое" - a live incident where a hardcoded bug generated 9 near-identical tasks over
        // 2 hours, invisible to the old evidence snapshot since it only ever gave status COUNTS, never
        // description-level comparison). This never needs Gemini's judgment to detect - identical
        // descriptions across 3+ non-terminal tasks is unambiguous mechanical evidence - but flagging it
        // for HER to decide whether it warrants a finding keeps the "backend gathers evidence, Gemini
        // reasons over it" contract intact rather than silently auto-acting on it.
        List<String> anomalies = new ArrayList<>();
        if (!duplicateDescriptionGroups.isEmpty()) {
            sb.append("\nDUPLICATE TASK WARNING: ");
            for (Map.Entry<String, Long> group : duplicateDescriptionGroups) {
                sb.append("\n  - ").append(group.getValue()).append(" tasks share the exact same description: \"")
                        .append(truncateForSnapshot(group.getKey(), 160)).append('"');
            }
            anomalies.add("duplicate task generation (" + duplicateDescriptionGroups.size() + " group(s))");
        }
        // 2026-07-26 addition: duration-based, not count-based - the old snapshot only ever gave point-in-
        // time counts, so a project stuck at the exact same readiness ratio for days looked identical to one
        // making steady progress, as long as SOME task moved status in between. Comparing this cycle's ratio
        // against her own last few journal entries' recorded ratio is what actually detects "no real
        // movement", not "no status changes at all".
        if (stagnant) {
            sb.append("\nSTAGNATION WARNING: deliverable readiness ratio has not moved (~")
                    .append(Math.round(readiness.ratio() * 100)).append("%) across your last ")
                    .append(STAGNATION_MIN_MATCHING_CYCLES).append("+ visits, and the project is not yet complete.");
            anomalies.add("readiness stagnant at ~" + Math.round(readiness.ratio() * 100) + "%");
        }
        if (!stuckTasks.isEmpty()) {
            sb.append("\nSTUCK/BLOCKED TASK CANDIDATES (idle > ").append(TASK_STUCK_THRESHOLD.toHours()).append("h): ");
            for (TaskEntity task : stuckTasks) {
                sb.append("\n  - taskId=").append(task.getId()).append(" [").append(task.getStatus()).append("] ")
                        .append(truncateForSnapshot(task.getTitle() != null ? task.getTitle() : task.getDescription(), 120));
            }
        }
        if (!staleWishlists.isEmpty()) {
            sb.append("\nSTALE WISHLIST CANDIDATES (pending > ").append(WISHLIST_STALE_THRESHOLD.toHours()).append("h, never compiled): ");
            for (WishlistEntity wishlist : staleWishlists) {
                sb.append("\n  - wishlistId=").append(wishlist.getId()).append(" ")
                        .append(truncateForSnapshot(wishlist.getContent(), 120));
            }
        }
        // 2026-08-01 addition: closes the exact gap that let task d9f35f4b/529e5252 sit unaddressed on
        // test-fortieth for hours - a terminal `failed` status was never shown here at all (only the
        // idle-too-long STUCK_CANDIDATE_STATUSES list above, which deliberately excludes failed since it's
        // not "idle", it's finished-but-broken). Scoped to the same reviveFailedTask-eligible cause
        // (PlannedWorkRecoveryService's marker string) so what she's shown here always matches what the
        // tool can actually act on - no point listing a failed task she'd only get "not eligible" back for.
        List<TaskEntity> revivableFailedTasks = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.failed)
                .filter(t -> t.getJulesDispatchStatus() != null && t.getJulesDispatchStatus()
                        .contains("left to complete it normally (periodic GitHub-truth reconciliation, testimony-vs-evidence Phase 2)"))
                .limit(MAX_STALE_CANDIDATES_LISTED)
                .toList();
        if (!revivableFailedTasks.isEmpty()) {
            sb.append("\nFAILED TASK CANDIDATES (PR closed without merging, nothing left working it - reviveFailedTask may apply): ");
            for (TaskEntity task : revivableFailedTasks) {
                sb.append("\n  - taskId=").append(task.getId()).append(" ")
                        .append(truncateForSnapshot(task.getTitle() != null ? task.getTitle() : task.getDescription(), 120));
            }
        }
        // 2026-08-03 addition (operator directive after a live incident, task 074efcb3/PR#38 on
        // test-forty-first, traced by hand): a session whose owning task collaterally cancelled it while it
        // had already done real, successful work (opened a mergeable PR) becomes invisible to every other
        // status-filtered sweep in the system - nothing else will ever surface it again on its own. Best-
        // effort like the other lookups here: never breaks the whole snapshot if it fails.
        List<com.eneik.production.services.orchestration.BranchGarbageCollectorService.OrphanedPrCandidate> orphanedPrs;
        try {
            orphanedPrs = branchGarbageCollectorService.findOrphanedPrCandidates(project);
        } catch (Exception e) {
            log.debug("GeminiProjectObserverService: could not check orphaned PRs for project {}: {}", project.getId(), e.getMessage());
            orphanedPrs = List.of();
        }
        if (!orphanedPrs.isEmpty()) {
            sb.append("\nORPHANED PR WARNING (owning session ended terminal, but the PR is still open on GitHub - resolveOrphanedPr may apply): ");
            for (var candidate : orphanedPrs) {
                sb.append("\n  - taskId=").append(candidate.taskId()).append(" PR #").append(candidate.pullNumber())
                        .append(" (").append(candidate.pullUrl()).append("), owning session status=").append(candidate.sessionStatus());
            }
            anomalies.add(orphanedPrs.size() + " orphaned PR(s) with a terminal owning session");
        }
        // 2026-08-03 addition (operator directive: "это её прямая задача читать все постоянные логи и
        // находить дефекты и причины дефектов" - after I had to trace today's incident by hand through
        // ProjectEventLogService because docker's own log buffer had already been lost across redeploys).
        // Bounded and filtered to the loggers that matter for defect forensics - the corrected, project-
        // scoped version of "read the log", not the whole-backend raw dump that was deliberately removed
        // in V58/restored durable-but-bounded on 2026-07-26. WARN/ERROR only - INFO noise (every routine
        // poll tick) would drown the signal and blow the token budget for no benefit.
        try {
            List<com.eneik.production.models.persistence.ProjectEventLogEntity> recentEvents =
                    projectEventLogService.since(project.getId(), since);
            List<com.eneik.production.models.persistence.ProjectEventLogEntity> forensicEvents = recentEvents.stream()
                    .filter(e -> ("WARN".equalsIgnoreCase(e.getLevel()) || "ERROR".equalsIgnoreCase(e.getLevel())))
                    .filter(e -> e.getLogger() != null && FORENSIC_LOGGERS.contains(e.getLogger()))
                    .toList();
            if (!forensicEvents.isEmpty()) {
                List<com.eneik.production.models.persistence.ProjectEventLogEntity> tail = forensicEvents.size() > MAX_FORENSIC_LOG_LINES
                        ? forensicEvents.subList(forensicEvents.size() - MAX_FORENSIC_LOG_LINES, forensicEvents.size())
                        : forensicEvents;
                sb.append("\nRECENT WARN/ERROR LOG (durable, deploy-independent - real evidence for root-causing a defect, not just its symptom count): ");
                for (var e : tail) {
                    sb.append("\n  - ").append(e.getCreatedAt()).append(" [").append(e.getLevel()).append("] ")
                            .append(truncateForSnapshot(e.getMessage(), 300));
                }
            }
        } catch (Exception e) {
            log.debug("GeminiProjectObserverService: could not read durable project log for project {}: {}", project.getId(), e.getMessage());
        }
        // Content-based dedup, not a hardcoded re-notify interval (2026-07-30): a stuck/stale candidate
        // that is genuinely new since the fingerprint last shown to her (its id was not there at all, or
        // its status has changed) forces a real cycle; one she has already been shown and that has not
        // moved stays silent indefinitely, no matter how much wall-clock time passes - repeating an
        // unchanged fact on a timer is waste, not vigilance.
        Set<String> currentFingerprints = computeAnomalyFingerprints(stuckTasks, staleWishlists, revivableFailedTasks, orphanedPrs);
        Set<String> newFingerprints = new LinkedHashSet<>(currentFingerprints);
        newFingerprints.removeAll(lastKnownFingerprints);
        boolean hasNewStuckEvidence = !newFingerprints.isEmpty();
        if (hasNewStuckEvidence) {
            anomalies.add(newFingerprints.size() + " new stuck/stale candidate(s) not previously surfaced");
        }

        // "Nothing changed" only ever suppresses the call once a real baseline cycle has already run once
        // (hasPriorJournal) - the very first cycle for a project always calls Gemini so a baseline journal
        // entry exists to compare against later. A newly-detected anomaly (duplicates, stagnation, a new/
        // changed stuck candidate) always forces a real cycle regardless, even if no task resolved
        // recently - and an action of hers still pending verification always forces one too (2026-07-30),
        // so she is never left not knowing whether her own intervention worked.
        boolean nothingChanged = hasPriorJournal && recentlyResolved.isEmpty()
                && duplicateDescriptionGroups.isEmpty() && !stagnant
                && !hasNewStuckEvidence && !hasUnverifiedActions;
        return new EvidenceSnapshot(sb.toString(), nothingChanged, readiness.ratio(), String.join("; ", anomalies), currentFingerprints);
    }

    private Set<String> computeAnomalyFingerprints(List<TaskEntity> stuckTasks, List<WishlistEntity> staleWishlists,
                                                     List<TaskEntity> revivableFailedTasks,
                                                     List<com.eneik.production.services.orchestration.BranchGarbageCollectorService.OrphanedPrCandidate> orphanedPrs) {
        Set<String> fingerprints = new LinkedHashSet<>();
        for (TaskEntity task : stuckTasks) {
            fingerprints.add("task:" + task.getId() + ":" + task.getStatus());
        }
        for (WishlistEntity wishlist : staleWishlists) {
            fingerprints.add("wishlist:" + wishlist.getId() + ":" + wishlist.getStatus());
        }
        for (TaskEntity task : revivableFailedTasks) {
            fingerprints.add("task:" + task.getId() + ":" + task.getStatus());
        }
        for (var candidate : orphanedPrs) {
            fingerprints.add("orphaned-pr:" + candidate.taskId() + ":" + candidate.pullNumber());
        }
        return fingerprints;
    }

    /**
     * True only when the project is genuinely incomplete AND her own last
     * {@value #STAGNATION_MIN_MATCHING_CYCLES}+ journal entries all recorded essentially the same readiness
     * ratio as right now - a short flat stretch between two merges is normal and must not trip this.
     */
    private boolean isReadinessStagnant(ClientDeliverableReadinessService.Readiness readiness,
                                          List<GeminiObserverJournalEntity> recentJournal) {
        // 2026-07-26: checks ratio() directly (feature-complete) rather than raw deliverable counts, so
        // this stays correct regardless of what granularity ratio() is computed from.
        if (readiness.decompositionComplete() && readiness.totalFeatures() > 0 && readiness.ratio() >= 1.0) {
            return false;
        }
        List<Double> priorRatios = recentJournal.stream()
                .map(GeminiObserverJournalEntity::getReadinessRatio)
                .filter(r -> r != null)
                .toList();
        if (priorRatios.size() < STAGNATION_MIN_MATCHING_CYCLES) {
            return false;
        }
        double current = readiness.ratio();
        return priorRatios.stream().allMatch(r -> Math.abs(r - current) < STAGNATION_EPSILON);
    }

    private static final int DUPLICATE_DESCRIPTION_THRESHOLD = 3;

    /** Groups NON-TERMINAL tasks by exact description text, returns groups at/above the threshold - a
     * done/failed/spike_completed task duplicating an older one is history, not an active problem. */
    private List<Map.Entry<String, Long>> detectDuplicateDescriptions(List<TaskEntity> tasks) {
        Map<String, Long> counts = tasks.stream()
                .filter(t -> t.getStatus() != TaskStatus.done && t.getStatus() != TaskStatus.failed
                        && t.getStatus() != TaskStatus.spike_completed)
                .filter(t -> t.getDescription() != null && !t.getDescription().isBlank())
                .collect(java.util.stream.Collectors.groupingBy(TaskEntity::getDescription, java.util.LinkedHashMap::new, java.util.stream.Collectors.counting()));
        return counts.entrySet().stream()
                .filter(e -> e.getValue() >= DUPLICATE_DESCRIPTION_THRESHOLD)
                .toList();
    }

    private static String truncateForSnapshot(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private String formatJournalForPrompt(List<GeminiObserverJournalEntity> recentJournal) {
        if (recentJournal.isEmpty()) {
            return "(none - this is your first cycle observing this project)";
        }
        StringBuilder sb = new StringBuilder();
        List<GeminiObserverJournalEntity> chronological = new ArrayList<>(recentJournal);
        chronological.sort(Comparator.comparing(GeminiObserverJournalEntity::getCreatedAt));
        for (GeminiObserverJournalEntity entry : chronological) {
            sb.append("- [").append(entry.getCreatedAt()).append("] ").append(entry.getEntry()).append('\n');
        }
        return sb.toString();
    }

    /**
     * The real audit trail of her own actions, chronological, with the ACTUAL outcome (not what she may
     * have hoped) - see {@link GeminiObserverActionEntity}. Distinct from the journal, which is only her own
     * self-reported prose; this is code-written, so it stays true even if she never mentioned the attempt.
     */
    private String formatRecentActionsForPrompt(List<GeminiObserverActionEntity> recentActions) {
        if (recentActions.isEmpty()) {
            return "(none yet)";
        }
        StringBuilder sb = new StringBuilder();
        List<GeminiObserverActionEntity> chronological = new ArrayList<>(recentActions);
        chronological.sort(Comparator.comparing(GeminiObserverActionEntity::getCreatedAt));
        for (GeminiObserverActionEntity action : chronological) {
            sb.append("- [").append(action.getCreatedAt()).append("] ").append(action.getTool())
                    .append("(targetId=").append(action.getTargetId()).append(") -> ").append(action.getOutcome());
            if (action.getDetail() != null && !action.getDetail().isBlank()) {
                sb.append(" (").append(action.getDetail()).append(')');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private record Finding(String summary, String evidence, String severity, String scope) {
        // Defaults an absent/blank/unrecognized scope to "product" (2026-08-01) rather than failing parsing -
        // a model response predating this field, or one that omits it, must never be silently treated as a
        // platform finding (the more consequential misroute of the two: a real product issue disappearing
        // into a review-only Kaizen queue instead of ever reaching the client project).
        Finding {
            scope = "platform".equalsIgnoreCase(scope) ? "platform" : "product";
        }
    }

    private record ProposedAction(String tool, String targetId, String reason) {
    }

    private record ObserverResponse(String journalEntry, List<Finding> findings, List<ProposedAction> actions) {
    }

    /**
     * Returns null when Gemini's own words could not be recovered from the response - the caller must skip
     * the cycle entirely rather than substitute a Claude-authored stand-in (2026-07-25, operator directive:
     * "так теперь ты сам ничего не пиши в его лог" - her journal only ever contains her own text, never mine).
     */
    private ObserverResponse parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }
        try {
            String cleaned = response.trim();
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start < 0 || end < 0 || end <= start) {
                return null;
            }
            JsonNode root = objectMapper.readTree(cleaned.substring(start, end + 1));
            String journalEntry = root.path("journalEntry").asText("");
            if (journalEntry.isBlank()) {
                return null;
            }
            if (journalEntry.length() > MAX_JOURNAL_ENTRY_CHARS) {
                journalEntry = journalEntry.substring(0, MAX_JOURNAL_ENTRY_CHARS) + "... [truncated]";
            }
            JsonNode rawFindings = root.path("findings");
            List<Finding> findings = new ArrayList<>();
            if (rawFindings.isArray()) {
                for (JsonNode f : rawFindings) {
                    String summary = f.path("summary").asText("");
                    if (summary.isBlank()) {
                        continue;
                    }
                    findings.add(new Finding(summary, f.path("evidence").asText(""), f.path("severity").asText("low"),
                            f.path("scope").asText("product")));
                }
            }
            JsonNode rawActions = root.path("actions");
            List<ProposedAction> actions = new ArrayList<>();
            if (rawActions.isArray()) {
                for (JsonNode a : rawActions) {
                    String tool = a.path("tool").asText("");
                    String targetId = a.path("targetId").asText("");
                    if (tool.isBlank() || targetId.isBlank()) {
                        continue;
                    }
                    actions.add(new ProposedAction(tool, targetId, a.path("reason").asText("")));
                }
            }
            return new ObserverResponse(journalEntry, findings, actions);
        } catch (Exception e) {
            log.warn("GeminiProjectObserverService: failed to parse Gemini response, skipping this cycle: {}", e.getMessage());
            return null;
        }
    }
}
