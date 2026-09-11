package com.eneik.production.services.onboarding;

import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void missingGithubTokenReturnsUncheckedProfileWithTriStateStatus() {
        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        when(settingsService.effectiveValue("github_token")).thenReturn(null);

        RepositoryStackAnalyzer analyzer = new RepositoryStackAnalyzer(
                "eneik", "https://api.github.com", settingsService, new ObjectMapper());

        RepositoryStackAnalyzer.AnalysisResult result = analyzer.analyze("test-repo");

        StackProfile profile = result.profile();
        assertEquals("Unknown", profile.primaryLanguage());
        assertEquals("не проверено", profile.framework());
        assertEquals("не проверено", profile.database());
        assertEquals(InspectionStatus.UNCHECKED, profile.hasCI());
        assertEquals(InspectionStatus.UNCHECKED, profile.hasTests());
        assertEquals(InspectionStatus.UNCHECKED, profile.isMonorepo());
        assertTrue(profile.hasCI().isUnchecked());
        assertTrue(profile.hasTests().isUnchecked());
        assertTrue(profile.isMonorepo().isUnchecked());
        assertTrue(profile.isUnchecked());
        assertEquals("GitHub token not configured.", profile.declaredPurpose());
        assertEquals("main", profile.defaultBranch());
        assertEquals(0, profile.totalFiles());
        assertEquals(0, profile.analyzedFiles());
        assertTrue(result.filesToScan().isEmpty());
    }

    @Test
    void blankGithubTokenReturnsUncheckedProfile() {
        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        when(settingsService.effectiveValue("github_token")).thenReturn("   ");

        RepositoryStackAnalyzer analyzer = new RepositoryStackAnalyzer(
                "eneik", "https://api.github.com", settingsService, new ObjectMapper());

        RepositoryStackAnalyzer.AnalysisResult result = analyzer.analyze("test-repo");

        assertTrue(result.profile().isUnchecked());
        assertEquals("не проверено", result.profile().framework());
        assertEquals("не проверено", result.profile().database());
        assertTrue(result.filesToScan().isEmpty());
    }
}
