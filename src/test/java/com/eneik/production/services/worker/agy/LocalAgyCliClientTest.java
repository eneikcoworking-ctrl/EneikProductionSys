package com.eneik.production.services.worker.agy;

import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.worker.deepseek.DeepSeekResponse;
import com.eneik.production.services.worker.deepseek.ScopeStatus;
import com.eneik.production.services.worker.worktree.ExclusionaryReasonViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalAgyCliClientTest {

    private LocalAgyCommandBuilder commandBuilder;
    private LocalAgyOutputParser outputParser;
    private SystemSettingsService settingsService;
    private LocalAgyCliClient client;

    @BeforeEach
    void setUp() {
        commandBuilder = new LocalAgyCommandBuilder();
        outputParser = new LocalAgyOutputParser();
        settingsService = mock(SystemSettingsService.class);
        client = new LocalAgyCliClient(commandBuilder, outputParser, settingsService);
    }

    @Test
    @DisplayName("Executes local agy CLI with correct user, model and parses response")
    void testSuccessfulExecution() throws Exception {
        when(settingsService.effectiveValue("worker_local_agy_user")).thenReturn("remotecli3");

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setTitle("Implement Service");
        task.setCynefinDomain("clear");
        task.setFileScope("[\"src/main/A.java\"]");
        task.setDescription("Add A class");

        String mockOutput = """
                ```json
                {
                  "summary": "Created A",
                  "rationale": "Clear domain task",
                  "scope_status": "SUFFICIENT",
                  "missing_files": [],
                  "files": [
                    {
                      "path": "src/main/A.java",
                      "operation": "CREATE",
                      "mode": "FULL_CONTENT",
                      "content": "public class A {}",
                      "patch_blocks": []
                    }
                  ]
                }
                ```
                """;

        AtomicReference<java.util.List<String>> executedCommand = new AtomicReference<>();
        client.setProcessRunner((command, timeout) -> {
            executedCommand.set(command);
            return new LocalAgyCliClient.ProcessResult(0, mockOutput, "");
        });

        DeepSeekResponse response = client.generateSolution(task, "", null, null);

        assertNotNull(response);
        assertEquals("Created A", response.summary());
        assertEquals(ScopeStatus.SUFFICIENT, response.scopeStatus());
        assertEquals(1, response.files().size());

        // Verify command arguments
        assertNotNull(executedCommand.get());
        assertTrue(executedCommand.get().contains("sudo"));
        assertTrue(executedCommand.get().contains("remotecli3"));
        assertTrue(executedCommand.get().contains("--model"));
        assertTrue(executedCommand.get().contains("gemini-3.8-flash-medium"));
    }

    @Test
    @DisplayName("Raz Deontic Gate: setting forbidden user (remotecli) aborts execution immediately")
    void testForbiddenUserAbortsExecution() {
        when(settingsService.effectiveValue("worker_local_agy_user")).thenReturn("remotecli");

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());

        assertThrows(ExclusionaryReasonViolationException.class, () ->
                client.generateSolution(task, "", null, null)
        );
    }
}
