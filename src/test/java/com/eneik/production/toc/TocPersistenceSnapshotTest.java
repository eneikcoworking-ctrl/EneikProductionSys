package com.eneik.production.toc;

import com.eneik.production.toc.engine.TocAnomalyDetector;
import com.eneik.production.toc.engine.TocExecutionGraph;
import com.eneik.production.toc.engine.TocOptimizer;
import com.eneik.production.toc.model.DbrStatus;
import com.eneik.production.toc.model.TocEdge;
import com.eneik.production.toc.model.TocNode;
import com.eneik.production.toc.model.TocStages;
import com.eneik.production.toc.model.TocToken;
import com.eneik.production.toc.model.persistence.TocEdgeSnapshotEntity;
import com.eneik.production.toc.model.persistence.TocNodeSnapshotEntity;
import com.eneik.production.toc.repository.TocEdgeSnapshotRepository;
import com.eneik.production.toc.repository.TocNodeSnapshotRepository;
import com.eneik.production.toc.service.TocPersistenceService;
import com.eneik.production.toc.service.TocSentinelService;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verification & Falsification harness for Prescription 47 (D010 PERSISTENCE_SNAPSHOT).
 *
 * Grounding:
 * - BARCAN-TAG-05_NECESSARY-IDENTITY:01:derek-parfit / PERSISTENCE_SNAPSHOT [D010, Parfit 1984]:
 *   Entity identity and execution parameters are continuous across snapshots.
 *   Prevents cold-start memory wipe where Welford variance series are zeroed out on restart
 *   and constraint decisions are made on a sample size of 1.
 * - ALFRED_TARSKIY_01_FALSIFICATION_HARNESS (D008):
 *   Falsifies the unpersisted condition by demonstrating that after restart, limits and constraint
 *   rulings are computed from durable historical series rather than falling back to default floor
 *   or single-sample distortion.
 */
class TocPersistenceSnapshotTest {

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
    @DisplayName("PARFIT_01: @Entity classes exist under com.eneik.production.toc package")
    void entityClassesExistInTocPackage() {
        assertThat(TocNodeSnapshotEntity.class.isAnnotationPresent(Entity.class)).isTrue();
        assertThat(TocEdgeSnapshotEntity.class.isAnnotationPresent(Entity.class)).isTrue();
        assertThat(TocNodeSnapshotEntity.class.getPackageName()).startsWith("com.eneik.production.toc");
        assertThat(TocEdgeSnapshotEntity.class.getPackageName()).startsWith("com.eneik.production.toc");
    }

    @Test
    @DisplayName("PERSISTENCE_SNAPSHOT: Welford metrics and edges persist and restore across process restart")
    void welfordMetricsAndEdgesRestoreAcrossProcessRestart() {
        // --- PHASE 1: Execution before restart ---
        TocExecutionGraph graph1 = new TocExecutionGraph();
        TocAnomalyDetector anomalyDetector1 = new TocAnomalyDetector(graph1);
        TocOptimizer optimizer1 = new TocOptimizer(graph1, 15);
        TocSentinelService sentinel1 = new TocSentinelService(graph1, anomalyDetector1, optimizer1, persistenceService);

        // Record 10 executions on AUTOMERGE_PROCESSING (~10.0s mean)
        for (int i = 0; i < 10; i++) {
            TocToken token = sentinel1.startExecution("SCENARIO_MERGE", 40);
            sentinel1.enterStep(token, TocStages.AUTOMERGE_PROCESSING);
            // Simulate 10s duration
            sentinel1.exitStep(token, TocStages.AUTOMERGE_PROCESSING, true);
            sentinel1.endExecution(token, true);
        }

        // Record 5 executions on ORCHESTRATION_PROCESSING (~2.0s mean)
        for (int i = 0; i < 5; i++) {
            TocToken token = sentinel1.startExecution("SCENARIO_ORCH", 30);
            sentinel1.enterStep(token, TocStages.ORCHESTRATION_PROCESSING);
            sentinel1.exitStep(token, TocStages.ORCHESTRATION_PROCESSING, true);
            sentinel1.endExecution(token, true);
        }

        assertThat(nodeRepo.savedSnapshots).isNotEmpty();

        // --- PHASE 2: Complete Process Restart ---
        // New empty in-memory graph representing fresh JVM process
        TocExecutionGraph restartedGraph = new TocExecutionGraph();
        assertThat(restartedGraph.getAllNodes()).isEmpty();

        TocAnomalyDetector restartedDetector = new TocAnomalyDetector(restartedGraph);
        TocOptimizer restartedOptimizer = new TocOptimizer(restartedGraph, 15);

        // Sentinel initialization restores historical state from persistenceService
        TocSentinelService restartedSentinel = new TocSentinelService(
                restartedGraph,
                restartedDetector,
                restartedOptimizer,
                persistenceService
        );

        // Verification: nodes exist with historical count, not zero
        TocNode restoredAutomerge = restartedGraph.getNode(TocStages.AUTOMERGE_PROCESSING);
        TocNode restoredOrch = restartedGraph.getNode(TocStages.ORCHESTRATION_PROCESSING);

        assertThat(restoredAutomerge).isNotNull();
        assertThat(restoredOrch).isNotNull();

        assertThat(restoredAutomerge.getCompletedCount()).isEqualTo(10);
        assertThat(restoredAutomerge.hasObservedDuration()).isTrue();
        assertThat(restoredOrch.getCompletedCount()).isEqualTo(5);
        assertThat(restoredOrch.hasObservedDuration()).isTrue();

        // Immediate Drum-Buffer-Rope evaluation after restart relies on historical evidence
        DbrStatus initialStatus = restartedOptimizer.getLatestDbrStatus();
        assertThat(initialStatus.primaryConstraintNode()).isNotEqualTo("NONE");
        assertThat(initialStatus.recommendation()).contains("System flow optimal");
    }

