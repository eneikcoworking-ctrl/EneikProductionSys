package com.eneik.production.services;

import com.eneik.production.models.persistence.WishlistEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    // clusterBySimilarity's threshold is dynamic (Otsu's method, see dynamicClusterThreshold) - these two
    // bound how far it may drift from DUPLICATE_THRESHOLD, the fallback/prior it starts from. Without a
    // bound, a genuinely degenerate similarity distribution (e.g. one run where every pair happens to score
    // similarly) could push Otsu toward 0 (merge everything into one giant cluster) or 1 (nothing ever
    // merges) - the clamp keeps the dynamic threshold adaptive without letting it collapse to either extreme.
    private static final double MIN_CLUSTER_THRESHOLD = 0.35;
    private static final double MAX_CLUSTER_THRESHOLD = 0.75;

    private static final Set<String> STOPWORDS = Set.of(
            "when", "i", "want", "so", "that", "the", "a", "an", "to", "of", "for", "and", "in", "on", "is",
            "given", "then", "must", "should", "this", "with", "as", "are", "be", "it"
    );

    /**
     * Pairwise Jaccard similarity between two free-text strings, on the same tokenizer/stopword rules as
     * findLikelyDuplicate/clusterBySimilarity above - added 2026-08-05 for EvidenceCoherenceService's WEAK
     * (topically-similar-but-no-shared-structural-id) edges, so it reuses this class's exact tokenization
     * instead of a second, possibly-diverging implementation. Same fail-open contract: a scoring exception
     * is treated as "not similar" (0.0), never propagated.
     */
    public double similarity(String a, String b) {
        try {
            return jaccard(tokenize(a), tokenize(b));
        } catch (Exception e) {
            log.warn("WishlistContentSimilarityMatcher: similarity scoring failed, treating as 0.0: {}", e.getMessage(), e);
            return 0.0;
        }
    }

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

    /**
     * Single-linkage clustering via union-find over the same pairwise Jaccard metric used for dedup above -
     * builds a graph where two candidates are connected if their similarity meets a threshold (directly, or
     * transitively through a chain of similar intermediates), and returns each connected component as one
     * cluster. Every input index appears in exactly one output cluster - nothing is discarded, unlike
     * findLikelyDuplicate's drop-the-later-one behavior. This is the mathematical shape the operator asked
     * for explicitly (2026-07-25): "let everyone speak, group instead of cut" - it is also, not coincidentally,
     * structurally identical to how Noriaki Kano's own original survey methodology aggregates many individual
     * respondent answers into one feature classification (by tabulating the modal answer) rather than
     * treating any single respondent's answer as authoritative on its own.
     *
     * The threshold itself is DYNAMIC (operator directive, 2026-07-25: a single hardcoded cutoff is arbitrary
     * relative to whatever this particular batch's actual similarity spread looks like) - computed per call
     * via Otsu's method (Otsu, 1979 - originally image-thresholding, generalizes directly to any 1-D score
     * distribution one wants to split into two classes) over this batch's own pairwise-similarity
     * distribution, rather than reusing findLikelyDuplicate's fixed DUPLICATE_THRESHOLD. DUPLICATE_THRESHOLD
     * remains untouched and is only this method's fallback/prior when Otsu has nothing to discriminate (fewer
     * than 2 pairs, or a degenerate all-equal distribution).
     *
     * O(n^2) pairwise comparisons plus an O(n^2 log n) sort of the similarity list - fine for the
     * philosophical-falsification track's bound of ~78 candidates per run (at most ~3000 pairs). Fail-open
     * like findLikelyDuplicate: on any exception, returns every candidate as its own singleton cluster
     * (equivalent to "no merging happened"), never silently drops an item.
     */
    public List<List<Integer>> clusterBySimilarity(List<String> candidateTexts) {
        try {
            return clusterBySimilarityInternal(candidateTexts);
        } catch (Exception e) {
            log.warn("WishlistContentSimilarityMatcher: clustering failed, falling back to singleton clusters: {}", e.getMessage(), e);
            List<List<Integer>> singletons = new java.util.ArrayList<>();
            for (int i = 0; i < candidateTexts.size(); i++) {
                singletons.add(List.of(i));
            }
            return singletons;
        }
    }

    private List<List<Integer>> clusterBySimilarityInternal(List<String> candidateTexts) {
        int n = candidateTexts.size();
        List<Set<String>> tokenSets = new java.util.ArrayList<>(n);
        for (String text : candidateTexts) {
            tokenSets.add(tokenize(text));
        }

        double[][] pairwiseScores = new double[n][n];
        List<Double> allPairScores = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double score = jaccard(tokenSets.get(i), tokenSets.get(j));
                pairwiseScores[i][j] = score;
                allPairScores.add(score);
            }
        }
        double threshold = dynamicClusterThreshold(allPairScores);
        log.info("WishlistContentSimilarityMatcher: clustering {} candidate(s) with dynamic threshold {} (Otsu over {} pair(s))",
                n, threshold, allPairScores.size());

        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (pairwiseScores[i][j] >= threshold) {
                    union(parent, i, j);
                }
            }
        }
        Map<Integer, List<Integer>> byRoot = new java.util.LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            byRoot.computeIfAbsent(find(parent, i), root -> new java.util.ArrayList<>()).add(i);
        }
        return new java.util.ArrayList<>(byRoot.values());
    }

    /**
     * Otsu's method: given a 1-D distribution of scores, find the split point maximizing between-class
     * variance (equivalently minimizing within-class variance) between "below" and "at-or-above" - the
     * classical way to turn a continuous score distribution into a binary decision without picking the cutoff
     * by hand. Candidate thresholds are midpoints between consecutive distinct sorted values (the only points
     * where the partition can actually change). Falls back to DUPLICATE_THRESHOLD when there is nothing to
     * discriminate (fewer than 2 pairs, or every pair scored identically - no real separation exists to find).
     */
    private double dynamicClusterThreshold(List<Double> pairwiseScores) {
        if (pairwiseScores.size() < 2) {
            return DUPLICATE_THRESHOLD;
        }
        List<Double> sorted = new java.util.ArrayList<>(pairwiseScores);
        java.util.Collections.sort(sorted);
        int n = sorted.size();
        double total = 0;
        for (double s : sorted) {
            total += s;
        }

        double runningSum = 0;
        double bestThreshold = DUPLICATE_THRESHOLD;
        double bestVariance = -1.0;
        for (int i = 0; i < n - 1; i++) {
            runningSum += sorted.get(i);
            double a = sorted.get(i);
            double b = sorted.get(i + 1);
            if (a == b) {
                continue;
            }
            int count0 = i + 1;
            int count1 = n - count0;
            double mean0 = runningSum / count0;
            double mean1 = (total - runningSum) / count1;
            double w0 = (double) count0 / n;
            double w1 = (double) count1 / n;
            double betweenVariance = w0 * w1 * (mean0 - mean1) * (mean0 - mean1);
            if (betweenVariance > bestVariance) {
                bestVariance = betweenVariance;
                bestThreshold = (a + b) / 2.0;
            }
        }
        if (bestVariance <= 0) {
            return DUPLICATE_THRESHOLD;
        }
        return Math.min(MAX_CLUSTER_THRESHOLD, Math.max(MIN_CLUSTER_THRESHOLD, bestThreshold));
    }

    private int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private void union(int[] parent, int a, int b) {
        int rootA = find(parent, a);
        int rootB = find(parent, b);
        if (rootA != rootB) {
            parent[rootA] = rootB;
        }
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
