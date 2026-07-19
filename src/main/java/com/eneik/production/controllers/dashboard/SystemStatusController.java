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

    public SystemStatusController(SystemStatusService systemStatusService,
                                  SystemSettingsService systemSettingsService,
                                  org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.systemStatusService = systemStatusService;
        this.systemSettingsService = systemSettingsService;
        this.jdbcTemplate = jdbcTemplate;
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
