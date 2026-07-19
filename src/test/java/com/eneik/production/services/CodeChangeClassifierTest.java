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
}
