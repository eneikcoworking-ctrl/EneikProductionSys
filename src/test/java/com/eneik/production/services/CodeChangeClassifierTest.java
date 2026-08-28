package com.eneik.production.services;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeChangeClassifierTest {

    private final CodeChangeClassifier classifier = new CodeChangeClassifier();

    @Test
    void emptyOrNullChangeListHasNoCode() {
        assertFalse(classifier.hasCode(List.of()));
        assertFalse(classifier.hasCode(null));
    }

    @Test
    void eneikVerdictFilesOnlyHasNoCode() {
        assertFalse(classifier.hasCode(List.of(".eneik/design-review-verdict.json")));
        assertFalse(classifier.hasCode(List.of(".eneik/falsification-report.json", ".eneik/records/plan-2026.json")));
    }

    @Test
    void designDraftAndApprovedMockupsHaveNoCode() {
        assertFalse(classifier.hasCode(List.of("design/draft/billing-alert/mockup.html")));
        assertFalse(classifier.hasCode(List.of("design/approved/billing-alert/mockup.png")));
    }

    @Test
    void markdownOnlyHasNoCode() {
        assertFalse(classifier.hasCode(List.of("README.md", "docs/architecture/bootstrap.md")));
    }

    @Test
    void generatedTestArtifactsHaveNoCode() {
        assertFalse(classifier.hasCode(List.of("playwright-report/index.html", "test-results/trace.zip", "coverage/lcov.info")));
    }

    @Test
    void mixOfDenyListCategoriesStillHasNoCode() {
        assertFalse(classifier.hasCode(List.of(".eneik/review-verdict.json", "README.md", "design/draft/x/mockup.html")));
    }

    @Test
    void anyRealSourceFileMeansHasCode() {
        assertTrue(classifier.hasCode(List.of("backend/src/main/java/com/example/App.java")));
        assertTrue(classifier.hasCode(List.of("frontend/src/routes/+page.svelte")));
        assertTrue(classifier.hasCode(List.of("pom.xml")));
        assertTrue(classifier.hasCode(List.of("package.json")));
    }

    @Test
    void oneRealFileAmongMostlyProcessFilesStillMeansHasCode() {
        assertTrue(classifier.hasCode(List.of(
                ".eneik/task-plan.json",
                "README.md",
                "backend/app/routers/billing.py"
        )));
    }
    // --- L_factory poka-yoke (2026-08-27, Phase 2) ---

    @Test
    void julesRunnerScriptsAreFactoryMetalanguageNotProductCode() {
        // The live defect: blocker PRs #304/#306/#307/#308 on eneikdru/test-fiftieth each contained
        // exactly one of these and were classified as containing product code, so they auto-merged.
        assertTrue(classifier.isFactoryArtifact("_temp_submit.sh"));
        assertTrue(classifier.isFactoryArtifact("_temp_submit_blocker.sh"));
        assertTrue(classifier.isFactoryArtifact("_temp_submit_custom.sh"));
        assertTrue(classifier.isFactoryArtifact("_temp_submit_review.sh"));
        assertTrue(classifier.isFactoryArtifact("final_submit.sh"));
        assertTrue(classifier.isFactoryArtifact("prep.sh"));
        assertTrue(classifier.isFactoryArtifact("verification-harness.html"));
        assertTrue(classifier.isFactoryArtifact("some/nested/dir/_temp_submit.sh"));
    }

    @Test
    void aPrContainingOnlyRunnerScriptsHasNoProductCode() {
        assertFalse(classifier.hasCode(List.of("_temp_submit_blocker.sh")));
        assertFalse(classifier.hasCode(List.of("prep.sh", "final_submit.sh", "README.md")));
    }

    @Test
    void legitimateClientShellScriptsAreStillProductCode() {
        // The expensive direction of this error is rejecting a real PR, so the ban is on the runner
        // shapes the factory emits, not on shell scripts.
        assertTrue(classifier.hasCode(List.of("scripts/backup.sh")));
        assertTrue(classifier.hasCode(List.of("scripts/migrate_db.sh")));
        assertTrue(classifier.hasCode(List.of("deploy.sh")));
        assertFalse(classifier.isFactoryArtifact("scripts/backup.sh"));
    }

    @Test
    void oneRunnerScriptAmongRealCodeStillLeavesTheProductCode() {
        assertTrue(classifier.hasCode(List.of("_temp_submit.sh", "backend/app/routers/billing.py")));
        assertTrue(classifier.isProductCodeFile("backend/app/routers/billing.py"));
        assertFalse(classifier.isProductCodeFile("_temp_submit.sh"));
    }

    @Test
    void factoryRecordFilesAreDistinctFromFactoryRunnerScripts() {
        // Record files are legitimately produced by the pipeline - stripped before merge, never grounds
        // to reject a PR. Runner scripts are grounds to reject one.
        assertTrue(classifier.isFactoryRecordFile(".eneik/task-plan.json"));
        assertFalse(classifier.isFactoryArtifact(".eneik/task-plan.json"));
        assertFalse(classifier.isFactoryRecordFile("_temp_submit.sh"));
    }
}
