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
 * Structural test for the operator's standing directive: this factory is autonomous, and a branch whose
 * only exit is a person is a dead end (2026-08-29, plan §4.34, restated by the operator three times).
 *
 * <p>Why structural rather than behavioural. A state that waits for a human is never wrong in any single
 * execution - the proposal is written, the log line is printed, the record is stored, and every one of
 * those steps succeeds. It is wrong in the limit: nothing ever leaves that state, because the actor it
 * names does not exist here. No behavioural test can see that, which is exactly the shape this repository
 * has already had to fix twice - the human-review list that accumulated eighteen entries, eight of them
 * about tasks that were already done, and the compiler carrier that "went to human review" while six
 * briefs sat in `compiling` forever.
 *
 * <p>Scope is what the factory SAYS ABOUT ITS OWN ACTIONS - message literals it emits. Prose that merely
 * mentions a human as a point of comparison ("Jules reads the exact same document a human reviewer
 * would") is untouched, and so are comments recording history; the forbidden thing is a runtime statement
 * that an action is taken, or awaited, by a person.
 */
class NoHumanInTheLoopInvariantTest {

    /** Phrases that name a person as the actor of a factory action, inside an emitted string literal. */
    private static final Pattern HUMAN_ACTOR = Pattern.compile(
            "(?i)\"[^\"]*\\b("
                    + "needs human|need human|needs a human|awaiting (a )?human|human review|human decision"
                    + "|by (explicit )?(human|operator) action|manual intervention|requires (a )?human"
                    + ")\\b[^\"]*\"");

    @Test
    void noEmittedMessageMakesAPersonTheActorOfAFactoryAction() throws IOException {
        List<String> offenders = new ArrayList<>();
        Path root = Path.of("src/main/java");
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (String line : source.split("\r?\n")) {
                    String trimmed = line.stripLeading();
                    if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                        continue; // history and rationale, not an emitted claim
                    }
                    Matcher matcher = HUMAN_ACTOR.matcher(line);
                    if (matcher.find()) {
                        offenders.add(root.relativize(file) + ": " + trimmed);
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "A factory with no human cannot emit a message making one the actor:\n" + String.join("\n", offenders));
    }
}
