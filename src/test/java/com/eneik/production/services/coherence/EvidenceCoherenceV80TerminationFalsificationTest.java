package com.eneik.production.services.coherence;

import com.eneik.production.kaizen.repository.KaizenProposalRepository;
import com.eneik.production.models.persistence.CoherenceRunEntity;
import com.eneik.production.models.persistence.CoherenceRunNodeResultEntity;
import com.eneik.production.models.persistence.EvidenceNodeEntity;
import com.eneik.production.repositories.CoherenceRunNodeResultRepository;
import com.eneik.production.repositories.CoherenceRunRepository;
import com.eneik.production.repositories.EvidenceNodeRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.services.WishlistContentSimilarityMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Фальсифицирующий замер Ступени 2 для V80 / EvidenceCoherenceService:
 * объяснительная когерентность Тагарда (POL_TAGARD_01 / D003)
 * и не-самоотчетная остановка по внешнему якорю истинности (LYUDVIG_VITGENSHTEYN_14 / D013).
 *
 * Proof obligations:
 * 1. Формула гармонии Тагарда (ECHO Harmony Score):
 *    coherence_score = sum_{i < j} w_{i,j} * a_i * a_j, где a_k in {+1, -1}.
 *    Кооперирующие узлы дают положительный вклад, взаимно исключающие (ингибиторные) при одновременном
 *    принятии дают штраф, а при разделении (один принят, второй отвергнут) повышают общую гармонию.
 * 2. Фиксация в V80 схеме (coherence_runs + coherence_run_node_results):
 *    Каждый цикл сохраняет заголовок CoherenceRunEntity (score, total, accepted) и детализацию
 *    CoherenceRunNodeResultEntity (finalActivation, accepted, confidence) для каждого узла.
 * 3. Не-самоотчетный внешний якорь (Wittgenstein Anti-Mirror Telemetry):
 *    Остановка и вердикт строятся строго на математической релаксации физических свидетельств,
 *    а не на словесных заверениях LLM-агента; пустые окна не создают фантомных записей.
 * 4. Удержание ретенции:
 *    Старые прогоны сверх лимита maxRunsPerProject вычищаются, не повреждая актуальный последний якорь.
 */
class EvidenceCoherenceV80TerminationFalsificationTest {

    private EvidenceNodeRepository evidenceNodeRepository;
    private CoherenceRunRepository coherenceRunRepository;
    private CoherenceRunNodeResultRepository coherenceRunNodeResultRepository;
    private WishlistContentSimilarityMatcher similarityMatcher;
    private ProjectRepository projectRepository;
    private KaizenProposalRepository kaizenProposalRepository;
    private EvidenceCoherenceService service;

    private final List<CoherenceRunEntity> savedRuns = new ArrayList<>();
    private final List<CoherenceRunNodeResultEntity> savedNodeResults = new ArrayList<>();

    @BeforeEach
    void setUp() {
        evidenceNodeRepository = mock(EvidenceNodeRepository.class);
        coherenceRunRepository = mock(CoherenceRunRepository.class);
        coherenceRunNodeResultRepository = mock(CoherenceRunNodeResultRepository.class);
        similarityMatcher = mock(WishlistContentSimilarityMatcher.class);
        projectRepository = mock(ProjectRepository.class);
        kaizenProposalRepository = mock(KaizenProposalRepository.class);

        when(kaizenProposalRepository.findAll()).thenReturn(List.of());
        when(evidenceNodeRepository.findAll()).thenReturn(List.of());

        savedRuns.clear();
        savedNodeResults.clear();

        when(coherenceRunRepository.save(any(CoherenceRunEntity.class))).thenAnswer(inv -> {
            CoherenceRunEntity run = inv.getArgument(0);
            if (run.getId() == null) {
                run.setId(UUID.randomUUID());
            }
            run.setRanAt(Instant.now());
            savedRuns.add(0, run); // latest first
            return run;
        });

        when(coherenceRunNodeResultRepository.save(any(CoherenceRunNodeResultEntity.class))).thenAnswer(inv -> {
            CoherenceRunNodeResultEntity res = inv.getArgument(0);
            if (res.getId() == null) {
                res.setId(UUID.randomUUID());
            }
            savedNodeResults.add(res);
            return res;
        });

        service = new EvidenceCoherenceService(
                evidenceNodeRepository,
                coherenceRunRepository,
                coherenceRunNodeResultRepository,
                similarityMatcher,
                projectRepository,
                kaizenProposalRepository
        );

        ReflectionTestUtils.setField(service, "decay", 0.05);
        ReflectionTestUtils.setField(service, "minActivation", -1.0);
        ReflectionTestUtils.setField(service, "maxActivation", 1.0);
        ReflectionTestUtils.setField(service, "strongEdgeWeight", 0.06);
        ReflectionTestUtils.setField(service, "weakEdgeWeight", 0.03);
        ReflectionTestUtils.setField(service, "specialUnitWeight", 0.05);
        ReflectionTestUtils.setField(service, "maxIterations", 200);
        ReflectionTestUtils.setField(service, "convergenceEpsilon", 0.001);
        ReflectionTestUtils.setField(service, "weakEdgeJaccardThreshold", 0.4);
        ReflectionTestUtils.setField(service, "reconciliationWindowHours", 24);
        ReflectionTestUtils.setField(service, "initialActivation", 0.01);
        ReflectionTestUtils.setField(service, "minReliabilitySamples", 10);
        ReflectionTestUtils.setField(service, "scheduledCycleEnabled", false);
        ReflectionTestUtils.setField(service, "maxRunsPerProject", 5);
    }

