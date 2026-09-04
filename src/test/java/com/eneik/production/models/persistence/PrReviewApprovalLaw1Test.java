package com.eneik.production.models.persistence;

import com.eneik.production.services.AutoMergeService;
import com.eneik.production.services.dashboard.ProjectOperationalContextService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Law 1 (Single Point of Application / Single Truth Source) Test for PR Review Approval Predicate:
 *
 *   |impl(I)| = 1
 *
 * "The approval predicate has two implementations, and I nearly broke it myself today...
 * AutoMergeService:46 and ProjectOperationalContextService:43 hold the byte-identical constant
 * 'CORE ARCHITECTURE VERIFIED. APPROVED.', and both answer the same question the same way.
 * Two points of application for one predicate."
 *
 * This test guarantees:
 * 1. PrReviewEntity is the canonical source of truth for APPROVAL_TOKEN, isApproved(), and isRejected().
 * 2. AutoMergeService and ProjectOperationalContextService declare no independent APPROVAL_TOKEN field.
 * 3. The literal string "CORE ARCHITECTURE VERIFIED. APPROVED." appears solely in PrReviewEntity.java
 *    across all production sources.
 */
public class PrReviewApprovalLaw1Test {

    @Test
    @DisplayName("Behavioral: isApproved and isRejected strictly follow the canonical tokens")
    void behavioralVerification() {
        PrReviewEntity review = new PrReviewEntity();

        // null diffSummary
        review.setDiffSummary(null);
        assertFalse(review.isApproved());
        assertFalse(review.isRejected());

        // empty diffSummary
        review.setDiffSummary("");
        assertFalse(review.isApproved());
        assertFalse(review.isRejected());

        // approved diffSummary
        review.setDiffSummary("Verification complete. " + PrReviewEntity.APPROVAL_TOKEN + " Reviewed by Jules.");
        assertTrue(review.isApproved());
        assertFalse(review.isRejected());

        // rejected diffSummary
        review.setDiffSummary(PrReviewEntity.REJECTION_PREFIX + ": missing test coverage.");
        assertFalse(review.isApproved());
        assertTrue(review.isRejected());

        // neutral comments
        review.setDiffSummary("Looks acceptable, minor formatting suggestions.");
        assertFalse(review.isApproved());
        assertFalse(review.isRejected());
    }

    @Test
    @DisplayName("Structural: AutoMergeService and ProjectOperationalContextService declare no APPROVAL_TOKEN field")
    void noDuplicateFieldsDeclaredInServices() {
        boolean autoMergeHasField = Arrays.stream(AutoMergeService.class.getDeclaredFields())
                .anyMatch(f -> f.getName().equals("APPROVAL_TOKEN"));
        assertFalse(autoMergeHasField, "AutoMergeService must not declare APPROVAL_TOKEN field; use PrReviewEntity");

        boolean dashboardHasField = Arrays.stream(ProjectOperationalContextService.class.getDeclaredFields())
                .anyMatch(f -> f.getName().equals("APPROVAL_TOKEN"));
        assertFalse(dashboardHasField, "ProjectOperationalContextService must not declare APPROVAL_TOKEN field; use PrReviewEntity");

        boolean entityHasField = Arrays.stream(PrReviewEntity.class.getDeclaredFields())
                .anyMatch(f -> f.getName().equals("APPROVAL_TOKEN"));
        assertTrue(entityHasField, "PrReviewEntity must be the single canonical holder of APPROVAL_TOKEN");
    }

    @Test
    @DisplayName("Structural Law 1 Invariant: 'CORE ARCHITECTURE VERIFIED. APPROVED.' literal defined solely in PrReviewEntity.java")
    void singlePointOfDefinitionInProductionSources() throws IOException {
        Path mainJavaRoot = Path.of("src/main/java/com/eneik/production");
        if (!Files.exists(mainJavaRoot)) {
            mainJavaRoot = Path.of("C:/Projects/Eneik/docker-build/EneikProductionSys/src/main/java/com/eneik/production");
        }
        assertTrue(Files.exists(mainJavaRoot),
                "Law 1 structural guard cannot run: main source tree not found at " + mainJavaRoot.toAbsolutePath());

        String targetLiteral = "\"CORE ARCHITECTURE VERIFIED. APPROVED.\"";
        List<String> filesWithLiteralDefinition = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(mainJavaRoot)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    String content = Files.readString(path);
                    if (content.contains(targetLiteral)) {
                        filesWithLiteralDefinition.add(path.getFileName().toString());
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        assertEquals(List.of("PrReviewEntity.java"), filesWithLiteralDefinition,
                "Law 1: Approval literal 'CORE ARCHITECTURE VERIFIED. APPROVED.' must reside solely in PrReviewEntity.java, "
                        + "but was found in " + filesWithLiteralDefinition);
    }
}
