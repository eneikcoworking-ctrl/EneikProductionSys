package com.eneik.production.services;

import com.eneik.production.models.persistence.WishlistEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic, non-AI safeguard against semantic (non-racy) wishlist-content duplication: two
 * DIFFERENT WishlistEntity rows, created at different times (e.g. two separate coverage-audit runs),
 * independently describing the same real-world requirement in different wording. Confirmed live incident,
 * 2026-07-24: "Implement Daily Outbound Messaging Rate Limiter" got identified as a gap twice, each time
 * creating its own wishlist row, each independently compiled into a full implementation task (PR#56/#57) -
 * a wasted duplicate implementation cycle no existing check could have caught (SelfFalsificationEpicMatcher
 * operates one layer up, on FeatureEntity/эпик title+jtbd, never on WishlistEntity.content).
 *
 * Deliberately a sibling of SelfFalsificationEpicMatcher, not a shared refactor of it - copying the small
 * tokenize/jaccard core carries zero risk to that already-proven-in-production class. Same fail-open
 * contract: a scoring exception is treated as "no match", never blocks or drops real work.
 *
 * Threshold set deliberately high relative to SelfFalsificationEpicMatcher's 0.42 (tuned for short
 * title+jtbd pairs): false negatives here just mean the same gap resurfaces and gets caught next audit
 * cycle - cheap and self-healing. False positives silently drop real distinct work with no retry path -
 * expensive and hard to notice. With no live data to tune against yet, bias toward the safe failure mode.
 */
@Component
public class WishlistContentSimilarityMatcher {

    private static final Logger log = LoggerFactory.getLogger(WishlistContentSimilarityMatcher.class);

    private static final double DUPLICATE_THRESHOLD = 0.55;

    private static final Set<String> STOPWORDS = Set.of(
            "when", "i", "want", "so", "that", "the", "a", "an", "to", "of", "for", "and", "in", "on", "is",
            "given", "then", "must", "should", "this", "with", "as", "are", "be", "it"
    );

    public Optional<UUID> findLikelyDuplicate(List<WishlistEntity> existingLiveWishlists, String candidateText) {
        try {
            return findLikelyDuplicateInternal(existingLiveWishlists, candidateText);
        } catch (Exception e) {
            log.warn("WishlistContentSimilarityMatcher: scoring failed, treating as no match: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    private Optional<UUID> findLikelyDuplicateInternal(List<WishlistEntity> existingLiveWishlists, String candidateText) {
        if (existingLiveWishlists == null || existingLiveWishlists.isEmpty()
                || candidateText == null || candidateText.isBlank()) {
            return Optional.empty();
        }
        Set<String> candidateTokens = tokenize(candidateText);
        if (candidateTokens.isEmpty()) {
            return Optional.empty();
        }

        WishlistEntity best = null;
        double bestScore = -1.0;
        for (WishlistEntity existing : existingLiveWishlists) {
            double score = jaccard(candidateTokens, tokenize(existing.getContent()));
            if (score > bestScore) {
                best = existing;
                bestScore = score;
            }
        }

        if (best == null || bestScore < DUPLICATE_THRESHOLD) {
            log.debug("WishlistContentSimilarityMatcher: no duplicate match - best score {} below threshold {}",
                    bestScore, DUPLICATE_THRESHOLD);
            return Optional.empty();
        }
        log.info("WishlistContentSimilarityMatcher: candidate content matched existing wishlist {} score={}",
                best.getId(), bestScore);
        return Optional.of(best.getId());
    }

    private Set<String> tokenize(String text) {
        String normalized = (text == null ? "" : text)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-zа-яё0-9\\s]", " ");
        Set<String> tokens = new HashSet<>();
        for (String token : normalized.split("\\s+")) {
            if (!token.isBlank() && !STOPWORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        if (union.isEmpty()) {
            return 0.0;
        }
        return (double) intersection.size() / union.size();
    }
}
