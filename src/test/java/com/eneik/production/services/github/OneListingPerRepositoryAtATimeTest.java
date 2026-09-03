package com.eneik.production.services.github;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Charter invariant 10 applied to shared external capacity (rule 8.17).
 *
 * <p>The pull request list is one fact about one repository, and the schedulers that need it run at the
 * same time - measured on the live circuit, AUTOMERGE cycles on three different scheduling threads at once
 * and `GET /repos/.../pulls` the largest attributable spend of the window at 52 calls, while the token's
 * hourly limit sat exhausted and every merge cycle was skipped. The limit is GitHub's and not ours to
 * raise; not spending it several times over on the same answer is ours.
 */
class OneListingPerRepositoryAtATimeTest {

    private final GitHubPullRequestService service = serviceWithItsOwnRegistry();

    /** CALLS_REAL_METHODS leaves final fields uninitialised, so the registry is supplied here. */
    private static GitHubPullRequestService serviceWithItsOwnRegistry() {
        GitHubPullRequestService instance =
                mock(GitHubPullRequestService.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        org.springframework.test.util.ReflectionTestUtils.setField(instance, "listingsInFlight",
                new java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<?>>());
        return instance;
    }

    @Test
    void callersArrivingDuringOneListingShareIt() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch firstIsInside = new CountDownLatch(1);
        CountDownLatch secondHasJoined = new CountDownLatch(1);
        AtomicReference<String> firstAnswer = new AtomicReference<>();
        AtomicReference<String> secondAnswer = new AtomicReference<>();

        Thread first = new Thread(() -> {
            try {
                firstAnswer.set(service.sharingOneInFlight("owner/repo:open", () -> {
                    calls.incrementAndGet();
                    firstIsInside.countDown();
                    secondHasJoined.await();
                    return "the listing";
                }));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }, "first");
        first.start();
        assertTrue(firstIsInside.await(10, TimeUnit.SECONDS), "the first listing never started");

        Thread second = new Thread(() -> {
            try {
                secondAnswer.set(service.sharingOneInFlight("owner/repo:open", () -> {
                    calls.incrementAndGet();
                    return "a second listing";
                }));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }, "second");
        second.start();
        while (second.getState() != Thread.State.WAITING && second.getState() != Thread.State.TERMINATED) {
            Thread.onSpinWait();
        }
        secondHasJoined.countDown();
        first.join(10_000);
        second.join(10_000);

        assertEquals(1, calls.get(), "the same listing was fetched twice at the same time");
        assertEquals("the listing", firstAnswer.get());
        assertEquals("the listing", secondAnswer.get());
    }

    @Test
    void adifferentKeyIsNotShared() throws Exception {
        // The reverse case: sharing across repositories or states would hand a caller an answer about
        // something else entirely.
        AtomicInteger calls = new AtomicInteger();
        service.sharingOneInFlight("owner/repo:open", calls::incrementAndGet);
        service.sharingOneInFlight("owner/repo:closed", calls::incrementAndGet);

        assertEquals(2, calls.get());
    }

    @Test
    void nothingIsRetainedAfterTheListingEnds() throws Exception {
        // A retained listing could serve a membership the repository has already left - the exact reason
        // the open state is excluded from the incremental watermark.
        AtomicInteger calls = new AtomicInteger();
        service.sharingOneInFlight("owner/repo:open", calls::incrementAndGet);
        service.sharingOneInFlight("owner/repo:open", calls::incrementAndGet);

        assertEquals(2, calls.get());
    }
}
