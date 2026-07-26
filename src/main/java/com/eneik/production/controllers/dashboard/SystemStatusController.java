package com.eneik.production.controllers.dashboard;

import com.eneik.production.services.dashboard.SystemStatusService;
import com.eneik.production.services.settings.SystemSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/system-status")
public class SystemStatusController {

    private final SystemStatusService systemStatusService;
    private final SystemSettingsService systemSettingsService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final com.eneik.production.services.GeminiContextService geminiContextService;
    private final com.eneik.production.services.ProjectEventLogService projectEventLogService;

    public SystemStatusController(SystemStatusService systemStatusService,
                                  SystemSettingsService systemSettingsService,
                                  org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
                                  com.eneik.production.services.GeminiContextService geminiContextService,
                                  com.eneik.production.services.ProjectEventLogService projectEventLogService) {
        this.systemStatusService = systemStatusService;
        this.systemSettingsService = systemSettingsService;
        this.jdbcTemplate = jdbcTemplate;
        this.geminiContextService = geminiContextService;
        this.projectEventLogService = projectEventLogService;
    }

    // Durable, deploy-independent project history (2026-07-26 restoration) - for external agents/operator
    // forensic access, e.g. "what actually happened to this session's PR url before it got overwritten".
    // Always available (no debug-flag gate, unlike /sql) since this is now baseline read access, not a
    // raw-JDBC backdoor. ?since=<ISO instant> returns everything chronologically from that point instead
    // of the default most-recent-first bounded window.
    @GetMapping("/project-log/{projectId}")
    public List<Map<String, Object>> projectLog(@org.springframework.web.bind.annotation.PathVariable UUID projectId,
                                                 @RequestParam(required = false) String since,
                                                 @RequestParam(required = false, defaultValue = "500") int limit) {
        List<com.eneik.production.models.persistence.ProjectEventLogEntity> entries = since != null && !since.isBlank()
                ? projectEventLogService.since(projectId, java.time.Instant.parse(since))
                : projectEventLogService.recent(projectId, limit);
        return entries.stream()
                .map(e -> Map.<String, Object>of(
                        "createdAt", e.getCreatedAt().toString(),
                        "level", e.getLevel(),
                        "logger", e.getLogger(),
                        "message", e.getMessage()))
                .toList();
    }

    // Manual trigger for GeminiContextService's standing-knowledge re-index (2026-07-25) - lets the
    // operator refresh the RAG corpus (OBSERVER_LOG, charters, Claude's own operator notes) out-of-cycle
    // instead of waiting for the daily cron. Honestly no-ops (still HTTP 202) if the feature flag is off or
    // the repo root isn't mounted - it never silently pretends to have indexed anything.
    @PostMapping("/gemini-context/reindex")
    public Map<String, Object> reindexGeminiContext() {
        geminiContextService.reindexStandingKnowledge();
        return Map.of("message", "Re-index triggered; check logs for per-source chunk counts "
                + "(it honestly no-ops if gemini_context_learning_enabled is off or the repo root isn't mounted)");
    }

    @GetMapping
    public Map<String, Object> getStatus(@RequestParam(required = false) UUID projectId) {
        return systemStatusService.getStatus(projectId);
    }

    // Raw JDBC executor with no auth of its own - gated behind an explicit, off-by-default feature flag
    // (debug_sql_endpoint_enabled) so it can never be live in an environment nobody deliberately opted
    // into. See docs/reports/POST_MORTEM_test-twenty-eighth_2026-07-19.md §5 item 6.
    @PostMapping("/sql")
    public Object runSql(@RequestBody String sql) {
        if (!systemSettingsService.effectiveBoolean("debug_sql_endpoint_enabled")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Debug SQL endpoint is disabled. Enable it explicitly via the debug_sql_endpoint_enabled setting "
                            + "(or DEBUG_SQL_ENDPOINT_ENABLED env var) for this environment before use.");
        }
        String trimmed = sql.trim().toUpperCase();
        if (trimmed.startsWith("SELECT") || trimmed.startsWith("SHOW") || trimmed.startsWith("DESC")) {
            return jdbcTemplate.queryForList(sql);
        } else {
            int rows = jdbcTemplate.update(sql);
            return Map.of("updated_rows", rows);
        }
    }
}
