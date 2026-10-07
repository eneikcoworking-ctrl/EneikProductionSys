package com.eneik.production.services.judgment;

import com.eneik.production.kaizen.service.KaizenService;
import com.eneik.production.models.persistence.InvariantStatusChangeEntity;
import com.eneik.production.repositories.InvariantStatusChangeRepository;
import com.eneik.production.services.settings.SystemSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Popperian Falsification Suite for FactoryJudgmentService.
 * Validates epistemic and architectural judgment invariants under Stage 4:
 * - D008 Karl Popper Falsification Harness (KARL_POPPER_01_FALSIFICATION_HARNESS):
 *     Popper's asymmetry: model is invoked strictly on refutations, never on a timer or confirmation.
 *     Absence of unjudged transitions results in zero model calls, zero tokens, and zero expense.
 *     Baseline registrations (null previous_status) are excluded from the candidate queue.
 * - D010 Alvin Goldman Reliable Process Audit (ALVIN_GOLDMAN_01_RELIABLE_PROCESS_AUDIT):
 *     Causal audit integrity: bounding cycles with maxPerCycle, limiting context history,
 *     preserving retryability on transient UNAVAILABLE endpoints (leaving judged_at null),
 *     while marking UNJUDGEABLE transitions read to prevent head-of-line queue poisoning.
 * - D006 Joseph Raz Deontic Prohibition As Code (DZHOZEF_RAZ_01_PROHIBITION_AS_CODE):
 *     Deontic subordination to KaizenService: findings are recorded as review-only proposals
 *     (SYSTEMIC_DEFECT), never auto-applied without human/arbiter signoff, correctly keyed to
 *     specific invariant designators (avoiding component-level dedup collapse).
 */
class FactoryJudgmentServiceFalsificationTest {

    private InvariantStatusChangeRepository repository;
    private JudgmentAgentClient client;
    private KaizenService kaizenService;
    private SystemSettingsService settings;
    private FactoryJudgmentService service;

    @BeforeEach
    void setUp() {
        repository = mock(InvariantStatusChangeRepository.class);
        client = mock(JudgmentAgentClient.class);
        kaizenService = mock(KaizenService.class);
        settings = mock(SystemSettingsService.class);

        when(settings.effectiveBoolean("judgment_agent_enabled")).thenReturn(true);
        when(repository.findByInvariantKeyOrderByObservedAtDesc(anyString())).thenReturn(List.of());

        service = new FactoryJudgmentService(repository, client, kaizenService, settings);
        ReflectionTestUtils.setField(service, "maxPerCycle", 5);
        ReflectionTestUtils.setField(service, "contextHistoryLimit", 6);
    }

    private InvariantStatusChangeEntity createTransition(String key, String previousStatus, String currentStatus) {
        InvariantStatusChangeEntity entity = new InvariantStatusChangeEntity();
        entity.setId(UUID.randomUUID());
        entity.setInvariantKey(key);
        entity.setPreviousStatus(previousStatus);
        entity.setStatus(currentStatus);
        entity.setStatement("Invariant " + key + " must hold unconditionally");
        entity.setEvidence("Telemetry evidence for " + key);
        entity.setObservedAt(Instant.now());
        return entity;
    }

    // =========================================================================
    // D008: Karl Popper Falsification Harness (KARL_POPPER_01_FALSIFICATION_HARNESS)
    // =========================================================================

    @Test
    @DisplayName("D008 Popper Falsification Harness: No refutation means zero model invocations and zero tokens spent")
    void falsifyPopperAsymmetry_noRefutationMeansNoModelInvocation() {
        // Given no unjudged transitions in the database
        when(repository.findByJudgedAtIsNullAndPreviousStatusIsNotNullOrderByObservedAtAsc())
                .thenReturn(List.of());

        // When scheduled judgment executes
        service.judgeOutstandingRefutations();

        // Then Popper's asymmetry holds: confirmations carry zero information; no LLM call is made
        verify(client, never()).judge(anyString());
        verifyNoInteractions(kaizenService);
    }

