package com.eneik.production.services;

import com.eneik.production.models.persistence.FeatureEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.WishlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
