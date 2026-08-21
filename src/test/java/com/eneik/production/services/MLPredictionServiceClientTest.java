package com.eneik.production.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class MLPredictionServiceClientTest {

    private MLPredictionServiceClient client;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        com.eneik.production.services.settings.SystemSettingsService settingsService =
                mock(com.eneik.production.services.settings.SystemSettingsService.class);
        when(settingsService.effectiveBoolean("gemini_enabled")).thenReturn(true);
        when(settingsService.effectiveValue("gemini_api_key")).thenReturn("test-key");

        client = new MLPredictionServiceClient(new RestTemplateBuilder(), "http://localhost:8000",
                settingsService, new com.eneik.production.services.monitor.AiHealthTracker(), null);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void testPredictBottleneck() {
        // Deliberately its OWN client instance, with no MockRestServiceServer bound - this test relies on a
        // real (failing) connection attempt to an unreachable URL, caught internally by checkSystemRisk's
        // own fallback, not on a mocked response. Sharing the class-level `client`/`mockServer` here would
        // make this call subject to the OTHER tests' explicit expectations instead of hitting the network.
        MLPredictionServiceClient standaloneClient = new MLPredictionServiceClient(new RestTemplateBuilder(),
                "http://localhost:8000", null, new com.eneik.production.services.monitor.AiHealthTracker(), null);
        Map<String, Object> result = standaloneClient.predictBottleneck(10, 1.5);
        assertNotNull(result);
        assertTrue(result.containsKey("is_bottleneck_predicted"));
    }

    // --- chatWithTools (Phase 5) --------------------------------------------------------------------------

    @Test
    void chatWithToolsReturnsImmediateTextWhenNoFunctionCallOnFirstRound() {
        mockServer.expect(requestTo("http://localhost:8000/api/v1/assistant/chat"))
                .andRespond(withSuccess("{\"text\":\"hello\",\"functionCall\":null,\"contents\":[]}", MediaType.APPLICATION_JSON));

        AtomicInteger executorCalls = new AtomicInteger();
        MLPredictionServiceClient.ToolLoopResult result = client.chatWithTools(
                "prompt", "system", List.of(),
                (name, args) -> { executorCalls.incrementAndGet(); return Map.of(); },
                (round, name, toolResult) -> true, 8);

        assertEquals("hello", result.finalText());
        assertEquals(1, result.roundsUsed());
        assertFalse(result.hitRoundCap());
        assertEquals(0, executorCalls.get());
        mockServer.verify();
    }

    @Test
    void chatWithToolsExecutesToolAndContinuesWhenContinuationSaysYes() {
        mockServer.expect(requestTo("http://localhost:8000/api/v1/assistant/chat"))
                .andRespond(withSuccess(
                        "{\"text\":null,\"functionCall\":{\"name\":\"readEvidence\",\"args\":{}},\"contents\":[{\"role\":\"model\",\"parts\":[]}]}",
                        MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo("http://localhost:8000/api/v1/assistant/chat"))
                .andRespond(withSuccess("{\"text\":\"final answer\",\"functionCall\":null,\"contents\":[]}", MediaType.APPLICATION_JSON));

        AtomicInteger executorCalls = new AtomicInteger();
        MLPredictionServiceClient.ToolLoopResult result = client.chatWithTools(
                "prompt", "system", List.of(new MLPredictionServiceClient.ToolDeclaration("readEvidence", "desc", Map.of())),
                (name, args) -> { executorCalls.incrementAndGet(); return Map.of("nodeIds", List.of("a", "b")); },
                (round, name, toolResult) -> true, 8);

        assertEquals("final answer", result.finalText());
        assertEquals(2, result.roundsUsed());
        assertEquals(1, executorCalls.get());
        mockServer.verify();
    }

    @Test
    void chatWithToolsStopsEarlyWithTextOnlyWrapupWhenContinuationSaysNo() {
        mockServer.expect(requestTo("http://localhost:8000/api/v1/assistant/chat"))
                .andRespond(withSuccess(
                        "{\"text\":null,\"functionCall\":{\"name\":\"readEvidence\",\"args\":{}},\"contents\":[{\"role\":\"model\",\"parts\":[]}]}",
                        MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo("http://localhost:8000/api/v1/assistant/chat"))
                .andRespond(withSuccess("{\"text\":\"wrapping up now\",\"functionCall\":null,\"contents\":[]}", MediaType.APPLICATION_JSON));

        MLPredictionServiceClient.ToolLoopResult result = client.chatWithTools(
                "prompt", "system", List.of(new MLPredictionServiceClient.ToolDeclaration("readEvidence", "desc", Map.of())),
                (name, args) -> Map.of(),
                (round, name, toolResult) -> false, // stop after this one tool round
                8);

        assertEquals("wrapping up now", result.finalText());
        assertEquals(2, result.roundsUsed()); // 1 tool round + 1 forced text-only wrap-up round
        assertFalse(result.hitRoundCap());
        mockServer.verify();
    }

    @Test
    void chatWithToolsHitsRoundCapWhenModelKeepsCallingTools() {
        for (int i = 0; i < 3; i++) {
            mockServer.expect(requestTo("http://localhost:8000/api/v1/assistant/chat"))
                    .andRespond(withSuccess(
                            "{\"text\":null,\"functionCall\":{\"name\":\"readEvidence\",\"args\":{}},\"contents\":[]}",
                            MediaType.APPLICATION_JSON));
        }

        MLPredictionServiceClient.ToolLoopResult result = client.chatWithTools(
                "prompt", "system", List.of(new MLPredictionServiceClient.ToolDeclaration("readEvidence", "desc", Map.of())),
                (name, args) -> Map.of(),
                (round, name, toolResult) -> true, // never wants to stop
                3);

        assertTrue(result.hitRoundCap());
        assertEquals(3, result.roundsUsed());
        mockServer.verify();
    }

    @Test
    void chatWithToolsShortCircuitsWhenGeminiDisabled() {
        com.eneik.production.services.settings.SystemSettingsService disabledSettings =
                mock(com.eneik.production.services.settings.SystemSettingsService.class);
        when(disabledSettings.effectiveBoolean("gemini_enabled")).thenReturn(false);
        MLPredictionServiceClient disabledClient = new MLPredictionServiceClient(new RestTemplateBuilder(),
                "http://localhost:8000", disabledSettings, new com.eneik.production.services.monitor.AiHealthTracker(), null);

        MLPredictionServiceClient.ToolLoopResult result = disabledClient.chatWithTools(
                "prompt", "system", List.of(), (name, args) -> Map.of(), (r, n, t) -> true, 8);

        assertEquals(0, result.roundsUsed());
        assertFalse(result.hitRoundCap());
        assertTrue(result.finalText().contains("disabled"));
    }
}
