package com.eneik.production.models.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The device rather than one more inspection.
 *
 * A task whose completion carries no statement cannot be judged by anything downstream, which is how
 * twenty-six tasks of one project reached done in August 2026 without a single one being asked whether it
 * did what it promised. Measured at the source: of the fourteen places that build a task, thirteen set no
 * criterion at all - TechnicalLeadCompiler was the only one that did, and only because
 * validateDefinitionOfReady step 7 refuses a wishlist without one.
 *
 * This fails the build instead of reporting at runtime, deliberately. A running factory must not be stopped
 * by a check, and a defect that cannot be built costs less than one that has to be noticed.
 */
class TaskAcceptanceCriteriaGuardTest {

    // Only a bound against a pathological file. The rule is "before it is saved", and the save is what
    // ends the scan - a line count standing in for that rule is how this guard reported its own false
    // positive on TechnicalLeadCompiler, whose construction and save are 117 lines apart.
    private static final int BLOCK_SCAN_LIMIT = 400;

    @Test
    @DisplayName("every place that builds a task states what would refute it, before saving it")
    void everyTaskConstructionCarriesItsFalsifier() throws IOException {
        List<String> unguarded = new ArrayList<>();

        try (Stream<Path> sources = Files.walk(Path.of("src", "main", "java"))) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).contains("new TaskEntity()") && !statesCriterion(lines, i)) {
                        unguarded.add(source + ":" + (i + 1));
                    }
                }
            }
        }

        assertThat(unguarded)
                .as("a task built without acceptance criteria cannot be judged when it claims to be done - "
                        + "call setAcceptanceCriteria with the statement this task can be refuted against")
                .isEmpty();
    }

    // Reads forward only as far as the save that ends this construction. Scanning the whole file would let a
    // guarded task vouch for an unguarded one beside it, which is the substitution this guard exists to stop.
    private boolean statesCriterion(List<String> lines, int construction) {
        for (int i = construction; i < Math.min(lines.size(), construction + BLOCK_SCAN_LIMIT); i++) {
            if (lines.get(i).contains("setAcceptanceCriteria")) {
                return true;
            }
            if (i > construction && lines.get(i).contains("taskRepository.save(")) {
                return false;
            }
        }
        return false;
    }
}
