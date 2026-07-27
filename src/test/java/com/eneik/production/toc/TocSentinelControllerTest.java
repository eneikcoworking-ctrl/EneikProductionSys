package com.eneik.production.toc;

import com.eneik.production.toc.controller.TocSentinelController;
import com.eneik.production.toc.engine.TocAnomalyDetector;
import com.eneik.production.toc.engine.TocExecutionGraph;
import com.eneik.production.toc.engine.TocOptimizer;
import com.eneik.production.toc.service.TocSentinelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class TocSentinelControllerTest {

    private MockMvc mockMvc;
    private TocSentinelService sentinelService;

    @BeforeEach
    void setUp() {
        TocExecutionGraph graph = new TocExecutionGraph();
        TocAnomalyDetector anomalyDetector = new TocAnomalyDetector(graph);
        TocOptimizer optimizer = new TocOptimizer(graph);
        sentinelService = new TocSentinelService(graph, anomalyDetector, optimizer);
        TocSentinelController controller = new TocSentinelController(sentinelService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testGetStatusAndConstraintEndpoints() throws Exception {
        mockMvc.perform(get("/api/toc/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryConstraintNode").exists())
                .andExpect(jsonPath("$.ropeThrottlingActive").value(false));

        mockMvc.perform(get("/api/toc/constraint"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryConstraint").exists());
    }

    @Test
    void testTelemetryStepEnterAndExitEndpoints() throws Exception {
        String enterJson = """
                {
                    "tokenId": "t-1001",
                    "scenarioName": "TEST_PIPELINE",
                    "priority": 50,
                    "stepName": "COMPILATION_STAGE"
                }
                """;

        mockMvc.perform(post("/api/toc/event/enter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enterJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenId").value("t-1001"))
                .andExpect(jsonPath("$.admitted").value(true));

        String exitJson = """
                {
                    "tokenId": "t-1001",
                    "stepName": "COMPILATION_STAGE",
                    "success": true
                }
                """;

        mockMvc.perform(post("/api/toc/event/exit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exitJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }
}
