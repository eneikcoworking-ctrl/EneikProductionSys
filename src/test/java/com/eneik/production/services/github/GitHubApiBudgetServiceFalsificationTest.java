package com.eneik.production.services.github;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpHeaders;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 4 empirical falsification test suite for {@link GitHubApiBudgetService}.
 * <p>
 * Philosophical anchors:
 * <ul>
 *   <li>SOL_KRIPKE_02_INDEXICAL_CONTEXT_LOCK [D006, Kripke]: Rigid designator token fingerprinting isolates budgets per context.</li>
 *   <li>DZHOZEF_RAZ_01_PROHIBITION_AS_CODE [D006, Raz]: Executable deontic prohibition blocks operations when rate limits are exhausted.</li>
 *   <li>LUDVIG_VITGENSHTEYN_01_ANTI_MIRROR_TELEMETRY [D013, Wittgenstein]: Physical empirical HTTP headers govern state over internal assumption.</li>
 * </ul>
 */
public class GitHubApiBudgetServiceFalsificationTest {

    private static HttpHeaders headers(int limit, int remaining, long resetAtEpoch) {
        return HttpHeaders.of(Map.of(
                "x-ratelimit-limit", List.of(String.valueOf(limit)),
                "x-ratelimit-remaining", List.of(String.valueOf(remaining)),
                "x-ratelimit-used", List.of(String.valueOf(limit - remaining)),
                "x-ratelimit-reset", List.of(String.valueOf(resetAtEpoch))
        ), (a, b) -> true);
    }

    @Test
    @DisplayName("Falsify Kripke: Token fingerprint computes irreversible SHA-256 without exposing raw secrets")
    void falsifyKripkeIndexicalLock_tokenFingerprintComputesIrreversibleSha256WithoutExposingSecrets() {
        String secretToken = "ghp_secretTokenWithSuperSensitiveEntropy9876543210";
        String bearerToken = "Bearer " + secretToken;
        String tokenPrefixed = "token " + secretToken;

        String fp1 = GitHubApiBudgetService.fingerprint(secretToken);
        String fp2 = GitHubApiBudgetService.fingerprint(bearerToken);
        String fp3 = GitHubApiBudgetService.fingerprint(tokenPrefixed);

        assertNotNull(fp1);
        assertEquals(16, fp1.length(), "Fingerprint must be truncated 16-hex characters");
        assertEquals(fp1, fp2, "Bearer prefix must normalize to identical fingerprint");
        assertEquals(fp1, fp3, "Token prefix must normalize to identical fingerprint");
        assertFalse(fp1.contains("ghp_"), "Fingerprint must not leak raw secret prefix");
        assertFalse(fp1.contains("secret"), "Fingerprint must not leak raw secret substring");

        assertEquals(GitHubApiBudgetService.DEFAULT_TOKEN_FINGERPRINT, GitHubApiBudgetService.fingerprint(null));
        assertEquals(GitHubApiBudgetService.DEFAULT_TOKEN_FINGERPRINT, GitHubApiBudgetService.fingerprint("   "));
    }

    @Test
    @DisplayName("Falsify Kripke: Budgets are strictly sharded and isolated by token fingerprint")
    void falsifyKripkeIndexicalLock_budgetsAreStrictlyIsolatedByTokenFingerprint() {
        GitHubApiBudgetService service = new GitHubApiBudgetService();
        String tokenAlpha = "ghp_alpha_token_value_aaaa11112222";
        String tokenBeta = "ghp_beta_token_value_bbbb33334444";

        long futureReset = Instant.now().plusSeconds(300).getEpochSecond();
        // Exhaust Alpha
        service.recordResponse(tokenAlpha, "GET /repos/o/r/pulls", 429, headers(5000, 0, futureReset), "rate limited");
        // Maintain Beta
        service.recordResponse(tokenBeta, "GET /repos/o/r/pulls", 200, headers(5000, 4500, futureReset), "[]");

        GitHubApiBudgetService.GuardDecision alphaGuard = service.guard(tokenAlpha, "GET /repos/o/r/pulls");
        GitHubApiBudgetService.GuardDecision betaGuard = service.guard(tokenBeta, "GET /repos/o/r/pulls");

        assertFalse(alphaGuard.allowed(), "Token Alpha must be blocked by exhausted rate limit");
        assertEquals("exhausted", service.snapshot(tokenAlpha).status());

        assertTrue(betaGuard.allowed(), "Token Beta must remain allowed in separate budget");
        assertEquals("available", service.snapshot(tokenBeta).status());
        assertEquals(4500, service.snapshot(tokenBeta).remaining());
    }

