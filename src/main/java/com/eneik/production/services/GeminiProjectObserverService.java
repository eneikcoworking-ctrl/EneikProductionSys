package com.eneik.production.services;

import com.eneik.production.models.persistence.GeminiObserverJournalEntity;
import com.eneik.production.models.persistence.LeanValue;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
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
import java.util.List;
import java.util.Map;

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
    private static final List<TaskStatus> STUCK_CANDIDATE_STATUSES = List.of(TaskStatus.blocked, TaskStatus.queued);
    private static final Duration TASK_STUCK_THRESHOLD = Duration.ofHours(24);
    private static final Duration WISHLIST_STALE_THRESHOLD = Duration.ofHours(24);
    // How many of her own last journal entries must show a near-identical readiness ratio, while the
    // project is still incomplete, before it counts as genuine stagnation rather than normal short-term
    // noise - fewer than this and a temporarily flat ratio (e.g. between two merges) looks the same as a
    // real stall, which would make the signal fire too eagerly to be useful.
    private static final int STAGNATION_MIN_MATCHING_CYCLES = 3;
    private static final double STAGNATION_EPSILON = 0.001;
    private static final int MAX_STALE_CANDIDATES_LISTED = 5;

    private final ProjectRepository projectRepository;
    private final WishlistRepository wishlistRepository;
    private final TaskRepository taskRepository;
    private final ClientDeliverableReadinessService readinessService;
    private final GeminiObserverJournalRepository journalRepository;
    private final GeminiContextService geminiContextService;
    private final MLPredictionServiceClient mlPredictionServiceClient;
    private final WishlistContentSimilarityMatcher wishlistContentSimilarityMatcher;
    private final GeminiObserverActionService actionService;
    private final SystemSettingsService settingsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiProjectObserverService(ProjectRepository projectRepository,
                                         WishlistRepository wishlistRepository,
                                         TaskRepository taskRepository,
                                         ClientDeliverableReadinessService readinessService,
                                         GeminiObserverJournalRepository journalRepository,
                                         GeminiContextService geminiContextService,
                                         MLPredictionServiceClient mlPredictionServiceClient,
                                         WishlistContentSimilarityMatcher wishlistContentSimilarityMatcher,
                                         GeminiObserverActionService actionService,
                                         SystemSettingsService settingsService) {
        this.projectRepository = projectRepository;
        this.wishlistRepository = wishlistRepository;
        this.taskRepository = taskRepository;
        this.readinessService = readinessService;
        this.journalRepository = journalRepository;
        this.geminiContextService = geminiContextService;
        this.mlPredictionServiceClient = mlPredictionServiceClient;
        this.wishlistContentSimilarityMatcher = wishlistContentSimilarityMatcher;
        this.actionService = actionService;
        this.settingsService = settingsService;
    }

    @Scheduled(cron = "${gemini-project-observer.cron:0 */30 * * * ?}")
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
        List<GeminiObserverJournalEntity> recentJournal =
                journalRepository.findTop5ByProjectIdOrderByCreatedAtDesc(project.getId());
        Instant since = recentJournal.stream()
                .map(GeminiObserverJournalEntity::getCreatedAt)
                .max(Comparator.naturalOrder())
                .orElse(project.getCreatedAt());

        EvidenceSnapshot snapshot = buildEvidenceSnapshot(project, since, !recentJournal.isEmpty(), recentJournal);
        if (snapshot.nothingChanged()) {
            // Real cost lever (2026-07-25, operator directive: maximize token savings without weakening
            // Gemini's reasoning): most 30-minute cycles for a stable/idle project have nothing new at all -
            // skip the Gemini call entirely rather than paying for a round trip whose answer is
            // structurally guaranteed to be "nothing notable". This is NOT the same as writing a fallback
            // entry ourselves - it is simply not consulting her this cycle, so nothing is written to her
            // journal at all, and "since" naturally carries forward to the next cycle that has real change.
            log.debug("GeminiProjectObserverService: project {} - nothing changed since last visit, skipping Gemini call", project.getId());
            return;
        }

        String journalBlock = formatJournalForPrompt(recentJournal);
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
                run every 30 minutes. You do NOT see the backend's internal implementation logs or any source
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

                ACTIONS - you may take a small number of real, reversible operations directly, in addition to
                (or instead of) raising a finding. Never invent a target id - only ever use an id that
                literally appears in the snapshot below. Every action is logged and independently audited,
                so only propose one when the evidence in the snapshot genuinely supports it - these mutate
                real state, unlike findings, which are only ever a suggestion.
                - dismissWishlist: cancel a wishlist item that is genuinely dead weight (listed as a stale
                  candidate, or a duplicate/superseded finding) - targetId = the wishlist id.
                - nudgeStuckSession: push a stuck task's live Jules session to respond now instead of waiting
                  - targetId = the task id, only for a task listed as a stuck/blocked candidate.
                - abandonConflict: give up on a conflict that is clearly beyond resolving - targetId = the
                  conflict id (only if one is explicitly listed in the snapshot).
                - boostPriority: raise a genuinely bottlenecked queued task above normal priority - targetId
                  = the task id, only for a task listed as a stuck/queued candidate.
                - triggerFalsificationRun: pull the philosophical falsification pass forward instead of
                  waiting for its own schedule - targetId = the project id (given below).

                Return ONLY JSON: {"journalEntry": "one short paragraph, your own notes for your future self -
                what you checked and concluded this cycle, even if nothing notable happened",
                "findings": [{"summary": "one sentence", "evidence": "what in the snapshot shows this",
                "severity": "low|medium|high"}],
                "actions": [{"tool": "dismissWishlist|nudgeStuckSession|abandonConflict|boostPriority|triggerFalsificationRun",
                "targetId": "an id copied exactly from the snapshot", "reason": "one sentence, why this is justified"}]}
                Use empty arrays when nothing genuinely warrants them - still always write a journalEntry.
                """;

        String prompt = (retrievedContext.isBlank() ? "" : retrievedContext + "\n\n")
                + "Your own journal from previous cycles:\n" + journalBlock
                + "\n\nProject id: " + project.getId()
                + "\nCurrent evidence snapshot for project \"" + project.getName() + "\":\n\n" + snapshot.text();

        // Flash tier, not chatCritical/pro (2026-07-25, operator directive: maximize token savings without
        // weakening reasoning quality). Unlike PR-review/merge-gating calls, this is a conservative,
        // structured-evidence-only noticing task - exactly the shape a cheaper model tier is suited for, and
        // the system instruction already asks for restraint regardless of model tier. Explicit caching (same
        // key every cycle) means the instruction above is billed at Gemini's reduced cached-token rate after
        // the first call instead of resent in full every 30 minutes for every active project.
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
        journalRepository.save(journalEntry);

        if (created > 0 || actionsTaken > 0) {
            log.info("GeminiProjectObserverService: project {} - {} new finding(s), {} action(s) taken",
                    project.getName(), created, actionsTaken);
        }
    }

    private record EvidenceSnapshot(String text, boolean nothingChanged, double readinessRatio, String anomalySummary) {
    }

    /**
     * Structured, bounded facts about the project's real current state - never raw internal log lines. Cost
     * stays O(current state), not O(total project history): the "what changed" section only looks back to
     * Gemini's own last journal entry, not the whole project lifetime.
     */
    private EvidenceSnapshot buildEvidenceSnapshot(ProjectEntity project, Instant since, boolean hasPriorJournal,
                                                     List<GeminiObserverJournalEntity> recentJournal) {
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
        sb.append("\nDeliverable readiness: ").append(readiness.mergedDeliverables()).append('/')
                .append(readiness.totalDeliverables()).append(" merged, decompositionComplete=")
                .append(readiness.decompositionComplete());
        sb.append("\nEpics: ").append(epics.size()).append(" total, ").append(incompleteEpics).append(" incomplete");
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
        // "Nothing changed" only ever suppresses the call once a real baseline cycle has already run once
        // (hasPriorJournal) - the very first cycle for a project always calls Gemini so a baseline journal
        // entry exists to compare against later. A newly-detected anomaly (duplicates or stagnation) always
        // forces a real cycle regardless, even if no task resolved recently.
        boolean nothingChanged = hasPriorJournal && recentlyResolved.isEmpty()
                && duplicateDescriptionGroups.isEmpty() && !stagnant;
        return new EvidenceSnapshot(sb.toString(), nothingChanged, readiness.ratio(), String.join("; ", anomalies));
    }

    /**
     * True only when the project is genuinely incomplete AND her own last
     * {@value #STAGNATION_MIN_MATCHING_CYCLES}+ journal entries all recorded essentially the same readiness
     * ratio as right now - a short flat stretch between two merges is normal and must not trip this.
     */
    private boolean isReadinessStagnant(ClientDeliverableReadinessService.Readiness readiness,
                                          List<GeminiObserverJournalEntity> recentJournal) {
        if (readiness.decompositionComplete() && readiness.totalDeliverables() > 0
                && readiness.mergedDeliverables() >= readiness.totalDeliverables()) {
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

    private record Finding(String summary, String evidence, String severity) {
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
            JsonNode rawFindings = root.path("findings");
            List<Finding> findings = new ArrayList<>();
            if (rawFindings.isArray()) {
                for (JsonNode f : rawFindings) {
                    String summary = f.path("summary").asText("");
                    if (summary.isBlank()) {
                        continue;
                    }
                    findings.add(new Finding(summary, f.path("evidence").asText(""), f.path("severity").asText("low")));
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
