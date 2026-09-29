package com.eneik.production.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Falsification test suite for {@link EpistemicMetadataClassifier} grounding Stage 4 invariants:
 *
 * <p>1. Floridi (D010, {@code LUCHANO_FLORIDI_04_LEVEL_OF_ABSTRACTION_LOCK}):
 * Honest null at the declared level of abstraction. The classifier returns strictly {@code null}
 * when no markers are matched, never fabricating a "middle" or default category (such as complex
 * or one-dimensional), preserving the Quine-Gärdenfors preorder. Both axes are nullable independently.
 *
 * <p>2. Gärdenfors (D007, {@code PITER_GERDENFORS_01_BELIEF_UPDATE_LEDGER}):
 * AGM belief revision and evidence ranking. On a tie in marker count, the classifier strictly selects
 * the more conservative (lower-certainty / most severe) domain declared earlier in the {@code LinkedHashMap},
 * preventing false certainty. Higher evidence hit counts rightfully override declared order.
 *
 * <p>3. Frege (D009, {@code GOTLOB_FREGE_01_SUBSTITUTION_ORACLE}):
 * Principle of sense and reference (Sinn und Bedeutung) & bilingual substitutivity salva veritate.
 * English and Russian markers for Cynefin domains and Kano classes preserve identical referents
 * across linguistic contexts.
 */
class EpistemicMetadataClassifierFalsificationTest {

