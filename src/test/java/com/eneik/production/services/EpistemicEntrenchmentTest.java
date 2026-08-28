package com.eneik.production.services;

import com.eneik.production.models.persistence.FeatureEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.WishlistRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 1 of ENGINEERING_PHILOSOPHY_ACTION_PLAN.md: the epistemic entrenchment formula and the end of its
 * constant-46.25 degeneracy. What each test pins is the property that made the defect possible, not the
 * arithmetic - the arithmetic is only pinned where a specific threshold decides a layer.
 */
class EpistemicEntrenchmentTest {

    private FeatureService serviceWith(FeatureRepository featureRepository, WishlistRepository wishlistRepository) {
        return new FeatureService(featureRepository, wishlistRepository, null, new EpistemicMetadataClassifier());
    }

    @Test
    void formulaIsTheThreeComponentPlanFormula() {
        FeatureService service = serviceWith(mock(FeatureRepository.class), mock(WishlistRepository.class));

        // 0.40*90 + 0.35*90 + 0.25*100 = 36 + 31.5 + 25
        assertEquals(92.5, service.calculateEpistemicEntrenchment("must-be", "clear", 100.0));
        // 0.40*65 + 0.35*60 + 0.25*40 = 26 + 21 + 10
        assertEquals(57.0, service.calculateEpistemicEntrenchment("one-dimensional", "complicated", 40.0));
        // 0.40*10 + 0.35*30 + 0 = 4 + 10.5
        assertEquals(14.5, service.calculateEpistemicEntrenchment("attractive", "chaotic", 0.0));
    }

    @Test
    void emsTermActuallyMovesTheScore() {
        FeatureService service = serviceWith(mock(FeatureRepository.class), mock(WishlistRepository.class));
        double withoutContract = service.calculateEpistemicEntrenchment("must-be", "clear", 0.0);
        double withContract = service.calculateEpistemicEntrenchment("must-be", "clear", 100.0);
        // The defect being pinned: the old formula had no third term at all, so these two were equal.
        assertNotEquals(withoutContract, withContract);
        assertEquals(25.0, withContract - withoutContract);
    }

    @Test
    void coreLayerIsUnreachableWithoutAVerifiedContract() {
        FeatureService service = serviceWith(mock(FeatureRepository.class), mock(WishlistRepository.class));
        // Best possible classification on both axes, zero contract fields: 0.40*90 + 0.35*90 = 67.5 < 75.
        double best = service.calculateEpistemicEntrenchment("must-be", "clear", 0.0);
        assertEquals("CONTRACT", service.classifyEpistemicLayer(best));
        assertTrue(best < 75.0, "no contract must make CORE unreachable, got " + best);
    }

    @Test
    void unclassifiedFeatureLandsInPeripheryWhereTheInvariantGateCanSeeIt() {
        FeatureService service = serviceWith(mock(FeatureRepository.class), mock(WishlistRepository.class));
        double score = service.calculateEpistemicEntrenchment(null, null);
        assertEquals("PERIPHERY", service.classifyEpistemicLayer(score));
        // The exact regression: nulls used to produce 46.25, one and a quarter points above CONTRACT, for
        // every feature the main compile path minted - so EpistemicLayerInvariantGate never once ran.
        assertNotEquals(46.25, score);
    }

    @Test
    void unrecognizedAxisValuesAreTreatedAsUnclassifiedNotAsMiddling() {
        FeatureService service = serviceWith(mock(FeatureRepository.class), mock(WishlistRepository.class));
        assertEquals(service.calculateEpistemicEntrenchment(null, null),
                service.calculateEpistemicEntrenchment("qwerty", "no-such-domain"));
    }

    @Test
    void emsContractConformanceCountsTheContractFieldsItActuallyHas() {
        FeatureService service = serviceWith(mock(FeatureRepository.class), mock(WishlistRepository.class));
        assertEquals(0.0, service.emsContractConformance(null, null, null));
        assertEquals(0.0, service.emsContractConformance("  ", "", null));
        assertEquals(40.0, service.emsContractConformance("as a user I want X", null, null));
        assertEquals(100.0, service.emsContractConformance("jtbd", "defects per opportunity", "bottleneck-3"));
    }

