package com.eneik.production.services;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Screen for Ontological Law 20 / Invariant S4:
 * "∀ путь к слиянию: он проходит через гейт"
 * (All paths to merge must pass through the gate).
 *
 * <p>Incident, 2026-09-04: AutoMergeService.reconcileCleanOpenGitHubPullRequests ([DIRECT-SWEEP])
 * merged 316 PRs directly into main upon detecting `mergeable == true` on GitHub, bypassing:
 * - PrReviewEntity verification
 * - CI check status
 * - CodeChangeClassifier (merging 0-code PRs such as PR #806, #808, #809)
 * - Quality gate / Epistemic layer evaluation
 *
 * This test guarantees that no ungated direct sweep can exist in AutoMergeService.
 */
class AutoMergeLaw20InvariantS4Test {

    @Test
    void reconcileCleanOpenGitHubPullRequestsDoesNotExist() {
        Method[] methods = AutoMergeService.class.getDeclaredMethods();
        boolean hasDirectSweep = Arrays.stream(methods)
                .anyMatch(m -> "reconcileCleanOpenGitHubPullRequests".equals(m.getName()));
        assertFalse(hasDirectSweep,
                "Law 20 / Invariant S4 violation: reconcileCleanOpenGitHubPullRequests must not exist. "
                        + "All merges must pass through PrReviewEntity and the quality gate.");
    }

    @Test
    void noUngatedDirectSweepInAutoMergeSource() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eneik/production/services/AutoMergeService.java"));

        assertFalse(source.contains("DIRECT-SWEEP"),
                "AutoMergeService must not contain [DIRECT-SWEEP] bypass logic.");
        assertFalse(source.contains("reconcileCleanOpenGitHubPullRequests"),
                "AutoMergeService must not invoke or declare reconcileCleanOpenGitHubPullRequests.");
    }

    @Test
    void executeMergeAlwaysGuardsWithFactoryPokaYoke() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eneik/production/services/AutoMergeService.java"));

        int executeMergeIndex = source.indexOf("void executeMerge(PrReviewEntity");
        assertTrue(executeMergeIndex > 0, "executeMerge(PrReviewEntity) must exist in AutoMergeService");

        int pokaYokeIndex = source.indexOf("rejectByFactoryPokaYoke", executeMergeIndex);
        int mergeIndex = source.indexOf("/merge\"", executeMergeIndex);

        assertTrue(pokaYokeIndex > 0 && pokaYokeIndex < mergeIndex,
                "executeMerge must consult rejectByFactoryPokaYoke before issuing the merge call");
    }
}
