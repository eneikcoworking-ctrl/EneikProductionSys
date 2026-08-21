package com.eneik.production.services.judgment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the request promises and what the answer is allowed to be.
 *
 * The transport changed on 2026-08-21 - the client speaks to `judgment-sidecar`, which runs the
 * operator's Claude subscription, instead of calling a metered API with an `sk-ant-` key. The verdict
 * contract did not change, and these tests are what pins that: a bounded two-valued verdict, and a
 * failure to obtain one that is never silently turned into an ABSTAIN. An ABSTAIN is a ruling.
 */
class JudgmentAgentClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JudgmentAgentClient client() {
        JudgmentAgentClient client = new JudgmentAgentClient(objectMapper, "http://judgment-sidecar.invalid:8092");
        // @Value fields are zero when the object is constructed with `new`.
        ReflectionTestUtils.setField(client, "requestTimeoutSeconds", 300);
        return client;
    }

    @Test
    void theAnswerIsBoundedBySchemaOnTheRequestNotByThePrompt() {
        JsonNode schema = client().verdictSchema();

        assertThat(schema.path("additionalProperties").asBoolean(true)).isFalse();
        assertThat(schema.path("required").toString()).contains("verdict", "reason", "title", "action");
        assertThat(schema.path("properties").path("verdict").path("enum").toString())
                .isEqualTo("[\"ABSTAIN\",\"FINDING\"]");
    }

    @Test
    void parsesAFindingAndAnAbstain() throws Exception {
        JudgmentAgentClient.Ruling finding = client().parse(
                "{\"outcome\":\"FINDING\",\"reason\":\"r\",\"title\":\"t\",\"action\":\"a\"}");
        assertThat(finding.outcome()).isEqualTo(JudgmentAgentClient.Outcome.FINDING);
        assertThat(finding.isRuling()).isTrue();
        assertThat(finding.title()).isEqualTo("t");
        assertThat(finding.action()).isEqualTo("a");

        JudgmentAgentClient.Ruling abstain = client().parse(
                "{\"outcome\":\"ABSTAIN\",\"reason\":\"already explained\",\"title\":\"\",\"action\":\"\"}");
        assertThat(abstain.outcome()).isEqualTo(JudgmentAgentClient.Outcome.ABSTAIN);
        assertThat(abstain.isRuling()).isTrue();
        assertThat(abstain.reason()).isEqualTo("already explained");
    }

    @Test
    void anAnswerOffTheDeclaredSchemaIsAFactAboutThisInputNotAboutTheSidecar() throws Exception {
        // UNJUDGEABLE, so the caller may mark the transition read: the same input produces the same
        // non-answer every cycle, and leaving it unjudged makes one row an absorbing state at the head of
        // a FIFO queue that stops all judgment for good.
        JudgmentAgentClient.Ruling ruling = client().parse(
                "{\"outcome\":\"UNJUDGEABLE\",\"reason\":\"answer did not satisfy the declared schema\"}");
        assertThat(ruling.isRuling()).isFalse();
        assertThat(ruling.outcome()).isEqualTo(JudgmentAgentClient.Outcome.UNJUDGEABLE);
    }

    @Test
    void anUnreachableSidecarIsAFactAboutTheInstrumentAndLeavesTheInputUntouched() throws Exception {
        JudgmentAgentClient.Ruling ruling = client().parse(
                "{\"outcome\":\"UNAVAILABLE\",\"reason\":\"spawn claude ENOENT\"}");
        assertThat(ruling.outcome()).isEqualTo(JudgmentAgentClient.Outcome.UNAVAILABLE);
        assertThat(ruling.isRuling()).isFalse();
    }

    @Test
    void anUnknownOutcomeIsNeverGuessedAtAndNeverBecomesAnAbstain() throws Exception {
        JudgmentAgentClient.Ruling ruling = client().parse("{\"outcome\":\"MAYBE\",\"reason\":\"\"}");
        assertThat(ruling.outcome()).isEqualTo(JudgmentAgentClient.Outcome.UNAVAILABLE);
        assertThat(ruling.isRuling()).isFalse();
    }

    @Test
    void anUnreachableSidecarYieldsUnavailableRatherThanThrowingInsideAScheduledCycle() {
        // The URL is deliberately unresolvable: a scheduled cycle must never die on a transport error.
        assertThat(client().judge("anything").outcome())
                .isEqualTo(JudgmentAgentClient.Outcome.UNAVAILABLE);
    }
}
