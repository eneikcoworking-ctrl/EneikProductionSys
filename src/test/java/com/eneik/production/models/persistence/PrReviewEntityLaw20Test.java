package com.eneik.production.models.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests enforcing Law 20 / Invariant S2 for PrReviewEntity:
 * terminal(review) ⟹ ciStatus(review) cannot be overwritten.
 *
 * Specifically, closed_unmerged is terminal by definition (a PR closed without
 * merging cannot become merged) and must never be overwritten (e.g. by superseded).
 */
public class PrReviewEntityLaw20Test {

    @Test
    @DisplayName("isTerminal() correctly identifies terminal review outcomes")
    void testIsTerminal() {
        PrReviewEntity review = new PrReviewEntity();
        assertFalse(review.isTerminal());

        review.setCiStatus("pending");
        assertFalse(review.isTerminal());

        review.setCiStatus("conflict");
        assertFalse(review.isTerminal());

        review.setCiStatus("superseded");
        assertFalse(review.isTerminal());

        review.setCiStatus("closed_unmerged");
        assertTrue(review.isTerminal(), "closed_unmerged must be terminal");

        PrReviewEntity mergedReview = new PrReviewEntity();
        mergedReview.setMerged(true);
        assertTrue(mergedReview.isTerminal(), "merged=true must be terminal");
    }

    @Test
    @DisplayName("Terminal closed_unmerged cannot be overwritten by superseded or other non-terminal statuses")
    void testClosedUnmergedCannotBeOverwritten() {
        PrReviewEntity review = new PrReviewEntity();
        review.setCiStatus("pending");
        assertEquals("pending", review.getCiStatus());

        review.setCiStatus("superseded");
        assertEquals("superseded", review.getCiStatus());

        // Transition to terminal closed_unmerged
        review.setCiStatus("closed_unmerged");
        assertEquals("closed_unmerged", review.getCiStatus());
        assertTrue(review.isTerminal());

        // Attempted overwrite by superseded must be rejected/ignored
        review.setCiStatus("superseded");
        assertEquals("closed_unmerged", review.getCiStatus(), "superseded must not overwrite closed_unmerged");

        // Attempted overwrite by pending or conflict must be rejected/ignored
        review.setCiStatus("pending");
        assertEquals("closed_unmerged", review.getCiStatus());

        review.setCiStatus("conflict");
        assertEquals("closed_unmerged", review.getCiStatus());

        // Idempotent write is permitted
        review.setCiStatus("closed_unmerged");
        assertEquals("closed_unmerged", review.getCiStatus());
    }
}
