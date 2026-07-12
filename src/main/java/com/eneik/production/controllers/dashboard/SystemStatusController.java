package com.eneik.production.controllers.dashboard;

import com.eneik.production.services.dashboard.SystemStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public SystemStatusController(SystemStatusService systemStatusService,
                                  org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.systemStatusService = systemStatusService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public Map<String, Object> getStatus(@RequestParam(required = false) UUID projectId) {
        return systemStatusService.getStatus(projectId);
    }

    @PostMapping("/sql")
    public Object runSql(@RequestBody String sql) {
        String trimmed = sql.trim().toUpperCase();
        if (trimmed.startsWith("SELECT") || trimmed.startsWith("SHOW") || trimmed.startsWith("DESC")) {
            return jdbcTemplate.queryForList(sql);
        } else {
            int rows = jdbcTemplate.update(sql);
            return Map.of("updated_rows", rows);
        }
    }
}
