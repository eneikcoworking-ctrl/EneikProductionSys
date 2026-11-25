package com.eneik.production.services.worker.verification;

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

class LocalVerificationRunnerTest {

    @Test
    @DisplayName("Gate 4C (D006/D010): LocalVerificationRunner never overwrites or blocks static/index.html for external-UI-owned project (knowledge-base-crie)")
    void externalUiOwnedProjectSkipsFrontendSyncAndAbstractionLock(@TempDir Path worktree) throws Exception {
        // Create both frontend/src/App.svelte and aesthetic-master's static/index.html
        Path svelteDir = worktree.resolve("frontend/src");
        Files.createDirectories(svelteDir);
        Files.writeString(svelteDir.resolve("App.svelte"), "<main>Legacy Svelte 31.08.36</main>", StandardCharsets.UTF_8);
        Files.writeString(worktree.resolve("frontend/index.html"), "<html><body>Legacy Svelte 31.08.36</body></html>", StandardCharsets.UTF_8);

        Path staticDir = worktree.resolve("src/main/resources/static");
        Files.createDirectories(staticDir);
        String aestheticMasterHtml = "<!DOCTYPE html><html><body>aesthetic-master canonical UI</body></html>";
        Path staticIndex = staticDir.resolve("index.html");
        Files.writeString(staticIndex, aestheticMasterHtml, StandardCharsets.UTF_8);

        SystemSettingsService settingsService = new SystemSettingsService(mock(JdbcTemplate.class), mock(Environment.class));
        LocalVerificationRunner runner = new LocalVerificationRunner(new SimulationStubDetector());
        runner.setSettingsService(settingsService);

        ProjectEntity project = new ProjectEntity();
        project.setName("knowledge-base-crie");
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setFileScope("[\"docs/api-contract.md\"]");

        VerificationResult result = runner.runVerification(worktree, task);

        assertTrue(result.passed(), "External-UI-owned project must NOT fail verifyLevelOfAbstractionLock on static/index.html: " + result.output());
        assertEquals(aestheticMasterHtml, Files.readString(staticIndex, StandardCharsets.UTF_8),
                "LocalVerificationRunner must NEVER overwrite aesthetic-master static/index.html");
    }
}
