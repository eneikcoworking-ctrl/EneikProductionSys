package com.eneik.production.services.github;

import com.eneik.production.config.GithubConfig;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Stage 4 empirical falsification test suite for {@link GithubAccessService}.
 * <p>
 * Grounded in:
 * <ul>
 *   <li>DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX [D006, Raz]: Jural matrix of rights and duties for probe/read/audit callers.</li>
 *   <li>LUDVIG_VITGENSHTEYN_01_ANTI_MIRROR_TELEMETRY [D013, Wittgenstein]: Empirical measurement of 5 GitHub aspects instead of local beliefs.</li>
 *   <li>ELVIN_GOLDMAN_01_RELIABILITY_CHAIN [D010, Goldman]: Causal persistence and audit trail with checkedAt and rawError.</li>
 * </ul>
 */
public class GithubAccessServiceFalsificationTest {

    private GithubConfig githubConfig;
    private SystemSettingsService settingsService;
    private JdbcTemplate jdbcTemplate;
    private ObjectMapper objectMapper;
    private GitHubApiBudgetService budgetService;
    private GithubAccessService service;
    private HttpServer mockServer;
    private int mockPort;

    @BeforeEach
    void setUp() throws IOException {
        githubConfig = Mockito.mock(GithubConfig.class);
        settingsService = Mockito.mock(SystemSettingsService.class);
        jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        objectMapper = new ObjectMapper();
        budgetService = new GitHubApiBudgetService();
        service = new GithubAccessService(githubConfig, settingsService, jdbcTemplate, objectMapper, budgetService);

        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockPort = mockServer.getAddress().getPort();
        mockServer.start();
    }

