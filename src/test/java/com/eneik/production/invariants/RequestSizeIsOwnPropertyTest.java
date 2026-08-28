package com.eneik.production.invariants;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P7 and P9 of §10: the size of a request this factory sends is a property of its own text.
 *
 * <p>Measured 2026-08-29. One review-fallback task reached 1 788 060 characters. The median task
 * description in the same project is 10 437, and every outlier above 100 000 was this one prompt - because
 * it was the only place that pasted an artifact the factory did not author (the reviewed PR's whole diff)
 * into a request. The reviewed party is another agent of this same factory, so the factory's output became
 * its own input with nothing bounding the loop.
 *
 * <p>Jules refused it with 400 INVALID_ARGUMENT. The factory had no dispatch outcome meaning "our request
 * was malformed", so the refusal landed on the nearest available object - the ACCOUNT - and three of them
 * were burned in a single tick against an escalation threshold of two.
 *
 * <p>Structural rather than behavioural on purpose. The defect is not that one prompt was too long; it is
 * that a prompt's length was allowed to be somebody else's property. A behavioural test would have to
 * guess a threshold, and any threshold would be a number nobody derived - the mistake §6 item 9 records.
 * This asserts the shape instead: the builder takes no diff, and the dedup key of §9 still carries one.
 */
class RequestSizeIsOwnPropertyTest {

    private String source() throws IOException {
        return Files.readString(Path.of(
                "src/main/java/com/eneik/production/services/jules/JulesDispatchService.java"));
    }

    private String reviewPromptBuilder(String source) {
        int start = source.indexOf("String reviewerFallbackPromptBatch(");
        assertTrue(start > 0, "the review-fallback prompt builder must exist");
        int end = source.indexOf("\n    }", start);
        return source.substring(start, end);
    }

    @Test
    void theReviewPromptDoesNotCarryTheDiffItAsksAbout() throws IOException {
        String builder = reviewPromptBuilder(source());

        assertFalse(builder.contains("diffs"),
                "the reviewer's session runs inside the repository and can read the PR there; sending a "
                        + "copy makes this factory's request size a property of somebody else's work");
        assertFalse(builder.contains("Diff to review"),
                "the prompt must reference the pull request, not embed it");
        assertTrue(builder.contains("PR under review"),
                "the reference itself must stay - the reviewer still has to know which PR");
    }

    @Test
    void nothingCarriesFetchedDiffTextTowardsThePrompt() throws IOException {
        // The plumbing that used to ferry it: a list built at fetch time and threaded through four
        // methods. Its absence is what makes the property above hold for the persistent-worker path too,
        // not only for the one-shot path - the same "repair placed on the path the defect does not take"
        // this plan has already recorded once.
        String source = source();
        assertFalse(source.contains("fetchedDiffs"), "the fetched-diff list must be gone entirely");
        assertFalse(source.contains(", diffs,"), "no method may still take the diff text as a parameter");
    }

    @Test
    void theDeduplicationKeyOfSectionNineIsUnchanged() throws IOException {
        // P9. The diff is still fetched and still hashed - it just stops travelling. If this fails, the
        // size fix silently weakened the guard that stopped seven sessions on one closed PR.
        assertTrue(source().contains("task.getId() + \"::\" + prUrl + \"::\" + diffHash"),
                "the key must remain taskId::prUrl::diffHash, to the bit");
    }
}
