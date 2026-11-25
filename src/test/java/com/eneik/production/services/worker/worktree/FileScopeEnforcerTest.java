package com.eneik.production.services.worker.worktree;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.services.settings.SystemSettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class FileScopeEnforcerTest {

    @Test
    @DisplayName("Gate 4C (D006): External-UI-owned project (knowledge-base-crie) blocks static/index.html modification even if in fileScope and rolls back worktree")
    void externalUiOwnedProjectBlocksStaticIndexHtmlEvenWhenInScope(@TempDir Path repoDir) throws Exception {
        initGitRepoWithStaticIndex(repoDir, "<html><body>aesthetic-master original UI</body></html>");

        SystemSettingsService settingsService = new SystemSettingsService(mock(JdbcTemplate.class), mock(Environment.class));
        FileScopeEnforcer enforcer = new FileScopeEnforcer();
        enforcer.setSettingsService(settingsService);

        ProjectEntity project = new ProjectEntity();
        project.setName("knowledge-base-crie");
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        // Even if task.fileScope explicitly names src/main/resources/static/index.html, Raz gate MUST forbid it
        task.setFileScope("[\"src/main/resources/static/index.html\", \"src/main/java/App.java\"]");

        Path indexHtml = repoDir.resolve("src/main/resources/static/index.html");
        Files.writeString(indexHtml, "<html><body>UNAUTHORIZED OVERWRITE</body></html>", StandardCharsets.UTF_8);

        ExclusionaryReasonViolationException ex = assertThrows(
                ExclusionaryReasonViolationException.class,
                () -> enforcer.enforce(repoDir, task)
        );
        assertTrue(ex.getMessage().contains("EXTERNAL_UI_OWNED_BY_AESTHETIC_MASTER"));
        assertTrue(ex.getViolatingFiles().contains("src/main/resources/static/index.html"));

        // Verify physical rollback restored aesthetic-master's original index.html
        String restored = Files.readString(indexHtml, StandardCharsets.UTF_8);
        assertEquals("<html><body>aesthetic-master original UI</body></html>", restored);
    }

    @Test
    @DisplayName("Gate 4C (D006): External-UI-owned project permits Java backend modifications while protecting static/index.html")
    void externalUiOwnedProjectPermitsBackendJavaChanges(@TempDir Path repoDir) throws Exception {
        initGitRepoWithStaticIndex(repoDir, "<html><body>aesthetic-master original UI</body></html>");

        SystemSettingsService settingsService = new SystemSettingsService(mock(JdbcTemplate.class), mock(Environment.class));
        FileScopeEnforcer enforcer = new FileScopeEnforcer();
        enforcer.setSettingsService(settingsService);

        ProjectEntity project = new ProjectEntity();
        project.setName("knowledge-base-crie");
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setFileScope("[\"src/main/java/App.java\"]");

        Path javaFile = repoDir.resolve("src/main/java/App.java");
        Files.writeString(javaFile, "public class App { int x = 42; }\n", StandardCharsets.UTF_8);

        assertDoesNotThrow(() -> enforcer.enforce(repoDir, task));
    }

    private void initGitRepoWithStaticIndex(Path repoDir, String indexContent) throws Exception {
        runGit(repoDir, "git", "init", "-b", "main");
        runGit(repoDir, "git", "config", "user.name", "Test");
        runGit(repoDir, "git", "config", "user.email", "test@example.com");

        Path staticDir = repoDir.resolve("src/main/resources/static");
        Files.createDirectories(staticDir);
        Files.writeString(staticDir.resolve("index.html"), indexContent, StandardCharsets.UTF_8);

        Path javaDir = repoDir.resolve("src/main/java");
        Files.createDirectories(javaDir);
        Files.writeString(javaDir.resolve("App.java"), "public class App {}\n", StandardCharsets.UTF_8);

        runGit(repoDir, "git", "add", ".");
        runGit(repoDir, "git", "commit", "-m", "initial");
    }

    private void runGit(Path dir, String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).start();
        int exit = p.waitFor();
        assertEquals(0, exit, "Command failed: " + String.join(" ", cmd));
    }
}
