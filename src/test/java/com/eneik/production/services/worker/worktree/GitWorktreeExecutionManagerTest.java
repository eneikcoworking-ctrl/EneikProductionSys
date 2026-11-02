package com.eneik.production.services.worker.worktree;

import com.eneik.production.services.settings.SystemSettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GitWorktreeExecutionManagerTest {

    @Test
    @DisplayName("resolveGitHubToken returns token from SystemSettingsService when available")
    void testResolveTokenFromSettingsService() {
        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        when(settingsService.isKnownKey("github_token")).thenReturn(true);
        when(settingsService.effectiveValue("github_token")).thenReturn("ghp_test123456789");

        GitWorktreeExecutionManager manager = new GitWorktreeExecutionManager();
        manager.setSettingsService(settingsService);

        String token = manager.resolveGitHubToken();
        assertEquals("ghp_test123456789", token);
    }

    @Test
    @DisplayName("resolveGitHubToken falls back to env when db token is empty or null")
    void testResolveTokenFallbackWhenDbEmpty() {
        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        when(settingsService.isKnownKey("github_token")).thenReturn(true);
        when(settingsService.effectiveValue("github_token")).thenReturn("   ");

        GitWorktreeExecutionManager manager = new GitWorktreeExecutionManager();
        manager.setSettingsService(settingsService);

        String token = manager.resolveGitHubToken();
        // If GITHUB_TOKEN is in env, it returns that, otherwise null
        String envToken = System.getenv("GITHUB_TOKEN");
        if (envToken != null && !envToken.isBlank()) {
            assertEquals(envToken.trim(), token);
        } else {
            assertNull(token);
        }
    }
}
