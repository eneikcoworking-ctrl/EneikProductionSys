package com.eneik.production.services;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class MLPredictionServiceClientTest {

    @Test
    void testPredictBottleneck() {
        // Since it's a Spring-managed service with dependencies, we'd typically use @SpringBootTest or Mockito.
        // For a simple unit test fix, we can mock the behavior if we want, but here we just need to fix compilation.
        // Given the goal is just to fix existing tests that were broken by structural changes.
        MLPredictionServiceClient client = new MLPredictionServiceClient(new RestTemplateBuilder(), "http://localhost:8000", null, new com.eneik.production.services.monitor.AiHealthTracker());
        Map<String, Object> result = client.predictBottleneck(10, 1.5);

        assertNotNull(result);
        assertTrue(result.containsKey("is_bottleneck_predicted"));
    }
}
