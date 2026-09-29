package com.eneik.production.services.compiler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Falsification test suite for {@link KanoClass} grounding Stage 4 invariants:
 *
 * <p>1. Wittgenstein (D001, {@code LYUDVIG_VITGENSHTEYN_05_ANCHOR_BOUND_NAME}):
 * Anchor-bound name & single source of canonical vocabulary. The epic-level Kano vocabulary
 * and the reading rule exist in exactly ONE place ({@link KanoClass}), removing conflicting duplicated
 * parsers between {@code JulesDispatchService} and {@code ProjectFlowService}. Every caller shares
 * the exact same anchor and referent. The valid vocabulary contains exactly 4 canonical classes.
 *
 * <p>2. Kripke (D001, {@code SOL_KRIPKE_01_RIGID_DESIGNATOR}):
 * Rigid designation across all parser worlds & synonym refusal. The four canonical classes rigidly
 * designate their exact canonical spelling regardless of casing or whitespace variations.
 * External literature synonyms ("Basic", "Threshold", "Exciter", "One-Dimensional") or critique
 * categories ("Indifferent", "Questionable") rigidly refuse to map to affirmative classes and
 * evaluate strictly to {@link KanoClass#UNCLASSIFIED}.
 *
 * <p>3. Varzi (D002, {@code AHILLE_VARTSI_04_ESSENCE_BEFORE_OPTION}):
 * Essence before option & unclassified invariant. An absent, null, empty, or blank classification
 * is never invented as "Must-Be" (the historical live defect of test-forty-sixth). An unclassified entity
 * rigidly normalizes to "Unclassified", which is neither blank nor one of the four valid classes,
 * preserving ontological truth and remaining visible to operators.
 */
class KanoClassFalsificationTest {

    // =========================================================================
    // 1. Wittgenstein (D001): LYUDVIG_VITGENSHTEYN_05_ANCHOR_BOUND_NAME
    // =========================================================================

    @Test
    @DisplayName("Wittgenstein [D001]: anchor-bound vocabulary contains exactly the four canonical classes")
    void falsifyWittgenstein_anchorBoundName_validListContainsExactlyFourDesignatedClasses() {
        List<String> validClasses = KanoClass.valid();

        assertThat(validClasses)
                .as("KanoClass.valid() must provide exactly the 4 epic-level canonical classes")
                .containsExactlyInAnyOrder("Must-Be", "Performance", "Attractive", "Reverse");

        assertThat(validClasses)
                .as("The marker UNCLASSIFIED must never be included in the valid class vocabulary")
                .doesNotContain(KanoClass.UNCLASSIFIED);

        assertThat(KanoClass.UNCLASSIFIED)
                .as("The UNCLASSIFIED marker must be an explicit, non-blank token")
                .isEqualTo("Unclassified")
                .isNotBlank();
    }

    @Test
    @DisplayName("Wittgenstein [D001]: single canonical source ensures consistent referents across all callers")
    void falsifyWittgenstein_anchorBoundName_singleCanonicalSourceAcrossCallers() {
        // Any caller normalizing raw strings obtains identical canonical objects
        for (String validName : KanoClass.valid()) {
            String normalizedUpper = KanoClass.normalize(validName.toUpperCase());
            String normalizedLower = KanoClass.normalize(validName.toLowerCase());
            String normalizedTrimmed = KanoClass.normalize("  " + validName + "  ");

            assertThat(normalizedUpper)
                    .as("Upper-case version of %s must resolve to the identical canonical referent", validName)
                    .isEqualTo(validName);
            assertThat(normalizedLower)
                    .as("Lower-case version of %s must resolve to the identical canonical referent", validName)
                    .isEqualTo(validName);
            assertThat(normalizedTrimmed)
                    .as("Padded version of %s must resolve to the identical canonical referent", validName)
                    .isEqualTo(validName);
        }
    }

    // =========================================================================
    // 2. Kripke (D001): SOL_KRIPKE_01_RIGID_DESIGNATOR
    // =========================================================================

    @Test
    @DisplayName("Kripke [D001]: rigid designation maps canonical classes across arbitrary casing and whitespace")
    void falsifyKripke_rigidDesignator_caseInsensitiveAndWhitespaceRigidMapping() {
        // Must-Be rigid designation
        assertThat(KanoClass.normalize("must-be")).isEqualTo("Must-Be");
        assertThat(KanoClass.normalize("MUST-BE")).isEqualTo("Must-Be");
        assertThat(KanoClass.normalize("  Must-Be  ")).isEqualTo("Must-Be");
        assertThat(KanoClass.normalize("\tmust-be\n")).isEqualTo("Must-Be");

        // Performance rigid designation
        assertThat(KanoClass.normalize("performance")).isEqualTo("Performance");
        assertThat(KanoClass.normalize("PERFORMANCE")).isEqualTo("Performance");
        assertThat(KanoClass.normalize("  Performance  ")).isEqualTo("Performance");

        // Attractive rigid designation
        assertThat(KanoClass.normalize("attractive")).isEqualTo("Attractive");
        assertThat(KanoClass.normalize("ATTRACTIVE")).isEqualTo("Attractive");
        assertThat(KanoClass.normalize("  Attractive  ")).isEqualTo("Attractive");

        // Reverse rigid designation
        assertThat(KanoClass.normalize("reverse")).isEqualTo("Reverse");
        assertThat(KanoClass.normalize("REVERSE")).isEqualTo("Reverse");
        assertThat(KanoClass.normalize("  Reverse  ")).isEqualTo("Reverse");
    }

    @Test
    @DisplayName("Kripke [D001]: rigid refusal to guess literature synonyms or critique categories")
    void falsifyKripke_rigidDesignator_refusesLiteratureSynonymsAndCritiqueCategories() {
        // Common literature synonyms for Must-Be
        assertThat(KanoClass.normalize("Basic"))
                .as("'Basic' is a literature synonym for Must-Be; guessing it would break rigid designation")
                .isEqualTo(KanoClass.UNCLASSIFIED);
        assertThat(KanoClass.normalize("Expected"))
                .as("'Expected' must not map to Must-Be")
                .isEqualTo(KanoClass.UNCLASSIFIED);
        assertThat(KanoClass.normalize("Threshold"))
                .as("'Threshold' must not map to Must-Be")
                .isEqualTo(KanoClass.UNCLASSIFIED);

        // Common literature synonyms for Performance and Attractive
        assertThat(KanoClass.normalize("One-Dimensional"))
                .as("'One-Dimensional' is an academic synonym for Performance and must not map")
                .isEqualTo(KanoClass.UNCLASSIFIED);
        assertThat(KanoClass.normalize("Linear"))
                .as("'Linear' must not map to Performance")
                .isEqualTo(KanoClass.UNCLASSIFIED);
        assertThat(KanoClass.normalize("Exciter"))
                .as("'Exciter' is an academic synonym for Attractive and must not map")
                .isEqualTo(KanoClass.UNCLASSIFIED);
        assertThat(KanoClass.normalize("Delighter"))
                .as("'Delighter' must not map to Attractive")
                .isEqualTo(KanoClass.UNCLASSIFIED);

        // Critique vocabulary
        assertThat(KanoClass.normalize("Indifferent"))
                .as("'Indifferent' belongs to critique vocabulary; no epic is planned as one")
                .isEqualTo(KanoClass.UNCLASSIFIED);
        assertThat(KanoClass.normalize("Questionable"))
                .as("'Questionable' belongs to critique vocabulary and must not map")
                .isEqualTo(KanoClass.UNCLASSIFIED);

        // Random or malformed inputs
        assertThat(KanoClass.normalize("unknown")).isEqualTo(KanoClass.UNCLASSIFIED);
        assertThat(KanoClass.normalize("random-string-123")).isEqualTo(KanoClass.UNCLASSIFIED);
    }

    // =========================================================================
    // 3. Varzi (D002): AHILLE_VARTSI_04_ESSENCE_BEFORE_OPTION
    // =========================================================================

    @Test
    @DisplayName("Varzi [D002]: essence before option - unclassified entity is never invented as Must-Be")
    void falsifyVarzi_essenceBeforeOption_unclassifiedNeverInventedAsMustBe() {
        // An absent classification was historically defaulted to "Must-Be"
        assertThat(KanoClass.normalize(null))
                .as("Null must never default to Must-Be")
                .isNotEqualTo("Must-Be")
                .isEqualTo(KanoClass.UNCLASSIFIED);

        assertThat(KanoClass.normalize(""))
                .as("Empty string must never default to Must-Be")
                .isNotEqualTo("Must-Be")
                .isEqualTo(KanoClass.UNCLASSIFIED);

        assertThat(KanoClass.normalize("   "))
                .as("Whitespace string must never default to Must-Be")
                .isNotEqualTo("Must-Be")
                .isEqualTo(KanoClass.UNCLASSIFIED);
    }

    @Test
    @DisplayName("Varzi [D002]: recognizes all historical and current absence representations")
    void falsifyVarzi_essenceBeforeOption_recognizesAllAbsenceRepresentations() {
        // Legacy absence: null, empty string, blank whitespace
        assertThat(KanoClass.isUnclassified(null)).isTrue();
        assertThat(KanoClass.isUnclassified("")).isTrue();
        assertThat(KanoClass.isUnclassified("   ")).isTrue();
        assertThat(KanoClass.isUnclassified("\t\n")).isTrue();

        // Explicit canonical marker
        assertThat(KanoClass.isUnclassified(KanoClass.UNCLASSIFIED)).isTrue();
        assertThat(KanoClass.isUnclassified("Unclassified")).isTrue();
        assertThat(KanoClass.isUnclassified("unclassified")).isTrue();
        assertThat(KanoClass.isUnclassified("  UNCLASSIFIED  ")).isTrue();

        // Valid canonical classes must return false
        for (String validClass : KanoClass.valid()) {
            assertThat(KanoClass.isUnclassified(validClass))
                    .as("Valid class '%s' must not be evaluated as unclassified", validClass)
                    .isFalse();
        }
    }
}
