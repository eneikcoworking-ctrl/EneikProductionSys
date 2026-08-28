package com.eneik.production.services;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 of ENGINEERING_PHILOSOPHY_ACTION_PLAN.md: the merge gate that enforces
 * Code(t) INTERSECT L_factory = EMPTY on the merged artifact.
 *
 * <p>Only the decision is tested here, not the GitHub round-trip around it - that is why
 * {@link AutoMergeService#judgeFactoryPokaYoke} exists as a separate pure function.
 */
class AutoMergePokaYokeTest {

    private final CodeChangeClassifier classifier = new CodeChangeClassifier();

    private AutoMergeService.PokaYokeVerdict judge(List<String> files, String title) {
        return AutoMergeService.judgeFactoryPokaYoke(files, title, classifier);
    }

    @Test
    void theFourLiveBlockerPrsWouldNowBeRejected() {
        // PRs #304/#306/#307/#308 on eneikdru/test-fiftieth: title "Architectural contradiction",
        // exactly one changed file, a Jules runner script. All four merged into the client's main.
        AutoMergeService.PokaYokeVerdict verdict =
                judge(List.of("_temp_submit_blocker.sh"), "Architectural contradiction: cannot implement");
        assertTrue(verdict.rejected());
        assertEquals("contaminated", verdict.ciStatus());
        assertTrue(verdict.reason().contains("REJECTED_METADATA_CONTAMINATION"));
        assertTrue(verdict.reason().contains("_temp_submit_blocker.sh"));
    }

    @Test
    void contaminationIsRejectedEvenAlongsideRealProductCode() {
        // The invariant is about the artifact, not about whether work also happened: a runner script must
        // not reach the client's main branch under cover of a legitimate change.
        AutoMergeService.PokaYokeVerdict verdict =
                judge(List.of("backend/app/routers/billing.py", "prep.sh"), "feat: billing endpoint");
        assertTrue(verdict.rejected());
        assertEquals("contaminated", verdict.ciStatus());
    }

    @Test
    void blockerTitlesAreRejectedEvenWithACleanFileList() {
        assertTrue(judge(List.of("src/main/java/App.java"), "Blocker: the schema contradicts the brief").rejected());
        assertTrue(judge(List.of("src/main/java/App.java"), "Halt: missing upstream contract").rejected());
        assertTrue(judge(List.of("src/main/java/App.java"), "Architectural contradiction").rejected());
        assertEquals("blocker_pr",
                judge(List.of("src/main/java/App.java"), "Blocker: schema mismatch").ciStatus());
    }

    @Test
    void ciStatusAlwaysFitsThePrReviewColumn() {
        // PrReviewEntity.ciStatus is declared length = 16. A status that does not fit throws on save, and
        // a gate that throws instead of rejecting would let the merge proceed on the next tick.
        assertTrue(judge(List.of("prep.sh"), "x").ciStatus().length() <= 16);
        assertTrue(judge(List.of("src/App.java"), "Blocker: x").ciStatus().length() <= 16);
    }

    @Test
    void ordinaryProductPrsPassUntouched() {
        assertFalse(judge(List.of("backend/app/routers/billing.py", "README.md"), "feat: add billing").rejected());
        assertFalse(judge(List.of("scripts/backup.sh"), "chore: nightly backup script").rejected());
        assertFalse(judge(List.of(".eneik/task-plan.json"), "chore: decompose brief into task plan").rejected());
    }

    @Test
    void aSpecOnlyPrWithNoProductCodeIsNotRejected() {
        // Deliberate: a no-code merge is the correct terminal outcome for every role that does not owe
        // code (EmsFlowStage.requiresCodeForDelivery). Blocking it here would stall every spec-stage task;
        // the false-delivery concern is owned downstream by requiresCodeForDelivery + routeUncertifiedMerge.
        assertFalse(judge(List.of("docs/architecture/bootstrap.md"), "docs: architecture decision").rejected());
        assertFalse(judge(List.of("design/approved/billing/mockup.png"), "design: billing screen").rejected());
    }

    @Test
    void anUnreadableFileListNeverRejectsOnItsOwn() {
        // Fail-open on ignorance: a gate that rejects when it cannot see is a way to strand work.
        assertFalse(judge(List.of(), "feat: something").rejected());
        assertFalse(judge(null, "feat: something").rejected());
    }

    @Test
    void innocentWordsInsideLongerWordsDoNotTripTheBlockerRule() {
        assertFalse(judge(List.of("src/App.java"), "feat: unblocked account recovery").rejected());
        assertFalse(judge(List.of("src/App.java"), "refactor: Blockchain adapter").rejected());
    }
    @Test
    void aRejectedReviewIsTerminalAndNeverPolledAgain() {
        // If "contaminated"/"blocker_pr" were pollable, executeMerge would re-run the same rejection every
        // cycle forever - the exact deadlock shape the "conflict" and "policy_denied" comments describe,
        // only inverted. These two are genuinely dead: the PR is closed on GitHub.
        assertFalse(AutoMergeService.isReviewPollCandidate(reviewWith("contaminated")));
        assertFalse(AutoMergeService.isReviewPollCandidate(reviewWith("blocker_pr")));
    }

    private com.eneik.production.models.persistence.PrReviewEntity reviewWith(String ciStatus) {
        var review = new com.eneik.production.models.persistence.PrReviewEntity();
        review.setCiStatus(ciStatus);
        return review;
    }
}
