package com.eneik.production.services.judgment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The factory's judgment layer: one bounded ruling on one refutation.
 *
 * Runs on the operator's Claude SUBSCRIPTION, not on a metered API key. The first build of this class
 * called /v1/messages with an `sk-ant-` key, which the plan had promised as "flat cost" - it is not, it
 * is per token, and swapping one metered API for another was no improvement over the Gemini account
 * whose credit had run out. Verified 2026-08-21 by a real call: the Claude Code CLI, running in a Linux
 * container with the operator's existing OAuth credential mounted, returns a schema-bounded verdict on
 * the subscription. No key, no balance, and nothing at all required from the operator.
 *
 * The CLI itself lives in {@code judgment-sidecar}, not here - it holds the credential, and a credential
 * belongs in the smallest separately-reviewable surface, which is the same decision the operator made
 * for the docker socket in docker-compose.yml. This class only speaks HTTP to that sidecar, in the same
 * java.net.http idiom as every other external client in this codebase.
 *
 * What this client will not do is return prose. The schema travels with the request, so the shape of the
 * answer is a property of the request rather than a request made politely inside a prompt. ACP-102
 * applies to the agent as much as to anything it reads: an answer that merely looks like a verdict is
 * not one.
 */
@Component
public class JudgmentAgentClient {

    private static final Logger log = LoggerFactory.getLogger(JudgmentAgentClient.class);

    /**
     * The agent's whole remit, stated once.
     *
     * Deliberately short. This model follows a system prompt closely and calibrates its own depth, so a
     * long prescriptive script would narrow the judgment being asked for rather than sharpen it. What it
     * does need is the boundary: this is the factory's own assertion about itself, not the client's
     * product and not the delivery, and those three must never be mixed.
     */
    private static final String SYSTEM_PROMPT = """
            You are the judgment layer of an autonomous software factory, reading a refutation the \
            factory has produced about itself.

            Context you must keep separate and never mix:
              - FACTORY: this orchestrator's own code and behaviour. That is the only level you rule on.
              - DELIVERY: whether a client's brief has been carried to a product. Not your subject.
              - PRODUCT: a running client instance. Not your subject.

            An invariant is something this factory asserts is always true of itself. You are given a \
            transition: an invariant's status changed, which means an assertion the factory made has \
            stopped holding, or has started holding again. Confirmations carry no information here; only \
            the transition does.

            Rule on exactly one question: does this transition indicate a defect in the FACTORY's own \
            construction that a person should act on?

            Answer ABSTAIN when the transition is already explained by something visible in the evidence \
            you were given - a known cause, a recovery, an expected consequence of work in progress. \
            ABSTAIN is the correct and expected answer most of the time. Do not manufacture a finding to \
            appear useful; a finding that is not real costs the factory more than a silence.

            Answer FINDING only when the transition indicates a real defect in the factory's own code or \
            process. Then title states the defect in one line, and action states what a person should \
            change, concretely and in the factory's own source.

            Fill every field. On ABSTAIN, set title and action to the empty string.""";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String sidecarUrl;

    @Value("${judgment-agent.request-timeout-seconds:300}")
    private int requestTimeoutSeconds;

    public JudgmentAgentClient(ObjectMapper objectMapper,
                               @Value("${judgment-agent.url:http://judgment-sidecar:8092}") String sidecarUrl) {
        // Same bounded-call convention as JulesApiClient and StitchClient, and for the same reason: this
        // runs on the shared scheduling pool, and a call with no connect timeout there was the 2026-07-24
        // incident that starved that pool down to one live thread and raised a factory-wide stall.
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        this.objectMapper = objectMapper;
        this.sidecarUrl = sidecarUrl;
    }

    /**
     * Never null, and never a silent ABSTAIN - an ABSTAIN is itself a ruling and must not be invented
     * from a failure to obtain one.
     *
     * The two kinds of non-answer are kept apart because they are facts about different things, which is
     * the same distinction V104 drew one level down. UNAVAILABLE is a fact about the sidecar - the same
     * input will very likely succeed later, so the transition must be retried. UNJUDGEABLE is a fact
     * about THIS INPUT - an answer that did not satisfy the declared schema will not satisfy it next time
     * either. Collapsing the two makes one such row an absorbing state at the head of a FIFO queue and
     * stops all judgment permanently.
     */
    /**
     * The width of this channel, enforced here because it is a property of the channel and not of any
     * caller.
     *
     * 2026-08-23: the sidecar hands the prompt to a spawned process as a single argument, and a single
     * argument has a hard kernel limit. Fifty-five restarts with `spawn E2BIG` before this existed, and
     * four more in the fifteen minutes after a caller-side cap was deployed - because bounding one caller
     * leaves every other caller able to kill the same shared instrument. The content is frequently
     * Cyrillic, where the limit counts bytes and each character costs two, so this sits well below the
     * kernel's figure rather than near it.
     *
     * Truncation is announced inside the prompt, never silent: a judge that cannot see it has been cut will
     * rule on the fragment as though it were the whole, which is worse than refusing to rule.
     */
    static final int PROMPT_CHAR_LIMIT = 40_000;

