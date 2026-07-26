package com.eneik.production.services.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable counterpart of {@link ScopedBufferAppender} (2026-07-26 restoration - operator directive: "лог
 * проекта должен независеть от деплоев"). Same PROJECT:{id}-scope filter, but hands events off to
 * {@link ProjectLogFlushQueue} instead of an in-memory-only ring buffer, so
 * {@link com.eneik.production.services.ProjectEventLogService} can persist them to a table that survives
 * container recreation. INFO and above only (skips DEBUG/TRACE) to keep volume bounded - this is a
 * forensic project history, not a full trace log.
 */
public class DurableProjectLogAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
    private static final String PROJECT_PREFIX = "PROJECT:";

    @Override
    protected void append(ILoggingEvent event) {
        if (event.getLevel().toInt() < Level.INFO_INT) {
            return;
        }
        String scope = event.getMDCPropertyMap().get(LogScope.MDC_KEY);
        if (scope == null || !scope.startsWith(PROJECT_PREFIX)) {
            return;
        }
        UUID projectId;
        try {
            projectId = UUID.fromString(scope.substring(PROJECT_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return;
        }
        ProjectLogFlushQueue.offer(new ProjectLogFlushQueue.PendingEntry(
                projectId,
                Instant.ofEpochMilli(event.getTimeStamp()),
                event.getLevel().toString(),
                event.getLoggerName(),
                event.getFormattedMessage()));
    }
}
