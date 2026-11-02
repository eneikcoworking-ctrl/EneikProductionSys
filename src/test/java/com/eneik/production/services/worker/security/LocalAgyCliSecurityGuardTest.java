package com.eneik.production.services.worker.security;

import com.eneik.production.services.worker.worktree.ExclusionaryReasonViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class LocalAgyCliSecurityGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {"remotecli", "remotecli2", "REMOTECLI", "RemoteCli2", " remotecli "})
    @DisplayName("Raz Deontic Gate (D006): strictly forbids L1/L2 factory identities from executing product worker tasks")
    void testForbiddenL1L2Identities(String forbiddenUser) {
        assertThrows(ExclusionaryReasonViolationException.class, () ->
                AgyWorkerSecurityGuard.validateWorkerExecutionUser(forbiddenUser)
        );
        assertFalse(AgyWorkerSecurityGuard.isPermitted(forbiddenUser));
    }

    @ParameterizedTest
    @ValueSource(strings = {"remotecli3", "root", "REMOTECLI3", "ROOT"})
    @DisplayName("Raz Deontic Gate (D006): permits dedicated product worker profiles (remotecli3, root)")
    void testPermittedProductWorkerIdentities(String permittedUser) {
        assertDoesNotThrow(() ->
                AgyWorkerSecurityGuard.validateWorkerExecutionUser(permittedUser)
        );
        assertTrue(AgyWorkerSecurityGuard.isPermitted(permittedUser));
    }

    @Test
    @DisplayName("Validates null or blank users produce IllegalArgumentException")
    void testNullOrBlankValidation() {
        assertThrows(IllegalArgumentException.class, () ->
                AgyWorkerSecurityGuard.validateWorkerExecutionUser(null)
        );
        assertThrows(IllegalArgumentException.class, () ->
                AgyWorkerSecurityGuard.validateWorkerExecutionUser("   ")
        );
    }
}
