package com.eneik.production.services.judgment;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Screen for Law 17 (Evidence Selection Law):
 * No judgment may be made on mechanically truncated evidence.
 * When the channel limit is exceeded, evidence must be selected by its bearing on the criteria
 * rather than by position. Files in the diff must be sliced strictly at file boundaries,
 * never truncated mid-hunk or mid-file. Omitted files must be explicitly named in the warning.
 */
class JudgmentAgentClientLaw17Test {

    private static final String CRITERIA = "the password reset token must expire after twenty minutes";

    @Test
    void promptWithinLimitPassesThroughUnchanged() {
        String prompt = "Short prompt within limit";
        assertThat(JudgmentAgentClient.withinChannel(prompt)).isEqualTo(prompt);
    }

    @Test
    void channelCutSelectsDiffByCriteriaBearingAndPreservesWholeFiles() {
        // Build a prompt where total length exceeds PROMPT_CHAR_LIMIT (40_000)
        String prefix = "TASK TITLE\nReset Password Feature\n\n"
                + "WHAT THIS TASK WAS ASKED TO DO\nImplement password reset token expiry.\n\n"
                + "ACCEPTANCE CRITERIA THIS TASK CARRIED\n" + CRITERIA + "\n\n"
                + "MERGED PULL REQUEST\nhttps://github.com/org/repo/pull/1\n\nDIFF\n";

        // File 1: irrelevant bulky file that sorts first alphabetically
        String irrelevant = section("aaa/UnrelatedConfig.java", "cosmetic whitespace config line\n".repeat(1500));

        // File 2: highly relevant file with criteria vocabulary that sorts last alphabetically
        String relevant = section("zzz/PasswordResetToken.java", "token expire twenty minutes validation logic\n".repeat(50));

        String oversizedPrompt = prefix + irrelevant + relevant;
        assertThat(oversizedPrompt.length()).isGreaterThan(JudgmentAgentClient.PROMPT_CHAR_LIMIT);

        String trimmed = JudgmentAgentClient.withinChannel(oversizedPrompt);

        // 1. Total length must be strictly bounded by the channel capacity
        assertThat(trimmed.length()).isLessThanOrEqualTo(JudgmentAgentClient.PROMPT_CHAR_LIMIT);

        // 2. The relevant file must be present because it bears on the criteria!
        assertThat(trimmed).contains("diff --git a/zzz/PasswordResetToken.java");
        assertThat(trimmed).contains("token expire twenty minutes validation logic");

        // 3. The irrelevant file must be omitted and named in the warning!
        assertThat(trimmed).doesNotContain("cosmetic whitespace config line");
        assertThat(trimmed).contains("aaa/UnrelatedConfig.java");
        assertThat(trimmed).contains("Evidence was selected at file boundaries by bearing on acceptance criteria");

        // 4. File boundaries must be respected: the relevant file must not be chopped mid-line
        assertThat(trimmed).endsWith("token expire twenty minutes validation logic\n");
    }

    @Test
    void nonDiffPromptIsCleanlyBoundedWithExplicitNotice() {
        String sentence = "General reasoning step and observable transition fact.\n";
        String longText = sentence.repeat(1000);
        assertThat(longText.length()).isGreaterThan(JudgmentAgentClient.PROMPT_CHAR_LIMIT);

        String trimmed = JudgmentAgentClient.withinChannel(longText);

        assertThat(trimmed.length()).isLessThanOrEqualTo(JudgmentAgentClient.PROMPT_CHAR_LIMIT);
        assertThat(trimmed).contains("[THIS INPUT WAS TRIMMED TO FIT THE JUDGMENT CHANNEL");
        assertThat(trimmed).contains("answer UNDECIDABLE instead of deciding");
        // Verify bounded at line/sentence boundary, never chopped mid-word
        assertThat(trimmed).contains("observable transition fact.\n\n[THIS INPUT WAS TRIMMED");
    }

    private static String section(String path, String body) {
        return "diff --git a/" + path + " b/" + path + "\n"
                + "index 0000000..1111111 100644\n"
                + "--- a/" + path + "\n"
                + "+++ b/" + path + "\n"
                + "@@ -1,1 +1,1 @@\n"
                + "+" + body;
    }
}
