package com.eneik.production.services.jules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P12–P15 of §12: three different facts arrive under one HTTP status, and each must land in its own place.
 *
 * <p>Measured 2026-08-29, an hour after §10 shipped. Every Jules refusal was 400 FAILED_PRECONDITION - 45 in
 * fifteen minutes - and every one was filed as REQUEST_REJECTED, the factory's own defect. It is not. On
 * session creation FAILED_PRECONDITION means this account is already running its maximum concurrent
 * sessions: a statement about the account, and a transient one that ends as those sessions finish.
 *
 * <p>Neither prior behaviour was right. Before §10 it led to api_blocked - a cooldown of tens to hundreds of
 * minutes for a condition lasting minutes. §10 then swept it into "our request was bad", where it says
 * nothing about anything. §10's split was correct and simply not fine enough.
 *
 * <p>What it actually is: the evidence channel AccountRepository's own comment declares does not exist -
 * "DispatchOutcome has no distinct 'rejected because too many concurrent sessions' signal, so there is no
 * real evidence channel to falsify a belief about it". It exists.
 */
class JulesRefusalKindsTest {

    private JulesApiClient.CreateSessionResult result(int status, String body) {
        return new JulesApiClient.CreateSessionResult(null, status, body);
    }

    private static final String INVALID_ARGUMENT =
            "{\"error\":{\"code\":400,\"message\":\"Request contains an invalid argument.\","
                    + "\"status\":\"INVALID_ARGUMENT\"}}";
    private static final String FAILED_PRECONDITION =
            "{\"error\":{\"code\":400,\"message\":\"Precondition check failed.\","
                    + "\"status\":\"FAILED_PRECONDITION\"}}";

    @Test
    void unspecifiedPreconditionIsNotTheFactorysDefect() {
        var r = result(400, FAILED_PRECONDITION);

        assertTrue(r.preconditionUnspecified(), "P12: it names no condition, so it claims nothing");
        assertFalse(r.requestRejected(), "and it must stop being filed as our own malformed request");
    }

    @Test
    void unspecifiedPreconditionIsNotGroundsToBlockTheAccount() {
        // P13. Being busy is not being broken. Before §10 this reached api_blocked and cost hours.
        assertFalse(result(400, FAILED_PRECONDITION).apiPreconditionOrAuthorizationBlocked());
    }

    @Test
    void aMalformedRequestIsStillTheFactorysDefect() {
        // P15, first half: §12 narrows §10, it does not undo it.
        var r = result(400, INVALID_ARGUMENT);

        assertTrue(r.requestRejected());
        assertFalse(r.preconditionUnspecified());
        assertFalse(r.apiPreconditionOrAuthorizationBlocked());
    }

    @Test
    void authorizationRefusalsAreStillTheAccountsFault() {
        // P15, second half.
        assertTrue(result(401, "{\"error\":\"unauthorized\"}").apiPreconditionOrAuthorizationBlocked());
        assertTrue(result(403, "{\"error\":\"forbidden\"}").apiPreconditionOrAuthorizationBlocked());
        assertFalse(result(401, "{\"error\":\"unauthorized\"}").requestRejected());
        assertFalse(result(401, "{\"error\":\"unauthorized\"}").preconditionUnspecified());
    }

    @Test
    void anAuthorizationWordInTheBodyOutranksTheDeclaredStatus() {
        // A 400 that says permission denied is about the account whatever else it claims.
        var r = result(400, "{\"error\":{\"status\":\"FAILED_PRECONDITION\",\"detail\":\"PERMISSION_DENIED\"}}");

        assertFalse(r.preconditionUnspecified());
        assertFalse(r.requestRejected());
        assertTrue(r.apiPreconditionOrAuthorizationBlocked());
    }

    @Test
    void aNamedAccountSidePreconditionIsStillTheAccountsFault() {
        // The case an earlier incident recorded and JulesDispatchServiceTest still asserts: this status
        // also arrives naming a condition that IS the account's. Retracting the capacity reading must not
        // take that with it.
        var r = result(400, "{\"error\":{\"status\":\"FAILED_PRECONDITION\","
                + "\"message\":\"Repository access is not ready\"}}");

        assertTrue(r.apiPreconditionOrAuthorizationBlocked());
        assertFalse(r.preconditionUnspecified(), "a named condition is not an unnamed one");
    }

    @Test
    void aRefusalWithNoDeclaredStatusFallsThroughToNeitherNewKind() {
        // Silence is not evidence: an unlabelled 400 is not claimed by either new predicate, so it keeps
        // whatever the older classification made of it rather than being guessed into a bucket.
        var r = result(400, "{\"error\":{\"code\":400}}");

        assertFalse(r.requestRejected());
        assertFalse(r.preconditionUnspecified());
    }
}
