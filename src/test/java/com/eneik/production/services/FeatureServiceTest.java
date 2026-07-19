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
        verify(featureRepository).save(any(FeatureEntity.class));
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
}
