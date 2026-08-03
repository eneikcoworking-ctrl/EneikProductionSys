package com.eneik.production.controllers;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.services.jules.JulesApiClient;
import com.eneik.production.services.orchestration.BranchGarbageCollectorService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Temporary, read-only diagnostic (2026-08-03) for the blind-cycle-overflow incident on test-forty-first:
 * confirms real Jules activities.list pagination behavior (item ordering, nextPageToken) against a live
 * session before redesigning JulesApiClient/JulesDispatchService's activity scanning around it. Deliberately
 * strips artifact content (git diffs, base64 screenshots, bash output) from the response - those are exactly
 * what makes a single page large, and this endpoint only needs to see shape/ordering, not payload.
 * Restricted to localhost in production via filter/security, same as the other /internal controllers.
 * Remove once the real pagination redesign lands.
 */
@RestController
@RequestMapping("/internal/jules-activities-probe")
public class InternalJulesActivitiesProbeController {

    private final JulesApiClient julesApiClient;
    private final SystemSettingsService settingsService;
    private final JulesSessionRepository julesSessionRepository;
    private final AccountRepository accountRepository;
    private final BranchGarbageCollectorService branchGarbageCollectorService;
    private final ProjectRepository projectRepository;

    public InternalJulesActivitiesProbeController(JulesApiClient julesApiClient, SystemSettingsService settingsService,
                                                    JulesSessionRepository julesSessionRepository, AccountRepository accountRepository,
                                                    BranchGarbageCollectorService branchGarbageCollectorService,
                                                    ProjectRepository projectRepository) {
        this.branchGarbageCollectorService = branchGarbageCollectorService;
        this.projectRepository = projectRepository;
        this.julesApiClient = julesApiClient;
        this.settingsService = settingsService;
        this.julesSessionRepository = julesSessionRepository;
        this.accountRepository = accountRepository;
    }

    @GetMapping
    public ResponseEntity<?> probe(@RequestParam String sessionId,
                                    @RequestParam(defaultValue = "5") int pageSize,
                                    @RequestParam(required = false) String pageToken) {
        // Each session is dispatched under a specific account's own key (JulesDispatchService.
        // apiKeyForSession) - the generic "jules_api_key" setting is a different, unrelated default and
        // returns 403 for a session that belongs to another account (confirmed live on this exact probe).
        Optional<JulesSessionEntity> sessionOpt = julesSessionRepository.findAll().stream()
                .filter(s -> sessionId.equals(s.getExternalSessionId()))
                .findFirst();
        String apiKey = sessionOpt
                .map(JulesSessionEntity::getAccountId)
                .flatMap(accountRepository::findById)
                .map(AccountEntity::getApiKey)
                .filter(key -> key != null && !key.isBlank())
                .orElseGet(() -> settingsService.effectiveValue("jules_api_key"));
        if (apiKey == null || apiKey.isBlank()) {
            return ResponseEntity.status(500).body(Map.of("error", "no resolvable api key for this session or default"));
        }

        JsonNode root = julesApiClient.getSessionActivities(sessionId, apiKey, pageSize, pageToken);
        if (root == null) {
            return ResponseEntity.status(502).body(Map.of("error", "null response from Jules"));
        }
        if (root.path("activitiesOverflow").asBoolean(false)) {
            return ResponseEntity.ok(Map.of("overflow", true, "maxBytes", root.path("maxBytes").asInt()));
        }

        List<Map<String, Object>> summary = new ArrayList<>();
        for (JsonNode a : root.path("activities")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", a.path("name").asText());
            m.put("createTime", a.path("createTime").asText());
            m.put("originator", a.path("originator").asText());
            String type = "unknown";
            for (String field : List.of("agentMessaged", "userMessaged", "planGenerated", "planApproved",
                    "progressUpdated", "sessionCompleted", "sessionFailed")) {
                if (a.has(field)) {
                    type = field;
                    break;
                }
            }
            m.put("type", type);
            m.put("artifactCount", a.path("artifacts").isArray() ? a.path("artifacts").size() : 0);
            summary.add(m);
        }

        return ResponseEntity.ok(Map.of(
                "count", summary.size(),
                "nextPageToken", root.path("nextPageToken").asText(""),
                "activities", summary
        ));
    }

    /**
     * 2026-08-03: precise lookup used to confirm/refute whether a given task owns a given GitHub PR - the
     * real jules_sessions table, not the possibly-stale/null TaskEntity.julesSessionName convenience field
     * (confirmed live: null on at least one real "done" task with session history). Read-only, no secrets.
     */
    @GetMapping("/sessions-for-task")
    public ResponseEntity<?> sessionsForTask(@RequestParam String taskId) {
        List<JulesSessionEntity> sessions = julesSessionRepository.findByTaskId(UUID.fromString(taskId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (JulesSessionEntity s : sessions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("externalSessionId", s.getExternalSessionId());
            m.put("status", s.getStatus());
            m.put("prUrl", s.getPrUrl());
            m.put("createdAt", s.getCreatedAt());
            m.put("lastProgressAt", s.getLastProgressAt());
            result.add(m);
        }
        return ResponseEntity.ok(Map.of("taskId", taskId, "sessionCount", result.size(), "sessions", result));
    }

    /**
     * 2026-08-03: the reverse direction of sessions-for-task - given a raw session token (the numeric part
     * of a Jules session name, as embedded in a GitHub branch name, e.g. "jules-5354685196398021436-xxx"),
     * find which task (if any) actually owns it, via the exact same substring match
     * GitHubPullRequestService.matchesSessionToken/BranchGarbageCollectorService use. Read-only, no secrets.
     */
    @GetMapping("/session-by-token")
    public ResponseEntity<?> sessionByToken(@RequestParam String token) {
        List<Map<String, Object>> matches = new ArrayList<>();
        for (JulesSessionEntity s : julesSessionRepository.findAll()) {
            String externalId = s.getExternalSessionId();
            if (externalId != null && externalId.contains(token)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("sessionId", s.getId());
                m.put("taskId", s.getTaskId());
                m.put("externalSessionId", externalId);
                m.put("status", s.getStatus());
                m.put("prUrl", s.getPrUrl());
                m.put("createdAt", s.getCreatedAt());
                matches.add(m);
            }
        }
        return ResponseEntity.ok(Map.of("token", token, "matchCount", matches.size(), "matches", matches));
    }

    /**
     * 2026-08-03: read-only exposure of BranchGarbageCollectorService.findOrphanedPrCandidates - lets the
     * real detector be verified live against a project (e.g. test-forty-first/PR#38) without triggering the
     * actual mutating resolveOrphanedPr action.
     */
    @GetMapping("/orphaned-prs")
    public ResponseEntity<?> orphanedPrs(@RequestParam String projectId) {
        ProjectEntity project = projectRepository.findById(UUID.fromString(projectId)).orElse(null);
        if (project == null) {
            return ResponseEntity.status(404).body(Map.of("error", "project not found"));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (var candidate : branchGarbageCollectorService.findOrphanedPrCandidates(project)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("taskId", candidate.taskId());
            m.put("pullNumber", candidate.pullNumber());
            m.put("pullUrl", candidate.pullUrl());
            m.put("headRef", candidate.headRef());
            m.put("sessionStatus", candidate.sessionStatus());
            result.add(m);
        }
        return ResponseEntity.ok(Map.of("projectId", projectId, "candidateCount", result.size(), "candidates", result));
    }
}
