package com.eneik.production.controllers;

import com.eneik.production.dto.agents.AgentOrchestratorSnapshotDTO;
import com.eneik.production.repositories.AgentAccountRepository;
import com.eneik.production.repositories.AgentTaskRepository;
import com.eneik.production.models.persistence.AgentTaskStatus;
import com.eneik.production.services.AgentOrchestratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentOrchestratorControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentTaskRepository taskRepository;

    @Autowired
    private AgentAccountRepository accountRepository;

    @Autowired
    private AgentOrchestratorService orchestratorService;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
        accountRepository.deleteAll();
        orchestratorService.seedDefaultAccounts();
    }

    @Test
    void createRequirementDecomposesAndAutoClaimsAgentTasks() throws Exception {
        String json = """
                {
                  "title": "Agent workflow smoke test",
                  "description": "Create frontend dashboard, backend API, docker deployment, security permissions, QA verification, and persistence schema."
                }
                """;

        mockMvc.perform(post("/api/v1/agents/requirements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.accounts").value(7))
                .andExpect(jsonPath("$.summary.todo").value(0))
                .andExpect(jsonPath("$.summary.claimed").value(10))
                .andExpect(jsonPath("$.tasks", hasSize(10)))
                .andExpect(jsonPath("$.tasks[?(@.agentTag == 'BARCAN-TAG-07')].claimedByAccountCode", hasItem("ACC-06")))
                .andExpect(jsonPath("$.tasks[?(@.agentTag == 'BARCAN-TAG-06')].claimedByAccountCode", hasItem("ACC-05")))
                .andExpect(jsonPath("$.tasks[?(@.agentTag == 'BARCAN-TAG-03')].claimedByAccountCode", hasItem("ACC-04")))
                .andExpect(jsonPath("$.tasks[?(@.agentTag == 'BARCAN-TAG-02')].claimedByAccountCode", hasItem("ACC-02")));
    }

    @Test
    void accountRemainsActiveUntilAllAssignedTasksAreFinished() {
        AgentOrchestratorSnapshotDTO snapshot = orchestratorService.createRequirement(
                "Architecture review",
                "Review architecture and execution order."
        );

        List<UUID> accountTaskIds = snapshot.getTasks().stream()
                .filter(task -> "ACC-01".equals(task.getClaimedByAccountCode()))
                .map(task -> task.getId())
                .toList();

        assertThat(accountTaskIds).hasSize(2);

        orchestratorService.updateTaskStatus(accountTaskIds.get(0), AgentTaskStatus.DONE);
        assertThat(accountRepository.findByAccountCode("ACC-01").orElseThrow().getStatus()).isEqualTo("ACTIVE");

        orchestratorService.updateTaskStatus(accountTaskIds.get(1), AgentTaskStatus.DONE);
        assertThat(accountRepository.findByAccountCode("ACC-01").orElseThrow().getStatus()).isEqualTo("IDLE");
    }
}
