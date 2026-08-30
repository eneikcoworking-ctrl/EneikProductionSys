package com.eneik.production.services.github;

import com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Model rule 8.5, monotone mark: a "repeat when something new appeared" mechanism compares against a mark
 * it cannot itself advance. The pull-request walk had no mark at all - it re-read the repository's entire
 * closed history on every invocation, from four call sites.
 *
 * <p>Measured on the live circuit 2026-08-30: 849 of 1173 accounted GitHub calls were this one listing,
 * 72% of the hourly window, and the 5000-request budget was exhausted twice inside one hour. Each
 * exhaustion put the project into GITHUB_RATE_LIMITED, which denies every operational action there is.
 */
class PullRequestWalkWatermarkTest {

    private static final Instant MARK = Instant.parse("2026-08-30T18:00:00Z");

    @Test
    void aPullRequestChangedAfterTheMarkIsNotSkipped() {
        assertThat(GitHubPullRequestService.alreadySeen(MARK.plusSeconds(1), MARK)).isFalse();
    }

    @Test
    void aPullRequestChangedExactlyAtTheMarkWasAlreadyInThePreviousWalk() {
        assertThat(GitHubPullRequestService.alreadySeen(MARK, MARK)).isTrue();
    }

    @Test
    void theFirstWalkHasNoMarkAndSkipsNothing() {
        assertThat(GitHubPullRequestService.alreadySeen(MARK.minusSeconds(86400), null)).isFalse();
    }

    @Test
    void aPullRequestWithNoTimestampIsNeverTreatedAsSeen() {
        // Rule 8.6's second line: what is merely unknown does not leave the set.
        assertThat(GitHubPullRequestService.alreadySeen(null, MARK)).isFalse();
    }

    @Test
    void anIncrementalWalkKeepsWhatItDidNotReFetch() {
        // The whole point: pages that were not re-read must not vanish from the caller's answer.
        List<GitHubPullRequest> merged = GitHubPullRequestService.mergeWithCached(
                List.of(pr(41, "older")), List.of(pr(42, "just changed")));

        assertThat(merged).extracting(GitHubPullRequest::number).containsExactlyInAnyOrder(42, 41);
    }

    @Test
    void aPullRequestThatChangedReplacesItsCachedCopy() {
        List<GitHubPullRequest> merged = GitHubPullRequestService.mergeWithCached(
                List.of(pr(42, "before")), List.of(pr(42, "after")));

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).title()).isEqualTo("after");
    }

    private static GitHubPullRequest pr(int number, String title) {
        return new GitHubPullRequest("https://github.com/o/r/pull/" + number, number, title,
                "head-" + number, "author", false, "main", true, MARK);
    }

    /**
     * A mark is only valid where membership grows. Measured on the live circuit 2026-08-30 18:50, within an
     * hour of the mark being deployed: PR #496 was closed without merge at 18:50:15 and the merge sweep went
     * on finding it "clean open" at 18:50:22 and every minute after, because the cached open list kept it.
     * Rule 8.6: what left the set must leave the answer.
     */
    @Test
    void theOpenListIsNeverCarriedOverBecauseItShrinks() {
        assertThat(GitHubPullRequestService.membershipOnlyGrows("open")).isFalse();
        assertThat(GitHubPullRequestService.membershipOnlyGrows("all")).isFalse();
    }

    /** The closed history is what made the walk expensive, and it only grows. */
    @Test
    void theClosedHistoryIsWalkedAgainstTheMark() {
        assertThat(GitHubPullRequestService.membershipOnlyGrows("closed")).isTrue();
    }
}
