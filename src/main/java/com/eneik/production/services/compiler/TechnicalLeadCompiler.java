package com.eneik.production.services.compiler;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TechnicalLeadCompiler {
    private static final Logger log = LoggerFactory.getLogger(TechnicalLeadCompiler.class);
    private final JdbcTemplate jdbcTemplate;

    public TechnicalLeadCompiler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void stopGeneration(UUID projectId) {
        log.info("Stopping generation for project: {}", projectId);
        Instant now = Instant.now();
        int updated = jdbcTemplate.update(
            "UPDATE project_generation_state SET is_generation_stopped = true, stopped_at = ? WHERE project_id = ?",
            now, projectId
        );
        if (updated == 0) {
            jdbcTemplate.update(
                "INSERT INTO project_generation_state (project_id, is_generation_stopped, stopped_at) VALUES (?, true, ?)",
                projectId, now
            );
        }
    }
}