    @Test
    @DisplayName("FALSIFICATION: Refutes single-observation decision after restart")
    void refutesSingleObservationConstraintDecisionAfterRestart() {
        // Populate historical database snapshot directly (e.g. from previous run)
        // Node A had 50 runs with mean 20,000 ms (~20s)
        TocNodeSnapshotEntity historicalNodeA = new TocNodeSnapshotEntity(
                "HEAVY_STEP",
                50,
                0,
                50 * 20_000_000_000L,
                20000.0,
                50 * 100000.0,
                100.0,
                20200.0,
                Instant.now().minusSeconds(60)
        );
        nodeRepo.savedSnapshots.add(historicalNodeA);

        // Fresh JVM restart
        TocExecutionGraph restartedGraph = new TocExecutionGraph();
        TocAnomalyDetector restartedDetector = new TocAnomalyDetector(restartedGraph);
        TocOptimizer restartedOptimizer = new TocOptimizer(restartedGraph, 15);
        TocSentinelService restartedSentinel = new TocSentinelService(
                restartedGraph,
                restartedDetector,
                restartedOptimizer,
                persistenceService
        );

        TocNode nodeA = restartedGraph.getNode("HEAVY_STEP");
        assertThat(nodeA).isNotNull();
        assertThat(nodeA.getCompletedCount()).isEqualTo(50);
        assertThat(nodeA.getMeanDurationMs()).isEqualTo(20000.0);

        // Now observe 1 new fast run of 500 ms in the new process
        // Online Welford update must calculate (50 * 20000 + 500) / 51 ≈ 19617.6 ms
        // If memory was wiped, count would be 1 and mean would collapse to 500 ms!
        nodeA.recordExecution(500_000_000L, true);

        assertThat(nodeA.getCompletedCount()).isEqualTo(51);
        assertThat(nodeA.getMeanDurationMs()).isGreaterThan(19000.0);
        assertThat(nodeA.getMeanDurationMs()).isLessThan(20000.0);

        // Dynamic timeout limit remains grounded in the 51 observations, not dropping to floor
        double timeoutLimit = nodeA.getDynamicTimeoutLimitMs(2.0, 5000.0);
        assertThat(timeoutLimit).isGreaterThan(19000.0);
    }

    @Test
    @DisplayName("Edge transitions persist and restore across process restart")
    void edgeTransitionsPersistAndRestoreAcrossRestart() {
        TocExecutionGraph graph1 = new TocExecutionGraph();
        TocAnomalyDetector anomalyDetector1 = new TocAnomalyDetector(graph1);
        TocOptimizer optimizer1 = new TocOptimizer(graph1, 15);
        TocSentinelService sentinel1 = new TocSentinelService(graph1, anomalyDetector1, optimizer1, persistenceService);

        // Perform transition STAGE_ORCH -> STAGE_DISPATCH
        TocToken token = sentinel1.startExecution("TEST_PIPELINE", 50);
        sentinel1.enterStep(token, "STAGE_ORCH");
        sentinel1.enterStep(token, "STAGE_DISPATCH");
        sentinel1.exitStep(token, "STAGE_DISPATCH", true);
        sentinel1.exitStep(token, "STAGE_ORCH", true);
        sentinel1.endExecution(token, true);

        assertThat(edgeRepo.savedEdges).isNotEmpty();

        // Fresh restart
        TocExecutionGraph graph2 = new TocExecutionGraph();
        TocAnomalyDetector anomalyDetector2 = new TocAnomalyDetector(graph2);
        TocOptimizer optimizer2 = new TocOptimizer(graph2, 15);
        TocSentinelService sentinel2 = new TocSentinelService(graph2, anomalyDetector2, optimizer2, persistenceService);

        TocEdge edge = graph2.getOrCreateEdge("STAGE_ORCH", "STAGE_DISPATCH");
        assertThat(edge).isNotNull();
        assertThat(edge.getTransitionCount()).isGreaterThanOrEqualTo(1);
    }

    // In-memory test implementations of repositories
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
