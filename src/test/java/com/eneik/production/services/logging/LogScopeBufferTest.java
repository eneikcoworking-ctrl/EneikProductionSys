package com.eneik.production.services.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LogScopeBufferTest {

    @Test
    void appenderOnlyBuffersProjectScopedEvents() {
        UUID projectId = UUID.randomUUID();
        ScopedBufferAppender appender = new ScopedBufferAppender();
        appender.start();

        appender.doAppend(eventWithScope("PROJECT:" + projectId, "dispatched task X"));
        appender.doAppend(eventWithScope("SYSTEM", "circuit breaker fired"));
        appender.doAppend(eventWithScope(null, "no scope set"));

        List<String> recent = LogScopeBuffer.recent(projectId, 10);
        assertThat(recent).hasSize(1);
        assertThat(recent.get(0)).contains("dispatched task X");
    }

    @Test
    void recentReturnsAtMostTheRequestedLimit() {
        UUID projectId = UUID.randomUUID();
        ScopedBufferAppender appender = new ScopedBufferAppender();
        appender.start();

        for (int i = 0; i < 5; i++) {
            appender.doAppend(eventWithScope("PROJECT:" + projectId, "event " + i));
        }

        assertThat(LogScopeBuffer.recent(projectId, 2)).hasSize(2);
        assertThat(LogScopeBuffer.recent(projectId, 100)).hasSize(5);
    }

    @Test
    void recentReturnsEmptyForUnknownProject() {
        assertThat(LogScopeBuffer.recent(UUID.randomUUID(), 10)).isEmpty();
    }

    private LoggingEvent eventWithScope(String scope, String message) {
        LoggingEvent event = new LoggingEvent();
        event.setLoggerName("test-logger");
        event.setLevel(Level.INFO);
        event.setMessage(message);
        if (scope != null) {
            event.setMDCPropertyMap(java.util.Map.of(LogScope.MDC_KEY, scope));
        } else {
            event.setMDCPropertyMap(java.util.Map.of());
        }
        return event;
    }
}
