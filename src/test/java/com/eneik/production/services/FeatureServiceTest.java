package com.eneik.production.services;

import com.eneik.production.models.persistence.FeatureEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.WishlistRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FeatureServiceTest {

    @Test
    void mintsNewFeatureWhenWishlistHasNone() {
        FeatureRepository featureRepository = mock(FeatureRepository.class);
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        FeatureService service = new FeatureService(featureRepository, wishlistRepository);

        UUID projectId = UUID.randomUUID();
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(UUID.randomUUID());

        UUID mintedId = UUID.randomUUID();
        when(featureRepository.save(any(FeatureEntity.class))).thenAnswer(invocation -> {
            FeatureEntity f = invocation.getArgument(0);
            f.setId(mintedId);
            return f;
        });

        UUID result = service.resolveOrCreateFeatureId(wishlist, projectId);

        assertEquals(mintedId, result);
        assertEquals(mintedId, wishlist.getFeatureId());
        // 2026-08-04 (3-layer model): save is now called twice - once to mint the id, once more to
        // stamp originFeatureId onto it (needs the id to exist first). See
        // stampsOriginFeatureIdOnceAtCreationAndNeverRewritesIt below for the lineage-specific assertion.
        verify(featureRepository, times(2)).save(any(FeatureEntity.class));
        verify(wishlistRepository).save(wishlist);
    }

    @Test
    void reusesAlreadySetFeatureIdWithoutMintingAgain() {
        FeatureRepository featureRepository = mock(FeatureRepository.class);
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        FeatureService service = new FeatureService(featureRepository, wishlistRepository);

        UUID existingFeatureId = UUID.randomUUID();
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(UUID.randomUUID());
        wishlist.setFeatureId(existingFeatureId);

        UUID result = service.resolveOrCreateFeatureId(wishlist, UUID.randomUUID());

        assertEquals(existingFeatureId, result);
        verify(featureRepository, never()).save(any());
        verify(wishlistRepository, never()).save(any());
    }

    @Test
    void stampsOriginFeatureIdOnceAtCreationAndNeverRewritesIt() {
        FeatureRepository featureRepository = mock(FeatureRepository.class);
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        FeatureService service = new FeatureService(featureRepository, wishlistRepository);

        UUID projectId = UUID.randomUUID();
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(UUID.randomUUID());

        UUID mintedId = UUID.randomUUID();
        when(featureRepository.save(any(FeatureEntity.class))).thenAnswer(invocation -> {
            FeatureEntity f = invocation.getArgument(0);
            if (f.getId() == null) {
                f.setId(mintedId);
            }
            return f;
        });

        service.resolveOrCreateFeatureId(wishlist, projectId);

        assertEquals(mintedId, wishlist.getOriginFeatureId());
        // A feature's own lineage always points to itself at creation - never left null, never a
        // different feature's id.
        verify(featureRepository, times(2)).save(any(FeatureEntity.class));
    }

    @Test
    void resolveOrCreateFeatureIdNeverTouchesAnAlreadySetOriginFeatureId() {
        FeatureRepository featureRepository = mock(FeatureRepository.class);
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        FeatureService service = new FeatureService(featureRepository, wishlistRepository);

        UUID existingFeatureId = UUID.randomUUID();
        UUID originalOriginId = UUID.randomUUID();
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(UUID.randomUUID());
        wishlist.setFeatureId(existingFeatureId);
        wishlist.setOriginFeatureId(originalOriginId);

        service.resolveOrCreateFeatureId(wishlist, UUID.randomUUID());

        // Reusing an already-resolved featureId (the early-return path) must never rewrite lineage,
        // even if it looks stale - originFeatureId is set exactly once, at first creation.
        assertEquals(originalOriginId, wishlist.getOriginFeatureId());
        verify(featureRepository, never()).save(any());
    }

    @Test
    void createFeatureStampsOriginFeatureIdToItsOwnMintedId() {
        FeatureRepository featureRepository = mock(FeatureRepository.class);
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        FeatureService service = new FeatureService(featureRepository, wishlistRepository);

        UUID mintedId = UUID.randomUUID();
        when(featureRepository.save(any(FeatureEntity.class))).thenAnswer(invocation -> {
            FeatureEntity f = invocation.getArgument(0);
            if (f.getId() == null) {
                f.setId(mintedId);
            }
            return f;
        });

        FeatureEntity result = service.createFeature(UUID.randomUUID(), UUID.randomUUID(),
                "title", "jtbd", "must-be", "clear", "metric", "constraint");

        assertEquals(mintedId, result.getId());
        assertEquals(mintedId, result.getOriginFeatureId());
    }
}
