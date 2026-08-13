package com.eneik.production.controllers;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.EvidenceNodeEntity;
import com.eneik.production.models.persistence.GeminiObserverActionEntity;
import com.eneik.production.models.persistence.GeminiObserverJournalEntity;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.OperationalRealityFindingEntity;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.CoherenceRunRepository;
import com.eneik.production.repositories.EvidenceNodeRepository;
import com.eneik.production.repositories.GeminiObserverActionRepository;
import com.eneik.production.repositories.GeminiObserverJournalRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.OperationalRealityFindingRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.ContinuousOrchestrationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-only exposure of Gemini's own real journal/action tables plus the shared evidence graph
 * (2026-08-08, engineering invariant #14 follow-up, operator directive: "у джемини есть свой лог ты мог там
 * посмотреть"). Before this existed, checking what she actually saw/did meant reconstructing it from docker
 * container logs (lost across every restart) or source code inference - both give an incomplete, sometimes
 * misleading picture, exactly the gap that triggered this endpoint's creation. GeminiObserverJournalEntity/
 * GeminiObserverActionEntity are the real record ("regardless of what she later claims in her journal
 * prose" - see GeminiObserverActionService's own class doc) - this makes that record actually reachable.
 * Restricted to localhost in production via filter/security, same as InternalTaskController.
 */
@RestController
@RequestMapping("/internal/gemini-observer")
public class InternalGeminiObserverController {

    private final GeminiObserverJournalRepository journalRepository;
    private final GeminiObserverActionRepository actionRepository;
    private final EvidenceNodeRepository evidenceNodeRepository;
    private final CoherenceRunRepository coherenceRunRepository;
    private final OperationalRealityFindingRepository operationalRealityFindingRepository;
    private final JulesSessionRepository julesSessionRepository;
    private final PrReviewRepository prReviewRepository;
    private final AccountRepository accountRepository;
    private final TaskRepository taskRepository;
    private final ContinuousOrchestrationService continuousOrchestrationService;
    private final com.eneik.production.services.GeminiObserverActionService geminiObserverActionService;
    private final com.eneik.production.repositories.ProjectRepository projectRepository;
    private final com.eneik.production.repositories.PersistentWorkerSessionRepository persistentWorkerSessionRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public InternalGeminiObserverController(GeminiObserverJournalRepository journalRepository,
                                             GeminiObserverActionRepository actionRepository,
                                             EvidenceNodeRepository evidenceNodeRepository,
                                             CoherenceRunRepository coherenceRunRepository,
                                             OperationalRealityFindingRepository operationalRealityFindingRepository,
                                             JulesSessionRepository julesSessionRepository,
                                             PrReviewRepository prReviewRepository,
                                             AccountRepository accountRepository,
                                             TaskRepository taskRepository,
                                             ContinuousOrchestrationService continuousOrchestrationService,
                                             com.eneik.production.services.GeminiObserverActionService geminiObserverActionService,
                                             com.eneik.production.repositories.ProjectRepository projectRepository,
                                             com.eneik.production.repositories.PersistentWorkerSessionRepository persistentWorkerSessionRepository,
                                             org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.journalRepository = journalRepository;
        this.actionRepository = actionRepository;
        this.evidenceNodeRepository = evidenceNodeRepository;
        this.coherenceRunRepository = coherenceRunRepository;
        this.operationalRealityFindingRepository = operationalRealityFindingRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.prReviewRepository = prReviewRepository;
        this.accountRepository = accountRepository;
        this.taskRepository = taskRepository;
        this.continuousOrchestrationService = continuousOrchestrationService;
        this.geminiObserverActionService = geminiObserverActionService;
        this.projectRepository = projectRepository;
        this.persistentWorkerSessionRepository = persistentWorkerSessionRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    // Pure diagnostic (2026-08-13, operator directive: "базу сжать, там мусор" - before running any real
    // compaction, see what's actually taking the space). H2's INFORMATION_SCHEMA.TABLES carries a row-count
    // estimate per table, no need to touch/lock the live .mv.db file to read it.
    @GetMapping("/db-table-sizes")
    public List<java.util.Map<String, Object>> dbTableSizes() {
        return jdbcTemplate.queryForList(
                "SELECT TABLE_NAME, ROW_COUNT_ESTIMATE FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_SCHEMA = 'PUBLIC' ORDER BY ROW_COUNT_ESTIMATE DESC LIMIT 20");
    }

    // Manual trigger (2026-08-13, operator directive: "освободи те 3 finalizing вручную, не жди её цикл")
    // for the exact same audited action Gemini's own observer cycle already calls - not a new/different
    // release path, the identical GeminiObserverActionService.retireStuckWorker method, same
    // OperationalPolicyService.authorize() gate, same GeminiObserverActionEntity audit trail. Exists because
    // her tool choice each cycle is her own LLM judgment (see 2026-08-13 test-forty-fourth incident - she
    // called triggerCodeDefectFalsificationRun instead of retireStuckWorker for several cycles running),
    // not a guarantee she picks the one specific action needed right now.
    @PostMapping("/retire-stuck-worker-now")
    public String retireStuckWorkerNow(@RequestParam UUID projectId, @RequestParam UUID carrierTaskId,
                                        @RequestParam(defaultValue = "manual operator trigger") String reason) {
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        return geminiObserverActionService.retireStuckWorker(project, carrierTaskId.toString(), reason);
    }

    // Pure diagnostic (2026-08-13, live dispute continued): retireStuckWorkerNow against the carrier task
    // id Gemini's own journal referenced (363cc677...) returned "already retired, nothing to do" with
    // nothing released - meaning that specific worker row's currentBatchIds is empty, so it never held the
    // 3 wishlists actually stuck in `finalizing`. WishlistEntity has no reverse pointer to whichever worker
    // claimed it (only the worker's own JSON currentBatchIds says so) - lists every persistent worker row
    // for a project (including retired ones, unlike findActiveWorker) so the real holder can be found by
    // inspection instead of guessing from journal prose.
    // Pure diagnostic (2026-08-13, continued): persistentWorkers below showed NEITHER of this project's two
    // persistent workers held the 3 stuck wishlists - both were retired before the wishlists even existed.
    // The real batch membership for a compiler completion isn't PersistentWorkerSessionEntity.currentBatchIds
    // at all - it's a `compilesWishlistIds` JSON marker on the compiler TaskEntity's own payload (see
    // JulesDispatchService.compilerTaskWishlistIds/completeWishlistCompilation). Scans this ONE project's
    // tasks (not the forbidden unscoped /internal/tasks dump) for whichever compiler task's marker actually
    // contains the given wishlist id.
    @GetMapping("/wishlist-compiler-task")
    public java.util.Map<String, Object> wishlistCompilerTask(@RequestParam UUID projectId, @RequestParam UUID wishlistId) {
        for (TaskEntity task : taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)) {
            if (task.getPayload() == null) {
                continue;
            }
            var idsNode = task.getPayload().path("compilesWishlistIds");
            if (!idsNode.isArray()) {
                continue;
            }
            for (var idNode : idsNode) {
                if (wishlistId.toString().equals(idNode.asText(""))) {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("taskId", task.getId());
                    m.put("status", task.getStatus());
                    m.put("payload", task.getPayload());
                    return m;
                }
            }
        }
        return java.util.Map.of("found", false);
    }

    @GetMapping("/persistent-workers")
    public List<java.util.Map<String, Object>> persistentWorkers(@RequestParam UUID projectId) {
        return persistentWorkerSessionRepository.findByProjectId(projectId).stream()
                .map(w -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", w.getId());
                    m.put("purpose", w.getPurpose());
                    m.put("carrierTaskId", w.getCarrierTaskId());
                    m.put("retiredAt", w.getRetiredAt());
                    m.put("createdAt", w.getCreatedAt());
                    m.put("cycleCount", w.getCycleCount());
                    m.put("currentBatchIds", w.getCurrentBatchIds());
                    return m;
                })
                .toList();
    }

    // Manual trigger (2026-08-08, operator directive) for the exact same job the daily cron
    // (ContinuousOrchestrationService.resetDailyLimitedAccounts, "0 5 0 * * ?") already runs every night -
    // not a separate/different reset path, the identical method. Resets sessionsDispatchedToday to 0 and
    // daily_limited accounts back to idle; deliberately does NOT touch estimatedDailyCapacity (invariant
    // #15's learned belief about each account's real capacity persists across days - only the day's used-
    // budget counter resets, same as the natural midnight rollover would do).
    @PostMapping("/reset-daily-session-counts-now")
    public String resetDailySessionCountsNow() {
        continuousOrchestrationService.resetDailyLimitedAccounts();
        return "Reset sessionsDispatchedToday and daily_limited accounts - same job as the 00:05 UTC cron, run manually.";
    }

    // Pure diagnostic (2026-08-08, live dispute continued): the account-capacity view below showed 0
    // sessions counted against every active account both before and after the blocked-task fix - meaning
    // that fix isn't what's currently denying dispatch for BARCAN-TAG-11/07/02. Calls the REAL repository
    // method dispatchQueuedTasks itself uses, with the real project id and tag, to see directly whether it
    // finds a candidate account or not - ground truth instead of re-deriving the WHERE clause by hand.
    @GetMapping("/dispatch-capacity-probe")
    public java.util.Map<String, Object> dispatchCapacityProbe(@RequestParam UUID projectId, @RequestParam String tag) {
        var found = accountRepository.lockNextJulesAccountWithCapacity(projectId, tag, 3, null, 15, null);
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("tag", tag);
        result.put("found", found.isPresent());
        found.ifPresent(a -> result.put("accountName", a.getName()));
        return result;
    }

    // Pure diagnostic (2026-08-08, live dispute continued): dispatchCapacityProbe returned found=false for
    // every stuck role even though the capacity subquery showed 0/3 used - meaning some OTHER clause in
    // lockNextJulesAccountWithCapacity's WHERE is failing, on the raw entity (not the public /api/accounts
    // response, which redacts apiKey). Checks every clause explicitly, per non-decommissioned account, so
    // the real blocking condition is visible instead of inferred.
    @GetMapping("/dispatch-eligibility-detail")
    public java.util.List<java.util.Map<String, Object>> dispatchEligibilityDetail(@RequestParam String tag) {
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (AccountEntity a : accountRepository.findAll()) {
            if (a.getStatus() == com.eneik.production.models.persistence.AccountStatus.decommissioned) continue;
            java.util.Map<String, Object> row = new java.util.HashMap<>();
            row.put("name", a.getName());
            row.put("enabled", a.isEnabled());
            row.put("status", a.getStatus());
            row.put("apiKeyPresent", a.getApiKey() != null && !a.getApiKey().isBlank());
            row.put("currentProjectId", String.valueOf(a.getCurrentProjectId()));
            String caps = a.getCapabilities();
            boolean hasTag = caps != null && (caps.equals("*") || (","+caps+",").contains(","+tag+","));
            row.put("capabilities", caps);
            row.put("hasTagCapability", hasTag);
            row.put("sessionsDispatchedToday", a.getSessionsDispatchedToday());
            row.put("maxConcurrentSessions", String.valueOf(a.getMaxConcurrentSessions()));
            result.add(row);
        }
        return result;
    }

    // Pure diagnostic (2026-08-08, live dispute): reproduces lockNextJulesAccountWithCapacity's own counting
    // logic in Java, per account, both with and without the 'blocked' exclusion - so a claim about account
    // capacity can be checked against the EXACT same criteria the real dispatch query uses, not a proxy like
    // AccountEntity.status (which is a coarse top-level flag, not the per-account concurrent-session count
    // this query actually gates on - the two can disagree, which is exactly what triggered this endpoint).
    @GetMapping("/account-capacity")
    public java.util.List<java.util.Map<String, Object>> accountCapacity() {
        java.util.Map<UUID, java.util.List<JulesSessionEntity>> sessionsByAccount = new java.util.HashMap<>();
        for (JulesSessionEntity session : julesSessionRepository.findAll()) {
            if (session.getAccountId() == null) continue;
            if (!java.util.Set.of("queued", "running", "revising", "stuck").contains(session.getStatus())) continue;
            sessionsByAccount.computeIfAbsent(session.getAccountId(), k -> new java.util.ArrayList<>()).add(session);
        }
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (AccountEntity account : accountRepository.findAll()) {
            java.util.List<JulesSessionEntity> sessions = sessionsByAccount.getOrDefault(account.getId(), java.util.List.of());
            int countIncludingBlocked = 0;
            int countExcludingBlocked = 0;
            java.util.List<java.util.Map<String, Object>> detail = new java.util.ArrayList<>();
            for (JulesSessionEntity session : sessions) {
                TaskEntity task = taskRepository.findById(session.getTaskId()).orElse(null);
                if (task == null) continue;
                boolean countsUnderCurrentQuery = task.getStatus() != TaskStatus.done && task.getStatus() != TaskStatus.failed;
                boolean countsUnderFixedQuery = countsUnderCurrentQuery && task.getStatus() != TaskStatus.blocked;
                if (countsUnderCurrentQuery) countIncludingBlocked++;
                if (countsUnderFixedQuery) countExcludingBlocked++;
                detail.add(java.util.Map.of(
                        "taskId", task.getId(),
                        "taskStatus", task.getStatus(),
                        "sessionStatus", session.getStatus(),
                        "countsUnderCurrentQuery", countsUnderCurrentQuery,
                        "countsUnderFixedQuery", countsUnderFixedQuery));
            }
            java.util.Map<String, Object> row = new java.util.HashMap<>();
            row.put("accountName", account.getName());
            row.put("accountStatus", account.getStatus());
            row.put("maxConcurrentSessions", String.valueOf(account.getMaxConcurrentSessions()));
            row.put("countIncludingBlocked_currentLiveBehavior", countIncludingBlocked);
            row.put("countExcludingBlocked_afterMyFix", countExcludingBlocked);
            row.put("sessions", detail);
            result.add(row);
        }
        return result;
    }

    // Pure diagnostic (2026-08-08): no PrReviewEntity/JulesSessionEntity read path existed anywhere before
    // this - every diagnosis tonight had to guess from GitHub's API + task-level fields alone, which is
    // exactly what led to fixing the wrong thing twice. Returns real, current local belief-state for a
    // task's sessions/reviews so a diagnosis can be verified against actual data before touching any code.
    @GetMapping("/task-merge-evidence")
    public java.util.Map<String, Object> taskMergeEvidence(@RequestParam UUID taskId) {
        List<JulesSessionEntity> sessions = julesSessionRepository.findByTaskId(taskId);
        java.util.List<java.util.Map<String, Object>> sessionDetails = new java.util.ArrayList<>();
        for (JulesSessionEntity session : sessions) {
            List<PrReviewEntity> reviews = prReviewRepository.findByJulesSessionId(session.getId());
            java.util.List<java.util.Map<String, Object>> reviewDetails = new java.util.ArrayList<>();
            for (PrReviewEntity r : reviews) {
                reviewDetails.add(java.util.Map.of(
                        "prUrl", String.valueOf(r.getPrUrl()),
                        "prNumber", String.valueOf(r.getPrNumber()),
                        "merged", String.valueOf(r.getMerged()),
                        "hasCode", String.valueOf(r.getHasCode()),
                        "baseRef", String.valueOf(r.getBaseRef()),
                        "ciStatus", String.valueOf(r.getCiStatus())));
            }
            java.util.Map<String, Object> sd = new java.util.HashMap<>();
            sd.put("sessionId", session.getId());
            sd.put("externalSessionId", session.getExternalSessionId());
            sd.put("status", session.getStatus());
            sd.put("prUrl", session.getPrUrl());
            sd.put("reviews", reviewDetails);
            sessionDetails.add(sd);
        }
        return java.util.Map.of("taskId", taskId, "sessions", sessionDetails);
    }

    // One-time repair (2026-08-08, engineering invariant #14): clears a session's prUrl when it was
    // corrupted by the now-fixed Closeout-PR token-collision bug (see GitHubPullRequestService.isCloseoutPr's
    // javadoc) - deliberately does NOT set it to a "correct" URL by hand (Claude/a human guessing which PR
    // is real is exactly the mistake that happened twice tonight before the real root cause was found).
    // Clearing it lets the now-corrected reconciliation (reconcileMergedGitHubPullRequests's branch-token
    // fallback, which excludes Closeout PRs) rediscover the task's real evidence itself on its next cycle -
    // trusting the fixed mechanism to find the truth, not a manually-asserted conclusion.
    @org.springframework.web.bind.annotation.PostMapping("/clear-corrupted-session-pr-url")
    public java.util.Map<String, Object> clearCorruptedSessionPrUrl(@RequestParam UUID sessionId) {
        JulesSessionEntity session = julesSessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return java.util.Map.of("error", "session not found");
        }
        String before = session.getPrUrl();
        session.setPrUrl(null);
        julesSessionRepository.save(session);
        return java.util.Map.of("sessionId", sessionId, "prUrlBefore", String.valueOf(before), "prUrlAfter", "null");
    }

    @GetMapping("/journal")
    public List<GeminiObserverJournalEntity> journal(@RequestParam UUID projectId,
                                                       @RequestParam(required = false) Instant from,
                                                       @RequestParam(required = false) Instant to) {
        Instant windowFrom = from != null ? from : Instant.EPOCH;
        Instant windowTo = to != null ? to : Instant.now();
        return journalRepository.findByProjectIdAndCreatedAtBetweenOrderByCreatedAtAsc(projectId, windowFrom, windowTo);
    }

    @GetMapping("/actions")
    public List<GeminiObserverActionEntity> actions(@RequestParam UUID projectId,
                                                      @RequestParam(required = false) Instant from,
                                                      @RequestParam(required = false) Instant to) {
        Instant windowFrom = from != null ? from : Instant.EPOCH;
        Instant windowTo = to != null ? to : Instant.now();
        return actionRepository.findByProjectIdAndCreatedAtBetweenOrderByCreatedAtAsc(projectId, windowFrom, windowTo);
    }

    @GetMapping("/evidence-nodes")
    public List<EvidenceNodeEntity> evidenceNodes(@RequestParam UUID projectId,
                                                   @RequestParam(required = false) UUID featureId,
                                                   @RequestParam(required = false) Integer prNumber) {
        if (prNumber != null) {
            return evidenceNodeRepository.findByProjectIdAndPrNumber(projectId, prNumber);
        }
        if (featureId != null) {
            return evidenceNodeRepository.findByProjectIdAndFeatureId(projectId, featureId);
        }
        return evidenceNodeRepository.findByProjectIdAndCreatedAtAfter(projectId, Instant.EPOCH);
    }

    @GetMapping("/coherence-runs")
    public List<?> coherenceRuns(@RequestParam UUID projectId) {
        return coherenceRunRepository.findByProjectIdOrderByRanAtDesc(projectId);
    }

    @GetMapping("/operational-reality-findings")
    public List<OperationalRealityFindingEntity> operationalRealityFindings(@RequestParam UUID taskId) {
        return operationalRealityFindingRepository.findByTaskId(taskId);
    }
}
