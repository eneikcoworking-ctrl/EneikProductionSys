package com.eneik.production.services.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;

import java.time.Instant;

/**
 * Logback appender that feeds {@link LogScopeBuffer}: for every log event tagged with a PROJECT:{id}
 * scope (see {@link LogScope}), appends a formatted line to that project's in-memory ring buffer.
 * Events with no scope or a SYSTEM scope are ignored entirely - this appender is the enforcement point
 * for "only project-scoped operational context ever reaches project-facing consumers".
 */
public class ScopedBufferAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
    private static final String PROJECT_PREFIX = "PROJECT:";

    @Override
    protected void append(ILoggingEvent event) {
        String scope = event.getMDCPropertyMap().get(LogScope.MDC_KEY);
        if (scope == null || !scope.startsWith(PROJECT_PREFIX)) {
            return;
        }
        String projectId = scope.substring(PROJECT_PREFIX.length());
        String line = Instant.ofEpochMilli(event.getTimeStamp())
                + " " + event.getLevel()
                + " " + event.getLoggerName()
                + " - " + event.getFormattedMessage();
        LogScopeBuffer.append(projectId, line);
    }
}
