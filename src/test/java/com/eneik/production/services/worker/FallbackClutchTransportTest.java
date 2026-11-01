package com.eneik.production.services.worker;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.worker.deepseek.DeepSeekApiClient;
import com.eneik.production.services.worker.deepseek.DeepSeekPatchParser;
import com.eneik.production.services.worker.deepseek.DeepSeekResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FallbackClutchTransportTest {

    @Test
    @DisplayName("Fallback-Clutch: transparently fails over to fallback URL upon 5xx/timeout on primary")
    void testFallbackClutchActivation() throws Exception {
        HttpClient mockHttpClient = mock(HttpClient.class);
        HttpResponse<String> primaryFailResponse = mock(HttpResponse.class);
        HttpResponse<String> fallbackSuccessResponse = mock(HttpResponse.class);
        SystemSettingsService mockSettings = mock(SystemSettingsService.class);

        when(mockSettings.effectiveValue("deepseek_base_url")).thenReturn("https://api.deepseek.com");
        when(mockSettings.effectiveValue("deepseek_fallback_url")).thenReturn("https://openrouter.ai/api/v1");
        when(mockSettings.effectiveValue("deepseek_api_key")).thenReturn("sk-primary-key");
        when(mockSettings.effectiveValue("deepseek_fallback_api_key")).thenReturn("sk-fallback-key");

        when(primaryFailResponse.statusCode()).thenReturn(503);
        when(primaryFailResponse.body()).thenReturn("Service Unavailable");

        String validPayload = """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "{\\"summary\\": \\"fallback ok\\", \\"rationale\\": \\"BARCAN\\", \\"scope_status\\": \\"SUFFICIENT\\", \\"files\\": []}"
                      }
                    }
                  ],
                  "usage": { "prompt_tokens": 100, "completion_tokens": 50 }
                }
                """;
        when(fallbackSuccessResponse.statusCode()).thenReturn(200);
        when(fallbackSuccessResponse.body()).thenReturn(validPayload);

        // First call to primary fails with IOException(HTTP 503), second call to fallback succeeds
        when(mockHttpClient.send(any(HttpRequest.class), any()))
                .thenAnswer(invocation -> {
                    HttpRequest req = invocation.getArgument(0);
                    if (req.uri().toString().contains("api.deepseek.com")) {
                        throw new IOException("HTTP 503 Service Unavailable");
                    } else if (req.uri().toString().contains("openrouter.ai")) {
                        return fallbackSuccessResponse;
                    }
                    return primaryFailResponse;
                });

        DeepSeekPatchParser parser = new DeepSeekPatchParser(new ObjectMapper());
        DeepSeekApiClient client = new DeepSeekApiClient(new ObjectMapper(), parser, mockSettings, mockHttpClient);

        TaskEntity task = new TaskEntity();
        task.setTitle("Fallback Test Task");
        AccountEntity account = new AccountEntity();

        DeepSeekResponse res = client.generateSolution(task, "", account, null);

        assertNotNull(res);
        assertEquals("fallback ok", res.summary());
        // Verify mockHttpClient sent 2 requests (primary then fallback)
        verify(mockHttpClient, times(2)).send(any(), any());
    }
}
