package com.eneik.production.services.onboarding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RepositoryStackAnalyzerTest {

    @Test
    void extractsOwnerFromHttpsUrl() {
        assertEquals("eneikdru", RepositoryStackAnalyzer.ownerFromRepositoryUrl("https://github.com/eneikdru/test-fortieth"));
    }

    @Test
    void extractsOwnerFromHttpsUrlWithTrailingSlash() {
        assertEquals("eneikcoworking-ctrl", RepositoryStackAnalyzer.ownerFromRepositoryUrl("https://github.com/eneikcoworking-ctrl/test-thirty-eighth/"));
    }

    @Test
    void returnsNullForBlankOrNullUrl() {
        assertNull(RepositoryStackAnalyzer.ownerFromRepositoryUrl(null));
        assertNull(RepositoryStackAnalyzer.ownerFromRepositoryUrl(""));
        assertNull(RepositoryStackAnalyzer.ownerFromRepositoryUrl("   "));
    }

    @Test
    void returnsNullForUrlWithoutPath() {
        assertNull(RepositoryStackAnalyzer.ownerFromRepositoryUrl("https://github.com"));
    }
}
