package com.eneik.production.services.jules;

import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JulesApiClientTest {

    @Test
    void createSessionNormalizesGithubUrlToJulesSourceName() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/sessions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"name\":\"sessions/123\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            SystemSettingsService settings = mock(SystemSettingsService.class);
            when(settings.effectiveBoolean("jules_enabled")).thenReturn(true);
            JulesApiClient client = new JulesApiClient(objectMapper,
                    "http://localhost:" + server.getAddress().getPort(),
                    settings);

            JulesApiClient.CreateSessionResult result = client.createSessionDetailed(
                    "https://github.com/eneikdru/test-fortieth.git",
                    "Implement the task",
                    "Role context",
                    "test-key",
                    "Compiler Task",
                    "main");

            assertThat(result.sessionName()).isEqualTo("sessions/123");
            JsonNode json = objectMapper.readTree(requestBody.get());
            assertThat(json.path("sourceContext").path("source").asText())
                    .isEqualTo("sources/github/eneikdru/test-fortieth");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sourceNamePassesThroughWhenAlreadyInJulesFormat() {
        assertThat(JulesApiClient.toJulesSourceName("sources/github/org/repo"))
                .isEqualTo("sources/github/org/repo");
    }
}
