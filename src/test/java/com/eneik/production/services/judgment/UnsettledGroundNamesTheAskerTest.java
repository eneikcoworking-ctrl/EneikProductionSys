package com.eneik.production.services.judgment;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Rule 8.11 O8 with the denial-evidence rule 8.3.1, applied to the asker instead of the answerer.
 *
 * <p>The judge is shown the first `diff-char-limit` characters of a merged diff and told, by this class's
 * own prompt, to answer UNDECIDABLE when a criterion's evidence could lie in the part it cannot see. When
 * it obeys, the record that results reads as a fact about the delivery. It is a fact about the question:
 * the factory asked whether the work delivered while withholding most of the work. Measured on the live
 * circuit - of 272 verdicts that settled nothing, 203 carried only the judge's own words and named no
 * cause, which is why the number pointed at no place to act.
 */
class UnsettledGroundNamesTheAskerTest {

    private final DeliveredWorkJudgmentService service = newServiceWithDiffLimit(10);

    @Test
    void anUnsettledVerdictOnATruncatedDiffNamesTheTruncation() {
        String ground = service.groundNamingWhoWasLimited(
                "UNDECIDABLE", "criterion 2 could not be checked", "0123456789ABCDEFGHIJ");

        assertTrue(ground.startsWith("criterion 2 could not be checked"), "the judge's own words are kept");
        assertTrue(ground.contains("shown 10 of 20 characters"), "and the factory's own limit is named");
    }

    @Test
    void aRuledVerdictIsLeftExactlyAsTheJudgeGaveIt() {
        // Without this the addition would rewrite grounds that settled the question, and the record would
        // start claiming a limitation where none affected the answer.
        assertEquals("the diff carries the change", service.groundNamingWhoWasLimited(
                "SATISFIED", "the diff carries the change", "0123456789ABCDEFGHIJ"));
    }

    @Test
    void anUnsettledVerdictOnAWholeDiffIsNotBlamedOnTheChannel() {
        // The other half of the same boundary: when the judge saw everything and still could not rule, the
        // limitation is not the channel's, and saying so would send the repair to the wrong place.
        assertEquals("the criteria describe no observable outcome", service.groundNamingWhoWasLimited(
                "UNDECIDABLE", "the criteria describe no observable outcome", "short"));
    }

    private DeliveredWorkJudgmentService newServiceWithDiffLimit(int limit) {
        DeliveredWorkJudgmentService instance = mock(DeliveredWorkJudgmentService.class,
                org.mockito.Mockito.CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(instance, "diffCharLimit", limit);
        return instance;
    }
}
