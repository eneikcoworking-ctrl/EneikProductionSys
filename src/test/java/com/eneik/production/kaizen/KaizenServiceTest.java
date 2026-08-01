package com.eneik.production.kaizen;

import com.eneik.production.kaizen.model.KaizenProposal;
import com.eneik.production.kaizen.service.DefectJournalService;
import com.eneik.production.kaizen.service.KaizenService;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.audit.SixSigmaAuditService;
import com.eneik.production.toc.engine.TocAnomalyDetector;
import com.eneik.production.toc.engine.TocExecutionGraph;
import com.eneik.production.toc.engine.TocOptimizer;
import com.eneik.production.toc.service.TocSentinelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KaizenServiceTest {

    private TocSentinelService tocSentinelService;
    private SixSigmaAuditService sixSigmaAuditService;
    private TaskRepository taskRepository;

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

        DefectJournalService defectJournalService = mock(DefectJournalService.class);
        when(defectJournalService.getDefectsInWindow(any(), anyInt())).thenReturn(
                List.of(new com.eneik.production.kaizen.model.DefectJournalEntity(
                        null, "HIGH", "BUFFER_TUNING", "AUTOMERGE_PROCESSING", "DBR_BUFFER_FULL", "Buffer full", 5.0
                ))
        );

        com.eneik.production.services.toc.ConstraintIdentificationService constraintIdentificationService =
                mock(com.eneik.production.services.toc.ConstraintIdentificationService.class);
        kaizenService = new KaizenService(tocSentinelService, sixSigmaAuditService, taskRepository, defectJournalService, constraintIdentificationService);
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
}
