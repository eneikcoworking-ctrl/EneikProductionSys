package com.eneik.production.services.coherence;

import com.eneik.production.kaizen.model.KaizenProposalEntity;
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
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Direct, deterministic unit coverage for the ECHO relaxation and AGM entrenchment logic - package-private
 * method visibility exists specifically so these can be tested against controlled small inputs instead of
 * relying on hard-to-hand-verify end-to-end numeric convergence.
 */
class EvidenceCoherenceServiceTest {

    private EvidenceNodeRepository evidenceNodeRepository;
    private CoherenceRunRepository coherenceRunRepository;
    private CoherenceRunNodeResultRepository coherenceRunNodeResultRepository;
    private WishlistContentSimilarityMatcher similarityMatcher;
    private ProjectRepository projectRepository;
    private KaizenProposalRepository kaizenProposalRepository;
    private EvidenceCoherenceService service;

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

        service = new EvidenceCoherenceService(evidenceNodeRepository, coherenceRunRepository,
                coherenceRunNodeResultRepository, similarityMatcher, projectRepository, kaizenProposalRepository);

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
    }

    private EvidenceNodeEntity node(UUID id, UUID projectId, UUID featureId, Integer prNumber,
                                     EvidenceNodeEntity.Polarity polarity, String summary) {
        EvidenceNodeEntity n = new EvidenceNodeEntity();
        n.setId(id);
        n.setProjectId(projectId);
        n.setFeatureId(featureId);
        n.setPrNumber(prNumber);
        n.setPolarity(polarity);
        n.setSummaryText(summary);
        n.setDefectJournalId(UUID.randomUUID()); // arbitrary real source so sourceType() never throws
        return n;
    }

    // --- edgeRelation ---------------------------------------------------------------------------------

    @Test
    void sameNonNeutralPolarityCooperates() {
        assertThat(service.edgeRelation(EvidenceNodeEntity.Polarity.NEGATIVE_FINDING, EvidenceNodeEntity.Polarity.NEGATIVE_FINDING))
                .isEqualTo(EvidenceCoherenceService.EdgeRelation.COOPERATE);
    }

    @Test
    void sameNeutralPolarityAssertsNothing() {
        assertThat(service.edgeRelation(EvidenceNodeEntity.Polarity.NEUTRAL_OBSERVATION, EvidenceNodeEntity.Polarity.NEUTRAL_OBSERVATION))
                .isEqualTo(EvidenceCoherenceService.EdgeRelation.NONE);
    }

    @Test
    void oppositePolarityCompetes() {
        assertThat(service.edgeRelation(EvidenceNodeEntity.Polarity.NEGATIVE_FINDING, EvidenceNodeEntity.Polarity.POSITIVE_CONFIRMATION))
                .isEqualTo(EvidenceCoherenceService.EdgeRelation.COMPETE);
    }

    @Test
    void neutralAgainstEitherExtremeIsNotAContradiction() {
        assertThat(service.edgeRelation(EvidenceNodeEntity.Polarity.NEUTRAL_OBSERVATION, EvidenceNodeEntity.Polarity.NEGATIVE_FINDING))
                .isEqualTo(EvidenceCoherenceService.EdgeRelation.NONE);
    }

    // --- buildWeightMatrix: STRONG dominates WEAK -------------------------------------------------------

    @Test
    void strongEdgeWeightExceedsWeakEdgeWeightWhenTheyConflict() {
        UUID featureId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        EvidenceNodeEntity a = node(UUID.randomUUID(), projectId, featureId, null, EvidenceNodeEntity.Polarity.NEGATIVE_FINDING, "stub in export handler");
        EvidenceNodeEntity b = node(UUID.randomUUID(), projectId, featureId, null, EvidenceNodeEntity.Polarity.NEGATIVE_FINDING, "unrelated text entirely");
        EvidenceNodeEntity c = node(UUID.randomUUID(), projectId, null, null, EvidenceNodeEntity.Polarity.POSITIVE_CONFIRMATION, "some other text");

        when(similarityMatcher.similarity(any(), any())).thenReturn(0.9); // force a WEAK edge candidate between a/b/c pairs lacking a shared featureId

        double[][] weights = service.buildWeightMatrix(List.of(a, b, c));

        // a-b: STRONG (shared featureId), COOPERATE -> +strongEdgeWeight
        assertThat(weights[0][1]).isEqualTo(0.06);
        // a-c: no shared featureId, but similarity forces a WEAK edge; opposite polarity -> COMPETE -> -weakEdgeWeight
        assertThat(weights[0][2]).isEqualTo(-0.03);
        assertThat(Math.abs(weights[0][1])).isGreaterThan(Math.abs(weights[0][2]));
    }

    @Test
    void belowJaccardThresholdProducesNoWeakEdge() {
        when(similarityMatcher.similarity(any(), any())).thenReturn(0.1);
        UUID projectId = UUID.randomUUID();
        EvidenceNodeEntity a = node(UUID.randomUUID(), projectId, null, null, EvidenceNodeEntity.Polarity.NEGATIVE_FINDING, "x");
        EvidenceNodeEntity b = node(UUID.randomUUID(), projectId, null, null, EvidenceNodeEntity.Polarity.POSITIVE_CONFIRMATION, "y");

        double[][] weights = service.buildWeightMatrix(List.of(a, b));

        assertThat(weights[0][1]).isEqualTo(0.0);
    }

    // --- relax: determinism and the special-evidence-unit baseline pull --------------------------------

    @Test
    void relaxIsDeterministicForTheSameInput() {
        double[][] weights = {{0, 0.06}, {0.06, 0}};
        double[] first = service.relax(weights, 2);
        double[] second = service.relax(weights, 2);
        assertThat(first).containsExactly(second);
    }

    @Test
    void isolatedNodeWithNoEdgesEndsUpAcceptedFromTheSpecialUnitAlone() {
        double[][] weights = {{0}};
        double[] activation = service.relax(weights, 1);
        assertThat(activation[0]).isGreaterThan(0.0);
    }

    // --- applyEntrenchmentRevision (Gärdenfors/AGM) -----------------------------------------------------

    @Test
    void tiedEntrenchmentPreservesBothSidesRawVerdict() {
        UUID featureId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        EvidenceNodeEntity neg = node(UUID.randomUUID(), projectId, featureId, null, EvidenceNodeEntity.Polarity.NEGATIVE_FINDING, "neg");
        EvidenceNodeEntity pos = node(UUID.randomUUID(), projectId, featureId, null, EvidenceNodeEntity.Polarity.POSITIVE_CONFIRMATION, "pos");
        when(evidenceNodeRepository.findByProjectIdAndFeatureId(projectId, featureId)).thenReturn(List.of(neg, pos));
        when(coherenceRunNodeResultRepository.findByEvidenceNodeId(any())).thenReturn(List.of()); // no history at all -> 0 vs 0, a tie

        Map<UUID, Boolean> raw = Map.of(neg.getId(), true, pos.getId(), true);
        Map<UUID, Boolean> revised = service.applyEntrenchmentRevision(List.of(neg, pos), raw);

        assertThat(revised.get(neg.getId())).isTrue();
        assertThat(revised.get(pos.getId())).isTrue();
    }

    @Test
    void lessEntrenchedContradictingSideIsRejected() {
        UUID featureId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        // Two OLD nodes, different source types, both historically accepted as NEGATIVE_FINDING for this feature.
        EvidenceNodeEntity oldNeg1 = node(UUID.randomUUID(), projectId, featureId, null, EvidenceNodeEntity.Polarity.NEGATIVE_FINDING, "old neg 1");
        EvidenceNodeEntity oldNeg2 = node(UUID.randomUUID(), projectId, featureId, null, EvidenceNodeEntity.Polarity.NEGATIVE_FINDING, "old neg 2");
        oldNeg2.setDefectJournalId(null);
        oldNeg2.setCodeIntegrityFindingId(UUID.randomUUID()); // distinct sourceType from oldNeg1
        // One NEW node this run, contradicting them.
        EvidenceNodeEntity newPos = node(UUID.randomUUID(), projectId, featureId, null, EvidenceNodeEntity.Polarity.POSITIVE_CONFIRMATION, "new pos");

        when(evidenceNodeRepository.findByProjectIdAndFeatureId(projectId, featureId))
                .thenReturn(List.of(oldNeg1, oldNeg2, newPos));
        when(coherenceRunNodeResultRepository.findByEvidenceNodeId(oldNeg1.getId())).thenReturn(
                List.of(acceptedResult()));
        when(coherenceRunNodeResultRepository.findByEvidenceNodeId(oldNeg2.getId())).thenReturn(
                List.of(acceptedResult()));
        when(coherenceRunNodeResultRepository.findByEvidenceNodeId(newPos.getId())).thenReturn(List.of());

        // This run's raw ECHO verdict: both the (already-entrenched) negative side and the new lone positive
        // node happened to come out accepted (e.g. the positive node had no other corroboration but the
        // special unit alone pulled it positive) - a genuine contradiction for applyEntrenchmentRevision to
        // resolve using history, not this run's numbers alone.
        Map<UUID, Boolean> raw = Map.of(oldNeg1.getId(), true, newPos.getId(), true);
        Map<UUID, Boolean> revised = service.applyEntrenchmentRevision(List.of(oldNeg1, newPos), raw);

        assertThat(revised.get(oldNeg1.getId())).isTrue();
        assertThat(revised.get(newPos.getId())).isFalse();
    }

    private CoherenceRunNodeResultEntity acceptedResult() {
        CoherenceRunNodeResultEntity r = new CoherenceRunNodeResultEntity();
        r.setAccepted(true);
        return r;
    }

    private CoherenceRunNodeResultEntity rejectedResult() {
        CoherenceRunNodeResultEntity r = new CoherenceRunNodeResultEntity();
        r.setAccepted(false);
        return r;
    }

    // --- sourceReliability (Phase 4, Bovens & Hartmann) - 3-tier fallback -------------------------------

    @Test
    void belowSampleThresholdReturnsUncalibratedPrior() {
        // Default setUp() mocks return empty for both tiers - too little data at either level.
        assertThat(service.sourceReliability("CODE_INTEGRITY_FINDING")).isEqualTo(0.5);
    }

    @Test
    void kaizenReliabilityUsesRealOutcomeGroundTruthWhenEnoughSamplesExist() {
        List<KaizenProposalEntity> proposals = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            proposals.add(kaizenProposalEntity("STANDARDIZED"));
        }
        for (int i = 0; i < 2; i++) {
            proposals.add(kaizenProposalEntity("REVERTED"));
        }
        when(kaizenProposalRepository.findAll()).thenReturn(proposals);

        assertThat(service.sourceReliability("KAIZEN_PROPOSAL")).isEqualTo(0.8);
    }

    @Test
    void nonKaizenSourceFallsBackToCoherenceEngineAcceptanceHistoryWhenNoOutcomeDataExists() {
        List<EvidenceNodeEntity> history = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            EvidenceNodeEntity n = node(UUID.randomUUID(), UUID.randomUUID(), null, null, EvidenceNodeEntity.Polarity.NEGATIVE_FINDING, "x");
            history.add(n);
            when(coherenceRunNodeResultRepository.findByEvidenceNodeId(n.getId())).thenReturn(List.of(acceptedResult()));
        }
        for (int i = 0; i < 3; i++) {
            EvidenceNodeEntity n = node(UUID.randomUUID(), UUID.randomUUID(), null, null, EvidenceNodeEntity.Polarity.NEGATIVE_FINDING, "x");
            history.add(n);
            CoherenceRunNodeResultEntity rejected = new CoherenceRunNodeResultEntity();
            rejected.setAccepted(false);
            when(coherenceRunNodeResultRepository.findByEvidenceNodeId(n.getId())).thenReturn(List.of(rejected));
        }
        when(evidenceNodeRepository.findAll()).thenReturn(history);

        assertThat(service.sourceReliability("DEFECT_JOURNAL")).isEqualTo(0.7);
    }

    private KaizenProposalEntity kaizenProposalEntity(String status) {
        KaizenProposalEntity e = new KaizenProposalEntity();
        e.setId("kz-" + UUID.randomUUID());
        e.setStatus(status);
        return e;
    }

    // --- computeConfidences (Phase 4) ------------------------------------------------------------------

    @Test
    void loneAcceptedNodeGetsItsOwnSourceReliabilityAsConfidence() {
        UUID projectId = UUID.randomUUID();
        EvidenceNodeEntity solo = node(UUID.randomUUID(), projectId, null, null, EvidenceNodeEntity.Polarity.NEGATIVE_FINDING, "solo finding");

        Map<UUID, Double> confidences = service.computeConfidences(List.of(solo), Map.of(solo.getId(), true));

        assertThat(confidences.get(solo.getId())).isEqualTo(0.5); // uncalibrated prior, default mocks
    }

    @Test
    void twoAgreeingCorroboratingSourcesProduceHigherConfidenceThanEitherAlone() {
        UUID projectId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        EvidenceNodeEntity a = node(UUID.randomUUID(), projectId, featureId, null, EvidenceNodeEntity.Polarity.NEGATIVE_FINDING, "a");
        a.setDefectJournalId(UUID.randomUUID());
        EvidenceNodeEntity b = node(UUID.randomUUID(), projectId, featureId, null, EvidenceNodeEntity.Polarity.NEGATIVE_FINDING, "b");
        b.setDefectJournalId(null);
        b.setCodeIntegrityFindingId(UUID.randomUUID());

        // Force both source types to the SAME moderate (non-saturating) reliability via the coherence-
        // history fallback tier: 7 accepted / 3 rejected each = 0.7, not 1.0, so the combination effect is
        // actually visible instead of both individual and combined values saturating near the sigmoid ceiling.
        List<EvidenceNodeEntity> allHistory = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            EvidenceNodeEntity h1 = node(UUID.randomUUID(), projectId, null, null, EvidenceNodeEntity.Polarity.NEGATIVE_FINDING, "h1");
            allHistory.add(h1);
            when(coherenceRunNodeResultRepository.findByEvidenceNodeId(h1.getId()))
                    .thenReturn(List.of(i < 7 ? acceptedResult() : rejectedResult()));
            EvidenceNodeEntity h2 = node(UUID.randomUUID(), projectId, null, null, EvidenceNodeEntity.Polarity.NEGATIVE_FINDING, "h2");
            h2.setDefectJournalId(null);
            h2.setCodeIntegrityFindingId(UUID.randomUUID());
            allHistory.add(h2);
            when(coherenceRunNodeResultRepository.findByEvidenceNodeId(h2.getId()))
                    .thenReturn(List.of(i < 7 ? acceptedResult() : rejectedResult()));
        }
        when(evidenceNodeRepository.findAll()).thenReturn(allHistory);

        double soloReliability = service.sourceReliability("DEFECT_JOURNAL"); // 0.7

        Map<UUID, Double> confidences = service.computeConfidences(List.of(a, b), Map.of(a.getId(), true, b.getId(), true));

        assertThat(confidences.get(a.getId())).isEqualTo(confidences.get(b.getId())); // same cluster, same polarity -> combined equally
        assertThat(confidences.get(a.getId())).isGreaterThan(soloReliability);
    }

    // --- runCoherenceCycle: persistence and the empty-window skip ---------------------------------------

    @Test
    void noEvidenceInWindowSkipsTheCycleEntirely() {
        UUID projectId = UUID.randomUUID();
        when(evidenceNodeRepository.findByProjectIdAndCreatedAtAfter(any(), any())).thenReturn(List.of());

        CoherenceRunEntity result = service.runCoherenceCycle(projectId);

        assertThat(result).isNull();
        org.mockito.Mockito.verify(coherenceRunRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void runPersistsOneRunAndOneResultPerNode() {
        UUID projectId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        EvidenceNodeEntity a = node(UUID.randomUUID(), projectId, featureId, null, EvidenceNodeEntity.Polarity.NEGATIVE_FINDING, "a");
        EvidenceNodeEntity b = node(UUID.randomUUID(), projectId, featureId, null, EvidenceNodeEntity.Polarity.NEGATIVE_FINDING, "b");
        when(evidenceNodeRepository.findByProjectIdAndCreatedAtAfter(any(), any())).thenReturn(List.of(a, b));
        when(evidenceNodeRepository.findByProjectIdAndFeatureId(any(), any())).thenReturn(List.of(a, b));
        when(coherenceRunNodeResultRepository.findByEvidenceNodeId(any())).thenReturn(List.of());
        when(coherenceRunRepository.save(any())).thenAnswer(inv -> {
            CoherenceRunEntity run = inv.getArgument(0);
            run.setId(UUID.randomUUID());
            return run;
        });

        CoherenceRunEntity result = service.runCoherenceCycle(projectId);

        assertThat(result).isNotNull();
        assertThat(result.getTotalNodes()).isEqualTo(2);
        org.mockito.Mockito.verify(coherenceRunNodeResultRepository, org.mockito.Mockito.times(2)).save(any());
    }
}
