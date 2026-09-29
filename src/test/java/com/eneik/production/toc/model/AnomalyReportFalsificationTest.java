package com.eneik.production.toc.model;

import com.eneik.production.toc.controller.TocSentinelController;
import com.eneik.production.toc.engine.TocAnomalyDetector;
import com.eneik.production.toc.engine.TocExecutionGraph;
import com.eneik.production.toc.engine.TocOptimizer;
import com.eneik.production.toc.service.TocSentinelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Фальсифицирующий тест-заслон для AnomalyReport.
 * <p>
 * Философский RAG-каркас:
 * - BARCAN-TAG-05_NECESSARY-IDENTITY:02:derek-parfit / DEREK_PARFIT_01_PERSISTENCE_SNAPSHOT (D010, Parfit):
 *   Неизменяемый снапшот аномалии. Гарантирует сохранение идентичности, каузального контекста и неизменяемости
 *   записи аномалии (id, type, tokenId, nodeName, resourceId, details, actionTaken, timestamp) при передаче
 *   через слои и в ретроспективном анализе инцидентов.
 * - BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE:05:fred-dretske / FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK (D011, Dretske):
 *   Телеосемантическая функция сигнала аномалии. Сигнал валиден только тогда, когда он активно модифицирует
 *   следующее действие системы (прерывание циклов, снятие взаимных блокировок ресурсов через выбор жертвы,
 *   маркировка stall bottleneck в графе TOC) и предотвращает деградацию и зацикливание потока.
 */
@DisplayName("Falsification: AnomalyReport Persistence Snapshot & Teleosemantic Feedback (D010 Parfit, D011 Dretske)")
class AnomalyReportFalsificationTest {

    private TocExecutionGraph graph;
    private TocAnomalyDetector anomalyDetector;
    private TocOptimizer optimizer;
    private TocSentinelService sentinelService;

    @BeforeEach
    void setUp() {
        graph = new TocExecutionGraph();
        anomalyDetector = new TocAnomalyDetector(graph);
        optimizer = new TocOptimizer(graph);
        sentinelService = new TocSentinelService(graph, anomalyDetector, optimizer);
    }

    @Nested
    @DisplayName("PARFIT_01: Неизменяемый снапшот аномалии и сохранение идентичности (D010)")
    class ParfitPersistenceSnapshotTests {

        @Test
        @DisplayName("PARFIT_01: Запись AnomalyReport сохраняет все 8 полей контекста и является неизменяемой")
        void recordPreservesAllContextualFieldsImmutably() {
            String id = UUID.randomUUID().toString();
            String tokenId = "tok-123";
            String nodeName = "STEP_PROCESS";
            String resourceId = "DB_CONNECTION_POOL";
            String details = "Cycle detected in execution call stack";
            String actionTaken = "Aborted token to protect resources";
            Instant now = Instant.now();

            AnomalyReport report = new AnomalyReport(
                    id,
                    AnomalyReport.AnomalyType.CYCLE_DETECTED,
                    tokenId,
                    nodeName,
                    resourceId,
                    details,
                    actionTaken,
                    now
            );

            assertThat(report.id()).isEqualTo(id);
            assertThat(report.type()).isEqualTo(AnomalyReport.AnomalyType.CYCLE_DETECTED);
            assertThat(report.tokenId()).isEqualTo(tokenId);
            assertThat(report.nodeName()).isEqualTo(nodeName);
            assertThat(report.resourceId()).isEqualTo(resourceId);
            assertThat(report.details()).isEqualTo(details);
            assertThat(report.actionTaken()).isEqualTo(actionTaken);
            assertThat(report.timestamp()).isEqualTo(now);

            // Проверка структурного равенства копии снапшота
            AnomalyReport copy = new AnomalyReport(
                    id,
                    AnomalyReport.AnomalyType.CYCLE_DETECTED,
                    tokenId,
                    nodeName,
                    resourceId,
                    details,
                    actionTaken,
                    now
            );
            assertThat(report).isEqualTo(copy);
            assertThat(report.hashCode()).isEqualTo(copy.hashCode());
        }

