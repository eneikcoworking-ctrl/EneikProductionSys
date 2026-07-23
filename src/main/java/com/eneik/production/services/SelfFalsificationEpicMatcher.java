package com.eneik.production.services;

import com.eneik.production.models.persistence.FeatureEntity;
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
 * Deterministic, non-AI safeguard for self_falsification-sourced эпик resolution: when the compiler
 * decides a falsification finding needs a brand-new эпик (existingEpicId == null), this independently
 * checks whether an existing эпик already covers the same client-facing JTBD before letting the backend
 * create a duplicate. Self-falsification audits already-shipped code, so its prior should favor attaching
 * over inventing - this is exactly Quine's web-of-belief principle: don't add new core structure when the
 * existing structure already accounts for the observation.
 *
 * Priority order requested by the operator (Cynefin, then Kano, then minimal-change) is encoded directly
 * in the scoring weights below: a Cynefin-domain match counts for more than a Kano-class match.
 */
@Component
public class SelfFalsificationEpicMatcher {

    private static final Logger log = LoggerFactory.getLogger(SelfFalsificationEpicMatcher.class);

    private static final double ATTACH_THRESHOLD = 0.42;
    private static final double AMBIGUITY_GAP = 0.08;
    private static final double CYNEFIN_MATCH_BONUS = 0.15;
    private static final double KANO_MATCH_BONUS = 0.07;

    private static final Set<String> JTBD_STOPWORDS = Set.of(
            "when", "i", "want", "so", "that", "the", "a", "an", "to", "of", "for", "and", "in", "on", "is"
    );

    public Optional<UUID> findLikelyExistingEpic(List<FeatureEntity> existingEpics,
            MLPredictionServiceClient.EpicPlan candidate) {
        try {
            return findLikelyExistingEpicInternal(existingEpics, candidate);
        } catch (Exception e) {
            log.warn("SelfFalsificationEpicMatcher: scoring failed, treating as no match: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    private Optional<UUID> findLikelyExistingEpicInternal(List<FeatureEntity> existingEpics,
            MLPredictionServiceClient.EpicPlan candidate) {
        if (existingEpics == null || existingEpics.isEmpty() || candidate == null) {
            return Optional.empty();
        }
        Set<String> candidateTokens = tokenize(candidate.title(), candidate.jtbd());

        FeatureEntity best = null;
        double bestScore = -1.0;
        FeatureEntity secondBest = null;
        double secondBestScore = -1.0;

        for (FeatureEntity epic : existingEpics) {
            double textScore = jaccard(candidateTokens, tokenize(epic.getTitle(), epic.getJtbd()));
            boolean cynefinMatch = equalsIgnoreCaseSafe(candidate.cynefinDomain(), epic.getCynefinDomain());
            boolean kanoMatch = equalsIgnoreCaseSafe(candidate.kanoClass(), epic.getKanoClass());
            double combinedScore = textScore
                    + (cynefinMatch ? CYNEFIN_MATCH_BONUS : 0)
                    + (kanoMatch ? KANO_MATCH_BONUS : 0);

            if (combinedScore > bestScore) {
                secondBest = best;
                secondBestScore = bestScore;
                best = epic;
                bestScore = combinedScore;
            } else if (combinedScore > secondBestScore) {
                secondBest = epic;
                secondBestScore = combinedScore;
            }
        }

        if (best == null || bestScore < ATTACH_THRESHOLD) {
            log.debug("SelfFalsificationEpicMatcher: no attach for '{}' - best score {} below threshold {}",
                    candidate.title(), bestScore, ATTACH_THRESHOLD);
            return Optional.empty();
        }
        boolean ambiguous = secondBest != null && (bestScore - secondBestScore) < AMBIGUITY_GAP;
        if (ambiguous) {
            log.debug("SelfFalsificationEpicMatcher: no attach for '{}' - ambiguous, best {} vs second-best {}",
                    candidate.title(), bestScore, secondBestScore);
            return Optional.empty();
        }

        log.info("SelfFalsificationEpicMatcher: matched '{}' to existing эпик {} ('{}') score={}",
                candidate.title(), best.getId(), best.getTitle(), bestScore);
        return Optional.of(best.getId());
    }

    private boolean equalsIgnoreCaseSafe(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.trim().equalsIgnoreCase(b.trim());
    }

    private Set<String> tokenize(String title, String jtbd) {
        String combined = ((title != null ? title : "") + " " + (jtbd != null ? jtbd : ""))
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-zа-яё0-9\\s]", " ");
        Set<String> tokens = new HashSet<>();
        for (String token : combined.split("\\s+")) {
            if (!token.isBlank() && !JTBD_STOPWORDS.contains(token)) {
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
