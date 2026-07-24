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
}
