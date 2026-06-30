package com.eneik.production.services;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class MLPredictionServiceClientTest {

    @Test
    void testPredictBottleneck() {
        MLPredictionServiceClient client = new MLPredictionServiceClient();
        Map<String, Object> result = client.predictBottleneck(10, 1.5);

        assertNotNull(result);
        assertEquals(0.15, result.get("risk_score"));
        assertEquals(false, result.get("is_bottleneck_predicted"));
    }
}
