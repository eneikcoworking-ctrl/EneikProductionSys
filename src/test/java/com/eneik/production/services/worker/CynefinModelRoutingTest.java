package com.eneik.production.services.worker;

import com.eneik.production.services.worker.deepseek.DeepSeekApiClient;
import com.eneik.production.services.worker.deepseek.DeepSeekPatchParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CynefinModelRoutingTest {

    private final DeepSeekApiClient client = new DeepSeekApiClient(
            new ObjectMapper(),
            new DeepSeekPatchParser(),
            null
    );

    @ParameterizedTest
    @CsvSource({
            "clear, deepseek-chat",
            "simple, deepseek-chat",
            "complicated, deepseek-chat",
            "COMPLICATED, deepseek-chat",
            "complex, deepseek-reasoner",
            "COMPLEX, deepseek-reasoner",
            "'', deepseek-chat"
    })
    @DisplayName("Cynefin Framework Routing: routes domain to optimal DeepSeek model")
    void testCynefinModelRouting(String domain, String expectedModel) {
        String model = client.resolveModel(domain);
        assertEquals(expectedModel, model);
    }
}