    private EpistemicMetadataClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new EpistemicMetadataClassifier();
    }

    // =========================================================================
    // 1. Floridi (D010): LUCHANO_FLORIDI_04_LEVEL_OF_ABSTRACTION_LOCK
    // =========================================================================

    @Test
    @DisplayName("Floridi [D010]: missing evidence returns strict null, never guessing an arbitrary middle value")
    void falsifyFloridi_levelOfAbstractionLock_returnsStrictNullWithoutGuessingMidpoint() {
        // Empty, blank, or null inputs
        EpistemicMetadataClassifier.Classification empty = classifier.classify(null, "", "   ");
        assertThat(empty.cynefinDomain())
                .as("Cynefin domain must be strictly null when no evidence is present")
                .isNull();
        assertThat(empty.kanoClass())
                .as("Kano class must be strictly null when no evidence is present")
                .isNull();
        assertThat(empty.anythingExtracted()).isFalse();

        // Neutral text containing no known keywords
        EpistemicMetadataClassifier.Classification neutral =
                classifier.classify("the quick brown fox jumps over the lazy dog");
        assertThat(neutral.cynefinDomain()).isNull();
        assertThat(neutral.kanoClass()).isNull();
        assertThat(neutral.anythingExtracted()).isFalse();
    }

    @Test
    @DisplayName("Floridi [D010]: axes are independently nullable - pinning one does not fabricate the other")
    void falsifyFloridi_levelOfAbstractionLock_independentNullabilityOfAxes() {
        // Kano only: "auth" and "security"
        EpistemicMetadataClassifier.Classification kanoOnly =
                classifier.classify("Configure role-based access control and password encryption");
        assertThat(kanoOnly.kanoClass())
                .as("Kano class must extract must-be from security keywords")
                .isEqualTo("must-be");
        assertThat(kanoOnly.cynefinDomain())
                .as("Cynefin domain must remain strictly null when unreferenced")
                .isNull();
        assertThat(kanoOnly.anythingExtracted()).isTrue();

        // Cynefin only: "crud" and "button"
        EpistemicMetadataClassifier.Classification cynefinOnly =
                classifier.classify("Add simple listing page with button styling");
        assertThat(cynefinOnly.cynefinDomain())
                .as("Cynefin domain must extract clear from UI crud keywords")
                .isEqualTo("clear");
        assertThat(cynefinOnly.kanoClass())
                .as("Kano class must remain strictly null when unreferenced")
                .isNull();
        assertThat(cynefinOnly.anythingExtracted()).isTrue();
    }

    // =========================================================================
    // 2. Gärdenfors (D007): PITER_GERDENFORS_01_BELIEF_UPDATE_LEDGER
    // =========================================================================

    @Test
    @DisplayName("Gärdenfors [D007]: tie in marker count selects the more conservative / lower-certainty domain")
    void falsifyGardenfors_beliefUpdateLedger_tieBreakPicksConservativeSevereDomain() {
        // Tie between chaotic (outage) and clear (button): chaotic is declared first and wins
        EpistemicMetadataClassifier.Classification tieChaoticClear =
                classifier.classify("Unexpected outage on the checkout button");
        assertThat(tieChaoticClear.cynefinDomain())
                .as("Tie-break must select chaotic over clear to prevent false certainty in belief state")
                .isEqualTo("chaotic");

        // Tie between complex (prototype) and complicated (pipeline): complex wins
        EpistemicMetadataClassifier.Classification tieComplexComplicated =
                classifier.classify("Build prototype for the processing pipeline");
        assertThat(tieComplexComplicated.cynefinDomain())
                .as("Tie-break must select complex over complicated to avoid over-stating problem certainty")
                .isEqualTo("complex");

        // Tie between must-be (login) and attractive (gamification): must-be wins
        EpistemicMetadataClassifier.Classification tieMustBeAttractive =
                classifier.classify("User login with onboarding gamification");
        assertThat(tieMustBeAttractive.kanoClass())
                .as("Tie-break must select must-be over attractive")
                .isEqualTo("must-be");
    }

    @Test
    @DisplayName("Gärdenfors [D007]: higher evidence count legitimately overrides declared ordering")
    void falsifyGardenfors_beliefUpdateLedger_higherMarkerCountOverridesOrder() {
        // 2 complicated markers ("pipeline", "architecture", "scaling") vs 1 chaotic marker ("crash")
        EpistemicMetadataClassifier.Classification weighted =
                classifier.classify("Occasional crash during architecture pipeline scaling under concurrency");
        assertThat(weighted.cynefinDomain())
                .as("Dominant evidence count (3 complicated hits vs 1 chaotic) must update belief to complicated")
                .isEqualTo("complicated");

        // 2 one-dimensional markers ("latency", "throughput") vs 1 must-be marker ("security")
        EpistemicMetadataClassifier.Classification kanoWeighted =
                classifier.classify("Enhance system latency and throughput while maintaining security");
        assertThat(kanoWeighted.kanoClass())
                .as("Dominant evidence count (2 one-dimensional hits vs 1 must-be) must update belief to one-dimensional")
                .isEqualTo("one-dimensional");
    }

    // =========================================================================
    // 3. Frege (D009): GOTLOB_FREGE_01_SUBSTITUTION_ORACLE
    // =========================================================================

    @Test
    @DisplayName("Frege [D009]: bilingual equivalence preserves sense and referent across English and Russian")
    void falsifyFrege_substitutionOracle_bilingualEquivalencePreservesSenseAndReferent() {
        // Cynefin domains across EN and RU:
        // Chaotic: crash vs падает / авария
        assertThat(classifier.classify("critical failure with data loss").cynefinDomain()).isEqualTo("chaotic");
        assertThat(classifier.classify("критический сбой и потеря данных").cynefinDomain()).isEqualTo("chaotic");

        // Complex: machine learning experiment vs исследование и машинное обучение
        assertThat(classifier.classify("machine learning prototype").cynefinDomain()).isEqualTo("complex");
        assertThat(classifier.classify("прототип и машинное обучение").cynefinDomain()).isEqualTo("complex");

        // Complicated: api migration refactor vs миграция схемы и рефакторинг
        assertThat(classifier.classify("api migration and refactor").cynefinDomain()).isEqualTo("complicated");
        assertThat(classifier.classify("миграция схемы и рефакторинг").cynefinDomain()).isEqualTo("complicated");

        // Clear: crud form styling vs верстка формы и кнопка
        assertThat(classifier.classify("crud form with styling").cynefinDomain()).isEqualTo("clear");
        assertThat(classifier.classify("верстка формы и кнопка").cynefinDomain()).isEqualTo("clear");

        // Kano classes across EN and RU:
        // Must-be: validation error handling vs валидация и обработка ошибок
        assertThat(classifier.classify("validation and error handling").kanoClass()).isEqualTo("must-be");
        assertThat(classifier.classify("валидация и обработка ошибок").kanoClass()).isEqualTo("must-be");

        // One-dimensional: throughput search vs пропускная способность и поиск
        assertThat(classifier.classify("throughput and search filter").kanoClass()).isEqualTo("one-dimensional");
        assertThat(classifier.classify("пропускная способность и фильтр").kanoClass()).isEqualTo("one-dimensional");

        // Attractive: dark mode recommendation vs темная тема и рекомендации
        assertThat(classifier.classify("dark mode recommendation").kanoClass()).isEqualTo("attractive");
        assertThat(classifier.classify("тёмная тема и рекомендации").kanoClass()).isEqualTo("attractive");

        // Indifferent: housekeeping rename vs уборка и переименование
        assertThat(classifier.classify("housekeeping and rename").kanoClass()).isEqualTo("indifferent");
        assertThat(classifier.classify("уборка и переименование").kanoClass()).isEqualTo("indifferent");
    }
}
