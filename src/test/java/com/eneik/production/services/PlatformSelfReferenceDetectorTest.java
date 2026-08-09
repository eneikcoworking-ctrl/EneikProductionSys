package com.eneik.production.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformSelfReferenceDetectorTest {

    @Test
    void nullOrBlankTextIsNeverAPlatformFinding() {
        assertFalse(PlatformSelfReferenceDetector.looksLikePlatformFinding(null));
        assertFalse(PlatformSelfReferenceDetector.looksLikePlatformFinding(""));
        assertFalse(PlatformSelfReferenceDetector.looksLikePlatformFinding("   "));
    }

    @Test
    void existingVocabularyMatchStillWorks() {
        assertTrue(PlatformSelfReferenceDetector.looksLikePlatformFinding(
                "fix the state transition bug, so that the pipeline queue resumes processing"));
    }

    @Test
    void aGenuineClientProductFindingIsNotFlagged() {
        assertFalse(PlatformSelfReferenceDetector.looksLikePlatformFinding(
                "Checkout page throws a 500 when the cart is empty and the user clicks 'Pay now'."));
    }

    @Test
    void thePatientZeroLogQuoteIsNowCaughtByStructureNotVocabulary() {
        String evidence = "2026-08-07T20:00:04.156Z [WARN] reconcileTaskStatusAgainstGitHubTruth: "
                + "task 0bcb9d29-... is marked done but PR#53 closed without merge";

        assertTrue(PlatformSelfReferenceDetector.looksLikeInternalLogLine(evidence),
                "the exact patient-zero phrase must be recognized as log-shaped");
        assertTrue(PlatformSelfReferenceDetector.looksLikePlatformFinding(evidence),
                "the exact patient-zero phrase must now be classified as a platform finding");
    }

    @Test
    void scopedBufferAppenderFormatIsAlsoCaught() {
        String evidence = "2026-08-09T11:36:14.399123Z WARN com.eneik.production.services.jules.JulesDispatchService "
                + "- reconcileTaskStatusAgainstGitHubTruth: task abc is marked done but PR#12 closed without merge";

        assertTrue(PlatformSelfReferenceDetector.looksLikeInternalLogLine(evidence));
    }

    @Test
    void aBareTimestampWithNoLevelMarkerIsNotFlaggedAsLogShaped() {
        assertFalse(PlatformSelfReferenceDetector.looksLikeInternalLogLine(
                "The invoice was generated at 2026-08-07T20:00:04.156Z and emailed to the customer."));
    }

    @Test
    void aBareLevelWordWithNoTimestampIsNotFlaggedAsLogShaped() {
        assertFalse(PlatformSelfReferenceDetector.looksLikeInternalLogLine(
                "Users reported a WARN-level anxiety about the pricing page, not an actual error."));
    }
}