        @Test
        @DisplayName("PARFIT_01: Универсум AnomalyType закрыт ровно 4 типами аномалий TOC")
        void anomalyTypeUniverseIsExhaustiveAndClosed() {
            AnomalyReport.AnomalyType[] types = AnomalyReport.AnomalyType.values();
            assertThat(types)
                    .as("Универсум типов аномалий должен содержать строго 4 типа")
                    .containsExactlyInAnyOrder(
                            AnomalyReport.AnomalyType.CYCLE_DETECTED,
                            AnomalyReport.AnomalyType.STALL_DETECTED,
                            AnomalyReport.AnomalyType.DEADLOCK_DETECTED,
                            AnomalyReport.AnomalyType.BUFFER_OVERFLOW
                    );
        }

        @Test
        @DisplayName("PARFIT_01: Журнал аномалий передаётся через контроллер как неизменяемый снимок")
        void anomaliesExposedAsUnmodifiableSnapshot() {
            TocSentinelController controller = new TocSentinelController(sentinelService);

            ResponseEntity<List<AnomalyReport>> response = controller.getAnomalies();
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            List<AnomalyReport> list = response.getBody();
            assertThat(list).isNotNull();

            // Попытка модифицировать полученный список аномалий должна выбрасывать UnsupportedOperationException
            AnomalyReport dummy = new AnomalyReport(
                    "dummy-id",
                    AnomalyReport.AnomalyType.BUFFER_OVERFLOW,
                    "dummy-token",
                    "dummy-node",
                    null,
                    "details",
                    "action",
                    Instant.now()
            );
            assertThatThrownBy(() -> list.add(dummy))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("DRETSKE_07: Телеосемантическая функция сигнала аномалии (D011)")
    class DretskeTeleosemanticFeedbackTests {

        @Test
        @DisplayName("DRETSKE_07: Сигнал CYCLE_DETECTED активно прерывает цикл и переводит токен в CYCLE_ABORTED")
        void cycleDetectionSignalTriggersImmediateProtectiveAction() {
            TocToken token = sentinelService.startExecution("WORKFLOW_LOOP", 10);

            assertThat(sentinelService.enterStep(token, "A")).isTrue();
            assertThat(sentinelService.enterStep(token, "B")).isTrue();
            assertThat(sentinelService.enterStep(token, "C")).isTrue();

            // Повторный вход в A внутри активного стека (A -> B -> C -> A)
            boolean allowed = sentinelService.enterStep(token, "A");
            assertThat(allowed).isFalse();

            // Телеосемантический эффект 1: состояние токена изменено на CYCLE_ABORTED
            assertThat(token.getStatus()).isEqualTo(TocToken.TokenStatus.CYCLE_ABORTED);

            // Телеосемантический эффект 2: токен удален из активных токенов графа
            assertThat(graph.getToken(token.getTokenId())).isNull();

            // Телеосемантический эффект 3: сформирован снапшот AnomalyReport с каузальным действием
            List<AnomalyReport> anomalies = sentinelService.getRecentAnomalies();
            assertThat(anomalies).hasSize(1);
            AnomalyReport report = anomalies.get(0);
            assertThat(report.type()).isEqualTo(AnomalyReport.AnomalyType.CYCLE_DETECTED);
            assertThat(report.tokenId()).isEqualTo(token.getTokenId());
            assertThat(report.nodeName()).isEqualTo("A");
            assertThat(report.actionTaken()).contains("Aborted token '" + token.getTokenId() + "'");
            assertThat(report.details()).contains("Active stack path");
        }

        @Test
        @DisplayName("DRETSKE_07: Сигнал DEADLOCK_DETECTED разрывает взаимную блокировку ресурсов жертвуя низкоприоритетным токеном")
        void deadlockDetectionSignalBreaksWaitChainByAbortingVictim() {
            TocToken lowPriorityToken = sentinelService.startExecution("LOW_PRIO_JOB", 5);
            TocToken highPriorityToken = sentinelService.startExecution("HIGH_PRIO_JOB", 50);

            sentinelService.enterStep(lowPriorityToken, "NODE_1");
            sentinelService.enterStep(highPriorityToken, "NODE_2");

            sentinelService.acquireResource(lowPriorityToken, "RESOURCE_MUTEX_1");
            sentinelService.acquireResource(highPriorityToken, "RESOURCE_MUTEX_2");

            // lowPriorityToken ждёт RESOURCE_MUTEX_2 (удерживаемый highPriorityToken)
            boolean deadlockedFirst = sentinelService.waitResource(lowPriorityToken, "RESOURCE_MUTEX_2");
            assertThat(deadlockedFirst).isFalse();

            // highPriorityToken ждёт RESOURCE_MUTEX_1 -> образуется цикл ожидания в графе (Deadlock)
            boolean deadlockedSecond = sentinelService.waitResource(highPriorityToken, "RESOURCE_MUTEX_1");
            assertThat(deadlockedSecond).isTrue();

            // Телеосемантический эффект: низкоприоритетный токен принесён в жертву (DEADLOCK_ABORTED),
            // а высокоприоритетный остаётся ACTIVE и разблокирован
            assertThat(lowPriorityToken.getStatus()).isEqualTo(TocToken.TokenStatus.DEADLOCK_ABORTED);
            assertThat(highPriorityToken.getStatus()).isEqualTo(TocToken.TokenStatus.ACTIVE);

            // Проверяем снапшот аномалии
            List<AnomalyReport> anomalies = sentinelService.getRecentAnomalies();
            assertThat(anomalies.stream().anyMatch(a ->
                    a.type() == AnomalyReport.AnomalyType.DEADLOCK_DETECTED
                            && a.actionTaken().contains("Selected victim token '" + lowPriorityToken.getTokenId() + "'")
            )).isTrue();
        }

        @Test
        @DisplayName("DRETSKE_07: Сигнал STALL_DETECTED маркирует узел графа как узкое место (stallBottleneck)")
        void stallDetectionSignalMarksNodeAsStallBottleneck() throws InterruptedException {
            anomalyDetector.setDefaultTimeoutFloorMs(10.0);
            anomalyDetector.setSensitivityMultiplier(0.0);

            // Обучаем узел быстрыми проходами для фиксации Welford-наблюдений
            TocToken train1 = sentinelService.startExecution("TRAIN_1", 10);
            sentinelService.enterStep(train1, "STAGE_QUICK");
            Thread.sleep(5);
            sentinelService.exitStep(train1, "STAGE_QUICK", true);

            TocToken train2 = sentinelService.startExecution("TRAIN_2", 10);
            sentinelService.enterStep(train2, "STAGE_QUICK");
            Thread.sleep(5);
            sentinelService.exitStep(train2, "STAGE_QUICK", true);

            TocNode node = graph.getNode("STAGE_QUICK");
            assertThat(node.hasObservedDuration()).isTrue();
            assertThat(node.isStallBottleneck()).isFalse();

            // Запускаем зависающий токен
            TocToken stallToken = sentinelService.startExecution("STALL_JOB", 10);
            sentinelService.enterStep(stallToken, "STAGE_QUICK");

            // Ждём превышения динамического порога
            Thread.sleep(50);

            List<AnomalyReport> stalls = anomalyDetector.scanForStalls();
            assertThat(stalls).isNotEmpty();

            AnomalyReport stallReport = stalls.stream()
                    .filter(s -> s.type() == AnomalyReport.AnomalyType.STALL_DETECTED && "STAGE_QUICK".equals(s.nodeName()))
                    .findFirst()
                    .orElseThrow();

            assertThat(stallReport.tokenId()).isEqualTo(stallToken.getTokenId());
            assertThat(stallReport.actionTaken()).contains("Flagged node 'STAGE_QUICK' as STALL_BOTTLENECK");

            // Телеосемантический эффект: узел помечен как узкое место задержки
            assertThat(node.isStallBottleneck()).isTrue();
        }

        @Test
        @DisplayName("DRETSKE_07: Дедупликация защищает информационный канал телеметрии от зашумления")
        void deduplicationProtectsInformationChannelBandwidth() {
            TocToken token1 = sentinelService.startExecution("TOKEN_1", 10);
            sentinelService.enterStep(token1, "LOOP_NODE");
            sentinelService.enterStep(token1, "SUB_NODE");
            // Вызываем цикл
            sentinelService.enterStep(token1, "LOOP_NODE");

            List<AnomalyReport> anomalies = sentinelService.getRecentAnomalies();
            int sizeBefore = anomalies.size();
            assertThat(sizeBefore).isGreaterThanOrEqualTo(1);

            // Повторная идентичная аномалия на том же узле/типе в пределах 5-минутного окна
            TocToken token2 = sentinelService.startExecution("TOKEN_2", 10);
            sentinelService.enterStep(token2, "LOOP_NODE");
            sentinelService.enterStep(token2, "SUB_NODE");
            sentinelService.enterStep(token2, "LOOP_NODE");

            List<AnomalyReport> anomaliesAfter = sentinelService.getRecentAnomalies();
            // Дедупликатор отсекает дубликат на том же nodeName
            assertThat(anomaliesAfter).hasSize(sizeBefore);
        }
    }
}
