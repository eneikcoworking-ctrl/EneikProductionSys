package com.eneik.production.toc;

import com.eneik.production.toc.engine.TocExecutionGraph;
import com.eneik.production.toc.model.TocEdge;
import com.eneik.production.toc.model.TocNode;
import com.eneik.production.toc.model.persistence.TocEdgeSnapshotEntity;
import com.eneik.production.toc.model.persistence.TocNodeSnapshotEntity;
import com.eneik.production.toc.repository.TocEdgeSnapshotRepository;
import com.eneik.production.toc.repository.TocNodeSnapshotRepository;
import com.eneik.production.toc.service.TocPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Фальсифицирующий замер Ступени 2 для TocNode и TocEdge:
 * онлайн-вариация Велфорда и восстановление снимков персистенции.
 *
 * Grounding:
 * - BARCAN-TAG-05_NECESSARY-IDENTITY:01:derek-parfit / DEREK_PARFIT_01_PERSISTENCE_SNAPSHOT [D010, Parfit]:
 *   персистентные снимки узлов и рёбер TOC, непрерывность идентичности через перезапуски JVM.
 * - BARCAN-TAG-00_CODE-GUARDIAN:01:lyudvig-vitgenshteyn / LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY [D013, Wittgenstein]:
 *   эмпирическая сверка статусов и метрик рантайма с физической реальностью без номинального искажения.
 */
class TocWelfordOnlineVarianceFalsificationTest {

