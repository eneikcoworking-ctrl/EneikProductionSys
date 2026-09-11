package com.eneik.production.services.verdict;

import com.eneik.production.kaizen.model.DefectJournalEntity;
import com.eneik.production.kaizen.service.DefectJournalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Falsification and invariant verification for {@link AutonomousVerdictObservationService}.
 *
 * Grounded in {@code FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK} (D011 Perception failure).
 * Proof obligation: Teleosemantic feedback must carry valid semantic state transitions
 * rather than unthrottled repetitive noise.
 *
 * Falsification criteria:
 * - A layer refusal produces a defect entry in {@link DefectJournalService}.
 * - Two consecutive ticks with the identical refusal produce exactly ONE entry (suppression of duplicate noise).
 * - A changed refusal reason produces a second entry.
 * - Resolution back to PERMIT produces ZERO entries and clears the ledger.
 * - A subsequent refusal re-triggering produces a new entry.
 */
class AutonomousVerdictObservationServiceTest {

    private VerdictReconciliation reconciliation;
    private DefectJournalService defectJournalService;
    private AutonomousVerdictObservationService observer;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        reconciliation = mock(VerdictReconciliation.class);
        defectJournalService = mock(DefectJournalService.class);
        observer = new AutonomousVerdictObservationService(reconciliation, defectJournalService, 1);
        projectId = UUID.randomUUID();

