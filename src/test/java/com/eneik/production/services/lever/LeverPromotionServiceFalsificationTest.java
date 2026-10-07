package com.eneik.production.services.lever;

import com.eneik.production.models.persistence.LeverObservation;
import com.eneik.production.models.persistence.LeverPromotionStateEntity;
import com.eneik.production.repositories.LeverObservationRepository;
import com.eneik.production.repositories.LeverPromotionStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Popperian Falsification Suite for LeverPromotionService.
 * Validates epistemic, belief-revision, and authority promotion invariants under Stage 4:
 * - D007 Peter Gärdenfors Belief Update Ledger (PITER_GERDENFORS_01_BELIEF_UPDATE_LEDGER):
 *     AGM belief revision: candidate levers begin strictly in observe_only mode with zero live authority.
 *     Revision of promotion stage follows one canonical monotonic path; individual levers cannot mutate
 *     their stage directly. Fresh evidence is required for each promotion step.
 * - D008 Karl Popper Falsification Harness (KARL_POPPER_01_FALSIFICATION_HARNESS):
 *     Severe test requirement: promotions demand resolved sample size N >= 20 and agreement rate >= 80%
 *     within the 14-day recency window. A single real disagreement immediately demotes a promoted lever
 *     without waiting for scheduled batch evaluation cycles.
 * - D010 Alvin Goldman Reliable Process Audit (ALVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS):
 *     Asymmetric trust dynamics: gaining trust requires accumulated evidence over time; losing trust
 *     is immediate upon falsification. Stale historical samples cannot cause repeated promotions.
 */
class LeverPromotionServiceFalsificationTest {

    private LeverPromotionStateRepository stateRepository;
    private LeverObservationRepository observationRepository;
    private LeverPromotionService service;