    @Test
    @DisplayName("D008 Popper Falsification Harness: Disabled judgment agent avoids database queries and calls")
    void falsifyPopperAsymmetry_disabledAgentAvoidsQueriesAndCalls() {
        when(settings.effectiveBoolean("judgment_agent_enabled")).thenReturn(false);

        service.judgeOutstandingRefutations();

        verifyNoInteractions(repository);
        verifyNoInteractions(client);
        verifyNoInteractions(kaizenService);
    }

    // =========================================================================
    // D010: Alvin Goldman Reliable Process Audit (ALVIN_GOLDMAN_01_RELIABLE_PROCESS_AUDIT)
    // =========================================================================

    @Test
    @DisplayName("D010 Goldman Process Audit: Transient UNAVAILABLE endpoint leaves row unjudged for retry and stops cycle")
    void falsifyGoldmanProcessAudit_unavailableEndpointLeavesRowUnjudgedForRetry() {
        InvariantStatusChangeEntity t1 = createTransition("runtime_status_affects_trust", "pass", "warn");
        InvariantStatusChangeEntity t2 = createTransition("delivered_requires_evidence", "pass", "fail");

        when(repository.findByJudgedAtIsNullAndPreviousStatusIsNotNullOrderByObservedAtAsc())
                .thenReturn(List.of(t1, t2));
        when(client.judge(anyString())).thenReturn(
                new JudgmentAgentClient.Ruling(JudgmentAgentClient.Outcome.UNAVAILABLE, "sidecar connection timeout", "", "")
        );

        service.judgeOutstandingRefutations();

        // t1 was attempted, but failed with UNAVAILABLE
        assertThat(t1.getJudgedAt()).isNull();
        verify(repository, never()).save(t1);

        // t2 was never attempted because the cycle stopped immediately to prevent burning the queue
        assertThat(t2.getJudgedAt()).isNull();
        verify(client, times(1)).judge(anyString());
    }

    @Test
    @DisplayName("D010 Goldman Process Audit: UNJUDGEABLE row is marked read to prevent head-of-line blocking")
    void falsifyGoldmanProcessAudit_unjudgeableRowMarkedReadToPreventQueueBlock() {
        InvariantStatusChangeEntity poison = createTransition("poison_invariant", "pass", "fail");
        InvariantStatusChangeEntity subsequent = createTransition("subsequent_invariant", "pass", "warn");

        when(repository.findByJudgedAtIsNullAndPreviousStatusIsNotNullOrderByObservedAtAsc())
                .thenReturn(List.of(poison, subsequent));
        when(client.judge(anyString()))
                .thenReturn(new JudgmentAgentClient.Ruling(JudgmentAgentClient.Outcome.UNJUDGEABLE, "declined / off-schema", "", ""))
                .thenReturn(new JudgmentAgentClient.Ruling(JudgmentAgentClient.Outcome.ABSTAIN, "explained by migration", "", ""));

        service.judgeOutstandingRefutations();

        // Poison row is marked read so subsequent rows can be evaluated
        assertThat(poison.getJudgedAt()).isNotNull();
        assertThat(subsequent.getJudgedAt()).isNotNull();
        verify(repository).save(poison);
        verify(repository).save(subsequent);
        verify(client, times(2)).judge(anyString());
    }

