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
 * Unit test suite enforcing Law 13 (Capacity / Token-Sharded Budget) from
 * ENGINEERING_PHILOSOPHY_ACTION_PLAN.md:
 *
 * <p>Grounded in Saul Kripke's Indexical Context Lock (SOL_KRIPKE_02_INDEXICAL_CONTEXT_LOCK):
 * "Never let terms like current, owner, user, latest or active float without an explicit context object.
 * Proof obligation: point to the context source and tests that prevent cross-user or stale-context leakage."
 */
public class GitHubApiBudgetLaw13Test {

    private static HttpHeaders headers(int remaining, int limit, long resetAtEpoch) {
        return HttpHeaders.of(Map.of(
                "x-ratelimit-limit", List.of(String.valueOf(limit)),
                "x-ratelimit-remaining", List.of(String.valueOf(remaining)),
                "x-ratelimit-used", List.of(String.valueOf(limit - remaining)),
                "x-ratelimit-reset", List.of(String.valueOf(resetAtEpoch))), (a, b) -> true);
    }

    @Test
    @DisplayName("Token fingerprinting produces irreversible, deterministic 16-char hex identifiers")
    void testTokenFingerprint() {
        String tokenA = "ghp_1234567890abcdefghijklmnopqrstuv";
        String tokenBearerA = "Bearer ghp_1234567890abcdefghijklmnopqrstuv";

        String fp1 = GitHubApiBudgetService.fingerprint(tokenA);
        String fp2 = GitHubApiBudgetService.fingerprint(tokenBearerA);

        assertEquals(fp1, fp2, "Bearer prefix must be stripped before fingerprinting");
        assertEquals(16, fp1.length(), "Fingerprint must be exactly 16 hex chars");
        assertFalse(fp1.contains("ghp_"), "Raw secret must never be exposed in fingerprint");

        assertEquals(GitHubApiBudgetService.DEFAULT_TOKEN_FINGERPRINT,
                GitHubApiBudgetService.fingerprint(null));
        assertEquals(GitHubApiBudgetService.DEFAULT_TOKEN_FINGERPRINT,
                GitHubApiBudgetService.fingerprint("   "));
    }

    @Test
    @DisplayName("Rate-limiting token A does NOT block token B (cross-context isolation)")
    void rateLimitingOneTokenDoesNotBlockAnother() {
        GitHubApiBudgetService budgetService = new GitHubApiBudgetService();
        String tokenAlice = "ghp_alice_token_secret_111111111111";
        String tokenBob = "ghp_bob_token_secret_22222222222222";

        // Alice receives 403 Rate Limit Exceeded
        HttpHeaders aliceExhaustedHeaders = headers(0, 5000, Instant.now().plusSeconds(180).getEpochSecond());
        budgetService.recordResponse(tokenAlice, "GET /repos/o/r/pulls", 403, aliceExhaustedHeaders,
                "{\"message\":\"API rate limit exceeded\"}");

        // Bob makes a successful call with remaining budget
        HttpHeaders bobNormalHeaders = headers(4950, 5000, Instant.now().plusSeconds(300).getEpochSecond());
        budgetService.recordResponse(tokenBob, "GET /repos/o/r/issues", 200, bobNormalHeaders, "[]");

        // Assert: Alice is blocked
        GitHubApiBudgetService.GuardDecision aliceGuard = budgetService.guard(tokenAlice, "GET /repos/o/r/pulls");
        assertFalse(aliceGuard.allowed(), "Token Alice must be blocked due to rate limit exhaustion");
        assertEquals("exhausted", budgetService.snapshot(tokenAlice).status());

        // Assert: Bob is allowed! The factory is NOT globally frozen!
        GitHubApiBudgetService.GuardDecision bobGuard = budgetService.guard(tokenBob, "GET /repos/o/r/issues");
        assertTrue(bobGuard.allowed(), "Token Bob must remain available even when Token Alice is exhausted");
        assertEquals("available", budgetService.snapshot(tokenBob).status());
        assertEquals(4950, budgetService.snapshot(tokenBob).remaining());

        // Factory-level availability: at least one token is available, so the factory can continue working
        assertTrue(budgetService.available(), "Factory must remain available as long as an active token is unblocked");
    }

    @Test
    @DisplayName("Window reset for token A does NOT clear spend tally for token B")
    void windowResetOnOneTokenDoesNotClearOtherTokenSpend() {
        GitHubApiBudgetService budgetService = new GitHubApiBudgetService();
        String tokenAlice = "ghp_alice_token_secret_111111111111";
        String tokenBob = "ghp_bob_token_secret_22222222222222";

        long window1Epoch = 1_000_000L;
        long window2Epoch = 2_000_000L;

        // Alice spends 2 calls in Window 1
        budgetService.recordResponse(tokenAlice, "GET /repos/o/r/pulls", 200, headers(4999, 5000, window1Epoch), "");
        budgetService.recordResponse(tokenAlice, "GET /repos/o/r/pulls", 200, headers(4998, 5000, window1Epoch), "");

        // Bob spends 3 calls in Window 1
        budgetService.recordResponse(tokenBob, "GET /repos/o/r/issues", 200, headers(4999, 5000, window1Epoch), "");
        budgetService.recordResponse(tokenBob, "GET /repos/o/r/issues", 200, headers(4998, 5000, window1Epoch), "");
        budgetService.recordResponse(tokenBob, "GET /repos/o/r/issues", 200, headers(4997, 5000, window1Epoch), "");

        assertThat(budgetService.spendByOperation(tokenAlice))
                .containsExactly(Map.entry("GET /repos/o/r/pulls", 2L));
        assertThat(budgetService.spendByOperation(tokenBob))
                .containsExactly(Map.entry("GET /repos/o/r/issues", 3L));

        // Alice enters Window 2 (reset occurs for Alice)
        budgetService.recordResponse(tokenAlice, "GET /repos/o/r/pulls", 200, headers(4999, 5000, window2Epoch), "");

        // Alice's spend tally has reset for the new window
        assertThat(budgetService.spendByOperation(tokenAlice))
                .containsExactly(Map.entry("GET /repos/o/r/pulls", 1L));

        // Bob's spend tally remains completely unaffected! (No cross-context window leak)
        assertThat(budgetService.spendByOperation(tokenBob))
                .containsExactly(Map.entry("GET /repos/o/r/issues", 3L));
    }

    @Test
    @DisplayName("Query string normalization ensures finite key set (|{key}| finite)")
    void queryParametersAreStrippedFromOperationKeys() {
        assertEquals("GET /repos/o/r/pulls",
                GitHubApiBudgetService.normalizeOperation("GET /repos/o/r/pulls?state=closed&per_page=100&page=4"));
        assertEquals("POST /repos/o/r/pulls/12/reviews",
                GitHubApiBudgetService.normalizeOperation("POST /repos/o/r/pulls/12/reviews"));
        assertEquals("(unnamed)",
                GitHubApiBudgetService.normalizeOperation("   "));
    }
}
