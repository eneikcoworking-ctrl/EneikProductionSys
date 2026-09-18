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

    @Test
    @org.junit.jupiter.api.DisplayName("Conversation Maxim (Grice 1975 / D007): LogScopeBuffer javadoc must reflect actual reader (ProjectController) and not claim falsification cycle reads it")
    void javadocReflectsActualReadersAndRejectsStaleClaims() throws Exception {
        java.nio.file.Path sourcePath = java.nio.file.Path.of("src/main/java/com/eneik/production/services/logging/LogScopeBuffer.java");
        if (!java.nio.file.Files.exists(sourcePath)) {
            sourcePath = java.nio.file.Path.of("/opt/EneikProductionSys/src/main/java/com/eneik/production/services/logging/LogScopeBuffer.java");
        }
        String source = java.nio.file.Files.readString(sourcePath);
        // Javadoc must cite the real external reader
        assertThat(source).contains("ProjectController");
        // Javadoc must NOT claim falsification cycle reads this buffer
        assertThat(source).doesNotContain("falsification cycle, which reads this");
        assertThat(source).doesNotContain("window for the next falsification pass");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("Category Error / Conversation Maxim: FalsificationCycleService must not call LogScopeBuffer.recent")
    void falsificationCycleServiceDoesNotCallLogScopeBuffer() throws Exception {
        java.nio.file.Path sourcePath = java.nio.file.Path.of("src/main/java/com/eneik/production/services/FalsificationCycleService.java");
        if (!java.nio.file.Files.exists(sourcePath)) {
            sourcePath = java.nio.file.Path.of("/opt/EneikProductionSys/src/main/java/com/eneik/production/services/FalsificationCycleService.java");
        }
        String source = java.nio.file.Files.readString(sourcePath);
        java.util.regex.Pattern callPattern = java.util.regex.Pattern.compile("^\\s*LogScopeBuffer\\.recent", java.util.regex.Pattern.MULTILINE);
        assertThat(callPattern.matcher(source).find()).isFalse();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("ProjectController.recentActivity is the sole live endpoint reading LogScopeBuffer")
    void projectControllerRecentActivityReadsLogScopeBuffer() {
        UUID projectId = UUID.randomUUID();
        ScopedBufferAppender appender = new ScopedBufferAppender();
        appender.start();
        appender.doAppend(eventWithScope("PROJECT:" + projectId, "project-specific audit event"));

        var controller = new com.eneik.production.controllers.projects.ProjectController(
                org.mockito.Mockito.mock(com.eneik.production.services.ProjectFlowService.class),
                org.mockito.Mockito.mock(com.eneik.production.services.ClaimService.class),
                org.mockito.Mockito.mock(com.eneik.production.repositories.OnboardingAuditFindingRepository.class),
                org.mockito.Mockito.mock(com.eneik.production.services.onboarding.OnboardingAuditService.class),
                org.mockito.Mockito.mock(com.eneik.production.services.ClientDeliverableReadinessService.class),
                org.mockito.Mockito.mock(com.eneik.production.services.FalsificationCycleService.class),
                org.mockito.Mockito.mock(com.eneik.production.services.tree.ProjectTreeService.class),
                org.mockito.Mockito.mock(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.class),
                org.mockito.Mockito.mock(com.eneik.production.services.runtime.ProductCapabilityService.class),
                org.mockito.Mockito.mock(com.eneik.production.services.coherence.EvidenceCoherenceService.class),
                org.mockito.Mockito.mock(com.eneik.production.repositories.GeminiObserverJournalRepository.class)
        );

        org.springframework.http.ResponseEntity<?> response = controller.recentActivity(projectId, 50);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> body = (java.util.Map<String, Object>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("projectId")).isEqualTo(projectId);
        @SuppressWarnings("unchecked")
        List<String> lines = (List<String>) body.get("lines");
        assertThat(lines).isNotEmpty();
        assertThat(lines.get(lines.size() - 1)).contains("project-specific audit event");
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