    @AfterEach
    void tearDown() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    @Test
    @DisplayName("Falsify Raz: Null projectId strictly rejected by duty predicate before any network execution")
    void falsifyRazDutyMatrix_nullProjectIdThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> service.checkAccess(null),
                "checkAccess must reject null projectId as duty violation");
        assertThrows(IllegalArgumentException.class, () -> service.getLatestResult(null),
                "getLatestResult must reject null projectId as duty violation");
    }

    @Test
    @DisplayName("Falsify Raz: Disabled GitHub integration short-circuits to skipped status with zero network spend")
    void falsifyRazDutyMatrix_disabledIntegrationShortCircuitsToSkippedWithoutNetworkCalls() {
        UUID projectId = UUID.randomUUID();
        when(settingsService.effectiveBoolean("github_enabled")).thenReturn(false);
        when(settingsService.effectiveValue("github_token")).thenReturn("");

        GithubAccessService.GithubAccessResult result = service.checkAccess(projectId);

        assertNotNull(result);
        assertEquals(projectId, result.projectId());
        assertFalse(result.hasRepoAccess());
        assertFalse(result.branchProtectionOk());
        assertFalse(result.prPermissionsOk());
        assertFalse(result.webhooksOk());
        assertEquals("skipped", result.ciStatus());
        assertEquals("GitHub integration disabled or token missing", result.rawError());

        // Budget service must record zero spend
        assertThat(budgetService.spendByOperation()).isEmpty();
        // Result must be safely persisted
        verify(jdbcTemplate).update(
                startsWith("INSERT INTO github_access_status"),
                eq(result.id()), eq(projectId), eq(false), eq(false), eq(false), eq(false),
                eq("skipped"), any(Instant.class), eq("GitHub integration disabled or token missing")
        );
    }

    @Test
    @DisplayName("Falsify Wittgenstein: Empirical probes measure 5 distinct GitHub aspects into state")
    void falsifyWittgensteinAntiMirror_empiricalProbesAccuratelyReflectPhysicalGitHubResponses() {
        UUID projectId = UUID.randomUUID();
        String repoName = "wittgenstein-repo";

        mockServer.createContext("/repos/eneik/wittgenstein-repo", exchange -> {
            json(exchange, 200, "{\"permissions\":{\"push\":true,\"admin\":true}}");
        });
        mockServer.createContext("/repos/eneik/wittgenstein-repo/branches/main/protection", exchange -> {
            json(exchange, 200, "{\"required_pull_request_reviews\":{},\"required_status_checks\":{}}");
        });
        mockServer.createContext("/repos/eneik/wittgenstein-repo/hooks", exchange -> {
            json(exchange, 200, "[{\"active\":true,\"events\":[\"push\",\"pull_request\"],\"config\":{\"url\":\"https://eneik.ai/webhook\"}}]");
        });
        mockServer.createContext("/repos/eneik/wittgenstein-repo/commits/main/check-runs", exchange -> {
            json(exchange, 200, "{\"total_count\":1,\"check_runs\":[{\"status\":\"completed\",\"conclusion\":\"success\"}]}");
        });

        when(settingsService.effectiveBoolean("github_enabled")).thenReturn(true);
        when(settingsService.effectiveValue("github_token")).thenReturn("ghp_empiric_token_123");
        when(githubConfig.getOrganization()).thenReturn("eneik");
        when(githubConfig.getApiBaseUrl()).thenReturn("http://localhost:" + mockPort);
        when(githubConfig.getWebhookUrl()).thenReturn("https://eneik.ai/webhook");
        when(jdbcTemplate.queryForObject("SELECT repository_name FROM projects WHERE id = ?", String.class, projectId))
                .thenReturn(repoName);

        GithubAccessService.GithubAccessResult result = service.checkAccess(projectId);

        assertTrue(result.hasRepoAccess(), "Repo access must reflect HTTP 200");
        assertTrue(result.branchProtectionOk(), "Branch protection must reflect valid review & status check rules");
        assertTrue(result.prPermissionsOk(), "PR permissions must reflect push=true");
        assertTrue(result.webhooksOk(), "Webhooks must reflect matching target URL and active events");
        assertEquals("passing", result.ciStatus(), "CI status must reflect completed successful check-runs");
        assertNull(result.rawError());
    }

    @Test
    @DisplayName("Falsify Wittgenstein: Partial empirical failure accurately degrades corresponding aspects")
    void falsifyWittgensteinAntiMirror_partialFailureDegradesSpecificAspectsWithoutCrashing() {
        UUID projectId = UUID.randomUUID();
        String repoName = "failing-aspects-repo";

        // Repo exists (200), but protection returns 404, permissions has push=false, hooks has no matching url, CI is failing
        mockServer.createContext("/repos/eneik/failing-aspects-repo", exchange -> {
            json(exchange, 200, "{\"permissions\":{\"push\":false,\"admin\":false}}");
        });
        mockServer.createContext("/repos/eneik/failing-aspects-repo/branches/main/protection", exchange -> {
            json(exchange, 404, "{\"message\":\"Branch not protected\"}");
        });
        mockServer.createContext("/repos/eneik/failing-aspects-repo/hooks", exchange -> {
            json(exchange, 200, "[]");
        });
        mockServer.createContext("/repos/eneik/failing-aspects-repo/commits/main/check-runs", exchange -> {
            json(exchange, 200, "{\"total_count\":1,\"check_runs\":[{\"status\":\"completed\",\"conclusion\":\"failure\"}]}");
        });

        when(settingsService.effectiveBoolean("github_enabled")).thenReturn(true);
        when(settingsService.effectiveValue("github_token")).thenReturn("ghp_empiric_token_123");
        when(githubConfig.getOrganization()).thenReturn("eneik");
        when(githubConfig.getApiBaseUrl()).thenReturn("http://localhost:" + mockPort);
        when(githubConfig.getWebhookUrl()).thenReturn("https://eneik.ai/webhook");
        when(jdbcTemplate.queryForObject("SELECT repository_name FROM projects WHERE id = ?", String.class, projectId))
                .thenReturn(repoName);

        GithubAccessService.GithubAccessResult result = service.checkAccess(projectId);

        assertTrue(result.hasRepoAccess());
        assertFalse(result.branchProtectionOk(), "Protection 404 must degrade branchProtectionOk to false");
        assertFalse(result.prPermissionsOk(), "push=false must degrade prPermissionsOk to false");
        assertFalse(result.webhooksOk(), "Empty hooks must degrade webhooksOk to false");
        assertEquals("failing", result.ciStatus(), "Failing check run must degrade ciStatus to failing");
    }

    @Test
    @DisplayName("Falsify Goldman: Historical defect calculation attributes opportunities and defects rigorously")
    void falsifyGoldmanReliabilityChain_calculateDefectRateMaintainsExactLineageAndDpmo() {
        UUID projectId = UUID.randomUUID();
        Instant since = Instant.now().minusSeconds(7200);

        // 4 checks: total 20 opportunities
        // Check 1: perfect (0 defects)
        // Check 2: 1 defect (branchProtectionOk=false)
        // Check 3: 2 defects (prPermissionsOk=false, ciStatus=failing)
        // Check 4: 5 defects (all false, ciStatus=no_ci)
        // Total defects = 0 + 1 + 2 + 5 = 8 defects out of 20 opportunities -> DPMO = (8/20)*1,000,000 = 400,000
        GithubAccessService.GithubAccessResult c1 = new GithubAccessService.GithubAccessResult(
                UUID.randomUUID(), projectId, true, true, true, true, "passing", since.plusSeconds(10), null);
        GithubAccessService.GithubAccessResult c2 = new GithubAccessService.GithubAccessResult(
                UUID.randomUUID(), projectId, true, false, true, true, "passing", since.plusSeconds(20), null);
        GithubAccessService.GithubAccessResult c3 = new GithubAccessService.GithubAccessResult(
                UUID.randomUUID(), projectId, true, true, false, true, "failing", since.plusSeconds(30), null);
        GithubAccessService.GithubAccessResult c4 = new GithubAccessService.GithubAccessResult(
                UUID.randomUUID(), projectId, false, false, false, false, "no_ci", since.plusSeconds(40), "network down");

        when(jdbcTemplate.query(eq("SELECT * FROM github_access_status WHERE checked_at >= ?"),
                any(RowMapper.class), any(Instant.class))).thenReturn(List.of(c1, c2, c3, c4));

        GithubAccessService.GithubSixSigmaDto dto = service.calculateDefectRate(since);

        assertEquals(4, dto.totalChecks());
        assertEquals(20, dto.totalOpportunities());
        assertEquals(8, dto.defects());
        assertEquals(400000.0, dto.dpmo(), 0.001);
    }

    @Test
    @DisplayName("Falsify Goldman: getLatestResult returns cached record when present without invoking probes")
    void falsifyGoldmanReliabilityChain_getLatestResultReturnsCachedStatusWithoutTriggeringNetwork() {
        UUID projectId = UUID.randomUUID();
        Instant checkedAt = Instant.now().minusSeconds(120);
        GithubAccessService.GithubAccessResult persisted = new GithubAccessService.GithubAccessResult(
                UUID.randomUUID(), projectId, true, true, true, true, "passing", checkedAt, null
        );

        when(jdbcTemplate.queryForObject(
                eq("SELECT * FROM github_access_status WHERE project_id = ? ORDER BY checked_at DESC LIMIT 1"),
                any(RowMapper.class),
                eq(projectId)
        )).thenReturn(persisted);

        GithubAccessService.GithubAccessResult actual = service.getLatestResult(projectId);

        assertSame(persisted, actual);
        assertEquals(checkedAt, actual.checkedAt());
        assertThat(budgetService.spendByOperation()).isEmpty();
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