    private InMemoryNodeSnapshotRepository nodeRepo;
    private InMemoryEdgeSnapshotRepository edgeRepo;
    private TocPersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        nodeRepo = new InMemoryNodeSnapshotRepository();
        edgeRepo = new InMemoryEdgeSnapshotRepository();
        persistenceService = new TocPersistenceService(nodeRepo, edgeRepo);
    }

    @Test
    @DisplayName("Welford онлайн-алгоритм совпадает с эталонной выборочной дисперсией (D013 Wittgenstein)")
    void welfordOnlineMeanAndSampleVarianceMatchExactTextbookFormula() {
        TocNode node = new TocNode("STAGE_BENCHMARK");
        // Выборка: 100, 200, 300, 400, 500 мс
        long[] samplesMs = {100, 200, 300, 400, 500};
        for (long sampleMs : samplesMs) {
            node.recordExecution(sampleMs * 1_000_000L, true);
        }

        // Эталонная математика:
        // n = 5
        // mean = 1500 / 5 = 300.0 ms
        // sum((x - mean)^2) = (-200)^2 + (-100)^2 + 0^2 + 100^2 + 200^2 = 40000 + 10000 + 0 + 10000 + 40000 = 100000.0 ms^2
        // sample_variance = M2 / (n - 1) = 100000 / 4 = 25000.0 ms^2
        // sample_std_dev = sqrt(25000.0) ≈ 158.11388300841898 ms
        assertThat(node.getCompletedCount()).isEqualTo(5);
        assertThat(node.getErrorCount()).isEqualTo(0);
        assertThat(node.getTotalDurationNanos()).isEqualTo(1500L * 1_000_000L);
        assertThat(node.getMeanDurationMs()).isCloseTo(300.0, within(1e-6));
        assertThat(node.getM2Ms()).isCloseTo(100000.0, within(1e-6));
        assertThat(node.getStdDevMs()).isCloseTo(158.11388300841898, within(1e-6));
    }

    @Test
    @DisplayName("Граничные случаи Велфорда (n=0, n=1, идентичные значения) численно устойчивы")
    void welfordDegenerateAndEdgeCasesAreNumericallyStable() {
        TocNode node = new TocNode("STAGE_EDGE_CASES");

        // n = 0
        assertThat(node.getCompletedCount()).isEqualTo(0);
        assertThat(node.hasObservedDuration()).isFalse();
        assertThat(node.getMeanDurationMs()).isEqualTo(0.0);
        assertThat(node.getM2Ms()).isEqualTo(0.0);
        assertThat(node.getStdDevMs()).isEqualTo(0.0);
        assertThat(node.getDynamicTimeoutLimitMs(2.0, 5000.0)).isEqualTo(5000.0);

        // n = 1: с одним измерением stdDev легитимно равен 0, а mean равен первому измерению
        node.recordExecution(1200L * 1_000_000L, true);
        assertThat(node.getCompletedCount()).isEqualTo(1);
        assertThat(node.hasObservedDuration()).isTrue();
        assertThat(node.getMeanDurationMs()).isCloseTo(1200.0, within(1e-6));
        assertThat(node.getM2Ms()).isEqualTo(0.0);
        assertThat(node.getStdDevMs()).isEqualTo(0.0);
        // Динамический лимит: max(floor, mean) = max(5000, 1200) = 5000
        assertThat(node.getDynamicTimeoutLimitMs(2.0, 5000.0)).isEqualTo(5000.0);

        // n = 1 с замером выше floor: max(floor, mean) = max(5000, 15000) = 15000
        TocNode heavyNode = new TocNode("STAGE_HEAVY");
        heavyNode.recordExecution(15000L * 1_000_000L, true);
        assertThat(heavyNode.getDynamicTimeoutLimitMs(2.0, 5000.0)).isEqualTo(15000.0);

        // Идентичные замеры: дисперсия строго 0.0, отсутствие NaN и отрицательных чисел
        TocNode constantNode = new TocNode("STAGE_CONSTANT");
        for (int i = 0; i < 20; i++) {
            constantNode.recordExecution(250L * 1_000_000L, true);
        }
        assertThat(constantNode.getCompletedCount()).isEqualTo(20);
        assertThat(constantNode.getMeanDurationMs()).isCloseTo(250.0, within(1e-6));
        assertThat(constantNode.getM2Ms()).isCloseTo(0.0, within(1e-6));
        assertThat(constantNode.getStdDevMs()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("PARFIT_01: Восстановление снимка узла гарантирует непрерывность ряда Велфорда после рестарта")
    void parfitPersistenceSnapshotPreservesContinuityAcrossRestart() {
        TocNode originalNode = new TocNode("PARFIT_STEP");

        // 10 исходных замеров со средним 1000 мс (от 550 до 1450 мс)
        for (int i = 1; i <= 10; i++) {
            long durationMs = 500 + i * 100; // 600, 700, 800, 900, 1000, 1100, 1200, 1300, 1400, 1500
            originalNode.recordExecution(durationMs * 1_000_000L, i % 5 != 0);
        }

        assertThat(originalNode.getCompletedCount()).isEqualTo(10);
        assertThat(originalNode.getErrorCount()).isEqualTo(2); // i=5, i=10
        double preRestartMean = originalNode.getMeanDurationMs();
        double preRestartM2 = originalNode.getM2Ms();
        double preRestartStdDev = originalNode.getStdDevMs();

        // Сохранение персистентного снимка
        TocNodeSnapshotEntity snapshot = persistenceService.saveNodeSnapshot(originalNode);
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getNodeName()).isEqualTo("PARFIT_STEP");
        assertThat(snapshot.getCompletedCount()).isEqualTo(10);
        assertThat(snapshot.getErrorCount()).isEqualTo(2);
        assertThat(snapshot.getMeanDurationMs()).isEqualTo(preRestartMean);
        assertThat(snapshot.getM2Ms()).isEqualTo(preRestartM2);
        assertThat(snapshot.getStdDevMs()).isEqualTo(preRestartStdDev);

        // Имитация рестарта: новый граф и новый экземпляр TocNode
        TocExecutionGraph restartedGraph = new TocExecutionGraph();
        int restoredCount = persistenceService.restoreHistoricalState(restartedGraph);
        assertThat(restoredCount).isEqualTo(1);

        TocNode restoredNode = restartedGraph.getNode("PARFIT_STEP");
        assertThat(restoredNode).isNotNull();
        assertThat(restoredNode.getCompletedCount()).isEqualTo(10);
        assertThat(restoredNode.getErrorCount()).isEqualTo(2);
        assertThat(restoredNode.getMeanDurationMs()).isCloseTo(preRestartMean, within(1e-6));
        assertThat(restoredNode.getM2Ms()).isCloseTo(preRestartM2, within(1e-6));
        assertThat(restoredNode.getStdDevMs()).isCloseTo(preRestartStdDev, within(1e-6));

        // 11-й замер в восстановленном рантайме:
        // Математическое обновление обязано совпасть с тем, как если бы рестарта не было!
        long eleventhDurationMs = 2100;
        restoredNode.recordExecution(eleventhDurationMs * 1_000_000L, true);

        // Контрольный экземпляр без рестарта
        originalNode.recordExecution(eleventhDurationMs * 1_000_000L, true);

        assertThat(restoredNode.getCompletedCount()).isEqualTo(11);
        assertThat(restoredNode.getMeanDurationMs()).isCloseTo(originalNode.getMeanDurationMs(), within(1e-6));
        assertThat(restoredNode.getM2Ms()).isCloseTo(originalNode.getM2Ms(), within(1e-6));
        assertThat(restoredNode.getStdDevMs()).isCloseTo(originalNode.getStdDevMs(), within(1e-6));
    }

    @Test
    @DisplayName("PARFIT_01: Непрерывность переходов рёбер TocEdge сквозь перезапуски")
    void parfitEdgeContinuityPreservesTransitionTopographyAcrossRestart() {
        TocExecutionGraph graph1 = new TocExecutionGraph();
        for (int i = 0; i < 42; i++) {
            graph1.recordTransition("STEP_ALPHA", "STEP_BETA");
        }

        TocEdge edge1 = graph1.getOrCreateEdge("STEP_ALPHA", "STEP_BETA");
        assertThat(edge1.getTransitionCount()).isEqualTo(42);

        // Сохранение снимка ребра
        persistenceService.saveGraphSnapshot(graph1);
        assertThat(edgeRepo.savedEdges).hasSize(1);

        // Рестарт в чистом графе
        TocExecutionGraph restartedGraph = new TocExecutionGraph();
        persistenceService.restoreHistoricalState(restartedGraph);

        TocEdge restoredEdge = restartedGraph.getOrCreateEdge("STEP_ALPHA", "STEP_BETA");
        assertThat(restoredEdge).isNotNull();
        assertThat(restoredEdge.getTransitionCount()).isEqualTo(42);

        // Дополнительные переходы после рестарта
        restartedGraph.recordTransition("STEP_ALPHA", "STEP_BETA");
        assertThat(restoredEdge.getTransitionCount()).isEqualTo(43);
    }

    @Test
    @DisplayName("Anti-Mirror Telemetry: In-flight счетчики и топологическая валидация графа")
    void wittgensteinAntiMirrorTelemetryUnderlyingRuntimeIntegrity() {
        TocNode node = new TocNode("TELEMETRY_STEP");

        // Декремент при 0 не уходит в отрицательные числа
        node.decrementInFlight();
        assertThat(node.getInFlightCount()).isEqualTo(0);

        node.incrementInFlight();
        node.incrementInFlight();
        assertThat(node.getInFlightCount()).isEqualTo(2);

        node.decrementInFlight();
        assertThat(node.getInFlightCount()).isEqualTo(1);

        // Граф отсекает циклы на себя и null узлы
        TocExecutionGraph graph = new TocExecutionGraph();
        graph.recordTransition("STEP_A", "STEP_A");
        assertThat(graph.getEdges()).isEmpty();

        graph.recordTransition(null, "STEP_A");
        graph.recordTransition("STEP_A", null);
        assertThat(graph.getEdges()).isEmpty();
    }

    // In-memory test repository implementations
    private static class InMemoryNodeSnapshotRepository implements TocNodeSnapshotRepository {
        final List<TocNodeSnapshotEntity> savedSnapshots = new ArrayList<>();

        @Override
        public List<TocNodeSnapshotEntity> findLatestSnapshotsForAllNodes() {
            Map<String, TocNodeSnapshotEntity> latestByNode = new HashMap<>();
            for (TocNodeSnapshotEntity s : savedSnapshots) {
                TocNodeSnapshotEntity existing = latestByNode.get(s.getNodeName());
                if (existing == null || s.getSnapshotAt().isAfter(existing.getSnapshotAt())) {
                    latestByNode.put(s.getNodeName(), s);
                }
            }
            return new ArrayList<>(latestByNode.values());
        }

        @Override
        public List<TocNodeSnapshotEntity> findByNodeNameOrderBySnapshotAtDesc(String nodeName) {
            return savedSnapshots.stream()
                    .filter(s -> s.getNodeName().equals(nodeName))
                    .sorted((a, b) -> b.getSnapshotAt().compareTo(a.getSnapshotAt()))
                    .toList();
        }

        @Override
        public Optional<TocNodeSnapshotEntity> findFirstByNodeNameOrderBySnapshotAtDesc(String nodeName) {
            return findByNodeNameOrderBySnapshotAtDesc(nodeName).stream().findFirst();
        }

        @Override
        public <S extends TocNodeSnapshotEntity> S save(S entity) {
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            savedSnapshots.add(entity);
            return entity;
        }

        @Override public <S extends TocNodeSnapshotEntity> List<S> saveAll(Iterable<S> entities) { return List.of(); }
        @Override public Optional<TocNodeSnapshotEntity> findById(UUID uuid) { return Optional.empty(); }
        @Override public boolean existsById(UUID uuid) { return false; }
        @Override public List<TocNodeSnapshotEntity> findAll() { return savedSnapshots; }
        @Override public List<TocNodeSnapshotEntity> findAllById(Iterable<UUID> uuids) { return List.of(); }
        @Override public long count() { return savedSnapshots.size(); }
        @Override public void deleteById(UUID uuid) {}
        @Override public void delete(TocNodeSnapshotEntity entity) {}
        @Override public void deleteAllById(Iterable<? extends UUID> uuids) {}
        @Override public void deleteAll(Iterable<? extends TocNodeSnapshotEntity> entities) {}
        @Override public void deleteAll() { savedSnapshots.clear(); }
        @Override public void flush() {}
        @Override public <S extends TocNodeSnapshotEntity> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends TocNodeSnapshotEntity> List<S> saveAllAndFlush(Iterable<S> entities) { return List.of(); }
        @Override public void deleteAllInBatch(Iterable<TocNodeSnapshotEntity> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> uuids) {}
        @Override public void deleteAllInBatch() {}
        @Override public TocNodeSnapshotEntity getOne(UUID uuid) { return null; }
        @Override public TocNodeSnapshotEntity getById(UUID uuid) { return null; }
        @Override public TocNodeSnapshotEntity getReferenceById(UUID uuid) { return null; }
        @Override public <S extends TocNodeSnapshotEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends TocNodeSnapshotEntity> List<S> findAll(org.springframework.data.domain.Example<S> example) { return List.of(); }
        @Override public <S extends TocNodeSnapshotEntity> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public <S extends TocNodeSnapshotEntity> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return null; }
        @Override public <S extends TocNodeSnapshotEntity> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends TocNodeSnapshotEntity> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends TocNodeSnapshotEntity, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public List<TocNodeSnapshotEntity> findAll(org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public org.springframework.data.domain.Page<TocNodeSnapshotEntity> findAll(org.springframework.data.domain.Pageable pageable) { return null; }
    }

    private static class InMemoryEdgeSnapshotRepository implements TocEdgeSnapshotRepository {
        final List<TocEdgeSnapshotEntity> savedEdges = new ArrayList<>();

        @Override
        public List<TocEdgeSnapshotEntity> findLatestSnapshotsForAllEdges() {
            Map<String, TocEdgeSnapshotEntity> latest = new HashMap<>();
            for (TocEdgeSnapshotEntity e : savedEdges) {
                String key = e.getSourceNode() + "->" + e.getTargetNode();
                TocEdgeSnapshotEntity existing = latest.get(key);
                if (existing == null || e.getSnapshotAt().isAfter(existing.getSnapshotAt())) {
                    latest.put(key, e);
                }
            }
            return new ArrayList<>(latest.values());
        }

        @Override
        public Optional<TocEdgeSnapshotEntity> findFirstBySourceNodeAndTargetNodeOrderBySnapshotAtDesc(String sourceNode, String targetNode) {
            return savedEdges.stream()
                    .filter(e -> e.getSourceNode().equals(sourceNode) && e.getTargetNode().equals(targetNode))
                    .sorted((a, b) -> b.getSnapshotAt().compareTo(a.getSnapshotAt()))
                    .findFirst();
        }

        @Override
        public <S extends TocEdgeSnapshotEntity> S save(S entity) {
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            savedEdges.add(entity);
            return entity;
        }

        @Override public <S extends TocEdgeSnapshotEntity> List<S> saveAll(Iterable<S> entities) { return List.of(); }
        @Override public Optional<TocEdgeSnapshotEntity> findById(UUID uuid) { return Optional.empty(); }
        @Override public boolean existsById(UUID uuid) { return false; }
        @Override public List<TocEdgeSnapshotEntity> findAll() { return savedEdges; }
        @Override public List<TocEdgeSnapshotEntity> findAllById(Iterable<UUID> uuids) { return List.of(); }
        @Override public long count() { return savedEdges.size(); }
        @Override public void deleteById(UUID uuid) {}
        @Override public void delete(TocEdgeSnapshotEntity entity) {}
        @Override public void deleteAllById(Iterable<? extends UUID> uuids) {}
        @Override public void deleteAll(Iterable<? extends TocEdgeSnapshotEntity> entities) {}
        @Override public void deleteAll() { savedEdges.clear(); }
        @Override public void flush() {}
        @Override public <S extends TocEdgeSnapshotEntity> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends TocEdgeSnapshotEntity> List<S> saveAllAndFlush(Iterable<S> entities) { return List.of(); }
        @Override public void deleteAllInBatch(Iterable<TocEdgeSnapshotEntity> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> uuids) {}
        @Override public void deleteAllInBatch() {}
        @Override public TocEdgeSnapshotEntity getOne(UUID uuid) { return null; }
        @Override public TocEdgeSnapshotEntity getById(UUID uuid) { return null; }
        @Override public TocEdgeSnapshotEntity getReferenceById(UUID uuid) { return null; }
        @Override public <S extends TocEdgeSnapshotEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends TocEdgeSnapshotEntity> List<S> findAll(org.springframework.data.domain.Example<S> example) { return List.of(); }
        @Override public <S extends TocEdgeSnapshotEntity> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public <S extends TocEdgeSnapshotEntity> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return null; }
        @Override public <S extends TocEdgeSnapshotEntity> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends TocEdgeSnapshotEntity> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends TocEdgeSnapshotEntity, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public List<TocEdgeSnapshotEntity> findAll(org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public org.springframework.data.domain.Page<TocEdgeSnapshotEntity> findAll(org.springframework.data.domain.Pageable pageable) { return null; }
    }
}
