package com.eneik.production.services;

import com.eneik.production.models.persistence.FeatureEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.WishlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the stable Feature identity a wishlist item (and everything compiled from it) belongs to.
 * A feature is minted lazily, once, the first time a wishlist item is actually turned into work - not
 * for every wishlist row, only the ones that become real tasks. Safe by construction: any wishlist item
 * without a pre-set featureId just becomes a feature of its own (see TechnicalLeadCompiler.
 * createAndSaveTask, the universal call-through point for task creation) - it never fails, it just fails
 * to find a reason for continuation, same "default to safe" principle as the account-pinning fix.
 */
@Service
public class FeatureService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FeatureService.class);

    private final FeatureRepository featureRepository;
    private final WishlistRepository wishlistRepository;
    private final com.eneik.production.services.coherence.EvidenceCoherenceService evidenceCoherenceService;
    private final EpistemicMetadataClassifier epistemicMetadataClassifier;

    public FeatureService(FeatureRepository featureRepository, WishlistRepository wishlistRepository) {
        this(featureRepository, wishlistRepository, null);
    }

    public FeatureService(FeatureRepository featureRepository,
                          WishlistRepository wishlistRepository,
                          com.eneik.production.services.coherence.EvidenceCoherenceService evidenceCoherenceService) {
        this(featureRepository, wishlistRepository, evidenceCoherenceService, new EpistemicMetadataClassifier());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public FeatureService(FeatureRepository featureRepository,
                          WishlistRepository wishlistRepository,
                          @org.springframework.context.annotation.Lazy com.eneik.production.services.coherence.EvidenceCoherenceService evidenceCoherenceService,
                          EpistemicMetadataClassifier epistemicMetadataClassifier) {
        this.featureRepository = featureRepository;
        this.wishlistRepository = wishlistRepository;
        this.evidenceCoherenceService = evidenceCoherenceService;
        this.epistemicMetadataClassifier = epistemicMetadataClassifier == null
                ? new EpistemicMetadataClassifier()
                : epistemicMetadataClassifier;
    }

    @Transactional
    public UUID resolveOrCreateFeatureId(WishlistEntity wishlist, UUID projectId) {
        if (wishlist.getFeatureId() != null) {
            return wishlist.getFeatureId();
        }
        FeatureEntity feature = new FeatureEntity();
        feature.setProjectId(projectId);
        feature.setRootWishlistId(wishlist.getId());

        // 2026-08-27 (Phase 1): this call used to be calculateEpistemicEntrenchment(null, null), which made
        // every feature minted by the main compile path score the same constant 46.25 - and the wishlist row
        // it was minting from was carrying real classification data the whole time. Prefer what the wishlist
        // itself declares; fall back to extracting it from the text; leave it null when neither yields
        // anything, so the feature scores low and lands in PERIPHERY where the invariant gate can see it.
        EpistemicMetadataClassifier.Classification extracted = epistemicMetadataClassifier.classify(
                wishlist.getContent(), wishlist.getGroundedContent(), wishlist.getJtbd(),
                wishlist.getAcceptanceCriteria());
        String cynefinDomain = firstUsable(wishlist.getCynefinDomain(), extracted.cynefinDomain());
        String kanoClass = extracted.kanoClass();
        double emsContract = emsContractConformance(
                wishlist.getJtbd(), wishlist.getSixSigmaMetric(), wishlist.getTocConstraintRef());

        double score = calculateEpistemicEntrenchment(kanoClass, cynefinDomain, emsContract);
        feature.setEpistemicScore(score);
        feature.setEpistemicLayer(classifyEpistemicLayer(score));
        // The two classification axes are stamped onto the row so the score is reproducible from stored
        // data and not only from this run's classifier - an unreadable number is not a measurement. The
        // contract fields deliberately are NOT copied here: they are already on the wishlist this feature
        // points at (rootWishlistId), and this path mints a bare grouping row whose jtbd is fed to the
        // compiler as an epic-matching candidate - filling it in from the wishlist would change what the
        // compiler matches against, which is a different decision than restoring the formula.
        feature.setCynefinDomain(cynefinDomain);
        feature.setKanoClass(kanoClass);
        log.info("[E3-ENTRENCHMENT] Feature from wishlist {}: cynefin={}, kano={}, E_EMS={}, EE={}, layer={}",
                wishlist.getId(), cynefinDomain, kanoClass, emsContract, score, feature.getEpistemicLayer());

        if (evidenceCoherenceService != null && wishlist.getContent() != null) {
            var evaluation = evidenceCoherenceService.evaluateFeatureHypothesis(
                    projectId, wishlist.getContent(), score, feature.getEpistemicLayer());
            log.info("[E3-HYPOTHESIS] Feature from wishlist {} coherence verdict: accepted={}, confidence={}, explanation={}",
                    wishlist.getId(), evaluation.accepted(), evaluation.confidence(), evaluation.explanation());
        }

        feature = featureRepository.save(feature);
        // 2026-08-04: a brand-new feature is its own lineage origin - stamped once here, alongside
        // featureId, and never touched again (see FeatureEntity.originFeatureId's javadoc).
        feature.setOriginFeatureId(feature.getId());
        feature = featureRepository.save(feature);
        wishlist.setFeatureId(feature.getId());
        if (wishlist.getOriginFeatureId() == null) {
            wishlist.setOriginFeatureId(feature.getId());
        }
        wishlistRepository.save(wishlist);
        return feature.getId();
    }

    /**
     * Phase 8 (2026-07-21, operator directive): every compile cycle must decide, per эпик, whether it matches
     * an already-existing эпик in the project or is genuinely new - this list is what gets handed to the
     * compiler prompt as the candidate set to semantically match against (id/title/jtbd only - enough for
     * the compiler to judge a narrative match without dumping every task inside each эпик into the prompt).
     */
    public List<FeatureEntity> listExistingEpics(UUID projectId) {
        return featureRepository.findByProjectIdAndDismissedAtIsNull(projectId);
    }

    /**
     * Resolves an existingEpicId the compiler echoed back against the real project's эпики - never trusts
     * the string blindly (a hallucinated or cross-project id must fall back to creating a new эпик, not
     * silently attach real tasks to the wrong one or throw).
     */
    public Optional<FeatureEntity> findExistingEpic(UUID projectId, String existingEpicIdRaw) {
        if (existingEpicIdRaw == null || existingEpicIdRaw.isBlank()) {
            return Optional.empty();
        }
        UUID id;
        try {
            id = UUID.fromString(existingEpicIdRaw.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return featureRepository.findById(id).filter(f -> projectId.equals(f.getProjectId()));
    }

    /**
     * Calculates the Quine-Gärdenfors Epistemic Entrenchment (EE) score in [0.0, 100.0].
     *
     * <p>The full E³ plan formula, restored 2026-08-27 (Phase 1 of ENGINEERING_PHILOSOPHY_ACTION_PLAN.md):
     * <pre>EE = 0.40·C(Cynefin) + 0.35·K(Kano) + 0.25·E_EMS</pre>
     * The version this replaces was {@code 0.55·C + 0.45·K} - the same two terms with the missing third
     * term's weight renormalized away (0.40/0.75 ≈ 0.53, 0.35/0.75 ≈ 0.47), which hid the omission behind
     * weights that still summed to 1.
     *
     * <p>Component scales are the plan's, not the previous code's: clear=90, complicated=65, complex=35,
     * chaotic=10; must-be=90, one-dimensional=60, attractive=30, indifferent=10.
     *
     * <p>Unclassified is its own value, not a middle one. A null, blank or unrecognized string on either
     * axis scores {@link #UNCLASSIFIED_COMPONENT}, below every named category, because a feature the
     * factory could not classify is not thereby a moderately-well-understood feature - it is one whose
     * position in the web of belief is unknown, and an unknown position is the weakest one. This is what
     * makes the preorder <=_EE non-trivial again: the old defaults (complex/one-dimensional) scored an
     * unclassified feature at 46.25, above the CONTRACT threshold, for every feature the main compile path
     * ever minted.
     */
    public double calculateEpistemicEntrenchment(String kanoClass, String cynefinDomain) {
        return calculateEpistemicEntrenchment(kanoClass, cynefinDomain, 0.0);
    }

    /** Score assigned to an axis that carries no usable classification - see the formula's javadoc. */
    public static final double UNCLASSIFIED_COMPONENT = 20.0;

    private static final double W_CYNEFIN = 0.40;
    private static final double W_KANO = 0.35;
    private static final double W_EMS = 0.25;

    public double calculateEpistemicEntrenchment(String kanoClass, String cynefinDomain, double emsContractScore) {
        double cynefinScore = switch (normalizeAxis(cynefinDomain)) {
            case "simple", "clear" -> 90.0;
            case "complicated" -> 65.0;
            case "complex" -> 35.0;
            case "chaotic" -> 10.0;
            default -> UNCLASSIFIED_COMPONENT;
        };

        double kanoScore = switch (normalizeAxis(kanoClass)) {
            case "must-be", "must_be" -> 90.0;
            case "one-dimensional", "one_dimensional" -> 60.0;
            case "attractive" -> 30.0;
            case "indifferent" -> 10.0;
            case "reverse" -> 0.0;
            default -> UNCLASSIFIED_COMPONENT;
        };

        double emsScore = Math.max(0.0, Math.min(100.0, emsContractScore));

        return Math.round((W_CYNEFIN * cynefinScore + W_KANO * kanoScore + W_EMS * emsScore) * 100.0) / 100.0;
    }

    private static String normalizeAxis(String raw) {
        return raw == null || raw.isBlank() ? "" : raw.toLowerCase(java.util.Locale.ROOT).trim();
    }

    /**
     * E_EMS in [0, 100] - how far this feature conforms to the runtime's base contract (the plan's
     * "Execution Boundary & Local Contract"), measured by which contract fields it actually carries:
     * what job it does (JTBD, 40), how its success is measured (Six Sigma metric, 30), and which
     * constraint it serves (TOC reference, 30).
     *
     * <p>A DECLARED DEFINITION, not a measurement. Nothing here establishes that a JTBD is worth exactly
     * 40 points of entrenchment; what is established is the ordering it produces - a feature carrying all
     * three contract fields is more entrenched than one carrying none - and the invariant that follows
     * from the weights: with E_EMS = 0, the highest reachable score is 0.40*90 + 0.35*90 = 67.5, so
     * <b>no feature can enter the CORE layer without a verified contract</b>, whatever its domain.
     */
    public double emsContractConformance(String jtbd, String sixSigmaMetric, String tocConstraintRef) {
        double score = 0.0;
        if (jtbd != null && !jtbd.isBlank()) {
            score += 40.0;
        }
        if (sixSigmaMetric != null && !sixSigmaMetric.isBlank()) {
            score += 30.0;
        }
        if (tocConstraintRef != null && !tocConstraintRef.isBlank()) {
            score += 30.0;
        }
        return score;
    }

    private static String firstUsable(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback != null && !fallback.isBlank() ? fallback : null;
    }

    public String classifyEpistemicLayer(double score) {
        if (score >= 75.0) return "CORE";
        if (score >= 45.0) return "CONTRACT";
        return "PERIPHERY";
    }

    /**
     * Mints a brand-new эпик with its own content (title/jtbd/Kano/Cynefin/business metrics) - unlike
     * {@link #resolveOrCreateFeatureId}, which only ever produces a bare grouping row for callers that
     * don't have (or don't need) эпик-level content, e.g. the recovery/cheap-compile path which reuses an
     * already-known featureId instead of classifying one from scratch.
     */
    @Transactional
    public FeatureEntity createFeature(UUID projectId, UUID rootWishlistId, String title, String jtbd,
            String kanoClass, String cynefinDomain, String sixSigmaMetric, String tocConstraintRef) {
        FeatureEntity feature = new FeatureEntity();
        feature.setProjectId(projectId);
        feature.setRootWishlistId(rootWishlistId);
        feature.setTitle(title);
        feature.setJtbd(jtbd);
        feature.setKanoClass(kanoClass);
        feature.setCynefinDomain(cynefinDomain);
        feature.setSixSigmaMetric(sixSigmaMetric);
        feature.setTocConstraintRef(tocConstraintRef);
        double score = calculateEpistemicEntrenchment(kanoClass, cynefinDomain,
                emsContractConformance(jtbd, sixSigmaMetric, tocConstraintRef));
        feature.setEpistemicScore(score);
        feature.setEpistemicLayer(classifyEpistemicLayer(score));
        feature = featureRepository.save(feature);
        feature.setOriginFeatureId(feature.getId());
        return featureRepository.save(feature);
    }
}
