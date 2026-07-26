package com.eneik.production.services;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.logging.LogScope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Embedded ops auditor (2026-07-25, operator directive: "мало системных решений... встроить такого же
 * аудитора как ты прямо внутрь бекенда... который будет проталкивать автоматизацию и фиксить мелкие баги").
 * This is an automated version of exactly what the human orchestrator (this session) spent the last several
 * hours doing by hand: query real system state, diagnose what's actually blocking progress, apply a narrow,
 * safe, evidence-based fix.
 *
 * Design principle, explicitly requested wide-but-bounded: Gemini decides WHICH real, gathered evidence is
 * worth acting on and WHEN (no hardcoded "if X then always Y" rule forcing every case through one path) -
 * but it can only ever call a small, curated, pre-vetted TOOL SET (dismissOrphanedWishlist,
 * markTaskFailedWithEvidence, flagForHumanReview), each of which re-validates its own precondition in code
 * before doing anything (defense in depth - the LLM's tool choice is trusted, but never its claim that a
 * precondition holds). It never gets raw SQL, git, or code-editing access. This mirrors the testimony-vs-
 * evidence principle already built into this codebase: the auditor only ever acts on independently-verified
 * facts gathered by this class itself, never on the LLM's own unverified assertions.
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpsAuditorService(ProjectRepository projectRepository,
                              WishlistRepository wishlistRepository,
                              TaskRepository taskRepository,
                              com.eneik.production.services.settings.SystemSettingsService settingsService,
                              MLPredictionServiceClient mlPredictionServiceClient,
                              GeminiContextService geminiContextService) {
        this.projectRepository = projectRepository;
        this.wishlistRepository = wishlistRepository;
        this.taskRepository = taskRepository;
        this.settingsService = settingsService::effectiveBoolean;
        this.mlPredictionServiceClient = mlPredictionServiceClient;
        this.geminiContextService = geminiContextService;
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
        List<Evidence> evidence = gatherOrphanedWishlistEvidence(project);
        if (evidence.isEmpty()) {
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
                2. "flagForHumanReview" {"subjectId": "<uuid>", "reasoning": "..."} - use this instead of #1
                   whenever you are not fully confident dismissing is correct, or the evidence suggests a real
                   product/architectural judgment call rather than mechanical cleanup (e.g. a task failed
                   because the underlying requirement might still be genuinely needed, not because it was
                   truly superseded or duplicate work).

                For EACH evidence item, decide independently: call exactly one tool. Do not invent a third
                tool. Do not act on anything not listed in the evidence above.

                Respond with STRICT JSON only, no prose, no markdown fences:
                {"decisions": [
                  {"tool": "dismissOrphanedWishlist", "args": {"wishlistId": "...", "reasoning": "..."}},
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
            case "flagForHumanReview" -> log.warn("OpsAuditorService: FLAGGED FOR HUMAN REVIEW - project {} subject {} - {}",
                    project.getName(), decision.subjectId(), decision.reasoning());
            default -> log.warn("OpsAuditorService: Gemini requested unknown tool '{}' (subject {}) - ignored, not in the whitelist",
                    decision.tool(), decision.subjectId());
        }
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
}