    @Test
    @DisplayName("POL_TAGARD_01: Расчет coherence_score строго следует формуле гармонии Тагарда")
    void thagardCoherenceScoreComputationMatchesHarmonyFormula() {
        UUID projectId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();

        // Узлы А и B кооперируют (одна фича, одинаковая полярность NEGATIVE_FINDING) -> w_AB = +0.06
        EvidenceNodeEntity nodeA = createNode(projectId, featureId, EvidenceNodeEntity.Polarity.NEGATIVE_FINDING, "defect-1");
        EvidenceNodeEntity nodeB = createNode(projectId, featureId, EvidenceNodeEntity.Polarity.NEGATIVE_FINDING, "defect-2");

        when(evidenceNodeRepository.findByProjectIdAndCreatedAtAfter(eq(projectId), any()))
                .thenReturn(List.of(nodeA, nodeB));
        when(evidenceNodeRepository.findByProjectIdAndFeatureId(eq(projectId), eq(featureId)))
                .thenReturn(List.of(nodeA, nodeB));

        CoherenceRunEntity run = service.runCoherenceCycle(projectId);

        assertThat(run).isNotNull();
        assertThat(run.getTotalNodes()).isEqualTo(2);
        assertThat(run.getAcceptedNodes()).isEqualTo(2);

        // Оба узла приняты (signA = 1.0, signB = 1.0), вес w = 0.06
        // Coherence score = 0.06 * 1.0 * 1.0 = 0.06
        assertThat(run.getCoherenceScore()).isCloseTo(0.06, within(1e-4));
    }

    @Test
    @DisplayName("V80: Фиксация run и node_results в персистентной структуре (coherence_runs)")
    void v80CoherenceRunsAndNodeResultsDurablePersistence() {
        UUID projectId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();

        EvidenceNodeEntity nodeA = createNode(projectId, featureId, EvidenceNodeEntity.Polarity.NEGATIVE_FINDING, "node A");
        EvidenceNodeEntity nodeB = createNode(projectId, featureId, EvidenceNodeEntity.Polarity.NEGATIVE_FINDING, "node B");

        when(evidenceNodeRepository.findByProjectIdAndCreatedAtAfter(eq(projectId), any()))
                .thenReturn(List.of(nodeA, nodeB));
        when(evidenceNodeRepository.findByProjectIdAndFeatureId(eq(projectId), eq(featureId)))
                .thenReturn(List.of(nodeA, nodeB));

        CoherenceRunEntity run = service.runCoherenceCycle(projectId);

        assertThat(run).isNotNull();
        assertThat(savedRuns).hasSize(1);
        assertThat(savedNodeResults).hasSize(2);

        // Проверка полей V80 coherence_runs
        CoherenceRunEntity savedRun = savedRuns.get(0);
        assertThat(savedRun.getProjectId()).isEqualTo(projectId);
        assertThat(savedRun.getTotalNodes()).isEqualTo(2);
        assertThat(savedRun.getAcceptedNodes()).isEqualTo(2);
        assertThat(savedRun.getCoherenceScore()).isGreaterThan(0.0);

        // Проверка полей V80 coherence_run_node_results
        for (CoherenceRunNodeResultEntity nr : savedNodeResults) {
            assertThat(nr.getCoherenceRunId()).isEqualTo(savedRun.getId());
            assertThat(nr.getEvidenceNodeId()).isIn(nodeA.getId(), nodeB.getId());
            assertThat(nr.isAccepted()).isTrue();
            assertThat(nr.getFinalActivation()).isGreaterThan(0.0);
            assertThat(nr.getConfidence()).isNotNull();
        }
    }

