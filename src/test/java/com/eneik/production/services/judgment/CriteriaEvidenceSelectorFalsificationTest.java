package com.eneik.production.services.judgment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Popperian Falsification Suite for CriteriaEvidenceSelector.
 * Validates epistemic and architectural evidence selection invariants under Stage 4:
 * - D007 Alvin Goldman Conversation Maxim (ALVIN_GOLDMAN_11_CONVERSATION_MAXIM):
 *     Law 17 (Evidence Selection Law): No judgment may be made on mechanically truncated evidence.
 *     Diff content exceeding channel limits is selected strictly at file boundaries based on vocabulary
 *     bearing on the acceptance criteria, keeping whole files, preserving relative order, and explicitly
 *     naming all omitted files in the warning metadata.
 * - D008 Karl Popper Falsification Harness (KARL_POPPER_01_FALSIFICATION_HARNESS):
 *     Diff slicing must never chop mid-hunk or mid-file when multiple files exist, and single oversized
 *     files are safely bounded without uncaught runtime exceptions crashing the caller.
 * - D013 Ludwig Wittgenstein Anti-Mirror Telemetry (LUDVIG_VITGENSHTEYN_01_ANTI_MIRROR_TELEMETRY):
 *     Diffs fitting completely within channel capacity are preserved verbatim without mutation or reordering.
 */
class CriteriaEvidenceSelectorFalsificationTest {

    private static final String CRITERIA = "verify payment gateway webhook signature and record transaction receipt";

    private String fileSection(String path, String content) {
        return "diff --git a/" + path + " b/" + path + "\n"
                + "--- a/" + path + "\n"
                + "+++ b/" + path + "\n"
                + "@@ -1,5 +1,5 @@\n"
                + content;
    }

    @Test
    @DisplayName("D013 Wittgenstein Anti-Mirror: Diff within budget is returned verbatim without mutation")
    void falsifyWittgensteinAntiMirror_diffWithinBudgetPreservedVerbatim() {
        String diff = fileSection("src/Webhook.java", "+ public boolean verifySignature() { return true; }\n");

        CriteriaEvidenceSelector.SelectedEvidence result =
                CriteriaEvidenceSelector.select(diff, CRITERIA, 10_000);

        assertThat(result.text()).isEqualTo(diff);
        assertThat(result.omitted()).isEmpty();
    }

    @Test
    @DisplayName("D007 Goldman Conversation Maxim: Law 17 selects files by semantic bearing and names omitted files")
    void falsifyGoldmanConversationMaxim_law17SelectsByBearingAndNamesOmittedFiles() {
        // File 1: Alphabetically first, but completely unrelated vocabulary
        String unrelated = fileSection("aaa/DocFormatting.md", "# Documentation\n" + "irrelevant line\n".repeat(500));

        // File 2: Alphabetically last, but dense with payment webhook criteria vocabulary
        String relevant = fileSection("zzz/PaymentGatewayWebhook.java",
                "+ // payment gateway webhook signature verification\n"
                + "+ // record transaction receipt\n"
                + "public void handleWebhook() {}\n");

        String combined = unrelated + relevant;
        int budget = relevant.length() + 100; // Only large enough to fit relevant, not unrelated

        CriteriaEvidenceSelector.SelectedEvidence result =
                CriteriaEvidenceSelector.select(combined, CRITERIA, budget);

        // 1. Relevant file must be included despite sorting last
        assertThat(result.text()).contains("zzz/PaymentGatewayWebhook.java");
        assertThat(result.text()).contains("payment gateway webhook signature verification");

        // 2. Unrelated file must be omitted and explicitly named
        assertThat(result.text()).doesNotContain("DocFormatting.md");
        assertThat(result.omitted()).contains("aaa/DocFormatting.md");

        // 3. Diff boundaries preserved (whole file, not sliced mid-hunk)
        assertThat(result.text()).endsWith("public void handleWebhook() {}\n");
    }

    @Test
    @DisplayName("D008 Popper Falsification Harness: Single oversized file is bounded safely without throwing")
    void falsifyPopperFalsification_singleOversizedFileSafelyBoundedWithoutCrashing() {
        String massiveFile = "diff --git a/HugeMigration.sql b/HugeMigration.sql\n" + "INSERT INTO big_table VALUES (1);\n".repeat(2000);

        int charLimit = 500;
        CriteriaEvidenceSelector.SelectedEvidence result =
                CriteriaEvidenceSelector.select(massiveFile, CRITERIA, charLimit);

        assertThat(result.text().length()).isLessThanOrEqualTo(charLimit);
        assertThat(result.omitted()).contains("(the remainder of a single file too large to carry)");
    }
}
