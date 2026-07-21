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

    private final FeatureRepository featureRepository;
    private final WishlistRepository wishlistRepository;

    public FeatureService(FeatureRepository featureRepository, WishlistRepository wishlistRepository) {
        this.featureRepository = featureRepository;
        this.wishlistRepository = wishlistRepository;
    }

    @Transactional
    public UUID resolveOrCreateFeatureId(WishlistEntity wishlist, UUID projectId) {
        if (wishlist.getFeatureId() != null) {
            return wishlist.getFeatureId();
        }
        FeatureEntity feature = new FeatureEntity();
        feature.setProjectId(projectId);
        feature.setRootWishlistId(wishlist.getId());
        feature = featureRepository.save(feature);
        wishlist.setFeatureId(feature.getId());
        wishlistRepository.save(wishlist);
        return feature.getId();
    }

    /**
     * Ф8 (2026-07-21, operator directive): every compile cycle must decide, per эпик, whether it matches
     * an already-existing эпик in the project or is genuinely new - this list is what gets handed to the
     * compiler prompt as the candidate set to semantically match against (id/title/jtbd only - enough for
     * the compiler to judge a narrative match without dumping every task inside each эпик into the prompt).
     */
    public List<FeatureEntity> listExistingEpics(UUID projectId) {
        return featureRepository.findByProjectId(projectId);
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
        return featureRepository.save(feature);
    }
}
