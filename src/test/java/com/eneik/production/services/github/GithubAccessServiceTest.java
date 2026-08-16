package com.eneik.production.services.github;

import com.eneik.production.config.GithubConfig;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
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
    private SystemSettingsService settingsService;
    private JdbcTemplate jdbcTemplate;
    private ObjectMapper objectMapper;
    private GithubAccessService githubAccessService;

    @BeforeEach
    void setUp() {
        githubConfig = Mockito.mock(GithubConfig.class);
        settingsService = Mockito.mock(SystemSettingsService.class);
        jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        objectMapper = new ObjectMapper();
        githubAccessService = new GithubAccessService(githubConfig, settingsService, jdbcTemplate, objectMapper);
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
        when(settingsService.effectiveBoolean("github_enabled")).thenReturn(false);
        when(settingsService.effectiveValue("github_token")).thenReturn("");
        UUID projectId = UUID.randomUUID();

        GithubAccessService.GithubAccessResult result = githubAccessService.checkAccess(projectId);

        assertEquals(false, result.hasRepoAccess());
        assertEquals("skipped", result.ciStatus());
    }

    @Test
    void checkAccessUsesRepositoryPermissionsForPrAccessAndActionsRunsForCi() throws Exception {
        UUID projectId = UUID.randomUUID();
        String repoName = "factory-probe";
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handleGithubAccessProbe);
        server.start();

        try {
            when(settingsService.effectiveBoolean("github_enabled")).thenReturn(true);
            when(settingsService.effectiveValue("github_token")).thenReturn("test-token");
            when(githubConfig.getOrganization()).thenReturn("eneikcoworking-ctrl");
            when(githubConfig.getApiBaseUrl()).thenReturn("http://localhost:" + server.getAddress().getPort());
            when(githubConfig.getWebhookUrl()).thenReturn("https://example.test/github/webhook");
            when(jdbcTemplate.queryForObject("SELECT repository_name FROM projects WHERE id = ?", String.class, projectId))
                    .thenReturn(repoName);

            GithubAccessService.GithubAccessResult result = githubAccessService.checkAccess(projectId);

            assertEquals(true, result.hasRepoAccess());
            assertEquals(true, result.prPermissionsOk());
            assertEquals(true, result.branchProtectionOk());
            assertEquals(true, result.webhooksOk());
            assertEquals("passing", result.ciStatus());
        } finally {
            server.stop(0);
        }
    }

    private void handleGithubAccessProbe(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();
        if ("/repos/eneikcoworking-ctrl/factory-probe".equals(path)) {
            json(exchange, 200, """
                    {"permissions":{"push":true,"admin":false}}
                    """);
            return;
        }
        if ("/repos/eneikcoworking-ctrl/factory-probe/branches/main/protection".equals(path)) {
            json(exchange, 200, """
                    {"required_pull_request_reviews":{},"required_status_checks":{}}
                    """);
            return;
        }
        if ("/repos/eneikcoworking-ctrl/factory-probe/hooks".equals(path)) {
            json(exchange, 200, """
                    [{"active":true,"events":["push","pull_request"],"config":{"url":"https://example.test/github/webhook"}}]
                    """);
            return;
        }
        if ("/repos/eneikcoworking-ctrl/factory-probe/commits/main/check-runs".equals(path)) {
            json(exchange, 403, """
                    {"message":"Resource not accessible by personal access token"}
                    """);
            return;
        }
        if ("/repos/eneikcoworking-ctrl/factory-probe/actions/runs".equals(path)
                && "branch=main&per_page=10".equals(query)) {
            json(exchange, 200, """
                    {"workflow_runs":[{"status":"completed","conclusion":"success"}]}
                    """);
            return;
        }
        json(exchange, 404, "{}");
    }

    private void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes();
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