    static String withinChannel(String prompt) {
        if (prompt == null || prompt.length() <= PROMPT_CHAR_LIMIT) {
            return prompt;
        }
        log.warn("[JUDGMENT] prompt of {} characters exceeds the {} the channel can carry; selecting evidence to fit channel",
                prompt.length(), PROMPT_CHAR_LIMIT);

        // Law 17: No judgment may be made on mechanically truncated evidence.
        // If the prompt contains a DIFF section, select diff evidence at file boundaries by criteria relevance
        // rather than slicing the prompt mechanically by character index.
        String marker = "\n\nDIFF\n";
        int diffIdx = prompt.indexOf(marker);
        if (diffIdx == -1) {
            marker = "\nDIFF\n";
            diffIdx = prompt.indexOf(marker);
        }

        if (diffIdx != -1) {
            String prefix = prompt.substring(0, diffIdx + marker.length());
            String rawDiff = prompt.substring(diffIdx + marker.length());

            // Extract criteria vocabulary if present
            String criteria = extractCriteria(prefix);

            // If prefix alone is so large that it suffocates the diff, reduce repository listing if present
            if (prefix.length() > PROMPT_CHAR_LIMIT - 10_000 && prefix.contains("WHAT THE REPOSITORY CONTAINS ON MAIN")) {
                prefix = compactRepositoryStateInPrefix(prefix, 40);
            }

            // Calculate exact space available for diff
            int warningReserve = 600;
            int allowedDiff = Math.max(0, PROMPT_CHAR_LIMIT - prefix.length() - warningReserve);

            CriteriaEvidenceSelector.SelectedEvidence selected =
                    CriteriaEvidenceSelector.select(rawDiff, criteria, allowedDiff);

            if (selected.omitted().isEmpty()) {
                return prefix + selected.text();
            }

            String warning = "\n[THIS INPUT WAS TRIMMED TO FIT THE JUDGMENT CHANNEL. Evidence was selected at file "
                    + "boundaries by bearing on acceptance criteria, not by position. Files omitted entirely: "
                    + String.join(", ", selected.omitted()) + ". Do not rule as though you had seen the whole: "
                    + "if what you need to decide could lie in an omitted file, answer UNDECIDABLE instead of deciding.]\n";
            return prefix + warning + selected.text();
        }

        // Fallback for non-diff prompts: bounded cleanly with explicit notice
        int cutoff = Math.max(0, PROMPT_CHAR_LIMIT - 300);
        return prompt.substring(0, cutoff)
                + "\n\n[THIS INPUT WAS TRUNCATED to fit the judgment channel. You are seeing the first "
                + cutoff + " characters. Do not rule as though you had seen the whole: if what "
                + "you need to decide could lie in the part you cannot see, say so instead of deciding.]";
    }

    private static String extractCriteria(String text) {
        int idx = text.indexOf("ACCEPTANCE CRITERIA THIS TASK CARRIED\n");
        if (idx == -1) {
            return "";
        }
        int start = idx + "ACCEPTANCE CRITERIA THIS TASK CARRIED\n".length();
        int end = text.indexOf("\n\nMERGED PULL REQUEST", start);
        if (end == -1) {
            end = text.indexOf("\n\nWHAT THE REPOSITORY", start);
        }
        if (end == -1) {
            end = text.indexOf("\n\nDIFF\n", start);
        }
        return end != -1 ? text.substring(start, end).trim() : text.substring(start).trim();
    }

    private static String compactRepositoryStateInPrefix(String prefix, int maxPaths) {
        int startIdx = prefix.indexOf("WHAT THE REPOSITORY CONTAINS ON MAIN");
        if (startIdx == -1) {
            return prefix;
        }
        int colonIdx = prefix.indexOf(":\n", startIdx);
        if (colonIdx == -1) {
            return prefix;
        }
        int endIdx = prefix.indexOf("\n\n", colonIdx);
        if (endIdx == -1) {
            endIdx = prefix.indexOf("\nDIFF\n", colonIdx);
        }
        if (endIdx == -1) {
            return prefix;
        }
        String pathsBlock = prefix.substring(colonIdx + 2, endIdx);
        String[] lines = pathsBlock.split("\n");
        if (lines.length <= maxPaths) {
            return prefix;
        }
        List<String> kept = new ArrayList<>();
        for (int i = 0; i < maxPaths && i < lines.length; i++) {
            kept.add(lines[i]);
        }
        String compacted = String.join("\n", kept) + "\n... (" + (lines.length - maxPaths) + " more files on main)";
        return prefix.substring(0, colonIdx + 2) + compacted + prefix.substring(endIdx);
    }

    public Ruling judge(String userPrompt) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("prompt", withinChannel(SYSTEM_PROMPT + "\n\n" + userPrompt));
            body.set("schema", verdictSchema());

