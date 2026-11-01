package com.eneik.production.services.worker;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.services.worker.worktree.GitWorktreeExecutionManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BareMirrorPoolLifecycleTest {

    @Test
    @DisplayName("Bare Mirror Pool: resolves orchestrator repository for factory tasks")
    void testResolveOrchestratorRepository(@TempDir Path tempDir) {
        Path mockOrch = tempDir.resolve("EneikProductionSys");
        GitWorktreeExecutionManager manager = new GitWorktreeExecutionManager(
                tempDir.resolve("worktrees").toString(),
                tempDir.resolve("mirrors").toString(),
                mockOrch.toString()
        );

        ProjectEntity factoryProject = new ProjectEntity();
        factoryProject.setName("ORCHESTRATOR_SYSTEM");

        Path resolved = manager.resolveBaseRepository(factoryProject);
        assertNotNull(resolved);
    }

    @Test
    @DisplayName("Bare Mirror Pool: initializes bare mirror directory for client projects")
    void testClientBareMirrorResolution(@TempDir Path tempDir) {
        Path mirrorsDir = tempDir.resolve("mirrors");
        GitWorktreeExecutionManager manager = new GitWorktreeExecutionManager(
                tempDir.resolve("worktrees").toString(),
                mirrorsDir.toString(),
                tempDir.resolve("EneikProductionSys").toString()
        );

        ProjectEntity clientProject = new ProjectEntity();
        clientProject.setName("client-repo-fortieth");

        Path mirrorPath = manager.getOrCreateBareMirror(clientProject);
        assertNotNull(mirrorPath);
        assertTrue(mirrorPath.toString().contains("client-repo-fortieth.git"));
        assertTrue(Files.exists(mirrorPath));
    }

    @Test
    @DisplayName("Deterministic Cleanup: worktree directory is wiped during teardown")
    void testWorktreeDeterministicTeardown(@TempDir Path tempDir) throws IOException {
        GitWorktreeExecutionManager manager = new GitWorktreeExecutionManager();
        Path mockWorktree = tempDir.resolve("task-" + UUID.randomUUID());
        Files.createDirectories(mockWorktree);
        Files.writeString(mockWorktree.resolve("test.txt"), "hello");

        assertTrue(Files.exists(mockWorktree));
        manager.removeWorktree(null, mockWorktree);
        assertFalse(Files.exists(mockWorktree), "Worktree directory must be completely wiped");
    }
}
