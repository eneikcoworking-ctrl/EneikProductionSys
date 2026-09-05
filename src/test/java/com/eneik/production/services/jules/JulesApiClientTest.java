package com.eneik.production.services.jules;

import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
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
        server.createContext("/sources", exchange -> {
            byte[] body = """
                    {"sources":[{"name":"sources/github/eneikdru/test-fortieth"}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
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
    void sourcePreflightReturnsMissingWithoutPostingSession() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicInteger sessionPosts = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/sources", exchange -> {
            byte[] body = "{\"sources\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/sessions", exchange -> {
            sessionPosts.incrementAndGet();
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
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
                    "sources/github/eneikdru/test-fortieth",
                    "Implement the task",
                    "Role context",
                    "test-key",
                    "Compiler Task",
                    "main");

            assertThat(result.sessionName()).isNull();
            assertThat(result.statusCode()).isEqualTo(404);
            assertThat(result.sourceNotFound()).isTrue();
            assertThat(result.errorBody()).contains("jules_source_not_found");
            assertThat(sessionPosts).hasValue(0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sourcePreflightNon2xxRejectsWithoutPostingSession() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicInteger sessionPosts = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/sources", exchange -> {
            byte[] body = "{\"error\":{\"code\":500,\"message\":\"internal server error\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/sessions", exchange -> {
            sessionPosts.incrementAndGet();
            byte[] body = "{\"name\":\"sessions/should-not-reach-here\"}".getBytes(StandardCharsets.UTF_8);
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
                    "sources/github/eneikdru/test-fortieth",
                    "Implement the task",
                    "Role context",
                    "test-key",
                    "Compiler Task",
                    "main");

            assertThat(result.sessionName()).isNull();
            assertThat(result.statusCode()).isEqualTo(500);
            assertThat(result.sourceUnverified()).isTrue();
            assertThat(result.errorBody()).contains("jules_source_unverified");
            assertThat(result.errorBody()).contains("promptLength=");
            assertThat(result.errorBody()).contains("source=sources/github/eneikdru/test-fortieth");
            assertThat(result.errorBody()).contains("startingBranch=main");
            assertThat(sessionPosts).hasValue(0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void createSessionFailureRecordsPromptLengthSourceAndStartingBranch() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/sources", exchange -> {
            byte[] body = """
                    {"sources":[{"name":"sources/github/eneikdru/test-fortieth"}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/sessions", exchange -> {
            byte[] body = """
                    {"error":{"code":400,"message":"Precondition check failed.","status":"FAILED_PRECONDITION"}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, body.length);
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

            String taskDescription = "Implement the task";
            String roleContext = "Role context";
            String expectedPrompt = taskDescription + "\n\nContext:\n" + roleContext;
            int expectedPromptLength = expectedPrompt.length();

            JulesApiClient.CreateSessionResult result = client.createSessionDetailed(
                    "sources/github/eneikdru/test-fortieth",
                    taskDescription,
                    roleContext,
                    "test-key",
                    "Compiler Task",
                    "main");

            assertThat(result.sessionName()).isNull();
            assertThat(result.statusCode()).isEqualTo(400);
            assertThat(result.promptLength()).isEqualTo(expectedPromptLength);
            assertThat(result.source()).isEqualTo("sources/github/eneikdru/test-fortieth");
            assertThat(result.startingBranch()).isEqualTo("main");
            assertThat(result.errorBody()).contains("promptLength=" + expectedPromptLength);
            assertThat(result.errorBody()).contains("source=sources/github/eneikdru/test-fortieth");
            assertThat(result.errorBody()).contains("startingBranch=main");
            assertThat(result.compactError()).contains("promptLength=" + expectedPromptLength);
            assertThat(result.preconditionUnspecified()).isTrue();
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
