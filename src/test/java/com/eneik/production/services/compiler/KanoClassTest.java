package com.eneik.production.services.compiler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rule under test is the one that was written twice and disagreed with itself.
 *
 * On 2026-08-14 one parser stopped defaulting an absent class to "Must-Be" and recorded that "the two sides
 * of the flow now hold the same discipline". The live parser still defaulted, which is why all five epics of
 * test-forty-sixth came out Must-Be AFTER that fix. These tests pin the behaviour in the one place both
 * parsers now call.
 */
class KanoClassTest {

    @Test
    void anAbsentClassIsNeverInventedAsMustBe() {
        assertThat(KanoClass.normalize(null))
                .as("the whole failure mode is that a missing classification gets spelled like a made one")
                .isEqualTo(KanoClass.UNCLASSIFIED);
        assertThat(KanoClass.normalize("")).isEqualTo(KanoClass.UNCLASSIFIED);
        assertThat(KanoClass.normalize("   ")).isEqualTo(KanoClass.UNCLASSIFIED);
    }

    @Test
    void aValueOutsideTheVocabularyIsNotGuessedAtEither() {
        assertThat(KanoClass.normalize("Basic"))
                .as("'Basic' is a real Kano synonym for Must-Be in the literature, and mapping it would be "
                        + "exactly the re-inference this class exists to refuse - the compiler was given "
                        + "four words and did not use them")
                .isEqualTo(KanoClass.UNCLASSIFIED);
        assertThat(KanoClass.normalize("Indifferent"))
                .as("Indifferent belongs to the critique vocabulary; no epic is planned as one")
                .isEqualTo(KanoClass.UNCLASSIFIED);
    }

    @Test
    void spellingAndCaseAreNormalisedButMeaningIsNot() {
        assertThat(KanoClass.normalize("must-be")).isEqualTo("Must-Be");
        assertThat(KanoClass.normalize("  PERFORMANCE ")).isEqualTo("Performance");
        assertThat(KanoClass.normalize("Attractive")).isEqualTo("Attractive");
        assertThat(KanoClass.normalize("reverse")).isEqualTo("Reverse");
    }

    @Test
    void theMarkerIsNeitherAValidClassNorBlank() {
        assertThat(KanoClass.valid())
                .as("a marker that is also a valid class is not a marker")
                .doesNotContain(KanoClass.UNCLASSIFIED);
        assertThat(KanoClass.UNCLASSIFIED)
                .as("blank was the old spelling and is indistinguishable from a column nobody wrote to")
                .isNotBlank();
    }

    @Test
    void bothOldSpellingsOfAbsenceStillReadAsAbsent() {
        assertThat(KanoClass.isUnclassified(""))
                .as("rows written before 2026-08-16 carry blank; they must not start reading as classified")
                .isTrue();
        assertThat(KanoClass.isUnclassified(null)).isTrue();
        assertThat(KanoClass.isUnclassified(KanoClass.UNCLASSIFIED)).isTrue();
        assertThat(KanoClass.isUnclassified("Must-Be")).isFalse();
    }
}
