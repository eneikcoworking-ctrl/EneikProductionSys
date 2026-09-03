package com.eneik.production.services;

import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Charter invariant 9: related entities may not disagree.
 *
 * <p>A brief whose slices exist has been decomposed - the guard that withholds it from re-collection says
 * so in its own words, and the definition it stands on is compiled(w) ⟺ a slice exists whose
 * originWishlistId is w. The brief's status nevertheless stayed `pending`, so the two disagreed: measured
 * 04.09 on test-fiftieth, one pending brief, nothing queued, nothing active, the project unable to leave
 * DECOMPOSING because the transition's owner had already made it, and the same line written every tick
 * about a fact that cannot change (rule 8.11 O9).
 */
class DecompositionEstablishedTest {

    private final ProjectFlowService service =
            mock(ProjectFlowService.class, org.mockito.Mockito.CALLS_REAL_METHODS);

    @Test
    void aBriefWhoseSlicesExistIsRecordedAsDecomposed() {
        WishlistEntity brief = new WishlistEntity();
        brief.setStatus(WishlistStatus.pending);

        assertTrue(service.markDecompositionEstablished(brief));
        assertEquals(WishlistStatus.converted_to_task, brief.getStatus());
    }

    @Test
    void aBriefStillCompilingIsAlsoRecorded() {
        // The other status the admission loop offers. Left as it was, it kept the same disagreement alive.
        WishlistEntity brief = new WishlistEntity();
        brief.setStatus(WishlistStatus.compiling);

        assertTrue(service.markDecompositionEstablished(brief));
        assertEquals(WishlistStatus.converted_to_task, brief.getStatus());
    }

    @Test
    void aBriefAlreadyRecordedIsNotWrittenAgain() {
        // The mandatory reverse case: without it the sweep saves the row and logs the same unchanging fact
        // on every tick, which is the defect the record was supposed to end.
        WishlistEntity brief = new WishlistEntity();
        brief.setStatus(WishlistStatus.converted_to_task);

        assertFalse(service.markDecompositionEstablished(brief));
        assertEquals(WishlistStatus.converted_to_task, brief.getStatus());
    }
}
