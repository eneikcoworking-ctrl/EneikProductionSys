package com.eneik.production.services;

import com.eneik.production.models.persistence.WishlistEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Falsification test suite for {@link WishlistContentSimilarityMatcher} grounding Stage 4 invariants:
 *
 * <p>1. Frege (D009, {@code GOTLOB_FREGE_01_SUBSTITUTION_ORACLE}):
 * Sense, reference and substitutivity salva veritate in wishlist content. Linguistic variations,
 * stopword insertions and syntactic rewordings preserve the core referent of the requirement.
 * Duplicates are caught above {@code DUPLICATE_THRESHOLD} (0.55), preventing redundant implementation cycles.
 *
 * <p>2. Grice (D009, {@code POL_GRAYS_08_SENSE_REFERENCE_SPLIT}):
 * Clear separation between surface phrasing (sense) and requirement denotation (reference).
 * Pairwise similarity is symmetric (salva veritate commutativity), and differing sentence structures
 * denoting the identical technical capability map to the same entity.
 *
 * <p>3. Grice (D001, {@code POL_GRAYS_03_INFERENTIAL_SCOREBOARD}):
 * Dynamic Otsu thresholding bounded within {@code [MIN_CLUSTER_THRESHOLD (0.35), MAX_CLUSTER_THRESHOLD (0.75)]}.
 * The clustering partition adapts to the batch's empirical variance without collapsing to extreme merges
 * or extreme fragmentation. Every element is preserved in exactly one cluster via union-find transitivity.
 */
class WishlistContentSimilarityMatcherFalsificationTest {

    private final WishlistContentSimilarityMatcher matcher = new WishlistContentSimilarityMatcher();

    private WishlistEntity wishlist(String content) {
        WishlistEntity entity = new WishlistEntity();
        entity.setId(UUID.randomUUID());
        entity.setContent(content);
        return entity;
    }

    // =========================================================================
    // 1. Frege (D009): GOTLOB_FREGE_01_SUBSTITUTION_ORACLE
    // =========================================================================

    @Test
    @DisplayName("Frege [D009]: phrasing substitution preserves requirement referent above DUPLICATE_THRESHOLD (0.55)")
    void falsifyFrege_substitutionOracle_rewordedContentPreservesReferentAboveThreshold() {
        WishlistEntity existingRateLimiter = wishlist(
                "Coverage audit gap [Daily Outbound Rate Limiter]: Telegram accounts require daily message caps "
                        + "to prevent spam-blocking under flood control rules.");

        // Candidate with active phrasing and stopword variations
        String candidateReworded = "Daily Outbound Rate Limiter: enforce message caps per Telegram account "
                + "so accounts prevent spam-blocking under flood control rules.";

        Optional<UUID> duplicateMatch = matcher.findLikelyDuplicate(List.of(existingRateLimiter), candidateReworded);

        assertThat(duplicateMatch)
                .as("Substitutivity salva veritate: reworded requirement must preserve referent and detect duplicate")
                .contains(existingRateLimiter.getId());
    }

    @Test
    @DisplayName("Frege [D009]: distinct functional requirements fall strictly below threshold")
    void falsifyFrege_substitutionOracle_distinctRequirementsDoNotMatch() {
        WishlistEntity webhookHandler = wishlist(
                "Implement Stripe webhook signature verification and idempotent charge event processing.");

        String authCandidate = "Implement OAuth2 refresh token rotation with Redis session revocation.";

        Optional<UUID> duplicateMatch = matcher.findLikelyDuplicate(List.of(webhookHandler), authCandidate);

        assertThat(duplicateMatch)
                .as("Functionally distinct requirements must evaluate below threshold (0.55) to prevent dropping real work")
                .isEmpty();
    }

    // =========================================================================
    // 2. Grice (D009): POL_GRAYS_08_SENSE_REFERENCE_SPLIT
    // =========================================================================

    @Test
    @DisplayName("Grice [D009]: pairwise similarity is symmetric and invariant under argument ordering")
    void falsifyGrice_senseReferenceSplit_similarityScoreIsSymmetric() {
        String docA = "Coverage gap: Export user audit log in CSV format for compliance reporting.";
        String docB = "Audit log CSV export for compliance reporting and data download.";

        double simAB = matcher.similarity(docA, docB);
        double simBA = matcher.similarity(docB, docA);

        assertThat(simAB)
                .as("Pairwise similarity must be strictly symmetric: sim(A, B) == sim(B, A)")
                .isEqualTo(simBA);
        assertThat(simAB).isGreaterThanOrEqualTo(0.5);
    }