    @Test
    void mintedFeatureReadsTheWishlistsOwnClassificationInsteadOfPassingNulls() {
        FeatureRepository featureRepository = mock(FeatureRepository.class);
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        when(featureRepository.save(any(FeatureEntity.class))).thenAnswer(inv -> {
            FeatureEntity f = inv.getArgument(0);
            if (f.getId() == null) {
                f.setId(UUID.randomUUID());
            }
            return f;
        });
        FeatureService service = serviceWith(featureRepository, wishlistRepository);

        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(UUID.randomUUID());
        wishlist.setContent("Add password reset and two-factor login to the account area");
        wishlist.setCynefinDomain("complicated");
        wishlist.setJtbd("As a user I want to recover access without support");
        wishlist.setSixSigmaMetric("failed logins per 1000 sessions");
        wishlist.setTocConstraintRef("auth-bottleneck");

        service.resolveOrCreateFeatureId(wishlist, UUID.randomUUID());

        ArgumentCaptor<FeatureEntity> saved = ArgumentCaptor.forClass(FeatureEntity.class);
        verify(featureRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        FeatureEntity feature = saved.getAllValues().get(0);

        assertEquals("complicated", feature.getCynefinDomain());
        assertEquals("must-be", feature.getKanoClass(), "login/password text must classify as must-be");
        // 0.40*65 + 0.35*90 + 0.25*100 = 26 + 31.5 + 25
        assertEquals(82.5, feature.getEpistemicScore().doubleValue());
        assertEquals("CORE", feature.getEpistemicLayer());
    }

    @Test
    void mintedFeaturesNoLongerAllScoreTheSameNumber() {
        FeatureRepository featureRepository = mock(FeatureRepository.class);
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        when(featureRepository.save(any(FeatureEntity.class))).thenAnswer(inv -> {
            FeatureEntity f = inv.getArgument(0);
            if (f.getId() == null) {
                f.setId(UUID.randomUUID());
            }
            return f;
        });
        FeatureService service = serviceWith(featureRepository, wishlistRepository);

        String[] contents = {
                "Add password reset and two-factor login",
                "Speed up the search filter on the report page",
                "Prototype a recommender to explore what users might want",
                "Fix the outage: the service crashes on startup",
                "я хочу сделать сайт в грузии для аренды и продажи бу машин",
        };
        Set<Double> scores = new HashSet<>();
        for (String content : contents) {
            WishlistEntity wishlist = new WishlistEntity();
            wishlist.setId(UUID.randomUUID());
            wishlist.setContent(content);
            service.resolveOrCreateFeatureId(wishlist, UUID.randomUUID());
        }
        ArgumentCaptor<FeatureEntity> saved = ArgumentCaptor.forClass(FeatureEntity.class);
        verify(featureRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        saved.getAllValues().forEach(f -> scores.add(f.getEpistemicScore()));

        // The preorder <=_EE is only a preorder if it distinguishes anything at all. One constant score
        // across five unrelated wishes is exactly the degeneracy this phase removes.
        assertTrue(scores.size() > 1, "expected a non-degenerate spread of scores, got " + scores);
    }

    @Test
    void aWishWithNoExtractableMetadataStaysInPeriphery() {
        FeatureRepository featureRepository = mock(FeatureRepository.class);
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        when(featureRepository.save(any(FeatureEntity.class))).thenAnswer(inv -> {
            FeatureEntity f = inv.getArgument(0);
            if (f.getId() == null) {
                f.setId(UUID.randomUUID());
            }
            return f;
        });
        FeatureService service = serviceWith(featureRepository, wishlistRepository);

        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(UUID.randomUUID());
        wishlist.setContent("something something");

        service.resolveOrCreateFeatureId(wishlist, UUID.randomUUID());

        ArgumentCaptor<FeatureEntity> saved = ArgumentCaptor.forClass(FeatureEntity.class);
        verify(featureRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertEquals("PERIPHERY", saved.getAllValues().get(0).getEpistemicLayer());
    }
}
