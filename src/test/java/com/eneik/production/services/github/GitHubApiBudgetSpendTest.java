package com.eneik.production.services.github;

import org.junit.jupiter.api.Test;

import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Model rule 8.17: shared capacity must be attributable to its consumers, or the admission order - a claim
 * about who yields to whom - cannot be applied at all. The hourly GitHub limit is capacity of exactly that
 * kind, and exhausting it stops the whole flow (GITHUB_RATE_LIMITED is globally blocking).
 *
 * <p>Measured on the live circuit 2026-08-30 17:20: 5000 of 5000 spent, the project frozen, and the only
 * thing recorded was the name of the last call. Measured again 142 seconds after the reset: 234 calls
 * already gone - the whole window inside forty-three minutes, so it recurs every hour until the spend can
 * be read.
 */
class GitHubApiBudgetSpendTest {

    private static HttpHeaders headers(int remaining, long resetAtEpoch) {
        return HttpHeaders.of(Map.of(
                "x-ratelimit-limit", List.of("5000"),
                "x-ratelimit-remaining", List.of(String.valueOf(remaining)),
                "x-ratelimit-used", List.of(String.valueOf(5000 - remaining)),
                "x-ratelimit-reset", List.of(String.valueOf(resetAtEpoch))), (a, b) -> true);
    }

    @Test
    void theWindowsSpendIsAttributedToTheOperationsThatCausedIt() {
        GitHubApiBudgetService budget = new GitHubApiBudgetService();
        budget.recordResponse("GET /repos/o/r/pulls", 200, headers(4999, 1_000_000L), "");
        budget.recordResponse("GET /repos/o/r/pulls", 200, headers(4998, 1_000_000L), "");
        budget.recordResponse("GET /repos/o/r/contents/x", 200, headers(4997, 1_000_000L), "");

        assertThat(budget.spendByOperation())
                .containsEntry("GET /repos/o/r/pulls", 2L)
                .containsEntry("GET /repos/o/r/contents/x", 1L);
    }

    @Test
    void pagingDoesNotSplitOneOperationIntoUnboundedlyManyKeys() {
        // The live value carried "?state=closed&per_page=100&page=2". Keyed raw, page numbers alone would
        // grow the tally without limit, and an unbounded tally is a leak rather than an account.
        GitHubApiBudgetService budget = new GitHubApiBudgetService();
        budget.recordResponse("GET /repos/o/r/pulls?state=closed&page=1", 200, headers(4999, 1_000_000L), "");
        budget.recordResponse("GET /repos/o/r/pulls?state=closed&page=2", 200, headers(4998, 1_000_000L), "");

        assertThat(budget.spendByOperation()).containsExactly(Map.entry("GET /repos/o/r/pulls", 2L));
    }

    @Test
    void aNewWindowStartsItsOwnAccount() {
        // The tally answers "what spent THIS window". Carrying it across a reset would answer nothing.
        GitHubApiBudgetService budget = new GitHubApiBudgetService();
        budget.recordResponse("GET /repos/o/r/pulls", 200, headers(4999, 1_000_000L), "");
        budget.recordResponse("GET /repos/o/r/issues", 200, headers(4999, 2_000_000L), "");

        assertThat(budget.spendByOperation()).containsExactly(Map.entry("GET /repos/o/r/issues", 1L));
    }

    @Test
    void theHeaviestConsumerIsNamedFirst() {
        GitHubApiBudgetService budget = new GitHubApiBudgetService();
        budget.recordResponse("GET /repos/o/r/contents/x", 200, headers(4999, 1_000_000L), "");
        budget.recordResponse("GET /repos/o/r/pulls", 200, headers(4998, 1_000_000L), "");
        budget.recordResponse("GET /repos/o/r/pulls", 200, headers(4997, 1_000_000L), "");

        assertThat(budget.spendByOperation().keySet().iterator().next()).isEqualTo("GET /repos/o/r/pulls");
    }
}
