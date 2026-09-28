package com.eneik.production.services.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.eneik.production.kaizen.model.DefectJournalEntity;
import com.eneik.production.services.quality.ProcessControlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Фальсифицирующий замер Ступени 2:
 * 1. LogScopeBuffer: ограниченность 200 строк, кольцевое вытеснение (FIFO), изоляция PROJECT-скоупа (POL_GRAYS_01 / D007).
 * 2. DefectJournalEntity: структура причинно-следственной связи INUS (DZH_L_MAKKI_03 / D007).
 *
 * Grounding:
 * - BARCAN-TAG-00_CODE-GUARDIAN:05:pol-grays / POL_GRAYS_01_CONVERSATION_MAXIM [D007, Grice]:
 *   изоляция фонового шума фабрики от контекста проекта; строго ограниченный кольцевой буфер (200 строк).
 * - BARCAN-TAG-05_NECESSARY-IDENTITY:02:dzh-l-makki / DZH_L_MAKKI_03_INUS_FACTOR_CHECK [D007, Mackie]:
 *   атрибуция коренных причин (root_cause_pattern_id 1..16) и подгрупп (featureId) вместо смешения симптома и причины.
 */
class LogScopeAndDefectJournalInusFalsificationTest {

    @Test
    @DisplayName("POL_GRAYS_01: LogScopeBuffer строго ограничен 200 строками с вытеснением старых записей (FIFO)")
    void logScopeBufferStrictlyCapsAt200LinesWithFifoEviction() {
        UUID projectId = UUID.randomUUID();

        // Добавляем 250 строк в буфер проекта
        for (int i = 0; i < 250; i++) {
            LogScopeBuffer.append(projectId.toString(), "event-" + i);
        }

        // Запрашиваем 300 строк
        List<String> recent = LogScopeBuffer.recent(projectId, 300);

        // Буфер обязан быть ограничен ровно 200 строками
        assertThat(recent).hasSize(200);

        // Первые 50 строк (event-0 ... event-49) вытеснены по FIFO
        assertThat(recent.get(0)).isEqualTo("event-50");
        assertThat(recent.get(199)).isEqualTo("event-249");

        // Запрос с меньшим лимитом возвращает хвост
        List<String> last10 = LogScopeBuffer.recent(projectId, 10);
        assertThat(last10).hasSize(10);
        assertThat(last10.get(0)).isEqualTo("event-240");
        assertThat(last10.get(9)).isEqualTo("event-249");
    }

    @Test
    @DisplayName("POL_GRAYS_01: ScopedBufferAppender изолирует PROJECT-скоупы и полностью отсекает SYSTEM-шум")
    void scopedBufferAppenderIsolatesProjectScopesAndDiscardsSystemNoise() {
        UUID projectA = UUID.randomUUID();
        UUID projectB = UUID.randomUUID();

        ScopedBufferAppender appender = new ScopedBufferAppender();
        appender.start();

        // Логи для проекта A
        appender.doAppend(createLogEvent("PROJECT:" + projectA, "project A task started"));
        appender.doAppend(createLogEvent("PROJECT:" + projectA, "project A compilation pass"));

        // Логи для проекта B
        appender.doAppend(createLogEvent("PROJECT:" + projectB, "project B git checkout"));

        // Фоновый системный шум фабрики (диспетчеризация, circuit breaker, healthcheck)
        appender.doAppend(createLogEvent("SYSTEM", "circuit breaker evaluated"));
        appender.doAppend(createLogEvent("SYSTEM", "heartbeat broadcast"));
        appender.doAppend(createLogEvent("GLOBAL", "global threadpool check"));
        appender.doAppend(createLogEvent(null, "unscoped debug message"));

        // Проверка изоляции
        List<String> linesA = LogScopeBuffer.recent(projectA, 10);
        assertThat(linesA).hasSize(2);
        assertThat(linesA.get(0)).contains("project A task started");
        assertThat(linesA.get(1)).contains("project A compilation pass");

        List<String> linesB = LogScopeBuffer.recent(projectB, 10);
        assertThat(linesB).hasSize(1);
        assertThat(linesB.get(0)).contains("project B git checkout");

        // Несуществующий проект не имеет логов
        UUID unknownProject = UUID.randomUUID();
        assertThat(LogScopeBuffer.recent(unknownProject, 10)).isEmpty();
    }

