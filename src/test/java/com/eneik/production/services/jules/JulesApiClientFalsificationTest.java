package com.eneik.production.services.jules;

import com.eneik.production.services.accounts.AccountHealthService.DispatchOutcome;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Popperian Falsification Test Harness for JulesApiClient (Stage 4).
 *
 * <p>Philosopher Anchors:
 * 1. NUEL_BELNAP_03_TRUTH_STATUS_TABLE [D012, Belnap]:
 *    Multi-valued truth lattice for SourceAvailability and CreateSessionResult classification:
 *    Distinguishes CONCURRENT_CAPACITY_EXHAUSTED, DAILY_LIMIT, REQUEST_REJECTED,
 *    PRECONDITION_BLOCKED, PRECONDITION_UNSPECIFIED, and UNCLASSIFIED without category conflation.
 *
 * 2. DZHOZEF_RAZ_01_PROHIBITION_AS_CODE [D006, Raz]:
 *    Deontic exclusionary reasons implemented as code: when integration is disabled,
 *    api_key is absent, or repoUrl is missing, dispatch is prohibited immediately with ZERO network calls.
 *
 * 3. ELVIN_GOLDMAN_01_RELIABILITY_CHAIN [D010, Goldman]:
 *    Causal reliability: refusal details name what was sent (promptLength, source, startingBranch).
 *    checkSessionRaw preserves physical evidence (404 status code) of external session absence.
 */
class JulesApiClientFalsificationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // =========================================================================
    // 1. NUEL_BELNAP_03_TRUTH_STATUS_TABLE [D012, Belnap]
    // =========================================================================

    @Test
    @DisplayName("Belnap D012: classifyOutcome partitions external refusals into mutually exclusive truth lattice")
    void falsifyBelnapTruthStatusTable_classifyOutcomeLatticePartitioning() {
        // 1. Concurrent capacity exhaustion
        var concurrentErr = new JulesApiClient.CreateSessionResult(null, 400, "{\"error\":{\"message\":\"maximum concurrent session limit reached\"}}");
        assertEquals(DispatchOutcome.CONCURRENT_CAPACITY_EXHAUSTED, concurrentErr.classifyOutcome());

        // 2. Daily limit / quota
        var dailyErr = new JulesApiClient.CreateSessionResult(null, 429, "{\"error\":{\"message\":\"quota exceeded for today\"}}");
        assertEquals(DispatchOutcome.DAILY_LIMIT, dailyErr.classifyOutcome());

        // 3. Request rejected (factory's invalid argument)
        var badRequestErr = new JulesApiClient.CreateSessionResult(null, 400, "{\"error\":{\"status\":\"INVALID_ARGUMENT\",\"message\":\"prompt too long\"}}");
        assertEquals(DispatchOutcome.REQUEST_REJECTED, badRequestErr.classifyOutcome());

        // 4. Precondition / authorization blocked (account side fault)
        var authErr = new JulesApiClient.CreateSessionResult(null, 401, "{\"error\":\"unauthorized: invalid credentials\"}");
        assertEquals(DispatchOutcome.PRECONDITION_BLOCKED, authErr.classifyOutcome());

        var repoNotReadyErr = new JulesApiClient.CreateSessionResult(null, 400, "{\"error\":{\"message\":\"Repository access is not ready\"}}");
        assertEquals(DispatchOutcome.PRECONDITION_BLOCKED, repoNotReadyErr.classifyOutcome());

        // 5. Precondition unspecified (bare precondition check failed without cause)
        var unspecErr = new JulesApiClient.CreateSessionResult(null, 400, "{\"error\":{\"status\":\"FAILED_PRECONDITION\",\"message\":\"Precondition check failed.\"}}");
        assertEquals(DispatchOutcome.PRECONDITION_UNSPECIFIED, unspecErr.classifyOutcome());

        // 6. Unclassified (500 internal server error or gateway)
        var serverErr = new JulesApiClient.CreateSessionResult(null, 502, "{\"error\":\"bad gateway\"}");
        assertEquals(DispatchOutcome.UNCLASSIFIED, serverErr.classifyOutcome());
    }

    @Test
    @DisplayName("Belnap D012: Request rejected does not capture authorization-flavored 400 errors")
    void falsifyBelnapTruthStatusTable_requestRejectedDoesNotSwallowPermissionDenied() {
        // 400 containing INVALID_ARGUMENT but also "permission_denied" is about ACCOUNT permissions, not factory prompt
        var permDenied400 = new JulesApiClient.CreateSessionResult(null, 400, "{\"error\":{\"status\":\"INVALID_ARGUMENT\",\"message\":\"permission_denied on project\"}}");
        assertEquals(DispatchOutcome.PRECONDITION_BLOCKED, permDenied400.classifyOutcome(),
                "Falsification: permission denied was falsely classified as factory request defect!");
    }

    // =========================================================================
    // 2. DZHOZEF_RAZ_01_PROHIBITION_AS_CODE [D006, Raz]
    // =========================================================================

    @Test
    @DisplayName("Raz D006: Deontic prohibition stops dispatch with strictly ZERO network calls when disabled")
    void falsifyRazProhibition_strictlyZeroNetworkCallsWhenDisabledOrMissingKey() throws Exception {
        AtomicInteger serverHits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            serverHits.incrementAndGet();
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.start();

        try {
            SystemSettingsService settings = mock(SystemSettingsService.class);
            // Case A: jules_enabled = false
            when(settings.effectiveBoolean("jules_enabled")).thenReturn(false);
            JulesApiClient client = new JulesApiClient(objectMapper, "http://localhost:" + server.getAddress().getPort(), settings);

            var resultDisabled = client.createSessionDetailed("https://github.com/org/repo", "task", "ctx", "key");
            assertThat(resultDisabled.sessionName()).isEqualTo("skipped");
            assertThat(resultDisabled.errorBody()).isEqualTo("jules_disabled");
            assertThat(serverHits).hasValue(0);

            boolean msgSent = client.sendMessage("sessions/123", "hello", "key");
            assertThat(msgSent).isFalse();
            assertThat(serverHits).hasValue(0);

            var deleteResult = client.deleteSession("sessions/123", "key");
            assertThat(deleteResult.success()).isFalse();
            assertThat(serverHits).hasValue(0);

            // Case B: enabled = true but missing apiKey
            when(settings.effectiveBoolean("jules_enabled")).thenReturn(true);
            var resultMissingKey = client.createSessionDetailed("https://github.com/org/repo", "task", "ctx", "");
            assertThat(resultMissingKey.sessionName()).isEqualTo("skipped");
            assertThat(resultMissingKey.errorBody()).isEqualTo("missing_api_key");
            assertThat(serverHits).hasValue(0);

            // Case C: enabled = true, valid key, but empty repoUrl
            var resultMissingRepo = client.createSessionDetailed("", "task", "ctx", "valid-key");
            assertThat(resultMissingRepo.statusCode()).isEqualTo(400);
            assertThat(resultMissingRepo.errorBody()).contains("repo_url_missing");
            assertThat(serverHits).hasValue(0);
        } finally {
            server.stop(0);
        }
    }

    // =========================================================================
    // 3. ELVIN_GOLDMAN_01_RELIABILITY_CHAIN [D010, Goldman]
    // =========================================================================

    @Test
    @DisplayName("Goldman D010: Failure records ground of denial naming prompt length, source, and starting branch")
    void falsifyGoldmanReliabilityChain_failureContextNamesEmpiricalGrounds() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/sources", exchange -> {
            byte[] body = """
                    {"sources":[{"name":"sources/github/owner/service"}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/sessions", exchange -> {
            byte[] body = "{\"error\":{\"code\":429,\"message\":\"Resource has been exhausted (e.g. check quota).\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            SystemSettingsService settings = mock(SystemSettingsService.class);
            when(settings.effectiveBoolean("jules_enabled")).thenReturn(true);
            JulesApiClient client = new JulesApiClient(objectMapper, "http://localhost:" + server.getAddress().getPort(), settings);

            String task = "Implement feature X";
            String ctx = "Deontic Rule 1";
            int expectedLength = (task + "\n\nContext:\n" + ctx).length();

            var result = client.createSessionDetailed(
                    "https://github.com/owner/service.git",
                    task,
                    ctx,
                    "valid-api-key",
                    "Feature X",
                    "feature/x-branch"
            );

            assertThat(result.sessionName()).isNull();
            assertThat(result.statusCode()).isEqualTo(429);
            assertThat(result.promptLength()).isEqualTo(expectedLength);
            assertThat(result.source()).isEqualTo("sources/github/owner/service");
            assertThat(result.startingBranch()).isEqualTo("feature/x-branch");
            assertThat(result.dailyLimitOrQuota()).isTrue();
            assertThat(result.errorBody()).contains("promptLength=" + expectedLength);
            assertThat(result.errorBody()).contains("source=sources/github/owner/service");
            assertThat(result.errorBody()).contains("startingBranch=feature/x-branch");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Goldman D010: checkSessionRaw returns true HTTP 404 as causal evidence of session absence")
    void falsifyGoldmanReliabilityChain_checkSessionRawPreservesHttp404Evidence() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/sessions/123", exchange -> {
            byte[] body = "{\"error\":{\"code\":404,\"message\":\"Session not found\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            SystemSettingsService settings = mock(SystemSettingsService.class);
            JulesApiClient client = new JulesApiClient(objectMapper, "http://localhost:" + server.getAddress().getPort(), settings);

            JulesApiClient.RawSessionCheckResult result = client.checkSessionRaw("sessions/123", "test-key");

            assertThat(result.statusCode()).isEqualTo(404);
            assertThat(result.body()).contains("Session not found");
        } finally {
            server.stop(0);
        }
    }
}