            HttpRequest request = HttpRequest.newBuilder(URI.create(sidecarUrl + "/judge"))
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[JUDGMENT] sidecar answered HTTP {}: {}", response.statusCode(), truncate(response.body()));
                return Ruling.unavailable("sidecar HTTP " + response.statusCode());
            }
            return parse(response.body());
        } catch (Exception e) {
            log.warn("[JUDGMENT] sidecar unreachable: {}", e.getMessage());
            return Ruling.unavailable("sidecar unreachable: " + e.getMessage());
        }
    }

    /**
     * A critical judgment whose reply format is the caller's own contract, returned as text.
     *
     * 2026-08-21: this is the replacement §11.2 of the plan named and nobody built. `chatCritical` was
     * the factory's single point for "read this and judge it" and it routed through the ml service to
     * Gemini. The Gemini observer was switched off on 2026-08-20 and the account is out of credit, so
     * every such call has been answering 502 - measured, 39 times in 600 log lines. The adjudicator was
     * removed and its replacement was not connected, which left `JulesDispatchService` unable to decide
     * whether a silent session is looping or waiting, and the factory stalled behind one claimed task.
     *
     * Null means no answer, and the caller must treat it as it always did: no information about the
     * session, never as evidence against it.
     */
    public String judgeAsText(String prompt, String systemInstruction) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("prompt", withinChannel(
                    (systemInstruction == null ? "" : systemInstruction + "\n\n") + prompt));
            body.set("schema", objectMapper.createObjectNode()); // no schema: the caller parses its own format

            HttpRequest request = HttpRequest.newBuilder(URI.create(sidecarUrl + "/judge"))
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[JUDGMENT] text judgment: sidecar answered HTTP {}", response.statusCode());
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (!"TEXT".equals(root.path("outcome").asText(""))) {
                log.warn("[JUDGMENT] text judgment: {} - {}", root.path("outcome").asText(""), root.path("reason").asText(""));
                return null;
            }
            return root.path("text").asText("");
        } catch (Exception e) {
            log.warn("[JUDGMENT] text judgment failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * The bound on the answer, declared on the request. additionalProperties false and a two-valued
     * verdict enum, so the caller switches on a value rather than interpreting text.
     */
    ObjectNode verdictSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();
        ObjectNode verdict = objectMapper.createObjectNode();
        verdict.put("type", "string");
        ArrayNode allowed = objectMapper.createArrayNode();
        allowed.add("ABSTAIN");
        allowed.add("FINDING");
        verdict.set("enum", allowed);
        properties.set("verdict", verdict);
        properties.set("reason", stringProperty("Why this ruling, in one or two sentences."));
        properties.set("title", stringProperty("One line naming the factory defect. Empty string when ABSTAIN."));
        properties.set("action", stringProperty("What a person should change in the factory's own source. Empty string when ABSTAIN."));
        schema.set("properties", properties);

        ArrayNode required = objectMapper.createArrayNode();
        required.add("verdict");
        required.add("reason");
        required.add("title");
        required.add("action");
        schema.set("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private ObjectNode stringProperty(String description) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "string");
        node.put("description", description);
        return node;
    }

    /** The sidecar has already reduced the CLI envelope to the four-valued outcome; this reads it. */
    Ruling parse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        String outcome = root.path("outcome").asText("");
        String reason = root.path("reason").asText("");
        switch (outcome) {
            case "FINDING":
                return new Ruling(Outcome.FINDING, reason,
                        root.path("title").asText(""), root.path("action").asText(""));
            case "ABSTAIN":
                return new Ruling(Outcome.ABSTAIN, reason, "", "");
            case "UNJUDGEABLE":
                log.warn("[JUDGMENT] this input cannot be ruled on: {}", reason);
                return Ruling.unjudgeable(reason);
            case "UNAVAILABLE":
                log.warn("[JUDGMENT] no ruling obtained: {}", reason);
                return Ruling.unavailable(reason);
            default:
                log.warn("[JUDGMENT] sidecar returned an unknown outcome '{}'", outcome);
                return Ruling.unavailable("unknown outcome from sidecar: " + outcome);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 500 ? value : value.substring(0, 500) + "...";
    }

    /**
     * What came back, as four cases rather than a value-or-null.
     *
     * FINDING and ABSTAIN are rulings. UNJUDGEABLE and UNAVAILABLE are not - and they differ in what
     * they are facts about, which is what decides whether the transition may be marked read.
     */
    public enum Outcome {
        /** A real defect in the factory's own construction. */
        FINDING,
        /** No defect. The expected answer most of the time, and a ruling like any other. */
        ABSTAIN,
        /** This input cannot be ruled on, and will not become rulable by being retried. */
        UNJUDGEABLE,
        /** The endpoint could not answer. A fact about the instrument; the input is untouched. */
        UNAVAILABLE
    }

    public record Ruling(Outcome outcome, String reason, String title, String action) {

        public boolean isRuling() {
            return outcome == Outcome.FINDING || outcome == Outcome.ABSTAIN;
        }

        static Ruling unjudgeable(String reason) {
            return new Ruling(Outcome.UNJUDGEABLE, reason, "", "");
        }

        static Ruling unavailable(String reason) {
            return new Ruling(Outcome.UNAVAILABLE, reason, "", "");
        }
    }
}
