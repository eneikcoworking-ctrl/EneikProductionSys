package com.eneik.production.services.worker;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.worker.deepseek.DeepSeekApiClient;
import com.eneik.production.services.worker.deepseek.DeepSeekPatchParser;
import com.eneik.production.services.worker.deepseek.DeepSeekResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeepSeekApiClientCacheTest {

    @Test
    @DisplayName("Context Caching Invariant: Ontological BARCAN-TAG Prefix must exceed 1024 tokens (~4000 chars)")
    void testOntologicalPrefixExceedsCachingThreshold() {
        String prefix = DeepSeekApiClient.getOntologicalCachePrefix();
        assertNotNull(prefix);
        // DeepSeek requires >= 1024 tokens for cache activation; 1 token ~ 3-4 chars
        assertTrue(prefix.length() > 3000, "BARCAN-TAG prefix length (" + prefix.length() + ") must comfortably exceed cache threshold");
        assertTrue(prefix.contains("BARCAN-TAG-00"));
        assertTrue(prefix.contains("BARCAN-TAG-12"));
        assertTrue(prefix.contains("RAZ DEONTIC EXCLUSIONARY REASON"));
        assertTrue(prefix.contains("ANTI-TRUNCATION GATE"));
    }

    @Test
    @DisplayName("Parser extracts cached_tokens from usage node")
    void testCachedTokensExtractedFromResponse() throws Exception {
        HttpClient mockHttpClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        SystemSettingsService mockSettings = mock(SystemSettingsService.class);

        when(mockSettings.effectiveValue("deepseek_base_url")).thenReturn("https://api.deepseek.com");
        when(mockSettings.effectiveValue("deepseek_api_key")).thenReturn("sk-test-key");

        String responseJson = """
                {
                  "id": "chatcmpl-test",
                  "choices": [
                    {
                      "message": {
                        "content": "{\\"summary\\": \\"test\\", \\"rationale\\": \\"BARCAN-02\\", \\"scope_status\\": \\"SUFFICIENT\\", \\"files\\": []}"
                      }
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 12500,
                    "completion_tokens": 150,
                    "prompt_cache_hit_tokens": 11200
                  }
                }
                """;

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(responseJson);
        doReturn(mockResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

        DeepSeekPatchParser parser = new DeepSeekPatchParser(new ObjectMapper());
        DeepSeekApiClient client = new DeepSeekApiClient(new ObjectMapper(), parser, mockSettings, mockHttpClient);

        TaskEntity task = new TaskEntity();
        task.setTitle("Cache Test Task");
        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-02");
        role.setDescription("Backend dev");
        task.setRole(role);
        task.setCynefinDomain("clear");

        AccountEntity account = new AccountEntity();
        account.setApiKey("sk-account-key");

        DeepSeekResponse result = client.generateSolution(task, "", account, null);

        assertNotNull(result);
        assertEquals(11200, result.cachedTokens(), "Cached tokens must match prompt_cache_hit_tokens");
        assertEquals(12500, result.promptTokens());
        assertEquals(150, result.completionTokens());
    }
}
