package com.eneik.production.services;

import com.eneik.production.models.persistence.WishlistEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WishlistContentSimilarityMatcherTest {

    private final WishlistContentSimilarityMatcher matcher = new WishlistContentSimilarityMatcher();

    private WishlistEntity existing(String content) {
        WishlistEntity w = new WishlistEntity();
        w.setId(UUID.randomUUID());
        w.setContent(content);
        return w;
    }

    @Test
    void matchesNearDuplicateContentAboveThreshold() {
        WishlistEntity original = existing("Coverage audit gap [Implement Daily Outbound Messaging Rate Limiter]: "
                + "the codebase has no enforcement of daily outbound message caps per account\n"
                + "JTBD: When running outreach campaigns, I want to enforce strict daily message limits per "
                + "Telegram account, so that our warmed-up accounts avoid getting spam-blocked by Telegram flood control.");
        String rewordedCandidate = "Implement Daily Outbound Messaging Rate Limiter enforce strict daily message "
                + "limits per Telegram account so warmed-up accounts avoid getting spam-blocked by Telegram flood control";

        Optional<UUID> result = matcher.findLikelyDuplicate(List.of(original), rewordedCandidate);

        assertTrue(result.isPresent());
        assertEquals(original.getId(), result.get());
    }

    @Test
    void doesNotMatchGenuinelyDifferentContent() {
        WishlistEntity rateLimiter = existing("Coverage audit gap [Implement Daily Outbound Messaging Rate Limiter]: "
                + "the codebase has no enforcement of daily outbound message caps per account\n"
                + "JTBD: When running outreach campaigns, I want to enforce strict daily message limits per "
                + "Telegram account, so that our warmed-up accounts avoid getting spam-blocked by Telegram flood control.");
        String unrelatedCandidate = "Implement CSV export for the admin dashboard's audit log, so operators can "
                + "download a report of every account status change for offline compliance review.";

        Optional<UUID> result = matcher.findLikelyDuplicate(List.of(rateLimiter), unrelatedCandidate);

        assertTrue(result.isEmpty());
    }

    @Test
    void returnsEmptyForNullOrBlankInputsInsteadOfThrowing() {
        assertTrue(matcher.findLikelyDuplicate(null, "anything").isEmpty());
        assertTrue(matcher.findLikelyDuplicate(List.of(), "anything").isEmpty());
        assertTrue(matcher.findLikelyDuplicate(List.of(existing("some content")), null).isEmpty());
        assertTrue(matcher.findLikelyDuplicate(List.of(existing("some content")), "   ").isEmpty());
    }

    @Test
    void treatsNullExistingContentAsNoMatchRatherThanThrowing() {
        WishlistEntity blank = existing(null);
        assertTrue(matcher.findLikelyDuplicate(List.of(blank), "Implement Daily Outbound Messaging Rate Limiter").isEmpty());
    }

    // --- clusterBySimilarity / dynamic (Otsu) threshold (2026-07-25) ------------------------------------

    @Test
    void clustersTwoDistinctBimodalGroupsCorrectlyRegardlessOfFixedThreshold() {
        // A clearly bimodal batch: two near-duplicate pairs, and one item unrelated to both - proves the
        // DYNAMIC threshold (computed from this batch's own pairwise distribution via Otsu, not a hardcoded
        // constant) finds the gap between "near-duplicate" and "unrelated" correctly on its own.
        List<String> candidates = List.of(
                "Implement Daily Outbound Messaging Rate Limiter enforce strict daily message limits per account",
                "Add Daily Outbound Messaging Rate Limiter to enforce strict daily message limits per account",
                "Implement CSV export for the admin dashboard audit log for offline compliance review"
        );

        List<List<Integer>> clusters = matcher.clusterBySimilarity(candidates);

        assertEquals(2, clusters.size(), "the two near-duplicate rate-limiter texts must merge into one cluster, "
                + "the unrelated CSV-export text must remain its own cluster");
        boolean hasPairCluster = clusters.stream().anyMatch(c -> c.size() == 2 && c.contains(0) && c.contains(1));
        boolean hasSingletonCluster = clusters.stream().anyMatch(c -> c.size() == 1 && c.contains(2));
        assertTrue(hasPairCluster, "the two rate-limiter texts (indices 0,1) must be in the same cluster");
        assertTrue(hasSingletonCluster, "the unrelated CSV-export text (index 2) must be its own cluster");
    }

    @Test
    void everyIndexAppearsInExactlyOneClusterNothingIsDiscarded() {
        List<String> candidates = List.of(
                "Add dark mode theme toggle for the dashboard",
                "Improve error message tone during checkout failures",
                "Wire pagination controls to the accounts table",
                "Support CSV export for the audit log"
        );

        List<List<Integer>> clusters = matcher.clusterBySimilarity(candidates);

        int totalIndices = clusters.stream().mapToInt(List::size).sum();
        assertEquals(candidates.size(), totalIndices, "every input index must appear in exactly one cluster - clustering must never drop an item");
    }

    @Test
    void singleCandidateFormsItsOwnClusterWithoutThrowing() {
        List<List<Integer>> clusters = matcher.clusterBySimilarity(List.of("A lone critique with nothing to compare against"));
        assertEquals(1, clusters.size());
        assertEquals(List.of(0), clusters.get(0));
    }

    @Test
    void emptyInputProducesNoClustersWithoutThrowing() {
        assertTrue(matcher.clusterBySimilarity(List.of()).isEmpty());
    }
}