    @Test
    @DisplayName("D010 Goldman Process Audit: Cycle strictly respects maxPerCycle bound to prevent cost runaway")
    void falsifyGoldmanProcessAudit_cycleRespectsMaxPerCycleBudget() {
        ReflectionTestUtils.setField(service, "maxPerCycle", 2);

        InvariantStatusChangeEntity t1 = createTransition("inv_1", "pass", "warn");
        InvariantStatusChangeEntity t2 = createTransition("inv_2", "pass", "warn");
        InvariantStatusChangeEntity t3 = createTransition("inv_3", "pass", "warn");

        when(repository.findByJudgedAtIsNullAndPreviousStatusIsNotNullOrderByObservedAtAsc())
                .thenReturn(List.of(t1, t2, t3));
        when(client.judge(anyString())).thenReturn(
                new JudgmentAgentClient.Ruling(JudgmentAgentClient.Outcome.ABSTAIN, "no defect", "", "")
        );

        service.judgeOutstandingRefutations();

        verify(client, times(2)).judge(anyString());
        assertThat(t1.getJudgedAt()).isNotNull();
        assertThat(t2.getJudgedAt()).isNotNull();
        assertThat(t3.getJudgedAt()).isNull();
    }

    // =========================================================================
    // D006: Joseph Raz Deontic Prohibition As Code (DZHOZEF_RAZ_01_PROHIBITION_AS_CODE)
    // =========================================================================

    @Test
    @DisplayName("D006 Raz Prohibition As Code: FINDING outcome is filed as review-only proposal against typed invariant component")
    void falsifyRazProhibitionAsCode_findingFiledAsReviewOnlySystemicProposal() {
        InvariantStatusChangeEntity transition = createTransition("defect_requires_invariant_capture", "pass", "fail");
        when(repository.findByJudgedAtIsNullAndPreviousStatusIsNotNullOrderByObservedAtAsc())
                .thenReturn(List.of(transition));

        JudgmentAgentClient.Ruling findingRuling = new JudgmentAgentClient.Ruling(
                JudgmentAgentClient.Outcome.FINDING,
                "Invariant captured without corresponding test barrier",
                "Missing test barrier on captured defect",
                "Add test barrier suite before closing defect"
        );
        when(client.judge(anyString())).thenReturn(findingRuling);

        service.judgeOutstandingRefutations();

        ArgumentCaptor<String> category = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> component = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);

        verify(kaizenService).recordSystemicDefectProposal(
                eq(null), category.capture(), component.capture(), title.capture(), action.capture()
        );

        assertThat(category.getValue()).isEqualTo("Global");
        assertThat(component.getValue())
                .as("Invariant must be prefixed to prevent dedup collision in Kaizen read path")
                .isEqualTo("invariant:defect_requires_invariant_capture");
        assertThat(title.getValue()).isEqualTo("Missing test barrier on captured defect");
        assertThat(action.getValue()).contains("Add test barrier suite before closing defect");
        assertThat(action.getValue()).contains("Ruling: Invariant captured without corresponding test barrier");

        // The entity is only marked judged AFTER filing the proposal
        assertThat(transition.getJudgedAt()).isNotNull();
        verify(repository).save(transition);
    }

    @Test
    @DisplayName("D006 Raz Prohibition As Code: Cycle with all unjudgeable transitions reports judgment layer itself")
    void falsifyRazProhibitionAsCode_quietFailureCycleReportsJudgmentLayerItself() {
        InvariantStatusChangeEntity t1 = createTransition("inv_a", "pass", "warn");
        InvariantStatusChangeEntity t2 = createTransition("inv_b", "pass", "warn");

        when(repository.findByJudgedAtIsNullAndPreviousStatusIsNotNullOrderByObservedAtAsc())
                .thenReturn(List.of(t1, t2));
        when(client.judge(anyString())).thenReturn(
                new JudgmentAgentClient.Ruling(JudgmentAgentClient.Outcome.UNJUDGEABLE, "malformed request / declined", "", "")
        );

        service.judgeOutstandingRefutations();

        // Layer self-policing: quiet drain of the backlog without rulings is itself a systemic defect
        verify(kaizenService).recordSystemicDefectProposal(
                eq(null), eq("Global"), eq("FactoryJudgmentService"),
                eq("Factory judgment produced no ruling on any refutation it read"),
                anyString()
        );
    }
}
