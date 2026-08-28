package com.eneik.production.invariants;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural test for the invariant "the cost of a question is proportional to its answer, not to the
 * accumulated history" (2026-08-28, §5b of ENGINEERING_PHILOSOPHY_ACTION_PLAN.md).
 *
 * <p>Why it is structural. The pattern it forbids -
 * {@code repository.findAll().stream().filter(p)} - is never wrong in a single execution: it returns the
 * right answer every time. It is wrong in the derivative. A factory that runs forever accumulates history
 * forever, so any per-tick cost proportional to the table is unbounded by construction, and the system
 * eventually spends on looking at its own past the resource that was meant for delivery. No behavioural
 * test can see that, because at every individual moment the behaviour is correct.
 *
 * <p>It was measured before it was forbidden. On 2026-08-27 three schedulers running every 60 seconds
 * performed sixteen full reads of growing tables per tick: AutoMergeService ten, JulesDispatchService
 * four, ContinuousOrchestrationService two. The charter had already written the rule down - "an unbounded
 * read of a whole table on a hot path is the very form that once caused an out-of-memory here" - and it
 * was honoured in exactly one place and broken in seventy-five. A rule that is written and not enforced is
 * a rule that is not there, which is what this test changes.
 *
 * <p>Scope is deliberately narrow, so that it forbids only what was argued for. It applies to classes that
 * schedule work more often than daily, and only to tables that grow with the work done. Bounded tables -
 * projects, roles, accounts, system_settings - keep {@code findAll()}, because for them the cost IS
 * proportional to the answer.
 */
class ScheduledQueryCostInvariantTest {

    /** Tables that grow with every unit of work the factory performs, by their repository field name. */
    private static final Set<String> GROWING = Set.of(
            "taskRepository",
            "julesSessionRepository",
            "prReviewRepository",
            "wishlistRepository",
            "projectEventLogRepository",
            "evidenceNodeRepository",
            "leverObservationRepository",
            "claimRepository",
            "taskConflictRepository");

    /**
     * The one place allowed to break the rule, named rather than pattern-matched, so that adding a second
     * one is a deliberate act with an argument attached.
     *
     * <p>EvidenceCoherenceService filters evidence nodes by {@code node.sourceType()}, which is a DERIVED
     * value computed in Java from the node's identity - not a column - so the predicate cannot be pushed
     * into a query at all. The plan's remedy for that case is a bounded window, and the number in it must
     * be derived from the question being asked rather than guessed. Here the question is a RATE
     * (accepted over total for a source type), and a window silently changes what the rate means. That is
     * a diagnosis, not an edit, and §5c-K of the plan holds it until someone measures it.
     */
    private static final Set<String> EXEMPT = Set.of("EvidenceCoherenceService.java");

    private static final Pattern FREQUENT_SCHEDULE = Pattern.compile(
            "@Scheduled\\((fixedRate|fixedDelay)|@Scheduled\\(cron[^)]*\\*/\\d");

    @Test
    void noScheduledPathReadsAWholeGrowingTable() throws IOException {
        Path root = Path.of("src/main/java");
        assertTrue(Files.isDirectory(root), "sources must be readable from the module root");

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (EXEMPT.contains(file.getFileName().toString())) {
                    continue;
                }
                String source = Files.readString(file);
                if (!FREQUENT_SCHEDULE.matcher(source).find()) {
                    continue;
                }
                String[] lines = source.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    for (String repository : GROWING) {
                        if (lines[i].contains(repository + ".findAll()")) {
                            violations.add(file + ":" + (i + 1) + "  " + repository + ".findAll()");
                        }
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "A class that schedules work more often than daily read an entire growing table. "
                        + "Push the predicate into the query (findBy<P>), or bound the read with a window "
                        + "whose size is derived from the question. If neither is possible, add the file to "
                        + "EXEMPT with the argument written out, as EvidenceCoherenceService has.\n"
                        + String.join("\n", violations));
    }

    @Test
    void theExemptionIsStillNeeded() throws IOException {
        // An exemption nobody needs any more is muda that reads as permission. If this fails, the site was
        // fixed and the entry should be deleted rather than kept "just in case".
        Path exempt = Path.of("src/main/java/com/eneik/production/services/coherence/EvidenceCoherenceService.java");
        assertTrue(Files.exists(exempt), "the exempt file must exist, or the exemption is stale");
        assertTrue(Files.readString(exempt).contains("evidenceNodeRepository.findAll()"),
                "EvidenceCoherenceService no longer reads the whole table - remove it from EXEMPT");
    }
}
