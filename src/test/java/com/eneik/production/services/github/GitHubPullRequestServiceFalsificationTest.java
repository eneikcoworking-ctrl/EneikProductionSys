package com.eneik.production.services.github;

import com.eneik.production.config.GithubConfig;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.services.CodeChangeClassifier;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Popperian Falsification Test Harness for GitHubPullRequestService (Stage 4).
 *
 * <p>Philosopher Anchors:
 * 1. DZHOZEF_RAZ_01_PROHIBITION_AS_CODE [D006, Raz]:
 *    Deontic exclusionary reasons implemented as code: fail-closed refusal of all repository write endpoints
 *    (commitFile, upsertFile, resolveFileConflictWithMain, resolveProductCodeConflictWithMain)
 *    when CodeChangeClassifier is absent (null) or when the target path is a factory record file
 *    (isFactoryRecordFile == true). Mutation is refused with strictly ZERO network calls and zero settings reads.
 *
 * 2. LUDVIG_VITGENSHTEYN_01_ANTI_MIRROR_TELEMETRY [D013, Wittgenstein]:
 *    Anti-mirror telemetry: physical state of the world on GitHub takes precedence over local beliefs.
 *    For branch existence, HTTP 404 is the ONLY conclusive proof of absence (returns false); HTTP 200 is proof
 *    of existence (returns true); ambiguous network failures (500, 429) fail-safe (return true) to prevent
 *    premature destruction. For CI checks, locally claimed success is invalid: only real GitHub check-runs
 *    determine mergeability, failing closed if any check fails or is pending.
 *
 * 3. ELVIN_GOLDMAN_01_RELIABILITY_CHAIN [D010, Goldman]:
 *    Causal reliability and epistemic stability across ticks:
 *    PullRequestSnapshot is cached with a short TTL (Task 25 / Property 4) to eliminate intra-tick drift
 *    and prevent API budget exhaustion across multiple consumers within the same tick. Clock advancement
 *    past TTL re-queries physical reality; explicit cache invalidation (invalidateSnapshotCache) immediately
 *    purges stale projections upon verified mutations.
 */
class GitHubPullRequestServiceFalsificationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private GithubConfig githubConfig;
    private SystemSettingsService settingsService;
    private GitHubApiBudgetService budgetService;
    private HttpClient httpClient;
    private CodeChangeClassifier classifier;

    private ProjectEntity activeProject;

    @BeforeEach
    void setUp() {
        githubConfig = mock(GithubConfig.class);
        settingsService = mock(SystemSettingsService.class);
        budgetService = new GitHubApiBudgetService();
        httpClient = mock(HttpClient.class);
        classifier = mock(CodeChangeClassifier.class);

        activeProject = new ProjectEntity();
        activeProject.setId(UUID.randomUUID());
        activeProject.setStatus(ProjectStatus.active);
        activeProject.setRepositoryUrl("https://github.com/eneik-org/test-repo");
        when(githubConfig.getApiBaseUrl()).thenReturn("https://api.github.com");
        when(githubConfig.getOrganization()).thenReturn("eneik-org");
        when(settingsService.effectiveBoolean("github_enabled")).thenReturn(true);
        when(settingsService.effectiveValue("github_token")).thenReturn("ghp_valid_token");
        when(settingsService.effectiveValue("github_pr_snapshot_ttl_seconds")).thenReturn("20");
    }

    // =========================================================================
    // 1. DZHOZEF_RAZ_01_PROHIBITION_AS_CODE [D006, Raz]
    // =========================================================================

    @Test
    @DisplayName("Raz D006: Missing CodeChangeClassifier prohibits all write endpoints with strictly zero network calls")
    void falsifyRazProhibition_missingClassifierRefusesAllWriteOperationsWithZeroNetwork() throws Exception {
        GitHubPullRequestService unverifiedService = new GitHubPullRequestService(
                githubConfig, settingsService, objectMapper, budgetService, httpClient, null);

        // Every write endpoint must fail closed immediately
        assertFalse(unverifiedService.commitFile(activeProject, "src/App.java", "test".getBytes(), "msg"),
                "Falsification: commitFile must refuse write when classifier is missing");
        assertFalse(unverifiedService.upsertFile(activeProject, "src/App.java", "test".getBytes(), "msg"),
                "Falsification: upsertFile must refuse write when classifier is missing");
        assertFalse(unverifiedService.resolveFileConflictWithMain(activeProject, "feat", "src/App.java"),
                "Falsification: resolveFileConflictWithMain must refuse write when classifier is missing");
        assertFalse(unverifiedService.resolveProductCodeConflictWithMain(activeProject, "feat", "src/App.java"),
                "Falsification: resolveProductCodeConflictWithMain must refuse write when classifier is missing");

        // Deontic prohibition invariant: strictly zero network interaction
        verifyNoInteractions(httpClient);
        verify(settingsService, never()).effectiveBoolean(any());
        verify(settingsService, never()).effectiveValue(any());
    }

    @Test
    @DisplayName("Raz D006: Writing factory record file to repository is categorically prohibited with zero network calls")
    void falsifyRazProhibition_factoryRecordPathRefusedWithZeroNetwork() throws Exception {
        GitHubPullRequestService service = new GitHubPullRequestService(
                githubConfig, settingsService, objectMapper, budgetService, httpClient, classifier);

        String factoryRecordPath = ".eneik/task-plan.json";
        when(classifier.isFactoryRecordFile(factoryRecordPath)).thenReturn(true);

        assertFalse(service.commitFile(activeProject, factoryRecordPath, "{}".getBytes(), "msg"),
                "Falsification: commitFile must refuse factory record path");
        assertFalse(service.upsertFile(activeProject, factoryRecordPath, "{}".getBytes(), "msg"),
                "Falsification: upsertFile must refuse factory record path");
        assertFalse(service.resolveFileConflictWithMain(activeProject, "feat", factoryRecordPath),
                "Falsification: resolveFileConflictWithMain must refuse factory record path");
        assertFalse(service.resolveProductCodeConflictWithMain(activeProject, "feat", factoryRecordPath),
                "Falsification: resolveProductCodeConflictWithMain must refuse factory record path");

        // Factory record invariant: strictly zero HTTP calls made
        verifyNoInteractions(httpClient);
    }

    @SuppressWarnings("rawtypes")
    private HttpResponse mockResponse(int statusCode, String body) {
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body != null ? body : "");
        when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(java.util.Map.of(), (a, b) -> true));
        return response;
    }

    // =========================================================================
    // 2. LUDVIG_VITGENSHTEYN_01_ANTI_MIRROR_TELEMETRY [D013, Wittgenstein]
    // =========================================================================

    @Test
    @DisplayName("Wittgenstein D013: branchExists requires conclusive HTTP 404 to prove absence; transient errors fail safe")
    void falsifyWittgensteinAntiMirror_branchExistsRequiresConclusiveHttp404ToReportAbsence() throws Exception {
        GitHubPullRequestService service = new GitHubPullRequestService(
                githubConfig, settingsService, objectMapper, budgetService, httpClient, classifier);

        // Case A: HTTP 200 -> external branch exists
        doReturn(mockResponse(200, "{\"name\":\"feature/alive\"}")).when(httpClient).send(any(), any());
        assertTrue(service.branchExists(activeProject, "feature/alive"),
                "Falsification: HTTP 200 must confirm branch existence");

        // Case B: HTTP 404 -> conclusive physical proof that branch does not exist on GitHub
        doReturn(mockResponse(404, "{\"message\":\"Branch not found\"}")).when(httpClient).send(any(), any());
        assertFalse(service.branchExists(activeProject, "feature/deleted"),
                "Falsification: HTTP 404 must confirm branch absence");

        // Case C: HTTP 500 / 429 -> transient network failure is NOT proof of absence; must fail safe to prevent deletion
        doReturn(mockResponse(500, "Internal Server Error")).when(httpClient).send(any(), any());
        assertTrue(service.branchExists(activeProject, "feature/inconclusive"),
                "Falsification: ambiguous HTTP 500 must NOT be treated as branch absence");

        doReturn(mockResponse(429, "Rate limit exceeded")).when(httpClient).send(any(), any());
        assertTrue(service.branchExists(activeProject, "feature/rate-limited"),
                "Falsification: ambiguous HTTP 429 must NOT be treated as branch absence");
    }

    @Test
    @DisplayName("Wittgenstein D013: evaluateCheckRuns verifies physical CI check-runs and fails closed on incomplete or red checks")
    void falsifyWittgensteinAntiMirror_evaluateCheckRunsRequiresActualCompletedChecksAndFailsClosed() throws Exception {
        // Case A: Empty check-runs list -> cannot assume green; must report pending
        var emptyChecks = GitHubPullRequestService.evaluateCheckRuns(objectMapper.readTree("[]"));
        assertFalse(emptyChecks.successful(), "Falsification: empty check-runs must not be successful");
        assertEquals("pending", emptyChecks.status());

        // Case B: In-progress check -> must remain pending until physically complete
        var inProgressChecks = GitHubPullRequestService.evaluateCheckRuns(objectMapper.readTree("""
                [
                  {"name":"ci/test","status":"in_progress","conclusion":""},
                  {"name":"ci/lint","status":"completed","conclusion":"success"}
                ]
                """));
        assertFalse(inProgressChecks.successful(), "Falsification: in-progress checks must not be successful");
        assertEquals("pending", inProgressChecks.status());

        // Case C: Single red check among green checks -> fail closed
        var redChecks = GitHubPullRequestService.evaluateCheckRuns(objectMapper.readTree("""
                [
                  {"name":"ci/test","status":"completed","conclusion":"failure"},
                  {"name":"ci/lint","status":"completed","conclusion":"success"}
                ]
                """));
        assertFalse(redChecks.successful(), "Falsification: failed check must fail closed");
        assertEquals("failure", redChecks.status());
        assertTrue(redChecks.detail().contains("ci/test=failure"));

        // Case D: All checks completed and green/neutral/skipped -> success
        var greenChecks = GitHubPullRequestService.evaluateCheckRuns(objectMapper.readTree("""
                [
                  {"name":"ci/test","status":"completed","conclusion":"success"},
                  {"name":"ci/lint","status":"completed","conclusion":"neutral"},
                  {"name":"ci/audit","status":"completed","conclusion":"skipped"}
                ]
                """));
        assertTrue(greenChecks.successful(), "Falsification: fully completed green/neutral checks must succeed");
        assertEquals("success", greenChecks.status());
    }

    // =========================================================================
    // 3. ELVIN_GOLDMAN_01_RELIABILITY_CHAIN [D010, Goldman]
    // =========================================================================

    @Test
    @DisplayName("Goldman D010: PR snapshot is cached within TTL to prevent intra-tick drift; expires after TTL")
    void falsifyGoldmanReliabilityChain_pullRequestSnapshotIsCachedWithinTtlToPreventIntraTickDrift() throws Exception {
        GitHubPullRequestService service = new GitHubPullRequestService(
                githubConfig, settingsService, objectMapper, budgetService, httpClient, classifier);

        Instant t0 = Instant.parse("2026-10-07T03:00:00Z");
        service.setClock(Clock.fixed(t0, ZoneOffset.UTC));

        doReturn(mockResponse(200, "[]")).when(httpClient).send(any(), any());

        // First call at t0: hits GitHub API (once for open, once for closed = 2 HTTP calls)
        var snapshot1 = service.pullRequestSnapshot(activeProject);
        assertTrue(snapshot1.available());
        verify(httpClient, times(2)).send(any(), any());

        // Second call at t0 + 5s (well within 20s TTL): returns cached snapshot without hitting GitHub API
        service.setClock(Clock.fixed(t0.plusSeconds(5), ZoneOffset.UTC));
        var snapshot2 = service.pullRequestSnapshot(activeProject);
        assertSame(snapshot1, snapshot2, "Falsification: within TTL, snapshot must be byte-identical cached instance");
        verify(httpClient, times(2)).send(any(), any()); // Still 2 calls!

        // Third call at t0 + 25s (exceeds 20s TTL): cache expires, re-queries GitHub API
        service.setClock(Clock.fixed(t0.plusSeconds(25), ZoneOffset.UTC));
        var snapshot3 = service.pullRequestSnapshot(activeProject);
        assertTrue(snapshot3.available());
        verify(httpClient, times(4)).send(any(), any()); // 2 new calls = 4 total
    }

    @Test
    @DisplayName("Goldman D010: Explicit invalidation purges cached snapshot immediately to maintain causal freshness")
    void falsifyGoldmanReliabilityChain_explicitInvalidationForcesImmediateFreshQuery() throws Exception {
        GitHubPullRequestService service = new GitHubPullRequestService(
                githubConfig, settingsService, objectMapper, budgetService, httpClient, classifier);

        Instant t0 = Instant.parse("2026-10-07T03:00:00Z");
        service.setClock(Clock.fixed(t0, ZoneOffset.UTC));

        doReturn(mockResponse(200, "[]")).when(httpClient).send(any(), any());

        // First call: caches snapshot
        var snapshot1 = service.pullRequestSnapshot(activeProject);
        assertTrue(snapshot1.available());
        verify(httpClient, times(2)).send(any(), any());

        // Explicit invalidation (e.g. after merge or branch closeout)
        service.invalidateSnapshotCache(activeProject.getId());

        // Immediate subsequent call at t0 + 1s: must re-query GitHub API
        service.setClock(Clock.fixed(t0.plusSeconds(1), ZoneOffset.UTC));
        var snapshot2 = service.pullRequestSnapshot(activeProject);
        assertTrue(snapshot2.available());
        verify(httpClient, times(4)).send(any(), any());
    }
}
