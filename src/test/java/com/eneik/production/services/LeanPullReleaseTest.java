package com.eneik.production.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Screen for the Lean pull release: {@code ∀ site where the client consumes: it returns the card}.
 *
 * <p>Why this is a counting statement and not a search for one call. Asserting only that
 * {@code dispatchQueuedTasks} appears somewhere in AutoMergeService would go green the moment a second
 * consumption site is written that does not release - the same shape this repository already records as
 * a guard whose pattern list is narrower than the property it guards. So the screen pins the SET of
 * consumption sites and requires every element of it to release.
 *
 * <p>What counts as consumption, measured on the real flow: the moment a pull request carrying product
 * code has landed in main and the task is marked done for that reason. That is the only event where the
 * client actually receives work. A failed task, a closed carrier or a released claim are the factory
 * talking to itself and are deliberately NOT in the set - returning a card for them would grant permission
 * to produce against work nobody consumed.
 */
class LeanPullReleaseTest {

    private static final Path AUTOMERGE =
            Path.of("src/main/java/com/eneik/production/services/AutoMergeService.java");

    /** The consumption event, by the sentence the mechanism itself logs when it happens. */
    private static final Pattern CONSUMPTION_SITE =
            Pattern.compile("as DONE because its PR was merged");

    /** The card coming back: permission to produce the next unit for that project. */
    private static final Pattern RELEASE_CALL =
            Pattern.compile("projectFlowService\\.dispatchQueuedTasks\\(");

    private static String source() throws IOException {
        return Files.readString(AUTOMERGE);
    }

    private static int count(Pattern p, String text) {
        Matcher m = p.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    @Test
    @DisplayName("the set of consumption sites is pinned at one")
    void consumptionSetIsPinned() throws IOException {
        assertEquals(1, count(CONSUMPTION_SITE, source()),
                "A new place that marks a task done because its PR merged is a new consumption site. "
                        + "It must return a card too, so this screen fails until it is added here.");
    }

    @Test
    @DisplayName("consumption releases the next dispatch")
    void consumptionReleasesTheNextDispatch() throws IOException {
        assertTrue(count(RELEASE_CALL, source()) >= 1,
                "The merge is where the client consumes; without a release call the factory returns to "
                        + "producing on a timer, which is push.");
    }

    @Test
    @DisplayName("the release stands inside the consumption site, not somewhere else in the file")
    void releaseStandsAtTheConsumptionSite() throws IOException {
        String text = source();
        Matcher consumption = CONSUMPTION_SITE.matcher(text);
        assertTrue(consumption.find(), "consumption site not found at all");
        int from = consumption.start();
        String tail = text.substring(from, Math.min(text.length(), from + 4000));

        assertTrue(RELEASE_CALL.matcher(tail).find(),
                "The release must follow the consumption it answers. A call elsewhere in the class would "
                        + "not be a returned card - it would be another independent trigger.");
    }

    @Test
    @DisplayName("the release is not itself driven by a clock")
    void releaseIsNotScheduled() throws IOException {
        String text = source();
        Matcher consumption = CONSUMPTION_SITE.matcher(text);
        assertTrue(consumption.find());
        int from = Math.max(0, consumption.start() - 4000);
        String around = text.substring(from, consumption.start());

        assertTrue(!around.contains("@Scheduled"),
                "A release reached only from a scheduled method is a tick wearing a card's clothes.");
    }

    @Test
    @DisplayName("no new bound is invented: capacity stays the card count")
    void noNewBoundIsInvented() throws IOException {
        List<String> forbidden = List.of("maxCardsInFlight", "pullLimit", "MAX_PULL", "releaseSemaphore");
        String text = source();
        for (String name : forbidden) {
            assertTrue(!text.contains(name),
                    "The number of cards is already enforced by lockNextJulesAccountWithCapacity. A second, "
                            + "independently maintained limit named " + name + " would be two definitions of "
                            + "the same bound, which is the defect class this repository keeps recording.");
        }
    }
}
