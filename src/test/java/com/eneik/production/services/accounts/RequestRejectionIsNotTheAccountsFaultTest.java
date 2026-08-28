package com.eneik.production.services.accounts;

import com.eneik.production.services.jules.JulesApiClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P8 of §10: a refusal caused by the content of our own request is never charged to an account.
 *
 * <p>Measured 2026-08-29. Jules answered 400 INVALID_ARGUMENT to a review-fallback prompt of 1 788 060
 * characters. The classifier folded 400 in with 401 and 403, so the refusal was recorded as
 * PRECONDITION_BLOCKED - a statement about the account - and with an escalation threshold of two, one
 * malformed request burned three accounts in a single tick. The operator freed them by hand; the factory
 * blocked them again within two minutes, because the same task was still queued and the same request was
 * still malformed. Consecutive block counts on live accounts stood at 12 and 13.
 *
 * <p>401 and 403 genuinely are about the account and must keep their old classification - the fix is a
 * distinction, not a relaxation. An authorization word in the body wins over the status code, because a
 * 400 that says "permission denied" is about the account whatever the number claims.
 */
class RequestRejectionIsNotTheAccountsFaultTest {

    private JulesApiClient.CreateSessionResult result(int status, String body) {
        return new JulesApiClient.CreateSessionResult(null, status, body);
    }

    @Test
    void anInvalidArgumentIsAboutTheRequest() {
        var r = result(400, "{\"error\":{\"code\":400,\"message\":\"Request contains an invalid argument.\","
                + "\"status\":\"INVALID_ARGUMENT\"}}");

        assertTrue(r.requestRejected(), "400 INVALID_ARGUMENT is the factory's own defect");
        assertFalse(r.apiPreconditionOrAuthorizationBlocked(),
                "and it must no longer reach the branch that charges an account");
    }

    @Test
    void unauthorizedAndForbiddenStayTheAccountsFault() {
        assertTrue(result(401, "{\"error\":\"unauthorized\"}").apiPreconditionOrAuthorizationBlocked());
        assertTrue(result(403, "{\"error\":\"forbidden\"}").apiPreconditionOrAuthorizationBlocked());
        assertFalse(result(401, "{\"error\":\"unauthorized\"}").requestRejected());
        assertFalse(result(403, "{\"error\":\"forbidden\"}").requestRejected());
    }

    @Test
    void anAuthorizationWordInTheBodyWinsOverA400StatusCode() {
        var r = result(400, "{\"error\":{\"status\":\"PERMISSION_DENIED\"}}");

        assertFalse(r.requestRejected(), "this one really is about the account");
        assertTrue(r.apiPreconditionOrAuthorizationBlocked());
    }

    @Test
    void theVocabularyHasAWordForOurOwnFault() {
        // Without this the classifier has nowhere to put the fact and files it against the nearest object
        // it can name - the account. Ashby: the response must have variety enough for the disturbance.
        assertTrue(java.util.Arrays.stream(AccountHealthService.DispatchOutcome.values())
                        .anyMatch(o -> o.name().equals("REQUEST_REJECTED")),
                "the outcome vocabulary must be able to say 'our request was bad'");
    }
}
