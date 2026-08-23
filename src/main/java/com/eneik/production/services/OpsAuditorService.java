package com.eneik.production.services;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.LeanValue;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.logging.LogScope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Embedded ops auditor (2026-07-25, operator directive: "too few systemic decisions... embed an
 * auditor like you right inside the backend... one that will push automation through and fix small bugs").
 * This is an automated version of exactly what the human orchestrator (this session) spent the last several
 * hours doing by hand: query real system state, diagnose what's actually blocking progress, apply a narrow,
 * safe, evidence-based fix.
 *
 * Design principle, explicitly requested wide-but-bounded: Gemini decides WHICH real, gathered evidence is
 * worth acting on and WHEN (no hardcoded "if X then always Y" rule forcing every case through one path) -
 * but it can only ever call a small, curated, pre-vetted TOOL SET (dismissOrphanedWishlist,
 * createTargetedRecoveryTask, flagForHumanReview), each of which re-validates its own precondition in code
 * before doing anything (defense in depth - the LLM's tool choice is trusted, but never its claim that a
 * precondition holds). It never gets raw SQL, git, or code-editing access. This mirrors the testimony-vs-
 * evidence principle already built into this codebase: the auditor only ever acts on independently-verified
 * facts gathered by this class itself, never on the LLM's own unverified assertions.
 *
 * createTargetedRecoveryTask (2026-08-07) closes the gap the class javadoc originally left open under the
 * name markTaskFailedWithEvidence: when a task is permanently retired as `failed` with no replacement
 * (iteration-admission poka-yoke, circuit-breaker exhaustion - see JulesDispatchService/ProjectFlowService),
 * any dependent task stuck waiting on it stays stuck forever unless something creates a real replacement
 * carrying the exact same role/featureId/semantic-key ClientDeliverableReadinessService.isDependencySatisfied
 * looks for. This is that something - a real task, not a hopeful wishlist that may or may not decompose into
 * a matching one.
 *
 * Deliberately excludes judgment calls about code CORRECTNESS (e.g. "this PR should be closed because it
 * would revert an architectural fix" - the real PR#78 decision made by the operator tonight) from the
 * autonomous tool set - that requires understanding product/architectural intent, not just reconciling
 * mechanical state drift. Only mechanical, structurally-verifiable reconciliation is in scope for v1.
 */
@Service
public class OpsAuditorService {
    private static final Logger log = LoggerFactory.getLogger(OpsAuditorService.class);

    private final ProjectRepository projectRepository;
    private final WishlistRepository wishlistRepository;
    private final TaskRepository taskRepository;
    private final SystemSettingsServiceAccessor settingsService;
    private final MLPredictionServiceClient mlPredictionServiceClient;
    private final GeminiContextService geminiContextService;
    private final ClientDeliverableReadinessService readinessService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Self-injected proxy reference (2026-08-07, same pattern/reason as ProcessControlService.self and
    // ProjectFlowService.self): createTargetedRecoveryTask is called from executeDecision within this same
    // class - a plain `this.` self-invocation bypasses the Spring AOP proxy entirely, so @Transactional on
    // it would silently never activate, leaving the check-then-create race (two near-concurrent audit
    // cycles on the same project both deciding to recover the same failed task) genuinely unguarded.
    // @Lazy breaks the constructor circular dependency this would otherwise create.
    private final OpsAuditorService self;

    public OpsAuditorService(ProjectRepository projectRepository,
                              WishlistRepository wishlistRepository,
                              TaskRepository taskRepository,
                              com.eneik.production.services.settings.SystemSettingsService settingsService,
                              MLPredictionServiceClient mlPredictionServiceClient,
                              GeminiContextService geminiContextService,
                              ClientDeliverableReadinessService readinessService,
                              @org.springframework.context.annotation.Lazy OpsAuditorService self) {
        this.projectRepository = projectRepository;
        this.wishlistRepository = wishlistRepository;
        this.taskRepository = taskRepository;
        this.settingsService = settingsService::effectiveBoolean;
        this.mlPredictionServiceClient = mlPredictionServiceClient;
        this.geminiContextService = geminiContextService;
        this.readinessService = readinessService;
        this.self = self;
    }

    // Thin functional seam so the constructor above doesn't need to import the concrete settings class
    // twice under two different names - purely a readability choice, not a real abstraction boundary.
    @FunctionalInterface
    private interface SystemSettingsServiceAccessor {
        boolean effectiveBoolean(String key);
    }

    @Scheduled(cron = "${ops-auditor.cron:0 */30 * * * ?}")
    public void runAuditCycle() {
        if (!settingsService.effectiveBoolean("ops_auditor_enabled")) {
            return;
        }
        List<ProjectEntity> activeProjects = projectRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProjectStatus.active)
                .toList();
        for (ProjectEntity project : activeProjects) {
            // Operator directive (2026-07-25): correct PROJECT:{id} scope per project, not SYSTEM for the
            // whole loop - same fix applied to GeminiProjectObserverService for the same reason.
            LogScope.project(project.getId());
            try {
                auditProject(project);
            } catch (Exception e) {
                log.error("OpsAuditorService: audit cycle failed for project {}: {}", project.getId(), e.getMessage(), e);
            } finally {
                LogScope.clear();
            }
        }
    }

    /** One piece of gathered, independently-verified evidence - never the LLM's own claim. */
    private record Evidence(String kind, String subjectId, String description) {
    }

    private void auditProject(ProjectEntity project) {
        List<Evidence> evidence = self.gatherAllEvidence(project);
        if (evidence.isEmpty()) {
            // Barcan condition (SYSTEMIC_REPAIR_PLAN_2026-08-17, defect D5): returning silently makes three
            // operationally different FACTORY states indistinguishable from outside - "swept, found nothing",
            // "did not sweep", and "service stopped". Declaring the outcome turns the first into an ABSTAIN
            // carrying its own witness (which gatherers were consulted) instead of an absence. This is a
            // factory-scope fact about the auditor itself; it says nothing about value delivery or about the
            // client's product, and must never be read as either.
            // Confirmed live 2026-08-17: two consecutive sweeps emitted no line at all while three terminally
            // failed tasks sat in the project, and the service's liveness could not be established from
            // outside at all - only by reading this method's source.
            // NOT the F56 defect (an action denied 35x in 35min with an empty work set): this is a periodic
            // sweep, once per 30 minutes per project, and its information content is non-zero precisely
            // because liveness is otherwise unobservable. A sweep must never be rate-limited into silence -
            // that is how monitoring stops without anyone noticing.
            log.info("OpsAuditorService: project {} - swept, 0 evidence item(s) from gatherers "
                            + "[orphaned_wishlist_behind_failed_task, orphaned_dependency_chain]; "
                            + "ABSTAIN - no decision requested",
                    project.getName());
            return;
        }

        String prompt = buildAuditPrompt(project, evidence);
        String systemInstruction = "You are a cautious, evidence-only operations auditor. You may ONLY act on the evidence "
                + "given to you in this prompt - never invent additional facts. Respond with strict JSON only.\n\n"
                + geminiContextService.buildContextBlock(prompt);
        String response = mlPredictionServiceClient.chatCritical(prompt, systemInstruction);
        List<AuditorDecision> decisions = parseDecisions(response);
        if (decisions.isEmpty()) {
            log.info("OpsAuditorService: project {} - {} evidence item(s) gathered, Gemini returned no actionable decisions",
                    project.getName(), evidence.size());
            return;
        }

        for (AuditorDecision decision : decisions) {
            executeDecision(project, decision);
        }
    }

    // 2026-08-07 (confirmed live incident, test-forty-third - same bug class as
    // ClientDeliverableReadinessService.computeForProject, found the same night): the evidence-gathering
    // walk below (via gatherOrphanedDependencyChainEvidence -> findDeadDependencyRoot) touches
    // TaskEntity.dependsOn, a lazy self-reference - without a transaction spanning the whole gather, a
    // proxy not already resident in whatever short session loaded the initial task list throws
    // LazyInitializationException ("no Session"). Scoped to ONLY the read-only evidence gather, not the
    // whole auditProject cycle - the Gemini call and executeDecision's real writes happen OUTSIDE this
    // transaction, same "short admit/decide transaction, slow work after" split already applied 4 times
    // tonight elsewhere (ProjectFlowService.dispatchFalsificationAudit and friends) - wrapping the network
    // call in a transaction too would just reintroduce that exact bug class in a new place. Routed through
    // self (see this class's own self field javadoc) since a plain `this.` call would bypass the proxy and
    // silently never open the transaction at all.
    @Transactional(readOnly = true)
    public List<Evidence> gatherAllEvidence(ProjectEntity project) {
        List<Evidence> evidence = new ArrayList<>(gatherOrphanedWishlistEvidence(project));
        evidence.addAll(gatherOrphanedDependencyChainEvidence(project));
        return evidence;
    }

    private List<Evidence> gatherOrphanedWishlistEvidence(ProjectEntity project) {
        List<Evidence> evidence = new ArrayList<>();
        List<WishlistEntity> candidateWishlists = wishlistRepository.findByProjectId(project.getId()).stream()
                .filter(w -> w.getCompiledByRole() != null)
                .filter(w -> w.getStatus() != WishlistStatus.dismissed)
                .toList();
        for (WishlistEntity wishlist : candidateWishlists) {
            List<TaskEntity> derivedTasks = taskRepository.findBySourceWishlistIdIn(List.of(wishlist.getId()));
            if (derivedTasks.isEmpty()) {
                continue;
            }
            boolean anySucceeded = derivedTasks.stream()
                    .anyMatch(t -> t.getStatus() == TaskStatus.done || t.getStatus() == TaskStatus.spike_completed);
            if (anySucceeded) {
                continue;
            }
            boolean allTerminalFailed = derivedTasks.stream().allMatch(t -> t.getStatus() == TaskStatus.failed);
            if (!allTerminalFailed) {
                continue;
            }
            String reasons = derivedTasks.stream()
                    .map(t -> t.getId() + ": " + (t.getJulesDispatchStatus() == null ? "(no reason recorded)" : t.getJulesDispatchStatus()))
                    .reduce("", (a, b) -> a.isEmpty() ? b : a + " | " + b);
            evidence.add(new Evidence("orphaned_wishlist_behind_failed_task", wishlist.getId().toString(),
                    "Wishlist " + wishlist.getId() + " (status=" + wishlist.getStatus() + ") has " + derivedTasks.size()
                            + " derived task(s), ALL terminally failed, none ever succeeded. This wishlist will "
                            + "permanently block the 100%-merged compiler-admission gate unless dismissed. Task failure reason(s): "
                            + reasons));
        }
        return evidence;
    }

    /**
     * 2026-08-08 (engineering invariant #14, generalized after a live incident showed the original trigger
     * was itself too narrow - test-forty-third: of 5 orphaned failures, only 2 ever got recovered, because
     * this method only ever looked for a task CURRENTLY stuck (`queued`/`blocked`) behind a dead dependency.
     * The other 3 were invisible to it forever, not just slow to be found: 2 had zero dependents anywhere
     * (leaf-level scope nobody's own dependsOn ever pointed at), 1 had a dependent that proceeded anyway
     * (an early-unblock or similar path let it go ahead without waiting) - "is anything visibly stuck right
     * now" was never the real question; "does this failed task have a live replacement" always was, and
     * {@link ClientDeliverableReadinessService#isDependencySatisfied} already answers exactly that,
     * correctly, and is the SAME check {@link #createTargetedRecoveryTask} re-verifies at execution time -
     * reusing it directly here means evidence-gathering can never again silently diverge from what actually
     * gets verified, and it finds every genuinely orphaned failure in the project up front, not only the
     * ones lucky enough to currently have something visibly stuck behind them. Auxiliary (DECISION-stage/
     * `complex`-Cynefin) failures are skipped - they were never counted toward feature-completion readiness
     * in the first place, so recovering one buys nothing.
     */
    private List<Evidence> gatherOrphanedDependencyChainEvidence(ProjectEntity project) {
        List<Evidence> evidence = new ArrayList<>();
        List<TaskEntity> projectTasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId());
        for (TaskEntity task : projectTasks) {
            if (task.getStatus() != TaskStatus.failed || task.getRole() == null || task.getFeatureId() == null) {
                continue;
            }
            if (readinessService.isAuxiliaryTask(task)) {
                continue;
            }
            if (readinessService.isDependencySatisfied(task)) {
                continue;
            }
            if (hasOpenRecoveryTaskFor(projectTasks, task.getId())) {
                continue;
            }
            evidence.add(new Evidence("orphaned_dependency_chain", task.getId().toString(),
                    "Task " + task.getId() + " (role=" + task.getRole().getTag() + ", title=\"" + task.getTitle()
                            + "\") is terminally failed with no live replacement anywhere in the project - "
                            + "regardless of whether anything is currently visibly stuck behind it, this is "
                            + "undelivered scope its epic needs before it can ever reach 100% complete. "
                            + "Original failure reason: " + (task.getJulesDispatchStatus() == null
                                    ? "(none recorded)" : task.getJulesDispatchStatus())));
        }
        return evidence;
    }

    private boolean hasOpenRecoveryTaskFor(List<TaskEntity> projectTasks, UUID failedTaskId) {
        return projectTasks.stream().anyMatch(t -> t.getStatus() != TaskStatus.failed
                && t.getPayload() != null
                && failedTaskId.toString().equals(t.getPayload().path(RECOVERS_FAILED_TASK_ID_KEY).asText("")));
    }

    private String buildAuditPrompt(ProjectEntity project, List<Evidence> evidence) {
        StringBuilder evidenceBlock = new StringBuilder();
        for (Evidence e : evidence) {
            evidenceBlock.append("- [").append(e.kind()).append("] subjectId=").append(e.subjectId())
                    .append(": ").append(e.description()).append("\n");
        }
        return """
                Project: %s

                GATHERED EVIDENCE (independently verified by the orchestrator's own code, not your own claim):
                %s

                Available tools (call ONLY these, with these exact argument shapes):
                1. "dismissOrphanedWishlist" {"wishlistId": "<uuid>", "reasoning": "..."} - marks a wishlist
                   dismissed. Only valid for evidence of kind orphaned_wishlist_behind_failed_task. The
                   orchestrator will re-verify the precondition (all derived tasks terminally failed, none
                   succeeded) before actually applying this - if it no longer holds, the call is a safe no-op.
                2. "createTargetedRecoveryTask" {"subjectId": "<uuid>", "reasoning": "..."} - only valid for
                   evidence of kind orphaned_dependency_chain. subjectId is the terminally failed task with no
                   replacement. Creates a real new task carrying the exact same role/feature/semantic identity
                   as the failed one, so the dependent task(s) waiting on it can recognize it as a genuine
                   replacement once it merges. The orchestrator re-verifies the failed task is still failed
                   and still has no replacement before actually creating anything - a safe no-op otherwise.
                   Use this whenever the failure looks like it can genuinely be retried (a transient blocker,
                   an environment issue, an implementation that needs another attempt) - not when the original
                   requirement itself may no longer be valid.
                3. "flagForHumanReview" {"subjectId": "<uuid>", "reasoning": "..."} - use this instead of #1/#2
                   whenever you are not fully confident the mechanical action is correct, or the evidence
                   suggests a real product/architectural judgment call (e.g. a task failed because the
                   underlying requirement might no longer be valid, not because it was truly superseded,
                   duplicate work, or a retryable failure).

                For EACH evidence item, decide independently: call exactly one tool. Do not invent a fourth
                tool. Do not act on anything not listed in the evidence above.

                Respond with STRICT JSON only, no prose, no markdown fences:
                {"decisions": [
                  {"tool": "dismissOrphanedWishlist", "args": {"wishlistId": "...", "reasoning": "..."}},
                  {"tool": "createTargetedRecoveryTask", "args": {"subjectId": "...", "reasoning": "..."}},
                  {"tool": "flagForHumanReview", "args": {"subjectId": "...", "reasoning": "..."}}
                ]}
                """.formatted(project.getName(), evidenceBlock);
    }

    private record AuditorDecision(String tool, String subjectId, String reasoning) {
    }

    private List<AuditorDecision> parseDecisions(String response) {
        if (response == null || response.isBlank()) {
            return List.of();
        }
        try {
            String cleaned = response.trim();
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start < 0 || end < 0 || end <= start) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(cleaned.substring(start, end + 1));
            JsonNode rawDecisions = root.path("decisions");
            if (!rawDecisions.isArray()) {
                return List.of();
            }
            List<AuditorDecision> result = new ArrayList<>();
            for (JsonNode d : rawDecisions) {
                String tool = d.path("tool").asText("");
                if (tool.isBlank()) {
                    continue;
                }
                JsonNode args = d.path("args");
                String subjectId = args.path("wishlistId").asText(args.path("subjectId").asText(""));
                String reasoning = args.path("reasoning").asText("(no reasoning given)");
                result.add(new AuditorDecision(tool, subjectId, reasoning));
            }
            return result;
        } catch (Exception e) {
            log.warn("OpsAuditorService: failed to parse Gemini response as structured decisions, treating as no action: {}", e.getMessage());
            return List.of();
        }
    }

    private void executeDecision(ProjectEntity project, AuditorDecision decision) {
        switch (decision.tool()) {
            case "dismissOrphanedWishlist" -> dismissOrphanedWishlist(project, decision);
            case "createTargetedRecoveryTask" -> self.createTargetedRecoveryTask(project, decision);
            case "flagForHumanReview" -> fileUnresolvedSubjectAsScope(project, decision);
            default -> log.warn("OpsAuditorService: Gemini requested unknown tool '{}' (subject {}) - ignored, not in the whitelist",
                    decision.tool(), decision.subjectId());
        }
    }

    /**
     * 2026-08-23 (F3). This tool used to write one warning line and stop. There is no human in this system,
     * so a subject routed here left the flow permanently: measured on test-fiftieth, task UI Slice
     * Fd6672c6 sat `failed` and flagged with nothing in the factory able to move it - an absorbing state
     * with no outgoing edge. An obligation addressed to an agent that does not exist is not an obligation.
     *
     * The auditor's judgement is not second-guessed; only the consequence changes. Its reasoning is carried
     * verbatim, because a finding filed without its witness makes the next worker re-derive what the system
     * already knows.
     */
    private void fileUnresolvedSubjectAsScope(ProjectEntity project, AuditorDecision decision) {
        String marker = "subject " + decision.subjectId();
        boolean alreadyFiled = wishlistRepository
                .findByProjectIdAndStatus(project.getId(), WishlistStatus.pending).stream()
                .anyMatch(existing -> existing.getSource() == WishlistSource.auditor_unresolved
                        && existing.getContent() != null && existing.getContent().contains(marker));
        if (alreadyFiled) {
            return;
        }

        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setProjectId(project.getId());
        wishlist.setSource(WishlistSource.auditor_unresolved);
        wishlist.setStatus(WishlistStatus.pending);
        wishlist.setLeanValue(LeanValue.essential);
        wishlist.setCynefinDomain("complicated");
        wishlist.setSourceRoleTag("BARCAN-TAG-00");
        wishlist.setContent("The operations auditor reached a subject it cannot resolve with the tools it "
                + "holds, and this factory has no human to hand it to.\n\n"
                + "The " + marker + " is stuck.\n\n"
                + "What the auditor found, in its own words - this is the evidence, not a summary of it:\n"
                + (decision.reasoning() == null || decision.reasoning().isBlank()
                        ? "(no reasoning recorded)" : decision.reasoning()) + "\n\n"
                + "Move that subject out of the state it is stuck in. Do not re-run the auditor and do not "
                + "restate the finding as new work: what blocks it is named above.");
        wishlist.setJtbd("When a subject is stuck in a state no automated tool can leave, I want the block "
                + "named above removed, so that work does not accumulate in a state with no exit.");
        wishlist.setAcceptanceCriteria("Given the subject named above, When this finding is delivered, Then "
                + "that subject is no longer in the state the auditor reported, and what changed is named.");
        wishlist.setDod("BARCAN-TAG-00: the named subject has left its stuck state, and the change that "
                + "moved it is identified in the pull request.");
        wishlistRepository.save(wishlist);

        log.warn("OpsAuditorService: unresolved subject {} of project {} filed as scope - {}",
                decision.subjectId(), project.getName(), decision.reasoning());
    }

    /**
     * Re-validates the precondition itself before acting (defense in depth) - the LLM's tool CHOICE is
     * trusted, its claim that a precondition holds is not. If the wishlist no longer matches the orphaned-
     * behind-failed-task shape (e.g. a fresh task was dispatched for it since the evidence was gathered),
     * this is a safe, logged no-op rather than a stale write.
     */
    private void dismissOrphanedWishlist(ProjectEntity project, AuditorDecision decision) {
        UUID wishlistId;
        try {
            wishlistId = UUID.fromString(decision.subjectId());
        } catch (Exception e) {
            log.warn("OpsAuditorService: dismissOrphanedWishlist called with invalid wishlistId '{}', ignored", decision.subjectId());
            return;
        }
        WishlistEntity wishlist = wishlistRepository.findById(wishlistId).orElse(null);
        if (wishlist == null || wishlist.getStatus() == WishlistStatus.dismissed) {
            return;
        }
        List<TaskEntity> derivedTasks = taskRepository.findBySourceWishlistIdIn(List.of(wishlistId));
        boolean stillOrphaned = !derivedTasks.isEmpty()
                && derivedTasks.stream().allMatch(t -> t.getStatus() == TaskStatus.failed);
        if (!stillOrphaned) {
            log.info("OpsAuditorService: skipped dismissOrphanedWishlist for {} - precondition no longer holds "
                    + "(re-verified at execution time, not just at evidence-gathering time)", wishlistId);
            return;
        }
        wishlist.setStatus(WishlistStatus.dismissed);
        wishlistRepository.save(wishlist);
        log.info("OpsAuditorService: dismissed orphaned wishlist {} for project {} - Gemini's reasoning: {}",
                wishlistId, project.getName(), decision.reasoning());
    }

    // Marker key stashed in a recovery task's own payload so hasOpenRecoveryTaskFor can find it again -
    // the identity link is "this task recovers that failed one", not anything the compiler or dispatch
    // pipeline needs to understand.
    private static final String RECOVERS_FAILED_TASK_ID_KEY = "recoversFailedTaskId";

    /**
     * Re-validates the precondition itself before acting (same defense-in-depth as dismissOrphanedWishlist):
     * the failed task must still be failed, still have no live replacement, and not already have an open
     * recovery task in flight. Copies role, featureId, and the ENTIRE original payload (including
     * ems_semantic_key) verbatim from the failed task - ClientDeliverableReadinessService.isDependencySatisfied
     * recognizes a replacement only by exact role+featureId+semantic-key match, so anything less than a
     * verbatim copy risks creating a task that merges but is never recognized as the fix.
     *
     * 2026-08-07 fix: wrapped in @Transactional + a project-row lock (same admission-mutex reasoning as
     * ProjectFlowService.dispatchFalsificationAudit) - the precondition check and the actual task creation
     * were not atomic, so two near-concurrent audit cycles on the same project could both pass the
     * precondition check before either committed and create two recovery tasks for the same failed root.
     * No network call happens in this method (the created task is just left `queued` for the normal
     * dispatch sweep to pick up later), so - unlike the two dispatch methods fixed alongside this one -
     * there's no separate "network call must happen outside the lock" concern here to split out.
     */
    @Transactional
    public void createTargetedRecoveryTask(ProjectEntity project, AuditorDecision decision) {
        UUID failedTaskId;
        try {
            failedTaskId = UUID.fromString(decision.subjectId());
        } catch (Exception e) {
            log.warn("OpsAuditorService: createTargetedRecoveryTask called with invalid subjectId '{}', ignored", decision.subjectId());
            return;
        }
        projectRepository.lockProjectForUpdate(project.getId());
        TaskEntity failedTask = taskRepository.findById(failedTaskId).orElse(null);
        if (failedTask == null || failedTask.getStatus() != TaskStatus.failed) {
            log.info("OpsAuditorService: skipped createTargetedRecoveryTask for {} - precondition no longer holds "
                    + "(task not found or no longer failed)", failedTaskId);
            return;
        }
        if (failedTask.getRole() == null || failedTask.getFeatureId() == null) {
            log.warn("OpsAuditorService: cannot create a recognizable recovery task for {} - original task is "
                    + "missing role or featureId, so no replacement could ever match it anyway", failedTaskId);
            return;
        }
        if (readinessService.isDependencySatisfied(failedTask)) {
            log.info("OpsAuditorService: skipped createTargetedRecoveryTask for {} - a live replacement already exists "
                    + "(re-verified at execution time, not just at evidence-gathering time)", failedTaskId);
            return;
        }
        List<TaskEntity> projectTasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId());
        if (hasOpenRecoveryTaskFor(projectTasks, failedTaskId)) {
            log.info("OpsAuditorService: skipped createTargetedRecoveryTask for {} - a recovery task is already in flight", failedTaskId);
            return;
        }

        TaskEntity recovery = new TaskEntity();
        recovery.setProject(project);
        recovery.setRole(failedTask.getRole());
        recovery.setFeatureId(failedTask.getFeatureId());
        recovery.setTitle("Recovery: " + failedTask.getTitle());
        recovery.setStatus(TaskStatus.queued);
        recovery.setDescription("This is a recovery attempt for a previous task that failed and was retired "
                + "without a replacement, leaving dependent work stuck.\n\n"
                + "Original task title: " + failedTask.getTitle() + "\n"
                + "Original failure reason: " + (failedTask.getJulesDispatchStatus() == null
                        ? "(none recorded)" : failedTask.getJulesDispatchStatus()) + "\n"
                + "Auditor's reasoning for retrying now: " + decision.reasoning() + "\n\n"
                + "Original task brief (re-attempt this goal, learning from the prior failure - do not repeat "
                + "whatever caused it):\n"
                + (failedTask.getDescription() == null ? "" : failedTask.getDescription()));

        ObjectNode payload = objectMapper.createObjectNode();
        if (failedTask.getPayload() instanceof ObjectNode originalPayload) {
            payload.setAll(originalPayload);
        }
        payload.put(RECOVERS_FAILED_TASK_ID_KEY, failedTaskId.toString());
        recovery.setPayload(payload);

        recovery.setAcceptanceCriteria(failedTask.getAcceptanceCriteria() != null
                ? failedTask.getAcceptanceCriteria()
                : "Given the goal of the task this replaces, When this attempt ends, Then that goal is met and the failure recorded against the original does not recur, shown by the artefact the original was to produce.");
        recovery = taskRepository.save(recovery);
        log.info("OpsAuditorService: created recovery task {} (role={}, feature={}) for failed task {} in project {} "
                        + "- Gemini's reasoning: {}",
                recovery.getId(), failedTask.getRole().getTag(), failedTask.getFeatureId(), failedTaskId,
                project.getName(), decision.reasoning());
    }
}
