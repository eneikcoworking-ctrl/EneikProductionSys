package com.eneik.production.services;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class MLPredictionServiceClientTest {

    @Test
    void testPredictBottleneck() {
        MLPredictionServiceClient client = new MLPredictionServiceClient(
                new RestTemplateBuilder(),
                "http://127.0.0.1:1"
        );
        Map<String, Object> result = client.predictBottleneck(10, 1.5);

        assertNotNull(result);
        assertFalse((Boolean) result.get("is_bottleneck_predicted"));
    }
}
