package com.eneik.production.services;

import com.eneik.production.models.persistence.FeatureEntity;
import com.eneik.production.models.persistence.LeanValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Falsification test suite for {@link SelfFalsificationEpicMatcher} grounding Stage 4 invariants:
 *
 * <p>1. Quine (D003, {@code UILLARD_KUAYN_01_HOLISM_IMPACT_MAP}):
 * Architectural holism and web-of-belief prior. Self-falsification audits already-shipped code,
 * so its prior favors attaching to existing epics over inventing duplicate structures whenever
 * an existing epic accounts for the observation ({@code score >= ATTACH_THRESHOLD} 0.42).
 *
 * <p>2. Frege (D009, {@code GOTLOB_FREGE_01_SUBSTITUTION_ORACLE}):
 * Sense, reference and substitutivity salva veritate in JTBD tokenization. Stopwords and punctuation
 * noise are stripped so that semantic core terms preserve referent identity. Cynefin (0.15) and
 * Kano (0.07) bonuses deterministically reinforce semantic identity across linguistic variations.
 *
 * <p>3. Quine (D005, {@code UILLARD_KUAYN_07_DECISION_EXPECTED_LOSS}):
 * Expected loss gate and ambiguity refusal. If the scoring gap between the top candidate and
 * second-best candidate is below {@code AMBIGUITY_GAP} (0.08), the matcher strictly returns
 * {@link Optional#empty()} to avoid erroneous attachment and misattribution of epic scope.
 */
class SelfFalsificationEpicMatcherFalsificationTest {

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

    // =========================================================================
    // 1. Quine (D003): UILLARD_KUAYN_01_HOLISM_IMPACT_MAP
    // =========================================================================

    @Test
    @DisplayName("Quine [D003]: holism impact map attaches observation to existing web of belief above threshold")
    void falsifyQuine_holismImpactMap_attachesToExistingWebWhenThresholdMet() {
        FeatureEntity userProfile = epic("User Profile Management",
                "When a registered user views settings, I want avatar upload and bio edit, so identity is updated.",
                "Performance", "clear");

        // Falsification finding discovering avatar resize defect
        MLPredictionServiceClient.EpicPlan avatarDefect = candidate("Avatar Upload Resize",
                "When an active user uploads avatar, I want image resize and thumbnail bio sync, so settings look right.",
                "Performance", "clear");

        Optional<UUID> attached = matcher.findLikelyExistingEpic(List.of(userProfile), avatarDefect);

        assertThat(attached)
                .as("Web-of-belief prior must attach related finding to existing profile epic rather than duplicating")
                .contains(userProfile.getId());
    }

    @Test
    @DisplayName("Quine [D003]: completely novel functionality falls below threshold and is not attached")
    void falsifyQuine_holismImpactMap_unrelatedObservationIsNotAttached() {
        FeatureEntity billingEpic = epic("Billing Invoicing",
                "When an accountant runs month-end, I want PDF invoice export, so bookkeeping is reconciled.",
                "Must-Be", "complicated");

        MLPredictionServiceClient.EpicPlan mlRecommendation = candidate("Product Recommendation Engine",
                "When a shopper browses catalog, I want neural collaborative filtering, so discovery improves.",
                "Attractive", "complex");

        Optional<UUID> attached = matcher.findLikelyExistingEpic(List.of(billingEpic), mlRecommendation);

        assertThat(attached)
                .as("Genuinely novel capability below threshold (0.42) must return empty to permit new epic creation")
                .isEmpty();
    }

    // =========================================================================
    // 2. Frege (D009): GOTLOB_FREGE_01_SUBSTITUTION_ORACLE
    // =========================================================================

    @Test
    @DisplayName("Frege [D009]: stopword and syntactic substitution preserves JTBD referent identity")
    void falsifyFrege_substitutionOracle_stopwordStrippingPreservesReferent() {
        FeatureEntity authEpic = epic("OAuth2 Authentication",
                "When a user signs in, I want OAuth2 Google login, so that access is granted securely.",
                "Must-Be", "complicated");

        // Candidate with different grammatical structure and stopword insertions
        MLPredictionServiceClient.EpicPlan finding = candidate("Google OAuth2 Authentication",
                "For the purpose of secure access, the user wants to sign in with OAuth2 Google login on the web.",
                "Must-Be", "complicated");

        Optional<UUID> match = matcher.findLikelyExistingEpic(List.of(authEpic), finding);

        assertThat(match)
                .as("Substitutivity salva veritate: semantic core tokens must match despite syntactic phrasing differences")
                .contains(authEpic.getId());
    }

    @Test
    @DisplayName("Frege [D009]: Cynefin and Kano bonuses deterministically discriminate between candidates")
    void falsifyFrege_substitutionOracle_domainAndKanoBonusesElevateMatchingEntity() {
        // Two candidate epics with identical text overlap with finding
        FeatureEntity epicMismatch = epic("Data Export Service",
                "When an analyst requests report, I want CSV export, so numbers are verified.",
                "Attractive", "complex");

        FeatureEntity epicMatch = epic("Data Export Service",
                "When an analyst requests report, I want CSV export, so numbers are verified.",
                "Must-Be", "complicated");

        // Finding shares Must-Be and complicated
        MLPredictionServiceClient.EpicPlan finding = candidate("Report Export Utility",
                "When an analyst downloads report, I want CSV data export pipeline, so audit is verified.",
                "Must-Be", "complicated");

        Optional<UUID> match = matcher.findLikelyExistingEpic(List.of(epicMismatch, epicMatch), finding);

        assertThat(match)
                .as("Cynefin (0.15) and Kano (0.07) bonuses must decisively elevate the concordant epic")
                .contains(epicMatch.getId());
    }

    // =========================================================================
    // 3. Quine (D005): UILLARD_KUAYN_07_DECISION_EXPECTED_LOSS
    // =========================================================================

    @Test
    @DisplayName("Quine [D005]: ambiguity gap (<0.08) strictly refuses attachment to prevent misattribution")
    void falsifyQuine_decisionExpectedLoss_ambiguityGapRefusesAttachment() {
        // Two sibling epics in the same domain with very close text overlap
        FeatureEntity notificationsEmail = epic("User Notifications Email",
                "When an alert triggers, I want email notification dispatch, so users are informed.",
                "Performance", "clear");

        FeatureEntity notificationsSms = epic("User Notifications SMS",
                "When an alert triggers, I want SMS notification dispatch, so users are informed.",
                "Performance", "clear");

        // Finding mentions generic notifications without clear tilt
        MLPredictionServiceClient.EpicPlan finding = candidate("User Notifications Dispatch",
                "When an alert triggers, I want notification dispatch, so users are informed.",
                "Performance", "clear");

        Optional<UUID> match = matcher.findLikelyExistingEpic(List.of(notificationsEmail, notificationsSms), finding);

        assertThat(match)
                .as("When top two candidates are within AMBIGUITY_GAP (0.08), matcher must refuse attach (minimize expected loss)")
                .isEmpty();
    }

    @Test
    @DisplayName("Quine [D005]: decisive lead exceeding ambiguity gap allows confident attachment")
    void falsifyQuine_decisionExpectedLoss_decisiveLeadAttachesConfidently() {
        FeatureEntity notificationsEmail = epic("User Notifications Email Delivery",
                "When an alert triggers, I want email notification delivery via SMTP template, so users are informed.",
                "Performance", "clear");

        FeatureEntity billingInvoice = epic("Invoice Billing Dispatch",
                "When a payment completes, I want invoice dispatch via email, so receipts are sent.",
                "Must-Be", "complicated");

        MLPredictionServiceClient.EpicPlan finding = candidate("Email Template Delivery",
                "When an alert triggers, I want SMTP template email notification delivery, so users are informed.",
                "Performance", "clear");

        Optional<UUID> match = matcher.findLikelyExistingEpic(List.of(notificationsEmail, billingInvoice), finding);

        assertThat(match)
                .as("Clear leader exceeding AMBIGUITY_GAP must attach confidently")
                .contains(notificationsEmail.getId());
    }
}
