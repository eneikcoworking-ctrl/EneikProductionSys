package com.eneik.production.services.stitch;

import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase B (design/QA acceptance redesign, 2026-08-04): coverage for the new design-system JSON-RPC
 * methods, using the same local HttpServer pattern already proven for JulesApiClientTest - StitchClient's
 * own callTool wraps every tool call in one JSON-RPC envelope
 * {"jsonrpc":"2.0","id":N,"result":{"content":[{"text":"<json-encoded-result>"}]}}, mirrored here exactly.
 */
class StitchClientTest {

    private StitchClient clientFor(HttpServer server) {
        SystemSettingsService settings = mock(SystemSettingsService.class);
        when(settings.effectiveValue("stitch_api_key")).thenReturn("test-key");
        return new StitchClient(settings, new ObjectMapper(),
                "http://localhost:" + server.getAddress().getPort(), 30);
    }

    @Test
    void createDesignSystemParsesBareIdFromResourceName() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            String inner = "{\"name\":\"projects/p1/designSystems/ds-42\"}";
            String envelope = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[{\"text\":"
                    + new ObjectMapper().writeValueAsString(inner) + "}]}}";
            byte[] body = envelope.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            StitchClient client = clientFor(server);
            StitchClient.DesignSystemResult result = client.createDesignSystem("p1", "make it modern");
            assertThat(result.available()).isTrue();
            assertThat(result.designSystemId()).isEqualTo("ds-42");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void createDesignSystemIsUnavailableWhenResponseHasNoName() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            String envelope = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[{\"text\":\"{}\"}]}}";
            byte[] body = envelope.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            StitchClient client = clientFor(server);
            StitchClient.DesignSystemResult result = client.createDesignSystem("p1", "prompt");
            assertThat(result.available()).isFalse();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void applyDesignSystemSendsCorrectArgumentsAndParsesSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        java.util.concurrent.atomic.AtomicReference<String> requestBody = new java.util.concurrent.atomic.AtomicReference<>();
        server.createContext("/", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String envelope = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[{\"text\":\"{\\\"applied\\\":true}\"}]}}";
            byte[] body = envelope.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            StitchClient client = clientFor(server);
            StitchClient.ApplyDesignSystemResult result =
                    client.applyDesignSystem("p1", "ds-42", List.of("screen-1", "screen-2"));

            assertThat(result.available()).isTrue();
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode sent = objectMapper.readTree(requestBody.get());
            assertThat(sent.path("params").path("name").asText()).isEqualTo("apply_design_system");
            assertThat(sent.path("params").path("arguments").path("designSystemId").asText()).isEqualTo("ds-42");
            assertThat(sent.path("params").path("arguments").path("screenIds")).hasSize(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void listDesignSystemsParsesBareIdsFromEachResourceName() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            String inner = "{\"designSystems\":[{\"name\":\"projects/p1/designSystems/ds-1\"},"
                    + "{\"name\":\"projects/p1/designSystems/ds-2\"}]}";
            String envelope = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[{\"text\":"
                    + new ObjectMapper().writeValueAsString(inner) + "}]}}";
            byte[] body = envelope.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            StitchClient client = clientFor(server);
            List<String> ids = client.listDesignSystems("p1");
            assertThat(ids).containsExactly("ds-1", "ds-2");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingApiKeySkipsTheHttpCallEntirely() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/", exchange -> {
            callCount.incrementAndGet();
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.start();
        try {
            SystemSettingsService settings = mock(SystemSettingsService.class);
            when(settings.effectiveValue("stitch_api_key")).thenReturn("");
            StitchClient client = new StitchClient(settings, new ObjectMapper(),
                    "http://localhost:" + server.getAddress().getPort(), 30);

            StitchClient.DesignSystemResult result = client.createDesignSystem("p1", "prompt");

            assertThat(result.available()).isFalse();
            assertThat(callCount).hasValue(0);
        } finally {
            server.stop(0);
        }
    }
}
