package com.eneik.production.invariants;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P16–P18 of §13: an account selector may not order by a marker its own selection advances in its favour.
 *
 * <p>Measured 2026-08-29: thirty four consecutive Jules refusals, every one through the same account, while
 * six others sat idle - the freshest of them three hours stale. No sessions were open anywhere, because
 * refusals create none, so the first ordering key tied for everyone and the second decided. That second key
 * was {@code last_heartbeat DESC}, and ClaimService writes last_heartbeat at the moment of claiming. Being
 * chosen therefore made an account the freshest, hence chosen again, forever.
 *
 * <p>Charter invariant 7 in its exact converse: it requires that a "repeat if something new appeared"
 * mechanism compare against a marker it cannot advance. Here the mechanism advanced the marker itself, in
 * the direction that reselected it. Six sevenths of the factory's capacity existed and could not be
 * reached - Ashby's variety present but unavailable to the regulator.
 *
 * <p>Structural because the property is about a native query's text, and because a behavioural test would
 * need a live database to observe an ordering. The fix is one word in three places, and this test is what
 * stops the fourth from being written with the old direction.
 */
class AccountSelectionFairnessTest {

    private static final Path REPOSITORY = Path.of(
            "src/main/java/com/eneik/production/repositories/AccountRepository.java");

    /**
     * Every ordering by the heartbeat marker, in either direction.
     *
     * <p>Matched on the ordered term itself rather than on the line carrying ORDER BY: the capacity query
     * spans several lines and its second key sits on its own, so a line-wise search for "ORDER BY" missed
     * it and this test failed on correct code the first time it ran. The detector was the defect.
     */
    private List<String> orderByHeartbeatLines(String source) {
        List<String> hits = new ArrayList<>();
        for (String line : source.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.contains("last_heartbeat ASC") || trimmed.contains("last_heartbeat DESC")) {
                hits.add(trimmed);
            }
        }
        return hits;
    }

    @Test
    void noMultiRowSelectorPrefersTheMostRecentlyTouchedAccount() throws IOException {
        String source = Files.readString(REPOSITORY);

        for (String line : orderByHeartbeatLines(source)) {
            // The by-name selector is exempt by irrelevance, not by permission: it filters on a single
            // account name, so at most one row can be ordered and the direction decides nothing. Named
            // here rather than pattern-matched so that adding a second exemption is a deliberate act.
            boolean byNameSelector = line.contains("a.last_heartbeat DESC LIMIT 1")
                    && source.contains("lockAccountByNameWithCapacity");
            if (byNameSelector && line.contains("DESC")) {
                continue;
            }
            assertTrue(!line.contains("DESC"),
                    "P16: this selector chooses among several accounts and orders by a marker that "
                            + "ClaimService advances when the account is chosen - DESC makes the choice "
                            + "reselect itself forever. Line: " + line);
        }
    }

    @Test
    void everyMultiRowSelectorOrdersLeastRecentlyTouchedFirst() throws IOException {
        String source = Files.readString(REPOSITORY);
        long ascending = orderByHeartbeatLines(source).stream().filter(l -> l.contains("ASC")).count();

        // P17: three selectors return one of many. If this count drops, a rotation has been lost; if it
        // rises, a new selector was written - either way the ordering deserves a fresh argument.
        assertEquals(3, ascending,
                "three selectors choose among many accounts and all three must move the chosen one to "
                        + "the back of the queue");
    }

    @Test
    void fitnessFiltersAreUnchangedByTheOrdering() throws IOException {
        // P18. The ordering decides the queue, never eligibility. If a fix to fairness quietly widened who
        // may be returned, it would be a different change wearing this one's justification.
        String source = Files.readString(REPOSITORY);

        assertTrue(source.contains("a.enabled = true"), "enabled still filters");
        assertTrue(source.contains("a.api_key IS NOT NULL"), "a usable key still filters");
        assertTrue(source.contains("a.status NOT IN ('decommissioned', 'offline', 'daily_limited', 'api_blocked')"),
                "the non-dispatchable statuses still filter");
        assertTrue(source.contains("COALESCE(a.sessions_dispatched_today, 0) < COALESCE(a.estimated_daily_capacity"),
                "the daily-capacity belief still filters");
    }
}
