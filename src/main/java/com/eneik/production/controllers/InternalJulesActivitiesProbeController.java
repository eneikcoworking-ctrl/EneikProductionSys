package com.eneik.production.controllers;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.services.jules.JulesApiClient;
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

    public InternalJulesActivitiesProbeController(JulesApiClient julesApiClient, SystemSettingsService settingsService,
                                                    JulesSessionRepository julesSessionRepository, AccountRepository accountRepository) {
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
}
