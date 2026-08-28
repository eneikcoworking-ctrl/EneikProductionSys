package com.eneik.production.services.jules;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P10 and P11 of §11: a request composed by a builder this factory no longer has is not sent.
 *
 * <p>§10 stopped new requests from carrying an artifact the factory did not author. It did not reach the
 * ones already written: a request is composed at dispatch time and STORED in its carrier task, so a task
 * created before that change still holds the old text. The rule went where a request is composed; the
 * action is sending, and a database sits between the two - invariant 10, and the same shape as "the guard
 * stands at the merge boundary while the leak is at the brief boundary" from §9.
 *
 * <p>Measured 2026-08-29: 31 tasks carried the removed builder's trace, 30 already terminal, one live at
 * 1 788 060 characters. It was dispatched, refused, and - thanks to §10 - cost no account. It would have
 * gone on being dispatched until its attempts ran out.
 *
 * <p>Not a length threshold, deliberately. There is nothing to derive one from, and §6 item 9 records what
 * an assigned number costs. A trace is exact, needs no calibration, and expires by itself.
 */
class RemovedBuilderTraceTest {

    @Test
    void aDescriptionCarryingARemovedBuildersTraceIsRecognised() {
        String old = "===== PR #0 (sourceIndex 0) =====\nPR under review: x\n\nDiff to review:\ndiff --git ...";

        assertNotNull(JulesDispatchService.removedBuilderTrace(old),
                "this text can only have been written by the builder §10 removed");
    }

    @Test
    void aDescriptionFromTheCurrentBuilderIsNotTouched() {
        // P11. A guard that also stops live work is worse than the defect it was written for.
        String current = "===== PR #0 (sourceIndex 0) =====\nPR under review: https://github.com/o/r/pull/9\n\n"
                + "Read that pull request's own diff in this repository to review it - it is not copied here.";

        assertNull(JulesDispatchService.removedBuilderTrace(current));
        assertNull(JulesDispatchService.removedBuilderTrace(null));
        assertNull(JulesDispatchService.removedBuilderTrace("Role: BARCAN-TAG-00\nDeliver the missing change."));
    }

    @Test
    void everyTraceIsAbsentFromTheCurrentSources() throws IOException {
        // What makes an entry a trace of something GONE rather than a filter on live output. If a current
        // builder can still emit it, the entry would start refusing the factory's own fresh work.
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (String trace : JulesDispatchService.REMOVED_BUILDER_TRACES) {
                    // The declaration itself is where the trace is written down; every other occurrence
                    // would mean some builder still produces it.
                    if (file.getFileName().toString().equals("JulesDispatchService.java")) {
                        continue;
                    }
                    assertTrue(!source.contains("\"" + trace + "\""),
                            file + " still emits the trace \"" + trace + "\" - it is not a removed builder");
                }
            }
        }
    }

    @Test
    void theTraceListIsNotEmptyWhileItIsStillDeclared() {
        // A trace that outlives its cause is muda that reads as permission. When no task carries one any
        // more, the entry is to be deleted - and this test is where that decision surfaces.
        assertTrue(!JulesDispatchService.REMOVED_BUILDER_TRACES.isEmpty(),
                "an empty list means the mechanism itself should go, not sit here doing nothing");
    }
}