    @BeforeEach
    void setUp() {
        stateRepository = mock(LeverPromotionStateRepository.class);
        observationRepository = mock(LeverObservationRepository.class);
        service = new LeverPromotionService(stateRepository, observationRepository);
        when(stateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private List<LeverObservation> createObservations(int count, String agreement, Instant time) {
        List<LeverObservation> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LeverObservation obs = new LeverObservation();
            obs.setAgreement(agreement);
            obs.setObservedAt(time);
            list.add(obs);
        }
        return list;
    }

    // =========================================================================
    // D007: Peter Gärdenfors Belief Revision (PITER_GERDENFORS_01_BELIEF_UPDATE_LEDGER)
    // =========================================================================

    @Test
    @DisplayName("D007 Gärdenfors Belief Revision: New lever starts at observe_only with zero operational authority")
    void falsifyGardenforsBeliefRevision_newLeverStartsAtObserveOnly() {
        when(stateRepository.findById("CANDIDATE_LEVER")).thenReturn(Optional.empty());

        service.recordObservation(
                "CANDIDATE_LEVER", "subj-101",
                "incumbent_action", "candidate_action",
                LeverAgreement.TRUE, "candidate_action"
        );

        ArgumentCaptor<LeverPromotionStateEntity> captor = ArgumentCaptor.forClass(LeverPromotionStateEntity.class);
        verify(stateRepository).save(captor.capture());

        LeverPromotionStateEntity saved = captor.getValue();
        assertThat(saved.getCurrentStage()).isEqualTo(LeverStage.OBSERVE_ONLY.wireValue());
        assertThat(saved.getSampleCount()).isEqualTo(1L);
        assertThat(saved.getAgreementCount()).isEqualTo(1L);
    }

    // =========================================================================
    // D008: Karl Popper Falsification Harness (KARL_POPPER_01_FALSIFICATION_HARNESS)
    // =========================================================================

    @Test
    @DisplayName("D008 Popper Falsification Harness: Real disagreement triggers immediate demotion without waiting for cron")
    void falsifyPopperFalsification_disagreementTriggersImmediateDemotion() {
        LeverPromotionStateEntity promotedState = new LeverPromotionStateEntity();
        promotedState.setLeverKey("HARD_GATE_LEVER");
        promotedState.setCurrentStage(LeverStage.HARD_GATE.wireValue());
        promotedState.setSampleCount(100);
        promotedState.setAgreementCount(95);

        when(stateRepository.findById("HARD_GATE_LEVER")).thenReturn(Optional.of(promotedState));

        // When a real operational disagreement occurs
        service.recordObservation(
                "HARD_GATE_LEVER", "subj-202",
                "incumbent_action", "candidate_action",
                LeverAgreement.FALSE, "incumbent_action"
        );

        ArgumentCaptor<LeverPromotionStateEntity> captor = ArgumentCaptor.forClass(LeverPromotionStateEntity.class);
        verify(stateRepository).save(captor.capture());

        LeverPromotionStateEntity demoted = captor.getValue();
        assertThat(demoted.getCurrentStage())
                .as("Popperian refutation: a single falsifying event immediately revokes the higher stage")
                .isEqualTo(LeverStage.SOFT_GATE.wireValue());
        assertThat(demoted.getDemotedAt()).isNotNull();
    }

    @Test
    @DisplayName("D008 Popper Falsification Harness: Promotion strictly requires resolved samples N >= 20 and rate >= 80%")
    void falsifyPopperFalsification_promotionRequiresRigorousSampleAndAgreementCensus() {
        LeverPromotionStateEntity state = new LeverPromotionStateEntity();
        state.setLeverKey("TEST_CANDIDATE");
        state.setCurrentStage(LeverStage.OBSERVE_ONLY.wireValue());
        when(stateRepository.findAll()).thenReturn(List.of(state));

        // Case 1: Insufficient samples (19 samples, 100% agreement) -> MUST NOT promote
        List<LeverObservation> insufficientSamples = createObservations(19, "TRUE", Instant.now());
        when(observationRepository.findByLeverKeyAndObservedAtAfterOrderByObservedAtAsc(eq("TEST_CANDIDATE"), any()))
                .thenReturn(insufficientSamples);

        service.evaluatePromotions();
        assertThat(state.getCurrentStage()).isEqualTo(LeverStage.OBSERVE_ONLY.wireValue());

        // Case 2: Sufficient samples (20 samples), but agreement rate below 80% (15/20 = 75%) -> MUST NOT promote
        List<LeverObservation> lowAgreement = new ArrayList<>();
        lowAgreement.addAll(createObservations(15, "TRUE", Instant.now()));
        lowAgreement.addAll(createObservations(5, "FALSE", Instant.now()));
        when(observationRepository.findByLeverKeyAndObservedAtAfterOrderByObservedAtAsc(eq("TEST_CANDIDATE"), any()))
                .thenReturn(lowAgreement);

        service.evaluatePromotions();
        assertThat(state.getCurrentStage()).isEqualTo(LeverStage.OBSERVE_ONLY.wireValue());

        // Case 3: Severe test passed (20 samples, 16 TRUE = 80%) -> Promotes exactly one stage
        List<LeverObservation> qualifying = new ArrayList<>();
        qualifying.addAll(createObservations(16, "TRUE", Instant.now()));
        qualifying.addAll(createObservations(4, "FALSE", Instant.now()));
        when(observationRepository.findByLeverKeyAndObservedAtAfterOrderByObservedAtAsc(eq("TEST_CANDIDATE"), any()))
                .thenReturn(qualifying);

        service.evaluatePromotions();
        assertThat(state.getCurrentStage()).isEqualTo(LeverStage.WARN_ONLY.wireValue());
        assertThat(state.getPromotedAt()).isNotNull();
    }

    // =========================================================================
    // D010: Alvin Goldman Reliable Process Audit (ALVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS)
    // =========================================================================

    @Test
    @DisplayName("D010 Goldman Trust Dynamics: Each promotion step consumes evidence once and requires fresh observations")
    void falsifyGoldmanTrustDynamics_subsequentEvaluationsRequireFreshEvidencePacket() {
        LeverPromotionStateEntity state = new LeverPromotionStateEntity();
        state.setLeverKey("LADDER_LEVER");
        state.setCurrentStage(LeverStage.OBSERVE_ONLY.wireValue());
        when(stateRepository.findAll()).thenReturn(List.of(state));

        Instant pastTimestamp = Instant.now().minusSeconds(1000);
        List<LeverObservation> staticPacket = createObservations(30, "TRUE", pastTimestamp);

        when(observationRepository.findByLeverKeyAndObservedAtAfterOrderByObservedAtAsc(eq("LADDER_LEVER"), any()))
                .thenAnswer(invocation -> {
                    Instant since = invocation.getArgument(1);
                    return staticPacket.stream()
                            .filter(o -> o.getObservedAt().isAfter(since))
                            .toList();
                });

        // First promotion cycle: consumes initial 30 observations -> promotes to warn_only
        service.evaluatePromotions();
        assertThat(state.getCurrentStage()).isEqualTo(LeverStage.WARN_ONLY.wireValue());
        Instant firstPromotedAt = state.getPromotedAt();
        assertThat(firstPromotedAt).isNotNull();

        // Second promotion cycle with NO fresh observations: must remain at warn_only!
        service.evaluatePromotions();
        assertThat(state.getCurrentStage())
                .as("Causal audit integrity: same evidence packet cannot be reused for double promotion")
                .isEqualTo(LeverStage.WARN_ONLY.wireValue());
    }
}
