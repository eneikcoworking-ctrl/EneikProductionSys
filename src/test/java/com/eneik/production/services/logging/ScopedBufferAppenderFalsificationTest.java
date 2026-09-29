package com.eneik.production.services.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Фальсифицирующий тест-заслон для ScopedBufferAppender.
 * <p>
 * Философский RAG-каркас:
 * - BARCAN-TAG-01_ACTUALIST-OBJECT:04:ahille-vartsi / AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY (D006, Varzi):
 *   Топологическая граница MDC scope. ScopedBufferAppender является строгим стражем границы:
 *   в буфер операционного контекста допускаются исключительно события с префиксом PROJECT:{id}.
 *   Обеспечивается строгая топологическая изоляция между проектами (отсутствие перекрестного загрязнения).
 * - BARCAN-TAG-00_CODE-GUARDIAN:02:gilbert-rayl / GILBERT_RAYL_03_CATEGORY_ERROR_SCAN (D002, Ryle):
 *   Категориальное отсечение системного шума фабрики (SYSTEM, null, alien scopes). Защищает потребителей
 *   контекста проекта от смешения разнородных категорий (внутренняя оркестрация фабрики vs проектная деятельность).
 */
@DisplayName("Falsification: ScopedBufferAppender Boundary Topology & Category Error Scan (D006 Varzi, D002 Ryle)")
class ScopedBufferAppenderFalsificationTest {

    private ScopedBufferAppender appender;

    @BeforeEach
    void setUp() {
        appender = new ScopedBufferAppender();
        appender.start();
        MDC.clear();
    }

    @Nested
    @DisplayName("VARZI_03: Топологическая граница и изоляция проектов (D006)")
    class VarziBoundaryTopologyTests {

        @Test
        @DisplayName("VARZI_03: Событие с валидным PROJECT:{id} пересекает границу с каноническим форматированием строки")
        void projectScopedEventPassesBoundaryWithDeterministicFormatting() {
            UUID projectId = UUID.randomUUID();
            long timestamp = 1700000000000L; // 2023-11-14T22:13:20Z
            String logger = "com.eneik.production.FlowSpine";
            String message = "Dispatched worker task for feature slice";

            LoggingEvent event = new LoggingEvent();
            event.setLoggerName(logger);
            event.setLevel(Level.INFO);
            event.setMessage(message);
            event.setTimeStamp(timestamp);
            event.setMDCPropertyMap(Map.of(LogScope.MDC_KEY, "PROJECT:" + projectId));

            appender.doAppend(event);

            List<String> lines = LogScopeBuffer.recent(projectId, 10);
            assertThat(lines)
                    .as("Буфер проекта обязан получить ровно одну строку")
                    .hasSize(1);

            String expectedLine = Instant.ofEpochMilli(timestamp)
                    + " INFO "
                    + logger
                    + " - "
                    + message;

            assertThat(lines.get(0))
                    .as("Форматирование строки должно в точности соответствовать контракту ScopedBufferAppender")
                    .isEqualTo(expectedLine);
        }

        @Test
        @DisplayName("VARZI_03: Топологическая изоляция между проектами (отсутствие перекрестного загрязнения буферов)")
        void topologicalIsolationBetweenDifferentProjects() {
            UUID projectAlpha = UUID.randomUUID();
            UUID projectBeta = UUID.randomUUID();

            LoggingEvent eventAlpha = createEvent("PROJECT:" + projectAlpha, Level.INFO, "Alpha progress 42%");
            LoggingEvent eventBeta = createEvent("PROJECT:" + projectBeta, Level.WARN, "Beta review pending");

            appender.doAppend(eventAlpha);
            appender.doAppend(eventBeta);

            List<String> alphaLines = LogScopeBuffer.recent(projectAlpha, 10);
            List<String> betaLines = LogScopeBuffer.recent(projectBeta, 10);

            assertThat(alphaLines).hasSize(1);
            assertThat(alphaLines.get(0)).contains("Alpha progress 42%");
            assertThat(alphaLines.get(0)).doesNotContain("Beta");

            assertThat(betaLines).hasSize(1);
            assertThat(betaLines.get(0)).contains("Beta review pending");
            assertThat(betaLines.get(0)).doesNotContain("Alpha");
        }
    }

