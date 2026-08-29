package com.eneik.production.invariants;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural test for "a transaction is not held across a call to another system" (2026-08-29, plan §4.24).
 *
 * <p>Why structural. A scheduled method that carries a transaction over its own network calls is correct in
 * every single execution - the queries run, the calls return, the work is done. It is wrong in what it
 * holds while it waits: a pooled connection out of twelve, and whatever rows it locked, for as long as
 * another system takes to answer. This repository had already fixed the same shape three times by hand -
 * SessionLifecycleService.deleteRemote, ProjectFlowService.dispatchQueuedTasks, claimAccountForTask, each
 * carrying its own comment about it - and it came back twice more, in the review sweep and in the
 * GitHub-truth sweep.
 *
 * <p>It was measured before it was forbidden. Hikari reported both sweeps' connections as leaked at the
 * 30-second threshold, and ProjectFlowService.admitDueCoverageAudits twice timed out waiting for the
 * project row those sweeps were holding - so the coverage mechanism did not run at all on those ticks.
 * Charter invariant 11: lock granularity must match causal dependency, and asking GitHub about one project
 * depends on nothing another project's admission lock protects.
 *
 * <p>The remedy the rule expects is the one already used here: the schedule holds nothing, and each
 * check-then-write inside takes its own short REQUIRES_NEW span through the self-proxy.
 */
class ScheduledTransactionSpanInvariantTest {

    /** Fields whose methods reach another system - the things one does not wait for while holding a lock. */
    private static final List<String> EXTERNAL_CLIENTS = List.of(
            "gitHubPullRequestService",
            "gitHubProjectFactoryClient",
            "julesApiClient",
            "geminiContextService",
            "stitchClient",
            "mlPredictionServiceClient");

    /** A method declaration in this file, captured by name so its body can be followed from a caller. */
    private static final Pattern METHOD_DECLARATION = Pattern.compile(
            "(?:public|protected|private)\\s+(?:static\\s+)?[\\w.<>\\[\\], ?]+\\s+(\\w+)\\s*\\([^;{]*\\)\\s*(?:throws [\\w., ]+)?\\{");

    /** A call to something by name - resolved against this file's own declarations, ignored otherwise. */
    private static final Pattern CALL = Pattern.compile("(?:^|[^\\w.])(\\w+)\\s*\\(");

    private static final Pattern SCHEDULED_METHOD = Pattern.compile(
            "@Scheduled\\([^)]*\\)((?:\\s*@[\\w.]+(?:\\([^)]*\\))?)*)\\s*(?:public|protected|private)[^{]*\\{");

    /**
     * Declared methods of one file, by name, so a call the scheduled method makes to its own helper is
     * followed rather than lost. This was not academic: the first version of this test looked only at the
     * method's own body and passed while the offending method still held a transaction, because an earlier
     * fix (plan §4.25) had moved its GitHub call one level down into a helper. A guard that cannot fail
     * guards nothing (plan §5.10), so the reachable set is closed over same-file calls until it stops
     * growing - no depth is chosen, the fixpoint decides.
     */
    private static java.util.Map<String, String> methodBodiesOf(String source) {
        java.util.Map<String, String> bodies = new java.util.HashMap<>();
        Matcher declarations = METHOD_DECLARATION.matcher(source);
        while (declarations.find()) {
            bodies.put(declarations.group(1), bodyOf(source, declarations.end() - 1));
        }
        return bodies;
    }

    private static String reachableFrom(String body, java.util.Map<String, String> bodies) {
        StringBuilder reached = new StringBuilder(body);
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.Deque<String> queue = new java.util.ArrayDeque<>();
        collectCalls(body, queue);
        while (!queue.isEmpty()) {
            String name = queue.poll();
            if (!seen.add(name)) {
                continue;
            }
            String calleeBody = bodies.get(name);
            if (calleeBody == null) {
                continue;
            }
            reached.append(calleeBody);
            collectCalls(calleeBody, queue);
        }
        return reached.toString();
    }

    private static void collectCalls(String body, java.util.Deque<String> into) {
        Matcher calls = CALL.matcher(body);
        while (calls.find()) {
            into.add(calls.group(1));
        }
    }

    @Test
    void noScheduledMethodHoldsATransactionAcrossACallToAnotherSystem() throws IOException {
        List<String> offenders = new ArrayList<>();
        Path root = Path.of("src/main/java");
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                java.util.Map<String, String> bodies = methodBodiesOf(source);
                Matcher matcher = SCHEDULED_METHOD.matcher(source);
                while (matcher.find()) {
                    if (!matcher.group(1).contains("@Transactional")) {
                        continue;
                    }
                    String reachable = reachableFrom(bodyOf(source, matcher.end() - 1), bodies);
                    for (String client : EXTERNAL_CLIENTS) {
                        if (reachable.contains(client + ".")) {
                            offenders.add(root.relativize(file) + " -> " + client);
                            break;
                        }
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "A scheduled transaction must not wait on another system while holding what it holds:\n"
                        + String.join("\n", offenders));
    }

    /** The method body from its opening brace to the matching close, so nested braces do not end it early. */
    private static String bodyOf(String source, int openBraceIndex) {
        int depth = 0;
        for (int i = openBraceIndex; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openBraceIndex, i + 1);
                }
            }
        }
        return source.substring(openBraceIndex);
    }
}
