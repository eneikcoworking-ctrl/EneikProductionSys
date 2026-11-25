package com.eneik.production.services.worker.worktree;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.services.settings.SystemSettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

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

    @Test
    @DisplayName("Gate 4A-2 (D013): Bare mirror fetch --prune never deletes active worker/* branch and commits preserve common parent with main")
    void concurrentMirrorFetchPreservesWorkerBranchAndCommonAncestry(@TempDir Path tempDir) throws Exception {
        // 1. Create a real bare remote "origin" with an initial commit on main
        Path remoteOrigin = tempDir.resolve("remote-origin.git");
        Path seedDir = tempDir.resolve("seed");
        Files.createDirectories(remoteOrigin);
        Files.createDirectories(seedDir);
        runGit(remoteOrigin, "git", "init", "--bare", "-b", "main");
        runGit(seedDir, "git", "init", "-b", "main");
        runGit(seedDir, "git", "config", "user.name", "Test");
        runGit(seedDir, "git", "config", "user.email", "test@example.com");
        Files.writeString(seedDir.resolve("README.md"), "# Seed\n", StandardCharsets.UTF_8);
        runGit(seedDir, "git", "add", ".");
        runGit(seedDir, "git", "commit", "-m", "initial commit");
        runGit(seedDir, "git", "remote", "add", "origin", remoteOrigin.toString());
        runGit(seedDir, "git", "push", "-u", "origin", "main");

        // 2. Initialize mirror via GitWorktreeExecutionManager
        Path worktreesDir = tempDir.resolve("worktrees");
        Path mirrorsDir = tempDir.resolve("mirrors");
        GitWorktreeExecutionManager manager = new GitWorktreeExecutionManager(
                worktreesDir.toString(), mirrorsDir.toString(), tempDir.toString());

        ProjectEntity project = new ProjectEntity();
        project.setName("knowledge-base-crie");
        project.setRepoUrl(remoteOrigin.toString());
        project.setDefaultBranch("main");

        Path mirrorPath = manager.getOrCreateBareMirror(project);
        UUID taskId = UUID.randomUUID();
        Path worktreePath = manager.createWorktree(taskId, mirrorPath, "main");
        try {
            // 3. Simulate another thread calling getOrCreateBareMirror (fetch origin --prune) while worktree is active
            manager.getOrCreateBareMirror(project);

            // 4. Verify worker/<taskId> ref was NOT pruned from mirror
            assertEquals(0, manager.runCommand(mirrorPath, 10, "git", "rev-parse", "--verify", "refs/heads/worker/" + taskId),
                    "worker/* branch must survive concurrent mirror fetch --prune");

            // 5. Commit in worktree and verify commit has real parent (not orphan parents=[]) and shares merge-base with main
            Files.writeString(worktreePath.resolve("Backend.java"), "class Backend {}\n", StandardCharsets.UTF_8);
            manager.commit(worktreePath, "feat: add backend");

            assertTrue(manager.hasCommonHistoryWithBase(worktreePath, "main"),
                    "Committed worktree HEAD must share common ancestor with origin/main");
            assertDoesNotThrow(() -> manager.verifyCommonHistoryWithBase(worktreePath, "main"));

            // Push to local bare remote succeeds
            assertDoesNotThrow(() -> manager.push(worktreePath, "worker/" + taskId));

            // 6. Falsification check: orphan commit MUST be detected as having no common history
            runGit(worktreePath, "git", "checkout", "--orphan", "orphan-test-branch");
            Files.writeString(worktreePath.resolve("Orphan.java"), "class Orphan {}\n", StandardCharsets.UTF_8);
            manager.commit(worktreePath, "orphan commit");
            assertFalse(manager.hasCommonHistoryWithBase(worktreePath, "main"),
                    "Orphan commit (parents=[]) MUST be rejected by hasCommonHistoryWithBase");
            assertThrows(IllegalStateException.class, () -> manager.verifyCommonHistoryWithBase(worktreePath, "main"));
        } finally {
            manager.removeWorktree(mirrorPath, worktreePath);
        }
    }

    private void runGit(Path dir, String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).start();
        int exit = p.waitFor();
        assertEquals(0, exit, "Command failed: " + String.join(" ", cmd));
    }
}
