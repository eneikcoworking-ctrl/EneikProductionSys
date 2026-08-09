package com.eneik.production.kaizen;

import com.eneik.production.kaizen.model.KaizenProposal;
import com.eneik.production.kaizen.model.KaizenProposalEntity;
import com.eneik.production.kaizen.repository.KaizenProposalRepository;
import com.eneik.production.kaizen.service.DefectJournalService;
import com.eneik.production.kaizen.service.KaizenService;
import com.eneik.production.models.persistence.EvidenceNodeEntity;
import com.eneik.production.repositories.EvidenceNodeRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.audit.SixSigmaAuditService;
import com.eneik.production.toc.engine.TocAnomalyDetector;
import com.eneik.production.toc.engine.TocExecutionGraph;
import com.eneik.production.toc.engine.TocOptimizer;
import com.eneik.production.toc.service.TocSentinelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KaizenServiceTest {

    private TocSentinelService tocSentinelService;
    private SixSigmaAuditService sixSigmaAuditService;
    private TaskRepository taskRepository;
    private KaizenProposalRepository kaizenProposalRepository;
    private EvidenceNodeRepository evidenceNodeRepository;
    private com.eneik.production.services.lever.LeverPromotionService leverPromotionService;
    private DefectJournalService defectJournalService;

    private KaizenService kaizenService;

    @BeforeEach
    void setUp() {
        TocExecutionGraph graph = new TocExecutionGraph();
        TocAnomalyDetector anomalyDetector = new TocAnomalyDetector(graph);
        TocOptimizer optimizer = new TocOptimizer(graph);
        tocSentinelService = new TocSentinelService(graph, anomalyDetector, optimizer);

        sixSigmaAuditService = mock(SixSigmaAuditService.class);
        taskRepository = mock(TaskRepository.class);

        when(taskRepository.findAll()).thenReturn(Collections.emptyList());
        when(sixSigmaAuditService.calculateFullSixSigmaAudit()).thenReturn(
                new SixSigmaAuditService.SixSigmaAuditReport(
                        null, "FACTORY_WIDE_ALL_PROJECTS", 100, 5, 50000.0, 95.0, 3.2, "AVERAGE_THREE_SIGMA",
                        Collections.emptyMap(), Collections.emptyMap(), java.time.Instant.now()
                )
        );
        when(sixSigmaAuditService.computeCtqBreakdown(any())).thenReturn(Collections.emptyList());

        defectJournalService = mock(DefectJournalService.class);
        when(defectJournalService.getDefectsInWindow(any(), anyInt())).thenReturn(
                List.of(new com.eneik.production.kaizen.model.DefectJournalEntity(
                        null, "HIGH", "BUFFER_TUNING", "AUTOMERGE_PROCESSING", "DBR_BUFFER_FULL", "Buffer full", 5.0
                ))
        );

        com.eneik.production.services.toc.ConstraintIdentificationService constraintIdentificationService =
                mock(com.eneik.production.services.toc.ConstraintIdentificationService.class);

        // Map-backed fakes standing in for real persistence (KaizenProposal used to live only in an
        // in-memory ConcurrentHashMap inside KaizenService itself - 2026-08-05 fix moved it to a real
        // repository, so tests now need a repository double that actually stores/returns data).
        Map<String, KaizenProposalEntity> proposalStore = new ConcurrentHashMap<>();
        kaizenProposalRepository = mock(KaizenProposalRepository.class);
        when(kaizenProposalRepository.save(any())).thenAnswer(inv -> {
            KaizenProposalEntity e = inv.getArgument(0);
            proposalStore.put(e.getId(), e);
            return e;
        });
        when(kaizenProposalRepository.findAll()).thenAnswer(inv -> new ArrayList<>(proposalStore.values()));
        when(kaizenProposalRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(proposalStore.get(inv.getArgument(0))));
        doAnswer(inv -> { proposalStore.remove(inv.getArgument(0)); return null; }).when(kaizenProposalRepository).deleteById(any());

        evidenceNodeRepository = mock(EvidenceNodeRepository.class);
        when(evidenceNodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, EvidenceNodeEntity.class));

        leverPromotionService = mock(com.eneik.production.services.lever.LeverPromotionService.class);
        when(leverPromotionService.currentStage(any())).thenReturn(com.eneik.production.services.lever.LeverStage.OBSERVE_ONLY);

        kaizenService = new KaizenService(tocSentinelService, sixSigmaAuditService, taskRepository, defectJournalService,
                constraintIdentificationService, kaizenProposalRepository, evidenceNodeRepository, leverPromotionService);
    }

    @Test
    void proposalsSurviveANewServiceInstanceNotJustTheOldInMemoryMap() {
        // Regression test for the real incident: KaizenProposal used to live only in KaizenService's own
        // ConcurrentHashMap field - a fresh instance (what a backend restart produces) had zero proposals no
        // matter what happened before. Now that storage is a real repository, a second KaizenService
        // instance backed by the SAME repository (simulating "process restarted, database did not") must
        // still see proposals recorded by the first instance.
        kaizenService.recordSystemicDefectProposal(null, "Global", "Orphaned pipeline stage",
                "Coverage falsification stage dispatched an empty placeholder");

        KaizenService restarted = new KaizenService(tocSentinelService, sixSigmaAuditService, taskRepository,
                mock(DefectJournalService.class),
                mock(com.eneik.production.services.toc.ConstraintIdentificationService.class),
                kaizenProposalRepository, evidenceNodeRepository, leverPromotionService);

        assertThat(restarted.getAllProposals()).hasSize(1);
    }

    @Test
    void systemicDefectProposalWritesANegativeEvidenceNode() {
        java.util.concurrent.atomic.AtomicReference<EvidenceNodeEntity> saved = new java.util.concurrent.atomic.AtomicReference<>();
        when(evidenceNodeRepository.save(any())).thenAnswer(inv -> {
            EvidenceNodeEntity e = inv.getArgument(0);
            saved.set(e);
            return e;
        });

        kaizenService.recordKnownPatternViolationProposal(null, "Global", 7, "Some Pattern",
                "Charter pattern violated", "A concrete violation");

        assertThat(saved.get()).isNotNull();
        assertThat(saved.get().getPolarity()).isEqualTo(EvidenceNodeEntity.Polarity.NEGATIVE_FINDING);
        assertThat(saved.get().getKaizenProposalId()).isNotBlank();
    }

    @Test
    void testScanAndApplyPdcaCycle() {
        // Plan: scan opportunities
        List<KaizenProposal> opportunities = kaizenService.scanForOpportunities();
        assertThat(opportunities).isNotEmpty();

        KaizenProposal proposal = opportunities.get(0);
        assertThat(proposal.getStatus()).isEqualTo(KaizenProposal.ProposalStatus.PROPOSED);

        // Do: apply micro step
        boolean applied = kaizenService.applyMicroStep(proposal.getId());
        assertThat(applied).isTrue();

        // Check & Act: evaluate and standardize
        KaizenProposal result = kaizenService.evaluateAndStandardize(proposal.getId());
        assertThat(result.getStatus()).isIn(KaizenProposal.ProposalStatus.STANDARDIZED, KaizenProposal.ProposalStatus.REVERTED);
    }

    // 2026-08-08 (ML-update patch, Phase 1): F1_KAIZEN_CTQ_TARGETING lever - observe_only must be a
    // no-op (existing "QualityGate" behavior unchanged) even when a dominant CTQ is real and observable;
    // only a promoted lever may actually retarget the proposal.

    private List<SixSigmaAuditService.CtqEntry> dominantCtqBreakdown() {
        return List.of(
                new SixSigmaAuditService.CtqEntry("unit_tests", 8, 10),
                new SixSigmaAuditService.CtqEntry("lint", 1, 10)
        );
    }

    @Test
    void observeOnlyLeverLeavesTheDefectEliminationProposalGenericEvenWithADominantCtq() {
        when(sixSigmaAuditService.computeCtqBreakdown(any())).thenReturn(dominantCtqBreakdown());
        when(leverPromotionService.currentStage(KaizenService.F1_KAIZEN_CTQ_TARGETING))
                .thenReturn(com.eneik.production.services.lever.LeverStage.OBSERVE_ONLY);
        // recordDefect is mocked (no real persistence), so getDefectsInWindow must independently reflect
        // what resolveQualityGateComponent would have actually written at this stage - "QualityGate", the
        // unchanged incumbent, since the lever is still at observe_only.
        when(defectJournalService.getDefectsInWindow(any(), anyInt())).thenReturn(
                List.of(new com.eneik.production.kaizen.model.DefectJournalEntity(
                        null, "HIGH", "DEFECT_ELIMINATION", "QualityGate", "HIGH_DPMO_DEFECT", "DPMO spike", 50000.0
                ))
        );

        List<KaizenProposal> opportunities = kaizenService.scanForOpportunities();

        KaizenProposal defectElimination = opportunities.stream()
                .filter(p -> p.getCategory() == KaizenProposal.KaizenCategory.DEFECT_ELIMINATION)
                .findFirst().orElseThrow();
        assertThat(defectElimination.getTargetComponent()).isEqualTo("QualityGate");
        org.mockito.Mockito.verify(leverPromotionService).recordObservation(
                org.mockito.ArgumentMatchers.eq(KaizenService.F1_KAIZEN_CTQ_TARGETING),
                any(), org.mockito.ArgumentMatchers.eq("QualityGate"), org.mockito.ArgumentMatchers.eq("unit_tests"),
                org.mockito.ArgumentMatchers.eq(com.eneik.production.services.lever.LeverAgreement.TRUE), any());
    }

    @Test
    void promotedLeverRetargetsTheDefectEliminationProposalAtTheDominantCheck() {
        when(sixSigmaAuditService.computeCtqBreakdown(any())).thenReturn(dominantCtqBreakdown());
        when(leverPromotionService.currentStage(KaizenService.F1_KAIZEN_CTQ_TARGETING))
                .thenReturn(com.eneik.production.services.lever.LeverStage.SOFT_GATE);
        when(defectJournalService.getDefectsInWindow(any(), anyInt())).thenReturn(
                List.of(new com.eneik.production.kaizen.model.DefectJournalEntity(
                        null, "HIGH", "DEFECT_ELIMINATION", "unit_tests", "HIGH_DPMO_DEFECT", "DPMO spike", 50000.0
                ))
        );

        List<KaizenProposal> opportunities = kaizenService.scanForOpportunities();

        KaizenProposal defectElimination = opportunities.stream()
                .filter(p -> p.getCategory() == KaizenProposal.KaizenCategory.DEFECT_ELIMINATION)
                .findFirst().orElseThrow();
        assertThat(defectElimination.getTargetComponent()).isEqualTo("unit_tests");
    }
}