    @Test
    @DisplayName("Grice [D009]: surface grammatical transformations preserve underlying requirement identity")
    void falsifyGrice_senseReferenceSplit_passiveActiveTransformationsPreserveReference() {
        WishlistEntity active = wishlist("Admin exports monthly payroll report to PDF file with digital seal.");
        String passive = "Monthly payroll report is exported to PDF file with digital seal by admin.";

        Optional<UUID> matched = matcher.findLikelyDuplicate(List.of(active), passive);

        assertThat(matched)
                .as("Grammatical voice alteration must not obscure the shared requirement referent")
                .contains(active.getId());
    }

    // =========================================================================
    // 3. Grice (D001): POL_GRAYS_03_INFERENTIAL_SCOREBOARD
    // =========================================================================

    @Test
    @DisplayName("Grice [D001]: Otsu clustering dynamically separates bimodal distribution and preserves all items")
    void falsifyGrice_inferentialScoreboard_otsuDynamicThresholdClustersBimodalDistribution() {
        List<String> batch = List.of(
                "OAuth2 authorization code flow with PKCE authentication",
                "Implement OAuth2 authorization code flow using PKCE for mobile auth",
                "PostgreSQL JSONB column migration for user audit logs",
                "Migrate user audit logs table to PostgreSQL JSONB column",
                "Add Prometheus scrape endpoint on actuator metrics"
        );

        List<List<Integer>> clusters = matcher.clusterBySimilarity(batch);

        // Expect 3 clusters: OAuth2 pair (0,1), PostgreSQL pair (2,3), and Prometheus singleton (4)
        assertThat(clusters)
                .as("Otsu dynamic threshold must split the 5 items into exactly 3 coherent clusters")
                .hasSize(3);

        // Invariant: every index appears in exactly one cluster
        List<Integer> allIndices = clusters.stream().flatMap(List::stream).sorted().toList();
        assertThat(allIndices).containsExactly(0, 1, 2, 3, 4);

        // Verify specific groupings
        boolean hasOauthCluster = clusters.stream().anyMatch(c -> c.contains(0) && c.contains(1));
        boolean hasPgCluster = clusters.stream().anyMatch(c -> c.contains(2) && c.contains(3));
        boolean hasMetricsCluster = clusters.stream().anyMatch(c -> c.size() == 1 && c.contains(4));

        assertThat(hasOauthCluster).as("OAuth2 items must cluster together").isTrue();
        assertThat(hasPgCluster).as("PostgreSQL migration items must cluster together").isTrue();
        assertThat(hasMetricsCluster).as("Prometheus item must form a singleton cluster").isTrue();
    }

    @Test
    @DisplayName("Grice [D001]: dynamic threshold clamp stays strictly bounded in [0.35, 0.75]")
    void falsifyGrice_inferentialScoreboard_thresholdClampPreventsExtremeCollapse() {
        // High-overlap batch: almost identical strings
        List<String> tightBatch = List.of(
                "Configure Redis cluster failover sentinel replica",
                "Configure Redis cluster failover sentinel replica node",
                "Configure Redis cluster failover sentinel replica instance"
        );

        List<List<Integer>> tightClusters = matcher.clusterBySimilarity(tightBatch);
        // All should merge cleanly without threshold exploding beyond MAX_CLUSTER_THRESHOLD (0.75)
        assertThat(tightClusters).hasSize(1);
        assertThat(tightClusters.get(0)).containsExactlyInAnyOrder(0, 1, 2);

        // Low-overlap batch: completely disparate items
        List<String> disparateBatch = List.of(
                "Configure Redis cluster failover sentinel replica",
                "Design dark mode UI stylesheet theme",
                "Generate automated PDF invoices for billing"
        );

        List<List<Integer>> disparateClusters = matcher.clusterBySimilarity(disparateBatch);
        // None should merge, threshold must not collapse to 0 (MIN_CLUSTER_THRESHOLD = 0.35 prevents false merges)
        assertThat(disparateClusters).hasSize(3);
    }
}