    @Nested
    @DisplayName("RYLE_03: Категориальное отсечение системного и чужеродного шума (D002)")
    class RyleCategoryErrorScanTests {

        @Test
        @DisplayName("RYLE_03: Системный шум фабрики (SYSTEM) безоговорочно отсекается аппендером")
        void systemScopedEventsAreCompletelyRejected() {
            UUID canaryProject = UUID.randomUUID();

            LoggingEvent systemEvent1 = createEvent(LogScope.SYSTEM, Level.INFO, "ContinuousOrchestration: poll cycle");
            LoggingEvent systemEvent2 = createEvent("SYSTEM", Level.ERROR, "Circuit breaker tripped: rate limit");

            appender.doAppend(systemEvent1);
            appender.doAppend(systemEvent2);

            assertThat(LogScopeBuffer.recent(canaryProject, 10)).isEmpty();
        }

        @Test
        @DisplayName("RYLE_03: События без scope или с null в MDC отсекаются, не попадая в буферы")
        void nullAndMissingScopeEventsAreCompletelyRejected() {
            UUID canaryProject = UUID.randomUUID();

            LoggingEvent noScopeEvent = createEvent(null, Level.INFO, "Unscoped thread activity");
            LoggingEvent emptyMdcEvent = new LoggingEvent();
            emptyMdcEvent.setLoggerName("root");
            emptyMdcEvent.setLevel(Level.INFO);
            emptyMdcEvent.setMessage("Empty MDC map event");
            emptyMdcEvent.setMDCPropertyMap(Map.of());

            appender.doAppend(noScopeEvent);
            appender.doAppend(emptyMdcEvent);

            assertThat(LogScopeBuffer.recent(canaryProject, 10)).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "GLOBAL",
                "ORCHESTRATOR",
                "WORKER:123",
                "TASK:456",
                "project:lowercase-uuid",
                "PROJECT",
                "PROJECT_",
                "SYSTEM:SUB_MODULE"
        })
        @DisplayName("RYLE_03: Чужеродные префиксы и искаженные маркеры отсекаются аппендером как категориальные ошибки")
        void alienAndMalformedScopesAreRejected(String alienScope) {
            UUID canaryProject = UUID.randomUUID();

            LoggingEvent alienEvent = createEvent(alienScope, Level.INFO, "Alien scoped activity");
            appender.doAppend(alienEvent);

            assertThat(LogScopeBuffer.recent(canaryProject, 10)).isEmpty();
        }

        @Test
        @DisplayName("RYLE_03: LogScope MDC-хелпер гарантирует дисциплину установки и очистки thread-local")
        void logScopeMdcHelperGuaranteesThreadLocalDiscipline() {
            UUID projectId = UUID.randomUUID();

            LogScope.system();
            assertThat(MDC.get(LogScope.MDC_KEY)).isEqualTo("SYSTEM");

            LogScope.project(projectId);
            assertThat(MDC.get(LogScope.MDC_KEY)).isEqualTo("PROJECT:" + projectId);

            LogScope.clear();
            assertThat(MDC.get(LogScope.MDC_KEY)).isNull();
        }
    }

    private LoggingEvent createEvent(String scope, Level level, String message) {
        LoggingEvent event = new LoggingEvent();
        event.setLoggerName("test.logger");
        event.setLevel(level);
        event.setMessage(message);
        event.setTimeStamp(System.currentTimeMillis());
        if (scope != null) {
            event.setMDCPropertyMap(Map.of(LogScope.MDC_KEY, scope));
        } else {
            event.setMDCPropertyMap(Map.of());
        }
        return event;
    }
}
