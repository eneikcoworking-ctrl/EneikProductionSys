package com.eneik.production.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 4 empirical falsification test suite for {@link CodeChangeClassifier}.
 * <p>
 * Philosophical anchors:
 * <ul>
 *   <li>DZHOZEF_RAZ_01_PROHIBITION_AS_CODE [D006, Raz]: Deontic exclusionary deny-list strictly excludes
 *       factory artifacts (_temp_submit*.sh, prep.sh, *harness.html) and process noise.</li>
 *   <li>AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP [D004, Varzi]: Mereological boundary separating client product
 *       wholes (arbitrary stack files, client shell scripts) from factory tooling parts.</li>
 *   <li>ALVIN_GOLDMAN_01_RELIABLE_PROCESS_AUDIT [D010, Goldman]: Reliable classification process rejects
 *       naive extension-based dogma and accurately audits mixed PR diff sets.</li>
 * </ul>
 */
public class CodeChangeClassifierFalsificationTest {

    private final CodeChangeClassifier classifier = new CodeChangeClassifier();

    @Test
    @DisplayName("Falsify Raz: Factory submission harnesses and runner scripts are strictly prohibited as product code")
    void falsifyRazProhibition_factoryRunnerScriptsAndSubmissionHarnessAreProhibited() {
        // Factory metalanguage artifacts emitted by agents
        List<String> runnerScripts = List.of(
                "_temp_submit.sh",
                "_temp_submit_blocker.sh",
                "_temp_submit_fix.sh",
                "final_submit.sh",
                "final_submit_v2.sh",
                "submit_task.sh",
                "prep.sh",
                "verification_harness.html",
                "nested/dir/_temp_submit.sh"
        );

        for (String runner : runnerScripts) {
            assertTrue(classifier.isFactoryArtifact(runner),
                    "Path must be recognized as factory artifact: " + runner);
            assertFalse(classifier.isProductCodeFile(runner),
                    "Factory artifact must never be classified as product code: " + runner);
        }

        // A PR composed entirely of factory runner scripts must have no product code
        assertFalse(classifier.hasCode(runnerScripts),
                "PR containing only factory runner scripts must not be auto-merged as product code");
    }

    @Test
    @DisplayName("Falsify Raz: Transient factory record files (.eneik/*) and design drafts are prohibited from product code")
    void falsifyRazProhibition_factoryRecordsAndDesignDraftsAreExcludedFromProductCode() {
        List<String> processFiles = List.of(
                ".eneik/task-plan.json",
                ".eneik/review-verdict.json",
                ".eneik/falsification-report.json",
                "design/draft/header-mockup.html",
                "design/approved/sidebar-spec.png",
                "README.md",
                "docs/index.md",
                "playwright-report/index.html",
                "test-results/trace.zip",
                "coverage/lcov.info",
                "node_modules/package/index.js",
                ".next/build-manifest.json"
        );

        for (String path : processFiles) {
            assertFalse(classifier.isProductCodeFile(path),
                    "Process/record artifact must not be classified as product code: " + path);
        }

        assertFalse(classifier.hasCode(processFiles),
                "PR containing only record, design draft and test report artifacts must return hasCode=false");

        // Distinguish record files from runner scripts
        assertTrue(classifier.isFactoryRecordFile(".eneik/task-plan.json"));
        assertFalse(classifier.isFactoryArtifact(".eneik/task-plan.json"));
    }

    @Test
    @DisplayName("Falsify Varzi: Legitimate client product code across arbitrary stacks is correctly identified")
    void falsifyVarziMereology_arbitraryClientStackSourceFilesAreRecognizedAsProductCode() {
        // EMS generates diverse stacks without needing allow-list updates
        List<String> realProductCodeFiles = List.of(
                "src/main/java/com/eneik/App.java",
                "frontend/src/routes/+page.svelte",
                "app/main.py",
                "server/api/users.go",
                "lib/core/kernel.rs",
                "scripts/db_migrate.sh",
                "scripts/backup_wal.sh",
                "deploy.sh",
                "Makefile",
                "Dockerfile",
                "pom.xml",
                "package.json"
        );

        for (String codeFile : realProductCodeFiles) {
            assertTrue(classifier.isProductCodeFile(codeFile),
                    "Real product file must be recognized as product code: " + codeFile);
            assertFalse(classifier.isFactoryArtifact(codeFile),
                    "Client product code must not be conflated with factory artifact: " + codeFile);
        }

        assertTrue(classifier.hasCode(realProductCodeFiles));
    }

    @Test
    @DisplayName("Falsify Varzi: Client shell scripts belong to product whole and are not rejected as factory harness")
    void falsifyVarziMereology_clientScriptsAreDistinguishedFromFactoryHarness() {
        // Client shell scripts must be preserved
        String clientScript1 = "scripts/cleanup-database.sh";
        String clientScript2 = "build/assemble-dist.sh";
        String clientScript3 = "run-migrations.sh";

        assertTrue(classifier.isProductCodeFile(clientScript1));
        assertTrue(classifier.isProductCodeFile(clientScript2));
        assertTrue(classifier.isProductCodeFile(clientScript3));

        assertFalse(classifier.isFactoryArtifact(clientScript1));
        assertFalse(classifier.isFactoryArtifact(clientScript2));
        assertFalse(classifier.isFactoryArtifact(clientScript3));
    }

    @Test
    @DisplayName("Falsify Goldman: Mixed PR diff audits correctly; single real code file makes whole PR haveCode=true")
    void falsifyGoldmanAudit_mixedPrDiffDetectsProductCodePresenceWithoutFalseNegative() {
        List<String> mixedDiff = List.of(
                ".eneik/task-plan.json",
                "_temp_submit.sh",
                "README.md",
                "design/draft/wireframe.html",
                "playwright-report/results.zip",
                "src/main/java/com/eneik/service/BillingService.java" // 1 real product code file
        );

        assertTrue(classifier.hasCode(mixedDiff),
                "Presence of single product code file among process artifacts must yield hasCode=true");
    }

    @Test
    @DisplayName("Falsify Goldman: Null, empty and blank path inputs audit safely and fail closed")
    void falsifyGoldmanAudit_nullAndBlankInputsFailClosedSafely() {
        assertFalse(classifier.hasCode(null));
        assertFalse(classifier.hasCode(List.of()));
        assertFalse(classifier.hasCode(List.of("   ", "")));
        assertFalse(classifier.isProductCodeFile(null));
        assertFalse(classifier.isProductCodeFile("   "));
        assertFalse(classifier.isFactoryArtifact(null));
        assertFalse(classifier.isFactoryArtifact("   "));
        assertFalse(classifier.isFactoryRecordFile(null));
        assertFalse(classifier.isFactoryRecordFile("   "));
    }
}