    @Test
    @DisplayName("LogScopeBuffer потокобезопасен при параллельном наполнении")
    void logScopeBufferConcurrentAppendSafety() throws InterruptedException, ExecutionException {
        UUID projectId = UUID.randomUUID();
        int threads = 10;
        int appendsPerThread = 30; // суммарно 300 строк > 200

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            tasks.add(() -> {
                for (int i = 0; i < appendsPerThread; i++) {
                    LogScopeBuffer.append(projectId.toString(), "thread-" + threadId + "-event-" + i);
                }
                return null;
            });
        }

        List<Future<Void>> futures = executor.invokeAll(tasks);
        for (Future<Void> future : futures) {
            future.get();
        }
        executor.shutdown();

        List<String> recent = LogScopeBuffer.recent(projectId, 500);
        assertThat(recent).hasSize(200);
    }

    @Test
    @DisplayName("DZH_L_MAKKI_03: DefectJournalEntity кодирует структуру INUS-причин и подгрупп")
    void defectJournalInusFactorCausalStructureIntegrity() {
        UUID projectId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        int charterPatternId = 6; // Charter Pattern #6: Category errors at serialization boundaries

        // Конструктор полной INUS-атрибуции
        DefectJournalEntity defect = new DefectJournalEntity(
                projectId,
                featureId,
                charterPatternId,
                "HIGH",
                "epistemic_layer_invariant",
                "AutoMergeService",
                "core_violation",
                "PR touching core schema without migration",
                1.0
        );

        assertEquals(projectId, defect.getProjectId());
        assertEquals(featureId, defect.getFeatureId());
        assertEquals(Integer.valueOf(6), defect.getRootCausePatternId());
        assertEquals("HIGH", defect.getSeverity());
        assertEquals("epistemic_layer_invariant", defect.getCategory());
        assertEquals("AutoMergeService", defect.getSourceComponent());
        assertEquals("core_violation", defect.getDefectType());
        assertEquals(Double.valueOf(1.0), defect.getMetricValue());
        assertNotNull(defect.getCreatedAt());

        // Проверка валидности паттерна по справочнику ProcessControlService
        assertTrue(ProcessControlService.CHARTER_PATTERN_NAMES.containsKey(charterPatternId));
        assertEquals("Category errors at serialization boundaries",
                ProcessControlService.CHARTER_PATTERN_NAMES.get(charterPatternId));
    }

    @Test
    @DisplayName("DZH_L_MAKKI_03: Симптомный дефект не приписывает ложную причину до триажа")
    void untriagedSymptomDefectHasNullRootCauseAndFeature() {
        UUID projectId = UUID.randomUUID();

        // Симптомный конструктор: причина не установлена до триажа (отсутствие псевдо-причины)
        DefectJournalEntity symptomDefect = new DefectJournalEntity(
                projectId,
                "MEDIUM",
                "PERFORMANCE",
                "TocAnomalyDetector",
                "DWELL_TIME_SPIKE",
                "Observation exceeded dynamic threshold",
                12500.0
        );

        assertEquals(projectId, symptomDefect.getProjectId());
        assertNull(symptomDefect.getFeatureId(), "featureId must be null for non-subgroup systemic defect");
        assertNull(symptomDefect.getRootCausePatternId(), "rootCausePatternId must be null until causal RCA triage");
        assertEquals("MEDIUM", symptomDefect.getSeverity());
        assertEquals(Double.valueOf(12500.0), symptomDefect.getMetricValue());
    }

    private LoggingEvent createLogEvent(String scope, String message) {
        LoggingEvent event = new LoggingEvent();
        event.setLoggerName("com.eneik.production.services.TestLogger");
        event.setLevel(Level.INFO);
        event.setMessage(message);
        event.setTimeStamp(System.currentTimeMillis());
        if (scope != null) {
            event.setMDCPropertyMap(java.util.Map.of(LogScope.MDC_KEY, scope));
        } else {
            event.setMDCPropertyMap(java.util.Map.of());
        }
        return event;
    }
}