    @Test
    @DisplayName("LYUDVIG_VITGENSHTEYN_14: Пустое окно свидетельств не создает ложных фантомных прогонов")
    void emptyEvidenceWindowProducesNoPhantomRuns() {
        UUID projectId = UUID.randomUUID();
        when(evidenceNodeRepository.findByProjectIdAndCreatedAtAfter(eq(projectId), any()))
                .thenReturn(List.of());

        CoherenceRunEntity run = service.runCoherenceCycle(projectId);

        assertThat(run).isNull();
        verify(coherenceRunRepository, never()).save(any());
        verify(coherenceRunNodeResultRepository, never()).save(any());
        assertThat(savedRuns).isEmpty();
    }

    @Test
    @DisplayName("LYUDVIG_VITGENSHTEYN_14: getLatestCoherenceScore возвращает внешний объективный якорь")
    void getLatestCoherenceScoreReturnsDurableExternalAnchor() {
        UUID projectId = UUID.randomUUID();

        // До запуска прогона - оценка 0.0
        when(coherenceRunRepository.findByProjectIdOrderByRanAtDesc(projectId)).thenReturn(List.of());
        assertThat(service.getLatestCoherenceScore(projectId)).isEqualTo(0.0);

        // После сохранения прогона - возвращает реальный скор из V80 таблицы
        CoherenceRunEntity recordedRun = new CoherenceRunEntity();
        recordedRun.setId(UUID.randomUUID());
        recordedRun.setProjectId(projectId);
        recordedRun.setCoherenceScore(0.1234);
        recordedRun.setTotalNodes(4);
        recordedRun.setAcceptedNodes(3);

        when(coherenceRunRepository.findByProjectIdOrderByRanAtDesc(projectId)).thenReturn(List.of(recordedRun));
        assertThat(service.getLatestCoherenceScore(projectId)).isEqualTo(0.1234);
    }

    @Test
    @DisplayName("Ретенция: pruneOldRuns удаляет устаревшие прогоны сверх лимита, сохраняя свежий якорь")
    void retentionPrunesOldRunsPreservingLatestAnchor() {
        UUID projectId = UUID.randomUUID();
        List<CoherenceRunEntity> runs = new ArrayList<>();

        for (int i = 0; i < 8; i++) {
            CoherenceRunEntity r = new CoherenceRunEntity();
            r.setId(UUID.randomUUID());
            r.setProjectId(projectId);
            r.setCoherenceScore(0.05 * (i + 1));
            runs.add(r);
        }

        // maxRunsPerProject настроен на 5, а в базе 8 прогонов
        when(coherenceRunRepository.findByProjectIdOrderByRanAtDesc(projectId)).thenReturn(runs);

        service.pruneOldRuns(projectId);

        // Должны быть удалены 3 старых прогона (индексы 5, 6, 7)
        verify(coherenceRunRepository).deleteAll(argThat(iterable -> {
            List<CoherenceRunEntity> deleted = (List<CoherenceRunEntity>) iterable;
            return deleted.size() == 3 && deleted.equals(runs.subList(5, 8));
        }));
    }

    private EvidenceNodeEntity createNode(UUID projectId, UUID featureId, EvidenceNodeEntity.Polarity polarity, String summary) {
        EvidenceNodeEntity n = new EvidenceNodeEntity();
        n.setId(UUID.randomUUID());
        n.setProjectId(projectId);
        n.setFeatureId(featureId);
        n.setPolarity(polarity);
        n.setSummaryText(summary);
        n.setDefectJournalId(UUID.randomUUID());
        return n;
    }
}