    @Test
    @DisplayName("Falsify Raz: Exhaustion triggers deontic prohibition blocking execution before socket invocation")
    void falsifyRazProhibition_rateLimitExhaustionProhibitsSubsequentCallsWithExecutableGuard() {
        GitHubApiBudgetService service = new GitHubApiBudgetService();
        String token = "ghp_deontic_test_token_99998888";

        // Initial state allows guarded calls
        assertTrue(service.guard(token, "GET /user").allowed());

        // External rate limit response arrives
        long resetEpoch = Instant.now().plusSeconds(60).getEpochSecond();
        service.recordResponse(token, "GET /user", 403, headers(5000, 0, resetEpoch), "API rate limit exceeded");

        GitHubApiBudgetService.GuardDecision guard = service.guard(token, "GET /user/repos");
        assertFalse(guard.allowed(), "Guard must strictly prohibit subsequent API attempts");
        assertEquals("exhausted", guard.status());
        assertThat(guard.reason()).contains("rate limit exhausted");
        assertThat(guard.reason()).contains("suppressing further calls until reset");
    }

    @Test
    @DisplayName("Falsify Raz: Expired cooldown period permits operations automatically")
    void falsifyRazProhibition_expiredCooldownAllowsSubsequentCallsAutomatically() {
        GitHubApiBudgetService service = new GitHubApiBudgetService();
        String token = "ghp_cooldown_expired_token_123";

        // Initial rate limit with future reset
        long shortReset = Instant.now().plusMillis(200).getEpochSecond();
        service.recordResponse(token, "GET /repos/o/r", 403, headers(5000, 0, shortReset), "rate limit exceeded");
        assertFalse(service.guard(token, "GET /repos/o/r").allowed(), "Must be blocked during cooldown");

        // Simulate subsequent successful empirical response resetting state
        service.recordResponse(token, "GET /repos/o/r", 200, headers(5000, 4999, Instant.now().plusSeconds(3600).getEpochSecond()), "{}");

        GitHubApiBudgetService.GuardDecision guard = service.guard(token, "GET /repos/o/r");
        assertTrue(guard.allowed(), "Guard must permit calls once empirical response confirms availability");
        assertEquals("available", guard.status());
    }

    @Test
    @DisplayName("Falsify Wittgenstein: Empirical GitHub headers override internal assumption and compute correct state")
    void falsifyWittgensteinAntiMirror_empiricalHeadersGovernBudgetAndRemainingTelemetry() {
        GitHubApiBudgetService service = new GitHubApiBudgetService();
        String token = "ghp_empirical_wittgenstein_token_456";

        assertEquals("unknown", service.snapshot(token).status());

        // First empirical receipt
        long resetAt = Instant.now().plusSeconds(1800).getEpochSecond();
        service.recordResponse(token, "GET /rate_limit", 200, headers(5000, 2500, resetAt), "{}");

        GitHubApiBudgetService.Snapshot snap = service.snapshot(token);
        assertEquals("available", snap.status());
        assertEquals(2500, snap.remaining());
        assertEquals(5000, snap.limit());
        assertEquals(Instant.ofEpochSecond(resetAt), snap.resetAt());
        assertNull(snap.cooldownUntil());
    }

    @Test
    @DisplayName("Falsify Wittgenstein: normalizeOperation bounds distinct endpoint keys by stripping query params")
    void falsifyWittgensteinAntiMirror_normalizeOperationAggregatesSpendWithoutQueryNoise() {
        GitHubApiBudgetService service = new GitHubApiBudgetService();
        String token = "ghp_query_noise_token_789";

        long reset = 1_500_000L;
        service.recordResponse(token, "GET /repos/eneik/test/issues?per_page=100&page=1", 200, headers(5000, 4999, reset), "[]");
        service.recordResponse(token, "GET /repos/eneik/test/issues?per_page=100&page=2", 200, headers(5000, 4998, reset), "[]");
        service.recordResponse(token, "GET /repos/eneik/test/issues?sort=created&direction=asc", 200, headers(5000, 4997, reset), "[]");

        Map<String, Long> spend = service.spendByOperation(token);
        assertThat(spend)
                .hasSize(1)
                .containsEntry("GET /repos/eneik/test/issues", 3L);
    }
}
