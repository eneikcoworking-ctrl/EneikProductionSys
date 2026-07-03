package com.eneik.production.services.github;

import com.eneik.production.config.GithubConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class GithubAccessServiceTest {

    private GithubConfig githubConfig;
    private JdbcTemplate jdbcTemplate;
    private ObjectMapper objectMapper;
    private GithubAccessService githubAccessService;

    @BeforeEach
    void setUp() {
        githubConfig = Mockito.mock(GithubConfig.class);
        jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        objectMapper = new ObjectMapper();
        githubAccessService = new GithubAccessService(githubConfig, jdbcTemplate, objectMapper);
    }

    @Test
    void testDpmoCalculation() {
        UUID projectId = UUID.randomUUID();
        Instant now = Instant.now();

        // 10 checks, each with 5 opportunities = 50 opportunities
        // 2 defects total
        GithubAccessService.GithubAccessResult res1 = new GithubAccessService.GithubAccessResult(
                UUID.randomUUID(), projectId, false, true, true, true, "passing", now, null); // 1 defect
        GithubAccessService.GithubAccessResult res2 = new GithubAccessService.GithubAccessResult(
                UUID.randomUUID(), projectId, true, true, true, true, "failing", now, null); // 1 defect

        // 8 perfect results
        GithubAccessService.GithubAccessResult perfect = new GithubAccessService.GithubAccessResult(
                UUID.randomUUID(), projectId, true, true, true, true, "passing", now, null);

        List<GithubAccessService.GithubAccessResult> results = List.of(res1, res2, perfect, perfect, perfect, perfect, perfect, perfect, perfect, perfect);

        when(jdbcTemplate.query(eq("SELECT * FROM github_access_status WHERE checked_at >= ?"),
                any(RowMapper.class), any(Instant.class))).thenReturn(results);

        GithubAccessService.GithubSixSigmaDto stats = githubAccessService.calculateDefectRate(now.minusSeconds(3600));

        assertEquals(10, stats.totalChecks());
        assertEquals(50, stats.totalOpportunities());
        assertEquals(2, stats.defects());
        // (2 / 50) * 1,000,000 = 0.04 * 1,000,000 = 40,000
        assertEquals(40000.0, stats.dpmo());
    }

    @Test
    void testCheckAccessDisabled() {
        when(githubConfig.isEnabled()).thenReturn(false);
        UUID projectId = UUID.randomUUID();

        GithubAccessService.GithubAccessResult result = githubAccessService.checkAccess(projectId);

        assertEquals(false, result.hasRepoAccess());
        assertEquals("skipped", result.ciStatus());
    }
}
