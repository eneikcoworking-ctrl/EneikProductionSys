package com.eneik.production.services;

import com.eneik.production.models.persistence.FeatureEntity;
import com.eneik.production.models.persistence.LeanValue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SelfFalsificationEpicMatcherTest {

    private final SelfFalsificationEpicMatcher matcher = new SelfFalsificationEpicMatcher();

    private FeatureEntity epic(String title, String jtbd, String kano, String cynefin) {
        FeatureEntity feature = new FeatureEntity();
        feature.setId(UUID.randomUUID());
        feature.setTitle(title);
        feature.setJtbd(jtbd);
        feature.setKanoClass(kano);
        feature.setCynefinDomain(cynefin);
        return feature;
    }

    private MLPredictionServiceClient.EpicPlan candidate(String title, String jtbd, String kano, String cynefin) {
        return new MLPredictionServiceClient.EpicPlan(
                null, title, jtbd, kano, cynefin, "metric", "toc", 0,
                List.of(new MLPredictionServiceClient.TaskSliceMetadata(
                        "slice", "jtbd", "acceptance", "BARCAN-TAG-08", LeanValue.essential, "clear",
                        "toc", "metric", false))
        );
    }

    @Test
    void strongMatchAttaches() {
        FeatureEntity notesEpic = epic("Notes CRUD",
                "When a user manages notes, I want CRUD, so I can track information.", "Must-Be", "complicated");

        Optional<UUID> result = matcher.findLikelyExistingEpic(List.of(notesEpic),
                candidate("Notes CRUD", "When a user manages notes, I want CRUD, so I can track information.",
                        "Must-Be", "complicated"));

        assertThat(result).contains(notesEpic.getId());
    }

    @Test
    void emptyExistingListReturnsEmpty() {
        Optional<UUID> result = matcher.findLikelyExistingEpic(List.of(),
                candidate("Notes CRUD", "When a user manages notes, I want CRUD, so I can track information.",
                        "Must-Be", "complicated"));

        assertThat(result).isEmpty();
    }

    @Test
    void ambiguousTiedCandidatesReturnEmpty() {
        FeatureEntity first = epic("Notes CRUD",
                "When a user manages notes, I want CRUD, so I can track information.", "Must-Be", "complicated");
        FeatureEntity second = epic("Notes CRUD",
                "When a user manages notes, I want CRUD, so I can track information.", "Must-Be", "complicated");

        Optional<UUID> result = matcher.findLikelyExistingEpic(List.of(first, second),
                candidate("Notes CRUD", "When a user manages notes, I want CRUD, so I can track information.",
                        "Must-Be", "complicated"));

        assertThat(result).isEmpty();
    }

    @Test
    void nullOrBlankKanoAndCynefinDoNotCrash() {
        FeatureEntity noMeta = epic("Notes CRUD",
                "When a user manages notes, I want CRUD, so I can track information.", null, null);

        Optional<UUID> result = matcher.findLikelyExistingEpic(List.of(noMeta),
                candidate("Notes CRUD", "When a user manages notes, I want CRUD, so I can track information.",
                        null, null));

        assertThat(result).contains(noMeta.getId());
    }

    @Test
    void nullCandidateTitleAndJtbdDoNotThrow() {
        FeatureEntity notesEpic = epic("Notes CRUD",
                "When a user manages notes, I want CRUD, so I can track information.", "Must-Be", "complicated");

        Optional<UUID> result = matcher.findLikelyExistingEpic(List.of(notesEpic),
                candidate(null, null, "Must-Be", "complicated"));

        assertThat(result).isEmpty();
    }

    @Test
    void unrelatedContentDoesNotMatch() {
        FeatureEntity notesEpic = epic("Notes CRUD",
                "When a user manages notes, I want CRUD, so I can track information.", "Must-Be", "complicated");

        Optional<UUID> result = matcher.findLikelyExistingEpic(List.of(notesEpic),
                candidate("Billing Export",
                        "When an admin reconciles accounts, I want a CSV invoice export, so bookkeeping is faster.",
                        "Attractive", "complex"));

        assertThat(result).isEmpty();
    }
}
