package com.eneik.production.controllers.dashboard;

import com.eneik.production.dto.dashboard.AcceptanceReadinessDto;
import com.eneik.production.dto.dashboard.CommandDashboardDto;
import com.eneik.production.security.ApiAuthorizationInterceptor;
import com.eneik.production.services.dashboard.CommandDashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full Spring MVC tests for CommandDashboardController (Section XXXIII / XXXIV,
 * NUEL_BELNAP_03_TRUTH_STATUS_TABLE / D012 Belnap tri-state,
 * ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK / D010 Goldman).
 *
 * <p>Verifies that GET /api/projects/{projectId}/command-dashboard correctly transmits
 * clientAcceptanceWitnessed and tri-state readiness (ready / not ready / unknown), ensuring
 * unknown is distinctly preserved as null rather than collapsed to false or true.
 */
@WebMvcTest(CommandDashboardController.class)
class CommandDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommandDashboardService commandDashboardService;

    @MockBean
    private ApiAuthorizationInterceptor apiAuthorizationInterceptor;

    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        when(apiAuthorizationInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("GET /api/projects/{projectId}/command-dashboard: returns 200 with readiness 'ready' and clientAcceptanceWitnessed=true")
    void getDashboardReturnsReadyWithWitnessedClientAcceptance() throws Exception {
        AcceptanceReadinessDto readiness = new AcceptanceReadinessDto(
                "ready",
                true,
                true,
                true,
                true,
                true,
                Collections.emptyList(),
                "READY",
                "border-success",
                "Delivered and accepted"
        );
        CommandDashboardDto dto = new CommandDashboardDto(
                List.of(Map.of("id", "w1")),
                List.of(Map.of("id", "t1")),
                Collections.emptyList(),
                List.of(Map.of("id", "pr1")),
                List.of(Map.of("ci_status", true)),
                Collections.emptyList(),
                readiness,
                Map.of("tasks", "available")
        );

        when(commandDashboardService.getDashboard(projectId)).thenReturn(dto);

        mockMvc.perform(get("/api/projects/{projectId}/command-dashboard", projectId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.acceptanceReadiness.readiness", is("ready")))
                .andExpect(jsonPath("$.acceptanceReadiness.clientAcceptanceWitnessed", is(true)))
                .andExpect(jsonPath("$.acceptanceReadiness.allTasksDone", is(true)))
                .andExpect(jsonPath("$.acceptanceReadiness.allQualityGatesPassed", is(true)))
                .andExpect(jsonPath("$.acceptanceReadiness.allPrsMerged", is(true)))
                .andExpect(jsonPath("$.acceptanceReadiness.githubAccessHealthy", is(true)))
                .andExpect(jsonPath("$.acceptanceReadiness.statusLabel", is("READY")))
                .andExpect(jsonPath("$.acceptanceReadiness.uiColorToken", is("border-success")))
                .andExpect(jsonPath("$.acceptanceReadiness.unmetConditions", hasSize(0)))
                .andExpect(jsonPath("$.wishlist", hasSize(1)))
                .andExpect(jsonPath("$.tasks", hasSize(1)))
                .andExpect(jsonPath("$.dataSourcesStatus.tasks", is("available")));
    }

    @Test
    @DisplayName("GET /api/projects/{projectId}/command-dashboard: returns 200 with readiness 'not ready' and clientAcceptanceWitnessed=false")
    void getDashboardReturnsNotReadyWhenClientAcceptanceNotWitnessed() throws Exception {
        AcceptanceReadinessDto readiness = new AcceptanceReadinessDto(
                "not ready",
                true,
                true,
                true,
                true,
                false,
                List.of("No client acceptance traversal recorded (scope built, awaiting client acceptance)"),
                "NOT READY",
                "border-error",
                null
        );
        CommandDashboardDto dto = new CommandDashboardDto(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                readiness,
                Map.of("traversals", "available")
        );

        when(commandDashboardService.getDashboard(projectId)).thenReturn(dto);

        mockMvc.perform(get("/api/projects/{projectId}/command-dashboard", projectId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptanceReadiness.readiness", is("not ready")))
                .andExpect(jsonPath("$.acceptanceReadiness.clientAcceptanceWitnessed", is(false)))
                .andExpect(jsonPath("$.acceptanceReadiness.statusLabel", is("NOT READY")))
                .andExpect(jsonPath("$.acceptanceReadiness.uiColorToken", is("border-error")))
                .andExpect(jsonPath("$.acceptanceReadiness.unmetConditions", hasSize(1)))
                .andExpect(jsonPath("$.acceptanceReadiness.unmetConditions[0]",
                        containsString("No client acceptance traversal recorded")));
    }

    @Test
    @DisplayName("GET /api/projects/{projectId}/command-dashboard: returns 200 with readiness 'unknown' and clientAcceptanceWitnessed=null (Belnap tri-state)")
    void getDashboardReturnsUnknownWhenClientAcceptanceCannotBeMeasured() throws Exception {
        AcceptanceReadinessDto readiness = new AcceptanceReadinessDto(
                "unknown",
                true,
                true,
                true,
                true,
                null,
                List.of("Client acceptance traversal measurement unavailable"),
                "UNKNOWN",
                "border-warning",
                null
        );
        CommandDashboardDto dto = new CommandDashboardDto(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                readiness,
                Map.of("traversals", "failed")
        );

        when(commandDashboardService.getDashboard(projectId)).thenReturn(dto);

        mockMvc.perform(get("/api/projects/{projectId}/command-dashboard", projectId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptanceReadiness.readiness", is("unknown")))
                .andExpect(jsonPath("$.acceptanceReadiness.clientAcceptanceWitnessed", nullValue()))
                .andExpect(jsonPath("$.acceptanceReadiness.statusLabel", is("UNKNOWN")))
                .andExpect(jsonPath("$.acceptanceReadiness.uiColorToken", is("border-warning")));
    }

    @Test
    @DisplayName("GET /api/projects/{projectId}/command-dashboard: returns 400 Bad Request for non-UUID projectId")
    void getDashboardReturnsBadRequestForInvalidUuid() throws Exception {
        mockMvc.perform(get("/api/projects/{projectId}/command-dashboard", "invalid-uuid-format")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