        // Default mock return for recordDefect
        when(defectJournalService.recordDefect(any(), any(), any(), any(), any(), any(), anyDouble()))
                .thenAnswer(inv -> new DefectJournalEntity(
                        inv.getArgument(0),
                        inv.getArgument(1),
                        inv.getArgument(2),
                        inv.getArgument(3),
                        inv.getArgument(4),
                        inv.getArgument(5),
                        inv.getArgument(6)
                ));
    }

    @Test
    void falsificationHarness_consecutiveTicksWithSameRefusalProducesSingleDefectRecord() {
        String layer = "doctrine";
        String prop = "doctrine BARCAN-TAG-00_CODE-GUARDIAN accepts the current project state";
        String reason1 = "Owner-role execution has failed work on task 123";
        String reason2 = "Defect-work evidence remains attached to review 456";

        // --- Tick 1: Initial refusal ---
        Judgement withhold1 = Judgement.withhold(layer, prop, reason1, "evidence-1");
        when(reconciliation.reconcile(projectId)).thenReturn(new VerdictReconciliation.Reconciliation(
                Verdict.WITHHOLD, 0, 1, layer, List.of(withhold1)
        ));

        List<DefectJournalEntity> tick1 = observer.observe(projectId);
        assertThat(tick1).hasSize(1);
        verify(defectJournalService, times(1)).recordDefect(
                eq(projectId),
                eq("HIGH"),
                eq("LAYER_VERDICT"),
                eq("doctrine"),
                eq("DOCTRINE_REFUSAL"),
                eq(prop + ": " + reason1),
                eq(1.0)
        );

        // --- Tick 2: Identical refusal on next tick (must be suppressed!) ---
        List<DefectJournalEntity> tick2 = observer.observe(projectId);
        assertThat(tick2).isEmpty();
        // Still exactly 1 call total across tick 1 and tick 2
        verify(defectJournalService, times(1)).recordDefect(any(), any(), any(), any(), any(), any(), anyDouble());

        // --- Tick 3: Refusal reason changes (must record updated defect!) ---
        Judgement withhold2 = Judgement.withhold(layer, prop, reason2, "evidence-2");
        when(reconciliation.reconcile(projectId)).thenReturn(new VerdictReconciliation.Reconciliation(
                Verdict.WITHHOLD, 0, 1, layer, List.of(withhold2)
        ));

        List<DefectJournalEntity> tick3 = observer.observe(projectId);
        assertThat(tick3).hasSize(1);
        // Total calls now 2
        verify(defectJournalService, times(2)).recordDefect(any(), any(), any(), any(), any(), any(), anyDouble());

        ArgumentCaptor<String> descCaptor = ArgumentCaptor.forClass(String.class);
        verify(defectJournalService, times(2)).recordDefect(
                eq(projectId), eq("HIGH"), eq("LAYER_VERDICT"), eq("doctrine"), eq("DOCTRINE_REFUSAL"),
                descCaptor.capture(), eq(1.0)
        );
        assertThat(descCaptor.getAllValues().get(1))
                .contains(reason2)
                .contains("reason updated, was: '" + reason1 + "'");

        // --- Tick 4: Resolution to PERMIT (no defect recorded, ledger cleared) ---
        Judgement permit = Judgement.permit(layer, prop, "all work completed");
        when(reconciliation.reconcile(projectId)).thenReturn(new VerdictReconciliation.Reconciliation(
                Verdict.PERMIT, 0, 0, "", List.of(permit)
        ));

        List<DefectJournalEntity> tick4 = observer.observe(projectId);
        assertThat(tick4).isEmpty();
        // Total calls remain 2
        verify(defectJournalService, times(2)).recordDefect(any(), any(), any(), any(), any(), any(), anyDouble());
        assertThat(observer.getActiveRefusals()).isEmpty();

        // --- Tick 5: Still PERMIT (no defect) ---
        List<DefectJournalEntity> tick5 = observer.observe(projectId);
        assertThat(tick5).isEmpty();
        verify(defectJournalService, times(2)).recordDefect(any(), any(), any(), any(), any(), any(), anyDouble());

        // --- Tick 6: Refusal reoccurs after resolution (must record anew!) ---
        Judgement withholdAgain = Judgement.withhold(layer, prop, reason1, "evidence-3");
        when(reconciliation.reconcile(projectId)).thenReturn(new VerdictReconciliation.Reconciliation(
                Verdict.WITHHOLD, 0, 1, layer, List.of(withholdAgain)
        ));

        List<DefectJournalEntity> tick6 = observer.observe(projectId);
        assertThat(tick6).hasSize(1);
        // Total calls now 3
        verify(defectJournalService, times(3)).recordDefect(any(), any(), any(), any(), any(), any(), anyDouble());
    }

    @Test
    void abstentionProducesZeroDefectRecords() {
        Judgement abstain = Judgement.abstain("doctrine", "doctrine TAG-01 accepts", "stance unknown");
        when(reconciliation.reconcile(projectId)).thenReturn(new VerdictReconciliation.Reconciliation(
                Verdict.ABSTAIN, 1, 0, "doctrine", List.of(abstain)
        ));

        List<DefectJournalEntity> result = observer.observe(projectId);
        assertThat(result).isEmpty();
        verify(defectJournalService, never()).recordDefect(any(), any(), any(), any(), any(), any(), anyDouble());
        assertThat(observer.getActiveRefusals()).isEmpty();
    }

    @Test
    void multipleLayersAndPropositionsTrackedIndependently() {
        Judgement doctrineWithhold = Judgement.withhold("doctrine", "prop-doc", "doc-failed", "ev1");
        Judgement infraWithhold = Judgement.withhold("infrastructure", "prop-infra", "infra-down", "ev2");

        // Tick 1: both layers refuse
        when(reconciliation.reconcile(projectId)).thenReturn(new VerdictReconciliation.Reconciliation(
                Verdict.WITHHOLD, 0, 2, "doctrine", List.of(doctrineWithhold, infraWithhold)
        ));

        List<DefectJournalEntity> tick1 = observer.observe(projectId);
        assertThat(tick1).hasSize(2);
        verify(defectJournalService, times(2)).recordDefect(any(), any(), any(), any(), any(), any(), anyDouble());
        assertThat(observer.getActiveRefusals()).hasSize(2);

        // Tick 2: doctrine clears to PERMIT, infrastructure still refuses
        Judgement doctrinePermit = Judgement.permit("doctrine", "prop-doc", "doc-ok");
        when(reconciliation.reconcile(projectId)).thenReturn(new VerdictReconciliation.Reconciliation(
                Verdict.WITHHOLD, 0, 1, "infrastructure", List.of(doctrinePermit, infraWithhold)
        ));

        List<DefectJournalEntity> tick2 = observer.observe(projectId);
        assertThat(tick2).isEmpty();
        verify(defectJournalService, times(2)).recordDefect(any(), any(), any(), any(), any(), any(), anyDouble());
        // Only infrastructure remains in ledger
        assertThat(observer.getActiveRefusals()).hasSize(1);
        assertThat(observer.getActiveRefusals()).containsKey(
                new AutonomousVerdictObservationService.RefusalKey(projectId, "infrastructure", "prop-infra")
        );
    }

    @Test
    void exceptionInReconciliationHandledGracefully() {
        when(reconciliation.reconcile(projectId)).thenThrow(new RuntimeException("Database connection timeout"));

        List<DefectJournalEntity> result = observer.observe(projectId);
        assertThat(result).isEmpty();
        verify(defectJournalService, never()).recordDefect(any(), any(), any(), any(), any(), any(), anyDouble());
    }

    @Test
    void cadenceThrottlingSkipsIntermediateTicksWhenConfigured() {
        // Observer with cadence = 3
        AutonomousVerdictObservationService cadenceObserver =
                new AutonomousVerdictObservationService(reconciliation, defectJournalService, 3);

        Judgement withhold = Judgement.withhold("doctrine", "prop-cadence", "reason", "ev");
        when(reconciliation.reconcile(projectId)).thenReturn(new VerdictReconciliation.Reconciliation(
                Verdict.WITHHOLD, 0, 1, "doctrine", List.of(withhold)
        ));

        // Tick 1 (tick = 1): executes
        List<DefectJournalEntity> tick1 = cadenceObserver.observe(projectId);
        assertThat(tick1).hasSize(1);
        verify(reconciliation, times(1)).reconcile(projectId);

        // Tick 2 (tick = 2): skipped by cadence
        List<DefectJournalEntity> tick2 = cadenceObserver.observe(projectId);
        assertThat(tick2).isEmpty();
        verify(reconciliation, times(1)).reconcile(projectId); // Still 1 call

        // Tick 3 (tick = 3): skipped by cadence
        List<DefectJournalEntity> tick3 = cadenceObserver.observe(projectId);
        assertThat(tick3).isEmpty();
        verify(reconciliation, times(1)).reconcile(projectId); // Still 1 call

        // Force call: bypasses cadence check
        List<DefectJournalEntity> forceTick = cadenceObserver.observe(projectId, true);
        verify(reconciliation, times(2)).reconcile(projectId); // Now 2 calls
    }

    @Test
    void sanitizeDefectTypeHandlesHyphensAndSpaces() {
        assertThat(AutonomousVerdictObservationService.sanitizeDefectType("doctrine"))
                .isEqualTo("DOCTRINE_REFUSAL");
        assertThat(AutonomousVerdictObservationService.sanitizeDefectType("six-sigma"))
                .isEqualTo("SIX_SIGMA_REFUSAL");
        assertThat(AutonomousVerdictObservationService.sanitizeDefectType("CUSTOM_REFUSAL"))
                .isEqualTo("CUSTOM_REFUSAL");
    }
}
