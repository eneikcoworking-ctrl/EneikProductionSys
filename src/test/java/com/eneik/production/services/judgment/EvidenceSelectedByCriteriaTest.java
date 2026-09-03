package com.eneik.production.services.judgment;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Model rule 8.23: a witness denied the evidence is not a witness, and what it is shown must be selected by
 * the question rather than by position.
 *
 * <p>Taking the first characters of a merged diff selects by the alphabetical accident of file names. What
 * settles the question ends up unseen not because it is absent but because it sorted late - and the judge,
 * told to refuse on partial evidence, refuses. Measured: 203 of 272 verdicts that settled nothing were the
 * judge obeying exactly that instruction.
 */
class EvidenceSelectedByCriteriaTest {

    private static final String CRITERIA = "the password reset token must expire";

    @Test
    void theFileThatBearsOnTheCriteriaIsShownEvenWhenItComesLast() {
        String irrelevant = section("aaa/Unrelated.java", "cosmetic whitespace change ".repeat(20));
        String relevant = section("zzz/PasswordResetToken.java", "token expire logic here ".repeat(5));
        DeliveredWorkJudgmentService service = serviceWithLimit(relevant.length() + 10);

        DeliveredWorkJudgmentService.SelectedEvidence shown =
                service.evidenceForCriteria(irrelevant + relevant, CRITERIA);

        assertTrue(shown.text().contains("PasswordResetToken.java"),
                "the evidence the criteria name was left out because it sorted last");
        assertEquals(java.util.List.of("aaa/Unrelated.java"), shown.omitted(),
                "and what was left out must be named, not summarised as a character count");
    }

    @Test
    void aDiffThatFitsIsPassedThroughUnchanged() {
        // The other half of the rule. Selecting when nothing needed selecting would reorder evidence for no
        // reason, which is a different defect in the same place.
        String whole = section("a/One.java", "x") + section("b/Two.java", "y");
        DeliveredWorkJudgmentService service = serviceWithLimit(whole.length());

        DeliveredWorkJudgmentService.SelectedEvidence shown = service.evidenceForCriteria(whole, CRITERIA);

        assertEquals(whole, shown.text());
        assertTrue(shown.omitted().isEmpty());
    }

    @Test
    void whatIsShownKeepsTheOrderTheDiffHad() {
        // Ordering by bearing decides WHAT is carried, not how it reads: a diff handed back shuffled would
        // make hunks reference each other backwards.
        String first = section("a/First.java", "token expire ".repeat(3));
        String second = section("b/Second.java", "token expire also ".repeat(3));
        String bulky = section("c/Bulky.java", "unrelated ".repeat(60));
        DeliveredWorkJudgmentService service = serviceWithLimit(first.length() + second.length() + 5);

        DeliveredWorkJudgmentService.SelectedEvidence shown =
                service.evidenceForCriteria(bulky + first + second, CRITERIA);

        assertTrue(shown.text().indexOf("First.java") < shown.text().indexOf("Second.java"));
        assertFalse(shown.text().contains("Bulky.java"));
        assertEquals(java.util.List.of("c/Bulky.java"), shown.omitted());
    }

    private String section(String path, String body) {
        return "diff --git a/" + path + " b/" + path + "\n@@ -1 +1 @@\n+" + body + "\n";
    }

    private DeliveredWorkJudgmentService serviceWithLimit(int limit) {
        DeliveredWorkJudgmentService instance = mock(DeliveredWorkJudgmentService.class,
                org.mockito.Mockito.CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(instance, "diffCharLimit", limit);
        return instance;
    }
}
