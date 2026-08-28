package com.eneik.production.services.github;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural test for the merge-time poka-yoke (2026-08-28).
 *
 * <p>Why this exists, and why it is a source test rather than a behavioural one. The behavioural tests in
 * AutoMergePokaYokeTest verify that the DECISION is right - a blocker title is refused, a runner script is
 * refused, a normal PR passes. All of them passed while PR #319 and #320 ("Blocker: Architectural
 * contradiction in Brief 2", one changed file: {@code _temp_submit.sh}) were merged into the client's main
 * branch anyway. The decision was correct and simply never consulted: the check sat in
 * AutoMergeService.executeMerge, and those PRs merged through mergeRecordPullRequest, one of nine merge
 * paths in the codebase.
 *
 * <p>A test that a predicate is correct is not a test that it is applied. This one pins application: both
 * methods that issue the actual {@code PUT /pulls/{n}/merge} must consult the guard. If a future edit
 * removes either call - or adds a third merge method without one - this fails.
 */
class MergeChokePointPokaYokeTest {

    @Test
    void bothMergeMethodsExistOnTheServiceThatIssuesTheMerge() throws Exception {
        Method[] methods = GitHubPullRequestService.class.getDeclaredMethods();
        boolean hasMerge = false;
        boolean hasRecordMerge = false;
        boolean hasGuard = false;
        for (Method m : methods) {
            if ("mergePullRequest".equals(m.getName())) hasMerge = true;
            if ("mergeRecordPullRequest".equals(m.getName())) hasRecordMerge = true;
            if ("refusedByFactoryPokaYoke".equals(m.getName())) hasGuard = true;
        }
        assertTrue(hasMerge, "mergePullRequest must exist");
        assertTrue(hasRecordMerge, "mergeRecordPullRequest must exist");
        assertTrue(hasGuard, "the guard must live on the same class as the methods it protects");
    }

    @Test
    void everyMergingMethodConsultsTheGuard() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/eneik/production/services/github/GitHubPullRequestService.java"));

        // The two method bodies that perform PUT .../merge, isolated by their signatures.
        for (String signature : List.of(
                "public boolean mergePullRequest(ProjectEntity project, int pullNumber)",
                "public PullRequestCloseResult mergeRecordPullRequest(")) {
            int start = source.indexOf(signature);
            assertTrue(start > 0, "method not found: " + signature);
            int guardAt = source.indexOf("refusedByFactoryPokaYoke", start);
            int mergeAt = source.indexOf("/merge\"", start);
            assertTrue(guardAt > 0 && guardAt < mergeAt,
                    "the poka-yoke must be consulted BEFORE the merge call in: " + signature);
        }
    }

    /**
     * Every place that issues a merge must consult a poka-yoke first - whichever one.
     *
     * <p>Written as "guarded", not "must go through GitHubPullRequestService", because
     * AutoMergeService.executeMerge legitimately builds its own merge request (it needs the response body
     * to distinguish a 405 "already merged" from a 405 "real conflict" - see its own incident comments).
     * That path has its own richer guard, rejectByFactoryPokaYoke, which also moves the task to blocked and
     * journals the defect. What must never exist is a place that merges without asking anything, which is
     * how PR #319/#320 reached main while a correct predicate sat unconsulted one class away.
     */
    @Test
    void everyPlaceThatIssuesAMergeConsultsAPokaYokeFirst() throws Exception {
        java.util.List<String> unguarded = new java.util.ArrayList<>();
        try (var paths = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/java"))) {
            for (java.nio.file.Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = java.nio.file.Files.readString(path);
                int mergeAt = src.indexOf("/merge\"");
                if (mergeAt < 0) {
                    continue;
                }
                int guardAt = Math.max(src.indexOf("refusedByFactoryPokaYoke"), src.indexOf("rejectByFactoryPokaYoke"));
                if (guardAt < 0 || guardAt > mergeAt) {
                    unguarded.add(path.toString());
                }
            }
        }
        assertTrue(unguarded.isEmpty(),
                "these files issue a merge without consulting a poka-yoke first: " + unguarded);
    }
}
